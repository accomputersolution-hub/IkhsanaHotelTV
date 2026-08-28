package `in`.pcncloud.hotel.tailscale.embed

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.net.NetworkInterface
import java.security.GeneralSecurityException
import java.util.Collections
import org.json.JSONArray

/**
 * Bridges Android APIs required by libtailscale [libtailscale.AppContext].
 */
class EmbeddedTailscaleAppContext(
    private val context: Context,
) : libtailscale.AppContext {

    private val tag = "EmbeddedTsAppCtx"
    private val prefsName = "embedded_tailscale_secret_prefs"

    companion object {
        /** Logcat tag for libtailscale Go log.Printf / control client output. */
        const val GO_LOG_TAG = "EmbeddedTsGo"
        /**
         * Key read by patched libtailscale at LocalBackend.Start() (customLoginServerPrefKey).
         * Must be written before [libtailscale.Libtailscale.start].
         */
        private const val PREF_CUSTOM_LOGIN_SERVER = "customloginserver"
    }

    override fun log(tag: String, logLine: String) {
        // Go runtime logs (control client, register errors) — use fixed tag for logcat.
        val line = logLine.trimEnd()
        if (line.isNotEmpty()) {
            Log.i(GO_LOG_TAG, line)
        }
    }

    override fun encryptToPref(key: String?, value: String?) {
        encryptedPrefs().edit().putString(key, value).commit()
    }

    override fun decryptFromPref(key: String?): String? =
        encryptedPrefs().getString(key, null)

    override fun getStateStoreKeysJSON(): String {
        val prefix = "statestore-"
        val keys = encryptedPrefs().all.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
        return JSONArray(keys).toString()
    }

    override fun getOSVersion(): String = Build.VERSION.RELEASE

    override fun getSDKInt(): Long = Build.VERSION.SDK_INT.toLong()

    override fun getDeviceName(): String {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    override fun getInstallSource(): String = context.packageName

    override fun shouldUseGoogleDNSFallback(): Boolean = true

    override fun isChromeOS(): Boolean =
        context.packageManager.hasSystemFeature("android.hardware.type.pc")

    override fun isClientLoggingEnabled(): Boolean = false

    override fun getInterfacesAsJson(): String {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val parts = interfaces.mapNotNull { nif ->
            try {
                val addrs = Collections.list(nif.inetAddresses).mapNotNull { addr ->
                    val host = addr.hostAddress ?: return@mapNotNull null
                    val prefix = if (addr is java.net.Inet4Address) 32 else 128
                    "\"ip\":\"$host\",\"prefixLen\":$prefix"
                }
                """{"name":"${nif.name}","index":${nif.index},"mtu":${nif.mtu},"addrs":[${addrs.joinToString(",", prefix = "{", postfix = "}")}]}"""
            } catch (_: Exception) {
                null
            }
        }
        return parts.joinToString(prefix = "[", postfix = "]")
    }

    override fun getPlatformDNSConfig(): String = ""

    override fun getSyspolicyStringValue(key: String): String = ""

    override fun getSyspolicyBooleanValue(key: String): Boolean = false

    override fun getSyspolicyStringArrayJSONValue(key: String): String = "[]"

    override fun hardwareAttestationKeySupported(): Boolean = false

    override fun hardwareAttestationKeyCreate(): String = ""

    override fun hardwareAttestationKeyRelease(id: String) {}

    override fun hardwareAttestationKeyPublic(id: String): ByteArray = ByteArray(0)

    override fun hardwareAttestationKeySign(id: String, data: ByteArray): ByteArray = ByteArray(0)

    override fun hardwareAttestationKeyLoad(id: String) {}

    override fun bindSocketToNetwork(fd: Int): Boolean = false

    override fun getUserCACertsPEM(): ByteArray = ByteArray(0)

    /**
     * Injected directly into libtailscale LocalBackend.Start(ipn.Options.UpdatePrefs)
     * — no LocalAPI PATCH required. Primary source for Headscale ControlURL.
     */
    override fun getEmbeddedControlURL(): String =
        EmbeddedTailscaleCredentials.CONTROL_URL

    /**
     * Backup path: encrypted pref read by Go if [getEmbeddedControlURL] is empty.
     */
    fun writeHeadscaleControlUrlForEngineStart(controlUrl: String) {
        encryptedPrefs().edit().putString(PREF_CUSTOM_LOGIN_SERVER, controlUrl).commit()
        Log.i(tag, "Seeded $PREF_CUSTOM_LOGIN_SERVER=$controlUrl (before Libtailscale.start)")
    }

    private fun encryptedPrefs(): android.content.SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            prefsName,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
