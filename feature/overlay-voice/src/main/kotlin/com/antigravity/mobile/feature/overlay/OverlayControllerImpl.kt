package com.antigravity.mobile.feature.overlay

import com.antigravity.mobile.contracts.ApprovalRequest
import com.antigravity.mobile.contracts.OverlayController
import com.antigravity.mobile.contracts.OverlayState
import com.antigravity.mobile.contracts.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the state and user-approval lifecycle of the floating `SYSTEM_ALERT_WINDOW` overlay.
 *
 * All approval gates are serialized through a [Mutex] and backed by a [CompletableDeferred],
 * ensuring that concurrent critical tool requests never overwrite each other and that timeouts,
 * hide events, or emergency stops cleanly resolve pending approvals to `false` (fail-closed).
 */
@Singleton
class OverlayControllerImpl @Inject constructor() : OverlayController {

    private val _state = MutableStateFlow(OverlayState())
    override val state: StateFlow<OverlayState> = _state.asStateFlow()

    private val _stopRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val stopRequested: SharedFlow<Unit> = _stopRequested.asSharedFlow()

    private val _voicePromptSubmitted = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val voicePromptSubmitted: SharedFlow<String> = _voicePromptSubmitted.asSharedFlow()

    private val _inputFocusRequested = MutableStateFlow(false)
    val inputFocusRequested: StateFlow<Boolean> = _inputFocusRequested.asStateFlow()

    private val approvalQueueMutex = Mutex()
    private val pendingLock = Any()
    private var activeApprovalId: String? = null
    private var activeApprovalDeferred: CompletableDeferred<Boolean>? = null

    override suspend fun showOrb() {
        _state.update { current ->
            current.copy(
                isVisible = true,
                isKilled = false,
            )
        }
    }

    override suspend fun updateTicker(text: String) {
        _state.update { current ->
            current.copy(tickerText = text)
        }
    }

    override suspend fun setAgentRunning(running: Boolean) {
        _state.update { current ->
            current.copy(
                isAgentRunning = running,
                isKilled = if (running) false else current.isKilled,
            )
        }
    }

    override suspend fun requestUserApproval(request: ApprovalRequest): Boolean {
        return approvalQueueMutex.withLock {
            val deferred = CompletableDeferred<Boolean>()
            synchronized(pendingLock) {
                activeApprovalId = request.requestId
                activeApprovalDeferred = deferred
            }

            _state.update { current ->
                current.copy(
                    isVisible = true,
                    isExpanded = true,
                    isKilled = false,
                    pendingApproval = request,
                    tickerText = current.tickerText.ifBlank { request.title },
                )
            }

            try {
                val effectiveTimeoutMs = request.timeoutMs.coerceAtLeast(1L)
                withTimeoutOrNull(effectiveTimeoutMs) {
                    deferred.await()
                } ?: false
            } catch (ce: CancellationException) {
                deferred.complete(false)
                throw ce
            } finally {
                synchronized(pendingLock) {
                    if (activeApprovalId == request.requestId) {
                        activeApprovalId = null
                        activeApprovalDeferred = null
                    }
                }
                _state.update { current ->
                    if (current.pendingApproval?.requestId == request.requestId) {
                        current.copy(pendingApproval = null)
                    } else {
                        current
                    }
                }
            }
        }
    }

    override suspend fun showKillNotice(message: String) {
        cancelActiveApproval()
        _inputFocusRequested.value = false
        _state.update { current ->
            current.copy(
                isVisible = true,
                isExpanded = true,
                isAgentRunning = false,
                pendingApproval = null,
                isKilled = true,
                tickerText = message,
            )
        }
    }

    override suspend fun hide() {
        cancelActiveApproval()
        _inputFocusRequested.value = false
        _state.update { current ->
            current.copy(
                isVisible = false,
                isExpanded = false,
                pendingApproval = null,
            )
        }
    }

    /**
     * Resolves the currently active [ApprovalRequest] from the overlay UI (`İzin Ver` / `Reddet`).
     * Returns `true` if a matching pending approval was completed.
     */
    fun resolveApproval(requestId: String, approved: Boolean): Boolean {
        val deferredToComplete = synchronized(pendingLock) {
            if (activeApprovalId == requestId) {
                activeApprovalDeferred
            } else {
                null
            }
        } ?: return false

        val completed = deferredToComplete.complete(approved)
        if (completed) {
            _state.update { current ->
                if (current.pendingApproval?.requestId == requestId) {
                    current.copy(pendingApproval = null)
                } else {
                    current
                }
            }
        }
        return completed
    }

    fun toggleExpanded() {
        _state.update { current ->
            val nextExpanded = !current.isExpanded
            if (!nextExpanded) {
                _inputFocusRequested.value = false
            }
            current.copy(
                isVisible = true,
                isExpanded = nextExpanded,
            )
        }
    }

    fun setExpanded(expanded: Boolean) {
        if (!expanded) {
            _inputFocusRequested.value = false
        }
        _state.update { current ->
            current.copy(isExpanded = expanded)
        }
    }

    /**
     * Invoked when the user taps the one-tap emergency Stop / Kill button in the overlay.
     * Immediately denies any pending approval, marks the overlay as killed, and emits on [stopRequested].
     */
    fun requestEmergencyStop(noticeMessage: String = "Ajan Durduruldu") {
        cancelActiveApproval()
        _inputFocusRequested.value = false
        _state.update { current ->
            current.copy(
                isVisible = true,
                isExpanded = true,
                isAgentRunning = false,
                pendingApproval = null,
                isKilled = true,
                tickerText = noticeMessage,
            )
        }
        _stopRequested.tryEmit(Unit)
    }

    fun dismissKillNotice() {
        _state.update { current ->
            current.copy(
                isKilled = false,
                tickerText = if (current.isKilled) "" else current.tickerText,
            )
        }
    }

    fun updateVoiceState(voiceState: VoiceState) {
        _state.update { current ->
            current.copy(voiceState = voiceState)
        }
    }

    fun submitQuickPrompt(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isNotEmpty()) {
            _voicePromptSubmitted.tryEmit(trimmed)
        }
    }

    /**
     * Toggles whether the overlay window should temporarily accept keyboard focus.
     * Defaults to `false` (`FLAG_NOT_FOCUSABLE`) so the Status Orb never steals focus.
     */
    fun setInputFocusRequested(focused: Boolean) {
        _inputFocusRequested.value = focused
    }

    private fun cancelActiveApproval() {
        val deferred = synchronized(pendingLock) {
            val current = activeApprovalDeferred
            activeApprovalId = null
            activeApprovalDeferred = null
            current
        }
        deferred?.complete(false)
    }
}
