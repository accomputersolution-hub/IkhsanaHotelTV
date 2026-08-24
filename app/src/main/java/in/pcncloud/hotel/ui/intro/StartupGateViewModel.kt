package `in`.pcncloud.hotel.ui.intro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Root cold-start gate. Single source of truth for which fullscreen surface is shown.
 *
 * Flow (atomic — only one route composed at a time):
 * 1. [StartupRoute.Checking] — branded welcome wrapper; Firestore/cache URL resolve
 * 2. [StartupRoute.IntroVideo] — valid URL → [IntroVideoScreen]
 * 3. [StartupRoute.Home] — no URL / timeout / intro done → [HomeScreen]
 *
 * Never boots interactive Home first and tries to navigate later (unreliable on TV SoCs).
 */
sealed interface StartupRoute {
    /** Lightweight welcome / branding while resolving introVideoUrl. */
    data object Checking : StartupRoute

    /** Play intro — [url] already validated as http(s). */
    data class IntroVideo(val url: String) : StartupRoute

    /** Guest dashboard — terminal for this process gate. */
    data object Home : StartupRoute
}

class StartupGateViewModel(
    private val repository: FirestoreRepository,
    private val config: HotelConfig,
) : ViewModel() {

    private val _route = MutableStateFlow<StartupRoute>(StartupRoute.Checking)
    val route: StateFlow<StartupRoute> = _route.asStateFlow()

    /** Once Home (or Intro) is committed, Checking cannot race back. */
    private val leftChecking = AtomicBoolean(false)

    init {
        resolveIntroUrl()
    }

    private fun resolveIntroUrl() {
        viewModelScope.launch {
            val hotelId = config.getHotelId().orEmpty()
            Log.i(TAG, "StartupGate resolve start hotelId=$hotelId timeoutMs=$RESOLVE_TIMEOUT_MS")

            val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                merge(
                    flow {
                        val url = runCatching { repository.fetchIntroVideoUrl() }
                            .onFailure { Log.e(TAG, "fetchIntroVideoUrl threw", it) }
                            .getOrDefault("")
                        Log.i(TAG, "one-shot blank=${url.isBlank()} len=${url.length}")
                        emit(url)
                    },
                    repository.observeIntroVideoUrl(),
                )
                    .filter { candidate ->
                        val ok = candidate.isNotBlank() && looksLikeHttpUrl(candidate)
                        if (!ok && candidate.isNotBlank()) {
                            Log.w(TAG, "reject non-http URL prefix=${candidate.take(64)}")
                        }
                        ok
                    }
                    .first()
                    .trim()
            }

            if (leftChecking.get()) {
                Log.w(TAG, "resolve finished after leave — ignore result")
                return@launch
            }

            if (!resolved.isNullOrBlank()) {
                goIntro(resolved)
            } else {
                goHome("no_url_or_timeout")
            }
        }
    }

    private fun goIntro(url: String) {
        if (!leftChecking.compareAndSet(false, true)) {
            Log.w(TAG, "goIntro ignored — already left Checking")
            return
        }
        Log.i(TAG, "StartupRoute Checking → IntroVideo len=${url.length} prefix=${url.take(72)}")
        _route.value = StartupRoute.IntroVideo(url)
    }

    fun goHome(reason: String) {
        val current = _route.value
        if (current is StartupRoute.Home) {
            Log.d(TAG, "goHome($reason) — already Home")
            return
        }
        leftChecking.set(true)
        Log.i(TAG, "StartupRoute $current → Home ($reason)")
        _route.value = StartupRoute.Home
    }

    fun onIntroFinished(reason: String = "intro_finished") {
        goHome(reason)
    }

    companion object {
        private const val TAG = "StartupGateVM"
        /** Bound wait on TV hardware — always leaves Checking. */
        const val RESOLVE_TIMEOUT_MS = 12_000L

        private fun looksLikeHttpUrl(url: String): Boolean =
            url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("http://", ignoreCase = true)
    }
}
