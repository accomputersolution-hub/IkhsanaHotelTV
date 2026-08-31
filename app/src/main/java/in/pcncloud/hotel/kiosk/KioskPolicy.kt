package `in`.pcncloud.hotel.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.alert.AlertOverlayService
import `in`.pcncloud.hotel.config.HotelConfig

/**
 * Central gate for kiosk / custom-launcher behaviour.
 *
 * Runtime flag: SharedPreferences key [KEY_KIOSK_ENABLED] (`is_kiosk_mode_enabled`).
 * Default: **true** (hotel TVs lock to guest UI unless technicians disable it).
 *
 * Sources:
 * - Firebase Realtime Database `app_config/is_kiosk_mode_enabled` (live Web Admin)
 * - [KioskRemoteConfig] writes the flag from Firebase Remote Config on launch
 * - Staff Admin Mode (Master PIN) can override locally and set [KEY_ADMIN_OVERRIDE]
 *
 * Also gates BootReceiver / Watchdog bring-to-front paths.
 */
object KioskPolicy {

    private const val TAG = "KioskPolicy"
    private const val PREFS = "hotel_tv_kiosk"
    private const val FORCE_BRING_REQUEST_CODE = 2002
    /** Distinct request code so Physical TV urgent PendingIntent is not coalesced away. */
    private const val PHYSICAL_TV_URGENT_REQUEST_CODE = 2003
    /**
     * Minimum safe gap between consecutive PendingIntent reclaim sends.
     * Primary reclaim always fires immediately; this only drops duplicate
     * secondary calls (prevents pause/resume storms) — never delays the first.
     */
    private const val FORCE_BRING_DEBOUNCE_MS = 50L
    /**
     * Device Owner lifecycle loop guard.
     * Physical TV uses the tighter [PHYSICAL_TV_LOOP_GUARD_MS] instead.
     */
    private const val SAFE_BRING_LOOP_GUARD_MS = 50L
    /**
     * Physical TV quiet / busy hold for setReclaimLifecycleBusy only.
     * Kept tiny so Home reclaim is never stalled after onNewIntent/onResume.
     */
    private const val PHYSICAL_TV_LOOP_GUARD_MS = 50L
    /**
     * Minimum interval between consecutive forceBringToFront startActivity calls.
     * Prevents ActivityManager throttle and onPause ↔ onNewIntent storms.
     * Does **not** delay the primary Intent — only drops secondary calls inside the window.
     */
    private const val RECLAIM_PENDING_GUARD_MS = 50L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastForceBringAtMs: Long = 0L

    /** True while an immediate Physical TV reclaim Intent was just sent. */
    @Volatile
    private var isReclaimPending: Boolean = false

    /**
     * True while [IntroVideoScreen] is the active NavHost destination.
     * Process-local — Watchdog / onNewIntent must not force Root Home nav
     * (that released ExoPlayer on API 28 before playback could finish).
     */
    @Volatile
    private var introPlaybackActive: Boolean = false

    /**
     * True while Staff Secret Settings / Master PIN UI is on screen.
     * Process-local — focus loss from Toast / dialogs must NOT snap to Home.
     */
    @Volatile
    private var staffAdminUiActive: Boolean = false

    /**
     * True while technician [exitKioskModeCleanly] is tearing down kiosk.
     * Blocks Watchdog / onUserLeaveHint reclaim for the duration of the exit.
     */
    @Volatile
    private var exitingAppCleanly: Boolean = false

    /** True while MainActivity is inside onNewIntent / onResume reclaim handling. */
    @Volatile
    private var reclaimLifecycleBusy: Boolean = false

    /** Wall-clock until which Device Owner paths may skip another reclaim. */
    @Volatile
    private var reclaimQuietUntilMs: Long = 0L

    /** Wall-clock until which reclaim Intents are suppressed (staff UI / clean exit). */
    @Volatile
    private var reclaimSuppressedUntilMs: Long = 0L

    private val clearReclaimBusyRunnable = Runnable {
        if (reclaimLifecycleBusy) {
            reclaimLifecycleBusy = false
            Log.d(TAG, "reclaimLifecycleBusy auto-cleared (≤${PHYSICAL_TV_LOOP_GUARD_MS}ms hold)")
        }
    }

    private val clearReclaimPendingRunnable = Runnable {
        isReclaimPending = false
    }

    /** Product / Remote Config key name — also stored in SharedPreferences. */
    const val KEY_KIOSK_ENABLED = "is_kiosk_mode_enabled"

    /** Legacy key — migrated once to [KEY_KIOSK_ENABLED]. */
    private const val KEY_KIOSK_ENABLED_LEGACY = "kiosk_mode_enabled"

    private const val KEY_ADMIN_OVERRIDE = "kiosk_admin_override"
    private const val KEY_USER_MINIMIZED = "user_minimized"
    private const val KEY_EXITED_CLEANLY = "exited_cleanly"
    private const val KEY_PENDING_CRASH_RECOVERY = "pending_crash_recovery"
    private const val KEY_EXTERNAL_APP_UNTIL = "external_app_until_ms"
    private const val KEY_LAST_OTT_PACKAGE = "last_ott_package"
    /**
     * Durable flag: guest is intentionally inside YouTube / Netflix / Live TV / etc.
     * Watchdog must NOT bring MainActivity to front while this is true.
     * Cleared only on HOME/BACK return ([clearExternalAppActive] / [clearOttLaunchState]).
     */
    private const val KEY_EXTERNAL_APP_ACTIVE = "is_external_app_active"
    /** Wall-clock when [markOttLaunched] last ran — blocks premature onResume clear. */
    private const val KEY_OTT_LAUNCHED_AT_MS = "ott_launched_at_ms"
    /** Block accidental OTT re-launch after returning from YouTube / Home. */
    private const val KEY_OTT_LAUNCH_SUPPRESS_UNTIL = "ott_launch_suppress_until_ms"
    /** True while MainActivity is resumed in the foreground. */
    private const val KEY_MAIN_FOREGROUND = "main_activity_foreground"
    /** True while Compose nav is on the primary guest Home route. */
    private const val KEY_ON_GUEST_HOME = "on_guest_home_screen"

    /** Explicit Admin whitelist from RTDB `hotels/{id}/config/allowedPackages`. */
    private const val KEY_ALLOWED_PACKAGES = "allowedPackages"
    /** Hotel that owns [KEY_ALLOWED_PACKAGES] — prevents cross-tenant leakage. */
    private const val KEY_ALLOWED_PACKAGES_HOTEL_ID = "allowedPackagesHotelId"

    /** MainActivity / Admin camelCase flag (preferred over [KEY_KIOSK_ENABLED] when set). */
    private const val KEY_KIOSK_ENABLED_CAMEL = "isKioskModeEnabled"

    /** How long [onUserLeaveHint] may skip reclaim after launching YouTube / IPTV / etc. */
    private const val EXTERNAL_APP_SESSION_MS = 4 * 60 * 60 * 1000L // 4 hours of OTT viewing

    enum class KioskSource {
        REMOTE_CONFIG,
        REALTIME_DATABASE,
        LOCAL_ADMIN,
        SYSTEM_DEFAULT,
    }

