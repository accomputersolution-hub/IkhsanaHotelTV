package `in`.pcncloud.hotel.config

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.util.Locale

/**
 * Local source of truth for cold-boot intro playback.
 *
 * Admin / Firestore updates are written here as soon as the TV learns the URL.
 * Cold start reads this synchronously — never waits on a network race.
 */
class IntroVideoCache(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Raw cached string (may be blank). Sync read. */
    fun getUrl(): String =
        prefs.getString(KEY_INTRO_VIDEO_URL, null)?.trim().orEmpty()

    /**
     * Valid http(s) URL for immediate playback, or null → start at Home.
     * Pure SharedPreferences — no Firestore / no coroutines.
     */
    fun getValidHttpUrl(): String? {
        val raw = getUrl()
        if (raw.isBlank()) return null
        return normalizeHttpUrl(raw)
    }

    /**
     * Persist URL for the next cold boot. Uses [SharedPreferences.Editor.commit]
     * so a process kill right after admin sync still keeps the value.
     */
    fun setUrl(url: String?) {
        val normalized = url?.let { normalizeHttpUrl(it.trim()) }.orEmpty()
        val ok = prefs.edit().putString(KEY_INTRO_VIDEO_URL, normalized).commit()
        Log.i(
            TAG,
            "setUrl commitOk=$ok blank=${normalized.isBlank()} len=${normalized.length} " +
                "prefix=${normalized.take(72)}",
        )
    }

    fun clear() {
        val ok = prefs.edit().remove(KEY_INTRO_VIDEO_URL).commit()
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
                // Rebuild encoded URI string for Media3 on API 28+.
                uri.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Uri.parse failed prefix=${trimmed.take(64)}", e)
                null
            }
        }

        fun parseMediaUri(raw: String): Uri? {
            val normalized = normalizeHttpUrl(raw) ?: return null
            return try {
                Uri.parse(normalized)
            } catch (e: Exception) {
                Log.e(TAG, "parseMediaUri failed", e)
                null
            }
        }
    }
}
