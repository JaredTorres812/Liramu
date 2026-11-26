package com.applemusic.android.playback

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.apple.android.music.playback.model.MediaContainerType
import com.apple.android.music.playback.model.MediaItemType
import com.apple.android.music.playback.model.MediaPlayerException
import com.apple.android.music.playback.model.PlaybackRepeatMode
import com.apple.android.music.playback.model.PlaybackShuffleMode
import com.apple.android.music.playback.model.PlaybackState
import com.apple.android.music.playback.model.PlayerQueueItem
import com.apple.android.music.playback.queue.CatalogPlaybackQueueItemProvider
import com.apple.android.music.playback.queue.LibraryPlaybackQueueItemProvider
import com.apple.android.music.playback.queue.PlaybackModeContainer
import com.apple.android.music.playback.queue.PlaybackQueueInsertionType
import com.apple.android.music.playback.queue.PlaybackQueueItemsAdded
import com.apple.android.music.playback.queue.StationPlaybackQueueItemProvider
import com.apple.android.sdk.authentication.TokenProvider
import com.applemusic.android.auth.TokenManager
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.QueueTrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackUiState(
    val currentItem: HomeItem? = null,
    val contextTitle: String? = null,
    val isAutoplayItem: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val positionUpdatedAtElapsedMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val autoplayEnabled: Boolean = true,
    val queueItems: List<QueueTrackItem> = emptyList(),
    val queuePosition: Int = -1,
    val errorMessage: String? = null,
    val usingWebPlayback: Boolean = false,
    val isVideo: Boolean = false,
    val isVideoFrameReady: Boolean = false,
    val videoPlaybackNonce: Long = 0L,
    val audioVariantBadge: String? = null,
    val dolbyCapabilities: DeviceDolbyCapabilities = DeviceDolbyCapabilities(),
    val atmosMode: AtmosPreferenceMode = AtmosPreferenceMode.AUTOMATIC,
    val crossfadeSeconds: Int = 0,
    val autoMixEnabled: Boolean = false,
    val isTransitioning: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerEndOfTrack: Boolean = false
)

/**
 * Motor de reproducción real de Apple Music:
 * 1. Usa el SDK nativo oficial (`MediaPlayback-release-1.1.4.aar` -> `MediaPlayerController`).
 * 2. Si solo hay sesión web (`AMPWebPlay` + `media-user-token`) y el SDK nativo rechaza el token web,
 *    conmuta automáticamente a `MusicKit JS` con Widevine DRM sobre `music.apple.com` para reproducir
 *    canciones completas sin pedir otro inicio de sesión.
 */
