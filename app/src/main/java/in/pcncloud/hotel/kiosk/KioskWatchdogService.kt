package `in`.pcncloud.hotel.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.SplashActivity

/**
 * Foreground keep-alive for hotel kiosk.
 *
 * - Every [ACCESSIBILITY_HEAL_INTERVAL_MS]: re-enable [HomeKeyInterceptorService]
 *   if OEM HOME turned accessibility off while kiosk is ON.
 * - Every [POLL_INTERVAL_MS]: optional bring-to-front when policy allows
 *   (never while [KioskPolicy.isExternalAppActive]).
 */
class KioskWatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private var bringPollTicks: Int = 0

    private val healRunnable = object : Runnable {
        override fun run() {
            healAccessibilityIfNeeded()
            // Bring-to-front less often than the 1s accessibility heal.
            bringPollTicks++
            if (bringPollTicks >= BRING_POLL_EVERY_N_HEALS) {
                bringPollTicks = 0
                maybeBringToFront("watchdog_poll")
            }
            handler.postDelayed(this, ACCESSIBILITY_HEAL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            TAG,
            "onStartCommand flags=$flags startId=$startId action=${intent?.action} " +
                "kiosk=${KioskPolicy.isKioskModeEnabled(this)}",
        )

        when (intent?.action) {
            ACTION_STOP -> {
                handler.removeCallbacks(healRunnable)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_NOW -> {
                healAccessibilityIfNeeded()
                maybeBringToFront("check_now")
            }
            else -> {
                if (KioskPolicy.isKioskModeEnabled(this)) {
                    handler.removeCallbacks(healRunnable)
                    handler.post(healRunnable)
                } else {
                    handler.removeCallbacks(healRunnable)
                    Log.i(TAG, "Kiosk off — watchdog will not poll")
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(healRunnable)
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    /**
     * If kiosk is ON and HomeKeyInterceptor was killed (common after OEM HOME),
     * silently rewrite Secure settings to turn it back on.
     */
    private fun healAccessibilityIfNeeded() {
        if (!KioskPolicy.isKioskModeEnabled(this)) {
            handler.removeCallbacks(healRunnable)
            return
        }
        if (HomeKeyInterceptorService.isEnabled(this)) {
            return
        }
        Log.w(TAG, "HomeKeyInterceptor OFF while kiosk ON — attempting self-heal")
        val ok = HomeKeyInterceptorService.tryReenableAccessibility(this)
        Log.i(TAG, "Accessibility self-heal result=$ok")
    }

    private fun maybeBringToFront(reason: String) {
        if (KioskPolicy.isExternalAppActive(this)) {
            Log.d(TAG, "maybeBringToFront skipped — isExternalAppActive=true ($reason)")
            return
        }
        if (!KioskPolicy.isKioskModeEnabled(this)) {
            Log.d(TAG, "maybeBringToFront skipped — kiosk disabled ($reason)")
            handler.removeCallbacks(healRunnable)
            return
        }
        if (!KioskPolicy.shouldBringAppToFront(this)) {
            return
        }

        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, SplashActivity::class.java)
        KioskPolicy.startActivityIfAllowed(this, launch, reason)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.kiosk_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.kiosk_notification_channel_desc)
                    setShowBadge(false)
                },
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SplashActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.kiosk_notification_title))
            .setContentText(getString(R.string.kiosk_notification_text))
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "KioskWatchdog"
        private const val CHANNEL_ID = "hotel_tv_kiosk"
        private const val NOTIFICATION_ID = 1001
        /** Accessibility self-heal cadence (user requirement: every 1 second). */
        private const val ACCESSIBILITY_HEAL_INTERVAL_MS = 1_000L
        /** Bring-to-front every N heal ticks (~30s). */
        private const val BRING_POLL_EVERY_N_HEALS = 30

        const val ACTION_CHECK_NOW = "in.pcncloud.hotel.kiosk.CHECK_NOW"
        const val ACTION_STOP = "in.pcncloud.hotel.kiosk.STOP"

        fun start(context: Context) {
            val intent = Intent(context, KioskWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, KioskWatchdogService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
