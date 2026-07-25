package com.example.ikhsanahoteltv.data.repository

import android.util.Log
import com.example.ikhsanahoteltv.config.HotelConfig
import com.example.ikhsanahoteltv.data.FirestorePaths
import com.example.ikhsanahoteltv.data.model.GuestProfile
import com.example.ikhsanahoteltv.data.model.HotelAlert
import com.example.ikhsanahoteltv.data.model.LiveOrder
import com.example.ikhsanahoteltv.data.model.MenuCategory
import com.example.ikhsanahoteltv.data.model.MenuItem
import com.example.ikhsanahoteltv.data.model.OrderLineItem
import com.example.ikhsanahoteltv.data.model.ServiceRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val config: HotelConfig,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private val hotelId get() = config.hotelId
    private val roomNumber get() = FirestorePaths.normalizeRoom(config.roomNumber)

    @Volatile
    private var currentSessionKey: String = ""

    fun currentSessionKey(): String = currentSessionKey

    fun observeGuestProfile(): Flow<GuestProfile> = callbackFlow {
        val docPath = FirestorePaths.roomDocument(hotelId, roomNumber)
        Log.d(TAG, "Listening guest profile: $docPath")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ROOMS)
            .document(roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Guest profile listener error at $docPath", error)
                    trySend(defaultGuestProfile())
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w(TAG, "Guest profile document not found: $docPath — using defaults")
                    trySend(defaultGuestProfile())
                    return@addSnapshotListener
                }

                val data = snapshot.data
                if (data != null) {
                    val sessionKey = data["sessionKey"] as? String ?: ""
                    currentSessionKey = sessionKey
                    val profile = GuestProfile(
                        guestName = data["guestName"] as? String ?: "Guest",
                        roomNumber = roomNumber,
                        hotelName = data["hotelName"] as? String ?: "Ikhsana Hotel",
                        hotelLogoUrl = data["hotelLogoUrl"] as? String ?: "",
                        hotelInfo = data["hotelInfo"] as? String ?: "",
                        checkInDate = data["checkInDate"] as? String ?: "",
                        checkOutDate = data["checkOutDate"] as? String ?: "",
                        sessionKey = sessionKey,
                        activeOrdersCount = (data["activeOrdersCount"] as? Number)?.toInt() ?: 0,
                        activeMessagesCount = (data["activeMessagesCount"] as? Number)?.toInt() ?: 0,
                    )
                    Log.d(TAG, "Guest profile updated: ${profile.guestName} (room $roomNumber, session=${profile.sessionKey})")
                    trySend(profile)
                } else {
                    trySend(defaultGuestProfile())
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeMenuItems(): Flow<List<MenuItem>> = callbackFlow {
        val path = "${FirestorePaths.HOTELS}/$hotelId/${FirestorePaths.MENU}"
        Log.d(TAG, "Listening menu: $path")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.MENU)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Menu listener error at $path", error)
                    trySend(defaultMenuItems())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val categoryKey = data["category"] as? String ?: "starters"
                    MenuItem(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                        category = MenuCategory.fromKey(categoryKey),
                        imageUrl = data["imageUrl"] as? String ?: "",
                        available = data["available"] as? Boolean ?: true,
                    )
                } ?: emptyList()

                trySend(if (items.isEmpty()) defaultMenuItems() else items)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Alerts live at Hotels/{hotelId}/Alerts with a roomNumber field.
     * Query uses equality filter only (no orderBy) to avoid requiring a composite index.
     * Results are sorted client-side by timestamp descending.
     */
    fun observeAlerts(): Flow<List<HotelAlert>> = callbackFlow {
        val collectionPath = FirestorePaths.alertsCollection(hotelId)
        Log.d(TAG, "Listening alerts: $collectionPath where roomNumber == '$roomNumber'")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ALERTS)
            .whereEqualTo("roomNumber", roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Alerts listener error at $collectionPath", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val alerts = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    HotelAlert(
                        id = doc.id,
                        title = data["title"] as? String ?: "Message",
                        message = data["message"] as? String ?: "",
                        roomNumber = data["roomNumber"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        read = data["read"] as? Boolean ?: false,
                        priority = data["priority"] as? String ?: "normal",
                        durationMs = (data["durationMs"] as? Number)?.toLong() ?: 0L,
                        revoked = data["revoked"] as? Boolean ?: false,
                        sessionKey = data["sessionKey"] as? String ?: "",
                        archived = data["archived"] as? Boolean ?: false,
                    )
                }?.filter { alert ->
                    !alert.archived && belongsToCurrentSession(alert.sessionKey)
                }?.sortedByDescending { it.timestamp } ?: emptyList()

                Log.d(TAG, "Alerts received for room $roomNumber: ${alerts.size} (${alerts.count { !it.read }} unread)")
                trySend(alerts)
            }
        awaitClose { listener.remove() }
    }

    /** Real-time orders for this room — used on the dining / order history screen. */
    fun observeRoomOrders(): Flow<List<LiveOrder>> = callbackFlow {
        val path = FirestorePaths.LIVE_ORDERS
        Log.d(TAG, "Listening room orders: $path where roomNumber == '$roomNumber'")

        val listener = firestore
            .collection(FirestorePaths.LIVE_ORDERS)
            .whereEqualTo("roomNumber", roomNumber)
            .whereEqualTo("hotelId", hotelId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Room orders listener error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val itemsRaw = data["items"] as? List<Map<String, Any>> ?: emptyList()
                    LiveOrder(
                        id = doc.id,
                        hotelId = data["hotelId"] as? String ?: "",
                        roomNumber = data["roomNumber"] as? String ?: "",
                        guestName = data["guestName"] as? String ?: "",
                        items = itemsRaw.map { item ->
                            OrderLineItem(
                                itemId = item["itemId"] as? String ?: "",
                                name = item["name"] as? String ?: "",
                                quantity = (item["quantity"] as? Number)?.toInt() ?: 1,
                                unitPrice = (item["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                            )
                        },
                        totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0,
                        status = data["status"] as? String ?: "pending",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        sessionKey = data["sessionKey"] as? String ?: "",
                        archived = data["archived"] as? Boolean ?: false,
                    )
                }?.filter { order ->
                    !order.archived && belongsToCurrentSession(order.sessionKey)
                }?.sortedByDescending { it.timestamp } ?: emptyList()

                Log.d(TAG, "Room orders updated: ${orders.size}")
                trySend(orders)
            }
        awaitClose { listener.remove() }
    }

    suspend fun placeOrder(order: LiveOrder): Result<String> = runCatching {
        val docRef = firestore.collection(FirestorePaths.LIVE_ORDERS).document()
        val payload = hashMapOf(
            "hotelId" to order.hotelId,
            "roomNumber" to FirestorePaths.normalizeRoom(order.roomNumber),
            "guestName" to order.guestName,
            "items" to order.items.map { item ->
                hashMapOf(
                    "itemId" to item.itemId,
                    "name" to item.name,
                    "quantity" to item.quantity,
                    "unitPrice" to item.unitPrice,
                )
            },
            "totalAmount" to order.totalAmount,
            "status" to order.status,
            "timestamp" to order.timestamp,
            "sessionKey" to currentSessionKey,
            "archived" to false,
        )
        docRef.set(payload).await()
        Log.d(TAG, "Order placed: ${FirestorePaths.LIVE_ORDERS}/${docRef.id}")
        docRef.id
    }

    suspend fun markAlertRead(alertId: String) {
        val docPath = "${FirestorePaths.alertsCollection(hotelId)}/$alertId"
        firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ALERTS)
            .document(alertId)
            .update("read", true)
            .await()
        Log.d(TAG, "Alert marked read: $docPath")
    }

    suspend fun submitServiceRequest(
        department: String,
        serviceType: String,
        serviceLabel: String,
        guestName: String,
    ): Result<String> = runCatching {
        val path = FirestorePaths.requestsCollection(hotelId)
        val docRef = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.REQUESTS)
            .document()
        val payload = hashMapOf(
            "roomNumber" to roomNumber,
            "guestName" to guestName,
            "department" to department,
            "serviceType" to serviceType,
            "serviceLabel" to serviceLabel,
            "status" to "pending",
            "source" to "tv",
            "timestamp" to System.currentTimeMillis(),
            "sessionKey" to currentSessionKey,
            "archived" to false,
        )
        docRef.set(payload).await()
        Log.d(TAG, "Service request submitted: $path/${docRef.id} → $serviceLabel")
        docRef.id
    }

    /** Live service requests for this room — status updates from admin panel. */
    fun observeRoomServiceRequests(): Flow<List<ServiceRequest>> = callbackFlow {
        val path = FirestorePaths.requestsCollection(hotelId)
        Log.d(TAG, "Listening service requests: $path where roomNumber == '$roomNumber'")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.REQUESTS)
            .whereEqualTo("roomNumber", roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Service requests listener error at $path", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    ServiceRequest(
                        id = doc.id,
                        roomNumber = data["roomNumber"] as? String ?: "",
                        guestName = data["guestName"] as? String ?: "",
                        department = data["department"] as? String ?: "",
                        serviceType = data["serviceType"] as? String ?: "",
                        serviceLabel = data["serviceLabel"] as? String ?: "",
                        status = data["status"] as? String ?: "pending",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        sessionKey = data["sessionKey"] as? String ?: "",
                        archived = data["archived"] as? Boolean ?: false,
                    )
                }?.filter { request ->
                    !request.archived && belongsToCurrentSession(request.sessionKey)
                }?.sortedByDescending { it.timestamp } ?: emptyList()

                Log.d(TAG, "Service requests for room $roomNumber: ${requests.size}")
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    private fun belongsToCurrentSession(itemSessionKey: String): Boolean {
        val activeSession = currentSessionKey
        if (activeSession.isBlank()) return itemSessionKey.isBlank()
        return itemSessionKey.isBlank() || itemSessionKey == activeSession
    }

    private fun defaultGuestProfile() = GuestProfile(
        guestName = "Guest",
        roomNumber = roomNumber,
        hotelName = "Ikhsana Hotel",
        hotelInfo = "Welcome to Ikhsana Hotel. Enjoy your stay!",
    )

    private fun defaultMenuItems(): List<MenuItem> = listOf(
        MenuItem("s1", "Soup of the Day", "Chef's seasonal starter", 220.0, MenuCategory.STARTERS),
        MenuItem("s2", "Veg Spring Rolls", "Crispy rolls with dip", 180.0, MenuCategory.STARTERS),
        MenuItem("m1", "Butter Chicken", "Rich tomato gravy", 450.0, MenuCategory.MAIN_COURSE),
        MenuItem("m2", "Paneer Tikka Masala", "Cottage cheese curry", 380.0, MenuCategory.MAIN_COURSE),
        MenuItem("v1", "Fresh Orange Juice", "250 ml", 120.0, MenuCategory.BEVERAGES),
        MenuItem("v2", "Masala Chai", "Traditional spiced tea", 80.0, MenuCategory.BEVERAGES),
        MenuItem("d1", "Gulab Jamun", "2 pcs with rabri", 150.0, MenuCategory.DESSERTS),
        MenuItem("d2", "Chocolate Brownie", "Warm with ice cream", 200.0, MenuCategory.DESSERTS),
    )

    companion object {
        private const val TAG = "FirestoreRepository"
    }
}
