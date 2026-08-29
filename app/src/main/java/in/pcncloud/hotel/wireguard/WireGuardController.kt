package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig

/**
 * App-facing WireGuard entry point. Corporate builds auto-connect when
 * [WireGuardCredentials] are filled in; hotel builds no-op.
 */
object WireGuardController {
    private const val TAG = "WireGuardController"

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

    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        try {
            WireGuardEngine.ensureRunning(context)
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
