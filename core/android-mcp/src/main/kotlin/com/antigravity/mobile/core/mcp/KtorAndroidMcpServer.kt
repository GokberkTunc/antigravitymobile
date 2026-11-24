package com.antigravity.mobile.core.mcp

import com.antigravity.mobile.contracts.AndroidMcpServer
import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.McpEndpoint
import com.antigravity.mobile.contracts.McpServerHealth
import com.antigravity.mobile.contracts.McpToolCall
import com.antigravity.mobile.contracts.McpToolDefinition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedded Ktor CIO local HTTP/JSON-RPC MCP server implementing [AndroidMcpServer].
 *
 * Security invariants (non-negotiable):
 * - Binds STRICTLY to `127.0.0.1` (NEVER `0.0.0.0`).
 * - Generates a fresh 256-bit `SecureRandom` Bearer token on every [start].
 * - Rejects every request missing or mismatching `Authorization: Bearer <token>` with `401 Unauthorized`
 *   using constant-time verification ([McpBearerAuth.verifyAuthorizationHeader]).
 * - Routes every `tools/call` through [McpToolDispatcher], which enforces [SecurityPolicyEngine].
 */
@Singleton
class KtorAndroidMcpServer(
    private val dispatcher: McpToolDispatcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidMcpServer {

    @Inject
    constructor(
        dispatcher: McpToolDispatcher,
    ) : this(
        dispatcher = dispatcher,
        ioDispatcher = Dispatchers.IO,
    )

    private val lifecycleMutex = Mutex()
    private val totalCallsHandled = AtomicLong(0L)
    private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

    private var serverEngine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var serverScope: CoroutineScope? = null
    private var activeEndpoint: McpEndpoint? = null

    private val _health = MutableStateFlow(
        McpServerHealth(
            isRunning = false,
            endpointHost = LOOPBACK_HOST,
            endpointPort = null,
            registeredToolCount = dispatcher.registeredTools().size,
            registeredTools = dispatcher.registeredTools().map { it.name },
        ),
    )
    override val health: StateFlow<McpServerHealth> = _health.asStateFlow()

    override fun registeredTools(): List<McpToolDefinition> = dispatcher.registeredTools()

    override suspend fun start(port: Int): McpEndpoint = lifecycleMutex.withLock {
        require(port == 0 || port in 1..65535) {
            "Requested MCP port must be 0 (ephemeral) or 1..65535, got: $port"
        }

        // If already running on the requested port (or any port when ephemeral), return existing endpoint.
        val existing = activeEndpoint
        if (serverEngine != null && existing != null && (port == 0 || existing.port == port)) {
            return@withLock existing
        }

        // Stop any stale instance cleanly before re-binding.
        stopLocked()

        val bearerToken = McpBearerAuth.generateToken()
        val startedAt = System.currentTimeMillis()
        totalCallsHandled.set(0L)

        val targetPort = if (port == 0) {
            withContext(ioDispatcher) {
                ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST)).use { socket ->
                    socket.localPort
                }
            }
        } else {
            port
        }

        val ownedScope = CoroutineScope(SupervisorJob() + ioDispatcher)
        val engine = withContext(ioDispatcher) {
            createAndStartServer(
                scope = ownedScope,
                targetPort = targetPort,
                bearerToken = bearerToken,
            )
        }

        val resolvedConnector = engine.engine.resolvedConnectors().firstOrNull()
        val boundHost = resolvedConnector?.host ?: LOOPBACK_HOST
        val boundPort = resolvedConnector?.port ?: targetPort
        check(boundHost == LOOPBACK_HOST || boundHost == "localhost") {
            "Refusing non-loopback MCP binding: $boundHost"
        }

        val endpoint = McpEndpoint(
            host = LOOPBACK_HOST,
            port = boundPort,
            bearerToken = bearerToken,
            path = MCP_PATH,
        )

        serverScope = ownedScope
        serverEngine = engine
        activeEndpoint = endpoint
        _health.value = McpServerHealth(
            isRunning = true,
            endpointHost = LOOPBACK_HOST,
            endpointPort = endpoint.port,
            registeredToolCount = registeredTools().size,
            registeredTools = registeredTools().map { it.name },
            totalCallsHandled = 0L,
            lastError = null,
            startedAtEpochMs = startedAt,
        )
        endpoint
    }

    /**
     * Kept outside any `CoroutineScope` receiver so Ktor 3 resolves to the standalone top-level
     * `embeddedServer(...)` instead of the `CoroutineScope.embeddedServer(...)` extension (which
     * would otherwise attach the long-running server job as a child of a transient `withContext` scope).
     */
    private fun createAndStartServer(
        scope: CoroutineScope,
        targetPort: Int,
        bearerToken: String,
    ): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val created = scope.embeddedServer(
            factory = CIO,
            port = targetPort,
            host = LOOPBACK_HOST,
        ) {
            // Global Bearer token gate executed before routing on every HTTP request.
            intercept(ApplicationCallPipeline.Plugins) {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (!McpBearerAuth.verifyAuthorizationHeader(authHeader, bearerToken)) {
                    call.response.headers.append(HttpHeaders.Connection, "close")
                    call.respondText(
                        text = UNAUTHORIZED_JSON,
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.Unauthorized,
                    )
                    finish()
                    return@intercept
                }
            }

            routing {
                get(HEALTH_PATH) {
                    val snapshot = _health.value.copy(
                        totalCallsHandled = totalCallsHandled.get(),
                    )
                    call.response.headers.append(HttpHeaders.Connection, "close")
                    call.respondText(
                        text = ContractsJson.encodeToString(McpServerHealth.serializer(), snapshot),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK,
                    )
                }

                post(MCP_PATH) {
                    val rawBody = call.receiveText()
                    val (status, responseBody) = handleJsonRpcRequest(rawBody)
                    call.response.headers.append(HttpHeaders.Connection, "close")
                    call.respondText(
                        text = responseBody,
                        contentType = ContentType.Application.Json,
                        status = status,
                    )
                }
            }
        }
        created.start(wait = false)
        return created
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        stopLocked()
    }

    private suspend fun stopLocked() {
        val engine = serverEngine
        val scope = serverScope
        serverEngine = null
        serverScope = null
        activeEndpoint = null
        if (engine != null) {
            val stopped = CompletableDeferred<Unit>()
            val shutdownThread = Thread(
                {
                    try {
                        engine.stop(gracePeriodMillis = 50L, timeoutMillis = 300L)
                    } catch (_: Throwable) {
                    } finally {
                        stopped.complete(Unit)
                    }
                },
                "ktor-mcp-shutdown",
            ).apply { isDaemon = true }
            shutdownThread.start()
            withTimeoutOrNull(1_500L) {
                stopped.await()
            }
        }
        scope?.cancel()
        _health.value = _health.value.copy(
            isRunning = false,
            endpointPort = null,
            totalCallsHandled = totalCallsHandled.get(),
        )
    }

    override suspend fun writeProfileMcpConfig(
        profileConfigDir: String,
        endpoint: McpEndpoint,
    ): Result<String> = withContext(ioDispatcher) {
        try {
            require(profileConfigDir.isNotBlank()) { "profileConfigDir must not be blank" }
            val dir = File(profileConfigDir)
            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext Result.failure(
                    IllegalStateException("Could not create profile config directory: $profileConfigDir"),
                )
            }
            val configFile = File(dir, MCP_CONFIG_FILENAME)

            val existingServers: Map<String, JsonElement> = if (configFile.exists() && configFile.length() > 0L) {
                try {
                    val existingRoot = ContractsJson.parseToJsonElement(configFile.readText()).jsonObject
                    existingRoot["mcpServers"]?.jsonObject ?: emptyMap()
                } catch (_: Throwable) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            val androidServerEntry = buildJsonObject {
                put("type", "http")
                put("url", endpoint.url)
                put(
                    "headers",
                    buildJsonObject {
                        put(HttpHeaders.Authorization, "Bearer ${endpoint.bearerToken}")
                    },
                )
            }

            val mergedServers = buildJsonObject {
                for ((name, value) in existingServers) {
                    if (name != ANDROID_SERVER_NAME) {
                        put(name, value)
                    }
                }
                put(ANDROID_SERVER_NAME, androidServerEntry)
            }

            val rootConfig = buildJsonObject {
                put("mcpServers", mergedServers)
            }

            val rendered = prettyJson.encodeToString(JsonObject.serializer(), rootConfig)
            val tmpFile = File(dir, "$MCP_CONFIG_FILENAME.tmp")
            tmpFile.writeText(rendered, Charsets.UTF_8)
            if (!tmpFile.renameTo(configFile)) {
                configFile.writeText(rendered, Charsets.UTF_8)
                tmpFile.delete()
            }
            Result.success(configFile.absolutePath)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun handleJsonRpcRequest(rawBody: String): Pair<HttpStatusCode, String> {
        val requestObj: JsonObject = try {
            ContractsJson.parseToJsonElement(rawBody).jsonObject
        } catch (t: Throwable) {
            return HttpStatusCode.BadRequest to jsonRpcError(
                id = JsonNull,
                code = -32700,
                message = "Parse error: ${t.message}",
            )
        }

        val id: JsonElement = requestObj["id"] ?: JsonNull
        val method = (requestObj["method"] as? JsonPrimitive)?.contentOrNull
            ?: return HttpStatusCode.BadRequest to jsonRpcError(
                id = id,
                code = -32600,
                message = "Invalid Request: missing 'method'",
            )
        val params = requestObj["params"] as? JsonObject ?: JsonObject(emptyMap())

        return when (method) {
            "initialize" -> {
                val result = buildJsonObject {
                    put("protocolVersion", MCP_PROTOCOL_VERSION)
                    put(
                        "capabilities",
                        buildJsonObject {
                            put("tools", buildJsonObject { put("listChanged", false) })
                        },
                    )
                    put(
                        "serverInfo",
                        buildJsonObject {
                            put("name", SERVER_INFO_NAME)
                            put("version", SERVER_INFO_VERSION)
                        },
                    )
                }
                HttpStatusCode.OK to jsonRpcResult(id, result)
            }

            "notifications/initialized" -> {
                HttpStatusCode.Accepted to jsonRpcResult(id, JsonObject(emptyMap()))
            }

            "ping" -> {
                HttpStatusCode.OK to jsonRpcResult(id, JsonObject(emptyMap()))
            }

            "tools/list" -> {
                val toolsArray = buildJsonArray {
                    for (tool in registeredTools()) {
                        val schemaElement = try {
                            ContractsJson.parseToJsonElement(tool.inputSchemaJson)
                        } catch (_: Throwable) {
                            JsonObject(emptyMap())
                        }
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("inputSchema", schemaElement)
                                put(
                                    "annotations",
                                    buildJsonObject {
                                        put("riskLevel", tool.riskLevel.name)
                                    },
                                )
                            },
                        )
                    }
                }
                val result = buildJsonObject {
                    put("tools", toolsArray)
                }
                HttpStatusCode.OK to jsonRpcResult(id, result)
            }

            "tools/call" -> {
                val toolName = (params["name"] as? JsonPrimitive)?.contentOrNull
                    ?: return HttpStatusCode.OK to jsonRpcError(
                        id = id,
                        code = -32602,
                        message = "Invalid params: missing 'name'",
                    )
                val argumentsElement = params["arguments"] ?: JsonObject(emptyMap())
                val projectId = (params["projectId"] as? JsonPrimitive)?.contentOrNull
                val callId = (id as? JsonPrimitive)?.contentOrNull ?: UUID.randomUUID().toString()

                val count = totalCallsHandled.incrementAndGet()
                _health.value = _health.value.copy(totalCallsHandled = count)

                val toolResult = dispatcher.dispatch(
                    McpToolCall(
                        callId = callId,
                        toolName = toolName,
                        argumentsJson = argumentsElement.toString(),
                        projectId = projectId,
                    ),
                )

                val mcpCallResult = buildJsonObject {
                    put("isError", toolResult.isError)
                    put(
                        "content",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", toolResult.textContent)
                                },
                            )
                        },
                    )
                    put("durationMs", toolResult.durationMs)
                }
                HttpStatusCode.OK to jsonRpcResult(id, mcpCallResult)
            }

            else -> {
                HttpStatusCode.OK to jsonRpcError(
                    id = id,
                    code = -32601,
                    message = "Method not found: $method",
                )
            }
        }
    }

    private fun jsonRpcResult(id: JsonElement, result: JsonElement): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }.toString()

    private fun jsonRpcError(id: JsonElement, code: Int, message: String): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put(
                "error",
                buildJsonObject {
                    put("code", code)
                    put("message", message)
                },
            )
        }.toString()

    companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val MCP_PATH = "/mcp"
        const val HEALTH_PATH = "/health"
        const val MCP_CONFIG_FILENAME = "mcp_config.json"
        const val ANDROID_SERVER_NAME = "android-system"
        const val MCP_PROTOCOL_VERSION = "2024-11-05"
        const val SERVER_INFO_NAME = "antigravity-android-system-mcp"
        const val SERVER_INFO_VERSION = "1.0.0"

        private const val UNAUTHORIZED_JSON =
            """{"jsonrpc":"2.0","error":{"code":-32001,"message":"Unauthorized: missing or invalid Bearer token"}}"""
    }
}
