package com.applemusic.android

import android.app.PictureInPictureParams
import android.os.Bundle
import android.util.Rational
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import coil.imageLoader
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.playback.PlaybackUiState
import com.applemusic.android.ui.auth.AppleMusicWebSessionDialog
import com.applemusic.android.ui.home.AccountConfigSheet
import com.applemusic.android.ui.home.AddToPlaylistBottomSheet
import com.applemusic.android.ui.home.BottomProgressiveBlur
import com.applemusic.android.ui.home.ExpandedNowPlayingScreen
import com.applemusic.android.ui.home.HomeScreen
import com.applemusic.android.ui.home.HomeViewModel
import com.applemusic.android.ui.home.LibraryScreen
import com.applemusic.android.ui.home.LocalMediaActionController
import com.applemusic.android.ui.home.LocalTabResetNonce
import com.applemusic.android.ui.home.MediaActionController
import com.applemusic.android.ui.home.MediaActionsBottomSheet
import com.applemusic.android.ui.home.MediaMenuRequest
import com.applemusic.android.ui.home.MediaNavigationDialogs
import com.applemusic.android.ui.home.SearchScreen
import com.applemusic.android.ui.home.SongCreditsBottomSheet
import com.applemusic.android.ui.home.rememberMediaNavigationState
import com.applemusic.android.ui.theme.AppleMusicTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    NEW("New", Icons.Rounded.GridView),
    RADIO("Radio", Icons.Rounded.Radio),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    SEARCH("Search", Icons.Rounded.Search)
}

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val isInPip = mutableStateOf(false)

    /** Solo se entra en Picture-in-Picture si hay un video musical reproduciéndose. */
    private fun canEnterPip(): Boolean {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        val pb = homeViewModel.playbackState.value
        return pb.isVideo && pb.currentItem != null && pb.isPlaying
    }

    private fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // Android 12+: al salir de la app (gesto/botón Home) entra solo a PiP, sin parpadeo.
                    setAutoEnterEnabled(canEnterPip())
                    setSeamlessResizeEnabled(false)
                }
            }
            .build()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (canEnterPip()) {
            isInPip.value = true // optimista: la UI ya se prepara para mostrar solo el video
            val entered = runCatching { enterPictureInPictureMode(buildPipParams()) }.getOrDefault(false)
            if (!entered) isInPip.value = false
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode) keepPlayingIfPipClosed()
    }

    /**
     * Si el usuario cierra la ventanita PiP (la X), la app ya no está visible pero el video/canción debe seguir
     * sonando. Si en cambio expandió la ventana de vuelta a la app, no hay nada que hacer.
     */
    private fun keepPlayingIfPipClosed() {
        window.decorView.postDelayed({
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                homeViewModel.resumeVideoIfIntended()
                window.decorView.postDelayed({ homeViewModel.resumeVideoIfIntended() }, 1200L)
            }
        }, 350L)
    }

    override fun onStop() {
        super.onStop()
        // Salir de la app (o cerrar PiP) no debe detener lo que suena.
        val pb = homeViewModel.playbackState.value
        if (pb.isVideo && pb.isPlaying) {
            window.decorView.postDelayed({ homeViewModel.resumeVideoIfIntended() }, 400L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Mantener actualizados los parámetros de PiP (entrada automática en Android 12+) según haya video sonando.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                homeViewModel.playbackState
                    .map { it.isVideo && it.currentItem != null && it.isPlaying }
                    .distinctUntilChanged()
                    .collect {
                        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                            runCatching { setPictureInPictureParams(buildPipParams()) }
                        }
                    }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        setContent {
            val settingsState by homeViewModel.settingsState.collectAsStateWithLifecycle()
            AppleMusicTheme(fontOption = settingsState.fontOption) {
                AppleMusicApp(
                    isInPip = isInPip.value,
                    homeViewModel = homeViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicApp(
    isInPip: Boolean = false,
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val playback by homeViewModel.playbackState.collectAsStateWithLifecycle()
    val newState by homeViewModel.newState.collectAsStateWithLifecycle()
    val radioState by homeViewModel.radioState.collectAsStateWithLifecycle()
    val searchState by homeViewModel.searchState.collectAsStateWithLifecycle()
    val libraryState by homeViewModel.libraryState.collectAsStateWithLifecycle()
    val favoriteOverrides by homeViewModel.favoriteOverrides.collectAsStateWithLifecycle()
    val settingsState by homeViewModel.settingsState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var showWebLogin by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showFullscreenVideo by remember { mutableStateOf(false) }
    var menuRequest by remember { mutableStateOf<MediaMenuRequest?>(null) }
    var playlistPickerItem by remember { mutableStateOf<HomeItem?>(null) }
    var creditsRequest by remember { mutableStateOf<Pair<HomeItem, ((HomeItem) -> Unit)?>?>(null) }
    var openLyricsNonce by remember { mutableLongStateOf(0L) }
    var openQueueNonce by remember { mutableLongStateOf(0L) }
    var tabResetNonce by remember { mutableLongStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val playerNav = rememberMediaNavigationState(homeViewModel::onPlayItem)
    val appScope = rememberCoroutineScope()

    val actionController = remember(homeViewModel, playerNav) {
        MediaActionController(
            // Lee el State de Compose (no el StateFlow.value) para que quien la invoque se recomponga al cambiar la estrella.
            favoriteOverrides = { favoriteOverrides },
            onOpenMenu = { req -> menuRequest = req },
            onToggleFavorite = { favItem -> homeViewModel.toggleFavorite(favItem, null) },
            onOpenCredits = { cItem, navCb -> creditsRequest = cItem to navCb },
            onStartStation = { stItem -> homeViewModel.startStation(stItem) },
            onRefreshStatus = { stItem -> appScope.launch { homeViewModel.loadItemStatus(stItem) } },
            onToggleInLibrary = homeViewModel::toggleInLibrary,
            loadItemStatus = homeViewModel::loadItemStatus,
            onSyncFavoritesForAlbum = homeViewModel::syncFavoritesForAlbum,
            onPlayTrackList = homeViewModel::onPlayTrackList,
            isPinned = homeViewModel::isPinned,
            onTogglePin = homeViewModel::togglePin,
            loadArtistArtwork = homeViewModel::loadArtistArtwork,
            onSetSleepTimer = { mins ->
                homeViewModel.setSleepTimerMinutes(mins)
                val msg = when {
                    mins == 0 -> "Temporizador desactivado"
                    mins < 0 -> "Se pausará al terminar esta canción"
                    else -> "Temporizador activado: $mins min"
                }
                homeViewModel.showFeedback(msg)
            },
            sleepTimerLabel = {
                val st = homeViewModel.playbackState.value
                when {
                    st.sleepTimerEndOfTrack -> "Fin de pista"
                    st.sleepTimerRemainingMs > 0L -> {
                        val s = st.sleepTimerRemainingMs / 1000L
                        "%d:%02d".format(s / 60, s % 60)
                    }
                    else -> null
                }
            },
            onOpenLyricsInPlayer = {
                showFullscreenVideo = false
                showNowPlaying = true
                openLyricsNonce++
            },
            onOpenQueueInPlayer = {
                showFullscreenVideo = false
                showNowPlaying = true
                openQueueNonce++
            }
        )
    }

    LaunchedEffect(playback.videoPlaybackNonce) {
        if (playback.videoPlaybackNonce > 0L && playback.isVideo) {
            showNowPlaying = true
        }
    }

    LaunchedEffect(playback.isVideo) {
        if (!playback.isVideo) {
            showFullscreenVideo = false
        }
    }

    // En Picture-in-Picture la ventana muestra solo el video a pantalla completa (sin controles ni menús).
    LaunchedEffect(isInPip) {
        if (isInPip && playback.isVideo && playback.currentItem != null) {
            showNowPlaying = true
            showFullscreenVideo = true
        }
    }

    LaunchedEffect(showNowPlaying, showFullscreenVideo) {
        homeViewModel.resumeVideoIfIntended()
    }

    // Refrescar recientes al volver a la app y periódicamente mientras está visible (para reflejar reproducciones de la app oficial)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                homeViewModel.refreshRecentlyPlayed()
                kotlinx.coroutines.delay(15_000L)
            }
        }
    }

    // Precargar cada pestaña la primera vez que se selecciona si aún no está en memoria
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            AppTab.NEW -> homeViewModel.loadNewIfNeeded()
            AppTab.RADIO -> homeViewModel.loadRadioIfNeeded()
            AppTab.LIBRARY -> homeViewModel.loadLibraryIfNeeded()
            AppTab.SEARCH -> homeViewModel.loadSearchCategoriesIfNeeded()
            else -> Unit
        }
    }

    LaunchedEffect(uiState.authFeedbackMessage) {
        val msg = uiState.authFeedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            homeViewModel.consumeAuthFeedback()
        }
    }

    val appStrings = remember(settingsState.languageOption) {
        com.applemusic.android.ui.theme.AppStrings.forLanguageOption(settingsState.languageOption)
    }
    CompositionLocalProvider(
        LocalMediaActionController provides actionController,
        LocalTabResetNonce provides tabResetNonce,
        com.applemusic.android.ui.theme.LocalAppStrings provides appStrings
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 96.dp)
            )
        }
    ) { _ ->
        val bottomHazeState = rememberHazeState()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        var rootBounds by remember { mutableStateOf<Rect?>(null) }
        var miniVideoBounds by remember { mutableStateOf<Rect?>(null) }
        var sheetVideoBounds by remember { mutableStateOf<Rect?>(null) }

        LaunchedEffect(showNowPlaying) {
            if (!showNowPlaying) sheetVideoBounds = null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootBounds = it.boundsInRoot() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = bottomHazeState)
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "tab_crossfade",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                when (tab) {
                    AppTab.HOME -> HomeScreen(
                        uiState = uiState,
                        onRefresh = homeViewModel::refresh,
                        onOpenAccountSheet = { showAccountSheet = true },
                        onConnectWebSession = { showWebLogin = true },
                        onPlayItem = homeViewModel::onPlayItem,
                        title = appStrings.tabHome,
                        userAvatarUrl = settingsState.userProfile?.avatarUrl,
                        loadFullSection = homeViewModel::loadFullSection,
                        loadListeningHistory = homeViewModel::loadListeningHistory,
                        loadMediaDetail = homeViewModel::loadMediaDetail,
                        loadArtist = homeViewModel::loadArtist,
                        loadReplay = homeViewModel::loadReplay,
                        loadSocialProfile = homeViewModel::loadSocialProfile,
                        loadEditorialSubpage = homeViewModel::loadEditorialSubpage
                    )
                    AppTab.NEW -> HomeScreen(
                        uiState = newState.copy(mode = uiState.mode, credentials = uiState.credentials),
                        onRefresh = homeViewModel::refreshNew,
                        onOpenAccountSheet = { showAccountSheet = true },
                        onConnectWebSession = { showWebLogin = true },
                        onPlayItem = homeViewModel::onPlayItem,
                        title = appStrings.tabNew,
                        showReplay = false,
                        showConnectBanner = false,
                        userAvatarUrl = settingsState.userProfile?.avatarUrl,
                        loadFullSection = homeViewModel::loadFullSection,
                        loadMediaDetail = homeViewModel::loadMediaDetail,
                        loadArtist = homeViewModel::loadArtist,
                        loadSocialProfile = homeViewModel::loadSocialProfile,
                        loadEditorialSubpage = homeViewModel::loadEditorialSubpage
                    )
                    AppTab.RADIO -> HomeScreen(
                        uiState = radioState.copy(mode = uiState.mode, credentials = uiState.credentials),
                        onRefresh = homeViewModel::refreshRadio,
                        onOpenAccountSheet = { showAccountSheet = true },
                        onConnectWebSession = { showWebLogin = true },
                        onPlayItem = homeViewModel::onPlayItem,
                        title = appStrings.tabRadio,
                        showReplay = false,
                        showConnectBanner = false,
                        userAvatarUrl = settingsState.userProfile?.avatarUrl,
                        loadFullSection = homeViewModel::loadFullSection,
                        loadMediaDetail = homeViewModel::loadMediaDetail,
                        loadArtist = homeViewModel::loadArtist,
                        loadSocialProfile = homeViewModel::loadSocialProfile,
                        loadEditorialSubpage = homeViewModel::loadEditorialSubpage
                    )
                    AppTab.LIBRARY -> LibraryScreen(
                        uiState = libraryState,
                        mode = uiState.mode,
                        onRefresh = homeViewModel::refreshLibrary,
                        onSelectCategory = homeViewModel::selectLibraryCategory,
                        onFilterChange = homeViewModel::onLibraryFilterChange,
                        onLoadMore = homeViewModel::loadMoreLibrary,
                        onOpenAccountSheet = { showAccountSheet = true },
                        onConnectWebSession = { showWebLogin = true },
                        onPlayItem = homeViewModel::onPlayItem,
                        userAvatarUrl = settingsState.userProfile?.avatarUrl,
                        loadFullSection = homeViewModel::loadFullSection,
                        loadMediaDetail = homeViewModel::loadMediaDetail,
                        loadArtist = homeViewModel::loadArtist,
                        loadReplay = homeViewModel::loadReplay,
                        loadSocialProfile = homeViewModel::loadSocialProfile,
                        loadEditorialSubpage = homeViewModel::loadEditorialSubpage
                    )
                    AppTab.SEARCH -> SearchScreen(
                        uiState = searchState,
                        mode = uiState.mode,
                        onQueryChange = homeViewModel::onSearchQueryChange,
                        onScopeChange = homeViewModel::onSearchScopeChange,
                        onSubmitSearch = { homeViewModel.submitSearch(it) },
                        onClearSearch = homeViewModel::clearSearch,
                        onClearRecentSearches = homeViewModel::clearRecentSearches,
                        onOpenAccountSheet = { showAccountSheet = true },
                        onConnectWebSession = { showWebLogin = true },
                        onPlayItem = homeViewModel::onPlayItem,
                        userAvatarUrl = settingsState.userProfile?.avatarUrl,
                        loadFullSection = homeViewModel::loadFullSection,
                        loadMediaDetail = homeViewModel::loadMediaDetail,
                        loadArtist = homeViewModel::loadArtist,
                        loadReplay = homeViewModel::loadReplay,
                        loadSocialProfile = homeViewModel::loadSocialProfile,
                        loadEditorialSubpage = homeViewModel::loadEditorialSubpage
                    )
                }
            }

            // Páginas abiertas desde el reproductor (ej. perfil del artista) integradas sobre la pestaña actual
            MediaNavigationDialogs(
                nav = playerNav,
                onPlayItem = homeViewModel::onPlayItem,
                loadFullSection = homeViewModel::loadFullSection,
                loadMediaDetail = homeViewModel::loadMediaDetail,
                loadArtist = homeViewModel::loadArtist,
                loadReplay = homeViewModel::loadReplay,
                loadSocialProfile = homeViewModel::loadSocialProfile,
                loadEditorialSubpage = homeViewModel::loadEditorialSubpage
            )
            }

            // Blur progresivo desde la mitad de la barra flotante hacia abajo (estilo expo-backdrop)
            BottomProgressiveBlur(
                hazeState = bottomHazeState,
                height = navBarBottom + 54.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // En tablets / horizontal la barra flotante no se estira de borde a borde
                    // (widthIn debe ir ANTES de fillMaxWidth para que el máximo se respete).
                    .widthIn(max = com.applemusic.android.ui.home.AdaptiveBottomBarMaxWidth)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                playback.currentItem?.let {
                    MiniPlayerBar(
                        playback = playback,
                        onOpenPlayer = { showNowPlaying = true },
                        onTogglePlayPause = homeViewModel::togglePlayPause,
                        onNext = homeViewModel::skipNext,
                        onPrevious = homeViewModel::skipPrevious,
                        onVideoBoundsChanged = { miniVideoBounds = it }
                    )
                }

                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                    ) {
                        AppTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = {
                                    playerNav.clear()
                                    tabResetNonce++
                                    selectedTab = tab
                                },
                                shapes = ToggleButtonDefaults.shapes(),
                                contentPadding = PaddingValues(
                                    horizontal = if (isSelected) 14.dp else 12.dp,
                                    vertical = 10.dp
                                )
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(20.dp)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = expandHorizontally(
                                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                                    ) + fadeIn(
                                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                                    ),
                                    exit = shrinkHorizontally(
                                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                                    ) + fadeOut(
                                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                                    )
                                ) {
                                    val tabLabel = when (tab) {
                                        AppTab.HOME -> appStrings.tabHome
                                        AppTab.NEW -> appStrings.tabNew
                                        AppTab.RADIO -> appStrings.tabRadio
                                        AppTab.LIBRARY -> appStrings.tabLibrary
                                        AppTab.SEARCH -> appStrings.tabSearch
                                    }
                                    Text(
                                        text = tabLabel,
                                        modifier = Modifier.padding(start = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Reproductor expandido en la misma ventana (no se cierra ni reabre al entrar/salir de pantalla completa de video).
            androidx.compose.animation.AnimatedVisibility(
                visible = showNowPlaying && playback.currentItem != null,
                enter = slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 320,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 280,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    targetOffsetY = { it }
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            ) {
                ExpandedNowPlayingScreen(
                    playback = playback,
                    onVideoBoundsChanged = { sheetVideoBounds = it },
                    onDismiss = {
                        showFullscreenVideo = false
                        showNowPlaying = false
                    },
                    onTogglePlayPause = homeViewModel::togglePlayPause,
                    onNext = homeViewModel::skipNext,
                    onPrevious = homeViewModel::skipPrevious,
                    onSeek = homeViewModel::seekTo,
                    onOpenArtistByName = { artistName ->
                        showFullscreenVideo = false
                        showNowPlaying = false
                        appScope.launch {
                            val resolved = homeViewModel.resolveArtistByName(artistName)
                            if (resolved != null) {
                                playerNav.activeArtist = resolved
                            } else {
                                snackbarHostState.showSnackbar("No se encontró el perfil de $artistName")
                            }
                        }
                    },
                    resolveArtistByName = homeViewModel::resolveArtistByName,
                    favoriteOverrides = favoriteOverrides,
                    loadItemStatus = homeViewModel::loadItemStatus,
                    onToggleFavorite = homeViewModel::toggleFavorite,
                    loadLyrics = homeViewModel::loadLyrics,
                    loadMotionArtwork = homeViewModel::loadSongMotionArtwork,
                    loadAudioTraits = homeViewModel::loadSongAudioTraits,
                    onSetAtmosMode = homeViewModel::setAtmosMode,
                    onToggleShuffle = homeViewModel::toggleShuffle,
                    onCycleRepeatMode = homeViewModel::cycleRepeatMode,
                    onToggleAutoplay = homeViewModel::toggleAutoplay,
                    onCycleTransitionMode = homeViewModel::cycleAudioTransitionMode,
                    onSkipToQueueIndex = homeViewModel::skipToQueueIndex,
                    onOpenMoreMenu = { targetItem -> menuRequest = MediaMenuRequest(item = targetItem) },
                    openLyricsNonce = openLyricsNonce,
                    openQueueNonce = openQueueNonce
                )
            }

            if (showFullscreenVideo && playback.currentItem != null && playback.isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(15f)
                )
            }

            // Host ÚNICO persistente de MusicKit WebView: nunca sale de composición ni cambia de ventana.
            val keepWebMounted = playback.usingWebPlayback ||
                playback.isVideo ||
                uiState.credentials.hasWebSession ||
                uiState.credentials.hasUserToken
            if (keepWebMounted) {
                val showVideoTarget = playback.isVideo && playback.currentItem != null
                val targetRect = when {
                    !showVideoTarget -> null
                    showFullscreenVideo -> rootBounds
                    showNowPlaying -> sheetVideoBounds ?: miniVideoBounds
                    else -> miniVideoBounds
                }
                val density = LocalDensity.current
                val rootLeft = rootBounds?.left ?: 0f
                val rootTop = rootBounds?.top ?: 0f
                val widthDp = if (targetRect != null && targetRect.width > 1f) {
                    with(density) { targetRect.width.toDp() }
                } else 1.dp
                val heightDp = if (targetRect != null && targetRect.height > 1f) {
                    with(density) { targetRect.height.toDp() }
                } else 1.dp
                val offsetX = if (targetRect != null) (targetRect.left - rootLeft).roundToInt() else 0
                val offsetY = if (targetRect != null) (targetRect.top - rootTop).roundToInt() else 0
                val cornerDp = when {
                    targetRect == null || showFullscreenVideo -> 0.dp
                    showNowPlaying -> 18.dp
                    else -> 8.dp
                }
                val videoAlpha = if (targetRect != null && playback.isVideoFrameReady) 1f else 0.01f

                VideoWebViewHost(
                    attachHost = { host -> homeViewModel.attachVideoHost(host, isFallback = false) },
                    detachHost = homeViewModel::detachVideoHost,
                    modifier = Modifier
                        .zIndex(20f)
                        .offset { IntOffset(offsetX, offsetY) }
                        .size(width = widthDp, height = heightDp)
                        .clip(RoundedCornerShape(cornerDp))
                        .alpha(videoAlpha)
                )
            }

            // Botón de pantalla completa y spinner encima del slot 16:9 cuando está abierto el reproductor expandido.
            val sRect = sheetVideoBounds
            val rRect = rootBounds
            if (showNowPlaying && !showFullscreenVideo && playback.isVideo && playback.currentItem != null && sRect != null && rRect != null) {
                val density = LocalDensity.current
                val wDp = with(density) { sRect.width.toDp() }
                val hDp = with(density) { sRect.height.toDp() }
                Box(
                    modifier = Modifier
                        .zIndex(25f)
                        .offset { IntOffset((sRect.left - rRect.left).roundToInt(), (sRect.top - rRect.top).roundToInt()) }
                        .size(wDp, hDp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    if (playback.isBuffering || !playback.isVideoFrameReady) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier
                                .size(34.dp)
                                .align(Alignment.Center)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.56f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .clickable { showFullscreenVideo = true }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "Pantalla completa",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                }
            }

            if (showFullscreenVideo && playback.currentItem != null && playback.isVideo && !isInPip) {
                FullscreenVideoControlsOverlay(
                    playback = playback,
                    onExitFullscreen = { showFullscreenVideo = false },
                    onTogglePlayPause = homeViewModel::togglePlayPause,
                    onNext = homeViewModel::skipNext,
                    onPrevious = homeViewModel::skipPrevious,
                    onSeek = homeViewModel::seekTo,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(25f)
                )
            }
        }
    }

    if (showAccountSheet) {
        AccountConfigSheet(
            mode = uiState.mode,
            credentials = uiState.credentials,
            settings = settingsState,
            atmosMode = playback.atmosMode,
            dolbyCapabilities = playback.dolbyCapabilities,
            onSetAtmosMode = homeViewModel::setAtmosMode,
            onSetFontOption = homeViewModel::setFontOption,
            onSetLanguageOption = homeViewModel::setLanguageOption,
            onSetCrossfadeSeconds = homeViewModel::setCrossfadeSeconds,
            onSetAutoMixEnabled = homeViewModel::setAutoMixEnabled,
            onSetEqualizerEnabled = homeViewModel::setEqualizerEnabled,
            onApplyEqPreset = homeViewModel::applyEqualizerPreset,
            onSetEqBandLevel = homeViewModel::setEqualizerBandLevel,
            onSetBassBoost = homeViewModel::setBassBoostStrength,
            onRefreshCacheStats = homeViewModel::refreshCacheStats,
            onSetCacheLimitMb = homeViewModel::setCacheLimitMb,
            onClearCaches = homeViewModel::clearAllCaches,
            loadListeningHistory = homeViewModel::loadListeningHistory,
            onPlayHistoryItem = { item -> homeViewModel.onPlayItem(item, null, 0, false) },
            onOpenReplay = { playerNav.showReplayDialog = true },
            onDismiss = { showAccountSheet = false },
            onSignInWithAppleMusic = {
                showAccountSheet = false
                showWebLogin = true
            },
            onSignOut = homeViewModel::signOut
        )
    }

    if (showWebLogin) {
        AppleMusicWebSessionDialog(
            storefront = uiState.credentials.storefront,
            onDismiss = { showWebLogin = false },
            onSessionCaptured = { userToken, devToken ->
                showWebLogin = false
                homeViewModel.onWebSessionCaptured(userToken, devToken)
            }
        )
    }

    val routeFromActions: (HomeItem, ((HomeItem) -> Unit)?) -> Unit = { target, scopedNav ->
        if (scopedNav != null) {
            scopedNav(target)
        } else {
            showFullscreenVideo = false
            showNowPlaying = false
            playerNav.routeItem(target)
        }
    }

    menuRequest?.let { req ->
        MediaActionsBottomSheet(
            request = req,
            autoplayEnabled = playback.autoplayEnabled,
            favoriteOverrides = favoriteOverrides,
            loadItemStatus = homeViewModel::loadItemStatus,
            onToggleFavorite = homeViewModel::toggleFavorite,
            onToggleInLibrary = homeViewModel::toggleInLibrary,
            onAddToPlaylist = { target -> playlistPickerItem = target },
            onPlayNext = homeViewModel::playNext,
            onPlayLater = homeViewModel::playLater,
            onToggleAutoplay = homeViewModel::toggleAutoplay,
            onOpenQueue = {
                showFullscreenVideo = false
                showNowPlaying = true
                openQueueNonce++
            },
            onOpenLyrics = { target ->
                if (playback.currentItem?.id != target.id) {
                    homeViewModel.onPlayItem(target, null, 0, false)
                }
                showFullscreenVideo = false
                showNowPlaying = true
                openLyricsNonce++
            },
            onStartStation = homeViewModel::startStation,
            onViewCredits = { target -> creditsRequest = target to req.onNavigateItem },
            onGoToAlbum = { albumId, artUrl ->
                routeFromActions(
                    HomeItem(id = albumId, type = "albums", title = req.item.title, subtitle = req.item.subtitle, artworkUrl = artUrl),
                    req.onNavigateItem
                )
            },
            onGoToArtist = { artistId, artistName ->
                routeFromActions(
                    HomeItem(id = artistId, type = "artists", title = artistName.orEmpty(), subtitle = "", artworkUrl = null),
                    req.onNavigateItem
                )
            },
            onRemoveFromPlaylist = homeViewModel::removeTrackFromPlaylist,
            onDeletePlaylist = homeViewModel::deletePlaylist,
            onDismiss = { menuRequest = null }
        )
    }

    playlistPickerItem?.let { pItem ->
        AddToPlaylistBottomSheet(
            item = pItem,
            loadPlaylists = homeViewModel::loadUserPlaylists,
            onSelectPlaylist = { plId, plName -> homeViewModel.addToPlaylist(plId, plName, pItem) },
            onCreatePlaylist = { newName -> homeViewModel.createPlaylistAndAdd(newName, pItem) },
            onDismiss = { playlistPickerItem = null }
        )
    }

    creditsRequest?.let { (cItem, scopedNav) ->
        SongCreditsBottomSheet(
            seed = cItem,
            loadCredits = homeViewModel::loadSongCredits,
            onNavigateArtist = { artistId, name ->
                routeFromActions(
                    HomeItem(id = artistId, type = "artists", title = name, subtitle = "", artworkUrl = null),
                    scopedNav
                )
            },
            onNavigateAlbum = { albumId ->
                routeFromActions(
                    HomeItem(id = albumId, type = "albums", title = cItem.title, subtitle = cItem.subtitle, artworkUrl = cItem.artworkUrl),
                    scopedNav
                )
            },
            onDismiss = { creditsRequest = null }
        )
    }
    }
}

