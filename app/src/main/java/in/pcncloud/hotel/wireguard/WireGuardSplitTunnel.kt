package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import `in`.pcncloud.hotel.kiosk.KioskLockTask

/**
 * App-based split tunnel: only [KioskLockTask.LIVE_TV_PACKAGE] (Pro TV) is
 * routed into the WireGuard TUN. This hotel/corporate app stays on direct
 * internet (intro video, Firebase, peer API) so Splash timing is not blocked
 * by the tunnel.
 *
 * GoBackend maps [com.wireguard.config.Interface.Builder.includeApplication]
 * → [android.net.VpnService.Builder.addAllowedApplication].
 */
object WireGuardSplitTunnel {
    private const val TAG = "WireGuardSplitTunnel"

    /**
     * Packages that use the VPN — **Pro TV only** (`com.ektv.pro`).
     *
     * Uninstalled packages are skipped so [VpnService.Builder.addAllowedApplication]
     * does not throw [PackageManager.NameNotFoundException] and abort tunnel UP.
     */
    fun resolveIncludedApplications(context: Context): List<String> {
        val app = context.applicationContext
        val liveTv = KioskLockTask.LIVE_TV_PACKAGE.trim()
        val pm = app.packageManager

        val included = buildList {
            if (liveTv.isNotEmpty() && isPackageInstalled(pm, liveTv)) {
                add(liveTv)
            } else if (liveTv.isNotEmpty()) {
                Log.w(TAG, "Split-tunnel skip (Pro TV not installed): $liveTv")
            }
        }

        Log.i(
            TAG,
            "IncludedApplications=${included.joinToString().ifBlank { "(none)" }} " +
                "(self=${app.packageName} stays off-VPN, liveTv=$liveTv)",
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
