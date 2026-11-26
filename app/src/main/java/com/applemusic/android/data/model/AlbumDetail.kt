package com.applemusic.android.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Detalle unificado de Álbum o Playlist (catálogo y librería),
 * para vistas de álbum y playlist.
 */
data class AlbumDetail(
    val id: String,
    val type: String,
    val title: String,
    val artistName: String,
    val genre: String?,
    val releaseDate: String?,
    val artworkUrl: String?,
    val bgColorHex: String?,
    val isExplicit: Boolean,
    val editorialNote: String?,
    val copyright: String?,
    val tracks: List<AlbumTrack>,
    val nextTracksUrl: String? = null,
    val catalogId: String? = null,
    val primaryArtistId: String? = null,
    val curatorItem: HomeItem? = null,
    val relatedSections: List<HomeSection> = emptyList(),
    val motionSquareUrl: String? = null,
    val motionTallUrl: String? = null,
    /** El álbum está disponible en Dolby Atmos (`audioTraits` contiene "atmos" o `audioVariants` "dolby-atmos"). */
    val hasAtmos: Boolean = false,
    /** Próximo estreno: el álbum aún no se ha lanzado completo (tiene pistas bloqueadas y fecha futura). */
    val isPreRelease: Boolean = false,
    /** Marca de tiempo (epoch ms) de lanzamiento para mostrar el contador regresivo. */
    val releaseEpochMillis: Long? = null
) {
    val isPlaylist: Boolean get() = type.contains("playlist")
    val releaseYear: String? get() = releaseDate?.take(4)
    val totalMillis: Long get() = tracks.sumOf { it.durationMillis }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val RELATED_VIEW_ORDER = listOf(
            "featured-artists",
            "other-versions",
            "more-by-artist",
            "more-by-curator",
            "related-videos",
            "appears-on",
            "you-might-also-like"
        )

        /** Parsea `/albums/{id}`, `/library/albums/{id}`, `/playlists/{id}` o `/library/playlists/{id}`. */
        fun parse(body: String): AlbumDetail? {
            val root = json.parseToJsonElement(body).obj ?: return null
            val media = (root["data"] as? JsonArray)?.firstOrNull().obj ?: return null
            val type = media.str("type").orEmpty()
            var attrs = media["attributes"].obj ?: return null

            // Para library-albums / library-playlists, fusionar metadata del catálogo si existe
            val catalogObj = media["relationships"].obj?.get("catalog").obj
                ?.get("data").arr?.firstOrNull().obj
            val catalogAttrs = catalogObj?.get("attributes").obj
            if (catalogAttrs != null) {
                attrs = JsonObject(catalogAttrs + attrs)
            }

            val artwork = attrs["artwork"].obj
            val rels = media["relationships"].obj
            val tracksRel = rels?.get("tracks").obj
            val nextTracksUrl = tracksRel?.str("next")
            val isPlaylist = type.contains("playlist")
            val rawReleaseDate = attrs.str("releaseDate") ?: attrs.str("lastModifiedDate")
            val releaseEpoch = if (!isPlaylist) parseReleaseEpochMillis(rawReleaseDate) else null
            val hasFutureRelease = releaseEpoch != null && releaseEpoch > System.currentTimeMillis()
            val explicitPreRelease = !isPlaylist && (attrs["isPrerelease"]?.prim?.booleanOrNull == true || hasFutureRelease)

            val tracks = tracksRel?.get("data").arr.orEmpty()
                .mapIndexedNotNull { index, t -> parseTrack(t, index, isPlaylist, explicitPreRelease) }
            val hasUnavailableTracks = !isPlaylist && tracks.any { !it.isAvailable }
            val isPreReleaseAlbum = explicitPreRelease || hasUnavailableTracks

            fun JsonElement?.strings(): List<String> =
                (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }.orEmpty()
            val hasAtmos = !isPlaylist && (
                attrs["audioTraits"].strings().any { it == "atmos" } ||
                    attrs["audioVariants"].strings().any { it == "dolby-atmos" || it == "atmos" }
                )

            val creator = attrs.str("artistName")
                ?: attrs.str("curatorName")
                ?: attrs.str("artistNames")
                ?: ""

            val artistRef = rels?.get("artists").obj?.get("data").arr?.firstOrNull().obj
            val catArtistRef = catalogObj?.get("relationships")?.obj?.get("artists")?.obj?.get("data").arr?.firstOrNull().obj
            val primaryArtistId = (catArtistRef ?: artistRef)?.str("id")?.takeIf { !it.startsWith("r.") }

            val curatorObj = (rels?.get("curator") ?: catalogObj?.get("relationships")?.obj?.get("curator"))
                ?.obj?.get("data").arr?.firstOrNull().obj
            val curatorItem = curatorObj?.let { parseViewItem(it) }

            val related = parseViewsObject(media["views"].obj ?: catalogObj?.get("views")?.obj, creator)
            val editorialVideo = attrs["editorialVideo"].obj ?: catalogAttrs?.get("editorialVideo").obj
            val motionSquareUrl = editorialVideo?.get("motionDetailSquare").obj?.str("video")
                ?: editorialVideo?.get("motionSquareVideo1x1").obj?.str("video")
                ?: editorialVideo?.get("motionArtistSquare1x1").obj?.str("video")
            val motionTallUrl = editorialVideo?.get("motionDetailTall").obj?.str("video")
                ?: editorialVideo?.get("motionTallVideo3x4").obj?.str("video")

            return AlbumDetail(
                id = media.str("id").orEmpty(),
                type = type,
                title = attrs.str("name").orEmpty(),
                artistName = creator,
                genre = (attrs["genreNames"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull },
                releaseDate = rawReleaseDate,
                artworkUrl = artwork?.str("url")?.replace("{w}", "1000")?.replace("{h}", "1000")
                    ?.replace("{c}", "bb")?.replace("{f}", "jpg"),
                bgColorHex = artwork?.str("bgColor"),
                isExplicit = attrs.str("contentRating") == "explicit",
                editorialNote = attrs["editorialNotes"].obj?.let { it.str("short") ?: it.str("standard") }
                    ?: attrs["description"].obj?.let { it.str("short") ?: it.str("standard") },
                copyright = attrs.str("copyright"),
                tracks = tracks,
                nextTracksUrl = nextTracksUrl,
                catalogId = catalogObj?.str("id") ?: attrs["playParams"].obj?.str("catalogId"),
                primaryArtistId = primaryArtistId,
                curatorItem = curatorItem,
                relatedSections = related,
                motionSquareUrl = motionSquareUrl,
                motionTallUrl = motionTallUrl,
                hasAtmos = hasAtmos,
                isPreRelease = isPreReleaseAlbum,
                releaseEpochMillis = releaseEpoch
            )
        }

        /** Extrae únicamente las secciones relacionadas (`views`) desde un álbum/playlist de catálogo. */
        fun parseRelatedViews(body: String, fallbackCreator: String): List<HomeSection> {
            val root = json.parseToJsonElement(body).obj ?: return emptyList()
            val media = root["data"].arr?.firstOrNull().obj ?: return emptyList()
            val attrs = media["attributes"].obj
            val creator = attrs?.str("artistName") ?: attrs?.str("curatorName") ?: fallbackCreator
            return parseViewsObject(media["views"].obj, creator)
        }

        private fun parseViewsObject(views: JsonObject?, creator: String): List<HomeSection> {
            if (views == null) return emptyList()
            val keys = RELATED_VIEW_ORDER.filter { views.containsKey(it) } +
                views.keys.filterNot { it in RELATED_VIEW_ORDER }
            return keys.mapNotNull { key ->
                val viewObj = views[key].obj ?: return@mapNotNull null
                val items = viewObj["data"].arr.orEmpty()
                    .mapNotNull { el -> el.obj?.let { parseViewItem(it) } }
                    .distinctBy { "${it.type}_${it.id}" }
                if (items.isEmpty()) return@mapNotNull null
                val defaultTitle = when (key) {
                    "featured-artists" -> "Artistas destacados"
                    "other-versions" -> "Otras versiones"
                    "more-by-artist", "more-by-curator" -> if (creator.isNotBlank()) "Más de $creator" else "Más"
                    "related-videos" -> "Videos musicales"
                    "appears-on" -> "Aparece en"
                    "you-might-also-like" -> "Quizás también te interese"
                    else -> key.replace('-', ' ').replaceFirstChar { it.uppercase() }
                }
                val title = viewObj["attributes"].obj?.str("title")?.takeIf { it.isNotBlank() } ?: defaultTitle
                val style = if (key == "featured-artists") HomeSectionStyle.ARTIST_CIRCLES else HomeSectionStyle.SQUARE_CAROUSEL
                HomeSection(
                    id = "col_view_$key",
                    title = title,
                    hasChevron = items.size > 3,
                    style = style,
                    items = items,
                    canLoadMore = items.size > 3
                )
            }
        }

        private fun parseViewItem(obj: JsonObject): HomeItem? {
            val id = obj.str("id") ?: return null
            val type = obj.str("type").orEmpty()
            val attrs = obj["attributes"].obj ?: return null
            val title = attrs.str("name") ?: return null
            val ea = attrs["editorialArtwork"].obj
            val artObj = attrs["artwork"].obj
                ?: ea?.get("backgroundStatic1x1").obj
                ?: ea?.get("staticDetailSquare").obj
                ?: ea?.get("brandLogo").obj
                ?: ea?.values?.firstOrNull()?.obj
            val isVideo = type == "music-videos" || type == "uploaded-videos"
            val subtitle = when {
                type.contains("artist") -> ""
                else -> attrs.str("artistName") ?: attrs.str("curatorName") ?: ""
            }
            val artworkUrl = artObj?.str("url")?.let { tpl ->
                if (isVideo) tpl.replace("{w}", "800").replace("{h}", "450").replace("{c}", "bb").replace("{f}", "jpg")
                else tpl.replace("{w}", "600").replace("{h}", "600").replace("{c}", "cc").replace("{f}", "jpg")
            }
            return HomeItem(
                id = id,
                type = type,
                title = title,
                subtitle = subtitle,
                artworkUrl = artworkUrl,
                bgColorHex = artObj?.str("bgColor"),
                isExplicit = attrs.str("contentRating") == "explicit",
                textColorHex = artObj?.str("textColor1"),
                url = attrs.str("url")
            )
        }

        fun parseTrackPage(body: String, startIndex: Int, isPlaylist: Boolean): Pair<List<AlbumTrack>, String?> {
            val root = json.parseToJsonElement(body).obj ?: return emptyList<AlbumTrack>() to null
            val next = root.str("next")
            val tracks = root["data"].arr.orEmpty().mapIndexedNotNull { i, t ->
                parseTrack(t, startIndex + i, isPlaylist)
            }
            return tracks to next
        }

        private fun parseTrack(
            t: JsonElement,
            index: Int,
            isPlaylist: Boolean,
            albumIsPreRelease: Boolean = false
        ): AlbumTrack? {
            val obj = t.obj ?: return null
            var ta = obj["attributes"].obj ?: return null
            val catTrackObj = obj["relationships"].obj?.get("catalog").obj
                ?.get("data").arr?.firstOrNull().obj
            val catTrackAttrs = catTrackObj?.get("attributes").obj
            if (catTrackAttrs != null) {
                ta = JsonObject(catTrackAttrs + ta)
            }
            val rawId = obj.str("id").orEmpty()
            val playParams = ta["playParams"].obj
            val catalogId = catTrackObj?.str("id")?.takeIf { it.isNotBlank() && !it.startsWith("i.") }
                ?: playParams?.str("catalogId")?.takeIf { it.isNotBlank() && !it.startsWith("i.") }
                ?: rawId.takeIf { it.all(Char::isDigit) && it.isNotBlank() }
            val explicitAvailable = ta["isAvailable"]?.prim?.booleanOrNull
            val hasPlayableDuration = ((ta["durationInMillis"] as? JsonPrimitive)?.longOrNull ?: 0L) > 0L
            val isAvailable = explicitAvailable
                ?: if (albumIsPreRelease) (playParams != null && hasPlayableDuration) else true
            return AlbumTrack(
                id = rawId,
                type = obj.str("type").orEmpty().ifBlank { "songs" },
                number = if (isPlaylist) index + 1 else (ta["trackNumber"] as? JsonPrimitive)?.intOrNull ?: (index + 1),
                disc = if (isPlaylist) 1 else (ta["discNumber"] as? JsonPrimitive)?.intOrNull ?: 1,
                title = ta.str("name").orEmpty(),
                artistName = ta.str("artistName").orEmpty(),
                albumName = ta.str("albumName"),
                artworkUrl = ta["artwork"].obj?.str("url")
                    ?.replace("{w}", "160")?.replace("{h}", "160")
                    ?.replace("{c}", "cc")?.replace("{f}", "jpg"),
                durationMillis = (ta["durationInMillis"] as? JsonPrimitive)?.longOrNull ?: 0L,
                isExplicit = ta.str("contentRating") == "explicit",
                catalogId = catalogId,
                isAvailable = isAvailable
            )
        }

        fun parseReleaseEpochMillis(rawDate: String?): Long? {
            val clean = rawDate?.trim()?.takeIf { it.isNotBlank() } ?: return null
            runCatching { return Instant.parse(clean).toEpochMilli() }
            runCatching {
                return LocalDate.parse(clean.take(10))
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            return null
        }

        private val JsonElement?.obj get() = this as? JsonObject
        private val JsonElement?.arr get() = this as? JsonArray
        private val JsonElement?.prim get() = this as? JsonPrimitive
        private fun JsonElement?.str(key: String): String? =
            ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}

data class AlbumTrack(
    val id: String,
    val type: String = "songs",
    val number: Int,
    val disc: Int,
    val title: String,
    val artistName: String,
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMillis: Long,
    val isExplicit: Boolean,
    val catalogId: String? = null,
    val isAvailable: Boolean = true
)
