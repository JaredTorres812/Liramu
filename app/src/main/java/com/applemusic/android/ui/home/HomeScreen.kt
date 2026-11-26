package com.applemusic.android.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.applemusic.android.data.model.FriendBadge
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection
import com.applemusic.android.data.model.HomeSectionStyle
import com.applemusic.android.data.model.ReplayPeriodOption
import com.applemusic.android.data.model.ReplaySummary
import com.applemusic.android.data.model.SocialProfileDetail
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

internal sealed interface MediaNavEntry {
    val key: String
    data class EditorialSubpage(val seed: HomeItem, override val key: String) : MediaNavEntry
    data class SectionDetail(val section: HomeSection, override val key: String) : MediaNavEntry
    data class Collection(val seed: HomeItem, override val key: String) : MediaNavEntry
    data class Artist(val seed: HomeItem, override val key: String) : MediaNavEntry
    data class SocialProfile(val seed: HomeItem, override val key: String) : MediaNavEntry
    data class Replay(override val key: String) : MediaNavEntry
}

internal class MediaNavigationState(
    private val context: android.content.Context,
    private val onPlayItem: (item: HomeItem, container: HomeItem?, startIndex: Int, shuffle: Boolean) -> Unit
) {
    private var seq = 0
    var pageStack by mutableStateOf<List<MediaNavEntry>>(emptyList())
        private set

    private fun push(entry: MediaNavEntry) {
        pageStack = pageStack + entry
    }

    fun pop() {
        if (pageStack.isNotEmpty()) {
            pageStack = pageStack.dropLast(1)
        }
    }

    fun clear() {
        if (pageStack.isNotEmpty()) {
            pageStack = emptyList()
        }
    }

    var activeDetailSection: HomeSection?
        get() = (pageStack.lastOrNull() as? MediaNavEntry.SectionDetail)?.section
        set(value) {
            if (value != null) push(MediaNavEntry.SectionDetail(value, "sec_${++seq}_${value.id}"))
            else if (pageStack.lastOrNull() is MediaNavEntry.SectionDetail) pop()
        }

    var activeCollection: HomeItem?
        get() = (pageStack.lastOrNull() as? MediaNavEntry.Collection)?.seed
        set(value) {
            if (value != null) push(MediaNavEntry.Collection(value, "col_${++seq}_${value.id}"))
            else if (pageStack.lastOrNull() is MediaNavEntry.Collection) pop()
        }

    var activeArtist: HomeItem?
        get() = (pageStack.lastOrNull() as? MediaNavEntry.Artist)?.seed
        set(value) {
            if (value != null) push(MediaNavEntry.Artist(value, "art_${++seq}_${value.id}"))
            else if (pageStack.lastOrNull() is MediaNavEntry.Artist) pop()
        }

    var activeSocialProfile: HomeItem?
        get() = (pageStack.lastOrNull() as? MediaNavEntry.SocialProfile)?.seed
        set(value) {
            if (value != null) push(MediaNavEntry.SocialProfile(value, "soc_${++seq}_${value.id}"))
            else if (pageStack.lastOrNull() is MediaNavEntry.SocialProfile) pop()
        }

    var showReplayDialog: Boolean
        get() = pageStack.lastOrNull() is MediaNavEntry.Replay
        set(value) {
            if (value) push(MediaNavEntry.Replay("rep_${++seq}"))
            else if (pageStack.lastOrNull() is MediaNavEntry.Replay) pop()
        }

    private fun openExternalUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun routeItem(item: HomeItem, fromReplaySection: Boolean = false) {
        val t = item.type.lowercase()
        when {
            fromReplaySection && (t == "editorial-items" || item.id.startsWith("ed.")) -> {
                showReplayDialog = true
            }
            t.contains("album") || t.contains("playlist") -> {
                activeCollection = item
            }
            t.contains("artist") || (t == "stations" && item.id.startsWith("ra.a-")) -> {
                val strippedStation = if (item.id.startsWith("ra.a-")) item.id.removePrefix("ra.a-") else null
                val resolvedArtistId = item.catalogId?.takeIf { it.isNotBlank() && !it.startsWith("r.") }
                    ?: strippedStation
                    ?: item.id
                activeArtist = item.copy(
                    id = resolvedArtistId,
                    type = "artists",
                    subtitle = "",
                    catalogId = resolvedArtistId
                )
            }
            t == "social-profiles" -> {
                activeSocialProfile = item
            }
            t == "editorial-link" || t == "apple-curators" || t == "curators" -> {
                val url = item.url.orEmpty()
                if (item.badge == "external" || url.contains("concerts", ignoreCase = true)) {
                    if (url.startsWith("http")) openExternalUrl(url)
                } else {
                    push(MediaNavEntry.EditorialSubpage(item, "ed_${++seq}_${item.id}"))
                }
            }
            t == "concerts" || t == "social-upsells" || t == "editorial-items" || t == "editorial-elements" || t == "activities" -> {
                val url = item.url?.takeIf { it.startsWith("http") }
                if (url != null) {
                    if (url.contains("viewGrouping", ignoreCase = true) ||
                        url.contains("fcId=", ignoreCase = true) ||
                        url.contains("viewTop", ignoreCase = true)
                    ) {
                        push(MediaNavEntry.EditorialSubpage(item, "ed_${++seq}_${item.id}"))
                    } else {
                        openExternalUrl(url)
                    }
                }
            }
            t.contains("song") || t.contains("station") || t.contains("video") || t == "uploaded-audios" -> {
                onPlayItem(item, null, 0, false)
            }
            fromReplaySection -> {
                showReplayDialog = true
            }
            else -> {
                val url = item.url?.takeIf { it.startsWith("http") }
                if (url != null) openExternalUrl(url)
            }
        }
    }

    fun openFriendBadge(badge: FriendBadge) {
        activeSocialProfile = HomeItem(
            id = badge.id,
            type = "social-profiles",
            title = badge.name,
            subtitle = badge.handle.orEmpty(),
            artworkUrl = badge.avatarUrl
        )
    }
}

