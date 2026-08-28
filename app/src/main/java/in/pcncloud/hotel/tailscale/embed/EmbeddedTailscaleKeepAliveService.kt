package `in`.pcncloud.hotel.tailscale.embed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.ui.home.BrandAssets

/**
 * Keeps embedded Tailscale engine warm and re-runs [EmbeddedTailscaleEngine.ensureRunning] after boot.
 */
class EmbeddedTailscaleKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (BuildConfig.IS_CORPORATE && EmbeddedTailscaleEngine.isVpnPrepared(this)) {
            EmbeddedTailscaleEngine.ensureRunning(this)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
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
        private const val TAG = "EmbeddedTsKeepAlive"
        private const val CHANNEL_ID = "embedded_tailscale_vpn"
        private const val NOTIFICATION_ID = 42043
        const val ACTION_STOP = "in.pcncloud.hotel.embedded.KEEPALIVE_STOP"

        fun start(app: Context) {
            if (!BuildConfig.IS_CORPORATE) return
            try {
                val intent = Intent(app, EmbeddedTailscaleKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "start failed", t)
            }
        }

        fun showForegroundNotification(service: Service) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    service.getString(R.string.tailscale_vpn_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
                service.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(service, CHANNEL_ID)
                .setSmallIcon(BrandAssets.logoRes)
                .setContentTitle(service.getString(R.string.tailscale_vpn_notification_title))
                .setContentText(service.getString(R.string.tailscale_vpn_notification_text))
                .setOngoing(true)
                .build()
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }
}
