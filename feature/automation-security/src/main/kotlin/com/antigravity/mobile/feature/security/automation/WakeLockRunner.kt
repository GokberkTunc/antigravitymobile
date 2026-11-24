package com.antigravity.mobile.feature.security.automation

import android.content.Context
import android.os.PowerManager

/**
 * Safely acquires and releases a `PowerManager.PARTIAL_WAKE_LOCK` around background `/schedule`
 * task execution using strict `try / finally` semantics so wake locks can never leak.
 */
interface WakeLockRunner {
    suspend fun <T> withPartialWakeLock(
        tag: String = DEFAULT_WAKE_LOCK_TAG,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend () -> T,
    ): T

    companion object {
        const val DEFAULT_WAKE_LOCK_TAG = "AntigravityMobile:ScheduledTaskWakeLock"
        const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L // 5-minute safety ceiling
    }
}

class AndroidWakeLockRunner(
    private val powerManager: PowerManager?,
) : WakeLockRunner {

    constructor(context: Context) : this(
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager,
    )

    override suspend fun <T> withPartialWakeLock(
        tag: String,
        timeoutMs: Long,
        block: suspend () -> T,
    ): T {
        val manager = powerManager ?: return block()
        val boundedTimeout = timeoutMs.coerceAtLeast(1_000L)
        val wakeLock = try {
            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                setReferenceCounted(false)
            }
        } catch (_: SecurityException) {
            return block()
        }

        var acquired = false
        try {
            wakeLock.acquire(boundedTimeout)
            acquired = true
            return block()
        } finally {
            if (acquired && wakeLock.isHeld) {
                try {
                    wakeLock.release()
                } catch (_: RuntimeException) {
                    // Already released by timeout
                }
            }
        }
    }
}
