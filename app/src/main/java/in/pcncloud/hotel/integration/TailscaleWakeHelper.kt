package `in`.pcncloud.hotel.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.kiosk.KioskPolicy

/**
 * Corporate-only Tailscale VPN wake via a short UI launch — **once per install**.
 *
 * Tracks completion in SharedPreferences (`kiosk_prefs` /
 * `is_vpn_configured_or_launched`). After the first successful startActivity,
 * BootReceiver / Splash never flash Tailscale again on later reopens.
 *
 * Hotel flavor: every entry point is a no-op.
 */
object TailscaleWakeHelper {

    private const val TAG = "TailscaleWake"

    const val PACKAGE_NAME = "com.tailscale.ipn"

    /** Same prefs file used by [KioskPolicy] so kiosk state stays in one place. */
    private const val PREFS_NAME = "kiosk_prefs"

    /** Durable run-once flag — true after the first Tailscale UI launch attempt. */
    private const val KEY_VPN_CONFIGURED_OR_LAUNCHED = "is_vpn_configured_or_launched"

    /** How long Tailscale stays foreground before kiosk UI is restored. */
    private const val RETURN_TO_KIOSK_DELAY_MS = 3_500L

    private val isCorporateFlavor: Boolean
        get() = BuildConfig.IS_CORPORATE

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the initial Tailscale UI wake has already run on this device. */
    fun hasAlreadyLaunchedVpn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VPN_CONFIGURED_OR_LAUNCHED, false)

    /**
     * Marks the run-once flag so subsequent startups skip Tailscale UI entirely.
     * Uses [SharedPreferences.Editor.commit] so BootReceiver + Splash see it immediately.
     */
    fun markVpnConfiguredOrLaunched(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_VPN_CONFIGURED_OR_LAUNCHED, true)
            .commit()
        Log.i(TAG, "$KEY_VPN_CONFIGURED_OR_LAUNCHED=true — future Tailscale UI wakes skipped")
    }

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
     * First corporate boot/open only: launches Tailscale UI, then restores
     * [MainActivity] after 3.5s. Later startups see the prefs flag and skip.
     *
     * Hotel flavor / already launched / missing package: invokes [onComplete] immediately.
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

        if (hasAlreadyLaunchedVpn(app)) {
            Log.i(TAG, "Tailscale UI wake skipped — already ran once ($KEY_VPN_CONFIGURED_OR_LAUNCHED=true)")
            onComplete?.invoke()
            return
        }

        if (!isInstalled(app)) {
            Log.w(TAG, "Tailscale not installed — skip UI wake (flag left false for retry)")
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
            Log.w(TAG, "No launch intent for $PACKAGE_NAME — flag left false for retry")
            onComplete?.invoke()
            return
        }

        try {
            // Keep Watchdog from reclaiming during the short Tailscale flash.
            KioskPolicy.markOttLaunched(app, PACKAGE_NAME)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Mark BEFORE startActivity so a concurrent Splash/Boot path cannot double-launch.
            markVpnConfiguredOrLaunched(app)
            app.startActivity(launchIntent)
            Log.i(
                TAG,
                "First-run Tailscale UI → $PACKAGE_NAME; reclaim MainActivity in ${RETURN_TO_KIOSK_DELAY_MS}ms",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Tailscale startActivity failed — clearing run-once flag for retry", t)
            try {
                prefs(app).edit().putBoolean(KEY_VPN_CONFIGURED_OR_LAUNCHED, false).commit()
            } catch (_: Throwable) {
            }
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
