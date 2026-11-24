package com.antigravity.mobile.core.accessibility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Pure, deterministic state machine detecting the simultaneous Hardware Kill-Switch chord:
 * `KEYCODE_VOLUME_UP` + `KEYCODE_VOLUME_DOWN` held continuously for at least [holdDurationMs]
 * (default `500ms`).
 *
 * Uses raw Android keycode/action integer constants (`24`, `25`, `0`, `1`) so it runs with zero
 * Android framework dependencies in pure JVM unit tests.
 */
class VolumeChordKillSwitchDetector(
    private val scope: CoroutineScope,
    private val holdDurationMs: Long = DEFAULT_HOLD_DURATION_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val onTriggered: (() -> Unit)? = null,
) {

    init {
        require(holdDurationMs > 0L) { "holdDurationMs must be positive, got $holdDurationMs" }
    }

    private val lock = Any()
    private var volumeUpPressed = false
    private var volumeDownPressed = false
    private var chordStartMs: Long? = null
    private var triggeredForCurrentChord = false
    private var pendingTimerJob: Job? = null

    private val _killSwitchTriggered = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val killSwitchTriggered: SharedFlow<Unit> = _killSwitchTriggered.asSharedFlow()

    val isChordActive: Boolean
        get() = synchronized(lock) { volumeUpPressed && volumeDownPressed }

    /**
     * Processes a hardware key event.
     *
     * @return `true` if the kill-switch triggered or is actively latched on this chord event,
     * `false` for normal single-key volume presses.
     */
    fun onKeyEvent(
        keyCode: Int,
        action: Int,
        eventTimeMs: Long = clockMs(),
    ): Boolean {
        if (keyCode != KEYCODE_VOLUME_UP && keyCode != KEYCODE_VOLUME_DOWN) {
            return false
        }

        var shouldFireNow = false
        var consumed = false

        synchronized(lock) {
            when (action) {
                ACTION_DOWN -> {
                    if (keyCode == KEYCODE_VOLUME_UP) volumeUpPressed = true
                    if (keyCode == KEYCODE_VOLUME_DOWN) volumeDownPressed = true

                    if (volumeUpPressed && volumeDownPressed) {
                        if (chordStartMs == null) {
                            chordStartMs = eventTimeMs
                            triggeredForCurrentChord = false
                            scheduleHoldTimerLocked(expectedStartMs = eventTimeMs)
                        } else if (!triggeredForCurrentChord) {
                            val elapsed = eventTimeMs - (chordStartMs ?: eventTimeMs)
                            if (elapsed >= holdDurationMs) {
                                triggeredForCurrentChord = true
                                pendingTimerJob?.cancel()
                                pendingTimerJob = null
                                shouldFireNow = true
                                consumed = true
                            }
                        } else {
                            consumed = true
                        }
                    }
                }

                ACTION_UP -> {
                    if (keyCode == KEYCODE_VOLUME_UP) volumeUpPressed = false
                    if (keyCode == KEYCODE_VOLUME_DOWN) volumeDownPressed = false
                    val wasLatched = triggeredForCurrentChord
                    cancelChordLocked()
                    consumed = wasLatched
                }
            }
        }

        if (shouldFireNow) {
            emitTrigger()
        }
        return consumed
    }

    /**
     * Clears any held key state and cancels pending chord timers (e.g. on service disconnect).
     */
    fun reset() {
        synchronized(lock) {
            volumeUpPressed = false
            volumeDownPressed = false
            cancelChordLocked()
        }
    }

    private fun scheduleHoldTimerLocked(expectedStartMs: Long) {
        pendingTimerJob?.cancel()
        pendingTimerJob = scope.launch {
            delay(holdDurationMs)
            val fire = synchronized(lock) {
                if (volumeUpPressed && volumeDownPressed && chordStartMs == expectedStartMs && !triggeredForCurrentChord) {
                    triggeredForCurrentChord = true
                    pendingTimerJob = null
                    true
                } else {
                    false
                }
            }
            if (fire) {
                emitTrigger()
            }
        }
    }

    private fun cancelChordLocked() {
        chordStartMs = null
        triggeredForCurrentChord = false
        pendingTimerJob?.cancel()
        pendingTimerJob = null
    }

    private fun emitTrigger() {
        _killSwitchTriggered.tryEmit(Unit)
        onTriggered?.invoke()
    }

    companion object {
        const val KEYCODE_VOLUME_UP: Int = 24
        const val KEYCODE_VOLUME_DOWN: Int = 25
        const val ACTION_DOWN: Int = 0
        const val ACTION_UP: Int = 1
        const val DEFAULT_HOLD_DURATION_MS: Long = 500L
    }
}
