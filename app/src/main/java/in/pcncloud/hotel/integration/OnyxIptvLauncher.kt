package `in`.pcncloud.hotel.integration

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.KioskPolicy

/**
 * Launches Onyx IPTV (ESTO) from the Live TV card.
 *
 * Marks [KioskPolicy.isExternalAppActive] **before** startActivity so Watchdog
 * and onUserLeaveHint do not reclaim MainActivity mid-viewing.
 */
object OnyxIptvLauncher {

    private const val TAG = "OnyxIptvLauncher"

    /** Primary Onyx / ESTO package on hotel and corporate boxes. */
    const val PACKAGE_NAME = "com.onnet.systems.iptv.esto"

    private val CANDIDATE_PACKAGES = listOf(
        PACKAGE_NAME,
        "com.onyxiptv",
        "com.onyx.iptv",
    )

    fun launch(context: Context) {
        val appContext = context.applicationContext
        try {
            val intent = resolveLaunchIntent(context)
            if (intent == null) {
                showNotInstalled(appContext)
                Log.w(TAG, "Onyx IPTV not installed — tried $CANDIDATE_PACKAGES")
                return
            }

            val launchedPackage = intent.component?.packageName
                ?: intent.`package`
                ?: PACKAGE_NAME

            KioskPolicy.markOttLaunched(context, launchedPackage)

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Launched Live TV → $launchedPackage (isExternalAppActive=true)")
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Onyx IPTV activity not found", e)
            try {
                KioskPolicy.clearOttLaunchState(context)
            } catch (_: Throwable) {
            }
            showNotInstalled(appContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Live TV launch failed", t)
            try {
                KioskPolicy.clearOttLaunchState(context)
            } catch (_: Throwable) {
            }
            showNotInstalled(appContext)
        }
    }

    /**
     * Explicit component when PackageManager can resolve MAIN / LEANBACK,
     * otherwise [PackageManager.getLaunchIntentForPackage] / leanback fallback.
     */
    fun resolveLaunchIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (pkg in CANDIDATE_PACKAGES) {
            explicitLauncherIntent(pm, pkg)?.let { return it }
            pm.getLeanbackLaunchIntentForPackage(pkg)?.let { return it }
            pm.getLaunchIntentForPackage(pkg)?.let { return it }
        }
        return null
    }

    private fun explicitLauncherIntent(pm: PackageManager, packageName: String): Intent? {
        val leanback = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(packageName)
        }
        val launcher = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val resolved = pm.resolveActivity(leanback, 0) ?: pm.resolveActivity(launcher, 0)
            ?: return null
        val activity = resolved.activityInfo ?: return null
        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(activity.packageName, activity.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun showNotInstalled(context: Context) {
        Toast.makeText(
            context,
            context.getString(R.string.onyx_iptv_not_installed),
            Toast.LENGTH_LONG,
        ).show()
    }
}