val LocalTabResetNonce = androidx.compose.runtime.compositionLocalOf { 0L }

@Composable
internal fun rememberMediaNavigationState(
    onPlayItem: (item: HomeItem, container: HomeItem?, startIndex: Int, shuffle: Boolean) -> Unit
): MediaNavigationState {
    val context = LocalContext.current
    val nav = remember(context, onPlayItem) { MediaNavigationState(context, onPlayItem) }
    val resetNonce = LocalTabResetNonce.current
    androidx.compose.runtime.LaunchedEffect(resetNonce) {
        if (resetNonce > 0L) nav.clear()
    }
    return nav
}

@Composable
internal fun MediaNavigationDialogs(
    nav: MediaNavigationState,
    onPlayItem: (item: HomeItem, container: HomeItem?, startIndex: Int, shuffle: Boolean) -> Unit,
    loadFullSection: suspend (HomeSection) -> List<HomeItem> = { it.items },
    loadMediaDetail: suspend (HomeItem) -> AlbumDetail? = { null },
    loadArtist: suspend (String) -> ArtistDetail? = { null },
    loadReplay: suspend (Int?, ReplayPeriodOption?) -> ReplaySummary? = { _, _ -> null },
    loadSocialProfile: suspend (String) -> SocialProfileDetail? = { null },
    loadEditorialSubpage: suspend (HomeItem) -> List<HomeSection> = { emptyList() }
) {
    val topEntry = nav.pageStack.lastOrNull() ?: return
    androidx.compose.runtime.key(topEntry.key) {
        when (topEntry) {
            is MediaNavEntry.EditorialSubpage -> {
                EditorialBrowseDialog(
                    seed = topEntry.seed,
                    loadSubpage = loadEditorialSubpage,
                    onDismiss = nav::pop,
                    onSeeAllClick = { sec -> nav.activeDetailSection = sec },
                    onItemClick = { next -> nav.routeItem(next) },
                    onFriendBadgeClick = nav::openFriendBadge
                )
            }

            is MediaNavEntry.SectionDetail -> {
                SectionDetailDialog(
                    section = topEntry.section,
                    loadFullSection = loadFullSection,
                    onDismiss = nav::pop,
                    onItemClick = { item -> nav.routeItem(item) },
                    onPlayClick = { item, shuffle -> onPlayItem(item, item, 0, shuffle) },
                    onFriendBadgeClick = nav::openFriendBadge
                )
            }

            is MediaNavEntry.Collection -> {
                AlbumDetailDialog(
                    seed = topEntry.seed,
                    loadDetail = loadMediaDetail,
                    onDismiss = nav::pop,
                    onPlay = onPlayItem,
                    onNavigateItem = { next -> nav.routeItem(next) },
                    onOpenReplay = { nav.showReplayDialog = true }
                )
            }

            is MediaNavEntry.Artist -> {
                ArtistDetailDialog(
                    seed = topEntry.seed,
                    loadArtist = loadArtist,
                    onDismiss = nav::pop,
                    onNavigateItem = { next -> nav.routeItem(next) },
                    onPlay = { item -> onPlayItem(item, null, 0, false) }
                )
            }

            is MediaNavEntry.SocialProfile -> {
                SocialProfileDialog(
                    seed = topEntry.seed,
                    loadProfile = loadSocialProfile,
                    onDismiss = nav::pop,
                    onNavigateItem = { next -> nav.routeItem(next) }
                )
            }

            is MediaNavEntry.Replay -> {
                ReplayDetailDialog(
                    loadReplay = loadReplay,
                    onDismiss = nav::pop,
                    onNavigateItem = { next -> nav.routeItem(next) },
                    onPlay = { item -> onPlayItem(item, null, 0, false) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FloatingTabHeader(
    title: String,
    mode: ConnectionMode,
    isBusy: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onOpenAccountSheet: () -> Unit,
    userAvatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

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
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UserAccountAvatarButton(
    mode: ConnectionMode,
    avatarUrl: String?,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp)
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Cuenta y Configuración",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            FilledTonalIconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = when (mode) {
                        ConnectionMode.PERSONALIZED -> MaterialTheme.colorScheme.primaryContainer
                        ConnectionMode.CATALOG_ONLY -> MaterialTheme.colorScheme.secondaryContainer
                        ConnectionMode.DEMO -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = "Cuenta y Configuración",
                    tint = when (mode) {
                        ConnectionMode.PERSONALIZED -> MaterialTheme.colorScheme.primary
                        ConnectionMode.CATALOG_ONLY -> MaterialTheme.colorScheme.onSecondaryContainer
                        ConnectionMode.DEMO -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (isBusy) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    onConnectWebSession: () -> Unit,
    onPlayItem: (item: HomeItem, container: HomeItem?, startIndex: Int, shuffle: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Inicio",
    @Suppress("UNUSED_PARAMETER") showReplay: Boolean = true,
    showConnectBanner: Boolean = true,
    userAvatarUrl: String? = null,
    loadFullSection: suspend (HomeSection) -> List<HomeItem> = { it.items },
    @Suppress("UNUSED_PARAMETER") loadListeningHistory: suspend () -> List<HomeItem> = { emptyList() },
    loadMediaDetail: suspend (HomeItem) -> AlbumDetail? = { null },
    loadArtist: suspend (String) -> ArtistDetail? = { null },
    loadReplay: suspend (Int?, ReplayPeriodOption?) -> ReplaySummary? = { _, _ -> null },
    loadSocialProfile: suspend (String) -> SocialProfileDetail? = { null },
    loadEditorialSubpage: suspend (HomeItem) -> List<HomeSection> = { emptyList() }
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val nav = rememberMediaNavigationState(onPlayItem)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingHeaderClearance = statusBarTop + 76.dp
    val topHazeState = rememberHazeState()

    Box(
        modifier = modifier
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
                uiState.isLoading -> HomeLoadingExpressive()
                uiState.errorMessage != null && uiState.sections.isEmpty() -> {
                    HomeErrorState(
                        message = uiState.errorMessage,
                        onRetry = onRefresh,
                        onConfigure = onOpenAccountSheet
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = floatingHeaderClearance, bottom = 188.dp),
                        verticalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        if (showConnectBanner && (uiState.webLoginRequired || uiState.mode != ConnectionMode.PERSONALIZED)) {
                            item(key = "connect_banner") {
                                ConnectAppleMusicBanner(
                                    mode = uiState.mode,
                                    onConnect = onConnectWebSession
                                )
                            }
                        }

                        items(
                            items = uiState.sections.distinctBy { it.id },
                            key = { it.id }
                        ) { section ->
                            val isReplaySection = section.title.contains("Replay", ignoreCase = true)
                            HomeSectionBlock(
                                section = section,
                                isReplaySection = isReplaySection,
                                onSectionClick = {
                                    when {
                                        isReplaySection -> nav.showReplayDialog = true
                                        section.primaryContent != null -> nav.routeItem(section.primaryContent)
                                        section.canLoadMore -> nav.activeDetailSection = section
                                    }
                                },
                                onSeeAllClick = { nav.activeDetailSection = section },
                                onItemClick = { item -> nav.routeItem(item, fromReplaySection = isReplaySection) },
                                onFriendBadgeClick = nav::openFriendBadge
                            )
                        }
                    }
                }
            }
        }

        TopProgressiveBlur(
            hazeState = topHazeState,
            height = statusBarTop + 82.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        FloatingTabHeader(
            title = title,
            mode = uiState.mode,
            isBusy = uiState.isLoading || uiState.isRefreshing,
            onRefresh = onRefresh,
            onOpenAccountSheet = onOpenAccountSheet,
            userAvatarUrl = userAvatarUrl,
            modifier = Modifier.align(Alignment.TopCenter)
        )

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
internal fun ConnectAppleMusicBanner(
    mode: ConnectionMode,
    onConnect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (mode == ConnectionMode.CATALOG_ONLY) {
                        "Conecta tu Apple Music"
                    } else {
                        "Inicia sesión en Apple Music"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Carga tu Home completo: Replay, Sugerencias, Conciertos y Amigos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onConnect,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Conectar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSectionBlock(
    section: HomeSection,
    isReplaySection: Boolean = false,
    onSectionClick: () -> Unit,
    onSeeAllClick: () -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onFriendBadgeClick: (FriendBadge) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (section.title.isNotBlank()) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerClickable = section.hasChevron || isReplaySection
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (headerClickable) {
                            Modifier.clickable(onClick = onSectionClick)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (!section.subtitle.isNullOrBlank()) {
                    Text(
                        text = section.subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (headerClickable) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Ver todo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            if (isReplaySection) {
                TextButton(onClick = onSectionClick) {
                    Text("Resumen", color = AppleRed, fontWeight = FontWeight.Bold)
                }
            } else if (section.canLoadMore && section.primaryContent != null) {
                TextButton(onClick = onSeeAllClick) {
                    Text("Ver todo")
                }
            }
        }

        when (section.style) {
            HomeSectionStyle.HERO_PICKS -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.items, key = { "${section.id}_${it.id}" }) { item ->
                        HeroPickCarouselCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onFriendBadgeClick = onFriendBadgeClick,
                            modifier = Modifier
                                .width(FeatureCardWidth)
                                .height(FeatureCardHeight)
                                .clip(FeatureCardShape)
                                .border(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.10f),
                                    shape = FeatureCardShape
                                )
                        )
                    }
                }
            }

            HomeSectionStyle.SUPER_HERO -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.items, key = { "${section.id}_${it.id}" }) { item ->
                        SuperHeroCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onFriendBadgeClick = onFriendBadgeClick,
                            modifier = Modifier
                                .fillParentMaxWidth(if (section.items.size > 1) 0.92f else 1f)
                                .height(216.dp)
                                .clip(FeatureCardShape)
                                .border(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.10f),
                                    shape = FeatureCardShape
                                )
                        )
                    }
                }
            }

            HomeSectionStyle.SQUARE_CAROUSEL -> if (isWideSection(section)) {
                // Videos musicales y entrevistas: tarjetas horizontales 16:9
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.items, key = { "${section.id}_${it.id}" }) { item ->
                        WideMediaCard(item = item, onClick = { onItemClick(item) })
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.height(222.dp)
                ) {
                    items(section.items, key = { "${section.id}_${it.id}" }) { item ->
                        SquareMediaCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onFriendBadgeClick = onFriendBadgeClick
                        )
                    }
                }
            }

            HomeSectionStyle.COVER_GRID -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(section.items, key = { "${section.id}_${it.id}" }) { item ->
                        MoodCoverCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }

            HomeSectionStyle.ARTIST_CIRCLES -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.height(174.dp)
                ) {
                    items(section.items, key = { "${section.id}_${it.id}" }) { item ->
                        ArtistCircleCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }

            HomeSectionStyle.COMPACT_TRACKS -> {
                val trackGroups = remember(section.items) { section.items.chunked(4) }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(trackGroups, key = { group -> "${section.id}_grp_${group.first().id}" }) { group ->
                        Surface(
                            shape = ListCardShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillParentMaxWidth(if (trackGroups.size > 1) 0.90f else 1f)
                        ) {
                            Column {
                                group.forEachIndexed { index, track ->
                                    val accentColor = parseHexColor(track.bgColorHex)
                                        ?: MaterialTheme.colorScheme.surfaceContainerHigh
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onItemClick(track) }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ArtworkBox(
                                            artworkUrl = track.artworkUrl,
                                            fallbackColor = accentColor,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CoverShape)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = track.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (track.isExplicit) {
                                                    ExplicitBadge(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (track.subtitle.isNotBlank()) {
                                                Text(
                                                    text = track.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Reproducir",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (index < group.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 72.dp, end = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HomeSectionStyle.EXPLORE_LINKS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    section.items.forEachIndexed { idx, item ->
                        ExploreLinkTile(
                            item = item,
                            shape = segmentedRowShape(
                                isFirst = idx == 0,
                                isLast = idx == section.items.lastIndex
                            ),
                            onClick = { onItemClick(item) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPickCarouselCard(
    item: HomeItem,
    onClick: () -> Unit,
    onFriendBadgeClick: (FriendBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val hasTallArt = !item.tallArtworkUrl.isNullOrBlank()
    val heroArt = item.tallArtworkUrl ?: item.artworkUrl
    val textColor = Color.White
    val overArt = hasTallArt
    val textShadow = if (overArt) {
        TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1.5f), 5f))
    } else {
        TextStyle.Default
    }

    Box(
        modifier = modifier
            .background(bgColor)
            .clickable(onClick = onClick)
    ) {
        if (hasTallArt) {
            ArtworkBox(
                artworkUrl = heroArt,
                fallbackColor = bgColor,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ArtworkBox(
                    artworkUrl = heroArt,
                    fallbackColor = bgColor,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(214.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF1C1C1E))
                )
            }
        }

        if (overArt) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.80f to Color.Black.copy(alpha = 0.56f),
                            1f to Color.Black.copy(alpha = 0.86f)
                        )
                    )
            )
        }

        if (item.friendBadges.isNotEmpty()) {
            FriendBadgesOverlay(
                badges = item.friendBadges,
                onBadgeClick = onFriendBadgeClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (!item.badge.isNullOrBlank()) {
                Text(
                    text = item.badge,
                    style = textShadow,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = item.title,
                    style = textShadow,
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.isExplicit) {
                    ExplicitBadge(color = textColor)
                }
            }
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = textShadow,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = textColor.copy(alpha = 0.9f),
                    maxLines = if (overArt) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ExplicitBadge(color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text = "E", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Banner horizontal completo estilo Apple Music (`Lo más destacado`), sin botón de play ni recortes laterales. */
@Composable
private fun SuperHeroCard(
    item: HomeItem,
    onClick: () -> Unit,
    onFriendBadgeClick: (FriendBadge) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bgColor = parseHexColor(item.bgColorHex) ?: Color(0xFF2C2C2E)
    val bannerArt = item.tallArtworkUrl?.takeIf { it.isNotBlank() } ?: item.artworkUrl
    val overlayShadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1.5f), 6f))

    Box(
        modifier = modifier
            .background(bgColor)
            .clickable(onClick = onClick)
    ) {
        if (!bannerArt.isNullOrBlank()) {
            val isWideBanner = !item.tallArtworkUrl.isNullOrBlank()
            AsyncImage(
                model = bannerArt,
                contentDescription = item.title,
                contentScale = if (isWideBanner) ContentScale.Fit else ContentScale.Crop,
                alignment = if (isWideBanner) Alignment.TopCenter else Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ArtworkBox(
                artworkUrl = null,
                fallbackColor = bgColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.75f to Color.Black.copy(alpha = 0.56f),
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )

        if (item.friendBadges.isNotEmpty()) {
            FriendBadgesOverlay(
                badges = item.friendBadges,
                onBadgeClick = onFriendBadgeClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (!item.badge.isNullOrBlank()) {
                Text(
                    text = item.badge.uppercase(),
                    style = overlayShadow,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = item.title,
                style = overlayShadow,
                fontSize = 15.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = overlayShadow,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SquareMediaCard(
    item: HomeItem,
    onClick: () -> Unit,
    onFriendBadgeClick: (FriendBadge) -> Unit
) {
    val accentColor = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = Modifier
            .width(156.dp)
            .height(242.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(156.dp)) {
            ArtworkBox(
                artworkUrl = item.artworkUrl,
                fallbackColor = accentColor,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CoverShape)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = CoverShape
                    )
            )

            if (item.friendBadges.isNotEmpty()) {
                FriendBadgesOverlay(
                    badges = item.friendBadges,
                    onBadgeClick = onFriendBadgeClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            } else if (item.type == "stations" && item.badge.equals("EN VIVO", ignoreCase = true)) {
                Surface(
                    shape = CircleShape,
                    color = AppleRed,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "EN VIVO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.isExplicit) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        if (item.subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FriendBadgesOverlay(
    badges: List<FriendBadge>,
    onBadgeClick: (FriendBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.take(3).forEach { friend ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.5.dp, Color.Black.copy(alpha = 0.85f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onBadgeClick(friend) }
            ) {
                if (!friend.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = friend.avatarUrl,
                        contentDescription = friend.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = friend.name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodCoverCard(
    item: HomeItem,
    onClick: () -> Unit
) {
    val bgColor = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    ElevatedCard(
        onClick = onClick,
        shape = CoverShape,
        modifier = Modifier
            .width(156.dp)
            .height(216.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkBox(
                artworkUrl = item.artworkUrl,
                fallbackColor = bgColor,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistCircleCard(
    item: HomeItem,
    onClick: () -> Unit
) {
    val bgColor = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val artistShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtworkBox(
            artworkUrl = item.artworkUrl,
            fallbackColor = bgColor,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(124.dp)
                .clip(artistShape)
                .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), artistShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ArtworkBox(
    artworkUrl: String?,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    if (!artworkUrl.isNullOrBlank()) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier.background(fallbackColor)
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        fallbackColor,
                        fallbackColor.copy(alpha = 0.65f),
                        Color(0xFF18181B)
                    )
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeLoadingExpressive(label: String = "Cargando tu música...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ContainedLoadingIndicator()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    onConfigure: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No se pudo cargar Home",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRetry,
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Reintentar")
            }
            OutlinedButton(
                onClick = onConfigure,
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Revisar Tokens")
            }
        }
    }
}

internal fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching {
        val clean = hex.removePrefix("#")
        Color(("FF$clean").toLong(16))
    }.getOrNull()
}

private fun itemKindBadge(type: String): String? = when {
    type.contains("album") -> "ÁLBUM"
    type.contains("playlist") -> "PLAYLIST"
    type.contains("station") -> "ESTACIÓN"
    type.contains("artist") -> "ARTISTA"
    type.contains("song") -> "CANCIÓN"
    type.contains("video") -> "VIDEO"
    else -> null
}

private fun isTrackLikeList(items: List<HomeItem>): Boolean =
    items.isNotEmpty() && items.all {
        val t = it.type.lowercase()
        t.contains("song") || t.contains("video") || t == "uploaded-audios"
    }

@Composable
private fun ExploreLinkTile(
    item: HomeItem,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when {
        item.title.contains("lista", ignoreCase = true) || item.title.contains("chart", ignoreCase = true) || item.title.contains("éxito", ignoreCase = true) -> Icons.Rounded.PlayArrow
        item.title.contains("video", ignoreCase = true) -> Icons.Rounded.PlayArrow
        else -> Icons.Rounded.MusicNote
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditorialBrowseDialog(
    seed: HomeItem,
    loadSubpage: suspend (HomeItem) -> List<HomeSection>,
    onDismiss: () -> Unit,
    onSeeAllClick: (HomeSection) -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onFriendBadgeClick: (FriendBadge) -> Unit
) {
    var sections by remember(seed.id, seed.url) { mutableStateOf<List<HomeSection>>(emptyList()) }
    var loading by remember(seed.id, seed.url) { mutableStateOf(true) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingHeaderClearance = statusBarTop + 72.dp
    val topHazeState = rememberHazeState()

    LaunchedEffect(seed.id, seed.url) {
        loading = true
        sections = loadSubpage(seed)
        loading = false
    }

    InAppPageContainer(onBack = onDismiss) {
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
            sections.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No se encontró contenido disponible para ${seed.title}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            sections.size == 1 && sections[0].style == HomeSectionStyle.SQUARE_CAROUSEL -> {
                val onlySection = sections[0]
                val gridItemsList = remember(onlySection.items) {
                    onlySection.items.distinctBy { "${it.type}_${it.id}" }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = floatingHeaderClearance,
                        bottom = 132.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = topHazeState)
                ) {
                    gridItems(
                        items = gridItemsList,
                        key = { "eb_${onlySection.id}_${it.type}_${it.id}" }
                    ) { item ->
                        val accentColor = parseHexColor(item.bgColorHex)
                            ?: MaterialTheme.colorScheme.surfaceContainerHigh
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                            border = BorderStroke(
                                0.8.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onItemClick(item) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(7.dp)
                            ) {
                                ArtworkBox(
                                    artworkUrl = item.artworkUrl,
                                    fallbackColor = accentColor,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(66.dp)
                                        .padding(horizontal = 5.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 13.5.sp,
                                        lineHeight = 17.sp,
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
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = topHazeState),
                    contentPadding = PaddingValues(top = floatingHeaderClearance, bottom = 188.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(sections.distinctBy { it.id }, key = { "sub_${it.id}" }) { section ->
                        HomeSectionBlock(
                            section = section,
                            isReplaySection = false,
                            onSectionClick = { if (section.canLoadMore) onSeeAllClick(section) },
                            onSeeAllClick = { onSeeAllClick(section) },
                            onItemClick = onItemClick,
                            onFriendBadgeClick = onFriendBadgeClick
                        )
                    }
                }
            }
        }

        TopProgressiveBlur(
            hazeState = topHazeState,
            height = floatingHeaderClearance + 10.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

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
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 6.dp
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver"
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
                    text = seed.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SectionDetailDialog(
    section: HomeSection,
    loadFullSection: suspend (HomeSection) -> List<HomeItem>,
    onDismiss: () -> Unit,
    onItemClick: (HomeItem) -> Unit,
    onPlayClick: (HomeItem, Boolean) -> Unit,
    onFriendBadgeClick: (FriendBadge) -> Unit
) {
    var items by remember(section.id) { mutableStateOf(section.items) }
    var loadingMore by remember(section.id) { mutableStateOf(section.canLoadMore) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showScrollTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex >= 4 }
    }

    LaunchedEffect(section.id) {
        if (section.canLoadMore) {
            items = loadFullSection(section)
            loadingMore = false
        }
    }

    val visibleItems = remember(items) { items.distinctBy { "${it.type}_${it.id}" } }
    val isTrackList = remember(visibleItems) { isTrackLikeList(visibleItems) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingHeaderClearance = statusBarTop + 74.dp
    val topHazeState = rememberHazeState()

    InAppPageContainer(onBack = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = topHazeState)
        ) {
            if (loadingMore) {
                Spacer(modifier = Modifier.height(floatingHeaderClearance))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (isTrackList) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (loadingMore) 8.dp else floatingHeaderClearance,
                        bottom = 132.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(visibleItems, key = { _, it -> "${section.id}_trk_${it.type}_${it.id}" }) { idx, track ->
                        ExpressiveMediaRow(
                            item = track,
                            shape = segmentedRowShape(idx == 0, idx == visibleItems.lastIndex),
                            onClick = { onItemClick(track) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    // Adaptive: 2 columnas en teléfono vertical, más en horizontal y tablets.
                    columns = GridCells.Adaptive(
                        when {
                            section.style == HomeSectionStyle.ARTIST_CIRCLES -> 100.dp
                            isWideSection(section) -> 300.dp
                            else -> 150.dp
                        }
                    ),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (loadingMore) 8.dp else floatingHeaderClearance,
                        bottom = 132.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (section.primaryContent != null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "seed_banner_${section.id}") {
                            val primary = section.primaryContent
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                                    .clickable { onItemClick(primary) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ArtworkBox(
                                        artworkUrl = primary.artworkUrl ?: visibleItems.firstOrNull()?.artworkUrl,
                                        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CoverShape)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Inspirado en",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppleRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = primary.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onPlayClick(primary, false) },
                                        shapes = ButtonDefaults.shapes()
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ir")
                                    }
                                }
                            }
                        }
                    }

                    gridItems(
                        items = visibleItems,
                        key = { "${section.id}_grid_${it.type}_${it.id}" }
                    ) { item ->
                        val accentColor = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                        val isCircle = section.style == HomeSectionStyle.ARTIST_CIRCLES
                        if (!isCircle && isWideSection(section)) {
                            WideMediaCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                width = androidx.compose.ui.unit.Dp.Unspecified,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (isCircle) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onItemClick(item) }
                            ) {
                                ArtworkBox(
                                    artworkUrl = item.artworkUrl,
                                    fallbackColor = accentColor,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable { onItemClick(item) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(7.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        ArtworkBox(
                                            artworkUrl = item.artworkUrl,
                                            fallbackColor = accentColor,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                        if (item.friendBadges.isNotEmpty()) {
                                            FriendBadgesOverlay(
                                                badges = item.friendBadges,
                                                onBadgeClick = onFriendBadgeClick,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(7.dp)
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(66.dp)
                                            .padding(horizontal = 5.dp, vertical = 7.dp),
                                        verticalArrangement = Arrangement.Top
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 13.5.sp,
                                            lineHeight = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sub = item.subtitle.ifBlank { itemKindBadge(item.type).orEmpty() }
                                        if (sub.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = sub,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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

        TopProgressiveBlur(
            hazeState = topHazeState,
            height = floatingHeaderClearance + 10.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

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
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 6.dp
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
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
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollTop && !isTrackList,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 118.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        scope.launch { gridState.animateScrollToItem(0) }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Subir", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
