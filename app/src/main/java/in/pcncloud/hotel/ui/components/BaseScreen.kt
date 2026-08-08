package `in`.pcncloud.hotel.ui.components

import android.util.Log
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.home.BrandAssets
import `in`.pcncloud.hotel.ui.home.HomeViewModel
import `in`.pcncloud.hotel.ui.theme.CorporateBlue
import `in`.pcncloud.hotel.ui.theme.GoldLight
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

/**
 * Global guest-shell layout: hotel wallpaper + dark overlay + top chrome
 * (logo, academy/hotel title, clock, room) shared by Home and every sub-menu.
 */
@Composable
fun BaseScreen(
    viewModelFactory: HotelViewModelFactory,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenAdmin: () -> Unit = {},
    onAlerts: () -> Unit = {},
    showAlertBell: Boolean = false,
    alertBellFocus: FocusRequester? = null,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chromeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val chromeState by chromeViewModel.uiState.collectAsState()
    val branding = chromeState.branding
    val profile = chromeState.guestProfile
    val unreadAlerts = chromeState.alerts.count { !it.read && !it.revoked }

    val wallpaperUrl = branding.bgWallpaperUrl.ifBlank { profile.bgWallpaperUrl }
    val hotelLogoUrl = branding.logoUrl.ifBlank { profile.hotelLogoUrl }
    val hotelName = branding.hotelName.ifBlank { profile.hotelName }
    val tagline = branding.tagline.ifBlank { profile.tagline }
    val roomNumber = profile.roomNumber

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (onBack != null) Modifier.luxuryBackHandler(onBack) else Modifier),
    ) {
        HotelWallpaperBackground(
            wallpaperUrl = wallpaperUrl,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            AppChromeHeader(
                roomNumber = roomNumber,
                hotelLogoUrl = hotelLogoUrl,
                hotelName = hotelName,
                tagline = tagline,
                unreadAlerts = unreadAlerts,
                showAlertBell = showAlertBell,
                alertBellFocus = alertBellFocus,
                onOpenAdmin = onOpenAdmin,
                onAlerts = onAlerts,
            )

            Spacer(modifier = Modifier.height(16.dp))
            GoldSeparatorLine()

            if (!title.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                LuxuryScreenHeader(
                    title = title,
                    subtitle = subtitle,
                    showSeparator = false,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            content()
        }
    }
}

/**
 * Full-bleed branding wallpaper with a semi-transparent dark overlay so
 * gold/white UI stays readable. Falls back to the navy luxury gradient.
 */
@Composable
fun HotelWallpaperBackground(
    wallpaperUrl: String,
    modifier: Modifier = Modifier,
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.28f),
                                0.35f to Color.Black.copy(alpha = 0.18f),
                                1.0f to Color.Black.copy(alpha = 0.62f),
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
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppChromeHeader(
    roomNumber: String,
    hotelLogoUrl: String,
    hotelName: String,
    tagline: String,
    unreadAlerts: Int = 0,
    showAlertBell: Boolean = false,
    alertBellFocus: FocusRequester? = null,
    onOpenAdmin: () -> Unit = {},
    onAlerts: () -> Unit = {},
) {
    val displayName = hotelName.trim()
    val displayTagline = tagline.trim()
    val isCorporate = BuildConfig.IS_CORPORATE
    val bellFocus = alertBellFocus ?: remember { FocusRequester() }

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
                if (displayName.isNotBlank()) {
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
                }
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showAlertBell) {
                        CorporateAlertBellButton(
                            unreadCount = unreadAlerts,
                            focusRequester = bellFocus,
                            onClick = onAlerts,
                        )
                    }
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
    val context = LocalContext.current
    val localLogo = painterResource(BrandAssets.logoRes)
    val remoteUrl = if (BuildConfig.IS_CORPORATE) {
        null
    } else {
        normalizeRemoteImageUrl(hotelLogoUrl)
    }

    LaunchedEffect(remoteUrl) {
        Log.i(
            "BrandLogo",
            if (BuildConfig.IS_CORPORATE) {
                "Corporate — using local BrandAssets.logoRes (lt_logo)"
            } else if (remoteUrl != null) {
                "Loading hotel logo → ${remoteUrl.take(120)}"
            } else {
                "No logoUrl from Firestore — showing flavor BrandAssets.logoRes"
            },
        )
    }

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
            .combinedClickable(
                onClick = {
                    Log.d("AdminUI", "Room Badge short click (ignored) — hold for Admin")
                },
                onLongClick = {
                    openAdmin("Mouse/Touch")
                },
            )
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
                            val held = System.currentTimeMillis() - pressStartedAt
                            if (held >= 500L) {
                                adminOpenedForThisPress = true
                                openAdmin("TV Remote")
                                return@onKeyEvent true
                            }
                        }
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .onKeyEvent { event ->
                val isSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (!isSelect) return@onKeyEvent false
                if (event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
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

private fun normalizeRemoteImageUrl(url: String): String? {
    var cleaned = url.trim().trim('"', '\'').trim()
    if (cleaned.isBlank()) return null
    if (cleaned.startsWith("data:image/svg", ignoreCase = true)) return null

    val wikiFilePage = Regex(
        pattern = """^https?://(?:commons\.wikimedia\.org|(?:[a-z]+\.)?wikipedia\.org)/wiki/File:(.+)$""",
        option = RegexOption.IGNORE_CASE,
    ).matchEntire(cleaned)
    if (wikiFilePage != null) {
        val fileName = wikiFilePage.groupValues[1]
        cleaned = "https://commons.wikimedia.org/wiki/Special:FilePath/$fileName"
    }
    return cleaned
}

private fun isLegacyUnsafeImageUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.US)
    return lower.contains(".svg") ||
        lower.contains("image/svg") ||
        lower.contains("format=svg") ||
        lower.contains("data:image/svg")
}