    /**
     * Credential-encrypted prefs are unavailable until unlock (Direct Boot /
     * LOCKED_BOOT_COMPLETED). Use device-protected storage while the user is locked.
     */
    private fun prefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        val safeContext = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !app.isUserUnlocked) {
                app.createDeviceProtectedStorageContext()
            } else {
                app
            }
        } catch (e: Exception) {
            Log.w(TAG, "prefs: CE storage unavailable — using device-protected context", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                app.createDeviceProtectedStorageContext()
            } else {
                app
            }
        }
        return safeContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val Context.isUserUnlocked: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
            return try {
                val userManager = getSystemService(Context.USER_SERVICE) as? UserManager
                userManager?.isUserUnlocked ?: true
            } catch (_: Exception) {
                // Fail open — prefer attempting CE; prefs() catch falls back to DE.
                true
            }
        }

    private fun migrateIfNeeded(context: Context) {
        try {
            val p = prefs(context)
            if (p.contains(KEY_KIOSK_ENABLED)) return
            if (p.contains(KEY_KIOSK_ENABLED_LEGACY)) {
                val legacy = p.getBoolean(KEY_KIOSK_ENABLED_LEGACY, true)
                p.edit()
                    .putBoolean(KEY_KIOSK_ENABLED, legacy)
                    .remove(KEY_KIOSK_ENABLED_LEGACY)
                    .apply()
                Log.i(TAG, "Migrated legacy kiosk flag → $KEY_KIOSK_ENABLED=$legacy")
            }
        } catch (e: Exception) {
            Log.w(TAG, "migrateIfNeeded skipped (Direct Boot / storage locked?)", e)
        }
    }

    /**
     * Whether kiosk / custom-launcher lock is active.
     * Default **true** when unset (first install / fresh prefs).
     */
    fun isKioskModeEnabled(context: Context): Boolean {
        migrateIfNeeded(context)
        val p = prefs(context)
        // Prefer camelCase key written by MainActivity RTDB sync.
        if (p.contains(KEY_KIOSK_ENABLED_CAMEL)) {
            return p.getBoolean(KEY_KIOSK_ENABLED_CAMEL, false)
        }
        return p.getBoolean(KEY_KIOSK_ENABLED, true)
    }

    /**
     * True when this package is provisioned as Device Owner.
     * Never throws — physical TVs where `dpm set-device-owner` was rejected return false.
     */
    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "isDeviceOwner check failed — treating as non-owner", e)
            false
        }
    }

    /**
     * Kiosk ON but Device Owner missing / rejected → use Screen Pinning + Overlay fallback.
     */
    fun needsPhysicalTvFallback(context: Context): Boolean =
        isKioskModeEnabled(context) && !isDeviceOwner(context)

    /**
     * True when this app is the resolved default activity for
     * [Intent.ACTION_MAIN] + [Intent.CATEGORY_HOME].
     */
    fun isMyAppDefaultLauncher(context: Context): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            val resolvedPackage = resolveInfo?.activityInfo?.packageName
            resolvedPackage.equals(context.packageName, ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "isMyAppDefaultLauncher check failed", e)
            false
        }
    }

    /**
     * Temporarily pause Watchdog / focus-loss / leave-hint reclaim.
     * Used for Staff Secret Settings (PIN dialog) and clean launcher exit.
     */
    fun suppressReclaimFor(durationMs: Long, reason: String) {
        val until = System.currentTimeMillis() + durationMs.coerceAtLeast(0L)
        reclaimSuppressedUntilMs = maxOf(reclaimSuppressedUntilMs, until)
        Log.i(TAG, "suppressReclaimFor ($reason) until=$until (${durationMs}ms)")
    }

    fun clearReclaimSuppression(reason: String = "cleared") {
        reclaimSuppressedUntilMs = 0L
        Log.d(TAG, "reclaim suppression cleared ($reason)")
    }

    /**
     * Clears a timed [suppressReclaimFor] window before HOME reclaim.
     * Staff Secret Settings and clean exit keep their blocks intact.
     */
    fun clearTimedSuppressForHomeReclaim(caller: String) {
        if (staffAdminUiActive || exitingAppCleanly) return
        if (!isReclaimSuppressed()) return
        clearReclaimSuppression("home_reclaim:$caller")
    }

    /** True while [suppressReclaimFor] window is still active. */
    fun isReclaimSuppressed(): Boolean {
        val until = reclaimSuppressedUntilMs
        if (until <= 0L) return false
        if (System.currentTimeMillis() >= until) {
            reclaimSuppressedUntilMs = 0L
            return false
        }
        return true
    }

    /**
     * Staff Secret Settings / Master PIN overlay is visible.
     * While true, reclaim must never fire [MainActivity.EXTRA_NAVIGATE_TO_HOME].
     */
    fun setStaffAdminUiActive(active: Boolean) {
        staffAdminUiActive = active
        if (active) {
            suppressReclaimFor(10 * 60 * 1000L, "staff_admin_ui")
        } else if (!exitingAppCleanly) {
            // Keep any exit suppress; drop long staff suppress when leaving admin.
            clearReclaimSuppression("staff_admin_ui_closed")
        }
        Log.i(TAG, "staffAdminUiActive=$active")
    }

    fun isStaffAdminUiActive(): Boolean = staffAdminUiActive

    fun setExitingAppCleanly(exiting: Boolean) {
        exitingAppCleanly = exiting
        Log.i(TAG, "exitingAppCleanly=$exiting")
    }

    fun isExitingAppCleanly(): Boolean = exitingAppCleanly

    /**
     * Unified gate: reclaim / Watchdog / Root-Home snap must not run.
     *
     * When [context] is provided, also skips while an intentional OTT / Live TV
     * session is active or [KioskLockTask.LIVE_TV_PACKAGE] is still visible —
     * Watchdog must never steal focus from EKTV Pro.
     *
     * @param ignoreTimedSuppress when true (Accessibility HOME), still reclaim
     *   even if [suppressReclaimFor] (e.g. default Home picker) is active.
     *   Staff Secret Settings and clean exit still block.
     */
    /**
     * Human-readable skip reason for logging, or null when reclaim may proceed.
     */
    fun reclaimSkipLabel(
        reason: String = "",
        context: Context? = null,
        ignoreTimedSuppress: Boolean = false,
    ): String? {
        if (exitingAppCleanly) {
            Log.d(TAG, "skip reclaim — exitingAppCleanly ($reason)")
            return "exitingAppCleanly"
        }
        if (staffAdminUiActive) {
            Log.d(TAG, "skip reclaim — staffAdminUiActive ($reason)")
            return "staffAdminUiActive"
        }
        if (!ignoreTimedSuppress && isReclaimSuppressed()) {
            Log.d(TAG, "skip reclaim — suppress window ($reason)")
            return "timedSuppress"
        }
        if (context != null && shouldProtectExternalAppSession(context)) {
            Log.d(TAG, "skip reclaim — Live TV / OTT protected ($reason)")
            return "ottSession"
        }
        return null
    }

    fun shouldSkipKioskReclaim(
        reason: String = "",
        context: Context? = null,
        ignoreTimedSuppress: Boolean = false,
    ): Boolean = reclaimSkipLabel(reason, context, ignoreTimedSuppress) != null

    /**
     * True while guest is intentionally in Live TV / OTT, or EKTV Pro is still
     * visible (even if the durable flag was cleared too early).
     */
    fun shouldProtectExternalAppSession(context: Context): Boolean {
        if (isExternalAppActive(context)) return true
        if (isOttLaunchGracePeriod(context)) return true
        if (isLastOttPackageVisible(context)) return true
        if (isPackageVisible(context, KioskLockTask.LIVE_TV_PACKAGE)) return true
        return false
    }

    /**
     * Persist Super Admin package whitelist for [hotelId] only.
     * Never write a global/unscoped list — empty [hotelId] clears the cache.
     */
    fun setAllowedPackagesList(
        context: Context,
        packages: List<String>,
        hotelId: String?,
    ) {
        val normalizedHotel = HotelConfig.normalizeHotelId(hotelId)
        if (normalizedHotel.isBlank()) {
            clearTenantWhitelistCache(context)
            return
        }
        val cleaned = (
            packages.map { it.trim() }.filter { it.isNotEmpty() } +
                KioskLockTask.baselineLockTaskPackages()
            )
            .toSet()
        prefs(context).edit()
            .putStringSet(KEY_ALLOWED_PACKAGES, cleaned)
            .putString(KEY_ALLOWED_PACKAGES_HOTEL_ID, normalizedHotel)
            .apply()
        Log.i(
            TAG,
            "allowedPackages hotelId=$normalizedHotel count=${cleaned.size} " +
                "(includes Live TV baseline) → $cleaned",
        )
    }

    /**
     * Returns this hotel's whitelist only.
     * Mismatch / missing hotel / unpaired → [emptyList] (no cross-tenant fallback).
     */
    fun getAllowedPackagesList(
        context: Context,
        hotelId: String? = null,
    ): List<String> {
        val currentHotel = HotelConfig.normalizeHotelId(
            hotelId ?: HotelConfig(context).getHotelId(),
        )
        if (currentHotel.isBlank()) {
            Log.d(TAG, "getAllowedPackagesList — unpaired → baseline only")
            return KioskLockTask.BASELINE_LOCK_TASK_PACKAGES
        }
        val cachedHotel = prefs(context).getString(KEY_ALLOWED_PACKAGES_HOTEL_ID, null)
        val stored = if (cachedHotel.isNullOrBlank() || cachedHotel != currentHotel) {
            Log.w(
                TAG,
                "getAllowedPackagesList — cache miss/mismatch " +
                    "cached=$cachedHotel current=$currentHotel → baseline only",
            )
            emptyList()
        } else {
            prefs(context).getStringSet(KEY_ALLOWED_PACKAGES, emptySet())
                ?.toList()
                .orEmpty()
        }
        // Always expose Live TV (EKTV Pro) even if Admin RTDB list omitted it.
        return (stored + KioskLockTask.BASELINE_LOCK_TASK_PACKAGES)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * Drop locally cached whitelist/OTT allowlist when switching or unpairing hotels.
     * Call from [HotelConfig.setHotelId] / [HotelConfig.clearHotelId].
     */
    fun clearTenantWhitelistCache(context: Context) {
        prefs(context).edit()
            .remove(KEY_ALLOWED_PACKAGES)
            .remove(KEY_ALLOWED_PACKAGES_HOTEL_ID)
            .apply()
        Log.i(TAG, "Tenant whitelist cache cleared (no cross-hotel fallback)")
    }

    /**
     * Full wipe of `hotel_tv_kiosk` tenant state on logout / unpair.
     * Stops Watchdog and clears whitelist, kiosk flags, and OTT session gates.
     */
    fun clearTenantKioskCache(context: Context) {
        prefs(context).edit()
            .remove(KEY_ALLOWED_PACKAGES)
            .remove(KEY_ALLOWED_PACKAGES_HOTEL_ID)
            .remove(KEY_KIOSK_ENABLED_CAMEL)
            .putBoolean(KEY_KIOSK_ENABLED, false)
            .remove(KEY_ADMIN_OVERRIDE)
            .remove(KEY_USER_MINIMIZED)
            .remove(KEY_EXTERNAL_APP_UNTIL)
            .remove(KEY_LAST_OTT_PACKAGE)
            .remove(KEY_OTT_LAUNCH_SUPPRESS_UNTIL)
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, false)
            .putBoolean(KEY_MAIN_FOREGROUND, false)
            .putBoolean(KEY_ON_GUEST_HOME, false)
            .apply()
        try {
            KioskWatchdogService.stop(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog stop during clearTenantKioskCache failed", e)
        }
        Log.i(TAG, "clearTenantKioskCache — hotel_tv_kiosk tenant state wiped")
    }

    /**
     * On attach / pair: if cached whitelist belongs to another hotel, wipe it and
     * bind an empty list to [hotelId] until RTDB delivers this tenant's config.
     */
    fun bindWhitelistToHotelOrClear(context: Context, hotelId: String) {
        val normalized = HotelConfig.normalizeHotelId(hotelId)
        if (normalized.isBlank()) {
            clearTenantWhitelistCache(context)
            return
        }
        val cachedHotel = prefs(context).getString(KEY_ALLOWED_PACKAGES_HOTEL_ID, null)
        if (cachedHotel != normalized) {
            Log.w(
                TAG,
                "Whitelist tenant switch cached=$cachedHotel → $normalized — clearing stale list",
            )
            setAllowedPackagesList(context, emptyList(), normalized)
        }
    }

    /**
     * Validates whether an external app may be launched.
     * When Kiosk Mode is OFF → allow everything.
     * When Kiosk Mode is ON → hotel Admin `allowedPackages` **or** Lock Task baseline
     * (Live TV / EKTV Pro).
     * Never throws — a prefs / parse failure fails closed (deny) under kiosk.
     */
    fun canLaunchApp(context: Context, targetPackageName: String): Boolean {
        return try {
            if (!isKioskModeEnabled(context)) return true
            val target = targetPackageName.trim()
            if (target.isEmpty()) return false
            if (target in KioskLockTask.baselineLockTaskPackages()) return true
            val allowed = getAllowedPackagesList(context)
            val ok = allowed.contains(target)
            if (!ok) {
                Log.w(
                    TAG,
                    "Blocked launch of $target — not in hotel allowedPackages ($allowed)",
                )
            }
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "canLaunchApp failed — denying under kiosk (safe)", t)
            isKioskModeEnabled(context).not()
        }
    }

    /**
     * Silent interception when an unauthorized package launch is refused.
     * Never throws, never restarts the process — only reorders [MainActivity]
     * to the front with [Intent.FLAG_ACTIVITY_REORDER_TO_FRONT].
     */
    fun denyExternalLaunchSilently(
        context: Context,
        blockedPackage: String? = null,
    ): Boolean {
        return try {
            if (!blockedPackage.isNullOrBlank()) {
                Log.w(TAG, "Silently blocked external package → $blockedPackage")
            }
            bringMainActivityToFrontGracefully(context)
        } catch (t: Throwable) {
            Log.e(TAG, "denyExternalLaunchSilently failed (ignored — no crash)", t)
            true
        }
    }

    /**
     * Soft foreground restore after a blocked OTT / whitelist denial.
     * Uses REORDER_TO_FRONT | SINGLE_TOP (no PendingIntent storm, no process restart).
     */
    fun bringMainActivityToFrontGracefully(context: Context): Boolean {
        if (shouldSkipKioskReclaim("bringMainActivityToFrontGracefully", context)) return false
        return try {
            val appContext = context.applicationContext
            val wantHome = !isStaffAdminUiActive()
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = reclaimIntentFlags()
                if (wantHome) {
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
                }
            }
            when (context) {
                is Activity -> {
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                        if (wantHome) {
                            putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
                        }
                    }
                    context.startActivity(
                        activityIntent,
                        buildReclaimActivityOptions(context).toBundle(),
                    )
                    suppressTransitionFlash(context)
                }
                else -> {
                    appContext.startActivity(
                        intent,
                        buildReclaimActivityOptions(appContext).toBundle(),
                    )
                }
            }
            Log.i(TAG, "bringMainActivityToFrontGracefully — REORDER_TO_FRONT")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "bringMainActivityToFrontGracefully failed (ignored)", t)
            false
        }
    }

    /**
     * Instantly tear down every app-level interceptor so the default launcher
     * (e.g. GTPL) can gain focus with no background reclaim pulls.
     * Does **not** disable or modify any system packages.
     */
    fun releaseAllKioskInterceptors(context: Context) {
        mainHandler.removeCallbacks(clearReclaimBusyRunnable)
        mainHandler.removeCallbacks(clearReclaimPendingRunnable)
        isReclaimPending = false
        reclaimLifecycleBusy = false
        reclaimQuietUntilMs = 0L
        lastForceBringAtMs = 0L

        prefs(context).edit()
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, false)
            .remove(KEY_EXTERNAL_APP_UNTIL)
            .remove(KEY_LAST_OTT_PACKAGE)
            .remove(KEY_OTT_LAUNCH_SUPPRESS_UNTIL)
            .putBoolean(KEY_MAIN_FOREGROUND, false)
            .apply()

        try {
            KioskWatchdogService.stop(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "releaseAllKioskInterceptors — Watchdog stop failed", e)
        }

        onKioskModeChangedListeners.forEach { listener ->
            try {
                listener(false)
            } catch (e: Exception) {
                Log.w(TAG, "onKioskModeChanged(false) listener failed", e)
            }
        }
        Log.i(TAG, "releaseAllKioskInterceptors — OTT/busy/Watchdog cleared (no package disables)")
    }

    private val onKioskModeChangedListeners =
        java.util.concurrent.CopyOnWriteArrayList<(Boolean) -> Unit>()

    /** MainActivity registers to cancel pending reclaim retries on instant toggle. */
    fun addKioskModeChangedListener(listener: (Boolean) -> Unit) {
        onKioskModeChangedListeners.addIfAbsent(listener)
    }

    fun removeKioskModeChangedListener(listener: (Boolean) -> Unit) {
        onKioskModeChangedListeners.remove(listener)
    }

    fun setKioskModeEnabled(
        context: Context,
        enabled: Boolean,
        source: KioskSource = KioskSource.LOCAL_ADMIN,
    ) {
        migrateIfNeeded(context)
        val editor = prefs(context).edit()
            .putBoolean(KEY_KIOSK_ENABLED, enabled)
            .putBoolean(KEY_KIOSK_ENABLED_CAMEL, enabled)
        if (source == KioskSource.LOCAL_ADMIN) {
            editor.putBoolean(KEY_ADMIN_OVERRIDE, true)
        }
        editor.apply()
        Log.i(TAG, "$KEY_KIOSK_ENABLED=$enabled source=$source")

        if (enabled) {
            clearUserMinimized(context)
            KioskWatchdogService.start(context.applicationContext)
            onKioskModeChangedListeners.forEach { listener ->
                try {
                    listener(true)
                } catch (e: Exception) {
                    Log.w(TAG, "onKioskModeChanged(true) listener failed", e)
                }
            }
        } else {
            // Instant OFF: stop interceptors / Watchdog / OTT gates — allow GTPL focus.
            releaseAllKioskInterceptors(context)
            markUserMinimized(context)
            clearDeviceOwnerLockTaskPackages(context)
            resolveActivity(context)?.let { activity ->
                try {
                    activity.stopLockTask()
                    Log.i(TAG, "setKioskModeEnabled(false) — stopLockTask")
                } catch (e: Exception) {
                    Log.w(TAG, "setKioskModeEnabled(false) — stopLockTask failed", e)
                }
            }
        }
    }

    /**
     * Fully release kiosk Lock Task on Android 9 / 11 (and all APIs):
     * 1. Persist [isKioskModeEnabled] = false (optional)
     * 2. [Activity.stopLockTask]
     * 3. Clear Device Owner [DevicePolicyManager.setLockTaskPackages] whitelist
     *
     * Without step 3, OEM Lock Task can still refuse YouTube / Netflix with
     * "Unauthorized by Admin" even after the kiosk flag is off.
     */
    fun disableKioskMode(
        activity: Activity,
        source: KioskSource = KioskSource.SYSTEM_DEFAULT,
        persistFlag: Boolean = true,
    ) {
        if (persistFlag) {
            setKioskModeEnabled(
                context = activity,
                enabled = false,
                source = source,
            )
        } else {
            // Flag already persisted (e.g. RTDB) — still tear down interceptors instantly.
            releaseAllKioskInterceptors(activity)
            markUserMinimized(activity)
        }

        try {
            // 1. Stop active Lock Task Mode (must run before clearing packages on some OEMs).
            try {
                activity.stopLockTask()
                Log.i(TAG, "disableKioskMode — stopLockTask ok")
            } catch (e: Exception) {
                Log.w(TAG, "disableKioskMode — stopLockTask failed (may already be off)", e)
            }

            // 2. Clear Device Owner LockTask whitelist so external apps can launch freely.
            clearDeviceOwnerLockTaskPackages(activity)
            Log.i(TAG, "disableKioskMode — Lock Task released, packages cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling kiosk mode", e)
        }
    }

    /**
     * Unset Device Owner Lock Task packages (`arrayOf()`).
     * No-op when not Device Owner. Safe to call without an [Activity].
     */
    fun clearDeviceOwnerLockTaskPackages(context: Context) {
        try {
            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = MyDeviceAdminReceiver.getComponentName(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                Log.d(TAG, "clearDeviceOwnerLockTaskPackages — not Device Owner, skip")
                return
            }
            // Empty array: no restricted Lock Task allowlist (OTT launches freely).
            dpm.setLockTaskPackages(adminComponent, arrayOf())
            Log.i(TAG, "clearDeviceOwnerLockTaskPackages → setLockTaskPackages([])")
        } catch (e: Exception) {
            Log.e(TAG, "clearDeviceOwnerLockTaskPackages failed", e)
        }
    }

    /**
     * Exit kiosk UI safely without starting invalid launcher components.
     *
     * Force-starting packages like `com.gtpl.customgooglelauncher` (no HOME/LAUNCHER
     * activity) freezes WindowManager on a black screen. Instead:
     * 1. [Activity.stopLockTask] to unpin
     * 2. [Activity.moveTaskToBack] to reveal the underlying TV UI
     * 3. Settings only if moveTaskToBack fails
     */
    fun launchSystemDefaultLauncher(context: Context): Boolean {
        markUserMinimized(context)
        val activity = resolveActivity(context)
        if (activity == null) {
            Log.w(TAG, "launchSystemDefaultLauncher — no Activity context")
            return false
        }
        return launchSystemDefaultLauncher(activity)
    }

    fun launchSystemDefaultLauncher(activity: Activity): Boolean {
        markUserMinimized(activity)
        return try {
            // Step 1: Force release LockTask mode (kiosk pinning).
            try {
                activity.stopLockTask()
                Log.d(TAG, "Successfully stopped LockTask mode")
            } catch (e: Exception) {
                Log.e(TAG, "LockTask was not active or failed to stop", e)
            }

            // Step 2: Push hotel app back to expose the TV's underlying native UI.
            // Do NOT start GTPL / leanback / FallbackHome intents — they black-screen.
            val moved = activity.moveTaskToBack(true)
            Log.i(TAG, "launchSystemDefaultLauncher → moveTaskToBack=$moved")
            if (!moved) {
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        },
                    )
                    Log.i(TAG, "moveTaskToBack failed — opened ACTION_SETTINGS")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed opening Settings fallback", e)
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in launchSystemDefaultLauncher", e)
            try {
                activity.moveTaskToBack(true)
            } catch (_: Exception) {
                // Best-effort.
            }
            false
        }
    }

    /**
     * Technician "Exit to Main Launcher" — ordered teardown so Watchdog / overlay
     * cannot glitch during unpin.
     *
     * Order:
     * 1. [setExitingAppCleanly] + long reclaim suppress
     * 2. Stop [AlertOverlayService] / remove WindowManager popups
     * 3. [disableKioskMode] (stopLockTask + clear packages + stop Watchdog)
     * 4. Launch standard HOME intent, then [moveTaskToBack] as backup
     */
    fun exitKioskModeCleanly(activity: Activity): Boolean {
        Log.i(TAG, "exitKioskModeCleanly — begin")
        setExitingAppCleanly(true)
        suppressReclaimFor(120_000L, "exit_kiosk_cleanly")
        markCleanExit(activity)
        markUserMinimized(activity)
        staffAdminUiActive = false

        // Stop SYSTEM_ALERT_WINDOW popups before teardown (prevents exit glitch).
        try {
            AlertOverlayService.stopFully(activity)
        } catch (e: Exception) {
            Log.w(TAG, "exitKioskModeCleanly — AlertOverlayService stop failed", e)
        }

        try {
            disableKioskMode(
                activity = activity,
                source = KioskSource.LOCAL_ADMIN,
                persistFlag = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "exitKioskModeCleanly — disableKioskMode failed", e)
            try {
                activity.stopLockTask()
            } catch (_: Exception) {
            }
        }

        // Route to the default TV launcher (CATEGORY_HOME), not a hard-coded package.
        var launchedHome = false
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(homeIntent)
            launchedHome = true
            Log.i(TAG, "exitKioskModeCleanly — CATEGORY_HOME launched")
        } catch (e: Exception) {
            Log.e(TAG, "exitKioskModeCleanly — HOME intent failed", e)
        }

        val moved = try {
            activity.moveTaskToBack(true)
        } catch (e: Exception) {
            Log.w(TAG, "exitKioskModeCleanly — moveTaskToBack failed", e)
            false
        }
        Log.i(
            TAG,
            "exitKioskModeCleanly — done home=$launchedHome moveTaskToBack=$moved",
        )
        return launchedHome || moved
    }

    private fun resolveActivity(context: Context): Activity? {
        if (context is Activity) return context
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun hasAdminOverride(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADMIN_OVERRIDE, false)

    fun clearAdminOverride(context: Context) {
        prefs(context).edit().putBoolean(KEY_ADMIN_OVERRIDE, false).apply()
        Log.i(TAG, "Admin override cleared — Remote Config may apply again")
    }

    fun markUserMinimized(context: Context) {
        prefs(context).edit().putBoolean(KEY_USER_MINIMIZED, true).apply()
        Log.d(TAG, "markUserMinimized")
    }

    fun clearUserMinimized(context: Context) {
        prefs(context).edit().putBoolean(KEY_USER_MINIMIZED, false).apply()
    }

    fun wasUserMinimized(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USER_MINIMIZED, false)

    /**
     * Call before launching YouTube / IPTV / other allowlisted apps so
     * Watchdog / [android.app.Activity.onUserLeaveHint] do not reclaim MainActivity.
     * Cleared only when the guest returns via HOME / BACK ([clearExternalAppActive]).
     */
    fun markExternalAppSession(context: Context, durationMs: Long = EXTERNAL_APP_SESSION_MS) {
        val until = System.currentTimeMillis() + durationMs
        prefs(context).edit()
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, true)
            .putLong(KEY_EXTERNAL_APP_UNTIL, until)
            .apply()
        // Leaving for OTT is intentional — do not treat as "user minimized for Home".
        clearUserMinimized(context)
        Log.i(TAG, "External app ACTIVE until=$until (${durationMs}ms)")
    }

    /**
     * True while guest is inside an intentionally launched OTT / allowlisted app.
     * Authoritative gate for Watchdog — survives nav pops / Entertainment dispose.
     */
    fun isExternalAppActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EXTERNAL_APP_ACTIVE, false)

    /** @deprecated Prefer [isExternalAppActive]; kept for existing call sites. */
    fun isExternalAppSessionActive(context: Context): Boolean {
        if (isExternalAppActive(context)) return true
        val until = prefs(context).getLong(KEY_EXTERNAL_APP_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    /**
     * Clears only the time window — does **not** clear [isExternalAppActive].
     * Safe for Entertainment enter; Watchdog still respects the durable flag.
     */
    fun clearExternalAppSession(context: Context) {
        prefs(context).edit().remove(KEY_EXTERNAL_APP_UNTIL).apply()
    }

    /**
     * Call from [HotelNavGraph] when Intro is shown / dismissed.
     * While true, reclaim may still bring MainActivity forward but must not
     * call navigateToHomeView / finishReturnFromExternalApp.
     */
    fun setIntroPlaybackActive(active: Boolean) {
        if (introPlaybackActive == active) return
        introPlaybackActive = active
        Log.i(TAG, "introPlaybackActive=$active")
    }

    fun isIntroPlaybackActive(): Boolean = introPlaybackActive

    /**
     * Reset when guest returns to hotel UI (HOME / BACK / onResume return path).
     * Resumes standard Watchdog reclaim behaviour.
     */
    fun clearExternalAppActive(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, false)
            .remove(KEY_EXTERNAL_APP_UNTIL)
            .remove(KEY_OTT_LAUNCHED_AT_MS)
            .apply()
        Log.i(TAG, "isExternalAppActive=false — Watchdog reclaim re-enabled")
    }

    /** Remember which OTT package was intentionally launched. */
    fun markOttLaunched(context: Context, packageName: String) {
        // Do NOT clear KEY_ON_GUEST_HOME — MainActivity may already have switched
        // to Root Home synchronously before startActivity (anti-flicker).
        // commit() so Watchdog / onUserLeaveHint see the flag on the same leave cycle.
        prefs(context).edit()
            .putString(KEY_LAST_OTT_PACKAGE, packageName)
            .putBoolean(KEY_MAIN_FOREGROUND, false)
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, true)
            .putLong(KEY_OTT_LAUNCHED_AT_MS, System.currentTimeMillis())
            .commit()
        markExternalAppSession(context)
        Log.i(TAG, "OTT launched → $packageName (isExternalAppActive=true)")
    }

    /**
     * True for a short window after [markOttLaunched] — MainActivity may briefly
     * resume during the handoff; do not clear [isExternalAppActive] yet.
     */
    fun isOttLaunchGracePeriod(context: Context, graceMs: Long = 20_000L): Boolean {
        val at = prefs(context).getLong(KEY_OTT_LAUNCHED_AT_MS, 0L)
        if (at <= 0L) return false
        return System.currentTimeMillis() - at < graceMs
    }

    /**
     * True when the last intentionally launched OTT / Live TV package is still
     * visible. Used to avoid clearing [isExternalAppActive] on spurious onResume.
     */
    fun isLastOttPackageVisible(context: Context): Boolean {
        val pkg = getLastOttPackage(context)?.trim().orEmpty()
        if (pkg.isEmpty()) return false
        return isPackageVisible(context, pkg)
    }

    /** True when [packageName] (or a `:subprocess`) is at least VISIBLE importance. */
    fun isPackageVisible(context: Context, packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        return try {
            val am = context.applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            val procs = am.runningAppProcesses ?: return false
            for (proc in procs) {
                val name = proc.processName ?: continue
                if (name != pkg && !name.startsWith("$pkg:")) continue
                if (proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    return true
                }
            }
            false
        } catch (t: Throwable) {
            Log.w(TAG, "isPackageVisible($pkg) failed", t)
            false
        }
    }

    fun getLastOttPackage(context: Context): String? =
        prefs(context).getString(KEY_LAST_OTT_PACKAGE, null)

    /**
     * Clears OTT session + durable active flag and suppresses auto re-launch briefly
     * (HOME / BACK return path only). Do **not** call from Entertainment dispose or
     * pre-OTT Root Home nav — that would let Watchdog steal focus from YouTube.
     */
    fun clearOttLaunchState(context: Context, suppressMs: Long = 2_500L) {
        val until = System.currentTimeMillis() + suppressMs
        prefs(context).edit()
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, false)
            .remove(KEY_EXTERNAL_APP_UNTIL)
            .remove(KEY_LAST_OTT_PACKAGE)
            .remove(KEY_OTT_LAUNCHED_AT_MS)
            .putLong(KEY_OTT_LAUNCH_SUPPRESS_UNTIL, until)
            .apply()
        Log.i(TAG, "OTT launch state cleared — isExternalAppActive=false, suppress until=$until")
    }

    fun shouldSuppressOttLaunch(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_OTT_LAUNCH_SUPPRESS_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    /** Called from MainActivity onResume / onPause. */
    fun setMainActivityForeground(context: Context, foreground: Boolean) {
        prefs(context).edit().putBoolean(KEY_MAIN_FOREGROUND, foreground).apply()
    }

    fun isMainActivityForeground(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MAIN_FOREGROUND, false)

    /**
     * Mark that MainActivity is handling [android.app.Activity.onNewIntent] /
     * [android.app.Activity.onResume] reclaim.
     *
     * Busy is **always** auto-cleared within [PHYSICAL_TV_LOOP_GUARD_MS] (50ms)
     * so Physical TV Home reclaim is never suppressed for seconds.
     */
    fun setReclaimLifecycleBusy(busy: Boolean) {
        mainHandler.removeCallbacks(clearReclaimBusyRunnable)
        reclaimLifecycleBusy = busy
        if (busy) {
            val holdMs = PHYSICAL_TV_LOOP_GUARD_MS
            reclaimQuietUntilMs = System.currentTimeMillis() + holdMs
            mainHandler.postDelayed(clearReclaimBusyRunnable, holdMs)
        }
        Log.d(TAG, "reclaimLifecycleBusy=$busy quietUntil=$reclaimQuietUntilMs")
    }

    fun isReclaimLifecycleBusy(): Boolean = reclaimLifecycleBusy

    /** True during the quiet window after a reclaim was just fired. */
    fun isInReclaimQuietPeriod(): Boolean =
        System.currentTimeMillis() < reclaimQuietUntilMs

    /** Elapsed ms since the last [forceBringToFront] / safe / urgent launch request. */
    fun millisSinceLastForceBring(): Long =
        System.currentTimeMillis() - lastForceBringAtMs

    /** Loop-guard window for [context] — 50ms on Physical TV and Device Owner. */
    fun loopGuardMs(context: Context): Long =
        if (needsPhysicalTvFallback(context)) PHYSICAL_TV_LOOP_GUARD_MS else SAFE_BRING_LOOP_GUARD_MS

    /** True when at least the context-appropriate loop guard has passed. */
    fun canForceBringAgain(context: Context): Boolean =
        millisSinceLastForceBring() >= loopGuardMs(context)

    /**
     * Loop-safe reclaim for lifecycle callbacks (onPause / onStop / focus-loss).
     * Suppresses rapid-fire calls and skips when MainActivity is already
     * foreground or actively handling onNewIntent / onResume.
     *
     * Prefer [forceBringToFrontPhysicalTvUrgent] on non–Device Owner TVs so
     * quiet/busy from a prior leave hint cannot stall Home reclaim for seconds.
     */
    fun forceBringToFrontSafely(
        context: Context,
        navigateToHome: Boolean = true,
        preferImmediateOptions: Boolean = false,
        ignoreTimedSuppress: Boolean = false,
    ): Boolean {
        if (shouldSkipKioskReclaim(
                "forceBringToFrontSafely",
                context,
                ignoreTimedSuppress = ignoreTimedSuppress,
            )
        ) {
            return false
        }
        val guardMs = loopGuardMs(context)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastForceBringAtMs < guardMs) {
            Log.d(
                TAG,
                "forceBringToFrontSafely suppressed — loop guard " +
                    "(${currentTime - lastForceBringAtMs}ms < ${guardMs}ms)",
            )
            return false
        }
        if (reclaimLifecycleBusy) {
            Log.d(TAG, "forceBringToFrontSafely skipped — onNewIntent/onResume in progress")
            return false
        }
        if (isInReclaimQuietPeriod()) {
            Log.d(TAG, "forceBringToFrontSafely skipped — reclaim quiet period")
            return false
        }
        // Already visible — do not startActivity again (causes onPause loop).
        if (isMainActivityForeground(context) && isProcessLifecycleStarted()) {
            Log.d(TAG, "forceBringToFrontSafely skipped — MainActivity already foreground")
            return false
        }

        lastForceBringAtMs = currentTime
        reclaimQuietUntilMs = currentTime + guardMs
        // Never snap to Root Home while staff PIN / settings are open.
        val home = navigateToHome && !isStaffAdminUiActive()
        return forceBringToFront(
            context = context,
            navigateToHome = home,
            skipDebounce = preferImmediateOptions,
            applyLoopGuard = false, // already applied above
            ignoreTimedSuppress = ignoreTimedSuppress,
        )
    }

    /**
     * True when an immediate Physical TV reclaim Intent was sent within [withinMs].
     * Uses wall-clock only — never a sticky flag that can block after the window.
     */
    fun wasReclaimIssuedRecently(withinMs: Long = RECLAIM_PENDING_GUARD_MS): Boolean {
        if (lastForceBringAtMs <= 0L) return false
        return System.currentTimeMillis() - lastForceBringAtMs < withinMs
    }

    /**
     * Physical TV (!Device Owner) urgent Home reclaim via high-priority PendingIntent.
     *
     * Never uses plain [Context.startActivity] — that path is throttled by ActivityManager
     * on Android TV and stalls MainActivity in ON_STOP for ~5s while GTPL shows.
     *
     * @param bypassDuplicateGuard when true (e.g. [android.app.Activity.onUserLeaveHint]),
     *   always send the PendingIntent immediately — no 50ms storm window skip.
     * @param ignoreTimedSuppress when true (Accessibility HOME), reclaim even during
     *   [suppressReclaimFor] windows such as the default-Home picker (180s).
     */
    fun forceBringToFrontPhysicalTvUrgent(
        context: Context,
        navigateToHome: Boolean = true,
        bypassDuplicateGuard: Boolean = false,
        ignoreTimedSuppress: Boolean = false,
    ): Boolean {
        if (shouldSkipKioskReclaim(
                "forceBringToFrontPhysicalTvUrgent",
                context,
                ignoreTimedSuppress = ignoreTimedSuppress,
            )
        ) {
            return false
        }
        if (!isKioskModeEnabled(context)) return false
        if (isDeviceOwner(context)) {
            return forceBringToFrontSafely(
                context = context,
                navigateToHome = navigateToHome && !isStaffAdminUiActive(),
                preferImmediateOptions = true,
            )
        }
        // HOME key reclaim must interrupt OTT / suppress windows — guest pressed Home.
        if (!ignoreTimedSuppress && isExternalAppActive(context)) {
            Log.d(TAG, "forceBringToFrontPhysicalTvUrgent skipped — OTT session")
            return false
        }

        val now = System.currentTimeMillis()
        val elapsedMs = if (lastForceBringAtMs <= 0L) {
            Long.MAX_VALUE
        } else {
            now - lastForceBringAtMs
        }

        if (!bypassDuplicateGuard && elapsedMs < RECLAIM_PENDING_GUARD_MS) {
            Log.d(
                TAG,
                "forceBringToFrontPhysicalTvUrgent skip duplicate " +
                    "(elapsed=${elapsedMs}ms < ${RECLAIM_PENDING_GUARD_MS}ms)",
            )
            return false
        }

        isReclaimPending = false
        mainHandler.removeCallbacks(clearReclaimPendingRunnable)

        if (reclaimLifecycleBusy) {
            mainHandler.removeCallbacks(clearReclaimBusyRunnable)
            reclaimLifecycleBusy = false
        }
        reclaimQuietUntilMs = 0L
        isReclaimPending = true
        lastForceBringAtMs = now
        mainHandler.postDelayed(clearReclaimPendingRunnable, RECLAIM_PENDING_GUARD_MS)

        // Kill any outgoing leave animation before the PendingIntent races the launcher.
        suppressTransitionFlash(context)

        Log.i(
            TAG,
            "forceBringToFrontPhysicalTvUrgent — PendingIntent IMMEDIATE " +
                "bypassGuard=$bypassDuplicateGuard " +
                "(elapsed was ${if (elapsedMs == Long.MAX_VALUE) "n/a" else "${elapsedMs}ms"})",
        )
        return sendPhysicalTvUrgentPendingIntent(
            context,
            navigateToHome && !isStaffAdminUiActive(),
        )
    }

    /**
     * High-priority PendingIntent reclaim for Physical TV.
     * Prefer this over [Context.startActivity] to avoid ActivityManager throttle / ON_STOP stall.
     */
    private fun sendPhysicalTvUrgentPendingIntent(
        context: Context,
        navigateToHome: Boolean,
    ): Boolean {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = reclaimIntentFlags()
            // Never CLEAR_TOP — destroys MainActivity mid-key-dispatch and breaks InputChannel.
            if (navigateToHome) {
                putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
            }
        }

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        return try {
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                PHYSICAL_TV_URGENT_REQUEST_CODE,
                intent,
                piFlags,
            )
            val options = buildReclaimActivityOptions(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent.send(
                    appContext,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options.toBundle(),
                )
            } else {
                @Suppress("DEPRECATION")
                pendingIntent.send()
            }
            suppressTransitionFlash(context)
            Log.i(
                TAG,
                "Physical TV reclaim via PendingIntent.send(no-anim ActivityOptions) " +
                    "api=${Build.VERSION.SDK_INT}",
            )
            true
        } catch (e: Exception) {
            // Do not fall back to plain startActivity — that triggers the 5s throttle stall.
            Log.e(TAG, "Physical TV PendingIntent reclaim failed (no startActivity fallback)", e)
            false
        }
    }

    /** Flags for every reclaim Intent — reorder existing task, never animate. */
    private fun reclaimIntentFlags(): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

    /**
     * No-animation ActivityOptions for reclaim.
     * Prefer [ActivityOptions.makeCustomAnimation] with resource ids **0, 0**
     * (Android treats 0 as "no animation") so the OS never paints a slow
     * fade/slide of the stock launcher while MainActivity reorders to front.
     */
    private fun buildReclaimActivityOptions(context: Context): ActivityOptions {
        val options = try {
            // Explicit 0,0 — documented "no animation" for custom transitions.
            ActivityOptions.makeCustomAnimation(context, 0, 0)
        } catch (t: Throwable) {
            Log.w(TAG, "makeCustomAnimation(0,0) failed — try kiosk_no_anim", t)
            try {
                ActivityOptions.makeCustomAnimation(
                    context,
                    R.anim.kiosk_no_anim,
                    R.anim.kiosk_no_anim,
                )
            } catch (t2: Throwable) {
                Log.w(TAG, "makeCustomAnimation(kiosk_no_anim) failed — makeBasic()", t2)
                ActivityOptions.makeBasic()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val mode = if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                options.setPendingIntentBackgroundActivityStartMode(mode)
            } catch (t: Throwable) {
                Log.w(TAG, "setPendingIntentBackgroundActivityStartMode failed (ignored)", t)
            }
        }
        return options
    }

    /** Zero out enter/exit window transitions on the calling Activity when possible. */
    private fun suppressTransitionFlash(context: Context) {
        if (context !is Activity) return
        try {
            @Suppress("DEPRECATION")
            context.overridePendingTransition(0, 0)
        } catch (_: Throwable) {
        }
    }

    /** Called from HotelNavGraph when the current route changes. */
    fun setOnGuestHomeScreen(context: Context, onHome: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON_GUEST_HOME, onHome).apply()
    }

    fun isOnGuestHomeScreen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ON_GUEST_HOME, false)

    /**
     * True ONLY when MainActivity is visible AND Compose is on the primary guest
     * Home route AND no OTT/external session is active.
     *
     * FALSE for YouTube / Live TV, Dining, Entertainment, Services, etc.
     */
    fun isOnRootHomeScreen(context: Context): Boolean {
        if (isExternalAppActive(context)) return false
        if (!isMainActivityForeground(context)) return false
        return isOnGuestHomeScreen(context)
    }

    /** @deprecated Prefer [isOnRootHomeScreen]. */
    fun isAlreadyOnForegroundGuestHome(context: Context): Boolean =
        isOnRootHomeScreen(context)

    /**
     * Call from [android.app.Application.onCreate] before UI starts.
     * If the previous process did not exit cleanly, arm crash recovery once.
     * Safe under Direct Boot — never throws into Application.onCreate.
     */
    fun onProcessStart(context: Context) {
        try {
            migrateIfNeeded(context)
            val p = prefs(context)
            val exitedCleanly = p.getBoolean(KEY_EXITED_CLEANLY, true)
            if (!exitedCleanly) {
                p.edit()
                    .putBoolean(KEY_PENDING_CRASH_RECOVERY, true)
                    .apply()
                Log.w(TAG, "Previous process exited uncleanly — crash recovery armed")
            }
            // New session starts unclean until ProcessLifecycle ON_STOP or explicit clean exit.
            p.edit().putBoolean(KEY_EXITED_CLEANLY, false).apply()
        } catch (e: Exception) {
            Log.w(TAG, "onProcessStart aborted (Direct Boot / encrypted storage)", e)
        }
    }

    /** Foreground session active — dying now without ON_STOP counts as unclean. */
    fun markSessionActive(context: Context) {
        prefs(context).edit().putBoolean(KEY_EXITED_CLEANLY, false).apply()
    }

    fun markCleanExit(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EXITED_CLEANLY, true)
            .apply()
    }

    fun markUnexpectedCrash(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EXITED_CLEANLY, false)
            .putBoolean(KEY_PENDING_CRASH_RECOVERY, true)
            .apply()
        Log.e(TAG, "markUnexpectedCrash")
    }

    fun consumeCrashRecovery(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING_CRASH_RECOVERY, false)) return false
        p.edit().putBoolean(KEY_PENDING_CRASH_RECOVERY, false).apply()
        return true
    }

    fun hasPendingCrashRecovery(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING_CRASH_RECOVERY, false)

    /** True when this process's UI is at least started (visible or partially visible). */
    fun isProcessLifecycleStarted(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)

    /**
     * True if the package already has a non-empty task (activity created / in recents).
     * Used to avoid duplicate startActivity when the app is merely backgrounded.
     */
    fun hasExistingTask(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.any { task ->
                val info = task.taskInfo
                val num = if (Build.VERSION.SDK_INT >= 23) {
                    info.numActivities
                } else {
                    @Suppress("DEPRECATION")
                    info.numActivities
                }
                num > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "hasExistingTask failed", e)
            false
        }
    }

    /**
     * Whether a background component may bring the hotel UI to the front.
     *
     * Allowed only when:
     * - Kiosk mode is explicitly ON, or
     * - A crash/kill recovery is pending
     *
     * Blocked when:
     * - Guest is in YouTube / OTT ([isExternalAppActive]), or
     * - User explicitly minimized (Home), or
     * - Process lifecycle is already STARTED, or
     * - An existing task is already created (unless crash recovery / kiosk needs reorder)
     */
    fun shouldBringAppToFront(context: Context, allowReorderIfKiosk: Boolean = true): Boolean {
        if (shouldSkipKioskReclaim("shouldBringAppToFront", context)) {
            return false
        }
        // Never reclaim UI while guest is in YouTube / OTT / Live TV under kiosk.
        if (shouldProtectExternalAppSession(context)) {
            Log.d(TAG, "shouldBringAppToFront=false (Live TV / OTT protected)")
            return false
        }

        // Intro playing + already foreground — do not re-fire NAVIGATE_TO_HOME intents.
        if (isIntroPlaybackActive() && isMainActivityForeground(context)) {
            Log.d(TAG, "shouldBringAppToFront=false (intro playing + already foreground)")
            return false
        }

        // Absolute block when kiosk is off — no watchdog / boot relaunch loops.
        if (!isKioskModeEnabled(context) && !hasPendingCrashRecovery(context)) {
            Log.d(TAG, "shouldBringAppToFront=false (kiosk off, no crash recovery)")
            return false
        }

        if (wasUserMinimized(context) && !isKioskModeEnabled(context)) {
            Log.d(TAG, "shouldBringAppToFront=false (user minimized, kiosk off)")
            return false
        }

        // MainActivity already foreground — Watchdog must not re-launch (log loop).
        if (isMainActivityForeground(context)) {
            Log.d(TAG, "shouldBringAppToFront=false (MainActivity already foreground)")
            return false
        }

        val kiosk = isKioskModeEnabled(context)
        val crash = hasPendingCrashRecovery(context)

        if (isProcessLifecycleStarted()) {
            Log.d(TAG, "shouldBringAppToFront=false (process already STARTED)")
            return false
        }

        if (hasExistingTask(context) && !(allowReorderIfKiosk && kiosk)) {
            Log.d(TAG, "shouldBringAppToFront=false (task already exists)")
            return false
        }

        Log.i(TAG, "shouldBringAppToFront=true (kiosk=$kiosk crash=$crash)")
        return true
    }

    /**
     * Safe startActivity for services / receivers. Returns false if blocked.
     * Uses [forceBringToFront] (PendingIntent) to avoid Android 10 BAL rejects.
     */
    fun startActivityIfAllowed(
        context: Context,
        intent: Intent,
        reason: String,
    ): Boolean {
        if (!shouldBringAppToFront(context)) {
            Log.i(TAG, "Blocked startActivity ($reason)")
            return false
        }
        val ok = forceBringToFront(
            context = context,
            navigateToHome = intent.getBooleanExtra(
                MainActivity.EXTRA_NAVIGATE_TO_HOME,
                true,
            ),
        )
        if (ok) {
            if (hasPendingCrashRecovery(context)) {
                consumeCrashRecovery(context)
            }
            clearUserMinimized(context)
            Log.i(TAG, "startActivity allowed via PendingIntent ($reason)")
        } else {
            Log.e(TAG, "startActivity failed ($reason)")
        }
        return ok
    }

    /**
     * Bring MainActivity to front under kiosk.
     *
     * Prefer [forceBringToFrontSafely] from Activity lifecycle callbacks to avoid
     * onPause ↔ onNewIntent infinite loops on physical TVs.
     *
     * Never finishes or recreates the Activity — only NEW_TASK | SINGLE_TOP so the
     * existing top instance is reused instead of spawning rapid new instances.
     *
     * @param skipDebounce when true, use [ActivityOptions.makeBasic] immediately
     *   (physical TV) — still respects the context loop guard unless [applyLoopGuard]
     *   is false (caller already guarded via [forceBringToFrontSafely] /
     *   [forceBringToFrontPhysicalTvUrgent]).
     * @param ignoreLifecycleBusy when true (physical TV urgent), do not bail on
     *   [reclaimLifecycleBusy] — only the short launch-request guard applies.
     */
    fun forceBringToFront(
        context: Context,
        navigateToHome: Boolean = true,
        requestCode: Int = FORCE_BRING_REQUEST_CODE,
        skipDebounce: Boolean = false,
        applyLoopGuard: Boolean = true,
        ignoreLifecycleBusy: Boolean = false,
        ignoreTimedSuppress: Boolean = false,
    ): Boolean {
        if (shouldSkipKioskReclaim(
                "forceBringToFront",
                context,
                ignoreTimedSuppress = ignoreTimedSuppress,
            )
        ) {
            return false
        }
        if (!isKioskModeEnabled(context)) return false
        if (!ignoreTimedSuppress && isExternalAppActive(context)) {
            Log.d(TAG, "forceBringToFront skipped — OTT/external session active")
            return false
        }
        if (reclaimLifecycleBusy && !ignoreLifecycleBusy) {
            Log.d(TAG, "forceBringToFront skipped — reclaim lifecycle busy")
            return false
        }

        // Hard minimum interval for every reclaim path (including skipDebounce callers
        // that already stamped lastForceBringAtMs — those pass applyLoopGuard=false).
        if (applyLoopGuard) {
            val now = System.currentTimeMillis()
            if (lastForceBringAtMs > 0L && now - lastForceBringAtMs < RECLAIM_PENDING_GUARD_MS) {
                Log.d(
                    TAG,
                    "forceBringToFront suppressed — min interval " +
                        "(${now - lastForceBringAtMs}ms < ${RECLAIM_PENDING_GUARD_MS}ms)",
                )
                return false
            }
        }

        val appContext = context.applicationContext
        val wantHome = navigateToHome && !isStaffAdminUiActive()
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = reclaimIntentFlags()
            if (wantHome) {
                putExtra(MainActivity.EXTRA_NAVIGATE_TO_HOME, true)
            }
        }

        // Physical TV / fast path: no-anim ActivityOptions, but still break pause/resume storms.
        if (skipDebounce) {
            if (applyLoopGuard) {
                val guardMs = maxOf(loopGuardMs(context), RECLAIM_PENDING_GUARD_MS)
                val now = System.currentTimeMillis()
                if (now - lastForceBringAtMs < guardMs) {
                    Log.d(
                        TAG,
                        "forceBringToFront skipDebounce suppressed — loop guard " +
                            "(${now - lastForceBringAtMs}ms < ${guardMs}ms)",
                    )
                    return false
                }
                lastForceBringAtMs = now
                reclaimQuietUntilMs = now + guardMs
            }
            Log.i(TAG, "forceBringToFront — no-anim ActivityOptions (physical TV / skipDebounce)")
            val started = startActivityImmediate(context, intent)
            if (started) return true
            Log.w(TAG, "forceBringToFront skipDebounce ActivityOptions failed — PendingIntent")
            return sendForceBringPendingIntent(context, appContext, intent, requestCode)
        }

        // ——— Android 12+ (API 31+): PendingIntent + debounce ———
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val now = System.currentTimeMillis()
            if (now - lastForceBringAtMs <= FORCE_BRING_DEBOUNCE_MS) {
                Log.d(TAG, "forceBringToFront debounced (${now - lastForceBringAtMs}ms) api=${Build.VERSION.SDK_INT}")
                return false
            }
            lastForceBringAtMs = now
            return sendForceBringPendingIntent(context, appContext, intent, requestCode)
        }

        // ——— Android 10 & 11 (API 29–30) only: ActivityOptions startActivity ———
        if (Build.VERSION.SDK_INT in 29..30) {
            if (applyLoopGuard) {
                val now = System.currentTimeMillis()
                if (now - lastForceBringAtMs < loopGuardMs(context)) {
                    Log.d(TAG, "forceBringToFront API29/30 suppressed — loop guard")
                    return false
                }
                lastForceBringAtMs = now
                reclaimQuietUntilMs = now + loopGuardMs(context)
            }
            return startActivityImmediate(context, intent)
        }

        // ——— API < 29 (Android 9): PendingIntent + debounce ———
        val now = System.currentTimeMillis()
        if (now - lastForceBringAtMs <= FORCE_BRING_DEBOUNCE_MS) {
            Log.d(TAG, "forceBringToFront debounced (${now - lastForceBringAtMs}ms) api=${Build.VERSION.SDK_INT}")
            return false
        }
        lastForceBringAtMs = now
        return sendForceBringPendingIntent(context, appContext, intent, requestCode)
    }

    /** Immediate startActivity via no-animation ActivityOptions — no debounce. */
    private fun startActivityImmediate(context: Context, intent: Intent): Boolean {
        return try {
            val options = buildReclaimActivityOptions(context)
            context.startActivity(intent, options.toBundle())
            suppressTransitionFlash(context)
            Log.i(TAG, "forceBringToFront via no-anim ActivityOptions api=${Build.VERSION.SDK_INT}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "no-anim ActivityOptions startActivity failed — plain startActivity", e)
            try {
                context.startActivity(intent)
                suppressTransitionFlash(context)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "forceBringToFront startActivity failed", e2)
                false
            }
        }
    }

    /** PendingIntent reclaim used by API 31+ and API &lt; 29. */
    private fun sendForceBringPendingIntent(
        context: Context,
        appContext: Context,
        intent: Intent,
        requestCode: Int,
    ): Boolean {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        return try {
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                requestCode,
                intent,
                piFlags,
            )
            val options = buildReclaimActivityOptions(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent.send(
                    appContext,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options.toBundle(),
                )
            } else {
                @Suppress("DEPRECATION")
                pendingIntent.send()
            }
            suppressTransitionFlash(context)
            Log.i(
                TAG,
                "forceBringToFront via PendingIntent (no-anim) " +
                    "(overlay=${Settings.canDrawOverlays(appContext)} api=${Build.VERSION.SDK_INT})",
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "forceBringToFront PendingIntent failed — fallback startActivity", e)
            try {
                context.startActivity(intent)
                suppressTransitionFlash(context)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "forceBringToFront startActivity failed", e2)
                false
            }
        }
    }

    /** Activity may navigate forward only while at least STARTED (not stopped/minimized). */
    fun canActivityNavigate(lifecycle: Lifecycle): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
