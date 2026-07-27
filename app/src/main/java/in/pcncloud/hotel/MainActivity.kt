package `in`.pcncloud.hotel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
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
import `in`.pcncloud.hotel.kiosk.KioskLockTask
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.ScreensaverOverlay
import `in`.pcncloud.hotel.ui.components.ServiceSuspendedScreen
import `in`.pcncloud.hotel.ui.navigation.HotelNavGraph
import `in`.pcncloud.hotel.ui.theme.IkhsanaHotelTVTheme
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay

/**
 * Guest dashboard host. Also registered as a HOME launcher candidate for kiosk TVs
 * (see AndroidManifest). Back behaviour is owned primarily by [HotelNavGraph]; this
 * Activity callback is a safety net when Compose has not consumed the event.
 *
 * Lock Task Mode is driven live from Realtime Database
 * `hotels/{hotelId}/config/isKioskModeEnabled` (Super Admin → Kiosk Settings).
 */
class MainActivity : ComponentActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var repository: FirestoreRepository
    private val syncListeners = mutableListOf<ListenerRegistration>()

    /** RTDB listener on hotels/{hotelId}/config for live kiosk / Lock Task control. */
    private var kioskConfigRef: DatabaseReference? = null
    private var kioskConfigListener: ValueEventListener? = null

    /**
     * Latest kiosk flag from RTDB (null until first cloud snapshot).
     * Prefer this over defaults so Admin unlock is not overwritten on resume.
     */
    private var currentKioskState: Boolean? = null

    /** @deprecated Prefer [resolveKioskEnabled] — kept in sync for existing call sites. */
    private var isKioskModeEnabled: Boolean = false

    /** Last packages applied from RTDB — skip no-op snapshot repeats. */
    private var lastAppliedAllowedPackages: List<String>? = null

    /** Bumped on any remote / touch interaction so the idle timer restarts. */
    private var lastInteractionAt by mutableLongStateOf(System.currentTimeMillis())

    /** When true, [ScreensaverOverlay] is shown; nav graph underneath stays composed. */
    private var screensaverVisible by mutableStateOf(false)

    private fun kioskPrefs() =
        applicationContext.getSharedPreferences(KIOSK_PREFS, Context.MODE_PRIVATE)

    /**
     * Authoritative local kiosk flag: in-memory RTDB value, else SharedPreferences.
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

        // Seed from last persisted RTDB value (default unlocked until cloud says otherwise).
        isKioskModeEnabled = resolveKioskEnabled()
        applyKeepScreenOn(isKioskModeEnabled)
        applyLockTaskFromPersistedState("onCreate")

        installKioskBackSafetyNet()

        hotelConfig = HotelConfig(applicationContext)
        val hotelId = hotelConfig.getHotelId()
        if (hotelId.isNullOrBlank()) {
            Log.i(TAG, "No paired hotelId — opening SplashActivity")
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        // Live Web Admin control via RTDB hotels/{hotelId}/config.
        attachKioskConfigRealtimeListener(hotelId)

        // Only prompt Home selection when kiosk is ON (avoid relaunch loops when unlocked).
        if (resolveKioskEnabled() && !isDefaultHomeLauncher()) {
            requestHomeLauncherSelection(this)
            checkAndPromptDefaultLauncher()
        }

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

    /** True when this package is the resolved default HOME launcher. */
    private fun isDefaultHomeLauncher(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        } catch (e: Exception) {
            Log.w(TAG, "isDefaultHomeLauncher check failed", e)
            false
        }
    }

    /**
     * Triggers the system Home-app chooser (or brings this launcher forward).
     * Required so Android TV surfaces this app in Home / default-launcher selection.
     */
    fun requestHomeLauncherSelection(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "requestHomeLauncherSelection — fired HOME intent")
        } catch (e: Exception) {
            Log.e(TAG, "requestHomeLauncherSelection failed", e)
        }
    }

    /**
     * If this app is not the current default HOME launcher, open system Home settings
     * so staff can set PCN Cloud / Hotel TV as the default launcher.
     */
    private fun checkAndPromptDefaultLauncher() {
        if (isDefaultHomeLauncher()) {
            Log.i(TAG, "Already default HOME launcher → $packageName")
            return
        }

        Log.w(TAG, "Not default HOME launcher — prompting Home settings")
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_HOME_SETTINGS unavailable, falling back", e)
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open default-apps settings", e2)
            }
        }
    }

    /**
     * Live RTDB listener on `hotels/{hotelId}/config`.
     * Reads `isKioskModeEnabled` and applies Lock Task immediately on change.
     */
    private fun attachKioskConfigRealtimeListener(hotelId: String) {
        try {
            detachKioskConfigRealtimeListener()

            val ref = FirebaseDatabase
                .getInstance(FirebaseApp.getInstance(), RTDB_URL)
                .getReference("hotels/$hotelId/config")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.w(TAG, "RTDB hotels/$hotelId/config missing — keeping kiosk=$isKioskModeEnabled")
                        return
                    }

                    val isKioskEnabled = readKioskEnabledFromConfig(snapshot) ?: run {
                        Log.w(
                            TAG,
                            "RTDB hotels/$hotelId/config has no isKioskModeEnabled — " +
                                "keeping kiosk=$isKioskModeEnabled",
                        )
                        return
                    }
                    val allowedPackages = readAllowedPackagesFromConfig(snapshot)

                    val kioskChanged = currentKioskState != isKioskEnabled
                    val packagesChanged = lastAppliedAllowedPackages != allowedPackages

                    Log.i(
                        TAG,
                        "RTDB hotels/$hotelId/config → isKioskModeEnabled=$isKioskEnabled " +
                            "allowedPackages=${allowedPackages.size} " +
                            "kioskChanged=$kioskChanged packagesChanged=$packagesChanged",
                    )

                    // Ignore no-op syncs — prevents relaunch / Lock Task churn while disabled.
                    if (!kioskChanged && !packagesChanged && currentKioskState != null) {
                        Log.d(TAG, "RTDB kiosk snapshot unchanged — skip apply")
                        return
                    }

                    if (KioskPolicy.hasAdminOverride(this@MainActivity)) {
                        KioskPolicy.clearAdminOverride(this@MainActivity)
                    }

                    if (kioskChanged || currentKioskState == null) {
                        persistKioskState(isKioskEnabled)
                        applyKeepScreenOn(isKioskEnabled)
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
                            // Unlocked: allow normal minimize — do not startActivity / reclaim.
                            KioskPolicy.markUserMinimized(this@MainActivity)
                        }
                    }

                    if (packagesChanged || lastAppliedAllowedPackages == null) {
                        applyLockTaskPackages(allowedPackages)
                        lastAppliedAllowedPackages = allowedPackages
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        TAG,
                        "RTDB hotels/$hotelId/config listener cancelled: ${error.message}",
                        error.toException(),
                    )
                }
            }

            kioskConfigRef = ref
            kioskConfigListener = listener
            ref.addValueEventListener(listener)
            Log.i(TAG, "Attached RTDB kiosk listener → hotels/$hotelId/config")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach RTDB hotels/$hotelId/config listener", e)
        }
    }

    private fun detachKioskConfigRealtimeListener() {
        val ref = kioskConfigRef
        val listener = kioskConfigListener
        if (ref != null && listener != null) {
            try {
                ref.removeEventListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove RTDB kiosk listener", e)
            }
        }
        kioskConfigRef = null
        kioskConfigListener = null
    }

    /** Prefer camelCase Admin field; accept legacy snake_case mirror. */
    private fun readKioskEnabledFromConfig(snapshot: DataSnapshot): Boolean? {
        val raw = snapshot.child("isKioskModeEnabled").value
            ?: snapshot.child("is_kiosk_mode_enabled").value
            ?: return null
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.equals("true", ignoreCase = true)
            else -> null
        }
    }

    private fun readAllowedPackagesFromConfig(snapshot: DataSnapshot): List<String> {
        val node = when {
            snapshot.hasChild("allowedPackages") -> snapshot.child("allowedPackages")
            snapshot.hasChild("allowed_packages") -> snapshot.child("allowed_packages")
            else -> return emptyList()
        }
        return node.children.mapNotNull { child ->
            (child.value as? String)?.trim()?.takeIf(String::isNotEmpty)
        }
    }

    /**
     * Apply Lock Task package whitelist via [DevicePolicyManager.setLockTaskPackages]
     * and suppress system UI with [DevicePolicyManager.setLockTaskFeatures]
     * (`LOCK_TASK_FEATURE_NONE`). Merges Firebase packages + hotel app + YouTube / Live TV.
     */
    private fun applyLockTaskPackages(allowedPackagesList: List<String>) {
        KioskLockTask.applyAllowlist(this, allowedPackagesList)
        MyDeviceAdminReceiver.applyStrictLockTaskFeatures(this)
    }

    /**
     * Start / stop Lock Task Mode. Wrapped safely — devices that are not device-owner
     * (or not allowlisted for lock task) throw; we must never crash the guest UI.
     */
    private fun applyLockTaskMode(enabled: Boolean) {
        try {
            if (enabled) {
                MyDeviceAdminReceiver.applyStrictLockTaskFeatures(this)
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
     * Soft Lock Task sync when focus returns — only while kiosk is ON.
     * When disabled, do nothing so we never fight normal minimization.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (!resolveKioskEnabled()) return
        applyLockTaskFromPersistedState("onWindowFocusChanged")
    }

    override fun onResume() {
        super.onResume()
        if (!screensaverVisible) {
            lastInteractionAt = System.currentTimeMillis()
        }
        // Only re-assert Lock Task while kiosk is ON.
        if (resolveKioskEnabled()) {
            applyLockTaskFromPersistedState("onResume")
        }
    }

    override fun onStart() {
        super.onStart()
        if (resolveKioskEnabled()) {
            KioskPolicy.clearUserMinimized(this)
        }
        if (!KioskPolicy.isExternalAppSessionActive(this)) {
            KioskPolicy.clearExternalAppSession(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // When unlocked, ignore HOME/MAIN re-entry side effects that re-lock UI.
        if (!resolveKioskEnabled()) {
            Log.d(TAG, "onNewIntent ignored — kiosk disabled")
            return
        }
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
     * Bring MainActivity to front ONLY while Kiosk Mode is ACTIVE.
     * When [isKioskModeEnabled] is false, do nothing — allow normal minimize / Home.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        val kioskOn = resolveKioskEnabled()
        isKioskModeEnabled = kioskOn

        if (!kioskOn) {
            KioskPolicy.markUserMinimized(this)
            Log.d(TAG, "onUserLeaveHint — kiosk DISABLED, allow normal minimization")
            return
        }

        if (KioskPolicy.isExternalAppSessionActive(this)) {
            Log.d(TAG, "onUserLeaveHint — OTT/IPTV session under kiosk, skip reclaim")
            return
        }

        Log.d(TAG, "onUserLeaveHint — kiosk ON, reordering MainActivity to front")
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring MainActivity to front after leave/Home", e)
        }
    }

    override fun onDestroy() {
        detachKioskConfigRealtimeListener()

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
        /** SharedPreferences key — matches Admin Panel / RTDB field name. */
        private const val PREF_KIOSK_ENABLED = "isKioskModeEnabled"
        /** Must match admin-panel RTDB region (google-services.json may still list us-central). */
        private const val RTDB_URL =
            "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app"
        /** 10 minutes of no remote / touch input before the screen saver appears. */
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
