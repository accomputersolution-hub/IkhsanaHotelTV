package `in`.pcncloud.hotel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BroadcastAlertOverlay(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(title, message) {
        dismissFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .zIndex(100f)
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .widthIn(max = 560.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            NavyDeep.copy(alpha = 0.96f),
                            Color(0xFF152238).copy(alpha = 0.98f),
                        ),
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .border(2.dp, GoldPrimary.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7F1D1D).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🔔", fontSize = 26.sp)
            }

            Text(
                text = title.ifBlank { "Message Alert" },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SerifDisplay,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Text(
                text = message,
                fontSize = 17.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = SansBody,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            DismissAlertButton(
                onDismiss = onDismiss,
                focusRequester = dismissFocusRequester,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DismissAlertButton(
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .background(
                if (focused) GoldPrimary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) GoldPrimary else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 40.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Dismiss",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) GoldLight else TextPrimary,
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
