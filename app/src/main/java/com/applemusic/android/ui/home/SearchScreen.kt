package com.applemusic.android.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.applemusic.android.data.model.AlbumDetail
import com.applemusic.android.data.model.ArtistDetail
import com.applemusic.android.data.model.ConnectionMode
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection
import com.applemusic.android.data.model.HomeSectionStyle
import com.applemusic.android.data.model.ReplayPeriodOption
import com.applemusic.android.data.model.ReplaySummary
import com.applemusic.android.data.model.SocialProfileDetail
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// Paleta editorial sobria (tonos profundos tipo Apple Music) solo como respaldo si una categoría no trae artwork
private val EDITORIAL_CATEGORY_TONES = listOf(
    Color(0xFF7B2D43),
    Color(0xFF2E4A62),
    Color(0xFF4C3B6E),
    Color(0xFF285950),
    Color(0xFF8C4A2F),
    Color(0xFF39466B),
    Color(0xFF6B3E52),
    Color(0xFF3A5A40)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    mode: ConnectionMode,
    onQueryChange: (String) -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onClearRecentSearches: () -> Unit,
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
    val focusManager = LocalFocusManager.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingHeaderClearance = statusBarTop + 76.dp
    val topHazeState = rememberHazeState()
    var selectedSectionFilter by remember(uiState.results) { mutableStateOf<String?>(null) }

    val appStrings = com.applemusic.android.ui.theme.LocalAppStrings.current
    val searchPlaceholder = if (uiState.scope == SearchScope.LIBRARY) {
        appStrings.searchLibraryPlaceholder
    } else {
        appStrings.searchCatalogPlaceholder
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Barra de búsqueda y selector permanecen siempre montados arriba para que el teclado nunca se cierre al escribir
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = topHazeState)
                .padding(top = floatingHeaderClearance)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExpressiveSearchPill(
                    query = uiState.query,
                    placeholder = searchPlaceholder,
                    onQueryChange = onQueryChange,
                    onClear = {
                        selectedSectionFilter = null
                        onClearSearch()
                    },
                    onSubmit = { onSubmitSearch(uiState.query) }
                )
                SearchScopeSegmentedBar(
                    selected = uiState.scope,
                    onSelect = onScopeChange
                )
                if (uiState.scope == SearchScope.LIBRARY && mode != ConnectionMode.PERSONALIZED) {
                    ConnectAppleMusicBanner(mode = mode, onConnect = onConnectWebSession)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    uiState.isSearching -> {
                        HomeLoadingExpressive(label = appStrings.searchingLabel)
                    }

                    uiState.query.isNotBlank() && !uiState.hasSearched -> {
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
                            if (uiState.suggestions.terms.isNotEmpty()) {
                                itemsIndexed(
                                    items = uiState.suggestions.terms,
                                    key = { _, term -> "term_$term" }
                                ) { index, term ->
                                    val shape = segmentedRowShape(
                                        isFirst = index == 0,
                                        isLast = index == uiState.suggestions.terms.lastIndex
                                    )
                                    Surface(
                                        shape = shape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .clickable {
                                                focusManager.clearFocus()
                                                onSubmitSearch(term)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 13.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                                                modifier = Modifier.size(19.dp)
                                            )
                                            Text(
                                                text = term,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (uiState.suggestions.topResults.isNotEmpty()) {
                                item(key = "sug_header") {
                                    Text(
                                        text = "Resultados principales",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
                                    )
                                }
                                itemsIndexed(
                                    items = uiState.suggestions.topResults,
                                    key = { _, item -> "sug_${item.type}_${item.id}" }
                                ) { index, item ->
                                    val shape = segmentedRowShape(
                                        isFirst = index == 0,
                                        isLast = index == uiState.suggestions.topResults.lastIndex
                                    )
                                    ExpressiveMediaRow(
                                        item = item,
                                        shape = shape,
                                        onClick = {
                                            focusManager.clearFocus()
                                            nav.routeItem(item)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    uiState.hasSearched -> {
                        if (uiState.errorMessage != null && uiState.results.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.errorMessage,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (uiState.results.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sin resultados para \"${uiState.query}\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val filteredSections = if (selectedSectionFilter == null) {
                                uiState.results
                            } else {
                                uiState.results.filter { it.id == selectedSectionFilter }
                            }
                            val showingSingleFilteredList = selectedSectionFilter != null && filteredSections.size == 1

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 188.dp),
                                verticalArrangement = Arrangement.spacedBy(if (showingSingleFilteredList) 3.dp else 22.dp)
                            ) {
                                item(key = "result_filters") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(
                                                start = 16.dp,
                                                end = 16.dp,
                                                bottom = if (showingSingleFilteredList) 10.dp else 0.dp
                                            ),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ResultFilterPill(
                                            label = if (appStrings.isEnglish) "All" else "Todo",
                                            selected = selectedSectionFilter == null,
                                            onClick = { selectedSectionFilter = null }
                                        )
                                        uiState.results.forEach { sec ->
                                            ResultFilterPill(
                                                label = sec.title,
                                                selected = selectedSectionFilter == sec.id,
                                                onClick = {
                                                    selectedSectionFilter = if (selectedSectionFilter == sec.id) null else sec.id
                                                }
                                            )
                                        }
                                    }
                                }

                                if (showingSingleFilteredList) {
                                    val chosen = filteredSections[0]
                                    itemsIndexed(
                                        items = chosen.items,
                                        key = { _, item -> "filtered_${chosen.id}_${item.type}_${item.id}" }
                                    ) { index, item ->
                                        val shape = segmentedRowShape(
                                            isFirst = index == 0,
                                            isLast = index == chosen.items.lastIndex
                                        )
                                        ExpressiveMediaRow(
                                            item = item,
                                            shape = shape,
                                            onClick = { nav.routeItem(item) },
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                } else {
                                    // Mejor resultado destacado al inicio de la vista "Todo"
                                    val topSection = filteredSections.firstOrNull()
                                    val topResultItem = topSection?.items?.firstOrNull()
                                    if (topResultItem != null) {
                                        item(key = "top_result_spotlight_${topResultItem.type}_${topResultItem.id}") {
                                            SearchTopResultCard(
                                                item = topResultItem,
                                                onClick = { nav.routeItem(topResultItem) },
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    }

                                    filteredSections.forEach { section ->
                                        val isSongsSection = section.style == HomeSectionStyle.COMPACT_TRACKS ||
                                            (section.items.isNotEmpty() && section.items.all { it.type.contains("song", ignoreCase = true) })
                                        val isArtistsSection = section.style == HomeSectionStyle.ARTIST_CIRCLES ||
                                            (section.items.isNotEmpty() && section.items.all {
                                                it.type.contains("artist", ignoreCase = true) ||
                                                    it.type.equals("social-profiles", ignoreCase = true)
                                            })
                                        val sectionItems = if (section.id == topSection?.id && section.items.size == 1) {
                                            emptyList()
                                        } else {
                                            section.items
                                        }

                                        if (sectionItems.isEmpty()) return@forEach

                                        when {
                                            isSongsSection -> {
                                                item(key = "search_songs_${section.id}") {
                                                    val previewSongs = sectionItems.take(5)
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        SearchResultSectionHeader(
                                                            title = section.title,
                                                            showSeeAll = sectionItems.size > 5,
                                                            onSeeAll = { selectedSectionFilter = section.id }
                                                        )
                                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                            previewSongs.forEachIndexed { idx, song ->
                                                                ExpressiveMediaRow(
                                                                    item = song,
                                                                    shape = segmentedRowShape(
                                                                        isFirst = idx == 0,
                                                                        isLast = idx == previewSongs.lastIndex
                                                                    ),
                                                                    onClick = { nav.routeItem(song) }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            isArtistsSection -> {
                                                item(key = "search_artists_${section.id}") {
                                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        SearchResultSectionHeader(
                                                            title = section.title,
                                                            showSeeAll = sectionItems.size > 3,
                                                            onSeeAll = { selectedSectionFilter = section.id },
                                                            modifier = Modifier.padding(horizontal = 16.dp)
                                                        )
                                                        LazyRow(
                                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            items(
                                                                items = sectionItems,
                                                                key = { "s_art_${it.id}" }
                                                            ) { artist ->
                                                                SearchArtistCard(
                                                                    item = artist,
                                                                    onClick = { nav.routeItem(artist) }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            else -> {
                                                item(key = section.id) {
                                                    HomeSectionBlock(
                                                        section = section,
                                                        isReplaySection = false,
                                                        onSectionClick = { nav.activeDetailSection = section },
                                                        onSeeAllClick = { nav.activeDetailSection = section },
                                                        onItemClick = { item -> nav.routeItem(item) },
                                                        onFriendBadgeClick = nav::openFriendBadge
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 132.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (uiState.recentQueries.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "recent_header") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Recientes",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            TextButton(onClick = onClearRecentSearches) {
                                                Text(
                                                    text = "Borrar",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            uiState.recentQueries.forEach { term ->
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                                                    border = BorderStroke(
                                                        0.8.dp,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                    ),
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            focusManager.clearFocus()
                                                            onSubmitSearch(term)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.History,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Text(
                                                            text = term,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }

                            if (uiState.categories.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "browse_header") {
                                    Text(
                                        text = "Explorar categorías",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.3).sp,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                    )
                                }

                                gridItems(
                                    items = uiState.categories,
                                    key = { "cat_${it.id}_${it.title}" }
                                ) { cat ->
                                    EditorialCategoryCard(
                                        item = cat,
                                        onClick = { nav.routeItem(cat) }
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

        FloatingTabHeader(
            title = appStrings.tabSearch,
            mode = mode,
            isBusy = uiState.isSearching || uiState.isLoadingCategories,
            onRefresh = null,
            onOpenAccountSheet = onOpenAccountSheet,
            userAvatarUrl = userAvatarUrl,
            modifier = Modifier.align(Alignment.TopCenter)
        )

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

/** Control segmentado estilo Apple Music × Material 3 Expressive (`Apple Music` | `Tu biblioteca`). */
@Composable
private fun SearchScopeSegmentedBar(
    selected: SearchScope,
    onSelect: (SearchScope) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SearchScope.entries.forEach { scope ->
                val isSelected = selected == scope
                val bg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "scope_bg"
                )
                val fg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "scope_fg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { onSelect(scope) },
                    contentAlignment = Alignment.Center
                ) {
                    val strings = com.applemusic.android.ui.theme.LocalAppStrings.current
                    val scopeLabel = when (scope) {
                        SearchScope.CATALOG -> strings.searchCatalogScope
                        SearchScope.LIBRARY -> strings.searchLibraryScope
                    }
                    Text(
                        text = scopeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
        )
    }
}

/** Tarjeta editorial de categoría estilo Apple Music (arte real si existe, o tono editorial sobrio). */
@Composable
private fun EditorialCategoryCard(
    item: HomeItem,
    onClick: () -> Unit
) {
    val idx = (item.title.hashCode().let { if (it < 0) -it else it }) % EDITORIAL_CATEGORY_TONES.size
    val fallbackTone = parseHexColor(item.bgColorHex) ?: EDITORIAL_CATEGORY_TONES[idx]
    val artUrl = item.artworkUrl?.takeIf { it.isNotBlank() }
    val titleShadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 6f))

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = fallbackTone,
        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.66f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to Color.Black.copy(alpha = 0.12f),
                                1f to Color.Black.copy(alpha = 0.78f)
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    fallbackTone,
                                    fallbackTone.copy(alpha = 0.72f),
                                    Color.Black.copy(alpha = 0.38f)
                                )
                            )
                        )
                )
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.merge(titleShadow),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

internal fun segmentedRowShape(isFirst: Boolean, isLast: Boolean): Shape = RoundedCornerShape(
    topStart = if (isFirst) 22.dp else 6.dp,
    topEnd = if (isFirst) 22.dp else 6.dp,
    bottomStart = if (isLast) 22.dp else 6.dp,
    bottomEnd = if (isLast) 22.dp else 6.dp
)

@Composable
internal fun ExpressiveMediaRow(
    item: HomeItem,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    playButtonBg: Color = MaterialTheme.colorScheme.primaryContainer,
    playButtonFg: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val isArtist = item.type.contains("artist", ignoreCase = true) ||
        item.type.equals("social-profiles", ignoreCase = true)
    val isPlayable = item.type.contains("song", ignoreCase = true) ||
        item.type.contains("station", ignoreCase = true) ||
        item.type.contains("video", ignoreCase = true) ||
        item.type.contains("music-movie", ignoreCase = true)
    val kindLabel = when {
        item.type.equals("social-profiles", ignoreCase = true) -> "Perfil"
        item.type.contains("artist", ignoreCase = true) -> "Artista"
        item.type.contains("album", ignoreCase = true) -> "Álbum"
        item.type.contains("playlist", ignoreCase = true) -> "Playlist"
        item.type.contains("song", ignoreCase = true) -> "Canción"
        item.type.contains("station", ignoreCase = true) -> "Estación"
        item.type.contains("music-movie", ignoreCase = true) -> "Película"
        item.type.contains("video", ignoreCase = true) -> "Video"
        item.type.contains("curator", ignoreCase = true) -> "Curador"
        item.type.contains("record-label", ignoreCase = true) -> "Sello"
        else -> null
    }
    val sub = listOfNotNull(
        kindLabel,
        item.subtitle.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    val actionCtrl = LocalMediaActionController.current
    val showMenuBtn = actionCtrl != null && !item.type.equals("social-profiles", ignoreCase = true) && !item.type.equals("activities", ignoreCase = true)

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Videos musicales: miniatura horizontal 16:9 en vez de cuadro
            val isVideoRow = item.isVideoItem()
            ArtworkBox(
                artworkUrl = if (isVideoRow) item.artworkUrl?.toWideArtworkUrl() else item.artworkUrl,
                fallbackColor = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                contentScale = ContentScale.Crop,
                modifier = (if (isVideoRow) Modifier.size(width = 92.dp, height = 52.dp) else Modifier.size(52.dp))
                    .clip(RoundedCornerShape(if (isArtist) 14.dp else 12.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isExplicit) {
                        ExplicitBadge(color = secondaryContentColor)
                    }
                }
                if (sub.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isPlayable) {
                Surface(
                    shape = CircleShape,
                    color = playButtonBg,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = playButtonFg,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = secondaryContentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (showMenuBtn) {
                androidx.compose.material3.IconButton(
                    onClick = { actionCtrl?.onOpenMenu(MediaMenuRequest(item = item)) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Más opciones",
                        tint = secondaryContentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/** Calcula un color opaco editorial a partir del color propio del artista/portada. */
private fun opaqueEditorialSurface(accentHex: String?, fallback: Color): Color {
    val parsed = parseHexColor(accentHex) ?: return fallback
    return lerp(Color(0xFF141418), parsed, 0.44f)
}

/** Tarjeta editorial "Mejor resultado" pintada con el color opaco real del perfil del artista/elemento. */
@Composable
private fun SearchTopResultCard(
    item: HomeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlayable = item.type.contains("song", ignoreCase = true) ||
        item.type.contains("station", ignoreCase = true) ||
        item.type.contains("video", ignoreCase = true) ||
        item.type.contains("music-movie", ignoreCase = true)
    val kindLabel = when {
        item.type.equals("social-profiles", ignoreCase = true) -> "PERFIL"
        item.type.contains("artist", ignoreCase = true) -> "ARTISTA"
        item.type.contains("album", ignoreCase = true) -> "ÁLBUM"
        item.type.contains("playlist", ignoreCase = true) -> "PLAYLIST"
        item.type.contains("song", ignoreCase = true) -> "CANCIÓN"
        item.type.contains("station", ignoreCase = true) -> "ESTACIÓN"
        item.type.contains("music-movie", ignoreCase = true) -> "PELÍCULA"
        item.type.contains("video", ignoreCase = true) -> "VIDEO"
        item.type.contains("curator", ignoreCase = true) -> "CURADOR"
        item.type.contains("record-label", ignoreCase = true) -> "SELLO"
        else -> "DESTACADO"
    }
    val rawAccent = parseHexColor(item.bgColorHex)
    val cardBg = opaqueEditorialSurface(item.bgColorHex, MaterialTheme.colorScheme.surfaceContainerHigh)
    val badgeBg = if (rawAccent != null) lerp(cardBg, Color.White, 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val actionBg = if (rawAccent != null) lerp(rawAccent, Color.White, 0.58f) else MaterialTheme.colorScheme.primary
    val actionFg = if (rawAccent != null) lerp(rawAccent, Color.Black, 0.78f) else MaterialTheme.colorScheme.onPrimary
    val onCard = if (rawAccent != null) Color.White else MaterialTheme.colorScheme.onSurface
    val onCardMuted = if (rawAccent != null) Color.White.copy(alpha = 0.80f) else MaterialTheme.colorScheme.onSurfaceVariant

    val shape = RoundedCornerShape(28.dp)
    val artShape = RoundedCornerShape(22.dp)

    Surface(
        shape = shape,
        color = cardBg,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isVideoTop = item.isVideoItem()
            ArtworkBox(
                artworkUrl = if (isVideoTop) item.artworkUrl?.toWideArtworkUrl() else item.artworkUrl,
                fallbackColor = badgeBg,
                contentScale = ContentScale.Crop,
                modifier = (if (isVideoTop) Modifier.size(width = 136.dp, height = 76.dp) else Modifier.size(102.dp))
                    .clip(artShape)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = badgeBg
                ) {
                    Text(
                        text = "MEJOR RESULTADO · $kindLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = onCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        color = onCard,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isExplicit) {
                        ExplicitBadge(color = onCard)
                    }
                }

                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = onCardMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = CircleShape,
                    color = actionBg,
                    contentColor = actionFg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayable) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = if (isPlayable) "Reproducir" else "Ver perfil",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultSectionHeader(
    title: String,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (showSeeAll) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSeeAll)
            ) {
                Text(
                    text = "Ver todo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Tarjeta de artista en búsqueda pintada con el color opaco propio del artista y foto no circular. */
@Composable
private fun SearchArtistCard(
    item: HomeItem,
    onClick: () -> Unit
) {
    val rawAccent = parseHexColor(item.bgColorHex)
    val cardBg = opaqueEditorialSurface(item.bgColorHex, MaterialTheme.colorScheme.surfaceContainerHigh)
    val textColor = if (rawAccent != null) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor = if (rawAccent != null) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val cardShape = RoundedCornerShape(22.dp)
    val photoShape = RoundedCornerShape(16.dp)

    Surface(
        shape = cardShape,
        color = cardBg,
        modifier = Modifier
            .width(148.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArtworkBox(
                artworkUrl = item.artworkUrl,
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(photoShape)
            )
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.type.equals("social-profiles", ignoreCase = true)) {
                        item.subtitle.ifBlank { "Perfil" }
                    } else "Artista",
                    style = MaterialTheme.typography.labelSmall,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
