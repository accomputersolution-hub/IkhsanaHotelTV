package `in`.pcncloud.hotel.tailscale.embed

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.system.OsConstants
import android.util.Log
import `in`.pcncloud.hotel.MainActivity
import java.util.UUID
import libtailscale.Libtailscale

/**
 * In-process Tailscale TUN owner. Per-app split tunnel: only [EmbeddedTailscaleCredentials.SPLIT_TUNNEL_PACKAGE].
 */
class EmbeddedTailscaleVpnService : VpnService(), libtailscale.IPNService {

    private val serviceId = UUID.randomUUID().toString()
  private var closed = false

    override fun onCreate() {
        super.onCreate()
        EmbeddedTailscaleEngine.onVpnServiceCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                close()
                return START_NOT_STICKY
            }
            ACTION_START_VPN, "android.net.VpnService" -> {
                EmbeddedTailscaleKeepAliveService.showForegroundNotification(this)
                Libtailscale.requestVPN(this)
                return START_STICKY
            }
        }
        if (EmbeddedTailscaleEngine.isAbleToStartVpn()) {
            EmbeddedTailscaleKeepAliveService.showForegroundNotification(this)
            Libtailscale.requestVPN(this)
            return START_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }

    override fun onRevoke() {
        EmbeddedTailscaleEngine.onVpnRevoked()
        close()
        super.onRevoke()
    }

    override fun id(): String = serviceId

    override fun updateVpnStatus(active: Boolean) {
        EmbeddedTailscaleEngine.onVpnActiveChanged(active)
    }

    override fun protect(fd: Int): Boolean = super.protect(fd)

    override fun newBuilder(): libtailscale.VPNServiceBuilder {
        val builder = Builder()
            .setConfigureIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        builder.setUnderlyingNetworks(null)

        try {
            builder.addAllowedApplication(EmbeddedTailscaleCredentials.SPLIT_TUNNEL_PACKAGE)
            Log.i(TAG, "Per-app VPN → only ${EmbeddedTailscaleCredentials.SPLIT_TUNNEL_PACKAGE}")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Split-tunnel package missing: ${EmbeddedTailscaleCredentials.SPLIT_TUNNEL_PACKAGE}", e)
        }

        return TailscaleVpnBuilderAdapter(builder)
    }

    override fun close() {
        if (closed) return
        closed = true
        disconnectVPN()
        Libtailscale.serviceDisconnect(this)
    }

    override fun disconnectVPN() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "EmbeddedTsVpnService"
        const val ACTION_START_VPN = "in.pcncloud.hotel.embedded.START_VPN"
        const val ACTION_STOP = "in.pcncloud.hotel.embedded.STOP_VPN"
    }
}
