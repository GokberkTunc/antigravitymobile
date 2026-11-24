package com.antigravity.mobile.feature.security.killswitch

import app.cash.turbine.test
import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.AcpSessionInfo
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.UiTreeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KillSwitchCoordinatorTest {

    @Test
    fun `trigger executes strict order - cancel coroutines, cancelSession, killAllAgyProcesses, updateTicker and notify`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val callOrder = mutableListOf<String>()

            val uiTreeProvider = mockk<UiTreeProvider>(relaxed = true)
            every { uiTreeProvider.killSwitchTriggered } returns MutableSharedFlow()

            val acpClient = mockk<AcpClient>()
            every { acpClient.activeSession } returns MutableStateFlow(
                AcpSessionInfo(sessionId = "sess-42", profileId = "prof-1"),
            )
            coEvery { acpClient.cancelSession("sess-42") } coAnswers {
                callOrder.add("2:acp_cancelSession:sess-42")
                Result.success(Unit)
            }

            val chrootManager = mockk<ChrootManager>()
            coEvery { chrootManager.killAllAgyProcesses() } coAnswers {
                callOrder.add("3:chroot_killAllAgyProcesses")
                Result.success(3)
            }

            val overlayController = mockk<OverlayController>(relaxed = true)
            every { overlayController.stopRequested } returns MutableSharedFlow()
            coEvery { overlayController.updateTicker(any()) } coAnswers {
                callOrder.add("4a:overlay_updateTicker:${firstArg<String>()}")
            }
            coEvery { overlayController.showKillNotice(any()) } coAnswers {
                callOrder.add("4b:overlay_showKillNotice:${firstArg<String>()}")
            }
            coEvery { overlayController.setAgentRunning(false) } coAnswers {
                callOrder.add("4c:overlay_setAgentRunning:false")
            }

            val notifier = KillSwitchNotifier { reason, killedCount ->
                callOrder.add("4d:notifier:$reason:$killedCount")
            }

            val coordinator = KillSwitchCoordinator(
                uiTreeProvider = uiTreeProvider,
                acpClient = acpClient,
                chrootManager = chrootManager,
                overlayController = overlayController,
                notifier = notifier,
                ioDispatcher = dispatcher,
                clock = { 1_700_000_000_000L },
            )

            val activeUiJob = launch {
                delay(60_000L)
            }
            coordinator.registerActiveJob(activeUiJob)
            coordinator.registerCancellationHook("ui-macro") {
                callOrder.add("1:cancel_ui_hook")
            }

            coordinator.events.test {
                val result = coordinator.trigger("Hardware volume combo")
                assertTrue(result.isSuccess)
                assertTrue("Registered UI/tool job must be cancelled", activeUiJob.isCancelled)

                assertEquals(
                    listOf(
                        "1:cancel_ui_hook",
                        "2:acp_cancelSession:sess-42",
                        "3:chroot_killAllAgyProcesses",
                        "4a:overlay_updateTicker:Ajan Durduruldu",
                        "4b:overlay_showKillNotice:Ajan Durduruldu",
                        "4c:overlay_setAgentRunning:false",
                        "4d:notifier:Hardware volume combo:3",
                    ),
                    callOrder,
                )

                val event = awaitItem()
                assertEquals("sess-42", event.cancelledSessionId)
                assertEquals(3, event.killedAgyProcessCount)
                assertEquals(2, event.cancelledJobsCount)
                assertTrue(event.errors.isEmpty())
            }
        }

    @Test
    fun `observing hardware killSwitchTriggered and overlay stopRequested invokes shutdown sequence`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val hardwareFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
            val overlayStopFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
            val reasons = mutableListOf<String>()

            val uiTreeProvider = mockk<UiTreeProvider>(relaxed = true)
            every { uiTreeProvider.killSwitchTriggered } returns hardwareFlow

            val acpClient = mockk<AcpClient>()
            every { acpClient.activeSession } returns MutableStateFlow(null)

            val chrootManager = mockk<ChrootManager>()
            coEvery { chrootManager.killAllAgyProcesses() } returns Result.success(1)

            val overlayController = mockk<OverlayController>(relaxed = true)
            every { overlayController.stopRequested } returns overlayStopFlow

            val notifier = KillSwitchNotifier { reason, _ -> reasons.add(reason) }

            val coordinator = KillSwitchCoordinator(
                uiTreeProvider = uiTreeProvider,
                acpClient = acpClient,
                chrootManager = chrootManager,
                overlayController = overlayController,
                notifier = notifier,
                ioDispatcher = dispatcher,
            )

            coordinator.startObserving(backgroundScope)

            hardwareFlow.emit(Unit)
            overlayStopFlow.emit(Unit)

            assertEquals(
                listOf(
                    KillSwitchCoordinator.REASON_HARDWARE_COMBO,
                    KillSwitchCoordinator.REASON_OVERLAY_STOP,
                ),
                reasons,
            )
            coordinator.stopObserving()
        }

    @Test
    fun `chroot kill and overlay notification STILL execute even when AcpClient cancelSession fails`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val callOrder = mutableListOf<String>()

            val uiTreeProvider = mockk<UiTreeProvider>(relaxed = true)
            every { uiTreeProvider.killSwitchTriggered } returns MutableSharedFlow()

            val acpClient = mockk<AcpClient>()
            every { acpClient.activeSession } returns MutableStateFlow(
                AcpSessionInfo(sessionId = "sess-broken", profileId = "prof-1"),
            )
            coEvery { acpClient.cancelSession("sess-broken") } throws IllegalStateException("Pipe broken")

            val chrootManager = mockk<ChrootManager>()
            coEvery { chrootManager.killAllAgyProcesses() } coAnswers {
                callOrder.add("chroot_killed")
                Result.success(2)
            }

            val overlayController = mockk<OverlayController>(relaxed = true)
            every { overlayController.stopRequested } returns MutableSharedFlow()
            coEvery { overlayController.updateTicker(KillSwitchCoordinator.STOPPED_TICKER_TEXT) } coAnswers {
                callOrder.add("overlay_ticker_updated")
            }

            val notifier = KillSwitchNotifier { _, count ->
                callOrder.add("notified:$count")
            }

            val coordinator = KillSwitchCoordinator(
                uiTreeProvider = uiTreeProvider,
                acpClient = acpClient,
                chrootManager = chrootManager,
                overlayController = overlayController,
                notifier = notifier,
                ioDispatcher = dispatcher,
            )

            val result = coordinator.trigger("Test resilience")

            assertTrue("Should propagate failure while still completing downstream kill stages", result.isFailure)
            assertEquals(
                listOf("chroot_killed", "overlay_ticker_updated", "notified:2"),
                callOrder,
            )
        }
}
