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
    private val json = Json { ignoreUnknownKeys = true }

    fun start(
        options: EmbeddedTailscaleModels.Options,
        onResult: (Result<Unit>) -> Unit,
    ) {
        post("start", json.encodeToString(options).toByteArray(), onResult)
    }

    /**
     * Headless Headscale login — AuthKey is consumed by POST /start (not login-interactive).
     */
    fun startWithAuthKey(
        controlUrl: String,
        authKey: String,
        wantRunning: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val options = EmbeddedTailscaleModels.Options(
            AuthKey = authKey,
            UpdatePrefs = EmbeddedTailscaleModels.Prefs(
                ControlURL = controlUrl,
                WantRunning = wantRunning,
            ),
        )
        val body = json.encodeToString(options).toByteArray()
        Log.i(
            TAG,
            "POST /start authKeyPrefix=${authKey.take(8)}… control=$controlUrl wantRunning=$wantRunning",
        )
        post("start", body, onResult)
    }

    fun editPrefs(
        controlUrl: String,
        wantRunning: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val body = """
            {
              "ControlURL": "$controlUrl",
              "ControlURLSet": true,
              "WantRunning": $wantRunning,
              "WantRunningSet": true
            }
        """.trimIndent()
        Log.i(TAG, "PATCH /prefs control=$controlUrl WantRunning=$wantRunning")
        patch("prefs", body.toByteArray(), onResult)
    }

    /** Poll engine status when IPN notifications are delayed or missed. */
    fun fetchStatus(onResult: (Result<String>) -> Unit) {
        get("status", onResult)
    }

    private fun get(path: String, onResult: (Result<String>) -> Unit) {
        invoke("GET", path, null, onResult)
    }

    private fun post(path: String, body: ByteArray?, onResult: (Result<Unit>) -> Unit) {
        invokeUnit("POST", path, body, onResult)
    }

    private fun patch(path: String, body: ByteArray?, onResult: (Result<Unit>) -> Unit) {
        invokeUnit("PATCH", path, body, onResult)
    }

    private fun invokeUnit(
        method: String,
        path: String,
        body: ByteArray?,
        onResult: (Result<Unit>) -> Unit,
    ) {
        invoke(method, path, body) { result ->
            onResult(
                result.map { Unit },
            )
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
