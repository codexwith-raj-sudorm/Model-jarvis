package com.jarvis.assistant.llm

import android.content.Context
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
        if (jobs[entry.id]?.isActive == true) return
        val archive = File(context.cacheDir, entry.id + ".tar.bz2")

        jobs[entry.id] = scope.launch {
            fun put(state: ModelDownloader.DownloadState) =
                _states.update { it + (entry.id to state) }

            put(ModelDownloader.DownloadState(entry.id, resumeBytes(archive), -1, ModelDownloader.Status.RUNNING))
            try {
                var lastTick = 0L
                fetcher.download(entry.url, archive) { received, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 250 || (total > 0 && received >= total)) {
                        lastTick = now
                        put(ModelDownloader.DownloadState(entry.id, received, total, ModelDownloader.Status.RUNNING))
                    }
                }
                extractTarBz2(archive, ttsRoot())
                archive.delete()
                if (!isInstalled(entry)) throw IllegalStateException("archive did not contain a voice at ${entry.dirName}")
                put(ModelDownloader.DownloadState(entry.id, 1, 1, ModelDownloader.Status.DONE))
                onDone(true)
            } catch (e: CancellationException) {
                put(ModelDownloader.DownloadState(entry.id, resumeBytes(archive), -1, ModelDownloader.Status.CANCELLED))
                throw e
            } catch (e: Exception) {
                put(ModelDownloader.DownloadState(entry.id, resumeBytes(archive), -1, ModelDownloader.Status.FAILED))
                onDone(false)
            } finally {
                jobs.remove(entry.id)
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
    }
}
