package `in`.pcncloud.hotel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.ui.theme.CorpCardBg
import `in`.pcncloud.hotel.ui.theme.CorpGold
import `in`.pcncloud.hotel.ui.theme.CorpGoldBorderIdle
import `in`.pcncloud.hotel.ui.theme.CorpGoldBright
import `in`.pcncloud.hotel.ui.theme.CorpSubtitle
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.NavyMain
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay

@Composable
fun LuxuryScreenBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to NavyDeep,
                    0.55f to NavyMain,
                    0.85f to Color(0xFF152238),
                    1.0f to Color(0xFF1A2D45),
                ),
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E3A52).copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

fun Modifier.luxuryBackHandler(onBack: () -> Unit): Modifier = onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
        onBack()
        true
    } else {
        false
    }
}

/**
 * Same Black & Gold focus chrome as Home Screen [LuxuryNavCard]:
 * idle gold hairline, focused bright gold glow + 3dp border.
 */
fun Modifier.luxuryGoldFocusChrome(
    focused: Boolean,
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier = this
    .shadow(
        elevation = if (focused) 24.dp else 4.dp,
        shape = shape,
        ambientColor = if (focused) {
            CorpGoldBright.copy(alpha = 0.7f)
        } else {
            Color.Black.copy(alpha = 0.5f)
        },
        spotColor = if (focused) {
            CorpGoldBright.copy(alpha = 0.9f)
        } else {
            Color.Black.copy(alpha = 0.4f)
        },
        clip = false,
    )
    .background(CorpCardBg, shape)
    .border(
        width = if (focused) 3.dp else 1.dp,
        color = if (focused) CorpGoldBright else CorpGoldBorderIdle,
        shape = shape,
    )

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LuxuryScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showSeparator: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SerifDisplay,
            color = CorpGold,
            letterSpacing = 1.sp,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 15.sp,
                fontFamily = SansBody,
                color = CorpSubtitle,
            )
        }
        if (showSeparator) {
            Spacer(modifier = Modifier.height(16.dp))
            GoldSeparatorLine()
        }
    }
}

@Composable
fun GoldSeparatorLine() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CorpGold.copy(alpha = 0.15f),
                            CorpGold.copy(alpha = 0.55f),
                            CorpGold,
                            CorpGold.copy(alpha = 0.55f),
                            CorpGold.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(CorpGold),
        )
    }
}

@Composable
fun LuxuryGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                CorpCardBg,
                shape = RoundedCornerShape(16.dp),
            )
            .border(1.dp, CorpGoldBorderIdle, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        content()
    }
}
