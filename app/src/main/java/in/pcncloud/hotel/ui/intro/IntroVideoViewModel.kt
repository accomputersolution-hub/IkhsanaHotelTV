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
 * Non-blocking cold-start intro: resolve URL + prepare media in the background while
 * Home is already visible. Overlay only after ExoPlayer reports READY with real media.
 *
 * Sources (same as admin):
 * 1. `Hotels/{hotelId}/Config/intro.introVideoUrl`
 * 2. `Hotels/{hotelId}.introVideoUrl` (admin mirror)
 */
data class IntroVideoUiState(
    val phase: IntroPhase = IntroPhase.Checking,
    val videoUrl: String = "",
    val statusMessage: String = "",
    val hotelId: String = "",
    val playerError: String = "",
) {
    /** Fullscreen overlay should paint — media is prepared. */
    val shouldShowOverlay: Boolean
        get() = phase == IntroPhase.Playing

    /** Session over — host may dispose (never showed, or finished/skipped). */
    val isSessionComplete: Boolean
        get() = phase == IntroPhase.Finished
}

enum class IntroPhase {
    /** Silent Firestore / cache lookup — Home stays fully interactive. */
    Checking,
    /** Valid URL — ExoPlayer preparing offscreen; still no overlay. */
    Preparing,
    /** Media READY — fullscreen overlay visible and playing. */
    Playing,
    /** No URL / timeout / error / skip / ended — stay on Home silently. */
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
    private var prepareWatchdog: Job? = null

    init {
        resolveIntroUrlInBackground()
    }

    private fun resolveIntroUrlInBackground() {
        viewModelScope.launch {
            val hotelId = config.getHotelId().orEmpty()
            Log.i(TAG, "background resolve start hotelId=$hotelId timeoutMs=$RESOLVE_TIMEOUT_MS")
            _uiState.update {
                it.copy(
                    phase = IntroPhase.Checking,
                    hotelId = hotelId,
                    statusMessage = "",
                )
            }

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
                Log.i(TAG, "Intro silent skip — no URL / slow fetch hotelId=$hotelId")
                finish("no_url_or_timeout")
                return@launch
            }

            Log.i(
                TAG,
                "Intro prepare (offscreen) → hotelId=$hotelId urlLen=${resolved.length} " +
                    "prefix=${resolved.take(72)}",
            )
            _uiState.update {
                it.copy(
                    phase = IntroPhase.Preparing,
                    videoUrl = resolved,
                    statusMessage = "",
                    hotelId = hotelId,
                )
            }
            startPrepareWatchdog()
        }
    }

    /** Fail open if media never becomes READY quickly enough. */
    private fun startPrepareWatchdog() {
        prepareWatchdog?.cancel()
        prepareWatchdog = viewModelScope.launch {
            delay(PREPARE_TIMEOUT_MS)
            if (!finished && _uiState.value.phase == IntroPhase.Preparing) {
                Log.w(
                    TAG,
                    "Intro prepare timeout — stay on Home url=${_uiState.value.videoUrl.take(72)}",
                )
                finish("prepare_timeout")
            }
        }
    }

    /**
     * ExoPlayer reached STATE_READY with valid duration while still offscreen.
     * Promote to Playing so the host can overlay without a loading screen.
     */
    fun onMediaReady() {
        if (finished) return
        if (_uiState.value.phase != IntroPhase.Preparing) return
        prepareWatchdog?.cancel()
        prepareWatchdog = null
        Log.i(TAG, "Intro media READY — show overlay")
        _uiState.update { it.copy(phase = IntroPhase.Playing, statusMessage = "") }
    }

    /**
     * Guest left Home (submenu) before prepare finished — abandon without flashing overlay.
     */
    fun abandonBecauseBusy(reason: String = "guest_busy") {
        if (finished) return
        if (_uiState.value.phase == IntroPhase.Playing) return
        Log.i(TAG, "Intro abandon ($reason) — stay on current UI")
        finish(reason)
    }

    fun onPlaybackEnded() {
        Log.i(TAG, "ExoPlayer STATE_ENDED")
        finish("ended")
    }

    fun onPlaybackError(message: String?) {
        val detail = message?.trim().orEmpty().ifBlank { "unknown ExoPlayer error" }
        Log.e(TAG, "ExoPlayer error — dismiss silently: $detail")
        // No error banner — Home stays (or returns) without a dead loading screen.
        finish("error")
    }

    fun onSkip() {
        Log.i(TAG, "Intro skip pressed")
        finish("skip")
    }

    private fun finish(reason: String) {
        if (finished) return
        finished = true
        prepareWatchdog?.cancel()
        prepareWatchdog = null
        Log.i(TAG, "Intro session done ($reason) hotelId=${_uiState.value.hotelId}")
        _uiState.update {
            it.copy(phase = IntroPhase.Finished, statusMessage = reason, playerError = "")
        }
    }

    companion object {
        private const val TAG = "IntroVideoVM"
        /** Keep Home snappy — abandon if Firestore is slow. */
        const val RESOLVE_TIMEOUT_MS = 4_000L
        /** Abandon if media is not READY soon after URL resolve. */
        const val PREPARE_TIMEOUT_MS = 6_000L

        private fun looksLikeHttpUrl(url: String): Boolean =
            url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true)
    }
}
