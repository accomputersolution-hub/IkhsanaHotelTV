package `in`.pcncloud.hotel.integration

import android.content.Context
import `in`.pcncloud.hotel.BuildConfig

/**
 * Routes Live TV card taps to the correct destination per product flavor:
 * - **Corporate** (L&T): shell `input keyevent 244` (KEYCODE_TV_INPUT_HDMI_2)
 * - **Hotel**: Onyx IPTV app
 */
object LiveTvLauncher {

    fun launch(context: Context) {
        if (BuildConfig.IS_CORPORATE) {
            HdmiInputKeyInjector.switchToHdmi2(context)
        } else {
            OnyxIptvLauncher.launch(context)
        }
    }
}
