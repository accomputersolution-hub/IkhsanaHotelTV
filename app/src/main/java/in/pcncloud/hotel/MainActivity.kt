package `in`.pcncloud.hotel

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import `in`.pcncloud.hotel.kiosk.HomeKeyInterceptorService
import `in`.pcncloud.hotel.kiosk.HotelSessionManager
import `in`.pcncloud.hotel.kiosk.KioskLockTask
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService
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
 * Guest dashboard host and Android TV Home / Lock Task kiosk shell.
 *
 * When provisioned as **Device Owner**, [ensureDeviceOwnerLockTask] whitelists this
 * package and enters true Lock Task Mode so the physical Home key cannot reach the
 * stock Android TV launcher. Before Device Owner is set, [onUserLeaveHint] reclaims
 * the UI as a fallback.
 */
class MainActivity : ComponentActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var repository: FirestoreRepository
    private val syncListeners = mutableListOf<ListenerRegistration>()

    /** RTDB listener on hotels/{hotelId}/config for live kiosk / Lock Task control. */
    private var kioskConfigRef: DatabaseReference? = null
    private var kioskConfigListener: ValueEventListener? = null

    /** RTDB listener on hotels/{hotelId}/rooms/{room}/ for remote Admin logout. */
    private var roomSessionRef: DatabaseReference? = null
    private var roomSessionListener: ValueEventListener? = null

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

    /**
     * Bumped when HOME reclaim / pending OTT-return requests Root Home.
     * Observed by [HotelNavGraph] to pop the Compose back stack to [Routes.HOME].
     */
    private var navigateHomeSignal by mutableLongStateOf(0L)

    /**
     * Set true when launching OTT after a synchronous Root Home switch.
     * On resume we only clear session state (UI already Home — no flicker).
     */
    private var pendingReturnToHome: Boolean = false

    /**
     * Opaque navy cover — fallback only if Root Home was not applied before OTT.
     */
    private var ottTransitionCover by mutableStateOf(false)

    /**
     * Local foreground gate — blocks self-reclaim loops while MainActivity is already
     * active. Synced to [KioskPolicy.setMainActivityForeground] for Watchdog.
     */
    private var isAppInForeground: Boolean = false

    /**
     * API &lt; 30: debounce + in-flight guard for pause/resume reclaim storms
     * (forceBringToFront → onNewIntent/onResume → onPause → …).
     */
    private var lastReclaimTimeMs: Long = 0L
    private var reclaimInFlight: Boolean = false

    /** Avoid repeatedly opening overlay settings when SYSTEM_ALERT_WINDOW is missing. */
    private var overlayPromptShown: Boolean = false

    /**
     * API &lt; 30 only: 1×1 transparent overlay (SYSTEM_ALERT_WINDOW) that helps
     * keep process privileges so HOME cannot flash the stock TV launcher.
     * Never used on API 30+ / Android 16.
     */
    private var kioskOverlayView: android.view.View? = null

    private fun kioskPrefs() =
        applicationContext.getSharedPreferences(KIOSK_PREFS, Context.MODE_PRIVATE)

    /**
     * Authoritative local kiosk flag from SharedPreferences (RTDB + Admin toggle).
     * Always re-sync memory from prefs so a local Admin unlock is not overwritten by
     * a stale [currentKioskState] on resume / Lock Task re-apply (Android 9/11).
     */
    private fun resolveKioskEnabled(): Boolean {
        val fromPrefs = kioskPrefs().getBoolean(PREF_KIOSK_ENABLED, false)
        if (currentKioskState != fromPrefs) {
            currentKioskState = fromPrefs
            isKioskModeEnabled = fromPrefs
        }
        return fromPrefs
    }

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
     * Used by onCreate / onWindowFocusChanged / onNewIntent.
     */
    private fun applyLockTaskFromPersistedState(reason: String) {
        val isKioskEnabled = resolveKioskEnabled()
        isKioskModeEnabled = isKioskEnabled
        applyKeepScreenOn(isKioskEnabled)
        Log.d(TAG, "applyLockTaskFromPersistedState($reason) → $isKioskEnabled")
        if (isKioskEnabled) {
            ensureDeviceOwnerLockTask(reason)
        } else {
            // Android 9/11: must clear DPM Lock Task packages, not only stopLockTask.
            KioskPolicy.disableKioskMode(
                activity = this,
                source = KioskPolicy.KioskSource.SYSTEM_DEFAULT,
                persistFlag = false,
            )
            Log.d("KioskMode", "Lock Task Mode DISABLED ($reason)")
        }
    }

    /**
     * Device Owner path: whitelist packages + true Lock Task (Home suppressed).
     * Non–Device Owner fallback: screen pinning via [startLockTask]; Home reclaim
     * is handled in [onUserLeaveHint].
     */
    private fun ensureDeviceOwnerLockTask(reason: String) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = MyDeviceAdminReceiver.getComponentName(this)
        val isOwner = dpm.isDeviceOwnerApp(packageName)

        if (isOwner) {
            try {
                val hotelId = currentHotelIdOrNull()
                val allowed = (
                    lastAppliedAllowedPackages
                        ?: KioskPolicy.getAllowedPackagesList(this, hotelId)
                    )
                val packages = KioskLockTask.buildLockTaskPackageArray(this, allowed)

                dpm.setLockTaskPackages(adminComponent, packages)
                MyDeviceAdminReceiver.applyStrictLockTaskFeatures(this)
                startLockTask()
                Log.i(
                    TAG,
                    "Device Owner Lock Task ($reason) — Home suppressed, " +
                        "packages=${packages.toList()}",
                )
                Log.d("KioskMode", "Lock Task Mode ENABLED ($reason / device-owner)")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG, "Device Owner Lock Task failed ($reason)", e)
            }
        } else {
            Log.w(
                TAG,
                "NOT Device Owner ($reason) — screen-pinning fallback; " +
                    "run: adb shell dpm set-device-owner " +
                    MyDeviceAdminReceiver.DEVICE_OWNER_COMPONENT,
            )
            try {
                startLockTask()
                Log.d("KioskMode", "Lock Task Mode ENABLED ($reason / pinning fallback)")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG, "startLockTask fallback failed ($reason)", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Manifest uses Theme.HotelTv.BootSplash (instant black+logo window).
        // Switch to the main theme before inflating so Compose draws under IkhsanaHotelTV.
        setTheme(R.style.Theme_IkhsanaHotelTV)
        super.onCreate(savedInstanceState)

        // LOCKED_BOOT_COMPLETED / Keyguard: show over lock + wake display.
        enableShowOverLockScreen()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        // Seed from last persisted RTDB value (default unlocked until cloud says otherwise).
        isKioskModeEnabled = resolveKioskEnabled()
        applyKeepScreenOn(isKioskModeEnabled)
        applyLockTaskFromPersistedState("onCreate")

        installKioskBackSafetyNet()

        // Cold start may already carry NAVIGATE_TO_HOME from HomeKeyInterceptor / launcher.
        handleNavigateToHomeExtra(intent)

        hotelConfig = HotelConfig(applicationContext)
        if (!hotelConfig.isPaired()) {
            Log.i(TAG, "Not paired (hotel/room missing) — opening PairingActivity")
            HotelSessionManager.openPairingScreen(this)
            return
        }
        val hotelId = hotelConfig.getHotelId()!!
        val roomNumber = hotelConfig.getRoomNumberOrNull()!!

        // Live Web Admin control via RTDB hotels/{hotelId}/config.
        attachKioskConfigRealtimeListener(hotelId)

        // Remote logout: hotels/{hotelId}/rooms/{room}/session_active|status.
        attachRoomSessionRealtimeListener(hotelId, roomNumber)

        // Verify / request default Home launcher when kiosk is active.
        verifyAndRequestDefaultHomeLauncher()

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
                        HotelNavGraph(
                            viewModelFactory = viewModelFactory,
                            navigateHomeSignal = navigateHomeSignal,
                        )
                        if (screensaverVisible) {
                            ScreensaverOverlay(branding = branding)
                        }
                        // Covers Entertainment only while applying pending Root Home on return.
                        if (ottTransitionCover) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(NavyDeep),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Complements manifest [android:showWhenLocked] / [android:turnScreenOn] so
     * MainActivity can render during Direct Boot / LOCKED_BOOT_COMPLETED when
     * Keyguard would otherwise keep the window invisible.
     */
    private fun enableShowOverLockScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "enableShowOverLockScreen failed", e)
        }
    }

    /**
     * Android 10+ (API 29+): [SYSTEM_ALERT_WINDOW] grants a BAL exemption for
     * background Home reclaim. Prompt once if missing (SplashActivity also requests).
     */
    private fun ensureOverlayPermissionForBal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (Settings.canDrawOverlays(this)) return
        if (overlayPromptShown) return
        overlayPromptShown = true
        Log.w(
            TAG,
            "SYSTEM_ALERT_WINDOW missing — required for Android 10+ HOME reclaim BAL exemption",
        )
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open overlay permission settings", e)
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
     * Diagnostic + prompt: logs Home launcher status and opens system Home settings
     * / chooser when kiosk is ON but this app is not the default Home activity.
     * Also logs whether the hardware HOME Accessibility interceptor is enabled.
     */
    private fun verifyAndRequestDefaultHomeLauncher() {
        val isDefault = isDefaultHomeLauncher()
        val kioskOn = resolveKioskEnabled()
        val homeKeyServiceOn = HomeKeyInterceptorService.isEnabled(this)
        Log.i(
            TAG,
            "Home launcher diagnostic → package=$packageName isDefault=$isDefault " +
                "kiosk=$kioskOn homeKeyInterceptor=$homeKeyServiceOn",
        )

        if (!homeKeyServiceOn) {
            Log.w(
                TAG,
                "HomeKeyInterceptor NOT enabled — OEM may drop HOME under Lock Task. " +
                    HomeKeyInterceptorService.adbEnableCommands().joinToString(" && "),
            )
        }

        if (!kioskOn) {
            Log.d(TAG, "Skip Home launcher prompt — kiosk disabled")
            return
        }

        if (isDefault) {
            Log.i(TAG, "Already default HOME launcher")
            return
        }

        Log.w(TAG, "Not default HOME launcher — opening Home settings / chooser")
        openDefaultHomePicker()
    }

    /**
     * Opens [Settings.ACTION_HOME_SETTINGS] so the user can select this app
     * (`in.pcncloud.hotel`) as the permanent default Home / TV launcher.
     */
    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            Log.i(TAG, "openHomeSettings — ACTION_HOME_SETTINGS opened")
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_HOME_SETTINGS failed — trying HOME chooser", e)
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Could not open Home settings or chooser", ex)
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (ex2: Exception) {
                    Log.e(TAG, "Could not open default-apps settings", ex2)
                }
            }
        }
    }

    /**
     * Triggers the Android Default Home picker so staff can set this app as the
     * system launcher (required for Lock Task / Home eligibility on TV).
     */
    private fun openDefaultHomePicker() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.i(TAG, "openDefaultHomePicker — HOME chooser intent fired")
        } catch (e: Exception) {
            Log.w(TAG, "HOME chooser failed — opening Home settings", e)
            openHomeSettings()
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
     * Live RTDB listener on `hotels/{hotelId}/config`.
     * Reads `isKioskModeEnabled` + `allowedPackages` scoped to this hotel only.
     * Missing config / missing packages → empty allowlist (never keep another hotel's list).
     */
    private fun attachKioskConfigRealtimeListener(hotelId: String) {
        try {
            detachKioskConfigRealtimeListener()

            // Drop any stale Treasure Island (etc.) cache before Upper Deck snapshot arrives.
            KioskPolicy.bindWhitelistToHotelOrClear(this, hotelId)
            lastAppliedAllowedPackages = KioskPolicy.getAllowedPackagesList(this, hotelId)

            val ref = FirebaseDatabase
                .getInstance(FirebaseApp.getInstance(), RTDB_URL)
                .getReference("hotels/$hotelId/config")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.w(
                            TAG,
                            "RTDB hotels/$hotelId/config missing — empty allowlist (no fallback)",
                        )
                        applyLockTaskPackages(emptyList())
                        lastAppliedAllowedPackages = emptyList()
                        return
                    }

                    // Packages apply independently of kiosk flag — never skip and keep stale TI list.
                    val allowedPackages = readAllowedPackagesFromConfig(snapshot)
                    val isKioskEnabled = readKioskEnabledFromConfig(snapshot)

                    val packagesChanged = lastAppliedAllowedPackages != allowedPackages
                    val kioskChanged = isKioskEnabled != null && currentKioskState != isKioskEnabled

                    Log.i(
                        TAG,
                        "RTDB hotels/$hotelId/config → isKioskModeEnabled=$isKioskEnabled " +
                            "allowedPackages=${allowedPackages.size} " +
                            "kioskChanged=$kioskChanged packagesChanged=$packagesChanged",
                    )

                    if (packagesChanged || lastAppliedAllowedPackages == null) {
                        applyLockTaskPackages(allowedPackages)
                        lastAppliedAllowedPackages = allowedPackages
                    }

                    if (isKioskEnabled == null) {
                        Log.w(
                            TAG,
                            "RTDB hotels/$hotelId/config has no isKioskModeEnabled — " +
                                "keeping kiosk=$isKioskModeEnabled (packages already applied)",
                        )
                        return
                    }

                    // Ignore no-op kiosk syncs — prevents relaunch / Lock Task churn.
                    if (!kioskChanged && !packagesChanged && currentKioskState != null) {
                        Log.d(TAG, "RTDB kiosk snapshot unchanged — skip kiosk apply")
                        return
                    }

                    if (KioskPolicy.hasAdminOverride(this@MainActivity)) {
                        KioskPolicy.clearAdminOverride(this@MainActivity)
                    }

                    if (kioskChanged || currentKioskState == null) {
                        val turningOff = currentKioskState == true && !isKioskEnabled
                        persistKioskState(isKioskEnabled)
                        applyKeepScreenOn(isKioskEnabled)
                        if (isKioskEnabled) {
                            ensureDeviceOwnerLockTask("rtdb")
                        } else {
                            // Unlock: stop Lock Task + clear DPM whitelist (Android 9/11 OTT fix).
                            KioskPolicy.disableKioskMode(
                                activity = this@MainActivity,
                                source = KioskPolicy.KioskSource.REALTIME_DATABASE,
                                persistFlag = false,
                            )
                            Log.d("KioskMode", "Lock Task Mode DISABLED")
                            KioskPolicy.markUserMinimized(this@MainActivity)
                            if (turningOff) {
                                Log.i(TAG, "Kiosk OFF via RTDB — disableKioskMode + moveTaskToBack")
                                KioskPolicy.launchSystemDefaultLauncher(this@MainActivity)
                            }
                        }
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

    /**
     * Live RTDB listener on `hotels/{hotelId}/rooms/{roomNumber}`.
     * Admin Panel sets `status=UNPAIRED` or `session_active=false` → full local logout
     * and [PairingActivity].
     */
    private fun attachRoomSessionRealtimeListener(hotelId: String, roomNumber: String) {
        try {
            detachRoomSessionRealtimeListener()

            val path = HotelSessionManager.roomSessionPath(hotelId, roomNumber)
            val ref = FirebaseDatabase
                .getInstance(FirebaseApp.getInstance(), RTDB_URL)
                .getReference(path)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.d(TAG, "RTDB $path missing — keep local session")
                        return
                    }
                    val status = snapshot.child("status").value
                    val sessionActive = snapshot.child("session_active").value
                        ?: snapshot.child("sessionActive").value
                    Log.i(
                        TAG,
                        "RTDB room session → status=$status session_active=$sessionActive",
                    )
                    if (HotelSessionManager.isRemoteLogoutSignal(status, sessionActive)) {
                        Log.w(TAG, "Remote logout signal — clearing session → PairingActivity")
                        runOnUiThread {
                            HotelSessionManager.performLogout(
                                this@MainActivity,
                                reason = "rtdb_remote_logout",
                            )
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        TAG,
                        "RTDB room session listener cancelled: ${error.message}",
                        error.toException(),
                    )
                }
            }
            roomSessionRef = ref
            roomSessionListener = listener
            ref.addValueEventListener(listener)
            Log.i(TAG, "Attached RTDB room session listener → $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach RTDB room session listener", e)
        }
    }

    private fun detachRoomSessionRealtimeListener() {
        val ref = roomSessionRef
        val listener = roomSessionListener
        if (ref != null && listener != null) {
            try {
                ref.removeEventListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove RTDB room session listener", e)
            }
        }
        roomSessionRef = null
        roomSessionListener = null
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
            else -> {
                // No hotel-specific whitelist defined → empty (never invent a default list).
                Log.i(TAG, "RTDB config has no allowedPackages — emptyList()")
                return emptyList()
            }
        }
        // RTDB may store as list children OR a single empty placeholder.
        val fromChildren = node.children.mapNotNull { child ->
            (child.value as? String)?.trim()?.takeIf(String::isNotEmpty)
        }
        if (fromChildren.isNotEmpty()) return fromChildren

        // Some writers store a JSON array mirrored as indexed children already handled above.
        // Explicit empty array / null → emptyList.
        return emptyList()
    }

    /**
     * Apply Lock Task package whitelist via [DevicePolicyManager.setLockTaskPackages]
     * and suppress system UI with [DevicePolicyManager.setLockTaskFeatures]
     * (`LOCK_TASK_FEATURE_NONE`). Persists Admin packages scoped to the paired hotelId.
     *
     * When kiosk is OFF, packages are persisted for later but the live DPM whitelist
     * is cleared so Android 9/11 do not keep blocking OTT with "Unauthorized by Admin".
     */
    private fun applyLockTaskPackages(allowedPackagesList: List<String>) {
        val hotelId = currentHotelIdOrNull()
        KioskPolicy.setAllowedPackagesList(this, allowedPackagesList, hotelId)
        if (!resolveKioskEnabled()) {
            KioskPolicy.clearDeviceOwnerLockTaskPackages(this)
            Log.d(TAG, "applyLockTaskPackages — kiosk OFF, DPM whitelist cleared")
            return
        }
        KioskLockTask.applyAllowlist(this, allowedPackagesList)
        MyDeviceAdminReceiver.applyStrictLockTaskFeatures(this)
    }

    /** Safe hotelId before/after [hotelConfig] init. */
    private fun currentHotelIdOrNull(): String? =
        if (::hotelConfig.isInitialized) {
            hotelConfig.getHotelId()
        } else {
            HotelConfig(applicationContext).getHotelId()
        }

    /**
     * Validates whether an external app may be launched.
     * When Kiosk Mode is OFF → allow everything.
     * When Kiosk Mode is ON → only packages explicitly in this hotel's Admin whitelist
     * (no YouTube / OTT baseline bypass).
     */
    fun canLaunchApp(targetPackageName: String): Boolean {
        if (!isKioskModeEnabled) return true

        val allowedPackagesList = lastAppliedAllowedPackages
            ?: KioskPolicy.getAllowedPackagesList(this, currentHotelIdOrNull())
        return allowedPackagesList.contains(targetPackageName.trim())
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
                KioskPolicy.disableKioskMode(
                    activity = this,
                    source = KioskPolicy.KioskSource.SYSTEM_DEFAULT,
                    persistFlag = false,
                )
                Log.d("KioskMode", "Lock Task Mode DISABLED")
                Log.i(TAG, "disableKioskMode() — normal navigation restored")
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
                        if (isSubViewActive()) {
                            // Entertainment / section cards — return to Root Home
                            navigateToRootHome()
                        } else {
                            Log.d(TAG, "Kiosk safety-net: Back blocked at Root Home")
                        }
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

    /**
     * Capture Remote HOME / BACK even under Device Owner Lock Task Mode.
     * Lock Task normally drops these at the OS layer; when they do reach us
     * (or OEM delivers them to the focused Activity), force Root Home instead
     * of letting the guest stay trapped in Entertainment / section cards.
     */
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
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_HOME -> {
                    if (isKioskModeEnabled || resolveKioskEnabled()) {
                        // Force navigation back to Main Root Home View instead of letting OS drop it
                        navigateToRootHome()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    if ((isKioskModeEnabled || resolveKioskEnabled()) && isSubViewActive()) {
                        navigateToRootHome()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** In-app Root Home switch for Lock Task key capture (no OTT cover flash). */
    private fun navigateToRootHome() {
        markUserActive(dismissScreensaver = true)
        Log.i(TAG, "navigateToRootHome — HOME/BACK under Lock Task → Root Home")
        navigateToRootHomeScreen(showCover = false)
    }

    /** True when Compose is on Entertainment / Dining / Services / etc. (not guest Home). */
    private fun isSubViewActive(): Boolean = !KioskPolicy.isOnGuestHomeScreen(this)

    /**
     * When kiosk is OFF, HOME releases Lock Task and backgrounds this app
     * ([KioskPolicy.launchSystemDefaultLauncher]) — never starts GTPL intents.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME && !resolveKioskEnabled()) {
            Log.i(TAG, "onKeyDown HOME — kiosk OFF → disableKioskMode + moveTaskToBack")
            KioskPolicy.disableKioskMode(
                activity = this,
                source = KioskPolicy.KioskSource.SYSTEM_DEFAULT,
                persistFlag = false,
            )
            KioskPolicy.launchSystemDefaultLauncher(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Focus-loss reclaim is **API-gated** (API 31+ path must stay untouched):
     * - API 29–30: reclaim on focus loss (HOME slip fix).
     * - API &lt; 29: debounced legacy reclaim.
     * - API 31+: ignore transient focus loss while still resumed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus) {
            if (!KioskPolicy.isKioskModeEnabled(this)) return
            if (KioskPolicy.isExternalAppActive(this)) {
                Log.d(TAG, "onWindowFocusChanged — OTT session, skip reclaim")
                return
            }

            // ——— API 29 & 30 only: reclaim on focus loss (do not skip while "resumed") ———
            if (Build.VERSION.SDK_INT in 29..30) {
                Log.d(TAG, "onWindowFocusChanged — API 29/30 focus lost, reclaim")
                try {
                    @Suppress("DEPRECATION")
                    sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_CLOSE_SYSTEM_DIALOGS failed", e)
                }
                isAppInForeground = false
                KioskPolicy.setMainActivityForeground(this, false)
                KioskPolicy.forceBringToFront(this)
                try {
                    startLockTask()
                } catch (e: Exception) {
                    Log.w(TAG, "API 29/30 startLockTask re-pin failed", e)
                }
                return
            }

            // ——— API < 29 (Android 9): debounced legacy reclaim ———
            if (Build.VERSION.SDK_INT < 29) {
                try {
                    @Suppress("DEPRECATION")
                    sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_CLOSE_SYSTEM_DIALOGS failed", e)
                }
                if (tryLegacyKioskReclaim("onWindowFocusChanged")) {
                    try {
                        startLockTask()
                    } catch (e: Exception) {
                        Log.w(TAG, "API<29 startLockTask re-pin failed", e)
                    }
                }
                return
            }

            // ——— API 31+: UNTOUCHED ———
            if (isAppInForeground ||
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            ) {
                Log.d(TAG, "onWindowFocusChanged — API31+ transient focus loss, skip reclaim")
                return
            }

            Log.d(TAG, "onWindowFocusChanged — API31+ focus lost under kiosk, reclaim now")
            try {
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_CLOSE_SYSTEM_DIALOGS failed", e)
            }
            bringAppToFront()
            return
        }

        // Focus regained — re-sync Lock Task on all APIs while kiosk is ON.
        if (!resolveKioskEnabled()) return
        applyLockTaskFromPersistedState("onWindowFocusChanged")
    }

    override fun onResume() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        super.onResume()
        reclaimInFlight = false
        isAppInForeground = true
        KioskPolicy.setMainActivityForeground(this, true)
        if (!screensaverVisible) {
            lastInteractionAt = System.currentTimeMillis()
        }

        // UI should already be Root Home (switched before OTT launch). Cleanup only.
        // Do NOT call bringAppToFront() here — already active.
        if (pendingReturnToHome ||
            intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false) == true
        ) {
            finishReturnFromExternalApp()
        } else if (KioskPolicy.isExternalAppActive(this)) {
            // Back from YouTube / OTT without HOME extra — resume Watchdog.
            Log.i(TAG, "onResume — clearing isExternalAppActive (returned from OTT)")
            KioskPolicy.clearExternalAppActive(this)
            KioskPolicy.clearOttLaunchState(this)
        }

        if (resolveKioskEnabled()) {
            ensureDeviceOwnerLockTask("onResume")
            // Safe here: Activity is foreground — Android 12+/16 allow FGS starts.
            try {
                KioskWatchdogService.start(this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start KioskWatchdogService", e)
            }
            // Android 10 BAL exemption: overlay permission must be granted.
            ensureOverlayPermissionForBal()
            // Android 9/10 only: attach overlay barrier against HOME launcher flash.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                setupLegacyOverlayBarrier()
            }
        } else {
            removeLegacyOverlayBarrier()
        }
    }

    override fun onPause() {
        isAppInForeground = false
        KioskPolicy.setMainActivityForeground(this, false)
        super.onPause()

        val kioskOn = KioskPolicy.isKioskModeEnabled(this)
        if (!kioskOn) return

        // API 29–30: ActivityOptions reclaim (no legacy debounce).
        if (Build.VERSION.SDK_INT in 29..30) {
            if (KioskPolicy.isExternalAppActive(this)) return
            Log.d(TAG, "onPause — API 29/30 kiosk reclaim")
            KioskPolicy.forceBringToFront(this)
            return
        }

        // API < 29: debounced reclaim — prevents onPause↔forceBringToFront storm.
        if (Build.VERSION.SDK_INT < 29) {
            tryLegacyKioskReclaim("onPause")
            return
        }

        // API 31+: UNTOUCHED existing reclaim path.
        bringAppToFront()
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
        KioskPolicy.setMainActivityForeground(this, true)
        if (resolveKioskEnabled()) {
            KioskPolicy.clearUserMinimized(this)
        }
    }

    override fun onStop() {
        isAppInForeground = false
        KioskPolicy.setMainActivityForeground(this, false)
        super.onStop()

        // API 31+: UNTOUCHED — no onStop reclaim.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return

        // API 29–30: post reclaim after Home task-switch.
        if (Build.VERSION.SDK_INT in 29..30) {
            if (KioskPolicy.isExternalAppActive(this)) return
            Log.d(TAG, "onStop — API 29/30 kiosk reclaim (post)")
            window.decorView.post {
                if (!KioskPolicy.isKioskModeEnabled(this@MainActivity)) return@post
                if (KioskPolicy.isExternalAppActive(this@MainActivity)) return@post
                KioskPolicy.forceBringToFront(this@MainActivity)
            }
            return
        }

        // API < 29: post + debounced reclaim.
        Log.d(TAG, "onStop — API<29 kiosk reclaim (post+PendingIntent)")
        window.decorView.post {
            tryLegacyKioskReclaim("onStop")
        }
    }

    /**
     * Receives reclaim intents from [onUserLeaveHint] (SINGLE_TOP / REORDER_TO_FRONT)
     * and HOME-category launches. Kiosk OFF lets the system home path proceed;
     * kiosk ON finishes any OTT return and re-applies Lock Task.
     */
    override fun onNewIntent(intent: Intent) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        super.onNewIntent(intent)
        setIntent(intent)

        val isKioskActive = KioskPolicy.isKioskModeEnabled(this)
        isKioskModeEnabled = isKioskActive
        currentKioskState = isKioskActive

        val isHomeIntent = intent.categories?.contains(Intent.CATEGORY_HOME) == true ||
            (intent.action == Intent.ACTION_MAIN &&
                intent.hasCategory(Intent.CATEGORY_HOME))

        // Kiosk OFF + HOME → unpin, clear DPM whitelist, and background.
        if (!isKioskActive && isHomeIntent) {
            Log.i(TAG, "onNewIntent HOME while kiosk OFF — disableKioskMode + moveTaskToBack")
            KioskPolicy.disableKioskMode(
                activity = this,
                source = KioskPolicy.KioskSource.SYSTEM_DEFAULT,
                persistFlag = false,
            )
            KioskPolicy.launchSystemDefaultLauncher(this)
            return
        }

        val wantsRootHome = intent.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false) ||
            isHomeIntent ||
            (intent.action == Intent.ACTION_MAIN && pendingReturnToHome)

        if (!wantsRootHome && !pendingReturnToHome) return

        if (!isKioskActive &&
            intent.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false) != true &&
            !pendingReturnToHome
        ) {
            Log.d(TAG, "onNewIntent ignored — kiosk disabled")
            return
        }

        Log.i(TAG, "onNewIntent → finishReturnFromExternalApp")
        finishReturnFromExternalApp()
        if (isKioskActive) {
            ensureDeviceOwnerLockTask("onNewIntent")
        }
    }

    fun isOnRootHomeScreen(): Boolean = KioskPolicy.isOnRootHomeScreen(this)

    /**
     * Synchronous Root Home switch **before** YouTube/OTT [startActivity].
     * No timers / postDelayed — Compose nav signal + prefs only (no focus steal later).
     */
    fun switchToRootHomeBeforeOttLaunch() {
        pendingReturnToHome = true
        screensaverVisible = false
        navigateHomeSignal = System.currentTimeMillis()
        KioskPolicy.setOnGuestHomeScreen(this, true)
        Log.i(TAG, "switchToRootHomeBeforeOttLaunch → signal=$navigateHomeSignal")
    }

    /** @deprecated Prefer [switchToRootHomeBeforeOttLaunch]. */
    fun markPendingReturnToHome() {
        pendingReturnToHome = true
    }

    /**
     * Fallback Root Home switch (cold start / HOME when not already Home).
     */
    fun navigateToRootHomeScreen(showCover: Boolean = true) {
        pendingReturnToHome = false
        KioskPolicy.clearOttLaunchState(this)
        KioskPolicy.clearUserMinimized(this)
        screensaverVisible = false
        intent?.removeExtra(EXTRA_NAVIGATE_TO_HOME)
        intent?.removeExtra(EXTRA_SOFT_HOME_RESET)

        if (KioskPolicy.isOnGuestHomeScreen(this)) {
            Log.i(TAG, "navigateToRootHomeScreen — already on guest Home, cleanup only")
            ottTransitionCover = false
            return
        }

        if (showCover) {
            ottTransitionCover = true
        }

        navigateHomeSignal = System.currentTimeMillis()
        KioskPolicy.setOnGuestHomeScreen(this, true)
        Log.i(TAG, "navigateToRootHomeScreen → signal=$navigateHomeSignal")

        window.decorView.post {
            window.decorView.post {
                ottTransitionCover = false
            }
        }
    }

    /**
     * On return from YouTube/HOME: zero animation.
     * Clears isExternalAppActive so Watchdog resume reclaim; if Root Home was applied
     * before launch, do not re-navigate (no flicker).
     */
    private fun finishReturnFromExternalApp() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val alreadyHome = KioskPolicy.isOnGuestHomeScreen(this)
        pendingReturnToHome = false
        // Resume Watchdog — guest is back in hotel UI.
        KioskPolicy.clearExternalAppActive(this)
        KioskPolicy.clearOttLaunchState(this)
        KioskPolicy.clearUserMinimized(this)
        screensaverVisible = false
        intent?.removeExtra(EXTRA_NAVIGATE_TO_HOME)
        intent?.removeExtra(EXTRA_SOFT_HOME_RESET)
        ottTransitionCover = false

        if (alreadyHome) {
            Log.i(TAG, "Return from OTT — already on Root Home, no view switch")
            return
        }

        Log.w(TAG, "Return from OTT — Root Home not ready, fallback switch")
        navigateToRootHomeScreen(showCover = true)
    }

    private fun handleNavigateToHomeExtra(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false) != true) {
            return false
        }
        pendingReturnToHome = true
        navigateToRootHomeScreen(showCover = true)
        return true
    }

    /**
     * Instant kiosk reclaim.
     * API 29–30 → [KioskPolicy.forceBringToFront] (ActivityOptions).
     * API &lt; 29 → [tryLegacyKioskReclaim].
     * API 31+ → PendingIntent path inside [KioskPolicy.forceBringToFront] (untouched).
     */
    private fun bringAppToFront() {
        val isKioskActive = KioskPolicy.isKioskModeEnabled(this)
        isKioskModeEnabled = isKioskActive
        currentKioskState = isKioskActive
        if (!isKioskActive) return

        if (KioskPolicy.isExternalAppActive(this)) {
            Log.d(TAG, "KioskInterceptor — OTT/IPTV session, skip reclaim")
            return
        }

        if (Build.VERSION.SDK_INT in 29..30) {
            Log.d(TAG, "KioskInterceptor — API 29/30 reclaim")
            isAppInForeground = false
            KioskPolicy.setMainActivityForeground(this, false)
            KioskPolicy.forceBringToFront(this)
            return
        }

        if (Build.VERSION.SDK_INT < 29) {
            tryLegacyKioskReclaim("bringAppToFront")
            return
        }

        // API 31+
        Log.d(TAG, "KioskInterceptor — forcing MainActivity to front (PendingIntent)")
        isAppInForeground = false
        KioskPolicy.setMainActivityForeground(this, false)
        KioskPolicy.forceBringToFront(this)
    }

    /**
     * API &lt; 29 only: reclaim with 500ms debounce + in-flight guard.
     * API 29–30 bypasses this (see [KioskPolicy.forceBringToFront]).
     * API 31+ never enters here.
     */
    private fun tryLegacyKioskReclaim(reason: String): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return false
        if (!KioskPolicy.isKioskModeEnabled(this)) return false
        if (KioskPolicy.isExternalAppActive(this)) {
            Log.d(TAG, "KioskInterceptor — OTT session, skip reclaim ($reason)")
            return false
        }
        if (reclaimInFlight) {
            Log.d(TAG, "KioskInterceptor — reclaim in-flight, skip ($reason)")
            return false
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            reason != "onUserLeaveHint"
        ) {
            Log.d(TAG, "KioskInterceptor — already RESUMED, skip reclaim ($reason)")
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastReclaimTimeMs <= RECLAIM_DEBOUNCE_MS) {
            Log.d(TAG, "KioskInterceptor — reclaim debounced ($reason)")
            return false
        }
        lastReclaimTimeMs = now
        reclaimInFlight = true
        isAppInForeground = false
        KioskPolicy.setMainActivityForeground(this, false)
        Log.d(TAG, "KioskInterceptor — legacy reclaim ($reason)")
        val ok = KioskPolicy.forceBringToFront(this)
        if (!ok) {
            reclaimInFlight = false
        }
        return ok
    }

    /**
     * Dynamic HOME interceptor — no system-launcher disable required.
     * API 29–30 / API 31+ / API &lt; 29 are branched; API 31+ PendingIntent path unchanged.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (!KioskPolicy.isKioskModeEnabled(this)) {
            KioskPolicy.markUserMinimized(this)
            Log.d(TAG, "KioskInterceptor — kiosk OFF, allow default HOME")
            return
        }

        if (KioskPolicy.isExternalAppActive(this)) {
            Log.d(TAG, "KioskInterceptor — OTT/IPTV session, skip onUserLeaveHint reclaim")
            return
        }

        isAppInForeground = false
        KioskPolicy.setMainActivityForeground(this, false)

        if (Build.VERSION.SDK_INT in 29..30) {
            Log.d(TAG, "KioskInterceptor — API 29/30 onUserLeaveHint reclaim")
            KioskPolicy.forceBringToFront(this)
            try {
                startLockTask()
            } catch (e: Exception) {
                Log.w(TAG, "API 29/30 onUserLeaveHint startLockTask failed", e)
            }
            return
        }

        if (Build.VERSION.SDK_INT < 29) {
            if (tryLegacyKioskReclaim("onUserLeaveHint")) {
                try {
                    startLockTask()
                } catch (e: Exception) {
                    Log.w(TAG, "API<29 onUserLeaveHint startLockTask failed", e)
                }
            }
            return
        }

        // API 31+
        Log.d(TAG, "KioskInterceptor — onUserLeaveHint immediate reclaim")
        KioskPolicy.forceBringToFront(this)
    }

    override fun onDestroy() {
        removeLegacyOverlayBarrier()
        detachRoomSessionRealtimeListener()
        detachKioskConfigRealtimeListener()

        syncListeners.forEach { registration ->
            registration.remove()
        }
        syncListeners.clear()
        Log.d(TAG, "MainActivity sync listeners removed")
        super.onDestroy()
    }

    /**
     * Android 9/10 only: tiny SYSTEM_ALERT_WINDOW overlay to reduce HOME launcher flash.
     * No-ops without [Settings.canDrawOverlays] or on API 30+.
     */
    private fun setupLegacyOverlayBarrier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        if (kioskOverlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "setupLegacyOverlayBarrier — overlay permission missing, skip")
            return
        }

        try {
            val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            }
            val layoutParams = android.view.WindowManager.LayoutParams(
                1,
                1,
                type,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSPARENT,
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }

            val view = android.view.View(this)
            windowManager.addView(view, layoutParams)
            kioskOverlayView = view
            Log.i(TAG, "Legacy overlay barrier attached (API ${Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            Log.w(TAG, "setupLegacyOverlayBarrier failed", e)
            kioskOverlayView = null
        }
    }

    /** Removes [kioskOverlayView] if present — safe to call from any lifecycle state. */
    private fun removeLegacyOverlayBarrier() {
        val view = kioskOverlayView ?: return
        try {
            val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
            windowManager.removeView(view)
            Log.d(TAG, "Legacy overlay barrier removed")
        } catch (e: Exception) {
            Log.w(TAG, "removeLegacyOverlayBarrier failed", e)
        } finally {
            kioskOverlayView = null
        }
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
        /** API &lt; 30: min gap between reclaim attempts (breaks pause/resume storms). */
        private const val RECLAIM_DEBOUNCE_MS = 500L
        /** Must match admin-panel RTDB region (google-services.json may still list us-central). */
        private const val RTDB_URL =
            "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app"
        /** 10 minutes of no remote / touch input before the screen saver appears. */
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L

        /**
         * Extra from [HomeKeyInterceptorService] / Home reclaim intents.
         * When true, Compose navigation resets to the primary guest Home screen.
         */
        const val EXTRA_NAVIGATE_TO_HOME = "NAVIGATE_TO_HOME"

        /**
         * Soft HOME reset while MainActivity is already foreground — avoid CLEAR_TOP
         * / Lock Task re-assert that causes a blue window flash.
         */
        const val EXTRA_SOFT_HOME_RESET = "SOFT_HOME_RESET"
    }
}
