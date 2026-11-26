package com.applemusic.android.playback

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.applemusic.android.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Registro compartido para exponer el `MediaSession` activo y la cola actual a Android Auto (`AppleMusicAutoService`).
 */
internal object PlaybackSessionRegistry {
    @Volatile
    var sessionToken: MediaSession.Token? = null

    @Volatile
    var latestState: PlaybackUiState = PlaybackUiState()

    @Volatile
    var onPlay: (() -> Unit)? = null

    @Volatile
    var onPlayFromMediaId: ((String) -> Unit)? = null
}

/**
 * Puente nativo de Android `MediaSession` + `Notification.MediaStyle`:
 * - Activa controles en pantalla de bloqueo, barra de notificaciones y Android Auto.
 * - Recibe gestos de audífonos Bluetooth/cable (Play/Pausa, Siguiente, Anterior, Seek).
 */
internal class PlaybackMediaSessionBridge(
    private val context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onTogglePlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeekTo: (Long) -> Unit,
    private val onSkipToQueueIndex: (Int) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val mediaSession = MediaSession(context, "AppleMusicAndroidSession")

    private var lastArtworkUrl: String? = null
    private var cachedArtwork: Bitmap? = null
    private var artworkJob: Job? = null
    private var lastState: PlaybackUiState = PlaybackUiState()

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PREV -> onPrevious()
                ACTION_TOGGLE -> onTogglePlayPause()
                ACTION_NEXT -> onNext()
            }
        }
    }

    init {
        ensureNotificationChannel()

        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = this@PlaybackMediaSessionBridge.onPlay()
            override fun onPause() = this@PlaybackMediaSessionBridge.onPause()
            override fun onSkipToNext() = this@PlaybackMediaSessionBridge.onNext()
            override fun onSkipToPrevious() = this@PlaybackMediaSessionBridge.onPrevious()
            override fun onSeekTo(pos: Long) = this@PlaybackMediaSessionBridge.onSeekTo(pos)
            override fun onSkipToQueueItem(id: Long) {
                this@PlaybackMediaSessionBridge.onSkipToQueueIndex(id.toInt())
            }
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                if (mediaId == null) return
                when {
                    mediaId == AppleMusicAutoService.MEDIA_NOW_PLAYING -> this@PlaybackMediaSessionBridge.onPlay()
                    mediaId.startsWith(AppleMusicAutoService.QUEUE_PREFIX) -> {
                        val idx = mediaId.removePrefix(AppleMusicAutoService.QUEUE_PREFIX).toIntOrNull()
                        if (idx != null) this@PlaybackMediaSessionBridge.onSkipToQueueIndex(idx)
                    }
                }
            }

            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                } ?: return super.onMediaButtonEvent(mediaButtonIntent)

                if (event.action != KeyEvent.ACTION_DOWN) return true
                return when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        this@PlaybackMediaSessionBridge.onPlay()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        this@PlaybackMediaSessionBridge.onPause()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        this@PlaybackMediaSessionBridge.onTogglePlayPause()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        this@PlaybackMediaSessionBridge.onNext()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        this@PlaybackMediaSessionBridge.onPrevious()
                        true
                    }
                    else -> super.onMediaButtonEvent(mediaButtonIntent)
                }
            }
        })
        mediaSession.isActive = true
        PlaybackSessionRegistry.sessionToken = mediaSession.sessionToken
        PlaybackSessionRegistry.onPlay = onPlay
        PlaybackSessionRegistry.onPlayFromMediaId = { mediaId ->
            when {
                mediaId == AppleMusicAutoService.MEDIA_NOW_PLAYING -> onPlay()
                mediaId.startsWith(AppleMusicAutoService.QUEUE_PREFIX) -> {
                    val idx = mediaId.removePrefix(AppleMusicAutoService.QUEUE_PREFIX).toIntOrNull()
                    if (idx != null) onSkipToQueueIndex(idx)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PREV)
            addAction(ACTION_TOGGLE)
            addAction(ACTION_NEXT)
        }
        ContextCompat.registerReceiver(context, actionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun update(state: PlaybackUiState) {
        lastState = state
        PlaybackSessionRegistry.latestState = state
        val item = state.currentItem
        if (item == null) {
            notificationManager.cancel(NOTIFICATION_ID)
            mediaSession.isActive = false
            return
        }

        if (!mediaSession.isActive) mediaSession.isActive = true

        // Publicar cola para Android Auto (Up Next / En cola)
        if (state.queueItems.isNotEmpty()) {
            val queue = state.queueItems.take(50).map { track ->
                val desc = MediaDescription.Builder()
                    .setMediaId("${AppleMusicAutoService.QUEUE_PREFIX}${track.index}")
                    .setTitle(track.title)
                    .setSubtitle(track.subtitle)
                    .apply {
                        track.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            setIconUri(Uri.parse(toHighResArtworkUrl(url)))
                        }
                    }
                    .build()
                MediaSession.QueueItem(desc, track.index.toLong())
            }
            mediaSession.setQueue(queue)
            mediaSession.setQueueTitle(state.contextTitle ?: "En cola")
        }

        val pbState = when {
            state.isBuffering -> PlaybackState.STATE_BUFFERING
            state.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO

        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(pbState, state.positionMs.coerceAtLeast(0L), if (state.isPlaying) 1f else 0f)
                .build()
        )

        val artUrl = item.artworkUrl?.takeIf { it.isNotBlank() }
        if (artUrl != lastArtworkUrl) {
            lastArtworkUrl = artUrl
            cachedArtwork = null
            artworkJob?.cancel()
            if (artUrl != null) {
                artworkJob = scope.launch {
                    val bmp = loadArtworkBitmap(artUrl)
                    if (lastArtworkUrl == artUrl && bmp != null) {
                        cachedArtwork = bmp
                        publishMetadataAndNotification(lastState)
                    }
                }
            }
        }

        publishMetadataAndNotification(state)
    }

    private fun publishMetadataAndNotification(state: PlaybackUiState) {
        val item = state.currentItem ?: return
        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, item.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, item.subtitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs.coerceAtLeast(0L))
        state.contextTitle?.takeIf { it.isNotBlank() && !state.isAutoplayItem }?.let { albumOrPlaylist ->
            metaBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, albumOrPlaylist)
        }
        cachedArtwork?.let {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, it)
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
        }
        mediaSession.setMetadata(metaBuilder.build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Anterior",
            broadcastPendingIntent(ACTION_PREV, 101)
        ).build()

        val toggleAction = Notification.Action.Builder(
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (state.isPlaying) "Pausar" else "Reproducir",
            broadcastPendingIntent(ACTION_TOGGLE, 102)
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Siguiente",
            broadcastPendingIntent(ACTION_NEXT, 103)
        ).build()

        val style = Notification.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(item.title)
            .setContentText(item.subtitle)
            .setContentIntent(contentIntent)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(style)
            .addAction(prevAction)
            .addAction(toggleAction)
            .addAction(nextAction)

        cachedArtwork?.let { builder.setLargeIcon(it) }

        runCatching {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun broadcastPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureNotificationChannel() {
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción en barra de notificaciones y pantalla de bloqueo"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun toHighResArtworkUrl(url: String): String {
        val expanded = url
            .replace("{w}", "1200")
            .replace("{h}", "1200")
            .replace("{c}", "bb")
            .replace("{f}", "jpg")
        return expanded.replace(Regex("/\\d+x\\d+([a-zA-Z]*)\\."), "/1200x1200$1.")
    }

    private suspend fun loadArtworkBitmap(url: String): Bitmap? = runCatching {
        val hiResUrl = toHighResArtworkUrl(url)
        val req = ImageRequest.Builder(context)
            .data(hiResUrl)
            .size(1024)
            .allowHardware(false)
            .build()
        val drawable = (context.imageLoader.execute(req) as? SuccessResult)?.drawable ?: return null
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return@runCatching drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceIn(512, 1024) ?: 1024
        val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceIn(512, 1024) ?: 1024
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        bmp
    }.getOrNull()

    fun release() {
        runCatching { context.unregisterReceiver(actionReceiver) }
        notificationManager.cancel(NOTIFICATION_ID)
        mediaSession.isActive = false
        mediaSession.release()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "apple_music_playback"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_PREV = "com.applemusic.android.action.PREV"
        private const val ACTION_TOGGLE = "com.applemusic.android.action.TOGGLE"
        private const val ACTION_NEXT = "com.applemusic.android.action.NEXT"
    }
}
