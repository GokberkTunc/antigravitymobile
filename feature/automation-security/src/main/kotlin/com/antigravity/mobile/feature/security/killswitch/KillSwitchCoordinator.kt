package com.antigravity.mobile.feature.security.killswitch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.antigravity.mobile.contracts.AcpClient
import com.antigravity.mobile.contracts.ChrootManager
import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.UiTreeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class KillSwitchEvent(
    val reason: String,
    val cancelledJobsCount: Int,
    val cancelledSessionId: String?,
    val killedAgyProcessCount: Int?,
    val timestampEpochMs: Long,
    val errors: List<String> = emptyList(),
)

fun interface KillSwitchNotifier {
    fun notifyAgentStopped(reason: String, killedProcessCount: Int?)
}

class AndroidKillSwitchNotifier(
    private val context: Context,
) : KillSwitchNotifier {

    override fun notifyAgentStopped(reason: String, killedProcessCount: Int?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Acil Durdurma (Kill-Switch)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Donanım tuşu veya durdur düğmesiyle ajan acil durdurma bildirimleri"
            }
            manager.createNotificationChannel(channel)
        }

        val detail = buildString {
            append(KillSwitchCoordinator.STOPPED_TICKER_TEXT)
            if (reason.isNotBlank()) {
                append(" (").append(reason).append(")")
            }
            if (killedProcessCount != null) {
                append(" • ").append(killedProcessCount).append(" süreç sonlandırıldı")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle(KillSwitchCoordinator.STOPPED_TICKER_TEXT)
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS might not yet be granted on Android 13+; overlay notice still displays.
        }
    }

    companion object {
        const val CHANNEL_ID = "antigravity_kill_switch"
        const val NOTIFICATION_ID = 4109
    }
}

/**
 * Coordinates emergency kill-switch triggers from:
 * - Hardware Volume-Up + Volume-Down (500ms) via [UiTreeProvider.killSwitchTriggered]
 * - Floating overlay Stop button via [OverlayController.stopRequested]
 * - Direct calls (e.g. foreground notification Stop action or [SecurityPolicyEngine.triggerEmergencyKillSwitch])
 *
 * Enforces the strict shutdown order:
 * 1. Cancel all active UI/tool coroutines and registered cancellation hooks.
 * 2. Cancel the active ACP session via [AcpClient.cancelSession].
 * 3. Terminate all chroot `agy` processes via [ChrootManager.killAllAgyProcesses].
 * 4. Update overlay ticker ("Ajan Durduruldu"), show kill notice, and post notification.
 */
