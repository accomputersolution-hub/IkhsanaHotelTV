package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import `in`.pcncloud.hotel.kiosk.KioskLockTask

/**
 * App-based split tunnel: only these packages are routed into the WireGuard TUN
 * ([com.wireguard.config.Interface.Builder.includeApplication] →
 * [android.net.VpnService.Builder.addAllowedApplication]).
 *
 * Everything else uses the direct (non-VPN) network.
 */
object WireGuardSplitTunnel {
    private const val TAG = "WireGuardSplitTunnel"

    /**
     * Packages that must use the VPN:
     * 1. This app ([Context.getPackageName]) — peer API / control plane on the private net
     * 2. Pro TV / Live TV ([KioskLockTask.LIVE_TV_PACKAGE]) — same package as the Live TV card
     *
     * Uninstalled packages are skipped so [VpnService.Builder.addAllowedApplication]
     * does not throw [PackageManager.NameNotFoundException] and abort tunnel UP.
     */
    fun resolveIncludedApplications(context: Context): List<String> {
        val app = context.applicationContext
        val candidates = linkedSetOf(
            app.packageName.trim(),
            KioskLockTask.LIVE_TV_PACKAGE.trim(),
        ).filter { it.isNotEmpty() }

        val pm = app.packageManager
        val included = candidates.filter { pkg ->
            val installed = isPackageInstalled(pm, pkg)
            if (!installed) {
                Log.w(TAG, "Split-tunnel skip (not installed): $pkg")
            }
            installed
        }

        Log.i(
            TAG,
            "IncludedApplications=${included.joinToString()} " +
                "(self=${app.packageName}, liveTv=${KioskLockTask.LIVE_TV_PACKAGE})",
        )
        return included
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
}
