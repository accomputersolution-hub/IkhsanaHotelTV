package `in`.pcncloud.hotel.tailscale.embed

/**
 * Headscale control plane + auth key baked into the corporate kiosk build.
 */
object EmbeddedTailscaleCredentials {
    const val CONTROL_URL = "https://estimate-unscrew-antidote.ngrok-free.dev"
    const val AUTH_KEY = "4f2d2a3b8c2faadf4f8e5ee067d54e7206f48572a4e52d82"

    /** Legacy split-tunnel package id (unused — VPN is full-tunnel). */
    const val SPLIT_TUNNEL_PACKAGE = "com.ektv.pro"
}
