package com.antigravity.mobile.core.mcp

import com.antigravity.mobile.contracts.ContractsJson
import com.antigravity.mobile.contracts.McpServerHealth
import com.antigravity.mobile.contracts.McpToolNames
import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.PolicyDecision
import com.antigravity.mobile.contracts.PolicyEvaluation
import com.antigravity.mobile.contracts.RootExecutor
import com.antigravity.mobile.contracts.SecurityPolicyEngine
import com.antigravity.mobile.contracts.UiBounds
import com.antigravity.mobile.contracts.UiNode
import com.antigravity.mobile.contracts.UiTreeProvider
import com.antigravity.mobile.core.accessibility.NotificationBridge
import com.antigravity.mobile.core.root.SuFileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class KtorAndroidMcpServerTest {

    private var activeServer: KtorAndroidMcpServer? = null

    private data class HttpResponseSnapshot(
        val statusCode: Int,
        val body: String,
    )

    @After
    fun tearDown() = runBlocking {
        activeServer?.stop()
        activeServer = null
    }

    private fun buildServer(): KtorAndroidMcpServer {
        val rootExecutor = mockk<RootExecutor>(relaxed = true)
        val suFileRepo = mockk<SuFileRepository>(relaxed = true)
        val uiTreeProvider = mockk<UiTreeProvider>(relaxed = true)
        val notificationBridge = mockk<NotificationBridge>(relaxed = true)
        val gateway = mockk<AndroidSystemToolGateway>(relaxed = true)
        val policyEngine = mockk<SecurityPolicyEngine>()
        val overlayController = mockk<OverlayController>(relaxed = true)

        every { uiTreeProvider.isServiceConnected } returns MutableStateFlow(true)
        coEvery { uiTreeProvider.getActivePackageName() } returns "com.example.notes"
        coEvery { uiTreeProvider.captureUiTree(any(), any(), any()) } returns UiNode(
            nodeId = "n0",
            className = "android.widget.FrameLayout",
            packageName = "com.example.notes",
            text = "Hello MCP",
            bounds = UiBounds(0, 0, 1080, 2400),
        )
        coEvery { policyEngine.evaluate(any(), any(), any(), any(), any()) } answers {
            PolicyEvaluation(
                toolName = firstArg(),
                riskLevel = secondArg(),
                decision = PolicyDecision.ALLOW,
                reason = "Allowed in test",
            )
        }

        val dispatcher = McpToolDispatcher(
            rootExecutor = rootExecutor,
            suFileRepository = suFileRepo,
            uiTreeProvider = uiTreeProvider,
            notificationBridge = notificationBridge,
            systemToolGateway = gateway,
            securityPolicyEngine = policyEngine,
            overlayController = overlayController,
        )
        return KtorAndroidMcpServer(dispatcher).also { activeServer = it }
    }

    private fun sendHttp(
        method: String,
        url: String,
        bearerToken: String? = null,
        jsonBody: String? = null,
    ): HttpResponseSnapshot {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
            useCaches = false
            setRequestProperty("Connection", "close")
            if (bearerToken != null) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (jsonBody != null) {
                conn.outputStream.use { out ->
                    out.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponseSnapshot(statusCode = code, body = body)
        } finally {
            conn.disconnect()
        }
    }

    @Test(timeout = 45_000L)
    fun `server binds strictly to 127_0_0_1, enforces 401 Bearer auth, and serves initialize, tools_list, tools_call, health`() =
        runBlocking {
            val server = buildServer()
            val endpoint = server.start(port = 0)

            assertEquals("127.0.0.1", endpoint.host)
            assertTrue("Ephemeral port must be resolved", endpoint.port in 1..65535)
            assertEquals(64, endpoint.bearerToken.length)
            assertEquals("http://127.0.0.1:${endpoint.port}/mcp", endpoint.url)
            assertTrue(server.health.value.isRunning)

            // 1. Missing Authorization header -> 401 Unauthorized on BOTH /health and /mcp
            val noAuthHealth = sendHttp("GET", "http://127.0.0.1:${endpoint.port}/health")
            assertEquals(401, noAuthHealth.statusCode)

            val noAuthMcp = sendHttp(
                method = "POST",
                url = endpoint.url,
                jsonBody = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
            )
            assertEquals(401, noAuthMcp.statusCode)

            // 2. Wrong Bearer token -> 401 Unauthorized
            val badAuthMcp = sendHttp(
                method = "POST",
                url = endpoint.url,
                bearerToken = "deadbeef000000000000000000000000",
                jsonBody = """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""",
            )
            assertEquals(401, badAuthMcp.statusCode)

            // 3. Valid Bearer token -> initialize succeeds
            val initResp = sendHttp(
                method = "POST",
                url = endpoint.url,
                bearerToken = endpoint.bearerToken,
                jsonBody = """{"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{}}""",
            )
            assertEquals(200, initResp.statusCode)
            val initJson = ContractsJson.parseToJsonElement(initResp.body).jsonObject
            assertEquals(
                KtorAndroidMcpServer.MCP_PROTOCOL_VERSION,
                initJson["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content,
            )

            // 4. Valid Bearer token -> tools/list returns all 15 tools (all 13 mandatory categories)
            val listResp = sendHttp(
                method = "POST",
                url = endpoint.url,
                bearerToken = endpoint.bearerToken,
                jsonBody = """{"jsonrpc":"2.0","id":3,"method":"tools/list"}""",
            )
            assertEquals(200, listResp.statusCode)
            val listJson = ContractsJson.parseToJsonElement(listResp.body).jsonObject
            val returnedToolNames = listJson["result"]!!.jsonObject["tools"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }
                .toSet()

            val expectedAll = setOf(
                McpToolNames.ROOT_EXEC,
                McpToolNames.FS_READ,
                McpToolNames.FS_WRITE,
                McpToolNames.FS_LIST,
                McpToolNames.UI_GET_TREE,
                McpToolNames.UI_ACTION,
                McpToolNames.UI_INPUT_RAW,
                McpToolNames.SCREEN_CAPTURE,
                McpToolNames.APP_LAUNCH_OR_MANAGE,
                McpToolNames.PHONE_CALL_CONTROL,
                McpToolNames.SMS_MANAGE,
                McpToolNames.CONTACTS_CALENDAR_QUERY,
                McpToolNames.NOTIFICATIONS_MANAGE,
                McpToolNames.DEVICE_CONTROL,
                McpToolNames.MEDIA_LOCATION_CAPTURE,
            )
            assertEquals(expectedAll, returnedToolNames)

            // 5. Valid Bearer token -> tools/call ui_get_tree returns compact JSON tree
            val callResp = sendHttp(
                method = "POST",
                url = endpoint.url,
                bearerToken = endpoint.bearerToken,
                jsonBody =
                    """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"ui_get_tree","arguments":{"max_depth":10}}}""",
            )
            assertEquals(200, callResp.statusCode)
            val callJson = ContractsJson.parseToJsonElement(callResp.body).jsonObject
            val callResult = callJson["result"]!!.jsonObject
            assertEquals("false", callResult["isError"]!!.jsonPrimitive.content)
            val textPayload = callResult["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
            assertTrue(textPayload.contains("Hello MCP"))

            // 6. Authenticated /health reflects running state & totalCallsHandled == 1
            val healthResp = sendHttp(
                method = "GET",
                url = "http://127.0.0.1:${endpoint.port}/health",
                bearerToken = endpoint.bearerToken,
            )
            assertEquals(200, healthResp.statusCode)
            val health = ContractsJson.decodeFromString(McpServerHealth.serializer(), healthResp.body)
            assertTrue(health.isRunning)
            assertEquals("127.0.0.1", health.endpointHost)
            assertEquals(endpoint.port, health.endpointPort)
            assertEquals(15, health.registeredToolCount)
            assertEquals(1L, health.totalCallsHandled)

            // 7. Restart produces a freshly generated Bearer token
            server.stop()
            assertFalse(server.health.value.isRunning)
            val restarted = server.start(port = 0)
            assertNotEquals(endpoint.bearerToken, restarted.bearerToken)
        }

    @Test(timeout = 30_000L)
    fun `writeProfileMcpConfig writes valid mcp_config_json and preserves existing unrelated servers`() =
        runBlocking {
            val server = buildServer()
            val endpoint = server.start(port = 0)
            val tmpDir = Files.createTempDirectory("ag_mcp_cfg_test").toFile()
            try {
                // Pre-populate with an existing unrelated MCP server
                val configFile = tmpDir.resolve(KtorAndroidMcpServer.MCP_CONFIG_FILENAME)
                configFile.writeText(
                    """{"mcpServers":{"context7":{"type":"http","url":"https://context7.example/mcp"}}}""",
                )

                val writtenPath = server.writeProfileMcpConfig(tmpDir.absolutePath, endpoint).getOrThrow()
                assertEquals(configFile.absolutePath, writtenPath)

                val parsed = ContractsJson.parseToJsonElement(configFile.readText()).jsonObject
                val servers = parsed["mcpServers"]!!.jsonObject
                assertTrue(servers.containsKey("context7"))
                val androidEntry = servers[KtorAndroidMcpServer.ANDROID_SERVER_NAME]!!.jsonObject
                assertEquals("http", androidEntry["type"]!!.jsonPrimitive.content)
                assertEquals(endpoint.url, androidEntry["url"]!!.jsonPrimitive.content)
                assertEquals(
                    "Bearer ${endpoint.bearerToken}",
                    androidEntry["headers"]!!.jsonObject["Authorization"]!!.jsonPrimitive.content,
                )
            } finally {
                tmpDir.deleteRecursively()
            }
        }
}
