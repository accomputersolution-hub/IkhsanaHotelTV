package `in`.pcncloud.hotel.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.integration.LiveTvLauncher
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.components.BaseScreen
import `in`.pcncloud.hotel.ui.components.BroadcastAlertOverlay
import `in`.pcncloud.hotel.ui.components.LuxuryNavCard
import `in`.pcncloud.hotel.ui.components.ServiceToast
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.util.Locale

/** Main dashboard cards — used to restore D-pad focus after submenu / resume. */
private enum class HomeNavCard {
    LiveTv,
    Entertainment,
    Dining,
    Agenda,
    Services,
    Alerts,
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModelFactory: HotelViewModelFactory,
    isHomeVisible: Boolean = true,
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
    val isCorporate = BuildConfig.IS_CORPORATE

    val liveTvFocus = remember { FocusRequester() }
    val entertainmentFocus = remember { FocusRequester() }
    val diningFocus = remember { FocusRequester() }
    val servicesFocus = remember { FocusRequester() }
    val agendaFocus = remember { FocusRequester() }
    val alertsFocus = remember { FocusRequester() }
    val alertDismissFocus = remember { FocusRequester() }
    val alertBellFocus = remember { FocusRequester() }
    val roomBadgeFocus = remember { FocusRequester() }

    var lastFocusedCard by rememberSaveable { mutableStateOf(HomeNavCard.LiveTv) }
    var resumeEpoch by remember { mutableIntStateOf(0) }

    val activeAlert = uiState.activePopupAlert
    val unreadAlerts = uiState.alerts.count { !it.read && !it.revoked }
    val contentReady = uiState.isContentReady
    val focusManager = LocalFocusManager.current

