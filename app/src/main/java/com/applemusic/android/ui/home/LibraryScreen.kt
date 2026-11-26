package com.applemusic.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusic.android.data.model.AlbumDetail
import com.applemusic.android.data.model.ArtistDetail
import com.applemusic.android.data.model.ConnectionMode
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection
import com.applemusic.android.data.model.ReplayPeriodOption
import com.applemusic.android.data.model.ReplaySummary
import com.applemusic.android.data.model.SocialProfileDetail
import com.applemusic.android.data.repository.LibraryCategoryPage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private enum class LibrarySortOption(val label: String) {
    RECENT("Agregado recientemente"),
    OLDEST("Más antiguos primero"),
    TITLE_ASC("Título (A–Z)"),
    TITLE_DESC("Título (Z–A)"),
    ARTIST_ASC("Artista (A–Z)"),
    ARTIST_DESC("Artista (Z–A)"),
    USER_FIRST("Mis playlists primero"),
    APPLE_FIRST("Apple Music primero")
}

private fun sortOptionsFor(tab: LibrarySectionTab): List<LibrarySortOption> = when (tab) {
    LibrarySectionTab.ALBUMS -> listOf(
        LibrarySortOption.RECENT,
        LibrarySortOption.OLDEST,
        LibrarySortOption.TITLE_ASC,
        LibrarySortOption.TITLE_DESC,
        LibrarySortOption.ARTIST_ASC,
        LibrarySortOption.ARTIST_DESC
    )
    LibrarySectionTab.PLAYLISTS -> listOf(
        LibrarySortOption.RECENT,
        LibrarySortOption.OLDEST,
        LibrarySortOption.USER_FIRST,
        LibrarySortOption.APPLE_FIRST,
        LibrarySortOption.TITLE_ASC,
        LibrarySortOption.TITLE_DESC
    )
    LibrarySectionTab.ARTISTS -> listOf(
        LibrarySortOption.RECENT,
        LibrarySortOption.OLDEST,
        LibrarySortOption.TITLE_ASC,
        LibrarySortOption.TITLE_DESC
    )
    LibrarySectionTab.SONGS -> listOf(
        LibrarySortOption.RECENT,
        LibrarySortOption.OLDEST,
        LibrarySortOption.TITLE_ASC,
        LibrarySortOption.TITLE_DESC,
        LibrarySortOption.ARTIST_ASC,
        LibrarySortOption.ARTIST_DESC
    )
    LibrarySectionTab.RECENT -> emptyList()
}

