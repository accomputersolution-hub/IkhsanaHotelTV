package `in`.pcncloud.hotel.integration

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.KioskPolicy

/**
 * Launches an installed OTT / entertainment app, or opens its Play Store page
 * when the package is missing on the TV.
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

    /**
     * If [packageName] is installed → launch it.
     * Otherwise → open Google Play Store (market://, then https fallback).
     */
    fun launchOrInstall(context: Context, packageName: String, appLabel: String = packageName) {
        if (isAppInstalled(context, packageName)) {
            launchInstalledApp(context, packageName, appLabel)
        } else {
            openPlayStore(context, packageName, appLabel)
        }
    }

    private fun launchInstalledApp(context: Context, packageName: String, appLabel: String) {
        val pm = context.packageManager
        // Prefer Leanback launcher on Android TV; fall back to standard launch intent.
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)

        if (launchIntent == null) {
            Log.w(TAG, "Installed but no launch intent → $packageName")
            Toast.makeText(
                context,
                context.getString(R.string.entertainment_launch_failed, appLabel),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        try {
            // Allow leave without Home reclaim so OTT can stay in foreground.
            KioskPolicy.markExternalAppSession(context)
            context.startActivity(
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            Log.i(TAG, "Launched $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            Toast.makeText(
                context,
                context.getString(R.string.entertainment_launch_failed, appLabel),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openPlayStore(context: Context, packageName: String, appLabel: String) {
        Log.i(TAG, "Not installed — opening Play Store for $packageName")
        Toast.makeText(
            context,
            context.getString(R.string.entertainment_not_installed, appLabel),
            Toast.LENGTH_SHORT,
        ).show()

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            KioskPolicy.markExternalAppSession(context)
            context.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
}
