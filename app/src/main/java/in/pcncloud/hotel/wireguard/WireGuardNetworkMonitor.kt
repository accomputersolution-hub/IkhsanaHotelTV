package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * [ConnectivityManager.NetworkCallback] for corporate WireGuard.
 *
 * When an underlying (non-VPN) network becomes available with
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] internet, triggers
 * [WireGuardController.ensureRunning]. On [onLost], tears the tunnel down
 * so we do not black-hole traffic until the link returns.
 */
object WireGuardNetworkMonitor {
    private const val TAG = "WireGuardNetworkMonitor"

    /** Ignore rapid capability flaps (OS often fires several in a row). */
    private const val CONNECT_DEBOUNCE_MS = 5_000L

    private val registered = AtomicBoolean(false)
    private val lastConnectTriggerElapsed = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var connectivityManager: ConnectivityManager? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "onAvailable network=$network — waiting for VALIDATED capability")
            // Do not connect yet; wait for onCapabilitiesChanged with VALIDATED.
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            if (isVpnTransport(networkCapabilities)) {
                Log.d(TAG, "Ignoring VPN transport capabilities for network=$network")
                return
            }

            val hasInternet =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(
                TAG,
                "onCapabilitiesChanged network=$network internet=$hasInternet validated=$validated",
            )

            if (hasInternet && validated) {
                triggerWireGuardConnect("validated_internet")
            }
        }

        override fun onLost(network: Network) {
            Log.w(TAG, "onLost network=$network — underlying link gone")
            handleNetworkLost(network)
        }

        override fun onUnavailable() {
            Log.w(TAG, "onUnavailable — no matching network")
            handleNetworkLost(null)
        }
    }

    /**
     * Registers the callback once. Safe to call from [WireGuardController.init].
     */
    fun start(context: Context) {
        if (!registered.compareAndSet(false, true)) {
            Log.d(TAG, "NetworkCallback already registered")
            return
        }

        val app = context.applicationContext
        appContext = app
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            Log.e(TAG, "ConnectivityManager unavailable — NetworkCallback not registered")
            registered.set(false)
            return
        }
        connectivityManager = cm

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        try {
            // Handler ensures callbacks land on main; connect work is still IO via Controller.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cm.registerNetworkCallback(request, callback, mainHandler)
            } else {
                cm.registerNetworkCallback(request, callback)
            }
            Log.i(TAG, "Registered NetworkCallback for Wi-Fi / Ethernet / Cellular")

            // Cold start: if already validated, kick connect without waiting for an event.
            if (WireGuardNetworkGate.hasValidatedInternet(app)) {
                triggerWireGuardConnect("already_validated_at_start")
            }
        } catch (t: Throwable) {
            registered.set(false)
            Log.e(TAG, "registerNetworkCallback failed", t)
        }
    }

    fun stop() {
        val cm = connectivityManager ?: return
        if (!registered.compareAndSet(true, false)) return
        try {
            cm.unregisterNetworkCallback(callback)
            Log.i(TAG, "Unregistered NetworkCallback")
        } catch (t: Throwable) {
            Log.w(TAG, "unregisterNetworkCallback failed", t)
        }
    }

    private fun triggerWireGuardConnect(reason: String) {
        val app = appContext ?: return
        val now = SystemClock.elapsedRealtime()
        val previous = lastConnectTriggerElapsed.get()
        if (now - previous < CONNECT_DEBOUNCE_MS) {
            Log.d(TAG, "Debounced connect reason=$reason")
            return
        }
        if (!lastConnectTriggerElapsed.compareAndSet(previous, now) &&
            now - lastConnectTriggerElapsed.get() < CONNECT_DEBOUNCE_MS
        ) {
            return
        }
        lastConnectTriggerElapsed.set(now)

        Log.i(TAG, "Validated internet ready — ensureRunning ($reason)")
        WireGuardController.ensureRunning(app)
    }

    private fun handleNetworkLost(network: Network?) {
        val app = appContext ?: return
        // Another underlying link may still be validated (e.g. Ethernet after Wi-Fi drop).
        if (WireGuardNetworkGate.hasValidatedInternet(app)) {
            Log.i(
                TAG,
                "Network $network lost but another validated link remains — keep / reconnect tunnel",
            )
            triggerWireGuardConnect("failover_after_partial_loss")
            return
        }
        Log.w(TAG, "Network lost ($network) — no validated link left; disconnecting WireGuard")
        try {
            WireGuardController.onUnderlyingNetworkLost(app)
        } catch (t: Throwable) {
            Log.e(TAG, "onUnderlyingNetworkLost failed", t)
        }
    }

    private fun isVpnTransport(caps: NetworkCapabilities): Boolean =
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}
