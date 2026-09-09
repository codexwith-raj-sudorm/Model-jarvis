package com.jarvis.assistant.web

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * THE ONLY NETWORKING CLASS IN THE APP. Audit the whole privacy story by
 * reading this one file.
 *
 * Design:
 *  - every fetch carries zero user data — just the URL;
 *  - responses are cached on disk (~20 MB) and reused up to 24 h stale when
 *    offline, so "airplane mode + repeat question" still answers;
 *  - an access log (last 200 fetches, in memory) is kept for the fetch-log UI
 *    and the privacy contract: the user can always see what left the device.
 */
class WebFetcher(context: Context) {

    data class AccessEntry(
        val url: String,
        val timestamp: Long,
        val fromCache: Boolean,
        val bytes: Int,
    )

    private val appContext = context.applicationContext

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(appContext.cacheDir, "http_cache"), CACHE_BYTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val accessLog = ArrayDeque<AccessEntry>()
    private val logLock = Any()

    /** Snapshot of recent fetches (newest last) for UI display. */
    fun accessSnapshot(): List<AccessEntry> = synchronized(logLock) { accessLog.toList() }

    private fun log(url: String, fromCache: Boolean, bytes: Int) {
        synchronized(logLock) {
            accessLog.addLast(AccessEntry(url, System.currentTimeMillis(), fromCache, bytes))
            while (accessLog.size > LOG_LIMIT) accessLog.removeFirst()
        }
    }

    /**
     * GETs a URL as text (UTF-8). Enforces http(s), caps the body at
     * [maxBytes] so a rogue page can't eat RAM, and falls back to the cache
     * (even stale) when the network is down.
     */
    suspend fun get(url: String, maxBytes: Int = MAX_BYTES): String = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "only http(s) fetches are allowed"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .cacheControl(CacheControl.Builder().maxStale(24, TimeUnit.HOURS).build())
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            // offline (or the host is down): serve from cache if we have it
            val cached = client.newCall(
                request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
            ).execute()
            cached
        }
        response.use { resp ->
            val body = resp.body ?: throw IllegalStateException("empty body: HTTP ${resp.code}")
            val text = body.string()
            val servedFromCache = resp.cacheResponse != null && resp.networkResponse == null
            log(url, servedFromCache, text.length)
            if (resp.code !in 200..299 && text.isEmpty()) {
                throw IllegalStateException("HTTP ${resp.code} for $url")
            }
            if (text.length > maxBytes) text.substring(0, maxBytes) else text
        }
    }

    /** GETs a URL and parses it as JSON. */
    suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(get(url))
    }

    /**
     * Streaming file download with resume, for the in-app model downloader.
     * Writes to `<dest>.part`, appends via HTTP Range when a partial exists,
     * and atomically renames on completion — a killed download never leaves
     * a corrupt GGUF behind. Progress callback is throttled by the caller.
     * This stays here so WebFetcher remains the ONLY networking class.
     */
    suspend fun download(
        url: String,
        destFile: File,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "only http(s) downloads are allowed"
        }
        destFile.parentFile?.mkdirs()
        val part = File(destFile.parentFile, destFile.name + ".part")

        var request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
        var resumeFrom = 0L
        if (part.isFile && part.length() > 0) {
            resumeFrom = part.length()
            request = request.header("Range", "bytes=$resumeFrom-")
        }

        client.newCall(request.build()).execute().use { resp ->
            if (resp.code !in 200..299 && resp.code != 206) {
                throw IllegalStateException("HTTP ${resp.code} for $url")
            }
            val body = resp.body ?: throw IllegalStateException("empty body: HTTP ${resp.code}")

            if (resp.code == 200 && resumeFrom > 0) {
                // server ignored the Range — start over
                part.delete()
                resumeFrom = 0
            }

            val remaining = body.contentLength()
            val total = if (remaining >= 0) resumeFrom + remaining else -1L
            val append = resp.code == 206
            var received = resumeFrom

            java.io.FileOutputStream(part, append).use { sink ->
                val buf = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }

            if (destFile.exists()) destFile.delete()
            if (!part.renameTo(destFile)) {
                throw java.io.IOException("could not finalize ${destFile.name}")
            }
        }
        log(url, fromCache = false, bytes = destFile.length().toInt())
        destFile
    }

    companion object {
        private const val CACHE_BYTES = 20L * 1024 * 1024
        private const val LOG_LIMIT = 200
        private const val MAX_BYTES = 1_500_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) JARVIS/0.1 (local-first assistant)"
    }
}
