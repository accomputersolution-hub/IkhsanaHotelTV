package `in`.pcncloud.hotel

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
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
import `in`.pcncloud.hotel.config.IntroVideoCache
import `in`.pcncloud.hotel.data.FirestorePaths
import `in`.pcncloud.hotel.data.model.HotelBranding
import `in`.pcncloud.hotel.data.repository.FirestoreRepository
import `in`.pcncloud.hotel.kiosk.BlockedKeysManager
import `in`.pcncloud.hotel.kiosk.HotelSessionManager
import `in`.pcncloud.hotel.kiosk.KioskLockTask
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.kiosk.KioskWatchdogService
import `in`.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.ScreensaverOverlay
import `in`.pcncloud.hotel.ui.components.ServiceSuspendedScreen
import `in`.pcncloud.hotel.ui.navigation.HotelNavGraph
import `in`.pcncloud.hotel.ui.theme.PcnCloudTvTheme
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
 * stock Android TV launcher.
 *
 * When Device Owner is rejected on a physical TV, [activatePhysicalTvFallback]
 * switches smoothly to Screen Pinning ([startLockTask]) + optional full-screen
 * overlay — never crashes on missing admin authorization.
 */
class MainActivity : ComponentActivity() {

    /**
     * When set, physical Back is handled by a nested Admin sub-screen
     * (e.g. Key Blocker → Staff Settings) instead of forcing Guest Home.
     * Return true if the nested handler consumed Back.
     */
    @Volatile
    var nestedAdminBackHandler: (() -> Boolean)? = null

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
     * Observed by [HotelNavGraph] to hide sub-screen overlays instantly.
     */
    private var navigateHomeSignal by mutableLongStateOf(0L)

    /**
     * Registered by [HotelNavGraph] — same action as the Top-Left Home / back control
     * (hide overlay → reveal retained Home). Prefer this over [navigateHomeSignal] for
     * remote BACK so there is no LaunchedEffect frame delay.
     */
    @Volatile
    private var homeViewNavigator: (() -> Unit)? = null

    /** True while Dining / Entertainment / Services / Alerts / Admin overlay is showing. */
    @Volatile
    private var subMenuVisible: Boolean = false

    fun registerHomeViewNavigator(action: (() -> Unit)?) {
        homeViewNavigator = action
    }

    /** Called from [HotelNavGraph] when a sub-menu overlay is shown or hidden. */
    fun setSubMenuVisible(visible: Boolean) {
        subMenuVisible = visible
        KioskPolicy.setOnGuestHomeScreen(this, !visible)
    }

    /**
     * Instant in-app return to Home — identical to the sub-screen Home / back control.
     * Does not call [onBackPressed] / Activity back-stack finish.
     */
    fun navigateToHomeView() {
        markUserActive(dismissScreensaver = true)
        screensaverVisible = false
        ottTransitionCover = false
        val navigator = homeViewNavigator
        if (navigator != null) {
            Log.i(TAG, "navigateToHomeView — instant overlay hide (Home icon path)")
            navigator.invoke()
            subMenuVisible = false
            return
        }
        Log.w(TAG, "navigateToHomeView — navigator not registered, signal fallback")
        subMenuVisible = false
        navigateHomeSignal = System.currentTimeMillis()
        KioskPolicy.setOnGuestHomeScreen(this, true)
    }

    /**
     * Remote / system Back while kiosk is active:
     * - Nested Admin sub-screen (Key Blocker) → pop to Staff Settings only.
     * - Sub-menu open → return to Home (hide overlay).
     * - Already on Home → consume; never exit / minimize.
     */
    private fun handleKioskBackPressed() {
        val nested = nestedAdminBackHandler
        if (nested != null) {
            try {
                if (nested.invoke()) {
                    Log.i(TAG, "Back — nested admin handler consumed (stay in Staff Settings)")
                    markUserActive(dismissScreensaver = true)
                    return
                }
            } catch (t: Throwable) {
                Log.e(TAG, "nestedAdminBackHandler failed", t)
            }
        }
        if (isSubViewActive()) {
            Log.i(TAG, "Back — sub-menu open → navigateToHomeView")
            navigateToHomeView()
        } else {
            markUserActive(dismissScreensaver = true)
            Log.d(TAG, "Back — already on Home, blocked (kiosk)")
        }
    }

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

    /** True while onNewIntent / onResume is handling a reclaim — blocks onPause loops. */
    private var handlingReclaimLifecycle: Boolean = false

    /** Main-thread handler — Physical TV reclaim retries must run while paused (not decorView). */
    private val mainHandler = Handler(Looper.getMainLooper())

    private val clearHandlingReclaimRunnable = Runnable {
        handlingReclaimLifecycle = false
    }

    private val physicalTvReclaimRetryRunnable = Runnable {
        if (isActivitySurfaceGone()) return@Runnable
        if (!KioskPolicy.isKioskModeEnabled(this)) return@Runnable
        if (KioskPolicy.isExternalAppActive(this)) return@Runnable
        if (KioskPolicy.isMainActivityForeground(this) &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        ) {
            Log.d(TAG, "physicalTvReclaimRetry — already resumed, skip")
            return@Runnable
        }
        Log.i(TAG, "physicalTvReclaimRetry — forceBringToFront skipDebounce")
        reclaimPhysicalTvImmediate("retry")
    }

    /** Instant Admin / RTDB kiosk toggle — cancel reclaim retries & sync memory. */
    private val kioskModeChangedListener: (Boolean) -> Unit = { enabled ->
        runOnUiThread {
            applyKioskModeChangedLocally(enabled, "KioskPolicy.listener")
        }
    }

    /**
     * API &lt; 30 Device Owner path: tiny 1×1 overlay for process privilege.
     * Non–Device Owner physical TV: full-screen transparent overlay (any API)
     * when [Settings.canDrawOverlays] is granted.
     */
    private var kioskOverlayView: android.view.View? = null

