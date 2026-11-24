package com.antigravity.mobile.feature.security.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Internal (`android:exported="false"`) broadcast receiver woken by
 * `AlarmManager.setExactAndAllowWhileIdle` for `/schedule` jobs.
 *
 * Acquires a brief `PARTIAL_WAKE_LOCK` safely inside `try/finally` while handing off an expedited
 * WorkManager request so the device stays awake across the handoff without blocking the main thread.
 */
@AndroidEntryPoint
class ScheduledTaskAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dispatchBackend: TaskDispatchBackend

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN_SCHEDULED_TASK) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)?.takeIf { it.isNotBlank() } ?: return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AntigravityMobile:AlarmHandoff_$taskId",
            )?.apply { setReferenceCounted(false) }
        } catch (_: SecurityException) {
            null
        }

        var acquired = false
        try {
            wakeLock?.acquire(HANDOFF_WAKE_LOCK_TIMEOUT_MS)
            acquired = wakeLock != null
            if (::dispatchBackend.isInitialized) {
                dispatchBackend.enqueueImmediate(taskId)
            } else {
                AndroidTaskDispatchBackend(context.applicationContext).enqueueImmediate(taskId)
            }
        } finally {
            if (acquired && wakeLock?.isHeld == true) {
                try {
                    wakeLock.release()
                } catch (_: RuntimeException) {
                    // Already released by timeout
                }
            }
        }
    }

    companion object {
        const val ACTION_RUN_SCHEDULED_TASK =
            "com.antigravity.mobile.feature.security.action.RUN_SCHEDULED_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val HANDOFF_WAKE_LOCK_TIMEOUT_MS = 15_000L
    }
}
