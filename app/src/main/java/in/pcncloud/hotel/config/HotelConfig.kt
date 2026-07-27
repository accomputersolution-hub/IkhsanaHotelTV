package `in`.pcncloud.hotel.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig

/**
 * Multi-tenant hotel / room identity for this TV device.
 *
 * Hotel id is set via [setHotelId] after pairing (or adb). Until then [getHotelId]
 * returns null and the app shows [in.pcncloud.hotel.PairingActivity].
 *
 * Tenant root: `Hotels/{hotelId}/…`
 */
class HotelConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    /** Persist the paired hotel slug under SharedPreferences. */
    fun setHotelId(hotelId: String) {
        val normalized = normalizeHotelId(hotelId)
        require(normalized.isNotBlank()) { "Hotel ID cannot be blank" }
        prefs.edit().putString(KEY_HOTEL_ID, normalized).apply()
        Log.d(TAG, "setHotelId → $normalized")
    }

    fun clearHotelId() {
        prefs.edit().remove(KEY_HOTEL_ID).apply()
        Log.d(TAG, "clearHotelId")
    }

    fun isPaired(): Boolean = getHotelId() != null

    val roomNumber: String
        get() = prefs.getString(KEY_ROOM_NUMBER, BuildConfig.DEFAULT_ROOM_NUMBER)!!.trim()
            .ifBlank { BuildConfig.DEFAULT_ROOM_NUMBER }

    fun setRoomNumber(roomNumber: String) {
        val room = roomNumber.trim().ifBlank { BuildConfig.DEFAULT_ROOM_NUMBER }
        prefs.edit().putString(KEY_ROOM_NUMBER, room).apply()
        Log.d(TAG, "setRoomNumber → $room")
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
            "HotelConfig ready → hotelId=${getHotelId() ?: "(unpaired)"} room=$roomNumber",
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
