package `in`.pcncloud.hotel.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.KioskLockTask
import `in`.pcncloud.hotel.kiosk.KioskPolicy

/**
 * Launches OTT apps safely.
 *
 * Order (focus-safe, no timers):
 * 1. Switch MainActivity to Root Home synchronously
 * 2. startActivity(YouTube) — nothing after that call
 */
object AppLauncherUtils {

    private const val TAG = "AppLauncherUtils"

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchOrInstall(context: Context, packageName: String, appLabel: String = packageName) {
        try {
            if (KioskPolicy.shouldSuppressOttLaunch(context)) {
                Log.w(TAG, "OTT launch suppressed after HOME/BACK return → $packageName")
                KioskPolicy.denyExternalLaunchSilently(context, packageName)
                return
            }

            // Resolve launch intent BEFORE leaving the Entertainment screen.
            val launchIntent = resolveLaunchIntent(context, packageName)
            if (launchIntent == null) {
                Log.w(TAG, "No launch intent — app missing/disabled → $packageName")
                showAppUnavailableToast(context)
                return
            }

            // 1) Synchronous Root Home switch BEFORE startActivity (no postDelayed).
            context.findMainActivity()?.switchToRootHomeBeforeOttLaunch()
            launchInstalledApp(context, packageName, appLabel, launchIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "ActivityNotFoundException for $packageName", e)
            showAppUnavailableToast(context)
        } catch (t: Throwable) {
            Log.e(TAG, "launchOrInstall failed for $packageName — staying on MainActivity", t)
            showAppUnavailableToast(context)
            try {
                KioskPolicy.clearOttLaunchState(context)
                KioskPolicy.denyExternalLaunchSilently(context, packageName)
            } catch (_: Throwable) {
            }
        }
    }

    /** Prefer [PackageManager.getLaunchIntentForPackage], then TV leanback-safe fallback. */
    fun resolveLaunchIntent(context: Context, packageName: String): Intent? {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: buildSafeLaunchIntent(context, packageName)
        } catch (_: Exception) {
            buildSafeLaunchIntent(context, packageName)
        }
    }

    private fun showAppUnavailableToast(context: Context) {
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.entertainment_app_unavailable),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun launchInstalledApp(
        context: Context,
        packageName: String,
        appLabel: String,
        preResolvedIntent: Intent? = null,
    ) {
        if (KioskLockTask.launchAllowlistedPackage(context, packageName)) {
            // Intentionally no code after successful startActivity.
            return
        }

        try {
            val intent = preResolvedIntent ?: resolveLaunchIntent(context, packageName)
            if (intent == null) {
                Log.w(TAG, "No launch intent → $packageName")
                showAppUnavailableToast(context)
                return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            KioskPolicy.markOttLaunched(context, packageName)
            KioskLockTask.applyLockTaskForLaunch(context, packageName)
            KioskLockTask.ensureLockTaskActive(context)
            context.startActivity(intent)
            Log.i(TAG, "Launched $packageName (fallback)")
            // Intentionally no code after startActivity.
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "ActivityNotFoundException launching $packageName", e)
            KioskPolicy.clearOttLaunchState(context)
            showAppUnavailableToast(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            KioskPolicy.clearOttLaunchState(context)
            showAppUnavailableToast(context)
        }
    }

    fun buildSafeLaunchIntent(context: Context, packageName: String): Intent? =
        KioskLockTask.buildSafeLaunchIntent(context, packageName)

    private fun Context.findMainActivity(): MainActivity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is MainActivity) return ctx
            if (ctx is Activity && ctx !is MainActivity) return null
            ctx = ctx.baseContext
        }
        return null
    }
}
