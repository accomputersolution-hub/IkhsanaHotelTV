package `in`.pcncloud.hotel.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun migrateIfNeeded(context: Context) {
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
        val cleaned = packages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        prefs(context).edit()
            .putStringSet(KEY_ALLOWED_PACKAGES, cleaned)
            .putString(KEY_ALLOWED_PACKAGES_HOTEL_ID, normalizedHotel)
            .apply()
        Log.i(
            TAG,
            "allowedPackages hotelId=$normalizedHotel count=${cleaned.size} → $cleaned",
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
            Log.d(TAG, "getAllowedPackagesList — unpaired → emptyList()")
            return emptyList()
        }
        val cachedHotel = prefs(context).getString(KEY_ALLOWED_PACKAGES_HOTEL_ID, null)
        if (cachedHotel.isNullOrBlank() || cachedHotel != currentHotel) {
            Log.w(
                TAG,
                "getAllowedPackagesList — cache miss/mismatch " +
                    "cached=$cachedHotel current=$currentHotel → emptyList()",
            )
            return emptyList()
        }
        return prefs(context).getStringSet(KEY_ALLOWED_PACKAGES, emptySet())
            ?.toList()
            .orEmpty()
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
     * When Kiosk Mode is ON → only packages explicitly in **this hotel's** Admin
     * `allowedPackages` (YouTube / Netflix / etc. have no hardcoded bypass).
     */
    fun canLaunchApp(context: Context, targetPackageName: String): Boolean {
        if (!isKioskModeEnabled(context)) return true
        val target = targetPackageName.trim()
        val allowed = getAllowedPackagesList(context)
        val ok = allowed.contains(target)
        if (!ok) {
            Log.w(
                TAG,
                "Blocked launch of $target — not in hotel allowedPackages ($allowed)",
            )
        }
        return ok
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

        // Keep watchdog aligned with the flag.
        if (enabled) {
            KioskWatchdogService.start(context.applicationContext)
        } else {
            KioskWatchdogService.stop(context.applicationContext)
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
     * Reset when guest returns to hotel UI (HOME / BACK / onResume return path).
     * Resumes standard Watchdog reclaim behaviour.
     */
    fun clearExternalAppActive(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, false)
            .remove(KEY_EXTERNAL_APP_UNTIL)
            .apply()
        Log.i(TAG, "isExternalAppActive=false — Watchdog reclaim re-enabled")
    }

    /** Remember which OTT package was intentionally launched. */
    fun markOttLaunched(context: Context, packageName: String) {
        // Do NOT clear KEY_ON_GUEST_HOME — MainActivity may already have switched
        // to Root Home synchronously before startActivity (anti-flicker).
        prefs(context).edit()
            .putString(KEY_LAST_OTT_PACKAGE, packageName)
            .putBoolean(KEY_MAIN_FOREGROUND, false)
            .putBoolean(KEY_EXTERNAL_APP_ACTIVE, true)
            .apply()
        markExternalAppSession(context)
        Log.i(TAG, "OTT launched → $packageName (isExternalAppActive=true)")
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
     */
    fun onProcessStart(context: Context) {
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
        // Never reclaim UI while guest is in YouTube / OTT under kiosk.
        if (isExternalAppActive(context)) {
            Log.d(TAG, "shouldBringAppToFront=false (isExternalAppActive=true)")
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
     * Always adds NEW_TASK when starting from non-Activity context.
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
        return try {
            val launch = Intent(intent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(launch)
            if (hasPendingCrashRecovery(context)) {
                consumeCrashRecovery(context)
            }
            clearUserMinimized(context)
            Log.i(TAG, "startActivity allowed ($reason)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startActivity failed ($reason)", e)
            false
        }
    }

    /** Activity may navigate forward only while at least STARTED (not stopped/minimized). */
    fun canActivityNavigate(lifecycle: Lifecycle): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}
