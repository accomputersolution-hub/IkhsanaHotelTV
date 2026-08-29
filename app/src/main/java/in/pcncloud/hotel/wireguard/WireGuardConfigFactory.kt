package `in`.pcncloud.hotel.wireguard

import android.util.Log
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer

/**
 * Builds an official WireGuard [Config] from app-level [WireGuardTunnelConfig].
 */
object WireGuardConfigFactory {
    private const val TAG = "WireGuardConfigFactory"

    fun build(config: WireGuardTunnelConfig): Config {
        require(config.isComplete()) { "WireGuard config incomplete" }

        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(config.privateKey.trim())
            .parseAddresses(config.address.trim())

        // Always install DNS on the TUN — Android TV otherwise has no resolver
        // when AllowedIPs is 0.0.0.0/0 (full tunnel).
        val dnsServers = config.dns?.trim().orEmpty().ifBlank {
            WireGuardCredentials.DNS
        }
        ifaceBuilder.parseDnsServers(dnsServers)
        Log.i(TAG, "Interface DNS=$dnsServers address=${config.address}")

        config.mtu?.let { ifaceBuilder.setMtu(it) }

        val peerBuilder = Peer.Builder()
            .parsePublicKey(config.peerPublicKey.trim())
            .parseEndpoint(config.endpoint.trim())
            .parseAllowedIPs(config.allowedIps.trim())

        if (config.persistentKeepalive > 0) {
            peerBuilder.setPersistentKeepalive(config.persistentKeepalive)
        }
        config.preSharedKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
            peerBuilder.parsePreSharedKey(it)
        }

        return Config.Builder()
            .setInterface(ifaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }
}
