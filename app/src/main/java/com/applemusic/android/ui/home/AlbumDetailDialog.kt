package com.applemusic.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.applemusic.android.data.model.AlbumDetail
import com.applemusic.android.data.model.AlbumTrack
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class UserPlaylistSort(val label: String) {
    PLAYLIST_ORDER("Orden de la playlist"),
    RECENT_FIRST("Más recientes primero"),
    TITLE_ASC("Título (A–Z)"),
    TITLE_DESC("Título (Z–A)"),
    ARTIST_ASC("Artista (A–Z)"),
    ARTIST_DESC("Artista (Z–A)"),
    DURATION_SHORT("Más cortas primero"),
    DURATION_LONG("Más largas primero")
}

/**
 * Página de Álbum o Playlist pintada con el color real de la portada y controles estilo Pixel Expressive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailDialog(
    seed: HomeItem,
    loadDetail: suspend (HomeItem) -> AlbumDetail?,
    onDismiss: () -> Unit,
    onPlay: (item: HomeItem, container: HomeItem?, startIndex: Int, shuffle: Boolean) -> Unit,
    onNavigateItem: (HomeItem) -> Unit = {},
    onOpenReplay: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var detail by remember(seed.id) { mutableStateOf<AlbumDetail?>(null) }
    var loading by remember(seed.id) { mutableStateOf(true) }
    var trackSort by remember(seed.id) { mutableStateOf(UserPlaylistSort.PLAYLIST_ORDER) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTrackSearch by remember(seed.id) { mutableStateOf(false) }
    var trackSearchQuery by remember(seed.id) { mutableStateOf("") }
    var relatedSeeAll by remember(seed.id) { mutableStateOf<HomeSection?>(null) }
    var extractedCoverColor by remember(seed.id) { mutableStateOf<Color?>(null) }
    var removedTrackIds by remember(seed.id) { mutableStateOf<Set<String>>(emptySet()) }

    val listState = rememberLazyListState()
    val showTitlePill by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 1 || listState.firstVisibleItemScrollOffset > 520
        }
    }

    LaunchedEffect(seed.id) {
        detail = loadDetail(seed)
        loading = false
    }

    val current = detail
    val rawCoverUrl = current?.artworkUrl?.takeIf { it.isNotBlank() } ?: seed.artworkUrl?.takeIf { it.isNotBlank() }
    val coverUrl = remember(rawCoverUrl) { rawCoverUrl?.let(::toHighResCollectionArtworkUrl) }

    LaunchedEffect(seed.id, coverUrl) {
        if (coverUrl == null) {
            extractedCoverColor = null
            return@LaunchedEffect
        }
        val dominant = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(64)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(req)
                (result as? SuccessResult)?.drawable?.let { extractVibrantDominantColor(it) }
            }.getOrNull()
        }
        if (dominant != null) extractedCoverColor = dominant
    }

    // Paleta completa pintada con el color de la portada (respetando modo claro y modo oscuro)
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val metadataAccent = parseHexColor(seed.bgColorHex)?.takeIf { it.luminance() in 0.05f..0.90f }
    val baseCoverColor = extractedCoverColor ?: metadataAccent ?: Color(0xFF5A738C)

    val targetPageBg = if (isLightMode) {
        lerp(baseCoverColor, Color.White, 0.54f)
    } else {
        lerp(Color(0xFF101215), baseCoverColor, 0.38f)
    }
    val targetCardBg = if (isLightMode) {
        lerp(targetPageBg, baseCoverColor, 0.16f)
    } else {
        lerp(Color(0xFF171A1E), baseCoverColor, 0.50f)
    }
    val targetTonalBtnBg = if (isLightMode) {
        lerp(targetPageBg, baseCoverColor, 0.28f)
    } else {
        lerp(Color(0xFF1C2025), baseCoverColor, 0.58f)
    }
    val targetPlayBtnBg = if (isLightMode) {
        lerp(baseCoverColor, Color.Black, 0.88f)
    } else {
        lerp(baseCoverColor, Color.White, 0.64f)
    }
    val targetPlayBtnFg = if (isLightMode) {
        Color.White
    } else {
        lerp(baseCoverColor, Color.Black, 0.86f)
    }
    val targetHeaderPillBg = if (isLightMode) {
        lerp(Color.White, baseCoverColor, 0.14f)
    } else {
        lerp(Color(0xFF181B20), baseCoverColor, 0.56f)
    }
    val targetOnPagePrimary = if (isLightMode) {
        lerp(baseCoverColor, Color.Black, 0.88f)
    } else {
        Color.White
    }
    val targetOnPageMuted = targetOnPagePrimary.copy(alpha = if (isLightMode) 0.72f else 0.76f)

    val pageBg by animateColorAsState(targetPageBg, label = "col_page_bg")
    val cardBg by animateColorAsState(targetCardBg, label = "col_card_bg")
    val tonalBtnBg by animateColorAsState(targetTonalBtnBg, label = "col_tonal_btn")
    val playBtnBg by animateColorAsState(targetPlayBtnBg, label = "col_play_bg")
    val playBtnFg by animateColorAsState(targetPlayBtnFg, label = "col_play_fg")
    val headerPillBg by animateColorAsState(targetHeaderPillBg, label = "col_header_pill")
    val onPagePrimary by animateColorAsState(targetOnPagePrimary, label = "col_on_primary")
    val onPageMuted by animateColorAsState(targetOnPageMuted, label = "col_on_muted")

    val collectionColorScheme = MaterialTheme.colorScheme.copy(
        background = pageBg,
        surface = pageBg,
        onBackground = onPagePrimary,
        onSurface = onPagePrimary,
        onSurfaceVariant = onPageMuted,
        surfaceContainer = cardBg,
        surfaceContainerHigh = cardBg,
        surfaceContainerHighest = tonalBtnBg,
        primary = playBtnBg,
        onPrimary = playBtnFg,
        primaryContainer = tonalBtnBg,
        onPrimaryContainer = onPagePrimary
    )

    // Solo playlists propias del usuario (de su biblioteca, excluyendo playlists editoriales de Apple Music)
    val isUserOwnPlaylist = current != null &&
        current.isPlaylist &&
        (current.type.equals("library-playlists", ignoreCase = true) ||
            seed.type.equals("library-playlists", ignoreCase = true) ||
            seed.id.startsWith("p.")) &&
        !current.artistName.contains("Apple Music", ignoreCase = true)

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingHeaderClearance = statusBarTop + 68.dp
    val topHazeState = rememberHazeState()
    val actionCtrl = LocalMediaActionController.current
    val currentAsItem = current?.let {
        seed.copy(
            title = it.title.ifBlank { seed.title },
            subtitle = it.artistName.ifBlank { seed.subtitle },
            artworkUrl = it.artworkUrl ?: seed.artworkUrl,
            catalogId = it.catalogId ?: seed.catalogId
        )
    } ?: seed
    val favMap = actionCtrl?.favoriteOverrides?.invoke().orEmpty()
    val isFavAlbum = favMap[currentAsItem.id]
        ?: currentAsItem.catalogId?.let { favMap[it] }
        ?: false
    var accountStatus by remember(seed.id) { mutableStateOf<com.applemusic.android.data.model.ItemAccountStatus?>(null) }
    var inLibraryOverride by remember(seed.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(seed.id, current?.id) {
        if (current != null) {
            actionCtrl?.onSyncFavoritesForAlbum?.invoke(current)
            accountStatus = actionCtrl?.loadItemStatus?.invoke(currentAsItem)
        }
    }
    val typeLower = currentAsItem.type.lowercase()
    val inLibrary = inLibraryOverride
        ?: accountStatus?.inLibrary
        ?: (typeLower.startsWith("library-") || currentAsItem.id.startsWith("l.") || currentAsItem.id.startsWith("p."))

    InAppPageContainer(onBack = {
        when {
            relatedSeeAll != null -> relatedSeeAll = null
            showTrackSearch -> {
                showTrackSearch = false
                trackSearchQuery = ""
            }
            else -> onDismiss()
        }
    }) {
        MaterialTheme(colorScheme = collectionColorScheme) {
            CompositionLocalProvider(LocalContentColor provides onPagePrimary) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pageBg)
                ) {
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = playBtnBg)
                        }
                        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("No se pudo cargar el contenido", color = onPageMuted)
                                FilledTonalButton(onClick = { onPlay(seed, seed, 0, false) }) {
                                    Text("Reproducir directamente")
                                }
                            }
                        }
                        else -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .hazeSource(state = topHazeState)
                        ) {
                            // Barra de búsqueda estable (no se desmonta al escribir para que el teclado no se cierre)
                            AnimatedVisibility(visible = showTrackSearch) {
                                ExpressiveSearchPill(
                                    query = trackSearchQuery,
                                    placeholder = if (current.isPlaylist) "Buscar en la playlist" else "Buscar en el álbum",
                                    onQueryChange = { trackSearchQuery = it },
                                    onClear = { trackSearchQuery = "" },
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = floatingHeaderClearance + 4.dp,
                                        bottom = 8.dp
                                    )
                                )
                            }

                            val displayAlbum = remember(current, removedTrackIds) {
                                if (removedTrackIds.isEmpty()) current
                                else current.copy(tracks = current.tracks.filterNot { it.id in removedTrackIds })
                            }
                            CollectionContent(
                                album = displayAlbum,
                                seed = seed,
                                hiResCoverUrl = coverUrl,
                                listState = listState,
                                topPadding = if (showTrackSearch) 4.dp else 0.dp,
                                pageBg = pageBg,
                                trackSort = if (isUserOwnPlaylist) trackSort else UserPlaylistSort.PLAYLIST_ORDER,
                                searchQuery = if (showTrackSearch) trackSearchQuery else "",
                                inLibrary = inLibrary,
                                isUserOwnPlaylist = isUserOwnPlaylist,
                                favoriteOverrides = favMap,
                                baseCoverColor = baseCoverColor,
                                cardBg = cardBg,
                                tonalBtnBg = tonalBtnBg,
                                playBtnBg = playBtnBg,
                                playBtnFg = playBtnFg,
                                onPagePrimary = onPagePrimary,
                                onPageMuted = onPageMuted,
                                onToggleInLibrary = {
                                    inLibraryOverride = !inLibrary
                                    actionCtrl?.onToggleInLibrary?.invoke(currentAsItem, inLibrary, accountStatus?.libraryId)
                                },
                                onPlay = onPlay,
                                onNavigateItem = onNavigateItem,
                                onSeeAllSection = { relatedSeeAll = it },
                                onOpenReplay = onOpenReplay,
                                onTrackRemoved = { trackId -> removedTrackIds = removedTrackIds + trackId },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    TopProgressiveBlur(
                        hazeState = topHazeState,
                        height = floatingHeaderClearance + 14.dp,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // Encabezado flotante estilo captura: atrás redondo + título al hacer scroll + cápsula de acciones
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = headerPillBg,
                            contentColor = onPagePrimary,
                            shadowElevation = 6.dp
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                            }
                        }

                        AnimatedVisibility(
                            visible = showTitlePill,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = headerPillBg,
                                contentColor = onPagePrimary,
                                shadowElevation = 6.dp
                            ) {
                                Text(
                                    text = current?.title?.ifBlank { seed.title } ?: seed.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (current != null) {
                            Surface(
                                shape = CircleShape,
                                color = headerPillBg,
                                contentColor = onPagePrimary,
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                ) {
                                    IconButton(onClick = {
                                        showTrackSearch = !showTrackSearch
                                        if (!showTrackSearch) trackSearchQuery = ""
                                    }) {
                                        Icon(
                                            imageVector = if (showTrackSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                                            contentDescription = if (showTrackSearch) "Cerrar búsqueda" else "Buscar canciones",
                                            tint = onPagePrimary
                                        )
                                    }

                                    if (isUserOwnPlaylist) {
                                        Box {
                                            IconButton(onClick = { showSortMenu = true }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.SwapVert,
                                                    contentDescription = "Ordenar canciones",
                                                    tint = onPagePrimary
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false },
                                                shape = RoundedCornerShape(20.dp),
                                                containerColor = headerPillBg
                                            ) {
                                                Text(
                                                    text = "Ordenar playlist",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = onPageMuted,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                                UserPlaylistSort.entries.forEach { option ->
                                                    val selected = option == trackSort
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = option.label,
                                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                                color = onPagePrimary
                                                            )
                                                        },
                                                        trailingIcon = if (selected) {
                                                            {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Check,
                                                                    contentDescription = null,
                                                                    tint = onPagePrimary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        } else null,
                                                        onClick = {
                                                            trackSort = option
                                                            showSortMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (actionCtrl != null) {
                                        IconButton(onClick = { actionCtrl.onToggleFavorite(currentAsItem) }) {
                                            Icon(
                                                imageVector = if (isFavAlbum) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                contentDescription = if (isFavAlbum) "Quitar de favoritos" else "Agregar a favoritos",
                                                tint = onPagePrimary
                                            )
                                        }
                                        IconButton(onClick = {
                                            actionCtrl.onOpenMenu(
                                                MediaMenuRequest(
                                                    item = currentAsItem,
                                                    albumIdHint = current.catalogId ?: current.id,
                                                    artistIdHint = current.primaryArtistId,
                                                    onNavigateItem = onNavigateItem,
                                                    accentColor = baseCoverColor,
                                                    isOwnPlaylist = isUserOwnPlaylist,
                                                    onPlaylistDeleted = onDismiss
                                                )
                                            )
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Más opciones",
                                                tint = onPagePrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    relatedSeeAll?.let { detailSec ->
                        SectionDetailDialog(
                            section = detailSec,
                            loadFullSection = { detailSec.items },
                            onDismiss = { relatedSeeAll = null },
                            onItemClick = { next ->
                                relatedSeeAll = null
                                onNavigateItem(next)
                            },
                            onPlayClick = { item, shuffle -> onPlay(item, item, 0, shuffle) },
                            onFriendBadgeClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionContent(
    album: AlbumDetail,
    seed: HomeItem,
    hiResCoverUrl: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    topPadding: androidx.compose.ui.unit.Dp,
    pageBg: Color,
    trackSort: UserPlaylistSort,
    searchQuery: String,
    inLibrary: Boolean,
    isUserOwnPlaylist: Boolean,
    favoriteOverrides: Map<String, Boolean>,
    baseCoverColor: Color,
    cardBg: Color,
    tonalBtnBg: Color,
    playBtnBg: Color,
    playBtnFg: Color,
    onPagePrimary: Color,
    onPageMuted: Color,
    onToggleInLibrary: () -> Unit,
    onPlay: (item: HomeItem, container: HomeItem?, startIndex: Int, shuffle: Boolean) -> Unit,
    onNavigateItem: (HomeItem) -> Unit,
    onSeeAllSection: (HomeSection) -> Unit,
    onOpenReplay: (() -> Unit)?,
    onTrackRemoved: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var noteExpanded by remember(album.id) { mutableStateOf(false) }
    val actionCtrl = LocalMediaActionController.current
    var artistAvatarUrl by remember(album.id, album.primaryArtistId) { mutableStateOf<String?>(null) }
    LaunchedEffect(album.id, album.primaryArtistId) {
        val aid = album.primaryArtistId?.takeIf { !album.isPlaylist && it.isNotBlank() }
        if (aid != null) {
            artistAvatarUrl = actionCtrl?.loadArtistArtwork?.invoke(aid)
        }
    }
    var nowMillis by remember(album.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(album.id, album.releaseEpochMillis, album.isPreRelease) {
        val target = album.releaseEpochMillis ?: return@LaunchedEffect
        while (target > System.currentTimeMillis()) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
        nowMillis = System.currentTimeMillis()
    }
    val remainingPreReleaseMs = album.releaseEpochMillis?.let { (it - nowMillis).coerceAtLeast(0L) } ?: 0L
    val availableTrackIndices = remember(album.tracks) {
        album.tracks.mapIndexedNotNull { idx, t -> if (t.isAvailable) idx else null }
    }
    val hasPlayableTracks = !album.isPreRelease || availableTrackIndices.isNotEmpty()
    val firstPlayableIdx = availableTrackIndices.firstOrNull() ?: 0
    val asItem = seed.copy(
        title = album.title.ifBlank { seed.title },
        subtitle = album.artistName.ifBlank { seed.subtitle },
        artworkUrl = album.artworkUrl ?: seed.artworkUrl,
        motionArtworkUrl = album.motionSquareUrl ?: album.motionTallUrl ?: seed.motionArtworkUrl
    )
    val queryTrimmed = searchQuery.trim()
    val multiDisc = queryTrimmed.isEmpty() && !album.isPlaylist && album.tracks.map { it.disc }.distinct().size > 1
    val indexedTracks = remember(album.tracks, trackSort, queryTrimmed) {
        val base = album.tracks.mapIndexed { idx, track -> idx to track }
        val filtered = if (queryTrimmed.isEmpty()) {
            base
        } else {
            base.filter { (_, t) ->
                t.title.contains(queryTrimmed, ignoreCase = true) ||
                    t.artistName.contains(queryTrimmed, ignoreCase = true)
            }
        }
        when (trackSort) {
            UserPlaylistSort.PLAYLIST_ORDER -> filtered
            UserPlaylistSort.RECENT_FIRST -> filtered.reversed()
            UserPlaylistSort.TITLE_ASC -> filtered.sortedBy { it.second.title.lowercase() }
            UserPlaylistSort.TITLE_DESC -> filtered.sortedByDescending { it.second.title.lowercase() }
            UserPlaylistSort.ARTIST_ASC -> filtered.sortedWith(
                compareBy({ it.second.artistName.lowercase() }, { it.second.title.lowercase() })
            )
            UserPlaylistSort.ARTIST_DESC -> filtered.sortedWith(
                compareByDescending<Pair<Int, AlbumTrack>> { it.second.artistName.lowercase() }
                    .thenBy { it.second.title.lowercase() }
            )
            UserPlaylistSort.DURATION_SHORT -> filtered.sortedBy { it.second.durationMillis }
            UserPlaylistSort.DURATION_LONG -> filtered.sortedByDescending { it.second.durationMillis }
        }
    }

    // Tablet / pantalla ancha: cabecera en dos columnas (portada | info) y lista centrada con ancho máximo.
    val adaptive = rememberAdaptiveInfo()
    val tabletLayout = adaptive.isExpandedWidth
    val sideGutter = if (tabletLayout) ((adaptive.widthDp - 1040) / 2).coerceAtLeast(8).dp else 0.dp

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sideGutter, end = sideGutter, top = topPadding, bottom = 188.dp)
    ) {
        if (queryTrimmed.isEmpty()) {
            item(key = "hero_header") {
                val motionHeroUrl = album.motionTallUrl ?: album.motionSquareUrl
                val hasMotionHero = !motionHeroUrl.isNullOrBlank()

                // Título + artista + metadatos + Dolby Atmos (centrado en teléfono, alineado a la izquierda en tablet)
                val titleBlock: @Composable (TextAlign) -> Unit = { textAlign ->
                            Text(
                                text = album.title.ifBlank { seed.title },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                                color = onPagePrimary,
                                textAlign = textAlign,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (album.artistName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                val creatorTarget = when {
                                    !album.isPlaylist && !album.primaryArtistId.isNullOrBlank() ->
                                        HomeItem(id = album.primaryArtistId, type = "artists", title = album.artistName, subtitle = "", artworkUrl = null)
                                    album.isPlaylist && album.curatorItem != null ->
                                        album.curatorItem
                                    else -> null
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = if (creatorTarget != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onNavigateItem(creatorTarget) }
                                            .padding(horizontal = if (textAlign == TextAlign.Start) 0.dp else 10.dp, vertical = 3.dp)
                                    } else Modifier.padding(vertical = 2.dp)
                                ) {
                                    if (!artistAvatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = artistAvatarUrl,
                                            contentDescription = album.artistName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(tonalBtnBg)
                                        )
                                    }
                                    Text(
                                        text = album.artistName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onPagePrimary.copy(alpha = 0.88f),
                                        textAlign = textAlign,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val totalMinutes = (album.totalMillis / 60_000L).coerceAtLeast(0L)
                            val metaLine = listOfNotNull(
                                album.genre?.takeIf { !album.isPlaylist },
                                if (!album.isPlaylist) album.releaseYear else null,
                                "${album.tracks.size} canciones".takeIf { album.tracks.isNotEmpty() },
                                if (totalMinutes >= 60) "${totalMinutes / 60} h ${totalMinutes % 60} min"
                                else if (totalMinutes > 0) "$totalMinutes min" else null
                            ).take(3).joinToString(" · ")

                            if (metaLine.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = metaLine,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onPageMuted,
                                    textAlign = textAlign
                                )
                            }

                            // Próximo estreno: badge + contador regresivo en vivo
                            if (album.isPreRelease) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = tonalBtnBg,
                                    contentColor = onPagePrimary
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalAlignment = if (textAlign == TextAlign.Start) Alignment.Start else Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "PRÓXIMO ESTRENO",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            color = playBtnBg
                                        )
                                        val countdownText = formatCountdown(remainingPreReleaseMs)
                                        val releaseFormatted = album.releaseDate?.let(::formatDate)
                                        Text(
                                            text = when {
                                                countdownText != null -> "Disponible en $countdownText"
                                                releaseFormatted != null -> "Estreno: $releaseFormatted"
                                                else -> "Lanzamiento próximamente"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = onPagePrimary
                                        )
                                    }
                                }
                            }

                            // Símbolo Dolby Atmos si el álbum está disponible en Atmos
                            if (album.hasAtmos) {
                                Spacer(Modifier.height(8.dp))
                                DolbyAtmosBadge(tint = onPagePrimary)
                            }
                }

                // Botones (Aleatorio | Reproducir | Biblioteca), Replay y nota editorial
                val actionsBlock: @Composable () -> Unit = {
                    // Grupo conectado de 3 botones estilo Pixel Expressive (Aleatorio | Reproducir | Favorito ★ — sin descargar)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val leftPill = RoundedCornerShape(
                            topStart = 28.dp,
                            bottomStart = 28.dp,
                            topEnd = 10.dp,
                            bottomEnd = 10.dp
                        )
                        val centerPill = RoundedCornerShape(14.dp)
                        val rightPill = RoundedCornerShape(
                            topStart = 10.dp,
                            bottomStart = 10.dp,
                            topEnd = 28.dp,
                            bottomEnd = 28.dp
                        )

                        Surface(
                            shape = leftPill,
                            color = tonalBtnBg,
                            contentColor = onPagePrimary,
                            modifier = Modifier
                                .size(width = 66.dp, height = 56.dp)
                                .alpha(if (hasPlayableTracks) 1f else 0.42f)
                                .clip(leftPill)
                                .clickable(enabled = hasPlayableTracks) {
                                    onPlay(asItem, asItem, firstPlayableIdx, true)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Aleatorio",
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }

                        Surface(
                            shape = centerPill,
                            color = playBtnBg,
                            contentColor = playBtnFg,
                            modifier = Modifier
                                .size(width = 152.dp, height = 56.dp)
                                .alpha(if (hasPlayableTracks) 1f else 0.42f)
                                .clip(centerPill)
                                .clickable(enabled = hasPlayableTracks) {
                                    onPlay(asItem, asItem, firstPlayableIdx, false)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Reproducir",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = rightPill,
                            color = if (inLibrary) playBtnBg else tonalBtnBg,
                            contentColor = if (inLibrary) playBtnFg else onPagePrimary,
                            modifier = Modifier
                                .size(width = 66.dp, height = 56.dp)
                                .clip(rightPill)
                                .clickable(onClick = onToggleInLibrary)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = if (inLibrary) "En tu biblioteca" else "Agregar a la biblioteca",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    if (onOpenReplay != null && (seed.id.startsWith("pl.rp-") || album.title.contains("Replay", ignoreCase = true))) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = tonalBtnBg,
                            contentColor = onPagePrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(onClick = onOpenReplay)
                        ) {
                            Text(
                                text = "Ver estadísticas de Apple Music Replay",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 13.dp, horizontal = 16.dp)
                            )
                        }
                    }

                    album.editorialNote?.takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onPagePrimary.copy(alpha = 0.80f),
                            maxLines = if (noteExpanded) 20 else 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { noteExpanded = !noteExpanded }
                                .padding(vertical = 2.dp)
                        )
                    }
                }

                if (tabletLayout) {
                    // TABLET: portada cuadrada a la izquierda + información y acciones a la derecha.
                    val coverSize = (adaptive.widthDp * 0.30f).dp.coerceIn(240.dp, 380.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(pageBg)
                            .statusBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 76.dp, bottom = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(36.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = tonalBtnBg,
                            shadowElevation = 16.dp,
                            modifier = Modifier.size(coverSize)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (!hiResCoverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = hiResCoverUrl,
                                        contentDescription = album.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                val squareMotion = album.motionSquareUrl ?: album.motionTallUrl
                                if (!squareMotion.isNullOrBlank()) {
                                    MotionArtworkVideo(
                                        videoUrl = squareMotion,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            titleBlock(TextAlign.Start)
                            Spacer(Modifier.height(20.dp))
                            actionsBlock()
                        }
                    }
                } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Portada/Video full-bleed recortado limpiamente sin franjas, más alto cuando hay video animado
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(adaptiveHeroHeight(if (hasMotionHero) 560.dp else 460.dp))
                            .clipToBounds()
                            .background(pageBg)
                    ) {
                        if (!hiResCoverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = hiResCoverUrl,
                                contentDescription = album.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (hasMotionHero) {
                            MotionArtworkVideo(
                                videoUrl = motionHeroUrl,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.22f),
                                        0.24f to Color.Transparent,
                                        (if (hasMotionHero) 0.56f else 0.52f) to Color.Transparent,
                                        (if (hasMotionHero) 0.74f else 0.72f) to pageBg.copy(alpha = 0.68f),
                                        0.89f to pageBg.copy(alpha = 0.96f),
                                        1f to pageBg
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            titleBlock(TextAlign.Center)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(pageBg)
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(10.dp))
                        actionsBlock()
                        Spacer(Modifier.height(18.dp))
                    }
                }
                }
            }
        }

        if (indexedTracks.isEmpty() && queryTrimmed.isNotEmpty()) {
            item(key = "empty_track_search") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron canciones para \"$queryTrimmed\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = onPageMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        var lastDisc = 0
        indexedTracks.forEachIndexed { displayIdx, (originalIndex, track) ->
            val isNewDisc = multiDisc && track.disc != lastDisc
            if (isNewDisc) {
                val disc = track.disc
                item(key = "disc_$disc") {
                    Text(
                        text = "Disco $disc",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = onPagePrimary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
                    )
                }
            }
            val nextDisc = indexedTracks.getOrNull(displayIdx + 1)?.second?.disc
            val isFirstInGroup = displayIdx == 0 || isNewDisc
            val isLastInGroup = displayIdx == indexedTracks.lastIndex || (multiDisc && nextDisc != track.disc)
            lastDisc = track.disc

            item(key = "t_${track.disc}_${track.number}_${track.id}_${originalIndex}_$displayIdx") {
                val trackItem = asItem.copy(
                    id = track.id,
                    type = track.type,
                    title = track.title,
                    subtitle = track.artistName,
                    artworkUrl = track.artworkUrl ?: asItem.artworkUrl,
                    catalogId = track.catalogId ?: track.id.takeIf { it.all(Char::isDigit) },
                    motionArtworkUrl = if (!album.isPlaylist) asItem.motionArtworkUrl else null
                )
                val isTrackFavorite = favoriteOverrides[track.id]
                    ?: track.catalogId?.let { favoriteOverrides[it] }
                    ?: false
                TrackCardRow(
                    track = track,
                    showArtwork = album.isPlaylist,
                    albumArtist = album.artistName,
                    trackItem = trackItem,
                    isTrackFavorite = isTrackFavorite,
                    shape = segmentedRowShape(isFirst = isFirstInGroup, isLast = isLastInGroup),
                    cardBg = cardBg,
                    tonalBg = tonalBtnBg,
                    onPagePrimary = onPagePrimary,
                    onPageMuted = onPageMuted,
                    baseCoverColor = baseCoverColor,
                    albumIdHint = album.catalogId ?: album.id,
                    artistIdHint = album.primaryArtistId,
                    playlistOwnerId = if (isUserOwnPlaylist) album.id else null,
                    onTrackRemoved = { onTrackRemoved(track.id) },
                    onNavigateItem = onNavigateItem
                ) {
                    if (track.isAvailable) {
                        onPlay(
                            trackItem,
                            asItem,
                            originalIndex,
                            false
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        item(key = "footer_meta") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val date = if (!album.isPlaylist) album.releaseDate?.let { formatDate(it) } else null
                val minutes = album.totalMillis / 60_000
                listOfNotNull(
                    date,
                    "${album.tracks.size} canciones · $minutes minutos",
                    album.copyright
                ).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = onPageMuted
                    )
                }
            }
        }

        if (queryTrimmed.isEmpty() && album.relatedSections.isNotEmpty()) {
            album.relatedSections.forEach { related ->
                item(key = "rel_${related.id}") {
                    Spacer(Modifier.height(10.dp))
                    HomeSectionBlock(
                        section = related,
                        isReplaySection = false,
                        onSectionClick = { if (related.canLoadMore) onSeeAllSection(related) },
                        onSeeAllClick = { onSeeAllSection(related) },
                        onItemClick = { next -> onNavigateItem(next) },
                        onFriendBadgeClick = {}
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackCardRow(
    track: AlbumTrack,
    showArtwork: Boolean,
    albumArtist: String,
    trackItem: HomeItem,
    isTrackFavorite: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    cardBg: Color,
    tonalBg: Color,
    onPagePrimary: Color,
    onPageMuted: Color,
    baseCoverColor: Color,
    albumIdHint: String?,
    artistIdHint: String?,
    playlistOwnerId: String? = null,
    onTrackRemoved: (() -> Unit)? = null,
    onNavigateItem: (HomeItem) -> Unit,
    onClick: () -> Unit
) {
    val actionCtrl = LocalMediaActionController.current
    val available = track.isAvailable
    Surface(
        shape = shape,
        color = cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .alpha(if (available) 1f else 0.44f)
            .clip(shape)
            .clickable(enabled = available, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showArtwork && !track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tonalBg)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.width(38.dp)
                ) {
                    if (isTrackFavorite) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Favorita",
                            tint = onPagePrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text = track.number.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = onPageMuted,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onPagePrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isExplicit) {
                        Box(
                            modifier = Modifier
                                .background(tonalBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "E",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = onPagePrimary
                            )
                        }
                    }
                    if (showArtwork && isTrackFavorite) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Favorita",
                            tint = onPagePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                val sub = if (showArtwork) {
                    track.artistName.takeIf { it.isNotBlank() }.orEmpty()
                } else {
                    track.artistName.takeIf { it.isNotBlank() && it != albumArtist }.orEmpty()
                }
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = onPageMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = if (track.isAvailable) formatDuration(track.durationMillis) else "Próximamente",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = onPageMuted
            )

            if (actionCtrl != null && track.isAvailable) {
                IconButton(
                    onClick = {
                        actionCtrl.onOpenMenu(
                            MediaMenuRequest(
                                item = trackItem,
                                albumIdHint = albumIdHint,
                                artistIdHint = artistIdHint,
                                onNavigateItem = onNavigateItem,
                                accentColor = baseCoverColor,
                                playlistOwnerIdForRemoval = playlistOwnerId,
                                onTrackRemovedFromPlaylist = onTrackRemoved
                            )
                        )
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Más opciones",
                        tint = onPagePrimary.copy(alpha = 0.78f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

private fun toHighResCollectionArtworkUrl(url: String): String {
    val expanded = url
        .replace("{w}", "1200")
        .replace("{h}", "1200")
        .replace("{c}", "bb")
        .replace("{f}", "jpg")
    return expanded.replace(Regex("/\\d+x\\d+([a-zA-Z]*)\\."), "/1200x1200$1.")
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun formatDate(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX")))
}.getOrDefault(iso)

private fun formatCountdown(remainingMs: Long): String? {
    if (remainingMs <= 0L) return null
    val totalSec = remainingMs / 1000L
    val days = totalSec / 86_400L
    val hours = (totalSec % 86_400L) / 3_600L
    val mins = (totalSec % 3_600L) / 60L
    val secs = totalSec % 60L
    return if (days > 0L) {
        "%dd %02dh %02dm %02ds".format(days, hours, mins, secs)
    } else {
        "%02dh %02dm %02ds".format(hours, mins, secs)
    }
}
