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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.FirestorePaths
import `in`.pcncloud.hotel.kiosk.HotelSessionManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * First-run / unpaired TV setup: staff enters Hotel ID (slug), we verify
 * `Hotels/{id}` exists via Firestore get listeners, save prefs, then open [SplashActivity].
 *
 * Uses ScrollView + `adjustPan` so the soft keyboard pans the window and focused
 * fields stay visible for D-pad / soft-keyboard entry on Android TV.
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var scrollView: ScrollView
    private lateinit var etHotelId: EditText
    private lateinit var etRoomNumber: EditText
    private lateinit var btnPair: Button
    private lateinit var tvPairError: TextView

    private var pairingInFlight = false
    private var baseBottomPadding = 0
    private var imePaddingPx = 0
    private var pendingScrollPass: Runnable? = null

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
        // Ensure stale prefs cannot skip this screen.
        HotelSessionManager.resetLogoutGuard()

        setContentView(R.layout.activity_pairing)

        scrollView = findViewById(R.id.pairing_scroll)
        etHotelId = findViewById(R.id.etHotelId)
        etRoomNumber = findViewById(R.id.etRoomNumber)
        btnPair = findViewById(R.id.btnPair)
        tvPairError = findViewById(R.id.tvPairError)

        applyFlavorCopy()

        baseBottomPadding = scrollView.paddingBottom
        imePaddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            IME_EXTRA_BOTTOM_DP,
            resources.displayMetrics,
        ).toInt()

        // Do not invent a default room — empty field forces staff to enter one.
        etRoomNumber.setText(hotelConfig.getRoomNumberOrNull().orEmpty())

        // Hotel ID → Next moves focus to Room Number on TV remote / soft keyboard.
        etHotelId.imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        etHotelId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etRoomNumber.requestFocus()
                applyImeFocusPadding(true)
                ensureFocusedFieldVisible(etRoomNumber)
                true
            } else {
                false
            }
        }

        etRoomNumber.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        etRoomNumber.inputType = EditorInfo.TYPE_CLASS_NUMBER
        etRoomNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                beginPairing()
                true
            } else {
                false
            }
        }

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            applyImeFocusPadding(hasFocus && (v === etHotelId || v === etRoomNumber))
            if (hasFocus) {
                ensureFocusedFieldVisible(v)
            }
        }
        etHotelId.onFocusChangeListener = focusListener
        etRoomNumber.onFocusChangeListener = focusListener

        btnPair.setOnClickListener { beginPairing() }
        etHotelId.requestFocus()
    }

    /**
     * Corporate flavor swaps pairing copy; hotel flavor keeps layout XML / string resources.
     */
    private fun applyFlavorCopy() {
        if (!BuildConfig.IS_CORPORATE) return

        findViewById<TextView>(R.id.pairing_title).text = "Setup Training Display"
        findViewById<TextView>(R.id.pairing_subtitle).text =
            "Enter the Client ID from Super Admin (e.g. lnt_001). We verify Clients/{id} in Firestore before saving."
        findViewById<TextView>(R.id.pairing_hotel_id_label).text = "Client ID / Slug"
        etHotelId.hint = "Client ID / Slug"
        findViewById<TextView>(R.id.pairing_room_label).text = "Conference Room"
        etRoomNumber.hint = "Conference Room"
    }

    override fun onDestroy() {
        pendingScrollPass?.let { pass ->
            if (::scrollView.isInitialized) {
                scrollView.removeCallbacks(pass)
            }
        }
        pendingScrollPass = null
        super.onDestroy()
    }

    /**
     * Extra bottom padding while an EditText is focused so the field (and Pair button)
     * can scroll above the soft keyboard under adjustPan.
     */
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
            // Expand downward so Room Number + Pair button clear the IME together.
            if (view === etHotelId || view === etRoomNumber) {
                rect.bottom += btnPair.height + (imePaddingPx / 3)
            }
            // Walks ViewParents (ScrollView) so the focused rect stays on-screen under adjustPan.
            view.requestRectangleOnScreen(rect, false)
        }
        pendingScrollPass = scrollPass
        // Immediate pass + delayed pass after the IME finishes animating in.
        scrollView.post(scrollPass)
        scrollView.postDelayed(scrollPass, IME_SETTLE_MS)
    }

    /** Immediately disable button → "Pairing..." then run Firestore check. */
    private fun beginPairing() {
        if (pairingInFlight || !btnPair.isEnabled) return

        btnPair.isEnabled = false
        btnPair.text = getString(R.string.pairing_verifying)
        tvPairError.visibility = View.GONE
        tvPairError.text = ""

        val hotelId = HotelConfig.normalizeHotelId(etHotelId.text?.toString()?.trim().orEmpty())
        val roomNumber = etRoomNumber.text?.toString()?.trim().orEmpty()

        if (hotelId.isBlank()) {
            resetPairButton("Enter a Hotel ID / slug")
            return
        }
        if (roomNumber.isBlank()) {
            resetPairButton("Enter a Room number")
            return
        }

        pairingInFlight = true
        Log.d(TAG, "Pairing lookup → Hotels/$hotelId room=$roomNumber")

        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .get()
            .addOnSuccessListener { snapshot ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    if (snapshot.exists()) {
                        val status = snapshot.getString("status") ?: "active"
                        if (status.equals("inactive", ignoreCase = true)) {
                            Log.w(TAG, "Hotels/$hotelId is inactive — rejecting pair")
                            resetPairButton(getString(R.string.pairing_hotel_inactive))
                            return@runOnUiThread
                        }
                        Log.i(TAG, "Hotels/$hotelId exists (status=$status) — saving prefs and launching SplashActivity")
                        try {
                            hotelConfig.setHotelId(hotelId)
                            hotelConfig.setRoomNumber(roomNumber)
                            HotelSessionManager.markSessionPaired(
                                applicationContext,
                                hotelId,
                                roomNumber,
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save HotelConfig", e)
                            resetPairButton(e.message ?: "Could not save hotel settings")
                            return@runOnUiThread
                        }
                        openSplashActivity()
                    } else {
                        Log.w(TAG, "Hotels/$hotelId does not exist")
                        resetPairButton("Hotel ID does not exist in database")
                    }
                }
            }
            .addOnFailureListener { error ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Log.e(TAG, "Firestore pair lookup failed for Hotels/$hotelId", error)
                    resetPairButton(
                        error.message ?: "Could not verify hotel. Check network and try again.",
                    )
                }
            }
    }

    private fun resetPairButton(message: String) {
        pairingInFlight = false
        btnPair.isEnabled = true
        btnPair.text = getString(R.string.pairing_submit)
        tvPairError.text = message
        tvPairError.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        /** Extra ScrollView bottom inset while typing so fields clear the IME. */
        private const val IME_EXTRA_BOTTOM_DP = 220f
        private const val IME_SETTLE_MS = 180L
    }
}
