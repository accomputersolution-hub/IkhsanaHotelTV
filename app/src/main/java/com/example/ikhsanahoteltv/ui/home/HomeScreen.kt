package com.example.ikhsanahoteltv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.ikhsanahoteltv.R
import com.example.ikhsanahoteltv.integration.OnyxIptvLauncher
import com.example.ikhsanahoteltv.ui.HotelViewModelFactory
import com.example.ikhsanahoteltv.ui.components.BroadcastAlertOverlay
import com.example.ikhsanahoteltv.ui.components.LuxuryNavCard
import com.example.ikhsanahoteltv.ui.components.ServiceToast
import com.example.ikhsanahoteltv.ui.theme.FocusBlueTeal
import com.example.ikhsanahoteltv.ui.theme.FocusCyan
import com.example.ikhsanahoteltv.ui.theme.FocusRoyalBlue
import com.example.ikhsanahoteltv.ui.theme.FocusTeal
import com.example.ikhsanahoteltv.ui.theme.GoldLight
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.NavyMain
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.SerifDisplay
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModelFactory: HotelViewModelFactory,
    onNavigateToDining: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToServices: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val liveTvFocus = remember { FocusRequester() }
    val diningFocus = remember { FocusRequester() }
    val servicesFocus = remember { FocusRequester() }
    val alertsFocus = remember { FocusRequester() }
    val alertDismissFocus = remember { FocusRequester() }

    val activeAlert = uiState.activePopupAlert

    LaunchedEffect(activeAlert?.id) {
        if (activeAlert == null) {
            liveTvFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RectangleShape)
            .background(NavyDeep),
    ) {
        ResortBackground(
            modifier = Modifier.fillMaxSize(),
            wallpaperUrl = uiState.guestProfile.bgWallpaperUrl
                .ifBlank { uiState.branding.bgWallpaperUrl },
        )

        HomeForegroundContent(
            modifier = Modifier.fillMaxSize(),
            roomNumber = uiState.guestProfile.roomNumber,
            hotelLogoUrl = uiState.guestProfile.hotelLogoUrl,
            guestName = uiState.guestProfile.guestName,
            unreadAlerts = uiState.alerts.count { !it.read && !it.revoked },
            liveTvFocus = liveTvFocus,
            diningFocus = diningFocus,
            servicesFocus = servicesFocus,
            alertsFocus = alertsFocus,
            onLiveTv = { OnyxIptvLauncher.launch(context) },
            onDining = onNavigateToDining,
            onServices = onNavigateToServices,
            onAlerts = onNavigateToAlerts,
        )

        uiState.serviceToastMessage?.let { message ->
            ServiceToast(
                message = message,
                type = uiState.serviceToastType,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 36.dp, bottom = 36.dp),
            )
        }

        activeAlert?.let { alert ->
            LaunchedEffect(alert.id, alert.durationMs) {
                if (alert.durationMs > 0L) {
                    delay(alert.durationMs)
                    viewModel.dismissPopup()
                }
            }

            BroadcastAlertOverlay(
                title = alert.title,
                message = alert.message,
                onDismiss = viewModel::dismissPopup,
                modifier = Modifier.fillMaxSize(),
                dismissFocusRequester = alertDismissFocus,
            )
        }
    }
}

/**
 * Background layer — optional Hotels/{id} branding wallpaper, else Compose gradients.
 * Wallpaper uses [ContentScale.Crop] for full-bleed 16:9 coverage (no Fit/oval letterboxing),
 * plus a dark gradient scrim so welcome text and nav tiles stay readable.
 */
