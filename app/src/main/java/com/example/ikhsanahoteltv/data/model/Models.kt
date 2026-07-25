package com.example.ikhsanahoteltv.data.model

data class GuestProfile(
    val guestName: String = "Guest",
    val roomNumber: String = "",
    val hotelName: String = "",
    val hotelLogoUrl: String = "",
    val hotelInfo: String = "",
    val checkInDate: String = "",
    val checkOutDate: String = "",
    val sessionKey: String = "",
    val activeOrdersCount: Int = 0,
    val activeMessagesCount: Int = 0,
)

data class MenuItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: MenuCategory = MenuCategory.STARTERS,
    val imageUrl: String = "",
    val available: Boolean = true,
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
