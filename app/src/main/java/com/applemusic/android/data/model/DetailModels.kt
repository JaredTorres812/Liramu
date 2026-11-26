package com.applemusic.android.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.roundToInt

/** Detalle completo de Artista. */
data class ArtistDetail(
    val id: String,
    val name: String,
    val genre: String?,
    val origin: String? = null,
    val bornOrFormed: String? = null,
    val artworkUrl: String?,
    val heroUrl: String?,
    val nameLogoUrl: String? = null,
    val nameLogoAspectRatio: Float? = null,
    val motionHeroUrl: String? = null,
    val bgColorHex: String?,
    val bio: String?,
    val latestRelease: HomeItem?,
    val topSongs: List<HomeItem>,
    val featuredAlbums: List<HomeItem> = emptyList(),
    val albums: List<HomeItem>,
    val singles: List<HomeItem>,
    val liveAlbums: List<HomeItem> = emptyList(),
    val compilationAlbums: List<HomeItem> = emptyList(),
    val appearsOnAlbums: List<HomeItem> = emptyList(),
    val playlists: List<HomeItem>,
    val topMusicVideos: List<HomeItem> = emptyList(),
    val musicVideos: List<HomeItem> = emptyList(),
    val similarArtists: List<HomeItem>
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val HTML_TAG_REGEX = Regex("<[^>]+>")

        fun parse(body: String): ArtistDetail? {
            val root = json.parseToJsonElement(body).obj ?: return null
            val artist = root["data"].arr?.firstOrNull().obj ?: return null
            val attrs = artist["attributes"].obj ?: return null
            val views = artist["views"].obj
            val editorialArt = attrs["editorialArtwork"].obj
            val artwork = attrs["artwork"].obj
            val heroFallback = attrs["hero"].arr?.firstOrNull().obj
                ?.get("content").arr?.firstOrNull().obj
                ?.get("artwork").obj
            val heroArt = editorialArt?.get("centeredFullscreenBackground").obj
                ?: editorialArt?.get("bannerUber").obj
                ?: editorialArt?.get("subscriptionHero").obj
                ?: editorialArt?.get("storeFlowcase").obj
                ?: heroFallback
                ?: editorialArt?.values?.firstOrNull()?.obj

            val logoArt = editorialArt?.get("musicContentColorLogoTrimmed").obj
            val nameLogoUrl = logoArt?.str("url")?.let { tpl ->
                val lw = logoArt["width"]?.prim?.intOrNull?.takeIf { it > 0 } ?: 1200
                val lh = logoArt["height"]?.prim?.intOrNull?.takeIf { it > 0 } ?: 320
                val targetW = lw.coerceAtMost(1200)
                val targetH = ((targetW.toDouble() * lh) / lw).toInt().coerceIn(80, 900)
                tpl.replace("{w}", targetW.toString())
                    .replace("{h}", targetH.toString())
                    .replace("{c}", "bb")
                    .replace("{f}", "png")
            }
            val nameLogoAspectRatio = logoArt?.let {
                val lw = it["width"]?.prim?.intOrNull?.takeIf { w -> w > 0 } ?: return@let null
                val lh = it["height"]?.prim?.intOrNull?.takeIf { h -> h > 0 } ?: return@let null
                (lw.toFloat() / lh.toFloat()).coerceIn(1f, 10f)
            }
            val editorialVideo = attrs["editorialVideo"].obj
            val motionHeroUrl = editorialVideo?.get("motionArtistSquare1x1").obj?.str("video")
                ?: editorialVideo?.get("motionDetailTall").obj?.str("video")
                ?: editorialVideo?.get("motionTallVideo3x4").obj?.str("video")
                ?: editorialVideo?.get("motionArtistSingular16x9").obj?.str("video")
                ?: editorialVideo?.get("motionArtistFullscreen16x9").obj?.str("video")
                ?: editorialVideo?.get("motionArtistWide16x9").obj?.str("video")

            fun parseView(key: String): List<HomeItem> =
                views?.get(key).obj?.get("data").arr.orEmpty().mapNotNull { it.obj?.toHomeItem() }

            val mergedPlaylists = (parseView("featured-playlists") + parseView("playlists"))
                .distinctBy { "${it.type}_${it.id}" }
            val topVideos = parseView("top-music-videos")
            val allVideos = (topVideos + parseView("music-videos"))
                .distinctBy { "${it.type}_${it.id}" }

            val squareArt = artwork
                ?: editorialArt?.get("backgroundStatic1x1").obj
                ?: editorialArt?.get("brandLogo").obj
                ?: editorialArt?.values?.firstOrNull()?.obj

            val rawBio = attrs.str("artistBio")
                ?: attrs["editorialNotes"].obj?.let { it.str("standard") ?: it.str("short") }
            val cleanBio = rawBio
                ?.replace("<br>", "\n", ignoreCase = true)
                ?.replace("<br/>", "\n", ignoreCase = true)
                ?.replace("<br />", "\n", ignoreCase = true)
                ?.replace(HTML_TAG_REGEX, "")
                ?.replace("&amp;", "&")
                ?.replace("&quot;", "\"")
                ?.replace("&#39;", "'")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val heroUrl = heroArt?.str("url")?.let { tpl ->
                val w = heroArt["width"]?.prim?.intOrNull?.takeIf { it > 0 } ?: 1600
                val h = heroArt["height"]?.prim?.intOrNull?.takeIf { it > 0 } ?: 900
                val targetW = 1400
                val targetH = ((targetW.toDouble() * h) / w).toInt().coerceIn(320, 1400)
                tpl.art(targetW, targetH, crop = "bb")
            }

            return ArtistDetail(
                id = artist.str("id").orEmpty(),
                name = attrs.str("name").orEmpty(),
                genre = attrs["genreNames"].arr?.firstOrNull()?.prim?.contentOrNull,
                origin = attrs.str("origin")?.takeIf { it.isNotBlank() },
                bornOrFormed = attrs.str("bornOrFormed")?.takeIf { it.isNotBlank() },
                artworkUrl = squareArt?.str("url")?.art(600),
                heroUrl = heroUrl,
                nameLogoUrl = nameLogoUrl,
                nameLogoAspectRatio = nameLogoAspectRatio,
                motionHeroUrl = motionHeroUrl,
                bgColorHex = (heroArt ?: squareArt)?.str("bgColor"),
                bio = cleanBio,
                latestRelease = parseView("latest-release").firstOrNull(),
                topSongs = parseView("top-songs"),
                featuredAlbums = parseView("featured-albums"),
                albums = parseView("full-albums"),
                singles = parseView("singles"),
                liveAlbums = parseView("live-albums"),
                compilationAlbums = parseView("compilation-albums"),
                appearsOnAlbums = parseView("appears-on-albums"),
                playlists = mergedPlaylists,
                topMusicVideos = topVideos,
                musicVideos = allVideos,
                similarArtists = parseView("similar-artists")
            )
        }
    }
}

