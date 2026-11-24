package com.antigravity.mobile.core.accessibility

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeChordKillSwitchDetectorTest {

    @Test
    fun `triggers exactly once after 500ms simultaneous Volume Up and Volume Down hold`() = runTest {
        var nowMs = 1_000L
        var callbackCount = 0
        val detector = VolumeChordKillSwitchDetector(
            scope = backgroundScope,
            holdDurationMs = 500L,
            clockMs = { nowMs },
            onTriggered = { callbackCount++ },
        )

        var flowEmissions = 0
        val job = backgroundScope.launch {
            detector.killSwitchTriggered.collect { flowEmissions++ }
        }
        runCurrent()

        // Press Volume Up, then Volume Down 20ms later
        assertFalse(detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_UP, VolumeChordKillSwitchDetector.ACTION_DOWN, nowMs))
        nowMs += 20L
        assertFalse(detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_DOWN, VolumeChordKillSwitchDetector.ACTION_DOWN, nowMs))
        assertTrue(detector.isChordActive)

        // At 499ms: must NOT have triggered yet
        advanceTimeBy(499L)
        runCurrent()
        assertEquals(0, flowEmissions)
        assertEquals(0, callbackCount)

        // At 500ms: triggers once!
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, flowEmissions)
        assertEquals(1, callbackCount)

        // Repeated ACTION_DOWN events while still held must NOT re-trigger
        nowMs += 600L
        assertTrue(detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_UP, VolumeChordKillSwitchDetector.ACTION_DOWN, nowMs))
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, flowEmissions)

        // Release one key and re-press for another 500ms -> triggers a second time
        detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_UP, VolumeChordKillSwitchDetector.ACTION_UP, nowMs + 10L)
        assertFalse(detector.isChordActive)

        nowMs += 100L
        detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_UP, VolumeChordKillSwitchDetector.ACTION_DOWN, nowMs)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(2, flowEmissions)
        assertEquals(2, callbackCount)

        job.cancel()
    }

    @Test
    fun `releasing either volume key before 500ms cancels kill-switch`() = runTest {
        var nowMs = 5_000L
        var triggered = 0
        val detector = VolumeChordKillSwitchDetector(
            scope = backgroundScope,
            holdDurationMs = 500L,
            clockMs = { nowMs },
            onTriggered = { triggered++ },
        )

        detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_UP, VolumeChordKillSwitchDetector.ACTION_DOWN, nowMs)
        detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_DOWN, VolumeChordKillSwitchDetector.ACTION_DOWN, nowMs)

        advanceTimeBy(450L)
        runCurrent()
        assertEquals(0, triggered)

        // Release Volume Down early at 450ms
        detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_DOWN, VolumeChordKillSwitchDetector.ACTION_UP, nowMs + 450L)
        assertFalse(detector.isChordActive)

        advanceTimeBy(500L)
        runCurrent()
        assertEquals(0, triggered)
    }

    @Test
    fun `holding only a single volume key never triggers kill-switch`() = runTest {
        var triggered = 0
        val detector = VolumeChordKillSwitchDetector(
            scope = backgroundScope,
            holdDurationMs = 500L,
            onTriggered = { triggered++ },
        )

        detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_UP, VolumeChordKillSwitchDetector.ACTION_DOWN, 100L)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(0, triggered)

        detector.reset()
        detector.onKeyEvent(VolumeChordKillSwitchDetector.KEYCODE_VOLUME_DOWN, VolumeChordKillSwitchDetector.ACTION_DOWN, 2_200L)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(0, triggered)
    }
}
