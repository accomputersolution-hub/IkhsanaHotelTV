package `in`.pcncloud.hotel

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import `in`.pcncloud.hotel.config.HotelConfig
import `in`.pcncloud.hotel.data.FirestorePaths
import `in`.pcncloud.hotel.data.model.HotelBranding
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskRemoteConfig
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.ScreensaverOverlay
import `in`.pcncloud.hotel.ui.components.ServiceSuspendedScreen
import `in`.pcncloud.hotel.ui.navigation.HotelNavGraph
import `in`.pcncloud.hotel.ui.theme.IkhsanaHotelTVTheme
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay

/**
 * Guest dashboard host. Also registered as a HOME launcher candidate for kiosk TVs
 * (see AndroidManifest). Back behaviour is owned primarily by [HotelNavGraph]; this
 * Activity callback is a safety net when Compose has not consumed the event.
 *
 * Lock Task Mode and package whitelist are driven live from Firestore
 * `Hotels/{hotelId}` fields `isKioskModeEnabled` and `allowedPackages`
 * (Super Admin → Kiosk Settings).
 */
class MainActivity : ComponentActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var repository: FirestoreRepository
    private val syncListeners = mutableListOf<ListenerRegistration>()

    /** Firestore listener on Hotels/{hotelId} for live kiosk / Lock Task control. */
    private var hotelKioskListener: ListenerRegistration? = null

    /**
     * Latest kiosk flag from Firestore (null until first cloud snapshot).
     * Prefer this over defaults so Admin unlock is not overwritten on resume.
     */
    private var currentKioskState: Boolean? = null

    /** @deprecated Prefer [resolveKioskEnabled] — kept in sync for existing call sites. */
    private var isKioskModeEnabled: Boolean = false

    /** Bumped on any remote / touch interaction so the idle timer restarts. */
    private var lastInteractionAt by mutableLongStateOf(System.currentTimeMillis())

    /** When true, [ScreensaverOverlay] is shown; nav graph underneath stays composed. */
    private var screensaverVisible by mutableStateOf(false)

    private fun kioskPrefs() =
        applicationContext.getSharedPreferences(KIOSK_PREFS, Context.MODE_PRIVATE)

    /**
     * Authoritative local kiosk flag: in-memory Firestore value, else SharedPreferences.
     * Default **false** so a missing/stale preference never re-locks after Admin unlock.
     */
    private fun resolveKioskEnabled(): Boolean =
        currentKioskState ?: kioskPrefs().getBoolean(PREF_KIOSK_ENABLED, false)

    /** Persist cloud kiosk flag locally so resume/focus cannot re-enable after unlock. */
    private fun persistKioskState(enabled: Boolean) {
        currentKioskState = enabled
        isKioskModeEnabled = enabled
        kioskPrefs().edit().putBoolean(PREF_KIOSK_ENABLED, enabled).apply()
        KioskPolicy.setKioskModeEnabled(
            context = this,
            enabled = enabled,
            source = KioskPolicy.KioskSource.REALTIME_DATABASE,
        )
        Log.i(TAG, "persistKioskState → $enabled")
    }

    /**
     * Apply Lock Task from persisted state — never force-enable blindly.
     * Used by onResume / onWindowFocusChanged.
     */
    private fun applyLockTaskFromPersistedState(reason: String) {
        val isKioskEnabled = resolveKioskEnabled()
        isKioskModeEnabled = isKioskEnabled
        applyKeepScreenOn(isKioskEnabled)
        Log.d(TAG, "applyLockTaskFromPersistedState($reason) → $isKioskEnabled")
        if (isKioskEnabled) {
            try {
                startLockTask()
                Log.d("KioskMode", "Lock Task Mode ENABLED ($reason)")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG, "startLockTask failed ($reason)", e)
            }
        } else {
            try {
                stopLockTask()
                Log.d("KioskMode", "Lock Task Mode DISABLED ($reason)")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG, "stopLockTask failed ($reason)", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        // Seed from last persisted Admin/Firestore value (default unlocked until cloud says otherwise).
        isKioskModeEnabled = resolveKioskEnabled()
        applyKeepScreenOn(isKioskModeEnabled)
        applyLockTaskFromPersistedState("onCreate")

        // Remote Config is fallback only — never override a Firestore-delivered unlock.
        KioskRemoteConfig.syncOnLaunch(this) { enabled ->
            if (currentKioskState != null) {
                Log.i(TAG, "Skip Remote Config kiosk=$enabled — Firestore already set $currentKioskState")
                return@syncOnLaunch
            }
            Log.i(TAG, "Kiosk mode after Remote Config sync → $enabled")
            persistKioskState(enabled)
            applyKeepScreenOn(enabled)
            applyLockTaskFromPersistedState("remoteConfig")
        }

        installKioskBackSafetyNet()

        hotelConfig = HotelConfig(applicationContext)
        val hotelId = hotelConfig.getHotelId()
        if (hotelId.isNullOrBlank()) {
            Log.i(TAG, "No paired hotelId — opening SplashActivity")
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        // Live Web Admin control via Firestore Hotels/{hotelId}.
        attachHotelKioskFirestoreListener(hotelId)

        repository = FirestoreRepository(hotelConfig)
        val viewModelFactory = HotelViewModelFactory(repository, hotelConfig)

        Log.d(TAG, "TV Firestore sync starting → hotelId=$hotelId room=${hotelConfig.roomNumber}")
        Log.d(TAG, "Path Hotels/{hotelId} → ${FirestorePaths.hotelDocument(hotelId)}")
        Log.d(TAG, "Path Hotels/{hotelId}/Rooms → ${FirestorePaths.roomsCollection(hotelId)}")
        Log.d(TAG, "Path guest room → ${FirestorePaths.roomDocument(hotelId, hotelConfig.roomNumber)}")
        Log.d(TAG, "Path menu → ${FirestorePaths.menuCollection(hotelId)}")
        Log.d(TAG, "Path requests → ${FirestorePaths.requestsCollection(hotelId)}")
        Log.d(TAG, "Path alerts → ${FirestorePaths.alertsCollection(hotelId)}")

        // Bind diagnostic SnapshotListeners to Hotels/{saved_hotel_id}/…
        syncListeners += repository.attachSyncDiagnostics(
            onBranding = { branding ->
                Log.d(
                    TAG,
                    "MainActivity branding update → logo_url=${branding.logoUrl} " +
                        "bg_wallpaper=${branding.bgWallpaperUrl} name=${branding.hotelName} " +
                        "status=${branding.status}",
                )
            },
            onRooms = { rooms ->
                Log.d(
                    TAG,
                    "MainActivity Rooms update → count=${rooms.size} " +
                        rooms.joinToString { "${it.roomNumber}:${it.status}:${it.guestName}" },
                )
            },
        )

        setContent {
            val view = LocalView.current
            SideEffect {
                ViewCompat.setBackground(view, null)
            }

            var branding by remember { mutableStateOf(HotelBranding(hotelId = hotelId)) }

            LaunchedEffect(hotelId) {
                repository.observeHotelBranding().collect { next ->
                    branding = next
                    if (next.status.equals("inactive", ignoreCase = true)) {
                        Log.w(TAG, "Hotel $hotelId is inactive — blocking guest UI")
                    }
                }
            }

            val hotelActive = !branding.status.equals("inactive", ignoreCase = true)

            LaunchedEffect(lastInteractionAt, hotelActive, screensaverVisible) {
                if (!hotelActive || screensaverVisible) return@LaunchedEffect
                delay(INACTIVITY_TIMEOUT_MS)
                screensaverVisible = true
                Log.i(TAG, "Inactivity timeout — showing screen saver")
            }

            IkhsanaHotelTVTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyDeep),
                ) {
                    if (!hotelActive) {
                        ServiceSuspendedScreen(hotelName = branding.hotelName)
                    } else {
                        // Keep nav graph composed under the overlay so the guest
                        // returns to the exact screen they left.
                        HotelNavGraph(viewModelFactory = viewModelFactory)
                        if (screensaverVisible) {
                            ScreensaverOverlay(branding = branding)
                        }
                    }
                }
            }
        }
    }

    /**
     * Live Firestore listener on `Hotels/{hotelId}`.
     * Super Admin Kiosk Settings writes `isKioskModeEnabled` + `allowedPackages`
     * on this document — TVs apply Lock Task instantly.
     */
    private fun attachHotelKioskFirestoreListener(hotelId: String) {
        try {
            hotelKioskListener?.remove()
            hotelKioskListener = FirebaseFirestore.getInstance()
                .collection(FirestorePaths.HOTELS)
                .document(hotelId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Hotels/$hotelId kiosk listener error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        Log.w(TAG, "Hotels/$hotelId missing — keeping kiosk=$isKioskModeEnabled")
                        return@addSnapshotListener
                    }

                    val isKioskEnabled = snapshot.getBoolean("isKioskModeEnabled") ?: true
                    @Suppress("UNCHECKED_CAST")
                    val allowedPackages = (snapshot.get("allowedPackages") as? List<*>)
                        ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
                        .orEmpty()

                    Log.i(
                        TAG,
                        "Firestore Hotels/$hotelId → isKioskModeEnabled=$isKioskEnabled " +
                            "allowedPackages=${allowedPackages.size} $allowedPackages",
                    )

                    // Persist so onResume / focus cannot re-lock after Admin unlock.
                    if (KioskPolicy.hasAdminOverride(this)) {
                        KioskPolicy.clearAdminOverride(this)
                    }
                    persistKioskState(isKioskEnabled)
                    applyKeepScreenOn(isKioskEnabled)
                    applyLockTaskPackages(allowedPackages)

                    if (isKioskEnabled) {
                        try {
                            startLockTask()
                            Log.d("KioskMode", "Lock Task Mode ENABLED")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.w(TAG, "startLockTask failed", e)
                        }
                    } else {
                        try {
                            stopLockTask()
                            Log.d("KioskMode", "Lock Task Mode DISABLED")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.w(TAG, "stopLockTask failed", e)
                        }
                    }
                }
            Log.i(TAG, "Attached Firestore kiosk listener → Hotels/$hotelId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Hotels/$hotelId kiosk listener", e)
        }
    }

    /**
     * Apply Lock Task package whitelist via [DevicePolicyManager.setLockTaskPackages].
     * Combines Firebase [allowedPackages] + this app + YouTube TV so guests can leave
     * Lock Task into allowlisted OTT apps without being blocked by the system.
     * No-op (safe) when not device owner.
     */
    private fun applyLockTaskPackages(allowedPackagesList: List<String>) {
        try {
            val adminName = ComponentName(this, MyDeviceAdminReceiver::class.java)
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

            if (!dpm.isDeviceOwnerApp(packageName)) {
                Log.w(TAG, "Not device owner — skip setLockTaskPackages")
                return
            }

            // Combine our app package + allowed packages from Firebase (+ YouTube TV baseline).
            val packagesToAllow = (
                allowedPackagesList +
                    packageName +
                    YOUTUBE_TV_PACKAGE
                )
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toTypedArray()

            dpm.setLockTaskPackages(adminName, packagesToAllow)
            Log.i(TAG, "setLockTaskPackages → ${packagesToAllow.toList()}")
        } catch (e: SecurityException) {
            Log.w(TAG, "setLockTaskPackages security exception", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "setLockTaskPackages invalid args", e)
        } catch (e: Exception) {
            Log.w(TAG, "setLockTaskPackages failed", e)
        }
    }

    /**
     * Start / stop Lock Task Mode. Wrapped safely — devices that are not device-owner
     * (or not allowlisted for lock task) throw; we must never crash the guest UI.
     */
    private fun applyLockTaskMode(enabled: Boolean) {
        try {
            if (enabled) {
                startLockTask()
                Log.d("KioskMode", "Lock Task Mode ENABLED")
                Log.i(TAG, "startLockTask() — Home/Back locked")
            } else {
                stopLockTask()
                Log.d("KioskMode", "Lock Task Mode DISABLED")
                Log.i(TAG, "stopLockTask() — normal navigation restored")
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Lock Task not permitted (not device-owner / not allowlisted)", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Lock Task state change failed", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Lock Task security exception", e)
        } catch (e: Exception) {
            Log.w(TAG, "Lock Task failed", e)
            e.printStackTrace()
        }
    }

    /**
     * Prevent display sleep / activity pause while kiosk is ON.
     * Without this, Android TV idle / dream / memory optimizations can drop Lock Task
     * after several minutes of no input.
     */
    private fun applyKeepScreenOn(enabled: Boolean) {
        try {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d(TAG, "FLAG_KEEP_SCREEN_ON added")
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d(TAG, "FLAG_KEEP_SCREEN_ON cleared")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update KEEP_SCREEN_ON flag", e)
        }
    }

    /** True when local cache / SharedPreferences say kiosk Lock Task should be active. */
    private fun isKioskActive(): Boolean = resolveKioskEnabled()

    /**
     * Fallback when Compose [androidx.activity.compose.BackHandler] is not in the tree
     * (e.g. service-suspended screen). Kiosk ON → never finish; kiosk OFF → leave task.
     */
    private fun installKioskBackSafetyNet() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isKioskActive()) {
                        Log.d(TAG, "Kiosk safety-net: Back blocked at Activity")
                    } else {
                        Log.d(TAG, "Kiosk off safety-net: moveTaskToBack")
                        moveTaskToBack(true)
                    }
                }
            },
        )
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        markUserActive(dismissScreensaver = true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screensaverVisible) {
            // Consume every remote key so dining/services focus does not move;
            // session and back stack stay exactly as they were.
            if (event.action == KeyEvent.ACTION_DOWN) {
                markUserActive(dismissScreensaver = true)
                Log.d(TAG, "Screen saver dismissed by keyCode=${event.keyCode}")
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Soft Lock Task sync when focus returns.
     * Never force-enable — honor Admin unlock via [resolveKioskEnabled].
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        applyLockTaskFromPersistedState("onWindowFocusChanged")
    }

    override fun onResume() {
        super.onResume()
        if (!screensaverVisible) {
            lastInteractionAt = System.currentTimeMillis()
        }
        // Re-evaluate from persisted Admin/Firestore state — do NOT blind startLockTask().
        applyLockTaskFromPersistedState("onResume")
    }

    override fun onStart() {
        super.onStart()
        KioskPolicy.clearUserMinimized(this)
        // Returning from YouTube / Live TV / Home — end external session suppress.
        if (!KioskPolicy.isExternalAppSessionActive(this)) {
            KioskPolicy.clearExternalAppSession(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // HOME category relaunch (Home pressed from YouTube / Live TV / stock UI).
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true ||
            intent.action == Intent.ACTION_MAIN
        ) {
            Log.i(TAG, "onNewIntent HOME/MAIN — hotel launcher reclaim")
            KioskPolicy.clearExternalAppSession(this)
            KioskPolicy.clearUserMinimized(this)
            applyLockTaskFromPersistedState("onNewIntent")
        }
    }

    /**
     * Hotel app is the primary launcher interface (locked OR unlocked).
     * Home / leave reclaim brings [MainActivity] to front, except while an
     * intentional OTT / IPTV session is active (YouTube, Live TV, etc.).
     * Pressing Home from those apps routes here via the HOME intent-filter.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (KioskPolicy.isExternalAppSessionActive(this)) {
            Log.d(TAG, "onUserLeaveHint — external OTT/IPTV session, skip reclaim")
            return
        }

        Log.d(TAG, "onUserLeaveHint — reclaiming hotel launcher (kiosk=${resolveKioskEnabled()})")
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring MainActivity to front after leave/Home", e)
        }
    }

    override fun onDestroy() {
        try {
            hotelKioskListener?.remove()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove Hotels kiosk listener", e)
        }
        hotelKioskListener = null

        syncListeners.forEach { registration ->
            registration.remove()
        }
        syncListeners.clear()
        Log.d(TAG, "MainActivity sync listeners removed")
        super.onDestroy()
    }

    private fun markUserActive(dismissScreensaver: Boolean) {
        lastInteractionAt = System.currentTimeMillis()
        if (dismissScreensaver && screensaverVisible) {
            screensaverVisible = false
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val KIOSK_PREFS = "hotel_tv_kiosk"
        /** SharedPreferences key — matches Admin Panel field name. */
        private const val PREF_KIOSK_ENABLED = "isKioskModeEnabled"
        /** Always allowlisted for Lock Task so Entertainment → YouTube works under kiosk. */
        private const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"
        /** 10 minutes of no remote / touch input before the screen saver appears. */
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