data class ReplayPeriodOption(
    val id: String,
    val year: Int,
    val month: Int? = null,
    val label: String
)

data class ReplayRankedItem(
    val item: HomeItem,
    val playCount: Int,
    val listenTimeMinutes: Int
)

/** Estadísticas completas de Apple Music Replay (anual y por meses, como `replay.music.apple.com`). */
data class ReplaySummary(
    val summaryId: String,
    val availableYears: List<Int>,
    val availableMonths: List<ReplayPeriodOption>,
    val selectedYear: Int,
    val selectedMonth: Int? = null,
    val periodLabel: String,
    val listenTimeMinutes: Int,
    val uniqueAlbumCount: Int,
    val uniqueArtistCount: Int,
    val uniqueSongCount: Int,
    val playlist: HomeItem?,
    val topArtists: List<ReplayRankedItem>,
    val topAlbums: List<ReplayRankedItem>,
    val topSongs: List<ReplayRankedItem>,
    val topGenres: List<Pair<String, Int>>
) {
    val listenHours: Int get() = listenTimeMinutes / 60
    val remainingMinutes: Int get() = listenTimeMinutes % 60

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val MONTH_NAMES_ES = listOf(
            "", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
        )

        fun parseAvailableYears(body: String): List<ReplayPeriodOption> {
            val root = json.parseToJsonElement(body).obj ?: return emptyList()
            return root["data"].arr.orEmpty().mapNotNull { entry ->
                val obj = entry.obj ?: return@mapNotNull null
                val id = obj.str("id") ?: return@mapNotNull null
                val attrs = obj["attributes"].obj
                val yr = attrs?.get("year")?.prim?.intOrNull
                    ?: id.removePrefix("year-").toIntOrNull()
                    ?: return@mapNotNull null
                val label = attrs?.str("name")?.takeIf { it.isNotBlank() } ?: yr.toString()
                ReplayPeriodOption(id = id, year = yr, month = null, label = label)
            }.distinctBy { it.year }.sortedByDescending { it.year }
        }

        fun parseAvailableMonths(body: String, fallbackYear: Int): List<ReplayPeriodOption> {
            val root = json.parseToJsonElement(body).obj ?: return emptyList()
            return root["data"].arr.orEmpty().mapNotNull { entry ->
                val obj = entry.obj ?: return@mapNotNull null
                val id = obj.str("id") ?: return@mapNotNull null
                val attrs = obj["attributes"].obj ?: return@mapNotNull null
                val month = attrs["month"]?.prim?.intOrNull
                    ?: id.substringAfterLast('-').toIntOrNull()
                    ?: return@mapNotNull null
                val year = attrs["year"]?.prim?.intOrNull ?: fallbackYear
                val label = attrs.str("name")?.takeIf { it.isNotBlank() }
                    ?: MONTH_NAMES_ES.getOrElse(month) { "M$month" }
                ReplayPeriodOption(id = id, year = year, month = month, label = label)
            }.sortedByDescending { it.month ?: 0 }
        }

        fun parseSummaryDetail(
            body: String,
            availableYears: List<Int>,
            availableMonths: List<ReplayPeriodOption>,
            requestedYear: Int,
            requestedMonth: Int?
        ): ReplaySummary? {
            val root = json.parseToJsonElement(body).obj ?: return null
            val summary = root["data"].arr?.firstOrNull().obj ?: return null
            val summaryId = summary.str("id").orEmpty()
            val attrs = summary["attributes"].obj ?: return null
            val views = summary["views"].obj
            val rels = summary["relationships"].obj

            val playlistObj = rels?.get("playlist").obj?.get("data").arr?.firstOrNull().obj
            val playlistItem = playlistObj?.toHomeItem()

            val topArtists = views?.get("top-artists").obj?.get("data").arr.orEmpty().mapNotNull { raw ->
                val o = raw.obj ?: return@mapNotNull null
                val a = o["attributes"].obj
                val artistObj = o["relationships"].obj?.get("artist").obj?.get("data").arr?.firstOrNull().obj
                    ?: return@mapNotNull null
                val item = artistObj.toHomeItem() ?: return@mapNotNull null
                ReplayRankedItem(
                    item = item,
                    playCount = a?.get("playCount")?.prim?.intOrNull ?: 0,
                    listenTimeMinutes = a?.get("listenTimeInMinutes")?.prim?.intOrNull ?: 0
                )
            }

            val topAlbums = views?.get("top-albums").obj?.get("data").arr.orEmpty().mapNotNull { raw ->
                val o = raw.obj ?: return@mapNotNull null
                val a = o["attributes"].obj
                val albumObj = o["relationships"].obj?.get("album").obj?.get("data").arr?.firstOrNull().obj
                    ?: return@mapNotNull null
                val item = albumObj.toHomeItem() ?: return@mapNotNull null
                ReplayRankedItem(
                    item = item,
                    playCount = a?.get("playCount")?.prim?.intOrNull ?: 0,
                    listenTimeMinutes = a?.get("listenTimeInMinutes")?.prim?.intOrNull ?: 0
                )
            }

            val songEntries = (views?.get("top-songs").obj?.get("data").arr
                ?: views?.get("top-songs-for-months").obj?.get("data").arr).orEmpty()

            val genreCounts = LinkedHashMap<String, Int>()
            var totalGenrePlays = 0
            val topSongs = songEntries.mapNotNull { raw ->
                val o = raw.obj ?: return@mapNotNull null
                val a = o["attributes"].obj
                val songObj = o["relationships"].obj?.get("song").obj?.get("data").arr?.firstOrNull().obj
                    ?: return@mapNotNull null
                val item = songObj.toHomeItem() ?: return@mapNotNull null
                val plays = a?.get("playCount")?.prim?.intOrNull ?: 0
                val genres = songObj["attributes"].obj?.get("genreNames").arr.orEmpty()
                    .mapNotNull { it.prim?.contentOrNull }
                    .filterNot { it.equals("Music", ignoreCase = true) || it.equals("Música", ignoreCase = true) }
                if (plays > 0) {
                    totalGenrePlays += plays
                    genres.forEach { g -> genreCounts[g] = (genreCounts[g] ?: 0) + plays }
                }
                ReplayRankedItem(
                    item = item,
                    playCount = plays,
                    listenTimeMinutes = a?.get("listenTimeInMinutes")?.prim?.intOrNull ?: 0
                )
            }

            val topGenres = if (totalGenrePlays > 0) {
                genreCounts.entries
                    .sortedByDescending { it.value }
                    .take(6)
                    .map { (genre, count) -> genre to ((count * 100f) / totalGenrePlays).roundToInt() }
            } else {
                emptyList()
            }

            val year = attrs["year"]?.prim?.intOrNull ?: requestedYear
            val month = attrs["month"]?.prim?.intOrNull ?: requestedMonth
            val years = (availableYears + year).distinct().sortedDescending()
            val label = if (month != null) {
                val monthName = MONTH_NAMES_ES.getOrElse(month) { "Mes $month" }
                "$monthName $year"
            } else {
                "Todo $year"
            }

            return ReplaySummary(
                summaryId = summaryId,
                availableYears = years,
                availableMonths = availableMonths,
                selectedYear = year,
                selectedMonth = month,
                periodLabel = label,
                listenTimeMinutes = attrs["listenTimeInMinutes"]?.prim?.intOrNull ?: 0,
                uniqueAlbumCount = attrs["uniqueAlbumCount"]?.prim?.intOrNull ?: 0,
                uniqueArtistCount = attrs["uniqueArtistCount"]?.prim?.intOrNull ?: 0,
                uniqueSongCount = attrs["uniqueSongCount"]?.prim?.intOrNull ?: 0,
                playlist = playlistItem,
                topArtists = topArtists,
                topAlbums = topAlbums,
                topSongs = topSongs,
                topGenres = topGenres
            )
        }
    }
}

