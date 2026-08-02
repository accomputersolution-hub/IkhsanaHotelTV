package `in`.pcncloud.hotel.ui.entertainment

import android.content.ActivityNotFoundException
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import `in`.pcncloud.hotel.kiosk.KioskPolicy
import `in`.pcncloud.hotel.ui.components.LuxuryScreenBackground
import `in`.pcncloud.hotel.ui.components.LuxuryScreenHeader
import `in`.pcncloud.hotel.ui.components.luxuryBackHandler
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.SansBody
import `in`.pcncloud.hotel.ui.theme.TextPrimary
import kotlinx.coroutines.delay

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
    var clicksEnabled by remember { mutableStateOf(false) }

    // Returning from YouTube often delivers a leftover OK/Enter KeyUp to the first tile.
    // Suppress launches briefly via clicksEnabled + KioskPolicy.shouldSuppressOttLaunch.
    // Do NOT clearOttLaunchState on dispose — pre-OTT Root Home pops this screen and
    // clearing would let Watchdog steal YouTube focus.

    LaunchedEffect(Unit) {
        clicksEnabled = false
        delay(900)
        clicksEnabled = true
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
                            if (!clicksEnabled || KioskPolicy.shouldSuppressOttLaunch(context)) {
                                Log.d(
                                    "EntertainmentHub",
                                    "Ignoring OTT click (suppress/cooldown) → ${app.packageName}",
                                )
                                return@EntertainmentAppTile
                            }
                            val packageName = app.packageName
                            try {
                                // Standard launch-intent check — do not leave this screen if missing.
                                val launchIntent =
                                    context.packageManager.getLaunchIntentForPackage(packageName)
                                        ?: AppLauncherUtils.buildSafeLaunchIntent(
                                            context,
                                            packageName,
                                        )
                                if (launchIntent != null) {
                                    AppLauncherUtils.launchOrInstall(
                                        context = context,
                                        packageName = packageName,
                                        appLabel = context.getString(app.labelRes),
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.entertainment_app_unavailable),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.entertainment_app_unavailable),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } catch (e: Exception) {
                                Log.e("EntertainmentHub", "Launch failed → $packageName", e)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.entertainment_app_unavailable),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "ottCardScale",
    )
    val shape = RoundedCornerShape(18.dp)
    val glassFill = Color(0x33000000)
    val idleBorder = Color.White.copy(alpha = 0.15f)
    val focusBorder = GoldLuxury // #D4AF37
    val installedGreen = Color(0xFF22C55E)
    val secondaryText = Color.White.copy(alpha = 0.60f)

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focused) {
                    Modifier.shadow(
                        elevation = 20.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.65f),
                        spotColor = Color.Black.copy(alpha = 0.80f),
                        clip = false,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(glassFill, shape)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) focusBorder else idleBorder,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                // Use KeyUp only — leftover KeyDown/Enter from YouTube often fires on resume.
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.DirectionCenter)
                }
            }
            .padding(vertical = 20.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pure transparent logo on the frosted glass — no plate, clip, or fill behind it.
        EntertainmentAppIcon(
            packageName = app.packageName,
            installed = installed,
            fallbackIconRes = app.fallbackIconRes,
            contentDescription = stringResource(app.labelRes),
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(app.labelRes),
            color = TextPrimary,
            fontFamily = SansBody,
            fontWeight = FontWeight.Bold,
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
            color = if (installed) installedGreen else secondaryText,
            fontFamily = SansBody,
            fontWeight = if (installed) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Installed → official system app icon via [android.content.pm.PackageManager.getApplicationIcon].
 * Not installed → static high-res PNG in `res/drawable/`.
 * No background, no clip — only [Modifier] size + [ContentScale.Fit].
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
