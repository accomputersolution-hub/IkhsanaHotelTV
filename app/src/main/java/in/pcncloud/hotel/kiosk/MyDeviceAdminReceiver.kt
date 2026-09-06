package `in`.pcncloud.hotel.kiosk

import android.content.ComponentName
import android.content.Context
import `in`.pcncloud.hotel.AdminReceiver

/**
 * Deprecated alias for [AdminReceiver].
 *
 * Device Owner must be provisioned against `in.pcncloud.hotel.AdminReceiver`:
 * ```
 * adb shell dpm set-device-owner <applicationId>/in.pcncloud.hotel.AdminReceiver
 * ```
 *
 * Kept so older call sites compile; all [ComponentName]s resolve to [AdminReceiver].
 */
@Deprecated(
    message = "Use in.pcncloud.hotel.AdminReceiver",
    replaceWith = ReplaceWith("AdminReceiver", "in.pcncloud.hotel.AdminReceiver"),
)
class MyDeviceAdminReceiver : AdminReceiver() {

    companion object {
        @Deprecated("Use AdminReceiver.DEVICE_OWNER_COMPONENT_HOTEL")
        const val DEVICE_OWNER_COMPONENT = AdminReceiver.DEVICE_OWNER_COMPONENT_HOTEL

        @Deprecated("Use AdminReceiver.DEVICE_OWNER_COMPONENT_CORPORATE")
        const val DEVICE_OWNER_COMPONENT_CORPORATE =
            AdminReceiver.DEVICE_OWNER_COMPONENT_CORPORATE

        fun getComponentName(context: Context): ComponentName =
            AdminReceiver.getComponentName(context)

        fun deviceOwnerAdbComponent(context: Context): String =
            AdminReceiver.deviceOwnerAdbComponent(context)

        fun logProvisioningDiagnostics(context: Context) =
            AdminReceiver.logProvisioningDiagnostics(context)

        fun isDeviceOwner(context: Context): Boolean =
            AdminReceiver.isDeviceOwner(context)

        fun ensureAlwaysOnWireGuardVpn(context: Context): Boolean =
            AdminReceiver.ensureAlwaysOnWireGuardVpn(context)

        fun clearAlwaysOnWireGuardVpn(context: Context): Boolean =
            AdminReceiver.clearAlwaysOnWireGuardVpn(context)

        fun setLockTaskPackages(
            context: Context,
            extraPackages: List<String> = emptyList(),
        ): Boolean = AdminReceiver.setLockTaskPackages(context, extraPackages)

        fun ensureSelfAllowlisted(context: Context): Boolean =
            AdminReceiver.ensureSelfAllowlisted(context)

        fun applyStrictLockTaskFeatures(context: Context) =
            AdminReceiver.applyStrictLockTaskFeatures(context)
    }
}
