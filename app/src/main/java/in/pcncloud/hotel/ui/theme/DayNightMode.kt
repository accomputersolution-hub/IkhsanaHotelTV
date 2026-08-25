package `in`.pcncloud.hotel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * Day = 06:00–17:59, Night = 18:00–05:59 (device local time).
 */
fun isNightModeAt(hourOfDay: Int): Boolean = hourOfDay < 6 || hourOfDay >= 18

fun isNightModeNow(): Boolean =
    isNightModeAt(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

val LocalIsNightMode = staticCompositionLocalOf { true }

/**
 * Tracks day/night and refreshes every minute so the UI can crossfade
 * wallpapers and theme without a process restart.
 */
@Composable
fun rememberIsNightMode(): Boolean {
    var night by remember { mutableStateOf(isNightModeNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            night = isNightModeNow()
            delay(60_000L)
        }
    }
    return night
}

@Composable
fun ProvideDayNightMode(
    isNightMode: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIsNightMode provides isNightMode, content = content)
}
