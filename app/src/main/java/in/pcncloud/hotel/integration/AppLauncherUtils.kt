package `in`.pcncloud.hotel.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

            if (!KioskPolicy.canLaunchApp(context, packageName)) {
                Log.w(TAG, "Blocked by kiosk whitelist → $packageName (silent)")
                KioskPolicy.denyExternalLaunchSilently(context, packageName)
                context.findMainActivity()?.onExternalLaunchBlocked(packageName)
                return
            }

            // 1) Synchronous Root Home switch BEFORE startActivity (no postDelayed).
            context.findMainActivity()?.switchToRootHomeBeforeOttLaunch()

            if (isAppInstalled(context, packageName)) {
                launchInstalledApp(context, packageName, appLabel)
            } else {
                openPlayStore(context, packageName, appLabel)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "launchOrInstall failed for $packageName — staying on MainActivity", t)
            try {
                KioskPolicy.clearOttLaunchState(context)
                KioskPolicy.denyExternalLaunchSilently(context, packageName)
            } catch (_: Throwable) {
            }
        }
    }

    private fun launchInstalledApp(context: Context, packageName: String, appLabel: String) {
        if (KioskLockTask.launchAllowlistedPackage(context, packageName)) {
            // Intentionally no code after successful startActivity.
            return
        }

        try {
            val intent = buildSafeLaunchIntent(context, packageName)
            if (intent == null) {
                Log.w(TAG, "No launch intent → $packageName")
                Toast.makeText(
                    context,
                    context.getString(R.string.entertainment_launch_failed, appLabel),
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            KioskPolicy.markOttLaunched(context, packageName)
            context.startActivity(intent)
            Log.i(TAG, "Launched $packageName (fallback)")
            // Intentionally no code after startActivity.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            KioskPolicy.clearOttLaunchState(context)
            Toast.makeText(
                context,
                context.getString(R.string.entertainment_launch_failed, appLabel),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun buildSafeLaunchIntent(context: Context, packageName: String): Intent? =
        KioskLockTask.buildSafeLaunchIntent(context, packageName)

    private fun openPlayStore(context: Context, packageName: String, appLabel: String) {
        Log.i(TAG, "Not installed — opening Play Store for $packageName")
        Toast.makeText(
            context,
            context.getString(R.string.entertainment_not_installed, appLabel),
            Toast.LENGTH_SHORT,
        ).show()

        if (KioskPolicy.isKioskModeEnabled(context)) {
            Toast.makeText(
                context,
                context.getString(R.string.entertainment_launch_failed, appLabel),
                Toast.LENGTH_LONG,
            ).show()
            Log.w(TAG, "Skip Play Store while kiosk Lock Task is ON → $packageName")
            return
        }

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )

        try {
            KioskPolicy.markExternalAppSession(context)
            context.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            try {
                KioskPolicy.markExternalAppSession(context)
                context.startActivity(webIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not open Play Store for $packageName", e)
                Toast.makeText(
                    context,
                    context.getString(R.string.entertainment_store_failed, appLabel),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

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
