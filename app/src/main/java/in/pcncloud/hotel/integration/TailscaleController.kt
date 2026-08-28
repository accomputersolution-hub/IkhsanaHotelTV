package `in`.pcncloud.hotel.integration

import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.tailscale.embed.EmbeddedTailscaleEngine

/**
 * Corporate kiosk VPN entry point — embedded libtailscale (single APK, no Tailscale app).
 */
object TailscaleController {

    private const val TAG = "TailscaleController"

    fun init(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        EmbeddedTailscaleEngine.init(context)
    }

    fun preparePermissionIntent(context: Context): Intent? {
        if (!BuildConfig.IS_CORPORATE) return null
        return EmbeddedTailscaleEngine.preparePermissionIntent(context)
    }

    fun isVpnPrepared(context: Context): Boolean {
        if (!BuildConfig.IS_CORPORATE) return true
        return EmbeddedTailscaleEngine.isVpnPrepared(context)
    }

    fun onVpnPermissionGranted(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        EmbeddedTailscaleEngine.onVpnPermissionGranted(context)
    }

    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) {
            Log.d(TAG, "ensureRunning skipped — not corporate")
            return
        }
        EmbeddedTailscaleEngine.ensureRunning(context)
    }
}
