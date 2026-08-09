package `in`.pcncloud.hotel.ui.home

import android.view.LayoutInflater
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.viewinterop.AndroidView
import `in`.pcncloud.hotel.R

/**
 * Compose wrapper for [R.layout.view_home_announcement_ticker].
 * Pin with [androidx.compose.ui.Alignment.BottomCenter] on the Home overlay.
 */
@Composable
fun HomeAnnouncementTicker(
    message: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            val root = LayoutInflater.from(context)
                .inflate(R.layout.view_home_announcement_ticker, null, false)
            val tv = root.findViewById<TextView>(R.id.tv_announcement_ticker)
            root.tag = AnnouncementTickerController(tv).also { it.updateAnnouncementText(message) }
            root
        },
        update = { root ->
            (root.tag as AnnouncementTickerController).updateAnnouncementText(message)
        },
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
    )
}
