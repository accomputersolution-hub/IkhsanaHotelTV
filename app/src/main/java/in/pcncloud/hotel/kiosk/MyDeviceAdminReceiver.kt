package `in`.pcncloud.hotel.kiosk

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import `in`.pcncloud.hotel.BuildConfig

/**
 * Device-owner / device-admin component required for [DevicePolicyManager]
 * Lock Task APIs (`setLockTaskPackages`, `setLockTaskFeatures`) and Always-On VPN.
 *
 * Provision TVs once via ADB (factory-reset device, no accounts):
 * ```
 * adb shell dpm set-device-owner <applicationId>/in.pcncloud.hotel.kiosk.MyDeviceAdminReceiver
 * ```
 * Hotel: `in.pcncloud.hotel/...`
 * Corporate: `in.pcncloud.corporate/...`
 *
 * True Lock Task (Home / Recents suppressed) only works after Device Owner is set.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled — applying Lock Task + Always-On VPN policy")
        ensureSelfAllowlisted(context)
        applyStrictLockTaskFeatures(context)
        ensureAlwaysOnTailscaleVpn(context)
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

        /** Tailscale package used for corporate Always-On VPN. */
        const val TAILSCALE_VPN_PACKAGE = "com.tailscale.ipn"

        /** Exact component string for hotel `adb shell dpm set-device-owner`. */
        const val DEVICE_OWNER_COMPONENT =
            "in.pcncloud.hotel/in.pcncloud.hotel.kiosk.MyDeviceAdminReceiver"

        /** Exact component string for corporate `adb shell dpm set-device-owner`. */
        const val DEVICE_OWNER_COMPONENT_CORPORATE =
            "in.pcncloud.corporate/in.pcncloud.hotel.kiosk.MyDeviceAdminReceiver"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, MyDeviceAdminReceiver::class.java)

        /** Flavor-aware ADB component for Device Owner provisioning. */
        fun deviceOwnerAdbComponent(context: Context): String =
            "${context.packageName}/in.pcncloud.hotel.kiosk.MyDeviceAdminReceiver"

        /**
         * Logs whether the admin receiver is registered and why Device Owner
         * provisioning may fail on this device (accounts, existing owner, etc.).
         */
        fun logProvisioningDiagnostics(context: Context) {
            val app = context.applicationContext
            val component = getComponentName(app)
            val pm = app.packageManager
            val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adbComponent = deviceOwnerAdbComponent(app)

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
                    "adbCommand=adb shell dpm set-device-owner $adbComponent",
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

        private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
            return try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
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
         * Corporate Device Owner only: set Tailscale as the system Always-On VPN
         * (`lockdownEnabled=false` so traffic is not blocked if VPN is down).
         *
         * Hotel flavor: no-op.
         * Requires API 24+ and Tailscale installed with a [android.net.VpnService].
         */
        fun ensureAlwaysOnTailscaleVpn(context: Context): Boolean {
            if (!BuildConfig.IS_CORPORATE) {
                return false
            }
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
                        "Not Device Owner — cannot setAlwaysOnVpnPackage($TAILSCALE_VPN_PACKAGE). " +
                            "Provision with: adb shell dpm set-device-owner ${deviceOwnerAdbComponent(app)}",
                    )
                    return false
                }

                if (!isPackageInstalled(app.packageManager, TAILSCALE_VPN_PACKAGE)) {
                    Log.w(
                        TAG,
                        "Tailscale ($TAILSCALE_VPN_PACKAGE) not installed — skip Always-On VPN",
                    )
                    return false
                }

                // lockdownEnabled=false → do not block networking if VPN disconnects.
                dpm.setAlwaysOnVpnPackage(admin, TAILSCALE_VPN_PACKAGE, /* lockdownEnabled= */ false)

                val active = try {
                    dpm.getAlwaysOnVpnPackage(admin)
                } catch (_: Throwable) {
                    null
                }
                Log.i(
                    TAG,
                    "Always-On VPN set → package=$TAILSCALE_VPN_PACKAGE lockdown=false " +
                        "activePackage=$active",
                )
                true
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(
                    TAG,
                    "setAlwaysOnVpnPackage failed — Tailscale missing or no VpnService",
                    e,
                )
                false
            } catch (e: SecurityException) {
                Log.e(TAG, "setAlwaysOnVpnPackage SecurityException — not Device Owner?", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "setAlwaysOnVpnPackage failed", e)
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
