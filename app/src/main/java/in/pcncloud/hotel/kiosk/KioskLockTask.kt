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
import `in`.pcncloud.hotel.integration.OnyxIptvLauncher

/**
 * Device-owner Lock Task helpers for hotel kiosk.
 *
 * Keeps Home/Back from escaping to the stock Android TV launcher while kiosk is ON,
 * including when guests are inside allowlisted apps (YouTube / Live TV).
 */
object KioskLockTask {

    private const val TAG = "KioskLockTask"

    const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"

    /** Baseline packages always merged into the Lock Task allowlist when kiosk is ON. */
    val BASELINE_ALLOWED_PACKAGES: List<String> = listOf(
        YOUTUBE_TV_PACKAGE,
        OnyxIptvLauncher.PACKAGE_NAME,
    )

    fun adminComponent(context: Context): ComponentName =
        MyDeviceAdminReceiver.getComponentName(context)

    /**
     * Registers Lock Task packages + suppresses system overlays/home chrome.
     * [firebasePackages] are merged with this app + baseline OTT packages.
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

            val allowedApps = (
                firebasePackages +
                    ourPackage +
                    BASELINE_ALLOWED_PACKAGES
                )
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toTypedArray()

            dpm.setLockTaskPackages(adminName, allowedApps)
            Log.i(TAG, "setLockTaskPackages → ${allowedApps.toList()}")

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
     * Launch an allowlisted package under Lock Task with safe Intent flags.
     * @return true if startActivity was attempted successfully
     */
    fun launchAllowlistedPackage(context: Context, targetPackage: String): Boolean {
        ensureLockTaskActive(context)

        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(targetPackage)
            ?: pm.getLaunchIntentForPackage(targetPackage)

        if (intent == null) {
            Log.w(TAG, "No launch intent for $targetPackage")
            return false
        }

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )

        // Still mark external session so onUserLeaveHint does not immediately reclaim
        // over the OTT UI while Lock Task keeps Home inside the allowlist.
        KioskPolicy.markExternalAppSession(context)

        return try {
            context.startActivity(intent)
            Log.i(TAG, "Launched allowlisted package under Lock Task → $targetPackage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $targetPackage", e)
            false
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
