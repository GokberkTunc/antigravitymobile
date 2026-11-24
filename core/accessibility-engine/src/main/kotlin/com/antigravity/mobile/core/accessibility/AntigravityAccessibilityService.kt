package com.antigravity.mobile.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.antigravity.mobile.contracts.UiActionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Android [AccessibilityService] hosting structured UI tree capture, deterministic node actions,
 * and the hardware Volume-Up + Volume-Down (500ms) Kill-Switch detector.
 */
class AntigravityAccessibilityService : AccessibilityService(), AccessibilityHost {

    private var serviceScope: CoroutineScope? = null
    private var killSwitchDetector: VolumeChordKillSwitchDetector? = null

    @Volatile
    private var lastObservedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        serviceScope = scope
        killSwitchDetector = VolumeChordKillSwitchDetector(
            scope = scope,
            holdDurationMs = VolumeChordKillSwitchDetector.DEFAULT_HOLD_DURATION_MS,
            onTriggered = {
                AccessibilityServiceBridge.instance.emitKillSwitch()
            },
        )
        AccessibilityServiceBridge.instance.attachHost(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (pkg != null) {
            lastObservedPackage = pkg
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val detector = killSwitchDetector ?: return false
        return detector.onKeyEvent(
            keyCode = event.keyCode,
            action = event.action,
            eventTimeMs = event.eventTime,
        )
    }

    override fun onInterrupt() {
        killSwitchDetector?.reset()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun activeRootNode(): AccessibilityNodeHandle? {
        val root = rootInActiveWindow ?: return null
        return AndroidAccessibilityNodeHandle(root)
    }

    override fun activePackageName(): String? {
        return rootInActiveWindow?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: lastObservedPackage
    }

    override fun performGlobalAction(action: UiActionType): Boolean {
        val androidAction = when (action) {
            UiActionType.BACK -> GLOBAL_ACTION_BACK
            UiActionType.HOME -> GLOBAL_ACTION_HOME
            UiActionType.RECENTS -> GLOBAL_ACTION_RECENTS
            UiActionType.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        }
        return performGlobalAction(androidAction)
    }

    private fun teardown() {
        killSwitchDetector?.reset()
        killSwitchDetector = null
        serviceScope?.cancel()
        serviceScope = null
        AccessibilityServiceBridge.instance.detachHost(this)
    }
}
