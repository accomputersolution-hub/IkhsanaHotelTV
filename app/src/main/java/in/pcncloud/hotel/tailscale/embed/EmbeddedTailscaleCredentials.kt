package `in`.pcncloud.hotel.tailscale.embed

/**
 * Headscale control plane + auth key baked into the corporate kiosk build.
 */
object EmbeddedTailscaleCredentials {
    const val CONTROL_URL = "http://192.168.1.111:8080"
    const val AUTH_KEY = "7005b51edf34ada8b0b54d72e25a6a1417fea63af17ae8ac"

    /** Only this app's traffic is routed through the Tailscale TUN (per-app VPN). */
    const val SPLIT_TUNNEL_PACKAGE = "com.ektv.pro"
}
