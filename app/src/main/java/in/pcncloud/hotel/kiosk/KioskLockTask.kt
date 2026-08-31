package `in`.pcncloud.hotel.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Device-owner Lock Task helpers for hotel kiosk.
 *
 * Keeps Home/Back from escaping to the stock Android TV launcher while kiosk is ON.
 * Any OTT app (YouTube, Hotstar, Netflix, …) may be launched; [applyLockTaskForLaunch]
 * adds the target package to DPM Lock Task right before startActivity.
 * Live TV (EKTV Pro) stays in the idle baseline.
 */
object KioskLockTask {

    private const val TAG = "KioskLockTask"

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

    /**
     * Lock Task package set = hotel app + [extraPackages] + baseline (Live TV).
     * [extraPackages] is usually the OTT being launched, not an Admin whitelist.
     */
    fun buildLockTaskPackageArray(context: Context, extraPackages: List<String> = emptyList()): Array<String> =
        (listOf(context.packageName) + extraPackages + baselineLockTaskPackages())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toTypedArray()

    /** DPM whitelist for launching [targetPackage] under kiosk without blocking other apps later. */
    fun buildLockTaskPackageArrayForLaunch(context: Context, targetPackage: String): Array<String> =
        buildLockTaskPackageArray(context, listOf(targetPackage.trim()))

    /** Live TV baseline lock-task packages. */
    fun baselineLockTaskPackages(): List<String> = BASELINE_LOCK_TASK_PACKAGES

    /**
     * Registers minimal Lock Task packages (hotel launcher + Live TV baseline).
     * RTDB `allowedPackages` is persisted for Admin but no longer gates guest launches.
     */
    fun applyAllowlist(context: Context, firebasePackages: List<String>) {
        // Never re-pin a DPM whitelist while kiosk is OFF (Android 9/11 OTT block).
        if (!KioskPolicy.isKioskModeEnabled(context)) {
            KioskPolicy.clearDeviceOwnerLockTaskPackages(context)
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

            val allowedApps = buildLockTaskPackageArray(context, emptyList())

            dpm.setLockTaskPackages(adminName, allowedApps)
            Log.i(
                TAG,
                "setLockTaskPackages (idle baseline, RTDB count=${firebasePackages.size}) " +
                    "→ ${allowedApps.toList()}",
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
            !context.getSharedPreferences("hotel_tv_kiosk", Context.MODE_PRIVATE)
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

            val intent = buildSafeLaunchIntent(context, targetPackage)

            if (intent == null) {
                Log.w(TAG, "No launch intent for $targetPackage")
                KioskPolicy.clearOttLaunchState(context)
                return false
            }

            try {
                context.startActivity(intent)
                Log.i(TAG, "Launched allowlisted package under Lock Task 뿯↽ $targetPackage")
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
     * Whitelist [targetPackage] for Lock Task before launching any external app.
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

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
