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
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R

/**
 * Foreground keep-alive for optional hotel kiosk / overlay scenarios.
 *
 * **Critical:** [START_STICKY] must NOT relaunch the Activity on service re-create.
 * Bring-to-front runs only when [KioskPolicy.shouldBringAppToFront] is true
 * (explicit kiosk mode or crash recovery) — never on ordinary minimize, and
 * never while [KioskPolicy.isExternalAppActive] (YouTube / OTT viewing).
 *
 * Reclaim targets [MainActivity] (never Splash) to avoid EGL BufferQueue
 * abandoned crashes from destroying splash mid-frame.
 */
class KioskWatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            maybeBringToFront("watchdog_poll")
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        startAsForeground()
        // Intentionally no startActivity here — sticky restarts must not pop the UI.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            TAG,
            "onStartCommand flags=$flags startId=$startId action=${intent?.action} " +
                "kiosk=${KioskPolicy.isKioskModeEnabled(this)}",
        )

        when (intent?.action) {
            ACTION_STOP -> {
                handler.removeCallbacks(pollRunnable)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_NOW -> maybeBringToFront("check_now")
            else -> {
                // Sticky re-delivery / first start: never auto-launch Activity.
                if (KioskPolicy.isKioskModeEnabled(this)) {
                    handler.removeCallbacks(pollRunnable)
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
                } else {
                    handler.removeCallbacks(pollRunnable)
                    Log.i(TAG, "Kiosk off — watchdog will not poll for bring-to-front")
                }
            }
        }

        // Survive process reclaim, but UI relaunch is gated in [maybeBringToFront].
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun maybeBringToFront(reason: String) {
        // Hard gate: never steal focus from YouTube / Netflix / Live TV / etc.
        if (KioskPolicy.isExternalAppActive(this)) {
            Log.d(TAG, "maybeBringToFront skipped — isExternalAppActive=true ($reason)")
            return
        }
        // Hard gate: never relaunch UI while kiosk is disabled.
        if (!KioskPolicy.isKioskModeEnabled(this)) {
            Log.d(TAG, "maybeBringToFront skipped — kiosk disabled ($reason)")
            handler.removeCallbacks(pollRunnable)
            return
        }
        if (!KioskPolicy.shouldBringAppToFront(this)) {
            return
        }

        // Reclaim MainActivity only — Splash mid-frame finish → EGL 12301.
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
        }
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
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
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
        private const val POLL_INTERVAL_MS = 30_000L

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
            val app = context.applicationContext
            try {
                app.startService(
                    Intent(app, KioskWatchdogService::class.java).setAction(ACTION_STOP),
                )
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_STOP startService failed", e)
            }
            try {
                app.stopService(Intent(app, KioskWatchdogService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "stopService failed", e)
            }
        }
    }
}
