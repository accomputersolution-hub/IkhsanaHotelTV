package `in`.pcncloud.hotel.wireguard

/**
 * Baked WireGuard peer settings for the corporate kiosk build.
 *
 * Fill in real values before deploying. Until [isConfigured] is true the
 * engine will not attempt to bring the tunnel up.
 */
object WireGuardCredentials {
    /** Interface Address — e.g. `10.66.66.2/32`. */
    const val ADDRESS = ""

    /** Interface PrivateKey (base64). */
    const val PRIVATE_KEY = ""

    /** Peer PublicKey (base64). */
    const val PEER_PUBLIC_KEY = ""

    /** Peer Endpoint — e.g. `vpn.example.com:51820`. */
    const val ENDPOINT = ""

    /** Peer AllowedIPs — e.g. `0.0.0.0/0, ::/0` for full tunnel. */
    const val ALLOWED_IPS = "0.0.0.0/0"

    /** Optional DNS — e.g. `1.1.1.1`. */
    const val DNS = "1.1.1.1"

    /** Optional PreSharedKey (base64). */
    const val PRE_SHARED_KEY = ""

    const val PERSISTENT_KEEPALIVE = 25

    fun isConfigured(): Boolean =
        PRIVATE_KEY.isNotBlank() &&
            ADDRESS.isNotBlank() &&
            PEER_PUBLIC_KEY.isNotBlank() &&
            ENDPOINT.isNotBlank()

    fun toTunnelConfig(): WireGuardTunnelConfig =
        WireGuardTunnelConfig(
            privateKey = PRIVATE_KEY.trim(),
            address = ADDRESS.trim(),
            peerPublicKey = PEER_PUBLIC_KEY.trim(),
            endpoint = ENDPOINT.trim(),
            allowedIps = ALLOWED_IPS.trim().ifBlank { "0.0.0.0/0" },
            dns = DNS.trim().takeIf { it.isNotEmpty() },
            preSharedKey = PRE_SHARED_KEY.trim().takeIf { it.isNotEmpty() },
            persistentKeepalive = PERSISTENT_KEEPALIVE,
        )
}
