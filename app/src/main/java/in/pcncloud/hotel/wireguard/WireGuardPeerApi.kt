package `in`.pcncloud.hotel.wireguard

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Registers this device with the Node.js WireGuard peer API.
 *
 * POST [WireGuardCredentials.ADD_PEER_URL]
 * ```json
 * { "publicKey": "<device>" }
 * ```
 *
 * Server allocates the next free `10.0.0.x` from `wg0.conf` and returns
 * `{ clientIp, address, serverPublicKey }`.
 */
class WireGuardPeerApi(
    private val client: OkHttpClient = defaultClient(),
) {
    data class AddPeerResult(
        val success: Boolean,
        val httpCode: Int,
        val clientIp: String? = null,
        val address: String? = null,
        val serverPublicKey: String? = null,
        val message: String? = null,
    )

    fun addPeer(publicKey: String): AddPeerResult {
        val bodyJson = JSONObject()
            .put("publicKey", publicKey)
            .toString()

        val request = Request.Builder()
            .url(WireGuardCredentials.ADD_PEER_URL)
            .post(bodyJson.toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                Log.i(TAG, "add-peer HTTP ${response.code} body=${raw.take(240)}")
                val parsed = raw.takeIf { it.isNotBlank() }?.let {
                    runCatching { JSONObject(it) }.getOrNull()
                }
                val clientIp = listOf("clientIp", "client_ip", "ip")
                    .firstNotNullOfOrNull { key ->
                        parsed?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
                    }
                    ?.replace(Regex("/\\d+$"), "")
                val address = parsed?.optString("address")?.trim()?.takeIf { it.isNotBlank() }
                    ?: clientIp?.let { "$it/32" }
                val serverKey = listOf("serverPublicKey", "server_public_key")
                    .firstNotNullOfOrNull { key ->
                        parsed?.optString(key)?.takeIf { s ->
                            s.isNotBlank() && s != publicKey
                        }
                    }
                AddPeerResult(
                    success = response.isSuccessful && !clientIp.isNullOrBlank(),
                    httpCode = response.code,
                    clientIp = clientIp,
                    address = address,
                    serverPublicKey = serverKey,
                    message = parsed?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: raw.takeIf { it.isNotBlank() },
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "add-peer request failed", t)
            AddPeerResult(success = false, httpCode = -1, message = t.message)
        }
    }

    companion object {
        private const val TAG = "WireGuardPeerApi"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
