package `in`.pcncloud.hotel.config

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Local source of truth for cold-boot intro playback (URL prefs).
 *
 * Binary MP4 bytes live in [IntroVideoFileStore] (`intro_cached.mp4`) for offline play.
 * Admin / Firestore updates write the URL here; a background download fills the file.
 */
class IntroVideoCache(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val fileStore = IntroVideoFileStore(appContext)

    /** Raw cached string (may be blank). Sync read. */
    fun getUrl(): String =
        prefs.getString(KEY_INTRO_VIDEO_URL, null)?.trim().orEmpty()

    /**
     * Valid http(s) URL for immediate playback, or null → start at Home.
     * Blank / whitespace-only prefs never count as a configured intro.
     */
    fun getValidHttpUrl(): String? {
        val raw = getUrl()
        if (raw.isBlank()) return null
        return normalizeHttpUrl(raw)
    }

    /**
     * Cold boot: intro only when a non-blank http(s) URL is configured.
     * An orphan [intro_cached.mp4] without a URL is deleted and does not start Intro.
     */
    fun canStartIntro(): Boolean {
        val http = getValidHttpUrl()
        if (http.isNullOrBlank()) {
            if (fileStore.hasReadyFile()) {
                Log.w(TAG, "canStartIntro — empty URL with local file; clearing orphan cache")
                fileStore.clear()
            }
            return false
        }
        return fileStore.canPlayIntro(http)
    }

    fun fileStore(): IntroVideoFileStore = fileStore

    /**
     * Persist URL for the next cold boot. Uses [SharedPreferences.Editor.commit]
     * so a process kill right after admin sync still keeps the value.
     * Blank URL clears the local MP4 as well.
     */
    fun setUrl(url: String?) {
        val normalized = url?.let { normalizeHttpUrl(it.trim()) }.orEmpty()
        val previous = getUrl()
        val ok = prefs.edit().putString(KEY_INTRO_VIDEO_URL, normalized).commit()
        Log.i(
            TAG,
            "setUrl commitOk=$ok blank=${normalized.isBlank()} len=${normalized.length} " +
                "prefix=${normalized.take(72)}",
        )
        if (normalized.isBlank()) {
            fileStore.clear()
        } else if (previous != normalized) {
            Log.i(TAG, "URL changed — background download should refresh local file")
        }
    }

    fun clear() {
        val ok = prefs.edit().remove(KEY_INTRO_VIDEO_URL).commit()
        fileStore.clear()
        Log.i(TAG, "clear commitOk=$ok")
    }

    companion object {
        private const val TAG = "IntroVideoCache"
        private const val PREFS_NAME = "hotel_tv_intro"
        private const val KEY_INTRO_VIDEO_URL = "intro_video_url"

        /**
         * API 28+ safe http(s) URI normalize — rejects blank host / bad schemes
         * that make ExoPlayer throw on older Android TV.
         */
        fun normalizeHttpUrl(raw: String): String? {
            val trimmed = raw.trim().trim('"', '\'').trim()
            if (trimmed.isBlank()) return null
            return try {
                val uri = Uri.parse(trimmed)
                val scheme = uri.scheme?.lowercase(Locale.US)
                if (scheme != "http" && scheme != "https") {
                    Log.w(TAG, "reject scheme=$scheme prefix=${trimmed.take(64)}")
                    return null
                }
                if (uri.host.isNullOrBlank()) {
                    Log.w(TAG, "reject blank host prefix=${trimmed.take(64)}")
                    return null
                }
                uri.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Uri.parse failed prefix=${trimmed.take(64)}", e)
                null
            }
        }

        fun parseMediaUri(raw: String): Uri? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            // Local offline file
            if (trimmed.startsWith("file:", ignoreCase = true) ||
                trimmed.startsWith("/")
            ) {
                return try {
                    if (trimmed.startsWith("/")) Uri.fromFile(File(trimmed))
                    else Uri.parse(trimmed)
                } catch (e: Exception) {
                    Log.e(TAG, "parse file Uri failed", e)
                    null
                }
            }
            val normalized = normalizeHttpUrl(trimmed) ?: return null
            return try {
                Uri.parse(normalized)
            } catch (e: Exception) {
                Log.e(TAG, "parseMediaUri failed", e)
                null
            }
        }
    }
}
