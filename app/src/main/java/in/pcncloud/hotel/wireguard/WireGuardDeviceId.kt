package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.UUID

/**
 * Stable device identity for WireGuard peer registration.
 *
 * Prefer [Settings.Secure.ANDROID_ID] (survives app reinstall on the same
 * device). If null/blank, fall back to a randomly generated UUID persisted
 * in SharedPreferences (survives process death, not factory reset / uninstall).
 */
object WireGuardDeviceId {
    private const val TAG = "WireGuardDeviceId"
    private const val PREFS = "wireguard_device_identity"
    private const val KEY_FALLBACK_UUID = "fallback_device_uuid"

    fun resolve(context: Context): String {
        val app = context.applicationContext
        val androidId = runCatching {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()

        if (!androidId.isNullOrBlank() && !isBrokenAndroidId(androidId)) {
            Log.d(TAG, "Using ANDROID_ID=${androidId.take(8)}…")
            return androidId
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_FALLBACK_UUID, null)?.trim()
        if (!existing.isNullOrBlank()) {
            Log.d(TAG, "Using stored fallback UUID=${existing.take(8)}…")
            return existing
        }

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_FALLBACK_UUID, created).apply()
        Log.i(TAG, "ANDROID_ID missing — generated fallback UUID=${created.take(8)}…")
        return created
    }

    /** Older emulators returned a constant all-zeros id — treat as unusable. */
    private fun isBrokenAndroidId(id: String): Boolean =
        id.equals("9774d56d682e549c", ignoreCase = true) ||
            id.matches(Regex("^0+$"))
}
