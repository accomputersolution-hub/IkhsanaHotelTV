package `in`.pcncloud.hotel.wireguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import `in`.pcncloud.hotel.R

/**
 * Foreground keep-alive for the WireGuard Go process.
 *
 * This is **not** the TUN owner — [com.wireguard.android.backend.GoBackend.VpnService]
 * creates the tunnel. This service only keeps the app process warm on Android TV.
 */
class WireGuardKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> promoteToForeground()
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        ensureChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wireguard_vpn_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.wireguard_vpn_channel_desc)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wireguard_vpn_notification_title))
            .setContentText(getString(R.string.wireguard_vpn_keepalive_text))
            .setSmallIcon(R.drawable.pcn_logo)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    companion object {
        private const val TAG = "WireGuardKeepAlive"
        private const val CHANNEL_ID = "wireguard_vpn"
        private const val NOTIFICATION_ID = 0x5747 // 'WG'
        const val ACTION_START = "in.pcncloud.hotel.wireguard.START_KEEPALIVE"
        const val ACTION_STOP = "in.pcncloud.hotel.wireguard.STOP_KEEPALIVE"

        fun start(context: Context) {
            val intent = Intent(context, WireGuardKeepAliveService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WireGuardKeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "stop keep-alive failed", t)
            }
        }
    }
}
