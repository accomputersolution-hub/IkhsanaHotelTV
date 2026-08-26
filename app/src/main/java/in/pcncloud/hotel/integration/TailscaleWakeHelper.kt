package `in`.pcncloud.hotel.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig

/**
 * Silently wakes Tailscale so its VPN can reconnect without opening any UI.
 *
 * **Corporate flavor only** — hotel builds no-op every entry point.
 *
 * Official Tailscale entry point (exported):
 * - Broadcast → [RECEIVER_CLASS] / [ACTION_CONNECT_VPN]
 *
 * [IPNService] is **not** exported, so another app cannot `startService` it.
 * Launching Tailscale [MainActivity] would flash UI — intentionally avoided.
 *
 * After a cold boot, Tailscale often needs a second CONNECT a few seconds later
 * once its process / Go backend has settled (known Android Tailscale behavior).
 */
object TailscaleWakeHelper {

    private const val TAG = "TailscaleWake"

    const val PACKAGE_NAME = "com.tailscale.ipn"
    const val RECEIVER_CLASS = "com.tailscale.ipn.IPNReceiver"
    const val ACTION_CONNECT_VPN = "com.tailscale.ipn.CONNECT_VPN"
    const val ACTION_DISCONNECT_VPN = "com.tailscale.ipn.DISCONNECT_VPN"

    /** Delay before the second CONNECT nudge (boot / cold-start reliability). */
    private const val RETRY_DELAY_MS = 2_500L

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
     * Sends one explicit CONNECT_VPN broadcast to Tailscale's exported receiver.
     * No Activity is started — zero visual interruption from Tailscale.
     *
     * Hotel flavor: always returns false (no-op).
     *
     * @return true if the broadcast was dispatched (package present / intent built).
     */
    fun wakeConnect(context: Context): Boolean {
        if (!isCorporateFlavor) {
            return false
        }
        if (!isInstalled(context)) {
            Log.w(TAG, "Tailscale not installed — skip silent wake")
            return false
        }

        return try {
            val intent = Intent(ACTION_CONNECT_VPN).apply {
                component = ComponentName(PACKAGE_NAME, RECEIVER_CLASS)
                setPackage(PACKAGE_NAME)
                // Explicit component broadcast; do not require a running Tailscale UI.
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.applicationContext.sendBroadcast(intent)
            Log.i(TAG, "Sent silent CONNECT_VPN → $PACKAGE_NAME/$RECEIVER_CLASS")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Silent Tailscale CONNECT_VPN failed", t)
            false
        }
    }

    /**
     * Sends CONNECT_VPN immediately, then again after [RETRY_DELAY_MS].
     * Safe to call from [android.content.BroadcastReceiver] when paired with [goAsync].
     *
     * Hotel flavor: invokes [onComplete] immediately without sending broadcasts.
     *
     * @param onComplete optional callback on the main thread after the retry attempt
     *   (use to finish a receiver's [android.content.BroadcastReceiver.PendingResult]).
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
        val first = wakeConnect(app)
        if (!first) {
            onComplete?.invoke()
            return
        }

        Handler(Looper.getMainLooper()).postDelayed(
            {
                try {
                    wakeConnect(app)
                } finally {
                    onComplete?.invoke()
                }
            },
            RETRY_DELAY_MS,
        )
    }
}
