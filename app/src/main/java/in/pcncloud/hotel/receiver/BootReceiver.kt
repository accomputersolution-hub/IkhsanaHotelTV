package `in`.pcncloud.hotel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService

/**
 * Auto-launches the hotel TV app when the device powers on.
 *
 * Does **not** relaunch when the user has minimized an already-created task.
 * Boot is treated as an allowed cold start (unless a visible task already exists).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in BOOT_ACTIONS) return

        // Already running in foreground — do not spawn a duplicate activity.
        if (KioskPolicy.isProcessLifecycleStarted()) {
            Log.i(TAG, "Skip boot launch — process already STARTED")
            return
        }
        if (KioskPolicy.hasExistingTask(context) && !KioskPolicy.isKioskModeEnabled(context)) {
            Log.i(TAG, "Skip boot launch — task already exists (non-kiosk)")
            return
        }

        // Clear stale minimize flag from a previous session after reboot.
        KioskPolicy.clearUserMinimized(context)

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        if (launchIntent == null) {
            Log.e(TAG, "No launch intent found for package ${context.packageName}")
            return
        }

        try {
            context.startActivity(launchIntent)
            Log.i(TAG, "Boot launch → ${intent?.action}")
            if (KioskPolicy.isKioskModeEnabled(context)) {
                KioskWatchdogService.start(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app after boot (${intent?.action})", e)
        }
    }
}
