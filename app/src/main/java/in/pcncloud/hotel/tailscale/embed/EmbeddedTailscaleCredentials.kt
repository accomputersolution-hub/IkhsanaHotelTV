package `in`.pcncloud.hotel.tailscale.embed

/**
 * Headscale control plane + auth key baked into the corporate kiosk build.
 */
object EmbeddedTailscaleCredentials {
    const val CONTROL_URL = "https://estimate-unscrew-antidote.ngrok-free.dev"
    const val AUTH_KEY = "f807162be9fdd0354a8c763d7ea87e3bcd2f939133bd2c63"

    /** Legacy split-tunnel package id (unused — VPN is full-tunnel). */
    const val SPLIT_TUNNEL_PACKAGE = "com.ektv.pro"
}
