package `in`.pcncloud.hotel.integration

import android.content.Context
import android.content.Intent
import android.widget.Toast
import `in`.pcncloud.hotel.R

object OnyxIptvLauncher {

    const val PACKAGE_NAME = "com.onnet.systems.iptv.esto"

    fun launch(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        if (launchIntent != null) {
            context.startActivity(
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.onyx_iptv_not_installed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
