package com.applemusic.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.applemusic.android.data.model.ArtistDetail
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.ReplayPeriodOption
import com.applemusic.android.data.model.ReplayRankedItem
import com.applemusic.android.data.model.ReplaySummary
import com.applemusic.android.data.model.SocialProfileDetail
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val ReplayMuted = Color(0xFFB3B3BC)

/**
 * Contenedor de subpágina integrada dentro de la pestaña actual (respeta la barra de navegación y minireproductor inferiores).
 */
@Composable
internal fun InAppPageContainer(
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            ),
        content = content
    )
}

private data class ArtistSeeAllSection(
    val title: String,
    val items: List<HomeItem>,
    val isCircular: Boolean = false,
    val isSongs: Boolean = false,
    val isVideos: Boolean = false
)

/** Vista completa de Artista (hero, último lanzamiento, top canciones, álbumes, sencillos, playlists, videos, recopilaciones, aparece en, artistas similares y biografía). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailDialog(
    seed: HomeItem,
    loadArtist: suspend (String) -> ArtistDetail?,
    onDismiss: () -> Unit,
    onNavigateItem: (HomeItem) -> Unit,
    onPlay: (HomeItem) -> Unit
) {
    var artist by remember(seed.id) { mutableStateOf<ArtistDetail?>(null) }
    var loading by remember(seed.id) { mutableStateOf(true) }
    var seeAllSection by remember(seed.id) { mutableStateOf<ArtistSeeAllSection?>(null) }
    var showAboutPage by remember(seed.id) { mutableStateOf(false) }
    var extractedPhotoColor by remember(seed.id) { mutableStateOf<Color?>(null) }

    val current = artist
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topHazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val mediaActions = LocalMediaActionController.current
    val artistItem = remember(seed, current) {
        val canonicalId = current?.id?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            ?: seed.catalogId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            ?: seed.id.removePrefix("ra.a-")
        seed.copy(
            id = canonicalId,
            title = current?.name?.ifBlank { seed.title } ?: seed.title,
            artworkUrl = current?.artworkUrl?.takeIf { it.isNotBlank() } ?: seed.artworkUrl,
            type = "artists",
            catalogId = canonicalId.takeIf { it.all(Char::isDigit) } ?: seed.catalogId
        )
    }
    // La estrella se rellena si el artista está en favoritos por id (catálogo/biblioteca) o por nombre.
    val favMap = mediaActions?.favoriteOverrides?.invoke().orEmpty()
    // `any`: un `false` viejo en una clave no debe tapar un `true` en otra (id de biblioteca, de catálogo o nombre).
    val isArtistFavorite = listOfNotNull(
        favMap[seed.id],
        favMap[artistItem.id],
        seed.catalogId?.let { favMap[it] },
        favMap[artistFavoriteNameKey(artistItem.title)],
        favMap[artistFavoriteNameKey(seed.title)]
    ).any { it }

    LaunchedEffect(seed.id) {
        listState.scrollToItem(0)
        mediaActions?.onRefreshStatus?.invoke(artistItem)
        artist = loadArtist(seed.id)
        loading = false
    }

    val showTitlePill by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 1 || listState.firstVisibleItemScrollOffset > 650
        }
    }

    // Priorizar el color más llamativo extraído directamente de la foto de perfil (siempre en modo oscuro inmersivo, nunca fondo blanco)
    val metadataAccent = parseHexColor(current?.bgColorHex ?: seed.bgColorHex)?.takeIf { c ->
        val lum = c.luminance()
        lum in 0.05f..0.90f
    }
    val baseProfileColor = extractedPhotoColor ?: metadataAccent ?: Color(0xFF3A3A48)
    val targetPageBg = run {
        val lum = baseProfileColor.luminance()
        when {
            lum > 0.65f -> lerp(baseProfileColor, Color(0xFF141418), 0.52f)
            lum > 0.22f -> lerp(baseProfileColor, Color(0xFF140E0E), 0.22f)
            else -> lerp(baseProfileColor, Color(0xFF121216), 0.12f)
        }
    }
    val targetCardBg = lerp(lerp(targetPageBg, baseProfileColor, 0.26f), Color.White, 0.08f)
    val targetPillBg = lerp(lerp(targetPageBg, baseProfileColor, 0.22f), Color.White, 0.14f)
    val targetAccentPill = lerp(baseProfileColor, Color.White, 0.38f)
    val targetAccentInk = lerp(baseProfileColor, Color.Black, 0.80f)

    val artistPageBg by animateColorAsState(targetValue = targetPageBg, label = "artist_page_bg")
    val artistCardBg by animateColorAsState(targetValue = targetCardBg, label = "artist_card_bg")
    val artistAccentPill by animateColorAsState(targetValue = targetAccentPill, label = "artist_accent_pill")
    val artistAccentInk by animateColorAsState(targetValue = targetAccentInk, label = "artist_accent_ink")
    val artistPillBg by animateColorAsState(targetValue = targetPillBg, label = "artist_pill_bg")
    val onArtistBg = Color.White
    val onArtistMuted = Color.White.copy(alpha = 0.78f)

    val artistColorScheme = MaterialTheme.colorScheme.copy(
        background = artistPageBg,
        surface = artistPageBg,
        onBackground = onArtistBg,
        onSurface = onArtistBg,
        onSurfaceVariant = onArtistMuted,
        surfaceContainer = artistCardBg,
        surfaceContainerHigh = artistCardBg,
        surfaceContainerHighest = artistPillBg,
        primary = artistAccentPill,
        onPrimary = artistAccentInk,
        primaryContainer = artistCardBg,
        onPrimaryContainer = onArtistBg
    )

    InAppPageContainer(
        onBack = {
            when {
                showAboutPage -> showAboutPage = false
                seeAllSection != null -> seeAllSection = null
                else -> onDismiss()
            }
        }
    ) {
        MaterialTheme(colorScheme = artistColorScheme) {
        CompositionLocalProvider(LocalContentColor provides onArtistBg) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(artistPageBg)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = topHazeState),
                contentPadding = PaddingValues(bottom = 188.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item(key = "hero") {
                    // Renderizado instantáneo con los datos disponibles del seed mientras llegan las secciones
                    val displayName = current?.name?.ifBlank { seed.title } ?: seed.title
                    val profileArt = current?.artworkUrl?.takeIf { it.isNotBlank() }
                        ?: seed.artworkUrl?.takeIf { it.isNotBlank() }

                    // Tablet: foto panorámica más baja, nombre y botones alineados a la izquierda (estilo iPad).
                    val heroAdaptive = rememberAdaptiveInfo()
                    val tabletArtist = heroAdaptive.isExpandedWidth
                    val heroSidePadding = if (tabletArtist) 40.dp else 24.dp
                    val heroHeight = if (tabletArtist) {
                        (heroAdaptive.heightDp * 0.60f).dp.coerceIn(360.dp, 560.dp)
                    } else {
                        adaptiveHeroHeight(470.dp)
                    }

                    // En tablet la foto se muestra mucho más ancha: pedir la versión de alta resolución para que no se vea borrosa.
                    val heroArtUrl = if (tabletArtist) {
                        profileArt?.replace(Regex("""/\d+x\d+([a-zA-Z]*)\."""), "/2000x2000$1.")
                    } else profileArt

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Nombre (logo o texto) sobre la parte baja de la cabecera
                        val nameOverlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
                            val nameLogoUrl = current?.nameLogoUrl
                            if (!nameLogoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = nameLogoUrl,
                                    contentDescription = displayName,
                                    contentScale = ContentScale.Fit,
                                    alignment = if (tabletArtist) Alignment.CenterStart else Alignment.Center,
                                    modifier = Modifier
                                        .align(if (tabletArtist) Alignment.BottomStart else Alignment.BottomCenter)
                                        .fillMaxWidth(if (tabletArtist) 0.45f else 0.84f)
                                        .heightIn(min = 44.dp, max = if (tabletArtist) 120.dp else 96.dp)
                                        .padding(horizontal = if (tabletArtist) heroSidePadding else 16.dp, vertical = 10.dp)
                                )
                            } else {
                                Text(
                                    text = displayName,
                                    style = if (tabletArtist) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.8).sp,
                                    textAlign = if (tabletArtist) TextAlign.Start else TextAlign.Center,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(if (tabletArtist) Alignment.BottomStart else Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = heroSidePadding, vertical = 10.dp)
                                )
                            }
                        }

                        if (tabletArtist) {
                            // TABLET: foto / video de perfil COMPLETOS (sin recortes) con su proporción real.
                            TabletHeroMedia(
                                imageUrl = heroArtUrl,
                                videoUrl = current?.motionHeroUrl,
                                backgroundColor = artistPageBg,
                                contentDescription = displayName,
                                isVideoActive = seeAllSection == null,
                                onImageSuccess = { drawable ->
                                    scope.launch(Dispatchers.Default) {
                                        val dominant = extractVibrantDominantColor(drawable, focusSubject = true)
                                        if (dominant != null) extractedPhotoColor = dominant
                                    }
                                }
                            ) { _ ->
                                nameOverlay()
                            }
                        } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight)
                                .background(artistPageBg)
                        ) {
                            if (!profileArt.isNullOrBlank()) {
                                AsyncImage(
                                    model = heroArtUrl,
                                    contentDescription = displayName,
                                    contentScale = ContentScale.Crop,
                                    // En tablet la foto se recorta a lo ancho: enfocar el tercio superior (cara del artista).
                                    alignment = if (tabletArtist) androidx.compose.ui.BiasAlignment(0f, -0.25f) else Alignment.TopCenter,
                                    onSuccess = { state ->
                                        scope.launch(Dispatchers.Default) {
                                            val dominant = extractVibrantDominantColor(state.result.drawable, focusSubject = true)
                                            if (dominant != null) extractedPhotoColor = dominant
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (!current?.motionHeroUrl.isNullOrBlank()) {
                                MotionArtworkVideo(
                                    videoUrl = current?.motionHeroUrl,
                                    isActive = seeAllSection == null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Degradado progresivo suave para que no se note ningún salto entre la foto y el color de fondo
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.46f to Color.Transparent,
                                            0.66f to artistPageBg.copy(alpha = 0.34f),
                                            0.82f to artistPageBg.copy(alpha = 0.80f),
                                            0.93f to artistPageBg.copy(alpha = 0.96f),
                                            1f to artistPageBg
                                        )
                                    )
                            )
                            nameOverlay()
                        }
                        }

                        // Botones compactos centrados estilo captura, 100% opacos en la paleta del artista
                        Row(
                            modifier = if (tabletArtist) {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = heroSidePadding, end = heroSidePadding, top = 6.dp, bottom = 6.dp)
                            } else {
                                Modifier.padding(top = 6.dp, bottom = 6.dp)
                            },
                            horizontalArrangement = Arrangement.spacedBy(12.dp, if (tabletArtist) Alignment.Start else Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = artistCardBg,
                                contentColor = onArtistBg,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { showAboutPage = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = "Acerca de",
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = artistCardBg,
                                contentColor = if (isArtistFavorite) Color(0xFFFFD54F) else onArtistBg,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { mediaActions?.onToggleFavorite?.invoke(artistItem) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isArtistFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Favorito",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = artistAccentPill,
                                contentColor = artistAccentInk,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                    val songs = current?.topSongs.orEmpty()
                                    if (songs.isNotEmpty() && mediaActions?.onPlayTrackList != null) {
                                        mediaActions.onPlayTrackList.invoke(songs, 0, current?.name ?: seed.title, false)
                                    } else {
                                        onPlay(songs.firstOrNull() ?: seed)
                                    }
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Reproducir",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = artistAccentPill,
                                contentColor = artistAccentInk,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        if (mediaActions != null) {
                                            mediaActions.onStartStation(artistItem)
                                        } else {
                                            onPlay(seed)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Estación",
                                        modifier = Modifier.size(23.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = artistCardBg,
                                contentColor = onArtistBg,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        mediaActions?.onOpenMenu?.invoke(
                                            MediaMenuRequest(
                                                item = artistItem,
                                                artistIdHint = artistItem.id,
                                                onNavigateItem = onNavigateItem
                                            )
                                        )
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
                }

                if (loading && current == null) {
                    item(key = "artist_sections_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = artistAccentPill)
                        }
                    }
                } else if (current == null) {
                    item(key = "artist_load_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("No se pudo cargar el artista", color = onArtistMuted)
                            FilledTonalButton(onClick = { onPlay(seed) }) {
                                Text("Reproducir estación del artista")
                            }
                        }
                    }
                } else {
                    current.latestRelease?.let { latest ->
                        item(key = "latest_release") {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Último lanzamiento",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = onArtistBg
                                )
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = artistCardBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable { onNavigateItem(latest) }
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        ArtworkBox(
                                            artworkUrl = latest.artworkUrl,
                                            fallbackColor = parseHexColor(latest.bgColorHex) ?: artistPillBg,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = latest.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = onArtistBg,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (latest.isExplicit) ExplicitBadge(onArtistMuted)
                                            }
                                            if (latest.subtitle.isNotBlank()) {
                                                Text(
                                                    text = latest.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = onArtistMuted,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = artistPillBg,
                                            contentColor = onArtistBg,
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                if (current.topSongs.isNotEmpty()) {
                    item(key = "top_songs") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArtistSectionHeader(
                                title = "Top canciones",
                                showSeeAll = current.topSongs.size > 6,
                                onSeeAll = {
                                    seeAllSection = ArtistSeeAllSection(
                                        title = "Top canciones · ${current.name}",
                                        items = current.topSongs,
                                        isSongs = true
                                    )
                                }
                            )
                            val topShown = current.topSongs.take(6)
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                topShown.forEachIndexed { idx, song ->
                                    ExpressiveMediaRow(
                                        item = song,
                                        shape = segmentedRowShape(idx == 0, idx == topShown.lastIndex),
                                        onClick = {
                                            if (mediaActions?.onPlayTrackList != null) {
                                                mediaActions.onPlayTrackList.invoke(current.topSongs, idx, current.name, false)
                                            } else {
                                                onPlay(song)
                                            }
                                        },
                                        containerColor = artistCardBg,
                                        contentColor = onArtistBg,
                                        secondaryContentColor = onArtistMuted,
                                        playButtonBg = artistAccentPill,
                                        playButtonFg = artistAccentInk
                                    )
                                }
                            }
                        }
                    }
                }

                if (current.featuredAlbums.isNotEmpty()) {
                    item(key = "featured_albums") {
                        ArtistCarouselSection(
                            title = "Álbumes imprescindibles",
                            items = current.featuredAlbums,
                            onItemClick = onNavigateItem,
                            onSeeAll = if (current.featuredAlbums.size > 3) {
                                {
                                    seeAllSection = ArtistSeeAllSection("Álbumes imprescindibles", current.featuredAlbums)
                                }
                            } else null
                        )
                    }
                }

                if (current.albums.isNotEmpty()) {
                    item(key = "albums") {
                        ArtistCarouselSection(
                            title = "Álbumes",
                            items = current.albums,
                            onItemClick = onNavigateItem,
                            onSeeAll = if (current.albums.size > 3) {
                                { seeAllSection = ArtistSeeAllSection("Álbumes · ${current.name}", current.albums) }
                            } else null
                        )
                    }
                }

                if (current.singles.isNotEmpty()) {
                    item(key = "singles") {
                        ArtistCarouselSection(
                            title = "Sencillos y EP",
                            items = current.singles,
                            onItemClick = onNavigateItem,
                            onSeeAll = if (current.singles.size > 3) {
                                { seeAllSection = ArtistSeeAllSection("Sencillos y EP · ${current.name}", current.singles) }
                            } else null
                        )
                    }
                }

                if (current.playlists.isNotEmpty()) {
                    item(key = "playlists") {
                        ArtistCarouselSection(
                            title = "Playlists",
                            items = current.playlists,
                            onItemClick = onNavigateItem,
                            onSeeAll = if (current.playlists.size > 3) {
                                { seeAllSection = ArtistSeeAllSection("Playlists · ${current.name}", current.playlists) }
                            } else null
                        )
                    }
                }

                if (current.musicVideos.isNotEmpty()) {
                    item(key = "videos") {
                        val vidActionCtrl = LocalMediaActionController.current
                        val vidStrings = com.applemusic.android.ui.theme.LocalAppStrings.current
                        ArtistVideoCarousel(
                            title = if (vidStrings.isEnglish) "Music Videos" else "Videos musicales",
                            items = current.musicVideos,
                            onItemClick = { clickedVideo ->
                                val idx = current.musicVideos.indexOfFirst { it.id == clickedVideo.id }.coerceAtLeast(0)
                                val playList = vidActionCtrl?.onPlayTrackList
                                if (playList != null && current.musicVideos.size > 1) {
                                    playList(current.musicVideos, idx, current.name, false)
                                } else {
                                    onPlay(clickedVideo)
                                }
                            },
                            onSeeAll = if (current.musicVideos.size > 2) {
                                {
                                    seeAllSection = ArtistSeeAllSection(
                                        title = "Videos · ${current.name}",
                                        items = current.musicVideos,
                                        isVideos = true
                                    )
                                }
                            } else null
                        )
                    }
                }

                if (current.liveAlbums.isNotEmpty()) {
                    item(key = "live_albums") {
                        ArtistCarouselSection(
                            title = "Álbumes en vivo",
                            items = current.liveAlbums,
                            onItemClick = onNavigateItem,
                            onSeeAll = if (current.liveAlbums.size > 3) {
                                { seeAllSection = ArtistSeeAllSection("Álbumes en vivo", current.liveAlbums) }
                            } else null
                        )
                    }
                }

                if (current.compilationAlbums.isNotEmpty()) {
                    item(key = "compilation_albums") {
                        ArtistCarouselSection(
                            title = "Recopilaciones",
                            items = current.compilationAlbums,
                            onItemClick = onNavigateItem,
                            onSeeAll = if (current.compilationAlbums.size > 3) {
                                { seeAllSection = ArtistSeeAllSection("Recopilaciones", current.compilationAlbums) }
                            } else null
                        )
                    }
                }

                if (current.appearsOnAlbums.isNotEmpty()) {
                    item(key = "appears_on") {
                        ArtistCarouselSection(
                            title = "Aparece en",
                            items = current.appearsOnAlbums,
                            onItemClick = onNavigateItem,
                            onSeeAll = if (current.appearsOnAlbums.size > 3) {
                                { seeAllSection = ArtistSeeAllSection("Aparece en", current.appearsOnAlbums) }
                            } else null
                        )
                    }
                }

                if (current.similarArtists.isNotEmpty()) {
                    item(key = "similar") {
                        ArtistSimilarCarousel(
                            title = "Artistas similares",
                            items = current.similarArtists,
                            onItemClick = { sim -> onNavigateItem(sim.copy(type = "artists")) },
                            onSeeAll = if (current.similarArtists.size > 3) {
                                {
                                    seeAllSection = ArtistSeeAllSection(
                                        title = "Artistas similares",
                                        items = current.similarArtists.map { it.copy(type = "artists") },
                                        isCircular = false
                                    )
                                }
                            } else null
                        )
                    }
                }

                }
            }

            // Blur sutil concentrado arriba de la píldora (barra de estado)
            TopProgressiveBlur(
                hazeState = topHazeState,
                height = statusBarTop + 32.dp,
                tintColor = artistPageBg,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Píldora de regreso siempre visible + píldora del nombre que aparece al pasar la foto de perfil
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
                    color = artistPillBg,
                    contentColor = onArtistBg,
                    shadowElevation = 6.dp
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                }

                AnimatedVisibility(
                    visible = showTitlePill,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = artistPillBg,
                        contentColor = onArtistBg,
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = current?.name ?: seed.title,
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
        }
        }

        seeAllSection?.let { sec ->
            ArtistSeeAllOverlay(
                section = sec,
                onDismiss = { seeAllSection = null },
                onItemClick = { item ->
                    seeAllSection = null
                    when {
                        sec.isSongs && mediaActions?.onPlayTrackList != null -> {
                            val idx = sec.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                            mediaActions.onPlayTrackList.invoke(sec.items, idx, current?.name ?: seed.title, false)
                        }
                        sec.isSongs || sec.isVideos -> onPlay(item)
                        else -> onNavigateItem(item)
                    }
                }
            )
        }

        if (showAboutPage && current != null) {
            ArtistAboutOverlay(
                artist = current,
                fallbackArtworkUrl = seed.artworkUrl,
                accentBg = artistPageBg,
                pillBg = artistPillBg,
                onDismiss = { showAboutPage = false }
            )
        }
    }
}

/**
 * Pantalla dedicada de información del artista ("Acerca de") con foto de perfil full-bleed,
 * origen, fecha de nacimiento/formación, género y biografía completa.
 */
