package `in`.pcncloud.hotel.kiosk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import `in`.pcncloud.hotel.PairingActivity
import `in`.pcncloud.hotel.admin.AdminSession
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.RoomTvPairing
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Full TV pairing session reset — local Admin unpair and remote Admin logout.
 *
 * Clears [HotelConfig] + [KioskPolicy] tenant cache, stops Lock Task / Watchdog,
 * and opens [PairingActivity] with a wiped back stack.
 */
object HotelSessionManager {

    private const val TAG = "HotelSessionManager"
    private const val RTDB_URL =
        "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app"

    @Volatile
    private var logoutInFlight = false

    /** RTDB path for this TV's pairing session. */
    fun roomSessionPath(hotelId: String, roomNumber: String): String =
        "hotels/${HotelConfig.normalizeHotelId(hotelId)}/rooms/${roomNumber.trim()}"

    /**
     * Mark this TV paired in RTDB so Admin can later set
     * `session_active=false` / `status=UNPAIRED` for remote logout.
     */
    fun markSessionPaired(
        context: Context,
        hotelId: String,
        roomNumber: String,
        deviceId: String? = null,
    ) {
        val path = roomSessionPath(hotelId, roomNumber)
        try {
            val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance(), RTDB_URL)
            val payload = mutableMapOf<String, Any>(
                "session_active" to true,
                "status" to "PAIRED",
                "pairedAt" to System.currentTimeMillis(),
                "packageName" to context.packageName,
            )
            deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["deviceId"] = it }
            db.getReference(path).updateChildren(payload)
            Log.i(TAG, "RTDB session PAIRED → $path")
        } catch (e: Exception) {
            Log.w(TAG, "markSessionPaired failed for $path", e)
        }
    }

    /**
     * Best-effort cloud mirror when logging out locally (Admin PIN unpair).
     * Clears Firestore pairing flags + decrements [activeTvScreens] (floor 0).
     */
    fun markSessionUnpaired(hotelId: String?, roomNumber: String?) {
        val hotel = HotelConfig.normalizeHotelId(hotelId)
        val room = roomNumber?.trim().orEmpty()
        if (hotel.isBlank() || room.isBlank()) return
        val path = roomSessionPath(hotel, room)
        try {
            val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance(), RTDB_URL)
            db.getReference(path).updateChildren(
                mapOf(
                    "session_active" to false,
                    "status" to "UNPAIRED",
                    "unpairedAt" to System.currentTimeMillis(),
                ),
            )
            Log.i(TAG, "RTDB session UNPAIRED → $path")
        } catch (e: Exception) {
            Log.w(TAG, "markSessionUnpaired RTDB failed for $path", e)
        }

        try {
            RoomTvPairing.unpairRoomBestEffort(
                firestore = FirebaseFirestore.getInstance(),
                hotelId = hotel,
                roomNumber = room,
            )
        } catch (e: Exception) {
            Log.w(TAG, "markSessionUnpaired Firestore failed for $path", e)
        }
    }

    /**
     * True when an RTDB room snapshot means Admin forced this TV to pairing.
     */
    fun isRemoteLogoutSignal(status: Any?, sessionActive: Any?): Boolean {
        val statusText = status?.toString()?.trim().orEmpty()
        if (statusText.equals("UNPAIRED", ignoreCase = true)) return true

        return when (sessionActive) {
            is Boolean -> !sessionActive
            is Number -> sessionActive.toInt() == 0
            is String -> sessionActive.equals("false", ignoreCase = true) ||
                sessionActive.equals("0", ignoreCase = true)
            else -> false
        }
    }

    /**
     * Wipe local tenant prefs + kiosk cache and open [PairingActivity].
     * Safe to call from MainActivity / Admin UI; guarded against re-entry.
     *
     * When [reason] is a remote Admin unpair, Firestore counter was already
     * decremented by the Admin Panel — skip Firestore unpair to avoid -2.
     */
    fun performLogout(activity: Activity, reason: String) {
        if (logoutInFlight || activity.isFinishing) {
            Log.d(TAG, "performLogout skipped (inFlight/finishing) reason=$reason")
            return
        }
        logoutInFlight = true
        Log.i(TAG, "performLogout → $reason")

        val config = HotelConfig(activity.applicationContext)
        val hotelId = config.getHotelId()
        val roomNumber = config.getRoomNumberOrNull()
        val remoteUnpair = reason.contains("remote", ignoreCase = true) ||
            reason.contains("rtdb", ignoreCase = true) ||
            reason.contains("admin_panel", ignoreCase = true)

        Thread {
            try {
                if (remoteUnpair) {
                    // Admin already cleared Firestore + counter; only mirror RTDB if needed.
                    markSessionUnpairedRtdbOnly(hotelId, roomNumber)
                } else {
                    markSessionUnpaired(hotelId, roomNumber)
                }
            } catch (_: Exception) {
                // Best-effort cloud mirror.
            }

            activity.runOnUiThread {
                finishLocalLogout(activity)
            }
        }.start()
    }

    private fun markSessionUnpairedRtdbOnly(hotelId: String?, roomNumber: String?) {
        val hotel = HotelConfig.normalizeHotelId(hotelId)
        val room = roomNumber?.trim().orEmpty()
        if (hotel.isBlank() || room.isBlank()) return
        val path = roomSessionPath(hotel, room)
        try {
            val db = FirebaseDatabase.getInstance(FirebaseApp.getInstance(), RTDB_URL)
            db.getReference(path).updateChildren(
                mapOf(
                    "session_active" to false,
                    "status" to "UNPAIRED",
                    "unpairedAt" to System.currentTimeMillis(),
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "RTDB-only unpair failed", e)
        }
    }

    private fun finishLocalLogout(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "stopLockTask during logout", e)
        }

        AdminSession.clear()
        HotelConfig(activity.applicationContext).clearPairingSession()
        openPairingScreen(activity)
    }

    fun openPairingScreen(context: Context) {
        val intent = Intent(context, PairingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
        }
        context.startActivity(intent)
        if (context is Activity && !context.isFinishing) {
            context.finish()
        }
        logoutInFlight = false
    }

    fun resetLogoutGuard() {
        logoutInFlight = false
    }
}
