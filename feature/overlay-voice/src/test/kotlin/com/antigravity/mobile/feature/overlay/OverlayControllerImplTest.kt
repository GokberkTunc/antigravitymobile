package com.antigravity.mobile.feature.overlay

import android.view.WindowManager
import app.cash.turbine.test
import com.antigravity.mobile.contracts.ApprovalRequest
import com.antigravity.mobile.contracts.McpToolRisk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayControllerImplTest {

    private lateinit var controller: OverlayControllerImpl

    @Before
    fun setUp() {
        controller = OverlayControllerImpl()
    }

    @Test
    fun `orb visibility, expansion, ticker and running state transitions work cleanly`() = runTest {
        assertFalse(controller.state.value.isVisible)
        assertFalse(controller.state.value.isExpanded)

        controller.showOrb()
        assertTrue(controller.state.value.isVisible)
        assertFalse(controller.state.value.isExpanded)

        controller.updateTicker("Dialer açılıyor...")
        controller.setAgentRunning(true)
        assertEquals("Dialer açılıyor...", controller.state.value.tickerText)
        assertTrue(controller.state.value.isAgentRunning)

        controller.toggleExpanded()
        assertTrue(controller.state.value.isExpanded)

        controller.setInputFocusRequested(true)
        assertTrue(controller.inputFocusRequested.value)

        controller.toggleExpanded()
        assertFalse(controller.state.value.isExpanded)
        assertFalse(controller.inputFocusRequested.value)

        controller.hide()
        assertFalse(controller.state.value.isVisible)
        assertFalse(controller.state.value.isExpanded)
    }

    @Test
    fun `requestUserApproval returns true when approved and clears pending card`() = runTest {
        val request = sampleApprovalRequest("req-approve")
        val approvalDeferred = async { controller.requestUserApproval(request) }
        runCurrent()

        val stateDuringPrompt = controller.state.value
        assertTrue(stateDuringPrompt.isVisible)
        assertTrue(stateDuringPrompt.isExpanded)
        assertEquals(request, stateDuringPrompt.pendingApproval)

        assertTrue(controller.resolveApproval("req-approve", approved = true))
        assertTrue(approvalDeferred.await())
        assertNull(controller.state.value.pendingApproval)
    }

    @Test
    fun `requestUserApproval returns false when denied`() = runTest {
        val request = sampleApprovalRequest("req-deny")
        val approvalDeferred = async { controller.requestUserApproval(request) }
        runCurrent()

        assertFalse(controller.resolveApproval("wrong-id", approved = true))
        assertTrue(controller.resolveApproval("req-deny", approved = false))
        assertFalse(approvalDeferred.await())
        assertNull(controller.state.value.pendingApproval)
    }

    @Test
    fun `requestUserApproval fails closed on timeout`() = runTest {
        val request = sampleApprovalRequest("req-timeout", timeoutMs = 5_000L)
        val approvalDeferred = async { controller.requestUserApproval(request) }
        runCurrent()

        assertEquals(request, controller.state.value.pendingApproval)

        advanceTimeBy(5_001L)
        runCurrent()

        assertFalse(approvalDeferred.await())
        assertNull(controller.state.value.pendingApproval)
    }

    @Test
    fun `emergency stop denies pending approval, emits stopRequested and shows kill notice`() = runTest {
        val request = sampleApprovalRequest("req-kill")
        controller.setAgentRunning(true)

        controller.stopRequested.test {
            val approvalDeferred = async { controller.requestUserApproval(request) }
            runCurrent()

            controller.requestEmergencyStop("Ajan Durduruldu")
            assertFalse(approvalDeferred.await())
            awaitItem()

            val afterKill = controller.state.value
            assertTrue(afterKill.isKilled)
            assertFalse(afterKill.isAgentRunning)
            assertNull(afterKill.pendingApproval)
            assertEquals("Ajan Durduruldu", afterKill.tickerText)
        }
    }

    @Test
    fun `hide cancels active approval safely`() = runTest {
        val request = sampleApprovalRequest("req-hide")
        val approvalDeferred = async { controller.requestUserApproval(request) }
        runCurrent()

        controller.hide()
        assertFalse(approvalDeferred.await())
        assertFalse(controller.state.value.isVisible)
        assertNull(controller.state.value.pendingApproval)
    }

    @Test
    fun `coroutine cancellation cleans up pending approval and rethrows CancellationException`() = runTest {
        val request = sampleApprovalRequest("req-cancel")
        var caughtCancellation = false
        val job = launch {
            try {
                controller.requestUserApproval(request)
            } catch (ce: CancellationException) {
                caughtCancellation = true
                throw ce
            }
        }
        runCurrent()
        assertEquals(request, controller.state.value.pendingApproval)

        job.cancelAndJoin()
        assertTrue(caughtCancellation)
        assertNull(controller.state.value.pendingApproval)
    }

    @Test
    fun `concurrent approval requests serialize cleanly without losing the second request`() = runTest {
        val first = sampleApprovalRequest("req-1")
        val second = sampleApprovalRequest("req-2")

        val d1 = async { controller.requestUserApproval(first) }
        runCurrent()
        val d2 = async { controller.requestUserApproval(second) }
        runCurrent()

        assertEquals("req-1", controller.state.value.pendingApproval?.requestId)
        assertTrue(controller.resolveApproval("req-1", approved = true))
        runCurrent()

        assertEquals("req-2", controller.state.value.pendingApproval?.requestId)
        assertTrue(controller.resolveApproval("req-2", approved = false))
        runCurrent()

        assertTrue(d1.await())
        assertFalse(d2.await())
        assertNull(controller.state.value.pendingApproval)
    }

    @Test
    fun `overlay window flags default to FLAG_NOT_FOCUSABLE unless text input focus is requested`() {
        val nonFocusableFlags = FloatingOverlayService.computeOverlayWindowFlags(focusable = false)
        assertTrue(nonFocusableFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)

        val focusableFlags = FloatingOverlayService.computeOverlayWindowFlags(focusable = true)
        assertEquals(0, focusableFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun sampleApprovalRequest(
        id: String,
        timeoutMs: Long = 30_000L,
    ) = ApprovalRequest(
        requestId = id,
        toolName = "phone_call_control",
        riskLevel = McpToolRisk.CRITICAL,
        title = "Arama Başlatma Onayı",
        summary = "+905551112233 numarası aranacak",
        argumentsPreview = """{"action":"call","phone_number":"+905551112233"}""",
        timeoutMs = timeoutMs,
    )
}
