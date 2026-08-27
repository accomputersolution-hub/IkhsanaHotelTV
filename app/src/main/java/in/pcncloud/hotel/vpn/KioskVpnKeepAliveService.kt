package `in`.pcncloud.hotel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.ui.home.BrandAssets

/**
 * Lightweight foreground watchdog that periodically re-asserts the internal
 * WireGuard tunnel after boot / process death on Android TV.
 */
class KioskVpnKeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if (BuildConfig.IS_CORPORATE) {
                KioskVpnController.ensureRunning(this@KioskVpnKeepAliveService)
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        handler.postDelayed(poll, POLL_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.removeCallbacks(poll)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.kiosk_vpn_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.kiosk_vpn_notification_title))
            .setContentText(getString(R.string.kiosk_vpn_keepalive_text))
            .setSmallIcon(BrandAssets.logoRes)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        private const val TAG = "KioskVpnKeepAlive"
        private const val CHANNEL_ID = "kiosk_vpn"
        private const val NOTIFICATION_ID = 1102
        private const val POLL_INTERVAL_MS = 60_000L
        const val ACTION_STOP = "in.pcncloud.hotel.vpn.KEEPALIVE_STOP"

        fun start(context: Context) {
            if (!BuildConfig.IS_CORPORATE) return
            val app = context.applicationContext
            val intent = Intent(app, KioskVpnKeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "start failed", t)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            try {
                app.startService(
                    Intent(app, KioskVpnKeepAliveService::class.java).setAction(ACTION_STOP),
                )
            } catch (_: Throwable) {
            }
            try {
                app.stopService(Intent(app, KioskVpnKeepAliveService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}
