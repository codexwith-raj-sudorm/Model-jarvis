package com.jarvis.assistant.wake

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * "Hey JARVIS" detection, two drivers:
 *
 *  1. [KwsWakeDriver] (preferred) — sherpa's bilingual KWS zipformer (3M
 *     params, int8, ≈4.4 MB) with phoneme-based custom keywords. "JARVIS"
 *     needs zero training: CMU phonemes `JARVIS @JH @AA1 @R @V @AH0 @S` are
 *     installed as keywords.txt. If detection is flaky, edit the phonemes or
 *     the threshold below; delete keywords.txt to let JARVIS regenerate the
 *     default.
 *
 *  2. [AsrPhraseWakeDriver] (fallback) — reuses the streaming ASR model and
 *     fires on the phrase "hey jarvis" appearing in the live transcript.
 *     Heavier on battery; used only when no KWS model is installed.
 *
 * Both drivers stop non-blocking: the mic loop flips an atomic and cleans up
 * on its own thread (Session 11 rules).
 */
interface WakeDriver {
    val name: String
    val available: Boolean

    /** Starts listening for the wake phrase. Fires [onDetected] at most once per detection. */
    fun start(onDetected: () -> Unit, onError: (String) -> Unit)

    /** Stops listening; non-blocking. */
    fun stop()
}

/** Shared 16 kHz mic loop feeding float chunks to a consumer. */
private class MicLoop {
    @Volatile
    var running = false
        private set

    private var thread: Thread? = null

    fun start(context: Context, onChunk: (FloatArray) -> Unit, onError: (String) -> Unit) {
        if (running) return
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onError("microphone permission missing")
            return
        }
        running = true
        thread = Thread({
            var record: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(CHUNK * 2),
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("microphone unavailable")
                }
                record.startRecording()
                val shorts = ShortArray(CHUNK)
                val floats = FloatArray(CHUNK)
                while (running) {
                    val n = record.read(shorts, 0, CHUNK)
                    if (n <= 0) continue
                    for (i in 0 until n) floats[i] = shorts[i] / 32768f
                    onChunk(floats.copyOf(n))
                }
            } catch (e: Throwable) {
                if (running) { running = false; onError(e.message ?: "wake mic failed") }
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
            }
        }, "wake-mic").apply { isDaemon = true }.start()
    }

    fun stop() { running = false }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK = 1600 // 100 ms
    }
}

/**
 * KWS driver. Model layout (scripts/get_voice_models.sh):
 *   files/voice/wake/{encoder-*.onnx, decoder-*.onnx, joiner-*.onnx, tokens.txt}
 * plus a generated keywords.txt.
 */
class KwsWakeDriver(private val context: Context) : WakeDriver {

    override val name = "kws (hey jarvis)"

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private val mic = MicLoop()

    override val available: Boolean
        get() {
            val dir = wakeDir(context)
            return dir.isDirectory &&
                dir.firstOnnx("encoder") != null &&
                dir.firstOnnx("decoder") != null &&
                File(dir, "tokens.txt").isFile
        }

    override fun start(onDetected: () -> Unit, onError: (String) -> Unit) {
        if (!available) { onError("KWS model not installed"); return }
        try {
            val dir = wakeDir(context)
            val keywordsFile = ensureKeywordsFile(dir)
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = MicLoop.SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = dir.firstOnnx("encoder")!!.absolutePath,
                        decoder = dir.firstOnnx("decoder")!!.absolutePath,
                        joiner = dir.firstOnnx("joiner")?.absolutePath ?: "",
                    ),
                    tokens = File(dir, "tokens.txt").absolutePath,
                    numThreads = 1, // 3M-param int8 model — one thread is plenty
                    provider = "cpu",
                    modelType = "zipformer2",
                ),
                keywordsFile = keywordsFile.absolutePath,
                keywordsScore = KEYWORDS_SCORE,
                keywordsThreshold = KEYWORDS_THRESHOLD,
                numTrailingBlanks = 2,
            )
            val kws = KeywordSpotter(assetManager = null, config = config)
            spotter = kws
            stream = kws.createStream("")

            mic.start(context, { chunk ->
                val s = stream ?: return@start
                val sp = spotter ?: return@start
                s.acceptWaveform(chunk, MicLoop.SAMPLE_RATE)
                while (sp.isReady(s)) sp.decode(s)
                val keyword = sp.getResult(s).keyword
                if (keyword.contains("JARVIS", ignoreCase = true)) {
                    sp.reset(s) // consume the detection, keep listening
                    onDetected()
                }
            }, onError)
        } catch (e: Exception) {
            onError(e.message ?: "KWS failed to start")
        }
    }

    override fun stop() {
        mic.stop()
        val kws = spotter
        val s = stream
        spotter = null
        stream = null
        if (kws != null || s != null) {
            Thread({
                runCatching { s?.release() }
                runCatching { kws?.release() }
            }, "kws-release").apply { isDaemon = true }.start()
        }
    }

    private fun ensureKeywordsFile(dir: File): File {
        val f = File(dir, "keywords.txt")
        if (!f.isFile) f.writeText(DEFAULT_KEYWORDS)
        return f
    }

    companion object {
        /** CMU phoneme spelling — tweak here if detection is flaky. */
        const val DEFAULT_KEYWORDS = "JARVIS @JH @AA1 @R @V @AH0 @S"

        // Tuning knobs surfaced in the README tuning table.
        const val KEYWORDS_SCORE = 1.4f
        const val KEYWORDS_THRESHOLD = 0.25f

        fun wakeDir(context: Context): File = File(context.filesDir, "voice/wake")

        private fun File.firstOnnx(prefix: String): File? =
            listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".onnx") }
                ?.minByOrNull { it.name }
    }
}

