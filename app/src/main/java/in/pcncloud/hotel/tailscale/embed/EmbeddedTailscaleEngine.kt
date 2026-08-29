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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import libtailscale.Libtailscale
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton embedded Tailscale / Headscale engine (libtailscale in-process).
 */
object EmbeddedTailscaleEngine {

    private const val TAG = "EmbeddedTsEngine"
    private const val WATCH_READY_TIMEOUT_MS = 15_000L
    private const val LOGIN_WATCHDOG_MS = 30_000L
    private const val IPN_INITIAL_STATE_TIMEOUT_MS = 15_000L
    private val CONTROL_URL_JSON_REGEX = Regex(""""ControlURL"\s*:\s*"([^"]*)"""")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsJson = Json { ignoreUnknownKeys = true }
    private var goBackendReady = false
    private val loginInFlight = AtomicBoolean(false)
    private var loginSequenceComplete = false
    private var wantRunningApplied = false
    private var vpnActive = false
    private var loginWatchdogGeneration = 0
    private val vpnServiceStarting = AtomicBoolean(false)
    private val controlPlaneProbedOk = AtomicBoolean(false)

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

        if (loginInFlight.get()) {
            Log.d(TAG, "ensureRunning skipped — AuthKey login already in flight")
            return
        }

        if (loginSequenceComplete) {
            kickEngineActive(app)
            return
        }

        performHeadlessLogin(app)
    }

    private fun performHeadlessLogin(app: Context) {
        if (!loginInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "performHeadlessLogin skipped — already in flight")
            return
        }

        // Match tailscale-android startForegroundForLogin — keeps network alive during auth.
        EmbeddedTailscaleKeepAliveService.start(app)

