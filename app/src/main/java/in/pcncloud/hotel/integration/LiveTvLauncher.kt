package `in`.pcncloud.hotel.integration

import android.content.Context

/**
 * Live TV card: launch Onyx IPTV on both hotel and corporate flavors.
 */
object LiveTvLauncher {

    fun launch(context: Context) {
        OnyxIptvLauncher.launch(context)
    }
}
