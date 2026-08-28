package `in`.pcncloud.hotel.tailscale.embed

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal IPN bus watcher — tracks engine [EmbeddedTailscaleModels.State].
 */
object EmbeddedTailscaleNotifier {
    private const val TAG = "EmbeddedTsNotifier"

    /**
     * Match tailscale-android Notifier mask: Prefs, InitialState, PeerChanges,
     * NoNetmap, InitialStatus, InitialHealthState — ensures State/Prefs/LoginFinished delivery.
     */
    private const val WATCH_MASK =
        2L or 4L or 128L or 4096L or 8192L or 16384L // InitialState|Prefs|Health|Peer|NoNetmap|Status

    private val decoder = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(EmbeddedTailscaleModels.State.NoState)
    val state: StateFlow<EmbeddedTailscaleModels.State> = _state

    private var initialStateReceived = false
    private val watching = AtomicBoolean(false)
    private var watchReady = CompletableDeferred<Unit>()

    fun hasInitialState(): Boolean = initialStateReceived

    fun isWatching(): Boolean = watching.get()

    suspend fun awaitWatching(timeoutMs: Long = 15_000L): Boolean {
        if (watching.get()) return true
        return withTimeoutOrNull(timeoutMs) {
            watchReady.await()
        } != null && watching.get()
    }

    suspend fun awaitInitialState(timeoutMs: Long = 15_000L): Boolean {
        if (initialStateReceived) return true
        return withTimeoutOrNull(timeoutMs) {
            while (!initialStateReceived) {
                delay(100)
            }
        } != null && initialStateReceived
    }

    private lateinit var app: libtailscale.Application
    private var manager: libtailscale.NotificationManager? = null

    /** Invoked when Headscale auth-key login completes or state leaves NeedsLogin. */
    @Volatile
    var onHeadlessLoginComplete: (() -> Unit)? = null

    fun setApp(application: libtailscale.Application) {
        app = application
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun start(scope: CoroutineScope) {
        if (!::app.isInitialized) {
            Log.e(TAG, "start skipped — libtailscale Application not set")
            return
        }
        if (watching.get()) {
            Log.d(TAG, "start skipped — already watching IPN bus")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "watchNotifications starting mask=$WATCH_MASK")
                manager = app.watchNotifications(WATCH_MASK) { notification ->
                    try {
                        val notify = decoder.decodeFromStream<EmbeddedTailscaleModels.Notify>(
                            notification.inputStream(),
                        )
                        logNotify(notify)

                        notify.State?.let { stateInt ->
                            initialStateReceived = true
                            val previous = _state.value
                            val newState = EmbeddedTailscaleModels.State.fromInt(stateInt)
                            if (previous != newState) {
                                Log.i(TAG, "IPN state $previous → $newState")
                            }
                            _state.value = newState
                            if (previous == EmbeddedTailscaleModels.State.NeedsLogin &&
                                (newState == EmbeddedTailscaleModels.State.Stopped ||
                                    newState == EmbeddedTailscaleModels.State.Running ||
                                    newState == EmbeddedTailscaleModels.State.Starting)
                            ) {
                                Log.i(TAG, "NeedsLogin cleared → invoking onHeadlessLoginComplete")
                                onHeadlessLoginComplete?.invoke()
                            }
                        }

                        if (notify.LoginFinished != null) {
                            Log.i(TAG, "LoginFinished received — invoking onHeadlessLoginComplete")
                            onHeadlessLoginComplete?.invoke()
                        }

                        notify.ErrMessage?.let { Log.w(TAG, "ipn ErrMessage: $it") }
                    } catch (t: Throwable) {
                        Log.w(TAG, "notify decode failed", t)
                    }
                }
                watching.set(true)
                if (!watchReady.isCompleted) {
                    watchReady.complete(Unit)
                }
                Log.i(TAG, "watchNotifications active — initial state=${_state.value}")
            } catch (t: Throwable) {
                Log.e(TAG, "watchNotifications failed", t)
                if (!watchReady.isCompleted) {
                    watchReady.completeExceptionally(t)
                }
            }
        }
    }

    private fun logNotify(notify: EmbeddedTailscaleModels.Notify) {
        val prefs = notify.Prefs
        if (prefs != null) {
            val control = prefs.ControlURL
            val controlLog = if (control.isBlank()) {
                "empty (stored pref; see GET /prefs / POST /start)"
            } else {
                control.take(48)
            }
            Log.d(
                TAG,
                "notify Prefs control=$controlLog wantRunning=${prefs.WantRunning}",
            )
        }
        notify.BrowseToURL?.let { url ->
            Log.w(TAG, "notify BrowseToURL=$url (interactive login — unexpected for auth-key)")
        }
        if (notify.LoginFinished != null) {
            Log.d(TAG, "notify LoginFinished present")
        }
    }

    fun stop() {
        manager?.stop()
        manager = null
        watching.set(false)
        watchReady = CompletableDeferred()
    }
}
