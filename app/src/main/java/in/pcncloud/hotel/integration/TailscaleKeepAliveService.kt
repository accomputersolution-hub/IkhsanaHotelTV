package `in`.pcncloud.hotel.integration

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.ui.home.BrandAssets
import `in`.pcncloud.hotel.R

/**
 * Lightweight corporate keep-alive that periodically re-asserts Tailscale VPN
 * after boot or process death on Android TV.
 */
class TailscaleKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (BuildConfig.IS_CORPORATE) {
            TailscaleController.ensureRunning(this)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tailscale_vpn_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.tailscale_vpn_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(BrandAssets.logoRes)
            .setContentTitle(getString(R.string.tailscale_vpn_notification_title))
            .setContentText(getString(R.string.tailscale_vpn_keepalive_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "TailscaleKeepAlive"
        private const val CHANNEL_ID = "tailscale_vpn"
        private const val NOTIFICATION_ID = 42042
        const val ACTION_STOP = "in.pcncloud.hotel.tailscale.KEEPALIVE_STOP"

        fun start(app: android.content.Context) {
            if (!BuildConfig.IS_CORPORATE) return
            try {
                val intent = Intent(app, TailscaleKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "start failed", t)
            }
        }

        fun stop(app: android.content.Context) {
            try {
                app.stopService(Intent(app, TailscaleKeepAliveService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "stop failed", t)
            }
        }
    }
}
