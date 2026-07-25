package com.example.ikhsanahoteltv.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.NavyMain
import com.example.ikhsanahoteltv.ui.theme.NavySurface
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.SerifDisplay
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary

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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LuxuryScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SerifDisplay,
            color = TextPrimary,
            letterSpacing = 1.sp,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 15.sp,
                fontFamily = SansBody,
                color = TextMuted,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        GoldSeparatorLine()
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

@Composable
fun LuxuryGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NavySurface.copy(alpha = 0.85f),
                        NavyDeep.copy(alpha = 0.75f),
                    ),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .border(1.dp, GoldPrimary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        content()
    }
}