@Composable
private fun ArtistAboutOverlay(
    artist: ArtistDetail,
    fallbackArtworkUrl: String?,
    accentBg: Color,
    pillBg: Color,
    onDismiss: () -> Unit
) {
    val bgColor = lerp(Color(0xFF0A0A0C), accentBg, 0.28f)
    val photoUrl = artist.artworkUrl?.takeIf { it.isNotBlank() }
        ?: artist.heroUrl?.takeIf { it.isNotBlank() }
        ?: fallbackArtworkUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        // Tablet: foto panorámica en alta resolución y texto con ancho de lectura cómodo.
        val aboutAdaptive = rememberAdaptiveInfo()
        val tabletAbout = aboutAdaptive.isExpandedWidth
        val aboutSidePadding = if (tabletAbout) 40.dp else 22.dp
        val aboutHeroHeight = if (tabletAbout) {
            (aboutAdaptive.heightDp * 0.55f).dp.coerceIn(340.dp, 520.dp)
        } else {
            adaptiveHeroHeight(410.dp)
        }
        val aboutPhotoUrl = if (tabletAbout) {
            photoUrl?.replace(Regex("""/\d+x\d+([a-zA-Z]*)\."""), "/2000x2000$1.")
        } else photoUrl

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 140.dp)
        ) {
            // Nombre del artista sobre la parte baja de la foto
            val aboutName: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
                Text(
                    text = artist.name,
                    style = if (tabletAbout) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = aboutSidePadding, vertical = 12.dp)
                )
            }
            if (tabletAbout) {
                // TABLET: foto completa sin recortes
                TabletHeroMedia(
                    imageUrl = aboutPhotoUrl,
                    videoUrl = null,
                    backgroundColor = bgColor,
                    contentDescription = artist.name
                ) { _ -> aboutName() }
            } else {
            // Foto de perfil grande con degradado hacia el fondo oscuro
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(aboutHeroHeight)
                    .background(bgColor)
            ) {
                if (!aboutPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = aboutPhotoUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        alignment = if (tabletAbout) androidx.compose.ui.BiasAlignment(0f, -0.25f) else Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.28f),
                                0.30f to Color.Transparent,
                                0.58f to bgColor.copy(alpha = 0.32f),
                                0.80f to bgColor.copy(alpha = 0.84f),
                                0.94f to bgColor.copy(alpha = 0.97f),
                                1f to bgColor
                            )
                        )
                )
                aboutName()
            }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = AdaptiveReadableMaxWidth + aboutSidePadding * 2)
                    .fillMaxWidth()
                    .padding(horizontal = aboutSidePadding),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Cuadrícula de metadatos estilo Apple Music (De | Fecha de nacimiento / Género)
                if (!artist.origin.isNullOrBlank() || !artist.bornOrFormed.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (!artist.origin.isNullOrBlank()) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "De",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.54f)
                                )
                                Text(
                                    text = artist.origin,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        val aboutStrings = com.applemusic.android.ui.theme.LocalAppStrings.current
                        if (!artist.bornOrFormed.isNullOrBlank()) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = aboutStrings.born,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.54f)
                                )
                                Text(
                                    text = artist.bornOrFormed,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                val aboutStringsMain = com.applemusic.android.ui.theme.LocalAppStrings.current
                if (!artist.genre.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = aboutStringsMain.genre,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.54f)
                        )
                        Text(
                            text = artist.genre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Sección "Acerca de"
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = aboutStringsMain.about,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = artist.bio?.takeIf { it.isNotBlank() }
                            ?: if (aboutStringsMain.isEnglish) {
                                "No biography available for ${artist.name} right now."
                            } else {
                                "No hay biografía disponible para ${artist.name} en este momento."
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 25.sp,
                        color = Color.White.copy(alpha = 0.86f)
                    )
                }
            }
        }

        // Botón circular de cerrar (✕) arriba a la izquierda
        Surface(
            shape = CircleShape,
            color = pillBg,
            contentColor = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar información",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistSectionHeader(
    title: String,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (showSeeAll) Modifier.clickable(onClick = onSeeAll) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (showSeeAll) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Ver todo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ArtistCarouselSection(
    title: String,
    items: List<HomeItem>,
    onItemClick: (HomeItem) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    val distinct = remember(items) { items.distinctBy { "${it.type}_${it.id}" } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArtistSectionHeader(title = title, showSeeAll = onSeeAll != null, onSeeAll = { onSeeAll?.invoke() })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.height(218.dp)
        ) {
            items(distinct, key = { "${title}_${it.type}_${it.id}" }) { item ->
                val accent = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                Column(
                    modifier = Modifier
                        .width(152.dp)
                        .clickable { onItemClick(item) }
                ) {
                    ArtworkBox(
                        artworkUrl = item.artworkUrl,
                        fallbackColor = accent,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(152.dp)
                            .clip(CoverShape)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CoverShape)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
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
    }
}

@Composable
private fun ArtistVideoCarousel(
    title: String,
    items: List<HomeItem>,
    onItemClick: (HomeItem) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    val distinct = remember(items) { items.distinctBy { "${it.type}_${it.id}" } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArtistSectionHeader(title = title, showSeeAll = onSeeAll != null, onSeeAll = { onSeeAll?.invoke() })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(distinct, key = { "vid_${it.type}_${it.id}" }) { item ->
                val accent = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                Column(
                    modifier = Modifier
                        .width(236.dp)
                        .clickable { onItemClick(item) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(CoverShape)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CoverShape)
                    ) {
                        ArtworkBox(
                            artworkUrl = item.artworkUrl,
                            fallbackColor = accent,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Reproducir video",
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
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
    }
}

@Composable
private fun ArtistSimilarCarousel(
    title: String,
    items: List<HomeItem>,
    onItemClick: (HomeItem) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    val distinct = remember(items) { items.distinctBy { "${it.type}_${it.id}" } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArtistSectionHeader(title = title, showSeeAll = onSeeAll != null, onSeeAll = { onSeeAll?.invoke() })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(distinct, key = { "sim_${it.type}_${it.id}" }) { item ->
                val accent = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                val artistTileShape = RoundedCornerShape(22.dp)
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onItemClick(item) }
                ) {
                    ArtworkBox(
                        artworkUrl = item.artworkUrl,
                        fallbackColor = accent,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(132.dp)
                            .clip(artistTileShape)
                            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), artistTileShape)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSeeAllOverlay(
    section: ArtistSeeAllSection,
    onDismiss: () -> Unit,
    onItemClick: (HomeItem) -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerClearance = statusBarTop + 74.dp
    val topHazeState = rememberHazeState()
    val distinct = remember(section.items) { section.items.distinctBy { "${it.type}_${it.id}" } }

    InAppPageContainer(onBack = onDismiss) {
        if (section.isSongs) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = topHazeState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerClearance, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(distinct, key = { _, it -> "all_song_${it.id}" }) { idx, item ->
                    ExpressiveMediaRow(
                        item = item,
                        shape = segmentedRowShape(idx == 0, idx == distinct.lastIndex),
                        onClick = { onItemClick(item) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (section.isCircular) 100.dp else 150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerClearance, bottom = 132.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = topHazeState)
            ) {
                gridItems(distinct, key = { "all_${it.type}_${it.id}" }) { item ->
                    val accent = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                    Column(
                        horizontalAlignment = if (section.isCircular) Alignment.CenterHorizontally else Alignment.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                    ) {
                        val shape = if (section.isCircular) CircleShape else CoverShape
                        ArtworkBox(
                            artworkUrl = item.artworkUrl,
                            fallbackColor = accent,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(if (section.isVideos) 16f / 9f else 1f)
                                .clip(shape)
                                .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = if (section.isCircular) TextAlign.Center else TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!section.isCircular && item.subtitle.isNotBlank()) {
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
        }

        TopProgressiveBlur(
            hazeState = topHazeState,
            height = headerClearance + 10.dp,
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
    }
}

/** Perfil social de amigo (foto, handle, géneros y playlists compartidas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialProfileDialog(
    seed: HomeItem,
    loadProfile: suspend (String) -> SocialProfileDetail?,
    onDismiss: () -> Unit,
    onNavigateItem: (HomeItem) -> Unit
) {
    var profile by remember(seed.id) { mutableStateOf<SocialProfileDetail?>(null) }
    var loading by remember(seed.id) { mutableStateOf(true) }
    LaunchedEffect(seed.id) {
        profile = loadProfile(seed.id)
        loading = false
    }

    val current = profile
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerClearance = statusBarTop + 76.dp
    val topHazeState = rememberHazeState()

    InAppPageContainer(onBack = onDismiss) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Perfil no disponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = topHazeState),
                contentPadding = PaddingValues(top = headerClearance, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArtworkBox(
                            artworkUrl = current.avatarUrl ?: seed.artworkUrl,
                            fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                        )
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        current.handle?.takeIf { it.isNotBlank() }?.let { h ->
                            Text("@$h", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current.topGenres.isNotEmpty()) {
                            Text(
                                text = current.topGenres.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppleRed,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (current.sharedPlaylists.isNotEmpty()) {
                    item {
                        DetailCarouselRow("Playlists compartidas", current.sharedPlaylists, onNavigateItem)
                    }
                } else {
                    item {
                        Text(
                            text = "Este perfil no tiene playlists públicas visibles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        TopProgressiveBlur(
            hazeState = topHazeState,
            height = headerClearance + 10.dp,
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
                    text = current?.name ?: seed.title,
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

/** Apple Music Replay anual y mensual (`/v1/me/music-summaries`) rediseñado 100% con Material 3 Expressive. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplayDetailDialog(
    loadReplay: suspend (Int?, ReplayPeriodOption?) -> ReplaySummary?,
    onDismiss: () -> Unit,
    onNavigateItem: (HomeItem) -> Unit,
    onPlay: (HomeItem) -> Unit
) {
    val strings = com.applemusic.android.ui.theme.LocalAppStrings.current
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedMonth by remember { mutableStateOf<ReplayPeriodOption?>(null) }
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedYear, selectedMonth?.id) {
        loading = true
        summary = loadReplay(selectedYear, selectedMonth)
        loading = false
    }

    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = cs.background,
            contentColor = cs.onBackground
        ) {
            Box(Modifier.fillMaxSize()) {
                val current = summary
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                    current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = cs.surfaceContainerHigh,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = if (strings.isEnglish) "Replay unavailable for this period" else "Replay no disponible para este período",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = {
                                        selectedMonth = null
                                        selectedYear = null
                                    },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(if (strings.isEnglish) "Back to current year" else "Volver al año actual")
                                }
                            }
                        }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 84.dp, bottom = 56.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Selector de año (Segmented Button M3 Expressive)
                        if (current.availableYears.isNotEmpty()) {
                            item(key = "replay_years") {
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    val yrs = current.availableYears.take(5)
                                    yrs.forEachIndexed { idx, yr ->
                                        SegmentedButton(
                                            selected = yr == current.selectedYear && current.selectedMonth == null,
                                            onClick = {
                                                selectedMonth = null
                                                selectedYear = yr
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = yrs.size),
                                            colors = SegmentedButtonDefaults.colors(
                                                activeContainerColor = cs.primaryContainer,
                                                activeContentColor = cs.onPrimaryContainer,
                                                inactiveContainerColor = cs.surfaceContainer,
                                                inactiveContentColor = cs.onSurfaceVariant
                                            )
                                        ) {
                                            Text(yr.toString(), fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }

                        // Píldoras horizontales de meses (M3 Expressive FilterChips)
                        if (current.availableMonths.isNotEmpty()) {
                            item(key = "replay_months") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = current.selectedMonth == null,
                                        onClick = {
                                            selectedMonth = null
                                            selectedYear = current.selectedYear
                                        },
                                        label = {
                                            Text(
                                                text = if (strings.isEnglish) "Full Year" else "Año completo",
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        shape = CircleShape
                                    )
                                    current.availableMonths.forEach { m ->
                                        FilterChip(
                                            selected = current.selectedMonth == m.month,
                                            onClick = {
                                                selectedYear = m.year
                                                selectedMonth = m
                                            },
                                            label = { Text(m.label, fontWeight = FontWeight.SemiBold) },
                                            shape = CircleShape
                                        )
                                    }
                                }
                            }
                        }

                        // Hero Bento Expressive con tiempo escuchado y cuadrícula de métricas
                        item(key = "replay_hero") { ReplayHero(current, strings) }

                        // Tarjeta tonal de Playlist Replay con botón Play Expressive
                        current.playlist?.let { pl ->
                            item(key = "replay_playlist") {
                                Surface(
                                    shape = RoundedCornerShape(32.dp),
                                    color = cs.secondaryContainer,
                                    contentColor = cs.onSecondaryContainer,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(32.dp))
                                        .clickable { onNavigateItem(pl) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        AsyncImage(
                                            model = pl.artworkUrl,
                                            contentDescription = pl.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = pl.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (strings.isEnglish) "Your Replay playlist" else "Tu playlist Replay",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = cs.onSecondaryContainer.copy(alpha = 0.78f)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = cs.primary,
                                            contentColor = cs.onPrimary,
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .clickable { onPlay(pl) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = strings.play,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Podio #1 Bento (Artista #1 + Álbum #1)
                        if (current.topArtists.isNotEmpty() || current.topAlbums.isNotEmpty()) {
                            item(key = "replay_podium") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    current.topArtists.firstOrNull()?.let { a1 ->
                                        ReplaySpotlightCard(
                                            label = if (strings.isEnglish) "#1 ARTIST" else "ARTISTA #1",
                                            ranked = a1,
                                            badgeBg = cs.primaryContainer,
                                            badgeFg = cs.onPrimaryContainer,
                                            cardColor = cs.surfaceContainerHigh,
                                            playsSuffix = if (strings.isEnglish) "plays" else "rep.",
                                            onClick = { onNavigateItem(a1.item) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    current.topAlbums.firstOrNull()?.let { al1 ->
                                        ReplaySpotlightCard(
                                            label = if (strings.isEnglish) "#1 ALBUM" else "ÁLBUM #1",
                                            ranked = al1,
                                            badgeBg = cs.tertiaryContainer,
                                            badgeFg = cs.onTertiaryContainer,
                                            cardColor = cs.surfaceContainerHigh,
                                            playsSuffix = if (strings.isEnglish) "plays" else "rep.",
                                            onClick = { onNavigateItem(al1.item) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Carruseles M3 Expressive: Top Artistas y Top Álbumes
                        if (current.topArtists.isNotEmpty()) {
                            item(key = "replay_top_artists") {
                                ReplayRankedCarousel(
                                    title = if (strings.isEnglish) "Top Artists" else "Top artistas",
                                    items = current.topArtists,
                                    playsSuffix = if (strings.isEnglish) "plays" else "rep.",
                                    onClick = onNavigateItem
                                )
                            }
                        }
                        if (current.topAlbums.isNotEmpty()) {
                            item(key = "replay_top_albums") {
                                ReplayRankedCarousel(
                                    title = if (strings.isEnglish) "Top Albums" else "Top álbumes",
                                    items = current.topAlbums,
                                    playsSuffix = if (strings.isEnglish) "plays" else "rep.",
                                    onClick = onNavigateItem
                                )
                            }
                        }

                        // Top Canciones: lista agrupada segmentada M3 Expressive con barras de proporción
                        if (current.topSongs.isNotEmpty()) {
                            item(key = "replay_top_songs") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ReplaySectionTitle(if (strings.isEnglish) "Top Songs" else "Top canciones")
                                    val songs = current.topSongs.take(15)
                                    val maxPlays = (songs.firstOrNull()?.playCount ?: 1).coerceAtLeast(1)
                                    val playsUnit = if (strings.isEnglish) "plays" else "rep."
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        songs.forEachIndexed { idx, entry ->
                                            val rowShape = when {
                                                songs.size == 1 -> RoundedCornerShape(26.dp)
                                                idx == 0 -> RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                                                idx == songs.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 26.dp, bottomEnd = 26.dp)
                                                else -> RoundedCornerShape(8.dp)
                                            }
                                            val isTop3 = idx < 3
                                            val ratio = (entry.playCount.toFloat() / maxPlays.toFloat()).coerceIn(0.08f, 1f)
                                            Surface(
                                                shape = rowShape,
                                                color = if (isTop3) cs.surfaceContainerHigh else cs.surfaceContainer,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(rowShape)
                                                    .clickable { onPlay(entry.item) }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Surface(
                                                            shape = if (isTop3) RoundedCornerShape(10.dp) else CircleShape,
                                                            color = if (isTop3) cs.primaryContainer else cs.surfaceContainerHighest,
                                                            contentColor = if (isTop3) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = "${idx + 1}",
                                                                    fontWeight = FontWeight.Black,
                                                                    style = MaterialTheme.typography.labelMedium
                                                                )
                                                            }
                                                        }
                                                        AsyncImage(
                                                            model = entry.item.artworkUrl,
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(RoundedCornerShape(14.dp))
                                                        )
                                                        Column(Modifier.weight(1f)) {
                                                            Text(
                                                                text = entry.item.title,
                                                                color = cs.onSurface,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "${entry.item.subtitle} · ${entry.playCount} $playsUnit",
                                                                color = cs.onSurfaceVariant,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        Icon(
                                                            imageVector = Icons.Rounded.PlayArrow,
                                                            contentDescription = null,
                                                            tint = cs.primary
                                                        )
                                                    }
                                                    if (isTop3) {
                                                        LinearWavyProgressIndicator(
                                                            progress = { ratio },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            color = cs.primary,
                                                            trackColor = cs.surfaceContainerHighest
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Géneros principales con indicadores ondulados M3 Expressive
                        if (current.topGenres.isNotEmpty()) {
                            item(key = "replay_genres") {
                                Surface(
                                    shape = RoundedCornerShape(32.dp),
                                    color = cs.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = if (strings.isEnglish) "Top Genres" else "Géneros principales",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = cs.onSurface
                                        )
                                        val genreColors = listOf(cs.primary, cs.tertiary, cs.secondary)
                                        current.topGenres.forEachIndexed { gIdx, (genre, pct) ->
                                            val barColor = genreColors[gIdx % genreColors.size]
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(genre, color = cs.onSurface, fontWeight = FontWeight.Bold)
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = barColor.copy(alpha = 0.16f)
                                                    ) {
                                                        Text(
                                                            text = "$pct%",
                                                            color = barColor,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                                LinearWavyProgressIndicator(
                                                    progress = { (pct / 100f).coerceIn(0.05f, 1f) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = barColor,
                                                    trackColor = cs.surfaceContainerHighest
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Cabecera flotante en píldoras Material 3 Expressive
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
                        color = cs.surfaceContainerHigh,
                        contentColor = cs.onSurface,
                        shadowElevation = 4.dp
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = cs.surfaceContainerHigh,
                        contentColor = cs.onSurface,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Replay",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            summary?.let { s ->
                                Surface(
                                    shape = CircleShape,
                                    color = cs.primaryContainer,
                                    contentColor = cs.onPrimaryContainer
                                ) {
                                    Text(
                                        text = s.periodLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
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

@Composable
private fun ReplaySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun ReplayHero(
    current: ReplaySummary,
    strings: com.applemusic.android.ui.theme.AppStrings
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tarjeta Hero principal en primaryContainer (Material 3 Expressive)
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = cs.primary,
                        contentColor = cs.onPrimary
                    ) {
                        Text(
                            text = "REPLAY ${current.periodLabel.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Text(
                        text = "${current.listenTimeMinutes} min",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${current.listenHours}",
                        fontSize = 84.sp,
                        lineHeight = 84.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-3).sp,
                        color = cs.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (strings.isEnglish) "hours" else "horas",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Text(
                    text = if (strings.isEnglish) {
                        "and ${current.remainingMinutes} minutes of music"
                    } else {
                        "y ${current.remainingMinutes} minutos de música"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.84f)
                )
            }
        }

        // Fila Bento de 3 tarjetas expresivas (Artistas / Álbumes / Canciones)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplayStatPill(
                value = "${current.uniqueArtistCount}",
                label = strings.artists,
                containerColor = cs.secondaryContainer,
                contentColor = cs.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            ReplayStatPill(
                value = "${current.uniqueAlbumCount}",
                label = strings.albums,
                containerColor = cs.tertiaryContainer,
                contentColor = cs.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            ReplayStatPill(
                value = "${current.uniqueSongCount}",
                label = strings.songs,
                containerColor = cs.surfaceContainerHighest,
                contentColor = cs.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReplayStatPill(
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = value,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReplaySpotlightCard(
    label: String,
    ranked: ReplayRankedItem,
    badgeBg: Color,
    badgeFg: Color,
    cardColor: Color,
    playsSuffix: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = cardColor,
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = badgeBg, contentColor = badgeFg) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            AsyncImage(
                model = ranked.item.artworkUrl,
                contentDescription = ranked.item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cs.surfaceContainerHighest)
            )
            Text(
                text = ranked.item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${ranked.playCount} $playsSuffix",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReplayRankedCarousel(
    title: String,
    items: List<ReplayRankedItem>,
    playsSuffix: String,
    onClick: (HomeItem) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val distinctItems = remember(items) { items.distinctBy { "${it.item.type}_${it.item.id}" } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReplaySectionTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(distinctItems, key = { _, entry -> "${entry.item.type}_${entry.item.id}" }) { idx, ranked ->
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier
                        .width(156.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { onClick(ranked.item) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box {
                            AsyncImage(
                                model = ranked.item.artworkUrl,
                                contentDescription = ranked.item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(cs.surfaceContainerHighest)
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (idx < 3) cs.primaryContainer else cs.surfaceContainerHighest,
                                contentColor = if (idx < 3) cs.onPrimaryContainer else cs.onSurface,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = ranked.item.title,
                            color = cs.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${ranked.playCount} $playsSuffix",
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCarouselRow(
    title: String,
    items: List<HomeItem>,
    onClick: (HomeItem) -> Unit
) {
    val distinctItems = remember(items) { items.distinctBy { "${it.type}_${it.id}" } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(distinctItems, key = { "${it.type}_${it.id}" }) { item ->
                Column(modifier = Modifier.width(148.dp).clickable { onClick(item) }) {
                    AsyncImage(
                        model = item.artworkUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(148.dp)
                            .clip(CoverShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) {
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
