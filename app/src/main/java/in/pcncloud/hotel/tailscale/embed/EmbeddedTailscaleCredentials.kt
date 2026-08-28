package `in`.pcncloud.hotel.tailscale.embed

/**
 * Headscale control plane + auth key baked into the corporate kiosk build.
 */
object EmbeddedTailscaleCredentials {
    const val CONTROL_URL = "http://192.168.1.235:8080"
    const val AUTH_KEY = "098c0e3f9b3d1eb000e6f736995a659e1ec8f735cbca200a"

    /** Only this app's traffic is routed through the Tailscale TUN (per-app VPN). */
    const val SPLIT_TUNNEL_PACKAGE = "com.ektv.pro"
}
