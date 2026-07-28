package `in`.pcncloud.hotel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.PairingActivity
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService

/**
 * Auto-starts the hotel UI after device restart.
 * Paired → [MainActivity]; unpaired → [PairingActivity].
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
        val action = intent?.action
        if (action !in BOOT_ACTIONS) {
            Log.d(TAG, "Ignoring unrelated action=$action")
            return
        }

        val hotelConfig = HotelConfig(context.applicationContext)
        Log.i(
            TAG,
            "Boot event → action=$action paired=${hotelConfig.isPaired()} " +
                "hotel=${hotelConfig.getHotelId()} room=${hotelConfig.getRoomNumberOrNull()}",
        )

        // Fresh boot: clear stale minimize / OTT session gates from previous power cycle.
        KioskPolicy.clearUserMinimized(context)
        KioskPolicy.clearExternalAppActive(context)
        KioskPolicy.clearExternalAppSession(context)

        val target = if (hotelConfig.isPaired()) {
            MainActivity::class.java
        } else {
            PairingActivity::class.java
        }

        val launchIntent = Intent(context, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        try {
            context.startActivity(launchIntent)
            Log.i(TAG, "Boot launch → ${target.simpleName} action=$action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ${target.simpleName} after boot ($action)", e)
        }

        if (hotelConfig.isPaired() && KioskPolicy.isKioskModeEnabled(context)) {
            try {
                KioskWatchdogService.start(context.applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog start after boot failed", e)
            }
        }
    }
}