    /** True when the attached overlay is the physical-TV full-screen fallback. */
    private var physicalTvOverlayActive: Boolean = false

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
        // setKioskModeEnabled notifies [kioskModeChangedListener] → applyKioskModeChangedLocally.
        KioskPolicy.setKioskModeEnabled(
            context = this,
            enabled = enabled,
            source = KioskPolicy.KioskSource.REALTIME_DATABASE,
        )
        Log.i(TAG, "persistKioskState → $enabled")
    }

    /**
     * Instant local sync when Admin Panel or RTDB toggles kiosk — no reboot needed.
     * OFF: cancel pending reclaim retries, drop overlay, stop Watchdog pulls.
     * ON: re-apply Lock Task / physical TV fallback.
     */
    fun applyKioskModeChangedLocally(enabled: Boolean, reason: String) {
        currentKioskState = enabled
        isKioskModeEnabled = enabled
        applyKeepScreenOn(enabled)

        if (!enabled) {
            mainHandler.removeCallbacks(physicalTvReclaimRetryRunnable)
            mainHandler.removeCallbacks(clearHandlingReclaimRunnable)
            handlingReclaimLifecycle = false
            reclaimInFlight = false
            removeKioskOverlayBarrier()
            KioskPolicy.clearReclaimSuppression("kiosk_off")
            Log.i(TAG, "Kiosk OFF ($reason) — interceptors/reclaim retries stopped")
            return
        }

        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            ensureDeviceOwnerLockTask(reason)
            startLockTaskSafely(reason)
            if (KioskPolicy.needsPhysicalTvFallback(this)) {
                setupPhysicalTvFallbackOverlay()
            }
        }
        Log.i(TAG, "Kiosk ON ($reason) — Lock Task / Physical TV fallback armed")
        // Prompt OS Home picker when this app is not yet the default launcher.
        verifyAndRequestDefaultHomeLauncher()
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
            removeKioskOverlayBarrier()
            Log.d("KioskMode", "Lock Task Mode DISABLED ($reason)")
        }
    }

    /**
     * Device Owner path: whitelist packages + true Lock Task (Home suppressed).
     * Physical TV / Device Owner rejected: Screen Pinning + Overlay fallback
     * via [activatePhysicalTvFallback] — never crash on missing admin component.
     */
    private fun ensureDeviceOwnerLockTask(reason: String) {
        if (!resolveKioskEnabled()) return

        if (KioskPolicy.isDeviceOwner(this)) {
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = MyDeviceAdminReceiver.getComponentName(this)
                val hotelId = currentHotelIdOrNull()
                val allowed = (
                    lastAppliedAllowedPackages
                        ?: KioskPolicy.getAllowedPackagesList(this, hotelId)
                    )
                val packages = KioskLockTask.buildLockTaskPackageArray(this, allowed)

                dpm.setLockTaskPackages(adminComponent, packages)
                MyDeviceAdminReceiver.applyStrictLockTaskFeatures(this)
                startLockTaskSafely(reason)
                // Device Owner does not need the full-screen physical-TV overlay.
                if (physicalTvOverlayActive) {
                    removeKioskOverlayBarrier()
                }
                Log.i(
                    TAG,
                    "Device Owner Lock Task ($reason) — Home suppressed, " +
                        "packages=${packages.toList()}",
                )
                Log.d("KioskMode", "Lock Task Mode ENABLED ($reason / device-owner)")
            } catch (e: SecurityException) {
                Log.w(TAG, "Device Owner APIs unauthorized ($reason) — physical TV fallback", e)
                activatePhysicalTvFallback(reason)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Device Owner Lock Task rejected ($reason) — physical TV fallback", e)
                activatePhysicalTvFallback(reason)
            } catch (e: Exception) {
                Log.w(TAG, "Device Owner Lock Task failed ($reason) — physical TV fallback", e)
                activatePhysicalTvFallback(reason)
            }
        } else {
            activatePhysicalTvFallback(reason)
        }
    }

    /**
     * Physical TV fallback when [KioskPolicy.isDeviceOwner] is false
     * (e.g. `adb shell dpm set-device-owner` rejected / accounts present):
     * 1. Overlay immediately (safe anytime)
     * 2. [startLockTask] only when RESUMED — see [startLockTaskSafely]
     * Never throws admin / authorization exceptions to the guest UI.
     */
    private fun activatePhysicalTvFallback(reason: String) {
        Log.w(
            TAG,
            "Physical TV fallback ($reason) — Screen Pinning + Overlay " +
                "(not Device Owner; set-device-owner was rejected or unavailable)",
        )
        setupPhysicalTvFallbackOverlay()
        // Pin only while foreground/RESUMED — never from leave/pause/focus-loss.
        startLockTaskSafely(reason)
        Log.d("KioskMode", "Lock Task Mode ENABLED ($reason / pinning+overlay fallback)")
    }

    /**
     * True when the Activity window/surface must not start another reclaim
     * (EGL / BufferQueue safety). Prefer this over quiet-period locks.
     */
    private fun isActivitySurfaceGone(): Boolean {
        if (isFinishing) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed) {
            return true
        }
        return false
    }

    /**
     * Physical TV Home reclaim — immediate [KioskPolicy.forceBringToFront] with
     * skipDebounce. Never finishes / recreates the Activity (surface stays alive).
     */
    private fun reclaimPhysicalTvImmediate(reason: String): Boolean {
        if (isActivitySurfaceGone()) {
            Log.d(TAG, "reclaimPhysicalTvImmediate skip — finishing/destroyed ($reason)")
            return false
        }
        if (!KioskPolicy.isKioskModeEnabled(this)) return false
        if (KioskPolicy.isExternalAppActive(this)) return false

        Log.i(TAG, "reclaimPhysicalTvImmediate ($reason) — forceBringToFront skipDebounce")
        return KioskPolicy.forceBringToFrontPhysicalTvUrgent(
            context = this,
            navigateToHome = true,
        )
    }

    /**
     * Schedule one follow-up reclaim on the **main Handler** (not decorView) so
     * the callback still runs while the Activity is paused/stopped.
     */
    private fun schedulePhysicalTvReclaimRetry(reason: String) {
        val elapsed = KioskPolicy.millisSinceLastForceBring()
        val delayMs = (KioskPolicy.loopGuardMs(this) - elapsed).coerceAtLeast(0L) + 16L
        Log.d(TAG, "schedulePhysicalTvReclaimRetry ($reason) in ${delayMs}ms")
        mainHandler.removeCallbacks(physicalTvReclaimRetryRunnable)
        mainHandler.postDelayed(physicalTvReclaimRetryRunnable, delayMs)
    }

    /** Pulse local + policy busy for ≤50ms so nested pause cannot stall Home reclaim. */
    private fun markReclaimLifecycleBusyBriefly() {
        handlingReclaimLifecycle = true
        KioskPolicy.setReclaimLifecycleBusy(true)
        mainHandler.removeCallbacks(clearHandlingReclaimRunnable)
        mainHandler.postDelayed(clearHandlingReclaimRunnable, 50L)
    }

    /**
     * Best-effort [startLockTask] — **only** while Activity is RESUMED / foreground.
     * Never call from [onPause], [onStop], [onUserLeaveHint], or focus-loss;
     * those throw `IllegalArgumentException: Invalid task, not in foreground`
     * and let the stock launcher flash for 1–2s.
     *
     * Skips when Lock Task / Screen Pinning is already active to avoid API 28 freezes
     * from repeated startLockTask in onNewIntent / onResume.
     */
    private fun startLockTaskSafely(reason: String = "kiosk") {
        if (!resolveKioskEnabled() && !isKioskModeEnabled) {
            Log.d(TAG, "startLockTaskSafely skip — kiosk off ($reason)")
            return
        }
        if (isActivitySurfaceGone()) {
            Log.d(TAG, "startLockTaskSafely skip — finishing/destroyed ($reason)")
            return
        }
        // Strict: only pin when the task is actually in the foreground.
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            Log.d(
                TAG,
                "startLockTaskSafely skip — not RESUMED " +
                    "(state=${lifecycle.currentState}, reason=$reason)",
            )
            return
        }
        if (isLockTaskAlreadyActive()) {
            Log.d(TAG, "startLockTaskSafely skip — already locked/pinned ($reason)")
            return
        }
        try {
            startLockTask()
            Log.i(TAG, "startLockTask ok ($reason)")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "startLockTask failed safely ($reason): ${e.message}")
        } catch (e: SecurityException) {
            Log.w(TAG, "startLockTask failed safely ($reason): ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "startLockTask failed safely ($reason): ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "startLockTask failed safely ($reason): ${e.message}")
        }
    }

    /** True when Lock Task Mode or Screen Pinning is already holding this task. */
    private fun isLockTaskAlreadyActive(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                ?: return false
            am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        } catch (e: Exception) {
            Log.w(TAG, "isLockTaskAlreadyActive check failed", e)
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Manifest uses Theme.HotelTv.BootSplash (instant black+logo window).
        // Switch to the main theme before inflating so Compose draws under PcnCloudTv.
        setTheme(R.style.Theme_PcnCloudTv)
        // Match Home base color so route pops never flash Material / AppCompat blue.
        window.setBackgroundDrawableResource(R.color.dark_bg_or_transparent)
        super.onCreate(savedInstanceState)

        // LOCKED_BOOT_COMPLETED / Keyguard: show over lock + wake display.
        enableShowOverLockScreen()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        // Instant Admin / RTDB kiosk toggles (no reboot).
        KioskPolicy.addKioskModeChangedListener(kioskModeChangedListener)

        // Seed from last persisted RTDB value (default unlocked until cloud says otherwise).
        isKioskModeEnabled = resolveKioskEnabled()
        applyKeepScreenOn(isKioskModeEnabled)
        applyLockTaskFromPersistedState("onCreate")

        installKioskBackSafetyNet()

        // Cold start may already carry NAVIGATE_TO_HOME from launcher / reclaim.
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

        repository = FirestoreRepository(hotelConfig, IntroVideoCache(applicationContext))
        val viewModelFactory = HotelViewModelFactory(repository, hotelConfig)

        Log.d(TAG, "TV Firestore sync starting → hotelId=$hotelId room=${hotelConfig.roomNumber}")
        Log.d(TAG, "Path Hotels/{hotelId} → ${FirestorePaths.hotelDocument(hotelId)}")
        Log.d(TAG, "Path Hotels/{hotelId}/Rooms → ${FirestorePaths.roomsCollection(hotelId)}")
        Log.d(TAG, "Path guest room → ${FirestorePaths.roomDocument(hotelId, hotelConfig.roomNumber)}")
        Log.d(TAG, "Path menu → ${FirestorePaths.menuCollection(hotelId)}")
        Log.d(TAG, "Path requests → ${FirestorePaths.requestsCollection(hotelId)}")
        Log.d(TAG, "Path alerts → ${FirestorePaths.alertsCollection(hotelId)}")

        // Paint Compose ASAP — NavHost startDestination from IntroVideoCache (sync).
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

            PcnCloudTvTheme {
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
                            repository = repository,
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

        // Diagnostic SnapshotListeners after first Compose frame (cache-first intro/home).
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
    private fun isDefaultHomeLauncher(): Boolean =
        KioskPolicy.isMyAppDefaultLauncher(this)

    /**
     * Diagnostic + prompt: logs Home launcher status and opens system Home settings
     * / chooser when kiosk is ON but this app is not the default Home activity.
     */
    private fun verifyAndRequestDefaultHomeLauncher() {
        val isDefault = KioskPolicy.isMyAppDefaultLauncher(this)
        val kioskOn = resolveKioskEnabled() || KioskPolicy.isKioskModeEnabled(this)
        Log.i(
            TAG,
            "Home launcher diagnostic → package=$packageName isDefault=$isDefault kiosk=$kioskOn",
        )

        if (!kioskOn) {
            Log.d(TAG, "Skip Home launcher prompt — kiosk disabled")
            return
        }

        if (isDefault) {
            KioskPolicy.clearReclaimSuppression("already_default_home")
            Log.i(TAG, "Already default HOME launcher")
            return
        }

        Log.w(TAG, "Not default HOME launcher — opening Home settings / chooser")
        openDefaultHomePicker()
    }

    /**
     * Opens Android's built-in Home picker so staff can set this app as default launcher.
     * Prefers [Settings.ACTION_HOME_SETTINGS]; falls back to [Intent.createChooser].
     * Suppresses kiosk reclaim briefly so Settings/chooser is not stolen back.
     */
    private fun openDefaultHomePicker() {
        KioskPolicy.suppressReclaimFor(
            durationMs = 180_000L,
            reason = "default_home_picker",
        )
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
            Log.i(TAG, "openDefaultHomePicker — ACTION_HOME_SETTINGS opened")
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_HOME_SETTINGS failed — createChooser fallback", e)
            try {
                val selector = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(Intent.createChooser(selector, "Select Default Launcher"))
                Log.i(TAG, "openDefaultHomePicker — HOME createChooser opened")
            } catch (ex: Exception) {
                Log.e(TAG, "Could not open Home settings or chooser", ex)
                KioskPolicy.clearReclaimSuppression("home_picker_failed")
            }
        }
    }

    /**
     * Opens [Settings.ACTION_HOME_SETTINGS] so the user can select this app
     * (`in.pcncloud.hotel`) as the permanent default Home / TV launcher.
     */
    private fun openHomeSettings() {
        openDefaultHomePicker()
    }

    /**
     * Triggers the system Home-app chooser (or brings this launcher forward).
     * Required so Android TV surfaces this app in Home / default-launcher selection.
     */
    fun requestHomeLauncherSelection(context: Context) {
        if (context is MainActivity) {
            context.verifyAndRequestDefaultHomeLauncher()
            return
        }
        try {
            if (KioskPolicy.isMyAppDefaultLauncher(context)) {
                Log.i(TAG, "requestHomeLauncherSelection — already default")
                return
            }
            KioskPolicy.suppressReclaimFor(180_000L, "default_home_picker_external")
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "requestHomeLauncherSelection failed", e)
            try {
                val selector = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(
                    Intent.createChooser(selector, "Select Default Launcher")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (ex: Exception) {
                Log.e(TAG, "requestHomeLauncherSelection chooser failed", ex)
            }
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
     * Never throws into Compose / key dispatch.
     */
    fun canLaunchApp(targetPackageName: String): Boolean {
        return try {
            if (!isKioskModeEnabled) return true
            val allowedPackagesList = lastAppliedAllowedPackages
                ?: KioskPolicy.getAllowedPackagesList(this, currentHotelIdOrNull())
            allowedPackagesList.contains(targetPackageName.trim())
        } catch (t: Throwable) {
            Log.e(TAG, "canLaunchApp failed — denying under kiosk (safe)", t)
            !isKioskModeEnabled
        }
    }

    /**
     * Called when an Entertainment / Live TV launch is refused by whitelist.
     * Silently keeps the guest on MainActivity — no crash, no input-channel break.
     */
    fun onExternalLaunchBlocked(blockedPackage: String) {
        try {
            Log.w(TAG, "onExternalLaunchBlocked → $blockedPackage (silent)")
            KioskPolicy.denyExternalLaunchSilently(this, blockedPackage)
        } catch (t: Throwable) {
            Log.e(TAG, "onExternalLaunchBlocked failed (ignored)", t)
        }
    }

    /**
     * Start / stop Lock Task Mode. Wrapped safely — devices that are not device-owner
     * (or not allowlisted for lock task) throw; we must never crash the guest UI.
     */
    private fun applyLockTaskMode(enabled: Boolean) {
        try {
            if (enabled) {
                MyDeviceAdminReceiver.applyStrictLockTaskFeatures(this)
                startLockTaskSafely("applyLockTaskMode")
                Log.d("KioskMode", "Lock Task Mode ENABLED")
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
     * (e.g. service-suspended screen). Never finishes the Activity under kiosk.
     */
    private fun installKioskBackSafetyNet() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isKioskActive()) {
                        handleKioskBackPressed()
                    } else if (isSubViewActive()) {
                        navigateToHomeView()
                    } else if (!BuildConfig.IS_CORPORATE) {
                        Log.d(TAG, "Hotel flavor @ Home — Back consumed (no exit)")
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
     * Kiosk key gate — Home / Back / Recent first (unchanged), then vendor/unknown
     * remote shortcuts are consumed so they never reach [super] (InputChannel safety).
     * Safe navigation keys (D-pad / Enter / volume) still pass through.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screensaverVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                markUserActive(dismissScreensaver = true)
                Log.d(TAG, "Screen saver dismissed by keyCode=${event.keyCode}")
            }
            return true
        }

        val kioskOn = isKioskModeEnabled || resolveKioskEnabled()
        if (kioskOn) {
            when (event.keyCode) {
                // —— Home only: dedicated reclaim path (do not alter) ——
                KeyEvent.KEYCODE_HOME -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        Log.d(TAG, "Swallowed HOME in Kiosk mode")
                        navigateToRootHome()
                    }
                    return true
                }
                // —— Back only: sub-menu navigation / Home lock ——
                KeyEvent.KEYCODE_BACK -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        Log.d(TAG, "Back in Kiosk mode — handleKioskBackPressed")
                        handleKioskBackPressed()
                    }
                    return true
                }
                // —— Recent / App switch: block under kiosk ——
                KeyEvent.KEYCODE_APP_SWITCH -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        Log.d(TAG, "Swallowed Recent/App Switch in Kiosk mode")
                        markUserActive(dismissScreensaver = true)
                    }
                    return true
                }
            }

            // Vendor / unknown remote shortcuts (YouTube, Hotstar, etc.):
            // consume DOWN+UP — never call super (protects InputDispatcher channel).
            if (shouldConsumeVendorRemoteKey(event)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    try {
                        markUserActive(dismissScreensaver = true)
                    } catch (_: Throwable) {
                    }
                    Log.d(
                        TAG,
                        "Consumed vendor/unknown key keyCode=${event.keyCode} " +
                            "scanCode=${event.scanCode} " +
                            "source=0x${Integer.toHexString(event.source)}",
                    )
                }
                return true
            }
        } else if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_DOWN
        ) {
            if (isSubViewActive()) {
                navigateToHomeView()
                return true
            }
            if (!BuildConfig.IS_CORPORATE) {
                Log.d(TAG, "Hotel flavor @ Home — Back consumed (no exit)")
                return true
            }
        }

        return try {
            super.dispatchKeyEvent(event)
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchKeyEvent super failed keyCode=${event.keyCode}", t)
            true
        }
    }

    /**
     * True for OEM YouTube / Hotstar / Netflix / Apps dedicated buttons and other
     * unhandled vendor KeyEvents that must not reach [super.dispatchKeyEvent].
     * Home / Back / Recent are handled separately and never evaluated here.
     */
    private fun shouldConsumeVendorRemoteKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,
            -> return false
        }

        // Known OEM OTT / Apps shortcut keyCodes.
        when (keyCode) {
            KEYCODE_OEM_APPS,
            KEYCODE_OEM_NETFLIX,
            KEYCODE_OEM_YOUTUBE,
            KEYCODE_OEM_PRIME_VIDEO,
            -> return true
        }

        // Raw vendor scan with no standard Android keyCode mapping.
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN && event.scanCode != 0) return true

        // Known vendor scan codes that some remotes report even when keyCode differs.
        if (isKnownVendorOttScanCode(event.scanCode)) return true

        // Safe TV navigation — allow through to Compose / system volume.
        if (isSafeKioskPassthroughKey(keyCode)) return false

        // Any other key from a remote-class device: consume (never super).
        return isRemoteOrVendorSource(event)
    }

    private fun isKnownVendorOttScanCode(scanCode: Int): Boolean {
        return when (scanCode) {
            KEYCODE_OEM_APPS,
            KEYCODE_OEM_NETFLIX,
            KEYCODE_OEM_YOUTUBE,
            KEYCODE_OEM_PRIME_VIDEO,
            -> true
            else -> false
        }
    }

    private fun isSafeKioskPassthroughKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            -> true
            else -> false
        }
    }

    private fun isRemoteOrVendorSource(event: KeyEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_CLASS_BUTTON) != 0 ||
            (source and InputDevice.SOURCE_DPAD) != 0 ||
            (source and InputDevice.SOURCE_GAMEPAD) != 0 ||
            (source and InputDevice.SOURCE_HDMI) != 0 ||
            (source and InputDevice.SOURCE_JOYSTICK) != 0 ||
            (source and InputDevice.SOURCE_KEYBOARD) != 0
    }

    /** In-app Root Home switch for Lock Task HOME key capture (Home button path only). */
    private fun navigateToRootHome() {
        markUserActive(dismissScreensaver = true)
        Log.i(TAG, "navigateToRootHome — HOME under Lock Task → Root Home")
        navigateToRootHomeScreen(showCover = false)
    }

    /** True when a sub-menu overlay (Dining / Apps / Housekeeping / etc.) is visible. */
    private fun isSubViewActive(): Boolean =
        subMenuVisible || !KioskPolicy.isOnGuestHomeScreen(this)

    /**
     * Secondary gate for Home / Back / Recent / vendor shortcuts.
     * Behavior is owned by [dispatchKeyEvent] — consume here, do not re-navigate.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return try {
            val kioskOn = isKioskModeEnabled || resolveKioskEnabled()
            if (kioskOn) {
                when (keyCode) {
                    KeyEvent.KEYCODE_HOME,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_APP_SWITCH,
                    -> {
                        Log.d(TAG, "onKeyDown swallowed key $keyCode in Kiosk mode")
                        return true
                    }
                }
                if (event != null && shouldConsumeVendorRemoteKey(event)) {
                    Log.d(TAG, "onKeyDown swallowed vendor key $keyCode")
                    return true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_HOME && !kioskOn) {
                Log.i(TAG, "onKeyDown HOME — kiosk OFF → disableKioskMode + moveTaskToBack")
                KioskPolicy.disableKioskMode(
                    activity = this,
                    source = KioskPolicy.KioskSource.SYSTEM_DEFAULT,
                    persistFlag = false,
                )
                KioskPolicy.launchSystemDefaultLauncher(this)
                return true
            }
            try {
                super.onKeyDown(keyCode, event)
            } catch (t: Throwable) {
                Log.e(TAG, "onKeyDown super failed keyCode=$keyCode", t)
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onKeyDown failed keyCode=$keyCode — consuming", t)
            true
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return try {
            val kioskOn = isKioskModeEnabled || resolveKioskEnabled()
            if (kioskOn) {
                when (keyCode) {
                    KeyEvent.KEYCODE_HOME,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_APP_SWITCH,
                    -> {
                        Log.d(TAG, "onKeyUp swallowed key $keyCode in Kiosk mode")
                        return true
                    }
                }
                if (event != null && shouldConsumeVendorRemoteKey(event)) {
                    return true
                }
            }
            try {
                super.onKeyUp(keyCode, event)
            } catch (t: Throwable) {
                Log.e(TAG, "onKeyUp super failed keyCode=$keyCode", t)
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onKeyUp failed keyCode=$keyCode — consuming", t)
            true
        }
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent?): Boolean {
        return try {
            val kioskOn = isKioskModeEnabled || resolveKioskEnabled()
            if (kioskOn) {
                when (keyCode) {
                    KeyEvent.KEYCODE_HOME,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_APP_SWITCH,
                    -> return true
                }
                if (event != null && shouldConsumeVendorRemoteKey(event)) {
                    return true
                }
            }
            try {
                super.onKeyMultiple(keyCode, repeatCount, event)
            } catch (t: Throwable) {
                Log.e(TAG, "onKeyMultiple super failed keyCode=$keyCode", t)
                true
            }
        } catch (t: Throwable) {
            true
        }
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        return try {
            val kioskOn = isKioskModeEnabled || resolveKioskEnabled()
            if (kioskOn && shouldConsumeVendorRemoteKey(event)) {
                return true
            }
            super.dispatchKeyShortcutEvent(event)
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchKeyShortcutEvent super failed", t)
            true
        }
    }

    /**
     * Focus-loss reclaim:
     * - Physical TV (kiosk ON, not Device Owner): immediate bringToFront, **no debounce**.
     * - Device Owner / API-gated paths otherwise (29–30 / &lt;29 / 31+).
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus) {
            if (!KioskPolicy.isKioskModeEnabled(this)) return
            if (KioskPolicy.isExternalAppActive(this)) {
                Log.d(TAG, "onWindowFocusChanged — OTT session, skip reclaim")
                return
            }

            // ——— Physical TV: immediate reclaim (0ms); duplicates skipped in policy (50ms) ———
            if (KioskPolicy.needsPhysicalTvFallback(this)) {
                Log.i(TAG, "onWindowFocusChanged — physical TV IMMEDIATE reclaim")
                try {
                    @Suppress("DEPRECATION")
                    sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_CLOSE_SYSTEM_DIALOGS failed", e)
                }
                isAppInForeground = false
                KioskPolicy.setMainActivityForeground(this, false)
                reclaimPhysicalTvImmediate("onWindowFocusChanged")
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
                KioskPolicy.forceBringToFrontSafely(this)
                // Do NOT startLockTask here — task is not in foreground.
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
                tryLegacyKioskReclaim("onWindowFocusChanged")
                // Do NOT startLockTask here — re-pin in onResume only.
                return
            }

            // ——— API 31+ Device Owner path ———
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

        // Focus regained — re-sync Lock Task / physical TV fallback while kiosk is ON.
        // startLockTaskSafely runs only once RESUMED (via ensureDeviceOwnerLockTask).
        if (!resolveKioskEnabled()) return
        applyLockTaskFromPersistedState("onWindowFocusChanged")
    }

    /**
     * First-frame kiosk snap: Screen Pinning + full-screen overlay on the main thread
     * **before** Compose nav / Firebase / Watchdog work. Masks stock-launcher flash
     * during surface reorder after PendingIntent reclaim.
     */
    private fun snapKioskSurfaceImmediate(reason: String) {
        if (!resolveKioskEnabled() && !KioskPolicy.isKioskModeEnabled(this)) return
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        try {
            window?.setWindowAnimations(0)
        } catch (_: Throwable) {
        }
        // Device Owner whitelist + pin, or Physical TV Screen Pinning path.
        ensureDeviceOwnerLockTask(reason)
        startLockTaskSafely(reason)
        if (KioskPolicy.needsPhysicalTvFallback(this)) {
            setupPhysicalTvFallbackOverlay()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            KioskPolicy.isDeviceOwner(this)
        ) {
            setupLegacyOverlayBarrier()
        }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        Log.i(TAG, "snapKioskSurfaceImmediate ($reason) — Lock Task + overlay armed")
    }

    override fun onResume() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // ≤50ms busy pulse — never hold reclaimLifecycleBusy for seconds.
        markReclaimLifecycleBusyBriefly()
        try {
            super.onResume()
            reclaimInFlight = false
            isAppInForeground = true
            KioskPolicy.setMainActivityForeground(this, true)
            if (!screensaverVisible) {
                lastInteractionAt = System.currentTimeMillis()
            }

            val kioskOn = resolveKioskEnabled()
            // —— Aggressive foreground snap FIRST (before nav / async) ——
            if (kioskOn) {
                snapKioskSurfaceImmediate("onResume")
            } else {
                removeKioskOverlayBarrier()
            }

            // UI should already be Root Home (switched before OTT launch). Cleanup only.
            // Do NOT call bringAppToFront() here — already active.
            if (pendingReturnToHome ||
                intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false) == true
            ) {
                finishReturnFromExternalApp()
            } else if (KioskPolicy.isExternalAppActive(this)) {
                if (KioskPolicy.isOttLaunchGracePeriod(this)) {
                    // Brief resume during Live TV / YouTube handoff — keep flag so Watchdog
                    // does not reclaim mid-launch.
                    Log.d(TAG, "onResume — OTT launch grace; keep isExternalAppActive")
                } else {
                    // Back from YouTube / OTT / Live TV without HOME extra — resume Watchdog.
                    Log.i(TAG, "onResume — clearing isExternalAppActive (returned from OTT)")
                    KioskPolicy.clearExternalAppActive(this)
                    KioskPolicy.clearOttLaunchState(this)
                }
            }

            if (kioskOn) {
                // Staff returned from Home picker after selecting this app as default.
                if (KioskPolicy.isMyAppDefaultLauncher(this)) {
                    KioskPolicy.clearReclaimSuppression("default_home_confirmed")
                }
                // Secondary keep-alive — after pin/overlay already covering the frame.
                try {
                    KioskWatchdogService.start(this)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start KioskWatchdogService", e)
                }
                // Android 10 BAL exemption: overlay permission must be granted.
                ensureOverlayPermissionForBal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume error", e)
        }
    }

    override fun onPause() {
        // Kill leave transition so stock launcher never paints a mid-frame flash.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        isAppInForeground = false
        KioskPolicy.setMainActivityForeground(this, false)
        super.onPause()

        val kioskOn = KioskPolicy.isKioskModeEnabled(this)
        if (!kioskOn) return
        if (KioskPolicy.isExternalAppActive(this)) return

        // Physical TV: NEVER reclaim from onPause — it fires mid-resume of our own
        // reclaim Intent and creates onPause ↔ onNewIntent storms (ActivityManager
        // throttle + stock launcher flash). Explicit leave → onUserLeaveHint only.
        if (KioskPolicy.needsPhysicalTvFallback(this) || !KioskPolicy.isDeviceOwner(this)) {
            Log.d(TAG, "onPause — skip reclaim (Physical TV; onUserLeaveHint only)")
            return
        }

        // Device Owner paths: skip while our own reclaim lifecycle is in-flight.
        val skipReclaim = handlingReclaimLifecycle ||
            KioskPolicy.isReclaimLifecycleBusy() ||
            KioskPolicy.isInReclaimQuietPeriod()
        if (skipReclaim) {
            Log.d(TAG, "onPause — skip reclaim (handling onNewIntent/onResume or quiet period)")
            return
        }

        // API 29–30: ActivityOptions reclaim (loop-safe).
        if (Build.VERSION.SDK_INT in 29..30) {
            Log.d(TAG, "onPause — API 29/30 kiosk reclaim")
            KioskPolicy.forceBringToFrontSafely(this)
            return
        }

        // API < 29: debounced reclaim — prevents onPause↔forceBringToFront storm.
        if (Build.VERSION.SDK_INT < 29) {
            tryLegacyKioskReclaim("onPause")
            return
        }

        // API 31+ Device Owner path.
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

        if (!KioskPolicy.isKioskModeEnabled(this)) return
        if (KioskPolicy.isExternalAppActive(this)) return

        // Physical TV: reclaim only from onUserLeaveHint (same reason as onPause).
        if (KioskPolicy.needsPhysicalTvFallback(this)) {
            Log.d(TAG, "onStop — skip reclaim (Physical TV; onUserLeaveHint only)")
            return
        }

        if (handlingReclaimLifecycle ||
            KioskPolicy.isReclaimLifecycleBusy() ||
            KioskPolicy.isInReclaimQuietPeriod()
        ) {
            Log.d(TAG, "onStop — skip reclaim (quiet/busy)")
            return
        }

        // API 31+: UNTOUCHED — no onStop reclaim for Device Owner path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return

        // API 29–30: post reclaim after Home task-switch.
        if (Build.VERSION.SDK_INT in 29..30) {
            Log.d(TAG, "onStop — API 29/30 kiosk reclaim (post)")
            window.decorView.post {
                if (!KioskPolicy.isKioskModeEnabled(this@MainActivity)) return@post
                if (KioskPolicy.isExternalAppActive(this@MainActivity)) return@post
                if (KioskPolicy.isInReclaimQuietPeriod()) return@post
                KioskPolicy.forceBringToFrontSafely(this@MainActivity)
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
     * Receives reclaim / Home-interceptor launches
     * ([HomeKeyInterceptorService] uses NEW_TASK | CLEAR_TOP | SINGLE_TOP +
     * [EXTRA_NAVIGATE_TO_HOME]). Instantly hides guest overlays and re-pins via
     * [snapKioskSurfaceImmediate] so the kiosk channel restores after YouTube.
     */
    override fun onNewIntent(intent: Intent) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // ≤50ms busy pulse — never finish/recreate; keep existing surface.
        markReclaimLifecycleBusyBriefly()
        try {
            super.onNewIntent(intent)
            setIntent(intent)

            val isKioskActive = KioskPolicy.isKioskModeEnabled(this)
            isKioskModeEnabled = isKioskActive
            currentKioskState = isKioskActive

            val fromHomeInterceptor =
                intent.getBooleanExtra(EXTRA_NAVIGATE_TO_HOME, false)
            val isHomeIntent = intent.categories?.contains(Intent.CATEGORY_HOME) == true ||
                (intent.action == Intent.ACTION_MAIN &&
                    intent.hasCategory(Intent.CATEGORY_HOME))

            // Kiosk OFF + HOME → unpin, clear DPM whitelist, and background.
            if (!isKioskActive && isHomeIntent && !fromHomeInterceptor) {
                Log.i(TAG, "onNewIntent HOME while kiosk OFF — disableKioskMode + moveTaskToBack")
                KioskPolicy.disableKioskMode(
                    activity = this,
                    source = KioskPolicy.KioskSource.SYSTEM_DEFAULT,
                    persistFlag = false,
                )
                KioskPolicy.launchSystemDefaultLauncher(this)
                return
            }

            val wantsRootHome = fromHomeInterceptor ||
                isHomeIntent ||
                (intent.action == Intent.ACTION_MAIN && pendingReturnToHome)

            if (!wantsRootHome && !pendingReturnToHome) return

            if (!isKioskActive && !fromHomeInterceptor && !pendingReturnToHome) {
                Log.d(TAG, "onNewIntent ignored — kiosk disabled")
                return
            }

            // —— From YouTube / OTT Home intercept: pin + hide overlay immediately ——
            if (isKioskActive || fromHomeInterceptor) {
                Log.i(
                    TAG,
                    "onNewIntent — interceptor/Home reclaim → snap Lock Task + Root Home",
                )
                // Clear OTT session flags before nav (interceptor may have already).
                KioskPolicy.clearExternalAppActive(this)
                KioskPolicy.clearOttLaunchState(this)
                snapKioskSurfaceImmediate("onNewIntent")
                // Hide guest overlays / pop to Root Home (same as in-app Home).
                finishReturnFromExternalApp()
                // Re-assert pin after nav (safe if already RESUMED; onResume also snaps).
                startLockTaskSafely("onNewIntent_post_nav")
            }
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            Log.e(TAG, "onNewIntent error", e)
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
     * Prefer [navigateToHomeView] when the Compose overlay navigator is registered.
     */
    fun navigateToRootHomeScreen(showCover: Boolean = true) {
        pendingReturnToHome = false
        KioskPolicy.clearOttLaunchState(this)
        KioskPolicy.clearUserMinimized(this)
        screensaverVisible = false
        intent?.removeExtra(EXTRA_NAVIGATE_TO_HOME)
        intent?.removeExtra(EXTRA_SOFT_HOME_RESET)

        if (KioskPolicy.isOnGuestHomeScreen(this) && !isSubViewActive()) {
            Log.i(TAG, "navigateToRootHomeScreen — already on guest Home, cleanup only")
            ottTransitionCover = false
            return
        }

        // Instant Home-icon path when NavGraph overlay navigator is live.
        if (homeViewNavigator != null && !showCover) {
            navigateToHomeView()
            return
        }

        if (showCover) {
            ottTransitionCover = true
        }

        // Prefer direct navigator; signal is fallback for race before composition.
        val navigator = homeViewNavigator
        if (navigator != null) {
            Log.i(TAG, "navigateToRootHomeScreen — instant navigateToHomeView")
            navigator.invoke()
        } else {
            navigateHomeSignal = System.currentTimeMillis()
            KioskPolicy.setOnGuestHomeScreen(this, true)
            Log.i(TAG, "navigateToRootHomeScreen → signal=$navigateHomeSignal")
        }

        window.decorView.post {
            window.decorView.post {
                ottTransitionCover = false
            }
        }
    }

    /**
     * On return from YouTube/HOME: zero animation.
     * Clears isExternalAppActive so Watchdog resumes reclaim, then always resets
     * the overlay nav graph to root Home (never leaves stock launcher on top).
     */
    private fun finishReturnFromExternalApp() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        pendingReturnToHome = false
        // Resume Watchdog — guest is back in hotel UI.
        KioskPolicy.clearExternalAppActive(this)
        KioskPolicy.clearOttLaunchState(this)
        KioskPolicy.clearUserMinimized(this)
        screensaverVisible = false
        intent?.removeExtra(EXTRA_NAVIGATE_TO_HOME)
        intent?.removeExtra(EXTRA_SOFT_HOME_RESET)
        ottTransitionCover = false

        // Always reset to root Home — dismiss overlays if any; no-op when already Home.
        Log.i(TAG, "finishReturnFromExternalApp — reset navigation to root Home")
        navigateToHomeView()
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
        if (handlingReclaimLifecycle || KioskPolicy.isInReclaimQuietPeriod()) {
            Log.d(TAG, "KioskInterceptor — skip bringAppToFront (quiet/busy)")
            return
        }

        if (Build.VERSION.SDK_INT in 29..30) {
            Log.d(TAG, "KioskInterceptor — API 29/30 reclaim")
            isAppInForeground = false
            KioskPolicy.setMainActivityForeground(this, false)
            KioskPolicy.forceBringToFrontSafely(this)
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
        KioskPolicy.forceBringToFrontSafely(this)
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
        val ok = KioskPolicy.forceBringToFrontSafely(this)
        if (!ok) {
            reclaimInFlight = false
        }
        return ok
    }

    /**
     * Dynamic HOME interceptor — Screen Pinning reclaim only (no Accessibility).
     * Physical TV: queue zero-anim PendingIntent **before** [super.onUserLeaveHint]
     * so the reclaim races ahead of the stock launcher paint — no debounce / post.
     */
    override fun onUserLeaveHint() {
        // Suppress outgoing transition before the system can paint the stock launcher.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val kioskOn = KioskPolicy.isKioskModeEnabled(this)
        val ottActive = KioskPolicy.isExternalAppActive(this)
        val physicalTv = KioskPolicy.needsPhysicalTvFallback(this)

        // Fire reclaim payload FIRST (before super) — zero debounce, main thread.
        if (kioskOn && !ottActive && physicalTv && !isActivitySurfaceGone()) {
            Log.i(TAG, "onUserLeaveHint — PendingIntent reclaim PRE-super (0ms, bypass guard)")
            KioskPolicy.forceBringToFrontPhysicalTvUrgent(
                context = this,
                navigateToHome = true,
                bypassDuplicateGuard = true,
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        super.onUserLeaveHint()

        if (!kioskOn) {
            KioskPolicy.markUserMinimized(this)
            Log.d(TAG, "KioskInterceptor — kiosk OFF, allow default HOME")
            return
        }

        if (ottActive) {
            Log.d(TAG, "KioskInterceptor — OTT/IPTV session, skip onUserLeaveHint reclaim")
            return
        }

        isAppInForeground = false
        KioskPolicy.setMainActivityForeground(this, false)

        // Physical TV reclaim already sent pre-super — do not send twice.
        if (physicalTv) {
            return
        }

        if (Build.VERSION.SDK_INT in 29..30) {
            Log.d(TAG, "KioskInterceptor — API 29/30 onUserLeaveHint reclaim")
            KioskPolicy.forceBringToFrontSafely(
                context = this,
                preferImmediateOptions = true,
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }

        if (Build.VERSION.SDK_INT < 29) {
            tryLegacyKioskReclaim("onUserLeaveHint")
            return
        }

        // API 31+ Device Owner — immediate reclaim (no deferred post).
        Log.d(TAG, "KioskInterceptor — onUserLeaveHint immediate reclaim")
        KioskPolicy.forceBringToFrontSafely(
            context = this,
            preferImmediateOptions = true,
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        nestedAdminBackHandler = null
        try {
            BlockedKeysManager.setLearnMode(applicationContext, false)
        } catch (_: Exception) {
        }
        KioskPolicy.removeKioskModeChangedListener(kioskModeChangedListener)
        mainHandler.removeCallbacks(physicalTvReclaimRetryRunnable)
        mainHandler.removeCallbacks(clearHandlingReclaimRunnable)
        removeKioskOverlayBarrier()
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
     * Physical TV (non–Device Owner): full-screen transparent SYSTEM_ALERT_WINDOW
     * overlay to reduce HOME / system-UI flash while Screen Pinning is active.
     * Passes through touch / focus so guest D-pad UI stays usable.
     * No-ops without [Settings.canDrawOverlays].
     */
    private fun setupPhysicalTvFallbackOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "setupPhysicalTvFallbackOverlay — overlay permission missing, skip")
            return
        }
        // Upgrade from 1×1 legacy barrier if present.
        if (kioskOverlayView != null && !physicalTvOverlayActive) {
            removeKioskOverlayBarrier()
        }
        if (kioskOverlayView != null && physicalTvOverlayActive) return

        try {
            val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            }
            val layoutParams = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                type,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }

            val view = android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            windowManager.addView(view, layoutParams)
            kioskOverlayView = view
            physicalTvOverlayActive = true
            Log.i(
                TAG,
                "Physical TV full-screen overlay attached (API ${Build.VERSION.SDK_INT})",
            )
        } catch (e: Exception) {
            Log.w(TAG, "setupPhysicalTvFallbackOverlay failed", e)
            kioskOverlayView = null
            physicalTvOverlayActive = false
        }
    }

    /**
     * Android 9/10 Device Owner only: tiny SYSTEM_ALERT_WINDOW overlay to reduce
     * HOME launcher flash. No-ops without [Settings.canDrawOverlays] or on API 30+.
     */
    private fun setupLegacyOverlayBarrier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        if (physicalTvOverlayActive) return
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
            physicalTvOverlayActive = false
            Log.i(TAG, "Legacy overlay barrier attached (API ${Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            Log.w(TAG, "setupLegacyOverlayBarrier failed", e)
            kioskOverlayView = null
        }
    }

    /** Removes [kioskOverlayView] if present — safe to call from any lifecycle state. */
    private fun removeKioskOverlayBarrier() {
        val view = kioskOverlayView ?: return
        try {
            val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
            windowManager.removeView(view)
            Log.d(TAG, "Kiosk overlay barrier removed (physicalTv=$physicalTvOverlayActive)")
        } catch (e: Exception) {
            Log.w(TAG, "removeKioskOverlayBarrier failed", e)
        } finally {
            kioskOverlayView = null
            physicalTvOverlayActive = false
        }
    }

    /** @deprecated Use [removeKioskOverlayBarrier]. */
    private fun removeLegacyOverlayBarrier() = removeKioskOverlayBarrier()

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
        private const val RECLAIM_DEBOUNCE_MS = 50L
        /** Must match admin-panel RTDB region (google-services.json may still list us-central). */
        private const val RTDB_URL =
            "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app"
        /** 10 minutes of no remote / touch input before the screen saver appears. */
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L

        /**
         * Extra from Home reclaim / launcher intents.
         * When true, Compose navigation resets to the primary guest Home screen.
         */
        const val EXTRA_NAVIGATE_TO_HOME = "NAVIGATE_TO_HOME"

        /**
         * Soft HOME reset while MainActivity is already foreground — avoid CLEAR_TOP
         * / Lock Task re-assert that causes a blue window flash.
         */
        const val EXTRA_SOFT_HOME_RESET = "SOFT_HOME_RESET"

        /** OEM Apps / GTPL Home dedicated remote key. */
        private const val KEYCODE_OEM_APPS = 228
        /** OEM Netflix dedicated remote key. */
        private const val KEYCODE_OEM_NETFLIX = 247
        /** OEM YouTube dedicated remote key. */
        private const val KEYCODE_OEM_YOUTUBE = 288
        /** OEM Prime Video dedicated remote key. */
        private const val KEYCODE_OEM_PRIME_VIDEO = 289
    }
}
