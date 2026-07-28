package `in`.pcncloud.hotel.integration

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import `in`.pcncloud.hotel.MainActivity
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

        // Synchronous Root Home BEFORE startActivity — no timers after launch.
        context.findMainActivity()?.switchToRootHomeBeforeOttLaunch()

        if (KioskLockTask.launchAllowlistedPackage(context, PACKAGE_NAME)) {
            return
        }
        Toast.makeText(
            context,
            context.getString(R.string.onyx_iptv_not_installed),
            Toast.LENGTH_LONG,
        ).show()
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
