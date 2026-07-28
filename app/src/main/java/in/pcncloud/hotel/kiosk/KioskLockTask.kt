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
 * OTT apps (YouTube, Netflix, …) are allowlisted **only** via RTDB `allowedPackages`
 * for the current hotel — never via a hardcoded baseline.
 */
object KioskLockTask {

    private const val TAG = "KioskLockTask"

    /** YouTube TV package id — used only for leanback URI fallback when launching. */
    const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"

    /**
     * Essential Lock Task packages only (hotel launcher is always merged separately).
     * Must NOT include YouTube or any OTT — those come solely from Admin `allowedPackages`.
     */
    val BASELINE_LOCK_TASK_PACKAGES: List<String> = emptyList()

    fun adminComponent(context: Context): ComponentName =
        MyDeviceAdminReceiver.getComponentName(context)

    /**
     * Lock Task package set = hotel app + [extraPackages] (RTDB allowlist).
     * No hardcoded OTT packages.
     */
    fun buildLockTaskPackageArray(context: Context, extraPackages: List<String> = emptyList()): Array<String> =
        (listOf(context.packageName) + extraPackages + BASELINE_LOCK_TASK_PACKAGES)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toTypedArray()

    /**
     * Registers Lock Task packages from the hotel's RTDB `allowedPackages` only,
     * plus this hotel launcher package.
     */
    fun applyAllowlist(context: Context, firebasePackages: List<String>) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminName = adminComponent(context)
            val ourPackage = context.packageName

            if (!dpm.isDeviceOwnerApp(ourPackage)) {
                Log.w(TAG, "Not device owner — skip setLockTaskPackages / setLockTaskFeatures")
                return
            }

            val allowedApps = buildLockTaskPackageArray(context, firebasePackages)

            dpm.setLockTaskPackages(adminName, allowedApps)
            Log.i(TAG, "setLockTaskPackages (RTDB allowlist only) → ${allowedApps.toList()}")

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
     * Launch an allowlisted package under Lock Task with safe Intent flags.
     * @return true if startActivity was attempted successfully
     */
    fun launchAllowlistedPackage(context: Context, targetPackage: String): Boolean {
        if (!canLaunchApp(context, targetPackage)) {
            Log.w(TAG, "Refusing launch — kiosk ON and $targetPackage not whitelisted")
            return false
        }

        // Mark OTT session BEFORE leaving MainActivity so watchdog / onUserLeaveHint skip reclaim.
        // Sets KioskPolicy.isExternalAppActive = true (durable until HOME/BACK return).
        KioskPolicy.markOttLaunched(context, targetPackage)

        // Ensure target (e.g. YouTube) is Lock-Task allowlisted before launch.
        applyAllowlistForLaunch(context, targetPackage)

        ensureLockTaskActive(context)

        val intent = buildSafeLaunchIntent(context, targetPackage)

        if (intent == null) {
            Log.w(TAG, "No launch intent for $targetPackage")
            KioskPolicy.clearOttLaunchState(context)
            return false
        }

        return try {
            context.startActivity(intent)
            Log.i(TAG, "Launched allowlisted package under Lock Task → $targetPackage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $targetPackage", e)
            KioskPolicy.clearOttLaunchState(context)
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
     * Immediately sets Lock Task packages from the hotel Admin allowlist only.
     * [targetPackage] is included only if it is already in that allowlist
     * (launch is gated by [canLaunchApp] first).
     */
    private fun applyAllowlistForLaunch(context: Context, targetPackage: String) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminName = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            val adminList = KioskPolicy.getAllowedPackagesList(context)
            if (!adminList.contains(targetPackage.trim())) {
                Log.w(TAG, "applyAllowlistForLaunch skipped — $targetPackage not in allowedPackages")
                return
            }
            val packages = buildLockTaskPackageArray(context, adminList)
            dpm.setLockTaskPackages(adminName, packages)
            MyDeviceAdminReceiver.applyStrictLockTaskFeatures(context)
            Log.i(TAG, "Pre-launch setLockTaskPackages → ${packages.toList()}")
        } catch (e: Exception) {
            Log.w(TAG, "applyAllowlistForLaunch failed for $targetPackage", e)
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
