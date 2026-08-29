package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App-facing WireGuard entry point.
 *
 * Corporate flow: generate local keypair → POST add-peer → connect tunnel.
 */
object WireGuardController {
    private const val TAG = "WireGuardController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        try {
            WireGuardEngine.init(context)
        } catch (t: Throwable) {
            Log.e(TAG, "WireGuard init failed", t)
        }
    }

    fun preparePermissionIntent(context: Context): Intent? {
        if (!BuildConfig.IS_CORPORATE) return null
        return try {
            WireGuardEngine.preparePermissionIntent(context)
        } catch (t: Throwable) {
            Log.e(TAG, "preparePermissionIntent failed", t)
            null
        }
    }

    fun isVpnPrepared(context: Context): Boolean {
        if (!BuildConfig.IS_CORPORATE) return true
        return try {
            WireGuardEngine.isVpnPrepared(context)
        } catch (t: Throwable) {
            Log.e(TAG, "isVpnPrepared failed", t)
            false
        }
    }

    fun onVpnPermissionGranted(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        ensureRunning(context)
    }

    /**
     * After VPN consent: generate/load keys, register peer, bring tunnel UP.
     */
    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        try {
            WireGuardEngine.init(context)
            if (!WireGuardEngine.isVpnPrepared(context)) {
                Log.d(TAG, "ensureRunning — VPN consent required")
                return
            }
            scope.launch {
                WireGuardProvisioner.provisionAndConnect(context.applicationContext)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ensureRunning failed", t)
        }
    }

    fun connect(context: Context, config: WireGuardTunnelConfig) {
        WireGuardEngine.connect(config, context)
    }

    fun disconnect() {
        WireGuardEngine.disconnect()
    }
}
