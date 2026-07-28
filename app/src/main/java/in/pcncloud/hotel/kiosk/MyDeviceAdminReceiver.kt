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
 * Provision hotel TVs once via ADB (factory-reset device, no accounts):
 * `adb shell dpm set-device-owner in.pcncloud.hotel/in.pcncloud.hotel.kiosk.MyDeviceAdminReceiver`
 *
 * True Lock Task (Home / Recents suppressed) only works after Device Owner is set.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled — applying Lock Task policy")
        ensureSelfAllowlisted(context)
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

        /** Exact component string for `adb shell dpm set-device-owner`. */
        const val DEVICE_OWNER_COMPONENT =
            "in.pcncloud.hotel/in.pcncloud.hotel.kiosk.MyDeviceAdminReceiver"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, MyDeviceAdminReceiver::class.java)

        /**
         * Logs whether the admin receiver is registered and why Device Owner
         * provisioning may fail on this device (accounts, existing owner, etc.).
         */
        fun logProvisioningDiagnostics(context: Context) {
            val app = context.applicationContext
            val component = getComponentName(app)
            val pm = app.packageManager
            val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

            val receiverOk = try {
                val info = pm.getReceiverInfo(component, 0)
                info.enabled
            } catch (e: Exception) {
                Log.e(TAG, "Admin receiver NOT registered in PackageManager → $component", e)
                false
            }

            Log.i(
                TAG,
                "Device Owner diagnostics → component=$component receiverEnabled=$receiverOk " +
                    "installed=${isPackageInstalled(pm, app.packageName)} " +
                    "isDeviceOwner=${dpm.isDeviceOwnerApp(app.packageName)} " +
                    "adbCommand=adb shell dpm set-device-owner $DEVICE_OWNER_COMPONENT",
            )

            if (!receiverOk) {
                Log.e(
                    TAG,
                    "Fix manifest: receiver must export DEVICE_ADMIN_ENABLED + device_admin.xml",
                )
            }
            if (dpm.isDeviceOwnerApp(app.packageName)) {
                Log.i(TAG, "Already Device Owner — no provisioning needed")
            }
        }

        private fun isPackageInstalled(pm: android.content.pm.PackageManager, packageName: String): Boolean {
            return try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                false
            }
        }

        /** True when this package is provisioned as Device Owner. */
        fun isDeviceOwner(context: Context): Boolean {
            return try {
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.isDeviceOwnerApp(context.packageName)
            } catch (e: Exception) {
                Log.w(TAG, "isDeviceOwner check failed", e)
                false
            }
        }

        /**
         * Whitelists [extraPackages] + this app for Lock Task.
         * No-op when not Device Owner.
         */
        fun setLockTaskPackages(
            context: Context,
            extraPackages: List<String> = emptyList(),
        ): Boolean {
            return try {
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = getComponentName(context)
                if (!dpm.isDeviceOwnerApp(context.packageName)) {
                    Log.w(TAG, "Not Device Owner — cannot setLockTaskPackages")
                    return false
                }
                val packages = KioskLockTask.buildLockTaskPackageArray(context, extraPackages)
                dpm.setLockTaskPackages(admin, packages)
                applyStrictLockTaskFeatures(context)
                Log.i(TAG, "setLockTaskPackages → ${packages.toList()}")
                true
            } catch (e: Exception) {
                Log.w(TAG, "setLockTaskPackages failed", e)
                false
            }
        }

        /** Ensure hotel app alone is Lock-Task allowlisted (OTT from RTDB only). */
        fun ensureSelfAllowlisted(context: Context): Boolean =
            setLockTaskPackages(context, emptyList())

        /**
         * Suppress system status / nav / home chrome while in Lock Task
         * (`LOCK_TASK_FEATURE_NONE`) so the physical Home key cannot reach
         * the stock Android TV launcher.
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
