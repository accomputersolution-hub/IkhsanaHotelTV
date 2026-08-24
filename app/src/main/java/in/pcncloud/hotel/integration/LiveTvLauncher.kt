package `in`.pcncloud.hotel.integration

import android.content.Context
import `in`.pcncloud.hotel.BuildConfig

/**
 * Routes Live TV card taps to the correct destination per product flavor:
 * - **Corporate** (L&T): inject KEYCODE_HOME (CEC HDMI switch, like the physical remote)
 * - **Hotel**: Onyx IPTV app
 */
object LiveTvLauncher {

    fun launch(context: Context) {
        if (BuildConfig.IS_CORPORATE) {
            HdmiCecSwitcher.switchToHdmi2(context)
        } else {
            OnyxIptvLauncher.launch(context)
        }
    }
}
