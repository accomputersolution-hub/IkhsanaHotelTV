package `in`.pcncloud.hotel.wireguard

import android.content.Context

/**
 * Tracks whether the user accepted the system [android.net.VpnService.prepare] dialog.
 *
 * Device Owner [setAlwaysOnVpnPackage] can make [VpnService.prepare] return null without
 * ever showing that dialog. We only set Always-On after this flag is true.
 */
object WireGuardConsentStore {
    private const val PREFS = "wireguard_vpn_consent"
    private const val KEY_GRANTED = "user_granted"

    fun hasUserGranted(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GRANTED, false)

    fun markUserGranted(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GRANTED, true)
            .apply()
    }
}
