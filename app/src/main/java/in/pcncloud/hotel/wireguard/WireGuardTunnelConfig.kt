package `in`.pcncloud.hotel.wireguard

/**
 * WireGuard parameters used to build a [com.wireguard.config.Config].
 *
 * Required: [privateKey], [address], [peerPublicKey], [endpoint], [allowedIps].
 */
data class WireGuardTunnelConfig(
    /** Interface private key (base64). */
    val privateKey: String,
    /** Interface address CIDR, e.g. `10.0.0.2/32`. */
    val address: String,
    /** Peer public key (base64). */
    val peerPublicKey: String,
    /** Peer endpoint `host:port`. */
    val endpoint: String,
    /** Comma-separated AllowedIPs, e.g. `0.0.0.0/0, ::/0`. */
    val allowedIps: String,
    /** Optional DNS servers, comma-separated. */
    val dns: String? = null,
    /** Optional peer pre-shared key (base64). */
    val preSharedKey: String? = null,
    /** Peer persistent keepalive seconds (0 = omit). */
    val persistentKeepalive: Int = 25,
    /** Optional MTU override. */
    val mtu: Int? = null,
) {
    fun isComplete(): Boolean =
        privateKey.isNotBlank() &&
            address.isNotBlank() &&
            peerPublicKey.isNotBlank() &&
            endpoint.isNotBlank() &&
            allowedIps.isNotBlank()
}
