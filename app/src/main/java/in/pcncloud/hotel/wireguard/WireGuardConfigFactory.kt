package `in`.pcncloud.hotel.wireguard

import android.content.Context
import android.util.Log
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer

/**
 * Builds an official WireGuard [Config] from app-level [WireGuardTunnelConfig].
 *
 * When [context] is provided (or [WireGuardTunnelConfig.includedApplications] is set),
 * applies app-based split tunneling via [Interface.Builder.includeApplication] so
 * GoBackend maps them to [android.net.VpnService.Builder.addAllowedApplication].
 */
object WireGuardConfigFactory {
    private const val TAG = "WireGuardConfigFactory"

    fun build(config: WireGuardTunnelConfig, context: Context? = null): Config {
        require(config.isComplete()) { "WireGuard config incomplete" }

        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(config.privateKey.trim())
            .parseAddresses(config.address.trim())

        // DNS still applies to apps that use the TUN (split-tunnel included packages).
        val dnsServers = config.dns?.trim().orEmpty().ifBlank {
            WireGuardCredentials.DNS
        }
        ifaceBuilder.parseDnsServers(dnsServers)
        Log.i(TAG, "Interface DNS=$dnsServers address=${config.address}")

        config.mtu?.let { ifaceBuilder.setMtu(it) }

        val included = resolveIncludedApplications(config, context)
        if (included.isNotEmpty()) {
            ifaceBuilder.includeApplications(included)
            Log.i(TAG, "Split tunnel IncludedApplications=$included")
        } else {
            Log.w(TAG, "No IncludedApplications — tunnel would capture all apps; refusing empty list")
        }

        val peerBuilder = Peer.Builder()
            .parsePublicKey(config.peerPublicKey.trim())
            .parseEndpoint(config.endpoint.trim())
            .parseAllowedIPs(config.allowedIps.trim())

        val keepalive = if (config.persistentKeepalive > 0) {
            config.persistentKeepalive
        } else {
            WireGuardCredentials.PERSISTENT_KEEPALIVE
        }
        peerBuilder.setPersistentKeepalive(keepalive)
        Log.i(TAG, "Peer PersistentKeepalive=$keepalive endpoint=${config.endpoint}")

        config.preSharedKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
            peerBuilder.parsePreSharedKey(it)
        }

        return Config.Builder()
            .setInterface(ifaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }

    private fun resolveIncludedApplications(
        config: WireGuardTunnelConfig,
        context: Context?,
    ): List<String> {
        val fromConfig = config.includedApplications
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
        if (fromConfig.isNotEmpty()) return fromConfig
        if (context != null) return WireGuardSplitTunnel.resolveIncludedApplications(context)
        return emptyList()
    }
}
