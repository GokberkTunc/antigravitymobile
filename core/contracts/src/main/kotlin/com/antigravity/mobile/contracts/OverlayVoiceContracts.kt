package com.antigravity.mobile.contracts

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface VoiceState {
    @Serializable
    @SerialName("idle")
    data object Idle : VoiceState

    @Serializable
    @SerialName("listening")
    data class Listening(
        val partialText: String = "",
        val rmsDb: Float = 0f,
    ) : VoiceState

    @Serializable
    @SerialName("processing")
    data class Processing(
        val partialText: String = "",
    ) : VoiceState

    @Serializable
    @SerialName("result")
    data class Result(
        val transcript: String,
        val localeTag: String = "tr-TR",
    ) : VoiceState

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
        val errorCode: Int = -1,
    ) : VoiceState
}

@Serializable
data class OverlayState(
    val isVisible: Boolean = false,
    val isExpanded: Boolean = false,
    val tickerText: String = "",
    val isAgentRunning: Boolean = false,
    val pendingApproval: ApprovalRequest? = null,
    val voiceState: VoiceState = VoiceState.Idle,
    val isKilled: Boolean = false,
)

/**
 * Controls the `SYSTEM_ALERT_WINDOW` (`TYPE_APPLICATION_OVERLAY`) floating Status Orb,
 * live action ticker, Push-to-Talk surface, approval card, and emergency Stop button.
 */
interface OverlayController {
    val state: StateFlow<OverlayState>
    val stopRequested: SharedFlow<Unit>

    suspend fun showOrb()

    suspend fun updateTicker(text: String)

    suspend fun setAgentRunning(running: Boolean)

    suspend fun requestUserApproval(request: ApprovalRequest): Boolean

    suspend fun showKillNotice(message: String = "Ajan Durduruldu")

    suspend fun hide()
}

/**
 * Pluggable speech-to-text abstraction backing `/voice` and floating Push-to-Talk.
 * Default implementation uses Android's built-in `SpeechRecognizer`.
 */
interface VoiceTranscriber {
    val state: StateFlow<VoiceState>

    fun isAvailable(): Boolean

    suspend fun startListening(localeTag: String = "tr-TR")

    suspend fun stopListening(): String?

    suspend fun cancel()
}
