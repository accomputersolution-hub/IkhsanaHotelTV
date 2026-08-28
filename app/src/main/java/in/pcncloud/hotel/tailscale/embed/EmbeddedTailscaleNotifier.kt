package `in`.pcncloud.hotel.tailscale.embed

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Minimal IPN bus watcher — tracks engine [EmbeddedTailscaleModels.State].
 */
object EmbeddedTailscaleNotifier {
    private const val TAG = "EmbeddedTsNotifier"
    private val decoder = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(EmbeddedTailscaleModels.State.NoState)
    val state: StateFlow<EmbeddedTailscaleModels.State> = _state

    private lateinit var app: libtailscale.Application
    private var manager: libtailscale.NotificationManager? = null

    fun setApp(application: libtailscale.Application) {
        app = application
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun start(scope: CoroutineScope) {
        if (!::app.isInitialized) return
        scope.launch(Dispatchers.IO) {
            val mask = 2L or 4L or 16384L // InitialState | Prefs | InitialStatus
            manager = app.watchNotifications(mask) { notification ->
                try {
                    val notify = decoder.decodeFromStream<EmbeddedTailscaleModels.Notify>(
                        notification.inputStream(),
                    )
                    notify.State?.let { _state.value = EmbeddedTailscaleModels.State.fromInt(it) }
                    notify.ErrMessage?.let { Log.w(TAG, "ipn error: $it") }
                } catch (t: Throwable) {
                    Log.w(TAG, "notify decode failed", t)
                }
            }
        }
    }

    fun stop() {
        manager?.stop()
        manager = null
    }
}
