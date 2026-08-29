package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the official WireGuard [GoBackend] (userspace Go / libwg-go) and drives
 * tunnel UP/DOWN. The TUN interface itself is created by
 * [GoBackend.VpnService] — do not replace that class; GoBackend starts it by name.
 */
object WireGuardEngine {
    private const val TAG = "WireGuardEngine"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    private var appContext: Context? = null
    private var backend: GoBackend? = null
    private val tunnel = WireGuardAppTunnel()

    private val _tunnelState = MutableStateFlow(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState.asStateFlow()

    private var lastConfig: WireGuardTunnelConfig? = null

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        appContext = app
        backend = GoBackend(app)
        GoBackend.setAlwaysOnCallback {
            Log.i(TAG, "Always-On VPN triggered — re-asserting tunnel via provisioner")
            appContext?.let { ctx ->
                scope.launch {
                    WireGuardProvisioner.provisionAndConnect(ctx)
                }
            }
        }
        Log.i(TAG, "GoBackend ready version=${runCatching { backend?.version }.getOrNull()}")
    }

    /** Null when VPN consent already granted; otherwise launch this Intent. */
    fun preparePermissionIntent(context: Context): Intent? =
        VpnService.prepare(context.applicationContext)

    fun isVpnPrepared(context: Context): Boolean =
        VpnService.prepare(context.applicationContext) == null

    fun connect(
        config: WireGuardTunnelConfig = WireGuardCredentials.toTunnelConfig(),
        context: Context? = null,
    ) {
        if (!config.isComplete()) {
            Log.w(TAG, "connect skipped — incomplete WireGuard config")
            return
        }
        val app = context?.applicationContext ?: appContext
        if (app == null) {
            Log.w(TAG, "connect skipped — engine not initialized")
            return
        }
        init(app)
        lastConfig = config
        WireGuardKeepAliveService.start(app)
        scope.launch {
            mutex.withLock {
                try {
                    val go = backend ?: return@withLock
                    if (!isVpnPrepared(app)) {
                        Log.w(TAG, "VpnService.prepare() not granted — wait for Activity consent")
                        return@withLock
                    }
                    val wgConfig = WireGuardConfigFactory.build(config)
                    Log.i(
                        TAG,
                        "Bringing tunnel UP endpoint=${config.endpoint} " +
                            "address=${config.address} allowedIps=${config.allowedIps}",
                    )
                    val state = go.setState(tunnel, Tunnel.State.UP, wgConfig)
                    _tunnelState.value = state
                    Log.i(TAG, "setState(UP) → $state")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to bring WireGuard tunnel UP", t)
                    _tunnelState.value = Tunnel.State.DOWN
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            mutex.withLock {
                try {
                    val go = backend ?: return@withLock
                    val state = go.setState(tunnel, Tunnel.State.DOWN, null)
                    _tunnelState.value = state
                    Log.i(TAG, "setState(DOWN) → $state")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to bring WireGuard tunnel DOWN", t)
                } finally {
                    appContext?.let { WireGuardKeepAliveService.stop(it) }
                }
            }
        }
    }

    fun ensureRunning(context: Context) {
        init(context)
        if (!isVpnPrepared(context)) {
            Log.d(TAG, "ensureRunning — VPN consent required")
            return
        }
        // Prefer last successful config; otherwise full provision (keygen + add-peer).
        val cached = lastConfig
        if (cached != null && cached.isComplete()) {
            connect(cached, context)
            return
        }
        scope.launch {
            WireGuardProvisioner.provisionAndConnect(context)
        }
    }
}
