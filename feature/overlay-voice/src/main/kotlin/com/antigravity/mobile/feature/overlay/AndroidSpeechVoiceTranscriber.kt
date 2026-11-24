package com.antigravity.mobile.feature.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.antigravity.mobile.contracts.VoiceState
import com.antigravity.mobile.contracts.VoiceTranscriber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Callback interface decoupling Android's [SpeechRecognizer] callbacks from state management
 * for deterministic lifecycle management and unit testing.
 */
interface SpeechRecognizerCallback {
    fun onReadyForSpeech()
    fun onRmsChanged(rmsdB: Float)
    fun onPartialResult(text: String)
    fun onEndOfSpeech()
    fun onFinalResult(text: String)
    fun onError(errorCode: Int, message: String)
}

/**
 * Platform abstraction around Android's built-in [SpeechRecognizer].
 * Implementations must operate strictly with Application context and never retain an Activity reference.
 */
interface SpeechRecognizerBackend {
    fun isRecognitionAvailable(): Boolean
    fun start(localeTag: String, callback: SpeechRecognizerCallback)
    fun stop()
    fun cancel()
    fun destroy()
}

/**
 * Default [SpeechRecognizerBackend] backed by Android's built-in [SpeechRecognizer].
 * Always retains `context.applicationContext` to guarantee zero Activity context leaks.
 */
