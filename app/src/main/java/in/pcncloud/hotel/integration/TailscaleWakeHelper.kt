package `in`.pcncloud.hotel.integration

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
 * Corporate-only Tailscale VPN wake via a short UI launch when VPN is down.
 *
 * No permanent SharedPreferences block — after a restart, if the VPN is not
 * connected, Tailscale is launched again; if already connected, startup skips
 * so the guest is not interrupted.
 *
 * Hotel flavor: every entry point is a no-op.
 */
object TailscaleWakeHelper {

    private const val TAG = "TailscaleWake"

    const val PACKAGE_NAME = "com.tailscale.ipn"

    /** How long Tailscale stays foreground before kiosk UI is restored. */
    private const val RETURN_TO_KIOSK_DELAY_MS = 3_500L

    /** Prevent BootReceiver + Splash from double-firing while VPN is still connecting. */
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

    /**
     * True when a VPN transport is up (Tailscale on these TVs), or when Device Owner
     * Always-On VPN is already pinned to [PACKAGE_NAME] and a VPN network is active.
     */
    fun isTailscaleVpnConnected(context: Context): Boolean {
        return try {
            val app = context.applicationContext
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            val vpnUp = hasActiveVpnTransport(cm)
            if (!vpnUp) {
                Log.d(TAG, "VPN transport not active")
                return false
            }

            // Prefer confirming Always-On package is Tailscale when we are Device Owner.
            val alwaysOnTailscale = isAlwaysOnTailscale(app)
            if (alwaysOnTailscale) {
                Log.d(TAG, "VPN up + Always-On package is Tailscale")
                return true
            }

            // Non-DO / Always-On unset: any active VPN on these corporate TVs is Tailscale.
            Log.d(TAG, "VPN transport active (treating as Tailscale connected)")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "isTailscaleVpnConnected check failed — treat as disconnected", t)
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
            val alwaysOn = dpm.getAlwaysOnVpnPackage(admin)
            alwaysOn == PACKAGE_NAME
        } catch (t: Throwable) {
            Log.w(TAG, "isAlwaysOnTailscale check failed", t)
            false
        }
    }

    /**
     * If Tailscale VPN is not connected, launches Tailscale UI then restores
     * [MainActivity] after 3.5s. If already connected, skips entirely.
     *
     * Hotel flavor / missing package / already connected: invokes [onComplete] immediately.
     *
     * @param onComplete called on the main thread after reclaim attempt (or early exit).
     *   Use from [android.content.BroadcastReceiver.goAsync] to finish the PendingResult.
     */
    fun wakeViaUiThenReturnToKiosk(
        context: Context,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!isCorporateFlavor) {
            onComplete?.invoke()
            return
        }

        val app = context.applicationContext

        if (!isInstalled(app)) {
            Log.w(TAG, "Tailscale not installed — skip UI wake")
            onComplete?.invoke()
            return
        }

        if (isTailscaleVpnConnected(app)) {
            Log.i(TAG, "Tailscale VPN already connected — skip UI launch")
            onComplete?.invoke()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val last = lastWakeAtElapsedMs
        if (last > 0L && now - last < WAKE_THROTTLE_MS) {
            Log.i(TAG, "Tailscale UI wake throttled (${now - last}ms)")
            onComplete?.invoke()
            return
        }

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
            // Keep Watchdog from reclaiming during the short Tailscale flash.
            KioskPolicy.markOttLaunched(app, PACKAGE_NAME)
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_INCLUDE_STOPPED_PACKAGES,
            )
            lastWakeAtElapsedMs = now
            app.startActivity(launchIntent)
            Log.i(
                TAG,
                "VPN down — launched Tailscale UI → $PACKAGE_NAME; " +
                    "reclaim MainActivity in ${RETURN_TO_KIOSK_DELAY_MS}ms",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Tailscale startActivity failed", t)
            try {
                KioskPolicy.clearOttLaunchState(app)
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

    /**
     * Backward-compatible alias used by BootReceiver / Splash.
     * Same behavior as [wakeViaUiThenReturnToKiosk].
     */
    fun wakeConnectWithRetry(
        context: Context,
        onComplete: (() -> Unit)? = null,
    ) = wakeViaUiThenReturnToKiosk(context, onComplete)

    private fun bringMainActivityToFront(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            Log.i(TAG, "Restored MainActivity with REORDER_TO_FRONT after Tailscale wake")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bring MainActivity to front after Tailscale", t)
        } finally {
            try {
                KioskPolicy.clearOttLaunchState(context)
            } catch (_: Throwable) {
            }
        }
    }
}