    fun requesterFor(card: HomeNavCard): FocusRequester = when (card) {
        HomeNavCard.LiveTv -> liveTvFocus
        HomeNavCard.Entertainment -> entertainmentFocus
        HomeNavCard.Dining -> diningFocus
        HomeNavCard.Agenda -> if (isCorporate) agendaFocus else liveTvFocus
        HomeNavCard.Services -> servicesFocus
        HomeNavCard.Alerts -> if (isCorporate) liveTvFocus else alertsFocus
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeEpoch += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(contentReady, isHomeVisible, activeAlert?.id, resumeEpoch) {
        if (!contentReady) return@LaunchedEffect
        if (activeAlert != null) {
            // Drop home-card focus immediately so Dismiss can own the first OK press.
            focusManager.clearFocus(force = true)
            return@LaunchedEffect
        }
        if (!isHomeVisible) return@LaunchedEffect
        // Wait for overlay canFocus + layout so requestFocus lands on the saved card.
        var attempts = 0
        while (attempts < 16) {
            val focused = runCatching { requesterFor(lastFocusedCard).requestFocus() }.getOrDefault(false)
            if (focused) break
            withFrameNanos { }
            attempts++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RectangleShape)
            .background(NavyDeep)
            .onPreviewKeyEvent { event ->
                if (activeAlert == null || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                    Key.Tab,
                    -> {
                        runCatching { alertDismissFocus.requestFocus() }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        viewModel.dismissPopup()
                        true
                    }
                    // OK / Enter must reach the Dismiss button — do not consume here.
                    else -> false
                }
            },
    ) {
        Crossfade(
            targetState = contentReady,
            animationSpec = tween(durationMillis = 280),
            label = "homeBootCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { ready ->
            if (!ready) {
                // Neutral boot shell — never show "YOUR HOTEL" / generic guest placeholders.
                HomeBootLoading(modifier = Modifier.fillMaxSize())
            } else {
                BaseScreen(
                    viewModelFactory = viewModelFactory,
                    onOpenAdmin = onNavigateToAdmin,
                    onAlerts = onNavigateToAlerts,
                    showAlertBell = BuildConfig.IS_CORPORATE,
                    alertBellFocus = alertBellFocus,
                    roomBadgeFocus = roomBadgeFocus,
                    headerDownFocus = requesterFor(lastFocusedCard),
                    modifier = Modifier.focusProperties { canFocus = activeAlert == null },
                ) {
                    Spacer(
                        modifier = Modifier
                            .height(28.dp)
                            .focusProperties { canFocus = false },
                    )
                    WelcomeBanner(
                        guestName = uiState.guestProfile.guestName,
                        salutation = uiState.guestProfile.salutation,
                        welcomeMessage = uiState.branding.welcomeMessage
                            .ifBlank { uiState.guestProfile.welcomeMessage }
                            .ifBlank { uiState.guestProfile.hotelInfo },
                    )
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .focusProperties { canFocus = false },
                    )
                    NavigationCardsRow(
                        liveTvFocus = liveTvFocus,
                        entertainmentFocus = entertainmentFocus,
                        diningFocus = diningFocus,
                        servicesFocus = servicesFocus,
                        agendaFocus = agendaFocus,
                        alertsFocus = alertsFocus,
                        alertBellFocus = alertBellFocus,
                        roomBadgeFocus = roomBadgeFocus,
                        unreadAlerts = unreadAlerts,
                        onCardFocused = { lastFocusedCard = it },
                        onLiveTv = {
                            lastFocusedCard = HomeNavCard.LiveTv
                            LiveTvLauncher.launch(context)
                        },
                        onEntertainment = {
                            lastFocusedCard = HomeNavCard.Entertainment
                            onNavigateToEntertainment()
                        },
                        onDining = {
                            lastFocusedCard = HomeNavCard.Dining
                            onNavigateToDining()
                        },
                        onAgenda = {
                            lastFocusedCard = HomeNavCard.Agenda
                            onNavigateToAgenda()
                        },
                        onServices = {
                            lastFocusedCard = HomeNavCard.Services
                            onNavigateToServices()
                        },
                        onAlerts = {
                            lastFocusedCard = HomeNavCard.Alerts
                            onNavigateToAlerts()
                        },
                    )
                    val tickerMessage = uiState.tickerMessage
                    if (tickerMessage.isNotBlank()) {
                        Spacer(
                            modifier = Modifier
                                .height(52.dp)
                                .focusProperties { canFocus = false },
                        )
                    }
                }
            }
        }

        if (contentReady) {
            val tickerMessage = uiState.tickerMessage
            if (tickerMessage.isNotBlank()) {
                HomeAnnouncementTicker(
                    message = tickerMessage,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            uiState.serviceToastMessage?.let { message ->
                ServiceToast(
                    message = message,
                    type = uiState.serviceToastType,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 36.dp,
                            bottom = if (tickerMessage.isNotBlank()) 88.dp else 36.dp,
                        ),
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
}

/**
 * Full-screen boot / resume gate — compact local logo + welcome + spinner.
 * Matches splash so the handoff into home does not flash a different mark.
 */
@Composable
private fun HomeBootLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(NavyDeep)
            .clip(RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(BrandAssets.logoRes),
                contentDescription = stringResource(R.string.splash_logo_content_description),
                modifier = Modifier.size(90.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.splash_welcome_loading),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SerifDisplay,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            Spacer(modifier = Modifier.height(32.dp))
            AndroidView(
                factory = { context ->
                    android.widget.ProgressBar(context).apply {
                        isIndeterminate = true
                    }
                },
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WelcomeBanner(
    guestName: String,
    salutation: String,
    welcomeMessage: String,
) {
    val subtitle = welcomeMessage.trim()
    val welcomeShadow = Shadow(
        color = Color.Black.copy(alpha = 0.7f),
        offset = Offset(0f, 2f),
        blurRadius = 10f,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.welcome_label),
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = SerifDisplay,
            color = Color.White,
            letterSpacing = 8.sp,
            style = TextStyle(shadow = welcomeShadow),
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
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = welcomeShadow),
            )
        }

        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = subtitle,
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = SerifDisplay,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                style = TextStyle(shadow = welcomeShadow),
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
}

@Composable
private fun NavigationCardsRow(
    liveTvFocus: FocusRequester,
    entertainmentFocus: FocusRequester,
    diningFocus: FocusRequester,
    servicesFocus: FocusRequester,
    agendaFocus: FocusRequester,
    alertsFocus: FocusRequester,
    alertBellFocus: FocusRequester,
    roomBadgeFocus: FocusRequester,
    unreadAlerts: Int,
    onCardFocused: (HomeNavCard) -> Unit,
    onLiveTv: () -> Unit,
    onEntertainment: () -> Unit,
    onDining: () -> Unit,
    onAgenda: () -> Unit,
    onServices: () -> Unit,
    onAlerts: () -> Unit,
) {
    val isCorporate = BuildConfig.IS_CORPORATE
    val cardHeight = if (isCorporate) 180.dp else 170.dp
    val cardSpacing = if (isCorporate) 12.dp else 14.dp

    fun upFocusFor(card: HomeNavCard): FocusRequester = when {
        !isCorporate -> roomBadgeFocus
        card == HomeNavCard.LiveTv || card == HomeNavCard.Entertainment -> alertBellFocus
        else -> roomBadgeFocus
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isCorporate) 200.dp else 170.dp),
        horizontalArrangement = Arrangement.spacedBy(
            cardSpacing,
            if (isCorporate) Alignment.CenterHorizontally else Alignment.Start,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fun cardMod(focus: FocusRequester, card: HomeNavCard): Modifier {
            val sized = if (isCorporate) {
                Modifier
                    .width(160.dp)
                    .height(cardHeight)
            } else {
                Modifier
                    .weight(1f)
                    .height(cardHeight)
            }
            return sized
                .focusRequester(focus)
                .focusProperties { up = upFocusFor(card) }
                .onFocusChanged { if (it.isFocused) onCardFocused(card) }
        }

        LuxuryNavCard(
            title = stringResource(R.string.feature_live_tv),
            subtitle = stringResource(R.string.feature_live_tv_subtitle),
            iconRes = NavCardIcons.liveTv,
            focusGlowColor = GoldLuxury,
            modifier = cardMod(liveTvFocus, HomeNavCard.LiveTv),
            onClick = onLiveTv,
        )
        LuxuryNavCard(
            title = stringResource(R.string.feature_entertainment),
            subtitle = stringResource(R.string.feature_entertainment_subtitle),
            iconRes = NavCardIcons.entertainment,
            focusGlowColor = GoldLuxury,
            modifier = cardMod(entertainmentFocus, HomeNavCard.Entertainment),
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
            iconRes = NavCardIcons.menu,
            focusGlowColor = GoldLuxury,
            modifier = cardMod(diningFocus, HomeNavCard.Dining),
            onClick = onDining,
        )
        if (isCorporate) {
            LuxuryNavCard(
                title = stringResource(R.string.feature_agenda),
                subtitle = stringResource(R.string.feature_agenda_subtitle),
                iconRes = NavCardIcons.agenda,
                focusGlowColor = GoldLuxury,
                modifier = cardMod(agendaFocus, HomeNavCard.Agenda),
                onClick = onAgenda,
            )
            LuxuryNavCard(
                title = stringResource(R.string.feature_emergency),
                subtitle = stringResource(R.string.feature_emergency_subtitle),
                iconRes = NavCardIcons.emergency,
                focusGlowColor = GoldLuxury,
                modifier = cardMod(servicesFocus, HomeNavCard.Services),
                onClick = onServices,
            )
        } else {
            LuxuryNavCard(
                title = stringResource(R.string.feature_services),
                subtitle = stringResource(R.string.feature_services_subtitle),
                iconRes = NavCardIcons.emergency,
                focusGlowColor = GoldLuxury,
                modifier = cardMod(servicesFocus, HomeNavCard.Services),
                onClick = onServices,
            )
            LuxuryNavCard(
                title = stringResource(R.string.feature_alerts),
                subtitle = if (unreadAlerts > 0) {
                    "$unreadAlerts new alert(s)"
                } else {
                    stringResource(R.string.feature_alerts_subtitle)
                },
                iconRes = NavCardIcons.alerts,
                focusGlowColor = GoldLuxury,
                modifier = cardMod(alertsFocus, HomeNavCard.Alerts),
                onClick = onAlerts,
            )
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
