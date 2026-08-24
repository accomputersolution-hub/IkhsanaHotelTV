package `in`.pcncloud.hotel.ui.intro

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import `in`.pcncloud.hotel.config.IntroVideoCache
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.TextPrimary
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/**
 * Fullscreen intro playback. URL comes from [IntroVideoCache] via NavHost
 * startDestination — no Firestore race on cold boot.
 *
 * Skip / end / hard error → [onFinished] exactly once (atomic).
 */
@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun IntroVideoScreen(
    videoUrl: String,
    onFinished: (reason: String) -> Unit,
) {
    val finished = remember { AtomicBoolean(false) }
    val skipFocus = remember { FocusRequester() }
    val mediaUri = remember(videoUrl) { IntroVideoCache.parseMediaUri(videoUrl) }

    fun finishOnce(reason: String) {
        if (!finished.compareAndSet(false, true)) return
        Log.i(TAG, "IntroVideoScreen finish ($reason)")
        onFinished(reason)
    }

    BackHandler(enabled = true) {
        finishOnce("back")
    }

    LaunchedEffect(mediaUri) {
        if (mediaUri == null) {
            Log.e(TAG, "Invalid intro URI — Home. raw=${videoUrl.take(80)}")
            finishOnce("bad_uri")
        }
    }

    LaunchedEffect(Unit) {
        runCatching { skipFocus.requestFocus() }
    }

    LaunchedEffect(videoUrl) {
        delay(PLAYBACK_WATCHDOG_MS)
        finishOnce("playback_watchdog")
    }

    if (mediaUri == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDeep),
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
    ) {
        IntroExoPlayer(
            videoUrl = videoUrl,
            onEnded = { finishOnce("ended") },
            onError = { msg ->
                Log.e(TAG, "playback error → Home: $msg")
                finishOnce("error")
            },
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = { finishOnce("skip") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 36.dp)
                .focusRequester(skipFocus),
            colors = ButtonDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                focusedContainerColor = GoldLuxury.copy(alpha = 0.9f),
                pressedContainerColor = GoldLuxury,
                contentColor = TextPrimary,
                focusedContentColor = NavyDeep,
                pressedContentColor = NavyDeep,
            ),
        ) {
            Text(text = "Skip")
        }
    }
}

@UnstableApi
@Composable
private fun IntroExoPlayer(
    videoUrl: String,
    onEnded: () -> Unit,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playbackEverStarted by remember(videoUrl) { mutableStateOf(false) }
    val mediaUri = remember(videoUrl) { IntroVideoCache.parseMediaUri(videoUrl) }

    val exoPlayer = remember(videoUrl) {
        val uri = mediaUri
        if (uri == null) {
            Log.e(TAG, "ExoPlayer skip create — bad URI")
            return@remember null
        }
        Log.i(TAG, "ExoPlayer create+prepare scheme=${uri.scheme} host=${uri.host}")
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(INTRO_HTTP_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1f
                // MediaItem.Builder + parsed Uri — safer on API 28 TV than raw string.
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMimeType(null) // let Media3 sniff progressive mp4/http
                        .build(),
                )
                prepare()
            }
    }

    if (exoPlayer == null) {
        LaunchedEffect(Unit) { onError("bad_uri") }
        return
    }

    DisposableEffect(exoPlayer, videoUrl) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val name = stateName(playbackState)
                val duration = exoPlayer.duration
                val position = exoPlayer.currentPosition
                Log.i(
                    TAG,
                    "ExoPlayer state=$name playWhenReady=${exoPlayer.playWhenReady} " +
                        "isPlaying=${exoPlayer.isPlaying} durationMs=$duration " +
                        "positionMs=$position everStarted=$playbackEverStarted",
                )

                when (playbackState) {
                    Player.STATE_BUFFERING -> Unit
                    Player.STATE_READY -> {
                        if (duration == 0L) {
                            onError(
                                "Media duration is 0 ms — empty/bad source. url=$videoUrl",
                            )
                            return
                        }
                        if (exoPlayer.playWhenReady) {
                            playbackEverStarted = true
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (!playbackEverStarted ||
                            duration == C.TIME_UNSET ||
                            duration <= 0L ||
                            position < 250L
                        ) {
                            onError(
                                "STATE_ENDED before real playback " +
                                    "(everStarted=$playbackEverStarted durationMs=$duration " +
                                    "positionMs=$position). url=$videoUrl",
                            )
                        } else {
                            onEnded()
                        }
                    }
                    Player.STATE_IDLE -> Unit
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playbackEverStarted = true
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(
                    "${error.errorCodeName} (${error.errorCode}): ${error.message}",
                )
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = exoPlayer
            }
        },
        update = { view -> view.player = exoPlayer },
        modifier = modifier,
    )
}

private fun stateName(playbackState: Int): String = when (playbackState) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "OTHER($playbackState)"
}

private const val TAG = "IntroVideoScreen"
private const val PLAYBACK_WATCHDOG_MS = 90_000L

private const val INTRO_HTTP_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 HostityIntro/1.0"
