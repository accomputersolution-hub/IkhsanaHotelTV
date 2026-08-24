package `in`.pcncloud.hotel.integration

import android.content.Context
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Panasonic Viera (p00NetworkControl) over Wi‑Fi — switches the display to HDMI 2.
 *
 * Used by Corporate Live TV instead of Onyx IPTV / local CEC key injection.
 */
object PanasonicVieraRemote {

    private const val TAG = "PanasonicVieraRemote"

    /** L&T training room Panasonic on the local LAN. */
    const val TV_HOST = "10.20.25.182"
    const val TV_PORT = 55000
    const val CONTROL_URL = "http://$TV_HOST:$TV_PORT/nrc/control_0"

    private const val SOAP_ACTION =
        "\"urn:panasonic-com:service:p00NetworkControl:1#X_SendKey\""

    private const val SOAP_BODY =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
            """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body>""" +
            """<u:X_SendKey xmlns:u="urn:panasonic-com:service:p00NetworkControl:1">""" +
            """<X_KeyEvent>NRC_HDMI2-ONOFF</X_KeyEvent>""" +
            """</u:X_SendKey>""" +
            """</s:Body>""" +
            """</s:Envelope>"""

    private val xmlMediaType = """text/xml; charset="utf-8"""".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Call from the Live TV click listener.
     * Network work runs on [Dispatchers.IO]; failure toast on the main thread.
     */
    fun switchToHdmi2(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val ok = withContext(Dispatchers.IO) { sendHdmi2Key() }
            if (!ok) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.hdmi_switch_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Blocking OkHttp POST — call only from an IO dispatcher / background thread.
     * @return true if the TV accepted the SOAP command (2xx).
     */
    fun sendHdmi2Key(): Boolean {
        return try {
            val body = SOAP_BODY.toRequestBody(xmlMediaType)
            val request = Request.Builder()
                .url(CONTROL_URL)
                .post(body)
                .header("SOAPACTION", SOAP_ACTION)
                .header("Content-Type", """text/xml; charset="utf-8"""")
                .build()

            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                if (ok) {
                    Log.i(TAG, "HDMI 2 key OK → $CONTROL_URL (${response.code})")
                } else {
                    Log.w(
                        TAG,
                        "HDMI 2 key failed → $CONTROL_URL (${response.code}) ${response.message}",
                    )
                }
                ok
            }
        } catch (t: Throwable) {
            Log.e(TAG, "HDMI 2 SOAP POST failed → $CONTROL_URL", t)
            false
        }
    }
}
