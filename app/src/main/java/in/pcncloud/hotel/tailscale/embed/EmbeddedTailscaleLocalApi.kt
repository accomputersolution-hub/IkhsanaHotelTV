package `in`.pcncloud.hotel.tailscale.embed

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import libtailscale.Libtailscale

/**
 * Thin wrapper around libtailscale [libtailscale.Application.callLocalAPI].
 */
class EmbeddedTailscaleLocalApi(
    private val scope: CoroutineScope,
    private val app: libtailscale.Application,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Headless Headscale login with AuthKey (no browser):
     * 1. PATCH /prefs LoggedOut=true
     * 2. PATCH /prefs LoggedOut=false
     * 3. PATCH /prefs ControlURL
     * 4. POST /start with AuthKey + WantRunning=true
     * 5. STOP — do not call /login-interactive (cancels headless AuthKey register)
     *
     * LocalAPI requires the Go backend to already be started ([Libtailscale.start]).
     */
    fun startWithAuthKey(
        controlUrl: String,
        authKey: String,
        wantRunning: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ) {
        forceLogoutReset { resetResult ->
            resetResult.onFailure { e ->
                Log.e(TAG, "LoggedOut reset failed — aborting auth-key login", e)
                onResult(Result.failure(e))
            }
            resetResult.onSuccess {
                patchControlUrlThenStart(controlUrl, authKey, wantRunning, onResult)
            }
        }
    }

    /**
     * Wipe stale auth state before re-login:
     * PATCH LoggedOut=true, then LoggedOut=false.
     */
    fun forceLogoutReset(onResult: (Result<Unit>) -> Unit) {
        val logout = EmbeddedTailscaleModels.MaskedPrefs(
            LoggedOut = true,
            LoggedOutSet = true,
        )
        Log.i(TAG, "PATCH /prefs LoggedOut=true (wipe corrupted auth cache)")
        editMaskedPrefs(logout) { logoutResult ->
            logoutResult.onFailure { e ->
                Log.e(TAG, "PATCH /prefs LoggedOut=true failed", e)
                onResult(Result.failure(e))
            }
            logoutResult.onSuccess {
                Log.i(TAG, "PATCH /prefs LoggedOut=true OK — clearing LoggedOut for re-auth")
                val loginReady = EmbeddedTailscaleModels.MaskedPrefs(
                    LoggedOut = false,
                    LoggedOutSet = true,
                )
                editMaskedPrefs(loginReady) { clearResult ->
                    clearResult.onFailure { e ->
                        Log.e(TAG, "PATCH /prefs LoggedOut=false failed", e)
                        onResult(Result.failure(e))
                    }
                    clearResult.onSuccess {
                        Log.i(TAG, "PATCH /prefs LoggedOut=false OK — auth cache reset complete")
                        onResult(Result.success(Unit))
                    }
                }
            }
        }
    }

    private fun patchControlUrlThenStart(
        controlUrl: String,
        authKey: String,
        wantRunning: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val masked = authKeyMaskedPrefs(controlUrl)
        Log.i(
            TAG,
            "PATCH /prefs before /start — ControlURLSet LoggedOut=false control=$controlUrl",
        )
        editMaskedPrefs(masked) { editResult ->
            editResult.onFailure { e ->
                Log.e(TAG, "PATCH /prefs (pre-start) failed", e)
                onResult(Result.failure(e))
            }
            editResult.onSuccess { prefsBody ->
                Log.i(TAG, "PATCH /prefs (pre-start) OK — response: ${prefsBody.take(200)}")
                val updatePrefs = mergeUpdatePrefs(prefsBody, controlUrl, wantRunning)
                val options = EmbeddedTailscaleModels.Options(
                    AuthKey = authKey,
                    UpdatePrefs = updatePrefs,
                )
                val body = json.encodeToString(options).toByteArray()
                Log.i(
                    TAG,
                    "POST /start authKeyPrefix=${authKey.take(8)}… " +
                        "UpdatePrefs.ControlURL=$controlUrl WantRunning=$wantRunning",
                )
                post("start", body) { startResult ->
                    startResult.onFailure { e ->
                        Log.e(TAG, "POST /start failed", e)
                        onResult(Result.failure(e))
                    }
                    startResult.onSuccess {
                        Log.i(
                            TAG,
                            "POST /start OK — skipping /login-interactive; " +
                                "forcing WantRunning=$wantRunning",
                        )
                        if (wantRunning) {
                            editWantRunning(true) { wr ->
                                wr.onFailure { e ->
                                    Log.e(TAG, "POST-start WantRunning=true failed", e)
                                    // Still continue — UpdatePrefs already requested WantRunning.
                                    onResult(Result.success(Unit))
                                }
                                wr.onSuccess {
                                    Log.i(TAG, "POST-start WantRunning=true OK — engine should run")
                                    onResult(Result.success(Unit))
                                }
                            }
                        } else {
                            onResult(Result.success(Unit))
                        }
                    }
                }
            }
        }
    }

    fun editMaskedPrefs(
        masked: EmbeddedTailscaleModels.MaskedPrefs,
        onResult: (Result<String>) -> Unit,
    ) {
        val body = json.encodeToString(masked).toByteArray()
        Log.d(TAG, "PATCH /prefs masked body=${sanitizeForLog(body.decodeToString())}")
        patch("prefs", body, onResult)
    }

    fun editWantRunning(
        wantRunning: Boolean,
        onResult: (Result<String>) -> Unit,
    ) {
        val masked = EmbeddedTailscaleModels.MaskedPrefs(
            WantRunning = wantRunning,
            WantRunningSet = true,
        )
        Log.i(TAG, "PATCH /prefs WantRunning=$wantRunning (ControlURL unchanged)")
        editMaskedPrefs(masked, onResult)
    }

    fun editControlUrl(
        controlUrl: String,
        onResult: (Result<String>) -> Unit,
    ) {
        val masked = EmbeddedTailscaleModels.MaskedPrefs(
            ControlURL = controlUrl,
            ControlURLSet = true,
        )
        Log.i(TAG, "PATCH /prefs ControlURL=$controlUrl ControlURLSet=true")
        editMaskedPrefs(masked, onResult)
    }

    fun fetchPrefs(onResult: (Result<String>) -> Unit) {
        get("prefs", onResult)
    }

    fun fetchStatus(onResult: (Result<String>) -> Unit) {
        get("status", onResult)
    }

    private fun authKeyMaskedPrefs(controlUrl: String): EmbeddedTailscaleModels.MaskedPrefs =
        EmbeddedTailscaleModels.MaskedPrefs(
            ControlURL = controlUrl,
            ControlURLSet = true,
            LoggedOut = false,
            LoggedOutSet = true,
        )

    /** PATCH response may omit ControlURL; always reinforce URL + WantRunning for POST /start. */
    private fun mergeUpdatePrefs(
        prefsBody: String,
        controlUrl: String,
        wantRunning: Boolean,
    ): EmbeddedTailscaleModels.Prefs {
        val parsed = runCatching {
            json.decodeFromString<EmbeddedTailscaleModels.Prefs>(prefsBody)
        }.getOrDefault(EmbeddedTailscaleModels.Prefs())
        return EmbeddedTailscaleModels.Prefs(
            ControlURL = controlUrl.ifBlank { parsed.ControlURL },
            WantRunning = wantRunning,
            LoggedOut = false,
            CorpDNS = parsed.CorpDNS,
            RouteAll = parsed.RouteAll,
        )
    }

    private fun get(path: String, onResult: (Result<String>) -> Unit) {
        invoke("GET", path, null, onResult)
    }

    private fun post(path: String, body: ByteArray?, onResult: (Result<Unit>) -> Unit) {
        invokeUnit("POST", path, body, onResult)
    }

    private fun patch(path: String, body: ByteArray?, onResult: (Result<String>) -> Unit) {
        invoke("PATCH", path, body, onResult)
    }

    private fun invokeUnit(
        method: String,
        path: String,
        body: ByteArray?,
        onResult: (Result<Unit>) -> Unit,
    ) {
        invoke(method, path, body) { result ->
            onResult(result.map { Unit })
        }
    }

    private fun invoke(
        method: String,
        path: String,
        body: ByteArray?,
        onResult: (Result<String>) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val endpoint = "/localapi/v0/$path"
            try {
                val bodyPreview = body?.decodeToString()?.let { sanitizeForLog(it) }
                Log.d(TAG, "→ $method $endpoint body=$bodyPreview")
                val response = if (body != null) {
                    app.callLocalAPI(
                        30_000,
                        method,
                        endpoint,
                        GoInputStreamAdapter(body.inputStream()),
                    )
                } else {
                    app.callLocalAPI(30_000, method, endpoint, null)
                }
                val code = response.statusCode()
                val respBody = response.bodyBytes()
                val respText = respBody?.decodeToString()?.let { sanitizeForLog(it) }.orEmpty()
                Log.d(TAG, "← $method $endpoint HTTP $code body=$respText")
                if (code >= 400) {
                    onResult(Result.failure(IllegalStateException("HTTP $code: $respText")))
                } else {
                    onResult(Result.success(respText))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "LocalAPI $method $endpoint failed", t)
                onResult(Result.failure(t))
            }
        }
    }

    private fun sanitizeForLog(text: String): String =
        text.replace(
            EmbeddedTailscaleCredentials.AUTH_KEY,
            EmbeddedTailscaleCredentials.AUTH_KEY.take(8) + "…",
        )

    companion object {
        private const val TAG = "EmbeddedTsLocalApi"
    }
}
