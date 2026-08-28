package `in`.pcncloud.hotel.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver

/**
 * Corporate-only Tailscale wake: silent CONNECT broadcasts and optional brief UI launch.
 *
 * Tailscale owns the [android.net.VpnService] TUN — this helper only nudges
 * [com.tailscale.ipn] to connect after boot.
 */
object TailscaleWakeHelper {

    private const val TAG = "TailscaleWake"

    const val PACKAGE_NAME = MyDeviceAdminReceiver.TAILSCALE_VPN_PACKAGE
    const val RECEIVER_CLASS = "com.tailscale.ipn.IPNReceiver"
    const val ACTION_CONNECT_VPN = "com.tailscale.ipn.CONNECT_VPN"

    private const val RETRY_DELAY_MS = 2_500L
    private const val RETURN_TO_KIOSK_DELAY_MS = 12_000L
    private const val WAKE_THROTTLE_MS = 15_000L

    @Volatile
    private var lastWakeAtElapsedMs: Long = 0L

    private val isCorporateFlavor: Boolean
        get() = BuildConfig.IS_CORPORATE

    fun isInstalled(context: Context): Boolean {
        if (!isCorporateFlavor) return false
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (t: Throwable) {
            Log.w(TAG, "isInstalled check failed", t)
            false
        }
    }

    fun isTailscaleVpnConnected(context: Context): Boolean {
        if (!isCorporateFlavor) return false
        return try {
            val app = context.applicationContext
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            val vpnUp = hasActiveVpnTransport(cm)
            if (!vpnUp) {
                Log.d(TAG, "VPN transport not active")
                return false
            }

            if (isAlwaysOnTailscale(app)) {
                Log.d(TAG, "VPN up + Always-On package is Tailscale")
                return true
            }

            Log.d(TAG, "VPN transport active (treating as Tailscale on corporate TV)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "isTailscaleVpnConnected failed — treat as disconnected", t)
            false
        }
    }

    private fun hasActiveVpnTransport(cm: ConnectivityManager): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val active = cm.activeNetwork
                if (active != null) {
                    val caps = cm.getNetworkCapabilities(active)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                }
            }
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }
            false
        } catch (t: Throwable) {
            Log.w(TAG, "hasActiveVpnTransport failed", t)
            false
        }
    }

    private fun isAlwaysOnTailscale(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            if (!MyDeviceAdminReceiver.isDeviceOwner(context)) return false
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? android.app.admin.DevicePolicyManager
                ?: return false
            val admin = MyDeviceAdminReceiver.getComponentName(context)
            dpm.getAlwaysOnVpnPackage(admin) == PACKAGE_NAME
        } catch (t: Throwable) {
            Log.w(TAG, "isAlwaysOnTailscale check failed", t)
            false
        }
    }

    fun sendConnectBroadcast(context: Context): Boolean {
        if (!isInstalled(context)) {
            Log.w(TAG, "Tailscale not installed — skip CONNECT_VPN")
            return false
        }

        return try {
            val intent = Intent(ACTION_CONNECT_VPN).apply {
                component = ComponentName(PACKAGE_NAME, RECEIVER_CLASS)
                setPackage(PACKAGE_NAME)
                addFlags(
                    Intent.FLAG_INCLUDE_STOPPED_PACKAGES or
                        Intent.FLAG_RECEIVER_FOREGROUND,
                )
            }
            context.applicationContext.sendBroadcast(intent)
            Log.i(TAG, "Sent CONNECT_VPN → $PACKAGE_NAME/$RECEIVER_CLASS")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "CONNECT_VPN broadcast failed", t)
            false
        }
    }

    /**
     * CONNECT immediately, then again after [RETRY_DELAY_MS].
     * Falls back to a brief Tailscale UI launch when VPN is still down.
     */
    fun wakeConnectWithRetry(
        context: Context,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!isCorporateFlavor) {
            onComplete?.invoke()
            return
        }

        val app = context.applicationContext
        if (!isInstalled(app)) {
            onComplete?.invoke()
            return
        }

        if (isTailscaleVpnConnected(app)) {
            Log.i(TAG, "Tailscale VPN already connected — skip wake")
            onComplete?.invoke()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val last = lastWakeAtElapsedMs
        if (last > 0L && now - last < WAKE_THROTTLE_MS) {
            Log.i(TAG, "Tailscale wake throttled (${now - last}ms)")
            onComplete?.invoke()
            return
        }
        lastWakeAtElapsedMs = now

        val first = sendConnectBroadcast(app)
        if (!first) {
            wakeViaUiThenReturnToKiosk(app, onComplete)
            return
        }

        Handler(Looper.getMainLooper()).postDelayed(
            {
                try {
                    if (!isTailscaleVpnConnected(app)) {
                        sendConnectBroadcast(app)
                    }
                    if (!isTailscaleVpnConnected(app)) {
                        wakeViaUiThenReturnToKiosk(app, null)
                    }
                } finally {
                    onComplete?.invoke()
                }
            },
            RETRY_DELAY_MS,
        )
    }

    private fun wakeViaUiThenReturnToKiosk(
        context: Context,
        onComplete: (() -> Unit)?,
    ) {
        val app = context.applicationContext
        val launchIntent = try {
            app.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        } catch (t: Throwable) {
            Log.e(TAG, "getLaunchIntentForPackage failed", t)
            null
        }

        if (launchIntent == null) {
            Log.w(TAG, "No launch intent for $PACKAGE_NAME")
            onComplete?.invoke()
            return
        }

        try {
            KioskPolicy.markOttLaunched(app, PACKAGE_NAME)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            Log.i(
                TAG,
                "VPN down — launched Tailscale UI; restore MainActivity in ${RETURN_TO_KIOSK_DELAY_MS}ms",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Tailscale startActivity failed", t)
            try {
                KioskPolicy.clearOttLaunchState(app, suppressMs = 0L)
            } catch (_: Throwable) {
            }
            onComplete?.invoke()
            return
        }

        Handler(Looper.getMainLooper()).postDelayed(
            {
                try {
                    bringMainActivityToFront(app)
                } finally {
                    onComplete?.invoke()
                }
            },
            RETURN_TO_KIOSK_DELAY_MS,
        )
    }

    private fun bringMainActivityToFront(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            Log.i(TAG, "Restored MainActivity after Tailscale UI wake")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to restore MainActivity after Tailscale", t)
        } finally {
            try {
                KioskPolicy.clearOttLaunchState(context, suppressMs = 0L)
            } catch (_: Throwable) {
            }
        }
    }
}
