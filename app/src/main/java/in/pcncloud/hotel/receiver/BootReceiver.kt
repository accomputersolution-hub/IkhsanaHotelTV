package `in`.pcncloud.hotel.receiver

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.PairingActivity
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.integration.TailscaleWakeHelper
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService

/**
 * Auto-starts the hotel UI after device restart.
 * Paired → [MainActivity]; unpaired → [PairingActivity].
 *
 * Bare [Context.startActivity] from a [BroadcastReceiver] is suppressed by
 * Background Activity Launch (BAL) on modern Android (and flaky on 9/10+).
 * Always launch via [PendingIntent.send]; on API 34+ also pass
 * [ActivityOptions.setPendingIntentBackgroundActivityStartMode].
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
            "com.android.internal.intent.action.QUICKBOOT_POWERON",
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
        val canDrawOverlays = Settings.canDrawOverlays(appContext)
        Log.i(
            TAG,
            "Boot event → action=$action paired=${hotelConfig.isPaired()} " +
                "kiosk=$isKioskActive overlay=$canDrawOverlays " +
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
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        // PendingIntent launch first (BAL-safe). Watchdog after UI attempt.
        launchUiAfterBoot(context, launchIntent, target.simpleName, action)

        // Corporate only: brief Tailscale UI wake, then MainActivity REORDER_TO_FRONT.
        // Hotel flavor skips entirely. goAsync keeps the receiver alive for the 7s delay.
        if (BuildConfig.IS_CORPORATE) {
            val pendingResult = goAsync()
            try {
                TailscaleWakeHelper.wakeViaUiThenReturnToKiosk(appContext) {
                    try {
                        pendingResult.finish()
                    } catch (e: Exception) {
                        Log.w(TAG, "BootReceiver PendingResult.finish failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tailscale UI wake after boot failed", e)
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        }

        if (hotelConfig.isPaired() && isKioskActive) {
            try {
                KioskWatchdogService.start(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog start after boot failed", e)
            }
        }
    }

    /**
     * Always prefer [PendingIntent.send] over bare [Context.startActivity] so
     * Android 9/10+ and 14–16 do not suppress the boot UI launch.
     */
    private fun launchUiAfterBoot(
        context: Context,
        launchIntent: Intent,
        targetName: String,
        action: String?,
    ) {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                BOOT_REQUEST_CODE,
                launchIntent,
                piFlags,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = balActivityOptions()
                try {
                    pendingIntent.send(
                        context,
                        0,
                        null,
                        null,
                        null,
                        null,
                        options.toBundle(),
                    )
                    Log.i(TAG, "Boot launch via PendingIntent+BAL → $targetName action=$action")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "PendingIntent+BAL send failed", e)
                    try {
                        context.startActivity(launchIntent, options.toBundle())
                        Log.i(TAG, "Boot launch fallback startActivity+BAL → $targetName")
                        return
                    } catch (e2: Exception) {
                        Log.e(TAG, "Fallback startActivity+BAL failed for $targetName", e2)
                    }
                }
            }

            try {
                pendingIntent.send()
                Log.i(TAG, "Boot launch via PendingIntent → $targetName action=$action")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send pending intent", e)
                context.startActivity(launchIntent)
                Log.i(TAG, "Boot launch fallback startActivity → $targetName action=$action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $targetName after boot ($action)", e)
            try {
                context.startActivity(launchIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Final startActivity fallback failed for $targetName", e2)
            }
        }
    }

    /**
     * Explicit BAL opt-in for API 34+ / 36 boot launches (app not visible yet).
     * API 36 deprecates [ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED]
     * in favor of [ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS].
     */
    private fun balActivityOptions(): ActivityOptions =
        ActivityOptions.makeBasic().apply {
            val mode = if (Build.VERSION.SDK_INT >= 36) {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            } else {
                @Suppress("DEPRECATION")
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            setPendingIntentBackgroundActivityStartMode(mode)
        }
}