/**
 * ASR-phrase fallback driver: fires when "hey jarvis" / "jarvis" shows up in
 * the live transcript of the streaming STT model. Works with zero extra
 * downloads when the ASR model is already installed.
 */
class AsrPhraseWakeDriver(private val context: Context) : WakeDriver {

    override val name = "asr-phrase (hey jarvis)"

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private val mic = MicLoop()

    override val available: Boolean
        get() = com.jarvis.assistant.speech.SherpaSttEngine
            .modelFilesPresent(com.jarvis.assistant.speech.SherpaSttEngine.modelDir(context))

    override fun start(onDetected: () -> Unit, onError: (String) -> Unit) {
        if (!available) { onError("ASR model not installed"); return }
        try {
            val dir = com.jarvis.assistant.speech.SherpaSttEngine.modelDir(context)
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = MicLoop.SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = dir.listFiles { f -> f.name.startsWith("encoder") && f.name.endsWith(".onnx") }!!
                            .minByOrNull { it.name }!!.absolutePath,
                        decoder = dir.listFiles { f -> f.name.startsWith("decoder") && f.name.endsWith(".onnx") }!!
                            .minByOrNull { it.name }!!.absolutePath,
                        joiner = dir.listFiles { f -> f.name.startsWith("joiner") && f.name.endsWith(".onnx") }
                            ?.minByOrNull { it.name }?.absolutePath ?: "",
                    ),
                    tokens = File(dir, "tokens.txt").absolutePath,
                    numThreads = 1,
                    provider = "cpu",
                    modelType = "zipformer2",
                ),
                enableEndpoint = false, // we listen forever, no turn-taking
                decodingMethod = "greedy_search",
            )
            val rec = OnlineRecognizer(assetManager = null, config = config)
            recognizer = rec
            stream = rec.createStream()

            mic.start(context, { chunk ->
                val s = stream ?: return@start
                val r = recognizer ?: return@start
                s.acceptWaveform(chunk, MicLoop.SAMPLE_RATE)
                while (r.isReady(s)) r.decode(s)
                val text = r.getResult(s).text.lowercase()
                if (PHRASES.any { it in text }) {
                    r.reset(s) // fresh transcript for the next detection
                    onDetected()
                }
            }, onError)
        } catch (e: Exception) {
            onError(e.message ?: "ASR wake failed to start")
        }
    }

    override fun stop() {
        mic.stop()
        val rec = recognizer
        val s = stream
        recognizer = null
        stream = null
        if (rec != null || s != null) {
            Thread({
                runCatching { s?.release() }
                runCatching { rec?.release() }
            }, "asr-wake-release").apply { isDaemon = true }.start()
        }
    }

    companion object {
        private val PHRASES = listOf("hey jarvis", "hey jervis", "jarvis")
    }
}

/** Picks the best available driver: KWS when its model exists, else ASR-phrase. */
fun bestWakeDriver(context: Context): WakeDriver? {
    val kws = KwsWakeDriver(context)
    if (kws.available) return kws
    val asr = AsrPhraseWakeDriver(context)
    if (asr.available) return asr
    return null
}
