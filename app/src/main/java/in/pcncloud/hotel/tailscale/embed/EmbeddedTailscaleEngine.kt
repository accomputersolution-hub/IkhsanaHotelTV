package `in`.pcncloud.hotel.tailscale.embed

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import libtailscale.Libtailscale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton embedded Tailscale / Headscale engine (libtailscale in-process).
 */
object EmbeddedTailscaleEngine {

    private const val TAG = "EmbeddedTsEngine"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var goBackendReady = false
    private var loginInFlight = false
    private var loginSequenceComplete = false
    private var wantRunningApplied = false
    private var vpnActive = false
    private val vpnServiceStarting = AtomicBoolean(false)

    private var storedAppContext: Context? = null
    private lateinit var appContext: EmbeddedTailscaleAppContext
    private lateinit var goApp: libtailscale.Application
    private lateinit var localApi: EmbeddedTailscaleLocalApi

    /** Lightweight process init — does not start the Go backend (needs VPN consent first). */
    fun init(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        storedAppContext = context.applicationContext
    }

    fun isGoBackendReady(): Boolean = goBackendReady

    /** System VPN consent intent — non-null when user must approve via a visible Activity. */
    fun preparePermissionIntent(context: Context): Intent? =
        try {
            VpnService.prepare(context.applicationContext)
        } catch (t: Throwable) {
            Log.e(TAG, "VpnService.prepare failed", t)
            null
        }

    fun isVpnPrepared(context: Context): Boolean =
        try {
            VpnService.prepare(context.applicationContext) == null
        } catch (t: Throwable) {
            Log.e(TAG, "isVpnPrepared failed", t)
            false
        }

    /** After Activity VPN consent succeeds, continue login + VpnService start. */
    fun onVpnPermissionGranted(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        if (!isVpnPrepared(context)) {
            Log.w(TAG, "onVpnPermissionGranted but consent still missing")
            return
        }
        ensureRunning(context)
    }

    /**
     * Keep-alive may re-assert only when the engine is idle — not while Starting/Stopping.
     */
    fun shouldKeepAliveReassert(): Boolean {
        if (!goBackendReady) return storedAppContext != null
        val state = EmbeddedTailscaleNotifier.state.value
        return when (state) {
            EmbeddedTailscaleModels.State.Starting,
            EmbeddedTailscaleModels.State.Stopping,
            EmbeddedTailscaleModels.State.Running,
            -> false
            else -> !vpnActive
        }
    }

    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        storedAppContext = context.applicationContext

        val app = context.applicationContext
        if (!isVpnPrepared(app)) {
            Log.w(TAG, "VPN consent missing — defer ensureRunning until Activity grants prepare")
            return
        }

        initGoBackend(app)

        val state = EmbeddedTailscaleNotifier.state.value
        when (state) {
            EmbeddedTailscaleModels.State.Starting,
            EmbeddedTailscaleModels.State.Stopping,
            -> {
                Log.i(TAG, "Engine $state — skip ensureRunning (avoid shutdown/restart loop)")
                return
            }
            EmbeddedTailscaleModels.State.Running -> {
                if (vpnActive) {
                    Log.i(TAG, "VPN already active")
                    return
                }
                startVpnService(app)
                return
            }
            EmbeddedTailscaleModels.State.NoState,
            EmbeddedTailscaleModels.State.InUseOtherUser,
            EmbeddedTailscaleModels.State.NeedsLogin,
            EmbeddedTailscaleModels.State.NeedsMachineAuth,
            EmbeddedTailscaleModels.State.Stopped,
            -> Unit
        }

        if (loginInFlight) return

        if (loginSequenceComplete) {
            startVpnService(app)
            applyWantRunningIfNeeded()
            return
        }

        loginInFlight = true

