package `in`.pcncloud.hotel.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/**
 * Root TV theme. Gold brand accents ([GoldPrimary] `#c9a962`, [GoldLuxury]) stay
 * visible in both day and night. Night uses deep navy; day uses slightly lifted
 * surfaces so wallpaper + UI are less harsh in daylight.
 */
@Composable
fun PcnCloudTvTheme(
    isInDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isInDarkTheme) {
        darkColorScheme(
            primary = GoldLuxury,
            onPrimary = NavyDeep,
            secondary = GoldPrimary,
            onSecondary = NavyDeep,
            tertiary = GoldLight,
            background = NightBackground,
            surface = NightSurface,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
        )
    } else {
        lightColorScheme(
            primary = GoldLuxury,
            onPrimary = NavyDeep,
            secondary = GoldPrimary,
            onSecondary = NavyDeep,
            tertiary = GoldLight,
            background = DayBackground,
            surface = DaySurface,
            onBackground = DayOnBackground,
            onSurface = DayOnBackground,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
