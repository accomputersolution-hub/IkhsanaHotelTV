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
 * Corporate-only Tailscale VPN wake via a short UI launch.
 *
 * Boot/splash only:
 * 1. [PackageManager.getLaunchIntentForPackage] → start Tailscale
 * 2. After [RETURN_TO_KIOSK_DELAY_MS] (12s), one-shot restore of [MainActivity]
 *
 * There is **no** ConnectivityManager / VPN network listener. After this one-shot
 * sequence finishes, later Tailscale VPN UI flashes are ignored by
 * [KioskPolicy.shouldSkipKioskReclaim] so we never inject more REORDER_TO_FRONT
 * intents when the tunnel connects.
 *
 * Hotel flavor: every entry point is a no-op.
 */
object TailscaleWakeHelper {

    private const val TAG = "TailscaleWake"

    const val PACKAGE_NAME = "com.tailscale.ipn"

    /** How long Tailscale stays foreground before the one-shot kiosk restore. */
    private const val RETURN_TO_KIOSK_DELAY_MS = 12_000L

    /** Prevent BootReceiver + Splash from double-firing the UI flash. */
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
     * Launches Tailscale UI, then restores [MainActivity] once after 12s.
     * Does not register network listeners; does not schedule further reclaim.
     *
     * Hotel flavor / missing package: invokes [onComplete] immediately.
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

        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastWakeAtElapsedMs
        if (last > 0L && now - last < WAKE_THROTTLE_MS) {
            Log.i(TAG, "Tailscale UI wake throttled (${now - last}ms)")
            onComplete?.invoke()
            return
        }
        lastWakeAtElapsedMs = now

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
            // Block Watchdog during the intentional Tailscale dwell.
            KioskPolicy.markOttLaunched(app, PACKAGE_NAME)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            Log.i(
                TAG,
                "Launched Tailscale UI → $PACKAGE_NAME; one-shot MainActivity restore in " +
                    "${RETURN_TO_KIOSK_DELAY_MS}ms (no VPN network listener)",
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
                    finishInitialWakeSequence(app)
                } finally {
                    onComplete?.invoke()
                }
            },
            RETURN_TO_KIOSK_DELAY_MS,
        )
    }

    /**
     * Backward-compatible alias used by BootReceiver / Splash.
     */
    fun wakeConnectWithRetry(
        context: Context,
        onComplete: (() -> Unit)? = null,
    ) = wakeViaUiThenReturnToKiosk(context, onComplete)

    /**
     * One-shot restore after the boot dwell. After this returns, no further
     * Tailscale/VPN-driven startActivity reclaim is scheduled from this helper.
     */
    private fun finishInitialWakeSequence(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            Log.i(TAG, "Initial Tailscale wake done — MainActivity restored once")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bring MainActivity to front after Tailscale", t)
        } finally {
            try {
                // Re-enable normal kiosk reclaim for non-Tailscale apps.
                // suppressMs=0: do not create a 2.5s "jump" window; Tailscale VPN
                // UI flashes are ignored via shouldSkipKioskReclaim instead.
                KioskPolicy.clearOttLaunchState(context, suppressMs = 0L)
            } catch (_: Throwable) {
            }
        }
    }
}
