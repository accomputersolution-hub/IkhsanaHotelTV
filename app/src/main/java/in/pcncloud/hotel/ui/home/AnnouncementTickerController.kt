package `in`.pcncloud.hotel.ui.home

import android.app.Activity
import android.view.View
import android.widget.TextView
import `in`.pcncloud.hotel.R

/**
 * Controls the bottom announcement ticker ([R.id.tv_announcement_ticker]).
 *
 * XML marquee only runs when the TextView is selected — call [start] once after
 * inflate, then [updateAnnouncementText] whenever Firebase/API text changes.
 */
class AnnouncementTickerController(private val tickerView: TextView) {

    init {
        start()
    }

    /** Enables auto-scroll without requiring D-pad focus or a click. */
    fun start() {
        tickerView.isFocusable = false
        tickerView.isFocusableInTouchMode = false
        tickerView.isSelected = true
    }

    /**
     * Replace ticker copy from the multi-tenant backend.
     * Blank [message] hides the bar. Long copy is looped so the marquee stays continuous.
     */
    fun updateAnnouncementText(message: String?) {
        val trimmed = message?.trim().orEmpty()
        if (trimmed.isBlank()) {
            (tickerView.parent as? View)?.visibility = View.GONE
            tickerView.text = ""
            tickerView.isSelected = false
            return
        }

        (tickerView.parent as? View)?.visibility = View.VISIBLE
        tickerView.text = loopForMarquee(trimmed)
        // Toggle selected to restart the marquee after a text change.
        tickerView.isSelected = false
        tickerView.isSelected = true
    }

    companion object {
        @JvmStatic
        fun bind(activity: Activity): AnnouncementTickerController {
            val tv = activity.findViewById<TextView>(R.id.tv_announcement_ticker)
            return AnnouncementTickerController(tv)
        }

        private fun loopForMarquee(message: String): String {
            if (message.length >= 80) return message
            return List(4) { message }.joinToString("     •     ")
        }
    }
}
