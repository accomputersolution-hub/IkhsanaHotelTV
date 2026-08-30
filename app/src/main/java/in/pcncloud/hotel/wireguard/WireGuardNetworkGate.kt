package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Cold-boot guard: Wi-Fi / Ethernet often needs 10–15s before UDP to the VPN
 * endpoint succeeds. Wait for [ConnectivityManager] validated internet, then
 * let the OS routing table settle before WireGuard handshake.
 */
object WireGuardNetworkGate {
    private const val TAG = "WireGuardNetworkGate"

    /** Poll interval while waiting for link + validation. */
    private const val POLL_MS = 2_000L

    /** Max wait on boot (TV DHCP + Wi-Fi association can be slow). */
    private const val MAX_WAIT_MS = 120_000L

    /** Post-validation settle — user asked for 10–12s; 11s is the midpoint. */
    private const val ROUTING_SETTLE_MS = 11_000L

    fun hasValidatedInternet(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
        if (cm == null) return false

        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        // Prefer underlying link; if active is VPN, look for any validated non-VPN network.
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            return true
        }

        @Suppress("DEPRECATION")
        val all = cm.allNetworks
        for (n in all) {
            val c = cm.getNetworkCapabilities(n) ?: continue
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Blocks until the default network is validated, then waits [ROUTING_SETTLE_MS]
     * so the routing table stabilizes before UDP WireGuard packets are sent.
     */
    suspend fun awaitInternetAndRoutingSettle(context: Context): Boolean {
        val app = context.applicationContext
        Log.i(TAG, "Waiting for validated internet before WireGuard connect…")

        val deadline = SystemClock.elapsedRealtime() + MAX_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (hasValidatedInternet(app)) {
                Log.i(
                    TAG,
                    "Validated internet available — settling routing ${ROUTING_SETTLE_MS}ms " +
                        "before tunnel UP",
                )
                delay(ROUTING_SETTLE_MS)
                return true
            }
            delay(POLL_MS)
        }

        Log.w(TAG, "Timed out after ${MAX_WAIT_MS}ms — no validated internet for VPN")
        return false
    }
}
