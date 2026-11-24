package com.antigravity.mobile.core.accessibility

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.antigravity.mobile.contracts.UiActionType
import com.antigravity.mobile.contracts.UiBounds

/**
 * Lightweight abstraction over [AccessibilityNodeInfo] allowing pure JVM tests of UI tree
 * depth/node capping, cycle protection (`IdentityHashMap`), and deterministic node actions.
 */
interface AccessibilityNodeHandle {
    /** Reference identity key used for cycle detection inside `IdentityHashMap`. */
    val identityKey: Any
    val className: String
    val packageName: String
    val text: String?
    val contentDesc: String?
    val resourceId: String?
    val bounds: UiBounds
    val clickable: Boolean
    val longClickable: Boolean
    val editable: Boolean
    val scrollable: Boolean
    val focused: Boolean
    val checked: Boolean?
    val enabled: Boolean
    val visibleToUser: Boolean
    val childCount: Int

    fun childAt(index: Int): AccessibilityNodeHandle?

    fun performAction(action: UiActionType, value: String? = null): Boolean
}

/**
 * Production [AccessibilityNodeHandle] wrapping Android's [AccessibilityNodeInfo].
 */
class AndroidAccessibilityNodeHandle(
    private val node: AccessibilityNodeInfo,
) : AccessibilityNodeHandle {

    override val identityKey: Any get() = node

    override val className: String
        get() = node.className?.toString().orEmpty()

    override val packageName: String
        get() = node.packageName?.toString().orEmpty()

    override val text: String?
        get() = node.text?.toString()?.takeIf { it.isNotEmpty() }

    override val contentDesc: String?
        get() = node.contentDescription?.toString()?.takeIf { it.isNotEmpty() }

    override val resourceId: String?
        get() = node.viewIdResourceName?.takeIf { it.isNotEmpty() }

    override val bounds: UiBounds
        get() {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return UiBounds(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }

    override val clickable: Boolean get() = node.isClickable
    override val longClickable: Boolean get() = node.isLongClickable
    override val editable: Boolean get() = node.isEditable
    override val scrollable: Boolean get() = node.isScrollable
    override val focused: Boolean get() = node.isFocused
    override val checked: Boolean? get() = if (node.isCheckable) node.isChecked else null
    override val enabled: Boolean get() = node.isEnabled
    override val visibleToUser: Boolean get() = node.isVisibleToUser
    override val childCount: Int get() = node.childCount

    override fun childAt(index: Int): AccessibilityNodeHandle? {
        if (index < 0 || index >= node.childCount) return null
        val child = node.getChild(index) ?: return null
        return AndroidAccessibilityNodeHandle(child)
    }

    override fun performAction(action: UiActionType, value: String?): Boolean {
        return when (action) {
            UiActionType.CLICK -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            UiActionType.LONG_CLICK -> node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            UiActionType.FOCUS -> node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            UiActionType.SET_TEXT -> {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        value.orEmpty(),
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }

            UiActionType.SCROLL_FORWARD ->
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

            UiActionType.SCROLL_BACKWARD ->
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

            UiActionType.SCROLL -> {
                val dir = value?.trim()?.lowercase().orEmpty()
                val androidAction = if (dir == "backward" || dir == "up" || dir == "left") {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                node.performAction(androidAction)
            }

            UiActionType.BACK,
            UiActionType.HOME,
            UiActionType.RECENTS,
            UiActionType.NOTIFICATIONS,
            -> false
        }
    }
}
