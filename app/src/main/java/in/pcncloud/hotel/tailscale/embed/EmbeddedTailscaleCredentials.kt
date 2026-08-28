package `in`.pcncloud.hotel.tailscale.embed

/**
 * Headscale control plane + auth key baked into the corporate kiosk build.
 */
object EmbeddedTailscaleCredentials {
    const val CONTROL_URL = "http://103.29.99.61:8080"
    const val AUTH_KEY = "8cc35884186f1ca5cddd595431a0c7994e1c38f66dbf4435"

    /** Only this app's traffic is routed through the Tailscale TUN (per-app VPN). */
    const val SPLIT_TUNNEL_PACKAGE = "com.ektv.pro"
}
