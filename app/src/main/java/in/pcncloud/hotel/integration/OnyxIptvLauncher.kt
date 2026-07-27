package `in`.pcncloud.hotel.integration

import android.content.Context
import android.widget.Toast
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.kiosk.KioskLockTask

object OnyxIptvLauncher {

    const val PACKAGE_NAME = "com.onnet.systems.iptv.esto"

    fun launch(context: Context) {
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
