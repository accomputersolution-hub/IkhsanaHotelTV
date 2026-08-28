package `in`.pcncloud.hotel.tailscale.embed

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import libtailscale.Libtailscale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton embedded Tailscale / Headscale engine (libtailscale in-process).
 */
object EmbeddedTailscaleEngine {

    private const val TAG = "EmbeddedTsEngine"
    private const val WATCH_READY_TIMEOUT_MS = 15_000L
    private const val LOGIN_WATCHDOG_MS = 8_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsJson = Json { ignoreUnknownKeys = true }
    private var goBackendReady = false
    private var loginInFlight = false
    private var loginSequenceComplete = false
    private var wantRunningApplied = false
    private var vpnActive = false
    private var loginWatchdogGeneration = 0
    private val vpnServiceStarting = AtomicBoolean(false)
    private val controlUrlBootstrapDone = CompletableDeferred<Unit>()

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
            EmbeddedTailscaleModels.State.NeedsLogin -> {
                if (loginSequenceComplete) {
                    Log.w(TAG, "NeedsLogin after auth-key — retrying headless login")
                    loginSequenceComplete = false
                    wantRunningApplied = false
                }
            }
            EmbeddedTailscaleModels.State.NoState,
            EmbeddedTailscaleModels.State.InUseOtherUser,
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

