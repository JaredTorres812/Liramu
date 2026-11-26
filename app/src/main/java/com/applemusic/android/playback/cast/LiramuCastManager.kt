package com.applemusic.android.playback.cast

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.applemusic.android.auth.AppleMusicCredentials
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.LyricLine
import com.applemusic.android.data.model.LyricWord
import com.applemusic.android.data.model.SongLyrics
import com.applemusic.android.playback.PlaybackUiState
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class CastDeviceUi(
    val id: String,
    val name: String,
    val description: String?,
    val isSelected: Boolean,
    val isConnecting: Boolean
)

enum class CastPlaybackMode {
    /** Sin sesión de Cast. */
    NONE,
    /** Conectado; esperando a que el receptor diga si puede reproducir. */
    CONNECTING,
    /** La TV reproduce el audio (MusicKit JS) y el teléfono queda en silencio como control remoto. */
    TV_AUDIO,
    /** La TV solo muestra portada/letra; el audio sigue saliendo del teléfono. */
    DISPLAY_ONLY
}

data class CastUiState(
    /** `false` si el dispositivo no tiene Google Play Services / Cast. */
    val available: Boolean = false,
    /** `true` si hay receptor propio de Liramu (portada animada + letra). */
    val customReceiver: Boolean = false,
    val devices: List<CastDeviceUi> = emptyList(),
    val connectedDeviceName: String? = null,
    val mode: CastPlaybackMode = CastPlaybackMode.NONE
) {
    val isConnected: Boolean get() = connectedDeviceName != null
}

/**
 * Chromecast para Liramu.
 *
 * Apple Music cifra el audio con DRM, así que no se puede "lanzar" el stream del teléfono. En su lugar:
 * 1. El teléfono sigue siendo la fuente de verdad (cola, siguiente, aleatorio, etc. funcionan igual).
 * 2. Al receptor (`docs/cast/index.html`) se le manda la canción actual, su portada (animada si existe),
 *    la letra con tiempos y la posición. El receptor reproduce la misma canción con MusicKit JS y se
 *    re-sincroniza con la posición del teléfono.
 * 3. Si la TV confirma que reproduce audio, el teléfono se silencia; si no puede (sin token, sin DRM),
 *    queda en modo "pantalla": la TV muestra portada + letra en seguimiento y el audio sale del teléfono.
 */
object LiramuCastManager {
    private const val TAG = "LiramuCast"
    const val NAMESPACE = "urn:x-cast:com.liramu.cast"
    private const val MAX_MESSAGE_CHARS = 60_000
    private const val PREFS = "liramu_cast"
    private const val KEY_MUTED_BY_CAST = "muted_by_cast"

    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var castContext: CastContext? = null
    private var router: MediaRouter? = null
    private var selector: MediaRouteSelector? = null
    private var discoveryClients = 0
    private var session: CastSession? = null

    private var lastPlayback: PlaybackUiState = PlaybackUiState()
    private var sentTrackKey: String? = null
    private var sentIsPlaying: Boolean? = null
    private var trackJob: Job? = null
    private var bindScope: CoroutineScope? = null
    private var bindJobs: List<Job> = emptyList()
    private var credentialsProvider: () -> AppleMusicCredentials = { AppleMusicCredentials() }
    private var lyricsLoader: suspend (HomeItem) -> SongLyrics? = { null }
    private var motionLoader: suspend (HomeItem) -> String? = { null }

