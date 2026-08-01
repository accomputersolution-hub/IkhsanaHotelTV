package `in`.pcncloud.hotel.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

@Composable
fun IkhsanaHotelTVTheme(
    isInDarkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isInDarkTheme) {
        darkColorScheme(
            primary = GoldLuxury,
            secondary = GoldPrimary,
            tertiary = GoldLight,
            background = NavyDeep,
            surface = NavyDeep,
        )
    } else {
        lightColorScheme(
            primary = GoldLuxury,
            secondary = GoldPrimary,
            tertiary = GoldLight,
            background = NavyDeep,
            surface = NavyDeep,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
