package `in`.pcncloud.hotel.wireguard

import android.util.Log
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Named tunnel handle required by [com.wireguard.android.backend.GoBackend].
 */
class WireGuardAppTunnel(
    private val tunnelName: String = DEFAULT_NAME,
) : Tunnel {
    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state.asStateFlow()

    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        Log.i(TAG, "tunnel=$tunnelName state=$newState")
        _state.value = newState
    }

    companion object {
        private const val TAG = "WireGuardAppTunnel"
        const val DEFAULT_NAME = "pcn-wg"
    }
}
