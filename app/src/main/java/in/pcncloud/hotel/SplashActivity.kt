package `in`.pcncloud.hotel

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.FirestorePaths
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.ui.home.BrandAssets
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

/**
 * Dedicated cold-start splash (3–4s minimum):
 * - Compact local flavor logo ([BrandAssets] / `@drawable/lt_logo` on corporate)
 * - Welcome tagline + circular progress
 * - Waits for Hotels/{id} + Rooms/{room} snapshots (or timeout) before [MainActivity]
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var hotelConfig: HotelConfig
    private var splashLogo: ImageView? = null
    private lateinit var splashWelcome: TextView
    private lateinit var splashStatus: TextView
    private lateinit var splashProgress: ProgressBar

    private var hotelListener: ListenerRegistration? = null
    private var roomListener: ListenerRegistration? = null
    private var startedAtMs: Long = 0L
    private var hasNavigated = false
    private var brandingReady = false
    private var roomReady = false
    private var mainTransitionScheduled = false
    private var unpairedFlow = false

    /** True once overlay check passed (granted, N/A, or gracefully skipped). */
    private var overlayGatePassed = false

    /** True while Settings overlay screen is open via [startActivityForResult]. */
    private var awaitingOverlayResult = false

    private val proceedUnpaired = Runnable { openPairing() }
    private val proceedMain = Runnable { openMain() }
    private val forceProceedMain = Runnable {
        Log.w(
            TAG,
            "Splash timeout — brandingReady=$brandingReady roomReady=$roomReady → MainActivity",
        )
        splashStatus.text = getString(R.string.splash_status_ready)
        brandingReady = true
        roomReady = true
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
        splashProgress = findViewById(R.id.splash_progress)
        startedAtMs = SystemClock.elapsedRealtime()

        // Compact local mark only — never Coil / Firebase on splash.
        splashLogo?.setImageResource(BrandAssets.logoRes)
        splashLogo?.isVisible = true
        splashWelcome.text = getString(R.string.splash_welcome_loading)
        splashStatus.text = getString(R.string.splash_status_loading)
        splashProgress.isVisible = true

        ensureOverlayPermissionThenInit()
    }

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

    private fun handleOverlayDenied(canRetry: Boolean) {
        splashStatus.text = getString(R.string.splash_overlay_denied)
        Toast.makeText(this, R.string.splash_overlay_denied_toast, Toast.LENGTH_LONG).show()

        if (canRetry && !overlayGatePassed) {
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

    private fun beginAppInitialization() {
        if (hasNavigated) return

        if (!hotelConfig.isPaired()) {
            Log.i(
                TAG,
                "Unpaired (hotel=${hotelConfig.getHotelId()} room=${hotelConfig.getRoomNumberOrNull()}) " +
                    "→ PairingActivity",
            )
            unpairedFlow = true
            splashWelcome.text = getString(R.string.splash_welcome_generic)
            splashStatus.text = getString(R.string.splash_status_pairing)
            if (KioskPolicy.canActivityNavigate(lifecycle)) {
                resumePendingNavigation()
            }
            return
        }

        val hotelId = hotelConfig.getHotelId()!!
        val roomNumber = hotelConfig.getRoomNumberOrNull().orEmpty()
        Log.d(TAG, "Paired hotel_id=$hotelId room=$roomNumber — prefetching config")
        unpairedFlow = false
        splashWelcome.text = getString(R.string.splash_welcome_loading)
        splashStatus.text = getString(R.string.splash_status_loading)

        if (hotelListener == null) {
            bindHotelBranding(hotelId)
        }
        if (roomListener == null) {
            bindRoomConfig(hotelId, roomNumber)
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
        splashRoot().removeCallbacks(forceProceedMain)
        splashRoot().postDelayed(forceProceedMain, DATA_TIMEOUT_MS)
        tryScheduleMainWhenReady()
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
                    brandingReady = true
                    tryScheduleMainWhenReady()
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w(TAG, "Hotels/$hotelId missing — continuing with splash copy")
                    brandingReady = true
                    tryScheduleMainWhenReady()
                    return@addSnapshotListener
                }

                val data = snapshot.data ?: emptyMap()
                val hotelName = firstNonBlank(
                    data["name"] as? String,
                    data["hotel_name"] as? String,
                    data["hotelName"] as? String,
                )

                // Keep corporate welcome tagline stable; hotel may refine with name.
                if (hotelName.isNotBlank() && !BuildConfig.IS_CORPORATE) {
                    splashWelcome.text = getString(R.string.splash_welcome_to, hotelName)
                } else if (splashWelcome.text.isNullOrBlank()) {
                    splashWelcome.text = getString(R.string.splash_welcome_loading)
                }

                brandingReady = true
                Log.i(TAG, "Splash hotel config ready → name=$hotelName")
                tryScheduleMainWhenReady()
            }
    }

    private fun bindRoomConfig(hotelId: String, roomNumber: String) {
        if (roomNumber.isBlank()) {
            Log.w(TAG, "No room number — marking roomReady")
            roomReady = true
            tryScheduleMainWhenReady()
            return
        }

        roomListener?.remove()
        roomListener = FirebaseFirestore.getInstance()
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.ROOMS)
            .document(roomNumber)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (hasNavigated) return@addSnapshotListener

                if (error != null) {
                    Log.e(TAG, "Room listen failed for Rooms/$roomNumber", error)
                    roomReady = true
                    tryScheduleMainWhenReady()
                    return@addSnapshotListener
                }

                roomReady = true
                Log.i(
                    TAG,
                    "Splash room ready → exists=${snapshot?.exists()} room=$roomNumber",
                )
                tryScheduleMainWhenReady()
            }
    }

    private fun tryScheduleMainWhenReady() {
        if (!brandingReady || !roomReady) return
        splashStatus.text = getString(R.string.splash_status_ready)
        scheduleMain()
    }

    private fun scheduleMain(minRemainingMs: Long = MIN_DISPLAY_MS) {
        if (hasNavigated || mainTransitionScheduled || !overlayGatePassed) return
        if (!brandingReady || !roomReady) return
        if (!KioskPolicy.canActivityNavigate(lifecycle)) {
            Log.d(TAG, "scheduleMain skipped — lifecycle=${lifecycle.currentState}")
            return
        }
        mainTransitionScheduled = true
        val elapsed = SystemClock.elapsedRealtime() - startedAtMs
        val delay = (minRemainingMs - elapsed).coerceAtLeast(0L)
        Log.d(TAG, "scheduleMain in ${delay}ms (elapsed=${elapsed}ms min=$minRemainingMs)")
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
        releaseSplashSurfaces()
        startActivity(
            Intent(this, PairingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
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
        roomListener?.remove()
        roomListener = null
        releaseSplashSurfaces()
        // Hotel flavor: show premium XML home. Corporate keeps Compose MainActivity.
        val homeClass = if (BuildConfig.IS_CORPORATE) {
            MainActivity::class.java
        } else {
            `in`.pcncloud.hotel.ui.home.PremiumHomeActivity::class.java
        }
        startActivity(
            Intent(this, homeClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }

    private fun releaseSplashSurfaces() {
        try {
            splashLogo?.animate()?.cancel()
            splashLogo?.setImageDrawable(null)
            window.decorView.clearAnimation()
        } catch (e: Exception) {
            Log.w(TAG, "releaseSplashSurfaces failed", e)
        }
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
        roomListener?.remove()
        roomListener = null
        super.onDestroy()
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

    companion object {
        private const val TAG = "SplashActivity"
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val UNPAIRED_DELAY_MS = 3_000L
        private const val MIN_DISPLAY_MS = 3_500L
        private const val DATA_TIMEOUT_MS = 10_000L
        private const val OVERLAY_DENY_CONTINUE_MS = 2_500L
    }
}
