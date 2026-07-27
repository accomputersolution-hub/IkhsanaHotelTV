package com.example.ikhsanahoteltv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import coil.load
import com.example.ikhsanahoteltv.config.HotelConfig
import com.example.ikhsanahoteltv.data.FirestorePaths
import com.example.ikhsanahoteltv.kiosk.KioskPolicy
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

/**
 * Branded cold-start splash: loads Hotels/{paired_hotel_id} and shows
 * hotel logo + "Welcome to {name}" before opening [MainActivity].
 *
 * Unpaired devices briefly show a generic loader, then [PairingActivity].
 *
 * On first launch path, requests [Settings.ACTION_MANAGE_OVERLAY_PERMISSION]
 * (`SYSTEM_ALERT_WINDOW`) before pairing / dashboard initialization.
 *
 * Delayed navigation is cancelled in [onStop] so Home/minimize cannot be
 * overridden by a pending `startActivity` a few seconds later.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var splashLogo: ImageView
    private lateinit var splashWelcome: TextView
    private lateinit var splashStatus: TextView

    private var hotelListener: ListenerRegistration? = null
    private var startedAtMs: Long = 0L
    private var hasNavigated = false
    private var brandingApplied = false
    private var mainTransitionScheduled = false
    private var unpairedFlow = false

    /** True once overlay check passed (granted, N/A, or gracefully skipped). */
    private var overlayGatePassed = false

    /** True while Settings overlay screen is open via [startActivityForResult]. */
    private var awaitingOverlayResult = false

    private val proceedUnpaired = Runnable { openPairing() }
    private val proceedMain = Runnable { openMain() }
    private val forceProceedMain = Runnable {
        if (!brandingApplied) {
            Log.w(TAG, "Branding timeout — continuing to MainActivity")
            splashStatus.text = getString(R.string.splash_status_ready)
        }
        scheduleMain(minRemainingMs = 0L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        hotelConfig = HotelConfig(applicationContext)
        splashLogo = findViewById(R.id.splash_logo)
        splashWelcome = findViewById(R.id.splash_welcome)
        splashStatus = findViewById(R.id.splash_status)
        startedAtMs = SystemClock.elapsedRealtime()

        splashWelcome.text = getString(R.string.splash_welcome_generic)
        splashLogo.setImageResource(R.drawable.ic_logo)

        ensureOverlayPermissionThenInit()
    }

    /**
     * Gate: [Settings.canDrawOverlays] → if missing, open
     * [Settings.ACTION_MANAGE_OVERLAY_PERMISSION] for this package and wait for
     * [onActivityResult] before pairing / dashboard init.
     */
    private fun ensureOverlayPermissionThenInit() {
        if (overlayGatePassed) {
            beginAppInitialization()
            return
        }

        if (hasOverlayPermission()) {
            Log.i(TAG, "SYSTEM_ALERT_WINDOW already granted")
            overlayGatePassed = true
            beginAppInitialization()
            return
        }

        Log.i(TAG, "SYSTEM_ALERT_WINDOW missing — opening manage-overlay settings")
        splashStatus.text = getString(R.string.splash_overlay_request)
        requestOverlayPermission()
    }

    private fun hasOverlayPermission(): Boolean {
        // Pre-Marshmallow: overlay permission model does not apply.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            overlayGatePassed = true
            beginAppInitialization()
            return
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        return try {
            awaitingOverlayResult = true
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open ACTION_MANAGE_OVERLAY_PERMISSION", e)
            awaitingOverlayResult = false
            handleOverlayDenied(canRetry = false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_OVERLAY_PERMISSION) return

        awaitingOverlayResult = false
        // OEMs often return RESULT_CANCELED even when the toggle was enabled — always re-check.
        Log.d(TAG, "Overlay settings returned resultCode=$resultCode")

        if (hasOverlayPermission()) {
            Log.i(TAG, "SYSTEM_ALERT_WINDOW granted after settings")
            Toast.makeText(this, R.string.splash_overlay_granted, Toast.LENGTH_SHORT).show()
            overlayGatePassed = true
            beginAppInitialization()
        } else {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW still denied after settings")
            handleOverlayDenied(canRetry = true)
        }
    }

    /**
     * Graceful denial: inform the user, optionally re-open settings once more on tap
     * of status, then continue to pairing/dashboard so the TV is never stuck on splash.
     */
    private fun handleOverlayDenied(canRetry: Boolean) {
        splashStatus.text = getString(R.string.splash_overlay_denied)
        Toast.makeText(this, R.string.splash_overlay_denied_toast, Toast.LENGTH_LONG).show()

        if (canRetry && !overlayGatePassed) {
            // Brief pause so the user can read the message, then continue init anyway.
            splashRoot().postDelayed({
                if (isFinishing || isDestroyed || overlayGatePassed) return@postDelayed
                if (hasOverlayPermission()) {
                    overlayGatePassed = true
                    beginAppInitialization()
                } else {
                    Log.w(TAG, "Continuing without SYSTEM_ALERT_WINDOW (graceful deny)")
                    overlayGatePassed = true
                    beginAppInitialization()
                }
            }, OVERLAY_DENY_CONTINUE_MS)
        } else {
            overlayGatePassed = true
            beginAppInitialization()
        }
    }

    /** Pairing vs dashboard bootstrap — only after the overlay gate. */
    private fun beginAppInitialization() {
        if (hasNavigated) return

        val hotelId = hotelConfig.getHotelId()
        if (hotelId.isNullOrBlank()) {
            Log.i(TAG, "No paired hotel_id — generic splash → PairingActivity")
            unpairedFlow = true
            splashWelcome.text = getString(R.string.splash_welcome_generic)
            splashStatus.text = getString(R.string.splash_status_pairing)
            splashLogo.setImageResource(R.drawable.ic_logo)
            if (KioskPolicy.canActivityNavigate(lifecycle)) {
                resumePendingNavigation()
            }
            return
        }

        Log.d(TAG, "Paired hotel_id=$hotelId — listening Hotels/$hotelId")
        unpairedFlow = false
        splashWelcome.text = getString(R.string.splash_welcome_loading)
        splashStatus.text = getString(R.string.splash_status_loading)
        if (hotelListener == null) {
            bindHotelBranding(hotelId)
        }
        if (KioskPolicy.canActivityNavigate(lifecycle)) {
            resumePendingNavigation()
        }
    }

    override fun onStart() {
        super.onStart()
        KioskPolicy.clearUserMinimized(this)
        if (hasNavigated || awaitingOverlayResult || !overlayGatePassed) return
        resumePendingNavigation()
    }

    override fun onStop() {
        // Cancel delayed startActivity — this is the main "reopens after a few seconds" bug.
        clearCallbacks()
        mainTransitionScheduled = false
        if (!isChangingConfigurations && !isFinishing && !awaitingOverlayResult) {
            KioskPolicy.markUserMinimized(this)
            Log.i(TAG, "onStop — cancelled pending navigation (user backgrounded)")
        }
        super.onStop()
    }

    private fun resumePendingNavigation() {
        if (hasNavigated || !overlayGatePassed || awaitingOverlayResult) return
        if (unpairedFlow) {
            splashRoot().removeCallbacks(proceedUnpaired)
            splashRoot().postDelayed(proceedUnpaired, UNPAIRED_DELAY_MS)
            return
        }
        // Paired: re-arm timeout + continue if branding already ready.
        splashRoot().removeCallbacks(forceProceedMain)
        splashRoot().postDelayed(forceProceedMain, BRANDING_TIMEOUT_MS)
        if (brandingApplied) {
            scheduleMain()
        }
    }

    private fun splashRoot() = findViewById<android.view.View>(R.id.splash_root)

    private fun bindHotelBranding(hotelId: String) {
        hotelListener?.remove()
        hotelListener = FirebaseFirestore.getInstance()
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (hasNavigated) return@addSnapshotListener

                if (error != null) {
                    Log.e(TAG, "Hotel branding listen failed for Hotels/$hotelId", error)
                    splashStatus.text = getString(R.string.splash_status_ready)
                    scheduleMain()
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w(TAG, "Hotels/$hotelId missing — continuing with defaults")
                    splashWelcome.text = getString(R.string.splash_welcome_generic)
                    splashStatus.text = getString(R.string.splash_status_ready)
                    scheduleMain()
                    return@addSnapshotListener
                }

                val data = snapshot.data ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val branding = (data["branding"] as? Map<String, Any?>) ?: emptyMap()

                val hotelName = firstNonBlank(
                    data["name"] as? String,
                    data["hotel_name"] as? String,
                    data["hotelName"] as? String,
                ).ifBlank { getString(R.string.brand_name) }

                val logoUrl = firstNonBlank(
                    branding["logo_url"] as? String,
                    branding["logoUrl"] as? String,
                    data["logo_url"] as? String,
                    data["logoUrl"] as? String,
                )

                brandingApplied = true
                splashWelcome.text = getString(R.string.splash_welcome_to, hotelName)
                splashStatus.text = getString(R.string.splash_status_ready)
                loadLogo(logoUrl)

                Log.i(TAG, "Splash branding ready → name=$hotelName logo=${logoUrl.take(64)}")
                scheduleMain()
            }
    }

    private fun loadLogo(logoUrl: String) {
        if (logoUrl.isBlank()) {
            splashLogo.setImageResource(R.drawable.ic_logo)
            splashLogo.isVisible = true
            return
        }
        splashLogo.load(logoUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_logo)
            error(R.drawable.ic_logo)
            listener(
                onError = { _, result ->
                    Log.w(TAG, "Logo load failed: ${result.throwable.message}")
                },
            )
        }
    }

    private fun scheduleMain(minRemainingMs: Long = MIN_DISPLAY_MS) {
        if (hasNavigated || mainTransitionScheduled || !overlayGatePassed) return
        // Do not arm navigation while stopped — onStart will reschedule.
        if (!KioskPolicy.canActivityNavigate(lifecycle)) {
            Log.d(TAG, "scheduleMain skipped — lifecycle=${lifecycle.currentState}")
            return
        }
        mainTransitionScheduled = true
        val elapsed = SystemClock.elapsedRealtime() - startedAtMs
        val delay = (minRemainingMs - elapsed).coerceAtLeast(0L)
        splashRoot().removeCallbacks(proceedMain)
        splashRoot().removeCallbacks(forceProceedMain)
        splashRoot().postDelayed(proceedMain, delay)
    }

    private fun openPairing() {
        if (hasNavigated) return
        if (!KioskPolicy.canActivityNavigate(lifecycle)) {
            Log.w(TAG, "Skipping openPairing — lifecycle=${lifecycle.currentState}")
            mainTransitionScheduled = false
            return
        }
        hasNavigated = true
        clearCallbacks()
        startActivity(
            Intent(this, PairingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        finish()
    }

    private fun openMain() {
        if (hasNavigated) return
        if (!KioskPolicy.canActivityNavigate(lifecycle)) {
            Log.w(TAG, "Skipping openMain — lifecycle=${lifecycle.currentState}")
            mainTransitionScheduled = false
            return
        }
        hasNavigated = true
        clearCallbacks()
        hotelListener?.remove()
        hotelListener = null
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun clearCallbacks() {
        splashRoot().removeCallbacks(proceedUnpaired)
        splashRoot().removeCallbacks(proceedMain)
        splashRoot().removeCallbacks(forceProceedMain)
    }

    override fun onDestroy() {
        clearCallbacks()
        hotelListener?.remove()
        hotelListener = null
        super.onDestroy()
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

    companion object {
        private const val TAG = "SplashActivity"
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val UNPAIRED_DELAY_MS = 3_000L
        private const val MIN_DISPLAY_MS = 2_000L
        private const val BRANDING_TIMEOUT_MS = 8_000L
        private const val OVERLAY_DENY_CONTINUE_MS = 2_500L
    }
}
