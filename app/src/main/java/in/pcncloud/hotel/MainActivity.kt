package `in`.pcncloud.hotel

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
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
 * Lock Task Mode is driven live from Firebase Realtime Database
 * `app_config/is_kiosk_mode_enabled` (Web Admin Panel).
 */
class MainActivity : ComponentActivity() {

    private lateinit var hotelConfig: HotelConfig
    private lateinit var repository: FirestoreRepository
    private val syncListeners = mutableListOf<ListenerRegistration>()

    /** RTDB listener for live kiosk / Lock Task control from Web Admin. */
    private var kioskModeRef: DatabaseReference? = null
    private var kioskModeListener: ValueEventListener? = null

    /** Last value applied from RTDB (also mirrored in [KioskPolicy]). */
    private var isKioskModeEnabled: Boolean = true

    /** Bumped on any remote / touch interaction so the idle timer restarts. */
    private var lastInteractionAt by mutableLongStateOf(System.currentTimeMillis())

    /** When true, [ScreensaverOverlay] is shown; nav graph underneath stays composed. */
    private var screensaverVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        // Sync is_kiosk_mode_enabled from Remote Config → SharedPreferences (unless admin override).
        KioskRemoteConfig.syncOnLaunch(this) { enabled ->
            Log.i(TAG, "Kiosk mode after Remote Config sync → $enabled")
            isKioskModeEnabled = enabled
            applyLockTaskMode(enabled)
        }

        // Live Web Admin control via Realtime Database.
        attachKioskModeRealtimeListener()

        installKioskBackSafetyNet()

        hotelConfig = HotelConfig(applicationContext)
        val hotelId = hotelConfig.getHotelId()
        if (hotelId.isNullOrBlank()) {
            Log.i(TAG, "No paired hotelId — opening SplashActivity")
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
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

    /**
     * Firebase Realtime Database listener for `app_config/is_kiosk_mode_enabled`.
     * Web Admin toggles this node to enable/disable Lock Task Mode instantly.
     */
    private fun attachKioskModeRealtimeListener() {
        try {
            val ref = FirebaseDatabase.getInstance()
                .getReference(RTDB_KIOSK_PATH)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val enabled = when (val raw = snapshot.value) {
                        is Boolean -> raw
                        is String -> raw.equals("true", ignoreCase = true)
                        is Number -> raw.toInt() != 0
                        else -> {
                            Log.w(TAG, "Kiosk RTDB node missing/invalid — keeping $isKioskModeEnabled")
                            return
                        }
                    }
                    Log.i(TAG, "RTDB $RTDB_KIOSK_PATH → $enabled")
                    isKioskModeEnabled = enabled
                    // Web Admin is authoritative — clear any local technician override.
                    if (KioskPolicy.hasAdminOverride(this@MainActivity)) {
                        KioskPolicy.clearAdminOverride(this@MainActivity)
                    }
                    KioskPolicy.setKioskModeEnabled(
                        context = this@MainActivity,
                        enabled = enabled,
                        source = KioskPolicy.KioskSource.REALTIME_DATABASE,
                    )
                    applyLockTaskMode(enabled)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Kiosk RTDB listener cancelled: ${error.message}")
                }
            }
            ref.addValueEventListener(listener)
            kioskModeRef = ref
            kioskModeListener = listener
            Log.i(TAG, "Attached RTDB listener → $RTDB_KIOSK_PATH")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach kiosk RTDB listener", e)
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
                Log.i(TAG, "startLockTask() — Home/Back locked")
            } else {
                stopLockTask()
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
        }
    }

    /**
     * Fallback when Compose [androidx.activity.compose.BackHandler] is not in the tree
     * (e.g. service-suspended screen). Kiosk ON → never finish; kiosk OFF → leave task.
     */
    private fun installKioskBackSafetyNet() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (KioskPolicy.isKioskModeEnabled(this@MainActivity)) {
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

    override fun onResume() {
        super.onResume()
        if (!screensaverVisible) {
            lastInteractionAt = System.currentTimeMillis()
        }
        // Re-assert Lock Task after resume (e.g. returning from another activity).
        if (KioskPolicy.isKioskModeEnabled(this)) {
            applyLockTaskMode(true)
        }
    }

    override fun onStart() {
        super.onStart()
        KioskPolicy.clearUserMinimized(this)
    }

    /**
     * Home / app-switch intercept: when kiosk is ON, immediately bring [MainActivity]
     * back to the front so the guest cannot stay on the Android TV home screen.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val kioskOn = KioskPolicy.isKioskModeEnabled(this)
        if (kioskOn) {
            Log.d(TAG, "onUserLeaveHint — kiosk ON, reordering MainActivity to front")
            try {
                val relaunch = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(relaunch)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to bring MainActivity to front after Home", e)
            }
        } else {
            // Explicit Home / app-switch — do not allow watchdog to pull us back.
            KioskPolicy.markUserMinimized(this)
            Log.d(TAG, "onUserLeaveHint — marked minimized (kiosk OFF)")
        }
    }

    override fun onDestroy() {
        kioskModeListener?.let { listener ->
            try {
                kioskModeRef?.removeEventListener(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove kiosk RTDB listener", e)
            }
        }
        kioskModeListener = null
        kioskModeRef = null

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
        /** Firebase Realtime Database path controlled by the Web Admin Panel. */
        private const val RTDB_KIOSK_PATH = "app_config/is_kiosk_mode_enabled"
        /** 10 minutes of no remote / touch input before the screen saver appears. */
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
