package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-facing WireGuard entry point.
 *
 * Corporate auto-connect runs on [Dispatchers.IO], waits for validated
 * internet + routing settle, then provisions and brings the tunnel UP.
 *
 * VPN consent ([android.net.VpnService.prepare]) must be launched from a
 * visible Activity (Splash / Main / Pairing). Always-On is applied only after
 * prepare succeeds so Device Owner does not silently skip the system dialog.
 */
object WireGuardController {
    private const val TAG = "WireGuardController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val autoConnectInFlight = AtomicBoolean(false)

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
        WireGuardConsentStore.markUserGranted(context)
        MyDeviceAdminReceiver.ensureAlwaysOnWireGuardVpn(context)
        ensureRunning(context)
    }

    /**
     * If Device Owner Always-On already authorized the package without our consent flag,
     * clear Always-On once so [preparePermissionIntent] can return the system dialog Intent.
     */
    fun prepareForConsentPrompt(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        if (WireGuardConsentStore.hasUserGranted(context)) return
        try {
            MyDeviceAdminReceiver.clearAlwaysOnWireGuardVpn(context)
        } catch (t: Throwable) {
            Log.w(TAG, "prepareForConsentPrompt clear Always-On failed", t)
        }
    }

    /**
     * Schedules boot-safe VPN connect on a background thread (never blocks UI).
     * No-op until [isVpnPrepared] is true.
     */
    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        if (!isVpnPrepared(context)) {
            Log.w(TAG, "ensureRunning deferred — VpnService.prepare() not granted yet")
            return
        }
        MyDeviceAdminReceiver.ensureAlwaysOnWireGuardVpn(context)
        if (!autoConnectInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "ensureRunning skipped — auto-connect already in flight")
            return
        }
        try {
            WireGuardEngine.init(context)
            scope.launch {
                try {
                    WireGuardAutoConnect.connectWhenNetworkReady(context.applicationContext)
                } catch (t: Throwable) {
                    Log.e(TAG, "ensureRunning background connect failed", t)
                } finally {
                    autoConnectInFlight.set(false)
                }
            }
        } catch (t: Throwable) {
            autoConnectInFlight.set(false)
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
