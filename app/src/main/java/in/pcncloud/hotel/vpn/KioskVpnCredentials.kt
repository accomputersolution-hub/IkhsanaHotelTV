package `in`.pcncloud.hotel.vpn

/**
 * Verified WireGuard peer parameters for the corporate kiosk tunnel.
 * Kept as named constants for clarity; [KioskVpnConfigStore.DEFAULT_CONFIG] is the
 * WireGuard conf text consumed by [KioskVpnController] / GoBackend.
 */
object KioskVpnCredentials {
    const val CLIENT_PRIVATE_KEY = "yL0hKUoNVsMaTrr2addSJDOAhhx281QYpsQlHhslPno="
    const val CLIENT_ADDRESS = "10.10.0.2/32"
    const val SERVER_PUBLIC_KEY = "tRaqqjfX1frGobtBbBGwbR/jn4Xp4u3xNpicF0NSp0Q="
    const val ENDPOINT = "103.29.99.61:51088"
    /** Split-tunnel: only VPN subnet. Use `0.0.0.0/0` for full-tunnel if required. */
    const val ALLOWED_IPS = "10.10.0.0/24"
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
