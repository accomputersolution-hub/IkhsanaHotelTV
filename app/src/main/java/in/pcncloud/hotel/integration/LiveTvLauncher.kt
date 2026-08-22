package `in`.pcncloud.hotel.integration

import android.content.Context
import `in`.pcncloud.hotel.BuildConfig

/**
 * Routes Live TV card taps to the correct destination per product flavor:
 * - **Corporate** (L&T): HDMI 2 via CEC / Amlogic input switch
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
