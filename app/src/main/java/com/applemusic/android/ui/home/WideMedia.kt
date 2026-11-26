package com.applemusic.android.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection

private val WIDE_ARTWORK_REGEX = Regex("""/(\d+)x(\d+)(?:cc|bb|sr|sc|ac)?\.(jpg|png|webp)""")
private val WIDE_TITLE_REGEX = Regex("entrevista|interview|video", RegexOption.IGNORE_CASE)

/** Convierte una URL de artwork cuadrada (`600x600cc`) a formato horizontal 16:9 (`800x450bb`) sin recortar. */
internal fun String.toWideArtworkUrl(): String =
    replace(WIDE_ARTWORK_REGEX) { "/800x450bb.${it.groupValues[3]}" }

internal fun HomeItem.isVideoItem(): Boolean = type.contains("video", ignoreCase = true)

/**
 * Una sección se muestra horizontal (16:9) si todos sus elementos son videos
 * o si es una sección de entrevistas / videos (por título).
 */
internal fun isWideSection(section: HomeSection): Boolean =
    section.items.isNotEmpty() && (
        section.items.all { it.isVideoItem() } ||
            WIDE_TITLE_REGEX.containsMatchIn(section.title)
        )

/** Tarjeta horizontal 16:9 para videos musicales y entrevistas. */
@Composable
internal fun WideMediaCard(
    item: HomeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 244.dp
) {
    val accent = parseHexColor(item.bgColorHex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val showPlay = item.isVideoItem() || item.type.contains("station", ignoreCase = true)
    Column(
        modifier = modifier
            .width(width)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CoverShape)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), CoverShape)
        ) {
            ArtworkBox(
                artworkUrl = item.artworkUrl?.toWideArtworkUrl(),
                fallbackColor = accent,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (showPlay) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
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
