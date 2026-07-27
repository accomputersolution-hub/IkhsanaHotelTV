package `in`.pcncloud.hotel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService

/**
 * Auto-launches [MainActivity] when the TV powers on (`BOOT_COMPLETED`).
 *
 * With kiosk / HOME-launcher mode this restores the guest dashboard immediately
 * after reboot. When kiosk is off we still cold-start the app so pairing / guest
 * UI is ready, but Watchdog bring-to-front stays gated by [KioskPolicy].
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

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        try {
            context.startActivity(launchIntent)
            Log.i(
                TAG,
                "Boot launch → MainActivity action=${intent?.action} " +
                    "kiosk=${KioskPolicy.isKioskModeEnabled(context)}",
            )
            if (KioskPolicy.isKioskModeEnabled(context)) {
                KioskWatchdogService.start(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity after boot (${intent?.action})", e)
        }
    }
}
