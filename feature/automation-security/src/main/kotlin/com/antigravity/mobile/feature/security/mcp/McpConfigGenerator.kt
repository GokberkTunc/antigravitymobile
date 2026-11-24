package com.antigravity.mobile.feature.security.mcp

import com.antigravity.mobile.contracts.McpEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Generates and atomically writes Antigravity CLI `mcp_config.json` pointing strictly
 * to the local loopback Android System MCP server (`127.0.0.1:<port>`) with
 * `Authorization: Bearer <token>` authentication.
 *
 * Non-loopback hosts are unconditionally rejected to preserve the Loopback + Auth invariant.
 */
class McpConfigGenerator(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Validates host, port, path, and bearer token, returning a canonical loopback-only [McpEndpoint]
     * whose host is strictly `127.0.0.1`.
     *
     * @throws IllegalArgumentException if any invariant (loopback host, valid port, high-entropy token,
     * safe path) is violated.
     */
    fun validateLoopbackEndpoint(
        host: String,
        port: Int,
        bearerToken: String,
        path: String = DEFAULT_MCP_PATH,
    ): McpEndpoint {
        val trimmedHost = host.trim()
        require(trimmedHost in ALLOWED_LOOPBACK_HOSTS) {
            "MCP server host MUST be loopback (127.0.0.1 or localhost); rejected non-loopback host: '$host'"
        }
        require(port in 1..65535) {
            "MCP server port must be between 1 and 65535, got: $port"
        }
        require(bearerToken.length >= MIN_TOKEN_LENGTH) {
            "MCP Bearer token must be at least $MIN_TOKEN_LENGTH characters"
        }
        require(bearerToken.none { it.isWhitespace() || it.isISOControl() }) {
            "MCP Bearer token must not contain whitespace or control characters"
        }
        val normalizedPath = path.trim()
        require(normalizedPath.startsWith("/")) {
            "MCP endpoint path must start with '/', got: '$path'"
        }
        require(normalizedPath.none { it.isWhitespace() || it.isISOControl() }) {
            "MCP endpoint path must not contain whitespace or control characters"
        }

        // Normalize `localhost` -> `127.0.0.1` so emitted config exclusively references 127.0.0.1
        return McpEndpoint(
            host = LOOPBACK_IPV4,
            port = port,
            bearerToken = bearerToken,
            path = normalizedPath,
        )
    }

    /**
     * Generates a formatted `mcp_config.json` string for [endpoint].
     */
    fun generateConfigJson(
        endpoint: McpEndpoint,
        serverName: String = DEFAULT_SERVER_NAME,
        existingConfigJson: String? = null,
        preserveExistingServers: Boolean = true,
    ): String {
        val validated = validateLoopbackEndpoint(
            host = endpoint.host,
            port = endpoint.port,
            bearerToken = endpoint.bearerToken,
            path = endpoint.path,
        )
        require(serverName.isNotBlank()) { "MCP server name must not be blank" }

        val existingServers = if (preserveExistingServers && !existingConfigJson.isNullOrBlank()) {
            parseExistingServers(existingConfigJson)
        } else {
            emptyMap()
        }

        val androidSystemEntry = buildJsonObject {
            put("type", JsonPrimitive("http"))
            put("url", JsonPrimitive(validated.url))
            put(
                "headers",
                buildJsonObject {
                    put("Authorization", JsonPrimitive("Bearer ${validated.bearerToken}"))
                },
            )
            put(
                "env",
                buildJsonObject {
                    put("ANTIGRAVITY_MCP_HOST", JsonPrimitive(LOOPBACK_IPV4))
                    put("ANTIGRAVITY_MCP_PORT", JsonPrimitive(validated.port.toString()))
                    put("ANTIGRAVITY_MCP_URL", JsonPrimitive(validated.url))
                    put("ANTIGRAVITY_MCP_TOKEN", JsonPrimitive(validated.bearerToken))
                },
            )
        }

        val mergedServers = linkedMapOf<String, JsonElement>()
        mergedServers.putAll(existingServers)
        mergedServers[serverName] = androidSystemEntry

        val root = buildJsonObject {
            put("mcpServers", JsonObject(mergedServers))
        }
        return prettyJson.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    /**
     * Convenience overload accepting raw parameters so non-loopback host rejection can be validated
     * directly even before constructing [McpEndpoint].
     */
    fun generateConfigJson(
        host: String,
        port: Int,
        bearerToken: String,
        path: String = DEFAULT_MCP_PATH,
        serverName: String = DEFAULT_SERVER_NAME,
        existingConfigJson: String? = null,
        preserveExistingServers: Boolean = true,
    ): String {
        val validated = validateLoopbackEndpoint(host, port, bearerToken, path)
        return generateConfigJson(
            endpoint = validated,
            serverName = serverName,
            existingConfigJson = existingConfigJson,
            preserveExistingServers = preserveExistingServers,
        )
    }

    /**
     * Atomically writes `mcp_config.json` inside [configDir] on [ioDispatcher]:
     * temp file -> flush + fsync -> owner-only permissions -> atomic move/replace.
     */
    suspend fun writeConfigAtomically(
        configDir: File,
        endpoint: McpEndpoint,
        fileName: String = DEFAULT_CONFIG_FILE_NAME,
        serverName: String = DEFAULT_SERVER_NAME,
        preserveExistingServers: Boolean = true,
    ): Result<File> = withContext(ioDispatcher) {
        try {
            val validated = validateLoopbackEndpoint(
                host = endpoint.host,
                port = endpoint.port,
                bearerToken = endpoint.bearerToken,
                path = endpoint.path,
            )
            require(fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\')) {
                "Invalid config file name: '$fileName'"
            }

            if (!configDir.exists() && !configDir.mkdirs()) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to create MCP config directory: ${configDir.absolutePath}"),
                )
            }

            val targetFile = File(configDir, fileName)
            val existingContent = if (preserveExistingServers && targetFile.isFile) {
                targetFile.readText(StandardCharsets.UTF_8)
            } else {
                null
            }

            val jsonContent = generateConfigJson(
                endpoint = validated,
                serverName = serverName,
                existingConfigJson = existingContent,
                preserveExistingServers = preserveExistingServers,
            )

            val tempFile = File(configDir, ".$fileName.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(tempFile).use { fos ->
                    fos.write(jsonContent.toByteArray(StandardCharsets.UTF_8))
                    fos.flush()
                    fos.fd.sync()
                }

                // Restrict permissions to owner-only (0600 on POSIX/Android) to protect Bearer token
                tempFile.setReadable(false, false)
                tempFile.setReadable(true, true)
                tempFile.setWritable(false, false)
                tempFile.setWritable(true, true)

                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

            Result.success(targetFile)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun parseExistingServers(existingJson: String): Map<String, JsonElement> {
        return try {
            val parsed = prettyJson.parseToJsonElement(existingJson).jsonObject
            parsed["mcpServers"]?.jsonObject ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val LOOPBACK_IPV4 = "127.0.0.1"
        const val DEFAULT_MCP_PATH = "/mcp"
        const val DEFAULT_SERVER_NAME = "android-system"
        const val DEFAULT_CONFIG_FILE_NAME = "mcp_config.json"
        const val MIN_TOKEN_LENGTH = 16
        private val ALLOWED_LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost")
    }
}
