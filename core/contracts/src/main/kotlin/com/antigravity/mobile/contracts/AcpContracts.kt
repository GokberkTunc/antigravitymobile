package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AcpModel(
    val cliId: String,
    val displayName: String,
) {
    GEMINI_4_NEON("gemini-4-neon", "Gemini 4 Neon"),
    GEMINI_4_HYDROGEN("gemini-4-hydrogen", "Gemini 4 Hydrogen"),
    GEMINI_4_CARBON("gemini-4-carbon", "Gemini 4 Carbon"),
    ORCHESTRATOR("orchestrator", "Orchestrator"),
}

@Serializable
enum class ThinkingEffort(
    val cliFlagValue: String,
) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    EXTRA("xhigh"),
}

@Serializable
enum class PermissionMode(
    val cliFlagValue: String,
) {
    AUTO("auto"),
    ASK("accept-edits"),
    PLAN("plan"),
}

@Serializable
enum class AcpSessionState {
    DISCONNECTED,
    CONNECTING,
    INITIALIZING,
    READY,
    STREAMING,
    AWAITING_PERMISSION,
    AWAITING_AUTH,
    CANCELLING,
    ERROR,
    CLOSED,
}

@Serializable
enum class ToolCallStatus {
    PENDING,
    RUNNING,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Serializable
data class AcpSessionConfig(
    val profileId: String,
    val workspaceDir: String = "/mnt/sdcard",
    val additionalDirs: List<String> = listOf("/mnt/sdcard"),
    val model: AcpModel = AcpModel.GEMINI_4_NEON,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val permissionMode: PermissionMode = PermissionMode.ASK,
    val ideMcpPort: Int = 0,
    val processPerSession: Boolean = true,
    val resumeConversationId: String? = null,
    val agentName: String? = null,
    val sandboxed: Boolean = false,
)

@Serializable
data class AcpSessionInfo(
    val sessionId: String,
    val profileId: String,
    val title: String = "",
    val workspaceDir: String = "/mnt/sdcard",
    val model: AcpModel = AcpModel.GEMINI_4_NEON,
    val state: AcpSessionState = AcpSessionState.READY,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
data class PermissionResponse(
    val requestId: String,
    val approved: Boolean,
    val rememberForSession: Boolean = false,
    val reason: String? = null,
)

@Serializable
sealed interface AcpEvent {
    @Serializable
    @SerialName("text_delta")
    data class TextDelta(
        val sessionId: String,
        val delta: String,
        val messageId: String? = null,
        val isFinal: Boolean = false,
    ) : AcpEvent

    @Serializable
    @SerialName("thought_delta")
    data class ThoughtDelta(
        val sessionId: String,
        val delta: String,
        val isFinal: Boolean = false,
    ) : AcpEvent

    @Serializable
    @SerialName("tool_call_update")
    data class ToolCallUpdate(
        val sessionId: String,
        val callId: String,
        val toolName: String,
        val status: ToolCallStatus,
        val argumentsJson: String = "{}",
        val outputPreview: String? = null,
        val errorMessage: String? = null,
    ) : AcpEvent

    @Serializable
    @SerialName("artifact_update")
    data class ArtifactUpdate(
        val sessionId: String,
        val artifactId: String,
        val title: String,
        val mimeType: String = "text/markdown",
        val content: String = "",
        val filePath: String? = null,
    ) : AcpEvent

    @Serializable
    @SerialName("swarm_update")
    data class SwarmUpdate(
        val sessionId: String,
        val boardState: SwarmBoardState,
    ) : AcpEvent

    @Serializable
    @SerialName("permission_request")
    data class PermissionRequest(
        val sessionId: String,
        val requestId: String,
        val toolName: String,
        val riskLevel: McpToolRisk = McpToolRisk.MEDIUM,
        val description: String,
        val argumentsJson: String = "{}",
    ) : AcpEvent

    @Serializable
    @SerialName("auth_required")
    data class AuthRequired(
        val sessionId: String? = null,
        val errorCode: Int = -32000,
        val message: String,
        val authUrl: String? = null,
    ) : AcpEvent

    @Serializable
    @SerialName("session_state_changed")
    data class SessionStateChanged(
        val sessionId: String,
        val state: AcpSessionState,
        val detail: String? = null,
    ) : AcpEvent

    @Serializable
    @SerialName("error")
    data class Error(
        val sessionId: String? = null,
        val code: Int? = null,
        val message: String,
        val recoverable: Boolean = true,
    ) : AcpEvent
}

/**
 * Bidirectional JSON-RPC v1 client for `agy acp` running inside the ARM64 glibc chroot.
 */
interface AcpClient {
    val events: SharedFlow<AcpEvent>
    val sessionState: StateFlow<AcpSessionState>
    val activeSession: StateFlow<AcpSessionInfo?>

    suspend fun startSession(config: AcpSessionConfig): Result<AcpSessionInfo>

    suspend fun listSessions(profileId: String): Result<List<AcpSessionInfo>>

    suspend fun loadSession(
        sessionId: String,
        config: AcpSessionConfig,
    ): Result<AcpSessionInfo>

    suspend fun sendMessage(
        sessionId: String,
        prompt: String,
        attachments: List<String> = emptyList(),
    ): Result<Unit>

    suspend fun cancelSession(sessionId: String): Result<Unit>

    suspend fun respondToPermission(response: PermissionResponse): Result<Unit>

    suspend fun close()
}