        // WantRunning=false until VpnService is up — prevents Go Stopping loop when TUN is missing.
        val prefs = EmbeddedTailscaleModels.Prefs(
            ControlURL = EmbeddedTailscaleCredentials.CONTROL_URL,
            WantRunning = false,
        )
        val options = EmbeddedTailscaleModels.Options(
            AuthKey = EmbeddedTailscaleCredentials.AUTH_KEY,
            UpdatePrefs = prefs,
        )

        localApi.start(options) { startResult ->
            startResult.onFailure { e ->
                Log.e(TAG, "localapi start failed", e)
                loginInFlight = false
            }
            startResult.onSuccess {
                localApi.startLoginInteractive { loginResult ->
                    loginResult.onFailure { e ->
                        Log.e(TAG, "login-interactive failed", e)
                        loginInFlight = false
                    }
                    loginResult.onSuccess {
                        Log.i(TAG, "Headscale login sequence dispatched (WantRunning deferred)")
                        loginInFlight = false
                        loginSequenceComplete = true
                        startVpnService(app)
                    }
                }
            }
        }
    }

    fun isAbleToStartVpn(): Boolean {
        if (!goBackendReady || !isVpnPrepared(storedAppContext ?: return false)) return false
        val state = EmbeddedTailscaleNotifier.state.value
        return state.value >= EmbeddedTailscaleModels.State.Stopped.value &&
            state != EmbeddedTailscaleModels.State.Stopping
    }

    fun onVpnServiceCreated(service: EmbeddedTailscaleVpnService) {
        Log.i(TAG, "VpnService created id=${service.id()}")
    }

    fun onVpnActiveChanged(active: Boolean) {
        vpnActive = active
        Log.i(TAG, "vpnActive=$active state=${EmbeddedTailscaleNotifier.state.value}")
        if (active) {
            applyWantRunningIfNeeded()
            storedAppContext?.let { EmbeddedTailscaleKeepAliveService.start(it) }
        }
    }

    fun onVpnRevoked() {
        vpnActive = false
        wantRunningApplied = false
        Log.w(TAG, "VpnService revoked")
    }

    private fun initGoBackend(app: Context) {
        if (goBackendReady) return
        appContext = EmbeddedTailscaleAppContext(app)
        goApp = Libtailscale.start(
            app.filesDir.absolutePath,
            app.filesDir.absolutePath,
            /* hwAttestation */ false,
            appContext,
        )
        localApi = EmbeddedTailscaleLocalApi(scope, goApp)
        EmbeddedTailscaleNotifier.setApp(goApp)
        EmbeddedTailscaleNotifier.start(scope)
        goBackendReady = true
        Log.i(TAG, "libtailscale started — control=${EmbeddedTailscaleCredentials.CONTROL_URL}")
    }

    private fun applyWantRunningIfNeeded() {
        if (!goBackendReady || wantRunningApplied) return
        localApi.editPrefs(
            EmbeddedTailscaleCredentials.CONTROL_URL,
            wantRunning = true,
            onResult = { result ->
                result.onSuccess {
                    wantRunningApplied = true
                    Log.i(TAG, "WantRunning=true applied after VpnService ready")
                }
                result.onFailure { e ->
                    Log.e(TAG, "editPrefs WantRunning failed", e)
                }
            },
        )
    }

    private fun startVpnService(context: Context) {
        if (!isVpnPrepared(context)) {
            Log.w(TAG, "VpnService.prepare() requires user consent — launch from Activity")
            return
        }
        if (!vpnServiceStarting.compareAndSet(false, true)) {
            Log.d(TAG, "VpnService start already in flight")
            return
        }

        val intent = Intent(context, EmbeddedTailscaleVpnService::class.java).apply {
            action = EmbeddedTailscaleVpnService.ACTION_START_VPN
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pi = PendingIntent.getForegroundService(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                pi.send()
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Started EmbeddedTailscaleVpnService")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start VpnService", t)
            vpnServiceStarting.set(false)
        }
    }

    fun onVpnServiceStartHandled() {
        vpnServiceStarting.set(false)
    }
}
