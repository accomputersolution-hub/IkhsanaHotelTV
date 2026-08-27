package `in`.pcncloud.hotel.vpn

/**
 * Verified WireGuard peer parameters for the corporate kiosk tunnel.
 *
 * Client keypair (must stay in sync):
 * - [CLIENT_PRIVATE_KEY] is embedded in the APK
 * - [CLIENT_PUBLIC_KEY] is derived via Curve25519 (`wg pubkey`) and MUST be
 *   configured on the PC/VM server under `[Peer] PublicKey = ...`
 *
 * Server peer (Android `[Peer]` section):
 * - [SERVER_PUBLIC_KEY] = VM interface public key
 * - [ENDPOINT] = VM listen address
 */
object KioskVpnCredentials {
    /** Android box private key (Interface.PrivateKey). */
    const val CLIENT_PRIVATE_KEY = "+PXcJMM9NqZNyPEa1zRUZULsTe1IvuS6PhU8M+b+WE0="

    /**
     * Public key for [CLIENT_PRIVATE_KEY] (`echo <private> | wg pubkey`).
     * Put this exact value on the VM server peer:
     * ```
     * [Peer]
     * PublicKey = EQ22ld0aHEfWWmuqSq1zSiI5itEwyBAg8PtXJAsL6Tc=
     * AllowedIPs = 10.191.1.2/32
     * ```
     */
    const val CLIENT_PUBLIC_KEY = "EQ22ld0aHEfWWmuqSq1zSiI5itEwyBAg8PtXJAsL6Tc="

    const val CLIENT_ADDRESS = "10.191.1.2/24"

    /** DNS for Interface so Firebase / external APIs resolve over the tunnel. */
    const val DNS = "8.8.8.8"

    /** Local VM WireGuard interface public key (Android Peer.PublicKey). */
    const val SERVER_PUBLIC_KEY = "LlKV5NZZA5I+8UxaaDuN1LZTHS1R1vGl2RjnCBC0P3k="

    const val PRESHARED_KEY = "UhcfP8qMkI3KZEbwtBTkQR1t6gLgOg/tM+O+bsWlgd0="

    const val ENDPOINT = "192.168.1.111:51820"

    /** Full-tunnel: send all IPv4 traffic through the VPN. */
    const val ALLOWED_IPS = "0.0.0.0/0"
    const val PERSISTENT_KEEPALIVE = 25

    fun toWireGuardConf(): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $CLIENT_PRIVATE_KEY")
        appendLine("Address = $CLIENT_ADDRESS")
        appendLine("DNS = $DNS")
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = $SERVER_PUBLIC_KEY")
        appendLine("PresharedKey = $PRESHARED_KEY")
        appendLine("AllowedIPs = $ALLOWED_IPS")
        appendLine("Endpoint = $ENDPOINT")
        appendLine("PersistentKeepalive = $PERSISTENT_KEEPALIVE")
    }
}
