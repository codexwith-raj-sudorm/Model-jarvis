package com.jarvis.assistant.speech

import android.content.Context
import com.jarvis.assistant.llm.VoicePackManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Fully-offline TTS: sherpa-onnx Piper/VITS, synthesized in chunks via
 * generateWithCallback and streamed into an AudioTrack as they arrive —
 * first audio starts playing long before the whole clip is synthesized.
 *
 * Concurrency model: a single-thread executor serializes utterances; each
 * speak() call mints a [Session] and aborts the previous one (barge-in).
 * Stale sessions bail at their next check, so a rapid speak→speak→stop
 * sequence can never double-fire onDone or overlap audio.
 *
 * Model layout (scripts/get_voice_models.sh):
 *   files/voice/tts/{*.onnx, tokens.txt, espeak-ng-data/, lexicon.txt?}
 */
class SherpaTtsEngine(private val context: Context) : SpeechOutput {

    private val appContext = context.applicationContext

    /** One utterance. [stopped] is the cooperative abort flag. */
    private class Session(val id: Long) {
        @Volatile
        var stopped = false
        var onDone: (() -> Unit)? = null
    }

    private var tts: OfflineTts? = null
    private val ttsLock = Any()

    private val sessionIds = AtomicLong(0)

    @Volatile
    private var active: Session? = null

    @Volatile
    private var track: android.media.AudioTrack? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sherpa-tts").apply { isDaemon = true }
    }

    override val displayName = "sherpa vits"
    override val isAvailable: Boolean = activeVoiceDir() != null

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (text.isBlank()) { onDone?.invoke(); return }

        // barge-in: abort whatever is playing, silence it, take over
        active?.stopped = true
        runCatching { track?.pause() }

        if (!isAvailable) { onDone?.invoke(); return }

        val session = Session(sessionIds.incrementAndGet()).apply { this.onDone = onDone }
        active = session

        executor.execute { synthesize(text, session) }
    }

    private fun synthesize(text: String, session: Session) {
        var localTrack: android.media.AudioTrack? = null
        try {
            if (session.stopped) return finish(session)

            val engine = obtainTts()
                ?: throw IllegalStateException("failed to create sherpa TTS engine")

            val sampleRate = engine.sampleRate()
            val minBuf = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_FLOAT,
            )
            localTrack = android.media.AudioTrack(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                (minBuf * 4).coerceAtLeast(sampleRate * 2), // ~1 s of float samples
                android.media.AudioTrack.MODE_STREAM,
                0, // audio session id
            ).also {
                track = it
                it.play()
            }

            engine.generateWithCallback(
                text = text,
                sid = 0,
                speed = SPEECH_RATE,
            ) { samples ->
                when {
                    session.stopped -> 0 // abort synthesis
                    track == null -> 0
                    else -> {
                        // float[] overload (API 23+) needs the 4-arg form
                        track!!.write(samples, 0, samples.size, android.media.AudioTrack.WRITE_BLOCKING)
                        if (session.stopped) 0 else samples.size
                    }
                }
            }
        } catch (_: Throwable) {
            // onDone must still fire exactly once (hands-free loop depends on it)
        } finally {
            // release the track on the synthesis thread — never join from UI
            runCatching { localTrack?.pause() }
            runCatching { localTrack?.flush() }
            runCatching { localTrack?.release() }
            if (track === localTrack) track = null
            finish(session)
        }
    }

    private fun finish(session: Session) {
        if (active === session) active = null
        session.onDone?.invoke()
        session.onDone = null
    }

    /** Instant barge-in: silence now, abort synthesis at the next chunk. */
    override fun stop() {
        active?.stopped = true
        runCatching { track?.pause() }
    }

    override fun shutdown() {
        active?.stopped = true
        executor.shutdown()
        val engine = synchronized(ttsLock) { tts }
        if (engine != null) {
            Thread({
                // wait for any in-flight synthesis to leave native code
                // before freeing the engine (bounded, on this release thread)
                runCatching { executor.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
                runCatching { engine.release() }
                synchronized(ttsLock) {
                    if (tts === engine) tts = null
                }
            }, "sherpa-tts-release").apply { isDaemon = true }.start()
        }
    }

    private fun obtainTts(): OfflineTts? =
        synchronized(ttsLock) {
            tts?.let { return it }
            val dir = activeVoiceDir() ?: return null
            val model = dir.listFiles { f -> f.isFile && f.name.endsWith(".onnx") }
                ?.minByOrNull { it.name } ?: return null
            val tokens = File(dir, "tokens.txt")
            if (!tokens.isFile) return null
            val espeak = File(dir, "espeak-ng-data")
            val lexicon = File(dir, "lexicon.txt")

            val vits = OfflineTtsVitsModelConfig(
                model = model.absolutePath,
                lexicon = if (lexicon.isFile) lexicon.absolutePath else "",
                tokens = tokens.absolutePath,
                dataDir = espeak.absolutePath,
            )
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(vits = vits, numThreads = 2, provider = "cpu"),
                maxNumSentences = 1, // sentence-by-sentence → lower latency to first audio
            )
            runCatching { OfflineTts(assetManager = null, config = config) }
                .getOrNull()
                ?.also { tts = it }
        }

    /**
     * Drops the cached engine so the next [speak] re-resolves the active
     * voice directory (called after a voice-pack switch). Synthesis in
     * flight keeps its own reference and finishes undisturbed.
     */
    fun invalidate() {
        val engine = synchronized(ttsLock) { tts }
        if (engine != null) {
            synchronized(ttsLock) { tts = null }
            Thread({
                runCatching { engine.release() }
            }, "sherpa-tts-invalidate").apply { isDaemon = true }.start()
        }
    }

    /**
     * Which voice directory speaks: (1) the user's explicit choice from the
     * voice-pack manager, (2) the legacy flat layout adb-pushed under
     * voice/tts/, (3) any installed pack — deterministic alphabetical order
     * so the same device always picks the same voice.
     */
    private fun activeVoiceDir(): File? {
        val root = modelDir(appContext)
        val pref = appContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getString(VoicePackManager.PREF_VOICE, null)
        if (pref != null) {
            val dir = File(root, pref)
            if (modelFilesPresent(dir)) return dir
        }
        if (modelFilesPresent(root)) return root
        return root.listFiles { f -> f.isDirectory }
            ?.filter { modelFilesPresent(it) }
            ?.minByOrNull { it.name }
    }

    companion object {
        /**
         * Voice speed — also a documented tuning knob. Slightly under 1.0:
         * a measured butler delivery beats a rushed one.
         */
        const val SPEECH_RATE = 0.95f

        fun modelDir(context: Context): File =
            File(com.jarvis.assistant.llm.ModelManager.baseDir(context), "voice/tts")

        fun modelFilesPresent(dir: File): Boolean =
            dir.isDirectory &&
                dir.listFiles { f -> f.isFile && f.name.endsWith(".onnx") }?.isNotEmpty() == true &&
                File(dir, "tokens.txt").isFile
    }
}
