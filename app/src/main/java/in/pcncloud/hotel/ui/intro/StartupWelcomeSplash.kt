package `in`.pcncloud.hotel.ui.intro

import android.widget.ProgressBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.BuildConfig
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.ui.home.BrandAssets
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary

/**
 * Lightweight root wrapper shown only while [StartupRoute.Checking].
 * Branded welcome — not interactive Home — so cold boot never lands on Home
 * before the gate has decided Intro vs Home.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StartupWelcomeSplash(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            if (BuildConfig.IS_CORPORATE) {
                Image(
                    painter = painterResource(BrandAssets.logoRes),
                    contentDescription = stringResource(R.string.splash_logo_content_description),
                    modifier = Modifier
                        .size(96.dp)
                        .padding(bottom = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.splash_welcome_loading),
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.splash_status_loading),
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            AndroidView(
                factory = { ctx ->
                    ProgressBar(ctx).apply {
                        isIndeterminate = true
                        val gold = ContextCompat.getColor(ctx, R.color.splash_gold)
                        indeterminateTintList = android.content.res.ColorStateList.valueOf(gold)
                    }
                },
                modifier = Modifier
                    .padding(top = 32.dp)
                    .size(48.dp),
            )
        }
    }
}
