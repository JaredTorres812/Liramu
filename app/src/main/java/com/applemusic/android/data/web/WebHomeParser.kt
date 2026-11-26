package com.applemusic.android.data.web

import com.applemusic.android.data.model.ArtistDetail
import com.applemusic.android.data.model.FriendBadge
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection
import com.applemusic.android.data.model.HomeSectionStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WebHomePage(
    val sections: List<HomeSection>,
    val nextUrl: String? = null,
    val debugSummary: String = ""
)

data class SearchSuggestionsResult(
    val terms: List<String> = emptyList(),
    val topResults: List<HomeItem> = emptyList()
)

/**
 * Convierte las respuestas de `amp-api.music.apple.com` (`format[resources]=map` y JSON:API normal)
 * en secciones e ítems nativos, resolviendo:
 *  - Estantes de Home (`listen-now`) con paginación `next`
 *  - `see-all` hasta 100 elementos
 *  - `v1/me/recent/played` y `v1/me/recent/played/tracks`
 *  - `v1/social/badging-map` y `v1/social/{sf}/social-profiles` para fotos de perfil de amigos
 *  - `v1/editorial/{sf}/groupings` como respaldo editorial sin login
 */
object WebHomeParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String, moreLikeLabel: String = "Más como"): List<HomeSection> =
        parsePage(body, moreLikeLabel).sections

    fun parsePage(body: String, moreLikeLabel: String = "Más como"): WebHomePage {
        val root = json.parseToJsonElement(body).obj ?: return WebHomePage(emptyList())
        val resources = root["resources"].obj ?: return WebHomePage(emptyList())
        val refs = root["data"].arr ?: return WebHomePage(emptyList())
        val nextUrl = root.str("next")

        fun resolve(type: String?, id: String?): JsonObject? =
            if (type == null || id == null) null else resources[type].obj?.get(id).obj

        val rawSummary = refs.joinToString(" | ") { ref ->
            val rec = resolve(ref.str("type"), ref.str("id"))
            val attrs = rec?.get("attributes").obj
            val kind = attrs?.get("display").obj?.str("kind")
            val t = attrs?.get("title").obj?.str("stringForDisplay").orEmpty()
            val contents = rec?.get("relationships").obj?.get("contents").obj?.get("data").arr.orEmpty()
            val types = contents.mapNotNull { it.str("type") }.distinct().joinToString(",")
            "${ref.str("id")}:$kind:'$t'(${contents.size}:$types)"
        }

        val sections = refs.mapNotNull { ref ->
            val rec = resolve(ref.str("type"), ref.str("id")) ?: return@mapNotNull null
            val recId = rec.str("id").orEmpty()
            val attrs = rec["attributes"].obj
            val kind = attrs?.get("display").obj?.str("kind")
            val rawTitle = attrs?.get("title").obj?.str("stringForDisplay").orEmpty()
            val contents = rec["relationships"].obj?.get("contents").obj?.get("data").arr.orEmpty()

            // Primero determinamos el estilo provisional por display.kind/id para saber si pide tall/wide artwork
            val preliminaryStyle = when {
                kind == "MusicNotesHeroShelf" -> HomeSectionStyle.HERO_PICKS
                kind == "MusicSuperHeroShelf" -> HomeSectionStyle.SUPER_HERO
                kind == "MusicCircleCoverShelf" || recId.startsWith("18-") -> HomeSectionStyle.ARTIST_CIRCLES
                kind == "MusicCoverGrid" || recId.startsWith("24-") -> HomeSectionStyle.COVER_GRID
                else -> HomeSectionStyle.SQUARE_CAROUSEL
            }

            val items = contents.mapNotNull { c ->
                val type = c.str("type")
                val res = resolve(type, c.str("id")) ?: return@mapNotNull null
                toItem(res, c.obj?.get("meta").obj, preliminaryStyle, resources)
            }
            if (items.isEmpty()) return@mapNotNull null

            // Si Apple devolvió MusicCoverShelf pero todos los ítems son artistas, mostrar en círculos
            val style = if (preliminaryStyle == HomeSectionStyle.SQUARE_CAROUSEL &&
                items.all { it.type == "artists" || it.type == "library-artists" }
            ) {
                HomeSectionStyle.ARTIST_CIRCLES
            } else {
                preliminaryStyle
            }

            val primary = rec["relationships"].obj?.get("primary-content").obj?.get("data").arr?.firstOrNull()
                ?.let { p -> resolve(p.str("type"), p.str("id")) }
                ?.let { toItem(it, null, HomeSectionStyle.SQUARE_CAROUSEL, resources) }
            val seeAll = attrs?.get("hasSeeAll")?.prim?.booleanOrNull == true

            val moreLike = MORE_LIKE.firstOrNull { rawTitle.startsWith(it, ignoreCase = true) }
            val (subtitle, title) = if (moreLike != null) {
                moreLikeLabel to rawTitle.substring(moreLike.length).trim()
            } else {
                null to rawTitle
            }
            HomeSection(
                id = "web_$recId",
                title = title,
                subtitle = subtitle,
                hasChevron = seeAll || primary != null || rawTitle.contains("Replay", ignoreCase = true),
                style = style,
                items = items,
                canLoadMore = seeAll,
                primaryContent = primary
            )
        }
        return WebHomePage(sections = sections, nextUrl = nextUrl, debugSummary = rawSummary)
    }

    /**
     * Parsea `/v1/me/recommendations/{id}/view/see-all` (que puede traer `personal-recommendation` o ítems directos)
     * y devuelve los ítems + posible cursor `next`.
     */
    fun parseSeeAllPage(body: String, moreLikeLabel: String = "Más como"): Pair<List<HomeItem>, String?> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList<HomeItem>() to null
        val next = root.str("next")
        val fromSections = parsePage(body, moreLikeLabel).sections.firstOrNull()?.items.orEmpty()
        if (fromSections.isNotEmpty()) return fromSections to next

        val resources = root["resources"].obj ?: return emptyList<HomeItem>() to next
        val directItems = root["data"].arr.orEmpty().mapNotNull { ref ->
            val res = resources[ref.str("type")].obj?.get(ref.str("id")).obj ?: return@mapNotNull null
            toItem(res, ref.obj?.get("meta").obj, HomeSectionStyle.SQUARE_CAROUSEL, resources)
        }
        return directItems to next
    }

    /** Parsea respuestas JSON:API normales de `/v1/me/recent/played` y `/v1/me/recent/played/tracks`. */
    fun parseRecentList(body: String): List<HomeItem> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        return root["data"].arr.orEmpty().mapNotNull { elem ->
            val obj = elem.obj ?: return@mapNotNull null
            toJsonApiItem(obj)
        }
    }

    /** Parsea `/v1/social/badging-map`: `{ results: { badgingMap: { "itemId": ["friendId", ...] } } }`. */
    fun parseBadgingMap(body: String): Map<String, List<String>> {
        val root = json.parseToJsonElement(body).obj ?: return emptyMap()
        val mapObj = root["results"].obj?.get("badgingMap").obj ?: return emptyMap()
        val result = LinkedHashMap<String, List<String>>(mapObj.size)
        for ((itemId, friendsEl) in mapObj) {
            val friendIds = friendsEl.arr.orEmpty().mapNotNull { it.prim?.contentOrNull }
            if (friendIds.isNotEmpty()) result[itemId] = friendIds
        }
        return result
    }

    /** Parsea `/v1/social/{sf}/social-profiles?ids=...` o `/social-profiles/{id}` en `FriendBadge`. */
    fun parseSocialProfiles(body: String): Map<String, FriendBadge> {
        val root = json.parseToJsonElement(body).obj ?: return emptyMap()
        val result = LinkedHashMap<String, FriendBadge>()
        // Formato JSON:API estándar (`data` como lista de objetos completos)
        root["data"].arr.orEmpty().forEach { entry ->
            val o = entry.obj ?: return@forEach
            val id = o.str("id") ?: return@forEach
            val attrs = o["attributes"].obj ?: return@forEach
            val name = attrs.str("name") ?: attrs.str("handle") ?: return@forEach
            val avatar = attrs["artwork"].obj?.str("url")?.let { artUrl(it, 160, 160) }
            result[id] = FriendBadge(
                id = id,
                name = name,
                handle = attrs.str("handle"),
                avatarUrl = avatar
            )
        }
        // Formato map por si viene en `resources.social-profiles`
        root["resources"].obj?.get("social-profiles").obj?.forEach { (id, el) ->
            val attrs = el.obj?.get("attributes").obj ?: return@forEach
            val name = attrs.str("name") ?: attrs.str("handle") ?: return@forEach
            val avatar = attrs["artwork"].obj?.str("url")?.let { artUrl(it, 160, 160) }
            result[id] = FriendBadge(id = id, name = name, handle = attrs.str("handle"), avatarUrl = avatar)
        }
        return result
    }

    /**
     * Parsea `/v1/editorial/{storefront}/groupings?name=music&format[resources]=map`
     * incluyendo banners destacados (`316`), listas de canciones (`327`), carruseles (`326`/`385`),
     * géneros rápidos (`322`) y categorías de "Más para explorar" (`391`).
     */
    fun parseGroupings(body: String, filterRadioTalkStations: Boolean = true): List<HomeSection> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        val resources = root["resources"].obj ?: return emptyList()
        val rootRef = root["data"].arr?.firstOrNull() ?: return emptyList()

        fun resolve(type: String?, id: String?): JsonObject? =
            if (type == null || id == null) null else resources[type].obj?.get(id).obj

        val grouping = resolve(rootRef.str("type"), rootRef.str("id")) ?: return emptyList()
        val tabs = grouping["relationships"].obj?.get("tabs").obj?.get("data").arr.orEmpty()
        val tabRef = tabs.firstOrNull() ?: return emptyList()
        val tab = resolve(tabRef.str("type"), tabRef.str("id")) ?: return emptyList()
        val shelfRefs = tab["relationships"].obj?.get("children").obj?.get("data").arr.orEmpty()

        return shelfRefs.mapIndexedNotNull { index, ref ->
            val shelf = resolve(ref.str("type"), ref.str("id")) ?: return@mapIndexedNotNull null
            val attrs = shelf["attributes"].obj ?: return@mapIndexedNotNull null
            val shelfId = shelf.str("id") ?: "$index"
            val rawTitle = attrs.str("name") ?: attrs.str("title").orEmpty()
            val kind = attrs.str("editorialElementKind").orEmpty()

            // Estantes de enlaces: en Novedades/Radio mostrar "Más para explorar" (391) al final; omitir 322 sin título
            val rawLinks = attrs["links"].arr.orEmpty()
            if (kind == "322" && rawTitle.isBlank()) {
                return@mapIndexedNotNull null
            }
            if (kind == "391" || (rawLinks.isNotEmpty() && rawTitle.isNotBlank())) {
                val linkItems = rawLinks.mapIndexedNotNull { lIdx, el ->
                    val lo = el.obj ?: return@mapIndexedNotNull null
                    val label = lo.str("label") ?: lo.str("name") ?: return@mapIndexedNotNull null
                    val linkUrl = lo.str("url") ?: return@mapIndexedNotNull null
                    HomeItem(
                        id = "link_${shelfId}_$lIdx",
                        type = "editorial-link",
                        title = label,
                        subtitle = "",
                        badge = lo.str("target"),
                        artworkUrl = null,
                        url = linkUrl
                    )
                }
                if (linkItems.isNotEmpty()) {
                    return@mapIndexedNotNull HomeSection(
                        id = "grp_$shelfId",
                        title = rawTitle.ifBlank { "Más para explorar" },
                        hasChevron = false,
                        style = HomeSectionStyle.EXPLORE_LINKS,
                        items = linkItems,
                        canLoadMore = false
                    )
                }
            }

            val rels = shelf["relationships"].obj
            val itemRefs = (rels?.get("children").obj?.get("data").arr
                ?: rels?.get("contents").obj?.get("data").arr).orEmpty()

            val style = when {
                kind == "316" || kind == "320" -> HomeSectionStyle.SUPER_HERO
                kind == "327" -> HomeSectionStyle.COMPACT_TRACKS
                kind == "385" -> HomeSectionStyle.HERO_PICKS
                else -> HomeSectionStyle.SQUARE_CAROUSEL
            }

            val items = itemRefs.mapNotNull { itemRef ->
                val res = resolve(itemRef.str("type"), itemRef.str("id")) ?: return@mapNotNull null
                toItem(res, itemRef.obj?.get("meta").obj, style, resources, filterRadioTalkStations)
            }
            if (items.isEmpty()) return@mapIndexedNotNull null

            val sectionTitle = if (rawTitle.isBlank() && (kind == "316" || kind == "320")) "Lo más destacado" else rawTitle
            val canSeeAll = items.size > 4 || kind == "327"
            HomeSection(
                id = "grp_$shelfId",
                title = sectionTitle,
                hasChevron = canSeeAll,
                style = style,
                items = items,
                canLoadMore = canSeeAll
            )
        }
    }

    /** Extrae los enlaces de géneros (`editorialElementKind == "322"`) de `groupings?name=music`. */
    fun parseGenreLinksFromGroupings(body: String): List<HomeSection> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        val resources = root["resources"].obj ?: return emptyList()
        val rootRef = root["data"].arr?.firstOrNull() ?: return emptyList()
        fun resolve(type: String?, id: String?): JsonObject? =
            if (type == null || id == null) null else resources[type].obj?.get(id).obj

        val grouping = resolve(rootRef.str("type"), rootRef.str("id")) ?: return emptyList()
        val tabRef = grouping["relationships"].obj?.get("tabs").obj?.get("data").arr?.firstOrNull() ?: return emptyList()
        val tab = resolve(tabRef.str("type"), tabRef.str("id")) ?: return emptyList()
        val shelfRefs = tab["relationships"].obj?.get("children").obj?.get("data").arr.orEmpty()

        val genres = shelfRefs.mapNotNull { ref ->
            val shelf = resolve(ref.str("type"), ref.str("id")) ?: return@mapNotNull null
            val attrs = shelf["attributes"].obj ?: return@mapNotNull null
            if (attrs.str("editorialElementKind") != "322") return@mapNotNull null
            val shelfId = shelf.str("id") ?: "genres"
            attrs["links"].arr.orEmpty().mapIndexedNotNull { idx, el ->
                val lo = el.obj ?: return@mapIndexedNotNull null
                val label = lo.str("label") ?: lo.str("name") ?: return@mapIndexedNotNull null
                val linkUrl = lo.str("url") ?: return@mapIndexedNotNull null
                HomeItem(
                    id = "genre_link_${shelfId}_$idx",
                    type = "editorial-link",
                    title = label,
                    subtitle = "",
                    badge = lo.str("target"),
                    artworkUrl = null,
                    url = linkUrl
                )
            }
        }.flatten()

        return if (genres.isEmpty()) emptyList() else listOf(
            HomeSection(
                id = "grp_all_genres",
                title = "Explorar por género",
                hasChevron = false,
                style = HomeSectionStyle.EXPLORE_LINKS,
                items = genres,
                canLoadMore = false
            )
        )
    }

    /**
     * Parsea `/v1/catalog/{storefront}/charts` (Top Canciones, Álbumes, Playlists, Videos, Top 100 Global y Ciudades).
     */
    fun parseCatalogCharts(body: String): List<HomeSection> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        val results = root["results"].obj ?: return emptyList()
        val sections = mutableListOf<HomeSection>()

        val order = listOf("songs", "albums", "playlists", "dailyGlobalTopCharts", "cityCharts", "music-videos")
        val keys = order.filter { results.containsKey(it) } + results.keys.filterNot { it in order }

        for (key in keys) {
            val groups = results[key].arr.orEmpty()
            groups.forEachIndexed { idx, groupEl ->
                val group = groupEl.obj ?: return@forEachIndexed
                val name = group.str("name").orEmpty().ifBlank { key }
                val chartId = group.str("chart") ?: "${key}_$idx"
                val items = group["data"].arr.orEmpty().mapNotNull { el ->
                    el.obj?.let { toJsonApiItem(it) }
                }
                if (items.isEmpty()) return@forEachIndexed
                val style = if (key == "songs") HomeSectionStyle.COMPACT_TRACKS else HomeSectionStyle.SQUARE_CAROUSEL
                sections += HomeSection(
                    id = "chart_${key}_${chartId}_$idx",
                    title = name,
                    hasChevron = items.size > 4 || key == "songs",
                    style = style,
                    items = items,
                    canLoadMore = items.size > 4 || key == "songs"
                )
            }
        }
        return sections.distinctBy { it.id }
    }

    /**
     * Parsea `/v1/editorial/{storefront}/rooms/{fcId}` y `/rooms/{fcId}/contents`
     * (usado por "Explorar por género", "Décadas", "Estados de ánimo", "Músicas del mundo" y Radio).
     */
    fun parseEditorialRoom(body: String, fallbackTitle: String): Pair<List<HomeItem>, String?> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList<HomeItem>() to null
        val resources = root["resources"].obj ?: JsonObject(emptyMap())
        val first = root["data"].arr?.firstOrNull().obj
        val isRoomWrapper = first?.str("type") == "rooms"
        val contentsObj = if (isRoomWrapper) first?.get("relationships")?.obj?.get("contents")?.obj else null
        val dataArr = contentsObj?.get("data").arr ?: root["data"].arr.orEmpty()
        val next = contentsObj?.str("next") ?: root.str("next")
        val items = dataArr.mapNotNull { el ->
            val obj = el.obj ?: return@mapNotNull null
            val resolved = if (obj["attributes"] == null) {
                resources[obj.str("type")].obj?.get(obj.str("id")).obj ?: obj
            } else obj
            toJsonApiItem(resolved)
        }.distinctBy { "${it.type}_${it.id}" }
        return items to next
    }

    /** Extrae el `grouping` ID de un `apple-curators/{id}?include=playlists,grouping` (programas de radio). */
    fun extractCuratorGroupingId(body: String): String? {
        val root = json.parseToJsonElement(body).obj ?: return null
        val first = root["data"].arr?.firstOrNull().obj ?: return null
        return first["relationships"].obj?.get("grouping").obj?.get("data").arr?.firstOrNull()?.str("id")
    }

    /** Sugerencias en tiempo real de `/v1/catalog/{storefront}/search/suggestions`. */
    fun parseSearchSuggestions(body: String): SearchSuggestionsResult {
        val root = json.parseToJsonElement(body).obj ?: return SearchSuggestionsResult()
        val raw = root["results"].obj?.get("suggestions").arr.orEmpty()
        val terms = mutableListOf<String>()
        val topResults = mutableListOf<HomeItem>()
        raw.forEach { entry ->
            val obj = entry.obj ?: return@forEach
            when (obj.str("kind")) {
                "terms" -> {
                    val term = obj.str("displayTerm") ?: obj.str("searchTerm")
                    if (!term.isNullOrBlank()) terms += term
                }
                "topResults" -> {
                    obj["content"].obj?.let { content ->
                        toJsonApiItem(content)?.let { topResults += it }
                    }
                }
            }
        }
        return SearchSuggestionsResult(
            terms = terms.distinct(),
            topResults = topResults.distinctBy { "${it.type}_${it.id}" }
        )
    }

    /** Resultados de `/v1/catalog/{storefront}/search` y `/v1/me/library/search`. */
    fun parseSearchResults(body: String): List<HomeSection> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        val results = root["results"].obj ?: return emptyList()
        val order = listOf(
            "top",
            "topResults",
            "songs",
            "library-songs",
            "artists",
            "library-artists",
            "albums",
            "library-albums",
            "playlists",
            "library-playlists",
            "music-videos",
            "library-music-videos",
            "music-movies",
            "stations",
            "social-profiles",
            "apple-curators",
            "curators",
            "record-labels",
            "activities",
            "editorial-items"
        )
        val keys = order.filter { results.containsKey(it) } + results.keys.filterNot { it in order }
        val sections = mutableListOf<HomeSection>()

        for (key in keys) {
            val group = results[key].obj ?: continue
            val items = group["data"].arr.orEmpty().mapNotNull { el ->
                val obj = el.obj ?: return@mapNotNull null
                toJsonApiItem(obj)
            }.distinctBy { "${it.type}_${it.id}" }
            if (items.isEmpty()) continue

            val defaultTitle = when (key) {
                "top", "topResults" -> "Resultado principal"
                "songs", "library-songs" -> "Canciones"
                "artists", "library-artists" -> "Artistas"
                "albums", "library-albums" -> "Álbumes"
                "playlists", "library-playlists" -> "Playlists"
                "music-videos", "library-music-videos" -> "Videos musicales"
                "music-movies" -> "Películas musicales"
                "stations" -> "Estaciones"
                "social-profiles" -> "Perfiles"
                "apple-curators", "curators" -> "Programas y curadores"
                "record-labels" -> "Sellos discográficos"
                "activities" -> "Estados de ánimo"
                "editorial-items" -> "Colecciones"
                else -> key.replaceFirstChar { it.uppercase() }
            }
            val title = group.str("name")
                ?.takeIf { it.isNotBlank() && !it.equals("topResults", ignoreCase = true) }
                ?: defaultTitle
            val style = when (key) {
                "songs", "library-songs" -> HomeSectionStyle.COMPACT_TRACKS
                "artists", "library-artists", "social-profiles" -> HomeSectionStyle.ARTIST_CIRCLES
                else -> HomeSectionStyle.SQUARE_CAROUSEL
            }
            sections += HomeSection(
                id = "search_$key",
                title = title,
                hasChevron = items.size > 4 || style == HomeSectionStyle.COMPACT_TRACKS,
                style = style,
                items = items,
                canLoadMore = items.size > 4 || style == HomeSectionStyle.COMPACT_TRACKS
            )
        }
        return sections
    }

    /** Lista paginada de `/v1/me/library/` (recently-added, playlists, albums, artists, songs). */
    fun parseLibraryList(body: String): Pair<List<HomeItem>, String?> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList<HomeItem>() to null
        val items = root["data"].arr.orEmpty().mapNotNull { el ->
            el.obj?.let { toJsonApiItem(it) }
        }.distinctBy { "${it.type}_${it.id}" }
        return items to root.str("next")
    }

    /** Extrae categorías visuales oficiales de `/v1/recommendations/{storefront}?name=search-landing`. */
    fun parseSearchLandingCategories(body: String): List<HomeItem> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        val resources = root["resources"].obj ?: JsonObject(emptyMap())
        val recs = root["data"].arr.orEmpty()
        fun resolve(type: String?, id: String?): JsonObject? =
            if (type == null || id == null) null else resources[type].obj?.get(id).obj

        val out = mutableListOf<HomeItem>()
        recs.forEach { recRef ->
            val recObj = resolve(recRef.str("type"), recRef.str("id")) ?: recRef.obj ?: return@forEach
            val contents = recObj["relationships"].obj?.get("contents").obj?.get("data").arr.orEmpty()
            contents.forEach { itemRef ->
                val res = resolve(itemRef.str("type"), itemRef.str("id")) ?: itemRef.obj ?: return@forEach
                val id = res.str("id") ?: return@forEach
                val type = res.str("type").orEmpty()
                if (type in EXCLUDED_TYPES) return@forEach

                val attrs = res["attributes"].obj ?: return@forEach
                val nestedRef = res["relationships"].obj?.get("contents").obj?.get("data").arr?.firstOrNull()
                val nestedObj = nestedRef?.let { resolve(it.str("type"), it.str("id")) }
                val nestedAttrs = nestedObj?.get("attributes").obj

                val notes = attrs["plainEditorialNotes"].obj ?: attrs["editorialNotes"].obj
                val title = attrs.str("name")
                    ?: notes?.str("name")
                    ?: nestedAttrs?.str("name")
                    ?: return@forEach

                val ea = attrs["editorialArtwork"].obj ?: nestedAttrs?.get("editorialArtwork")?.obj
                val artObj = attrs["artwork"].obj
                    ?: ea?.get("brandLogo").obj
                    ?: ea?.get("subscriptionHero").obj
                    ?: ea?.get("subscriptionCover").obj
                    ?: nestedAttrs?.get("artwork")?.obj
                    ?: ea?.values?.firstOrNull()?.obj

                val rawLink = attrs["link"].obj
                val linkUrl = rawLink?.str("url") ?: attrs.str("url") ?: nestedAttrs?.str("url")
                val linkTarget = rawLink?.str("target")

                val artW = artObj?.int("width")
                val artH = artObj?.int("height")
                val formattedArt = artObj?.str("url")?.let { tpl ->
                    if (artW != null && artH != null && artW > artH) {
                        val h = ((800.0 * artH) / artW).toInt().coerceIn(200, 800)
                        artUrl(tpl, 800, h, crop = "bb")
                    } else {
                        artUrl(tpl, 600, 600, crop = "bb")
                    }
                }

                val resolvedType = if (type == "editorial-items" && linkUrl != null) {
                    if (nestedObj != null && !linkUrl.contains("viewTop", true) && !linkUrl.contains("fcId=", true) && !linkUrl.contains("viewGrouping", true)) {
                        nestedObj.str("type") ?: type
                    } else {
                        "editorial-link"
                    }
                } else type

                out += HomeItem(
                    id = if (resolvedType == nestedObj?.str("type")) (nestedObj.str("id") ?: id) else id,
                    type = resolvedType,
                    title = title.removePrefix("Apple Music ").takeIf { it.isNotBlank() } ?: title,
                    subtitle = notes?.str("short").orEmpty(),
                    badge = linkTarget,
                    artworkUrl = formattedArt,
                    bgColorHex = artObj?.str("bgColor"),
                    textColorHex = artObj?.str("textColor1"),
                    url = linkUrl
                )
            }
        }
        return out
            .filterNot { it.badge == "external" || it.url.orEmpty().contains("concerts", ignoreCase = true) }
            .distinctBy { "${it.type}_${it.id}_${it.url.orEmpty()}" }
    }

    /** Mapea respuestas de `/v1/catalog/{storefront}/artists?ids=...` a `(artworkUrl, bgColorHex)`. */
    fun parseArtistArtworkBatch(body: String): Map<String, Pair<String?, String?>> {
        val root = json.parseToJsonElement(body).obj ?: return emptyMap()
        val out = HashMap<String, Pair<String?, String?>>()
        root["data"].arr.orEmpty().forEach { el ->
            val obj = el.obj ?: return@forEach
            val id = obj.str("id") ?: return@forEach
            val attrs = obj["attributes"].obj ?: return@forEach
            val ea = attrs["editorialArtwork"].obj
            val artObj = attrs["artwork"].obj
                ?: ea?.get("backgroundStatic1x1").obj
                ?: ea?.get("staticDetailSquare").obj
                ?: ea?.get("brandLogo").obj
                ?: ea?.get("superHeroTall").obj
                ?: ea?.values?.firstOrNull()?.obj
            val url = artObj?.str("url")?.let { artUrl(it, 600, 600) }
            if (!url.isNullOrBlank()) {
                out[id] = url to artObj.str("bgColor")
            }
        }
        return out
    }

    /** Extrae categorías internas explorables (391 + 322) desde `groupings?name=music` para el estado inicial de Buscar. */
    fun extractBrowseCategoriesFromGroupings(body: String): List<HomeItem> {
        val sections = (parseGroupings(body).filter { it.style == HomeSectionStyle.EXPLORE_LINKS } +
            parseGenreLinksFromGroupings(body))
        return sections
            .flatMap { it.items }
            .filterNot { item ->
                item.badge == "external" || item.url.orEmpty().contains("concerts", ignoreCase = true)
            }
            .distinctBy { it.url ?: it.title }
    }

    /** Resuelve `/v1/me/library/artists/{id}?include=catalog,albums` a un ID de catálogo o detalle local. */
    fun extractLibraryArtistDetail(body: String, fallbackName: String): Pair<String?, ArtistDetail?> {
        val root = json.parseToJsonElement(body).obj ?: return null to null
        val artist = root["data"].arr?.firstOrNull().obj ?: return null to null
        val catalogId = artist["relationships"].obj
            ?.get("catalog")?.obj
            ?.get("data")?.arr
            ?.firstOrNull()?.str("id")
        if (!catalogId.isNullOrBlank() && !catalogId.startsWith("r.")) {
            return catalogId to null
        }
        val attrs = artist["attributes"].obj
        val name = attrs?.str("name")?.takeIf { it.isNotBlank() } ?: fallbackName
        val albums = artist["relationships"].obj
            ?.get("albums")?.obj
            ?.get("data")?.arr.orEmpty()
            .mapNotNull { it.obj?.let { a -> toJsonApiItem(a) } }
        val art = albums.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
        return null to ArtistDetail(
            id = artist.str("id").orEmpty(),
            name = name,
            genre = "En tu biblioteca",
            artworkUrl = art,
            heroUrl = art,
            bgColorHex = albums.firstOrNull()?.bgColorHex,
            bio = null,
            latestRelease = albums.firstOrNull(),
            topSongs = emptyList(),
            albums = albums,
            singles = emptyList(),
            playlists = emptyList(),
            similarArtists = emptyList()
        )
    }

    /**
     * Parsea `/v1/catalog/{storefront}/apple-curators/{id}/playlists` o `/apple-curators/{id}?include=playlists`.
     */
    fun parseCuratorSections(body: String, fallbackTitle: String): List<HomeSection> {
        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        val first = root["data"].arr?.firstOrNull().obj
        val isCuratorObj = first?.str("type")?.contains("curator") == true
        val playlistArr = if (isCuratorObj) {
            first?.get("relationships")?.obj?.get("playlists")?.obj?.get("data").arr.orEmpty()
        } else {
            root["data"].arr.orEmpty()
        }
        val items = playlistArr.mapNotNull { el ->
            el.obj?.let { toJsonApiItem(it) }
        }.distinctBy { "${it.type}_${it.id}" }
        if (items.isEmpty()) return emptyList()
        val title = if (isCuratorObj) {
            first?.get("attributes")?.obj?.str("name")?.takeIf { it.isNotBlank() } ?: fallbackTitle
        } else fallbackTitle
        return listOf(
            HomeSection(
                id = "cur_${first?.str("id") ?: "list"}",
                title = title,
                hasChevron = items.size > 4,
                style = HomeSectionStyle.SQUARE_CAROUSEL,
                items = items,
                canLoadMore = items.size > 4
            )
        )
    }

    /**
     * Parsea `/v1/editorial/{storefront}/multiplex/{id}` (Categorías: Explorar por género, Décadas, Estado de ánimo...).
     */
    fun parseMultiplexSections(body: String, fallbackTitle: String): List<HomeSection> {
        val fromGroupings = parseGroupings(body)
        if (fromGroupings.isNotEmpty()) return fromGroupings

        val root = json.parseToJsonElement(body).obj ?: return emptyList()
        val resources = root["resources"].obj ?: JsonObject(emptyMap())
        val refs = root["data"].arr.orEmpty()

        fun resolve(type: String?, id: String?): JsonObject? =
            if (type == null || id == null) null else resources[type].obj?.get(id).obj

        val directLinks = mutableListOf<HomeItem>()
        val directMedia = mutableListOf<HomeItem>()
        val nestedSections = mutableListOf<HomeSection>()

        refs.forEachIndexed { idx, ref ->
            val refObj = ref.obj ?: return@forEachIndexed
            val res = resolve(refObj.str("type"), refObj.str("id")) ?: refObj
            val attrs = res["attributes"].obj
            val links = attrs?.get("links").arr.orEmpty()
            if (links.isNotEmpty()) {
                links.forEachIndexed { lIdx, le ->
                    val lo = le.obj ?: return@forEachIndexed
                    val label = lo.str("label") ?: lo.str("name") ?: return@forEachIndexed
                    val linkUrl = lo.str("url") ?: return@forEachIndexed
                    directLinks += HomeItem(
                        id = "m_link_${idx}_$lIdx",
                        type = "editorial-link",
                        title = label,
                        subtitle = "",
                        badge = lo.str("target"),
                        artworkUrl = null,
                        url = linkUrl
                    )
                }
            }
            val rels = res["relationships"].obj
            val childRefs = (rels?.get("children").obj?.get("data").arr
                ?: rels?.get("contents").obj?.get("data").arr).orEmpty()
            if (childRefs.isNotEmpty() && attrs?.containsKey("editorialElementKind") == true) {
                val items = childRefs.mapNotNull { cr ->
                    val target = resolve(cr.str("type"), cr.str("id")) ?: return@mapNotNull null
                    toItem(target, cr.obj?.get("meta").obj, HomeSectionStyle.SQUARE_CAROUSEL, resources)
                }
                if (items.isNotEmpty()) {
                    val shelfTitle = attrs.str("name")?.takeIf { it.isNotBlank() } ?: fallbackTitle
                    nestedSections += HomeSection(
                        id = "grp_mx_${res.str("id") ?: idx}",
                        title = shelfTitle,
                        hasChevron = items.size > 4,
                        style = HomeSectionStyle.SQUARE_CAROUSEL,
                        items = items,
                        canLoadMore = items.size > 4
                    )
                }
            } else {
                toItem(res, refObj["meta"].obj, HomeSectionStyle.SQUARE_CAROUSEL, resources)?.let {
                    directMedia += it
                }
            }
        }

        val out = mutableListOf<HomeSection>()
        out += nestedSections
        if (directMedia.isNotEmpty()) {
            out += HomeSection(
                id = "grp_mx_media",
                title = fallbackTitle,
                hasChevron = directMedia.size > 4,
                style = HomeSectionStyle.SQUARE_CAROUSEL,
                items = directMedia,
                canLoadMore = directMedia.size > 4
            )
        }
        if (directLinks.isNotEmpty()) {
            out += HomeSection(
                id = "grp_mx_links",
                title = if (out.isEmpty()) fallbackTitle else "Categorías",
                hasChevron = false,
                style = HomeSectionStyle.EXPLORE_LINKS,
                items = directLinks,
                canLoadMore = false
            )
        }
        return out
    }

    private fun toJsonApiItem(res: JsonObject): HomeItem? {
        val id = res.str("id") ?: return null
        val type = res.str("type").orEmpty()
        var attrs = res["attributes"].obj ?: JsonObject(emptyMap())
        val catObj = res["relationships"].obj?.get("catalog").obj?.get("data").arr?.firstOrNull().obj
        val catAttrs = catObj?.get("attributes").obj
        if (catAttrs != null) attrs = JsonObject(catAttrs + attrs)

        val relAlbumArt = res["relationships"].obj?.get("albums").obj?.get("data").arr
            ?.firstOrNull()?.obj?.get("attributes")?.obj?.get("artwork")?.obj
        val ea = attrs["editorialArtwork"].obj

        val title = attrs.str("name") ?: attrs.str("handle") ?: return null
        val subtitle = when (type) {
            "artists", "library-artists" -> ""
            "social-profiles" -> attrs.str("handle")?.takeIf { it.isNotBlank() }?.let {
                if (it.startsWith("@")) it else "@$it"
            } ?: attrs["editorialNotes"].obj?.str("short").orEmpty()
            "stations" -> attrs["editorialNotes"].obj?.str("short") ?: attrs.str("curatorName").orEmpty()
            "library-songs", "songs" -> attrs.str("artistName") ?: attrs.str("albumName").orEmpty()
            else -> attrs.str("artistName")
                ?: attrs.str("curatorName")
                ?: attrs["editorialNotes"].obj?.str("short")
                ?: ""
        }
        val artwork = attrs["artwork"].obj
            ?: relAlbumArt
            ?: ea?.get("backgroundStatic1x1").obj
            ?: ea?.get("staticDetailSquare").obj
            ?: ea?.get("brandLogo").obj
            ?: ea?.get("subscriptionCover").obj?.takeIf {
                val w = it.int("width"); val h = it.int("height"); w == null || h == null || w == h
            }
            ?: ea?.get("subscriptionHero").obj
            ?: ea?.values?.firstOrNull()?.obj
        val catalogId = catObj?.str("id") ?: attrs["playParams"].obj?.str("catalogId") ?: attrs["playParams"].obj?.str("id")

        return HomeItem(
            id = id,
            type = type,
            title = title,
            subtitle = subtitle,
            artworkUrl = artwork?.str("url")?.let { artUrl(it, 600, 600) },
            bgColorHex = artwork?.str("bgColor"),
            isExplicit = attrs.str("contentRating") == "explicit",
            textColorHex = artwork?.str("textColor1"),
            url = attrs.str("url"),
            catalogId = catalogId,
            motionArtworkUrl = extractMotionVideoUrl(attrs)
        )
    }

    private fun extractMotionVideoUrl(attrs: JsonObject?): String? {
        val ev = attrs?.get("editorialVideo").obj ?: return null
        return listOf(
            "motionDetailSquare",
            "motionSquareVideo1x1",
            "motionArtistSquare1x1",
            "motionDetailTall",
            "motionTallVideo3x4"
        ).firstNotNullOfOrNull { k -> ev[k].obj?.str("video")?.takeIf { it.isNotBlank() } }
    }

    private fun toItem(
        res: JsonObject,
        meta: JsonObject?,
        style: HomeSectionStyle,
        resources: JsonObject,
        filterRadioTalkStations: Boolean = true
    ): HomeItem? {
        val id = res.str("id") ?: return null
        val type = res.str("type").orEmpty()
        // Filtrar podcasts, programas de TV/películas y (solo en Home/Novedades) talk-shows con radio-show/airTime
        if (type in EXCLUDED_TYPES) return null
        var attrs = res["attributes"].obj ?: JsonObject(emptyMap())
        if (filterRadioTalkStations && type == "stations") {
            val hasRadioShow = res["relationships"].obj?.get("radio-show").obj?.get("data").arr?.isNotEmpty() == true
            val isLiveTalk = attrs["airTime"] != null ||
                attrs["airDate"] != null ||
                attrs["isLive"]?.prim?.booleanOrNull == true
            if (hasRadioShow || (style == HomeSectionStyle.SUPER_HERO && isLiveTalk)) {
                return null
            }
        }

        val isEditorial = type == "editorial-items" || type == "editorial-elements"
        val nestedRef = when {
            isEditorial ->
                res["relationships"].obj?.get("contents").obj?.get("data").arr?.firstOrNull()
            attrs["artwork"] == null ->
                res["relationships"].obj?.get("catalog").obj?.get("data").arr?.firstOrNull()
            else -> null
        }
        val nestedObj = nestedRef?.let {
            resources[it.str("type")].obj?.get(it.str("id")).obj
        }
        val nestedAttrs = nestedObj?.get("attributes").obj
        if (nestedAttrs != null) {
            attrs = JsonObject(nestedAttrs + attrs)
        }

        val targetObj = nestedObj ?: res
        val resolvedType = nestedObj?.str("type")?.takeIf { isEditorial } ?: type
        val resolvedId = nestedObj?.str("id")?.takeIf { isEditorial } ?: id

        val cardKey = meta?.str("editorialCard")
        val card = cardKey?.let { attrs["plainEditorialCard"].obj?.get(it).obj }
        val notes = attrs["plainEditorialNotes"].obj
        val editorialNotes = attrs["editorialNotes"].obj
        val cardNotes = card?.get("plainEditorialNotes").obj

        val title = attrs.str("name")
            ?: notes?.str("name")
            ?: cardNotes?.str("name")
            ?: attrs.str("handle")
            ?: editorialNotes?.str("name")
            ?: nestedAttrs?.str("name")
            ?: return null

        val topGenres = (attrs["topGenreNames"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.take(2)
            ?.joinToString(" · ")
            ?.takeIf { it.isNotBlank() }

        val liveEventInfo = if (resolvedType == "stations") {
            extractLiveEventLabel(targetObj, resources)
        } else null

        val subtitle = when (resolvedType) {
            "artists", "library-artists" -> ""
            "social-profiles" -> attrs.str("handle")?.let { "@$it" }
                ?.let { h -> if (topGenres != null) "$h · $topGenres" else h }
                ?: topGenres
                ?: ""
            "editorial-items", "editorial-elements", "social-upsells", "concerts" ->
                attrs.str("artistName")
                    ?: notes?.str("short")
                    ?: cardNotes?.str("short")
                    ?: editorialNotes?.str("short")
                    ?: attrs.str("curatorName")
                    ?: ""
            "stations" ->
                liveEventInfo
                    ?: notes?.str("short")
                    ?: editorialNotes?.str("short")
                    ?: attrs.str("curatorName")
                    ?: ""
            else ->
                attrs.str("artistName")
                    ?: attrs.str("artistNames")
                    ?: attrs.str("curatorName")
                    ?: notes?.str("short")
                    ?: ""
        }

        val artwork = attrs["artwork"].obj
        val editorial = card?.get("editorialArtwork").obj ?: attrs["editorialArtwork"].obj
        val rootEditorial = attrs["editorialArtwork"].obj
        val editorialArt = when (style) {
            HomeSectionStyle.HERO_PICKS ->
                editorial?.get("superHeroTall").obj ?: rootEditorial?.get("superHeroTall").obj
            HomeSectionStyle.SUPER_HERO ->
                editorial?.get("superHeroWide").obj
                    ?: editorial?.get("subscriptionHero").obj
                    ?: editorial?.get("bannerUber").obj
                    ?: rootEditorial?.get("superHeroWide").obj
                    ?: rootEditorial?.get("subscriptionHero").obj
                    ?: rootEditorial?.get("bannerUber").obj
            else -> null
        }
        val fallbackArt = artwork
            ?: editorial?.get("backgroundStatic1x1").obj
            ?: editorial?.get("staticDetailSquare").obj
            ?: editorial?.get("brandLogo").obj
            ?: editorial?.get("subscriptionCover").obj?.takeIf {
                val w = it.int("width"); val h = it.int("height"); w == null || h == null || w == h
            }
            ?: editorial?.get("subscriptionHero").obj
            ?: rootEditorial?.values?.firstOrNull()?.obj

        val playParams = attrs["playParams"].obj
        val catalogRefId = res["relationships"].obj?.get("catalog").obj?.get("data").arr?.firstOrNull()?.str("id")
        val stationArtistCatalogId = if (resolvedType == "stations" && resolvedId.startsWith("ra.a-")) {
            resolvedId.removePrefix("ra.a-").takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        } else null
        val catalogId = catalogRefId
            ?: stationArtistCatalogId
            ?: playParams?.str("catalogId")
            ?: playParams?.str("globalId")
            ?: playParams?.str("id")
            ?: resolvedId

        val linkUrl = attrs["link"].obj?.str("url") ?: attrs.str("url")
        val isLiveStation = resolvedType == "stations" && attrs["isLive"]?.prim?.booleanOrNull == true

        return HomeItem(
            id = resolvedId,
            type = resolvedType,
            title = title,
            subtitle = subtitle,
            badge = meta?.get("reason").obj?.str("stringForDisplay")
                ?: attrs.str("designBadge")?.takeIf { it.isNotBlank() }
                ?: notes?.str("tagline")
                ?: editorialNotes?.str("tagline")
                ?: if (isLiveStation) "EN VIVO" else null,
            artworkUrl = fallbackArt?.str("url")?.let { artUrl(it, 600, 600) },
            bgColorHex = (editorialArt ?: fallbackArt)?.str("bgColor"),
            isExplicit = attrs.str("contentRating") == "explicit",
            tallArtworkUrl = editorialArt?.str("url")?.let { urlTemplate ->
                if (style == HomeSectionStyle.SUPER_HERO) {
                    val nativeW = editorialArt.int("width")?.takeIf { it > 0 } ?: 4320
                    val nativeH = editorialArt.int("height")?.takeIf { it > 0 } ?: 1800
                    val targetW = 1200
                    val targetH = ((targetW.toDouble() * nativeH) / nativeW).toInt().coerceIn(250, 900)
                    artUrl(urlTemplate, targetW, targetH, crop = "bb")
                } else {
                    artUrl(urlTemplate, 600, 800, crop = "bb")
                }
            },
            textColorHex = (editorialArt ?: fallbackArt)?.str("textColor1"),
            url = linkUrl,
            catalogId = catalogId,
            motionArtworkUrl = extractMotionVideoUrl(attrs)
        )
    }

    private fun extractLiveEventLabel(stationObj: JsonObject, resources: JsonObject): String? {
        val eventRefs = stationObj["relationships"].obj?.get("events").obj?.get("data").arr.orEmpty()
        if (eventRefs.isEmpty()) return null
        val events = eventRefs.mapNotNull { ref ->
            resources[ref.str("type")].obj?.get(ref.str("id")).obj
        }
        if (events.isEmpty()) return null

        val now = Instant.now()
        val chosen = events.firstOrNull { ev ->
            val a = ev["attributes"].obj ?: return@firstOrNull false
            val s = a.str("startTime")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val e = a.str("endTime")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            s != null && e != null && !now.isBefore(s) && now.isBefore(e)
        } ?: events.firstOrNull() ?: return null

        val evAttrs = chosen["attributes"].obj ?: return null
        val evTitle = evAttrs.str("title")?.takeIf { it.isNotBlank() }
        val desc = evAttrs["description"].obj?.str("standard")?.takeIf { it.isNotBlank() }
        val timeRange = formatTimeRange(evAttrs.str("startTime"), evAttrs.str("endTime"))
        return when {
            evTitle != null && timeRange != null -> "$evTitle · $timeRange"
            evTitle != null -> evTitle
            else -> desc
        }
    }

    private fun formatTimeRange(startIso: String?, endIso: String?): String? {
        if (startIso.isNullOrBlank() || endIso.isNullOrBlank()) return null
        return runCatching {
            val zone = ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
            "${fmt.format(Instant.parse(startIso))}–${fmt.format(Instant.parse(endIso))}"
        }.getOrNull()
    }

    private fun artUrl(template: String, w: Int, h: Int, crop: String = "cc") = template
        .replace("{w}", w.toString())
        .replace("{h}", h.toString())
        .replace("{c}", crop)
        .replace("{f}", "jpg")

    private val MORE_LIKE = listOf("Más como ", "Similar a ", "More Like ", "More like ", "Similar to ")
    private val EXCLUDED_TYPES = setOf("podcasts", "podcast-episodes", "tv-shows", "tv-episodes", "music-movies", "audiobooks")

    private val JsonElement?.obj get() = this as? JsonObject
    private val JsonElement?.arr get() = this as? JsonArray
    private val JsonElement?.prim get() = this as? JsonPrimitive
    private fun JsonElement?.str(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.int(key: String): Int? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.intOrNull
}
