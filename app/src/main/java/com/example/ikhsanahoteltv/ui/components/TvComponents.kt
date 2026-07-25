package com.example.ikhsanahoteltv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.ikhsanahoteltv.ui.services.ServiceToastType
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary

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
    icon: String,
    focusGlowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "navCardScale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "navCardGlow",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = 20.dp,
                        shape = cardShape,
                        ambientColor = focusGlowColor.copy(alpha = 0.5f * glowAlpha),
                        spotColor = focusGlowColor.copy(alpha = 0.7f * glowAlpha),
                    )
                } else {
                    Modifier
                },
            )
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NavyDeep.copy(alpha = if (isFocused) 0.72f else 0.52f),
                        NavyDeep.copy(alpha = if (isFocused) 0.62f else 0.42f),
                    ),
                ),
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                brush = if (isFocused) {
                    Brush.linearGradient(
                        listOf(
                            focusGlowColor,
                            focusGlowColor.copy(alpha = 0.6f),
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
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .background(Color.Transparent),
        ) {
            Text(text = icon, fontSize = 40.sp)
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SansBody,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontFamily = SansBody,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "›",
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                color = if (isFocused) focusGlowColor else TextMuted.copy(alpha = 0.5f),
            )
        }
    }
}

/** @deprecated Use [LuxuryNavCard] on the home screen */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LuxuryNavCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        focusGlowColor = accentColor,
        modifier = modifier.size(width = 220.dp, height = 180.dp),
        onClick = onClick,
    )
}
