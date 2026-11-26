package com.applemusic.android.ui.home

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.ItemAccountStatus
import com.applemusic.android.data.model.SongCreditsDetail

@Immutable
data class MediaMenuRequest(
    val item: HomeItem,
    val albumIdHint: String? = null,
    val artistIdHint: String? = null,
    val onNavigateItem: ((HomeItem) -> Unit)? = null,
    val accentColor: Color? = null,
    val playlistOwnerIdForRemoval: String? = null,
    val isOwnPlaylist: Boolean = false,
    val onTrackRemovedFromPlaylist: (() -> Unit)? = null,
    val onPlaylistDeleted: (() -> Unit)? = null
)

class MediaActionController(
    val favoriteOverrides: () -> Map<String, Boolean>,
    val onOpenMenu: (MediaMenuRequest) -> Unit,
    val onToggleFavorite: (HomeItem) -> Unit,
    val onOpenCredits: (HomeItem, ((HomeItem) -> Unit)?) -> Unit,
    val onStartStation: (HomeItem) -> Unit = {},
    val onRefreshStatus: (HomeItem) -> Unit = {},
    val onToggleInLibrary: ((HomeItem, Boolean, String?) -> Unit)? = null,
    val loadItemStatus: (suspend (HomeItem) -> ItemAccountStatus?)? = null,
    val onSyncFavoritesForAlbum: ((com.applemusic.android.data.model.AlbumDetail) -> Unit)? = null,
    val onPlayTrackList: ((tracks: List<HomeItem>, startIndex: Int, contextTitle: String?, shuffle: Boolean) -> Unit)? = null,
    val isPinned: (HomeItem) -> Boolean = { false },
    val onTogglePin: ((HomeItem) -> Unit)? = null,
    val loadArtistArtwork: (suspend (String) -> String?)? = null,
    val onSetSleepTimer: ((Int) -> Unit)? = null,
    val sleepTimerLabel: (() -> String?)? = null,
    val onOpenLyricsInPlayer: (() -> Unit)? = null,
    val onOpenQueueInPlayer: (() -> Unit)? = null
)

