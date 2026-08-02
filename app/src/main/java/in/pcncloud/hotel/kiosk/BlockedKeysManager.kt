package `in`.pcncloud.hotel.kiosk

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Persists admin-managed remote keyCodes that [HomeKeyInterceptorService] silently drops.
 * Defaults seed OEM OTT dedicated buttons so fresh installs stay safe out of the box.
 */
object BlockedKeysManager {

    private const val TAG = "BlockedKeysManager"
    private const val PREFS = "hotel_tv_blocked_keys"
    private const val KEY_BLOCKED = "blocked_keycodes"
    private const val KEY_LEARNING = "learning_mode"
    private const val KEY_SEEDED = "defaults_seeded"

    /** Factory-default OTT dedicated buttons (YouTube / Netflix / etc. OEM codes). */
    val DEFAULT_BLOCKED_KEYS: Set<Int> = setOf(5118, 5119, 5121, 5122)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureDefaultsSeeded(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_SEEDED, false)) return
        val existing = p.getStringSet(KEY_BLOCKED, null)
        if (existing.isNullOrEmpty()) {
            p.edit()
                .putStringSet(KEY_BLOCKED, DEFAULT_BLOCKED_KEYS.map { it.toString() }.toSet())
                .putBoolean(KEY_SEEDED, true)
                .apply()
            Log.i(TAG, "Seeded default blocked keys: $DEFAULT_BLOCKED_KEYS")
        } else {
            p.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }

    fun getBlockedKeys(context: Context): Set<Int> {
        ensureDefaultsSeeded(context)
        val raw = prefs(context).getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun addBlockedKey(context: Context, keyCode: Int) {
        ensureDefaultsSeeded(context)
        val next = getBlockedKeys(context).toMutableSet().apply { add(keyCode) }
        persist(context, next)
        Log.i(TAG, "addBlockedKey $keyCode → $next")
    }

    fun removeBlockedKey(context: Context, keyCode: Int) {
        ensureDefaultsSeeded(context)
        val next = getBlockedKeys(context).toMutableSet().apply { remove(keyCode) }
        persist(context, next)
        Log.i(TAG, "removeBlockedKey $keyCode → $next")
    }

    private fun persist(context: Context, keys: Set<Int>) {
        prefs(context).edit()
            .putStringSet(KEY_BLOCKED, keys.map { it.toString() }.toSet())
            .apply()
    }

    /**
     * When true, [HomeKeyInterceptorService] must NOT swallow blocked OTT keys so the
     * Admin "Learn New Key" UI can observe them via [android.app.Activity.dispatchKeyEvent].
     */
    fun setLearningMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LEARNING, enabled).apply()
        Log.i(TAG, "learningMode=$enabled")
    }

    fun isLearningMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LEARNING, false)

    fun registerChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** Pref key for [KEY_BLOCKED] — used by interceptor change listener. */
    const val PREF_KEY_BLOCKED: String = KEY_BLOCKED
}
