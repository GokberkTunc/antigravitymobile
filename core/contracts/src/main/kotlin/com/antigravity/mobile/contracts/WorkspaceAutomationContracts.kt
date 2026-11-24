package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class ScheduledTaskRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

@Serializable
data class ScheduledTask(
    val id: String,
    val name: String,
    val prompt: String,
    val profileId: String,
    val projectId: String? = null,
    val cronExpression: String? = null,
    val triggerAtEpochMs: Long? = null,
    val enabled: Boolean = true,
    val exactTiming: Boolean = false,
    val wakeDevice: Boolean = true,
    val createdAtEpochMs: Long = 0L,
    val lastRunAtEpochMs: Long? = null,
    val nextRunAtEpochMs: Long? = null,
) {
    init {
        require(cronExpression != null || triggerAtEpochMs != null) {
            "ScheduledTask must specify either cronExpression or triggerAtEpochMs"
        }
    }
}

@Serializable
data class ScheduledTaskRun(
    val runId: String,
    val taskId: String,
    val status: ScheduledTaskRunStatus,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long? = null,
    val summary: String = "",
    val errorMessage: String? = null,
)

interface AutomationScheduler {
    val tasks: StateFlow<List<ScheduledTask>>
    val recentRuns: StateFlow<List<ScheduledTaskRun>>

    suspend fun scheduleTask(task: ScheduledTask): Result<ScheduledTask>

    suspend fun cancelTask(taskId: String): Result<Unit>

    suspend fun setTaskEnabled(taskId: String, enabled: Boolean): Result<Unit>

    suspend fun triggerNow(taskId: String): Result<ScheduledTaskRun>
}

@Serializable
data class WorkspaceProject(
    val id: String,
    val name: String,
    val rootPath: String,
    val profileId: String,
    val defaultPermissionMode: PermissionMode = PermissionMode.ASK,
    val lastOpenedAtEpochMs: Long = 0L,
)

@Serializable
enum class ChatMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}

@Serializable
data class ToolCallSummary(
    val callId: String,
    val toolName: String,
    val status: ToolCallStatus,
    val argumentsPreview: String = "",
    val outputPreview: String? = null,
)

@Serializable
data class WorkspaceChatMessage(
    val id: String,
    val sessionId: String,
    val role: ChatMessageRole,
    val content: String,
    val thinkingContent: String? = null,
    val toolCalls: List<ToolCallSummary> = emptyList(),
    val attachments: List<String> = emptyList(),
    val timestampEpochMs: Long = 0L,
    val isStreaming: Boolean = false,
)

@Serializable
enum class SwarmMemberStatus {
    WORKING,
    WAITING,
    STOPPED,
    DONE,
    ERROR,
    KILLED,
}

@Serializable
data class SwarmMemberInfo(
    val name: String,
    val role: String,
    val model: String = "",
    val status: SwarmMemberStatus = SwarmMemberStatus.WAITING,
    val ownedModules: List<String> = emptyList(),
    val lastUpdateText: String = "",
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
data class SwarmBoardMessage(
    val seq: Int,
    val sender: String,
    val text: String,
    val mentions: List<String> = emptyList(),
    val timestampEpochMs: Long = 0L,
)

@Serializable
data class SwarmInteractionEdge(
    val fromMember: String,
    val toMember: String,
    val messageCount: Int = 1,
)

@Serializable
data class SwarmBoardState(
    val swarmTitle: String = "",
    val members: List<SwarmMemberInfo> = emptyList(),
    val messages: List<SwarmBoardMessage> = emptyList(),
    val edges: List<SwarmInteractionEdge> = emptyList(),
)

@Serializable
enum class DiffChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
}

@Serializable
enum class DiffApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

@Serializable
data class DiffItem(
    val id: String,
    val filePath: String,
    val changeType: DiffChangeType = DiffChangeType.MODIFIED,
    val unifiedDiff: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val status: DiffApprovalStatus = DiffApprovalStatus.PENDING,
)
