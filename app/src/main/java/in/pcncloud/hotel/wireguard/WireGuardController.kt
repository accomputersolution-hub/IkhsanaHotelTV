package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
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
        ensureRunning(context)
    }

    /**
     * Schedules boot-safe VPN connect on a background thread (never blocks UI).
     */
    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
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
