package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.util.Log

/**
 * Boot-safe WireGuard bring-up: background IO thread, wait for real internet,
 * routing settle delay, then provision + tunnel UP.
 */
object WireGuardAutoConnect {
    private const val TAG = "WireGuardAutoConnect"

    /**
     * Full auto-connect sequence for kiosk / boot / Always-On VPN.
     * Never call from the main thread — invoked from [WireGuardController] on IO.
     */
    suspend fun connectWhenNetworkReady(context: Context): Boolean {
        val app = context.applicationContext

        if (!WireGuardEngine.isVpnPrepared(app)) {
            Log.w(TAG, "VPN consent missing — defer until Activity grants prepare()")
            return false
        }

        if (!WireGuardNetworkGate.awaitInternetAndRoutingSettle(app)) {
            Log.w(TAG, "Network not ready — skipping WireGuard connect this attempt")
            return false
        }

        // Reconnect with cached config when possible; otherwise keygen + add-peer.
        val cached = WireGuardEngine.peekLastConfig()
        if (cached != null && cached.isComplete()) {
            val included = WireGuardSplitTunnel.resolveIncludedApplications(app)
            val refreshed = cached.copy(includedApplications = included)
            Log.i(TAG, "Network ready — reconnecting with cached tunnel config includedApps=$included")
            WireGuardEngine.connect(refreshed, app)
            return true
        }

        Log.i(TAG, "Network ready — full provision (keygen + add-peer + connect)")
        return WireGuardProvisioner.provisionAndConnect(app)
    }
}
