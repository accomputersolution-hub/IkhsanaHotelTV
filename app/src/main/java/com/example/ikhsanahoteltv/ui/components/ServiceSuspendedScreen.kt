package com.example.ikhsanahoteltv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.ikhsanahoteltv.R
import com.example.ikhsanahoteltv.ui.theme.GoldLight
import com.example.ikhsanahoteltv.ui.theme.GoldPrimary
import com.example.ikhsanahoteltv.ui.theme.NavyDeep
import com.example.ikhsanahoteltv.ui.theme.NavyMain
import com.example.ikhsanahoteltv.ui.theme.SansBody
import com.example.ikhsanahoteltv.ui.theme.SerifDisplay
import com.example.ikhsanahoteltv.ui.theme.TextMuted
import com.example.ikhsanahoteltv.ui.theme.TextPrimary

/**
 * Full-screen lockout when Hotels/{hotelId}.status is "inactive".
 * Non-dismissible — guest UI stays blocked until Super Admin reactivates the hotel.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServiceSuspendedScreen(
    hotelName: String = "",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NavyDeep, NavyMain, NavyDeep),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(48.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NavyMain.copy(alpha = 0.92f))
                .border(1.dp, GoldPrimary.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary.copy(alpha = 0.15f))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = GoldLight,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifDisplay,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.service_suspended_title),
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SerifDisplay,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.service_suspended_message),
                color = TextMuted,
                fontSize = 18.sp,
                fontFamily = SansBody,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )

            if (hotelName.isNotBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = hotelName,
                    color = GoldLight,
                    fontSize = 16.sp,
                    fontFamily = SansBody,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.service_suspended_hint),
                color = TextMuted.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontFamily = SansBody,
                textAlign = TextAlign.Center,
            )
        }
    }
}
