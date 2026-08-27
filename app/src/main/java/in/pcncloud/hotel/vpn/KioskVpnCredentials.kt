package `in`.pcncloud.hotel.vpn

/**
 * Verified WireGuard peer parameters for the corporate kiosk tunnel.
 *
 * Keys are case-sensitive — do not OCR / autocorrect.
 *
 * Client keypair:
 * - [CLIENT_PRIVATE_KEY] embedded in the APK
 * - [CLIENT_PUBLIC_KEY] = `wg pubkey` of private key (for VM `[Peer] PublicKey`)
 */
object KioskVpnCredentials {
    /** Android box private key (Interface.PrivateKey). Exact case. */
    const val CLIENT_PRIVATE_KEY = "+PXcjMM9NqZNyPEa1zRUZULsTe1IvuS6PhU8M+b+WE0="

    /**
     * Public key for [CLIENT_PRIVATE_KEY] (`echo -n <private> | wg pubkey`).
     * Configure on the VM:
     * ```
     * [Peer]
     * PublicKey = /SulENeXHZGZFXSjnv0++BPB1CD/hZIofvcny+gvp1w=
     * AllowedIPs = 10.191.1.2/32
     * ```
     */
    const val CLIENT_PUBLIC_KEY = "/SulENeXHZGZFXSjnv0++BPB1CD/hZIofvcny+gvp1w="

    const val CLIENT_ADDRESS = "10.191.1.2/24"
    const val DNS = "8.8.8.8"

    /** Local VM interface public key (Android Peer.PublicKey). Exact case. */
    const val SERVER_PUBLIC_KEY = "LlKV5NZZAsI+8UxaaDuN1LZTHS1R1vG12RjnCBc0P3k="

    const val PRESHARED_KEY = "UhcfP8qMkI3KZEbwtBTkQR1t6gLgOg/tM+O+bsWlgd0="

    const val ENDPOINT = "192.168.1.111:51820"

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
