package `in`.pcncloud.hotel.data

/**
 * Single source of truth for Firestore paths.
 * Must stay in sync with admin-panel/js/paths.js and Android TV listeners.
 *
 * Strict multi-tenant root: **Hotels** (capital H) / {hotelId} / …
 * [hotelId] comes from SharedPreferences after pairing — never hardcoded.
 */
object FirestorePaths {
    /** Collection name — never lowercase "hotels". */
    const val HOTELS = "Hotels"

    const val ROOMS = "Rooms"
    const val MENU = "Menu"
    const val ALERTS = "Alerts"
    const val REQUESTS = "Requests"
    const val BROADCASTS = "Broadcasts"
    const val CONFIG = "Config"
    const val EMERGENCY_CONTACTS = "Emergency_Contacts"
    const val DAILY_AGENDA = "Daily_Agenda"
    const val LIVE_ORDERS = "Live_Orders"

    /** Trim, lowercase, hyphens → underscores (same rules as HotelConfig). */
    fun normalizeHotelId(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().lowercase().replace('-', '_').trim('_')
    }

    fun hotelDocument(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}"

    fun roomsCollection(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$ROOMS"

    fun roomDocument(hotelId: String, roomNumber: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$ROOMS/${normalizeRoom(roomNumber)}"

    fun menuCollection(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$MENU"

    fun alertsCollection(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$ALERTS"

    fun requestsCollection(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$REQUESTS"

    fun broadcastsCollection(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$BROADCASTS"

    fun emergencyContactsCollection(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$EMERGENCY_CONTACTS"

    fun dailyAgendaCollection(hotelId: String): String =
        "$HOTELS/${normalizeHotelId(hotelId)}/$DAILY_AGENDA"

    fun normalizeRoom(roomNumber: String): String =
        roomNumber.trim()
}
