package com.jarvis.assistant.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.concurrent.atomic.AtomicLong

/**
 * System TextToSpeech fallback. QUEUE_FLUSH gives instant barge-in on a new
 * speak(); stop() silences immediately; shutdown() happens on a daemon
 * thread so the caller (UI) never waits on the engine (Session 11 pattern).
 *
 * All pending-callback bookkeeping is funneled through the main handler so
 * binder-thread listener callbacks and UI-thread calls can't race; onDone
 * fires exactly once whether the utterance finishes, is flushed or fails.
 */
class AndroidTtsEngine(context: Context) : SpeechOutput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    private val utteranceIds = AtomicLong(0)

    /** Set on the main thread, fired on the main thread. */
    private var pendingDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = java.util.Locale.getDefault()
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) = postFireDone()
            override fun onError(utteranceId: String?) = postFireDone()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = postFireDone()
            override fun onStart(utteranceId: String?) {}
            private fun postFireDone() {
                main.post { fireDone() }
            }
        })
    }

    override val displayName = "system TTS"

    override val isAvailable: Boolean get() = ready

    override fun speak(text: String, onDone: (() -> Unit)?) {
        main.post {
            val engine = tts
            if (engine == null || !ready) { onDone?.invoke(); return@post }
            fireDone() // flush any previous utterance's callback first
            pendingDone = onDone
            val params = Bundle()
            val id = "jarvis-${utteranceIds.incrementAndGet()}"
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            if (result != TextToSpeech.SUCCESS) {
                fireDone()
            }
        }
    }

    override fun stop() {
        main.post {
            runCatching { tts?.stop() }
            fireDone() // stop() may not route to onStop for flushed utterances
        }
    }

    override fun shutdown() {
        main.post {
            fireDone()
            val engine = tts
            tts = null
            ready = false
            if (engine != null) {
                Thread({
                    runCatching { engine.shutdown() }
                }, "tts-release").apply { isDaemon = true }.start()
            }
        }
    }

    private fun fireDone() {
        val cb = pendingDone
        pendingDone = null
        cb?.invoke()
    }
}
