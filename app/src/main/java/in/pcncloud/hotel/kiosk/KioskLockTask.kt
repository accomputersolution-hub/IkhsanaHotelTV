package `in`.pcncloud.hotel.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * Device-owner Lock Task helpers for hotel kiosk.
 *
 * Keeps Home/Back from escaping to the stock Android TV launcher while kiosk is ON.
 * Any OTT app may be launched; each launch is added to a **session** DPM whitelist so
 * RTDB/idle refreshes cannot shrink Lock Task packages and leak HOME to the box launcher.
 * Live TV (EKTV Pro) stays in the baseline.
 */
object KioskLockTask {

    private const val TAG = "KioskLockTask"
    private const val PREFS = "hotel_tv_kiosk"
    /** Packages launched this kiosk session — merged into every setLockTaskPackages call. */
    private const val KEY_SESSION_LOCK_TASK_PACKAGES = "sessionLockTaskPackages"

    /** YouTube TV package id — used only for leanback URI fallback when launching. */
    const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"

    /** In-room Live TV / IPTV app — must stay Lock-Task allowlisted under kiosk. */
    const val LIVE_TV_PACKAGE = "com.ektv.pro"

    /**
     * Essential Lock Task packages always merged with the hotel launcher.
     * Live TV is included so Lock Task Mode does not silently block IPTV.
     */
    val BASELINE_LOCK_TASK_PACKAGES: List<String> = listOf(
        LIVE_TV_PACKAGE,
    )

    fun adminComponent(context: Context): ComponentName =
        MyDeviceAdminReceiver.getComponentName(context)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Packages opened via Entertainment / Live TV during this kiosk session. */
    fun getSessionLockTaskPackages(context: Context): List<String> =
        prefs(context).getStringSet(KEY_SESSION_LOCK_TASK_PACKAGES, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()

    fun rememberSessionLockTaskPackage(context: Context, packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty() || pkg == context.packageName) return
        val next = (getSessionLockTaskPackages(context) + pkg).toSet()
        prefs(context).edit()
            .putStringSet(KEY_SESSION_LOCK_TASK_PACKAGES, next)
            .apply()
        Log.i(TAG, "sessionLockTaskPackages += $pkg → $next")
    }

    fun clearSessionLockTaskPackages(context: Context) {
        prefs(context).edit().remove(KEY_SESSION_LOCK_TASK_PACKAGES).apply()
        Log.i(TAG, "sessionLockTaskPackages cleared")
    }