/** Perfil social de un amigo (foto, @handle, playlists compartidas). */
data class SocialProfileDetail(
    val id: String,
    val name: String,
    val handle: String?,
    val avatarUrl: String?,
    val topGenres: List<String>,
    val sharedPlaylists: List<HomeItem>
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(body: String): SocialProfileDetail? {
            val root = json.parseToJsonElement(body).obj ?: return null
            val profile = root["data"].arr?.firstOrNull().obj ?: return null
            val attrs = profile["attributes"].obj ?: return null
            val playlists = profile["relationships"].obj?.get("shared-playlists").obj?.get("data").arr.orEmpty()
                .mapNotNull { it.obj?.toHomeItem() }
            return SocialProfileDetail(
                id = profile.str("id").orEmpty(),
                name = attrs.str("name") ?: attrs.str("handle").orEmpty(),
                handle = attrs.str("handle"),
                avatarUrl = attrs["artwork"].obj?.str("url")?.art(400),
                topGenres = attrs["topGenreNames"].arr.orEmpty().mapNotNull { it.prim?.contentOrNull },
                sharedPlaylists = playlists
            )
        }

        fun parseMyAccountProfile(body: String): com.applemusic.android.data.settings.UserAccountProfile? {
            val root = json.parseToJsonElement(body).obj ?: return null
            val resources = root["resources"].obj
            val personalMap = resources?.get("personal-social-profiles").obj
            val socialMap = resources?.get("social-profiles").obj
            val dataFirst = root["data"].arr?.firstOrNull().obj
            val meObj = personalMap?.get("me").obj
                ?: personalMap?.values?.firstOrNull()?.obj
                ?: dataFirst?.let { d ->
                    val type = d.str("type")
                    val id = d.str("id")
                    if (type != null && id != null) resources?.get(type).obj?.get(id).obj else null
                }
                ?: socialMap?.values?.firstOrNull()?.obj
                ?: dataFirst
                ?: return null
            val attrs = meObj["attributes"].obj ?: return null
            val name = attrs.str("name")?.takeIf { it.isNotBlank() }
                ?: attrs.str("handle")?.takeIf { it.isNotBlank() }
                ?: ""
            val handle = attrs.str("handle")?.takeIf { it.isNotBlank() }
            val avatarUrl = attrs["artwork"].obj?.str("url")?.art(320)
            if (name.isBlank() && handle.isNullOrBlank() && avatarUrl.isNullOrBlank()) return null
            return com.applemusic.android.data.settings.UserAccountProfile(
                id = meObj.str("id") ?: dataFirst?.str("id").orEmpty(),
                name = name.ifBlank { handle.orEmpty() },
                handle = handle,
                avatarUrl = avatarUrl
            )
        }
    }
}

