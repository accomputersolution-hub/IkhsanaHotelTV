package `in`.pcncloud.hotel.kiosk

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Device-owner / device-admin component required for [DevicePolicyManager]
 * Lock Task APIs (`setLockTaskPackages`, `setLockTaskFeatures`).
 *
 * Provisioned as device owner on hotel TVs (ADB / EMM), not via the user "Activate admin" flow.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled — applying strict Lock Task features")
        applyStrictLockTaskFeatures(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.i(TAG, "Lock Task entering → pkg=$pkg")
        applyStrictLockTaskFeatures(context)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.i(TAG, "Lock Task exiting")
    }

    companion object {
        private const val TAG = "MyDeviceAdminReceiver"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, MyDeviceAdminReceiver::class.java)

        /**
         * Suppress system status bars / home / overview chrome while in Lock Task
         * so Home/Back inside YouTube cannot leak the stock Android TV launcher.
         */
        fun applyStrictLockTaskFeatures(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            try {
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = getComponentName(context)
                if (!dpm.isDeviceOwnerApp(context.packageName)) return
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                Log.i(TAG, "setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)")
            } catch (e: Exception) {
                Log.w(TAG, "applyStrictLockTaskFeatures failed", e)
            }
        }
    }
}
