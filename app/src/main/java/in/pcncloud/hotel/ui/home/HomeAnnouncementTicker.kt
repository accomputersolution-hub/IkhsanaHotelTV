package `in`.pcncloud.hotel.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.SansBody
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Black bar behind the home announcement strip (matches legacy XML ticker). */
private val TickerBarBackground = Color(0xCC000000)

/** Gap between looped copies of the announcement. */
private const val TickerGap = "     •     "

/** Horizontal scroll speed in dp/sec — readable on 10-foot UI. */
private const val TickerSpeedDpPerSec = 48f

/**
 * Bottom home-screen announcement ticker.
 *
 * Pure Compose infinite marquee so copy keeps scrolling on Android TV even when:
 * - the string is shorter than the screen (XML [android.widget.TextView] marquee stays static)
 * - focus is on a nav card (XML marquee often requires selected/focused)
 * - the parent recomposes (clock / focus) — scroll state lives in a remembered [Animatable]
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeAnnouncementTicker(
    message: String,
    modifier: Modifier = Modifier,
) {
    val trimmed = message.trim()
    if (trimmed.isBlank()) return

    val density = LocalDensity.current
    val cd = stringResource(R.string.home_announcement_ticker_cd)
    val segment = remember(trimmed) { trimmed + TickerGap }
    val offsetX = remember(trimmed) { Animatable(0f) }

    var segmentWidthPx by remember(trimmed) { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }

    val copyCount = remember(segmentWidthPx, viewportWidthPx) {
        val seg = segmentWidthPx
        if (seg <= 0f) {
            4
        } else {
            // Cover the viewport plus one extra segment for the seamless handoff.
            max(2, ceil((viewportWidthPx + seg) / seg).toInt() + 1)
        }
    }

    LaunchedEffect(trimmed, segmentWidthPx) {
        val width = segmentWidthPx
        if (width <= 0f) return@LaunchedEffect
        val pxPerSec = with(density) { TickerSpeedDpPerSec.dp.toPx() }
        val durationMs = max(1, (width / pxPerSec * 1000f).roundToInt())
        offsetX.snapTo(0f)
        while (true) {
            offsetX.animateTo(
                targetValue = -width,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            )
            offsetX.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TickerBarBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clipToBounds()
            .focusProperties { canFocus = false }
            .semantics { contentDescription = cd }
            .onSizeChanged { viewportWidthPx = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(copyCount) { index ->
                Text(
                    text = segment,
                    color = GoldLuxury,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SansBody,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = if (index == 0) {
                        Modifier.onGloballyPositioned { coords ->
                            val w = coords.size.width.toFloat()
                            if (w > 0f && segmentWidthPx != w) {
                                segmentWidthPx = w
                            }
                        }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
