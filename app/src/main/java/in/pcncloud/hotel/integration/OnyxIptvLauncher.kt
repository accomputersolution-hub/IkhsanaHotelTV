package `in`.pcncloud.hotel.integration

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.KioskPolicy

/**
 * Launches the hotel Live TV / IPTV app (Onyx ESTO).
 * Marks [KioskPolicy.isExternalAppActive] **before** startActivity so Watchdog
 * and onUserLeaveHint do not reclaim MainActivity mid-viewing.
 */
object OnyxIptvLauncher {

    private const val TAG = "OnyxIptvLauncher"
    const val PACKAGE_NAME = "com.onnet.systems.iptv.esto"

    fun launch(context: Context) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(PACKAGE_NAME)
                ?: pm.getLeanbackLaunchIntentForPackage(PACKAGE_NAME)

            if (intent == null) {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.onyx_iptv_not_installed),
                    Toast.LENGTH_LONG,
                ).show()
                Log.w(TAG, "No launch intent for $PACKAGE_NAME")
                return
            }

            // CRITICAL: set before startActivity so Watchdog / leave-hint skip reclaim.
            KioskPolicy.markOttLaunched(context, PACKAGE_NAME)

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Launched Live TV → $PACKAGE_NAME (isExternalAppActive=true)")
        } catch (t: Throwable) {
            Log.e(TAG, "Live TV launch failed", t)
            try {
                KioskPolicy.clearOttLaunchState(context)
            } catch (_: Throwable) {
            }
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.onyx_iptv_not_installed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
