package `in`.pcncloud.hotel.data.model

data class GuestProfile(
    val guestName: String = "Guest",
    /** Optional honorific from PMS, e.g. "Mr.", "Ms.", "Mrs.", "Dr." — empty = no prefix. */
    val salutation: String = "",
    /** Always a String — numeric ("101") or named ("Middle East"). Never Int. */
    val roomNumber: String = "",
    val hotelName: String = "",
    val hotelLogoUrl: String = "",
    val hotelInfo: String = "",
    val checkInDate: String = "",
    val checkOutDate: String = "",
    val sessionKey: String = "",
    val activeOrdersCount: Int = 0,
    val activeMessagesCount: Int = 0,
    val bgWallpaperUrl: String = "",
    /** Optional night wallpaper; blank → keep day image after 18:00. */
    val bgWallpaperDarkUrl: String = "",
    val themeColor: String = "",
    val tagline: String = "",
    val welcomeMessage: String = "",
)

/** Branding from Hotels/{hotelId} document (multi-tenant master panel). */
data class HotelBranding(
    val hotelId: String = "",
    val hotelName: String = "",
    val logoUrl: String = "",
    val bgWallpaperUrl: String = "",
    /**
     * Optional night wallpaper (`bgWallpaperDark` / `bgWallpaperNight` / `bg_wallpaper_dark`).
     * When blank at night, TV keeps showing [bgWallpaperUrl].
     */
    val bgWallpaperDarkUrl: String = "",
    val themeColor: String = "",
    val tagline: String = "",
    val welcomeMessage: String = "",
    /** Bottom home ticker from Hotels/{id}.announcement / ticker_text. */
    val announcement: String = "",
    val status: String = "active",
    /** Corporate helpdesk contacts from Hotels/{id}/Emergency_Contacts (legacy: .emergency_contacts). */
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    /** Corporate daily schedule from Hotels/{id}/Daily_Agenda (legacy: .daily_agenda). */
    val dailyAgenda: List<AgendaItem> = emptyList(),
    /**
     * When true, guest alerts may use SYSTEM_ALERT_WINDOW over YouTube / Live TV.
     * Hotels/{hotelId}.allowOverlayPopups (default true).
     */
    val allowOverlayPopups: Boolean = true,
)

/** One entry from Hotels/{hotelId}/Emergency_Contacts (admin Helpdesk Config). */
data class EmergencyContact(
    val id: String = "",
    val title: String = "",
    val extension: String = "",
)

/** One entry from Hotels/{hotelId}/Daily_Agenda (admin Daily Agenda / docx import). */
data class AgendaItem(
    val id: String = "",
    val time: String = "",
    val title: String = "",
    val location: String = "",
    /** Optional calendar day (`yyyy-MM-dd` or display label). Blank = show on today's board. */
    val date: String = "",
    /** Contacts / footnotes from the schedule document. */
    val notes: String = "",
)

/** Live room / guest status from Hotels/{hotelId}/Rooms/{roomNumber}. */
data class RoomStatus(
    /** Document id / room key as String — "101" or "Middle East". */
    val roomNumber: String = "",
    val guestName: String = "",
    val status: String = "",
    val sessionKey: String = "",
    val occupied: Boolean = false,
    val checkInDate: String = "",
    val checkOutDate: String = "",
    /** Per-room home card visibility (admin Feature Toggles). Missing → shown. */
    val features: RoomFeatureFlags = RoomFeatureFlags(),
)

/**
 * Hotels/{hotelId}/Rooms/{roomId} feature toggle flags.
 * Defaults **true** so existing rooms keep all cards until admin hides them.
 */
data class RoomFeatureFlags(
    val showLiveTv: Boolean = true,
    val showEntertainment: Boolean = true,
    val showDining: Boolean = true,
    val showAgenda: Boolean = true,
    val showServices: Boolean = true,
    val showAlerts: Boolean = true,
)

data class MenuItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: MenuCategory = MenuCategory.STARTERS,
    val imageUrl: String = "",
    val available: Boolean = true,
    /** True = vegetarian (green badge); false = non-veg (red badge). */
    val isVeg: Boolean = true,
)

enum class MenuCategory(val displayName: String, val firestoreKey: String) {
    STARTERS("Starters", "starters"),
    MAIN_COURSE("Main Course", "main_course"),
    BEVERAGES("Beverages", "beverages"),
    DESSERTS("Desserts", "desserts");

    companion object {
        fun fromKey(key: String?): MenuCategory =
            entries.find { it.firestoreKey == key }
                ?: when (key) {
                    "breakfast", "snacks" -> STARTERS
                    else -> STARTERS
                }
    }
}

enum class OrderStatus(val key: String, val label: String) {
    PENDING("pending", "Pending"),
    PREPARING("preparing", "Preparing"),
    DELIVERED("delivered", "Delivered");

    companion object {
        fun fromKey(key: String?): OrderStatus =
            entries.find { it.key == key } ?: PENDING
    }
}

data class CartItem(
    val menuItem: MenuItem,
    val quantity: Int = 1,
) {
    val lineTotal: Double get() = menuItem.price * quantity
}

/** Payment method selected by the guest at checkout. */
enum class PaymentMethod(val key: String, val label: String) {
    PAY_AT_CHECKOUT("PAY_AT_CHECKOUT", "Pay at Checkout"),
    PAID_ONLINE("PAID_ONLINE", "Pay Now");

    companion object {
        fun fromKey(key: String?): PaymentMethod =
            entries.find { it.key == key } ?: PAY_AT_CHECKOUT
    }
}

data class LiveOrder(
    val id: String = "",
    val hotelId: String = "",
    val roomNumber: String = "",
    val guestName: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val status: String = "pending",
    val timestamp: Long = System.currentTimeMillis(),
    val sessionKey: String = "",
    val archived: Boolean = false,
    val paymentMethod: PaymentMethod = PaymentMethod.PAY_AT_CHECKOUT,
)

data class OrderLineItem(
    val itemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
)

data class HotelAlert(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val roomNumber: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false,
    val priority: String = "normal",
    val durationMs: Long = 0L,
    val revoked: Boolean = false,
    val sessionKey: String = "",
    val archived: Boolean = false,
)

data class ServiceRequest(
    val id: String = "",
    val roomNumber: String = "",
    val guestName: String = "",
    val department: String = "",
    val serviceType: String = "",
    val serviceLabel: String = "",
    val status: String = "pending",
    val timestamp: Long = 0L,
    val sessionKey: String = "",
    val archived: Boolean = false,
)