    /**
     * Full DPM whitelist: hotel app + Live TV + Admin RTDB list + session-launched OTTs
     * + any [extraPackages] for this call.
     *
     * Never shrink to "idle baseline only" — that dropped OTT from Lock Task and let
     * HOME escape to the Android box launcher.
     */
    fun buildEffectiveLockTaskPackages(
        context: Context,
        extraPackages: List<String> = emptyList(),
    ): Array<String> {
        val adminList = runCatching {
            KioskPolicy.getAllowedPackagesList(context)
        }.getOrDefault(emptyList())
        val session = getSessionLockTaskPackages(context)
        return (
            listOf(context.packageName) +
                adminList +
                session +
                extraPackages +
                baselineLockTaskPackages()
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toTypedArray()
    }

    /**
     * Lock Task package set = hotel app + [extraPackages] + baseline + session + RTDB.
     */
    fun buildLockTaskPackageArray(context: Context, extraPackages: List<String> = emptyList()): Array<String> =
        buildEffectiveLockTaskPackages(context, extraPackages)

    /** DPM whitelist including [targetPackage] (also remembered for the session). */
    fun buildLockTaskPackageArrayForLaunch(context: Context, targetPackage: String): Array<String> {
        rememberSessionLockTaskPackage(context, targetPackage)
        return buildEffectiveLockTaskPackages(context, listOf(targetPackage.trim()))
    }

    /** Live TV baseline lock-task packages. */
    fun baselineLockTaskPackages(): List<String> = BASELINE_LOCK_TASK_PACKAGES

    /**
     * Push the effective Lock Task whitelist (never drops session OTTs).
     * [firebasePackages] are merged in addition to the persisted Admin list / session.
     */
    fun applyAllowlist(context: Context, firebasePackages: List<String>) {
        // Never re-pin a DPM whitelist while kiosk is OFF (Android 9/11 OTT block).
        if (!KioskPolicy.isKioskModeEnabled(context)) {
            KioskPolicy.clearDeviceOwnerLockTaskPackages(context)
            clearSessionLockTaskPackages(context)
            Log.d(TAG, "applyAllowlist skipped — kiosk OFF, DPM packages cleared")
            return
        }
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminName = adminComponent(context)
            val ourPackage = context.packageName

            if (!dpm.isDeviceOwnerApp(ourPackage)) {
                Log.w(TAG, "Not device owner — skip setLockTaskPackages / setLockTaskFeatures")
                return
            }

            val allowedApps = buildEffectiveLockTaskPackages(context, firebasePackages)

            dpm.setLockTaskPackages(adminName, allowedApps)
            Log.i(
                TAG,
                "setLockTaskPackages (effective) → ${allowedApps.toList()}",
            )

            // API 28+: hide status / nav / home affordances that can leak native TV UI.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminName,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
                )
                Log.i(TAG, "setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Lock Task policy security exception", e)
        } catch (e: Exception) {
            Log.w(TAG, "Lock Task policy apply failed", e)
        }
    }

    /** True when this process is currently in Lock Task / screen pinning. */
    fun isInLockTask(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                @Suppress("DEPRECATION")
                am.isInLockTaskMode
            }
        } catch (e: Exception) {
            Log.w(TAG, "isInLockTask check failed", e)
            false
        }
    }

    /**
     * Ensure Lock Task is active before launching an allowlisted external app.
     * Must be called from an [Activity] context when kiosk is enabled.
     */
    fun ensureLockTaskActive(context: Context) {
        if (!KioskPolicy.isKioskModeEnabled(context) &&
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("isKioskModeEnabled", false)
        ) {
            return
        }
        val activity = context.findActivity() ?: run {
            Log.w(TAG, "ensureLockTaskActive: no Activity context")
            return
        }
        if (isInLockTask(activity)) {
            Log.d(TAG, "Lock Task already active")
            return
        }
        try {
            activity.startLockTask()
            Log.i(TAG, "startLockTask() before external launch")
        } catch (e: Exception) {
            Log.w(TAG, "startLockTask before launch failed", e)
            e.printStackTrace()
        }
    }

    /**
     * Same as [KioskPolicy.canLaunchApp] — convenience for launch call sites.
     */
    fun canLaunchApp(context: Context, targetPackageName: String): Boolean =
        KioskPolicy.canLaunchApp(context, targetPackageName)

    /**
     * Launch any installed package under Lock Task with safe Intent flags.
     * @return true if startActivity was attempted successfully
     */
    fun launchAllowlistedPackage(context: Context, targetPackage: String): Boolean {
        return try {
            val target = targetPackage.trim()
            if (target.isEmpty()) return false
            if (!canLaunchApp(context, target)) {
                Log.w(TAG, "Refusing launch — invalid/empty package under kiosk")
                KioskPolicy.denyExternalLaunchSilently(context, targetPackage)
                return false
            }

            // Mark OTT session BEFORE leaving MainActivity so watchdog / onUserLeaveHint skip reclaim.
            KioskPolicy.markOttLaunched(context, target)

            // Add target to DPM Lock Task so Android 9/11 do not block with "Unauthorized by Admin".
            applyLockTaskForLaunch(context, target)

            ensureLockTaskActive(context)

            val intent = buildSafeLaunchIntent(context, target)

            if (intent == null) {
                Log.w(TAG, "No launch intent for $targetPackage")
                KioskPolicy.clearOttLaunchState(context)
                return false
            }

            try {
                context.startActivity(intent)
                Log.i(TAG, "Launched package under Lock Task → $target")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch $targetPackage", e)
                KioskPolicy.clearOttLaunchState(context)
                KioskPolicy.denyExternalLaunchSilently(context, targetPackage)
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "launchAllowlistedPackage crashed for $targetPackage — recovering", t)
            try {
                KioskPolicy.clearOttLaunchState(context)
                KioskPolicy.denyExternalLaunchSilently(context, targetPackage)
            } catch (_: Throwable) {
            }
            false
        }
    }

    /**
     * Safe OTT launch Intent: leanback/launch intent, or YouTube TV URI fallback.
     * Flags: NEW_TASK | RESET_TASK_IF_NEEDED.
     */
    fun buildSafeLaunchIntent(context: Context, targetPackage: String): Intent? {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(targetPackage)
            ?: pm.getLaunchIntentForPackage(targetPackage)
            ?: if (targetPackage == YOUTUBE_TV_PACKAGE) {
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/tv"),
                )
            } else {
                null
            }

        return intent?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
        }
    }

    /**
     * Remember [targetPackage] for the session and push the full effective whitelist.
     */
    fun applyLockTaskForLaunch(context: Context, targetPackage: String) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminName = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            val packages = buildLockTaskPackageArrayForLaunch(context, targetPackage)
            dpm.setLockTaskPackages(adminName, packages)
            MyDeviceAdminReceiver.applyStrictLockTaskFeatures(context)
            Log.i(TAG, "Pre-launch setLockTaskPackages → ${packages.toList()}")
        } catch (e: Exception) {
            Log.w(TAG, "applyLockTaskForLaunch failed for $targetPackage", e)
        }
    }

    /**
     * Re-assert DPM whitelist + features after HOME reclaim so Lock Task stays pinned
     * on the hotel app (never empty / baseline-only shrink).
     */
    fun reassertLockTaskPackages(context: Context) {
        if (!KioskPolicy.isKioskModeEnabled(context)) return
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminName = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return
            val packages = buildEffectiveLockTaskPackages(context)
            dpm.setLockTaskPackages(adminName, packages)
            MyDeviceAdminReceiver.applyStrictLockTaskFeatures(context)
            Log.i(TAG, "reassertLockTaskPackages → ${packages.toList()}")
        } catch (e: Exception) {
            Log.w(TAG, "reassertLockTaskPackages failed", e)
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
