package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}

@Serializable
data class UiNode(
    val nodeId: String,
    val className: String = "",
    val packageName: String = "",
    val text: String? = null,
    val contentDesc: String? = null,
    val resourceId: String? = null,
    val bounds: UiBounds,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val focused: Boolean = false,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val children: List<UiNode> = emptyList(),
)

@Serializable
enum class UiActionType {
    CLICK,
    LONG_CLICK,
    SET_TEXT,
    SCROLL,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    FOCUS,
}

@Serializable
data class UiActionRequest(
    val action: UiActionType,
    val nodeId: String? = null,
    val targetText: String? = null,
    val targetResourceId: String? = null,
    val value: String? = null,
)

@Serializable
data class UiActionResult(
    val success: Boolean,
    val message: String = "",
    val matchedNodeId: String? = null,
)

/**
 * Provides structured AccessibilityService UI trees, deterministic node actions,
 * and hardware Volume-Up + Volume-Down (500ms) kill-switch signals.
 */
interface UiTreeProvider {
    val isServiceConnected: StateFlow<Boolean>
    val killSwitchTriggered: SharedFlow<Unit>

    suspend fun getActivePackageName(): String?

    suspend fun captureUiTree(
        includeInvisible: Boolean = false,
        maxDepth: Int = 30,
        maxNodes: Int = 500,
    ): UiNode?

    suspend fun performAction(request: UiActionRequest): UiActionResult
}
