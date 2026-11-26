package com.applemusic.android.ui.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Translate
import com.applemusic.android.ui.theme.LocalAppStrings
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.ItemAccountStatus
import com.applemusic.android.data.model.SongLyrics
import com.applemusic.android.playback.AtmosPreferenceMode
import com.applemusic.android.playback.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class AudioOutputKind(val fallbackLabel: String, val icon: ImageVector) {
    PHONE_SPEAKER("Este teléfono", Icons.Rounded.PhoneAndroid),
    BLUETOOTH_HEADPHONES("Audífonos Bluetooth", Icons.Rounded.Bluetooth),
    WIRED_HEADPHONES("Audífonos", Icons.Rounded.Headset),
    EXTERNAL_SPEAKER("Bocina externa", Icons.Rounded.Speaker)
}

private data class AudioRouteOption(
    val id: String,
    val name: String,
    val kind: AudioOutputKind,
    val isActive: Boolean,
    val route2Info: Any? = null,
    val audioDeviceId: Int = -1
)

private data class ActiveAudioState(
    val kind: AudioOutputKind,
    val deviceName: String?,
    val routes: List<AudioRouteOption>,
    val volumePercent: Int
)

private fun parseArtistNames(rawSubtitle: String): List<String> {
    if (rawSubtitle.isBlank()) return emptyList()
    // Limpiar posibles separadores secundarios como " · Álbum" o " — ..."
    val primary = rawSubtitle.substringBefore(" · ").trim()
    return primary
        .split(Regex("""\s*(?:,|&|feat\.?|ft\.?|con)\s+""", RegexOption.IGNORE_CASE))
        .map { it.trim().trimEnd(')', ']').trimStart('(', '[') }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

@Composable
private fun rememberAudioOutputController(): Pair<ActiveAudioState, Pair<() -> Unit, (AudioRouteOption) -> Unit>> {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val mediaRouter2 = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { MediaRouter2.getInstance(context) }.getOrNull()
        } else null
    }

    fun readVolumePercent(): Int {
        val am = audioManager ?: return 100
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((cur.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    fun cleanName(raw: CharSequence?, fallback: String): String {
        val s = raw?.toString()?.trim().orEmpty()
        val model = Build.MODEL.orEmpty().trim()
        return if (s.isBlank() || s.equals(model, ignoreCase = true) || s.equals("Phone", ignoreCase = true)) {
            fallback
        } else s
    }

    fun routeKindFromMediaRoute2(type: Int): AudioOutputKind = when (type) {
        MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
        MediaRoute2Info.TYPE_BLE_HEADSET,
        MediaRoute2Info.TYPE_HEARING_AID -> AudioOutputKind.BLUETOOTH_HEADPHONES
        MediaRoute2Info.TYPE_WIRED_HEADPHONES,
        MediaRoute2Info.TYPE_WIRED_HEADSET,
        MediaRoute2Info.TYPE_USB_HEADSET -> AudioOutputKind.WIRED_HEADPHONES
        MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> AudioOutputKind.PHONE_SPEAKER
        else -> AudioOutputKind.EXTERNAL_SPEAKER
    }

    fun detect(): ActiveAudioState {
        val vol = readVolumePercent()

        // Preferir MediaRouter2 porque permite listar Y transferir audio real entre dispositivos.
        // MediaRoute2Info.getType() solo existe en Android 14+ (API 34) y RoutingController.getTransferableRoutes()
        // en Android 16+ (API 36): en versiones anteriores (p. ej. Galaxy Tab con Android 13) llamarlos crashea
        // con NoSuchMethodError, así que ahí se usa el respaldo de AudioManager.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && mediaRouter2 != null) {
            val sysController = runCatching { mediaRouter2.systemController }.getOrNull()
            val selectedIds = runCatching { sysController?.selectedRoutes?.map { it.id }?.toSet() }.getOrNull().orEmpty()
            val allRoutes = buildList {
                runCatching { sysController?.selectedRoutes }.getOrNull()?.let { addAll(it) }
                if (Build.VERSION.SDK_INT >= 36) {
                    runCatching { sysController?.transferableRoutes }.getOrNull()?.let { addAll(it) }
                }
                runCatching { mediaRouter2.routes }.getOrNull()?.let { addAll(it) }
            }.distinctBy { it.id }

            if (allRoutes.isNotEmpty()) {
                val options = allRoutes.map { route ->
                    val kind = routeKindFromMediaRoute2(route.type)
                    val isPhone = kind == AudioOutputKind.PHONE_SPEAKER
                    AudioRouteOption(
                        id = route.id,
                        name = if (isPhone) "Este teléfono" else cleanName(route.name, kind.fallbackLabel),
                        kind = kind,
                        isActive = route.id in selectedIds,
                        route2Info = route
                    )
                }.sortedByDescending { it.isActive }

                val active = options.firstOrNull { it.isActive } ?: options.first()
                return ActiveAudioState(
                    kind = active.kind,
                    deviceName = if (active.kind == AudioOutputKind.PHONE_SPEAKER) null else active.name,
                    routes = options,
                    volumePercent = vol
                )
            }
        }

        // Respaldo con AudioManager
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        val btHeadset = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val wiredHeadset = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val externalSpeaker = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_HDMI
        }
        val builtIn = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val activeDev = btHeadset ?: wiredHeadset ?: externalSpeaker
        val activeKind = when {
            btHeadset != null -> AudioOutputKind.BLUETOOTH_HEADPHONES
            wiredHeadset != null -> AudioOutputKind.WIRED_HEADPHONES
            externalSpeaker != null -> AudioOutputKind.EXTERNAL_SPEAKER
            else -> AudioOutputKind.PHONE_SPEAKER
        }
        val options = buildList {
            btHeadset?.let {
                add(
                    AudioRouteOption(
                        id = "dev_${it.id}",
                        name = cleanName(it.productName, AudioOutputKind.BLUETOOTH_HEADPHONES.fallbackLabel),
                        kind = AudioOutputKind.BLUETOOTH_HEADPHONES,
                        isActive = activeDev?.id == it.id,
                        audioDeviceId = it.id
                    )
                )
            }
            wiredHeadset?.let {
                add(
                    AudioRouteOption(
                        id = "dev_${it.id}",
                        name = cleanName(it.productName, AudioOutputKind.WIRED_HEADPHONES.fallbackLabel),
                        kind = AudioOutputKind.WIRED_HEADPHONES,
                        isActive = activeDev?.id == it.id,
                        audioDeviceId = it.id
                    )
                )
            }
            externalSpeaker?.let {
                add(
                    AudioRouteOption(
                        id = "dev_${it.id}",
                        name = cleanName(it.productName, AudioOutputKind.EXTERNAL_SPEAKER.fallbackLabel),
                        kind = AudioOutputKind.EXTERNAL_SPEAKER,
                        isActive = activeDev?.id == it.id,
                        audioDeviceId = it.id
                    )
                )
            }
            add(
                AudioRouteOption(
                    id = "dev_${builtIn?.id ?: -1}",
                    name = "Este teléfono",
                    kind = AudioOutputKind.PHONE_SPEAKER,
                    isActive = activeKind == AudioOutputKind.PHONE_SPEAKER,
                    audioDeviceId = builtIn?.id ?: -1
                )
            )
        }
        return ActiveAudioState(
            kind = activeKind,
            deviceName = if (activeKind == AudioOutputKind.PHONE_SPEAKER) null else options.firstOrNull { it.isActive }?.name,
            routes = options,
            volumePercent = vol
        )
    }

    var state by remember { mutableStateOf(detect()) }
    val routerScope = rememberCoroutineScope()

    val refresh: () -> Unit = { state = detect() }

    val selectRoute: (AudioRouteOption) -> Unit = { option ->
        val wasAlreadyActive = option.isActive
        if (!wasAlreadyActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaRouter2 != null && option.route2Info is MediaRoute2Info) {
                runCatching { mediaRouter2.transferTo(option.route2Info) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null) {
                if (option.kind == AudioOutputKind.PHONE_SPEAKER) {
                    val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) runCatching { audioManager.setCommunicationDevice(speaker) }
                } else {
                    val target = audioManager.availableCommunicationDevices.firstOrNull { it.id == option.audioDeviceId }
                    if (target != null) {
                        runCatching { audioManager.setCommunicationDevice(target) }
                    } else {
                        runCatching { audioManager.clearCommunicationDevice() }
                    }
                }
            }
            // Verificar tras 260ms si Android permitió cambiar la salida A2DP; si el sistema lo bloquea, abrir el panel nativo de salida
            routerScope.launch {
                delay(260L)
                val updated = detect()
                state = updated
                val switched = updated.routes.any { it.id == option.id && it.isActive }
                if (!switched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.Panel.ACTION_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(audioManager) {
        if (audioManager == null) return@DisposableEffect onDispose {}
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                state = detect()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                state = detect()
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    return state to (refresh to selectRoute)
}

private enum class NowPlayingCenterMode {
    ARTWORK,
    LYRICS,
    QUEUE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedNowPlayingScreen(
    playback: PlaybackUiState,
    onVideoBoundsChanged: (Rect?) -> Unit,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenArtistByName: (String) -> Unit = {},
    resolveArtistByName: suspend (String) -> HomeItem? = { null },
    favoriteOverrides: Map<String, Boolean> = emptyMap(),
    loadItemStatus: suspend (HomeItem) -> ItemAccountStatus? = { null },
    onToggleFavorite: (HomeItem, Boolean?) -> Unit = { _, _ -> },
    loadLyrics: suspend (HomeItem) -> SongLyrics? = { null },
    loadMotionArtwork: suspend (HomeItem) -> String? = { null },
    loadAudioTraits: suspend (HomeItem) -> List<String> = { emptyList() },
    onSetAtmosMode: (AtmosPreferenceMode) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    onToggleAutoplay: () -> Unit = {},
    onCycleTransitionMode: () -> Unit = {},
    onSkipToQueueIndex: (Int) -> Unit = {},
    onOpenMoreMenu: (HomeItem) -> Unit = {},
    openLyricsNonce: Long = 0L,
    openQueueNonce: Long = 0L
) {
    val item = playback.currentItem ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actionCtrl = LocalMediaActionController.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val (audioState, audioActions) = rememberAudioOutputController()
    val (refreshAudioDevices, selectAudioRoute) = audioActions

    var draggingValue by remember { mutableFloatStateOf(-1f) }
    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    var extractedColor by remember { mutableStateOf<Color?>(null) }

    // Extraer el color dominante SIEMPRE que cambie la canción o su portada (incluso en modo Letras o Video)
    LaunchedEffect(item.id, item.artworkUrl) {
        val url = item.artworkUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            extractedColor = null
            return@LaunchedEffect
        }
        val dominant = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(64)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                (result as? SuccessResult)?.drawable?.let { extractVibrantDominantColor(it) }
            }.getOrNull()
        }
        extractedColor = dominant
    }

    var fetchedFavorite by remember(item.id, item.catalogId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(item.id, item.catalogId) {
        fetchedFavorite = loadItemStatus(item)?.isFavorite
    }
    val isFavorite = favoriteOverrides[item.id]
        ?: item.catalogId?.let { favoriteOverrides[it] }
        ?: fetchedFavorite
        ?: false
    val shuffleActive = playback.shuffleEnabled
    val repeatMode = playback.repeatMode

    // Los videos musicales no tienen letra, cola, favoritos ni "infinito" (igual que en Apple Music).
    val isVideoContent = playback.isVideo
    val isRadioStream = item.type.equals("stations", ignoreCase = true) ||
        item.type.equals("radio-episodes", ignoreCase = true) ||
        item.id.startsWith("ra.")
    val isLiveBroadcast = isRadioStream && playback.durationMs <= 0L

    var centerMode by remember { mutableStateOf(NowPlayingCenterMode.ARTWORK) }
    LaunchedEffect(openLyricsNonce) {
        if (openLyricsNonce > 0L && !isVideoContent) centerMode = NowPlayingCenterMode.LYRICS
    }
    LaunchedEffect(openQueueNonce) {
        if (openQueueNonce > 0L && !isVideoContent) centerMode = NowPlayingCenterMode.QUEUE
    }
    LaunchedEffect(isVideoContent) {
        if (isVideoContent && centerMode != NowPlayingCenterMode.ARTWORK) centerMode = NowPlayingCenterMode.ARTWORK
    }
    LaunchedEffect(centerMode) {
        if (centerMode != NowPlayingCenterMode.ARTWORK) {
            onVideoBoundsChanged(null)
        }
    }

    val lyricsActive = centerMode == NowPlayingCenterMode.LYRICS
    val queueActive = centerMode == NowPlayingCenterMode.QUEUE

    var motionCoverUrl by remember(item.id, item.catalogId, item.title) {
        mutableStateOf(if (isRadioStream) null else item.motionArtworkUrl)
    }
    LaunchedEffect(item.id, item.catalogId, item.title, item.motionArtworkUrl, playback.isVideo) {
        if (playback.isVideo) {
            motionCoverUrl = null
        } else if (motionCoverUrl.isNullOrBlank()) {
            motionCoverUrl = loadMotionArtwork(item)
        }
    }

    var songAudioTraits by remember(item.id, item.catalogId) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(item.id, item.catalogId) {
        songAudioTraits = loadAudioTraits(item)
    }
    val effectiveAudioBadge = remember(playback.audioVariantBadge, songAudioTraits, playback.atmosMode, playback.dolbyCapabilities) {
        when {
            !playback.audioVariantBadge.isNullOrBlank() -> playback.audioVariantBadge
            playback.atmosMode != AtmosPreferenceMode.OFF &&
                (playback.dolbyCapabilities.isDolbyAtmosSupported || playback.atmosMode == AtmosPreferenceMode.ALWAYS_ON) &&
                songAudioTraits.any { it.contains("Atmos", ignoreCase = true) || it.contains("espacial", ignoreCase = true) } ->
                "Dolby Atmos"
            else -> null
        }
    }

    var lyricsData by remember(item.id, item.catalogId, item.title, item.subtitle) { mutableStateOf<SongLyrics?>(null) }
    var lyricsLoading by remember(item.id, item.catalogId, item.title, item.subtitle) { mutableStateOf(false) }
    var lyricsAttempted by remember(item.id, item.catalogId, item.title, item.subtitle) { mutableStateOf(false) }
    LaunchedEffect(item.id, item.catalogId, item.title, item.subtitle, lyricsActive) {
        if (lyricsActive && !lyricsAttempted) {
            lyricsLoading = true
            lyricsData = loadLyrics(item)
            lyricsAttempted = true
            lyricsLoading = false
        }
    }

    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var showArtistPickerSheet by remember { mutableStateOf(false) }

    val artistNames = remember(item.subtitle) { parseArtistNames(item.subtitle) }

    BackHandler(onBack = {
        if (centerMode != NowPlayingCenterMode.ARTWORK) {
            centerMode = NowPlayingCenterMode.ARTWORK
        } else {
            onDismiss()
        }
    })

    // Paleta 100% pintada con el color real de la portada (sin botones grises)
    val metadataAccent = parseHexColor(item.bgColorHex)?.takeIf { it.luminance() in 0.05f..0.90f }
    val baseCoverColor = extractedColor ?: metadataAccent ?: Color(0xFF4B6444)
    val deepTone = lerp(Color(0xFF101210), baseCoverColor, 0.44f)
    val targetTonalButtonBg = lerp(Color(0xFF121412), baseCoverColor, 0.58f)
    val targetDockBg = lerp(Color(0xFF0E100E), baseCoverColor, 0.48f)
    val targetSkipBg = lerp(baseCoverColor, Color.White, 0.44f)
    val targetSkipFg = lerp(baseCoverColor, Color.Black, 0.84f)
    val targetPlayBg = lerp(baseCoverColor, Color.White, 0.68f)
    val targetPlayFg = lerp(baseCoverColor, Color.Black, 0.86f)
    val targetTitleTint = lerp(baseCoverColor, Color.White, 0.62f)
    val targetSheetCardBg = lerp(Color(0xFF141612), baseCoverColor, 0.48f)

    val fallbackBg by animateColorAsState(deepTone, label = "np_fallback_bg")
    val tonalButtonBg by animateColorAsState(targetTonalButtonBg, label = "np_tonal_btn")
    val dockBg by animateColorAsState(targetDockBg, label = "np_dock_bg")
    val skipBg by animateColorAsState(targetSkipBg, label = "np_skip_bg")
    val skipFg by animateColorAsState(targetSkipFg, label = "np_skip_fg")
    val playBg by animateColorAsState(targetPlayBg, label = "np_play_bg")
    val playFg by animateColorAsState(targetPlayFg, label = "np_play_fg")
    val titleTint by animateColorAsState(targetTitleTint, label = "np_title_tint")
    val sheetCardBg by animateColorAsState(targetSheetCardBg, label = "np_sheet_card")

    // Animación estilo Pixel en Play/Pausa
    val playCornerRadius by animateDpAsState(
        targetValue = if (playback.isPlaying) 24.dp else 40.dp,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
        label = "pixel_play_corner"
    )
    val playButtonWidth by animateDpAsState(
        targetValue = if (playback.isPlaying) 98.dp else 78.dp,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 420f),
        label = "pixel_play_width"
    )

    val onPlayerPrimary = Color.White
    val onPlayerMuted = Color.White.copy(alpha = 0.80f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fallbackBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulated = 0f },
                    onDragEnd = {
                        if (dragAccumulated > 150f) onDismiss()
                        dragAccumulated = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) dragAccumulated += dragAmount
                    }
                )
            }
    ) {
        // Fondo con la portada real difuminada
        if (!item.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .blur(if (lyricsActive) 100.dp else 80.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = if (lyricsActive) 0.52f else 0.36f),
                        0.5f to Color.Black.copy(alpha = if (lyricsActive) 0.56f else 0.44f),
                        1f to Color.Black.copy(alpha = if (lyricsActive) 0.72f else 0.66f)
                    )
                )
        )

        // Diseño adaptable: una columna en teléfono vertical / tablet vertical (centrada y con ancho máximo),
        // dos columnas (portada | controles) en horizontal o pantallas anchas.
        val adaptive = rememberAdaptiveInfo()
        val twoPane = adaptive.useTwoPane
        val compactHeight = adaptive.isCompactHeight
        val mainControlHeight = if (compactHeight) 60.dp else 76.dp
        val dockHeight = if (compactHeight) 46.dp else 54.dp

        val headerSection: @Composable () -> Unit = {
            // 1. Barra superior: minimizar + "Reproduciendo ahora" + [Dispositivo | Cola]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = tonalButtonBg,
                        contentColor = onPlayerPrimary,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Minimizar",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Text(
                        text = when (centerMode) {
                            NowPlayingCenterMode.LYRICS -> "Letras"
                            NowPlayingCenterMode.QUEUE -> "Cola"
                            NowPlayingCenterMode.ARTWORK -> {
                                if (playback.isAutoplayItem) {
                                    "Reproduciendo ahora"
                                } else {
                                    playback.contextTitle?.takeIf { it.isNotBlank() } ?: "Reproduciendo ahora"
                                }
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Botones conectados: Dispositivo de salida (abre hoja Devices/Controls) + Cola
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val leftConnected = if (isVideoContent) RoundedCornerShape(22.dp) else RoundedCornerShape(
                        topStart = 22.dp,
                        bottomStart = 22.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    )
                    val rightConnected = RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 22.dp,
                        bottomEnd = 22.dp
                    )

                    Surface(
                        shape = leftConnected,
                        color = tonalButtonBg,
                        contentColor = onPlayerPrimary,
                        modifier = Modifier
                            .size(width = 48.dp, height = 42.dp)
                            .clip(leftConnected)
                            .clickable {
                                refreshAudioDevices()
                                showAudioOutputSheet = true
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = audioState.kind.icon,
                                contentDescription = audioState.deviceName ?: "Conectar dispositivo",
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    if (!isVideoContent) {
                        Surface(
                            shape = rightConnected,
                            color = if (queueActive) skipBg else tonalButtonBg,
                            contentColor = if (queueActive) skipFg else onPlayerPrimary,
                            modifier = Modifier
                                .size(width = 48.dp, height = 42.dp)
                                .clip(rightConnected)
                                .clickable {
                                    centerMode = if (queueActive) NowPlayingCenterMode.ARTWORK else NowPlayingCenterMode.QUEUE
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = "Cola de reproducción",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Portada grande / Video (gesto horizontal: izquierda = anterior, derecha = siguiente)
        var artworkDragPx by remember { mutableFloatStateOf(0f) }
        val artworkOffsetX by animateFloatAsState(
            targetValue = (artworkDragPx * 0.36f).coerceIn(-64f, 64f),
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
            label = "np_art_swipe_x"
        )
        val artworkSection: @Composable (Modifier) -> Unit = { artworkModifier ->
            Box(
                modifier = artworkModifier
                    .padding(vertical = 12.dp)
                    .pointerInput(item.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { artworkDragPx = 0f },
                            onHorizontalDrag = { _, dragAmount -> artworkDragPx += dragAmount },
                            onDragCancel = { artworkDragPx = 0f },
                            onDragEnd = {
                                when {
                                    artworkDragPx < -90f -> onPrevious()
                                    artworkDragPx > 90f -> onNext()
                                }
                                artworkDragPx = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (playback.isVideo) {
                    // Ajuste 16:9 explícito dentro de la caja disponible para teléfonos, foldables y tablets (vertical u horizontal).
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val availW = maxWidth
                        val availH = maxHeight
                        val fitW = minOf(availW, availH * (16f / 9f)).coerceAtLeast(120.dp)
                        val fitH = (fitW * (9f / 16f)).coerceAtLeast(68.dp)
                        Box(
                            modifier = Modifier
                                .size(width = fitW, height = fitH)
                                .graphicsLayer { translationX = artworkOffsetX }
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.Black)
                                .onGloballyPositioned { onVideoBoundsChanged(it.boundsInRoot()) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.24f))
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = tonalButtonBg,
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .graphicsLayer { translationX = artworkOffsetX }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = item.artworkUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (!motionCoverUrl.isNullOrBlank()) {
                                MotionArtworkVideo(
                                    videoUrl = motionCoverUrl,
                                    isActive = centerMode == NowPlayingCenterMode.ARTWORK,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        val infoSection: @Composable () -> Unit = {
            // 3. Título (pintado con el color de la portada) + Artista clicable (abre perfil o selector) + [Estrella | 3 puntos]
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                            color = titleTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.subtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = artistNames.isNotEmpty()) {
                                        if (artistNames.size == 1) {
                                            onOpenArtistByName(artistNames.first())
                                        } else if (artistNames.size > 1) {
                                            showArtistPickerSheet = true
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onPlayerMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (artistNames.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Ir al perfil del artista",
                                        tint = onPlayerMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Estrella (favoritos) + 3 puntos
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        val starShape = RoundedCornerShape(
                            topStart = 22.dp,
                            bottomStart = 22.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp
                        )
                        val moreShape = if (isVideoContent) RoundedCornerShape(22.dp) else RoundedCornerShape(
                            topStart = 8.dp,
                            bottomStart = 8.dp,
                            topEnd = 22.dp,
                            bottomEnd = 22.dp
                        )

                        if (!isVideoContent) {
                            Surface(
                                shape = starShape,
                                color = if (isFavorite) skipBg else tonalButtonBg,
                                contentColor = if (isFavorite) skipFg else onPlayerPrimary,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 44.dp)
                                    .clip(starShape)
                                    .clickable { onToggleFavorite(item, isFavorite) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = moreShape,
                            color = tonalButtonBg,
                            contentColor = onPlayerPrimary,
                            modifier = Modifier
                                .size(width = 46.dp, height = 44.dp)
                                .clip(moreShape)
                                .clickable {
                                    if (actionCtrl != null) {
                                        actionCtrl.onOpenMenu(
                                            MediaMenuRequest(
                                                item = item,
                                                accentColor = baseCoverColor
                                            )
                                        )
                                    } else {
                                        onOpenMoreMenu(item)
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Más opciones",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // 4. Barra de progreso ondulada (recta y fija con LIVE en radio en vivo; quieta al pausar) + tiempos
                val duration = playback.durationMs.coerceAtLeast(1L)
                val currentRatio = if (isLiveBroadcast) {
                    1f
                } else if (draggingValue >= 0f) {
                    draggingValue
                } else {
                    (playback.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WavyProgressScrubber(
                        progress = currentRatio,
                        isPlaying = playback.isPlaying && !isLiveBroadcast,
                        isWavy = !isLiveBroadcast,
                        enabled = !isLiveBroadcast && playback.durationMs > 0L,
                        activeColor = if (isLiveBroadcast) Color.White.copy(alpha = 0.55f) else skipBg,
                        inactiveColor = Color.White.copy(alpha = 0.24f),
                        onValueChange = { draggingValue = it },
                        onValueChangeFinished = {
                            if (draggingValue >= 0f) {
                                val targetMs = (draggingValue * duration).toLong()
                                draggingValue = -1f
                                onSeek(targetMs)
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isLiveBroadcast) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFA243C).copy(alpha = 0.22f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFA243C))
                                    )
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = formatPlayerTimeMs(if (draggingValue >= 0f) (draggingValue * duration).toLong() else playback.positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onPlayerMuted
                            )
                        }

                        val showDeviceChip = audioState.kind != AudioOutputKind.PHONE_SPEAKER && !audioState.deviceName.isNullOrBlank()
                        val showActiveAutoMix = !isLiveBroadcast && !isVideoContent && playback.autoMixEnabled && playback.isTransitioning
                        val sleepBadgeText = when {
                            playback.sleepTimerEndOfTrack -> "Fin"
                            playback.sleepTimerRemainingMs > 0L -> {
                                val sec = playback.sleepTimerRemainingMs / 1000L
                                "%d:%02d".format(sec / 60, sec % 60)
                            }
                            else -> null
                        }
                        if (showDeviceChip || effectiveAudioBadge != null || showActiveAutoMix || sleepBadgeText != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 8.dp)
                            ) {
                                if (showDeviceChip) {
                                    Surface(
                                        shape = CircleShape,
                                        color = tonalButtonBg,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clip(CircleShape)
                                            .clickable {
                                                refreshAudioDevices()
                                                showAudioOutputSheet = true
                                            }
                                    ) {
                                        Text(
                                            text = audioState.deviceName.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = onPlayerMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (showActiveAutoMix) {
                                    Surface(
                                        shape = CircleShape,
                                        color = skipBg,
                                        contentColor = skipFg
                                    ) {
                                        Text(
                                            text = "AutoMix",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (sleepBadgeText != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = tonalButtonBg,
                                        contentColor = titleTint
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bedtime,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = sleepBadgeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (effectiveAudioBadge != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (effectiveAudioBadge.contains("Atmos", ignoreCase = true)) {
                                            skipBg.copy(alpha = 0.24f)
                                        } else {
                                            tonalButtonBg
                                        },
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                refreshAudioDevices()
                                                showAudioOutputSheet = true
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        ) {
                                            if (effectiveAudioBadge.contains("Atmos", ignoreCase = true)) {
                                                DolbyDoubleDMark(height = 11.dp, tint = titleTint)
                                            }
                                            Text(
                                                text = effectiveAudioBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = titleTint,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (isLiveBroadcast) "" else formatPlayerTimeMs(playback.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onPlayerMuted
                        )
                    }
                }
            }
        }

        val controlsSection: @Composable () -> Unit = {
            // 5. Controles principales expresivos con animación Pixel en Anterior, Play/Pausa y Siguiente
            val prevInteraction = remember { MutableInteractionSource() }
            val prevPressed by prevInteraction.collectIsPressedAsState()
            val prevCorner by animateDpAsState(
                targetValue = if (prevPressed) 20.dp else 40.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "pixel_prev_corner"
            )
            val prevScale by animateFloatAsState(
                targetValue = if (prevPressed) 0.93f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "pixel_prev_scale"
            )
            val prevOffsetX by animateDpAsState(
                targetValue = if (prevPressed) (-4).dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "pixel_prev_offset"
            )

            val nextInteraction = remember { MutableInteractionSource() }
            val nextPressed by nextInteraction.collectIsPressedAsState()
            val nextCorner by animateDpAsState(
                targetValue = if (nextPressed) 20.dp else 40.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "pixel_next_corner"
            )
            val nextScale by animateFloatAsState(
                targetValue = if (nextPressed) 0.93f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "pixel_next_scale"
            )
            val nextOffsetX by animateDpAsState(
                targetValue = if (nextPressed) 4.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                label = "pixel_next_offset"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val prevShape = RoundedCornerShape(prevCorner)
                Surface(
                    shape = prevShape,
                    color = skipBg,
                    contentColor = skipFg,
                    modifier = Modifier
                        .weight(1f)
                        .height(mainControlHeight)
                        .scale(prevScale)
                        .clip(prevShape)
                        .clickable(
                            interactionSource = prevInteraction,
                            indication = null,
                            onClick = onPrevious
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Anterior",
                            modifier = Modifier
                                .size(30.dp)
                                .offset(x = prevOffsetX)
                        )
                    }
                }

                val playShape = RoundedCornerShape(playCornerRadius)
                Surface(
                    shape = playShape,
                    color = playBg,
                    contentColor = playFg,
                    modifier = Modifier
                        .width(playButtonWidth)
                        .height(mainControlHeight)
                        .clip(playShape)
                        .clickable(onClick = onTogglePlayPause)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playback.isBuffering) {
                            CircularProgressIndicator(
                                color = playFg,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            AnimatedContent(
                                targetState = playback.isPlaying,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = 0.65f, stiffness = 480f)) + fadeIn(tween(150)))
                                        .togetherWith(scaleOut(targetScale = 0.72f, animationSpec = tween(120)) + fadeOut(tween(120)))
                                },
                                label = "pixel_play_icon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Pausar" else "Reproducir",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }

                val nextShape = RoundedCornerShape(nextCorner)
                Surface(
                    shape = nextShape,
                    color = skipBg,
                    contentColor = skipFg,
                    modifier = Modifier
                        .weight(1f)
                        .height(mainControlHeight)
                        .scale(nextScale)
                        .clip(nextShape)
                        .clickable(
                            interactionSource = nextInteraction,
                            indication = null,
                            onClick = onNext
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Siguiente",
                            modifier = Modifier
                                .size(30.dp)
                                .offset(x = nextOffsetX)
                        )
                    }
                }
            }
        }

        val dockSection: @Composable () -> Unit = {
            // 6. Barra inferior conectada: Aleatorio | Letra | Infinito | Repetir
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val leftDockShape = RoundedCornerShape(
                    topStart = 24.dp,
                    bottomStart = 24.dp,
                    topEnd = 8.dp,
                    bottomEnd = 8.dp
                )
                val midDockShape = RoundedCornerShape(8.dp)
                val rightDockShape = RoundedCornerShape(
                    topStart = 8.dp,
                    bottomStart = 8.dp,
                    topEnd = 24.dp,
                    bottomEnd = 24.dp
                )

                Surface(
                    shape = leftDockShape,
                    color = if (shuffleActive) skipBg else dockBg,
                    contentColor = if (shuffleActive) skipFg else onPlayerPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(dockHeight)
                        .clip(leftDockShape)
                        .clickable(onClick = onToggleShuffle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Aleatorio",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (!isVideoContent && !isLiveBroadcast) {
                    Surface(
                        shape = midDockShape,
                        color = if (lyricsActive) skipBg else dockBg,
                        contentColor = if (lyricsActive) skipFg else onPlayerPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .height(dockHeight)
                            .clip(midDockShape)
                            .clickable {
                                centerMode = if (lyricsActive) NowPlayingCenterMode.ARTWORK else NowPlayingCenterMode.LYRICS
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = "Letra",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (!isVideoContent) {
                    Surface(
                        shape = midDockShape,
                        color = if (playback.autoplayEnabled) skipBg else dockBg,
                        contentColor = if (playback.autoplayEnabled) skipFg else onPlayerPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .height(dockHeight)
                            .clip(midDockShape)
                            .clickable(onClick = onToggleAutoplay)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AllInclusive,
                                contentDescription = "Infinito",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = rightDockShape,
                    color = if (repeatMode > 0) skipBg else dockBg,
                    contentColor = if (repeatMode > 0) skipFg else onPlayerPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(dockHeight)
                        .clip(rightDockShape)
                        .clickable(onClick = onCycleRepeatMode)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repetir",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Reproductor en una columna (cabecera, portada, info, controles y barra inferior)
        val singleColumnPlayer: @Composable (Modifier) -> Unit = { columnModifier ->
            Column(
                modifier = columnModifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                headerSection()
                artworkSection(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                infoSection()
                Spacer(Modifier.height(if (compactHeight) 10.dp else 14.dp))
                controlsSection()
                Spacer(Modifier.height(if (compactHeight) 10.dp else 16.dp))
                dockSection()
            }
        }

        // TABLET (vertical u horizontal) con Letras activas: reproductor a la izquierda y letra a la derecha.
        val tabletLyrics = adaptive.isTablet && lyricsActive

        if (tabletLyrics) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(
                        start = if (adaptive.isLandscape) 32.dp else 20.dp,
                        end = 8.dp,
                        top = 16.dp,
                        bottom = if (adaptive.isLandscape) 20.dp else 32.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(if (adaptive.isLandscape) 32.dp else 16.dp)
            ) {
                singleColumnPlayer(
                    Modifier
                        .weight(if (adaptive.isLandscape) 0.44f else 0.5f)
                        .fillMaxHeight()
                )
                FullScreenLyricsPage(
                    item = item,
                    playback = playback,
                    lyrics = lyricsData,
                    isLoading = lyricsLoading,
                    bgFallback = fallbackBg,
                    tonalBg = tonalButtonBg,
                    accentBg = skipBg,
                    accentFg = skipFg,
                    playBg = playBg,
                    playFg = playFg,
                    titleTint = titleTint,
                    onBack = { centerMode = NowPlayingCenterMode.ARTWORK },
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    embedded = true,
                    modifier = Modifier
                        .weight(if (adaptive.isLandscape) 0.56f else 0.5f)
                        .fillMaxHeight()
                )
            }
        } else if (twoPane) {
            // Horizontal (teléfono girado / tablet apaisada): portada a la izquierda, información y controles a la derecha.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(
                        horizontal = if (adaptive.isTablet) 40.dp else 20.dp,
                        vertical = if (compactHeight) 6.dp else 20.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (adaptive.isTablet) 48.dp else 24.dp)
            ) {
                // Tablet + video musical: el video ocupa más ancho de pantalla (no queda como un cuadrito).
                val tabletVideo = adaptive.isTablet && playback.isVideo
                artworkSection(
                    Modifier
                        .weight(if (tabletVideo) 1.6f else 1f)
                        .fillMaxHeight()
                )
                if (adaptive.isTablet) {
                    // TABLET horizontal: cabecera arriba y la información + controles CENTRADOS verticalmente.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .widthIn(max = AdaptivePlayerMaxWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        headerSection()
                        Spacer(Modifier.weight(1f))
                        infoSection()
                        Spacer(Modifier.height(18.dp))
                        controlsSection()
                        Spacer(Modifier.height(16.dp))
                        dockSection()
                        Spacer(Modifier.weight(1f))
                        // Compensa el alto de la cabecera para que el bloque quede centrado con la portada
                        Spacer(Modifier.height(42.dp))
                    }
                } else BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val paneHeight = maxHeight
                    // Si la ventana es muy baja, la columna de controles se desplaza en vez de colapsar.
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = AdaptivePlayerMaxWidth)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = paneHeight),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        headerSection()
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(if (compactHeight) 8.dp else 16.dp))
                            infoSection()
                            Spacer(Modifier.height(if (compactHeight) 10.dp else 18.dp))
                            controlsSection()
                            Spacer(Modifier.height(if (compactHeight) 10.dp else 16.dp))
                            dockSection()
                            Spacer(Modifier.height(if (compactHeight) 4.dp else 8.dp))
                        }
                    }
                }
            }
        } else {
            // Vertical: una columna. En tablets se centra con ancho máximo para que no se estire.
            Box(modifier = Modifier.fillMaxSize()) {
                singleColumnPlayer(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxHeight()
                        // Tablet vertical + video: más ancho para que el video se vea grande
                        .widthIn(max = if (adaptive.isTablet && playback.isVideo) 980.dp else AdaptivePlayerMaxWidth)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(
                            start = 22.dp,
                            end = 22.dp,
                            top = if (adaptive.isTablet) 24.dp else 10.dp,
                            bottom = if (adaptive.isTablet) 40.dp else 24.dp
                        )
                )
            }
        }

        // Hoja "Conectar dispositivo" (DEVICES | CONTROLS) igual que la captura, sin salir a otros menús
        if (showAudioOutputSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var activeSheetTab by remember { mutableIntStateOf(1) } // 0 = CONTROLS, 1 = DEVICES
            var sliderVol by remember(audioState.volumePercent) {
                mutableFloatStateOf(audioState.volumePercent / 100f)
            }

            ModalBottomSheet(
                onDismissRequest = { showAudioOutputSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF171815),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val appStringsAudio = LocalAppStrings.current
                    Text(
                        text = if (activeSheetTab == 1) appStringsAudio.connectDevice else appStringsAudio.volumeControls,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    if (activeSheetTab == 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = appStringsAudio.nearbyDevices,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = appStringsAudio.tapToConnect,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.68f)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF282A25),
                                contentColor = Color.White,
                                modifier = Modifier.size(42.dp)
                            ) {
                                IconButton(onClick = refreshAudioDevices) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar dispositivos")
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            audioState.routes.forEach { route ->
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = if (route.isActive) sheetCardBg else Color(0xFF232520),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(28.dp))
                                        .clickable {
                                            selectAudioRoute(route)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (route.isActive) lerp(sheetCardBg, Color.White, 0.16f) else Color(0xFF2F322B),
                                            contentColor = Color.White,
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = route.kind.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = route.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (route.isActive) titleTint else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (route.isActive) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = lerp(sheetCardBg, Color.White, 0.14f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = route.kind.icon,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = "Conectado",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (route.isActive) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "${audioState.volumePercent}%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Pestaña CONTROLS: solo control de volumen en vivo
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = sheetCardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Volumen multimedia",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${(sliderVol * 100f).roundToInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = titleTint
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            audioManager?.adjustStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                AudioManager.ADJUST_LOWER,
                                                0
                                            )
                                            refreshAudioDevices()
                                            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                                            val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                            sliderVol = (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                                            contentDescription = "Bajar volumen",
                                            tint = Color.White
                                        )
                                    }

                                    Slider(
                                        value = sliderVol,
                                        onValueChange = { ratio ->
                                            sliderVol = ratio
                                            val am = audioManager ?: return@Slider
                                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                            val target = (ratio * max).roundToInt().coerceIn(0, max)
                                            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                        },
                                        onValueChangeFinished = { refreshAudioDevices() },
                                        colors = SliderDefaults.colors(
                                            thumbColor = skipBg,
                                            activeTrackColor = skipBg,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            audioManager?.adjustStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                AudioManager.ADJUST_RAISE,
                                                0
                                            )
                                            refreshAudioDevices()
                                            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                                            val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                            sliderVol = (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = "Subir volumen",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = sheetCardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SurroundSound,
                                        contentDescription = null,
                                        tint = titleTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Dolby Atmos",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = playback.dolbyCapabilities.summaryLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (playback.dolbyCapabilities.isDolbyAtmosSupported) {
                                                titleTint
                                            } else {
                                                Color.White.copy(alpha = 0.65f)
                                            }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AtmosPreferenceMode.entries.forEach { modeOption ->
                                        val selected = playback.atmosMode == modeOption
                                        Surface(
                                            shape = CircleShape,
                                            color = if (selected) skipBg else Color(0xFF282A25),
                                            contentColor = if (selected) skipFg else Color.White,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(CircleShape)
                                                .clickable { onSetAtmosMode(modeOption) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = modeOption.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = if (playback.usingWebPlayback && playback.atmosMode != AtmosPreferenceMode.OFF) {
                                        "Esta canción suena con el respaldo web (sin Atmos). Atmos requiere el motor nativo con sesión iniciada."
                                    } else {
                                        "Atmos usa el motor nativo (E-AC-3 JOC) con respaldo web automático."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }

                    // Selector inferior: CONTROLS | DEVICES (igual que la captura)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (activeSheetTab == 0) skipBg else Color(0xFF1F211C),
                            contentColor = if (activeSheetTab == 0) skipFg else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(CircleShape)
                                .clickable { activeSheetTab = 0 }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speaker,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "CONTROLS",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (activeSheetTab == 1) skipBg else Color(0xFF1F211C),
                            contentColor = if (activeSheetTab == 1) skipFg else Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(CircleShape)
                                .clickable { activeSheetTab = 1 }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Devices,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "DEVICES",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selector de artista cuando la canción tiene 2 o más artistas
        if (showArtistPickerSheet && artistNames.size > 1) {
            val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showArtistPickerSheet = false },
                sheetState = pickerState,
                containerColor = Color(0xFF171815),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Ir al perfil del artista",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        artistNames.forEachIndexed { idx, name ->
                            val rowShape = segmentedRowShape(
                                isFirst = idx == 0,
                                isLast = idx == artistNames.lastIndex
                            )
                            Surface(
                                shape = rowShape,
                                color = sheetCardBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .clickable {
                                        showArtistPickerSheet = false
                                        onOpenArtistByName(name)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Foto de perfil del artista (se resuelve por nombre; mientras carga se ve el ícono)
                                    val artistPhotoUrl by produceState<String?>(initialValue = null, key1 = name) {
                                        value = runCatching { resolveArtistByName(name)?.artworkUrl }.getOrNull()
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = lerp(sheetCardBg, Color.White, 0.16f),
                                        contentColor = Color.White,
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                                            if (!artistPhotoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = artistPhotoUrl,
                                                    contentDescription = name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.72f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // Página independiente a pantalla completa para LETRAS (diseño original Material 3 Expressive)
        AnimatedVisibility(
            // En tablet la letra va al lado de la portada (ver tabletLyrics), no a pantalla completa.
            visible = lyricsActive && !adaptive.isTablet,
            enter = fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 10 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 10 }
        ) {
            FullScreenLyricsPage(
                item = item,
                playback = playback,
                lyrics = lyricsData,
                isLoading = lyricsLoading,
                bgFallback = fallbackBg,
                tonalBg = tonalButtonBg,
                accentBg = skipBg,
                accentFg = skipFg,
                playBg = playBg,
                playFg = playFg,
                titleTint = titleTint,
                onBack = { centerMode = NowPlayingCenterMode.ARTWORK },
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek
            )
        }

        // Página independiente a pantalla completa para COLA DE REPRODUCCIÓN
        AnimatedVisibility(
            visible = queueActive,
            enter = fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 10 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 10 }
        ) {
            FullScreenQueuePage(
                item = item,
                playback = playback,
                bgFallback = fallbackBg,
                tonalBg = tonalButtonBg,
                activeBg = skipBg,
                activeFg = skipFg,
                titleTint = titleTint,
                onBack = { centerMode = NowPlayingCenterMode.ARTWORK },
                onToggleAutoplay = onToggleAutoplay,
                onSkipToIndex = onSkipToQueueIndex,
                onOpenTrackMenu = { qItem ->
                    val asHome = HomeItem(
                        id = qItem.id,
                        type = qItem.type,
                        title = qItem.title,
                        subtitle = qItem.subtitle,
                        artworkUrl = qItem.artworkUrl,
                        catalogId = qItem.catalogId
                    )
                    if (actionCtrl != null) {
                        actionCtrl.onOpenMenu(MediaMenuRequest(item = asHome))
                    } else {
                        onOpenMoreMenu(asHome)
                    }
                }
            )
        }
    }
}

/**
 * Barra de progreso ondulada estilo Material 3 Expressive:
 * - Ondula suavemente mientras reproduce.
 * - Al pausar se aplana suavemente hasta quedar completamente recta y quieta.
 */
@Composable
private fun WavyProgressScrubber(
    progress: Float,
    isPlaying: Boolean,
    isWavy: Boolean = true,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val amplitudeFraction by animateFloatAsState(
        targetValue = if (isPlaying && isWavy) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "wave_amp"
    )
    val phase = if ((isPlaying && isWavy) || amplitudeFraction > 0.01f) {
        val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase_val"
        )
        animated
    } else {
        0f
    }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 0.99f)
                    latestOnValueChange(ratio)
                    latestOnValueChangeFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { latestOnValueChangeFinished() },
                    onDragCancel = { latestOnValueChangeFinished() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val ratio = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 0.99f)
                        latestOnValueChange(ratio)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val centerY = size.height / 2f
            val thumbRadius = 7.dp.toPx()
            val trackStroke = 3.5.dp.toPx()
            val startX = thumbRadius
            val endX = (size.width - thumbRadius).coerceAtLeast(startX + 1f)
            val thumbX = startX + (endX - startX) * clamped

            if (thumbX < endX) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(thumbX + thumbRadius * 0.6f, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round
                )
            }

            val activeWidth = (thumbX - startX).coerceAtLeast(0f)
            if (activeWidth > 2f) {
                val amplitude = 3.4.dp.toPx() * amplitudeFraction
                if (amplitude < 0.15f) {
                    drawLine(
                        color = activeColor,
                        start = Offset(startX, centerY),
                        end = Offset(thumbX, centerY),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                } else {
                    val wavelength = 24.dp.toPx()
                    val path = Path()
                    val step = 3f
                    var x = startX
                    var first = true
                    while (x <= thumbX) {
                        val y = centerY + sin(((x - startX) / wavelength) * (2f * PI).toFloat() - phase) * amplitude
                        if (first) {
                            path.moveTo(x, y)
                            first = false
                        } else {
                            path.lineTo(x, y)
                        }
                        x += step
                    }
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = trackStroke, cap = StrokeCap.Round)
                    )
                }
            }

            if (isWavy) {
                drawCircle(
                    color = activeColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, centerY)
                )
            }
        }
    }
}

private fun formatPlayerTimeMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}

/**
 * Página dedicada a pantalla completa para Letras (diseño original Material 3 Expressive):
 * - Fondo inmersivo con portada difuminada.
 * - Encabezado compacto con píldora de regreso + tarjeta mini de la pista actual.
 * - Letras sincronizadas a pantalla completa (toca cualquier verso para saltar).
 * - Cápsula flotante inferior con barra de progreso y controles compactos.
 */
@Composable
private fun FullScreenLyricsPage(
    item: HomeItem,
    playback: PlaybackUiState,
    lyrics: SongLyrics?,
    isLoading: Boolean,
    bgFallback: Color,
    tonalBg: Color,
    accentBg: Color,
    accentFg: Color,
    playBg: Color,
    playFg: Color,
    titleTint: Color,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    /** Tablet: se muestra como panel al lado de la portada (sin fondo, encabezado ni cápsula de controles). */
    embedded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val latestPlayback by rememberUpdatedState(playback)
    var livePositionMs by remember(item.id) {
        val initSince = if (playback.isPlaying && playback.positionUpdatedAtElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - playback.positionUpdatedAtElapsedMs).coerceIn(0L, 1200L)
        } else 0L
        mutableLongStateOf(playback.positionMs + initSince)
    }

    // Traducción de letras (nativa TTML x-translation o traducción automática al idioma de la app)
    val appStrings = LocalAppStrings.current
    val targetLangCode = appStrings.langCode
    var showTranslation by remember(item.id) { mutableStateOf(false) }
    var isTranslating by remember(item.id) { mutableStateOf(false) }
    var autoTranslations by remember(item.id, targetLangCode) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(item.id, lyrics, showTranslation, targetLangCode) {
        if (!showTranslation || lyrics == null || lyrics.lines.isEmpty()) return@LaunchedEffect
        val hasNativeTranslations = lyrics.lines.any { !it.translation.isNullOrBlank() }
        if (hasNativeTranslations || autoTranslations != null) return@LaunchedEffect
        isTranslating = true
        val translated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            LyricsTranslator.translateLines(
                songId = item.id,
                lines = lyrics.lines.map { it.text },
                targetLang = targetLangCode
            )
        }
        autoTranslations = translated
        isTranslating = false
    }

    // Reloj monotónico a 60 fps sin reinicios ni saltos hacia atrás (cero parpadeo)
    LaunchedEffect(item.id, playback.isPlaying, lyrics?.isSynced) {
        if (!playback.isPlaying || lyrics?.isSynced != true) {
            livePositionMs = latestPlayback.positionMs
            return@LaunchedEffect
        }
        var lastFrameMs = SystemClock.elapsedRealtime()
        while (true) {
            withFrameMillis {
                val now = SystemClock.elapsedRealtime()
                val dt = (now - lastFrameMs).coerceIn(0L, 64L)
                lastFrameMs = now

                val pb = latestPlayback
                val sinceReport = if (pb.positionUpdatedAtElapsedMs > 0L) {
                    (now - pb.positionUpdatedAtElapsedMs).coerceIn(0L, 1200L)
                } else 0L
                val reported = pb.positionMs + sinceReport
                val advanced = livePositionMs + dt
                val drift = reported - advanced

                val nextPos = when {
                    kotlin.math.abs(drift) > 400L -> reported
                    drift > 30L -> advanced + (drift / 6L)
                    drift < -120L -> advanced + (drift / 10L)
                    else -> advanced
                }
                livePositionMs = if (pb.durationMs > 0L) {
                    nextPos.coerceIn(0L, pb.durationMs)
                } else {
                    nextPos.coerceAtLeast(0L)
                }
            }
        }
    }

    val isRadioLiveTop = (item.type.equals("stations", ignoreCase = true) ||
        item.type.equals("radio-episodes", ignoreCase = true) ||
        item.id.startsWith("ra.")) && playback.durationMs <= 0L
    // Línea activa derivada del reloj: solo notifica cuando cambia de línea (no 60 veces por segundo).
    val activeLineIndexState = remember(lyrics, isRadioLiveTop) {
        derivedStateOf {
            val l = lyrics
            if (l == null || !l.isSynced || isRadioLiveTop || l.lines.isEmpty()) -1
            else findActiveLyricLine(l.lines, livePositionMs)
        }
    }
    val activeLineIndex by activeLineIndexState

    // "Navegando" = el usuario está arrastrando la lista. El auto-scroll NO cuenta (antes se bloqueaba a sí mismo 3,2 s).
    var isUserBrowsing by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var resetJob: Job? = null
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    resetJob?.cancel()
                    isUserBrowsing = true
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    resetJob?.cancel()
                    resetJob = launch {
                        delay(3200L)
                        isUserBrowsing = false
                    }
                }
            }
        }
    }

    // Auto-scroll suave estilo Apple Music: la línea activa se ancla arriba y el resto se desliza con ella.
    val anchorPx = with(LocalDensity.current) { 110.dp.toPx() }
    LaunchedEffect(activeLineIndex, isUserBrowsing) {
        if (activeLineIndex < 0 || lyrics?.isSynced != true || isUserBrowsing) return@LaunchedEffect
        val itemIndex = activeLineIndex + 1 // +1: el ítem 0 es siempre el de intro
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        if (info != null) {
            val delta = info.offset - anchorPx
            if (kotlin.math.abs(delta) > 2f) {
                listState.animateScrollBy(delta, tween(durationMillis = 560, easing = FastOutSlowInEasing))
            }
        } else {
            listState.animateScrollToItem(itemIndex, -anchorPx.toInt())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (embedded) Modifier else Modifier
                    .background(bgFallback)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            )
    ) {
        if (!embedded && !item.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .blur(100.dp)
            )
        }
        if (!embedded) Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.56f),
                        0.5f to Color.Black.copy(alpha = 0.62f),
                        1f to Color.Black.copy(alpha = 0.78f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = AdaptiveReadableMaxWidth)
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
        ) {
            // Encabezado con píldora de regreso + mini pista actual (en tablet el encabezado está en el panel del reproductor)
            if (!embedded) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = tonalBg,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver al reproductor",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = tonalBg,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ArtworkBox(
                            artworkUrl = item.artworkUrl,
                            fallbackColor = Color.White.copy(alpha = 0.14f),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = titleTint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.76f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Botón de traducción de letras (TTML nativo + traducción automática al idioma de la app)
                if (lyrics != null && lyrics.lines.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = if (showTranslation) accentBg else tonalBg,
                        contentColor = if (showTranslation) accentFg else Color.White,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { showTranslation = !showTranslation }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = if (showTranslation) accentFg else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = "Traducir letra",
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (embedded && lyrics != null && lyrics.lines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (showTranslation) accentBg else tonalBg,
                        contentColor = if (showTranslation) accentFg else Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showTranslation = !showTranslation }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = if (showTranslation) accentFg else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (showTranslation) appStrings.translation else appStrings.translate,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Contenido principal de letras a pantalla completa
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = titleTint)
                            Text(
                                text = appStrings.loadingLyrics,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }

                    lyrics == null || lyrics.lines.isEmpty() -> {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = tonalBg,
                                contentColor = titleTint,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lyrics,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = appStrings.lyricsUnavailable,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = titleTint,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = appStrings.lyricsUnavailableDesc,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        val isRadioLive = (item.type.equals("stations", ignoreCase = true) ||
                            item.type.equals("radio-episodes", ignoreCase = true) ||
                            item.id.startsWith("ra.")) && playback.durationMs <= 0L
                        val firstLineStartMs = lyrics.lines.firstOrNull()?.startMs ?: 0L
                        val synced = lyrics.isSynced && !isRadioLive
                        // Proveedores en lambda: solo los composables diminutos que los invocan se recomponen por fotograma.
                        val positionProvider: () -> Long = { livePositionMs }
                        val activeIndexProvider: () -> Int = { activeLineIndex }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 26.dp,
                                end = 26.dp,
                                top = 24.dp,
                                bottom = 260.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(26.dp)
                        ) {
                            // Ítem 0 fijo (puntos de intro o vacío): mantiene estables los índices de las líneas.
                            item(key = "intro_interlude") {
                                val showIntro by remember {
                                    derivedStateOf { synced && firstLineStartMs >= 4500L && livePositionMs < firstLineStartMs }
                                }
                                if (showIntro) {
                                    val introProgress = (livePositionMs.toFloat() / firstLineStartMs.coerceAtLeast(1L).toFloat())
                                        .coerceIn(0f, 1f)
                                    InstrumentalSyncDots(
                                        progress = introProgress,
                                        accent = Color.White,
                                        alignEnd = false
                                    )
                                } else {
                                    Spacer(Modifier.height(0.dp))
                                }
                            }

                            itemsIndexed(
                                items = lyrics.lines,
                                key = { idx, line -> "${idx}_${line.startMs}" }
                            ) { idx, line ->
                                val translatedLine = if (showTranslation) {
                                    line.translation?.takeIf { it.isNotBlank() }
                                        ?: autoTranslations?.getOrNull(idx)?.takeIf { it.isNotBlank() }
                                } else null
                                LyricLineItem(
                                    idx = idx,
                                    line = line,
                                    nextLine = lyrics.lines.getOrNull(idx + 1),
                                    synced = synced,
                                    positionProvider = positionProvider,
                                    activeIndexProvider = activeIndexProvider,
                                    isUserBrowsing = isUserBrowsing,
                                    translatedText = translatedLine,
                                    onSeekLine = { startMs ->
                                        isUserBrowsing = false
                                        onSeek(startMs)
                                    }
                                )
                            }

                            lyrics.songwriterCredit?.let { sw ->
                                item(key = "songwriter_credit") {
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "Composición: $sw",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.48f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cápsula flotante inferior de controles rápidos en la página de Letras (en tablet los controles están al lado)
            if (!embedded) Surface(
                shape = CircleShape,
                color = tonalBg,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatPlayerTimeMs(playback.positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = Color.White)
                        }
                        Surface(
                            shape = RoundedCornerShape(if (playback.isPlaying) 16.dp else 24.dp),
                            color = playBg,
                            contentColor = playFg,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(if (playback.isPlaying) 16.dp else 24.dp))
                                .clickable(onClick = onTogglePlayPause)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", tint = Color.White)
                        }
                    }
                    Text(
                        text = formatPlayerTimeMs(playback.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}


/** Traductor ligero con caché en memoria para letras sin `x-translation` nativo en el TTML. */
private object LyricsTranslator {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    fun translateLines(songId: String, lines: List<String>, targetLang: String): List<String>? {
        val key = "${songId}_$targetLang"
        cache[key]?.let { return it }
        if (lines.isEmpty()) return null

        val result = MutableList(lines.size) { "" }
        // Traducir en lotes pequeños para respetar límites de URL y conservar la alineación 1:1 por línea
        var start = 0
        while (start < lines.size) {
            val chunk = mutableListOf<String>()
            var chars = 0
            var idx = start
            while (idx < lines.size && chunk.size < 24 && chars + lines[idx].length < 1400) {
                val safe = lines[idx].replace('\n', ' ').trim()
                chunk += safe
                chars += safe.length + 1
                idx++
            }
            if (chunk.isEmpty()) {
                chunk += lines[start].replace('\n', ' ').take(1000)
                idx = start + 1
            }

            val joined = chunk.joinToString("\n")
            val translatedChunk = translateBatch(joined, targetLang)
            if (translatedChunk != null) {
                val split = translatedChunk.split("\n")
                for (i in chunk.indices) {
                    result[start + i] = split.getOrNull(i)?.trim().orEmpty()
                }
            }
            start = idx
        }

        if (result.any { it.isNotBlank() }) {
            cache[key] = result
            return result
        }
        return null
    }

    private fun translateBatch(text: String, targetLang: String): String? = runCatching {
        val encoded = java.net.URLEncoder.encode(text, "UTF-8")
        val endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
        val conn = (java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONArray(body)
            val segments = root.optJSONArray(0) ?: return@runCatching null
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i) ?: continue
                sb.append(seg.optString(0))
            }
            sb.toString()
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

/** Búsqueda binaria de la línea activa (las letras vienen ordenadas por `startMs`). */
private fun findActiveLyricLine(lines: List<com.applemusic.android.data.model.LyricLine>, pos: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var lastStarted = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].startMs <= pos) {
            lastStarted = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    if (lastStarted < 0) return -1
    // Si hay líneas solapadas (dúos), preferir la última que aún contiene la posición.
    var i = lastStarted
    var steps = 0
    while (i >= 0 && steps < 6) {
        if (pos <= lines[i].endMs) return i
        i--
        steps++
    }
    return lastStarted
}

/**
 * Una línea de letra. Rendimiento: no lee la posición en composición (solo vía derivedStateOf / lambdas),
 * y la animación de opacidad/escala corre en `graphicsLayer` (sin recomposición ni `blur` por línea,
 * que era lo que más trababa la pantalla).
 */
@Composable
private fun LyricLineItem(
    idx: Int,
    line: com.applemusic.android.data.model.LyricLine,
    nextLine: com.applemusic.android.data.model.LyricLine?,
    synced: Boolean,
    positionProvider: () -> Long,
    activeIndexProvider: () -> Int,
    isUserBrowsing: Boolean,
    translatedText: String? = null,
    onSeekLine: (Long) -> Unit
) {
    val isWithinLine by remember(line, synced) {
        derivedStateOf { synced && positionProvider() in line.startMs..line.endMs }
    }
    val activeIndex = activeIndexProvider()
    val isCurrent = synced && (idx == activeIndex || isWithinLine)
    val isPast = synced && activeIndex >= 0 && idx < activeIndex && !isWithinLine
    val distance = if (activeIndex >= 0) kotlin.math.abs(idx - activeIndex) else 0
    val alignEnd = line.isSecondaryAgent
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start

    val targetAlpha = when {
        !synced -> 0.92f
        isCurrent -> 1f
        isUserBrowsing -> 0.72f
        isPast -> 0.30f
        distance == 1 -> 0.48f
        else -> 0.34f
    }
    val targetScale = if (!synced || isCurrent || isUserBrowsing) 1f else 0.95f
    val alphaState = animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "am_lyric_alpha"
    )
    val scaleState = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
        label = "am_lyric_scale"
    )
    val mainHasWordTiming = remember(line) { hasRealWordTiming(line.words, line) }
    val bgHasWordTiming = remember(line) { hasRealWordTiming(line.backgroundWords, line) }

    // Column externo: en LazyColumn varios hijos de un mismo ítem se apilan (Box); aquí van uno tras otro.
    Column(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = alphaState.value
                scaleX = scaleState.value
                scaleY = scaleState.value
                transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = synced
            ) { onSeekLine(line.startMs) }
            .padding(vertical = 2.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        // Misma tipografía en todas las líneas (cero saltos de altura)
        if (isCurrent && mainHasWordTiming) {
            LyricSweepText(
                words = line.words,
                positionProvider = positionProvider,
                dimColor = Color.White.copy(alpha = 0.46f),
                litColor = Color.White,
                fontSize = 29.sp,
                lineHeight = 37.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = textAlign
            )
        } else {
            Text(
                text = line.text,
                fontSize = 29.sp,
                lineHeight = 37.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Traducción de la línea (cuando el usuario activa el botón Traducir)
        if (!translatedText.isNullOrBlank() && !translatedText.equals(line.text, ignoreCase = true)) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = translatedText,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (isCurrent) 0.88f else 0.64f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Coros / voces de fondo (ttm:role="x-bg")
        val bgTxt = line.backgroundText
        if (!bgTxt.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            if (isCurrent && bgHasWordTiming) {
                LyricSweepText(
                    words = line.backgroundWords,
                    positionProvider = positionProvider,
                    dimColor = Color.White.copy(alpha = 0.38f),
                    litColor = Color.White.copy(alpha = 0.88f),
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                    letterSpacing = (-0.2).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign
                )
            } else {
                Text(
                    text = bgTxt,
                    fontSize = 19.sp,
                    lineHeight = 25.sp,
                    letterSpacing = (-0.2).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Puntos de interludio instrumental entre líneas (>= 4,5 s)
    if (synced && nextLine != null && nextLine.startMs - line.endMs >= 4500L) {
        LyricGapDots(line = line, nextLine = nextLine, positionProvider = positionProvider)
    }
    }
}

/**
 * `true` solo si [words] trae tiempos reales por palabra/sílaba. Una sola "palabra" con espacios que abarca
 * toda la línea corresponde a letras sincronizadas únicamente por línea.
 */
private fun hasRealWordTiming(
    words: List<com.applemusic.android.data.model.LyricWord>,
    line: com.applemusic.android.data.model.LyricLine
): Boolean {
    if (words.isEmpty()) return false
    if (words.size > 1) return true
    val w = words[0]
    val spansWholeLine = w.startMs <= line.startMs + 50L && w.endMs >= line.endMs - 50L
    return !(spansWholeLine && w.text.trim().contains(' '))
}

/** Texto con barrido palabra a palabra. Solo este composable se recompone con el reloj (~60 Hz). */
@Composable
private fun LyricSweepText(
    words: List<com.applemusic.android.data.model.LyricWord>,
    positionProvider: () -> Long,
    dimColor: Color,
    litColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    letterSpacing: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign
) {
    val bucket by remember { derivedStateOf { positionProvider() / 16L } }
    val annotated = remember(words, bucket) {
        buildAppleMusicSweepString(
            words = words,
            positionMs = bucket * 16L,
            dimColor = dimColor,
            litColor = litColor
        )
    }
    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        fontWeight = fontWeight,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LyricGapDots(
    line: com.applemusic.android.data.model.LyricLine,
    nextLine: com.applemusic.android.data.model.LyricLine,
    positionProvider: () -> Long
) {
    val showGapDots by remember(line, nextLine) {
        derivedStateOf {
            val p = positionProvider()
            p > line.endMs && p < nextLine.startMs
        }
    }
    if (showGapDots) {
        val gapAfterMs = (nextLine.startMs - line.endMs).coerceAtLeast(1L)
        val gapProgress = ((positionProvider() - line.endMs).toFloat() / gapAfterMs.toFloat()).coerceIn(0f, 1f)
        Spacer(Modifier.height(16.dp))
        InstrumentalSyncDots(
            progress = gapProgress,
            accent = Color.White,
            alignEnd = nextLine.isSecondaryAgent
        )
    }
}
/**
 * Barrido progresivo de izquierda a derecha estilo Apple Music Sing (sin parpadeos ni sombras que re-maqueten).
 * - Palabras ya cantadas: blanco sólido.
 * - Palabra actual: barrido suave carácter por carácter con borde difuminado, ignorando espacios iniciales.
 * - Palabras futuras: blanco tenue uniforme.
 */
private fun buildAppleMusicSweepString(
    words: List<com.applemusic.android.data.model.LyricWord>,
    positionMs: Long,
    dimColor: Color,
    litColor: Color
) = buildAnnotatedString {
    words.forEach { w ->
        when {
            positionMs >= w.endMs -> {
                withStyle(SpanStyle(color = litColor)) { append(w.text) }
            }
            positionMs <= w.startMs -> {
                withStyle(SpanStyle(color = dimColor)) { append(w.text) }
            }
            else -> {
                val dur = (w.endMs - w.startMs).coerceAtLeast(60L).toFloat()
                val p = ((positionMs - w.startMs).toFloat() / dur).coerceIn(0f, 1f)
                val firstVisible = w.text.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
                if (firstVisible > 0) {
                    withStyle(SpanStyle(color = litColor)) { append(w.text.substring(0, firstVisible)) }
                }
                val visible = w.text.substring(firstVisible)
                val len = visible.length
                if (len <= 1) {
                    withStyle(SpanStyle(color = lerp(dimColor, litColor, p))) { append(visible) }
                } else {
                    val sweepPos = p * len
                    for (i in 0 until len) {
                        val charP = ((sweepPos - i) / 1.8f + 0.5f).coerceIn(0f, 1f)
                        withStyle(SpanStyle(color = lerp(dimColor, litColor, charP))) {
                            append(visible[i])
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tres puntos animados estilo Apple Music para interludios instrumentales largos (>= 4.5 s).
 */
@Composable
private fun InstrumentalSyncDots(
    progress: Float,
    accent: Color,
    alignEnd: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (alignEnd) Arrangement.spacedBy(10.dp, Alignment.End) else Arrangement.spacedBy(10.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val segStart = index / 3f
            val segEnd = (index + 1) / 3f
            val localP = ((progress - segStart) / (segEnd - segStart)).coerceIn(0f, 1f)
            val dotScale = 0.72f + 0.42f * localP
            val dotColor = lerp(accent.copy(alpha = 0.28f), Color.White, localP)
            Box(
                modifier = Modifier
                    .size((11.dp.value * dotScale).dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

/**
 * Página dedicada a pantalla completa para Cola de reproducción (Material 3 Expressive).
 */
@Composable
private fun FullScreenQueuePage(
    item: HomeItem,
    playback: PlaybackUiState,
    bgFallback: Color,
    tonalBg: Color,
    activeBg: Color,
    activeFg: Color,
    titleTint: Color,
    onBack: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onSkipToIndex: (Int) -> Unit,
    onOpenTrackMenu: (com.applemusic.android.data.model.QueueTrackItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgFallback)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        if (!item.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.25f)
                    .blur(90.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = AdaptiveReadableMaxWidth)
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Encabezado con píldora de regreso + título + contador
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = tonalBg,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver al reproductor",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = "Cola de reproducción",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                if (playback.queueItems.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = tonalBg
                    ) {
                        Text(
                            text = "${playback.queueItems.size}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = titleTint,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Tarjeta de Infinito / Reproducción automática
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (playback.autoplayEnabled) activeBg else tonalBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onToggleAutoplay)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AllInclusive,
                        contentDescription = null,
                        tint = if (playback.autoplayEnabled) activeFg else titleTint
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reproducción automática",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (playback.autoplayEnabled) activeFg else Color.White
                        )
                        Text(
                            text = if (playback.autoplayEnabled) "Infinito activado (música similar)" else "Desactivado",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (playback.autoplayEnabled) activeFg.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.72f)
                        )
                    }
                    Switch(
                        checked = playback.autoplayEnabled,
                        onCheckedChange = { onToggleAutoplay() }
                    )
                }
            }

            if (playback.queueItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay más pistas en la cola aún.\nUsa ••• → Reproducir a continuación o activa Infinito ∞.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(
                        items = playback.queueItems,
                        key = { _, q -> "${q.index}_${q.id}" }
                    ) { idx, qItem ->
                        val isCurrent = qItem.index == playback.queuePosition
                        val rowShape = segmentedRowShape(
                            isFirst = idx == 0,
                            isLast = idx == playback.queueItems.lastIndex
                        )
                        Surface(
                            shape = rowShape,
                            color = if (isCurrent) activeBg else tonalBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .clickable { onSkipToIndex(qItem.index) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ArtworkBox(
                                    artworkUrl = qItem.artworkUrl,
                                    fallbackColor = Color.White.copy(alpha = 0.14f),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qItem.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (isCurrent) activeFg else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (qItem.subtitle.isNotBlank()) {
                                        Text(
                                            text = if (isCurrent) "Sonando · ${qItem.subtitle}" else qItem.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCurrent) activeFg.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.72f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onOpenTrackMenu(qItem) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Más opciones",
                                        tint = if (isCurrent) activeFg else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
