package `in`.pcncloud.hotel.vpn

import android.content.Context
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import java.io.File

/**
 * Loads WireGuard config for the built-in kiosk VPN.
 *
 * Priority:
 * 1. App-private file [FILE_NAME] (writable / remotely provisionable override)
 * 2. Packaged [assets/kiosk_vpn.conf]
 * 3. Embedded [DEFAULT_CONFIG] (verified corporate credentials)
 */
object KioskVpnConfigStore {

    private const val TAG = "KioskVpnConfig"
    const val FILE_NAME = "kiosk_vpn.conf"
    private const val ASSET_NAME = "kiosk_vpn.conf"

    /** Verified corporate WireGuard peer — see [KioskVpnCredentials]. */
    val DEFAULT_CONFIG: String get() = KioskVpnCredentials.toWireGuardConf()

    fun hasUsableConfig(context: Context): Boolean {
        val text = loadConfigText(context) ?: return false
        return isUsable(text)
    }

    fun loadConfigText(context: Context): String? {
        if (!BuildConfig.IS_CORPORATE) return null
        return try {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            val fromFile = if (file.isFile && file.length() > 0L) {
                file.readText().trim().ifBlank { null }
            } else {
                null
            }
            if (fromFile != null && isUsable(fromFile)) return fromFile

            val fromAsset = try {
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
                    .trim().ifBlank { null }
            } catch (_: Throwable) {
                null
            }
            if (fromAsset != null && isUsable(fromAsset)) return fromAsset

            DEFAULT_CONFIG
        } catch (t: Throwable) {
            Log.w(TAG, "loadConfigText failed — using embedded default", t)
            DEFAULT_CONFIG
        }
    }

    fun saveConfigText(context: Context, conf: String): Boolean {
        return try {
            File(context.applicationContext.filesDir, FILE_NAME).writeText(conf.trim())
            true
        } catch (t: Throwable) {
            Log.e(TAG, "saveConfigText failed", t)
            false
        }
    }

    private fun isUsable(text: String): Boolean =
        text.contains("[Interface]", ignoreCase = true) &&
            text.contains("PrivateKey", ignoreCase = true) &&
            text.contains("[Peer]", ignoreCase = true) &&
            !text.contains("<device_private_key>") &&
            !text.contains("<server_public_key>")
}
