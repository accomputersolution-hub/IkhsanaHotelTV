package `in`.pcncloud.hotel.data.repository

import android.util.Log
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.FirestorePaths
import `in`.pcncloud.hotel.data.model.GuestProfile
import `in`.pcncloud.hotel.data.model.EmergencyContact
import `in`.pcncloud.hotel.data.model.AgendaItem
import `in`.pcncloud.hotel.data.model.HotelAlert
import `in`.pcncloud.hotel.data.model.HotelBranding
import `in`.pcncloud.hotel.data.model.LiveOrder
import `in`.pcncloud.hotel.data.model.MenuCategory
import `in`.pcncloud.hotel.data.model.MenuItem
import `in`.pcncloud.hotel.data.model.OrderLineItem
import `in`.pcncloud.hotel.data.model.RoomStatus
import `in`.pcncloud.hotel.data.model.ServiceRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * All multi-tenant reads/writes go through [FirestorePaths.HOTELS] ("Hotels") + [hotelId].
 *
 * [hotelId] comes from SharedPreferences after pairing (`HotelConfig.getHotelId()`).
 * Paths: Hotels/{hotelId}/Requests|Alerts|Rooms|Menu|Emergency_Contacts|Daily_Agenda
 */
class FirestoreRepository(
    private val config: HotelConfig,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    /**
     * Paired tenant id for every Firestore read and write.
     * Must match the document id under `Hotels/{hotelId}`.
     */
    private val hotelId: String
        get() {
            val id = config.getHotelId()?.let { HotelConfig.normalizeHotelId(it) }.orEmpty()
            check(id.isNotBlank()) {
                "TV is not paired — HotelConfig.getHotelId() is null"
            }
            return id
        }

    private val roomNumber get() = FirestorePaths.normalizeRoom(config.roomNumber)

    @Volatile
    private var currentSessionKey: String = ""

    fun currentSessionKey(): String = currentSessionKey

    /** Hotels/{hotelId}/Rooms/{room} — this TV's guest profile. */
    fun observeGuestProfile(): Flow<GuestProfile> = callbackFlow {
        val docPath = FirestorePaths.roomDocument(hotelId, roomNumber)
        Log.d(TAG, "LISTEN Room doc → $docPath (HOTEL_ID=$hotelId)")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ROOMS)
            .document(roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Room doc listener at $docPath: ${error.message}", error)
                    trySend(defaultGuestProfile())
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.d(TAG, "EMPTY Room doc missing: $docPath — using defaults")
                    trySend(defaultGuestProfile())
                    return@addSnapshotListener
                }

                val data = snapshot.data
                if (data != null) {
                    val sessionKey = data["sessionKey"] as? String ?: ""
                    currentSessionKey = sessionKey
                    val profile = GuestProfile(
                        guestName = data["guestName"] as? String ?: "Guest",
                        salutation = firstNonBlank(
                            data["salutation"] as? String,
                            data["title"] as? String,
                            data["guestTitle"] as? String,
                            data["guest_title"] as? String,
                        ),
                        roomNumber = roomNumber,
                        hotelName = data["hotelName"] as? String ?: "",
                        hotelLogoUrl = data["hotelLogoUrl"] as? String ?: "",
                        hotelInfo = data["hotelInfo"] as? String ?: "",
                        checkInDate = data["checkInDate"] as? String ?: "",
                        checkOutDate = data["checkOutDate"] as? String ?: "",
                        sessionKey = sessionKey,
                        activeOrdersCount = (data["activeOrdersCount"] as? Number)?.toInt() ?: 0,
                        activeMessagesCount = (data["activeMessagesCount"] as? Number)?.toInt() ?: 0,
                    )
                    Log.d(
                        TAG,
                        "OK Room doc snapshot → path=$docPath guest=${profile.guestName} " +
                            "status=${data["status"]} session=${profile.sessionKey} " +
                            "keys=${data.keys} fromCache=${snapshot.metadata.isFromCache}",
                    )
                    trySend(profile)
                } else {
                    Log.e(TAG, "EMPTY Room doc data null at $docPath")
                    trySend(defaultGuestProfile())
                }
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN Room doc → $docPath")
            listener.remove()
        }
    }

    /**
     * Hotels/{hotelId}/Rooms/{room} — live occupancy status for *this* TV's room only.
     *
     * Occupancy matches admin PMS [deriveStatus]:
     * - status == "occupied" → occupied
     * - status in vacant/housekeeping/maintenance/needs_cleaning → NOT occupied
     * - guestName "Guest" is a vacant placeholder, NOT a real guest
     *
     * Fail-closed: missing/error → occupied=false so orders cannot be placed blindly.
     */
    fun observeThisRoomStatus(): Flow<RoomStatus> = callbackFlow {
        val docPath = FirestorePaths.roomDocument(hotelId, roomNumber)
        Log.d(TAG, "LISTEN ThisRoom status → $docPath")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ROOMS)
            .document(roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL ThisRoom status listener: ${error.message}", error)
                    trySend(RoomStatus(roomNumber = roomNumber, occupied = false, status = "unknown"))
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    Log.d(TAG, "EMPTY ThisRoom doc missing: $docPath — fail-closed vacant")
                    trySend(RoomStatus(roomNumber = roomNumber, occupied = false, status = "vacant"))
                    return@addSnapshotListener
                }
                val roomStatus = parseRoomStatus(roomNumber, snapshot.data ?: emptyMap())
                Log.d(
                    TAG,
                    "OK ThisRoom status → occupied=${roomStatus.occupied} " +
                        "status=${roomStatus.status} guest=${roomStatus.guestName}",
                )
                trySend(roomStatus)
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN ThisRoom status → $docPath")
            listener.remove()
        }
    }

    /**
     * One-shot occupancy check used as a hard gate before writing orders / service requests.
     * Throws if the room is not occupied so [runCatching] surfaces a failure to the ViewModel.
     */
    private suspend fun requireRoomOccupied() {
        val snap = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ROOMS)
            .document(roomNumber)
            .get()
            .await()
        if (!snap.exists()) {
            throw IllegalStateException("Room $roomNumber not found — cannot place order")
        }
        val room = parseRoomStatus(roomNumber, snap.data ?: emptyMap())
        if (!room.occupied) {
            Log.w(
                TAG,
                "BLOCKED write — room $roomNumber is not occupied " +
                    "(status=${room.status}, guest=${room.guestName})",
            )
            throw IllegalStateException("Room is not currently Checked-In")
        }
    }

    /**
     * Hotels/{hotelId}/Rooms — live room and guest status for the whole tenant.
     */
    fun observeRooms(): Flow<List<RoomStatus>> = callbackFlow {
        val path = FirestorePaths.roomsCollection(hotelId)
        Log.d(TAG, "LISTEN Rooms collection → $path (HOTEL_ID=$hotelId)")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ROOMS)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Rooms collection listener at $path: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    Log.e(TAG, "FAIL Rooms collection snapshot null at $path")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val rooms = snapshot.documents.map { doc ->
                    parseRoomStatus(doc.id, doc.data ?: emptyMap())
                }.sortedBy { it.roomNumber }

                Log.d(
                    TAG,
                    "OK Rooms collection snapshot → path=$path count=${rooms.size} " +
                        "fromCache=${snapshot.metadata.isFromCache} " +
                        "rooms=${rooms.joinToString { "${it.roomNumber}:${it.guestName.ifBlank { "-" }}:${it.status}" }}",
                )
                trySend(rooms)
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN Rooms collection → $path")
            listener.remove()
        }
    }

    /**
     * Hotels/{hotelId} — branding for logo / wallpaper / theme / copy.
     * Supports admin camelCase and snake_case field names.
     * hotelId comes from SharedPreferences via [HotelConfig].
     */
    fun observeHotelBranding(): Flow<HotelBranding> = callbackFlow {
        val docPath = FirestorePaths.hotelDocument(hotelId)
        Log.d(TAG, "LISTEN Hotel branding → $docPath (HOTEL_ID=$hotelId)")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Hotel branding listener at $docPath: ${error.message}", error)
                    trySend(HotelBranding(hotelId = hotelId))
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.e(TAG, "EMPTY Hotel doc missing: $docPath (check HOTEL_ID=$hotelId)")
                    trySend(HotelBranding(hotelId = hotelId))
                    return@addSnapshotListener
                }

                val result = parseHotelBranding(hotelId, snapshot.data ?: emptyMap())
                Log.d(
                    TAG,
                    "OK Hotel branding snapshot → path=$docPath name=${result.hotelName} " +
                        "tagline=${result.tagline} welcome=${result.welcomeMessage.take(40)} " +
                        "logo_url=${result.logoUrl} bg_wallpaper=${result.bgWallpaperUrl} " +
                        "theme=${result.themeColor} status=${result.status} " +
                        "keys=${snapshot.data?.keys} fromCache=${snapshot.metadata.isFromCache}",
                )
                trySend(result)
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN Hotel branding → $docPath")
            listener.remove()
        }
    }

    /**
     * Hotels/{hotelId}/Emergency_Contacts — live helpdesk list.
     * Falls back to Hotels/{hotelId}.emergency_contacts[] only until the first
     * subcollection snapshot arrives (including an empty list after last delete).
     */
    fun observeEmergencyContacts(): Flow<List<EmergencyContact>> = callbackFlow {
        val path = FirestorePaths.emergencyContactsCollection(hotelId)
        Log.d(TAG, "LISTEN Emergency Contacts → $path")

        var subReady = false
        var subItems: List<EmergencyContact> = emptyList()
        var legacyItems: List<EmergencyContact> = emptyList()

        fun emitMerged() {
            trySend(if (subReady) subItems else legacyItems)
        }

        val subReg = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.EMERGENCY_CONTACTS)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Emergency Contacts listener at $path: ${error.message}", error)
                    subItems = emptyList()
                    subReady = true
                    emitMerged()
                    return@addSnapshotListener
                }
                subItems = emptyList()
                if (snapshot != null && !snapshot.isEmpty) {
                    subItems = snapshot.documents.mapNotNull { doc ->
                        parseEmergencyContactMap(doc.id, doc.data)
                    }
                }
                subReady = true
                Log.d(TAG, "OK Emergency Contacts snapshot → ${subItems.size} at $path")
                emitMerged()
            }

        val hotelReg = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    legacyItems = emptyList()
                    emitMerged()
                    return@addSnapshotListener
                }
                legacyItems = parseEmergencyContacts(snapshot?.data?.get("emergency_contacts"))
                emitMerged()
            }

        awaitClose {
            Log.d(TAG, "UNLISTEN Emergency Contacts → $path")
            subReg.remove()
            hotelReg.remove()
        }
    }

    /**
     * Hotels/{hotelId}/Daily_Agenda — live corporate agenda.
     * Falls back to Hotels/{hotelId}.daily_agenda[] only until the first
     * subcollection snapshot arrives (including an empty list after last delete).
     */
    fun observeDailyAgenda(): Flow<List<AgendaItem>> = callbackFlow {
        val path = FirestorePaths.dailyAgendaCollection(hotelId)
        Log.d(TAG, "LISTEN Daily Agenda → $path")

        var subReady = false
        var subItems: List<AgendaItem> = emptyList()
        var legacyItems: List<AgendaItem> = emptyList()

        fun emitMerged() {
            trySend(if (subReady) subItems else legacyItems)
        }

        val subReg = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.DAILY_AGENDA)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Daily Agenda listener at $path: ${error.message}", error)
                    subItems = emptyList()
                    subReady = true
                    emitMerged()
                    return@addSnapshotListener
                }
                subItems = emptyList()
                if (snapshot != null && !snapshot.isEmpty) {
                    subItems = snapshot.documents
                        .mapNotNull { doc -> parseAgendaItemMap(doc.id, doc.data) }
                        .sortedWith(
                            compareBy<AgendaItem> { agendaTimeSortKey(it.time) }
                                .thenBy { it.time },
                        )
                }
                subReady = true
                Log.d(TAG, "OK Daily Agenda snapshot → ${subItems.size} at $path")
                emitMerged()
            }

        val hotelReg = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    legacyItems = emptyList()
                    emitMerged()
                    return@addSnapshotListener
                }
                legacyItems = parseDailyAgenda(snapshot?.data?.get("daily_agenda"))
                emitMerged()
            }

        awaitClose {
            Log.d(TAG, "UNLISTEN Daily Agenda → $path")
            subReg.remove()
            hotelReg.remove()
        }
    }

    /**
     * Attaches activity-scoped diagnostic listeners for branding + Rooms.
     * Returns registrations so [MainActivity] can remove them in onDestroy.
     */
    fun attachSyncDiagnostics(
        onBranding: (HotelBranding) -> Unit = {},
        onRooms: (List<RoomStatus>) -> Unit = {},
    ): List<ListenerRegistration> {
        val hotelPath = FirestorePaths.hotelDocument(hotelId)
        val roomsPath = FirestorePaths.roomsCollection(hotelId)
        Log.d(TAG, "ATTACH sync diagnostics → hotel=$hotelPath rooms=$roomsPath")

        val brandingReg = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "DIAG FAIL Hotels/$hotelId: ${error.message}", error)
                    return@addSnapshotListener
                }
                Log.d(
                    TAG,
                    "DIAG Hotels/$hotelId snapshot exists=${snapshot?.exists()} " +
                        "data=${snapshot?.data} fromCache=${snapshot?.metadata?.isFromCache}",
                )
                if (snapshot != null && snapshot.exists()) {
                    onBranding(parseHotelBranding(hotelId, snapshot.data ?: emptyMap()))
                }
            }

        val roomsReg = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ROOMS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "DIAG FAIL Hotels/$hotelId/Rooms: ${error.message}", error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                Log.d(
                    TAG,
                    "DIAG Hotels/$hotelId/Rooms snapshot count=${docs.size} " +
                        "ids=${docs.map { it.id }} fromCache=${snapshot?.metadata?.isFromCache}",
                )
                docs.forEach { doc ->
                    Log.d(TAG, "DIAG Room ${doc.id} → ${doc.data}")
                }
                onRooms(
                    docs.map { doc ->
                        parseRoomStatus(doc.id, doc.data ?: emptyMap())
                    },
                )
            }

        return listOf(brandingReg, roomsReg)
    }

    /** Hotels/{hotelId}/Menu */
    fun observeMenuItems(): Flow<List<MenuItem>> = callbackFlow {
        val path = FirestorePaths.menuCollection(hotelId)
        Log.d(TAG, "LISTEN Menu → $path")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.MENU)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Menu listener at $path", error)
                    trySend(defaultMenuItems())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val categoryKey = data["category"] as? String ?: "starters"
                    val name = data["name"] as? String ?: ""
                    val explicitVeg = data["isVeg"] as? Boolean
                        ?: data["is_veg"] as? Boolean
                        ?: data["veg"] as? Boolean
                    MenuItem(
                        id = doc.id,
                        name = name,
                        description = data["description"] as? String ?: "",
                        price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                        category = MenuCategory.fromKey(categoryKey),
                        imageUrl = firstNonBlank(
                            data["imageUrl"] as? String,
                            data["image_url"] as? String,
                            data["photoUrl"] as? String,
                        ),
                        available = data["available"] as? Boolean ?: true,
                        isVeg = explicitVeg ?: inferIsVeg(name),
                    )
                } ?: emptyList()

                Log.d(TAG, "OK Menu snapshot → ${items.size} items at $path (cache=${snapshot?.metadata?.isFromCache})")
                trySend(if (items.isEmpty()) defaultMenuItems() else items)
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN Menu → $path")
            listener.remove()
        }
    }

    /** Hotels/{hotelId}/Alerts?roomNumber= */
    fun observeAlerts(): Flow<List<HotelAlert>> = callbackFlow {
        val collectionPath = FirestorePaths.alertsCollection(hotelId)
        Log.d(TAG, "LISTEN Alerts → $collectionPath where roomNumber == '$roomNumber'")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ALERTS)
            .whereEqualTo("roomNumber", roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Alerts listener at $collectionPath", error)
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

                Log.d(TAG, "OK Alerts → ${alerts.size} for room $roomNumber (${alerts.count { !it.read }} unread)")
                trySend(alerts)
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN Alerts → $collectionPath")
            listener.remove()
        }
    }

    /** Top-level Live_Orders filtered by hotelId + roomNumber */
    fun observeRoomOrders(): Flow<List<LiveOrder>> = callbackFlow {
        val path = FirestorePaths.LIVE_ORDERS
        Log.d(TAG, "LISTEN Orders → $path where hotelId=$hotelId roomNumber=$roomNumber")

        val listener = firestore
            .collection(FirestorePaths.LIVE_ORDERS)
            .whereEqualTo("roomNumber", roomNumber)
            .whereEqualTo("hotelId", hotelId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Orders listener at $path", error)
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

                Log.d(TAG, "OK Orders → ${orders.size}")
                trySend(orders)
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN Orders → $path")
            listener.remove()
        }
    }

    suspend fun placeOrder(order: LiveOrder): Result<String> = runCatching {
        requireRoomOccupied()
        val docRef = firestore.collection(FirestorePaths.LIVE_ORDERS).document()
        val payload = hashMapOf(
            "hotelId" to HotelConfig.normalizeHotelId(order.hotelId.ifBlank { hotelId }),
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
            "payment_method" to order.paymentMethod.key,
            "archived" to false,
        )
        docRef.set(payload).await()
        Log.d(TAG, "WRITE Order → ${FirestorePaths.LIVE_ORDERS}/${docRef.id} hotelId=$hotelId path=Hotels/$hotelId")
        docRef.id
    }

    suspend fun markAlertRead(alertId: String) {
        val docPath = "Hotels/$hotelId/Alerts/$alertId"
        firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ALERTS)
            .document(alertId)
            .update("read", true)
            .await()
        Log.d(TAG, "WRITE Alert read → $docPath")
    }

    suspend fun submitServiceRequest(
        department: String,
        serviceType: String,
        serviceLabel: String,
        guestName: String,
        requestType: String = serviceType.uppercase(),
        items: List<String> = emptyList(),
        scheduledTime: String? = null,
        details: String? = null,
    ): Result<String> = runCatching {
        requireRoomOccupied()
        val docRef = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.REQUESTS)
            .document()
        val payload = hashMapOf<String, Any>(
            "hotelId" to hotelId,
            "roomNumber" to roomNumber,
            "room_number" to roomNumber,
            "guestName" to guestName,
            "department" to department,
            "serviceType" to serviceType,
            "serviceLabel" to serviceLabel,
            "request_type" to requestType.uppercase(),
            "items" to items,
            // Keep lowercase for admin panel filters; also mirror uppercase alias.
            "status" to "pending",
            "STATUS" to "PENDING",
            "source" to "tv",
            "timestamp" to System.currentTimeMillis(),
            "sessionKey" to currentSessionKey,
            "archived" to false,
        )
        val resolvedDetails = details?.trim()?.takeIf { it.isNotEmpty() }
            ?: items.joinToString(" · ").trim().takeIf { it.isNotEmpty() }
        if (resolvedDetails != null) {
            payload["details"] = resolvedDetails
            payload["notes"] = resolvedDetails
        }
        scheduledTime?.trim()?.takeIf { it.isNotEmpty() }?.let { time ->
            payload["scheduledTime"] = time
            payload["scheduledFor"] = time
        }
        docRef.set(payload).await()
        Log.d(
            TAG,
            "WRITE Request → Hotels/$hotelId/Requests/${docRef.id} " +
                "type=$requestType items=$items scheduledTime=$scheduledTime details=$resolvedDetails",
        )
        docRef.id
    }

    /** Hotels/{hotelId}/Requests?roomNumber= */
    fun observeRoomServiceRequests(): Flow<List<ServiceRequest>> = callbackFlow {
        val path = FirestorePaths.requestsCollection(hotelId)
        Log.d(TAG, "LISTEN Requests → $path where roomNumber == '$roomNumber'")

        val listener = firestore
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.REQUESTS)
            .whereEqualTo("roomNumber", roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAIL Requests listener at $path", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                try {
                    val requests = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        runCatching { parseServiceRequest(doc.id, data) }
                            .onFailure { Log.e(TAG, "FAIL parse Request ${doc.id}", it) }
                            .getOrNull()
                    }?.filter { request ->
                        !request.archived && belongsToCurrentSession(request.sessionKey)
                    }?.sortedByDescending { it.timestamp } ?: emptyList()

                    Log.d(TAG, "OK Requests → ${requests.size} for room $roomNumber")
                    trySend(requests)
                } catch (t: Throwable) {
                    Log.e(TAG, "FAIL Requests snapshot parse at $path", t)
                    trySend(emptyList())
                }
            }
        awaitClose {
            Log.d(TAG, "UNLISTEN Requests → $path")
            listener.remove()
        }
    }

    private fun belongsToCurrentSession(itemSessionKey: String): Boolean {
        val activeSession = currentSessionKey
        if (activeSession.isBlank()) return itemSessionKey.isBlank()
        return itemSessionKey.isBlank() || itemSessionKey == activeSession
    }

    private fun parseServiceRequest(docId: String, data: Map<String, Any>): ServiceRequest {
        val statusRaw = firstNonBlank(
            asTrimmedString(data["status"]),
            asTrimmedString(data["STATUS"]),
        )
        val timestamp = parseEpochMillis(data["timestamp"]).takeIf { it > 0L }
            ?: parseEpochMillis(data["createdAt"])

        return ServiceRequest(
            id = docId,
            roomNumber = firstNonBlank(
                asTrimmedString(data["roomNumber"]),
                asTrimmedString(data["room_number"]),
            ),
            guestName = asTrimmedString(data["guestName"]).orEmpty(),
            department = firstNonBlank(asTrimmedString(data["department"])).lowercase(),
            serviceType = firstNonBlank(
                asTrimmedString(data["serviceType"]),
                asTrimmedString(data["request_type"]),
            ),
            serviceLabel = firstNonBlank(
                asTrimmedString(data["serviceLabel"]),
                asTrimmedString(data["serviceType"]),
                asTrimmedString(data["request_type"]),
            ),
            status = normalizeRequestStatus(statusRaw),
            timestamp = timestamp,
            sessionKey = asTrimmedString(data["sessionKey"]).orEmpty(),
            archived = data["archived"] as? Boolean ?: false,
        )
    }

    private fun parseEpochMillis(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is com.google.firebase.Timestamp -> value.toDate().time
        else -> 0L
    }

    private fun normalizeRequestStatus(raw: String): String =
        when (raw.trim().lowercase().replace(' ', '_')) {
            "in_progress", "progress", "accepted", "assigned" -> "in_progress"
            "completed", "complete", "done" -> "completed"
            "cancelled", "canceled" -> "cancelled"
            else -> "pending"
        }

    private fun firstNonBlank(vararg values: String?): String =
        values.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()

    /** Coerce Firestore field values (String / unexpected types) to a trimmed string. */
    private fun asTrimmedString(value: Any?): String? = when (value) {
        null -> null
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Number, is Boolean -> value.toString()
        else -> value.toString().trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Parse a Rooms/{id} document into [RoomStatus].
     * Mirrors admin-panel `deriveStatus` in guests.js / analytics.js:
     * - Explicit status wins when it is a known PMS status.
     * - Placeholder guestName "Guest" does NOT mean occupied.
     * - Only a real guest name (or status=occupied / occupied=true / guest_checked_in=true)
     *   marks the room as occupied for TV order/service writes.
     */
    private fun parseRoomStatus(roomId: String, data: Map<String, Any?>): RoomStatus {
        val guestName = (data["guestName"] as? String)?.trim().orEmpty()
        val rawStatus = firstNonBlank(
            data["status"] as? String,
            data["roomStatus"] as? String,
            data["room_status"] as? String,
        ).lowercase()

        val knownNonOccupied = setOf(
            "vacant",
            "housekeeping",
            "maintenance",
            "needs_cleaning",
            "checked_out",
            "checkout",
            "dirty",
        )

        val guestCheckedIn = data["guest_checked_in"] as? Boolean
            ?: data["guestCheckedIn"] as? Boolean
            ?: data["checkedIn"] as? Boolean

        val occupiedFlag = data["occupied"] as? Boolean
        val hasRealGuest = guestName.isNotBlank() &&
            !guestName.equals("Guest", ignoreCase = true)

        val occupied = when {
            guestCheckedIn == false -> false
            occupiedFlag == false -> false
            rawStatus in knownNonOccupied -> false
            rawStatus == "occupied" -> true
            guestCheckedIn == true -> true
            occupiedFlag == true -> true
            hasRealGuest -> true
            else -> false
        }

        val status = when {
            rawStatus.isNotBlank() -> rawStatus
            occupied -> "occupied"
            else -> "vacant"
        }

        return RoomStatus(
            roomNumber = roomId,
            guestName = guestName,
            status = status,
            sessionKey = data["sessionKey"] as? String ?: "",
            occupied = occupied,
            checkInDate = data["checkInDate"] as? String ?: "",
            checkOutDate = data["checkOutDate"] as? String ?: "",
        )
    }

    /** Map Hotels/{hotelId} document fields → [HotelBranding]. */
    private fun parseHotelBranding(hotelId: String, data: Map<String, Any?>): HotelBranding {
        @Suppress("UNCHECKED_CAST")
        val branding = (data["branding"] as? Map<String, Any?>) ?: emptyMap()

        return HotelBranding(
            hotelId = hotelId,
            hotelName = firstNonBlank(
                data["name"] as? String,
                data["hotel_name"] as? String,
                data["hotelName"] as? String,
                branding["hotel_name"] as? String,
                branding["hotelName"] as? String,
                branding["name"] as? String,
            ),
            logoUrl = firstNonBlank(
                asTrimmedString(branding["logo_url"]),
                asTrimmedString(branding["logoUrl"]),
                asTrimmedString(branding["logo"]),
                asTrimmedString(data["logo_url"]),
                asTrimmedString(data["logoUrl"]),
                asTrimmedString(data["logo"]),
                asTrimmedString(data["brand_logo"]),
                asTrimmedString(data["brandLogoUrl"]),
            ),
            bgWallpaperUrl = firstNonBlank(
                asTrimmedString(branding["bg_wallpaper"]),
                asTrimmedString(branding["bgWallpaper"]),
                asTrimmedString(branding["bgWallpaperUrl"]),
                asTrimmedString(branding["wallpaper"]),
                asTrimmedString(data["bg_wallpaper"]),
                asTrimmedString(data["bgWallpaper"]),
                asTrimmedString(data["bgWallpaperUrl"]),
                asTrimmedString(data["wallpaper"]),
                asTrimmedString(data["wallpaperUrl"]),
            ),
            themeColor = firstNonBlank(
                asTrimmedString(branding["theme_color"]),
                asTrimmedString(branding["themeColor"]),
                asTrimmedString(data["theme_color"]),
                asTrimmedString(data["themeColor"]),
            ),
            tagline = firstNonBlank(
                asTrimmedString(data["tagline"]),
                asTrimmedString(branding["tagline"]),
                asTrimmedString(data["brand_tagline"]),
                asTrimmedString(branding["brand_tagline"]),
                asTrimmedString(branding["brandTagline"]),
                asTrimmedString(data["brandTagline"]),
            ),
            welcomeMessage = firstNonBlank(
                asTrimmedString(data["welcome_message"]),
                asTrimmedString(data["welcomeMessage"]),
                asTrimmedString(branding["welcome_message"]),
                asTrimmedString(branding["welcomeMessage"]),
                asTrimmedString(data["welcome_msg"]),
                asTrimmedString(data["welcomeText"]),
                asTrimmedString(branding["welcomeText"]),
                asTrimmedString(data["hotelInfo"]),
                asTrimmedString(branding["hotelInfo"]),
            ),
            announcement = firstNonBlank(
                asTrimmedString(data["announcement"]),
                asTrimmedString(data["announcement_text"]),
                asTrimmedString(data["ticker_text"]),
                asTrimmedString(data["tickerText"]),
                asTrimmedString(branding["announcement"]),
                asTrimmedString(branding["announcement_text"]),
                asTrimmedString(branding["ticker_text"]),
                asTrimmedString(branding["tickerText"]),
            ),
            status = data["status"] as? String ?: "active",
            emergencyContacts = parseEmergencyContacts(data["emergency_contacts"]),
            dailyAgenda = parseDailyAgenda(data["daily_agenda"]),
        )
    }

    private fun parseEmergencyContacts(raw: Any?): List<EmergencyContact> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { index, item ->
            val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
            parseEmergencyContactMap(
                firstNonBlank(map["id"] as? String, "contact_$index"),
                map,
            )
        }
    }

    private fun parseEmergencyContactMap(id: String, data: Map<*, *>?): EmergencyContact? {
        if (data == null) return null
        val title = firstNonBlank(
            data["title"] as? String,
            data["name"] as? String,
        )
        val extension = firstNonBlank(
            data["extension"] as? String,
            data["ext"] as? String,
            data["subtitle"] as? String,
        )
        if (title.isBlank() && extension.isBlank()) return null
        return EmergencyContact(id = id, title = title, extension = extension)
    }

    private fun parseDailyAgenda(raw: Any?): List<AgendaItem> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { index, item ->
            val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
            parseAgendaItemMap(
                firstNonBlank(map["id"] as? String, "agenda_$index"),
                map,
            )
        }.sortedWith(
            compareBy<AgendaItem> { agendaTimeSortKey(it.time) }
                .thenBy { it.time },
        )
    }

    private fun parseAgendaItemMap(id: String, data: Map<*, *>?): AgendaItem? {
        if (data == null) return null
        val time = firstNonBlank(data["time"] as? String)
        val title = firstNonBlank(
            data["title"] as? String,
            data["name"] as? String,
        )
        val location = firstNonBlank(
            data["location"] as? String,
            data["place"] as? String,
            data["venue"] as? String,
        )
        if (time.isBlank() && title.isBlank() && location.isBlank()) return null
        return AgendaItem(id = id, time = time, title = title, location = location)
    }

    /** Parse start of a time range for chronological sort (e.g. "09:00 AM - 10:30 AM"). */
    private fun agendaTimeSortKey(time: String): Int {
        val match = AGENDA_TIME_REGEX.find(time.trim()) ?: return Int.MAX_VALUE
        var hours = match.groupValues[1].toIntOrNull() ?: return Int.MAX_VALUE
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val meridian = match.groupValues[3].uppercase()
        if (meridian == "PM" && hours < 12) hours += 12
        if (meridian == "AM" && hours == 12) hours = 0
        return hours * 60 + minutes
    }

    private fun defaultGuestProfile() = GuestProfile(
        guestName = "Guest",
        roomNumber = roomNumber,
        hotelName = "",
        hotelInfo = "",
        welcomeMessage = "",
    )

    private fun defaultMenuItems(): List<MenuItem> = listOf(
        MenuItem(
            "s1", "Soup of the Day", "Seasonal broth finished with garden herbs",
            220.0, MenuCategory.STARTERS, isVeg = true,
        ),
        MenuItem(
            "s2", "Veg Spring Rolls", "Crispy rolls with sweet chilli dipping sauce",
            180.0, MenuCategory.STARTERS, isVeg = true,
        ),
        MenuItem(
            "m1", "Butter Chicken", "Tender chicken cooked in rich tomato spices",
            450.0, MenuCategory.MAIN_COURSE, isVeg = false,
        ),
        MenuItem(
            "m2", "Paneer Tikka Masala", "Charred cottage cheese in silky gravy",
            380.0, MenuCategory.MAIN_COURSE, isVeg = true,
        ),
        MenuItem(
            "v1", "Fresh Orange Juice", "Cold-pressed, served chilled (250 ml)",
            120.0, MenuCategory.BEVERAGES, isVeg = true,
        ),
        MenuItem(
            "v2", "Masala Chai", "Traditional spiced tea with aromatic spices",
            80.0, MenuCategory.BEVERAGES, isVeg = true,
        ),
        MenuItem(
            "d1", "Gulab Jamun", "Warm milk dumplings with rabri (2 pcs)",
            150.0, MenuCategory.DESSERTS, isVeg = true,
        ),
        MenuItem(
            "d2", "Chocolate Brownie", "Warm fudge brownie with vanilla ice cream",
            200.0, MenuCategory.DESSERTS, isVeg = true,
        ),
    )

    /** Best-effort veg flag when Firestore omits isVeg / is_veg. */
    private fun inferIsVeg(name: String): Boolean {
        val n = name.lowercase()
        val nonVegHints = listOf(
            "chicken", "mutton", "lamb", "beef", "fish", "prawn", "shrimp",
            "egg", "bacon", "meat", "kebab", "keema", "biryani",
        )
        return nonVegHints.none { n.contains(it) }
    }

    companion object {
        private const val TAG = "FirestoreRepository"
        private val AGENDA_TIME_REGEX =
            Regex("""(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)?""")
    }
}
