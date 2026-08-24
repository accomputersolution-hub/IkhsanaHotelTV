package `in`.pcncloud.hotel.ui.intro

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import `in`.pcncloud.hotel.ui.HotelViewModelFactory
import `in`.pcncloud.hotel.ui.theme.GoldLuxury
import `in`.pcncloud.hotel.ui.theme.NavyDeep
import `in`.pcncloud.hotel.ui.theme.TextPrimary

/**
 * Fullscreen branded intro after pairing splash → MainActivity.
 * Plays [IntroVideoUiState.videoUrl] with Media3 ExoPlayer (no controls).
 * Skip / real end / hard error / timeout → [onFinished].
 *
 * Buffering / READY / transient states do **not** finish the intro.
 */
@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun IntroVideoScreen(
    viewModelFactory: HotelViewModelFactory,
    onFinished: () -> Unit,
) {
    val viewModel: IntroVideoViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val skipFocus = remember { FocusRequester() }

    LaunchedEffect(uiState.shouldEnterHome) {
        if (uiState.shouldEnterHome) onFinished()
    }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == IntroPhase.Playing) {
            // Focus Skip for D-pad, but do not auto-click it.
            runCatching { skipFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep),
    ) {
        when (uiState.phase) {
            IntroPhase.Resolving -> {
                Text(
                    text = if (uiState.hotelId.isNotBlank()) {
                        "Loading intro… (${uiState.hotelId})"
                    } else {
                        "Loading intro…"
                    },
                    color = TextPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            IntroPhase.Playing -> {
                IntroExoPlayer(
                    videoUrl = uiState.videoUrl,
                    onPlaybackStarted = viewModel::onPlaybackStarted,
                    onEnded = viewModel::onPlaybackEnded,
                    onError = viewModel::onPlaybackError,
                    modifier = Modifier.fillMaxSize(),
                )
                if (uiState.playerError.isNotBlank()) {
                    Text(
                        text = uiState.playerError,
                        color = Color(0xFFFFCDD2),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp, start = 32.dp, end = 32.dp),
                    )
                }
                Button(
                    onClick = viewModel::onSkip,
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
            IntroPhase.Finished -> {
                // Brief blank while parent swaps to Home.
            }
        }
    }
}

@UnstableApi
@Composable
private fun IntroExoPlayer(
    videoUrl: String,
    onPlaybackStarted: () -> Unit,
    onEnded: () -> Unit,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playbackEverStarted by remember(videoUrl) { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl) {
        Log.i(TAG, "ExoPlayer create+prepare url=$videoUrl")
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
                // Keep control of when we leave intro — ignore short auto-transitions.
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl.trim())))
                prepare()
            }
    }

    DisposableEffect(exoPlayer, videoUrl) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val name = stateName(playbackState)
                val duration = exoPlayer.duration
                val position = exoPlayer.currentPosition
                Log.i(
                    TAG,
                    "ExoPlayer onPlaybackStateChanged state=$name " +
                        "playWhenReady=${exoPlayer.playWhenReady} " +
                        "isPlaying=${exoPlayer.isPlaying} " +
                        "durationMs=$duration positionMs=$position " +
                        "everStarted=$playbackEverStarted",
                )

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        // Buffering is normal — never finish intro here.
                        Log.d(TAG, "ExoPlayer buffering — keep waiting")
                    }
                    Player.STATE_READY -> {
                        if (exoPlayer.playWhenReady) {
                            // Duration known after READY; 0 / UNSET ⇒ empty or bad source.
                            if (duration == 0L) {
                                val msg =
                                    "Media duration is 0 ms — URL likely returns an empty file " +
                                        "(e.g. Catbox 0-byte). Re-upload a real .mp4 or paste a " +
                                        "direct HTTPS link that downloads >0 bytes. url=$videoUrl"
                                Log.e(TAG, msg)
                                onError(msg)
                                return
                            }
                            playbackEverStarted = true
                            onPlaybackStarted()
                        }
                    }
                    Player.STATE_ENDED -> {
                        // Empty / failed sources often jump IDLE→ENDED with duration UNSET/0.
                        if (!playbackEverStarted ||
                            duration == C.TIME_UNSET ||
                            duration <= 0L ||
                            position < 250L
                        ) {
                            val msg =
                                "STATE_ENDED before real playback " +
                                    "(everStarted=$playbackEverStarted durationMs=$duration " +
                                    "positionMs=$position). Source may be empty or unreadable. " +
                                    "url=$videoUrl"
                            Log.e(TAG, msg)
                            onError(msg)
                        } else {
                            Log.i(TAG, "STATE_ENDED after playback — intro complete")
                            onEnded()
                        }
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "ExoPlayer IDLE — ignore (not a finish signal)")
                    }
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(TAG, "ExoPlayer onIsPlayingChanged isPlaying=$isPlaying")
                if (isPlaying) {
                    playbackEverStarted = true
                    onPlaybackStarted()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val causeChain = buildString {
                    var c: Throwable? = error
                    var depth = 0
                    while (c != null && depth < 6) {
                        if (depth > 0) append(" ← ")
                        append(c.javaClass.simpleName)
                        append(": ")
                        append(c.message)
                        c = c.cause
                        depth++
                    }
                }
                Log.e(
                    TAG,
                    "ExoPlayer onPlayerError " +
                        "errorCode=${error.errorCode} " +
                        "errorCodeName=${error.errorCodeName} " +
                        "message=${error.message} " +
                        "timestampMs=${error.timestampMs} " +
                        "url=$videoUrl " +
                        "causeChain=[$causeChain]",
                    error,
                )
                // Dump nested causes separately for logcat filters.
                var nested: Throwable? = error.cause
                var i = 1
                while (nested != null && i <= 5) {
                    Log.e(TAG, "ExoPlayer cause[$i]=${nested.javaClass.name}: ${nested.message}", nested)
                    nested = nested.cause
                    i++
                }
                onError("${error.errorCodeName} (${error.errorCode}): ${error.message} | $causeChain")
            }
        }

        Log.i(TAG, "ExoPlayer addListener url=$videoUrl")
        exoPlayer.addListener(listener)
        onDispose {
            Log.i(TAG, "ExoPlayer removeListener+release")
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

/** Browser-like UA — some CDNs reject the default ExoPlayer user-agent. */
private const val INTRO_HTTP_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 HostityIntro/1.0"
