package com.jarvis.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * System SpeechRecognizer fallback — zero-setup STT when the sherpa model
 * isn't installed. Prefers the on-device recognizer (EXTRA_PREFER_OFFLINE)
 * where the platform supports it.
 *
 * SpeechRecognizer demands the main thread, so everything is dispatched via
 * a main-thread handler; stop/destroy stay non-blocking for the caller.
 */
class SttEngine(context: Context) : VoiceInput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    override val displayName = "system STT"
    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        main.post {
            if (!isAvailable) {
                onError("no speech recognition service on this device")
                return@post
            }
            stopQuietly()

            val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString(" ") { it.trim() }
                        ?.trim().orEmpty()
                    if (text.isNotEmpty()) onPartial(text)
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) onFinal(text) else onError("nothing heard")
                    destroyQuietly()
                }

                override fun onError(error: Int) {
                    onError(humanError(error))
                    destroyQuietly()
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onReadyForSpeech(params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            sr.startListening(intent)
        }
    }

    override fun stopListening() {
        main.post { stopQuietly() }
    }

    override fun shutdown() {
        main.post { destroyQuietly() }
    }

    private fun stopQuietly() {
        runCatching { recognizer?.stopListening() }
    }

    private fun destroyQuietly() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun humanError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "nothing heard"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech detected"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission missing"
        SpeechRecognizer.ERROR_AUDIO -> "audio recording problem"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy — try again"
        else -> "speech error $code"
    }
}
