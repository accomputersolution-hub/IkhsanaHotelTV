package `in`.pcncloud.hotel.integration

import android.content.Context
import `in`.pcncloud.hotel.BuildConfig

/**
 * Routes Live TV card taps to the correct destination per product flavor:
 * - **Corporate** (L&T): Panasonic Viera Wi‑Fi → HDMI 2 (`NRC_HDMI2-ONOFF`)
 * - **Hotel**: Onyx IPTV app
 */
object LiveTvLauncher {

    fun launch(context: Context) {
        if (BuildConfig.IS_CORPORATE) {
            PanasonicVieraRemote.switchToHdmi2(context)
        } else {
            OnyxIptvLauncher.launch(context)
        }
    }
}
