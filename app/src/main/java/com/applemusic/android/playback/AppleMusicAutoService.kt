package com.applemusic.android.playback

import android.content.Intent
import android.media.MediaDescription
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.net.Uri
import android.os.Bundle
import android.service.media.MediaBrowserService
import com.applemusic.android.MainActivity

/**
 * Servicio oficial `MediaBrowserService` para Android Auto.
 * Conecta el tablero del automóvil directamente al `MediaSession` activo de la app
 * para mostrar portada HD, controles en el volante y navegación por la cola de reproducción.
 */
class AppleMusicAutoService : MediaBrowserService() {

    private var fallbackSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val activeToken = PlaybackSessionRegistry.sessionToken
        if (activeToken != null) {
            sessionToken = activeToken
        } else {
            val session = MediaSession(this, "AppleMusicAutoFallback").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        val callback = PlaybackSessionRegistry.onPlay
                        if (callback != null) {
                            callback()
                        } else {
                            val launch = Intent(this@AppleMusicAutoService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { startActivity(launch) }
                        }
                    }

                    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                        if (!mediaId.isNullOrBlank()) {
                            PlaybackSessionRegistry.onPlayFromMediaId?.invoke(mediaId)
                        }
                    }
                })
                isActive = true
            }
            fallbackSession = session
            sessionToken = session.sessionToken
        }
    }

    override fun onDestroy() {
        fallbackSession?.let {
            it.isActive = false
            it.release()
        }
        fallbackSession = null
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        // Si la sesión principal ya se creó después del arranque del servicio, sincronizar el token.
        PlaybackSessionRegistry.sessionToken?.let { activeToken ->
            if (sessionToken != activeToken) {
                sessionToken = activeToken
            }
        }
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        val state = PlaybackSessionRegistry.latestState
        val items = mutableListOf<MediaBrowser.MediaItem>()

        when (parentId) {
            ROOT_ID -> {
                state.currentItem?.let { current ->
                    items += MediaBrowser.MediaItem(
                        MediaDescription.Builder()
                            .setMediaId(MEDIA_NOW_PLAYING)
                            .setTitle(current.title)
                            .setSubtitle(current.subtitle.ifBlank { "Reproduciendo ahora" })
                            .apply {
                                current.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                    setIconUri(Uri.parse(toAutoArtworkUrl(url)))
                                }
                            }
                            .build(),
                        MediaBrowser.MediaItem.FLAG_PLAYABLE
                    )
                }

                if (state.queueItems.isNotEmpty()) {
                    items += MediaBrowser.MediaItem(
                        MediaDescription.Builder()
                            .setMediaId(QUEUE_ID)
                            .setTitle("En cola (${state.queueItems.size})")
                            .setSubtitle(state.contextTitle ?: "Siguientes canciones")
                            .build(),
                        MediaBrowser.MediaItem.FLAG_BROWSABLE
                    )
                }
            }

            QUEUE_ID -> {
                state.queueItems.take(50).forEach { track ->
                    items += MediaBrowser.MediaItem(
                        MediaDescription.Builder()
                            .setMediaId("$QUEUE_PREFIX${track.index}")
                            .setTitle(track.title)
                            .setSubtitle(track.subtitle)
                            .apply {
                                track.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                    setIconUri(Uri.parse(toAutoArtworkUrl(url)))
                                }
                            }
                            .build(),
                        MediaBrowser.MediaItem.FLAG_PLAYABLE
                    )
                }
            }
        }

        result.sendResult(items)
    }

    private fun toAutoArtworkUrl(url: String): String =
        url.replace("{w}", "600")
            .replace("{h}", "600")
            .replace("{c}", "bb")
            .replace("{f}", "jpg")

    companion object {
        const val ROOT_ID = "am_auto_root"
        const val QUEUE_ID = "am_auto_queue"
        const val MEDIA_NOW_PLAYING = "am_auto_now_playing"
        const val QUEUE_PREFIX = "queue:"
    }
}