        performHeadlessLogin(app)
    }

    private fun performHeadlessLogin(app: Context) {
        if (loginInFlight) return
        loginInFlight = true

        scope.launch(Dispatchers.IO) {
            val watching = EmbeddedTailscaleNotifier.awaitWatching(WATCH_READY_TIMEOUT_MS)
            if (!watching) {
                Log.e(
                    TAG,
                    "IPN bus not watching after ${WATCH_READY_TIMEOUT_MS}ms — " +
                        "LoginFinished events may be missed",
                )
            } else {
                Log.i(
                    TAG,
                    "IPN bus ready state=${EmbeddedTailscaleNotifier.state.value} " +
                        "initial=${EmbeddedTailscaleNotifier.hasInitialState()}",
                )
            }

            Log.i(
                TAG,
                "Headless login — await ControlURL bootstrap, then PATCH+POST /start " +
                    "AuthKey=${EmbeddedTailscaleCredentials.AUTH_KEY.take(8)}… " +
                    "control=${EmbeddedTailscaleCredentials.CONTROL_URL}",
            )

            if (!controlUrlBootstrapDone.isCompleted) {
                Log.i(TAG, "Waiting for POST /start ControlURL bootstrap after Libtailscale.start()")
            }
            controlUrlBootstrapDone.await()

            localApi.startWithAuthKey(
                controlUrl = EmbeddedTailscaleCredentials.CONTROL_URL,
                authKey = EmbeddedTailscaleCredentials.AUTH_KEY,
                wantRunning = false,
                onResult = { startResult ->
                    startResult.onFailure { e ->
                        Log.e(TAG, "Headless auth-key start failed", e)
                        loginInFlight = false
                    }
                    startResult.onSuccess {
                        val state = EmbeddedTailscaleNotifier.state.value
                        Log.i(
                            TAG,
                            "Auth-key start accepted — state=$state watching=${EmbeddedTailscaleNotifier.isWatching()}",
                        )
                        verifyControlUrlPersisted("auth-key start")
                        loginInFlight = false
                        when (state) {
                            EmbeddedTailscaleModels.State.Stopped,
                            EmbeddedTailscaleModels.State.Running,
                            EmbeddedTailscaleModels.State.Starting,
                            -> onHeadlessLoginComplete(app)
                            EmbeddedTailscaleModels.State.NeedsLogin -> {
                                Log.i(
                                    TAG,
                                    "Auth-key start: NeedsLogin — awaiting LoginFinished / state change",
                                )
                                scheduleLoginCompletionWatchdog(app)
                            }
                            else -> {
                                Log.w(
                                    TAG,
                                    "Auth-key start: unexpected state=$state — scheduling watchdog",
                                )
                                scheduleLoginCompletionWatchdog(app)
                            }
                        }
                    }
                },
            )
        }
    }

    private fun scheduleLoginCompletionWatchdog(app: Context) {
        val generation = ++loginWatchdogGeneration
        scope.launch(Dispatchers.IO) {
            delay(LOGIN_WATCHDOG_MS)
            if (generation != loginWatchdogGeneration || loginSequenceComplete) return@launch

            val state = EmbeddedTailscaleNotifier.state.value
            Log.w(
                TAG,
                "Login watchdog (${LOGIN_WATCHDOG_MS}ms) — state=$state " +
                    "wantApplied=$wantRunningApplied loginComplete=$loginSequenceComplete",
            )

            localApi.fetchStatus { statusResult ->
                statusResult.onSuccess { body ->
                    Log.i(TAG, "GET /status snapshot: ${body.take(400)}")
                }
                statusResult.onFailure { e ->
                    Log.w(TAG, "GET /status failed during login watchdog", e)
                }
            }

            when (state) {
                EmbeddedTailscaleModels.State.Stopped,
                EmbeddedTailscaleModels.State.Running,
                EmbeddedTailscaleModels.State.Starting,
                -> onHeadlessLoginComplete(app)
                EmbeddedTailscaleModels.State.NeedsLogin -> {
                    Log.w(
                        TAG,
                        "Still NeedsLogin — applying WantRunning=true (callback may have been missed)",
                    )
                    applyWantRunningIfNeeded()
                    scheduleLoginCompletionWatchdog(app, delayMs = LOGIN_WATCHDOG_MS * 2)
                }
                else -> scheduleLoginCompletionWatchdog(app, delayMs = LOGIN_WATCHDOG_MS)
            }
        }
    }

    private fun scheduleLoginCompletionWatchdog(app: Context, delayMs: Long = LOGIN_WATCHDOG_MS) {
        val generation = ++loginWatchdogGeneration
        scope.launch(Dispatchers.IO) {
            delay(delayMs)
            if (generation != loginWatchdogGeneration || loginSequenceComplete) return@launch
            val state = EmbeddedTailscaleNotifier.state.value
            Log.w(TAG, "Login watchdog retry — state=$state")
            if (state == EmbeddedTailscaleModels.State.NeedsLogin && !loginSequenceComplete) {
                Log.w(TAG, "Retrying headless login after watchdog")
                loginInFlight = false
                performHeadlessLogin(app)
            }
        }
    }

    private fun onHeadlessLoginComplete(app: Context) {
        loginWatchdogGeneration++
        if (loginSequenceComplete) {
            Log.d(TAG, "onHeadlessLoginComplete (already complete) — re-apply WantRunning")
            applyWantRunningIfNeeded()
            return
        }
        loginSequenceComplete = true
        loginInFlight = false
        Log.i(
            TAG,
            "onHeadlessLoginComplete — state=${EmbeddedTailscaleNotifier.state.value}; " +
                "setting WantRunning=true",
        )
        applyWantRunningIfNeeded()
        startVpnService(app)
    }

    fun isAbleToStartVpn(): Boolean = isReadyForRequestVpn()

    /** Go notifier + login complete and engine in a stable state for TUN bring-up. */
    fun isReadyForRequestVpn(): Boolean {
        if (!goBackendReady || !EmbeddedTailscaleNotifier.hasInitialState()) return false
        if (!isVpnPrepared(storedAppContext ?: return false)) return false
        if (EmbeddedTailscaleNotifier.state.value == EmbeddedTailscaleModels.State.NeedsLogin) {
            return false
        }
        return when (EmbeddedTailscaleNotifier.state.value) {
            EmbeddedTailscaleModels.State.Stopped,
            EmbeddedTailscaleModels.State.Running,
            -> loginSequenceComplete
            else -> false
        }
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
        EmbeddedTailscaleNotifier.onHeadlessLoginComplete = {
            storedAppContext?.let { onHeadlessLoginComplete(it.applicationContext) }
        }
        EmbeddedTailscaleNotifier.start(scope)
        goBackendReady = true
        Log.i(TAG, "libtailscale started — control=${EmbeddedTailscaleCredentials.CONTROL_URL}")
        seedHeadscaleControlUrlAtEngineStart()
    }

    /**
     * libtailscale already invoked LocalBackend.Start(empty Options) during Libtailscale.start().
     * PATCH /prefs alone does not re-point the control client — POST /start UpdatePrefs is required.
     */
    private fun seedHeadscaleControlUrlAtEngineStart() {
        scope.launch(Dispatchers.IO) {
            localApi.bootstrapControlUrlViaStart(EmbeddedTailscaleCredentials.CONTROL_URL) { result ->
                result.onSuccess {
                    Log.i(
                        TAG,
                        "ControlURL seeded via POST /start UpdatePrefs " +
                            EmbeddedTailscaleCredentials.CONTROL_URL,
                    )
                }
                result.onFailure { e ->
                    Log.e(
                        TAG,
                        "ControlURL bootstrap POST /start failed — Headscale may not receive traffic",
                        e,
                    )
                }
                if (!controlUrlBootstrapDone.isCompleted) {
                    controlUrlBootstrapDone.complete(Unit)
                }
            }
        }
    }

    private fun applyWantRunningIfNeeded() {
        if (!goBackendReady) {
            Log.w(TAG, "applyWantRunning skipped — Go backend not ready")
            return
        }
        if (wantRunningApplied) {
            Log.d(TAG, "applyWantRunning skipped — already applied")
            return
        }
        Log.i(TAG, "PATCH /prefs WantRunning=true")
        localApi.editWantRunning(
            wantRunning = true,
            onResult = { result ->
                result.onSuccess { prefsBody ->
                    wantRunningApplied = true
                    logStoredControlUrl("WantRunning patch", prefsBody)
                    Log.i(
                        TAG,
                        "WantRunning=true applied — state=${EmbeddedTailscaleNotifier.state.value}",
                    )
                    verifyControlUrlPersisted("WantRunning patch")
                }
                result.onFailure { e ->
                    Log.e(TAG, "PATCH /prefs WantRunning failed", e)
                }
            },
        )
    }

    /**
     * GET /prefs stored ControlURL is often "" until the control plane first responds.
     * The live control client URL comes from POST /start UpdatePrefs (and bootstrap seed).
     */
    private fun verifyControlUrlPersisted(source: String) {
        if (!goBackendReady) return
        val expected = EmbeddedTailscaleCredentials.CONTROL_URL
        localApi.fetchPrefs { result ->
            result.onSuccess { body ->
                logStoredControlUrl(source, body)
                val stored = decodeStoredControlUrl(body)
                if (stored.isBlank() || stored != expected) {
                    Log.w(
                        TAG,
                        "ControlURL not persisted ($source stored=${stored.ifBlank { "empty" }}) — " +
                            "PATCH ControlURLSet expected=$expected",
                    )
                    localApi.editControlUrl(expected) { patchResult ->
                        patchResult.onSuccess { patchBody ->
                            logStoredControlUrl("ControlURL reassert", patchBody)
                        }
                        patchResult.onFailure { e ->
                            Log.e(TAG, "PATCH ControlURL reassert failed", e)
                        }
                    }
                }
            }
            result.onFailure { e ->
                Log.w(TAG, "GET /prefs failed during ControlURL verify ($source)", e)
            }
        }
    }

    private fun decodeStoredControlUrl(prefsBody: String): String {
        val decoded = runCatching {
            prefsJson.decodeFromString<EmbeddedTailscaleModels.Prefs>(prefsBody).ControlURL
        }.getOrDefault("")
        if (decoded.isNotBlank()) return decoded
        return CONTROL_URL_JSON_REGEX.find(prefsBody)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun logStoredControlUrl(source: String, prefsBody: String) {
        val stored = decodeStoredControlUrl(prefsBody)
        val expected = EmbeddedTailscaleCredentials.CONTROL_URL
        when {
            stored.isBlank() ->
                Log.i(
                    TAG,
                    "GET /prefs ($source) stored ControlURL empty — " +
                        "if POST /start UpdatePrefs succeeded, control client uses $expected",
                )
            stored != expected ->
                Log.w(TAG, "GET /prefs ($source) stored ControlURL=$stored expected=$expected")
            else ->
                Log.i(TAG, "GET /prefs ($source) stored ControlURL=$stored")
        }
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

    companion object {
        private val CONTROL_URL_JSON_REGEX = Regex(""""ControlURL"\s*:\s*"([^"]*)"""")
    }
}