class PlatformSpeechRecognizerBackend(
    context: Context,
) : SpeechRecognizerBackend {

    private val appContext: Context = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    override fun start(localeTag: String, callback: SpeechRecognizerCallback) {
        destroy()
        val instance = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = instance
        instance.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    callback.onReadyForSpeech()
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    callback.onRmsChanged(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    callback.onEndOfSpeech()
                }

                override fun onError(error: Int) {
                    callback.onError(error, mapSpeechErrorMessage(error))
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    callback.onFinalResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotEmpty()) {
                        callback.onPartialResult(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        instance.startListening(intent)
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
    }

    override fun destroy() {
        recognizer?.let { current ->
            runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
        recognizer = null
    }
}

internal fun mapSpeechErrorMessage(errorCode: Int): String = when (errorCode) {
    SpeechRecognizer.ERROR_AUDIO -> "Ses kaydı hatası oluştu"
    SpeechRecognizer.ERROR_CLIENT -> "Konuşma tanıyıcı istemci hatası"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni (RECORD_AUDIO) eksik"
    SpeechRecognizer.ERROR_NETWORK -> "Ağ bağlantısı hatası"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ağ zaman aşımı"
    SpeechRecognizer.ERROR_NO_MATCH -> "Konuşma algılanamadı"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Konuşma tanıyıcı meşgul"
    SpeechRecognizer.ERROR_SERVER -> "Konuşma sunucusu hatası"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses girişi zaman aşımına uğradı"
    else -> "Bilinmeyen ses tanıma hatası ($errorCode)"
}

/**
 * Default [VoiceTranscriber] implementation using Android's built-in [SpeechRecognizer].
 *
 * Supports Turkish (`tr-TR`) and English (`en-US`) dictation with live partial results,
 * clean teardown on stop/cancel, and strict Application-context lifecycle safety.
 */
@Singleton
class AndroidSpeechVoiceTranscriber internal constructor(
    private val backend: SpeechRecognizerBackend,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val stopResultWaitMs: Long = 2_000L,
) : VoiceTranscriber {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        backend = PlatformSpeechRecognizerBackend(context.applicationContext),
        mainDispatcher = Dispatchers.Main.immediate,
    )

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val lock = Any()
    private var activeLocaleTag: String = DEFAULT_LOCALE_TR
    private var latestPartialText: String = ""
    private var latestFinalResult: String? = null
    private var pendingFinalDeferred: CompletableDeferred<String?>? = null
    private var sessionToken: Long = 0L

    override fun isAvailable(): Boolean = backend.isRecognitionAvailable()

    override suspend fun startListening(localeTag: String) {
        val normalizedLocale = localeTag.ifBlank { DEFAULT_LOCALE_TR }
        if (!isAvailable()) {
            _state.value = VoiceState.Error(
                message = "Bu cihazda yerleşik konuşma tanıma servisi kullanılamıyor",
                errorCode = SpeechRecognizer.ERROR_CLIENT,
            )
            return
        }

        val token = synchronized(lock) {
            pendingFinalDeferred?.complete(null)
            sessionToken += 1
            activeLocaleTag = normalizedLocale
            latestPartialText = ""
            latestFinalResult = null
            pendingFinalDeferred = CompletableDeferred()
            sessionToken
        }

        _state.value = VoiceState.Listening(partialText = "", rmsDb = 0f)

        try {
            withContext(mainDispatcher) {
                backend.start(
                    localeTag = normalizedLocale,
                    callback = createCallback(token),
                )
            }
        } catch (ce: CancellationException) {
            withContext(mainDispatcher) {
                backend.destroy()
            }
            throw ce
        } catch (t: Throwable) {
            synchronized(lock) {
                if (sessionToken == token) {
                    pendingFinalDeferred?.complete(null)
                    pendingFinalDeferred = null
                }
            }
            _state.value = VoiceState.Error(
                message = t.message ?: "Konuşma tanıma başlatılamadı",
                errorCode = SpeechRecognizer.ERROR_CLIENT,
            )
        }
    }

    override suspend fun stopListening(): String? {
        val (currentState, deferred, fallbackPartial) = synchronized(lock) {
            Triple(_state.value, pendingFinalDeferred, latestPartialText)
        }

        if (currentState is VoiceState.Result) {
            return currentState.transcript
        }

        if (currentState is VoiceState.Listening) {
            _state.value = VoiceState.Processing(partialText = currentState.partialText)
            withContext(mainDispatcher) {
                backend.stop()
            }
        }

        val resolved = try {
            if (deferred != null) {
                withTimeoutOrNull(stopResultWaitMs) { deferred.await() }
            } else {
                null
            }
        } catch (ce: CancellationException) {
            withContext(mainDispatcher) {
                backend.destroy()
            }
            throw ce
        }

        val finalText = resolved?.takeIf { it.isNotBlank() }
            ?: latestFinalResult?.takeIf { it.isNotBlank() }
            ?: fallbackPartial.takeIf { it.isNotBlank() }

        withContext(mainDispatcher) {
            backend.destroy()
        }

        if (finalText != null && _state.value !is VoiceState.Result) {
            _state.value = VoiceState.Result(
                transcript = finalText,
                localeTag = activeLocaleTag,
            )
        } else if (finalText == null && _state.value is VoiceState.Processing) {
            _state.value = VoiceState.Idle
        }

        return finalText
    }

    override suspend fun cancel() {
        synchronized(lock) {
            sessionToken += 1
            latestPartialText = ""
            latestFinalResult = null
            pendingFinalDeferred?.complete(null)
            pendingFinalDeferred = null
        }
        withContext(mainDispatcher) {
            backend.cancel()
            backend.destroy()
        }
        _state.value = VoiceState.Idle
    }

    /**
     * Releases any native [SpeechRecognizer] resources during service teardown.
     */
    suspend fun release() {
        cancel()
    }

    private fun createCallback(token: Long): SpeechRecognizerCallback =
        object : SpeechRecognizerCallback {
            override fun onReadyForSpeech() {
                if (!isCurrentSession(token)) return
                val partial = synchronized(lock) { latestPartialText }
                _state.value = VoiceState.Listening(partialText = partial, rmsDb = 0f)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isCurrentSession(token)) return
                val current = _state.value
                if (current is VoiceState.Listening) {
                    _state.value = current.copy(rmsDb = rmsdB)
                }
            }

            override fun onPartialResult(text: String) {
                if (!isCurrentSession(token)) return
                val currentRms = (_state.value as? VoiceState.Listening)?.rmsDb ?: 0f
                synchronized(lock) {
                    latestPartialText = text
                }
                _state.value = VoiceState.Listening(partialText = text, rmsDb = currentRms)
            }

            override fun onEndOfSpeech() {
                if (!isCurrentSession(token)) return
                val partial = synchronized(lock) { latestPartialText }
                _state.value = VoiceState.Processing(partialText = partial)
            }

            override fun onFinalResult(text: String) {
                val (locale, deferred) = synchronized(lock) {
                    if (sessionToken != token) return
                    val effective = text.ifBlank { latestPartialText }
                    latestFinalResult = effective
                    val d = pendingFinalDeferred
                    pendingFinalDeferred = null
                    Pair(activeLocaleTag, d)
                }
                val finalText = text.ifBlank { latestPartialText }
                if (finalText.isNotBlank()) {
                    _state.value = VoiceState.Result(
                        transcript = finalText,
                        localeTag = locale,
                    )
                } else {
                    _state.value = VoiceState.Idle
                }
                deferred?.complete(finalText.ifBlank { null })
            }

            override fun onError(errorCode: Int, message: String) {
                val deferred = synchronized(lock) {
                    if (sessionToken != token) return
                    val d = pendingFinalDeferred
                    pendingFinalDeferred = null
                    d
                }
                _state.value = VoiceState.Error(
                    message = message,
                    errorCode = errorCode,
                )
                deferred?.complete(null)
            }
        }

    private fun isCurrentSession(token: Long): Boolean = synchronized(lock) {
        sessionToken == token
    }

    companion object {
        const val DEFAULT_LOCALE_TR = "tr-TR"
        const val LOCALE_EN_US = "en-US"
    }
}
