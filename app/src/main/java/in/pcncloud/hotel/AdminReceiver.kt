package `in`.pcncloud.hotel

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.kiosk.KioskLockTask

/**
 * Device Owner / Device Admin receiver.
 *
 * Component (must match ADB exactly):
 * ```
 * Hotel:     adb shell dpm set-device-owner in.pcncloud.hotel/in.pcncloud.hotel.AdminReceiver
 * Corporate: adb shell dpm set-device-owner in.pcncloud.corporate/in.pcncloud.hotel.AdminReceiver
 * ```
 *
 * Manifest requirements (all present):
 * - android:permission="android.permission.BIND_DEVICE_ADMIN"
 * - android:exported="true"
 * - intent-filter DEVICE_ADMIN_ENABLED
 * - meta-data android.app.device_admin → @xml/device_admin
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled — applying Lock Task + Always-On VPN policy")
        ensureSelfAllowlisted(context)
        applyStrictLockTaskFeatures(context)
        ensureAlwaysOnWireGuardVpn(context)
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
        private const val TAG = "AdminReceiver"

        /** Fully-qualified class name used in ADB / ComponentName. */
        const val CLASS_NAME = "in.pcncloud.hotel.AdminReceiver"

        /** Hotel Device Owner ADB component. */
        const val DEVICE_OWNER_COMPONENT_HOTEL =
            "in.pcncloud.hotel/$CLASS_NAME"

        /** Corporate Device Owner ADB component. */
        const val DEVICE_OWNER_COMPONENT_CORPORATE =
            "in.pcncloud.corporate/$CLASS_NAME"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, AdminReceiver::class.java)

        /** Flavor-aware ADB component: `<applicationId>/in.pcncloud.hotel.AdminReceiver`. */
        fun deviceOwnerAdbComponent(context: Context): String =
            "${context.packageName}/$CLASS_NAME"

        fun logProvisioningDiagnostics(context: Context) {
            val app = context.applicationContext
            val component = getComponentName(app)
            val pm = app.packageManager
            val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adbComponent = deviceOwnerAdbComponent(app)

            val receiverOk = try {
                pm.getReceiverInfo(component, 0).enabled
            } catch (e: Exception) {
                Log.e(TAG, "AdminReceiver NOT registered in PackageManager → $component", e)
                false
            }

            Log.i(
                TAG,
                "Device Owner diagnostics → component=$component receiverEnabled=$receiverOk " +
                    "installed=${isPackageInstalled(pm, app.packageName)} " +
                    "isDeviceOwner=${dpm.isDeviceOwnerApp(app.packageName)} " +
                    "adbCommand=adb shell dpm set-device-owner $adbComponent",
            )

            if (!receiverOk) {
                Log.e(
                    TAG,
                    "Fix manifest: AdminReceiver must be exported with " +
                        "DEVICE_ADMIN_ENABLED + @xml/device_admin meta-data",
                )
            }
            if (dpm.isDeviceOwnerApp(app.packageName)) {
                Log.i(TAG, "Already Device Owner — no provisioning needed")
            }
        }

        private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
            return try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

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

        fun ensureAlwaysOnWireGuardVpn(context: Context): Boolean {
            if (!BuildConfig.IS_CORPORATE) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.w(TAG, "setAlwaysOnVpnPackage requires API 24+ — skip")
                return false
            }
            return try {
                val app = context.applicationContext
                val dpm =
                    app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = getComponentName(app)
                if (!dpm.isDeviceOwnerApp(app.packageName)) {
                    Log.w(
                        TAG,
                        "Not Device Owner — cannot setAlwaysOnVpnPackage(self). " +
                            "Provision: adb shell dpm set-device-owner ${deviceOwnerAdbComponent(app)}",
                    )
                    return false
                }
                dpm.setAlwaysOnVpnPackage(admin, app.packageName, /* lockdownEnabled= */ false)
                Log.i(TAG, "Always-On VPN set → package=${app.packageName}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "setAlwaysOnVpnPackage failed", e)
                false
            }
        }

        fun clearAlwaysOnWireGuardVpn(context: Context): Boolean {
            if (!BuildConfig.IS_CORPORATE) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            return try {
                val app = context.applicationContext
                val dpm =
                    app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = getComponentName(app)
                if (!dpm.isDeviceOwnerApp(app.packageName)) return false
                dpm.setAlwaysOnVpnPackage(admin, null, false)
                Log.i(TAG, "Always-On VPN cleared")
                true
            } catch (e: Exception) {
                Log.w(TAG, "clearAlwaysOnWireGuardVpn failed", e)
                false
            }
        }

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

        fun ensureSelfAllowlisted(context: Context): Boolean =
            setLockTaskPackages(context, emptyList())

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