class KillSwitchCoordinator(
    private val uiTreeProvider: UiTreeProvider,
    private val acpClient: AcpClient,
    private val chrootManager: ChrootManager,
    private val overlayController: OverlayController,
    private val notifier: KillSwitchNotifier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val triggerMutex = Mutex()
    private val observerMutex = Mutex()

    private val activeJobs = CopyOnWriteArrayList<Job>()
    private val cancellationHooks = ConcurrentHashMap<String, suspend () -> Unit>()

    private var hardwareObserverJob: Job? = null
    private var overlayObserverJob: Job? = null

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _events = MutableSharedFlow<KillSwitchEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<KillSwitchEvent> = _events.asSharedFlow()

    /**
     * Begins observing hardware volume-combo and overlay stop button triggers within [scope].
     */
    suspend fun startObserving(scope: CoroutineScope) {
        observerMutex.withLock {
            if (hardwareObserverJob?.isActive == true && overlayObserverJob?.isActive == true) {
                return
            }
            hardwareObserverJob?.cancel()
            overlayObserverJob?.cancel()

            hardwareObserverJob = scope.launch(ioDispatcher) {
                uiTreeProvider.killSwitchTriggered.collect {
                    trigger(REASON_HARDWARE_COMBO)
                }
            }
            overlayObserverJob = scope.launch(ioDispatcher) {
                overlayController.stopRequested.collect {
                    trigger(REASON_OVERLAY_STOP)
                }
            }
        }
    }

    /**
     * Stops observing trigger flows cleanly.
     */
    suspend fun stopObserving() {
        observerMutex.withLock {
            hardwareObserverJob?.cancel()
            hardwareObserverJob = null
            overlayObserverJob?.cancel()
            overlayObserverJob = null
        }
    }

    /**
     * Registers an active UI or tool execution [Job] so step (1) cancels it immediately on kill-switch.
     */
    fun registerActiveJob(job: Job) {
        if (!job.isActive) return
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    /**
     * Registers a custom cancellation hook executed during step (1) before ACP/chroot termination.
     */
    fun registerCancellationHook(id: String, hook: suspend () -> Unit) {
        cancellationHooks[id] = hook
    }

    fun unregisterCancellationHook(id: String) {
        cancellationHooks.remove(id)
    }

    /**
     * Executes the strict 4-stage emergency shutdown sequence.
     */
    suspend fun trigger(reason: String = REASON_HARDWARE_COMBO): Result<Unit> =
        withContext(ioDispatcher) {
            triggerMutex.withLock {
                _isExecuting.value = true
                val errors = mutableListOf<String>()
                var firstFailure: Throwable? = null
                var cancelledCount = 0
                var cancelledSessionId: String? = null
                var killedProcessCount: Int? = null

                try {
                    // Stage 1: Cancel active UI/tool coroutines and hooks
                    cancelledCount = cancelActiveExecutions(reason, errors) { err ->
                        if (firstFailure == null) firstFailure = err
                    }

                    // Stage 2: Cancel active ACP session
                    val sessionId = acpClient.activeSession.value?.sessionId
                    if (sessionId != null) {
                        cancelledSessionId = sessionId
                        try {
                            val cancelResult = acpClient.cancelSession(sessionId)
                            cancelResult.onFailure { err ->
                                errors.add("ACP cancelSession failed: ${err.message}")
                                if (firstFailure == null) firstFailure = err
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            errors.add("ACP cancelSession threw: ${t.message}")
                            if (firstFailure == null) firstFailure = t
                        }
                    }

                    // Stage 3: Kill all chroot `agy` processes
                    try {
                        val killResult = chrootManager.killAllAgyProcesses()
                        killResult.onSuccess { count ->
                            killedProcessCount = count
                        }.onFailure { err ->
                            errors.add("Chroot killAllAgyProcesses failed: ${err.message}")
                            if (firstFailure == null) firstFailure = err
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        errors.add("Chroot killAllAgyProcesses threw: ${t.message}")
                        if (firstFailure == null) firstFailure = t
                    }

                    // Stage 4: Update overlay ticker ("Ajan Durduruldu") + kill notice + notification
                    try {
                        overlayController.updateTicker(STOPPED_TICKER_TEXT)
                        overlayController.showKillNotice(STOPPED_TICKER_TEXT)
                        overlayController.setAgentRunning(false)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        errors.add("Overlay update failed: ${t.message}")
                        if (firstFailure == null) firstFailure = t
                    }

                    try {
                        notifier.notifyAgentStopped(reason, killedProcessCount)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        errors.add("Kill-switch notification failed: ${t.message}")
                        if (firstFailure == null) firstFailure = t
                    }

                    _events.tryEmit(
                        KillSwitchEvent(
                            reason = reason,
                            cancelledJobsCount = cancelledCount,
                            cancelledSessionId = cancelledSessionId,
                            killedAgyProcessCount = killedProcessCount,
                            timestampEpochMs = clock(),
                            errors = errors.toList(),
                        ),
                    )

                    val failure = firstFailure
                    if (failure != null) Result.failure(failure) else Result.success(Unit)
                } finally {
                    _isExecuting.value = false
                }
            }
        }

    private suspend fun cancelActiveExecutions(
        reason: String,
        errors: MutableList<String>,
        onFirstError: (Throwable) -> Unit,
    ): Int {
        var count = 0

        val jobsSnapshot = activeJobs.toList()
        activeJobs.clear()
        for (job in jobsSnapshot) {
            if (job.isActive) {
                job.cancel(CancellationException("Emergency kill-switch triggered: $reason"))
                count++
            }
        }

        val hooksSnapshot = cancellationHooks.entries.toList()
        for ((id, hook) in hooksSnapshot) {
            try {
                hook.invoke()
                count++
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                errors.add("Cancellation hook '$id' failed: ${t.message}")
                onFirstError(t)
            }
        }

        return count
    }

    companion object {
        const val STOPPED_TICKER_TEXT = "Ajan Durduruldu"
        const val REASON_HARDWARE_COMBO = "Hardware volume combo (500ms)"
        const val REASON_OVERLAY_STOP = "Overlay Stop button"
    }
}
