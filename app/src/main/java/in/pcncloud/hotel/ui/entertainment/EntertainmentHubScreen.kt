package `in`.pcncloud.hotel.ui.entertainment

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.R
import `in`.pcncloud.hotel.integration.AppLauncherUtils
import `in`.pcncloud.hotel.ui.components.LuxuryScreenBackground
import `in`.pcncloud.hotel.ui.components.LuxuryScreenHeader
import `in`.pcncloud.hotel.ui.components.luxuryBackHandler
import `in`.pcncloud.hotel.ui.theme.GoldLight
import `in`.pcncloud.hotel.ui.theme.GoldPrimary
import `in`.pcncloud.hotel.ui.theme.NavySurface
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.TextMuted
import `in`.pcncloud.hotel.ui.theme.TextPrimary

data class EntertainmentApp(
    val id: String,
    val labelRes: Int,
    val packageName: String,
    /** High-res PNG fallback when the app is not installed. */
    @param:DrawableRes val fallbackIconRes: Int,
)

fun entertainmentCatalog(): List<EntertainmentApp> = listOf(
    EntertainmentApp(
        id = "youtube",
        labelRes = R.string.ott_youtube,
        packageName = "com.google.android.youtube.tv",
        fallbackIconRes = R.drawable.ic_youtube,
    ),
    EntertainmentApp(
        id = "netflix",
        labelRes = R.string.ott_netflix,
        packageName = "com.netflix.ninja",
        fallbackIconRes = R.drawable.ic_netflix,
    ),
    EntertainmentApp(
        id = "prime",
        labelRes = R.string.ott_prime,
        packageName = "com.amazon.amazonvideo.livingroom",
        fallbackIconRes = R.drawable.ic_prime_video,
    ),
    EntertainmentApp(
        id = "hotstar",
        labelRes = R.string.ott_hotstar,
        packageName = "in.startv.hotstar",
        fallbackIconRes = R.drawable.ic_hotstar,
    ),
    EntertainmentApp(
        id = "zee5",
        labelRes = R.string.ott_zee5,
        packageName = "com.graymatrix.did",
        fallbackIconRes = R.drawable.ic_zee5,
    ),
    EntertainmentApp(
        id = "jiocinema",
        labelRes = R.string.ott_jiocinema,
        packageName = "com.jio.media.ondemand",
        fallbackIconRes = R.drawable.ic_jiocinema,
    ),
    EntertainmentApp(
        id = "sonyliv",
        labelRes = R.string.ott_sonyliv,
        packageName = "com.sonyliv",
        fallbackIconRes = R.drawable.ic_sonyliv,
    ),
    EntertainmentApp(
        id = "spotify",
        labelRes = R.string.ott_spotify,
        packageName = "com.spotify.tv.android",
        fallbackIconRes = R.drawable.ic_spotify,
    ),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EntertainmentHubScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { entertainmentCatalog() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryBackHandler(onBack),
    ) {
        LuxuryScreenBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp),
        ) {
            LuxuryScreenHeader(
                title = stringResource(R.string.entertainment_title),
                subtitle = stringResource(R.string.entertainment_subtitle),
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                    EntertainmentAppTile(
                        app = app,
                        installed = AppLauncherUtils.isAppInstalled(context, app.packageName),
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                        onClick = {
                            AppLauncherUtils.launchOrInstall(
                                context = context,
                                packageName = app.packageName,
                                appLabel = context.getString(app.labelRes),
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EntertainmentAppTile(
    app: EntertainmentApp,
    installed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.06f else 1f
    val shape = RoundedCornerShape(16.dp)
    val iconSize = 88.dp

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        NavySurface.copy(alpha = 0.95f),
                        Color(0xFF152238),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) GoldPrimary else Color.White.copy(alpha = 0.18f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
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
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            EntertainmentAppIcon(
                packageName = app.packageName,
                installed = installed,
                fallbackIconRes = app.fallbackIconRes,
                contentDescription = stringResource(app.labelRes),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(app.labelRes),
            color = if (focused) GoldLight else TextPrimary,
            fontFamily = SansBody,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(
                if (installed) R.string.entertainment_installed
                else R.string.entertainment_get_app,
            ),
            color = if (installed) Color(0xFF86EFAC) else TextMuted,
            fontFamily = SansBody,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Installed → official system app icon via [android.content.pm.PackageManager.getApplicationIcon].
 * Not installed → static high-res PNG in `res/drawable/`.
 * Always [ContentScale.Fit] (fitCenter) so logos stay undistorted inside the rounded card.
 */
@Composable
private fun EntertainmentAppIcon(
    packageName: String,
    installed: Boolean,
    @DrawableRes fallbackIconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    rasterSize: Dp = 192.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { rasterSize.roundToPx().coerceAtLeast(128) }

    val systemBitmap = remember(packageName, installed, px) {
        if (!installed) return@remember null
        runCatching {
            val icon: Drawable = context.packageManager.getApplicationIcon(packageName)
            when (icon) {
                is BitmapDrawable -> {
                    val src = icon.bitmap
                    if (src != null && !src.isRecycled) {
                        src.copy(src.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                    } else {
                        icon.toBitmap(px, px)
                    }
                }
                else -> icon.toBitmap(px, px)
            }
        }.getOrNull()
    }

    if (systemBitmap != null) {
        Image(
            bitmap = systemBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
        )
    } else {
        Image(
            painter = painterResource(fallbackIconRes),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
        )
    }
}
