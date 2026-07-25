package com.example.ikhsanahoteltv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ikhsanahoteltv.config.HotelConfig
import com.example.ikhsanahoteltv.data.FirestorePaths
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * First-run / unpaired TV setup: staff enters Hotel ID (slug), we verify
 * `Hotels/{id}` exists via Firestore get listeners, save prefs, then open [MainActivity].
 *
 * Uses ScrollView + adjustResize so the soft keyboard does not cover Room / Pair controls.
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var scrollView: ScrollView
    private lateinit var etHotelId: EditText
    private lateinit var etRoomNumber: EditText
    private lateinit var btnPair: Button
    private lateinit var tvPairError: TextView

    private var pairingInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        hotelConfig = HotelConfig(applicationContext)
        if (hotelConfig.isPaired()) {
            Log.d(TAG, "Already paired → ${hotelConfig.getHotelId()}, opening MainActivity")
            openMainActivity()
            return
        }

        setContentView(R.layout.activity_pairing)

        scrollView = findViewById(R.id.pairing_scroll)
        etHotelId = findViewById(R.id.etHotelId)
        etRoomNumber = findViewById(R.id.etRoomNumber)
        btnPair = findViewById(R.id.btnPair)
        tvPairError = findViewById(R.id.tvPairError)

        etRoomNumber.setText(hotelConfig.roomNumber)

        // Hotel ID → Next moves focus to Room Number on TV remote / soft keyboard.
        etHotelId.imeOptions = EditorInfo.IME_ACTION_NEXT
        etHotelId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etRoomNumber.requestFocus()
                scrollFocusedIntoView(etRoomNumber)
                true
            } else {
                false
            }
        }

        etRoomNumber.imeOptions = EditorInfo.IME_ACTION_DONE
        etRoomNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                beginPairing()
                true
            } else {
                false
            }
        }

        etHotelId.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) scrollFocusedIntoView(v)
        }
        etRoomNumber.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) scrollFocusedIntoView(v)
        }

        btnPair.setOnClickListener { beginPairing() }
        etHotelId.requestFocus()
    }

    private fun scrollFocusedIntoView(view: View) {
        scrollView.post {
            scrollView.smoothScrollTo(0, (view.parent as? View)?.top ?: view.top)
        }
    }

    /** Immediately disable button → "Pairing..." then run Firestore check. */
    private fun beginPairing() {
        if (pairingInFlight || !btnPair.isEnabled) return

        btnPair.isEnabled = false
        btnPair.text = getString(R.string.pairing_verifying)
        tvPairError.visibility = View.GONE
        tvPairError.text = ""

        val hotelId = HotelConfig.normalizeHotelId(etHotelId.text?.toString()?.trim().orEmpty())
        val roomNumber = etRoomNumber.text?.toString()?.trim().orEmpty().ifBlank { "101" }

        if (hotelId.isBlank()) {
            resetPairButton("Enter a Hotel ID / slug")
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
                        Log.i(TAG, "Hotels/$hotelId exists (status=$status) — saving prefs and launching MainActivity")
                        try {
                            hotelConfig.setHotelId(hotelId)
                            hotelConfig.setRoomNumber(roomNumber)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save HotelConfig", e)
                            resetPairButton(e.message ?: "Could not save hotel settings")
                            return@runOnUiThread
                        }
                        openMainActivity()
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

    private fun openMainActivity() {
        pairingInFlight = false
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finish()
    }

    companion object {
        private const val TAG = "PairingActivity"
    }
}
