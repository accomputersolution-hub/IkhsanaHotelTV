package `in`.pcncloud.hotel.ui.intro

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
 * Home-first intro host: Firestore + ExoPlayer prepare run in the background while
 * Home is already on screen. Overlay paints only after media is READY — never a
 * blocking "Loading intro…" screen.
 *
 * @param allowShow when false (guest opened a submenu), abandon prepare without overlay.
 * @param onPlayingChanged notifies parent so kiosk / focus treat intro as covering Home.
 * @param onSessionComplete called once when the intro session ends (shown or silent skip).
 */
@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun IntroVideoHost(
    viewModelFactory: HotelViewModelFactory,
    allowShow: Boolean,
    onPlayingChanged: (Boolean) -> Unit,
    onSessionComplete: () -> Unit,
) {
    val viewModel: IntroVideoViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()
    val skipFocus = remember { FocusRequester() }

    LaunchedEffect(uiState.isSessionComplete) {
        if (uiState.isSessionComplete) onSessionComplete()
    }

    LaunchedEffect(uiState.shouldShowOverlay) {
        onPlayingChanged(uiState.shouldShowOverlay)
    }

    LaunchedEffect(allowShow, uiState.phase) {
        if (!allowShow &&
            (uiState.phase == IntroPhase.Checking || uiState.phase == IntroPhase.Preparing)
        ) {
            viewModel.abandonBecauseBusy("submenu_open")
        }
    }

    BackHandler(enabled = uiState.shouldShowOverlay) {
        viewModel.onSkip()
    }

    LaunchedEffect(uiState.phase) {
        if (uiState.phase == IntroPhase.Playing) {
            runCatching { skipFocus.requestFocus() }
        }
    }

    when (uiState.phase) {
        IntroPhase.Checking, IntroPhase.Finished -> {
            // Nothing — Home remains the only UI.
        }
        IntroPhase.Preparing, IntroPhase.Playing -> {
            val showOverlay = uiState.phase == IntroPhase.Playing
            Box(
                modifier = if (showOverlay) {
                    Modifier
                        .fillMaxSize()
                        .background(NavyDeep)
                } else {
                    // Keep ExoPlayer in the tree offscreen until READY — never steal Home focus.
                    Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusProperties { canFocus = false }
                },
            ) {
                IntroExoPlayer(
                    videoUrl = uiState.videoUrl,
                    playWhenReady = showOverlay,
                    onMediaReady = {
                        if (allowShow) {
                            viewModel.onMediaReady()
                        } else {
                            viewModel.abandonBecauseBusy("ready_but_busy")
                        }
                    },
                    onEnded = viewModel::onPlaybackEnded,
                    onError = viewModel::onPlaybackError,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showOverlay) {
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
            }
        }
    }
}

/**
 * Legacy entry used only if a caller still expects [IntroVideoScreen].
 * Prefer [IntroVideoHost] for Home-first boot.
 */
@Composable
fun IntroVideoScreen(
    viewModelFactory: HotelViewModelFactory,
    onFinished: () -> Unit,
) {
    IntroVideoHost(
        viewModelFactory = viewModelFactory,
        allowShow = true,
        onPlayingChanged = {},
        onSessionComplete = onFinished,
    )
}

@UnstableApi
@Composable
private fun IntroExoPlayer(
    videoUrl: String,
    playWhenReady: Boolean,
    onMediaReady: () -> Unit,
    onEnded: () -> Unit,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playbackEverStarted by remember(videoUrl) { mutableStateOf(false) }
    var reportedReady by remember(videoUrl) { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl) {
        Log.i(TAG, "ExoPlayer create+prepare (background) url=$videoUrl")
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(INTRO_HTTP_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .apply {
                this.playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1f
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl.trim())))
                prepare()
            }
    }

    LaunchedEffect(exoPlayer, playWhenReady) {
        exoPlayer.playWhenReady = playWhenReady
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
                        "everStarted=$playbackEverStarted reportedReady=$reportedReady",
                )

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "ExoPlayer buffering — keep preparing offscreen")
                    }
                    Player.STATE_READY -> {
                        if (duration == 0L) {
                            val msg =
                                "Media duration is 0 ms — empty/bad source. url=$videoUrl"
                            Log.e(TAG, msg)
                            onError(msg)
                            return
                        }
                        if (!reportedReady) {
                            reportedReady = true
                            Log.i(TAG, "ExoPlayer READY — promote overlay")
                            onMediaReady()
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
                            val msg =
                                "STATE_ENDED before real playback " +
                                    "(everStarted=$playbackEverStarted durationMs=$duration " +
                                    "positionMs=$position). url=$videoUrl"
                            Log.e(TAG, msg)
                            onError(msg)
                        } else {
                            Log.i(TAG, "STATE_ENDED after playback — intro complete")
                            onEnded()
                        }
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "ExoPlayer IDLE — ignore")
                    }
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(TAG, "ExoPlayer onIsPlayingChanged isPlaying=$isPlaying")
                if (isPlaying) {
                    playbackEverStarted = true
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
                        "url=$videoUrl " +
                        "causeChain=[$causeChain]",
                    error,
                )
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
