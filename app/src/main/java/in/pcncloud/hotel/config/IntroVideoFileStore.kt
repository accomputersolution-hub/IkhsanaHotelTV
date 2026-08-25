package `in`.pcncloud.hotel.config

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/**
 * Downloads [introVideoUrl] into app internal storage and serves a local [file://] URI
 * for offline / API-28-friendly ExoPlayer playback.
 *
 * File: `{filesDir}/intro/intro_cached.mp4`
 * Prefs track which remote URL the file was downloaded for.
 */
class IntroVideoFileStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val introDir: File = File(appContext.filesDir, DIR_NAME).also { it.mkdirs() }
    private val cacheFile: File = File(introDir, FILE_NAME)
    private val partialFile: File = File(introDir, "$FILE_NAME.part")

    fun hasReadyFile(): Boolean =
        cacheFile.isFile && cacheFile.length() >= MIN_READY_BYTES

    fun localFileLength(): Long = if (cacheFile.isFile) cacheFile.length() else 0L

    /** Remote URL this file was downloaded for (may be blank). */
    fun downloadedForUrl(): String =
        prefs.getString(KEY_FILE_SOURCE_URL, null)?.trim().orEmpty()

    fun getLocalPlaybackUri(): Uri? {
        if (!hasReadyFile()) return null
        return Uri.fromFile(cacheFile)
    }

    /**
     * Prefer local file when ready — works offline for a configured URL.
     * Falls back to remote http(s) Uri only when no local bytes yet.
     * Blank / whitespace [remoteUrl] never returns a Uri (no orphan-file playback).
     */
    fun resolvePlaybackUri(remoteUrl: String?): Uri? {
        val http = IntroVideoCache.normalizeHttpUrl(remoteUrl?.trim().orEmpty())
        if (http.isNullOrBlank()) {
            Log.i(TAG, "playback SKIP — empty remote URL (no ExoPlayer Uri)")
            return null
        }
        val local = getLocalPlaybackUri()
        if (local != null) {
            Log.i(
                TAG,
                "playback LOCAL len=${localFileLength()} " +
                    "forUrl=${downloadedForUrl().take(64)}",
            )
            return local
        }
        Log.i(TAG, "playback REMOTE (no local file yet) prefix=${http.take(64)}")
        return IntroVideoCache.parseMediaUri(http)
    }

    /**
     * True when cold boot should open Intro: a valid non-blank remote URL is required.
     * Local file alone is not enough (admin may have disabled intro).
     */
    fun canPlayIntro(remoteUrl: String?): Boolean =
        !IntroVideoCache.normalizeHttpUrl(remoteUrl.orEmpty()).isNullOrBlank()

    /**
     * Ensures [remoteUrl] is on disk. No-op when already cached for the same URL.
     * Blank URL deletes [intro_cached.mp4] so offline cannot replay a disabled intro.
     * On URL change: replaces the local file after a successful download.
     *
     * @return true if a playable local file exists after this call.
     */
    suspend fun ensureCached(remoteUrl: String?): Boolean = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val url = IntroVideoCache.normalizeHttpUrl(remoteUrl?.trim().orEmpty())
            if (url.isNullOrBlank()) {
                if (hasReadyFile() || partialFile.exists()) {
                    Log.i(TAG, "ensureCached — blank URL, deleting local intro file")
                    clear()
                } else {
                    Log.i(TAG, "ensureCached — blank URL, no local file")
                }
                return@withLock false
            }
            val already = downloadedForUrl()
            if (url == already && hasReadyFile()) {
                Log.i(TAG, "ensureCached HIT local len=${localFileLength()}")
                return@withLock true
            }
            if (downloadInFlight.getAndSet(true)) {
                Log.d(TAG, "ensureCached — download already in flight")
                return@withLock hasReadyFile()
            }
            try {
                Log.i(
                    TAG,
                    "ensureCached DOWNLOAD start sdk=${Build.VERSION.SDK_INT} " +
                        "prev=${already.take(48)} new=${url.take(72)}",
                )
                downloadToCache(url)
                true
            } catch (e: Exception) {
                Log.e(TAG, "ensureCached DOWNLOAD failed — keep old file=${hasReadyFile()}", e)
                // Keep previous file on failure so offline still works.
                hasReadyFile()
            } finally {
                downloadInFlight.set(false)
            }
        }
    }

    fun clear() {
        runCatching { partialFile.delete() }
        runCatching { cacheFile.delete() }
        prefs.edit().remove(KEY_FILE_SOURCE_URL).commit()
        Log.i(TAG, "clear local intro file + source url")
    }

    @Throws(IOException::class)
    private fun downloadToCache(url: String) {
        partialFile.parentFile?.mkdirs()
        if (partialFile.exists()) partialFile.delete()

        val client = buildDownloadClient()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DOWNLOAD_USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for intro download")
            }
            val body = response.body ?: throw IOException("empty body")
            body.byteStream().use { input ->
                partialFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.flush()
                }
            }
        }

        val len = partialFile.length()
        if (len < MIN_READY_BYTES) {
            partialFile.delete()
            throw IOException("downloaded intro too small ($len bytes)")
        }

        if (cacheFile.exists() && !cacheFile.delete()) {
            Log.w(TAG, "could not delete old cache before replace")
        }
        if (!partialFile.renameTo(cacheFile)) {
            partialFile.copyTo(cacheFile, overwrite = true)
            partialFile.delete()
        }
        prefs.edit().putString(KEY_FILE_SOURCE_URL, url).commit()
        Log.i(TAG, "ensureCached OK len=${cacheFile.length()} url=${url.take(72)}")
    }

    private fun buildDownloadClient(): OkHttpClient {
        val legacy = Build.VERSION.SDK_INT < 30
        return OkHttpClient.Builder()
            .connectTimeout(if (legacy) 45L else 20L, TimeUnit.SECONDS)
            .readTimeout(if (legacy) 120L else 60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // large files on slow TV Wi‑Fi
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                ),
            )
            .build()
    }

    companion object {
        private const val TAG = "IntroVideoFileStore"
        private const val PREFS_NAME = "hotel_tv_intro"
        private const val KEY_FILE_SOURCE_URL = "intro_file_source_url"
        private const val DIR_NAME = "intro"
        private const val FILE_NAME = "intro_cached.mp4"
        /** Reject empty / tiny garbage downloads. */
        private const val MIN_READY_BYTES = 8_192L

        private const val DOWNLOAD_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 " +
                "HostityIntroDownload/1.0"

        private val downloadMutex = Mutex()
        private val downloadInFlight = AtomicBoolean(false)
    }
}
