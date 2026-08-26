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
 * Custom TV OS blocks background broadcasts and has no Settings package, so the
 * only reliable path on Android 9 is:
 * 1. [PackageManager.getLaunchIntentForPackage] → start Tailscale
 * 2. After [RETURN_TO_KIOSK_DELAY_MS] (5s), bring [MainActivity] back with
 *    [Intent.FLAG_ACTIVITY_REORDER_TO_FRONT]
 *
 * Hotel flavor: every entry point is a no-op.
 */
object TailscaleWakeHelper {

    private const val TAG = "TailscaleWake"

    const val PACKAGE_NAME = "com.tailscale.ipn"

    /** How long Tailscale stays foreground before kiosk UI is restored. */
    private const val RETURN_TO_KIOSK_DELAY_MS = 5_000L

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
     * Launches Tailscale UI, then restores [MainActivity] after 5s.
     *
     * Hotel flavor / missing package: invokes [onComplete] immediately.
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
            // Keep Watchdog from reclaiming during the short Tailscale flash.
            KioskPolicy.markOttLaunched(app, PACKAGE_NAME)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            Log.i(TAG, "Launched Tailscale UI → $PACKAGE_NAME; reclaim MainActivity in ${RETURN_TO_KIOSK_DELAY_MS}ms")
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
