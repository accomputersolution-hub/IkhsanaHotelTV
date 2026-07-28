package `in`.pcncloud.hotel.receiver

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.PairingActivity
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService

/**
 * Auto-starts the hotel UI after device restart.
 * Paired → [MainActivity]; unpaired → [PairingActivity].
 *
 * On Android 14+ (API 34 / 35 / 36), bare [Context.startActivity] from a
 * [BroadcastReceiver] is blocked by Background Activity Launch restrictions.
 * We launch via [PendingIntent] + [ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED],
 * and start the kiosk foreground service first when kiosk is ON.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_REQUEST_CODE = 1001

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

        val appContext = context.applicationContext
        val hotelConfig = HotelConfig(appContext)
        val isKioskActive = KioskPolicy.isKioskModeEnabled(appContext)
        Log.i(
            TAG,
            "Boot event → action=$action paired=${hotelConfig.isPaired()} " +
                "kiosk=$isKioskActive hotel=${hotelConfig.getHotelId()} " +
                "room=${hotelConfig.getRoomNumberOrNull()}",
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

        // Android 14+: bring up FGS first when kiosk is ON — privileged path for BAL.
        if (hotelConfig.isPaired() && isKioskActive) {
            try {
                KioskWatchdogService.start(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog start after boot failed", e)
            }
        }

        launchUiAfterBoot(context, launchIntent, target.simpleName, action)
    }

    /**
     * Android 14 / 15 / 16: [PendingIntent.send] with background-activity-start allowed.
     * Older APIs: direct [Context.startActivity].
     */
    private fun launchUiAfterBoot(
        context: Context,
        launchIntent: Intent,
        targetName: String,
        action: String?,
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    BOOT_REQUEST_CODE,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                try {
                    val options = ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                    }
                    pendingIntent.send(
                        context,
                        0,
                        null,
                        null,
                        null,
                        null,
                        options.toBundle(),
                    )
                    Log.i(TAG, "Boot launch via PendingIntent → $targetName action=$action")
                } catch (e: Exception) {
                    Log.w(TAG, "PendingIntent.send failed, falling back to startActivity", e)
                    context.startActivity(launchIntent)
                    Log.i(TAG, "Boot launch fallback startActivity → $targetName action=$action")
                }
            } else {
                context.startActivity(launchIntent)
                Log.i(TAG, "Boot launch → $targetName action=$action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $targetName after boot ($action)", e)
        }
    }
}
