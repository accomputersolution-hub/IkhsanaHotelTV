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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
 * Skip / end / error / timeout → [onFinished] so Home is never blocked.
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
    val exoPlayer = remember(videoUrl) {
        Log.i(TAG, "ExoPlayer prepare urlLen=${videoUrl.length} prefix=${videoUrl.take(72)}")
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val name = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "OTHER($playbackState)"
                }
                Log.d(TAG, "ExoPlayer state=$name playWhenReady=${exoPlayer.playWhenReady}")
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                            onPlaybackStarted()
                        }
                    }
                    Player.STATE_ENDED -> onEnded()
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "ExoPlayer isPlaying=$isPlaying")
                if (isPlaying) onPlaybackStarted()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "ExoPlayer error code=${error.errorCodeName} msg=${error.message} " +
                        "cause=${error.cause?.message} urlPrefix=${videoUrl.take(72)}",
                    error,
                )
                onError("${error.errorCodeName}: ${error.message}")
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            Log.d(TAG, "ExoPlayer release")
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
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                player = exoPlayer
            }
        },
        update = { view -> view.player = exoPlayer },
        modifier = modifier,
    )
}

private const val TAG = "IntroVideoScreen"
