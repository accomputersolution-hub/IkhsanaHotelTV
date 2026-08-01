package `in`.pcncloud.hotel.kiosk

import android.os.Build
import android.view.KeyEvent

/**
 * Physical TV remote dedicated / shortcut keys that bypass normal navigation
 * and launch Netflix / YouTube / Prime / Apps drawer on OEM Android TV.
 *
 * App-level only: consumed under kiosk in [android.app.Activity.dispatchKeyEvent]
 * and [HomeKeyInterceptorService.onKeyEvent]. Never disables system packages.
 */
object KioskHotkeys {

    const val KEYCODE_NETFLIX = 247
    const val KEYCODE_YOUTUBE = 288
    const val KEYCODE_PRIME_VIDEO = 289
    const val KEYCODE_APPS = 228

    fun shouldBlockUnderKiosk(keyCode: Int): Boolean {
        return when (keyCode) {
            KEYCODE_NETFLIX,
            KEYCODE_YOUTUBE,
            KEYCODE_PRIME_VIDEO,
            KEYCODE_APPS,
            -> true

            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_NOTIFICATION,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_ASSIST,
            -> true

            else -> {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    keyCode == KeyEvent.KEYCODE_ALL_APPS
            }
        }
    }

    fun label(keyCode: Int): String = when (keyCode) {
        KEYCODE_NETFLIX -> "Netflix"
        KEYCODE_YOUTUBE -> "YouTube"
        KEYCODE_PRIME_VIDEO -> "PrimeVideo"
        KEYCODE_APPS -> "Apps"
        else -> "keyCode=$keyCode"
    }
}