    /** Debe llamarse en el hilo principal. Seguro de llamar varias veces. */
    fun init(context: Context) {
        if (castContext != null) return
        val app = context.applicationContext
        appContext = app
        restoreVolumeIfLeftMuted(app)
        val ctx = runCatching { CastContext.getSharedInstance(app) }
            .onFailure { Log.w(TAG, "Cast no disponible: ${it.message}") }
            .getOrNull() ?: return
        castContext = ctx
        router = runCatching { MediaRouter.getInstance(app) }.getOrNull()
        selector = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(LiramuCastOptionsProvider.receiverAppId()))
            .build()
        ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        _state.update {
            it.copy(available = true, customReceiver = LiramuCastOptionsProvider.hasCustomReceiver())
        }
        // Sesión que sobrevivió a un reinicio de la app
        ctx.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { onSessionReady(it) }
        refreshDevices()
    }

    /**
     * Conecta el gestor con el reproductor. [scope] debe usar el hilo principal (p. ej. `viewModelScope`).
     */
    fun bind(
        scope: CoroutineScope,
        playback: StateFlow<PlaybackUiState>,
        credentials: () -> AppleMusicCredentials,
        loadLyrics: suspend (HomeItem) -> SongLyrics?,
        loadMotionArtwork: suspend (HomeItem) -> String?
    ) {
        bindJobs.forEach { it.cancel() }
        bindScope = scope
        credentialsProvider = credentials
        lyricsLoader = loadLyrics
        motionLoader = loadMotionArtwork
        bindJobs = listOf(scope.launch {
            playback.collect { pb ->
                lastPlayback = pb
                if (session != null) onPlaybackChanged(pb)
            }
        }, scope.launch {
            // Latido de posición: el receptor corrige su reloj / su reproducción con esto.
            while (isActive) {
                delay(1_000L)
                if (session != null && sentTrackKey != null) sendSync(lastPlayback)
            }
        })
    }

    // ---------------------------------------------------------------- descubrimiento de dispositivos

    fun startDiscovery() {
        val r = router ?: return
        val sel = selector ?: return
        discoveryClients++
        if (discoveryClients == 1) {
            r.addCallback(sel, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        }
        refreshDevices()
    }

    fun stopDiscovery() {
        val r = router ?: return
        if (discoveryClients <= 0) return
        discoveryClients--
        if (discoveryClients == 0) {
            r.removeCallback(routerCallback)
        }
    }

    fun selectDevice(id: String) {
        val r = router ?: return
        val route = r.routes.firstOrNull { it.id == id } ?: return
        runCatching { r.selectRoute(route) }.onFailure { Log.w(TAG, "selectRoute: ${it.message}") }
        refreshDevices()
    }

    fun disconnect() {
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
        runCatching { router?.unselect(MediaRouter.UNSELECT_REASON_STOPPED) }
        refreshDevices()
    }

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshDevices()
    }

    private fun refreshDevices() {
        val r = router ?: return
        val sel = selector ?: return
        val devices = r.routes
            .filter { !it.isDefault && it.isEnabled && it.matchesSelector(sel) }
            .map {
                CastDeviceUi(
                    id = it.id,
                    name = it.name.toString(),
                    description = it.description?.toString()?.takeIf { d -> d.isNotBlank() },
                    isSelected = it.isSelected,
                    isConnecting = it.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                )
            }
            .sortedWith(compareByDescending<CastDeviceUi> { it.isSelected }.thenBy { it.name.lowercase() })
        _state.update { it.copy(devices = devices) }
    }

    // ---------------------------------------------------------------- sesión

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _state.update { it.copy(mode = CastPlaybackMode.CONNECTING) }
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) = onSessionReady(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = onSessionGone()
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) = onSessionGone()
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onSessionReady(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = onSessionGone()
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private fun onSessionReady(s: CastSession) {
        session = s
        sentTrackKey = null
        sentIsPlaying = null
        val deviceName = s.castDevice?.friendlyName ?: "Chromecast"
        if (LiramuCastOptionsProvider.hasCustomReceiver()) {
            runCatching {
                s.setMessageReceivedCallbacks(NAMESPACE) { _, _, message -> onReceiverMessage(message) }
            }.onFailure { Log.w(TAG, "No se pudo abrir el canal: ${it.message}") }
            _state.update { it.copy(connectedDeviceName = deviceName, mode = CastPlaybackMode.CONNECTING) }
            sendHello()
        } else {
            _state.update { it.copy(connectedDeviceName = deviceName, mode = CastPlaybackMode.DISPLAY_ONLY) }
        }
        onPlaybackChanged(lastPlayback)
        refreshDevices()
    }

    private fun onSessionGone() {
        runCatching { session?.removeMessageReceivedCallbacks(NAMESPACE) }
        session = null
        sentTrackKey = null
        sentIsPlaying = null
        trackJob?.cancel()
        setPhoneMuted(false)
        _state.update { it.copy(connectedDeviceName = null, mode = CastPlaybackMode.NONE) }
        refreshDevices()
    }

    private fun onReceiverMessage(raw: String) {
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (msg.optString("type")) {
            "status" -> {
                val tvAudio = msg.optBoolean("audio", false)
                setPhoneMuted(tvAudio && session != null)
                _state.update {
                    it.copy(mode = if (tvAudio) CastPlaybackMode.TV_AUDIO else CastPlaybackMode.DISPLAY_ONLY)
                }
            }
            // El receptor se reinició o se perdió el estado: reenviar todo.
            "hello" -> {
                sentTrackKey = null
                sentIsPlaying = null
                sendHello()
                onPlaybackChanged(lastPlayback)
            }
        }
    }

    // ---------------------------------------------------------------- envío de estado

    private fun onPlaybackChanged(pb: PlaybackUiState) {
        val item = pb.currentItem ?: return
        val key = "${item.id}|${item.catalogId}|${item.title}"
        if (key != sentTrackKey) {
            sentTrackKey = key
            sentIsPlaying = pb.isPlaying
            sendTrack(item, pb)
        } else if (pb.isPlaying != sentIsPlaying) {
            sentIsPlaying = pb.isPlaying
            sendSync(pb)
        }
    }

    private fun sendTrack(item: HomeItem, pb: PlaybackUiState) {
        val s = session ?: return
        val artwork = item.artworkUrl?.let(::highResArtwork)
        if (!LiramuCastOptionsProvider.hasCustomReceiver()) {
            loadOnDefaultReceiver(s, item, artwork)
            return
        }
        // 1) Datos básicos al instante (portada fija) para que la TV cambie sin esperar a la red.
        send(trackJson(item, pb, artwork, motionUrl = item.motionArtworkUrl, lyrics = null))
        // 2) Letra + portada animada en cuanto estén.
        trackJob?.cancel()
        val scope = bindScope ?: return
        val expectedKey = sentTrackKey
        trackJob = scope.launch {
            val isMusicVideo = item.type.contains("video", ignoreCase = true)
            val isStation = item.type.contains("station", ignoreCase = true) || item.id.startsWith("ra.")
            val motion = if (isMusicVideo || isStation) null else
                item.motionArtworkUrl ?: runCatching { motionLoader(item) }.getOrNull()
            val lyrics = if (isMusicVideo || isStation) null else runCatching { lyricsLoader(item) }.getOrNull()
            if (sentTrackKey != expectedKey || session == null) return@launch
            if (motion != null || lyrics != null) {
                send(trackJson(item, lastPlayback, artwork, motion, lyrics))
            }
        }
    }

    private fun sendHello() {
        val creds = credentialsProvider()
        // Solo el token de desarrollador propio (.p8) funciona fuera de music.apple.com;
        // el token web (AMPWebPlay) está atado a ese dominio.
        val hasNative = creds.developerToken.isNotBlank() && creds.musicUserToken.isNotBlank()
        val json = JSONObject()
            .put("type", "hello")
            .put("storefront", creds.storefront)
            .put("developerToken", if (hasNative) creds.developerToken else "")
            .put("musicUserToken", if (hasNative) creds.musicUserToken else "")
        send(json.toString())
    }

    private fun sendSync(pb: PlaybackUiState) {
        if (!LiramuCastOptionsProvider.hasCustomReceiver()) return
        val json = JSONObject()
            .put("type", "sync")
            .put("positionMs", estimatedPosition(pb))
            .put("durationMs", pb.durationMs)
            .put("isPlaying", pb.isPlaying)
        send(json.toString())
    }

    private fun estimatedPosition(pb: PlaybackUiState): Long {
        if (!pb.isPlaying || pb.positionUpdatedAtElapsedMs <= 0L) return pb.positionMs
        val since = (SystemClock.elapsedRealtime() - pb.positionUpdatedAtElapsedMs).coerceIn(0L, 2_000L)
        val pos = pb.positionMs + since
        return if (pb.durationMs > 0L) pos.coerceAtMost(pb.durationMs) else pos
    }

    private fun send(message: String) {
        val s = session ?: return
        if (!LiramuCastOptionsProvider.hasCustomReceiver()) return
        runCatching { s.sendMessage(NAMESPACE, message) }
            .onFailure { Log.w(TAG, "sendMessage: ${it.message}") }
    }

    private fun trackJson(
        item: HomeItem,
        pb: PlaybackUiState,
        artwork: String?,
        motionUrl: String?,
        lyrics: SongLyrics?
    ): String {
        fun build(withWords: Boolean): String {
            val track = JSONObject()
                .put("id", item.id)
                .put("catalogId", item.catalogId ?: item.id)
                .put("type", item.type)
                .put("title", item.title)
                .put("artist", item.subtitle)
                .put("artworkUrl", artwork ?: "")
                .put("motionUrl", motionUrl ?: "")
                .put("bgColor", item.bgColorHex ?: "")
            val json = JSONObject()
                .put("type", "load")
                .put("track", track)
                .put("positionMs", estimatedPosition(pb))
                .put("durationMs", pb.durationMs)
                .put("isPlaying", pb.isPlaying)
            if (lyrics != null && lyrics.lines.isNotEmpty()) {
                json.put("lyrics", lyricsJson(lyrics, withWords))
            }
            return json.toString()
        }
        val full = build(withWords = true)
        // Los mensajes de Cast tienen límite (~64 KB): si no cabe, se manda sin tiempos por palabra.
        return if (full.length <= MAX_MESSAGE_CHARS) full else build(withWords = false)
    }

    private fun lyricsJson(lyrics: SongLyrics, withWords: Boolean): JSONObject {
        val lines = JSONArray()
        lyrics.lines.forEach { line ->
            val o = JSONObject()
                .put("s", line.startMs)
                .put("e", line.endMs)
                .put("t", line.text)
            if (line.isSecondaryAgent) o.put("sec", true)
            if (withWords && hasWordTiming(line.words, line)) o.put("w", wordsJson(line.words))
            line.backgroundText?.takeIf { it.isNotBlank() }?.let { bg ->
                o.put("bg", bg)
                if (withWords && hasWordTiming(line.backgroundWords, line)) o.put("bw", wordsJson(line.backgroundWords))
            }
            line.translation?.takeIf { it.isNotBlank() }?.let { o.put("tr", it) }
            lines.put(o)
        }
        return JSONObject()
            .put("synced", lyrics.isSynced)
            .put("lines", lines)
            .put("songwriter", lyrics.songwriterCredit ?: "")
    }

    private fun wordsJson(words: List<LyricWord>): JSONArray {
        val arr = JSONArray()
        words.forEach { w -> arr.put(JSONArray().put(w.startMs).put(w.endMs).put(w.text)) }
        return arr
    }

    /** Igual que en el reproductor: una sola "palabra" que abarca la línea entera = letra sincronizada por línea. */
    private fun hasWordTiming(words: List<LyricWord>, line: LyricLine): Boolean {
        if (words.isEmpty()) return false
        if (words.size > 1) return true
        val w = words[0]
        val spansWholeLine = w.startMs <= line.startMs + 50L && w.endMs >= line.endMs - 50L
        return !(spansWholeLine && w.text.trim().contains(' '))
    }

    /** Sin receptor propio: la TV muestra la portada con título/artista (Default Media Receiver). */
    private fun loadOnDefaultReceiver(s: CastSession, item: HomeItem, artwork: String?) {
        val client = s.remoteMediaClient ?: return
        val url = artwork ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_PHOTO).apply {
            putString(MediaMetadata.KEY_TITLE, item.title)
            putString(MediaMetadata.KEY_SUBTITLE, item.subtitle)
            addImage(WebImage(Uri.parse(url)))
        }
        val info = MediaInfo.Builder(url)
            .setContentType("image/jpeg")
            .setStreamType(MediaInfo.STREAM_TYPE_NONE)
            .setMetadata(metadata)
            .build()
        runCatching {
            client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
        }.onFailure { Log.w(TAG, "load default receiver: ${it.message}") }
    }

    private fun highResArtwork(url: String): String {
        val expanded = url
            .replace("{w}", "1200")
            .replace("{h}", "1200")
            .replace("{c}", "bb")
            .replace("{f}", "jpg")
        return expanded.replace(Regex("/\\d+x\\d+([a-zA-Z]*)\\."), "/1200x1200$1.")
    }

    // ---------------------------------------------------------------- silencio del teléfono

    private var phoneMuted = false

    /**
     * Cuando la TV reproduce el audio, el teléfono sigue llevando la cola pero en silencio (el SDK de
     * Apple Music no expone volumen propio). Se guarda una marca para restaurarlo aunque la app se cierre.
     */
    private fun setPhoneMuted(muted: Boolean) {
        val ctx = appContext ?: return
        if (muted == phoneMuted) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
        }.onFailure { Log.w(TAG, "mute: ${it.message}") }
        phoneMuted = muted
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_MUTED_BY_CAST, muted)
            .apply()
    }

    private fun restoreVolumeIfLeftMuted(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_MUTED_BY_CAST, false)) return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        runCatching { am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
        prefs.edit().putBoolean(KEY_MUTED_BY_CAST, false).apply()
    }
}
