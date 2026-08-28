package `in`.pcncloud.hotel.integration

/**
 * Headscale / Tailscale control-plane settings for corporate kiosk TVs.
 *
 * Applied to the Tailscale Android app via Device Owner managed configuration
 * ([android.app.admin.DevicePolicyManager.setApplicationRestrictions]).
 */
object TailscaleCredentials {
  /** Headscale control server URL (LoginURL MDM key). */
  const val CONTROL_URL = "http://103.29.99.61:8080"

  /** Reusable Headscale pre-auth key (AuthKey MDM key). */
  const val AUTH_KEY = "8cc35884186f1ca5cddd595431a0c7994e1c38f66dbf4435"
}
