package `in`.pcncloud.hotel.tailscale.embed

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import libtailscale.Libtailscale

/**
 * Singleton embedded Tailscale / Headscale engine (libtailscale in-process).
 */
object EmbeddedTailscaleEngine {

    private const val TAG = "EmbeddedTsEngine"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false
    private var loginInFlight = false
    private var vpnActive = false

    private lateinit var appContext: EmbeddedTailscaleAppContext
    private lateinit var goApp: libtailscale.Application
    private lateinit var localApi: EmbeddedTailscaleLocalApi

    fun init(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        if (initialized) return

        val app = context.applicationContext
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
        initialized = true
        Log.i(TAG, "libtailscale started — control=${EmbeddedTailscaleCredentials.CONTROL_URL}")
    }

    fun ensureRunning(context: Context) {
        if (!BuildConfig.IS_CORPORATE) return
        init(context.applicationContext)

        val app = context.applicationContext
        if (isVpnTransportUp(app) && vpnActive) {
            Log.i(TAG, "VPN already active")
            EmbeddedTailscaleKeepAliveService.start(app)
            return
        }

        if (loginInFlight) return
        loginInFlight = true

        val prefs = EmbeddedTailscaleModels.Prefs(
            ControlURL = EmbeddedTailscaleCredentials.CONTROL_URL,
            WantRunning = true,
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
                        Log.i(TAG, "Headscale login sequence dispatched")
                        loginInFlight = false
                        startVpnService(app)
                        EmbeddedTailscaleKeepAliveService.start(app)
                    }
                }
            }
        }
    }

    fun isAbleToStartVpn(): Boolean {
        val state = EmbeddedTailscaleNotifier.state.value
        return state.value >= EmbeddedTailscaleModels.State.Stopped.value
    }

    fun onVpnServiceCreated(service: EmbeddedTailscaleVpnService) {
        Log.i(TAG, "VpnService created id=${service.id()}")
    }

    fun onVpnActiveChanged(active: Boolean) {
        vpnActive = active
        Log.i(TAG, "vpnActive=$active state=${EmbeddedTailscaleNotifier.state.value}")
    }

    fun onVpnRevoked() {
        vpnActive = false
        Log.w(TAG, "VpnService revoked")
    }

    private fun startVpnService(context: Context) {
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            Log.w(TAG, "VpnService.prepare() requires user consent — launch from Activity")
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
        }
    }

    private fun isVpnTransportUp(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } else {
                cm.allNetworks.any { n ->
                    cm.getNetworkCapabilities(n)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
            }
        } catch (t: Throwable) {
            false
        }
    }
}
