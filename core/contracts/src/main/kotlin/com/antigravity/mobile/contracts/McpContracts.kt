package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class McpToolRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

object McpToolNames {
    const val ROOT_EXEC = "root_exec"
    const val FS_READ = "fs_read"
    const val FS_WRITE = "fs_write"
    const val FS_LIST = "fs_list"
    const val UI_GET_TREE = "ui_get_tree"
    const val UI_ACTION = "ui_action"
    const val UI_INPUT_RAW = "ui_input_raw"
    const val SCREEN_CAPTURE = "screen_capture"
    const val APP_LAUNCH_OR_MANAGE = "app_launch_or_manage"
    const val PHONE_CALL_CONTROL = "phone_call_control"
    const val SMS_MANAGE = "sms_manage"
    const val CONTACTS_CALENDAR_QUERY = "contacts_calendar_query"
    const val NOTIFICATIONS_MANAGE = "notifications_manage"
    const val DEVICE_CONTROL = "device_control"
    const val MEDIA_LOCATION_CAPTURE = "media_location_capture"
}

@Serializable
data class McpEndpoint(
    val host: String = "127.0.0.1",
    val port: Int,
    val bearerToken: String,
    val path: String = "/mcp",
) {
    init {
        require(host == "127.0.0.1" || host == "localhost") {
            "Embedded Android System MCP server MUST bind strictly to loopback (127.0.0.1/localhost), got: $host"
        }
        require(port in 1..65535) {
            "MCP server port must be between 1 and 65535, got: $port"
        }
        require(bearerToken.length >= 16) {
            "MCP Bearer token must be at least 16 characters of high-entropy randomness"
        }
    }

    val url: String
        get() = "http://$host:$port$path"
}

@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String,
    val riskLevel: McpToolRisk,
    val inputSchemaJson: String = "{}",
)

@Serializable
data class McpToolCall(
    val callId: String,
    val toolName: String,
    val argumentsJson: String = "{}",
    val projectId: String? = null,
)

@Serializable
data class McpToolResult(
    val callId: String,
    val toolName: String,
    val isError: Boolean = false,
    val contentJson: String = "{}",
    val textContent: String = "",
    val durationMs: Long = 0L,
)

@Serializable
data class McpServerHealth(
    val isRunning: Boolean = false,
    val endpointHost: String = "127.0.0.1",
    val endpointPort: Int? = null,
    val registeredToolCount: Int = 0,
    val registeredTools: List<String> = emptyList(),
    val totalCallsHandled: Long = 0L,
    val lastError: String? = null,
    val startedAtEpochMs: Long? = null,
)

/**
 * Embedded Ktor MCP Server listening exclusively on `127.0.0.1` with high-entropy
 * `Authorization: Bearer <token>` authentication, exposing all 13 Android system tools.
 */
interface AndroidMcpServer {
    val health: StateFlow<McpServerHealth>

    fun registeredTools(): List<McpToolDefinition>

    suspend fun start(port: Int = 0): McpEndpoint

    suspend fun stop()

    suspend fun writeProfileMcpConfig(
        profileConfigDir: String,
        endpoint: McpEndpoint,
    ): Result<String>
}
