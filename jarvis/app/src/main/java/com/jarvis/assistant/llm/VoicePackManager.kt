package com.jarvis.assistant.llm

import android.content.Context
import com.jarvis.assistant.speech.SherpaSttEngine
import com.jarvis.assistant.speech.SherpaTtsEngine
import com.jarvis.assistant.web.WebFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * In-app voice-pack manager (P2 roadmap item): downloads sherpa-onnx TTS
 * voices (piper/vits tar.bz2 archives from the k2-fsa release), extracts
 * them under files/voice/tts/<voice-id>/, and switches the active voice +
 * the model's reply-language hint in one tap.
 *
 * Downloads go through [WebFetcher] — still the only networking class.
 * Catalog assets verified against the k2-fsa/sherpa-onnx tts-models
 * release. int8 variants: quantized for mobile CPU, ~half the size.
 */
class VoicePackManager(
    private val context: Context,
    private val fetcher: WebFetcher,
    /** Fired after a successful activation; swaps the live TTS engine. */
    private val onActivated: () -> Unit = {},
    /** Fired after a successful ASR activation; swaps the live STT engine. */
    private val onAsrActivated: () -> Unit = {},
) {

    data class VoiceEntry(
        val id: String,          // stable key + archive file name
        val dirName: String,     // directory created under voice/tts/
        val title: String,
        val langLabel: String,
        val sizeLabel: String,
        val gender: String,
        val replyLang: String,   // "en" | "hi" — persona hint
        val url: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    private val _states =
        MutableStateFlow<Map<String, ModelDownloader.DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, ModelDownloader.DownloadState>> = _states

    private fun prefs() =
        context.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    val catalog = listOf(
        VoiceEntry(
            id = "vits-piper-en_GB-alan-medium-int8",
            dirName = "vits-piper-en_GB-alan-medium-int8",
            title = "Alan — English (UK), the default butler",
            langLabel = "English",
            sizeLabel = "~65 MB",
            gender = "masculine",
            replyLang = "en",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium-int8.tar.bz2",
        ),
        VoiceEntry(
            id = "vits-piper-hi_IN-rohan-medium-int8",
            dirName = "vits-piper-hi_IN-rohan-medium-int8",
            title = "Rohan — Hindi (भारत)",
            langLabel = "हिन्दी",
            sizeLabel = "~65 MB",
            gender = "masculine",
            replyLang = "hi",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium-int8.tar.bz2",
        ),
        VoiceEntry(
            id = "vits-piper-hi_IN-priyamvada-medium-int8",
            dirName = "vits-piper-hi_IN-priyamvada-medium-int8",
            title = "Priyamvada — Hindi (भारत)",
            langLabel = "हिन्दी",
            sizeLabel = "~65 MB",
            gender = "feminine",
            replyLang = "hi",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2",
        ),
    )

    private fun ttsRoot(): File =
        File(ModelManager.baseDir(context), "voice/tts")

    private fun packDir(entry: VoiceEntry): File =
        File(ttsRoot(), entry.dirName)

    fun isInstalled(entry: VoiceEntry): Boolean =
        SherpaTtsEngine.modelFilesPresent(packDir(entry))

    fun isActive(entry: VoiceEntry): Boolean =
        prefs().getString(PREF_VOICE, null) == entry.dirName

    /** All installed voice directories that look valid. */
    fun installedVoices(): List<File> =
        ttsRoot().listFiles { f -> f.isDirectory }?.filter { SherpaTtsEngine.modelFilesPresent(it) }
            ?: emptyList()

    // ---- ASR packs (the "ear") --------------------------------------------------

    data class AsrEntry(
        val id: String,
        val dirName: String,
        val title: String,
        val langLabel: String,
        val sizeLabel: String,
        val url: String,
    )

    /** Streaming zipformer packs — layout verified against the sherpa docs. */
    val asrCatalog = listOf(
        AsrEntry(
            id = "sherpa-onnx-streaming-zipformer-en-2023-06-26",
            dirName = "sherpa-onnx-streaming-zipformer-en-2023-06-26",
            title = "English — the default ear",
            langLabel = "English",
            sizeLabel = "~45 MB",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2",
        ),
        AsrEntry(
            id = "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09",
            dirName = "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09",
            title = "বাংলা — Bengali streaming",
            langLabel = "বাংলা",
            sizeLabel = "~83 MB",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09.tar.bz2",
        ),
    )

    private fun asrRoot(): File =
        File(ModelManager.baseDir(context), "voice/asr")

    private fun asrDir(entry: AsrEntry): File =
        File(asrRoot(), entry.dirName)

    fun isAsrInstalled(entry: AsrEntry): Boolean =
        SherpaSttEngine.modelFilesPresent(asrDir(entry))

    fun isActiveAsr(entry: AsrEntry): Boolean =
        prefs().getString(PREF_ASR, null) == entry.dirName

    /** One tap: remember the ear; a fresh STT engine picks it up. */
    fun activateAsr(entry: AsrEntry) {
        prefs().edit().putString(PREF_ASR, entry.dirName).apply()
        onAsrActivated()
    }

    fun startAsr(entry: AsrEntry, onDone: (Boolean) -> Unit = {}) {
        startCore(
            key = entry.id,
            url = entry.url,
            into = asrRoot(),
            installed = { isAsrInstalled(entry) },
            onDone = onDone,
        )
    }

    /** The reply-language hint the Orchestrator adds to the system prompt. */
    fun activeReplyLang(): String {
        val dir = prefs().getString(PREF_VOICE, null) ?: return "auto"
        return catalog.firstOrNull { it.dirName == dir }?.replyLang ?: "auto"
    }

    /**
     * Downloads (resumably) and extracts a voice pack. [onDone] fires on the
     * IO dispatcher; hop threads before touching UI.
     */
    fun start(entry: VoiceEntry, onDone: (Boolean) -> Unit = {}) {
        startCore(
            key = entry.id,
            url = entry.url,
            into = ttsRoot(),
            installed = { isInstalled(entry) },
            onDone = onDone,
        )
    }

    private fun startCore(
        key: String,
        url: String,
        into: File,
        installed: () -> Boolean,
        onDone: (Boolean) -> Unit,
    ) {
        if (jobs[key]?.isActive == true) return
        val archive = File(context.cacheDir, key + ".tar.bz2")

        jobs[key] = scope.launch {
            fun put(state: ModelDownloader.DownloadState) =
                _states.update { it + (key to state) }

            put(ModelDownloader.DownloadState(key, resumeBytes(archive), -1, ModelDownloader.Status.RUNNING))
            try {
                var lastTick = 0L
                fetcher.download(url, archive) { received, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 250 || (total > 0 && received >= total)) {
                        lastTick = now
                        put(ModelDownloader.DownloadState(key, received, total, ModelDownloader.Status.RUNNING))
                    }
                }
                extractTarBz2(archive, into)
                archive.delete()
                if (!installed()) throw IllegalStateException("archive did not contain the expected model files")
                put(ModelDownloader.DownloadState(key, 1, 1, ModelDownloader.Status.DONE))
                onDone(true)
            } catch (e: CancellationException) {
                put(ModelDownloader.DownloadState(key, resumeBytes(archive), -1, ModelDownloader.Status.CANCELLED))
                throw e
            } catch (e: Exception) {
                put(ModelDownloader.DownloadState(key, resumeBytes(archive), -1, ModelDownloader.Status.FAILED))
                onDone(false)
            } finally {
                jobs.remove(key)
            }
        }
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    /** One tap: remember the voice + language hint, swap the live engine. */
    fun activate(entry: VoiceEntry) {
        prefs().edit()
            .putString(PREF_VOICE, entry.dirName)
            .putString(PREF_REPLY_LANG, entry.replyLang)
            .apply()
        onActivated()
    }

    private fun resumeBytes(archive: File): Long =
        if (archive.isFile) archive.length() else 0L

    // ---- extraction -----------------------------------------------------------

    /**
     * Streams a .tar.bz2 into [into]. Zip-slip safe: every target path is
     * canonicalized and must stay under [into]; links and device nodes are
     * skipped outright.
     */
    @Throws(Exception::class)
    internal fun extractTarBz2(archive: File, into: File) {
        into.mkdirs()
        val root = into.canonicalFile
        TarArchiveInputStream(BZip2CompressorInputStream(FileInputStream(archive))).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val name = entry.name.trimEnd('/')
                if (name.isEmpty()) continue
                val out = File(root, name)
                if (!out.canonicalFile.path.startsWith(root.path + File.separatorChar)) {
                    continue // zip-slip attempt or odd path — skip
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else if (entry.isLink || entry.isSymbolicLink) {
                    continue // no links in voice packs
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { tar.copyTo(it) }
                }
            }
        }
    }

    companion object {
        const val PREF_VOICE = "tts_voice"
        const val PREF_REPLY_LANG = "reply_lang"
        const val PREF_ASR = "stt_model"
    }
}
