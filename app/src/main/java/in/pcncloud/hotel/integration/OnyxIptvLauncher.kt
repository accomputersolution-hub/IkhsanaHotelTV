package `in`.pcncloud.hotel.integration

import android.content.Context
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.KioskLockTask
import `in`.pcncloud.hotel.kiosk.KioskPolicy

object OnyxIptvLauncher {

    private const val TAG = "OnyxIptvLauncher"
    const val PACKAGE_NAME = "com.onnet.systems.iptv.esto"

    fun launch(context: Context) {
        if (!KioskPolicy.canLaunchApp(context, PACKAGE_NAME)) {
            Log.w(TAG, "Blocked by kiosk whitelist → $PACKAGE_NAME")
            Toast.makeText(
                context,
                context.getString(R.string.kiosk_app_not_allowed, "Live TV"),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        if (KioskLockTask.launchAllowlistedPackage(context, PACKAGE_NAME)) {
            return
        }
        Toast.makeText(
            context,
            context.getString(R.string.onyx_iptv_not_installed),
            Toast.LENGTH_LONG,
        ).show()
    }
}
