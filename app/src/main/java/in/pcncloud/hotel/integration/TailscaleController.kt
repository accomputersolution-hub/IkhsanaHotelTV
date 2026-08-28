package `in`.pcncloud.hotel.integration

import android.content.Context
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
/**
 * Corporate kiosk entry point for Headscale / Tailscale VPN.
 *
 * Configures the Tailscale Android app (managed restrictions + Always-On VPN)
 * and silently nudges it to connect on boot / resume.
 */
object TailscaleController {

    private const val TAG = "TailscaleController"

    fun init(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        Log.i(TAG, "init — control=${TailscaleCredentials.CONTROL_URL}")
    }

  /**
   * Apply Headscale settings, pin Always-On VPN to Tailscale, and connect.
   */
    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) {
            Log.d(TAG, "ensureRunning skipped — not corporate")
            return
        }

        val app = context.applicationContext
        if (!TailscaleWakeHelper.isInstalled(app)) {
            Log.w(TAG, "Tailscale ($TailscaleWakeHelper.PACKAGE_NAME) not installed")
            return
        }

        try {
            MyDeviceAdminReceiver.applyTailscaleManagedConfig(app)
            MyDeviceAdminReceiver.ensureAlwaysOnTailscaleVpn(app)
        } catch (t: Throwable) {
            Log.w(TAG, "Tailscale Device Owner setup failed", t)
        }

        if (TailscaleWakeHelper.isTailscaleVpnConnected(app)) {
            Log.i(TAG, "Tailscale VPN already connected")
            TailscaleKeepAliveService.start(app)
            return
        }

        TailscaleWakeHelper.wakeConnectWithRetry(app)
        TailscaleKeepAliveService.start(app)
    }
}
