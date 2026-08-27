package `in`.pcncloud.hotel.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import `in`.pcncloud.hotel.BuildConfig
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts / stops / reconnects the built-in WireGuard tunnel with no external UI.
 * Corporate flavor only.
 */
object KioskVpnController {

    private const val TAG = "KioskVpnController"
    private const val TUNNEL_NAME = "kiosk"
    private const val RECONNECT_DELAY_MS = 5_000L
    private const val MAX_RECONNECT_ATTEMPTS = 24

    private val io = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val starting = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    @Volatile
    private var backend: GoBackend? = null

    @Volatile
    private var reconnectAttempts = 0

    @Volatile
    private var lastAppContext: Context? = null

    private val reconnectRunnable = Runnable {
        lastAppContext?.let { ensureRunning(it, fromReconnect = true) }
    }

    private val kioskTunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            Log.i(TAG, "Tunnel state → $newState")
            when (newState) {
                Tunnel.State.UP -> {
                    reconnectAttempts = 0
                    mainHandler.removeCallbacks(reconnectRunnable)
                }
                Tunnel.State.DOWN -> scheduleReconnect()
                else -> Unit
            }
        }
    }

    /**
     * Must run once early (Application.onCreate). Registers Always-On callback so when
     * the system starts [GoBackend.VpnService] we immediately bring the tunnel UP.
     */
    fun init(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        val app = context.applicationContext
        lastAppContext = app
        if (!initialized.compareAndSet(false, true)) return
        try {
            GoBackend.setAlwaysOnCallback {
                Log.i(TAG, "Always-On VPN triggered — ensuring tunnel")
                ensureRunning(app, fromReconnect = true)
            }
            Log.i(TAG, "GoBackend AlwaysOnCallback registered")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register AlwaysOnCallback", t)
        }
    }

    fun preparePermissionIntent(context: Context): Intent? =
        try {
            VpnService.prepare(context)
        } catch (t: Throwable) {
            Log.e(TAG, "VpnService.prepare failed", t)
            null
        }

    /** True when system VPN consent has already been granted (or Always-On authorized). */
    fun isVpnPrepared(context: Context): Boolean =
        try {
            VpnService.prepare(context.applicationContext) == null
        } catch (t: Throwable) {
            Log.e(TAG, "isVpnPrepared failed", t)
            false
        }

    fun isTunnelUp(): Boolean =
        try {
            val b = backend ?: return false
            b.getState(kioskTunnel) == Tunnel.State.UP
        } catch (_: Throwable) {
            false
        }

    /**
     * Bring the tunnel up if a usable config exists and VPN consent is granted.
     * Safe to call from Application / Boot / Splash / Main.
     */
    fun ensureRunning(context: Context, fromReconnect: Boolean = false) {
        if (!BuildConfig.IS_CORPORATE) {
            Log.d(TAG, "ensureRunning skipped — not corporate")
            return
        }
        init(context)
        val app = context.applicationContext
        lastAppContext = app

        if (!KioskVpnConfigStore.hasUsableConfig(app)) {
            Log.w(TAG, "No usable WireGuard config yet — internal VPN idle")
            return
        }

        if (!fromReconnect && !starting.compareAndSet(false, true)) {
            Log.d(TAG, "ensureRunning already in progress")
            return
        }

        io.execute {
            try {
                Log.i(
                    TAG,
                    "ensureRunning begin prepared=${isVpnPrepared(app)} " +
                        "endpoint=${KioskVpnCredentials.ENDPOINT} " +
                        "serverPub=${KioskVpnCredentials.SERVER_PUBLIC_KEY.take(12)}…",
                )
                startShell(app)
                val prep = VpnService.prepare(app)
                if (prep != null) {
                    Log.w(TAG, "VPN consent required — offering prepare Intent (tunnel not UP yet)")
                    KioskVpnPermissionBridge.offer(prep)
                    return@execute
                }
                bringTunnelUp(app)
            } catch (t: Throwable) {
                Log.e(TAG, "ensureRunning failed", t)
                scheduleReconnect()
            } finally {
                if (!fromReconnect) starting.set(false)
            }
        }
    }

    /** Called from [KioskVpnService] after FG start when permission is already granted. */
    fun bringTunnelUpIfPrepared(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        init(context)
        io.execute {
            try {
                if (VpnService.prepare(context.applicationContext) != null) {
                    Log.d(TAG, "bringTunnelUpIfPrepared — waiting for consent")
                    return@execute
                }
                if (!KioskVpnConfigStore.hasUsableConfig(context)) return@execute
                if (isTunnelUp()) {
                    Log.d(TAG, "bringTunnelUpIfPrepared — already UP")
                    return@execute
                }
                bringTunnelUp(context.applicationContext)
            } catch (t: Throwable) {
                Log.e(TAG, "bringTunnelUpIfPrepared failed", t)
                scheduleReconnect()
            }
        }
    }

    fun stop(context: Context) {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS
        io.execute {
            try {
                val b = backend ?: GoBackend(context.applicationContext).also { backend = it }
                b.setState(kioskTunnel, Tunnel.State.DOWN, null)
            } catch (t: Throwable) {
                Log.w(TAG, "stop tunnel failed", t)
            }
            try {
                context.applicationContext.stopService(Intent(context, KioskVpnService::class.java))
            } catch (_: Throwable) {
            }
            KioskVpnKeepAliveService.stop(context)
            reconnectAttempts = 0
        }
    }

    fun onVpnRevoked(context: Context) {
        reconnectAttempts = 0
        try {
            backend?.setState(kioskTunnel, Tunnel.State.DOWN, null)
        } catch (_: Throwable) {
        }
        scheduleReconnect()
    }

    private fun bringTunnelUp(app: Context) {
        val confText = KioskVpnConfigStore.loadConfigText(app)
            ?: throw IllegalStateException("missing WireGuard config")
        Log.i(TAG, "Bringing tunnel UP with conf:\n$confText")

        // Pre-warm GoBackend.VpnService so setState's 2s wait does not time out.
        prewarmGoVpnService(app)

        val config = Config.parse(
            ByteArrayInputStream(confText.toByteArray(StandardCharsets.UTF_8)),
        )
        val b = backend ?: GoBackend(app).also { backend = it }
        val state = b.setState(kioskTunnel, Tunnel.State.UP, config)
        reconnectAttempts = 0
        Log.i(
            TAG,
            "WireGuard setState → $state ($TUNNEL_NAME) " +
                "addr=${KioskVpnCredentials.CLIENT_ADDRESS} " +
                "endpoint=${KioskVpnCredentials.ENDPOINT} " +
                "allowed=${KioskVpnCredentials.ALLOWED_IPS}",
        )
        if (state != Tunnel.State.UP) {
            throw IllegalStateException("tunnel not UP after setState ($state)")
        }
        KioskVpnKeepAliveService.start(app)
        startShell(app)
    }

    private fun prewarmGoVpnService(app: Context) {
        try {
            val intent = Intent(app, GoBackend.VpnService::class.java)
            app.startService(intent)
            Log.i(TAG, "Pre-warmed GoBackend.VpnService")
        } catch (t: Throwable) {
            Log.w(TAG, "Pre-warm GoBackend.VpnService failed (setState will retry)", t)
        }
        // Brief yield so onCreate can complete the GoBackend future before setState's 2s wait.
        try {
            Thread.sleep(300L)
        } catch (_: InterruptedException) {
        }
    }

    private fun startShell(app: Context) {
        val intent = Intent(app, KioskVpnService::class.java).setAction(KioskVpnService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                app.startService(intent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "startShell failed (will retry from Splash/Boot/Main)", t)
        }
    }

    private fun scheduleReconnect() {
        if (!BuildConfig.IS_CORPORATE) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached")
            return
        }
        reconnectAttempts++
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
        Log.i(TAG, "Scheduled reconnect #$reconnectAttempts in ${RECONNECT_DELAY_MS}ms")
    }
}
