package com.antigravity.mobile.feature.overlay

import android.speech.SpeechRecognizer
import com.antigravity.mobile.contracts.VoiceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSpeechVoiceTranscriberTest {

    private class FakeSpeechRecognizerBackend(
        var available: Boolean = true,
    ) : SpeechRecognizerBackend {
        var lastLocaleTag: String? = null
        var callback: SpeechRecognizerCallback? = null
        var stopCalledCount = 0
        var cancelCalledCount = 0
        var destroyCalledCount = 0

        override fun isRecognitionAvailable(): Boolean = available

        override fun start(localeTag: String, callback: SpeechRecognizerCallback) {
            lastLocaleTag = localeTag
            this.callback = callback
        }

        override fun stop() {
            stopCalledCount += 1
        }

        override fun cancel() {
            cancelCalledCount += 1
        }

        override fun destroy() {
            destroyCalledCount += 1
        }
    }

    @Test
    fun `full Turkish dictation lifecycle transitions Idle to Listening to Processing to Result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeSpeechRecognizerBackend(available = true)
        val transcriber = AndroidSpeechVoiceTranscriber(
            backend = backend,
            mainDispatcher = dispatcher,
            stopResultWaitMs = 1_000L,
        )

        assertTrue(transcriber.isAvailable())
        assertEquals(VoiceState.Idle, transcriber.state.value)

        transcriber.startListening("tr-TR")
        assertEquals("tr-TR", backend.lastLocaleTag)
        assertTrue(transcriber.state.value is VoiceState.Listening)

        backend.callback?.onRmsChanged(6.5f)
        backend.callback?.onPartialResult("Merhaba Antigravity")
        val listeningState = transcriber.state.value as VoiceState.Listening
        assertEquals("Merhaba Antigravity", listeningState.partialText)
        assertEquals(6.5f, listeningState.rmsDb, 0.01f)

        backend.callback?.onEndOfSpeech()
        assertEquals(VoiceState.Processing("Merhaba Antigravity"), transcriber.state.value)

        val stopDeferred = async { transcriber.stopListening() }
        runCurrent()
        backend.callback?.onFinalResult("Merhaba Antigravity sistemi kontrol et")
        val transcript = stopDeferred.await()

        assertEquals("Merhaba Antigravity sistemi kontrol et", transcript)
        assertEquals(
            VoiceState.Result(
                transcript = "Merhaba Antigravity sistemi kontrol et",
                localeTag = "tr-TR",
            ),
            transcriber.state.value,
        )
        assertEquals(1, backend.destroyCalledCount)
    }

    @Test
    fun `English locale en-US is forwarded and stored in result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeSpeechRecognizerBackend(available = true)
        val transcriber = AndroidSpeechVoiceTranscriber(
            backend = backend,
            mainDispatcher = dispatcher,
        )

        transcriber.startListening("en-US")
        assertEquals("en-US", backend.lastLocaleTag)

        backend.callback?.onFinalResult("Check battery status")
        assertEquals(
            VoiceState.Result(transcript = "Check battery status", localeTag = "en-US"),
            transcriber.state.value,
        )
        assertEquals("Check battery status", transcriber.stopListening())
    }

    @Test
    fun `stopListening falls back to latest partial text if final callback times out`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeSpeechRecognizerBackend(available = true)
        val transcriber = AndroidSpeechVoiceTranscriber(
            backend = backend,
            mainDispatcher = dispatcher,
            stopResultWaitMs = 500L,
        )

        transcriber.startListening("tr-TR")
        backend.callback?.onPartialResult("Kısmi metin")

        val stopDeferred = async { transcriber.stopListening() }
        advanceTimeBy(600L)
        runCurrent()

        val result = stopDeferred.await()
        assertEquals("Kısmi metin", result)
        assertEquals(1, backend.stopCalledCount)
        assertEquals(1, backend.destroyCalledCount)
        assertEquals(
            VoiceState.Result(transcript = "Kısmi metin", localeTag = "tr-TR"),
            transcriber.state.value,
        )
    }

    @Test
    fun `cancel tears down recognizer, resets state to Idle, and ignores stale callbacks`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeSpeechRecognizerBackend(available = true)
        val transcriber = AndroidSpeechVoiceTranscriber(
            backend = backend,
            mainDispatcher = dispatcher,
        )

        transcriber.startListening("tr-TR")
        val staleCallback = backend.callback!!
        transcriber.cancel()

        assertEquals(VoiceState.Idle, transcriber.state.value)
        assertEquals(1, backend.cancelCalledCount)
        assertEquals(1, backend.destroyCalledCount)

        // Late callback from cancelled session must be ignored
        staleCallback.onFinalResult("Eski oturum metni")
        assertEquals(VoiceState.Idle, transcriber.state.value)
    }

    @Test
    fun `unavailable recognizer emits VoiceState Error immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeSpeechRecognizerBackend(available = false)
        val transcriber = AndroidSpeechVoiceTranscriber(
            backend = backend,
            mainDispatcher = dispatcher,
        )

        assertFalse(transcriber.isAvailable())
        transcriber.startListening("tr-TR")
        assertTrue(transcriber.state.value is VoiceState.Error)
        assertNull(backend.callback)
    }

    @Test
    fun `recognizer error transitions to VoiceState Error and unblocks stopListening`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backend = FakeSpeechRecognizerBackend(available = true)
        val transcriber = AndroidSpeechVoiceTranscriber(
            backend = backend,
            mainDispatcher = dispatcher,
        )

        transcriber.startListening("tr-TR")
        val stopDeferred = async { transcriber.stopListening() }
        runCurrent()

        backend.callback?.onError(
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            "Mikrofon izni (RECORD_AUDIO) eksik",
        )
        assertNull(stopDeferred.await())

        val errorState = transcriber.state.value as VoiceState.Error
        assertEquals(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, errorState.errorCode)
        assertTrue(errorState.message.contains("RECORD_AUDIO"))
    }
}
