package `in`.pcncloud.hotel.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.ui.services.ServiceToastType
import `in`.pcncloud.hotel.ui.theme.CorpCardSubtitleDay
import `in`.pcncloud.hotel.ui.theme.CorpCardSubtitleNight
import `in`.pcncloud.hotel.ui.theme.CorpCardTextDay
import `in`.pcncloud.hotel.ui.theme.CorpCardTextNight
import `in`.pcncloud.hotel.ui.theme.CorpGlassDay
import `in`.pcncloud.hotel.ui.theme.CorpGlassNight
import `in`.pcncloud.hotel.ui.theme.CorpGold
import `in`.pcncloud.hotel.ui.theme.CorpGoldBorderIdle
import `in`.pcncloud.hotel.ui.theme.CorpGoldBright
import `in`.pcncloud.hotel.ui.theme.GoldGlassBorder
import `in`.pcncloud.hotel.ui.theme.GoldGlassFill
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.LocalIsNightMode
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServiceToast(
    message: String,
    modifier: Modifier = Modifier,
    type: ServiceToastType = ServiceToastType.STATUS,
) {
    val background = when (type) {
        ServiceToastType.SUCCESS -> Color(0xFF1B5E20).copy(alpha = 0.92f)
        ServiceToastType.ERROR -> Color(0xFF7F1D1D).copy(alpha = 0.94f)
        ServiceToastType.STATUS -> Color(0xFF1E3A5F).copy(alpha = 0.94f)
    }
    val borderColor = when (type) {
        ServiceToastType.SUCCESS -> Color(0xFF81C784)
        ServiceToastType.ERROR -> Color(0xFFFCA5A5)
        ServiceToastType.STATUS -> GoldPrimary
    }

    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(14.dp), ambientColor = borderColor.copy(alpha = 0.4f))
            .background(background, RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        RowWithIcon(message = message, type = type)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RowWithIcon(message: String, type: ServiceToastType) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when (type) {
                        ServiceToastType.ERROR -> Color(0xFFDC2626)
                        else -> Color(0xFF334155)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔔", fontSize = 18.sp)
        }
        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LuxuryNavCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    focusGlowColor: Color = GoldLuxury,
    modifier: Modifier = Modifier,
    /** When false, draw the asset's original colors (for full-color PNGs). Hotel only. */
    tintIcon: Boolean = !BuildConfig.IS_CORPORATE,
    iconSize: Dp = if (BuildConfig.IS_CORPORATE) 56.dp else 52.dp,
    onClick: () -> Unit,
) {
    if (BuildConfig.IS_CORPORATE) {
        CorporateLuxuryNavCard(
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            iconSize = iconSize,
            modifier = modifier,
            onClick = onClick,
        )
    } else {
        HotelLuxuryNavCard(
            title = title,
            subtitle = subtitle,
            iconRes = iconRes,
            focusGlowColor = focusGlowColor,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

/** Corporate tile — same translucent navy glass as hotel, with gold brand accents. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CorporateLuxuryNavCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(16.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "corpNavCardScale",
    )
    val titleColor = if (isFocused) CorpGoldBright else CorpGold
    val subtitleColor = if (isFocused) TextPrimary.copy(alpha = 0.9f) else TextMuted
    val iconAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.92f,
        animationSpec = tween(durationMillis = 150),
        label = "corpNavCardIconAlpha",
    )
    // Match hotel flavor transparency so wallpaper shows through.
    val fillTop = NavyDeep.copy(alpha = 0.42f)
    val fillBottom = NavyDeep.copy(alpha = 0.32f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = true
                shape = cardShape
            }
            .background(
                brush = Brush.verticalGradient(listOf(fillTop, fillBottom)),
                shape = cardShape,
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.linearGradient(
                        listOf(
                            CorpGoldBright,
                            GoldLight,
                            CorpGoldBright.copy(alpha = 0.78f),
                            GoldLight.copy(alpha = 0.95f),
                            CorpGoldBright,
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    )
                },
                shape = cardShape,
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Full-color PNG like hotel — tinting flattens the 3D look.
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { alpha = iconAlpha },
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title.uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = titleColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                lineHeight = 17.sp,
                letterSpacing = 0.4.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                color = subtitleColor,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Original hotel / hospitality tile — unchanged for hotel flavor. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HotelLuxuryNavCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    focusGlowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "hotelNavCardScale",
    )
    val titleColor = if (isFocused) GoldLight else TextPrimary
    val subtitleColor = if (isFocused) TextPrimary.copy(alpha = 0.9f) else TextMuted
    val iconAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.92f,
        animationSpec = tween(durationMillis = 150),
        label = "hotelNavCardIconAlpha",
    )
    val fillTop = NavyDeep.copy(alpha = 0.42f)
    val fillBottom = NavyDeep.copy(alpha = 0.32f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = true
                shape = cardShape
            }
            .background(
                brush = Brush.verticalGradient(listOf(fillTop, fillBottom)),
                shape = cardShape,
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.linearGradient(
                        listOf(
                            focusGlowColor,
                            GoldLight,
                            focusGlowColor.copy(alpha = 0.78f),
                            GoldLight.copy(alpha = 0.95f),
                            focusGlowColor,
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                    )
                },
                shape = cardShape,
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Full-color 3D PNG (corporate assets) — never gold-tint or the 3D dies.
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { alpha = iconAlpha },
                contentScale = ContentScale.Fit,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SansBody,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontFamily = SansBody,
                    color = subtitleColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Glowing gold glassmorphism disc for dashboard nav icons.
 * Idle: soft gold wash + hairline border.
 * Focused (D-pad): elevated amber shadow / glow so the icon reads as selected.
 */
@Composable
fun LuxuryIconBadge(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val elevation by animateFloatAsState(
        targetValue = if (focused) 16f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "iconBadgeElevation",
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "iconBadgeScale",
    )
    val borderColor = if (focused) {
        GoldLuxury.copy(alpha = 0.55f)
    } else {
        GoldGlassBorder
    }
    val fillBrush = if (focused) {
        Brush.radialGradient(
            colors = listOf(
                GoldLuxury.copy(alpha = 0.28f),
                GoldGlassFill,
                GoldLuxury.copy(alpha = 0.08f),
            ),
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                GoldLuxury.copy(alpha = 0.16f),
                GoldGlassFill,
            ),
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = badgeScale
                scaleY = badgeScale
            }
            .shadow(
                elevation = elevation.dp,
                shape = CircleShape,
                ambientColor = GoldLuxury.copy(alpha = 0.45f),
                spotColor = GoldLuxury.copy(alpha = 0.65f),
                clip = false,
            )
            .size(56.dp)
            .clip(CircleShape)
            .background(brush = fillBrush, shape = CircleShape)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Soft inner glow ring when focused (reads even if parent card clips elevation).
        if (focused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .border(
                        width = 1.dp,
                        color = GoldLuxury.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            )
        }
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(30.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(GoldLuxury, BlendMode.SrcIn),
        )
    }
}

/** @deprecated Use [LuxuryNavCard] on the home screen */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LuxuryNavCard(
        title = title,
        subtitle = subtitle,
        iconRes = iconRes,
        focusGlowColor = accentColor,
        modifier = modifier.size(width = 220.dp, height = 180.dp),
        onClick = onClick,
    )
}
