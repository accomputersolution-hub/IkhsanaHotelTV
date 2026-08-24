package `in`.pcncloud.hotel.ui.intro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves [Hotels/{hotelId}/Config/intro].introVideoUrl for cold-start playback.
 * Fail-open: empty URL, load timeout, or resolve error → [shouldEnterHome] immediately.
 */
data class IntroVideoUiState(
    val phase: IntroPhase = IntroPhase.Resolving,
    val videoUrl: String = "",
    val statusMessage: String = "",
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntroVideoUiState())
    val uiState: StateFlow<IntroVideoUiState> = _uiState.asStateFlow()

    private var finished = false
    private var playbackWatchdog: Job? = null

    init {
        resolveIntroUrl()
    }

    private fun resolveIntroUrl() {
        viewModelScope.launch {
            val url = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                repository.observeIntroVideoUrl().first { true }
            }?.trim().orEmpty()

            if (finished) return@launch

            if (url.isBlank() || !looksLikeHttpUrl(url)) {
                Log.i(TAG, "Intro skip — no valid URL (blank=${url.isBlank()})")
                finish("no_url")
                return@launch
            }

            Log.i(TAG, "Intro play → urlLen=${url.length}")
            _uiState.update {
                it.copy(
                    phase = IntroPhase.Playing,
                    videoUrl = url,
                    statusMessage = "",
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
                Log.w(TAG, "Intro playback timeout → Home")
                finish("playback_timeout")
            }
        }
    }

    fun onPlaybackStarted() {
        playbackWatchdog?.cancel()
        playbackWatchdog = null
    }

    fun onPlaybackEnded() = finish("ended")

    fun onPlaybackError(message: String?) {
        Log.w(TAG, "Intro playback error: $message")
        finish("error")
    }

    fun onSkip() = finish("skip")

    private fun finish(reason: String) {
        if (finished) return
        finished = true
        playbackWatchdog?.cancel()
        playbackWatchdog = null
        Log.i(TAG, "Intro finished ($reason) → Home")
        _uiState.update {
            it.copy(phase = IntroPhase.Finished, statusMessage = reason)
        }
    }

    companion object {
        private const val TAG = "IntroVideoVM"
        /** Max wait for Config/intro snapshot before skipping to Home. */
        const val RESOLVE_TIMEOUT_MS = 8_000L
        /** Max wait for first successful playback after URL is known. */
        const val PLAYBACK_TIMEOUT_MS = 12_000L

        private fun looksLikeHttpUrl(url: String): Boolean =
            url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true)
    }
}
