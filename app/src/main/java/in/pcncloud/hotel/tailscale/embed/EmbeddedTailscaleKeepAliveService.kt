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
        promoteToForeground(this, R.string.tailscale_vpn_keepalive_text, NOTIFICATION_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (BuildConfig.IS_CORPORATE && EmbeddedTailscaleEngine.isVpnPrepared(this) &&
            EmbeddedTailscaleEngine.shouldKeepAliveReassert()
        ) {
            EmbeddedTailscaleEngine.ensureRunning(this)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "EmbeddedTsKeepAlive"
        private const val CHANNEL_ID = "embedded_tailscale_vpn"
        private const val NOTIFICATION_ID = 42043
        const val VPN_SERVICE_NOTIFICATION_ID = 42044
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

        /** Must run within seconds of [Context.startForegroundService] — avoids RemoteServiceException. */
        fun promoteToForeground(
            service: Service,
            contentTextRes: Int = R.string.tailscale_vpn_notification_text,
            notificationId: Int = NOTIFICATION_ID,
        ) {
            ensureNotificationChannel(service)
            service.startForeground(notificationId, buildNotification(service, contentTextRes))
        }

        fun showForegroundNotification(service: Service) {
            promoteToForeground(service)
        }

        private fun ensureNotificationChannel(service: Service) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                service.getString(R.string.tailscale_vpn_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = service.getString(R.string.tailscale_vpn_channel_desc)
                setShowBadge(false)
            }
            service.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        private fun buildNotification(service: Service, contentTextRes: Int): Notification {
            val openIntent = PendingIntent.getActivity(
                service,
                0,
                Intent(service, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(service, CHANNEL_ID)
                .setSmallIcon(BrandAssets.logoRes)
                .setContentTitle(service.getString(R.string.tailscale_vpn_notification_title))
                .setContentText(service.getString(contentTextRes))
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