private fun List<HomeItem>.applyLibrarySort(sort: LibrarySortOption): List<HomeItem> = when (sort) {
    LibrarySortOption.RECENT -> this
    LibrarySortOption.OLDEST -> reversed()
    LibrarySortOption.TITLE_ASC -> sortedBy { it.title.lowercase() }
    LibrarySortOption.TITLE_DESC -> sortedByDescending { it.title.lowercase() }
    LibrarySortOption.ARTIST_ASC -> sortedWith(compareBy({ it.subtitle.lowercase() }, { it.title.lowercase() }))
    LibrarySortOption.ARTIST_DESC -> sortedWith(compareByDescending<HomeItem> { it.subtitle.lowercase() }.thenBy { it.title.lowercase() })
    LibrarySortOption.USER_FIRST -> sortedWith(
        compareBy<HomeItem> { it.subtitle.contains("Apple Music", ignoreCase = true) }
            .thenBy { it.title.lowercase() }
    )
    LibrarySortOption.APPLE_FIRST -> sortedWith(
        compareBy<HomeItem> { !it.subtitle.contains("Apple Music", ignoreCase = true) }
            .thenBy { it.title.lowercase() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    mode: ConnectionMode,
    onRefresh: () -> Unit,
    onSelectCategory: (LibrarySectionTab) -> Unit,
    onFilterChange: (String) -> Unit,
    onLoadMore: (LibrarySectionTab) -> Unit,
    onOpenAccountSheet: () -> Unit,
    onConnectWebSession: () -> Unit,
    onPlayItem: (item: HomeItem, container: HomeItem?, startIndex: Int, shuffle: Boolean) -> Unit,
    userAvatarUrl: String? = null,
    loadFullSection: suspend (HomeSection) -> List<HomeItem> = { it.items },
    loadMediaDetail: suspend (HomeItem) -> AlbumDetail? = { null },
    loadArtist: suspend (String) -> ArtistDetail? = { null },
    loadReplay: suspend (Int?, ReplayPeriodOption?) -> ReplaySummary? = { _, _ -> null },
    loadSocialProfile: suspend (String) -> SocialProfileDetail? = { null },
    loadEditorialSubpage: suspend (HomeItem) -> List<HomeSection> = { emptyList() }
) {
    val nav = rememberMediaNavigationState(onPlayItem)
    val pullToRefreshState = rememberPullToRefreshState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingHeaderClearance = statusBarTop + 76.dp
    val topHazeState = rememberHazeState()
    val isInSubcategory = uiState.selectedCategory != LibrarySectionTab.RECENT

    var albumsSort by remember { mutableStateOf(LibrarySortOption.RECENT) }
    var playlistsSort by remember { mutableStateOf(LibrarySortOption.RECENT) }
    var artistsSort by remember { mutableStateOf(LibrarySortOption.RECENT) }
    var songsSort by remember { mutableStateOf(LibrarySortOption.RECENT) }

    val activeSort = when (uiState.selectedCategory) {
        LibrarySectionTab.ALBUMS -> albumsSort
        LibrarySectionTab.PLAYLISTS -> playlistsSort
        LibrarySectionTab.ARTISTS -> artistsSort
        LibrarySectionTab.SONGS -> songsSort
        LibrarySectionTab.RECENT -> LibrarySortOption.RECENT
    }
    val onSelectSort: (LibrarySortOption) -> Unit = { chosen ->
        when (uiState.selectedCategory) {
            LibrarySectionTab.ALBUMS -> albumsSort = chosen
            LibrarySectionTab.PLAYLISTS -> playlistsSort = chosen
            LibrarySectionTab.ARTISTS -> artistsSort = chosen
            LibrarySectionTab.SONGS -> songsSort = chosen
            LibrarySectionTab.RECENT -> Unit
        }
    }

    BackHandler(enabled = isInSubcategory) {
        onFilterChange("")
        onSelectCategory(LibrarySectionTab.RECENT)
    }

    val queryTrimmed = uiState.filterQuery.trim()
    val filteredPage: LibraryCategoryPage = remember(uiState, queryTrimmed, activeSort) {
        val source = when (uiState.selectedCategory) {
            LibrarySectionTab.RECENT -> uiState.recentlyAdded
            LibrarySectionTab.PLAYLISTS -> uiState.playlists
            LibrarySectionTab.ALBUMS -> uiState.albums
            LibrarySectionTab.ARTISTS -> uiState.artists
            LibrarySectionTab.SONGS -> uiState.songs
        }
        val filtered = if (queryTrimmed.isEmpty()) {
            source.items
        } else {
            source.items.filter {
                it.title.contains(queryTrimmed, ignoreCase = true) ||
                    it.subtitle.contains(queryTrimmed, ignoreCase = true)
            }
        }
        source.copy(items = filtered.applyLibrarySort(activeSort))
    }

    val appStrings = com.applemusic.android.ui.theme.LocalAppStrings.current
    val searchPlaceholder = when (uiState.selectedCategory) {
        LibrarySectionTab.RECENT -> appStrings.filterLibraryPlaceholder
        LibrarySectionTab.PLAYLISTS -> if (appStrings.isEnglish) "Find in Playlists" else "Buscar en playlists"
        LibrarySectionTab.ALBUMS -> if (appStrings.isEnglish) "Find in Albums" else "Buscar en álbumes"
        LibrarySectionTab.ARTISTS -> if (appStrings.isEnglish) "Find in Artists" else "Buscar en artistas"
        LibrarySectionTab.SONGS -> if (appStrings.isEnglish) "Find in Songs" else "Buscar en canciones"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = floatingHeaderClearance)
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = topHazeState)
        ) {
            when {
                mode != ConnectionMode.PERSONALIZED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = floatingHeaderClearance, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ConnectAppleMusicBanner(mode = mode, onConnect = onConnectWebSession)
                        Text(
                            text = "Conecta tu cuenta de Apple Music para ver tus playlists, álbumes, artistas y canciones guardadas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                uiState.isLoading && !uiState.hasLoaded -> {
                    HomeLoadingExpressive(label = "Cargando tu biblioteca...")
                }

                else -> {
                    // La barra de búsqueda permanece siempre montada arriba para que el teclado nunca se cierre al escribir
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = floatingHeaderClearance)
                    ) {
                        ExpressiveSearchPill(
                            query = uiState.filterQuery,
                            placeholder = searchPlaceholder,
                            onQueryChange = onFilterChange,
                            onClear = { onFilterChange("") },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when {
                                queryTrimmed.length >= 2 && uiState.librarySearchResults.isNotEmpty() -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 4.dp, bottom = 188.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        items(uiState.librarySearchResults, key = { it.id }) { section ->
                                            HomeSectionBlock(
                                                section = section,
                                                isReplaySection = false,
                                                onSectionClick = { nav.activeDetailSection = section },
                                                onSeeAllClick = { nav.activeDetailSection = section },
                                                onItemClick = { nav.routeItem(it) },
                                                onFriendBadgeClick = nav::openFriendBadge
                                            )
                                        }
                                    }
                                }

                                uiState.isSearchingLibrary -> {
                                    HomeLoadingExpressive(label = "Buscando en tu biblioteca...")
                                }

                                uiState.selectedCategory == LibrarySectionTab.ARTISTS -> {
                                    LibraryArtistsList(
                                        page = filteredPage,
                                        isLoadingMore = uiState.isLoadingMore,
                                        onArtistClick = { nav.routeItem(it) },
                                        onLoadMore = { onLoadMore(LibrarySectionTab.ARTISTS) }
                                    )
                                }

                                uiState.selectedCategory == LibrarySectionTab.SONGS -> {
                                    LibrarySongsList(
                                        page = filteredPage,
                                        isLoadingMore = uiState.isLoadingMore,
                                        onSongClick = { nav.routeItem(it) },
                                        onPlayFirst = { shuffle ->
                                            val songs = filteredPage.items
                                            val chosen = if (shuffle && songs.isNotEmpty()) songs.random() else songs.firstOrNull()
                                            if (chosen != null) onPlayItem(chosen, null, 0, shuffle)
                                        },
                                        onLoadMore = { onLoadMore(LibrarySectionTab.SONGS) }
                                    )
                                }

                                uiState.selectedCategory == LibrarySectionTab.RECENT -> {
                                    LibraryCollectionGrid(
                                        page = filteredPage,
                                        isLoadingMore = uiState.isLoadingMore,
                                        headerContent = if (queryTrimmed.isEmpty()) {
                                            {
                                                if (uiState.pinned.isNotEmpty()) {
                                                    LibraryPinnedSection(
                                                        items = uiState.pinned,
                                                        onItemClick = { nav.routeItem(it) }
                                                    )
                                                    Spacer(modifier = Modifier.height(20.dp))
                                                }
                                                LibraryCategoryMenu(
                                                    playlistsCount = countLabel(uiState.playlists),
                                                    artistsCount = countLabel(uiState.artists),
                                                    albumsCount = countLabel(uiState.albums),
                                                    songsCount = countLabel(uiState.songs),
                                                    onSelectCategory = { tab ->
                                                        onFilterChange("")
                                                        onSelectCategory(tab)
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(22.dp))
                                                Text(
                                                    text = com.applemusic.android.ui.theme.LocalAppStrings.current.recentlyAdded,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = (-0.3).sp
                                                )
                                            }
                                        } else null,
                                        onItemClick = { nav.routeItem(it) },
                                        onLoadMore = { onLoadMore(LibrarySectionTab.RECENT) }
                                    )
                                }

                                else -> {
                                    LibraryCollectionGrid(
                                        page = filteredPage,
                                        isLoadingMore = uiState.isLoadingMore,
                                        onItemClick = { nav.routeItem(it) },
                                        onLoadMore = { onLoadMore(uiState.selectedCategory) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        TopProgressiveBlur(
            hazeState = topHazeState,
            height = floatingHeaderClearance + 14.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (isInSubcategory) {
            val subTitle = when (uiState.selectedCategory) {
                LibrarySectionTab.PLAYLISTS -> appStrings.playlists
                LibrarySectionTab.ALBUMS -> appStrings.albums
                LibrarySectionTab.ARTISTS -> appStrings.artists
                LibrarySectionTab.SONGS -> appStrings.songs
                LibrarySectionTab.RECENT -> appStrings.recentlyAdded
            }
            LibrarySubcategoryHeader(
                title = subTitle,
                mode = mode,
                isBusy = uiState.isLoading || uiState.isRefreshing || uiState.isLoadingMore,
                sortOptions = sortOptionsFor(uiState.selectedCategory),
                activeSort = activeSort,
                onSelectSort = onSelectSort,
                onBack = {
                    onFilterChange("")
                    onSelectCategory(LibrarySectionTab.RECENT)
                },
                onRefresh = onRefresh,
                onOpenAccountSheet = onOpenAccountSheet,
                userAvatarUrl = userAvatarUrl,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } else {
            FloatingTabHeader(
                title = appStrings.tabLibrary,
                mode = mode,
                isBusy = uiState.isLoading || uiState.isRefreshing || uiState.isLoadingMore,
                onRefresh = onRefresh,
                onOpenAccountSheet = onOpenAccountSheet,
                userAvatarUrl = userAvatarUrl,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Páginas de detalle integradas dentro de la pestaña (no flotantes)
        MediaNavigationDialogs(
            nav = nav,
            onPlayItem = onPlayItem,
            loadFullSection = loadFullSection,
            loadMediaDetail = loadMediaDetail,
            loadArtist = loadArtist,
            loadReplay = loadReplay,
            loadSocialProfile = loadSocialProfile,
            loadEditorialSubpage = loadEditorialSubpage
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibrarySubcategoryHeader(
    title: String,
    mode: ConnectionMode,
    isBusy: Boolean,
    sortOptions: List<LibrarySortOption>,
    activeSort: LibrarySortOption,
    onSelectSort: (LibrarySortOption) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    userAvatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver a Biblioteca"
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (sortOptions.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Ordenar",
                                tint = if (activeSort != LibrarySortOption.RECENT) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "Ordenar por",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            sortOptions.forEach { option ->
                                val selected = option == activeSort
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    trailingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onSelectSort(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualizar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                UserAccountAvatarButton(
                    mode = mode,
                    avatarUrl = userAvatarUrl,
                    isBusy = isBusy,
                    onClick = onOpenAccountSheet
                )
            }
        }
    }
}

/** Barra de búsqueda compacta en píldora estilo Material 3 Expressive (sin bordes rígidos). */
@Composable
internal fun ExpressiveSearchPill(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSubmit?.invoke()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Limpiar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

private fun countLabel(page: LibraryCategoryPage): String =
    if (page.items.isEmpty()) "0"
    else if (!page.nextUrl.isNullOrBlank()) "${page.items.size}+"
    else "${page.items.size}"

private data class LibraryMenuEntry(
    val icon: ImageVector,
    val label: String,
    val count: String,
    val tab: LibrarySectionTab
)

/** Lista segmentada estilo Apple Music × Material 3 Expressive (Playlists, Artistas, Álbumes, Canciones). */
@Composable
private fun LibraryCategoryMenu(
    playlistsCount: String,
    artistsCount: String,
    albumsCount: String,
    songsCount: String,
    onSelectCategory: (LibrarySectionTab) -> Unit
) {
    val strings = com.applemusic.android.ui.theme.LocalAppStrings.current
    val entries = listOf(
        LibraryMenuEntry(Icons.AutoMirrored.Rounded.PlaylistPlay, strings.playlists, playlistsCount, LibrarySectionTab.PLAYLISTS),
        LibraryMenuEntry(Icons.Rounded.Person, strings.artists, artistsCount, LibrarySectionTab.ARTISTS),
        LibraryMenuEntry(Icons.Rounded.Album, strings.albums, albumsCount, LibrarySectionTab.ALBUMS),
        LibraryMenuEntry(Icons.Rounded.MusicNote, strings.songs, songsCount, LibrarySectionTab.SONGS)
    )

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        entries.forEachIndexed { index, entry ->
            val isFirst = index == 0
            val isLast = index == entries.lastIndex
            val rowShape = RoundedCornerShape(
                topStart = if (isFirst) 22.dp else 6.dp,
                topEnd = if (isFirst) 22.dp else 6.dp,
                bottomStart = if (isLast) 22.dp else 6.dp,
                bottomEnd = if (isLast) 22.dp else 6.dp
            )
            Surface(
                shape = rowShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .clickable { onSelectCategory(entry.tab) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = entry.count,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryCollectionGrid(
    page: LibraryCategoryPage,
    isLoadingMore: Boolean,
    headerContent: (@Composable () -> Unit)? = null,
    onItemClick: (HomeItem) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 132.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (headerContent != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "lib_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    headerContent()
                }
            }
        }
        if (page.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "lib_empty") {
                EmptyLibraryPlaceholder("No hay elementos en esta sección.")
            }
        } else {
            gridItems(
                items = page.items,
                key = { "lib_${it.type}_${it.id}" }
            ) { item ->
                val accent = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item) }
                ) {
                    ArtworkBox(
                        artworkUrl = item.artworkUrl,
                        fallbackColor = accent,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 0.6.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (!page.nextUrl.isNullOrBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "lib_load_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !isLoadingMore,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Cargar más")
                    }
                }
            }
        }
    }
}

/** Lista de artistas fiel a Apple Music en formato segmentado Material 3 Expressive. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryArtistsList(
    page: LibraryCategoryPage,
    isLoadingMore: Boolean,
    onArtistClick: (HomeItem) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 132.dp
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (page.items.isEmpty()) {
            item(key = "artists_empty") {
                EmptyLibraryPlaceholder("No hay artistas en tu biblioteca.")
            }
        } else {
            itemsIndexed(
                items = page.items,
                key = { _, item -> "lib_art_${item.id}" }
            ) { index, item ->
                val accent = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                val isFirst = index == 0
                val isLast = index == page.items.lastIndex
                val rowShape = RoundedCornerShape(
                    topStart = if (isFirst) 22.dp else 6.dp,
                    topEnd = if (isFirst) 22.dp else 6.dp,
                    bottomStart = if (isLast) 22.dp else 6.dp,
                    bottomEnd = if (isLast) 22.dp else 6.dp
                )
                Surface(
                    shape = rowShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .clickable { onArtistClick(item) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val artistPhotoShape = RoundedCornerShape(14.dp)
                        ArtworkBox(
                            artworkUrl = item.artworkUrl,
                            fallbackColor = accent,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(artistPhotoShape)
                                .border(
                                    width = 0.8.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                    shape = artistPhotoShape
                                )
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (!page.nextUrl.isNullOrBlank()) {
            item(key = "lib_art_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(onClick = onLoadMore, enabled = !isLoadingMore, shapes = ButtonDefaults.shapes()) {
                        Text("Cargar más")
                    }
                }
            }
        }
    }
}

/** Lista de canciones estilo Apple Music (Reproducir + Aleatorio) en tarjetas segmentadas M3 Expressive. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibrarySongsList(
    page: LibraryCategoryPage,
    isLoadingMore: Boolean,
    onSongClick: (HomeItem) -> Unit,
    onPlayFirst: (Boolean) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 132.dp
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (page.items.isNotEmpty()) {
            item(key = "songs_actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val songStrings = com.applemusic.android.ui.theme.LocalAppStrings.current
                    Button(
                        onClick = { onPlayFirst(false) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(songStrings.play, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { onPlayFirst(true) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(songStrings.shuffle, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (page.items.isEmpty()) {
            item(key = "songs_empty") {
                EmptyLibraryPlaceholder("No hay canciones en tu biblioteca.")
            }
        } else {
            itemsIndexed(
                items = page.items,
                key = { _, song -> "lib_song_${song.id}" }
            ) { index, song ->
                val isFirst = index == 0
                val isLast = index == page.items.lastIndex
                val rowShape = RoundedCornerShape(
                    topStart = if (isFirst) 22.dp else 6.dp,
                    topEnd = if (isFirst) 22.dp else 6.dp,
                    bottomStart = if (isLast) 22.dp else 6.dp,
                    bottomEnd = if (isLast) 22.dp else 6.dp
                )
                ExpressiveMediaRow(
                    item = song,
                    shape = rowShape,
                    onClick = { onSongClick(song) }
                )
            }
        }

        if (!page.nextUrl.isNullOrBlank()) {
            item(key = "lib_songs_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(onClick = onLoadMore, enabled = !isLoadingMore, shapes = ButtonDefaults.shapes()) {
                        Text("Cargar más canciones")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
private fun LibraryPinnedSection(
    items: List<HomeItem>,
    onItemClick: (HomeItem) -> Unit
) {
    val actionCtrl = LocalMediaActionController.current
    val adaptive = rememberAdaptiveInfo()
    val pinnedItems = remember(items) { items.take(6) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = com.applemusic.android.ui.theme.LocalAppStrings.current.pinned,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (adaptive.isTablet || adaptive.isExpandedWidth) {
            // En tablet usar tamaño compacto fijo (116.dp) en fila horizontal para que no se vean enormes.
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pinnedItems, key = { "pin_${it.type}_${it.id}" }) { item ->
                    LibraryPinnedCard(
                        item = item,
                        onItemClick = { onItemClick(item) },
                        onTogglePin = { actionCtrl?.onTogglePin?.invoke(item) },
                        modifier = Modifier.width(116.dp)
                    )
                }
            }
        } else {
            val rows = remember(pinnedItems) { pinnedItems.chunked(3) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            LibraryPinnedCard(
                                item = item,
                                onItemClick = { onItemClick(item) },
                                onTogglePin = { actionCtrl?.onTogglePin?.invoke(item) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryPinnedCard(
    item: HomeItem,
    onItemClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fallback = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onItemClick),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            ArtworkBox(
                artworkUrl = item.artworkUrl,
                fallbackColor = fallback,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePin)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
