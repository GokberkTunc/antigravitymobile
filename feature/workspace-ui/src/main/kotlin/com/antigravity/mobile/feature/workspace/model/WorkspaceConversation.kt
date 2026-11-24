package com.antigravity.mobile.feature.workspace.model

import com.antigravity.mobile.contracts.WorkspaceChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceConversation(
    val id: String,
    val projectId: String,
    val title: String,
    val sessionId: String? = null,
    val messages: List<WorkspaceChatMessage> = emptyList(),
    val isPinned: Boolean = false,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    companion object {
        const val DEFAULT_TITLE = "Yeni Sohbet"
    }
}
