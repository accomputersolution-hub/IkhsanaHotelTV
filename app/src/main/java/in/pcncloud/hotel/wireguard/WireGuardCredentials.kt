package `in`.pcncloud.hotel.wireguard

/**
 * Fixed WireGuard control-plane + tunnel endpoints for corporate TVs.
 *
 * Client identity (private/public key) is generated locally and persisted —
 * see [WireGuardKeyStore] / [WireGuardProvisioner].
 */
object WireGuardCredentials {
    /** HTTP API that registers this device's public key as a WireGuard peer. */
    const val ADD_PEER_URL = "http://103.29.99.61:3000/api/add-peer"

    /** Tunnel interface address assigned to this Android client. */
    const val CLIENT_ADDRESS = "10.0.0.3/32"

    /** IP sent to add-peer (no CIDR suffix). */
    const val CLIENT_IP = "10.0.0.3"

    /**
     * Server WireGuard public key (base64, 44 chars).
     * Ops hint: begins with `eGIDnt4o…` — paste the **full** key here.
     * If add-peer returns `serverPublicKey`, that value is preferred and persisted.
     */
    const val SERVER_PUBLIC_KEY = ""

    /** UDP endpoint host:port. */
    const val ENDPOINT = "103.29.99.61:51820"

    const val ALLOWED_IPS = "0.0.0.0/0"
    const val DNS = "1.1.1.1"
    const val PERSISTENT_KEEPALIVE = 25

    /** Legacy baked-key path — unused once local keygen + add-peer is active. */
    fun isConfigured(): Boolean = false

    fun toTunnelConfig(): WireGuardTunnelConfig =
        WireGuardTunnelConfig(
            privateKey = "",
            address = CLIENT_ADDRESS,
            peerPublicKey = SERVER_PUBLIC_KEY,
            endpoint = ENDPOINT,
            allowedIps = ALLOWED_IPS,
            dns = DNS,
            persistentKeepalive = PERSISTENT_KEEPALIVE,
        )
}
