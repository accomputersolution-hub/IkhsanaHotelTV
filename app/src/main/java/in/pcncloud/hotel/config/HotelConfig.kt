package `in`.pcncloud.hotel.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import java.util.UUID

/**
 * Multi-tenant hotel / room identity for this TV device.
 *
 * Hotel id is set via [setHotelId] after pairing (or adb). Until then [getHotelId]
 * returns null and the app shows [in.pcncloud.hotel.PairingActivity].
 *
 * Tenant root: `Hotels/{hotelId}/…`
 */
class HotelConfig(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val introCache = IntroVideoCache(appContext)

    /**
     * Paired tenant id, or null if this TV has not been paired yet.
     * Always normalized (lowercase, hyphens → underscores).
     */
    fun getHotelId(): String? {
        val raw = prefs.getString(KEY_HOTEL_ID, null)?.trim()
        if (raw.isNullOrBlank()) return null
        val normalized = normalizeHotelId(raw)
        return normalized.ifBlank { null }
    }

    /**
     * Explicitly paired room number, or null if never set / cleared by logout.
     * Does **not** invent [BuildConfig.DEFAULT_ROOM_NUMBER] — that would block Pairing.
     */
    fun getRoomNumberOrNull(): String? {
        val raw = prefs.getString(KEY_ROOM_NUMBER, null)?.trim()
        return raw?.takeIf { it.isNotEmpty() }
    }

    /** Persist the paired hotel slug under SharedPreferences. */
    fun setHotelId(hotelId: String) {
        val normalized = normalizeHotelId(hotelId)
        require(normalized.isNotBlank()) { "Hotel ID cannot be blank" }
        val previous = prefs.getString(KEY_HOTEL_ID, null)?.let(::normalizeHotelId)
        prefs.edit().putString(KEY_HOTEL_ID, normalized).apply()
        Log.d(TAG, "setHotelId → $normalized")
        if (previous != normalized) {
            // Drop Treasure Island (etc.) whitelist so Upper Deck never inherits it.
            KioskPolicy.clearTenantWhitelistCache(appContext)
            KioskPolicy.bindWhitelistToHotelOrClear(appContext, normalized)
        }
    }

    /**
     * Clears hotel id only. Prefer [clearPairingSession] for full logout / unpair.
     */
    fun clearHotelId() {
        clearPairingSession()
    }

    /**
     * Full local pairing wipe: hotel id + room number + tenant kiosk/whitelist cache.
     * Used by Admin Unpair and remote RTDB logout.
     */
    fun clearPairingSession() {
        prefs.edit()
            .remove(KEY_HOTEL_ID)
            .remove(KEY_ROOM_NUMBER)
            .apply()
        introCache.clear()
        KioskPolicy.clearTenantKioskCache(appContext)
        Log.i(TAG, "clearPairingSession — hotel/room prefs + intro cache + kiosk cache wiped")
    }

    /** True only when both hotel id and room number are present. */
    fun isPaired(): Boolean {
        val hotel = getHotelId()
        val room = getRoomNumberOrNull()
        return !hotel.isNullOrBlank() && !room.isNullOrBlank()
    }

    /**
     * Room for guest UI when paired. Falls back to build default only for
     * legacy installs that have a hotel id but never persisted a room key.
     */
    val roomNumber: String
        get() = getRoomNumberOrNull()
            ?: BuildConfig.DEFAULT_ROOM_NUMBER

    fun setRoomNumber(roomNumber: String) {
        val room = roomNumber.trim()
        require(room.isNotBlank()) { "Room number cannot be blank" }
        prefs.edit().putString(KEY_ROOM_NUMBER, room).apply()
        Log.d(TAG, "setRoomNumber → $room")
    }

    /** Stable device id for pairing_codes/{code}.deviceId (survives app restarts). */
    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)?.trim()
        if (!existing.isNullOrBlank() && existing.length >= 16) return existing
        val created = try {
            UUID.randomUUID().toString()
        } catch (_: Exception) {
            "tv_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        Log.d(TAG, "Created deviceId → $created")
        return created
    }

    init {
        // Migrate hyphenated prefs to underscore form (do not invent a default hotel).
        val raw = prefs.getString(KEY_HOTEL_ID, null)
        if (!raw.isNullOrBlank()) {
            val normalized = normalizeHotelId(raw)
            if (raw != normalized) {
                prefs.edit().putString(KEY_HOTEL_ID, normalized).apply()
                Log.d(TAG, "Migrated hotelId '$raw' → '$normalized'")
            }
        }
        Log.d(
            TAG,
            "HotelConfig ready → hotelId=${getHotelId() ?: "(unpaired)"} " +
                "room=${getRoomNumberOrNull() ?: "(none)"} paired=${isPaired()}",
        )
    }

    fun update(hotelId: String, roomNumber: String) {
        setHotelId(hotelId)
        setRoomNumber(roomNumber)
    }

    companion object {
        private const val TAG = "HotelConfig"
        private const val PREFS_NAME = "hotel_tv_config"
        private const val KEY_HOTEL_ID = "hotel_id"
        private const val KEY_ROOM_NUMBER = "room_number"
        private const val KEY_DEVICE_ID = "device_id"

        /**
         * Trim, lowercase, hyphens → underscores.
         * Does not invent a default tenant — blank stays blank.
         */
        fun normalizeHotelId(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw
                .trim()
                .lowercase()
                .replace('-', '_')
                .trim('_')
        }
    }
}
