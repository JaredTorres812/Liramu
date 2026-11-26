package com.applemusic.android.ui.home

import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.io.File

/**
 * Caché de disco compartida de Media3 para loops HLS (`editorialVideo`) de Apple Music.
 * Evita re-buffering en cada repetición y mantiene el scroll y la UI fluidos.
 */
@OptIn(UnstableApi::class)
private object MotionArtworkCache {
    @Volatile
    private var cache: SimpleCache? = null

    fun dataSourceFactory(context: Context): CacheDataSource.Factory {
        val app = context.applicationContext
        val simpleCache = cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(app.cacheDir, "am_motion_video_cache"),
                LeastRecentlyUsedCacheEvictor(28L * 1024L * 1024L),
                StandaloneDatabaseProvider(app)
            ).also { cache = it }
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(10_000)

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

/**
 * Reproduce portadas animadas HLS (`editorialVideo` `.m3u8`) de Apple Music en bucle silencioso:
 * - Usa `TextureView` para respetar clips redondeados y superposiciones de Compose sin z-glitches.
 * - Desactiva la pista de audio y el audio-focus para jamás interrumpir la música ni Bluetooth.
 * - Limita la resolución/bitrate y solo aparece con crossfade al renderizarse el primer frame real.
 */
@OptIn(UnstableApi::class)
@Composable
fun MotionArtworkVideo(
    videoUrl: String?,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    /** Informa la proporción real del video (ancho / alto) para poder mostrarlo completo sin recortes. */
    onVideoAspect: ((Float) -> Unit)? = null
) {
    val cleanUrl = videoUrl?.trim()?.takeIf { it.isNotBlank() } ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var firstFrameReady by remember(cleanUrl) { mutableStateOf(false) }
    var failed by remember(cleanUrl) { mutableStateOf(false) }
    var videoSize by remember(cleanUrl) { mutableStateOf(VideoSize.UNKNOWN) }

    if (failed) return

    val textureView = remember(cleanUrl) {
        TextureView(context).apply {
            isOpaque = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val player = remember(cleanUrl) {
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setMaxVideoSize(1920, 1920)
                .setMaxVideoBitrate(5_500_000)
                .build()
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 6000, 400, 800)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ false)
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playWhenReady = isActive
                val dsFactory = MotionArtworkCache.dataSourceFactory(context)
                val mediaSource = HlsMediaSource.Factory(dsFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(cleanUrl))
                setMediaSource(mediaSource)
                prepare()
            }
    }

    fun applyCenterCrop(view: TextureView, vs: VideoSize) {
        if (vs.width <= 0 || vs.height <= 0 || view.width <= 0 || view.height <= 0) return
        val viewW = view.width.toFloat()
        val viewH = view.height.toFloat()
        val videoAspect = (vs.width * vs.pixelWidthHeightRatio) / vs.height.toFloat()
        val viewAspect = viewW / viewH
        var scaleX = 1f
        var scaleY = 1f
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
        } else {
            scaleY = viewAspect / videoAspect
        }
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        }
        view.setTransform(matrix)
    }

    LaunchedEffect(isActive, player) {
        player.playWhenReady = isActive
    }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameReady = true
            }

            override fun onVideoSizeChanged(vs: VideoSize) {
                videoSize = vs
                applyCenterCrop(textureView, vs)
                if (vs.width > 0 && vs.height > 0) {
                    onVideoAspect?.invoke((vs.width * vs.pixelWidthHeightRatio) / vs.height.toFloat())
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        player.setVideoTextureView(textureView)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = isActive
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.clearVideoTextureView(textureView)
            player.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameReady) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "motion_artwork_alpha"
    )

    val container = remember(cleanUrl) {
        FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            clipToOutline = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(textureView)
        }
    }

    AndroidView(
        factory = { container },
        update = {
            if (videoSize.width > 0 && videoSize.height > 0) {
                applyCenterCrop(textureView, videoSize)
            }
        },
        modifier = modifier
            .clipToBounds()
            .alpha(videoAlpha)
    )
}
