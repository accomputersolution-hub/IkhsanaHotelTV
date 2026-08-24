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
import `in`.pcncloud.hotel.config.IntroVideoCache
import `in`.pcncloud.hotel.data.FirestorePaths
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.ui.home.BrandAssets
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

/**
 * Dedicated cold-start splash:
 * - Default local flavor logo first, then hotel Firestore logo when available
 * - Welcome tagline + circular progress
 * - Prefetches introVideoUrl into [IntroVideoCache] so MainActivity cold boot is cache-first
 * - Waits for Hotels/{id} + Rooms/{room} + intro cache (or timeout) before [MainActivity]
 *
 * Android 9 / API &lt; 30 uses a longer data timeout so Firestore + intro prefetch
 * can finish before MainActivity reads the cache.
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
    private var introCacheReady = false
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
            "Splash timeout — brandingReady=$brandingReady roomReady=$roomReady " +
                "introCacheReady=$introCacheReady → MainActivity",
        )
        splashStatus.text = getString(R.string.splash_status_ready)
        brandingReady = true
        roomReady = true
        introCacheReady = true
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

        // Hotel build: keep splash logo hidden until branding arrives, so the
        // default flower does not flash before the remote logo replaces it.
        splashLogo?.isVisible = BuildConfig.IS_CORPORATE
        if (BuildConfig.IS_CORPORATE) {
            splashLogo?.setImageResource(BrandAssets.logoRes)
        } else {
            splashLogo?.setImageDrawable(null)
        }
        splashWelcome.isVisible = BuildConfig.IS_CORPORATE
        splashWelcome.text =
            if (BuildConfig.IS_CORPORATE) {
                getString(R.string.splash_welcome_loading)
            } else {
                ""
            }
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
            splashWelcome.isVisible = true
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
        splashWelcome.isVisible = BuildConfig.IS_CORPORATE
        splashWelcome.text =
            if (BuildConfig.IS_CORPORATE) {
                getString(R.string.splash_welcome_loading)
            } else {
                ""
            }
        splashStatus.text = getString(R.string.splash_status_loading)

        if (hotelListener == null) {
            bindHotelBranding(hotelId)
        }
        if (roomListener == null) {
            bindRoomConfig(hotelId, roomNumber)
        }
        if (!introCacheReady) {
            prefetchIntroVideoCache(hotelId)
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
        splashRoot().postDelayed(forceProceedMain, dataTimeoutMs())
        tryScheduleMainWhenReady()
    }

    private fun splashRoot() = findViewById<android.view.View>(R.id.splash_root)

    /**
     * Seeds [IntroVideoCache] URL from Firestore and kicks a background MP4 download
     * into [IntroVideoFileStore]. If a local file already exists, Splash does not wait
     * on the network (offline cold boot).
     */
    private fun prefetchIntroVideoCache(hotelId: String) {
        val cache = IntroVideoCache(applicationContext)
        // Offline / already-downloaded: unblock Splash immediately.
        if (cache.canStartIntro()) {
            Log.i(
                TAG,
                "Intro ready locally url=${!cache.getValidHttpUrl().isNullOrBlank()} " +
                    "file=${cache.fileStore().hasReadyFile()} " +
                    "len=${cache.fileStore().localFileLength()}",
            )
            introCacheReady = true
            tryScheduleMainWhenReady()
        }

        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.HOTELS)
            .document(hotelId)
            .collection(FirestorePaths.CONFIG)
            .document("intro")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val snap = task.result
                    val raw = if (snap != null && snap.exists()) {
                        firstNonBlank(
                            asTrimmedString(snap.getString("introVideoUrl")),
                            asTrimmedString(snap.getString("intro_video_url")),
                        )
                    } else {
                        ""
                    }
                    val normalized = IntroVideoCache.normalizeHttpUrl(raw).orEmpty()
                    if (normalized.isNotBlank()) {
                        cache.setUrl(normalized)
                        // Background download — do not block Splash / MainActivity.
                        Thread(
                            {
                                try {
                                    kotlinx.coroutines.runBlocking {
                                        cache.fileStore().ensureCached(normalized)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Intro MP4 background download failed", e)
                                }
                            },
                            "intro-mp4-download",
                        ).start()
                    } else if (task.isSuccessful && snap != null && !snap.exists()) {
                        // Explicit empty config — only clear if we know doc is missing.
                        Log.i(TAG, "Intro Config/intro missing — keep prior URL/file")
                    }
                    Log.i(
                        TAG,
                        "Intro cache prefetch OK blank=${normalized.isBlank()} " +
                            "len=${normalized.length} prefix=${normalized.take(64)}",
                    )
                } else {
                    Log.w(
                        TAG,
                        "Intro cache prefetch failed — keep existing cache " +
                            "prefix=${cache.getUrl().take(64)} file=${cache.fileStore().hasReadyFile()}",
                        task.exception,
                    )
                }
                introCacheReady = true
                tryScheduleMainWhenReady()
            }
    }

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
                @Suppress("UNCHECKED_CAST")
                val branding = (data["branding"] as? Map<String, Any?>) ?: emptyMap()
                val hotelName = firstNonBlank(
                    data["name"] as? String,
                    data["hotel_name"] as? String,
                    data["hotelName"] as? String,
                )
                val logoUrl = firstNonBlank(
                    asTrimmedString(branding["logo_url"]),
                    asTrimmedString(branding["logoUrl"]),
                    asTrimmedString(branding["logo"]),
                    asTrimmedString(data["logo_url"]),
                    asTrimmedString(data["logoUrl"]),
                    asTrimmedString(data["logo"]),
                )

                // Admin mirrors introVideoUrl on the hotel root — seed local cache early.
                val mirroredIntro = IntroVideoCache.normalizeHttpUrl(
                    firstNonBlank(
                        asTrimmedString(data["introVideoUrl"]),
                        asTrimmedString(data["intro_video_url"]),
                    ),
                )
                if (!mirroredIntro.isNullOrBlank()) {
                    IntroVideoCache(applicationContext).setUrl(mirroredIntro)
                }

                // Keep corporate welcome tagline stable; hotel may refine with name.
                if (hotelName.isNotBlank() && !BuildConfig.IS_CORPORATE) {
                    splashWelcome.isVisible = true
                    splashWelcome.text = getString(R.string.splash_welcome_to, hotelName)
                } else if (splashWelcome.text.isNullOrBlank() && BuildConfig.IS_CORPORATE) {
                    splashWelcome.isVisible = true
                    splashWelcome.text = getString(R.string.splash_welcome_loading)
                }
                loadSplashLogo(logoUrl) {
                    brandingReady = true
                    Log.i(TAG, "Splash hotel config ready → name=$hotelName logo=${logoUrl.take(120)}")
                    tryScheduleMainWhenReady()
                }
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
        if (!brandingReady || !roomReady || !introCacheReady) return
        splashStatus.text = getString(R.string.splash_status_ready)
        scheduleMain()
    }

    private fun scheduleMain(minRemainingMs: Long = MIN_DISPLAY_MS) {
        if (hasNavigated || mainTransitionScheduled || !overlayGatePassed) return
        if (!brandingReady || !roomReady || !introCacheReady) return
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
        startActivity(
            Intent(this, MainActivity::class.java).apply {
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

    private fun asTrimmedString(value: Any?): String? = when (value) {
        null -> null
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Number, is Boolean -> value.toString()
        else -> value.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun loadSplashLogo(rawUrl: String, onSettled: () -> Unit) {
        val logoView = splashLogo ?: return
        val remoteUrl = normalizeRemoteImageUrl(rawUrl)
        if (BuildConfig.IS_CORPORATE || remoteUrl.isNullOrBlank()) {
            logoView.isVisible = BuildConfig.IS_CORPORATE
            if (BuildConfig.IS_CORPORATE) {
                logoView.setImageResource(BrandAssets.logoRes)
            } else {
                logoView.setImageDrawable(null)
            }
            onSettled()
            return
        }
        logoView.isVisible = false
        logoView.setImageDrawable(null)
        imageLoader.enqueue(
            ImageRequest.Builder(this)
                .data(remoteUrl)
                .crossfade(true)
                .allowHardware(false)
                .target(
                    onStart = {
                        logoView.isVisible = false
                        logoView.setImageDrawable(null)
                    },
                    onSuccess = { result ->
                        logoView.setImageDrawable(result)
                        logoView.isVisible = true
                    },
                    onError = {
                        logoView.isVisible = false
                        logoView.setImageDrawable(null)
                    },
                )
                .listener(
                    onSuccess = { _, _ ->
                        Log.i(TAG, "SplashLogo loaded OK")
                        onSettled()
                    },
                    onError = { _, result ->
                        Log.e(
                            TAG,
                            "SplashLogo FAILED url=${remoteUrl.take(160)}: ${result.throwable.message}",
                            result.throwable,
                        )
                        onSettled()
                    },
                )
                .build(),
        )
    }

    private fun normalizeRemoteImageUrl(url: String): String? {
        var cleaned = url.trim().trim('"', '\'').trim()
        if (cleaned.isBlank()) return null
        if (cleaned.startsWith("data:image/svg", ignoreCase = true)) return null

        val wikiFilePage = Regex(
            pattern = """^https?://(?:commons\.wikimedia\.org|(?:[a-z]+\.)?wikipedia\.org)/wiki/File:(.+)$""",
            option = RegexOption.IGNORE_CASE,
        ).matchEntire(cleaned)
        if (wikiFilePage != null) {
            val fileName = wikiFilePage.groupValues[1]
            cleaned = "https://commons.wikimedia.org/wiki/Special:FilePath/$fileName"
        }
        return cleaned
    }

    companion object {
        private const val TAG = "SplashActivity"
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val UNPAIRED_DELAY_MS = 900L
        private const val MIN_DISPLAY_MS = 1_100L
        /** Modern devices — branding + room + intro cache. */
        private const val DATA_TIMEOUT_MS = 10_000L
        /** API &lt; 30 TV boxes — Firestore / TLS often slower on cold boot. */
        private const val DATA_TIMEOUT_LEGACY_MS = 22_000L
        private const val OVERLAY_DENY_CONTINUE_MS = 2_500L

        private fun dataTimeoutMs(): Long =
            if (Build.VERSION.SDK_INT < 30) DATA_TIMEOUT_LEGACY_MS else DATA_TIMEOUT_MS
    }
}
