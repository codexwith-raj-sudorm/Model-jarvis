package com.jarvis.assistant.llm

import android.content.Context
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
import java.io.File

/**
 * In-app model downloader (P0 roadmap item): a curated GGUF catalog with
 * resumable downloads and progress, ending in a warm hand-off to the active
 * slot. Downloads go through [WebFetcher] — still the only networking class.
 */
class ModelDownloader(
    private val context: Context,
    private val fetcher: WebFetcher,
    private val modelManager: ModelManager,
) {

    data class CatalogEntry(
        val fileName: String,
        val title: String,
        val sizeLabel: String,
        val ramTier: String,
        val url: String,
    )

    enum class Status { RUNNING, DONE, FAILED, CANCELLED }

    data class DownloadState(
        val fileName: String,
        val received: Long,
        val total: Long, // -1 = unknown length
        val status: Status,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states

    /** Same URLs as scripts/get_models.sh — kept in sync deliberately. */
    val catalog = listOf(
        CatalogEntry(
            fileName = "Qwen3-1.7B-Q4_K_M.gguf",
            title = "Qwen3 1.7B — recommended default",
            sizeLabel = "~1.1 GB",
            ramTier = "6 GB+ RAM",
            url = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
        ),
        CatalogEntry(
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            title = "Gemma 3 1B — budget phones",
            sizeLabel = "~0.8 GB",
            ramTier = "4–6 GB RAM",
            url = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
        ),
        CatalogEntry(
            fileName = "Qwen3-4B-Q4_K_M.gguf",
            title = "Qwen3 4B — flagships",
            sizeLabel = "~2.4 GB",
            ramTier = "8–12 GB RAM",
            url = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
        ),
    )

    fun isInstalled(entry: CatalogEntry): Boolean =
        File(modelManager.modelsDir, entry.fileName).isFile

    fun installedFile(entry: CatalogEntry): File =
        File(modelManager.modelsDir, entry.fileName)

    /**
     * Starts (or resumes) a catalog download. [onDone] fires on the IO
     * dispatcher with success/failure — hop threads before touching UI.
     */
    fun start(entry: CatalogEntry, onDone: (Boolean) -> Unit = {}) {
        if (jobs[entry.fileName]?.isActive == true) return
        val dest = File(modelManager.modelsDir, entry.fileName)
        val fileName = entry.fileName

        jobs[fileName] = scope.launch {
            fun put(state: DownloadState) =
                _states.update { it + (fileName to state) }

            put(DownloadState(fileName, resumeBytes(dest), -1, Status.RUNNING))
            try {
                var lastTick = 0L
                fetcher.download(entry.url, dest) { received, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 250 || (total > 0 && received >= total)) {
                        lastTick = now
                        put(DownloadState(fileName, received, total, Status.RUNNING))
                    }
                }
                put(DownloadState(fileName, dest.length(), dest.length(), Status.DONE))
                onDone(true)
            } catch (e: CancellationException) {
                put(DownloadState(fileName, resumeBytes(dest), -1, Status.CANCELLED))
                throw e
            } catch (e: Exception) {
                put(DownloadState(fileName, resumeBytes(dest), -1, Status.FAILED))
                onDone(false)
            } finally {
                jobs.remove(fileName)
            }
        }
    }

    fun cancel(fileName: String) {
        jobs[fileName]?.cancel()
    }

    private fun resumeBytes(dest: File): Long {
        val part = File(dest.parentFile, dest.name + ".part")
        return if (part.isFile) part.length() else 0L
    }
}
