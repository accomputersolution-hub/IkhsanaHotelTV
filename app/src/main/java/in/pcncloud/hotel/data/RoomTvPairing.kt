package `in`.pcncloud.hotel.data

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

/**
 * Firestore room ↔ TV pairing + [Hotels/{hotelId}.activeTvScreens] sync.
 *
 * Room fields (flat `Hotels/{hotelId}/Rooms/{roomId}`):
 * - [isTvPaired], [pairedDeviceId], [pairedAt], [pairingCounted]
 *
 * [pairingCounted] prevents double increment/decrement when Admin RTDB unpair
 * and local Admin PIN logout both fire.
 */
object RoomTvPairing {
    private const val TAG = "RoomTvPairing"
    private const val TX_TIMEOUT_SEC = 25L

    class AlreadyPairedException :
        IllegalStateException("Already paired with another TV. Please unpair first.")

    /**
     * Transaction: abort if another device owns the room; otherwise mark paired
     * and increment [activeTvScreens] once.
     */
    fun pairRoomOrThrow(
        firestore: FirebaseFirestore,
        hotelId: String,
        roomNumber: String,
        deviceId: String,
    ) {
        val hotel = FirestorePaths.normalizeHotelId(hotelId)
        val room = RoomIds.normalize(roomNumber)
        require(hotel.isNotBlank()) { "Hotel id required" }
        require(room.isNotBlank()) { "Room id required" }
        require(deviceId.isNotBlank()) { "Device id required" }

        val hotelRef = firestore.collection(FirestorePaths.HOTELS).document(hotel)
        val roomRef = hotelRef.collection(FirestorePaths.ROOMS).document(room)

        val task = firestore.runTransaction { tx ->
            val roomSnap = tx.get(roomRef)
            val hotelSnap = tx.get(hotelRef)

            val existingDevice = roomSnap.getString("pairedDeviceId")?.trim().orEmpty()
            val isPaired = roomSnap.getBoolean("isTvPaired") == true ||
                roomSnap.getBoolean("is_tv_paired") == true ||
                existingDevice.isNotBlank()
            val counted = roomSnap.getBoolean("pairingCounted") == true ||
                roomSnap.getBoolean("pairing_counted") == true

            if (isPaired && existingDevice.isNotBlank() && existingDevice != deviceId) {
                throw AlreadyPairedException()
            }

            val now = System.currentTimeMillis()
            val roomPayload = hashMapOf<String, Any>(
                "roomNumber" to room,
                "isTvPaired" to true,
                "is_tv_paired" to true,
                "pairedDeviceId" to deviceId,
                "paired_device_id" to deviceId,
                "pairedAt" to now,
                "paired_at" to now,
                "pairingCounted" to true,
                "pairing_counted" to true,
            )
            tx.set(roomRef, roomPayload, SetOptions.merge())

            // Increment hotel counter only once per successful room pairing.
            if (!counted) {
                val current = readActiveScreens(hotelSnap.data)
                tx.set(
                    hotelRef,
                    mapOf("activeTvScreens" to current + 1L),
                    SetOptions.merge(),
                )
            }

            null
        }

        try {
            Tasks.await(task, TX_TIMEOUT_SEC, TimeUnit.SECONDS)
            Log.i(TAG, "pairRoom OK hotel=$hotel room=$room device=$deviceId")
        } catch (e: Exception) {
            val cause = e.cause ?: e
            if (cause is AlreadyPairedException) throw cause
            // Tasks.await wraps exceptions
            var cursor: Throwable? = e
            while (cursor != null) {
                if (cursor is AlreadyPairedException) throw cursor
                cursor = cursor.cause
            }
            throw e
        }
    }

    /**
     * Clear room pairing flags and decrement [activeTvScreens] (floor 0) if
     * this room previously contributed to the count.
     */
    fun unpairRoomBestEffort(
        firestore: FirebaseFirestore,
        hotelId: String?,
        roomNumber: String?,
    ) {
        val hotel = FirestorePaths.normalizeHotelId(hotelId)
        val room = RoomIds.normalize(roomNumber)
        if (hotel.isBlank() || room.isBlank()) return

        val hotelRef = firestore.collection(FirestorePaths.HOTELS).document(hotel)
        val roomRef = hotelRef.collection(FirestorePaths.ROOMS).document(room)

        val task = firestore.runTransaction { tx ->
            val roomSnap = tx.get(roomRef)
            val hotelSnap = tx.get(hotelRef)

            val counted = roomSnap.getBoolean("pairingCounted") == true ||
                roomSnap.getBoolean("pairing_counted") == true
            val isPaired = roomSnap.getBoolean("isTvPaired") == true ||
                roomSnap.getBoolean("is_tv_paired") == true ||
                !roomSnap.getString("pairedDeviceId").isNullOrBlank()

            if (!counted && !isPaired && !roomSnap.exists()) {
                return@runTransaction null
            }

            if (counted) {
                val current = readActiveScreens(hotelSnap.data)
                val next = (current - 1L).coerceAtLeast(0L)
                tx.set(
                    hotelRef,
                    mapOf("activeTvScreens" to next),
                    SetOptions.merge(),
                )
            }

            tx.set(
                roomRef,
                mapOf(
                    "isTvPaired" to false,
                    "is_tv_paired" to false,
                    "pairedDeviceId" to "",
                    "paired_device_id" to "",
                    "pairingCounted" to false,
                    "pairing_counted" to false,
                    "unpairedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
            null
        }

        try {
            Tasks.await(task, TX_TIMEOUT_SEC, TimeUnit.SECONDS)
            Log.i(TAG, "unpairRoom OK hotel=$hotel room=$room")
        } catch (e: Exception) {
            Log.w(TAG, "unpairRoom failed hotel=$hotel room=$room", e)
        }
    }

    private fun readActiveScreens(data: Map<String, Any>?): Long {
        if (data == null) return 0L
        val raw = data["activeTvScreens"] ?: data["active_tv_screens"] ?: return 0L
        return when (raw) {
            is Number -> raw.toLong().coerceAtLeast(0L)
            is String -> raw.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            else -> 0L
        }
    }
}