@Composable
private fun MiniPlayerBar(
    playback: PlaybackUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVideoBoundsChanged: (Rect) -> Unit
) {
    val item = playback.currentItem ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var extractedColor by remember(item.id, item.artworkUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(item.id, item.artworkUrl) {
        val url = item.artworkUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            extractedColor = null
            return@LaunchedEffect
        }
        val dominant = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .size(48)
                    .allowHardware(false)
                    .build()
                val res = context.imageLoader.execute(req)
                (res as? coil.request.SuccessResult)?.drawable?.let {
                    com.applemusic.android.ui.home.extractVibrantDominantColor(it)
                }
            }.getOrNull()
        }
        extractedColor = dominant
    }

    val baseCover = extractedColor ?: Color(0xFF3E4B59)
    val miniBg by androidx.compose.animation.animateColorAsState(
        targetValue = androidx.compose.ui.graphics.lerp(Color(0xFF141618), baseCover, 0.56f),
        label = "mini_bg"
    )
    val miniPlayBg by androidx.compose.animation.animateColorAsState(
        targetValue = androidx.compose.ui.graphics.lerp(baseCover, Color.White, 0.66f),
        label = "mini_play_bg"
    )
    val miniPlayFg by androidx.compose.animation.animateColorAsState(
        targetValue = androidx.compose.ui.graphics.lerp(baseCover, Color.Black, 0.86f),
        label = "mini_play_fg"
    )
    val miniNextBg by androidx.compose.animation.animateColorAsState(
        targetValue = androidx.compose.ui.graphics.lerp(Color(0xFF1B1E22), baseCover, 0.68f),
        label = "mini_next_bg"
    )
    val miniTitleTint by androidx.compose.animation.animateColorAsState(
        targetValue = androidx.compose.ui.graphics.lerp(baseCover, Color.White, 0.72f),
        label = "mini_title_tint"
    )

    var swipeDragPx by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (swipeDragPx * 0.35f).coerceIn(-44f, 44f),
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.68f, stiffness = 460f),
        label = "mini_swipe_x"
    )

    // Mini reproductor compacto pintado del color de la portada + gesto horizontal (izq = anterior, der = siguiente)
    Surface(
        color = miniBg,
        contentColor = Color.White,
        shadowElevation = 8.dp,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .graphicsLayer { translationX = animatedOffsetX }
            .clip(CircleShape)
            .pointerInput(item.id) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeDragPx = 0f },
                    onHorizontalDrag = { _, dragAmount -> swipeDragPx += dragAmount },
                    onDragCancel = { swipeDragPx = 0f },
                    onDragEnd = {
                        when {
                            swipeDragPx < -72f -> onPrevious()
                            swipeDragPx > 72f -> onNext()
                        }
                        swipeDragPx = 0f
                    }
                )
            }
            .clickable(onClick = onOpenPlayer)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val thumbModifier = if (playback.isVideo) {
                    Modifier
                        .width(58.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .onGloballyPositioned { onVideoBoundsChanged(it.boundsInRoot()) }
                } else {
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                }
                if (!item.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = thumbModifier
                    )
                } else {
                    Box(
                        modifier = thumbModifier.background(miniNextBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = miniTitleTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val isLiveRadio = (item.type.equals("stations", ignoreCase = true) ||
                            item.type.equals("radio-episodes", ignoreCase = true) ||
                            item.id.startsWith("ra.")) && playback.durationMs <= 0L
                        if (playback.isVideo) {
                            Surface(
                                shape = CircleShape,
                                color = miniPlayBg
                            ) {
                                Text(
                                    text = "VIDEO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = miniPlayFg,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        } else if (isLiveRadio) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFA243C).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (playback.isBuffering) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = miniPlayBg,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = miniPlayBg,
                            contentColor = miniPlayFg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onTogglePlayPause)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = miniNextBg,
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNext)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Siguiente",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            if (playback.durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = miniPlayBg,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(2.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoWebViewHost(
    attachHost: (ViewGroup) -> Unit,
    detachHost: (ViewGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            containerRef?.let(detachHost)
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // FrameLayout que no roba eventos táctiles para que los controles Compose encima sigan interactivos.
            object : FrameLayout(ctx) {
                override fun dispatchTouchEvent(ev: MotionEvent?) = false
            }.apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                containerRef = this
                attachHost(this)
            }
        },
        update = { host ->
            if (containerRef !== host) containerRef = host
            attachHost(host)
        }
    )
}

@Composable
private fun FullscreenVideoControlsOverlay(
    playback: PlaybackUiState,
    onExitFullscreen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val item = playback.currentItem ?: return
    var showControls by remember { mutableStateOf(true) }
    var draggingValue by remember { mutableFloatStateOf(-1f) }

    // Auto-ocultar controles tras 3.5 s mientras se reproduce el video
    LaunchedEffect(showControls, playback.isPlaying, draggingValue) {
        if (showControls && playback.isPlaying && draggingValue < 0f) {
            kotlinx.coroutines.delay(3500L)
            showControls = false
        }
    }

    BackHandler(onBack = onExitFullscreen)

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        if (playback.isBuffering || !playback.isVideoFrameReady) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            0.24f to Color.Black.copy(alpha = 0.16f),
                            0.74f to Color.Black.copy(alpha = 0.16f),
                            1f to Color.Black.copy(alpha = 0.84f)
                        )
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Encabezado superior con cápsula de título + botón de salir de pantalla completa
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF141518).copy(alpha = 0.82f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.74f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF141518).copy(alpha = 0.82f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onExitFullscreen)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.FullscreenExit,
                                contentDescription = "Salir de pantalla completa",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Controles centrales estilo Pixel Cinema (Anterior | Play-Pausa | Siguiente)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF141518).copy(alpha = 0.78f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPrevious)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Anterior",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    val playShape = RoundedCornerShape(if (playback.isPlaying) 22.dp else 36.dp)
                    Surface(
                        shape = playShape,
                        color = Color.White,
                        contentColor = Color.Black,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(playShape)
                            .clickable(onClick = onTogglePlayPause)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF141518).copy(alpha = 0.78f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNext)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Siguiente",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Cápsula inferior con barra de progreso y tiempos
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141518).copy(alpha = 0.82f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val duration = playback.durationMs.coerceAtLeast(1L)
                        val currentRatio = if (draggingValue >= 0f) draggingValue else {
                            (playback.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        }
                        Text(
                            text = formatTimeMs(
                                if (draggingValue >= 0f) (draggingValue * duration).toLong() else playback.positionMs
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Slider(
                            value = currentRatio,
                            onValueChange = { draggingValue = it },
                            onValueChangeFinished = {
                                val targetMs = (draggingValue * duration).toLong()
                                draggingValue = -1f
                                onSeek(targetMs)
                            },
                            enabled = playback.durationMs > 0L,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTimeMs(playback.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val s = ms / 1000L
    return "%d:%02d".format(s / 60, s % 60)
}
