package `in`.pcncloud.hotel.kiosk

import android.os.Build
import android.view.KeyEvent

/**
 * Physical TV remote dedicated / shortcut keys that bypass normal navigation
 * and launch Netflix / YouTube / Prime / Apps drawer on OEM Android TV.
 *
 * When kiosk is ON these must be consumed in [android.app.Activity.dispatchKeyEvent]
 * and [HomeKeyInterceptorService.onKeyEvent].
 */
object KioskHotkeys {

    /** OEM Netflix dedicated button (common on Android TV remotes). */
    const val KEYCODE_NETFLIX = 247

    /** OEM YouTube dedicated button. */
    const val KEYCODE_YOUTUBE = 288

    /** OEM Amazon Prime Video dedicated button. */
    const val KEYCODE_PRIME_VIDEO = 289

    /** OEM Apps / launcher drawer button. */
    const val KEYCODE_APPS = 228

    /**
     * True when [keyCode] must be swallowed under kiosk so hardware shortcuts
     * cannot escape to stock / OTT launchers.
     */
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
            KeyEvent.KEYCODE_EXPLORER,
            KeyEvent.KEYCODE_CONTACTS,
            KeyEvent.KEYCODE_CALENDAR,
            KeyEvent.KEYCODE_MUSIC,
            KeyEvent.KEYCODE_CALCULATOR,
            KeyEvent.KEYCODE_ENVELOPE,
            KeyEvent.KEYCODE_BOOKMARK,
            -> true

            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    keyCode == KeyEvent.KEYCODE_ALL_APPS
                ) {
                    true
                } else {
                    false
                }
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
