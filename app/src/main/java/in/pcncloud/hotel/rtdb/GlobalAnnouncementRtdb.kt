package `in`.pcncloud.hotel.rtdb

import android.util.Log
import `in`.pcncloud.hotel.config.HotelConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Realtime Database node shared by Admin and TV kiosk:
 * `hotels/{hotelId}/config/global_announcement`
 *
 * Same tree as kiosk Lock Task flags (`hotels/{id}/config/…`) so signed-in
 * admin panel writes are allowed by production RTDB rules.
 * Legacy `hotel_settings/…` path returns permission_denied for admins.
 */
object GlobalAnnouncementRtdb {

    private const val TAG = "GlobalAnnouncementRtdb"
    private const val RTDB_URL =
        "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app"

    fun path(hotelId: String): String =
        "hotels/${HotelConfig.normalizeHotelId(hotelId)}/config/global_announcement"

    private fun database() =
        FirebaseDatabase.getInstance(FirebaseApp.getInstance(), RTDB_URL)

    /**
     * Admin write — same as Java `ref.setValue(text)` from an EditText + Button.
     */
    fun publish(hotelId: String, message: String, onComplete: (Exception?) -> Unit = {}) {
        val id = HotelConfig.normalizeHotelId(hotelId)
        if (id.isBlank()) {
            onComplete(IllegalArgumentException("Hotel ID is blank"))
            return
        }
        try {
            database().getReference(path(id)).setValue(message.trim())
                .addOnSuccessListener { onComplete(null) }
                .addOnFailureListener { err ->
                    Log.e(TAG, "publish failed → ${path(id)}", err)
                    onComplete(err)
                }
        } catch (err: Exception) {
            Log.e(TAG, "publish threw → ${path(id)}", err)
            onComplete(err)
        }
    }

    /**
     * TV kiosk listen — `addValueEventListener` on the same node.
     * Emits `""` when the snapshot is missing or null so the ticker can hide.
     */
    fun observe(hotelId: String?): Flow<String> = callbackFlow {
        val id = HotelConfig.normalizeHotelId(hotelId)
        if (id.isBlank()) {
            trySend("")
            awaitClose { }
            return@callbackFlow
        }

        val ref = database().getReference(path(id))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || snapshot.value == null) {
                    Log.d(TAG, "RTDB ${path(id)} empty/null")
                    trySend("")
                    return
                }
                val text = when (val value = snapshot.value) {
                    is String -> value.trim()
                    else -> value.toString().trim()
                }
                Log.d(TAG, "RTDB ${path(id)} → \"${text.take(80)}\"")
                trySend(text)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "RTDB ${path(id)} cancelled: ${error.message}", error.toException())
                trySend("")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