@Composable
private fun ResortBackground(
    modifier: Modifier = Modifier,
    wallpaperUrl: String = "",
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RectangleShape)
            .background(NavyDeep),
    ) {
        if (wallpaperUrl.isNotBlank() && !isLegacyUnsafeImageUrl(wallpaperUrl)) {
            AsyncImage(
                model = wallpaperUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )
            // Dark gradient scrim — keeps white/gold UI readable over any custom wallpaper.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.58f),
                                0.28f to Color.Black.copy(alpha = 0.32f),
                                0.55f to Color.Black.copy(alpha = 0.38f),
                                0.78f to Color.Black.copy(alpha = 0.55f),
                                1.0f to Color.Black.copy(alpha = 0.72f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.22f),
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.28f),
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
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
                        .fillMaxHeight()
                        .fillMaxWidth(0.38f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF1E3A52).copy(alpha = 0.55f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to NavyDeep.copy(alpha = 0.4f),
                                    0.25f to Color.Transparent,
                                    0.75f to Color.Transparent,
                                    1.0f to NavyDeep,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HomeForegroundContent(
    modifier: Modifier = Modifier,
    roomNumber: String,
    hotelLogoUrl: String,
    guestName: String,
    unreadAlerts: Int,
    liveTvFocus: FocusRequester,
    diningFocus: FocusRequester,
    servicesFocus: FocusRequester,
    alertsFocus: FocusRequester,
    onLiveTv: () -> Unit,
    onDining: () -> Unit,
    onServices: () -> Unit,
    onAlerts: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        HomeHeader(
            roomNumber = roomNumber,
            hotelLogoUrl = hotelLogoUrl,
        )

        Spacer(modifier = Modifier.height(20.dp))
        GoldSeparatorLine()
        Spacer(modifier = Modifier.height(28.dp))

        WelcomeBanner(
            guestName = guestName,
            roomNumber = roomNumber,
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationCardsRow(
            liveTvFocus = liveTvFocus,
            diningFocus = diningFocus,
            servicesFocus = servicesFocus,
            alertsFocus = alertsFocus,
            unreadAlerts = unreadAlerts,
            onLiveTv = onLiveTv,
            onDining = onDining,
            onServices = onServices,
            onAlerts = onAlerts,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeHeader(
    roomNumber: String,
    hotelLogoUrl: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f),
        ) {
            BrandLogo(hotelLogoUrl = hotelLogoUrl)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.brand_name),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifDisplay,
                    color = TextPrimary,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.brand_subtitle),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SansBody,
                    color = GoldPrimary,
                    letterSpacing = 3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.brand_tagline),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SansBody,
                    color = GoldPrimary.copy(alpha = 0.75f),
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RoomBadge(roomNumber = roomNumber.ifBlank { "101" })
            WifiStatusIcon()
            LiveClockWidget()
        }
    }
}

@Composable
private fun BrandLogo(hotelLogoUrl: String) {
    // Multi-color PNG (ic_logo) fallback — reliable on API 24.
    // Never load remote SVG / unsupported formats on API 24 (Coil shows a broken "X").
    val localLogo = painterResource(R.drawable.ic_logo)
    val useRemote = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
        hotelLogoUrl.isNotBlank() &&
        !isLegacyUnsafeImageUrl(hotelLogoUrl)

    // Clean transparent logo slot — no gold circle frame; fitCenter so PNGs scale naturally.
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(80.dp)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (useRemote) {
            AsyncImage(
                model = hotelLogoUrl,
                contentDescription = "Hotel Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                placeholder = localLogo,
                error = localLogo,
            )
        } else {
            androidx.compose.foundation.Image(
                painter = localLogo,
                contentDescription = "Hotel Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
        }
    }
}

