package `in`.pcncloud.hotel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.ui.theme.CorpGoldBright
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import kotlinx.coroutines.launch

private val DialogCharcoal = Color(0xFF151821)
private val DialogCharcoalTop = Color(0xFF1C2330)
private val DialogCharcoalBottom = Color(0xFF10141C)
private val DialogInk = Color(0xFF0E1218)
private val MessageGray = Color(0xFFC5CDD8)
private val DialogShape = RoundedCornerShape(24.dp)
private val DismissShape = RoundedCornerShape(14.dp)

private fun isConfirmKey(key: Key): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BroadcastAlertOverlay(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val focusManager = LocalFocusManager.current
    var dismissHasFocus by remember(title, message) { mutableStateOf(false) }
    val appearAlpha = remember(title, message) { Animatable(0f) }
    val appearScale = remember(title, message) { Animatable(0.94f) }

    fun grabDismissFocus() {
        runCatching { dismissFocusRequester.requestFocus() }
    }

    LaunchedEffect(title, message) {
        focusManager.clearFocus(force = true)
        appearAlpha.snapTo(0f)
        appearScale.snapTo(0.94f)
        launch {
            appearAlpha.animateTo(
                1f,
                tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        }
        launch {
            appearScale.animateTo(
                1f,
                tween(durationMillis = 340, easing = FastOutSlowInEasing),
            )
        }
        var attempts = 0
        while (!dismissHasFocus && attempts < 45) {
            grabDismissFocus()
            withFrameNanos { }
            attempts++
        }
    }

    Box(
        modifier = modifier
            .zIndex(100f)
            .fillMaxSize()
            .graphicsLayer { alpha = appearAlpha.value }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xCC05070C),
                        Color(0xE6000000),
                    ),
                ),
            )
            .focusProperties {
                up = dismissFocusRequester
                down = dismissFocusRequester
                left = dismissFocusRequester
                right = dismissFocusRequester
                next = dismissFocusRequester
                previous = dismissFocusRequester
                start = dismissFocusRequester
                end = dismissFocusRequester
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Back || event.key == Key.Escape -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                        event.key == Key.Tab -> {
                        grabDismissFocus()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = appearScale.value
                    scaleY = appearScale.value
                }
                .padding(horizontal = 48.dp)
                .widthIn(min = 480.dp, max = 640.dp)
                .shadow(
                    elevation = 36.dp,
                    shape = DialogShape,
                    ambientColor = Color.Black.copy(alpha = 0.75f),
                    spotColor = GoldPrimary.copy(alpha = 0.28f),
                    clip = false,
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DialogCharcoalTop,
                            DialogCharcoal,
                            DialogCharcoalBottom,
                        ),
                    ),
                    shape = DialogShape,
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GoldLight.copy(alpha = 0.62f),
                            Color.White.copy(alpha = 0.18f),
                            GoldPrimary.copy(alpha = 0.42f),
                        ),
                    ),
                    shape = DialogShape,
                )
                .padding(horizontal = 44.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AlertBellIcon()

            Text(
                text = title.ifBlank { "Message Alert" },
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SerifDisplay,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 0.4.sp,
                lineHeight = 42.sp,
            )

            Text(
                text = message,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = SansBody,
                color = MessageGray,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                letterSpacing = 0.15.sp,
            )

            DismissAlertButton(
                onDismiss = onDismiss,
                focusRequester = dismissFocusRequester,
                hasFocus = dismissHasFocus,
                onFocusChanged = { dismissHasFocus = it },
                onLaidOut = { grabDismissFocus() },
            )
        }
    }
}

@Composable
private fun AlertBellIcon() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldLuxury.copy(alpha = 0.48f),
                            GoldLuxury.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.38f),
                            GoldPrimary.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = GoldLuxury.copy(alpha = 0.55f),
                    spotColor = GoldLuxury.copy(alpha = 0.70f),
                    clip = false,
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2C261A), DialogInk),
                    ),
                )
                .border(1.dp, GoldPrimary.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔔", fontSize = 28.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DismissAlertButton(
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
    hasFocus: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onLaidOut: () -> Unit,
) {
    Button(
        onClick = onDismiss,
        modifier = Modifier
            .padding(top = 10.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .onGloballyPositioned { onLaidOut() }
            .onKeyEvent { event ->
                if (!isConfirmKey(event.key)) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        onDismiss()
                        true
                    }
                    KeyEventType.KeyUp -> true
                    else -> false
                }
            },
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            contentColor = Color.White,
            focusedContainerColor = CorpGoldBright,
            focusedContentColor = DialogInk,
            pressedContainerColor = GoldPrimary,
            pressedContentColor = DialogInk,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.05f, pressedScale = 1.0f),
        glow = ButtonDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow(
                elevationColor = CorpGoldBright.copy(alpha = 0.90f),
                elevation = 28.dp,
            ),
            pressedGlow = Glow(
                elevationColor = GoldPrimary.copy(alpha = 0.70f),
                elevation = 16.dp,
            ),
        ),
        shape = ButtonDefaults.shape(
            shape = DismissShape,
            focusedShape = DismissShape,
            pressedShape = DismissShape,
        ),
        border = ButtonDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                shape = DismissShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, GoldLight),
                shape = DismissShape,
            ),
            pressedBorder = Border(
                border = BorderStroke(2.dp, GoldLight),
                shape = DismissShape,
            ),
        ),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.dismiss),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SansBody,
            letterSpacing = 0.8.sp,
            color = if (hasFocus) DialogInk else Color.White,
        )
    }
}

/** @deprecated Use [BroadcastAlertOverlay] — Dialog-based popups are unreliable on Android TV */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlertPopup(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    BroadcastAlertOverlay(
        title = title,
        message = message,
        onDismiss = onDismiss,
    )
}
