package `in`.pcncloud.hotel.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device-owner / device-admin component required for [android.app.admin.DevicePolicyManager]
 * APIs such as [android.app.admin.DevicePolicyManager.setLockTaskPackages].
 *
 * Provisioned as device owner on hotel TVs (ADB / EMM), not via the user "Activate admin" flow.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled")
    }

    companion object {
        private const val TAG = "MyDeviceAdminReceiver"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, MyDeviceAdminReceiver::class.java)
    }
}
