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
     * Headless Headscale login — tailscale-android order:
     * 1. PATCH /prefs MaskedPrefs (ControlURLSet)
     * 2. POST /start with UpdatePrefs [Prefs] + AuthKey
     * ControlURL must already be seeded before Libtailscale.start() (patched libtailscale).
     */
    fun startWithAuthKey(
        controlUrl: String,
        authKey: String,
        wantRunning: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val masked = headscaleMaskedPrefs(controlUrl, wantRunning)
        Log.i(
            TAG,
            "PATCH /prefs before /start — ControlURLSet=true WantRunningSet=true " +
                "control=$controlUrl wantRunning=$wantRunning",
        )
        editMaskedPrefs(masked) { editResult ->
            editResult.onFailure { e ->
                Log.e(TAG, "PATCH /prefs (pre-start ControlURL) failed", e)
                onResult(Result.failure(e))
            }
            editResult.onSuccess { prefsBody ->
                Log.i(TAG, "PATCH /prefs (pre-start) OK — response: ${prefsBody.take(200)}")
                val options = EmbeddedTailscaleModels.Options(
                    AuthKey = authKey,
                    UpdatePrefs = headscalePrefs(controlUrl, wantRunning),
                )
                val body = json.encodeToString(options).toByteArray()
                Log.i(
                    TAG,
                    "POST /start authKeyPrefix=${authKey.take(8)}… " +
                        "UpdatePrefs.ControlURL=$controlUrl WantRunning=$wantRunning",
                )
                post("start", body, onResult)
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

    private fun headscalePrefs(controlUrl: String, wantRunning: Boolean): EmbeddedTailscaleModels.Prefs =
        EmbeddedTailscaleModels.Prefs(
            ControlURL = controlUrl,
            WantRunning = wantRunning,
            LoggedOut = false,
        )

    private fun headscaleMaskedPrefs(
        controlUrl: String,
        wantRunning: Boolean,
    ): EmbeddedTailscaleModels.MaskedPrefs =
        EmbeddedTailscaleModels.MaskedPrefs(
            ControlURL = controlUrl,
            ControlURLSet = true,
            WantRunning = wantRunning,
            WantRunningSet = true,
            LoggedOut = false,
            LoggedOutSet = true,
        )

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