private fun JsonObject.toHomeItem(): HomeItem? {
    val id = str("id") ?: return null
    val type = str("type").orEmpty()
    val attrs = this["attributes"].obj ?: return null
    val title = attrs.str("name") ?: return null
    val ea = attrs["editorialArtwork"].obj
    val artwork = attrs["artwork"].obj
        ?: ea?.get("backgroundStatic1x1").obj
        ?: ea?.get("staticDetailSquare").obj
        ?: ea?.get("brandLogo").obj
        ?: ea?.get("subscriptionCover").obj
        ?: ea?.values?.firstOrNull()?.obj
    val isVideo = type == "music-videos" || type == "uploaded-videos"
    val subtitle = when (type) {
        "artists", "library-artists" -> ""
        else -> attrs.str("artistName") ?: attrs.str("curatorName") ?: ""
    }
    val ev = attrs["editorialVideo"].obj
    val motionUrl = ev?.get("motionDetailSquare").obj?.str("video")
        ?: ev?.get("motionSquareVideo1x1").obj?.str("video")
        ?: ev?.get("motionDetailTall").obj?.str("video")
        ?: ev?.get("motionArtistSquare1x1").obj?.str("video")
    return HomeItem(
        id = id,
        type = type,
        title = title,
        subtitle = subtitle,
        artworkUrl = artwork?.str("url")?.let { tpl ->
            if (isVideo) tpl.art(800, 450, crop = "bb") else tpl.art(600)
        },
        bgColorHex = artwork?.str("bgColor"),
        isExplicit = attrs.str("contentRating") == "explicit",
        textColorHex = artwork?.str("textColor1"),
        url = attrs.str("url"),
        motionArtworkUrl = motionUrl
    )
}

private fun String.art(w: Int, h: Int = w, crop: String = "cc"): String =
    replace("{w}", w.toString())
        .replace("{h}", h.toString())
        .replace("{c}", crop)
        .replace("{f}", "jpg")

private val JsonElement?.obj get() = this as? JsonObject
private val JsonElement?.arr get() = this as? JsonArray
private val JsonElement?.prim get() = this as? JsonPrimitive
private fun JsonElement?.str(key: String): String? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
