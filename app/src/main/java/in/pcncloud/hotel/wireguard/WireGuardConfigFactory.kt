package `in`.pcncloud.hotel.wireguard

import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer

/**
 * Builds an official WireGuard [Config] from app-level [WireGuardTunnelConfig].
 */
object WireGuardConfigFactory {
    fun build(config: WireGuardTunnelConfig): Config {
        require(config.isComplete()) { "WireGuard config incomplete" }

        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(config.privateKey.trim())
            .parseAddresses(config.address.trim())

        config.dns?.trim()?.takeIf { it.isNotEmpty() }?.let { ifaceBuilder.parseDnsServers(it) }
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