val LocalMediaActionController = compositionLocalOf<MediaActionController?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionsBottomSheet(
    request: MediaMenuRequest,
    autoplayEnabled: Boolean,
    favoriteOverrides: Map<String, Boolean>,
    loadItemStatus: suspend (HomeItem) -> ItemAccountStatus?,
    onToggleFavorite: (HomeItem, Boolean?) -> Unit,
    onToggleInLibrary: (HomeItem, Boolean, String?) -> Unit,
    onAddToPlaylist: (HomeItem) -> Unit,
    onPlayNext: (HomeItem) -> Unit,
    onPlayLater: (HomeItem) -> Unit,
    onToggleAutoplay: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: (HomeItem) -> Unit,
    onStartStation: (HomeItem) -> Unit,
    onViewCredits: (HomeItem) -> Unit,
    onGoToAlbum: (String, String?) -> Unit,
    onGoToArtist: (String, String?) -> Unit,
    onRemoveFromPlaylist: (String, HomeItem, () -> Unit) -> Unit = { _, _, _ -> },
    onDeletePlaylist: (HomeItem, () -> Unit) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val item = request.item
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var status by remember(item.id) { mutableStateOf<ItemAccountStatus?>(null) }
    var sleepTimerExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        status = loadItemStatus(item)
    }

    val typeLower = item.type.lowercase()
    val isSongOrVideo = typeLower.contains("song") || typeLower.contains("video") || typeLower == "uploaded-audios"
    val isSong = typeLower.contains("song") || typeLower == "uploaded-audios"
    val isArtist = typeLower.contains("artist")
    val isPlaylist = typeLower.contains("playlist")
    val isAlbum = typeLower.contains("album")
    // Videos musicales: sin favoritos, playlists, cola, estaciones ni letra (esas opciones no existen en Apple Music para videos).
    val isVideo = typeLower.contains("video")
    val isPlayable = isSongOrVideo || isAlbum || isPlaylist || typeLower.contains("station")
    val canFavorite = !isVideo && (isSongOrVideo || isAlbum || isPlaylist || isArtist || typeLower.contains("station"))
    val canLibrary = isSongOrVideo || isAlbum || isPlaylist
    val canAddToPlaylist = !isVideo && (isSongOrVideo || isAlbum || isPlaylist)
    val canStartStation = !isVideo && (isSongOrVideo || isArtist || typeLower.contains("station"))
    val canQueue = !isVideo

    val isFavorite = favoriteOverrides[item.id] ?: status?.isFavorite ?: false
    val inLibrary = status?.inLibrary ?: (typeLower.startsWith("library-") || item.id.startsWith("i.") || item.id.startsWith("l.") || item.id.startsWith("p."))
    val resolvedAlbumId = status?.albumId ?: request.albumIdHint
    val resolvedArtistId = status?.artistId ?: request.artistIdHint

    val quickActions = buildList {
        if (isPlayable && canQueue) {
            add(
                QuickSheetAction(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    label = "Siguiente",
                    highlighted = false,
                    onClick = {
                        onPlayNext(item)
                        onDismiss()
                    }
                )
            )
        }
        if (canLibrary) {
            add(
                QuickSheetAction(
                    icon = if (inLibrary) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                    label = if (inLibrary) "En biblioteca" else "Biblioteca",
                    highlighted = inLibrary,
                    onClick = {
                        onToggleInLibrary(item, inLibrary, status?.libraryId)
                        onDismiss()
                    }
                )
            )
        }
        if (canAddToPlaylist) {
            add(
                QuickSheetAction(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "Playlist",
                    highlighted = false,
                    onClick = {
                        onDismiss()
                        onAddToPlaylist(item)
                    }
                )
            )
        }
        add(
            QuickSheetAction(
                icon = Icons.Rounded.Share,
                label = "Compartir",
                highlighted = false,
                onClick = {
                    val shareUrl = item.url?.takeIf { it.startsWith("http") }
                        ?: buildAppleMusicShareUrl(item)
                    val shareText = listOfNotNull(
                        item.title.takeIf { it.isNotBlank() },
                        item.subtitle.takeIf { it.isNotBlank() },
                        shareUrl
                    ).joinToString(" — ")
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, "Compartir con"))
                    }
                    onDismiss()
                }
            )
        )
    }.take(3)

    val strings = com.applemusic.android.ui.theme.LocalAppStrings.current
    val queueGroup = buildList {
        if (!canQueue) return@buildList
        if (isPlayable) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.Queue,
                    title = strings.playLater,
                    onClick = {
                        onPlayLater(item)
                        onDismiss()
                    }
                )
            )
        }
        add(
            SheetMenuEntry(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = strings.viewQueue,
                onClick = {
                    onDismiss()
                    onOpenQueue()
                }
            )
        )
    }

    val detailsGroup = buildList {
        val actionCtrl = LocalMediaActionController.current
        val canPin = isAlbum || isPlaylist || isArtist || isSongOrVideo
        if (canPin && actionCtrl?.onTogglePin != null) {
            val pinnedNow = actionCtrl.isPinned(item)
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.PushPin,
                    title = if (pinnedNow) strings.unpinFromLibrary else strings.pinToLibrary,
                    onClick = {
                        actionCtrl.onTogglePin.invoke(item)
                        onDismiss()
                    }
                )
            )
        }
        if (canStartStation) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.Radio,
                    title = if (isArtist) strings.artistRadio else strings.createStation,
                    onClick = {
                        onStartStation(item)
                        onDismiss()
                    }
                )
            )
        }
        if (isSong) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.Lyrics,
                    title = strings.viewLyrics,
                    onClick = {
                        onDismiss()
                        onOpenLyrics(item)
                    }
                )
            )
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.Info,
                    title = strings.viewCredits,
                    onClick = {
                        onDismiss()
                        onViewCredits(item)
                    }
                )
            )
        }
        if (isSong && !resolvedAlbumId.isNullOrBlank()) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.Album,
                    title = strings.goToAlbum,
                    onClick = {
                        onDismiss()
                        onGoToAlbum(resolvedAlbumId, item.artworkUrl)
                    }
                )
            )
        }
        if (!isArtist && !resolvedArtistId.isNullOrBlank()) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.Person,
                    title = strings.goToArtist,
                    onClick = {
                        onDismiss()
                        onGoToArtist(resolvedArtistId, item.subtitle.ifBlank { item.title })
                    }
                )
            )
        }
        val removePlaylistId = request.playlistOwnerIdForRemoval?.takeIf { it.isNotBlank() }
        if (removePlaylistId != null) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.PlaylistRemove,
                    title = strings.removeFromPlaylist,
                    isDestructive = true,
                    onClick = {
                        onRemoveFromPlaylist(removePlaylistId, item) {
                            request.onTrackRemovedFromPlaylist?.invoke()
                        }
                        onDismiss()
                    }
                )
            )
        }
        if (request.isOwnPlaylist || (item.type == "library-playlists" && item.id.startsWith("p."))) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.DeleteForever,
                    title = strings.deletePlaylist,
                    isDestructive = true,
                    onClick = {
                        onDeletePlaylist(item) {
                            request.onPlaylistDeleted?.invoke()
                        }
                        onDismiss()
                    }
                )
            )
        }
        if (canLibrary && inLibrary && !request.isOwnPlaylist && item.type != "library-playlists") {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Eliminar de la biblioteca",
                    isDestructive = true,
                    onClick = {
                        onToggleInLibrary(item, true, status?.libraryId)
                        onDismiss()
                    }
                )
            )
        }
        if (quickActions.none { it.label == "Compartir" }) {
            add(
                SheetMenuEntry(
                    icon = Icons.Rounded.Share,
                    title = "Compartir",
                    onClick = {
                        val shareUrl = item.url?.takeIf { it.startsWith("http") }
                            ?: buildAppleMusicShareUrl(item)
                        val shareText = listOfNotNull(
                            item.title.takeIf { it.isNotBlank() },
                            item.subtitle.takeIf { it.isNotBlank() },
                            shareUrl
                        ).joinToString(" — ")
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Compartir con"))
                        }
                        onDismiss()
                    }
                )
            )
        }
    }

    val playerAccent = request.accentColor
    val sheetBgColor = if (playerAccent != null) {
        lerp(Color(0xFF101210), playerAccent, 0.34f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBgColor = if (playerAccent != null) {
        lerp(Color(0xFF151815), playerAccent, 0.48f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val badgeBgColor = if (playerAccent != null) {
        lerp(Color(0xFF1D221D), playerAccent, 0.62f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val highlightBgColor = if (playerAccent != null) {
        lerp(playerAccent, Color.White, 0.56f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val highlightFgColor = if (playerAccent != null) {
        lerp(playerAccent, Color.Black, 0.84f)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val primaryTextColor = if (playerAccent != null) Color.White else MaterialTheme.colorScheme.onSurface
    val titleColor = if (playerAccent != null) lerp(playerAccent, Color.White, 0.66f) else MaterialTheme.colorScheme.onSurface
    val mutedTextColor = if (playerAccent != null) Color.White.copy(alpha = 0.76f) else MaterialTheme.colorScheme.onSurfaceVariant
    val handleColor = if (playerAccent != null) titleColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
    val destructiveTextColor = if (playerAccent != null) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error
    val destructiveBadgeColor = if (playerAccent != null) Color(0xFF5C1E1B) else MaterialTheme.colorScheme.errorContainer

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(handleColor)
            )
        },
        containerColor = sheetBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            // Encabezado con carátula cuadrada redondeada, título, subtítulo y estrella ★ directa
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ArtworkBox(
                    artworkUrl = item.artworkUrl,
                    fallbackColor = cardBgColor,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (canFavorite) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isFavorite) highlightBgColor else cardBgColor,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggleFavorite(item, isFavorite) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                                tint = if (isFavorite) highlightFgColor else primaryTextColor
                            )
                        }
                    }
                }
            }

            // Fila de acciones rápidas Material 3 Expressive
            if (quickActions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickActions.forEach { action ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (action.highlighted) highlightBgColor else cardBgColor,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(onClick = action.onClick)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.label,
                                    tint = if (action.highlighted) highlightFgColor else primaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (action.highlighted) highlightFgColor else primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            val sleepActionCtrl = LocalMediaActionController.current
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Grupo 1: Cola e Infinito (no aplica a videos musicales)
                if (canQueue) item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val totalInQueueGroup = queueGroup.size + 1
                        queueGroup.forEachIndexed { idx, entry ->
                            MenuActionRow(
                                icon = entry.icon,
                                title = entry.title,
                                tint = primaryTextColor,
                                cardColor = cardBgColor,
                                badgeColor = badgeBgColor,
                                shape = sheetSegmentedShape(idx, totalInQueueGroup),
                                onClick = entry.onClick
                            )
                        }
                        Surface(
                            shape = sheetSegmentedShape(queueGroup.size, totalInQueueGroup),
                            color = cardBgColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(sheetSegmentedShape(queueGroup.size, totalInQueueGroup))
                                .clickable(onClick = onToggleAutoplay)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (autoplayEnabled) highlightBgColor else badgeBgColor,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.AllInclusive,
                                            contentDescription = null,
                                            tint = if (autoplayEnabled) highlightFgColor else mutedTextColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.autoPlay,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTextColor
                                    )
                                    Text(
                                        text = if (strings.isEnglish) {
                                            if (autoplayEnabled) "Play similar music when queue ends" else "Stop when queue ends"
                                        } else {
                                            if (autoplayEnabled) "Música similar al terminar la cola" else "Se detiene al terminar la cola"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mutedTextColor
                                    )
                                }
                                Switch(
                                    checked = autoplayEnabled,
                                    onCheckedChange = { onToggleAutoplay() },
                                    colors = if (playerAccent != null) {
                                        SwitchDefaults.colors(
                                            checkedThumbColor = highlightFgColor,
                                            checkedTrackColor = highlightBgColor,
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                                            uncheckedTrackColor = badgeBgColor,
                                            uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                                        )
                                    } else {
                                        SwitchDefaults.colors()
                                    }
                                )
                            }
                        }
                    }
                }

                // Temporizador (Sleep Timer) integrado directamente en el mismo menú (sin ventana aparte)
                if (sleepActionCtrl?.onSetSleepTimer != null) {
                    item(key = "inline_sleep_timer") {
                        val activeTimer = sleepActionCtrl.sleepTimerLabel?.invoke()
                        val strings = com.applemusic.android.ui.theme.LocalAppStrings.current
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = cardBgColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { sleepTimerExpanded = !sleepTimerExpanded }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        color = if (activeTimer != null) highlightBgColor else badgeBgColor,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bedtime,
                                                contentDescription = null,
                                                tint = if (activeTimer != null) highlightFgColor else primaryTextColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = strings.sleepTimer,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor
                                        )
                                        if (activeTimer != null) {
                                            Text(
                                                text = activeTimer,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = mutedTextColor
                                            )
                                        }
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(visible = sleepTimerExpanded) {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val presets = listOf(
                                            5 to "5 min",
                                            15 to "15 min",
                                            30 to "30 min",
                                            45 to "45 min",
                                            60 to "1 h",
                                            -1 to strings.endOfTrack,
                                            0 to strings.turnOffTimer
                                        )
                                        presets.forEach { (mins, label) ->
                                            Surface(
                                                shape = CircleShape,
                                                color = if (mins == 0 && activeTimer != null) destructiveBadgeColor else badgeBgColor,
                                                contentColor = if (mins == 0 && activeTimer != null) destructiveTextColor else primaryTextColor,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        sleepActionCtrl.onSetSleepTimer.invoke(mins)
                                                        sleepTimerExpanded = false
                                                        onDismiss()
                                                    }
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Grupo 2: Explorar y detalles
                if (detailsGroup.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            detailsGroup.forEachIndexed { idx, entry ->
                                MenuActionRow(
                                    icon = entry.icon,
                                    title = entry.title,
                                    tint = if (entry.isDestructive) destructiveTextColor else primaryTextColor,
                                    cardColor = cardBgColor,
                                    badgeColor = if (entry.isDestructive) destructiveBadgeColor else badgeBgColor,
                                    shape = sheetSegmentedShape(idx, detailsGroup.size),
                                    onClick = entry.onClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QuickSheetAction(
    val icon: ImageVector,
    val label: String,
    val highlighted: Boolean,
    val onClick: () -> Unit
)

private data class SheetMenuEntry(
    val icon: ImageVector,
    val title: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

private fun sheetSegmentedShape(index: Int, count: Int): RoundedCornerShape {
    if (count <= 1) return RoundedCornerShape(20.dp)
    return when (index) {
        0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

private fun buildAppleMusicShareUrl(item: HomeItem): String {
    val cleanId = item.catalogId?.takeIf { it.isNotBlank() } ?: item.id
    val pathKind = when {
        item.type.contains("album") -> "album"
        item.type.contains("playlist") -> "playlist"
        item.type.contains("artist") -> "artist"
        item.type.contains("music-video") -> "music-video"
        item.type.contains("station") -> "station"
        else -> "song"
    }
    return "https://music.apple.com/$pathKind/$cleanId"
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    badgeColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = shape,
        color = cardColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = badgeColor,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tint,
                modifier = Modifier.weight(1f)
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    item: HomeItem,
    loadPlaylists: suspend () -> List<HomeItem>,
    onSelectPlaylist: (playlistId: String, playlistTitle: String) -> Unit,
    onCreatePlaylist: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var playlists by remember { mutableStateOf<List<HomeItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        playlists = loadPlaylists()
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Añadir a una playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showCreateDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Nueva playlist…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            when {
                loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                playlists.isEmpty() -> Text(
                    text = "No se encontraron playlists editables en tu biblioteca.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectPlaylist(pl.id, pl.title)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ArtworkBox(
                                    artworkUrl = pl.artworkUrl,
                                    fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pl.subtitle.isNotBlank()) {
                                        Text(
                                            text = pl.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.PlaylistAddCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nueva playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre de la playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newName.trim().isNotEmpty(),
                    onClick = {
                        onCreatePlaylist(newName.trim())
                        showCreateDialog = false
                        onDismiss()
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongCreditsBottomSheet(
    seed: HomeItem,
    loadCredits: suspend (HomeItem) -> SongCreditsDetail?,
    onNavigateArtist: (artistId: String, name: String) -> Unit,
    onNavigateAlbum: (albumId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var credits by remember(seed.id) { mutableStateOf<SongCreditsDetail?>(null) }
    var loading by remember(seed.id) { mutableStateOf(true) }

    LaunchedEffect(seed.id) {
        credits = loadCredits(seed)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Créditos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
                }
            }

            when {
                loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                credits == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron créditos disponibles para esta canción.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val data = credits!!
                    val sleepActionCtrl = LocalMediaActionController.current
            LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 540.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AsyncImage(
                                    model = data.artworkUrl ?: seed.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = data.title.ifBlank { seed.title },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = data.artistName.ifBlank { seed.subtitle },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.let { base ->
                                            val aid = data.primaryArtistId
                                            if (aid != null) {
                                                base.clickable {
                                                    onDismiss()
                                                    onNavigateArtist(aid, data.artistName)
                                                }
                                            } else base
                                        }
                                    )
                                    data.albumName?.let { alb ->
                                        Text(
                                            text = alb,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.let { base ->
                                                val alId = data.albumId
                                                if (alId != null) {
                                                    base.clickable {
                                                        onDismiss()
                                                        onNavigateAlbum(alId)
                                                    }
                                                } else base
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (data.audioTraits.isNotEmpty()) {
                            item {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    data.audioTraits.forEach { trait ->
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = trait,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        data.groups.forEach { group ->
                            item(key = "cg_${group.title}") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = group.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        group.people.forEachIndexed { idx, person ->
                                            val shape = segmentedRowShape(idx == 0, idx == group.people.lastIndex)
                                            Surface(
                                                shape = shape,
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(shape)
                                                    .let { base ->
                                                        val aid = person.artistId
                                                        if (aid != null) {
                                                            base.clickable {
                                                                onDismiss()
                                                                onNavigateArtist(aid, person.name)
                                                            }
                                                        } else base
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    ArtworkBox(
                                                        artworkUrl = person.artworkUrl,
                                                        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = person.name,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        if (person.roleNames.isNotEmpty()) {
                                                            Text(
                                                                text = person.roleNames.joinToString(", "),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                        if (!data.releaseDate.isNullOrBlank()) {
                            item {
                                Text(
                                    text = "Fecha de lanzamiento: ${data.releaseDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
