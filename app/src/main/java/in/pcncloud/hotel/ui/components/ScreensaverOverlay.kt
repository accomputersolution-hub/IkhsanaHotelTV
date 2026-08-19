package `in`.pcncloud.hotel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.data.model.HotelBranding
import `in`.pcncloud.hotel.ui.home.BrandAssets
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.NavyMain
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.SerifDisplay
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Full-screen hotel screen saver. Nav / session underneath stay intact;
 * any remote key is handled by [MainActivity] to dismiss this overlay.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScreensaverOverlay(
    branding: HotelBranding,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val infinite = rememberInfiniteTransition(label = "screensaver")
    val pulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hintPulse",
    )
    val glow by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoGlow",
    )
    val drift by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bgDrift",
    )

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val date = Date(now)

    Box(
        modifier = modifier
            .zIndex(80f)
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .background(NavyDeep),
        contentAlignment = Alignment.Center,
    ) {
        ScreensaverBackground(
            wallpaperUrl = branding.bgWallpaperUrl,
            drift = drift,
        )

        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ScreensaverLogo(
                logoUrl = branding.logoUrl,
                glow = glow,
            )

            Spacer(modifier = Modifier.height(28.dp))

            val hotelName = branding.hotelName.ifBlank {
                stringResource(R.string.app_name)
            }
            Text(
                text = hotelName,
                color = TextPrimary,
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (branding.tagline.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = branding.tagline.uppercase(Locale.getDefault()),
                    color = GoldPrimary,
                    fontFamily = SansBody,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Spacer(
                modifier = Modifier
                    .width(120.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                GoldPrimary.copy(alpha = 0.85f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = timeFmt.format(date),
                color = TextPrimary,
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 72.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateFmt.format(date),
                color = TextMuted,
                fontFamily = SansBody,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.screensaver_press_any_key),
                color = GoldLight,
                fontFamily = SansBody,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(pulse),
            )
        }
    }
}

@Composable
private fun ScreensaverBackground(
    wallpaperUrl: String,
    drift: Float,
) {
    val lower = wallpaperUrl.lowercase(Locale.US)
    val useRemote = wallpaperUrl.isNotBlank() &&
        !lower.contains(".svg") &&
        !lower.contains("image/svg") &&
        !lower.contains("format=svg") &&
        !lower.contains("data:image/svg")

    Box(modifier = Modifier.fillMaxSize()) {
        if (useRemote) {
            AsyncImage(
                model = hotelImageRequest(
                    context = LocalContext.current,
                    url = wallpaperUrl,
                    logTag = "ScreensaverWallpaper",
                ),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = drift
                        scaleY = drift
                    },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NavyDeep,
                                NavyMain,
                                Color(0xFF152238),
                                NavyDeep,
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun ScreensaverLogo(
    logoUrl: String,
    glow: Float,
) {
    val localLogo = painterResource(BrandAssets.logoRes)
    val remoteUrl = logoUrl.trim().trim('"', '\'').trim().takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .size(160.dp)
            .graphicsLayer {
                scaleX = glow
                scaleY = glow
                alpha = 0.95f
            },
        contentAlignment = Alignment.Center,
    ) {
        if (remoteUrl != null) {
            AsyncImage(
                model = hotelImageRequest(
                    context = LocalContext.current,
                    url = remoteUrl,
                    logTag = "ScreensaverLogo",
                ),
                contentDescription = stringResource(R.string.screensaver_logo_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                error = localLogo,
            )
        } else {
            Image(
                painter = localLogo,
                contentDescription = stringResource(R.string.screensaver_logo_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
