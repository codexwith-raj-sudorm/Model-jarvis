package com.jarvis.assistant.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fully-offline streaming STT: AudioRecord 16 kHz → sherpa-onnx streaming
 * zipformer with endpoint-detected turn taking and live partials.
 *
 * Threading (Session 11):
 *  - all heavy work (recognizer creation, mic reads, decode) happens on one
 *    worker thread owned by the listening session;
 *  - stop/shutdown never join the worker from the caller — they flip
 *    [running] and the worker cleans up in its own `finally` block;
 *  - mic-open gets a 3×200 ms handoff-grace retry because the wake service
 *    may still be releasing the microphone when the overlay opens.
 *
 * Model layout (installed by scripts/get_voice_models.sh):
 *   files/voice/asr/{encoder-*.onnx, decoder-*.onnx, joiner-*.onnx, tokens.txt}
 */
class SherpaSttEngine(private val context: Context) : VoiceInput {

    private val appContext = context.applicationContext

    private var recognizer: OnlineRecognizer? = null
    private val recognizerLock = Any()

    private val running = AtomicBoolean(false)

    override val displayName = "sherpa zipformer"
    override val isAvailable: Boolean = modelFilesPresent(modelDir(appContext))

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!running.compareAndSet(false, true)) return // already listening

        if (appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            running.set(false)
            onError("microphone permission missing")
            return
        }
        if (!isAvailable) {
            running.set(false)
            onError("sherpa STT model not installed (run scripts/get_voice_models.sh)")
            return
        }

        Thread({
            var record: android.media.AudioRecord? = null
            var stream: OnlineStream? = null
            try {
                val rec = obtainRecognizer()
                    ?: throw IllegalStateException("failed to create sherpa recognizer")
                stream = rec.createStream()

                record = openMicWithGrace()
                    ?: throw IllegalStateException("microphone busy or unavailable")

                val buffer = ShortArray(CHUNK_SAMPLES)
                val floats = FloatArray(CHUNK_SAMPLES)
                val startAt = android.os.SystemClock.elapsedRealtime()

                while (running.get()) {
                    val n = record.read(buffer, 0, CHUNK_SAMPLES)
                    if (n <= 0) break

                    for (i in 0 until n) floats[i] = buffer[i] / 32768f
                    stream.acceptWaveform(floats.copyOf(n), SAMPLE_RATE)

                    while (rec.isReady(stream)) rec.decode(stream)

                    val partial = rec.getResult(stream).text.trim()
                    if (partial.isNotEmpty()) onPartial(partial)

                    val elapsed = android.os.SystemClock.elapsedRealtime() - startAt
                    if (rec.isEndpoint(stream) || elapsed >= MAX_UTTERANCE_MS) {
                        val finalText = rec.getResult(stream).text.trim()
                        if (finalText.isNotEmpty()) {
                            onFinal(finalText)
                            break // one endpoint-detected turn per session
                        }
                        rec.reset(stream) // silence so far — keep listening
                    }
                }
            } catch (e: Throwable) {
                if (running.getAndSet(false)) onError(e.message ?: "STT failed")
                return
            } finally {
                // cleanup on the worker's OWN thread — never join from UI
                runCatching { record?.stop() }
                runCatching { record?.release() }
                runCatching { stream?.release() }
                running.set(false)
            }
        }, "sherpa-stt").apply { isDaemon = true }.start()
    }

    @Volatile
    private var worker: Thread? = null

    override fun stopListening() {
        running.set(false) // worker exits at its next 100 ms read
    }

    override fun shutdown() {
        running.set(false)
        val rec = synchronized(recognizerLock) { recognizer }
        if (rec != null) {
            Thread({
                // bounded join on this release thread (never the UI thread):
                // the worker may be inside native decode() right now — freeing
                // the recognizer under it would be a use-after-free
                runCatching { worker?.join(500) }
                runCatching { rec.release() }
                synchronized(recognizerLock) {
                    if (recognizer === rec) recognizer = null
                }
            }, "sherpa-stt-release").apply { isDaemon = true }.start()
        }
    }

    // ---- recognizer ---------------------------------------------------------

    private fun obtainRecognizer(): OnlineRecognizer? =
        synchronized(recognizerLock) {
            recognizer?.let { return it }
            val dir = modelDir(appContext)
            val encoder = dir.firstOnnx("encoder") ?: return null
            val decoder = dir.firstOnnx("decoder") ?: return null
            val joiner = dir.firstOnnx("joiner") ?: return null
            val tokens = File(dir, "tokens.txt")
            if (!tokens.isFile) return null

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                    modelType = "zipformer2",
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0.0f),
                    rule2 = EndpointRule(mustContainNonSilence = true,  minTrailingSilence = 1.2f, minUtteranceLength = 0.0f),
                    rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0.0f, minUtteranceLength = 20.0f),
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )
            runCatching { OnlineRecognizer(assetManager = null, config = config) }
                .getOrNull()
                ?.also { recognizer = it }
        }

    /**
     * Opens the mic, retrying up to 3× with 200 ms pauses — the wake service
     * may still hold the AudioRecord when it hands off to the overlay.
     */
    @SuppressLint("MissingPermission") // checked by caller before start
    private fun openMicWithGrace(): android.media.AudioRecord? {
        repeat(3) { attempt ->
            runCatching {
                val record = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    micBufferBytes(),
                )
                if (record.state == android.media.AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                    if (record.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                        return record
                    }
                    record.release()
                }
            }
            if (attempt < 2) Thread.sleep(200)
        }
        return null
    }

    private fun micBufferBytes(): Int =
        (CHUNK_SAMPLES * 4).coerceAtLeast(android.media.AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
        ))

    private fun File.firstOnnx(prefix: String): File? =
        listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".onnx") }
            ?.minByOrNull { it.name }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_SAMPLES = 1600          // 100 ms
        const val MAX_UTTERANCE_MS = 20_000L

        fun modelDir(context: Context): File =
            File(com.jarvis.assistant.llm.ModelManager.baseDir(context), "voice/asr")

        fun modelFilesPresent(dir: File): Boolean =
            dir.isDirectory &&
                dir.firstOnnxStatic("encoder") != null &&
                dir.firstOnnxStatic("decoder") != null &&
                File(dir, "tokens.txt").isFile

        private fun File.firstOnnxStatic(prefix: String): File? =
            listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".onnx") }
                ?.minByOrNull { it.name }
    }
}