/** Remote SVG / vector URLs fail on API 24 without an SVG decoder. */
private fun isLegacyUnsafeImageUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.US)
    return lower.contains(".svg") ||
        lower.contains("image/svg") ||
        lower.contains("format=svg") ||
        lower.contains("data:image/svg")
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RoomBadge(roomNumber: String) {
    Box(
        modifier = Modifier
            .background(GoldPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.room_number, roomNumber),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SansBody,
            color = GoldLight,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WifiStatusIcon() {
    Text(
        text = "📶",
        fontSize = 22.sp,
        color = TextPrimary,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveClockWidget() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, dd MMMM yyyy", Locale.getDefault()) }
    val date = Date(now)

    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = timeFmt.format(date),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SansBody,
            color = TextPrimary,
        )
        Text(
            text = dateFmt.format(date),
            fontSize = 11.sp,
            fontFamily = SansBody,
            color = TextMuted,
        )
    }
}

@Composable
private fun GoldSeparatorLine() {
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
                            GoldPrimary.copy(alpha = 0.15f),
                            GoldPrimary.copy(alpha = 0.55f),
                            GoldPrimary,
                            GoldPrimary.copy(alpha = 0.55f),
                            GoldPrimary.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(GoldPrimary),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WelcomeBanner(
    guestName: String,
    roomNumber: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.welcome_label),
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = SerifDisplay,
            color = GoldPrimary,
            letterSpacing = 8.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = formatGuestDisplayName(guestName),
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn())
                    .togetherWith(slideOutVertically { -it / 3 } + fadeOut())
            },
            label = "guestName",
        ) { name ->
            Text(
                text = name,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SerifDisplay,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        RoomBadgeWithLines(roomNumber = roomNumber.ifBlank { "101" })

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.welcome_subtitle),
            fontSize = 15.sp,
            fontStyle = FontStyle.Italic,
            fontFamily = SerifDisplay,
            color = TextPrimary.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RoomBadgeWithLines(roomNumber: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        GoldLine(modifier = Modifier.width(80.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.room_badge, roomNumber),
            fontSize = 14.sp,
            fontFamily = SerifDisplay,
            color = GoldLight,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.width(16.dp))
        GoldLine(modifier = Modifier.width(80.dp))
    }
}

@Composable
private fun GoldLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        GoldPrimary.copy(alpha = 0.7f),
                    ),
                ),
            ),
    )
}

@Composable
private fun NavigationCardsRow(
    liveTvFocus: FocusRequester,
    diningFocus: FocusRequester,
    servicesFocus: FocusRequester,
    alertsFocus: FocusRequester,
    unreadAlerts: Int,
    onLiveTv: () -> Unit,
    onDining: () -> Unit,
    onServices: () -> Unit,
    onAlerts: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LuxuryNavCard(
            title = stringResource(R.string.feature_live_tv),
            subtitle = stringResource(R.string.feature_live_tv_subtitle),
            iconRes = R.drawable.ic_live_tv,
            focusGlowColor = FocusCyan,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(liveTvFocus),
            onClick = onLiveTv,
        )
        LuxuryNavCard(
            title = stringResource(R.string.feature_dining),
            subtitle = stringResource(R.string.feature_dining_subtitle),
            iconRes = R.drawable.ic_dining,
            focusGlowColor = FocusTeal,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(diningFocus),
            onClick = onDining,
        )
        LuxuryNavCard(
            title = stringResource(R.string.feature_services),
            subtitle = stringResource(R.string.feature_services_subtitle),
            iconRes = R.drawable.ic_services,
            focusGlowColor = FocusBlueTeal,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(servicesFocus),
            onClick = onServices,
        )
        LuxuryNavCard(
            title = stringResource(R.string.feature_alerts),
            subtitle = if (unreadAlerts > 0) {
                "$unreadAlerts new alert(s)"
            } else {
                stringResource(R.string.feature_alerts_subtitle)
            },
            iconRes = R.drawable.ic_alerts,
            focusGlowColor = FocusRoyalBlue,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(alertsFocus),
            onClick = onAlerts,
        )
    }
}

private fun formatGuestDisplayName(raw: String): String {
    val trimmed = raw.trim().ifBlank { "Guest" }
    if (trimmed.equals("Guest", ignoreCase = true)) return "Guest"
    if (trimmed.startsWith("Mr.", ignoreCase = true) ||
        trimmed.startsWith("Mrs.", ignoreCase = true) ||
        trimmed.startsWith("Ms.", ignoreCase = true) ||
        trimmed.startsWith("Dr.", ignoreCase = true)
    ) {
        return trimmed.uppercase(Locale.getDefault())
    }
    return "Mr. ${trimmed.uppercase(Locale.getDefault())}"
}
