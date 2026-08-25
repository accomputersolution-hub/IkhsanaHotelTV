package `in`.pcncloud.hotel

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.FirestorePaths
import `in`.pcncloud.hotel.data.RoomIds
import `in`.pcncloud.hotel.kiosk.HotelSessionManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlin.random.Random

/**
 * First-run / unpaired TV setup (secure pairing):
 * 1. Staff enters **public slug** → resolve `public_hotels/{slug}`
 * 2. TV creates `Hotels/{hotelId}/pairing_codes/{6digit}` and shows the code
 * 3. Reception claims the code in Admin (Pair Device Code) and assigns a room
 * 4. TV listens, saves hotelId + roomNumber, opens [SplashActivity]
 *
 * Room number is never typed on the TV (avoids IDOR / guest misuse).
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var scrollView: ScrollView
    private lateinit var slugSection: LinearLayout
    private lateinit var codeSection: LinearLayout
    private lateinit var etHotelId: EditText
    private lateinit var btnPair: Button
    private lateinit var btnNewCode: Button
    private lateinit var tvPairingCode: TextView
    private lateinit var tvPairingHotelName: TextView
    private lateinit var tvPairingWaiting: TextView
    private lateinit var tvPairError: TextView

    private var pairingInFlight = false
    private var baseBottomPadding = 0
    private var imePaddingPx = 0
    private var pendingScrollPass: Runnable? = null
    private var codeListener: ListenerRegistration? = null

    private var resolvedHotelId: String = ""
    private var resolvedPublicSlug: String = ""
    private var activeCode: String = ""
    private var deviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        hotelConfig = HotelConfig(applicationContext)
        if (hotelConfig.isPaired()) {
            Log.d(
                TAG,
                "Already paired → ${hotelConfig.getHotelId()} / ${hotelConfig.getRoomNumberOrNull()}, " +
                    "opening SplashActivity",
            )
            openSplashActivity()
            return
        }
        HotelSessionManager.resetLogoutGuard()

        setContentView(R.layout.activity_pairing)

        scrollView = findViewById(R.id.pairing_scroll)
        slugSection = findViewById(R.id.pairing_slug_section)
        codeSection = findViewById(R.id.pairing_code_section)
        etHotelId = findViewById(R.id.etHotelId)
        btnPair = findViewById(R.id.btnPair)
        btnNewCode = findViewById(R.id.btnNewCode)
        tvPairingCode = findViewById(R.id.tvPairingCode)
        tvPairingHotelName = findViewById(R.id.tvPairingHotelName)
        tvPairingWaiting = findViewById(R.id.tvPairingWaiting)
        tvPairError = findViewById(R.id.tvPairError)

        deviceId = hotelConfig.getOrCreateDeviceId()

        applyFlavorCopy()

        baseBottomPadding = scrollView.paddingBottom
        imePaddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            IME_EXTRA_BOTTOM_DP,
            resources.displayMetrics,
        ).toInt()

        etHotelId.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        etHotelId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                beginPairingLookup()
                true
            } else {
                false
            }
        }
        etHotelId.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            applyImeFocusPadding(hasFocus)
            if (hasFocus) ensureFocusedFieldVisible(v)
        }

        btnPair.setOnClickListener { beginPairingLookup() }
        btnNewCode.setOnClickListener { regenerateCode() }
        etHotelId.requestFocus()
    }

    private fun applyFlavorCopy() {
        if (!BuildConfig.IS_CORPORATE) return
        findViewById<TextView>(R.id.pairing_title).text = "Setup Training Display"
        findViewById<TextView>(R.id.pairing_subtitle).text =
            "Enter the public client slug. This display shows a 6-digit code — staff claims it in Admin and assigns the conference room."
        findViewById<TextView>(R.id.pairing_hotel_id_label).text = "Public client slug"
        etHotelId.hint = "e.g. lnt"
    }

    override fun onDestroy() {
        codeListener?.remove()
        codeListener = null
        pendingScrollPass?.let { pass ->
            if (::scrollView.isInitialized) {
                scrollView.removeCallbacks(pass)
            }
        }
        pendingScrollPass = null
        super.onDestroy()
    }

    private fun applyImeFocusPadding(focused: Boolean) {
        val bottom = if (focused) baseBottomPadding + imePaddingPx else baseBottomPadding
        if (scrollView.paddingBottom == bottom) return
        scrollView.setPadding(
            scrollView.paddingLeft,
            scrollView.paddingTop,
            scrollView.paddingRight,
            bottom,
        )
    }

    private fun ensureFocusedFieldVisible(view: View) {
        pendingScrollPass?.let { scrollView.removeCallbacks(it) }
        val scrollPass = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            if (currentFocus !== view) return@Runnable
            val rect = Rect()
            view.getDrawingRect(rect)
            rect.bottom += btnPair.height + (imePaddingPx / 3)
            view.requestRectangleOnScreen(rect, false)
        }
        pendingScrollPass = scrollPass
        scrollView.post(scrollPass)
        scrollView.postDelayed(scrollPass, IME_SETTLE_MS)
    }

    /** Step 1: resolve public_hotels/{slug} → internal hotelId, then mint a code. */
    private fun beginPairingLookup() {
        if (pairingInFlight || !btnPair.isEnabled) return

        val publicSlug = normalizePublicSlug(etHotelId.text?.toString())
        if (publicSlug.isBlank()) {
            showError(getString(R.string.pairing_slug_missing))
            return
        }

        pairingInFlight = true
        btnPair.isEnabled = false
        btnPair.text = getString(R.string.pairing_verifying)
        clearError()

        Log.d(TAG, "Public slug lookup → public_hotels/$publicSlug")

        FirebaseFirestore.getInstance()
            .collection(PUBLIC_HOTELS)
            .document(publicSlug)
            .get()
            .addOnSuccessListener { snapshot ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    if (!snapshot.exists()) {
                        // Backward-compat: allow typing internal Hotels/{id} if public doc missing
                        tryInternalHotelFallback(publicSlug)
                        return@runOnUiThread
                    }

                    val data = snapshot.data ?: emptyMap()
                    val hotelId = FirestorePaths.normalizeHotelId(
                        (data["hotelId"] as? String) ?: (data["hotel_id"] as? String) ?: "",
                    )
                    val status = (data["status"] as? String) ?: "active"
                    val name = (data["name"] as? String).orEmpty()

                    if (hotelId.isBlank()) {
                        resetSlugButton(getString(R.string.pairing_slug_not_found))
                        return@runOnUiThread
                    }
                    if (status.equals("inactive", ignoreCase = true)) {
                        resetSlugButton(getString(R.string.pairing_hotel_inactive))
                        return@runOnUiThread
                    }

                    resolvedHotelId = hotelId
                    resolvedPublicSlug = publicSlug
                    tvPairingHotelName.text = name.ifBlank { publicSlug }
                    createAndShowPairingCode()
                }
            }
            .addOnFailureListener { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Log.e(TAG, "public_hotels lookup failed", error)
                    resetSlugButton(
                        error.message ?: "Could not verify hotel. Check network and try again.",
                    )
                }
            }
    }

    /** Fallback for properties not yet migrated to public_hotels. */
    private fun tryInternalHotelFallback(maybeHotelId: String) {
        val hotelId = FirestorePaths.normalizeHotelId(maybeHotelId)
        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .get()
            .addOnSuccessListener { snapshot ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (!snapshot.exists()) {
                        resetSlugButton(getString(R.string.pairing_slug_not_found))
                        return@runOnUiThread
                    }
                    val status = snapshot.getString("status") ?: "active"
                    if (status.equals("inactive", ignoreCase = true)) {
                        resetSlugButton(getString(R.string.pairing_hotel_inactive))
                        return@runOnUiThread
                    }
                    resolvedHotelId = hotelId
                    resolvedPublicSlug = snapshot.getString("public_slug")
                        ?.takeIf { it.isNotBlank() }
                        ?: hotelId
                    tvPairingHotelName.text = snapshot.getString("name") ?: hotelId
                    createAndShowPairingCode()
                }
            }
            .addOnFailureListener { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    resetSlugButton(error.message ?: getString(R.string.pairing_slug_not_found))
                }
            }
    }

    private fun regenerateCode() {
        if (resolvedHotelId.isBlank()) {
            showSlugStep()
            return
        }
        createAndShowPairingCode()
    }

    private fun createAndShowPairingCode() {
        codeListener?.remove()
        codeListener = null

        val code = randomSixDigitCode()
        activeCode = code
        val expiresAt = System.currentTimeMillis() + CODE_TTL_MS

        val payload = hashMapOf<String, Any?>(
            "code" to code,
            "hotelId" to resolvedHotelId,
            "publicSlug" to resolvedPublicSlug,
            "deviceId" to deviceId,
            "status" to "pending",
            "roomNumber" to null,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "expiresAt" to expiresAt,
            "claimedAt" to null,
            "claimedBy" to null,
        )

        Log.i(TAG, "Creating pairing code $code → Hotels/$resolvedHotelId/pairing_codes/$code")

        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.HOTELS)
            .document(resolvedHotelId)
            .collection(PAIRING_CODES)
            .document(code)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showCodeStep(code)
                    attachCodeListener(code, expiresAt)
                    pairingInFlight = false
                }
            }
            .addOnFailureListener { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Log.e(TAG, "Failed to write pairing code", error)
                    resetSlugButton(error.message ?: "Could not create pairing code")
                }
            }
    }

    private fun attachCodeListener(code: String, expiresAt: Long) {
        codeListener?.remove()
        val ref = FirebaseFirestore.getInstance()
            .collection(FirestorePaths.HOTELS)
            .document(resolvedHotelId)
            .collection(PAIRING_CODES)
            .document(code)

        codeListener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Pairing code listener failed", error)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showError(error.message ?: "Pairing listener failed")
                    }
                }
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showError(getString(R.string.pairing_code_expired))
                    }
                }
                return@addSnapshotListener
            }

            val data = snapshot.data ?: return@addSnapshotListener
            val status = data["status"] as? String ?: "pending"
            val roomNumber = RoomIds.coerceFromFirestore(
                data["roomNumber"] ?: data["room_number"],
            )
            val boundDevice = data["deviceId"] as? String
            val docExpires = (data["expiresAt"] as? Number)?.toLong() ?: expiresAt

            if (!boundDevice.isNullOrBlank() && boundDevice != deviceId) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showError("This code is bound to another device")
                    }
                }
                return@addSnapshotListener
            }

            if (status != "claimed" && docExpires < System.currentTimeMillis()) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showError(getString(R.string.pairing_code_expired))
                    }
                }
                return@addSnapshotListener
            }

            if (status == "claimed" && roomNumber.isNotBlank()) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    completePairing(roomNumber)
                }
            }
        }
    }

    private fun completePairing(roomNumber: String) {
        codeListener?.remove()
        codeListener = null
        try {
            hotelConfig.setHotelId(resolvedHotelId)
            hotelConfig.setRoomNumber(roomNumber)
            HotelSessionManager.markSessionPaired(
                applicationContext,
                resolvedHotelId,
                roomNumber,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save HotelConfig", e)
            showError(e.message ?: "Could not save hotel settings")
            showSlugStep()
            return
        }
        Toast.makeText(
            this,
            getString(R.string.pairing_claimed, RoomIds.formatDisplay(roomNumber)),
            Toast.LENGTH_LONG,
        ).show()
        Log.i(TAG, "Paired hotel=$resolvedHotelId room=$roomNumber via code=$activeCode")
        openSplashActivity()
    }

    private fun showCodeStep(code: String) {
        slugSection.visibility = View.GONE
        codeSection.visibility = View.VISIBLE
        tvPairingCode.text = code
        tvPairingWaiting.text = getString(R.string.pairing_waiting)
        clearError()
        btnNewCode.requestFocus()
    }

    private fun showSlugStep() {
        codeListener?.remove()
        codeListener = null
        codeSection.visibility = View.GONE
        slugSection.visibility = View.VISIBLE
        resetSlugButton(null)
        etHotelId.requestFocus()
    }

    private fun resetSlugButton(message: String?) {
        pairingInFlight = false
        btnPair.isEnabled = true
        btnPair.text = getString(R.string.pairing_generate_code)
        if (message.isNullOrBlank()) {
            clearError()
        } else {
            showError(message)
        }
    }

    private fun showError(message: String) {
        tvPairError.text = message
        tvPairError.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearError() {
        tvPairError.text = ""
        tvPairError.visibility = View.GONE
    }

    private fun openSplashActivity() {
        pairingInFlight = false
        startActivity(
            Intent(this, SplashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finish()
    }

    companion object {
        private const val TAG = "PairingActivity"
        private const val PUBLIC_HOTELS = "public_hotels"
        private const val PAIRING_CODES = "pairing_codes"
        private const val IME_EXTRA_BOTTOM_DP = 220f
        private const val IME_SETTLE_MS = 180L
        private const val CODE_TTL_MS = 15 * 60 * 1000L

        fun normalizePublicSlug(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.trim()
                .lowercase()
                .replace('-', '_')
                .replace(Regex("[^a-z0-9_]"), "")
                .trim('_')
                .take(63)
        }

        fun randomSixDigitCode(): String =
            Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
    }
}
