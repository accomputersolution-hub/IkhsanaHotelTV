package com.example.ikhsanahoteltv.data

/**
 * Single source of truth for Firestore paths.
 * Must stay in sync with admin-panel/js/paths.js
 */
object FirestorePaths {
    const val HOTELS = "Hotels"
    const val ROOMS = "Rooms"
    const val MENU = "Menu"
    const val ALERTS = "Alerts"
    const val REQUESTS = "Requests"
    const val LIVE_ORDERS = "Live_Orders"

    fun roomDocument(hotelId: String, roomNumber: String): String =
        "$HOTELS/$hotelId/$ROOMS/${normalizeRoom(roomNumber)}"

    fun alertsCollection(hotelId: String): String =
        "$HOTELS/$hotelId/$ALERTS"

    fun requestsCollection(hotelId: String): String =
        "$HOTELS/$hotelId/$REQUESTS"

    fun normalizeRoom(roomNumber: String): String =
        roomNumber.trim()
}
