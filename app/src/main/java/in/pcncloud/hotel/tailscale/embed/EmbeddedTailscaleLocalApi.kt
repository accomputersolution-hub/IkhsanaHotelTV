package `in`.pcncloud.hotel.tailscale.embed

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
        post("start", json.encodeToString(options).toByteArray(), onResult)
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
        patch("prefs", body.toByteArray(), onResult)
    }

    private fun post(path: String, body: ByteArray?, onResult: (Result<Unit>) -> Unit) {
        invoke("POST", path, body, onResult)
    }

    private fun patch(path: String, body: ByteArray?, onResult: (Result<Unit>) -> Unit) {
        invoke("PATCH", path, body, onResult)
    }

    private fun invoke(
        method: String,
        path: String,
        body: ByteArray?,
        onResult: (Result<Unit>) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val endpoint = "/localapi/v0/$path"
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
                if (code >= 400) {
                    val text = respBody?.decodeToString() ?: "HTTP $code"
                    onResult(Result.failure(IllegalStateException(text)))
                } else {
                    onResult(Result.success(Unit))
                }
            } catch (t: Throwable) {
                onResult(Result.failure(t))
            }
        }
    }
}
