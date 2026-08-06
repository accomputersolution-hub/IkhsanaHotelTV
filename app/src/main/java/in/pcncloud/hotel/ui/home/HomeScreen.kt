package `in`.pcncloud.hotel.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.util.Log
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
import coil.request.ImageRequest
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.integration.OnyxIptvLauncher
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.BroadcastAlertOverlay
import `in`.pcncloud.hotel.ui.components.LuxuryNavCard
import `in`.pcncloud.hotel.ui.components.ServiceToast
import `in`.pcncloud.hotel.ui.theme.CorporateBlue
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.NavyMain
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary
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
    onNavigateToAgenda: () -> Unit = {},
    onNavigateToEntertainment: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val liveTvFocus = remember { FocusRequester() }
    val entertainmentFocus = remember { FocusRequester() }
    val diningFocus = remember { FocusRequester() }
    val servicesFocus = remember { FocusRequester() }
    val agendaFocus = remember { FocusRequester() }
    val alertsFocus = remember { FocusRequester() }
    val alertDismissFocus = remember { FocusRequester() }
    val alertBellFocus = remember { FocusRequester() }

    val activeAlert = uiState.activePopupAlert
    val unreadAlerts = uiState.alerts.count { !it.read && !it.revoked }

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
            // Hotels/{id} branding wallpaper first, then any room-level override.
            wallpaperUrl = uiState.branding.bgWallpaperUrl
                .ifBlank { uiState.guestProfile.bgWallpaperUrl },
        )

        HomeForegroundContent(
            modifier = Modifier.fillMaxSize(),
            roomNumber = uiState.guestProfile.roomNumber,
            // Prefer Hotels/{hotelId} branding from Web Admin over room/defaults.
            hotelLogoUrl = uiState.branding.logoUrl
                .ifBlank { uiState.guestProfile.hotelLogoUrl },
            hotelName = uiState.branding.hotelName
                .ifBlank { uiState.guestProfile.hotelName },
            tagline = uiState.branding.tagline
                .ifBlank { uiState.guestProfile.tagline },
            welcomeMessage = uiState.branding.welcomeMessage
                .ifBlank { uiState.guestProfile.welcomeMessage }
                .ifBlank { uiState.guestProfile.hotelInfo },
            guestName = uiState.guestProfile.guestName,
            salutation = uiState.guestProfile.salutation,
            unreadAlerts = unreadAlerts,
            liveTvFocus = liveTvFocus,
            entertainmentFocus = entertainmentFocus,
            diningFocus = diningFocus,
            servicesFocus = servicesFocus,
            agendaFocus = agendaFocus,
            alertsFocus = alertsFocus,
            alertBellFocus = alertBellFocus,
            onLiveTv = { OnyxIptvLauncher.launch(context) },
            onEntertainment = onNavigateToEntertainment,
            onDining = onNavigateToDining,
            onAgenda = onNavigateToAgenda,
            onServices = onNavigateToServices,
            onAlerts = onNavigateToAlerts,
            onOpenAdmin = onNavigateToAdmin,
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
    hotelName: String,
    tagline: String,
    welcomeMessage: String,
    guestName: String,
    salutation: String,
    unreadAlerts: Int,
    liveTvFocus: FocusRequester,
    entertainmentFocus: FocusRequester,
    diningFocus: FocusRequester,
    servicesFocus: FocusRequester,
    agendaFocus: FocusRequester,
    alertsFocus: FocusRequester,
    alertBellFocus: FocusRequester,
    onLiveTv: () -> Unit,
    onEntertainment: () -> Unit,
    onDining: () -> Unit,
    onAgenda: () -> Unit,
    onServices: () -> Unit,
    onAlerts: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        HomeHeader(
            roomNumber = roomNumber,
            hotelLogoUrl = hotelLogoUrl,
            hotelName = hotelName,
            tagline = tagline,
            unreadAlerts = unreadAlerts,
            alertBellFocus = alertBellFocus,
            onOpenAdmin = onOpenAdmin,
            onAlerts = onAlerts,
        )

        Spacer(modifier = Modifier.height(20.dp))
        GoldSeparatorLine()
        Spacer(modifier = Modifier.height(28.dp))

        WelcomeBanner(
            guestName = guestName,
            salutation = salutation,
            roomNumber = roomNumber,
            welcomeMessage = welcomeMessage,
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationCardsRow(
            liveTvFocus = liveTvFocus,
            entertainmentFocus = entertainmentFocus,
            diningFocus = diningFocus,
            servicesFocus = servicesFocus,
            agendaFocus = agendaFocus,
            alertsFocus = alertsFocus,
            unreadAlerts = unreadAlerts,
            onLiveTv = onLiveTv,
            onEntertainment = onEntertainment,
            onDining = onDining,
            onAgenda = onAgenda,
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
    hotelName: String,
    tagline: String,
    unreadAlerts: Int,
    alertBellFocus: FocusRequester,
    onOpenAdmin: () -> Unit,
    onAlerts: () -> Unit,
) {
    val displayName = hotelName.ifBlank { stringResource(R.string.brand_name) }
    val displayTagline = tagline.trim()
    val isCorporate = BuildConfig.IS_CORPORATE

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = if (isCorporate) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            BrandLogo(hotelLogoUrl = hotelLogoUrl)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            ) {
                Text(
                    text = displayName.uppercase(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifDisplay,
                    color = TextPrimary,
                    letterSpacing = 2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayTagline.isNotBlank()) {
                    Text(
                        text = displayTagline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SansBody,
                        color = GoldPrimary,
                        letterSpacing = 2.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (isCorporate) {
            // Corporate: Bell + Room on top; Wi‑Fi + Clock stacked below.
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CorporateAlertBellButton(
                        unreadCount = unreadAlerts,
                        focusRequester = alertBellFocus,
                        onClick = onAlerts,
                    )
                    RoomBadge(
                        roomNumber = roomNumber.ifBlank { "101" },
                        onOpenAdmin = onOpenAdmin,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    WifiStatusIcon()
                    LiveClockWidget()
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RoomBadge(
                    roomNumber = roomNumber.ifBlank { "101" },
                    onOpenAdmin = onOpenAdmin,
                )
                WifiStatusIcon()
                LiveClockWidget()
            }
        }
    }
}

@Composable
private fun BrandLogo(hotelLogoUrl: String) {
    // Coil loads Hotels/{id}.logoUrl / branding.logoUrl; local ic_logo only when empty/error.
    val context = LocalContext.current
    val localLogo = painterResource(R.drawable.ic_logo)
    val remoteUrl = normalizeRemoteImageUrl(hotelLogoUrl)

    LaunchedEffect(remoteUrl) {
        Log.i(
            "BrandLogo",
            if (remoteUrl != null) {
                "Loading hotel logo → ${remoteUrl.take(120)}"
            } else {
                "No logoUrl from Firestore — showing local ic_logo fallback"
            },
        )
    }

    // Clean transparent logo slot — no gold circle frame; fitCenter so PNGs scale naturally.
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(80.dp)
            .padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (remoteUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(remoteUrl)
                    .crossfade(true)
                    .allowHardware(false)
                    .listener(
                        onSuccess = { _, _ ->
                            Log.i("BrandLogo", "Hotel logo loaded OK")
                        },
                        onError = { _, result ->
                            Log.e(
                                "BrandLogo",
                                "Hotel logo FAILED url=${remoteUrl.take(120)}: ${result.throwable.message}",
                                result.throwable,
                            )
                        },
                    )
                    .build(),
                contentDescription = "Hotel Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                error = localLogo,
                fallback = localLogo,
            )
        } else {
            Image(
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

/**
 * Normalize a Firestore logo / wallpaper URL for Coil.
 * Do NOT block SVG — [HotelTvApplication] registers [coil.decode.SvgDecoder].
 */
private fun normalizeRemoteImageUrl(url: String): String? {
    val cleaned = url.trim().trim('"', '\'').trim()
    if (cleaned.isBlank()) return null
    // Reject only inline data SVGs that Coil cannot fetch as a network model.
    if (cleaned.startsWith("data:image/svg", ignoreCase = true)) return null
    return cleaned
}

/** Wallpaper: skip formats that cannot paint a full-bleed background reliably. */
private fun isLegacyUnsafeImageUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.US)
    return lower.contains(".svg") ||
        lower.contains("image/svg") ||
        lower.contains("format=svg") ||
        lower.contains("data:image/svg")
}

/**
 * Top-right header Room badge (next to Wi‑Fi / clock) — NOT the center decorative badge.
 * Long-press opens Admin PIN:
 * - Mouse / touch → [combinedClickable] onLongClick
 * - TV remote OK / Enter → first key-repeat or 2s hold fallback
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RoomBadge(
    roomNumber: String,
    onOpenAdmin: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressStartedAt by remember { mutableLongStateOf(0L) }
    var selectKeyDown by remember { mutableStateOf(false) }
    var adminOpenedForThisPress by remember { mutableStateOf(false) }
    var downCount by remember { mutableIntStateOf(0) }

    fun openAdmin(source: String) {
        Log.e("AdminUI", ">>> Room Badge LONG PRESSED via $source! <<<")
        onOpenAdmin()
    }

    // TV remotes that keep delivering KeyDown while held (no nativeKeyEvent.repeatCount).
    LaunchedEffect(selectKeyDown, downCount) {
        if (!selectKeyDown || adminOpenedForThisPress) return@LaunchedEffect
        delay(2_000L)
        if (selectKeyDown && !adminOpenedForThisPress) {
            adminOpenedForThisPress = true
            openAdmin("TV Remote")
        }
    }

    Box(
        modifier = Modifier
            .background(
                if (focused) GoldPrimary.copy(alpha = 0.22f) else GoldPrimary.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) GoldPrimary else GoldPrimary.copy(alpha = 0.45f),
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            // Mouse / touch long-press (emulator & touch panels).
            .combinedClickable(
                onClick = {
                    Log.d("AdminUI", "Room Badge short click (ignored) — hold for Admin")
                },
                onLongClick = {
                    openAdmin("Mouse/Touch")
                },
            )
            // Physical TV remote: D-Pad Center / Enter long-press.
            .onKeyEvent { event ->
                val isSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isSelect) return@onKeyEvent false

                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (!selectKeyDown) {
                            selectKeyDown = true
                            pressStartedAt = System.currentTimeMillis()
                            adminOpenedForThisPress = false
                            downCount += 1
                            Log.d("AdminUI", "Room Badge OK/Enter DOWN (start long-press timer)")
                        } else if (!adminOpenedForThisPress) {
                            // Some remotes emit repeated KeyDown without UP — treat 2nd+ as hold.
                            val held = System.currentTimeMillis() - pressStartedAt
                            if (held >= 500L) {
                                adminOpenedForThisPress = true
                                openAdmin("TV Remote")
                                return@onKeyEvent true
                            }
                        }
                        // Consume after Admin opened so the hold does not navigate elsewhere.
                        adminOpenedForThisPress
                    }
                    KeyEventType.KeyUp -> {
                        val held = System.currentTimeMillis() - pressStartedAt
                        val opened = adminOpenedForThisPress
                        selectKeyDown = false
                        adminOpenedForThisPress = false
                        if (!opened && pressStartedAt > 0L && held >= 2_000L) {
                            openAdmin("TV Remote (hold)")
                            true
                        } else {
                            opened
                        }
                    }
                    else -> false
                }
            }
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
    if (BuildConfig.IS_CORPORATE) {
        Image(
            painter = painterResource(R.drawable.ic_wifi),
            contentDescription = stringResource(R.string.wifi_status),
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(TextPrimary.copy(alpha = 0.92f)),
        )
    } else {
        Text(
            text = "📶",
            fontSize = 22.sp,
            color = TextPrimary,
        )
    }
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
    salutation: String,
    roomNumber: String,
    welcomeMessage: String,
) {
    val subtitle = welcomeMessage.ifBlank { stringResource(R.string.welcome_subtitle) }

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
            targetState = formatGuestDisplayName(guestName, salutation),
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
            text = subtitle,
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
    entertainmentFocus: FocusRequester,
    diningFocus: FocusRequester,
    servicesFocus: FocusRequester,
    agendaFocus: FocusRequester,
    alertsFocus: FocusRequester,
    unreadAlerts: Int,
    onLiveTv: () -> Unit,
    onEntertainment: () -> Unit,
    onDining: () -> Unit,
    onAgenda: () -> Unit,
    onServices: () -> Unit,
    onAlerts: () -> Unit,
) {
    val isCorporate = BuildConfig.IS_CORPORATE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LuxuryNavCard(
            title = stringResource(R.string.feature_live_tv),
            subtitle = stringResource(R.string.feature_live_tv_subtitle),
            iconRes = R.drawable.ic_nav_live_tv,
            focusGlowColor = GoldLuxury,
            modifier = Modifier
                .weight(1f)
                .height(170.dp)
                .focusRequester(liveTvFocus),
            onClick = onLiveTv,
        )
        LuxuryNavCard(
            title = stringResource(R.string.feature_entertainment),
            subtitle = stringResource(R.string.feature_entertainment_subtitle),
            iconRes = R.drawable.ic_nav_entertainment,
            focusGlowColor = GoldLuxury,
            modifier = Modifier
                .weight(1f)
                .height(170.dp)
                .focusRequester(entertainmentFocus),
            onClick = onEntertainment,
        )
        LuxuryNavCard(
            title = if (isCorporate) {
                stringResource(R.string.feature_menu)
            } else {
                stringResource(R.string.feature_dining)
            },
            subtitle = if (isCorporate) {
                stringResource(R.string.feature_menu_subtitle_corporate)
            } else {
                stringResource(R.string.feature_dining_subtitle)
            },
            iconRes = R.drawable.ic_nav_dining,
            focusGlowColor = GoldLuxury,
            modifier = Modifier
                .weight(1f)
                .height(170.dp)
                .focusRequester(diningFocus),
            onClick = onDining,
        )
        if (isCorporate) {
            LuxuryNavCard(
                title = stringResource(R.string.feature_agenda),
                subtitle = stringResource(R.string.feature_agenda_subtitle),
                iconRes = R.drawable.ic_nav_agenda,
                focusGlowColor = GoldLuxury,
                modifier = Modifier
                    .weight(1f)
                    .height(170.dp)
                    .focusRequester(agendaFocus),
                onClick = onAgenda,
            )
            LuxuryNavCard(
                title = stringResource(R.string.feature_emergency),
                subtitle = stringResource(R.string.feature_emergency_subtitle),
                iconRes = R.drawable.ic_nav_services,
                focusGlowColor = GoldLuxury,
                modifier = Modifier
                    .weight(1f)
                    .height(170.dp)
                    .focusRequester(servicesFocus),
                onClick = onServices,
            )
        } else {
            LuxuryNavCard(
                title = stringResource(R.string.feature_services),
                subtitle = stringResource(R.string.feature_services_subtitle),
                iconRes = R.drawable.ic_nav_services,
                focusGlowColor = GoldLuxury,
                modifier = Modifier
                    .weight(1f)
                    .height(170.dp)
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
                iconRes = R.drawable.ic_nav_alerts,
                focusGlowColor = GoldLuxury,
                modifier = Modifier
                    .weight(1f)
                    .height(170.dp)
                    .focusRequester(alertsFocus),
                onClick = onAlerts,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CorporateAlertBellButton(
    unreadCount: Int,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = CircleShape
    val badgeText = when {
        unreadCount <= 0 -> null
        unreadCount > 9 -> "9+"
        else -> unreadCount.toString()
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            // Single focusable click target (mouse / touch / DPAD when mapped).
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            // Explicit TV remote OK / Enter — same pattern as LuxuryNavCard.
            .onKeyEvent { event ->
                val isSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isSelect) return@onKeyEvent false
                if (event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    // Consume KeyDown so the event does not fall through.
                    event.type == KeyEventType.KeyDown
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (focused) CorporateBlue.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                    shape = shape,
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) CorporateBlue else Color.White.copy(alpha = 0.2f),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_alert_bell),
                contentDescription = stringResource(R.string.feature_alerts),
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(
                    if (focused) Color.White else TextPrimary.copy(alpha = 0.92f),
                ),
            )
        }

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .heightIn(min = 18.dp)
                    .widthIn(min = 18.dp)
                    .background(CorporateBlue, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Dashboard greeting name.
 * - Vacant / missing name → "Guest"
 * - Salutation present → "Ms. SANA CHAUDHARY"
 * - Salutation missing → name only (never force "Mr.")
 */
private fun formatGuestDisplayName(rawName: String, salutation: String): String {
    val name = rawName.trim()
    if (name.isBlank() || name.equals("Guest", ignoreCase = true)) {
        return "Guest"
    }

    val displayName = name.uppercase(Locale.getDefault())
    val prefix = salutation.trim()
    if (prefix.isBlank()) {
        return displayName
    }

    // Avoid "Ms. Ms. NAME" if PMS already embedded the title in guestName.
    val knownTitles = listOf("Mr.", "Mrs.", "Ms.", "Miss", "Dr.", "Prof.")
    if (knownTitles.any { displayName.startsWith(it.uppercase(Locale.getDefault()), ignoreCase = true) }) {
        return displayName
    }

    val normalizedPrefix = if (prefix.endsWith(".")) prefix else "$prefix."
    return "${normalizedPrefix.uppercase(Locale.getDefault())} $displayName"
}
