package `in`.pcncloud.hotel.ui.intro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves intro video URL for cold-start playback.
 *
 * Sources (same as admin):
 * 1. `Hotels/{hotelId}/Config/intro.introVideoUrl`
 * 2. `Hotels/{hotelId}.introVideoUrl` (admin mirror)
 *
 * Fail-open only after both sources miss / timeout — never on the first empty
 * listener tick alone.
 */
data class IntroVideoUiState(
    val phase: IntroPhase = IntroPhase.Resolving,
    val videoUrl: String = "",
    val statusMessage: String = "",
    val hotelId: String = "",
    /** Last ExoPlayer failure (shown briefly before Home). */
    val playerError: String = "",
) {
    val shouldEnterHome: Boolean
        get() = phase == IntroPhase.Finished
}

enum class IntroPhase {
    /** Waiting for Firestore Config/intro (or timeout). */
    Resolving,
    /** Valid URL ready — ExoPlayer should start. */
    Playing,
    /** Skip / ended / empty / error / timeout — navigate to Home. */
    Finished,
}

class IntroVideoViewModel(
    private val repository: FirestoreRepository,
    private val config: HotelConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        IntroVideoUiState(hotelId = config.getHotelId().orEmpty()),
    )
    val uiState: StateFlow<IntroVideoUiState> = _uiState.asStateFlow()

    private var finished = false
    private var playbackWatchdog: Job? = null

    init {
        resolveIntroUrl()
    }

    private fun resolveIntroUrl() {
        viewModelScope.launch {
            val hotelId = config.getHotelId().orEmpty()
            Log.i(TAG, "resolveIntroUrl start hotelId=$hotelId timeoutMs=$RESOLVE_TIMEOUT_MS")
            _uiState.update {
                it.copy(
                    phase = IntroPhase.Resolving,
                    hotelId = hotelId,
                    statusMessage = "Resolving intro for $hotelId…",
                )
            }

            // Race: one-shot get (Config + hotel root) vs live non-blank snapshots.
            val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                merge(
                    flow {
                        val url = runCatching { repository.fetchIntroVideoUrl() }
                            .onFailure { Log.e(TAG, "fetchIntroVideoUrl threw", it) }
                            .getOrDefault("")
                        Log.i(TAG, "one-shot result blank=${url.isBlank()} len=${url.length}")
                        emit(url)
                    },
                    repository.observeIntroVideoUrl(),
                )
                    .filter { candidate ->
                        val ok = candidate.isNotBlank() && looksLikeHttpUrl(candidate)
                        if (!ok && candidate.isNotBlank()) {
                            Log.w(TAG, "reject non-http intro URL prefix=${candidate.take(64)}")
                        }
                        ok
                    }
                    .first()
                    .trim()
            }

            if (finished) return@launch

            if (resolved.isNullOrBlank()) {
                Log.e(
                    TAG,
                    "Intro skip — no valid URL hotelId=$hotelId " +
                        "(check TV is paired to the same hotel as admin, e.g. 3210)",
                )
                finish("no_url")
                return@launch
            }

            Log.i(
                TAG,
                "Intro play → hotelId=$hotelId urlLen=${resolved.length} prefix=${resolved.take(72)}",
            )
            _uiState.update {
                it.copy(
                    phase = IntroPhase.Playing,
                    videoUrl = resolved,
                    statusMessage = "",
                    hotelId = hotelId,
                )
            }
            startPlaybackWatchdog()
        }
    }

    /** If ExoPlayer never reaches ready/playing, fail open to Home. */
    private fun startPlaybackWatchdog() {
        playbackWatchdog?.cancel()
        playbackWatchdog = viewModelScope.launch {
            delay(PLAYBACK_TIMEOUT_MS)
            if (!finished && _uiState.value.phase == IntroPhase.Playing) {
                Log.e(
                    TAG,
                    "Intro playback timeout → Home url=${_uiState.value.videoUrl.take(72)}",
                )
                finish("playback_timeout")
            }
        }
    }

    fun onPlaybackStarted() {
        Log.i(TAG, "ExoPlayer playing — cancel watchdog")
        playbackWatchdog?.cancel()
        playbackWatchdog = null
    }

    fun onPlaybackEnded() {
        Log.i(TAG, "ExoPlayer STATE_ENDED (real playback)")
        finish("ended")
    }

    /**
     * Hard player failure. Logs and briefly surfaces the message, then goes Home.
     * Does **not** run for buffering / READY — only explicit [Player.Listener.onPlayerError]
     * or empty-media guards from the screen.
     */
    fun onPlaybackError(message: String?) {
        val detail = message?.trim().orEmpty().ifBlank { "unknown ExoPlayer error" }
        Log.e(TAG, "ExoPlayer error (will leave intro shortly): $detail")
        _uiState.update { it.copy(playerError = detail.take(220)) }
        // Give logcat / on-screen message a moment before tearing down.
        viewModelScope.launch {
            delay(ERROR_HOLD_MS)
            finish("error")
        }
    }

    fun onSkip() {
        Log.i(TAG, "Intro skip pressed")
        finish("skip")
    }

    private fun finish(reason: String) {
        if (finished) return
        finished = true
        playbackWatchdog?.cancel()
        playbackWatchdog = null
        Log.i(TAG, "Intro finished ($reason) → Home hotelId=${_uiState.value.hotelId}")
        _uiState.update {
            it.copy(phase = IntroPhase.Finished, statusMessage = reason)
        }
    }

    companion object {
        private const val TAG = "IntroVideoVM"
        /** Wait for Config/intro + hotel-root get before skipping. */
        const val RESOLVE_TIMEOUT_MS = 15_000L
        /** Max wait for first successful playback after URL is known. */
        const val PLAYBACK_TIMEOUT_MS = 25_000L
        /** Keep error text visible briefly before Home. */
        private const val ERROR_HOLD_MS = 2_500L

        private fun looksLikeHttpUrl(url: String): Boolean =
            url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true)
    }
}
