package `in`.pcncloud.hotel.tailscale.embed

/**
 * Headscale control plane + auth key baked into the corporate kiosk build.
 */
object EmbeddedTailscaleCredentials {
    const val CONTROL_URL = "https://b6ba5e93d09be1.lhr.life"
    const val AUTH_KEY = "088d3dc2b14a652888fb19f5eb8c7367b37efc59f7bcb585"

    /** Only this app's traffic is routed through the Tailscale TUN (per-app VPN). */
    const val SPLIT_TUNNEL_PACKAGE = "com.ektv.pro"
}
