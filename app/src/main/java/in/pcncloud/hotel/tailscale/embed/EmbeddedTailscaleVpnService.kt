package `in`.pcncloud.hotel.tailscale.embed

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.OsConstants
import android.util.Log
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import java.util.UUID
import libtailscale.Libtailscale

/**
 * In-process Tailscale TUN owner. Per-app split tunnel: only [EmbeddedTailscaleCredentials.SPLIT_TUNNEL_PACKAGE].
 */
class EmbeddedTailscaleVpnService : VpnService(), libtailscale.IPNService {

    private val serviceId = UUID.randomUUID().toString()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var closed = false
    private var vpnRequestIssued = false
    private var vpnRetryCount = 0

    private val vpnRetryRunnable = Runnable { attemptRequestVpnWhenReady() }

    override fun onCreate() {
        super.onCreate()
        enterForegroundImmediately()
        EmbeddedTailscaleEngine.onVpnServiceCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForegroundImmediately()
        EmbeddedTailscaleEngine.onVpnServiceStartHandled()

        when (intent?.action) {
            ACTION_STOP -> {
                close()
                return START_NOT_STICKY
            }
        }

        if (!EmbeddedTailscaleEngine.isVpnPrepared(this)) {
            Log.w(TAG, "VpnService.prepare() not granted — stop until Activity consent")
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        scheduleRequestVpnWhenReady()
        return START_STICKY
    }

    private fun scheduleRequestVpnWhenReady() {
        vpnRetryCount = 0
        vpnRequestIssued = false
        mainHandler.removeCallbacks(vpnRetryRunnable)
        mainHandler.post(vpnRetryRunnable)
    }

    private fun attemptRequestVpnWhenReady() {
        if (closed) return

        if (!EmbeddedTailscaleEngine.isVpnPrepared(this)) {
            Log.w(TAG, "VPN consent revoked — stopping VpnService")
            stopForegroundAndSelf()
            return
        }

        if (!EmbeddedTailscaleEngine.isGoBackendReady()) {
            retryOrTimeout("Go backend not ready")
            return
        }

        if (!EmbeddedTailscaleEngine.isReadyForRequestVpn()) {
            retryOrTimeout(
                "Go engine not ready for VPN (state=${EmbeddedTailscaleNotifier.state.value})",
            )
            return
        }

        if (vpnRequestIssued) return
        vpnRequestIssued = true
        requestVpnTunnel()
    }

    private fun retryOrTimeout(reason: String) {
        if (vpnRetryCount >= MAX_VPN_RETRY_ATTEMPTS) {
            Log.e(TAG, "Timed out waiting for Go engine — $reason")
            stopForegroundAndSelf()
            return
        }
        vpnRetryCount++
        Log.d(TAG, "$reason — retry $vpnRetryCount/$MAX_VPN_RETRY_ATTEMPTS")
        mainHandler.postDelayed(vpnRetryRunnable, VPN_RETRY_MS)
    }

    private fun requestVpnTunnel() {
        if (!EmbeddedTailscaleEngine.isReadyForRequestVpn()) {
            Log.w(TAG, "requestVPN skipped — engine not ready")
            vpnRequestIssued = false
            scheduleRequestVpnWhenReady()
            return
        }
        enterForegroundImmediately()
        Log.i(TAG, "Calling Libtailscale.requestVPN (state=${EmbeddedTailscaleNotifier.state.value})")
        Libtailscale.requestVPN(this)
    }

    private fun enterForegroundImmediately() {
        EmbeddedTailscaleKeepAliveService.promoteToForeground(
            this,
            R.string.tailscale_vpn_notification_text,
            EmbeddedTailscaleKeepAliveService.VPN_SERVICE_NOTIFICATION_ID,
        )
    }

    private fun stopForegroundAndSelf() {
        mainHandler.removeCallbacks(vpnRetryRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(vpnRetryRunnable)
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
        mainHandler.removeCallbacks(vpnRetryRunnable)
        if (EmbeddedTailscaleEngine.isGoBackendReady()) {
            disconnectVPN()
            Libtailscale.serviceDisconnect(this)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun disconnectVPN() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "EmbeddedTsVpnService"
        private const val VPN_RETRY_MS = 500L
        private const val MAX_VPN_RETRY_ATTEMPTS = 120
        const val ACTION_START_VPN = "in.pcncloud.hotel.embedded.START_VPN"
        const val ACTION_STOP = "in.pcncloud.hotel.embedded.STOP_VPN"
    }
}