class AppleMusicPlayerManager(
    context: Context,
    private val tokenManager: TokenManager
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioPrefs = appContext.getSharedPreferences("am_audio_quality", Context.MODE_PRIVATE)
    @Volatile
    private var nativeRejectedWebToken = false
    /** El SDK nativo ya reprodujo audio con las credenciales actuales: sus errores posteriores son del contenido, no del token. */
    private var nativeWorkedWithCurrentToken = false

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var nativeController: MediaPlayerController? = null
    private var webPlayerView: WebView? = null
    private var webPlayerReady = false
    private var pendingWebQueueJs: String? = null
    private var pendingRequest: PlayRequest? = null
    /** Solicitud para la que ya se reintentó el motor nativo tras un error (evita bucles de reintento). */
    private var nativeRetriedRequest: PlayRequest? = null
    private var usingWebEngine = false
    private var fallbackVideoHost: ViewGroup? = null
    private var activeVideoHost: ViewGroup? = null
    private var videoNonceCounter = 0L
    @Volatile
    private var userIntentPlaying = false
    @Volatile
    private var explicitContainerTitle: String? = null
    @Volatile
    private var explicitContainerIsPlaylist: Boolean = false
    @Volatile
    private var explicitContainerIsAlbum: Boolean = false
    @Volatile
    private var switchEpoch: Long = 0L
    @Volatile
    private var prevTrackIdBeforeSwitch: String? = null
    @Volatile
    private var switchStartedAtMs: Long = 0L
    @Volatile
    private var lastPlayPauseToggleAtMs: Long = 0L
    @Volatile
    private var pendingSeekTargetMs: Long = -1L
    @Volatile
    private var pendingSeekAtMs: Long = 0L
    @Volatile
    private var sleepTimerTargetElapsedMs: Long = 0L
    @Volatile
    private var sleepTimerEndOfTrackTrackId: String? = null
    @Volatile
    private var restoredPendingPlay: Boolean = false
    @Volatile
    private var restoredSeekPositionMs: Long = 0L
    @Volatile
    private var lastPlaybackSavedAtMs: Long = 0L
    private val lastPlaybackPrefs = appContext.getSharedPreferences("am_last_playback", Context.MODE_PRIVATE)

    private val mediaSessionBridge: PlaybackMediaSessionBridge

    private fun filterStaleSeekPosition(incomingMs: Long, fallbackMs: Long, nowMs: Long): Long {
        val target = pendingSeekTargetMs
        if (target < 0L) return incomingMs.coerceAtLeast(0L)
        val elapsed = nowMs - pendingSeekAtMs
        if (elapsed > 2_000L) {
            pendingSeekTargetMs = -1L
            return incomingMs.coerceAtLeast(0L)
        }
        return if (kotlin.math.abs(incomingMs - target) <= 2_500L) {
            pendingSeekTargetMs = -1L
            incomingMs.coerceAtLeast(0L)
        } else {
            fallbackMs
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val nowMs = SystemClock.elapsedRealtime()
            if (!usingWebEngine) {
                nativeController?.let { ctrl ->
                    if (ctrl.playbackState == PlaybackState.PLAYING) {
                        val rawPos = ctrl.currentPosition.coerceAtLeast(0L)
                        _state.update {
                            val cleanPos = filterStaleSeekPosition(rawPos, it.positionMs, nowMs)
                            val posChanged = cleanPos != it.positionMs
                            it.copy(
                                positionMs = cleanPos,
                                positionUpdatedAtElapsedMs = if (posChanged || !it.isPlaying || it.positionUpdatedAtElapsedMs == 0L) nowMs else it.positionUpdatedAtElapsedMs,
                                durationMs = ctrl.duration.coerceAtLeast(it.durationMs)
                            )
                        }
                    }
                }
            }

            // Crossfade / AutoMix automático (respeta continuidad gapless en álbumes en orden para no cortar pistas enlazadas)
            val fadeWindowMs = effectiveTransitionMs()
            val st = _state.value
            val cur = st.currentItem
            val isStation = cur?.type?.contains("station", ignoreCase = true) == true ||
                cur?.id?.startsWith("ra.") == true
            val isContinuousAlbum = explicitContainerIsAlbum && !st.shuffleEnabled
            if (fadeWindowMs > 0L && !isContinuousAlbum && st.isPlaying && !st.isVideo && !isStation && st.durationMs > 15_000L) {
                val elapsedSinceUpdate = (nowMs - st.positionUpdatedAtElapsedMs).coerceIn(0L, 1_500L)
                val estPos = st.positionMs + elapsedSinceUpdate
                val remaining = st.durationMs - estPos
                val trackKey = "${st.switchKeyToken()}_${cur?.id.orEmpty()}"
                val hasNext = st.queuePosition in 0 until st.queueItems.lastIndex || st.autoplayEnabled
                if (hasNext && remaining in 350L..fadeWindowMs && lastAutoCrossfadeTrackKey != trackKey) {
                    lastAutoCrossfadeTrackKey = trackKey
                    _state.update { it.copy(isTransitioning = true) }
                    if (usingWebEngine) {
                        val fadeMs = remaining.coerceIn(700L, 4_200L)
                        evalWebJs(
                            """
                            (async function() {
                              try {
                                var mk = MusicKit.getInstance();
                                if (!mk || !mk.isPlaying) return;
                                if (window.__amFadeOut) await window.__amFadeOut(mk, $fadeMs);
                                await mk.skipToNextItem();
                                if (window.__amFadeIn) window.__amFadeIn(mk, ${minOf(fadeMs, 1_800L)});
                              } catch (e) {}
                            })();
                            """.trimIndent()
                        )
                    } else if (remaining <= 1_400L) {
                        nativeController?.let { ctrl ->
                            if (ctrl.canSkipToNextItem()) ctrl.skipToNextItem()
                        }
                    }
                    mainHandler.postDelayed({ _state.update { it.copy(isTransitioning = false) } }, 2_200L)
                }
            }
            // Temporizador de apagado (Sleep Timer)
            val sleepEndAt = sleepTimerTargetElapsedMs
            if (sleepEndAt > 0L) {
                val rem = (sleepEndAt - nowMs).coerceAtLeast(0L)
                if (rem <= 0L) {
                    sleepTimerTargetElapsedMs = 0L
                    _state.update { it.copy(sleepTimerRemainingMs = 0L, sleepTimerEndOfTrack = false) }
                    if (_state.value.isPlaying) togglePlayPause()
                } else {
                    _state.update { it.copy(sleepTimerRemainingMs = rem) }
                }
            }
            val endTrackId = sleepTimerEndOfTrackTrackId
            val activeTrackId = _state.value.currentItem?.id
            if (endTrackId != null && !activeTrackId.isNullOrBlank() && activeTrackId != endTrackId) {
                sleepTimerEndOfTrackTrackId = null
                _state.update { it.copy(sleepTimerEndOfTrack = false) }
                if (_state.value.isPlaying) togglePlayPause()
            }

            // Persistir continuidad de la canción cada 3 s para restaurarla al volver a abrir la app
            if ((nowMs - lastPlaybackSavedAtMs) > 3_000L && _state.value.currentItem != null) {
                lastPlaybackSavedAtMs = nowMs
                saveLastPlaybackState(_state.value)
            }

            mediaSessionBridge.update(_state.value)
            mainHandler.postDelayed(this, 400L)
        }
    }

    private fun PlaybackUiState.switchKeyToken(): String = "${queuePosition}_${durationMs}"

    /** Rellena título/portada de show de radio en vivo cuando el stream aún no emite tags ID3 de canción. */
    fun applyLiveStationFallbackMetadata(stationId: String, showTitle: String, artworkUrl: String?) {
        _state.update { st ->
            val cur = st.currentItem ?: return@update st
            val matches = cur.id == stationId || cur.id.removePrefix("ra.") == stationId.removePrefix("ra.")
            if (!matches) return@update st
            // No sobrescribir si el stream ID3 ya entregó una canción real (con artista distinto al título de la estación).
            if (cur.subtitle.isNotBlank() && cur.title != explicitContainerTitle) return@update st
            st.copy(
                currentItem = cur.copy(
                    subtitle = showTitle,
                    artworkUrl = artworkUrl?.takeIf { it.isNotBlank() } ?: cur.artworkUrl
                )
            )
        }
    }

    init {
        val savedAtmos = audioPrefs.getString(KEY_ATMOS_MODE, AtmosPreferenceMode.AUTOMATIC.name)
            ?.let { name -> runCatching { AtmosPreferenceMode.valueOf(name) }.getOrNull() }
            ?: AtmosPreferenceMode.AUTOMATIC
        val caps = AudioSpatialCapabilities.inspectDeviceCapabilities(appContext)
        AudioSpatialCapabilities.applyNativePreferences(appContext, savedAtmos, caps)
        _state.update {
            it.copy(
                dolbyCapabilities = caps,
                atmosMode = savedAtmos
            )
        }
        restoreLastPlaybackState()
        mediaSessionBridge = PlaybackMediaSessionBridge(
            context = appContext,
            onPlay = { if (!_state.value.isPlaying) togglePlayPause() },
            onPause = { if (_state.value.isPlaying) togglePlayPause() },
            onTogglePlayPause = { togglePlayPause() },
            onNext = { skipNext() },
            onPrevious = { skipPrevious() },
            onSeekTo = { pos -> seekTo(pos) },
            onSkipToQueueIndex = { idx -> skipToQueueIndex(idx) }
        )
        mainHandler.postDelayed(progressTicker, 500L)
        // El motor nativo (Atmos) se calienta primero; el WebView de respaldo se precalienta pronto para Radio y Videos.
        mainHandler.postDelayed({ prewarmNativeController() }, 250L)
        mainHandler.postDelayed({ prewarmWebPlayer() }, if (shouldUseNativeEngine()) 1_400L else 600L)
    }

    @Volatile
    private var crossfadeDurationMs: Long = 0L
    @Volatile
    private var autoMixActive: Boolean = false
    @Volatile
    private var lastAutoCrossfadeTrackKey: String? = null

    fun setAudioTransitionConfig(crossfadeSeconds: Int, autoMixEnabled: Boolean) {
        val safeSec = crossfadeSeconds.coerceIn(0, 12)
        crossfadeDurationMs = safeSec * 1000L
        autoMixActive = autoMixEnabled
        val fadeMs = effectiveTransitionMs()
        _state.update {
            it.copy(
                crossfadeSeconds = safeSec,
                autoMixEnabled = autoMixEnabled,
                isTransitioning = false
            )
        }
        evalWebJs("try{window.__amCrossfadeMs=$fadeMs;window.__amAutoMix=$autoMixEnabled;}catch(e){}")
    }

    /** Configura el Sleep Timer: 0 = apagar, >0 = minutos, -1 = al terminar la canción actual. */
    fun setSleepTimerMinutes(minutes: Int) {
        when {
            minutes == 0 -> {
                sleepTimerTargetElapsedMs = 0L
                sleepTimerEndOfTrackTrackId = null
                _state.update { it.copy(sleepTimerRemainingMs = 0L, sleepTimerEndOfTrack = false) }
            }
            minutes < 0 -> {
                sleepTimerTargetElapsedMs = 0L
                sleepTimerEndOfTrackTrackId = _state.value.currentItem?.id?.takeIf { it.isNotBlank() }
                _state.update {
                    it.copy(
                        sleepTimerRemainingMs = 0L,
                        sleepTimerEndOfTrack = sleepTimerEndOfTrackTrackId != null
                    )
                }
            }
            else -> {
                val durMs = minutes * 60_000L
                sleepTimerEndOfTrackTrackId = null
                sleepTimerTargetElapsedMs = SystemClock.elapsedRealtime() + durMs
                _state.update { it.copy(sleepTimerRemainingMs = durMs, sleepTimerEndOfTrack = false) }
            }
        }
    }

    private fun saveLastPlaybackState(st: PlaybackUiState) {
        val item = st.currentItem ?: return
        if (item.id.isBlank() || item.type.contains("station", ignoreCase = true) || item.id.startsWith("ra.")) return
        lastPlaybackPrefs.edit()
            .putString("id", item.id)
            .putString("type", item.type)
            .putString("title", item.title)
            .putString("subtitle", item.subtitle)
            .putString("artworkUrl", item.artworkUrl.orEmpty())
            .putString("catalogId", item.catalogId.orEmpty())
            .putString("contextTitle", st.contextTitle.orEmpty())
            .putLong("positionMs", st.positionMs.coerceAtLeast(0L))
            .putLong("durationMs", st.durationMs.coerceAtLeast(0L))
            .apply()
    }

    private fun restoreLastPlaybackState() {
        val id = lastPlaybackPrefs.getString("id", null)?.takeIf { it.isNotBlank() } ?: return
        val title = lastPlaybackPrefs.getString("title", null)?.takeIf { it.isNotBlank() } ?: return
        val type = lastPlaybackPrefs.getString("type", "songs").orEmpty().ifBlank { "songs" }
        val subtitle = lastPlaybackPrefs.getString("subtitle", "").orEmpty()
        val artworkUrl = lastPlaybackPrefs.getString("artworkUrl", null)?.takeIf { it.isNotBlank() }
        val catalogId = lastPlaybackPrefs.getString("catalogId", null)?.takeIf { it.isNotBlank() }
        val contextTitle = lastPlaybackPrefs.getString("contextTitle", null)?.takeIf { it.isNotBlank() }
        val posMs = lastPlaybackPrefs.getLong("positionMs", 0L).coerceAtLeast(0L)
        val durMs = lastPlaybackPrefs.getLong("durationMs", 0L).coerceAtLeast(0L)
        restoredPendingPlay = true
        restoredSeekPositionMs = posMs
        _state.update {
            it.copy(
                currentItem = HomeItem(
                    id = id,
                    type = type,
                    title = title,
                    subtitle = subtitle,
                    artworkUrl = artworkUrl,
                    catalogId = catalogId
                ),
                contextTitle = contextTitle,
                positionMs = posMs,
                durationMs = durMs,
                isPlaying = false,
                isBuffering = false
            )
        }
    }

    /** Cicla entre Continuidad (gapless) → Crossfade 5s → AutoMix desde el reproductor. */
    fun cycleAudioTransitionMode() {
        val st = _state.value
        when {
            !st.autoMixEnabled && st.crossfadeSeconds == 0 -> setAudioTransitionConfig(crossfadeSeconds = 5, autoMixEnabled = false)
            st.crossfadeSeconds > 0 && !st.autoMixEnabled -> setAudioTransitionConfig(crossfadeSeconds = 0, autoMixEnabled = true)
            else -> setAudioTransitionConfig(crossfadeSeconds = 0, autoMixEnabled = false)
        }
    }

    private fun effectiveTransitionMs(): Long = when {
        crossfadeDurationMs > 0L -> crossfadeDurationMs
        autoMixActive -> 5_500L
        else -> 0L
    }

    private fun shouldUseNativeEngine(): Boolean {
        val creds = tokenManager.getCredentials()
        val atmosWanted = _state.value.atmosMode != AtmosPreferenceMode.OFF
        val hasNativeCreds = creds.hasDeveloperToken && creds.hasUserToken
        val hasWebCreds = creds.hasWebSession && creds.webDeveloperToken.isNotBlank() && !nativeRejectedWebToken
        return hasNativeCreds || (atmosWanted && hasWebCreds)
    }

    /** Carga las librerías nativas en segundo plano y crea el controlador antes de la primera canción. */
    fun prewarmNativeController() {
        if (nativeController != null || !shouldUseNativeEngine()) return
        Thread {
            runCatching {
                System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0")
                System.setProperty("org.bytedeco.javacpp.maxbytes", "0")
                System.loadLibrary("c++_shared")
                System.loadLibrary("appleMusicSDK")
            }
            mainHandler.post { if (nativeController == null) ensureNativeController() }
        }.apply { isDaemon = true }.start()
    }

    fun setAtmosMode(mode: AtmosPreferenceMode) {
        audioPrefs.edit().putString(KEY_ATMOS_MODE, mode.name).apply()
        val caps = AudioSpatialCapabilities.inspectDeviceCapabilities(appContext)
        AudioSpatialCapabilities.applyNativePreferences(appContext, mode, caps)
        // Al reactivar Atmos se vuelve a intentar el motor nativo (el web nunca entrega Atmos).
        if (mode != AtmosPreferenceMode.OFF) nativeRejectedWebToken = false
        _state.update { it.copy(atmosMode = mode, dolbyCapabilities = caps) }
    }

    fun prewarmWebPlayer() {
        prewarmNativeController()
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession && !creds.hasUserToken) return
        mainHandler.post {
            ensureWebPlayer()
        }
    }

    private data class PlayRequest(
        val item: HomeItem,
        val container: HomeItem?,
        val startIndex: Int,
        val shuffle: Boolean,
        val preloadedTracks: List<HomeItem> = emptyList()
    )

    private fun ensureNativeController(): MediaPlayerController? {
        if (nativeController != null) return nativeController
        return runCatching {
            System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0")
            System.setProperty("org.bytedeco.javacpp.maxbytes", "0")
            System.loadLibrary("c++_shared")
            System.loadLibrary("appleMusicSDK")

            val provider = object : TokenProvider {
                override fun getDeveloperToken(): String {
                    val creds = tokenManager.getCredentials()
                    return creds.developerToken.ifBlank { creds.webDeveloperToken }
                }

                override fun getUserToken(): String? {
                    val creds = tokenManager.getCredentials()
                    return creds.musicUserToken.ifBlank { creds.webUserToken }.takeIf { it.isNotBlank() }
                }

                override fun getAppleMusicStoreFrontId(): String? =
                    tokenManager.getCredentials().appleMusicStorefrontId.ifBlank {
                        tokenManager.getCredentials().storefront
                    }.takeIf { it.isNotBlank() }
            }

            MediaPlayerControllerFactory.createLocalController(appContext, mainHandler, provider).apply {
                addListener(nativeListener)
                nativeController = this
            }
        }.onFailure {
            Log.w(TAG, "No se pudo inicializar MediaPlayerController nativo, usando MusicKit Web", it)
        }.getOrNull()
    }

    private fun isVideoItem(item: HomeItem?): Boolean {
        val t = item?.type?.lowercase().orEmpty()
        return t.contains("video") || t.contains("music-movie")
    }

    fun attachVideoHost(host: ViewGroup, isFallback: Boolean = false) {
        mainHandler.post {
            if (isFallback) {
                fallbackVideoHost = host
            } else {
                activeVideoHost = host
            }
            reparentWebPlayerToBestHost()
        }
    }

    fun detachVideoHost(host: ViewGroup) {
        mainHandler.post {
            if (activeVideoHost === host) {
                activeVideoHost = null
            }
            if (fallbackVideoHost === host) {
                fallbackVideoHost = null
            }
            if (host.indexOfChild(webPlayerView) >= 0) {
                host.removeView(webPlayerView)
            }
            reparentWebPlayerToBestHost()
        }
    }

    private fun reparentWebPlayerToBestHost() {
        val wv = webPlayerView ?: return
        val target = activeVideoHost ?: fallbackVideoHost ?: return
        // Mantener siempre VISIBLE en ventana única para que Chromium no pause el decodificador <video> Widevine.
        wv.visibility = View.VISIBLE
        if (wv.parent === target) {
            wv.requestLayout()
            return
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
        target.addView(
            wv,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        wv.requestLayout()
        if (_state.value.isVideo) {
            evalWebJs("try{var mk=MusicKit.getInstance();if(mk&&window.__amEnsureVideoStage)window.__amEnsureVideoStage(mk,true);}catch(e){}")
        }
    }

    fun resumeVideoIfIntended() {
        if (!userIntentPlaying || !_state.value.isVideo || !usingWebEngine) return
        evalWebJs("try{var mk=MusicKit.getInstance();if(mk&&mk.nowPlayingItem&&!mk.isPlaying){mk.play().catch(function(){});}}catch(e){}")
    }

    fun playItem(
        item: HomeItem,
        container: HomeItem? = null,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        preloadedTracks: List<HomeItem> = emptyList()
    ) {
        restoredPendingPlay = false
        val request = PlayRequest(item, container, startIndex, shuffle, preloadedTracks)
        pendingRequest = request
        userIntentPlaying = true
        val isVideoRequest = isVideoItem(item) || isVideoItem(container)
        val nextNonce = if (isVideoRequest) ++videoNonceCounter else _state.value.videoPlaybackNonce
        val containerType = container?.type?.lowercase().orEmpty()
        // Estación tocada directamente (Radio): el contexto que se muestra es el nombre de la estación
        val stationAsContext = container == null &&
            (item.type.contains("station", ignoreCase = true) || item.id.startsWith("ra."))
        val isPlaylistContainer = containerType.contains("playlist") || containerType.contains("station") || stationAsContext
        val isAlbumContainer = containerType.contains("album")
        explicitContainerIsPlaylist = isPlaylistContainer
        explicitContainerIsAlbum = isAlbumContainer
        explicitContainerTitle = when {
            stationAsContext -> item.title.takeIf { it.isNotBlank() }
            isPlaylistContainer || isAlbumContainer -> container?.title?.takeIf { it.isNotBlank() }
            else -> null
        }
        val initialDisplayItem = if (container != null && preloadedTracks.isNotEmpty() && !shuffle) {
            preloadedTracks.getOrNull(startIndex) ?: item
        } else {
            item
        }.let { baseItem ->
            if (stationAsContext || baseItem.type.contains("station", ignoreCase = true) || baseItem.id.startsWith("ra.")) {
                baseItem.copy(motionArtworkUrl = null)
            } else {
                baseItem
            }
        }
        val oldId = _state.value.currentItem?.id?.takeIf { it.isNotBlank() && it != initialDisplayItem.id }
        prevTrackIdBeforeSwitch = oldId
        switchStartedAtMs = SystemClock.elapsedRealtime()
        switchEpoch++
        _state.update {
            it.copy(
                currentItem = initialDisplayItem,
                contextTitle = explicitContainerTitle,
                isAutoplayItem = false,
                isPlaying = true,
                isBuffering = true,
                positionMs = 0L,
                positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime(),
                errorMessage = null,
                shuffleEnabled = shuffle,
                isVideo = isVideoRequest,
                isVideoFrameReady = false,
                videoPlaybackNonce = nextNonce,
                audioVariantBadge = null
            )
        }

        // MediaPlayback-release-1.1.4.aar solo soporta canciones locales/catálogo con solidez; los videos
        // y las estaciones de radio van directo por MusicKit Web (Widevine + rápida respuesta + metadatos ID3).
        if (isVideoRequest || stationAsContext || containerType.contains("station")) {
            runCatching { nativeController?.stop() }
            runCatching { nativeController?.pause() }
            playViaWebMusicKit(request)
            return
        }

        val creds = tokenManager.getCredentials()
        // Dolby Atmos (E-AC-3 JOC) solo lo entrega el SDK nativo; MusicKit Web se limita a AAC.
        // Se usa el nativo siempre que Atmos no esté en "No" y haya credenciales (nativas o de sesión web).
        val preferNative = shouldUseNativeEngine()
        if (preferNative) {
            // Detener por completo cualquier radio/canción previa en el WebView para que jamás se amontonen dos audios.
            mainHandler.post { pendingWebQueueJs = null }
            evalWebJs(
                """
                (function() {
                  try {
                    window.__amSwitchSeq = (window.__amSwitchSeq || 0) + 1;
                    window.__amSwitchingQueue = true;
                    window.__amUserPaused = true;
                    if (window.__amFadeTimer) { clearInterval(window.__amFadeTimer); window.__amFadeTimer = null; }
                    var els = document.querySelectorAll('audio,video');
                    for (var i = 0; i < els.length; i++) {
                      try { els[i].pause(); els[i].muted = true; } catch (e) {}
                    }
                    var mk = window.MusicKit && MusicKit.getInstance && MusicKit.getInstance();
                    if (mk) {
                      try { mk.stop(); } catch (e) {}
                      try { mk.pause(); } catch (e) {}
                      if (window.__amEnsureVideoStage) window.__amEnsureVideoStage(mk, false);
                    }
                  } catch (e) {}
                })();
                """.trimIndent()
            )
            usingWebEngine = false
            _state.update { it.copy(usingWebPlayback = false) }
            val ctrl = ensureNativeController()
            if (ctrl != null && prepareNative(ctrl, request)) {
                return
            }
        }
        runCatching { nativeController?.stop() }
        runCatching { nativeController?.pause() }
        playViaWebMusicKit(request)
    }

    private fun prepareNative(ctrl: MediaPlayerController, req: PlayRequest): Boolean {
        val target = req.container ?: req.item
        val type = target.type.lowercase()
        val id = target.id
        val isLibrary = type.startsWith("library-") || id.startsWith("l.") || id.startsWith("p.") || id.startsWith("i.")

        return runCatching {
            val shuffleMode = if (req.shuffle) PlaybackShuffleMode.SHUFFLE_MODE_SONGS else PlaybackShuffleMode.SHUFFLE_MODE_OFF
            val provider = when {
                type.contains("station") || id.startsWith("ra.") -> {
                    val sb = StationPlaybackQueueItemProvider.Builder()
                    sb.containers(MediaContainerType.RADIO_STATION, id)
                    sb.playActivityFeatureName("home")
                    sb.build()
                }
                isLibrary -> {
                    val b = LibraryPlaybackQueueItemProvider.Builder()
                        .shuffleMode(shuffleMode)
                        .startItemIndex(req.startIndex.coerceAtLeast(0))
                        .featureName("home")
                    when {
                        type.contains("album") -> b.containers(MediaContainerType.ALBUM, id)
                        type.contains("playlist") -> b.containers(MediaContainerType.PLAYLIST, id)
                        else -> b.items(MediaItemType.SONG, id)
                    }
                    b.build()
                }
                else -> {
                    val b = CatalogPlaybackQueueItemProvider.Builder()
                        .shuffleMode(shuffleMode)
                        .startItemIndex(req.startIndex.coerceAtLeast(0))
                        .featureName("home")
                    when {
                        type.contains("album") -> b.containers(MediaContainerType.ALBUM, id)
                        type.contains("playlist") -> b.containers(MediaContainerType.PLAYLIST, id)
                        else -> b.items(MediaItemType.SONG, id)
                    }
                    b.build()
                }
            }
            ctrl.prepare(provider, true)
            true
        }.onFailure {
            Log.w(TAG, "prepareNative failed", it)
        }.getOrDefault(false)
    }

    fun togglePlayPause() {
        val currentSt = _state.value
        if (restoredPendingPlay && !currentSt.isPlaying && currentSt.currentItem != null) {
            val savedItem = currentSt.currentItem
            val resumePos = restoredSeekPositionMs
            restoredPendingPlay = false
            playItem(savedItem)
            if (resumePos > 2_000L) {
                mainHandler.postDelayed({ seekTo(resumePos) }, 1_100L)
            }
            return
        }
        val nextPlaying = !currentSt.isPlaying
        userIntentPlaying = nextPlaying
        lastPlayPauseToggleAtMs = SystemClock.elapsedRealtime()
        _state.update { it.copy(isPlaying = nextPlaying) }
        mediaSessionBridge.update(_state.value)
        if (usingWebEngine) {
            val isStation = _state.value.currentItem?.let {
                it.type.contains("station", ignoreCase = true) || it.id.startsWith("ra.")
            } == true
            val fadeJs = if (nextPlaying) {
                """
                (async function() {
                  try {
                    window.__amUserPaused = false;
                    var mk = MusicKit.getInstance();
                    if (!mk) return;
                    if (window.__amFadeTimer) { clearInterval(window.__amFadeTimer); window.__amFadeTimer = null; }
                    var target = (typeof window.__amBaseVol === 'number' && window.__amBaseVol > 0.05) ? window.__amBaseVol : 1.0;
                    var els = document.querySelectorAll('audio,video');
                    if (${isStation} && (mk.playbackState === 0 || !mk.nowPlayingItem) && window.__amLiveStationId) {
                      await mk.setQueue({station: window.__amLiveStationId, startPlaying: true});
                    }
                    mk.volume = ${if (isStation) "target" else "0.05"};
                    await mk.play();
                    for (var i = 0; i < els.length; i++) { try { if (els[i].paused && els[i].src) els[i].play().catch(function(){}); } catch(e) {} }
                    if (!${isStation}) {
                      var step = 0, steps = 10;
                      window.__amFadeTimer = setInterval(function() {
                        step++;
                        mk.volume = Math.min(target, target * (step / steps));
                        if (step >= steps) { clearInterval(window.__amFadeTimer); window.__amFadeTimer = null; }
                      }, 22);
                    }
                  } catch(e) {}
                })();
                """.trimIndent()
            } else if (isStation) {
                """
                (async function() {
                  try {
                    window.__amUserPaused = true;
                    if (window.__amFadeTimer) { clearInterval(window.__amFadeTimer); window.__amFadeTimer = null; }
                    var mk = MusicKit.getInstance();
                    var els = document.querySelectorAll('audio,video');
                    for (var i = 0; i < els.length; i++) { try { els[i].pause(); } catch(e) {} }
                    if (mk) {
                      try { await mk.pause(); } catch(e) {}
                      if (mk.isPlaying === true) { try { await mk.stop(); } catch(e2) {} }
                    }
                  } catch(e) {}
                })();
                """.trimIndent()
            } else {
                """
                try {
                  window.__amUserPaused = true;
                  var mk = MusicKit.getInstance();
                  if (mk) {
                    if (window.__amFadeTimer) { clearInterval(window.__amFadeTimer); window.__amFadeTimer = null; }
                    var startVol = (typeof mk.volume === 'number' && mk.volume > 0.05) ? mk.volume : 1.0;
                    window.__amBaseVol = startVol;
                    var step = 0, steps = 10;
                    window.__amFadeTimer = setInterval(function() {
                      step++;
                      mk.volume = Math.max(0, startVol * (1 - step / steps));
                      if (step >= steps) {
                        clearInterval(window.__amFadeTimer);
                        window.__amFadeTimer = null;
                        mk.pause();
                        mk.volume = startVol;
                      }
                    }, 22);
                  }
                } catch(e) {}
                """.trimIndent()
            }
            evalWebJs(fadeJs)
            return
        }
        val ctrl = nativeController
        if (ctrl != null) {
            if (ctrl.playbackState == PlaybackState.PLAYING) ctrl.pause() else ctrl.play()
        } else {
            _state.update { it.copy(isPlaying = !it.isPlaying) }
        }
    }

    fun skipNext() {
        if (usingWebEngine) {
            evalWebJs(
                """
                (async function() {
                  try {
                    var mk = MusicKit.getInstance();
                    if (!mk) return;
                    if (mk.isPlaying && window.__amFadeOut) await window.__amFadeOut(mk, 90);
                    await mk.skipToNextItem();
                    if (window.__amFadeIn) window.__amFadeIn(mk, 140);
                  } catch (e) {}
                })();
                """.trimIndent()
            )
            return
        }
        nativeController?.let { if (it.canSkipToNextItem()) it.skipToNextItem() }
    }

    fun skipPrevious() {
        if (usingWebEngine) {
            evalWebJs(
                """
                (async function() {
                  try {
                    var mk = MusicKit.getInstance();
                    if (!mk) return;
                    if (mk.currentPlaybackTime > 3) {
                      mk.seekToTime(0);
                    } else {
                      if (mk.isPlaying && window.__amFadeOut) await window.__amFadeOut(mk, 90);
                      await mk.skipToPreviousItem();
                      if (window.__amFadeIn) window.__amFadeIn(mk, 140);
                    }
                  } catch (e) {}
                })();
                """.trimIndent()
            )
            return
        }
        nativeController?.let {
            if (it.currentPosition > 3_000L && it.canSeek()) it.seekToPosition(0L)
            else if (it.canSkipToPreviousItem()) it.skipToPreviousItem()
        }
    }

    fun seekTo(positionMs: Long) {
        val maxDur = _state.value.durationMs
        // Evitar saltar al último segundo exacto y disparar accidentalmente el fin de pista
        val safeMax = if (maxDur > 2_500L) maxDur - 1_500L else maxDur
        val clamped = if (safeMax > 0L) positionMs.coerceIn(0L, safeMax) else positionMs.coerceAtLeast(0L)
        val nowMs = SystemClock.elapsedRealtime()
        pendingSeekTargetMs = clamped
        pendingSeekAtMs = nowMs
        lastAutoCrossfadeTrackKey = null
        _state.update {
            it.copy(
                positionMs = clamped,
                positionUpdatedAtElapsedMs = nowMs,
                isTransitioning = false
            )
        }
        if (usingWebEngine) {
            val sec = clamped / 1000.0
            evalWebJs("try{var mk=MusicKit.getInstance();if(mk){window.__amLastPosMs=$clamped;mk.seekToTime($sec);}}catch(e){}")
            return
        }
        nativeController?.let {
            if (it.canSeek()) {
                it.seekToPosition(clamped)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun toMusicKitKind(item: HomeItem): String {
        val type = item.type.lowercase()
        return when {
            type.contains("library-playlist") || type.contains("playlist") -> "playlists"
            type.contains("library-album") || type.contains("album") -> "albums"
            type.contains("library-song") -> "librarySongs"
            type.contains("library-music-video") || type.contains("music-video") -> "musicVideos"
            type.contains("music-movie") -> "musicMovies"
            type.contains("uploaded-video") -> "uploadedVideos"
            type.contains("uploaded-audio") -> "uploadedAudios"
            else -> "songs"
        }
    }

    fun playNext(item: HomeItem) {
        if (_state.value.currentItem == null) {
            playItem(item)
            return
        }
        val ctrl = nativeController
        if (!usingWebEngine && ctrl != null && !isVideoItem(item)) {
            runCatching {
                val provider = buildNativeProvider(item, shuffle = false)
                ctrl.addQueueItems(provider, PlaybackQueueInsertionType.INSERTION_TYPE_AFTER_CURRENT_ITEM)
                return
            }.onFailure { Log.w(TAG, "native playNext failed", it) }
        }
        val jsKind = toMusicKitKind(item)
        evalWebJs(
            "try{var mk=MusicKit.getInstance();if(mk){mk.playNext({${JSONObject.quote(jsKind)}:[${JSONObject.quote(item.id)}]}).then(function(){if(window.__amEmitQueue)window.__amEmitQueue();});}}catch(e){}"
        )
    }

    fun playLater(item: HomeItem) {
        if (_state.value.currentItem == null) {
            playItem(item)
            return
        }
        val ctrl = nativeController
        if (!usingWebEngine && ctrl != null && !isVideoItem(item)) {
            runCatching {
                val provider = buildNativeProvider(item, shuffle = false)
                ctrl.addQueueItems(provider, PlaybackQueueInsertionType.INSERTION_TYPE_AT_END)
                return
            }.onFailure { Log.w(TAG, "native playLater failed", it) }
        }
        val jsKind = toMusicKitKind(item)
        evalWebJs(
            "try{var mk=MusicKit.getInstance();if(mk){mk.playLater({${JSONObject.quote(jsKind)}:[${JSONObject.quote(item.id)}]}).then(function(){if(window.__amEmitQueue)window.__amEmitQueue();});}}catch(e){}"
        )
    }

    fun startStationForItem(item: HomeItem) {
        val type = item.type.lowercase()
        if (type.contains("station") || item.id.startsWith("ra.")) {
            playItem(item)
            return
        }
        runCatching { nativeController?.pause() }
        usingWebEngine = true
        userIntentPlaying = true
        val isVideoRequest = isVideoItem(item)
        val stationId = item.catalogId?.takeIf { it.isNotBlank() && !it.startsWith("i.") } ?: item.id
        val stationParamKey = when {
            type.contains("artist") -> "artist"
            isVideoRequest -> "musicVideo"
            else -> "song"
        }
        val stationVal = if (stationParamKey == "artist" && !stationId.startsWith("a-")) "a-$stationId" else stationId
        prevTrackIdBeforeSwitch = _state.value.currentItem?.id?.takeIf { it.isNotBlank() && it != item.id }
        switchStartedAtMs = SystemClock.elapsedRealtime()
        val myEpoch = ++switchEpoch
        _state.update {
            it.copy(
                currentItem = item,
                isPlaying = true,
                isBuffering = true,
                usingWebPlayback = true,
                isVideo = isVideoRequest,
                isVideoFrameReady = false,
                autoplayEnabled = true
            )
        }
        val script = """
            (async function() {
              var mySeq = (window.__amSwitchSeq = (window.__amSwitchSeq || 0) + 1);
              window.__amActiveEpoch = $myEpoch;
              window.__amSwitchingQueue = true;
              try {
                var mk = MusicKit.getInstance();
                if (!mk || !mk.isAuthorized) {
                  window.__amSwitchingQueue = false;
                  AMPlayerBridge.onPlaybackError('Inicia sesión en Apple Music para reproducir.');
                  return;
                }
                try {
                  if (mk.isPlaying) {
                    if (window.__amFadeOut) await window.__amFadeOut(mk, 110);
                    await mk.pause();
                  }
                } catch (e) {}
                if (mySeq !== window.__amSwitchSeq) return;
                if (window.__amEnsureVideoStage) window.__amEnsureVideoStage(mk, $isVideoRequest);
                try { mk.volume = 0.02; } catch (e) {}
                await mk.setStationQueue({${JSONObject.quote(stationParamKey)}: ${JSONObject.quote(stationVal)}});
                if (mySeq !== window.__amSwitchSeq) return;
                window.__amSwitchingQueue = false;
                await mk.play();
                if (window.__amFadeIn) window.__amFadeIn(mk, 150);
                if (window.__amEmitState) window.__amEmitState();
                if (window.__amEmitQueue) window.__amEmitQueue();
              } catch (e) {
                if (mySeq === window.__amSwitchSeq) {
                  window.__amSwitchingQueue = false;
                  AMPlayerBridge.onPlaybackError(String(e && e.message ? e.message : e));
                }
              }
            })();
        """.trimIndent()
        mainHandler.post {
            ensureWebPlayer()
            reparentWebPlayerToBestHost()
            if (webPlayerReady) evalWebJs(script) else pendingWebQueueJs = script
        }
    }

    fun skipToQueueIndex(index: Int) {
        if (index < 0) return
        userIntentPlaying = true
        if (usingWebEngine) {
            evalWebJs(
                """
                (async function() {
                  try {
                    var mk = MusicKit.getInstance();
                    if (!mk) return;
                    if (mk.isPlaying && window.__amFadeOut) await window.__amFadeOut(mk, 90);
                    await mk.changeToMediaAtIndex($index);
                    if (window.__amFadeIn) window.__amFadeIn(mk, 140);
                    if (window.__amEmitQueue) window.__amEmitQueue();
                  } catch (e) {}
                })();
                """.trimIndent()
            )
            return
        }
        val ctrl = nativeController ?: return
        val qItem = ctrl.queueItems.getOrNull(index) ?: return
        ctrl.skipToQueueItemWithId(qItem.playbackQueueId)
    }

    fun toggleAutoplay() {
        val next = !_state.value.autoplayEnabled
        _state.update { it.copy(autoplayEnabled = next) }
        evalWebJs("try{var mk=MusicKit.getInstance();if(mk){mk.autoplayEnabled=$next;if(window.__amEmitQueue)window.__amEmitQueue();}}catch(e){}")
    }

    fun cycleRepeatMode() {
        val next = (_state.value.repeatMode + 1) % 3
        _state.update { it.copy(repeatMode = next) }
        if (usingWebEngine) {
            evalWebJs("try{var mk=MusicKit.getInstance();if(mk){mk.repeatMode=$next;if(window.__amEmitQueue)window.__amEmitQueue();}}catch(e){}")
        } else {
            nativeController?.setRepeatMode(next)
        }
    }

    fun toggleShuffle() {
        val next = !_state.value.shuffleEnabled
        _state.update { it.copy(shuffleEnabled = next) }
        if (usingWebEngine) {
            val modeVal = if (next) 1 else 0
            evalWebJs("try{var mk=MusicKit.getInstance();if(mk){mk.shuffleMode=$modeVal;if(window.__amEmitQueue)window.__amEmitQueue();}}catch(e){}")
        } else {
            nativeController?.setShuffleMode(
                if (next) PlaybackShuffleMode.SHUFFLE_MODE_SONGS else PlaybackShuffleMode.SHUFFLE_MODE_OFF
            )
        }
    }

    private fun buildNativeProvider(target: HomeItem, shuffle: Boolean, startIndex: Int = 0) = run {
        val type = target.type.lowercase()
        val id = target.id
        val isLibrary = type.startsWith("library-") || id.startsWith("l.") || id.startsWith("p.") || id.startsWith("i.")
        val shuffleMode = if (shuffle) PlaybackShuffleMode.SHUFFLE_MODE_SONGS else PlaybackShuffleMode.SHUFFLE_MODE_OFF
        when {
            type.contains("station") || id.startsWith("ra.") -> {
                val sb = StationPlaybackQueueItemProvider.Builder()
                sb.containers(MediaContainerType.RADIO_STATION, id)
                sb.playActivityFeatureName("home")
                sb.build()
            }
            isLibrary -> {
                val b = LibraryPlaybackQueueItemProvider.Builder()
                    .shuffleMode(shuffleMode)
                    .startItemIndex(startIndex.coerceAtLeast(0))
                    .featureName("home")
                when {
                    type.contains("album") -> b.containers(MediaContainerType.ALBUM, id)
                    type.contains("playlist") -> b.containers(MediaContainerType.PLAYLIST, id)
                    else -> b.items(MediaItemType.SONG, id)
                }
                b.build()
            }
            else -> {
                val b = CatalogPlaybackQueueItemProvider.Builder()
                    .shuffleMode(shuffleMode)
                    .startItemIndex(startIndex.coerceAtLeast(0))
                    .featureName("home")
                when {
                    type.contains("album") -> b.containers(MediaContainerType.ALBUM, id)
                    type.contains("playlist") -> b.containers(MediaContainerType.PLAYLIST, id)
                    else -> b.items(MediaItemType.SONG, id)
                }
                b.build()
            }
        }
    }

    fun release() {
        mainHandler.removeCallbacks(progressTicker)
        mediaSessionBridge.release()
        nativeController?.removeListener(nativeListener)
        nativeController?.release()
        nativeController = null
        mainHandler.post {
            (webPlayerView?.parent as? ViewGroup)?.removeView(webPlayerView)
            activeVideoHost = null
            fallbackVideoHost = null
            webPlayerView?.destroy()
            webPlayerView = null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // WebView MusicKit JS Player (DRM Widevine sobre music.apple.com)
    // ---------------------------------------------------------------------------------------------

    private fun resolveTrackQueueEntry(track: HomeItem): Pair<String, String> {
        val catalogId = track.catalogId?.takeIf { it.isNotBlank() && !it.startsWith("i.") && !it.startsWith("l.") }
        val tLower = track.type.lowercase()
        val isVideo = tLower.contains("video") || tLower.contains("movie")
        return when {
            isVideo && catalogId != null -> "musicVideos" to catalogId
            isVideo -> toMusicKitKind(track) to track.id
            catalogId != null -> "songs" to catalogId
            track.id.startsWith("i.") || tLower.contains("library-song") -> "librarySongs" to track.id
            else -> toMusicKitKind(track) to track.id
        }
    }

    private fun buildPlayLaterBatchesJs(remaining: List<HomeItem>): String {
        if (remaining.isEmpty()) return ""
        val batches = mutableListOf<Pair<String, MutableList<String>>>()
        for (track in remaining) {
            if (track.id.isBlank()) continue
            val (kind, qId) = resolveTrackQueueEntry(track)
            val last = batches.lastOrNull()
            if (last != null && last.first == kind) {
                last.second += qId
            } else {
                batches += kind to mutableListOf(qId)
            }
        }
        if (batches.isEmpty()) return ""
        return batches.joinToString("\n") { (kind, ids) ->
            val arrJson = ids.joinToString(",") { JSONObject.quote(it) }
            "if (mySeq !== window.__amSwitchSeq) return;\nawait mk.playLater({${JSONObject.quote(kind)}: [$arrJson]});"
        }
    }

    private fun playViaWebMusicKit(req: PlayRequest) {
        usingWebEngine = true
        val isVideoRequest = isVideoItem(req.item) || isVideoItem(req.container)
        _state.update {
            it.copy(
                usingWebPlayback = true,
                isVideo = isVideoRequest,
                isVideoFrameReady = false
            )
        }

        val target = req.container ?: req.item
        val type = target.type.lowercase()
        val id = if (isVideoRequest) {
            target.catalogId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: target.id
        } else {
            target.id
        }
        Log.d(TAG, "playViaWebMusicKit target=$type/$id item=${req.item.type}/${req.item.id} startIndex=${req.startIndex} shuffle=${req.shuffle} preloaded=${req.preloadedTracks.size} video=$isVideoRequest")
        val isArtist = type.contains("artist")
        val isStation = type.contains("station") || id.startsWith("ra.")
        val jsKind = when {
            type.contains("library-playlist") || type.contains("playlist") -> "playlists"
            type.contains("library-album") || type.contains("album") -> "albums"
            type.contains("library-song") -> "librarySongs"
            type.contains("library-music-video") || type.contains("music-video") -> "musicVideos"
            type.contains("music-movie") -> "musicMovies"
            type.contains("uploaded-video") -> "uploadedVideos"
            type.contains("uploaded-audio") -> "uploadedAudios"
            else -> "songs"
        }

        val useFastPlaylistWindow = req.preloadedTracks.isNotEmpty() &&
            jsKind == "playlists" &&
            !isStation &&
            !isArtist

        val shuffleVal = if (req.shuffle) 1 else 0
        val myEpoch = switchEpoch
        val script = if (useFastPlaylistWindow) {
            val safeIdx = req.startIndex.coerceIn(0, req.preloadedTracks.lastIndex)
            val orderedWindow = if (req.shuffle) {
                req.preloadedTracks.shuffled().take(50)
            } else {
                req.preloadedTracks.drop(safeIdx).take(50)
            }
            val firstTrack = if (!req.shuffle && req.item.id.isNotBlank() && !req.item.type.contains("playlist")) {
                req.item
            } else {
                orderedWindow.first()
            }
            val (firstKind, firstId) = resolveTrackQueueEntry(firstTrack)
            val tailTracks = orderedWindow.drop(1)
            val playLaterJs = buildPlayLaterBatchesJs(tailTracks)
            val firstQueueJs = if (firstKind == "musicVideos") {
                """
                try {
                  await mk.setQueue({musicVideo: ${JSONObject.quote(firstId)}, startPlaying: true});
                } catch (eVidFirst) {
                  await mk.setQueue({musicVideos: [${JSONObject.quote(firstId)}], startPlaying: true});
                }
                """.trimIndent()
            } else {
                "await mk.setQueue({${JSONObject.quote(firstKind)}: [${JSONObject.quote(firstId)}], startPlaying: true});"
            }
            """
                (async function() {
                  var mySeq = (window.__amSwitchSeq = (window.__amSwitchSeq || 0) + 1);
                  window.__amActiveEpoch = $myEpoch;
                  window.__amSwitchingQueue = true;
                  try {
                    var mk = MusicKit.getInstance();
                    try {
                      var savedTok = ${JSONObject.quote(tokenManager.getCredentials().let { it.webUserToken.ifBlank { it.musicUserToken }.trim() })};
                      if (savedTok) {
                        if (window.localStorage && localStorage.getItem('music.ampwebplay.media-user-token') !== savedTok) {
                          localStorage.setItem('music.ampwebplay.media-user-token', savedTok);
                        }
                        if (mk) {
                          try { mk.previewOnly = false; } catch (ep0) {}
                          if (!mk.musicUserToken) {
                            try { mk.musicUserToken = savedTok; } catch (ep1) {}
                          }
                        }
                      }
                    } catch (etok) {}
                    if (!mk || !mk.isAuthorized) {
                      window.__amSwitchingQueue = false;
                      AMPlayerBridge.onPlaybackError('Inicia sesión en Apple Music para reproducir.');
                      return;
                    }
                    try { mk.previewOnly = false; } catch (ep2) {}
                    try {
                      var elsU = document.querySelectorAll('audio,video');
                      for (var ui = 0; ui < elsU.length; ui++) { try { elsU[ui].muted = false; } catch (eu) {} }
                      if (mk.isPlaying) { try { mk.pause(); } catch (ep) {} }
                    } catch (e) {}
                    if (mySeq !== window.__amSwitchSeq) return;
                    if (window.__amEnsureVideoStage) window.__amEnsureVideoStage(mk, $isVideoRequest);
                    try { mk.volume = 0.02; } catch (e) {}
                    $firstQueueJs
                    if (mySeq !== window.__amSwitchSeq) return;
                    try { mk.shuffleMode = $shuffleVal; } catch (e) {}
                    try { mk.autoplayEnabled = ${_state.value.autoplayEnabled}; } catch (e) {}
                    window.__amSwitchingQueue = false;
                    if (!mk.isPlaying) { try { await mk.play(); } catch (e) {} }
                    if (window.__amFadeIn) window.__amFadeIn(mk, 150);
                    window.__amUserQueueEnd = (mk.queue && Array.isArray(mk.queue.items)) ? mk.queue.items.length : -1;
                    if (window.__amEmitState) window.__amEmitState();
                    try {
                      $playLaterJs
                      if (mySeq === window.__amSwitchSeq) {
                        window.__amUserQueueEnd = (mk.queue && Array.isArray(mk.queue.items)) ? mk.queue.items.length : -1;
                        if (window.__amEmitQueue) window.__amEmitQueue();
                      }
                    } catch (e) {}
                  } catch (e) {
                    if (mySeq === window.__amSwitchSeq) {
                      window.__amSwitchingQueue = false;
                      try { var mkR = MusicKit.getInstance(); if (mkR && window.__amFadeIn) window.__amFadeIn(mkR, 80); } catch (e2) {}
                      AMPlayerBridge.onPlaybackError(String(e && e.message ? e.message : e));
                    }
                  }
                })();
            """.trimIndent()
        } else {
            val queueCall = when {
                isArtist -> "await mk.setStationQueue({artist: ${JSONObject.quote("a-$id")}});"
                isStation -> "await mk.setQueue({station: ${JSONObject.quote(id)}, startPlaying: true});"
                isVideoRequest && jsKind == "musicVideos" -> """
                    try {
                      await mk.setQueue({musicVideo: ${JSONObject.quote(id)}, startPlaying: true});
                    } catch (eVid1) {
                      await mk.setQueue({musicVideos: [${JSONObject.quote(id)}], startPlaying: true});
                    }
                """.trimIndent()
                req.startIndex > 0 -> """
                    await mk.setQueue({
                      ${JSONObject.quote(jsKind)}: [${JSONObject.quote(id)}],
                      startWith: ${req.startIndex},
                      startPlaying: true
                    });
                """.trimIndent()
                else -> "await mk.setQueue({${JSONObject.quote(jsKind)}: [${JSONObject.quote(id)}], startPlaying: true});"
            }
            val stationMetaResetJs = if (isStation) {
                "window.__amLiveStationMode = true; window.__amLiveStationId = ${JSONObject.quote(id)}; window.__amLiveMeta = null; window.__amLiveTmp = { title: '', artist: '', album: '', artwork: '' };"
            } else {
                "window.__amLiveStationMode = false; window.__amLiveStationId = ''; window.__amLiveMeta = null;"
            }
            """
                (async function() {
                  var mySeq = (window.__amSwitchSeq = (window.__amSwitchSeq || 0) + 1);
                  window.__amActiveEpoch = $myEpoch;
                  window.__amSwitchingQueue = true;
                  $stationMetaResetJs
                  try {
                    var mk = MusicKit.getInstance();
                    try {
                      var savedTok = ${JSONObject.quote(tokenManager.getCredentials().let { it.webUserToken.ifBlank { it.musicUserToken }.trim() })};
                      if (savedTok) {
                        if (window.localStorage && localStorage.getItem('music.ampwebplay.media-user-token') !== savedTok) {
                          localStorage.setItem('music.ampwebplay.media-user-token', savedTok);
                        }
                        if (mk) {
                          try { mk.previewOnly = false; } catch (ep0) {}
                          if (!mk.musicUserToken) {
                            try { mk.musicUserToken = savedTok; } catch (ep1) {}
                          }
                        }
                      }
                    } catch (etok) {}
                    if (!mk || !mk.isAuthorized) {
                      window.__amSwitchingQueue = false;
                      AMPlayerBridge.onPlaybackError('Inicia sesión en Apple Music para reproducir.');
                      return;
                    }
                    try { mk.previewOnly = false; } catch (ep2) {}
                    try {
                      var elsU = document.querySelectorAll('audio,video');
                      for (var ui = 0; ui < elsU.length; ui++) { try { elsU[ui].muted = false; } catch (eu) {} }
                      if (mk.isPlaying) { try { mk.pause(); } catch (ep) {} }
                    } catch (e) {}
                    if (mySeq !== window.__amSwitchSeq) return;
                    if (window.__amEnsureVideoStage) window.__amEnsureVideoStage(mk, $isVideoRequest);
                    try { mk.volume = 0.02; } catch (e) {}
                    $queueCall
                    if (mySeq !== window.__amSwitchSeq) return;
                    try { mk.shuffleMode = $shuffleVal; } catch (e) {}
                    try { mk.autoplayEnabled = ${_state.value.autoplayEnabled}; } catch (e) {}
                    window.__amSwitchingQueue = false;
                    window.__amUserQueueEnd = (mk.queue && Array.isArray(mk.queue.items)) ? mk.queue.items.length : -1;
                    if (!mk.isPlaying) { try { await mk.play(); } catch (e) {} }
                    if (window.__amFadeIn) window.__amFadeIn(mk, 150);
                    if (window.__amEnsureVideoStage) window.__amEnsureVideoStage(mk, $isVideoRequest);
                    if (window.__amEmitState) window.__amEmitState();
                  } catch (e) {
                    if (mySeq === window.__amSwitchSeq) {
                      window.__amSwitchingQueue = false;
                      try { var mkR = MusicKit.getInstance(); if (mkR && window.__amFadeIn) window.__amFadeIn(mkR, 80); } catch (e2) {}
                      AMPlayerBridge.onPlaybackError(String(e && e.message ? e.message : e));
                    }
                  }
                })();
            """.trimIndent()
        }

        mainHandler.post {
            ensureWebPlayer()
            reparentWebPlayerToBestHost()
            if (webPlayerReady) {
                evalWebJs(script)
            } else {
                pendingWebQueueJs = script
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun ensureWebPlayer() {
        if (webPlayerView != null) return
        val creds = tokenManager.getCredentials()
        val storefront = creds.storefront.ifBlank { "mx" }
        // Solo restaurar la cookie si el WebView no tiene ya una sesión válida (nunca sobrescribir la cookie activa).
        val savedUserToken = creds.webUserToken.ifBlank { creds.musicUserToken }.trim()
        if (savedUserToken.isNotBlank()) {
            runCatching {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                val existing = cm.getCookie("https://music.apple.com").orEmpty()
                if (!existing.contains("media-user-token=")) {
                    cm.setCookie(
                        "https://music.apple.com",
                        "media-user-token=$savedUserToken; Domain=.music.apple.com; Path=/; Secure; Max-Age=15552000"
                    )
                    cm.flush()
                }
            }.onFailure { Log.w(TAG, "No se pudo restaurar la cookie de sesión web", it) }
        }
        val restoreSessionJs = if (savedUserToken.isNotBlank()) {
            "try{var k='music.ampwebplay.media-user-token';if(window.localStorage&&!localStorage.getItem(k)){localStorage.setItem(k,${JSONObject.quote(savedUserToken)});}}catch(e){}"
        } else ""
        webPlayerView = WebView(appContext).apply {
            setBackgroundColor(Color.BLACK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                // MusicKit JS restringe VideoPlayer cuando runtime.os.isAndroid === true; con UA desktop Linux Chrome habilita HlsJsVideoPlayer + Widevine.
                userAgentString = DESKTOP_CHROME_UA
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val webView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
            addJavascriptInterface(WebPlayerJsBridge(), "AMPlayerBridge")
            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): android.graphics.Bitmap =
                    android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)

                override fun onPermissionRequest(request: PermissionRequest) {
                    val allowed = request.resources.filter {
                        it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                    }
                    if (allowed.isNotEmpty()) request.grant(allowed.toTypedArray()) else request.deny()
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    if (url?.contains("music.apple.com") == true) {
                        if (restoreSessionJs.isNotBlank()) view.evaluateJavascript(restoreSessionJs, null)
                        view.evaluateJavascript(WEB_CLOAK_CSS_JS, null)
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (url?.contains("music.apple.com") == true) {
                        view.evaluateJavascript(WEB_CLOAK_CSS_JS, null)
                        view.evaluateJavascript(WEB_PLAYER_HOOK_JS, null)
                    }
                }
            }
            loadUrl("https://music.apple.com/$storefront/home")
        }
        reparentWebPlayerToBestHost()
    }

    private fun evalWebJs(js: String) {
        mainHandler.post {
            webPlayerView?.evaluateJavascript(js, null)
        }
    }

    private inner class WebPlayerJsBridge {
        @JavascriptInterface
        fun onReady() {
            Log.d(TAG, "WebMusicKit player ready (pending=${!pendingWebQueueJs.isNullOrBlank()})")
            mainHandler.post {
                webPlayerReady = true
                val pending = pendingWebQueueJs
                pendingWebQueueJs = null
                if (!pending.isNullOrBlank()) evalWebJs(pending)
            }
        }

        @JavascriptInterface
        fun onState(
            isPlaying: Boolean,
            isBuffering: Boolean,
            positionMs: Long,
            durationMs: Long,
            title: String,
            artist: String,
            artworkUrl: String,
            isVideo: Boolean,
            isVideoFrameReady: Boolean,
            itemId: String,
            itemType: String,
            catalogId: String,
            albumName: String,
            isAutoplay: Boolean,
            epoch: Long
        ) {
            // Descartar eventos rezagados de una canción anterior mientras se cambia de cola
            if (epoch < switchEpoch) return
            val prevId = prevTrackIdBeforeSwitch
            if (prevId != null && itemId.isNotBlank() && itemId == prevId &&
                (SystemClock.elapsedRealtime() - switchStartedAtMs) < 4000L
            ) {
                return
            }
            if (prevId != null && itemId.isNotBlank() && itemId != prevId) {
                prevTrackIdBeforeSwitch = null
            }

            _state.update { current ->
                val resolvedType = itemType.ifBlank {
                    current.currentItem?.type ?: if (isVideo) "music-videos" else "songs"
                }
                val resolvedId = itemId.ifBlank { current.currentItem?.id.orEmpty() }
                val sameItem = current.currentItem?.id == resolvedId
                val resolvedCatalogId = catalogId.ifBlank {
                    when {
                        resolvedId.all(Char::isDigit) && resolvedId.isNotBlank() -> resolvedId
                        sameItem -> current.currentItem?.catalogId
                        else -> null
                    }
                }
                val isStationItem = resolvedType.contains("station", ignoreCase = true) ||
                    resolvedId.startsWith("ra.") ||
                    resolvedType.contains("radio", ignoreCase = true)
                val updatedItem = if (title.isNotBlank()) {
                    if (sameItem && current.currentItem != null) {
                        current.currentItem.copy(
                            id = resolvedId.ifBlank { current.currentItem.id },
                            type = resolvedType,
                            title = title,
                            subtitle = artist.ifBlank { current.currentItem.subtitle },
                            artworkUrl = artworkUrl.ifBlank { current.currentItem.artworkUrl },
                            catalogId = resolvedCatalogId,
                            motionArtworkUrl = if (isStationItem) null else current.currentItem.motionArtworkUrl
                        )
                    } else {
                        // Nueva canción: crear HomeItem limpio para no arrastrar metadatos/catalogId/url de la canción anterior
                        HomeItem(
                            id = resolvedId,
                            type = resolvedType,
                            title = title,
                            subtitle = artist,
                            artworkUrl = artworkUrl.takeIf { it.isNotBlank() },
                            catalogId = resolvedCatalogId,
                            motionArtworkUrl = null
                        )
                    }
                } else {
                    current.currentItem
                }
                val resolvedContext = when {
                    isAutoplay -> null
                    explicitContainerIsPlaylist && !explicitContainerTitle.isNullOrBlank() -> explicitContainerTitle
                    albumName.isNotBlank() -> albumName
                    !explicitContainerTitle.isNullOrBlank() -> explicitContainerTitle
                    else -> current.contextTitle
                }
                val recentToggle = (SystemClock.elapsedRealtime() - lastPlayPauseToggleAtMs) < 1600L
                val effectivePlaying = if (recentToggle) userIntentPlaying else isPlaying
                val sameVideoItem = current.currentItem?.id == resolvedId
                val nowMs = SystemClock.elapsedRealtime()
                val cleanPos = filterStaleSeekPosition(positionMs, current.positionMs, nowMs)
                val posChanged = cleanPos != current.positionMs
                val trackChanged = current.currentItem?.id != resolvedId
                val updatedAt = if (posChanged || trackChanged || !effectivePlaying || isBuffering || current.positionUpdatedAtElapsedMs == 0L) {
                    nowMs
                } else {
                    current.positionUpdatedAtElapsedMs
                }
                current.copy(
                    currentItem = updatedItem,
                    contextTitle = resolvedContext,
                    isAutoplayItem = isAutoplay,
                    isPlaying = effectivePlaying,
                    isBuffering = if (recentToggle && !userIntentPlaying) false else isBuffering,
                    positionMs = cleanPos,
                    positionUpdatedAtElapsedMs = updatedAt,
                    durationMs = durationMs.coerceAtLeast(0L),
                    isVideo = if (title.isNotBlank()) isVideo else current.isVideo,
                    isVideoFrameReady = when {
                        !isVideo -> false
                        !sameVideoItem -> isVideoFrameReady
                        else -> current.isVideoFrameReady || isVideoFrameReady
                    }
                )
            }
        }

        @JavascriptInterface
        fun onTime(positionMs: Long, durationMs: Long) {
            val now = SystemClock.elapsedRealtime()
            if (prevTrackIdBeforeSwitch != null && (now - switchStartedAtMs) < 1500L) return
            _state.update { current ->
                val cleanPos = filterStaleSeekPosition(positionMs, current.positionMs, now)
                val posChanged = cleanPos != current.positionMs
                current.copy(
                    positionMs = cleanPos,
                    positionUpdatedAtElapsedMs = if (posChanged || !current.isPlaying || current.positionUpdatedAtElapsedMs == 0L) now else current.positionUpdatedAtElapsedMs,
                    durationMs = if (durationMs > 0L) durationMs else current.durationMs
                )
            }
        }

        @JavascriptInterface
        fun onQueueState(
            queueJson: String,
            queuePosition: Int,
            autoplayEnabled: Boolean,
            repeatMode: Int,
            shuffleEnabled: Boolean
        ) {
            val parsed = runCatching {
                val arr = JSONArray(queueJson)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(
                            QueueTrackItem(
                                index = o.optInt("index", i),
                                id = o.optString("id"),
                                catalogId = o.optString("catalogId").takeIf { it.isNotBlank() },
                                type = o.optString("type", "songs"),
                                title = o.optString("title"),
                                subtitle = o.optString("subtitle"),
                                artworkUrl = o.optString("artworkUrl").takeIf { it.isNotBlank() },
                                durationMs = o.optLong("durationMs", 0L)
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    queueItems = parsed,
                    queuePosition = queuePosition,
                    autoplayEnabled = autoplayEnabled,
                    repeatMode = repeatMode,
                    shuffleEnabled = shuffleEnabled
                )
            }
        }

        /** Depuración de metadatos de radio en vivo (Logcat, etiqueta `LiveRadio`). */
        @JavascriptInterface
        fun onDebug(message: String) {
            Log.d("LiveRadio", message)
        }

        @JavascriptInterface
        fun onPlaybackError(message: String) {
            Log.w(TAG, "WebMusicKit error: $message")
            // Si quien reproduce es el SDK nativo, un error del reproductor web (p. ej. "Inicia sesión" en un
            // dispositivo donde la sesión web no está autorizada) NO debe marcar la canción como detenida:
            // antes esto dejaba la nueva canción "sin cargar" al cambiar de pista.
            if (!usingWebEngine) {
                Log.d(TAG, "Ignorando error web: el motor activo es el nativo")
                return
            }
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = message.ifBlank { "No se pudo reproducir esta canción." }
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Native MediaPlayerController Listener
    // ---------------------------------------------------------------------------------------------

    private val nativeListener = object : MediaPlayerController.Listener {
        override fun onPlaybackStateChanged(controller: MediaPlayerController, previousState: Int, currentState: Int) {
            if (currentState == PlaybackState.PLAYING) {
                nativeWorkedWithCurrentToken = true
                nativeRejectedWebToken = false
            }
            val badge = AudioSpatialCapabilities.mapAudioVariantBadge(controller.audioVariant)
            _state.update {
                it.copy(
                    isPlaying = currentState == PlaybackState.PLAYING,
                    isBuffering = false,
                    positionMs = controller.currentPosition.coerceAtLeast(0L),
                    positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime(),
                    durationMs = controller.duration.coerceAtLeast(it.durationMs),
                    audioVariantBadge = badge ?: it.audioVariantBadge
                )
            }
        }

        override fun onPlaybackStateUpdated(controller: MediaPlayerController) {
            _state.update {
                it.copy(
                    positionMs = controller.currentPosition.coerceAtLeast(0L),
                    durationMs = controller.duration.coerceAtLeast(it.durationMs)
                )
            }
        }

        override fun onBufferingStateChanged(controller: MediaPlayerController, isBuffering: Boolean) {
            _state.update { it.copy(isBuffering = isBuffering) }
        }

        override fun onCurrentItemChanged(
            controller: MediaPlayerController,
            previousItem: PlayerQueueItem?,
            currentItem: PlayerQueueItem?
        ) {
            val media = currentItem?.item ?: return
            val art = runCatching { media.getArtworkUrl(600, 600) }.getOrNull()?.takeIf { it.isNotBlank() }
            val newId = media.subscriptionStoreId.orEmpty()
            val newTitle = media.title.orEmpty()
            val newArtist = media.artistName.orEmpty()
            _state.update { state ->
                val base = state.currentItem
                // ¿Sigue siendo la misma canción (solo llegaron metadatos nuevos)?
                val sameTrack = base != null && (
                    (newId.isNotBlank() && (base.id == newId || base.catalogId == newId)) ||
                        (newTitle.isNotBlank() && base.title == newTitle && base.subtitle == newArtist)
                    )
                if (sameTrack && base != null) {
                    state.copy(
                        currentItem = base.copy(
                            title = newTitle.ifBlank { base.title },
                            subtitle = newArtist.ifBlank { base.subtitle },
                            artworkUrl = art ?: base.artworkUrl
                        ),
                        durationMs = controller.duration.coerceAtLeast(state.durationMs),
                        queuePosition = controller.playbackQueueIndex
                    )
                } else {
                    // Canción distinta: construir el ítem desde cero. Antes se copiaba el anterior y
                    // quedaban el id y la portada viejos (portada congelada al saltar de canción).
                    state.copy(
                        currentItem = HomeItem(
                            id = newId,
                            type = "songs",
                            title = newTitle,
                            subtitle = newArtist,
                            artworkUrl = art,
                            catalogId = newId.takeIf { it.isNotBlank() }
                        ),
                        positionMs = 0L,
                        positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime(),
                        durationMs = controller.duration.coerceAtLeast(0L),
                        audioVariantBadge = null,
                        queuePosition = controller.playbackQueueIndex
                    )
                }
            }
        }

        override fun onItemEnded(controller: MediaPlayerController, queueItem: PlayerQueueItem, endPosition: Long) = Unit

        override fun onMetadataUpdated(controller: MediaPlayerController, currentItem: PlayerQueueItem) {
            onCurrentItemChanged(controller, null, currentItem)
        }

        override fun onPlaybackQueueChanged(controller: MediaPlayerController, queueItems: MutableList<PlayerQueueItem>) {
            val mapped = queueItems.mapIndexed { idx, qi ->
                val m = qi.item
                val art = runCatching { m.getArtworkUrl(160, 160) }.getOrNull()
                QueueTrackItem(
                    index = idx,
                    id = m.subscriptionStoreId ?: idx.toString(),
                    catalogId = m.subscriptionStoreId,
                    type = "songs",
                    title = m.title.orEmpty(),
                    subtitle = m.artistName.orEmpty(),
                    artworkUrl = art,
                    durationMs = 0L
                )
            }
            _state.update {
                it.copy(
                    queueItems = mapped,
                    queuePosition = controller.playbackQueueIndex
                )
            }
        }

        override fun onPlaybackQueueItemsAdded(controller: MediaPlayerController, addedItems: PlaybackQueueItemsAdded) {
            onPlaybackQueueChanged(controller, controller.queueItems)
        }

        override fun onPlaybackModeChanged(controller: MediaPlayerController, playbackMode: PlaybackModeContainer) = Unit

        override fun onPlaybackRepeatModeChanged(controller: MediaPlayerController, @PlaybackRepeatMode currentRepeatMode: Int) {
            _state.update { it.copy(repeatMode = currentRepeatMode) }
        }

        override fun onPlaybackShuffleModeChanged(controller: MediaPlayerController, @PlaybackShuffleMode currentShuffleMode: Int) {
            _state.update { it.copy(shuffleEnabled = currentShuffleMode != PlaybackShuffleMode.SHUFFLE_MODE_OFF) }
        }
        override fun onPlayerStateRestored(controller: MediaPlayerController) = Unit

        override fun onPlaybackError(controller: MediaPlayerController, error: MediaPlayerException) {
            Log.w(TAG, "Native playback error type=${error.type}: ${error.message}")
            val creds = tokenManager.getCredentials()
            // Antes, CUALQUIER error nativo (p. ej. una emisora que el SDK no soporta) marcaba el token web como
            // rechazado para siempre: todas las canciones siguientes se iban al reproductor web y, en dispositivos
            // sin sesión web (tablet), se quedaban sin cargar. Solo se marca si el nativo nunca funcionó con este token.
            if (!(creds.hasDeveloperToken && creds.hasUserToken) && !nativeWorkedWithCurrentToken) {
                nativeRejectedWebToken = true
            }
            val fallbackReq = pendingRequest
            if (fallbackReq != null && !usingWebEngine) {
                // Un error justo al cambiar de canción suele ser la descarga cancelada de la pista anterior:
                // reintentar UNA vez con el motor nativo antes de caer al reproductor web (que puede no tener
                // sesión en este dispositivo y quedarse sin cargar).
                val target = fallbackReq.container ?: fallbackReq.item
                val isStation = target.type.contains("station", ignoreCase = true) || target.id.startsWith("ra.")
                val nativeUsable = (creds.hasDeveloperToken && creds.hasUserToken) || nativeWorkedWithCurrentToken
                if (nativeUsable && !isStation && nativeRetriedRequest !== fallbackReq) {
                    nativeRetriedRequest = fallbackReq
                    Log.w(TAG, "Reintentando la canción con el motor nativo")
                    mainHandler.postDelayed({
                        if (pendingRequest === fallbackReq && !usingWebEngine) {
                            val ctrl = ensureNativeController()
                            if (ctrl == null || !prepareNative(ctrl, fallbackReq)) {
                                playViaWebMusicKit(fallbackReq)
                            }
                        }
                    }, 350L)
                    return
                }
                // Si el token web no sirve en SDK nativo, pasar sin fricción a MusicKit JS Widevine
                playViaWebMusicKit(fallbackReq)
                return
            }
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.localizedMessage ?: "Error al reproducir (${error.type})."
                )
            }
        }
    }

    private companion object {
        const val TAG = "AppleMusicPlayer"
        const val KEY_ATMOS_MODE = "dolby_atmos_mode"
        const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        // Oculta toda la UI web de music.apple.com desde el primer milisegundo para que el usuario jamás vea páginas ni menús web.
        val WEB_CLOAK_CSS_JS = """
            (function(){
              var d = document.documentElement;
              if (!d) return;
              var st = document.getElementById('am-cloak-css') || document.createElement('style');
              st.id = 'am-cloak-css';
              st.textContent =
                'html, body { background:#000 !important; margin:0 !important; padding:0 !important; overflow:hidden !important; }' +
                'body > *:not(#am-video-stage) { opacity:0 !important; visibility:hidden !important; pointer-events:none !important; }' +
                '#am-video-stage, #am-video-stage video, #apple-music-video-player {' +
                '  opacity:1 !important; visibility:visible !important; pointer-events:none !important;' +
                '  position:fixed !important; inset:0 !important; width:100vw !important; height:100vh !important;' +
                '  max-width:none !important; max-height:none !important; object-fit:contain !important;' +
                '  background:#000 !important; z-index:2147483647 !important;' +
                '}' +
                'video::-webkit-media-controls, video::-webkit-media-controls-enclosure, video::-webkit-media-controls-panel, video::-webkit-media-controls-start-playback-button, video::-webkit-media-controls-overlay-play-button {' +
                '  display:none !important; opacity:0 !important; visibility:hidden !important; -webkit-appearance:none !important;' +
                '}';
              (document.head || d).appendChild(st);
            })();
        """.trimIndent()

        val WEB_PLAYER_HOOK_JS = """
            (function() {
              if (window.__amPlayerHooked) return;
              window.__amEnsureVideoStage = function(mk, show) {
                try {
                  $WEB_CLOAK_CSS_JS
                  var stage = document.getElementById('am-video-stage');
                  if (!stage && document.body) {
                    stage = document.createElement('div');
                    stage.id = 'am-video-stage';
                    stage.style.cssText = 'position:fixed;inset:0;width:100vw;height:100vh;background:#000;z-index:2147483646;display:none;align-items:center;justify-content:center;overflow:hidden;';
                    document.body.appendChild(stage);
                  }
                  if (mk && stage) {
                    try { mk.videoContainerElement = stage; } catch (e) {}
                  }
                  if (stage) stage.style.display = show ? 'flex' : 'none';
                  var rawVid = document.getElementById('apple-music-video-player') || document.querySelector('video');
                  var vid = (rawVid && rawVid.tagName !== 'VIDEO' && rawVid.shadowRoot)
                    ? (rawVid.shadowRoot.querySelector('video') || rawVid)
                    : rawVid;
                  if (vid && stage && show && rawVid && rawVid.parentElement !== stage && rawVid.tagName === 'VIDEO') {
                    try { stage.appendChild(rawVid); } catch (e) {}
                  } else if (rawVid && stage && show && rawVid.id === 'apple-music-video-player' && rawVid.parentElement !== stage) {
                    try { stage.appendChild(rawVid); } catch (e) {}
                  }
                  if (vid && !vid.__amHookedEvents) {
                    vid.__amHookedEvents = true;
                    ['loadeddata', 'canplay', 'playing', 'resize', 'timeupdate'].forEach(function(ev) {
                      vid.addEventListener(ev, function() {
                        if (window.__amEmitState) window.__amEmitState();
                      });
                    });
                  }
                } catch (e) {}
              };
              // onPageFinished puede dispararse 2 veces: un solo ciclo de inicialización por página.
              if (window.__amInitStarted) return;
              window.__amInitStarted = true;
              var tries = 0;
              function init() {
                tries++;
                $WEB_CLOAK_CSS_JS
                var mk = null;
                try { mk = window.MusicKit && MusicKit.getInstance && MusicKit.getInstance(); } catch (e) {}
                if (tries % 8 === 1) { try { AMPlayerBridge.onDebug('[diag] init try=' + tries + ' hasMK=' + !!window.MusicKit + ' mk=' + !!mk + ' auth=' + (mk ? mk.isAuthorized : 'n/a') + ' url=' + location.pathname); } catch (e) {} }
                if (!mk || !mk.isAuthorized) {
                  // En algunos equipos (tablets) MusicKit tarda más de 20 s en autorizar la sesión web. Antes se
                  // rendía a los 20 s y los videos quedaban esperando indefinidamente: ahora sigue intentando
                  // hasta ~2 min (cada 250 ms los primeros 20 s y luego cada 500 ms).
                  if (tries < 80) return setTimeout(init, 250);
                  if (tries < 280) return setTimeout(init, 500);
                  window.__amInitStarted = false;
                  AMPlayerBridge.onPlaybackError('Inicia sesión en Apple Music para reproducir.');
                  return;
                }
                window.__amPlayerHooked = true;
                window.__amFadeOut = function(mk, ms) {
                  return new Promise(function(resolve) {
                    try {
                      if (!mk) return resolve();
                      if (window.__amFadeTimer) { clearInterval(window.__amFadeTimer); window.__amFadeTimer = null; }
                      var startVol = (typeof mk.volume === 'number' && mk.volume > 0.05) ? mk.volume : 1.0;
                      window.__amBaseVol = startVol;
                      var steps = Math.max(3, Math.round((ms || 100) / 18));
                      var step = 0;
                      window.__amFadeTimer = setInterval(function() {
                        step++;
                        try { mk.volume = Math.max(0.01, startVol * (1 - step / steps)); } catch (e) {}
                        if (step >= steps) {
                          clearInterval(window.__amFadeTimer);
                          window.__amFadeTimer = null;
                          resolve();
                        }
                      }, 18);
                    } catch (e) { resolve(); }
                  });
                };
                window.__amFadeIn = function(mk, ms) {
                  try {
                    if (!mk) return;
                    if (window.__amFadeTimer) { clearInterval(window.__amFadeTimer); window.__amFadeTimer = null; }
                    var target = (typeof window.__amBaseVol === 'number' && window.__amBaseVol > 0.05) ? window.__amBaseVol : 1.0;
                    var steps = Math.max(4, Math.round((ms || 140) / 18));
                    var step = 0;
                    window.__amFadeTimer = setInterval(function() {
                      step++;
                      try { mk.volume = Math.min(target, target * (step / steps)); } catch (e) {}
                      if (step >= steps) {
                        clearInterval(window.__amFadeTimer);
                        window.__amFadeTimer = null;
                      }
                    }, 18);
                  } catch (e) {}
                };
                try { if (typeof mk.autoplayEnabled === 'boolean' && !mk.autoplayEnabled) mk.autoplayEnabled = true; } catch (e) {}
                try { mk.bitrate = 256; } catch (e) {}
                window.__amEnsureVideoStage(mk, false);
                function serializeQueue() {
                  try {
                    var q = mk.queue;
                    var raw = q && Array.isArray(q.items) ? q.items : [];
                    var pos = typeof (q && q.position) === 'number' ? q.position : mk.nowPlayingItemIndex;
                    if (typeof pos !== 'number') pos = -1;
                    var out = [];
                    var start = Math.max(0, pos - 5);
                    var end = Math.min(raw.length, start + 65);
                    for (var i = start; i < end; i++) {
                      var qi = raw[i];
                      if (!qi) continue;
                      var qa = qi.attributes || {};
                      var qArt = (qa.artwork && qa.artwork.url)
                        ? qa.artwork.url.replace('{w}', '160').replace('{h}', '160').replace('{c}', 'cc').replace('{f}', 'jpg')
                        : '';
                      var catId = (qa.playParams && qa.playParams.catalogId) ? qa.playParams.catalogId : '';
                      out.push({
                        index: i,
                        id: String(qi.id || ''),
                        catalogId: String(catId || ''),
                        type: String(qi.type || 'songs'),
                        title: String(qa.name || qi.title || ''),
                        subtitle: String(qa.artistName || ''),
                        artworkUrl: qArt,
                        durationMs: Number(qa.durationInMillis || 0)
                      });
                    }
                    var ap = mk.autoplayEnabled === true;
                    var rep = Number(mk.repeatMode || 0);
                    var shf = Number(mk.shuffleMode || 0) !== 0;
                    var jsonStr = JSON.stringify(out);
                    var sig = pos + '|' + ap + '|' + rep + '|' + shf + '|' + out.length + '|' + (out[0] ? out[0].id : '') + '|' + (out[out.length - 1] ? out[out.length - 1].id : '');
                    if (sig !== window.__amLastQueueSig) {
                      window.__amLastQueueSig = sig;
                      AMPlayerBridge.onQueueState(jsonStr, pos, ap, rep, shf);
                    }
                  } catch (e) {}
                }
                window.__amEmitQueue = function() {
                  window.__amLastQueueSig = '';
                  serializeQueue();
                };
                function emit() {
                  try {
                    if (window.__amSwitchingQueue) return;
                    var item = mk.nowPlayingItem;
                    var attrs = item && item.attributes ? item.attributes : {};
                    var itemType = item && item.type ? String(item.type).toLowerCase() : '';
                    var isVideo = itemType.indexOf('video') !== -1 || itemType.indexOf('movie') !== -1 || attrs.hasVideo === true;
                    window.__amEnsureVideoStage(mk, isVideo);
                    var art = (attrs.artwork && attrs.artwork.url)
                      ? attrs.artwork.url
                          .replace('{w}', isVideo ? '1280' : '1000')
                          .replace('{h}', isVideo ? '720' : '1000')
                          .replace('{c}', 'cc')
                          .replace('{f}', 'jpg')
                      : '';
                    var playing = mk.isPlaying === true || mk.playbackState === 2;
                    var loading = mk.playbackState === 1 || mk.playbackState === 8;
                    var rawVid = document.getElementById('apple-music-video-player') || document.querySelector('video');
                    var vid = (rawVid && rawVid.tagName !== 'VIDEO' && rawVid.shadowRoot)
                      ? (rawVid.shadowRoot.querySelector('video') || rawVid)
                      : rawVid;
                    var aud = vid || document.querySelector('audio');
                    var frameReady = !!(isVideo && ((vid && !loading && (vid.readyState >= 2 || vid.videoWidth > 0) && !vid.ended) || (playing && !loading && mk.currentPlaybackTime > 0)));
                    var mkTime = (mk.currentPlaybackTime && mk.currentPlaybackTime > 0) ? mk.currentPlaybackTime : 0;
                    var audTime = (aud && typeof aud.currentTime === 'number' && !aud.ended && !aud.paused && aud.currentTime > 0) ? aud.currentTime : -1;
                    var rawTimeSec = (playing && audTime > 0 && (mkTime <= 0 || Math.abs(audTime - mkTime) < 1.5)) ? audTime : mkTime;
                    var pos = Math.round(rawTimeSec * 1000);
                    var dur = Math.round((mk.currentPlaybackDuration || 0) * 1000);
                    var pp = attrs.playParams || {};
                    var catalogId = pp.catalogId
                      ? String(pp.catalogId)
                      : ((itemType === 'songs' && item && item.id)
                        ? String(item.id)
                        : ((pp.id && /^\d+$/.test(String(pp.id))) ? String(pp.id) : ''));
                    var albumName = attrs.albumName ? String(attrs.albumName) : '';
                    var isAutoplay = false;
                    try {
                      var q = mk.queue;
                      var qPos = (q && typeof q.position === 'number') ? q.position : mk.nowPlayingItemIndex;
                      if (item && (item.isAutoPlay === true || item._isAutoplay === true)) isAutoplay = true;
                      else if (item && item.container && String(item.container.type || item.container.id || '').toLowerCase().indexOf('autoplay') !== -1) isAutoplay = true;
                      else if (q && Array.isArray(q.autoPlayItems) && item && q.autoPlayItems.indexOf(item) !== -1) isAutoplay = true;
                      else if (typeof qPos === 'number' && typeof window.__amUserQueueEnd === 'number' && window.__amUserQueueEnd > 0 && qPos >= window.__amUserQueueEnd && mk.autoplayEnabled === true) isAutoplay = true;
                    } catch (e) {}
                    // Radio en vivo: si el stream trae metadatos de la canción actual, mostrar canción, artista y portada
                    var liveName = attrs.name || '';
                    var liveArtist = attrs.artistName || '';
                    var liveArt = art;
                    try {
                      var lm = window.__amLiveMeta;
                      var curItemId = item && item.id ? String(item.id) : '';
                      var isStationItem = itemType === 'stations' || itemType === 'radio-episodes' || curItemId.indexOf('ra.') === 0 || window.__amLiveStationMode === true;
                      if (isStationItem && lm && lm.title && (!lm.itemId || lm.itemId === curItemId || curItemId.indexOf('ra.') === 0)) {
                        liveName = lm.title;
                        liveArtist = lm.artist || liveArtist;
                        if (lm.artworkUrl) liveArt = lm.artworkUrl;
                        if (lm.catalogId) catalogId = lm.catalogId;
                        if (lm.album) albumName = lm.album;
                      }
                    } catch (e) {}
                    window.__amLastPosMs = pos;
                    AMPlayerBridge.onState(
                      playing,
                      loading,
                      pos,
                      dur,
                      liveName,
                      liveArtist,
                      liveArt,
                      isVideo,
                      frameReady,
                      item && item.id ? String(item.id) : '',
                      item && item.type ? String(item.type) : '',
                      catalogId,
                      albumName,
                      isAutoplay,
                      Number(window.__amActiveEpoch || 0)
                    );
                    serializeQueue();
                  } catch (e) {}
                }
                window.__amEmitState = emit;

                // ---- Radio en vivo: canción actual (metadatos ID3 del stream HLS / evento de MusicKit) ----
                window.__amLiveMeta = null;
                window.__amLiveTmp = { title: '', artist: '', album: '', artwork: '' };
                function amDebug(msg) { try { AMPlayerBridge.onDebug(String(msg)); } catch (e) {} }
                function setLiveMeta(title, artist, album, directArtwork) {
                  title = String(title || '').trim();
                  artist = String(artist || '').trim();
                  directArtwork = String(directArtwork || '').trim();
                  if (!title) return;
                  var cur = mk.nowPlayingItem;
                  var itemId = cur && cur.id ? String(cur.id) : String(window.__amLiveStationId || '');
                  var prev = window.__amLiveMeta;
                  if (prev && prev.itemId === itemId && prev.title === title && prev.artist === artist && (!directArtwork || prev.artworkUrl)) return;
                  var meta = {
                    itemId: itemId,
                    title: title,
                    artist: artist,
                    album: String(album || ''),
                    artworkUrl: directArtwork || '',
                    catalogId: (prev && prev.title === title) ? (prev.catalogId || '') : ''
                  };
                  window.__amLiveMeta = meta;
                  amDebug('live-meta: ' + title + ' | ' + artist + ' | station=' + itemId);
                  emit();
                  // La portada y el id de catálogo se buscan en Apple Music con título + artista
                  try {
                    var sf = mk.storefrontId || mk.storefrontCountryCode || 'us';
                    mk.api.music('/v1/catalog/' + sf + '/search', { term: title + ' ' + artist, types: 'songs', limit: 1 })
                      .then(function(res) {
                        try {
                          var d = res && res.data ? res.data : res;
                          var songs = d && d.results && d.results.songs && d.results.songs.data;
                          var s = songs && songs[0];
                          if (s && window.__amLiveMeta === meta) {
                            var a = s.attributes && s.attributes.artwork && s.attributes.artwork.url;
                            if (a) meta.artworkUrl = a.replace('{w}', '1000').replace('{h}', '1000').replace('{c}', 'cc').replace('{f}', 'jpg');
                            meta.catalogId = String(s.id || '');
                            if (!meta.album && s.attributes) meta.album = s.attributes.albumName || '';
                            amDebug('live-meta artwork resolved id=' + meta.catalogId);
                            emit();
                          }
                        } catch (e) {}
                      })
                      .catch(function(err) { amDebug('live-meta search failed: ' + err); });
                  } catch (e) {}
                }
                function readId3Cue(cue) {
                  try {
                    var v = cue.value || {};
                    var key = String(v.key || '').toUpperCase();
                    var info = String(v.info || '').toLowerCase();
                    var data = v.data;
                    amDebug('id3 key=' + key + ' info=' + info + ' data=' + (typeof data === 'string' ? data.substring(0, 80) : typeof data));
                    if (typeof data !== 'string') return;
                    var tmp = window.__amLiveTmp;
                    if (key === 'TIT2' || key === 'TITLE') tmp.title = data;
                    else if (key === 'TPE1' || key === 'ARTIST') tmp.artist = data;
                    else if (key === 'TALB' || key === 'ALBUM') tmp.album = data;
                    else if (key === 'WXXX' && data.indexOf('http') === 0) tmp.artwork = data;
                    else if (key === 'TXXX' || key === 'PRIV') {
                      if (info === 'title' || info === 'song' || info === 'songname') tmp.title = data;
                      else if (info === 'artist' || info === 'artistname') tmp.artist = data;
                      else if (info === 'album') tmp.album = data;
                    }
                  } catch (e) {}
                }
                function hookMetadataTracks() {
                  try {
                    var els = document.querySelectorAll('audio,video');
                    for (var n = 0; n < els.length; n++) {
                      var tts = els[n].textTracks;
                      if (!tts) continue;
                      for (var i = 0; i < tts.length; i++) {
                        var tt = tts[i];
                        if (tt.kind === 'metadata' && !tt.__amHook) {
                          tt.__amHook = true;
                          try { if (tt.mode === 'disabled') tt.mode = 'hidden'; } catch (e) {}
                          tt.addEventListener('cuechange', function() {
                            try {
                              var ac = this.activeCues;
                              if (ac) for (var j = 0; j < ac.length; j++) readId3Cue(ac[j]);
                              var t = window.__amLiveTmp;
                              if (t.title) setLiveMeta(t.title, t.artist, t.album, t.artwork);
                            } catch (e) {}
                          });
                          amDebug('hooked metadata text track');
                        }
                      }
                    }
                  } catch (e) {}
                }
                setInterval(hookMetadataTracks, 1500);
                try {
                  mk.addEventListener('metadataDidChange', function(e) {
                    try {
                      var raw = '';
                      try { raw = JSON.stringify(e); } catch (er) { raw = String(e); }
                      amDebug('metadataDidChange ' + String(raw).substring(0, 300));
                      var t = e && (e.title || e.name || e.songName);
                      var a = e && (e.artistName || e.artist);
                      if (t) setLiveMeta(String(t), String(a || ''), String((e && e.albumName) || ''));
                    } catch (err) {}
                  });
                } catch (e) {}

                try {
                  mk.addEventListener('playbackStateDidChange', emit);
                  mk.addEventListener('mediaItemDidChange', emit);
                  mk.addEventListener('playbackTimeDidChange', emit);
                  mk.addEventListener('queueItemsDidChange', serializeQueue);
                  mk.addEventListener('queuePositionDidChange', serializeQueue);
                  mk.addEventListener('autoplayEnabledDidChange', serializeQueue);
                  mk.addEventListener('repeatModeDidChange', serializeQueue);
                  mk.addEventListener('shuffleModeDidChange', serializeQueue);
                } catch (e) {}
                setInterval(emit, 350);
                setInterval(function() {
                  try {
                    if (window.__amSwitchingQueue) return;
                    if (!(mk.isPlaying === true || mk.playbackState === 2)) return;
                    var rawVid = document.getElementById('apple-music-video-player') || document.querySelector('video');
                    var vid = (rawVid && rawVid.tagName !== 'VIDEO' && rawVid.shadowRoot)
                      ? (rawVid.shadowRoot.querySelector('video') || rawVid)
                      : rawVid;
                    var aud = vid || document.querySelector('audio');
                    var mkTime = (mk.currentPlaybackTime && mk.currentPlaybackTime > 0) ? mk.currentPlaybackTime : 0;
                    var audTime = (aud && typeof aud.currentTime === 'number' && !aud.ended && !aud.paused && aud.currentTime > 0) ? aud.currentTime : -1;
                    var tSec = (audTime > 0 && (mkTime <= 0 || Math.abs(audTime - mkTime) < 1.5)) ? audTime : mkTime;
                    var pos = Math.round(tSec * 1000);
                    if (pos !== window.__amLastPosMs) {
                      window.__amLastPosMs = pos;
                      var dur = Math.round((mk.currentPlaybackDuration || 0) * 1000);
                      AMPlayerBridge.onTime(pos, dur);
                    }
                  } catch (e) {}
                }, 100);
                AMPlayerBridge.onReady();
              }
              init();
            })();
        """.trimIndent()
    }
}
