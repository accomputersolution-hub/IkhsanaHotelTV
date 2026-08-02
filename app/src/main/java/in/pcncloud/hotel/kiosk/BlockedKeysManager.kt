package `in`.pcncloud.hotel.kiosk

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.HashSet

/**
 * Persists admin-managed remote keyCodes that [HomeKeyInterceptorService] silently drops.
 * Also hosts the Learn Mode shield flag so OTT keys can be discovered safely.
 *
 * SharedPreferences [getStringSet] returns a live set that must never be mutated in place —
 * always copy via [HashSet] before read/modify/write.
 */
object BlockedKeysManager {

    private const val TAG = "BlockedKeysManager"
    private const val PREFS = "hotel_tv_blocked_keys"
    private const val KEY_BLOCKED = "blocked_keycodes"
    /** SharedPreferences boolean — Learn Mode shield (default false). */
    private const val KEY_LEARN_MODE_ACTIVE = "isLearnModeActive"
    private const val KEY_SEEDED = "defaults_seeded"

    /** Factory-default OTT dedicated buttons (YouTube / Netflix / etc. OEM codes). */
    val DEFAULT_BLOCKED_KEYS: Set<Int> = setOf(5118, 5119, 5121, 5122)

    /** Broadcast when Learn Mode captures a non-navigation remote key. */
    const val ACTION_KEY_LEARNED = "in.pcncloud.hotel.ACTION_KEY_LEARNED"
    const val EXTRA_KEY_CODE = "keyCode"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Always return a defensive copy — never the live SharedPreferences set. */
    private fun readStringSetCopy(context: Context): HashSet<String> {
        val live = prefs(context).getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()
        return HashSet(live)
    }

    private fun ensureDefaultsSeeded(context: Context) {
        try {
            val p = prefs(context)
            if (p.getBoolean(KEY_SEEDED, false)) return
            val existing = readStringSetCopy(context)
            if (existing.isEmpty()) {
                val seed = HashSet(DEFAULT_BLOCKED_KEYS.map { it.toString() })
                p.edit()
                    .putStringSet(KEY_BLOCKED, seed)
                    .putBoolean(KEY_SEEDED, true)
                    .apply()
                Log.i(TAG, "Seeded default blocked keys: $DEFAULT_BLOCKED_KEYS")
            } else {
                p.edit().putBoolean(KEY_SEEDED, true).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureDefaultsSeeded failed (ignored)", e)
        }
    }

    fun getBlockedKeys(context: Context): Set<Int> {
        return try {
            ensureDefaultsSeeded(context)
            val copy = readStringSetCopy(context)
            copy.mapNotNull { it.toIntOrNull() }.toCollection(HashSet())
        } catch (e: Exception) {
            Log.e(TAG, "getBlockedKeys failed — returning empty", e)
            emptySet()
        }
    }

    fun addBlockedKey(context: Context, keyCode: Int) {
        try {
            ensureDefaultsSeeded(context)
            val next = HashSet(getBlockedKeys(context)).apply { add(keyCode) }
            persist(context, next)
            Log.i(TAG, "addBlockedKey $keyCode → $next")
        } catch (e: Exception) {
            Log.e(TAG, "addBlockedKey failed keyCode=$keyCode", e)
        }
    }

    fun removeBlockedKey(context: Context, keyCode: Int) {
        try {
            ensureDefaultsSeeded(context)
            val next = HashSet(getBlockedKeys(context)).apply { remove(keyCode) }
            persist(context, next)
            Log.i(TAG, "removeBlockedKey $keyCode → $next")
        } catch (e: Exception) {
            Log.e(TAG, "removeBlockedKey failed keyCode=$keyCode", e)
        }
    }

    private fun persist(context: Context, keys: Set<Int>) {
        // Fresh HashSet for putStringSet — never reuse a previous prefs instance.
        val toStore = HashSet(keys.map { it.toString() })
        prefs(context).edit()
            .putStringSet(KEY_BLOCKED, toStore)
            .apply()
    }

    /** Pref key for Learn Mode — Accessibility may observe changes. */
    const val PREF_KEY_LEARN_MODE: String = KEY_LEARN_MODE_ACTIVE

    /**
     * In-process Learn Mode flag — updated **synchronously** so Accessibility
     * [HomeKeyInterceptorService.onKeyEvent] sees it on the very next key press
     * (SharedPreferences.apply() alone is too late for OEM OTT buttons).
     */
    @Volatile
    private var learnModeActiveMemory: Boolean = false

    /**
     * Learn Mode shield — when active, Accessibility swallows non-nav keys and
     * broadcasts [ACTION_KEY_LEARNED] instead of letting the OS launch OTT apps.
     */
    fun setLearnMode(context: Context, isActive: Boolean) {
        // Memory first — same-process Accessibility must see this immediately.
        learnModeActiveMemory = isActive
        try {
            // commit() so disk matches memory before the next physical key.
            prefs(context).edit().putBoolean(KEY_LEARN_MODE_ACTIVE, isActive).commit()
            Log.i(TAG, "isLearnModeActive=$isActive (memory+prefs committed)")
        } catch (e: Exception) {
            Log.e(TAG, "setLearnMode prefs failed (memory still $isActive)", e)
        }
    }

    fun isLearnMode(context: Context): Boolean {
        if (learnModeActiveMemory) return true
        return try {
            prefs(context).getBoolean(KEY_LEARN_MODE_ACTIVE, false).also { disk ->
                if (disk) learnModeActiveMemory = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "isLearnMode failed", e)
            false
        }
    }

    /** @deprecated Use [setLearnMode]. */
    fun setLearningMode(context: Context, enabled: Boolean) = setLearnMode(context, enabled)

    /** @deprecated Use [isLearnMode]. */
    fun isLearningMode(context: Context): Boolean = isLearnMode(context)

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
