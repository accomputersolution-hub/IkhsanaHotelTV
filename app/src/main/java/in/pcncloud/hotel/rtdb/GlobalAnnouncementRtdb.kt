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
 * TV ticker from Realtime Database.
 *
 * Prefers `hotels/{hotelId}/config/global_announcement`, and also listens to
 * legacy `hotel_settings/{hotelId}/global_announcement` so either Admin write
 * path still updates the Home ticker.
 *
 * Firestore `Hotels/{id}.announcement` remains a fallback via [HomeUiState.tickerMessage].
 */
object GlobalAnnouncementRtdb {

    private const val TAG = "GlobalAnnouncementRtdb"
    private const val RTDB_URL =
        "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app"

    fun path(hotelId: String): String =
        "hotels/${HotelConfig.normalizeHotelId(hotelId)}/config/global_announcement"

    fun legacyPath(hotelId: String): String =
        "hotel_settings/${HotelConfig.normalizeHotelId(hotelId)}/global_announcement"

    private fun database() =
        FirebaseDatabase.getInstance(FirebaseApp.getInstance(), RTDB_URL)

    fun publish(hotelId: String, message: String, onComplete: (Exception?) -> Unit = {}) {
        val id = HotelConfig.normalizeHotelId(hotelId)
        if (id.isBlank()) {
            onComplete(IllegalArgumentException("Hotel ID is blank"))
            return
        }
        val text = message.trim()
        try {
            val primary = database().getReference(path(id))
            val legacy = database().getReference(legacyPath(id))
            primary.setValue(text)
                .continueWithTask { legacy.setValue(text) }
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
     * Emits the latest non-blank ticker from primary or legacy RTDB node.
     */
    fun observe(hotelId: String?): Flow<String> = callbackFlow {
        val id = HotelConfig.normalizeHotelId(hotelId)
        if (id.isBlank()) {
            trySend("")
            awaitClose { }
            return@callbackFlow
        }

        var primaryText = ""
        var legacyText = ""

        fun emitBest() {
            val best = primaryText.ifBlank { legacyText }
            Log.d(TAG, "ticker emit hotel=$id len=${best.length}")
            trySend(best)
        }

        fun parseValue(snapshot: DataSnapshot): String {
            if (!snapshot.exists() || snapshot.value == null) return ""
            return when (val value = snapshot.value) {
                is String -> value.trim()
                else -> value.toString().trim()
            }
        }

        val primaryRef = database().getReference(path(id))
        val legacyRef = database().getReference(legacyPath(id))

        val primaryListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                primaryText = parseValue(snapshot)
                Log.d(TAG, "RTDB ${path(id)} → \"${primaryText.take(80)}\"")
                emitBest()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "RTDB ${path(id)} cancelled: ${error.message}", error.toException())
                // Keep last primaryText — do not blank the ticker on transient cancel.
                emitBest()
            }
        }

        val legacyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                legacyText = parseValue(snapshot)
                Log.d(TAG, "RTDB ${legacyPath(id)} → \"${legacyText.take(80)}\"")
                emitBest()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "RTDB ${legacyPath(id)} cancelled: ${error.message}")
                // Keep last legacyText — permission denied must not wipe a good primary ticker.
                emitBest()
            }
        }

        primaryRef.addValueEventListener(primaryListener)
        legacyRef.addValueEventListener(legacyListener)

        awaitClose {
            primaryRef.removeEventListener(primaryListener)
            legacyRef.removeEventListener(legacyListener)
        }
    }
}
