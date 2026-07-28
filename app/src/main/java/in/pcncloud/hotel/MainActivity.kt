package `in`.pcncloud.hotel

import android.app.admin.DevicePolicyManager
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
import `in`.pcncloud.hotel.kiosk.HomeKeyInterceptorService
import `in`.pcncloud.hotel.kiosk.HotelSessionManager
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
            try {
                stopLockTask()
                Log.d("KioskMode", "Lock Task Mode DISABLED ($reason)")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.w(TAG, "stopLockTask failed ($reason)", e)
            }
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
        super.onCreate(savedInstanceState)

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
                        persistKioskState(isKioskEnabled)
                        applyKeepScreenOn(isKioskEnabled)
                        if (isKioskEnabled) {
                            ensureDeviceOwnerLockTask("rtdb")
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
     */
    private fun applyLockTaskPackages(allowedPackagesList: List<String>) {
        val hotelId = currentHotelIdOrNull()
        KioskPolicy.setAllowedPackagesList(this, allowedPackagesList, hotelId)
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
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        super.onResume()
        KioskPolicy.setMainActivityForeground(this, true)
        if (!screensaverVisible) {
            lastInteractionAt = System.currentTimeMillis()
        }

        // UI should already be Root Home (switched before OTT launch). Cleanup only.
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
        }
    }

    override fun onPause() {
        KioskPolicy.setMainActivityForeground(this, false)
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        if (resolveKioskEnabled()) {
            KioskPolicy.clearUserMinimized(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        super.onNewIntent(intent)
        setIntent(intent)

        val wantsRootHome = intent.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false) ||
            intent.categories?.contains(Intent.CATEGORY_HOME) == true ||
            (intent.action == Intent.ACTION_MAIN && pendingReturnToHome)

        if (!wantsRootHome && !pendingReturnToHome) return

        if (!resolveKioskEnabled() &&
            intent.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false) != true &&
            !pendingReturnToHome
        ) {
            Log.d(TAG, "onNewIntent ignored — kiosk disabled")
            return
        }

        Log.i(TAG, "onNewIntent → finishReturnFromExternalApp")
        finishReturnFromExternalApp()
        if (resolveKioskEnabled()) {
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
     * Home-key reclaim fallback — especially important before Device Owner is provisioned
     * (screen pinning alone cannot fully suppress Home on Android TV).
     * When kiosk is OFF, allow normal minimize.
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

        if (KioskPolicy.isExternalAppActive(this)) {
            Log.d(TAG, "onUserLeaveHint — OTT/IPTV session under kiosk, skip reclaim")
            return
        }

        // Already on Root Home in foreground — do not re-fire Intent (blue flash).
        if (isOnRootHomeScreen()) {
            Log.d(TAG, "onUserLeaveHint — already on Root Home, skip reclaim")
            return
        }

        // Device Owner true Lock Task should already block Home; reclaim covers
        // non–Device Owner pinning and any OEM Home leaks.
        val isOwner = MyDeviceAdminReceiver.isDeviceOwner(this)
        Log.d(
            TAG,
            "onUserLeaveHint — reclaim MainActivity (deviceOwner=$isOwner)",
        )
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                putExtra(EXTRA_NAVIGATE_TO_HOME, true)
            }
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring MainActivity to front after leave/Home", e)
        }
    }

    override fun onDestroy() {
        detachRoomSessionRealtimeListener()
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
