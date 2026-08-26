package `in`.pcncloud.hotel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R

/**
 * App-owned [VpnService] for the corporate kiosk VPN.
 *
 * WireGuard [com.wireguard.android.backend.GoBackend] owns the TUN fd through its
 * nested [com.wireguard.android.backend.GoBackend.VpnService] (declared with the
 * `android.net.VpnService` intent-filter so Device Owner Always-On sees exactly one
 * VPN service). This service:
 * - runs as a foreground service so Android TV does not kill VPN keep-alive
 * - re-triggers [KioskVpnController] on start / reconnect / system restart
 * - does **not** register the VpnService intent-filter (avoids Always-On dual-match)
 */
class KioskVpnService : VpnService() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startAsForeground()
        Log.i(TAG, "KioskVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                KioskVpnController.stop(applicationContext)
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // START / RECONNECT / null (system restart after process death)
                KioskVpnController.bringTunnelUpIfPrepared(applicationContext)
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked")
        KioskVpnController.onVpnRevoked(applicationContext)
        super.onRevoke()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "KioskVpnService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    private fun startAsForeground() {
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "KioskVpnService"
        const val ACTION_START = "in.pcncloud.hotel.vpn.START"
        const val ACTION_STOP = "in.pcncloud.hotel.vpn.STOP"
        const val ACTION_RECONNECT = "in.pcncloud.hotel.vpn.RECONNECT"
        private const val CHANNEL_ID = "kiosk_vpn"
        private const val NOTIFICATION_ID = 7101

        fun start(context: Context) {
            if (!BuildConfig.IS_CORPORATE) return
            val intent = Intent(context, KioskVpnService::class.java).setAction(ACTION_START)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "startForegroundService failed", t)
            }
        }

        fun reconnect(context: Context) {
            if (!BuildConfig.IS_CORPORATE) return
            val intent = Intent(context, KioskVpnService::class.java).setAction(ACTION_RECONNECT)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "reconnect start failed", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, KioskVpnService::class.java).setAction(ACTION_STOP),
                )
            } catch (t: Throwable) {
                Log.w(TAG, "stop failed", t)
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.kiosk_vpn_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.kiosk_vpn_channel_desc)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context): Notification {
            val launch = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.kiosk_vpn_notification_title))
                .setContentText(context.getString(R.string.kiosk_vpn_notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(launch)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }
    }
}
