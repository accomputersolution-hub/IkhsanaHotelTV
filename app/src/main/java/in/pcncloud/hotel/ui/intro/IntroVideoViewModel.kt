package `in`.pcncloud.hotel.ui.intro

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Intro playback policy for API 28 TV boxes vs modern devices.
 *
 * Older SoCs need longer HTTP / buffer / watchdog windows and a retry before
 * falling back to Home — otherwise ExoPlayer is still handshaking TLS while
 * the UI already navigates away.
 */
class IntroVideoViewModel : ViewModel() {

    private val _playerGeneration = MutableStateFlow(0)
    val playerGeneration: StateFlow<Int> = _playerGeneration.asStateFlow()

    private val retriesLeft = AtomicInteger(if (isLegacyApi) LEGACY_RETRIES else MODERN_RETRIES)

    val isLegacyApi: Boolean = Build.VERSION.SDK_INT < 30

    val connectTimeoutMs: Int = if (isLegacyApi) 45_000 else 15_000
    val readTimeoutMs: Int = if (isLegacyApi) 60_000 else 20_000
    val callTimeoutMs: Long = if (isLegacyApi) 90_000L else 45_000L
    val playbackWatchdogMs: Long = if (isLegacyApi) 180_000L else 90_000L
    /** Hold after READY before treating duration=0 as fatal (decoder lag). */
    val readyGraceMs: Long = if (isLegacyApi) 5_000L else 750L
    /** Hold after error before Home / before retry rebuild. */
    val errorGraceMs: Long = if (isLegacyApi) 8_000L else 1_500L
    val minBufferMs: Int = if (isLegacyApi) 8_000 else 3_500
    val maxBufferMs: Int = if (isLegacyApi) 60_000 else 50_000
    val bufferForPlaybackMs: Int = if (isLegacyApi) 5_000 else 1_500
    val bufferForPlaybackAfterRebufferMs: Int = if (isLegacyApi) 8_000 else 3_000

    init {
        Log.i(
            TAG,
            "policy sdk=${Build.VERSION.SDK_INT} legacy=$isLegacyApi " +
                "watchdogMs=$playbackWatchdogMs connectMs=$connectTimeoutMs " +
                "readMs=$readTimeoutMs bufferForPlaybackMs=$bufferForPlaybackMs " +
                "retries=${retriesLeft.get()}",
        )
    }

    /**
     * @return true if a new [playerGeneration] was issued (caller should rebuild ExoPlayer).
     */
    fun tryRetryAfterError(errorDetail: String): Boolean {
        val left = retriesLeft.decrementAndGet()
        if (left < 0) {
            Log.e(TAG, "no retries left — will fall back to Home. error=$errorDetail")
            return false
        }
        val next = _playerGeneration.value + 1
        _playerGeneration.value = next
        Log.w(
            TAG,
            "retry ExoPlayer generation=$next remaining=$left after error=$errorDetail",
        )
        return true
    }

    companion object {
        private const val TAG = "IntroVideoVM"
        private const val LEGACY_RETRIES = 2
        private const val MODERN_RETRIES = 0
    }
}
