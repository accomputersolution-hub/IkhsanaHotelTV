package `in`.pcncloud.hotel.ui.intro

import android.os.Build
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * Fullscreen intro playback. URL from [IntroVideoCache].
 *
 * API &lt; 30: longer buffers, OkHttp COMPATIBLE_TLS, retries, delayed Home fallback
 * so Android 9 TV boxes are not skipped during TLS / decoder warmup.
 */
@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun IntroVideoScreen(
    videoUrl: String,
    onFinished: (reason: String) -> Unit,
    viewModel: IntroVideoViewModel = viewModel(),
) {
    val finished = remember { AtomicBoolean(false) }
    val skipFocus = remember { FocusRequester() }
    val mediaUri = remember(videoUrl) { IntroVideoCache.parseMediaUri(videoUrl) }
    val playerGeneration by viewModel.playerGeneration.collectAsState()
    var playbackEverStarted by remember(videoUrl) { mutableStateOf(false) }
    var mountGeneration by remember { mutableIntStateOf(0) }

    fun finishOnce(reason: String) {
        if (!finished.compareAndSet(false, true)) return
        Log.i(TAG, "IntroVideoScreen finish ($reason) sdk=${Build.VERSION.SDK_INT}")
        onFinished(reason)
    }

    fun handlePlayerFailure(message: String?) {
        val detail = message?.trim().orEmpty().ifBlank { "unknown" }
        Log.e(TAG, "player failure → $detail")
        if (finished.get()) return
        if (playbackEverStarted) {
            finishOnce("error_after_start")
            return
        }
        if (viewModel.tryRetryAfterError(detail)) {
            return
        }
        finishOnce("error")
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

    LaunchedEffect(videoUrl, viewModel.playbackWatchdogMs) {
        delay(viewModel.playbackWatchdogMs)
        if (!playbackEverStarted) {
            Log.e(TAG, "watchdog — never started after ${viewModel.playbackWatchdogMs}ms")
        }
        finishOnce("playback_watchdog")
    }

    LaunchedEffect(playerGeneration) {
        if (playerGeneration == 0) {
            mountGeneration = 0
            return@LaunchedEffect
        }
        // Tear down broken player immediately, wait, then rebuild.
        mountGeneration = -1
        Log.i(TAG, "grace ${viewModel.errorGraceMs}ms before retry gen=$playerGeneration")
        delay(viewModel.errorGraceMs)
        if (!finished.get()) {
            mountGeneration = playerGeneration
        }
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
        if (mountGeneration >= 0) {
            key(mountGeneration) {
                IntroExoPlayer(
                    videoUrl = videoUrl,
                    policy = viewModel,
                    onPlaybackStarted = {
                        playbackEverStarted = true
                        Log.i(TAG, "ExoPlayer playing (gen=$mountGeneration)")
                    },
                    onEnded = { finishOnce("ended") },
                    onError = { handlePlayerFailure(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
    policy: IntroVideoViewModel,
    onPlaybackStarted: () -> Unit,
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
        Log.i(
            TAG,
            "ExoPlayer create sdk=${Build.VERSION.SDK_INT} legacy=${policy.isLegacyApi} " +
                "scheme=${uri.scheme} host=${uri.host} " +
                "connectMs=${policy.connectTimeoutMs} readMs=${policy.readTimeoutMs}",
        )

        val okHttp = buildIntroOkHttpClient(policy)
        val httpFactory = OkHttpDataSource.Factory(okHttp)
            .setUserAgent(INTRO_HTTP_USER_AGENT)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                policy.minBufferMs,
                policy.maxBufferMs,
                policy.bufferForPlaybackMs,
                policy.bufferForPlaybackAfterRebufferMs,
            )
            .build()

        val mimeType = when {
            videoUrl.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
            videoUrl.contains(".webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
            else -> null
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1f
                val item = MediaItem.Builder()
                    .setUri(uri)
                    .apply {
                        if (mimeType != null) setMimeType(mimeType)
                    }
                    .build()
                setMediaItem(item)
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
                        "positionMs=$position everStarted=$playbackEverStarted " +
                        "sdk=${Build.VERSION.SDK_INT}",
                )

                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "buffering — keep waiting (no Home fallback)")
                    }
                    Player.STATE_READY -> {
                        if (duration == 0L) {
                            Log.w(
                                TAG,
                                "READY with duration=0 — wait readyGrace=${policy.readyGraceMs}ms",
                            )
                        } else {
                            if (exoPlayer.playWhenReady) {
                                playbackEverStarted = true
                                onPlaybackStarted()
                            }
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
                                    "positionMs=$position sdk=${Build.VERSION.SDK_INT}). " +
                                    "url=$videoUrl",
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
                Log.i(TAG, "onIsPlayingChanged isPlaying=$isPlaying")
                if (isPlaying) {
                    playbackEverStarted = true
                    onPlaybackStarted()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val causeChain = buildString {
                    var c: Throwable? = error
                    var depth = 0
                    while (c != null && depth < 8) {
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
                    "onPlayerError sdk=${Build.VERSION.SDK_INT} " +
                        "errorCode=${error.errorCode} name=${error.errorCodeName} " +
                        "message=${error.message} timestampMs=${error.timestampMs} " +
                        "url=$videoUrl causeChain=[$causeChain]",
                    error,
                )
                var nested: Throwable? = error.cause
                var i = 1
                while (nested != null && i <= 6) {
                    Log.e(
                        TAG,
                        "onPlayerError cause[$i]=${nested.javaClass.name}: ${nested.message}",
                        nested,
                    )
                    nested = nested.cause
                    i++
                }
                onError(
                    "${error.errorCodeName} (${error.errorCode}): ${error.message} | $causeChain",
                )
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Only fail on READY+duration=0 after grace — never while still BUFFERING.
    LaunchedEffect(exoPlayer, policy.readyGraceMs) {
        delay(policy.readyGraceMs)
        if (playbackEverStarted) return@LaunchedEffect
        val duration = exoPlayer.duration
        val state = exoPlayer.playbackState
        if (state == Player.STATE_READY && duration == 0L) {
            Log.e(TAG, "readyGrace: READY with duration=0 — treating as bad source")
            onError(
                "No playable duration after ${policy.readyGraceMs}ms " +
                    "(READY duration=0). url=$videoUrl",
            )
        } else {
            Log.d(
                TAG,
                "readyGrace elapsed state=${stateName(state)} durationMs=$duration " +
                    "everStarted=$playbackEverStarted — continue waiting",
            )
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
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view -> view.player = exoPlayer },
        modifier = modifier,
    )
}

@UnstableApi
private fun buildIntroOkHttpClient(policy: IntroVideoViewModel): OkHttpClient {
    val protocols = if (policy.isLegacyApi) {
        listOf(Protocol.HTTP_1_1)
    } else {
        listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
    }
    return OkHttpClient.Builder()
        .connectTimeout(policy.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(policy.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(policy.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .callTimeout(policy.callTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .protocols(protocols)
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
            ),
        )
        .build()
}

private fun stateName(playbackState: Int): String = when (playbackState) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "OTHER($playbackState)"
}

private const val TAG = "IntroVideoScreen"

private const val INTRO_HTTP_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 HostityIntro/1.1"
