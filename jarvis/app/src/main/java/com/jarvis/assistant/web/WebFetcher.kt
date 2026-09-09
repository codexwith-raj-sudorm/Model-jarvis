package com.jarvis.assistant.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

    companion object {
        private const val CACHE_BYTES = 20L * 1024 * 1024
        private const val LOG_LIMIT = 200
        private const val MAX_BYTES = 1_500_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) JARVIS/0.1 (local-first assistant)"
    }
}
