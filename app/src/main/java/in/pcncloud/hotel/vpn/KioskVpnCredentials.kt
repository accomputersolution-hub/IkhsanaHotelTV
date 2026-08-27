package `in`.pcncloud.hotel.vpn

/**
 * Verified WireGuard peer parameters for the corporate kiosk tunnel.
 *
 * Client keypair (must stay in sync):
 * - [CLIENT_PRIVATE_KEY] is embedded in the APK
 * - [CLIENT_PUBLIC_KEY] is derived via Curve25519 (`wg pubkey`) and MUST be
 *   configured on the PC server under `[Peer] PublicKey = ...`
 *
 * Server peer (Android `[Peer]` section):
 * - [SERVER_PUBLIC_KEY] = PC interface public key
 * - [ENDPOINT] = PC listen address
 */
object KioskVpnCredentials {
    /** Android box private key (Interface.PrivateKey). */
    const val CLIENT_PRIVATE_KEY = "yL0hKUoNVsMaTrr2addSJDOAhhx281QYpsQlHhslPno="

    /**
     * Public key for [CLIENT_PRIVATE_KEY] (`echo <private> | wg pubkey`).
     * Put this exact value on the PC server peer:
     * ```
     * [Peer]
     * PublicKey = 4O7ZQVjs06rHD4SUOB6gzWT/ljRmqaV10+EN6Jb1Sic=
     * AllowedIPs = 10.10.0.2/32
     * ```
     */
    const val CLIENT_PUBLIC_KEY = "4O7ZQVjs06rHD4SUOB6gzWT/ljRmqaV10+EN6Jb1Sic="

    const val CLIENT_ADDRESS = "10.10.0.2/32"

    /** PC WireGuard interface public key (Android Peer.PublicKey). */
    const val SERVER_PUBLIC_KEY = "JvU5blzhYou9lAsbUKvwRH7x8Z3kVo3JgrCz/j6XEyc="

    const val ENDPOINT = "103.29.99.61:51088"

    const val ALLOWED_IPS = "10.10.0.2/32"
    const val PERSISTENT_KEEPALIVE = 25

    fun toWireGuardConf(): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $CLIENT_PRIVATE_KEY")
        appendLine("Address = $CLIENT_ADDRESS")
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = $SERVER_PUBLIC_KEY")
        appendLine("AllowedIPs = $ALLOWED_IPS")
        appendLine("Endpoint = $ENDPOINT")
        appendLine("PersistentKeepalive = $PERSISTENT_KEEPALIVE")
    }
}
