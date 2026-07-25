package com.example.ikhsanahoteltv.config

import android.content.Context
import android.content.SharedPreferences
import com.example.ikhsanahoteltv.BuildConfig

/**
 * Holds the multi-tenant hotel and room identity for this TV device.
 * Override defaults via SharedPreferences or adb:
 *   adb shell am broadcast -a com.example.ikhsanahoteltv.PROVISION \
 *     --es hotel_id "grand_palace" --es room_number "305"
 */
class HotelConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hotelId: String
        get() = prefs.getString(KEY_HOTEL_ID, BuildConfig.DEFAULT_HOTEL_ID)!!.trim()

    val roomNumber: String
        get() = prefs.getString(KEY_ROOM_NUMBER, BuildConfig.DEFAULT_ROOM_NUMBER)!!.trim()

    fun update(hotelId: String, roomNumber: String) {
        prefs.edit()
            .putString(KEY_HOTEL_ID, hotelId)
            .putString(KEY_ROOM_NUMBER, roomNumber)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "hotel_tv_config"
        private const val KEY_HOTEL_ID = "hotel_id"
        private const val KEY_ROOM_NUMBER = "room_number"
    }
}