        scope.launch(Dispatchers.IO) {
            try {
                val watching = EmbeddedTailscaleNotifier.awaitWatching(WATCH_READY_TIMEOUT_MS)
                if (!watching) {
                    Log.e(
                        TAG,
                        "IPN bus not watching after ${WATCH_READY_TIMEOUT_MS}ms — " +
                            "LoginFinished events may be missed",
                    )
                }
                val initialReady =
                    EmbeddedTailscaleNotifier.awaitInitialState(IPN_INITIAL_STATE_TIMEOUT_MS)
                val state = EmbeddedTailscaleNotifier.state.value
                Log.i(
                    TAG,
                    "IPN bus ready state=$state initial=$initialReady " +
                        "watching=${EmbeddedTailscaleNotifier.isWatching()}",
                )
                if (!initialReady) {
                    Log.w(TAG, "IPN InitialState not received — deferring login 2s")
                    delay(2_000L)
                }

                val probeOk = probeHeadscaleReachability()
                if (probeOk) {
                    Log.i(
                        TAG,
                        "GET /key HTTP 200 — proceeding immediately to AuthKey POST /start " +
                            "(no probe loop)",
                    )
                } else {
                    Log.w(
                        TAG,
                        "GET /key probe failed — still attempting AuthKey POST /start",
                    )
                }

                Log.i(
                    TAG,
                    "Headless login — PATCH prefs + POST /start with AuthKey " +
                        "${EmbeddedTailscaleCredentials.AUTH_KEY.take(8)}… " +
                        "control=${EmbeddedTailscaleCredentials.CONTROL_URL}",
                )

                localApi.startWithAuthKey(
                    controlUrl = EmbeddedTailscaleCredentials.CONTROL_URL,
                    authKey = EmbeddedTailscaleCredentials.AUTH_KEY,
                    wantRunning = true,
                    onResult = { startResult ->
                        startResult.onFailure { e ->
                            Log.e(TAG, "Headless auth-key start failed", e)
                            loginInFlight.set(false)
                        }
                        startResult.onSuccess {
                            val newState = EmbeddedTailscaleNotifier.state.value
                            wantRunningApplied = true
                            Log.i(
                                TAG,
                                "Auth-key POST /start chain OK — state=$newState; " +
                                    "forcing WantRunning=true + engine kickoff",
                            )
                            logLoginDiagnostics("auth-key chain")
                            verifyControlUrlPersisted("auth-key start")
                            loginInFlight.set(false)
                            kickEngineActive(app)
                            when (newState) {
                                EmbeddedTailscaleModels.State.Stopped,
                                EmbeddedTailscaleModels.State.Running,
                                EmbeddedTailscaleModels.State.Starting,
                                -> onHeadlessLoginComplete(app)
                                EmbeddedTailscaleModels.State.NeedsLogin -> {
                                    Log.i(
                                        TAG,
                                        "NeedsLogin after AuthKey start — WantRunning set; " +
                                            "watchdog monitors (will NOT re-probe /key)",
                                    )
                                    scheduleLoginCompletionWatchdog(app)
                                }
                                else -> scheduleLoginCompletionWatchdog(app)
                            }
                        }
                    },
                )
            } catch (t: Throwable) {
                Log.e(TAG, "performHeadlessLogin crashed before/during AuthKey start", t)
                loginInFlight.set(false)
            }
        }
    }

    private fun logLoginDiagnostics(source: String) {
        localApi.fetchStatus { statusResult ->
            statusResult.onSuccess { body ->
                Log.i(TAG, "GET /status ($source): ${body.take(800)}")
            }
            statusResult.onFailure { e ->
                Log.w(TAG, "GET /status failed ($source)", e)
            }
        }
    }

    /**
     * Best-effort TCP/TLS probe of the Headscale control URL.
     * Returns true on HTTP 2xx. Called at most once successfully per process
     * (subsequent attempts are skipped to avoid a probe loop).
     */
    private fun probeHeadscaleReachability(): Boolean {
        if (controlPlaneProbedOk.get()) {
            Log.d(TAG, "Headscale probe skipped — already succeeded this process")
            return true
        }
        val base = EmbeddedTailscaleCredentials.CONTROL_URL.trimEnd('/')
        val keyUrl = "$base/key?v=109"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(keyUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("ngrok-skip-browser-warning", "true")
                setRequestProperty("User-Agent", "IkhsanaHotelTV-HeadscaleProbe/1.0")
            }
            val code = conn.responseCode
            val body = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    ?.take(200)
                    .orEmpty()
            }.getOrDefault("")
            Log.i(TAG, "Headscale probe GET $keyUrl → HTTP $code body=${body.replace('\n', ' ')}")
            val ok = code in 200..299
            if (ok) controlPlaneProbedOk.set(true)
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "Headscale probe FAILED for $keyUrl — TV cannot reach control plane", t)
            false
        } finally {
            conn?.disconnect()
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
                        "Still NeedsLogin — re-assert WantRunning only " +
                            "(no GET /key probe, no full re-login)",
                    )
                    kickEngineActive(app)
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
            Log.w(TAG, "Login watchdog retry — state=$state (WantRunning kick only)")
            if (state == EmbeddedTailscaleModels.State.NeedsLogin && !loginSequenceComplete) {
                // Do NOT call performHeadlessLogin again — that re-runs GET /key and
                // looks like a probe loop while never advancing registration.
                kickEngineActive(app)
            } else if (
                state == EmbeddedTailscaleModels.State.Stopped ||
                state == EmbeddedTailscaleModels.State.Running ||
                state == EmbeddedTailscaleModels.State.Starting
            ) {
                onHeadlessLoginComplete(app)
            }
        }
    }

    private fun onHeadlessLoginComplete(app: Context) {
        loginWatchdogGeneration++
        if (loginSequenceComplete) {
            Log.d(TAG, "onHeadlessLoginComplete (already complete) — re-apply WantRunning")
            kickEngineActive(app)
            return
        }
        loginSequenceComplete = true
        loginInFlight.set(false)
        Log.i(
            TAG,
            "onHeadlessLoginComplete — state=${EmbeddedTailscaleNotifier.state.value}; " +
                "WantRunning=true + VPN service",
        )
        kickEngineActive(app)
    }

    /** Assert WantRunning=true and start VpnService so the engine loop runs without manual triggers. */
    private fun kickEngineActive(app: Context) {
        wantRunningApplied = true
        applyWantRunning(force = true)
        startVpnService(app)
        Log.i(
            TAG,
            "kickEngineActive — WantRunning forced, VpnService started " +
                "(state=${EmbeddedTailscaleNotifier.state.value})",
        )
    }

    fun isAbleToStartVpn(): Boolean = isReadyForRequestVpn()

    /** Go notifier ready and WantRunning asserted — allow TUN while Starting/Stopped/Running. */
    fun isReadyForRequestVpn(): Boolean {
        if (!goBackendReady || !EmbeddedTailscaleNotifier.hasInitialState()) return false
        if (!isVpnPrepared(storedAppContext ?: return false)) return false
        if (!wantRunningApplied && !loginSequenceComplete) return false
        return when (EmbeddedTailscaleNotifier.state.value) {
            EmbeddedTailscaleModels.State.Stopped,
            EmbeddedTailscaleModels.State.Running,
            EmbeddedTailscaleModels.State.Starting,
            -> true
            // AuthKey register may still report NeedsLogin briefly; do not block forever —
            // VpnService retries until state advances.
            EmbeddedTailscaleModels.State.NeedsLogin -> wantRunningApplied
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
            applyWantRunning(force = true)
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
        // Patched libtailscale reads this pref during LocalBackend.Start — before any LocalAPI call.
        appContext.writeHeadscaleControlUrlForEngineStart(EmbeddedTailscaleCredentials.CONTROL_URL)
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
        Log.i(
            TAG,
            "libtailscale started — ControlURL seeded for engine init: " +
                EmbeddedTailscaleCredentials.CONTROL_URL,
        )
    }

    private fun applyWantRunning(force: Boolean = false) {
        if (!goBackendReady) {
            Log.w(TAG, "applyWantRunning skipped — Go backend not ready")
            return
        }
        if (wantRunningApplied && !force) {
            Log.d(TAG, "applyWantRunning skipped — already applied")
            return
        }
        Log.i(TAG, "PATCH /prefs WantRunning=true (force=$force)")
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
                    wantRunningApplied = false
                    Log.e(TAG, "PATCH /prefs WantRunning failed", e)
                }
            },
        )
    }

    private fun applyWantRunningIfNeeded() = applyWantRunning(force = false)

    /**
     * GET /prefs often returns ControlURL "" — live URL is set at engine init (patched libtailscale)
     * and reinforced via POST /start UpdatePrefs on auth-key login.
     */
    private fun verifyControlUrlPersisted(source: String) {
        if (!goBackendReady) return
        localApi.fetchPrefs { result ->
            result.onSuccess { body -> logStoredControlUrl(source, body) }
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
                        "engine init + POST /start use $expected",
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
}
