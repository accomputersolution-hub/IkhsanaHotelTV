package `in`.pcncloud.hotel.wireguard

/**
 * Fixed WireGuard control-plane + tunnel endpoints for corporate TVs.
 *
 * Client IP is **not** baked — the Node add-peer API allocates the next free
 * `10.0.0.x` from `/etc/wireguard/wg0.conf` and the app persists it.
 */
object WireGuardCredentials {
    /** HTTP API that registers this device's public key as a WireGuard peer. */
    const val ADD_PEER_URL = "http://103.29.99.58:3001/api/add-peer"

    /**
     * Server WireGuard public key (base64, 44 chars).
     * If add-peer returns `serverPublicKey`, that value is preferred and persisted.
     */
    const val SERVER_PUBLIC_KEY = "pvRrIXRM4EaVis/q+RSbGjoBF741FC+ENP71nR6KzUU="

    /** UDP endpoint host:port. */
    const val ENDPOINT = "103.29.99.58:51820"

    const val ALLOWED_IPS = "0.0.0.0/0"

    /**
     * Interface DNS for full-tunnel Android TV — without this, names do not
     * resolve even when the WireGuard tunnel is UP.
     */
    const val DNS = "8.8.8.8, 8.8.4.4"

    const val PERSISTENT_KEEPALIVE = 25

    /** Legacy baked-key path — unused once local keygen + add-peer is active. */
    fun isConfigured(): Boolean = false

    fun toTunnelConfig(): WireGuardTunnelConfig =
        WireGuardTunnelConfig(
            privateKey = "",
            address = "",
            peerPublicKey = SERVER_PUBLIC_KEY,
            endpoint = ENDPOINT,
            allowedIps = ALLOWED_IPS,
            dns = DNS,
            persistentKeepalive = PERSISTENT_KEEPALIVE,
        )
}
