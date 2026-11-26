package com.applemusic.android.data.repository

import android.util.Log
import com.applemusic.android.auth.AppleMusicCredentials
import com.applemusic.android.auth.TokenManager
import com.applemusic.android.auth.WebTokenProvider
import com.applemusic.android.data.api.AmpApiService
import com.applemusic.android.data.api.AppleMusicApiService
import com.applemusic.android.data.model.AlbumDetail
import com.applemusic.android.data.model.ArtistDetail
import com.applemusic.android.data.model.ConnectionMode
import com.applemusic.android.data.model.FriendBadge
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection
import com.applemusic.android.data.model.HomeSectionStyle
import com.applemusic.android.data.model.ItemAccountStatus
import com.applemusic.android.data.model.MediaDetailsParser
import com.applemusic.android.data.model.MediaResource
import com.applemusic.android.data.model.RecommendationResource
import com.applemusic.android.data.model.ReplayPeriodOption
import com.applemusic.android.data.model.ReplaySummary
import com.applemusic.android.data.model.SocialProfileDetail
import com.applemusic.android.data.model.SongCreditsDetail
import com.applemusic.android.data.model.SongLyrics
import com.applemusic.android.data.model.StorefrontInfo
import com.applemusic.android.data.model.TtmlLyricsParser
import com.applemusic.android.data.settings.UserAccountProfile
import com.applemusic.android.data.web.SearchSuggestionsResult
import com.applemusic.android.data.web.WebHomeParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.time.Year
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class HomeFeedResult(
    val mode: ConnectionMode,
    val sections: List<HomeSection>,
    val warningMessage: String? = null,
    val webLoginRequired: Boolean = false
)

@Serializable
data class LibraryCategoryPage(
    val items: List<HomeItem> = emptyList(),
    val nextUrl: String? = null
)

@Serializable
data class LibraryOverviewResult(
    val pinned: List<HomeItem> = emptyList(),
    val recentlyAdded: LibraryCategoryPage = LibraryCategoryPage(),
    val playlists: LibraryCategoryPage = LibraryCategoryPage(),
    val albums: LibraryCategoryPage = LibraryCategoryPage(),
    val artists: LibraryCategoryPage = LibraryCategoryPage(),
    val songs: LibraryCategoryPage = LibraryCategoryPage()
)

/**
 * Arma el Home completo igual que music.apple.com:
 *  - Feed principal (`listen-now` + paginación `next`)
 *  - Fotos de perfil de amigos (`v1/social/badging-map` + `v1/social/{sf}/social-profiles`)
 *  - Refresco liviano de "Escuchado recientemente" (`/v1/me/recent/played`)
 *  - "Ver todo" completo (`/view/see-all` paginado + historial de pistas)
 *  - Vistas de detalle: Álbumes, Playlists, Artistas, Perfiles Sociales y Replay (`music-summaries`)
 */
class HomeRepository(
    private val apiService: AppleMusicApiService,
    private val ampApiService: AmpApiService,
    private val webTokenProvider: WebTokenProvider,
    private val tokenManager: TokenManager,
    private val strings: HomeStrings = HomeStrings(),
    cacheDir: File? = null,
    private val languageOverrideProvider: () -> String? = { null }
) {

    private data class CachedEntry<T>(
        val value: T,
        val cachedAtMs: Long,
        val isLibrary: Boolean = false
    )

    private val diskCacheDir: File? = cacheDir?.let { File(it, "am_json_cache").apply { mkdirs() } }
    private val snapshotJson = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cachedProfiles = HashMap<String, FriendBadge>()
    private val detailCache = ConcurrentHashMap<String, CachedEntry<AlbumDetail>>()
    private val lyricsCache = ConcurrentHashMap<String, SongLyrics>()
    private val creditsCache = ConcurrentHashMap<String, SongCreditsDetail>()
    private val songMotionCache = ConcurrentHashMap<String, String>()

    fun loadCachedHomeFeed(): List<HomeSection>? {
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val raw = readDiskCache("feed_home_v1_$sf", FEED_SNAPSHOT_TTL_MS) ?: return null
        return runCatching { snapshotJson.decodeFromString<List<HomeSection>>(raw) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun loadCachedNewFeed(): List<HomeSection>? {
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val raw = readDiskCache("feed_new_v1_$sf", FEED_SNAPSHOT_TTL_MS) ?: return null
        return runCatching { snapshotJson.decodeFromString<List<HomeSection>>(raw) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun loadCachedRadioFeed(): List<HomeSection>? {
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val raw = readDiskCache("feed_radio_v1_$sf", FEED_SNAPSHOT_TTL_MS) ?: return null
        return runCatching { snapshotJson.decodeFromString<List<HomeSection>>(raw) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun loadCachedSearchCategories(): List<HomeItem>? {
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val raw = readDiskCache("feed_cats_v1_$sf", FEED_SNAPSHOT_TTL_MS) ?: return null
        return runCatching { snapshotJson.decodeFromString<List<HomeItem>>(raw) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun loadCachedLibraryOverview(): LibraryOverviewResult? {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession) return null
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val raw = readDiskCache("feed_lib_v1_$sf", FEED_SNAPSHOT_TTL_MS) ?: return null
        return runCatching { snapshotJson.decodeFromString<LibraryOverviewResult>(raw) }.getOrNull()
    }

    private fun readDiskCache(key: String, ttlMs: Long): String? {
        val dir = diskCacheDir ?: return null
        val file = File(dir, "${key.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.json")
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > ttlMs) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun writeDiskCache(key: String, body: String) {
        val dir = diskCacheDir ?: return
        if (body.isBlank()) return
        runCatching {
            val file = File(dir, "${key.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.json")
            file.writeText(body)
            // Mantener caché JSON ligera (máx. 36 archivos / 5 MB, protegiendo pins y feeds principales)
            val files = dir.listFiles()?.toList().orEmpty()
            val totalBytes = files.sumOf { it.length() }
            if (files.size > 36 || totalBytes > 5L * 1024L * 1024L) {
                val evictable = files
                    .filterNot { it.name.startsWith("local_pins_") || it.name.startsWith("feed_") }
                    .sortedBy { it.lastModified() }
                var bytes = totalBytes
                var count = files.size
                for (old in evictable) {
                    if (count <= 28 && bytes <= 3L * 1024L * 1024L) break
                    val len = old.length()
                    if (old.delete()) {
                        bytes -= len
                        count--
                    }
                }
            }
        }
    }

    private fun deleteDiskCacheByPrefix(prefix: String) {
        val dir = diskCacheDir ?: return
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(safePrefix)) f.delete()
            }
        }
    }

    /** Devuelve las pistas ya cargadas en memoria para un álbum/playlist (permite iniciar música al instante sin re-descargar la playlist). */
    fun getCachedContainerTracks(containerId: String?): List<HomeItem> {
        if (containerId.isNullOrBlank()) return emptyList()
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val detail = detailCache["${sf}_$containerId"]?.value
            ?: detailCache.entries.firstOrNull { it.key.endsWith("_$containerId") }?.value?.value
            ?: return emptyList()
        val containerAsItem = HomeItem(
            id = detail.id,
            type = detail.type,
            title = detail.title,
            subtitle = detail.artistName,
            artworkUrl = detail.artworkUrl,
            bgColorHex = detail.bgColorHex,
            catalogId = detail.catalogId,
            motionArtworkUrl = detail.motionSquareUrl ?: detail.motionTallUrl
        )
        return detail.tracks.map { track ->
            containerAsItem.copy(
                id = track.id,
                type = track.type,
                title = track.title,
                subtitle = track.artistName,
                artworkUrl = track.artworkUrl ?: detail.artworkUrl,
                catalogId = track.catalogId ?: track.id.takeIf { it.all(Char::isDigit) },
                motionArtworkUrl = if (!detail.isPlaylist) (detail.motionSquareUrl ?: detail.motionTallUrl) else null
            )
        }
    }

    private fun invalidateDetailCache(itemId: String? = null) {
        if (!itemId.isNullOrBlank()) {
            detailCache.keys.filter { it.endsWith("_$itemId") }.forEach { detailCache.remove(it) }
            deleteDiskCacheByPrefix("detail_v3_")
        } else {
            detailCache.entries.removeAll { it.value.isLibrary }
            deleteDiskCacheByPrefix("detail_v3_")
        }
    }

    fun invalidateFeedAndDetailCaches() {
        detailCache.clear()
        lyricsCache.clear()
        creditsCache.clear()
        songMotionCache.clear()
        artistCache.clear()
        diskCacheDir?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private suspend fun syncAccount(): Boolean {
        webTokenProvider.getOrFetchWebToken()
        tokenManager.syncWebUserTokenFromCookies()

        val creds = tokenManager.getCredentials()
        var sessionExpired = false

        val body: String? = if (creds.hasWebSession) {
            runCatching { ampApiService.getMyStorefrontRaw().string() }
                .onFailure { err ->
                    if (err.isAuthFailure()) {
                        Log.w(TAG, "Web user token rechazado (${err.message}); requiere reconexión")
                        tokenManager.clearWebUserToken()
                        sessionExpired = true
                    }
                }
                .getOrNull()
        } else {
            null
        } ?: if (creds.hasDeveloperToken && creds.hasUserToken) {
            runCatching { apiService.getMyStorefrontRaw().string() }.getOrNull()
        } else {
            null
        }

        val account = body?.let { runCatching { StorefrontInfo.parse(it) }.getOrNull() }
        if (account != null) {
            val language = account.pickLanguage(Locale.getDefault())
            Log.d(TAG, "Cuenta: storefront=${account.id} idioma=$language")
            tokenManager.saveAccountRegion(storefront = account.id, language = language)
        }
        return sessionExpired
    }

    private fun locale(): String =
        languageOverrideProvider()?.takeIf { it.isNotBlank() }
            ?: tokenManager.getCredentials().language.ifBlank { Locale.getDefault().toLanguageTag() }

    suspend fun loadMySocialProfile(): UserAccountProfile? {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession) return null
        val body = runCatching {
            ampApiService.getMySocialProfileRaw(locale = locale()).string()
        }.onFailure { Log.w(TAG, "loadMySocialProfile error", it) }.getOrNull() ?: return null
        return SocialProfileDetail.parseMyAccountProfile(body)
    }

    /** Consulta eventos / programa en vivo de una estación (`ra.*`) para mostrar la canción/show al instante. */
    suspend fun loadLiveStationShowInfo(stationId: String): Pair<String, String?>? {
        if (stationId.isBlank()) return null
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val body = runCatching {
            ampApiService.getStationDetailsRaw(sf, stationId, locale = locale()).string()
        }.getOrNull() ?: return null
        return runCatching {
            val root = JSONObject(body)
            val dataArr = root.optJSONArray("data")
            val stationObj = dataArr?.optJSONObject(0) ?: return@runCatching null
            val rels = stationObj.optJSONObject("relationships")
            val resources = root.optJSONObject("resources")
            val nowMs = System.currentTimeMillis()
            val eventRefs = rels?.optJSONObject("events")?.optJSONArray("data")
            val eventsMap = resources?.optJSONObject("station-events")
            if (eventRefs != null && eventsMap != null) {
                var activeEvent: JSONObject? = null
                for (i in 0 until eventRefs.length()) {
                    val eid = eventRefs.optJSONObject(i)?.optString("id").orEmpty()
                    val ev = eventsMap.optJSONObject(eid) ?: continue
                    val ea = ev.optJSONObject("attributes") ?: continue
                    val startMs = AlbumDetail.parseReleaseEpochMillis(ea.optString("startTime"))
                    val endMs = AlbumDetail.parseReleaseEpochMillis(ea.optString("endTime"))
                    if (startMs != null && endMs != null && nowMs in startMs..endMs) {
                        activeEvent = ev
                        break
                    }
                    if (activeEvent == null) activeEvent = ev
                }
                val ea = activeEvent?.optJSONObject("attributes")
                val evTitle = ea?.optString("title")?.takeIf { it.isNotBlank() }
                    ?: ea?.optString("name")?.takeIf { it.isNotBlank() }
                if (evTitle != null) {
                    val art = ea?.optJSONObject("artwork")?.optString("url")
                        ?.replace("{w}", "1000")?.replace("{h}", "1000")
                        ?.replace("{c}", "cc")?.replace("{f}", "jpg")
                    return@runCatching evTitle to art
                }
            }
            val showRef = rels?.optJSONObject("radio-show")?.optJSONArray("data")?.optJSONObject(0)
            val showId = showRef?.optString("id").orEmpty()
            val showObj = resources?.optJSONObject("radio-shows")?.optJSONObject(showId)
            val showName = showObj?.optJSONObject("attributes")?.optString("name")?.takeIf { it.isNotBlank() }
                ?: stationObj.optJSONObject("attributes")?.optString("currentEpisodeName")?.takeIf { it.isNotBlank() }
            val showArt = showObj?.optJSONObject("attributes")?.optJSONObject("artwork")?.optString("url")
                ?.replace("{w}", "1000")?.replace("{h}", "1000")
                ?.replace("{c}", "cc")?.replace("{f}", "jpg")
            showName?.let { it to showArt }
        }.getOrNull()
    }

    suspend fun loadHomeFeed(): HomeFeedResult = coroutineScope {
        val initialCreds = tokenManager.getCredentials()
        val localeReady = initialCreds.hasWebSession && initialCreds.language.isNotBlank()
        // Si ya tenemos tienda e idioma guardados, sincronizamos cuenta EN PARALELO con `listen-now` para no bloquear el arranque.
        val syncDeferred = if (localeReady) async { syncAccount() } else null
        val preSessionExpired = if (!localeReady) syncAccount() else false

        val creds = if (localeReady) initialCreds else tokenManager.getCredentials()
        val localeTag = locale()
        var webLoginRequired = preSessionExpired || !creds.hasWebSession

        if (creds.hasWebSession) {
            val ampResult = runCatching { fetchFullAmpHome(creds.storefront, localeTag) }
                .onFailure { err ->
                    Log.w(TAG, "amp-api listen-now failed", err)
                    if (err.isAuthFailure()) {
                        tokenManager.clearWebUserToken()
                        webLoginRequired = true
                    }
                }
                .getOrNull()

            if (syncDeferred?.await() == true) webLoginRequired = true

            if (!ampResult.isNullOrEmpty()) {
                ampResult.forEachIndexed { i, sec ->
                    Log.d(TAG, "AmpHome[$i] ${sec.style} '${sec.subtitle.orEmpty()} ${sec.title}' items=${sec.items.size}")
                }
                runCatching {
                    val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
                    writeDiskCache("feed_home_v1_$sf", snapshotJson.encodeToString(ampResult))
                }
                return@coroutineScope HomeFeedResult(mode = ConnectionMode.PERSONALIZED, sections = ampResult)
            }
        } else {
            syncDeferred?.await()
        }

        loadFromFallback(tokenManager.getCredentials(), localeTag, webLoginRequired)
    }

    /**
     * Pestaña "Novedades" / "New":
     * `/v1/editorial/{storefront}/groupings?name=music` (banners 316 horizontales, lanzamientos,
     * playlists, canciones 327, mixes, videos, próximos estrenos y al final "Más para explorar" 391).
     */
    suspend fun loadNewFeed(): List<HomeSection> {
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val body = ampApiService.getEditorialGroupingsRaw(
            storefront = sf,
            locale = locale()
        ).string()
        val parsed = WebHomeParser.parseGroupings(body).distinctBy { it.id }
        parsed.forEachIndexed { idx, s ->
            Log.d(TAG, "New[$idx] ${s.style} '${s.title}' items=${s.items.size}")
        }
        if (parsed.isNotEmpty()) {
            runCatching { writeDiskCache("feed_new_v1_$sf", snapshotJson.encodeToString(parsed)) }
        }
        return parsed
    }

    /**
     * Pestaña "Radio": equivalente a Apple Music Radio / `groupings?name=radio`
     * (emisoras en vivo Apple Music 1/Hits/Country/Música Uno/Club/Chill, "Al aire ahora",
     * episodios recientes, entrevistas, sessions, mixes, programas, emisoras locales/top y géneros).
     */
    suspend fun loadRadioFeed(): List<HomeSection> {
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val body = ampApiService.getEditorialGroupingsRaw(
            storefront = sf,
            name = "radio",
            locale = locale(),
            extendStations = "airDate,airTime,supportsAirTimeUpdates,editorialArtwork",
            includeStations = "radio-show,events"
        ).string()
        val parsed = WebHomeParser.parseGroupings(body, filterRadioTalkStations = false).distinctBy { it.id }
        parsed.forEachIndexed { idx, s ->
            Log.d(TAG, "Radio[$idx] ${s.style} '${s.title}' items=${s.items.size}")
        }
        if (parsed.isNotEmpty()) {
            runCatching { writeDiskCache("feed_radio_v1_$sf", snapshotJson.encodeToString(parsed)) }
        }
        return parsed
    }

    /**
     * Carga una subpágina de "Novedades" o "Radio" al tocar:
     *  - `apple-curators` / `curators` (playlists o grouping de programas de radio)
     *  - Una categoría con `fcId=...` (`/v1/editorial/{storefront}/rooms/{fcId}/contents`)
     *  - Un género o vista `viewGrouping?id=...` (incl. Audio espacial `188750`)
     *  - "Charts" (`viewTop`)
     */
    suspend fun loadEditorialSubpage(linkItem: HomeItem): List<HomeSection> {
        val url = linkItem.url.orEmpty()
        val itemType = linkItem.type.lowercase()
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()

        if (itemType == "apple-curators" || itemType == "curators") {
            val curatorType = if (itemType == "apple-curators") "apple-curators" else "curators"
            val playlistsBody = runCatching {
                ampApiService.getCuratorPlaylistsRaw(sf, curatorType, linkItem.id, locale = localeTag).string()
            }.getOrNull()
            val fromPlaylists = playlistsBody?.let { WebHomeParser.parseCuratorSections(it, linkItem.title) }.orEmpty()
            if (fromPlaylists.isNotEmpty()) return fromPlaylists

            val curatorBody = runCatching {
                ampApiService.getCuratorRaw(sf, curatorType, linkItem.id, locale = localeTag).string()
            }.getOrNull()
            if (curatorBody != null) {
                val fromCurator = WebHomeParser.parseCuratorSections(curatorBody, linkItem.title)
                if (fromCurator.isNotEmpty()) return fromCurator
                val groupingId = WebHomeParser.extractCuratorGroupingId(curatorBody)
                if (!groupingId.isNullOrBlank()) {
                    val grpBody = runCatching {
                        ampApiService.getEditorialGroupingByIdRaw(
                            storefront = sf,
                            groupingId = groupingId,
                            locale = localeTag
                        ).string()
                    }.getOrNull()
                    if (grpBody != null) {
                        val parsed = WebHomeParser.parseGroupings(grpBody, filterRadioTalkStations = false)
                            .distinctBy { it.id }
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            }
            return emptyList()
        }

        val groupingId = Regex("[?&]id=(\\d+)").find(url)?.groupValues?.getOrNull(1)
        val roomFcId = Regex("[?&]fcId=([\\w-]+)").find(url)?.groupValues?.getOrNull(1)
        val isCharts = url.contains("viewTop", ignoreCase = true) ||
            linkItem.title.containsAny("chart", "éxitos", "listas")

        return when {
            isCharts -> {
                val genreId = Regex("[?&]genreId=(\\d+)").find(url)?.groupValues?.getOrNull(1)
                    ?.takeIf { it != "34" }
                val body = ampApiService.getCatalogChartsFullRaw(
                    storefront = sf,
                    genreId = genreId,
                    locale = localeTag
                ).string()
                WebHomeParser.parseCatalogCharts(body)
            }
            roomFcId != null -> {
                val roomBody = runCatching {
                    ampApiService.getEditorialRoomContentsRaw(sf, roomFcId, locale = localeTag).string()
                }.getOrNull() ?: runCatching {
                    ampApiService.getEditorialRoomRaw(sf, roomFcId, locale = localeTag).string()
                }.getOrNull()
                if (roomBody != null) {
                    val collected = mutableListOf<HomeItem>()
                    var (items, next) = WebHomeParser.parseEditorialRoom(roomBody, linkItem.title)
                    collected += items
                    var pages = 0
                    while (!next.isNullOrBlank() && pages < 3) {
                        val nextBody = runCatching {
                            ampApiService.getByUrlRaw(next!!.removePrefix("/")).string()
                        }.getOrNull() ?: break
                        val pageParsed = WebHomeParser.parseEditorialRoom(nextBody, linkItem.title)
                        collected += pageParsed.first
                        next = pageParsed.second
                        pages++
                    }
                    val distinctItems = collected.distinctBy { "${it.type}_${it.id}" }
                    if (distinctItems.isNotEmpty()) {
                        return listOf(
                            HomeSection(
                                id = "room_$roomFcId",
                                title = linkItem.title,
                                hasChevron = distinctItems.size > 4,
                                style = HomeSectionStyle.SQUARE_CAROUSEL,
                                items = distinctItems,
                                canLoadMore = distinctItems.size > 4
                            )
                        )
                    }
                }
                if (linkItem.title.containsAny("género", "genre")) {
                    val grpBody = ampApiService.getEditorialGroupingsRaw(storefront = sf, locale = localeTag).string()
                    WebHomeParser.parseGenreLinksFromGroupings(grpBody)
                } else emptyList()
            }
            url.contains("viewGrouping", ignoreCase = true) && groupingId != null -> {
                val body = ampApiService.getEditorialGroupingByIdRaw(
                    storefront = sf,
                    groupingId = groupingId,
                    locale = localeTag
                ).string()
                WebHomeParser.parseGroupings(body, filterRadioTalkStations = false).distinctBy { it.id }
            }
            else -> emptyList()
        }
    }

    private suspend fun fetchFullAmpHome(storefront: String, localeTag: String): List<HomeSection> = coroutineScope {
        val badgingDeferred = async {
            runCatching {
                WebHomeParser.parseBadgingMap(ampApiService.getSocialBadgingMapRaw().string())
            }.getOrDefault(emptyMap())
        }

        val allSections = mutableListOf<HomeSection>()
        val firstBody = ampApiService.getListenNowRaw(
            timezone = currentTimezoneOffset(),
            locale = localeTag
        ).string()
        var page = WebHomeParser.parsePage(firstBody, strings.moreLike)
        Log.d(TAG, "RawPage[0] next=${page.nextUrl} shelves=${page.debugSummary}")
        allSections += page.sections

        var pageCount = 1
        while (!page.nextUrl.isNullOrBlank() && pageCount < MAX_HOME_PAGES) {
            val nextPath = page.nextUrl!!.removePrefix("/")
            val nextBody = runCatching { ampApiService.getByUrlRaw(nextPath).string() }
                .onFailure { Log.w(TAG, "amp-api next page failed: $nextPath", it) }
                .getOrNull() ?: break
            page = WebHomeParser.parsePage(nextBody, strings.moreLike)
            Log.d(TAG, "RawPage[$pageCount] next=${page.nextUrl} shelves=${page.debugSummary}")
            allSections += page.sections
            pageCount++
        }

        val distinct = allSections.distinctBy { it.id }
        distinct.forEachIndexed { idx, s ->
            Log.d(
                TAG,
                "AmpHome[$idx] id=${s.id} style=${s.style} title='${s.title}' count=${s.items.size} sample=[${s.items.take(3).joinToString { "${it.type}:${it.id}:${it.title}" }}]"
            )
        }
        val badgingMap = badgingDeferred.await()
        attachFriendBadges(distinct, badgingMap, storefront, localeTag)
    }

    /**
     * Inyecta badges sociales de amigos que escucharon cada ítem:
     * cruza el `badgingMap` con los ítems del Home y descarga las fotos de perfil de esos amigos.
     */
    private suspend fun attachFriendBadges(
        sections: List<HomeSection>,
        badgingMap: Map<String, List<String>>,
        storefront: String,
        localeTag: String
    ): List<HomeSection> {
        if (badgingMap.isEmpty()) return sections

        val neededFriendIds = LinkedHashSet<String>()
        sections.forEach { section ->
            section.items.forEach { item ->
                val matched = badgingMap[item.id] ?: item.catalogId?.let { badgingMap[it] }
                matched?.forEach { friendId ->
                    if (!cachedProfiles.containsKey(friendId)) neededFriendIds.add(friendId)
                }
            }
        }

        if (neededFriendIds.isNotEmpty()) {
            fetchSocialProfilesBatch(storefront, neededFriendIds.toList(), localeTag)
        }

        return sections.map { section ->
            var changed = false
            val updatedItems = section.items.map { item ->
                val friendIds = (badgingMap[item.id] ?: item.catalogId?.let { badgingMap[it] }).orEmpty()
                if (friendIds.isEmpty()) {
                    item
                } else {
                    val badges = friendIds.mapNotNull { cachedProfiles[it] }
                    if (badges.isNotEmpty()) {
                        changed = true
                        item.copy(friendBadges = badges)
                    } else item
                }
            }
            if (changed) section.copy(items = updatedItems) else section
        }
    }

    private suspend fun fetchSocialProfilesBatch(
        storefront: String,
        ids: List<String>,
        localeTag: String
    ) = coroutineScope {
        ids.chunked(25).map { chunk ->
            async {
                val batchOk = runCatching {
                    val body = ampApiService.getSocialProfilesBatchRaw(
                        storefront = storefront,
                        ids = chunk.joinToString(","),
                        locale = localeTag
                    ).string()
                    val parsed = WebHomeParser.parseSocialProfiles(body)
                    synchronized(cachedProfiles) { cachedProfiles.putAll(parsed) }
                    parsed.isNotEmpty()
                }.getOrDefault(false)

                if (!batchOk) {
                    chunk.take(12).forEach { id ->
                        runCatching {
                            val body = ampApiService.getSocialProfileRaw(storefront, id, locale = localeTag).string()
                            val parsed = WebHomeParser.parseSocialProfiles(body)
                            synchronized(cachedProfiles) { cachedProfiles.putAll(parsed) }
                        }
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Refresco de "Escuchado recientemente" usando el mismo estante `web_7-` de `listen-now`,
     * preservando badges y actualizando solo si realmente cambiaron los elementos.
     */
    suspend fun refreshRecentlyPlayed(sections: List<HomeSection>): List<HomeSection>? {
        val index = sections.indexOfFirst {
            it.id.startsWith("web_$KIND_RECENT") || it.id.startsWith("rec_$KIND_RECENT")
        }
        if (index < 0) return null
        val current = sections[index]
        val creds = tokenManager.getCredentials()
        val localeTag = locale()
        val rowLimit = current.items.size.takeIf { it > 0 } ?: RECENT_ROW_SIZE

        val freshRaw = if (current.id.startsWith("web_") && creds.hasWebSession) {
            val recId = current.id.removePrefix("web_")
            runCatching {
                val body = ampApiService.getSeeAllRaw(
                    recommendationId = recId,
                    limitContents = rowLimit,
                    locale = localeTag,
                    timezone = currentTimezoneOffset()
                ).string()
                WebHomeParser.parseSeeAllPage(body, strings.moreLike).first.take(rowLimit)
            }.getOrNull().orEmpty()
        } else if (creds.hasDeveloperToken && creds.hasUserToken) {
            runCatching {
                apiService.getRecentlyPlayed(locale = localeTag, limit = rowLimit).data.map { it.toHomeItem() }
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }

        if (freshRaw.isEmpty() || freshRaw.map { it.id } == current.items.map { it.id }) return null
        val oldBadges = current.items.associate { it.id to it.friendBadges }
        val fresh = freshRaw.map { item ->
            val existingBadges = oldBadges[item.id]
            if (!existingBadges.isNullOrEmpty() && item.friendBadges.isEmpty()) item.copy(friendBadges = existingBadges)
            else item
        }
        Log.d(TAG, "Escuchado recientemente actualizado: ${fresh.firstOrNull()?.title}")
        return sections.toMutableList().also { it[index] = current.copy(items = fresh) }
    }

    /**
     * "Ver todo": devuelve TODOS los elementos de una sección (hasta 100+ siguiendo `next`).
     */
    suspend fun loadFullSection(section: HomeSection): List<HomeItem> {
        if (!section.canLoadMore) return section.items
        if (section.id.startsWith("grp_") || section.id.startsWith("chart_")) {
            return section.items
        }
        val creds = tokenManager.getCredentials()
        val localeTag = locale()

        val full: List<HomeItem> = when {
            section.id.startsWith("web_") && creds.hasWebSession -> {
                val recId = section.id.removePrefix("web_")
                runCatching {
                    val collected = mutableListOf<HomeItem>()
                    val firstBody = ampApiService.getSeeAllRaw(
                        recommendationId = recId,
                        locale = localeTag,
                        timezone = currentTimezoneOffset()
                    ).string()
                    var (items, next) = WebHomeParser.parseSeeAllPage(firstBody, strings.moreLike)
                    collected += items
                    var pages = 1
                    while (!next.isNullOrBlank() && pages < 4) {
                        val nextBody = runCatching {
                            ampApiService.getByUrlRaw(next!!.removePrefix("/")).string()
                        }.getOrNull() ?: break
                        val parsed = WebHomeParser.parseSeeAllPage(nextBody, strings.moreLike)
                        collected += parsed.first
                        next = parsed.second
                        pages++
                    }
                    collected.distinctBy { it.id }
                }.onFailure { Log.w(TAG, "see-all error", it) }
                    .getOrDefault(emptyList())
            }
            section.id.startsWith("rec_$KIND_RECENT") -> {
                val all = mutableListOf<MediaResource>()
                for (offset in 0 until 50 step 10) {
                    val page = runCatching {
                        apiService.getRecentlyPlayed(locale = localeTag, limit = 10, offset = offset).data
                    }.getOrNull().orEmpty()
                    all += page
                    if (page.size < 10) break
                }
                all.map { it.toHomeItem() }
            }
            else -> emptyList()
        }
        Log.d(TAG, "see-all '${section.title}': ${full.size} (home ${section.items.size})")
        if (full.isEmpty()) return section.items
        val ids = full.mapTo(HashSet()) { it.id }
        return full + section.items.filterNot { it.id in ids }
    }

    /** Historial de canciones escuchadas (`/v1/me/recent/played/tracks`). */
    suspend fun loadListeningHistory(): List<HomeItem> {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession) return emptyList()
        val body = runCatching {
            ampApiService.getRecentPlayedTracksRaw(locale = locale(), limit = 40).string()
        }.getOrNull() ?: return emptyList()
        return WebHomeParser.parseRecentList(body)
    }

    /** Carga detalle de Álbum (catálogo o librería) o Playlist (catálogo o librería, paginada) con caché en memoria + disco. */
    suspend fun loadMediaDetail(item: HomeItem, forceRefresh: Boolean = false): AlbumDetail? {
        val creds = tokenManager.getCredentials()
        val localeTag = locale()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val isLibrary = item.type.startsWith("library-") || item.id.startsWith("l.") || item.id.startsWith("p.")
        val isPlaylist = item.type.contains("playlist") || item.id.startsWith("pl.") || item.id.startsWith("p.")
        val cacheKey = "${sf}_${item.id}"
        val ttlMs = if (isLibrary) LIBRARY_DETAIL_CACHE_TTL_MS else CATALOG_DETAIL_CACHE_TTL_MS

        if (!forceRefresh) {
            detailCache[cacheKey]?.let { entry ->
                if (System.currentTimeMillis() - entry.cachedAtMs <= ttlMs) {
                    return entry.value
                }
            }
            readDiskCache("detail_v3_$cacheKey", ttlMs)?.let { cachedJson ->
                runCatching { AlbumDetail.parse(cachedJson) }.getOrNull()?.let { parsed ->
                    detailCache[cacheKey] = CachedEntry(parsed, System.currentTimeMillis(), isLibrary = isLibrary)
                    return parsed
                }
            }
        }

        val body = runCatching {
            when {
                isPlaylist && isLibrary -> ampApiService.getLibraryPlaylistRaw(item.id, locale = localeTag).string()
                isPlaylist -> ampApiService.getCatalogPlaylistRaw(sf, item.id, locale = localeTag).string()
                isLibrary -> ampApiService.getLibraryAlbumRaw(item.id, locale = localeTag).string()
                else -> ampApiService.getAlbumRaw(sf, item.id, locale = localeTag).string()
            }
        }.getOrNull() ?: run {
            // Fallback por si el tipo vino ambiguo
            if (!isPlaylist && creds.hasDeveloperToken) {
                runCatching { apiService.getAlbumRaw(sf, item.id, locale = localeTag).string() }.getOrNull()
            } else null
        } ?: return detailCache[cacheKey]?.value

        var detail = AlbumDetail.parse(body) ?: return null
        val catId = detail.catalogId?.takeIf { it.isNotBlank() && !it.startsWith("l.") && !it.startsWith("p.") }
        // Solo pedir vistas de catálogo extra en álbumes de librería (en playlists evitar retrasar la apertura)
        if (!isPlaylist && detail.relatedSections.isEmpty() && isLibrary && catId != null) {
            val catBody = readDiskCache("catviews_${sf}_$catId", CATALOG_DETAIL_CACHE_TTL_MS)
                ?: runCatching {
                    ampApiService.getAlbumRaw(sf, catId, locale = localeTag).string()
                }.getOrNull()?.also { writeDiskCache("catviews_${sf}_$catId", it) }
            if (catBody != null) {
                val catDetail = AlbumDetail.parse(catBody)
                detail = detail.copy(
                    relatedSections = AlbumDetail.parseRelatedViews(catBody, detail.artistName),
                    primaryArtistId = detail.primaryArtistId ?: catDetail?.primaryArtistId,
                    curatorItem = detail.curatorItem ?: catDetail?.curatorItem
                )
            }
        }
        // Abrir playlists al instante con el primer bloque (hasta 100 pistas) en lugar de bloquear 4-5 rondas de red
        writeDiskCache("detail_v3_$cacheKey", body)
        detailCache[cacheKey] = CachedEntry(detail, System.currentTimeMillis(), isLibrary = isLibrary)
        return detail
    }

    suspend fun loadAlbum(albumId: String): AlbumDetail? =
        loadMediaDetail(HomeItem(id = albumId, type = "albums", title = "", subtitle = "", artworkUrl = null))

    private val artistCache = ConcurrentHashMap<String, ArtistDetail>()

    /** Obtiene la foto de perfil de un artista (instantáneo si ya está en caché). */
    suspend fun loadArtistArtwork(artistId: String): String? {
        if (artistId.isBlank()) return null
        artistCache[artistId]?.artworkUrl?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching { loadArtist(artistId)?.artworkUrl?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    /** Carga la página de artista (resolviendo también `library-artists` `r.*`). */
    suspend fun loadArtist(artistId: String): ArtistDetail? {
        artistCache[artistId]?.let { return it }
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()
        readDiskCache("artist_v2_${sf}_$artistId", ARTIST_CACHE_TTL_MS)?.let { cachedBody ->
            ArtistDetail.parse(cachedBody)?.let { parsed ->
                artistCache[artistId] = parsed
                return parsed
            }
        }
        if (artistId.startsWith("r.")) {
            val libBody = runCatching {
                ampApiService.getLibraryArtistRaw(artistId, locale = localeTag).string()
            }.onFailure { Log.w(TAG, "library artist error", it) }.getOrNull()
            if (libBody != null) {
                val (catalogId, localFallback) = WebHomeParser.extractLibraryArtistDetail(libBody, "")
                if (!catalogId.isNullOrBlank()) {
                    artistCache[catalogId]?.let { cached ->
                        artistCache[artistId] = cached
                        return cached
                    }
                    val catBody = readDiskCache("artist_v2_${sf}_$catalogId", ARTIST_CACHE_TTL_MS)
                        ?: runCatching {
                            ampApiService.getArtistRaw(sf, catalogId, locale = localeTag).string()
                        }.getOrNull()?.also { writeDiskCache("artist_v2_${sf}_$catalogId", it) }
                    val parsed = catBody?.let { ArtistDetail.parse(it) }
                    if (parsed != null) {
                        artistCache[catalogId] = parsed
                        artistCache[artistId] = parsed
                        return parsed
                    }
                }
                if (localFallback != null) {
                    artistCache[artistId] = localFallback
                    return localFallback
                }
            }
            return null
        }

        val body = runCatching {
            ampApiService.getArtistRaw(sf, artistId, locale = localeTag).string()
        }.onFailure { Log.w(TAG, "artist error", it) }.getOrNull() ?: return null
        writeDiskCache("artist_v2_${sf}_$artistId", body)
        return ArtistDetail.parse(body)?.also { artistCache[artistId] = it }
    }

    /** Categorías visuales oficiales de Apple Music para la vista inicial de Buscar. */
    suspend fun loadSearchCategories(): List<HomeItem> {
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()
        val fromLanding = runCatching {
            val landingBody = ampApiService.getSearchLandingRaw(storefront = sf, locale = localeTag).string()
            WebHomeParser.parseSearchLandingCategories(landingBody)
        }.onFailure { Log.w(TAG, "search-landing error", it) }
            .getOrDefault(emptyList())
        if (fromLanding.isNotEmpty()) {
            runCatching { writeDiskCache("feed_cats_v1_$sf", snapshotJson.encodeToString(fromLanding)) }
            return fromLanding
        }

        val body = runCatching {
            ampApiService.getEditorialGroupingsRaw(storefront = sf, locale = localeTag).string()
        }.onFailure { Log.w(TAG, "search categories error", it) }.getOrNull() ?: return emptyList()
        val parsed = WebHomeParser.extractBrowseCategoriesFromGroupings(body)
        if (parsed.isNotEmpty()) {
            runCatching { writeDiskCache("feed_cats_v1_$sf", snapshotJson.encodeToString(parsed)) }
        }
        return parsed
    }

    suspend fun searchSuggestions(term: String): SearchSuggestionsResult {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return SearchSuggestionsResult()
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val body = ampApiService.searchSuggestionsRaw(
            storefront = sf,
            term = trimmed,
            locale = locale()
        ).string()
        return WebHomeParser.parseSearchSuggestions(body)
    }

    suspend fun searchCatalog(term: String): List<HomeSection> = coroutineScope {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return@coroutineScope emptyList()
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()

        val catalogDef = async {
            ampApiService.searchCatalogRaw(
                storefront = sf,
                term = trimmed,
                locale = localeTag
            ).string()
        }
        val socialDef = async {
            if (!creds.hasWebSession) null
            else runCatching {
                ampApiService.searchSocialRaw(
                    storefront = sf,
                    term = trimmed,
                    locale = localeTag
                ).string()
            }.getOrNull()
        }

        val catalogSections = WebHomeParser.parseSearchResults(catalogDef.await())
        val socialSections = socialDef.await()?.let { WebHomeParser.parseSearchResults(it) }.orEmpty()

        val merged = (catalogSections + socialSections)
            .groupBy { it.id }
            .map { (_, group) ->
                if (group.size == 1) group.first()
                else group.first().copy(
                    items = group.flatMap { it.items }.distinctBy { "${it.type}_${it.id}" }
                )
            }

        merged.map { sec ->
            if (sec.id == "search_artists" || sec.id == "search_library-artists") {
                sec.copy(items = enrichArtistsArtwork(sec.items, sf, localeTag))
            } else sec
        }
    }

    suspend fun searchLibrary(term: String): List<HomeSection> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()
        val body = ampApiService.searchLibraryRaw(
            term = trimmed,
            locale = localeTag
        ).string()
        return WebHomeParser.parseSearchResults(body).map { sec ->
            if (sec.style == HomeSectionStyle.ARTIST_CIRCLES) {
                sec.copy(items = enrichArtistsArtwork(sec.items, sf, localeTag))
            } else sec
        }
    }

    private fun readLocalPins(): List<HomeItem> {
        val raw = readDiskCache("local_pins_v1", Long.MAX_VALUE) ?: return emptyList()
        return runCatching { snapshotJson.decodeFromString<List<HomeItem>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveLocalPins(items: List<HomeItem>) {
        runCatching { writeDiskCache("local_pins_v1", snapshotJson.encodeToString(items.take(6))) }
    }

    /** Fija o desfija un elemento en Biblioteca (actualización local inmediata + sincronización con `/v1/me/library/pins`). */
    suspend fun setLibraryPin(item: HomeItem, pin: Boolean): List<HomeItem> {
        val keys = setOfNotNull(item.id.takeIf { it.isNotBlank() }, item.catalogId?.takeIf { it.isNotBlank() })
        val current = readLocalPins()
        val updated = if (pin) {
            val cleaned = current.filterNot { it.id in keys || (it.catalogId != null && it.catalogId in keys) }
            (listOf(item) + cleaned).take(6)
        } else {
            current.filterNot { it.id in keys || (it.catalogId != null && it.catalogId in keys) }
        }
        saveLocalPins(updated)

        val creds = tokenManager.getCredentials()
        if (creds.hasWebSession && item.id.isNotBlank()) {
            val resType = normalizedResourceType(item)
            val query = mapOf("ids[$resType]" to item.id)
            runCatching {
                if (pin) ampApiService.addLibraryPinRaw(query) else ampApiService.removeLibraryPinRaw(query)
            }
        }
        return updated
    }

    /** Lee instantáneamente el último snapshot en disco de Biblioteca para abrir sin esperas. */
    fun readCachedLibraryOverview(): LibraryOverviewResult? {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession) return null
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val raw = readDiskCache("feed_lib_v2_$sf", Long.MAX_VALUE)
            ?: readDiskCache("feed_lib_v1_$sf", Long.MAX_VALUE)
            ?: return null
        val decoded = runCatching { snapshotJson.decodeFromString<LibraryOverviewResult>(raw) }.getOrNull() ?: return null
        val localPins = readLocalPins()
        val mergedPins = (decoded.pinned + localPins).distinctBy { it.catalogId ?: it.id }.take(6)
        return decoded.copy(pinned = mergedPins)
    }

    suspend fun loadLibraryOverview(forceRefresh: Boolean = false): LibraryOverviewResult = coroutineScope {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession) return@coroutineScope LibraryOverviewResult()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        if (forceRefresh) {
            deleteDiskCacheByPrefix("feed_lib_v2_$sf")
            deleteDiskCacheByPrefix("feed_lib_v1_$sf")
        }
        val localeTag = locale()

        val pinsDef = async {
            val remote = runCatching {
                val body = ampApiService.getLibraryPinsRaw(locale = localeTag).string()
                val (parsed, _) = WebHomeParser.parseLibraryList(body)
                if (parsed.isNotEmpty()) enrichArtistsArtwork(parsed, sf, localeTag) else emptyList()
            }.onFailure { Log.d(TAG, "lib pins unavailable: ${it.message}") }.getOrDefault(emptyList())
            val local = readLocalPins()
            val merged = (remote + local).distinctBy { it.catalogId ?: it.id }.take(6)
            if (remote.isNotEmpty()) saveLocalPins(merged)
            merged
        }
        val recentDef = async {
            runCatching {
                val (items, next) = WebHomeParser.parseLibraryList(
                    ampApiService.getLibraryRecentlyAddedRaw(locale = localeTag).string()
                )
                LibraryCategoryPage(items, next)
            }.onFailure { Log.w(TAG, "lib recent error", it) }.getOrDefault(LibraryCategoryPage())
        }
        val playlistsDef = async {
            runCatching {
                val (items, next) = WebHomeParser.parseLibraryList(
                    ampApiService.getLibraryPlaylistsRaw(locale = localeTag).string()
                )
                LibraryCategoryPage(items, next)
            }.onFailure { Log.w(TAG, "lib playlists error", it) }.getOrDefault(LibraryCategoryPage())
        }
        val albumsDef = async {
            runCatching {
                val (items, next) = WebHomeParser.parseLibraryList(
                    ampApiService.getLibraryAlbumsRaw(locale = localeTag).string()
                )
                LibraryCategoryPage(items, next)
            }.onFailure { Log.w(TAG, "lib albums error", it) }.getOrDefault(LibraryCategoryPage())
        }
        val artistsDef = async {
            runCatching {
                val (items, next) = WebHomeParser.parseLibraryList(
                    ampApiService.getLibraryArtistsRaw(locale = localeTag).string()
                )
                LibraryCategoryPage(enrichArtistsArtwork(items.take(24), sf, localeTag) + items.drop(24), next)
            }.onFailure { Log.w(TAG, "lib artists error", it) }.getOrDefault(LibraryCategoryPage())
        }
        val songsDef = async {
            runCatching {
                val (items, next) = WebHomeParser.parseLibraryList(
                    ampApiService.getLibrarySongsRaw(locale = localeTag).string()
                )
                LibraryCategoryPage(items, next)
            }.onFailure { Log.w(TAG, "lib songs error", it) }.getOrDefault(LibraryCategoryPage())
        }

        val res = LibraryOverviewResult(
            pinned = pinsDef.await(),
            recentlyAdded = recentDef.await(),
            playlists = playlistsDef.await(),
            albums = albumsDef.await(),
            artists = artistsDef.await(),
            songs = songsDef.await()
        )
        if (res.pinned.isNotEmpty() || res.recentlyAdded.items.isNotEmpty() || res.playlists.items.isNotEmpty() || res.albums.items.isNotEmpty()) {
            runCatching { writeDiskCache("feed_lib_v2_$sf", snapshotJson.encodeToString(res)) }
        }
        res
    }

    suspend fun loadMoreLibraryPage(nextUrl: String): LibraryCategoryPage {
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()
        val body = ampApiService.getByUrlRaw(nextUrl.removePrefix("/")).string()
        val (items, next) = WebHomeParser.parseLibraryList(body)
        return LibraryCategoryPage(
            items = enrichArtistsArtwork(items, sf, localeTag),
            nextUrl = next
        )
    }

    /** Rellena fotos de perfil faltantes para artistas / library-artists consultando `/v1/catalog/{sf}/artists?ids=...`. */
    private suspend fun enrichArtistsArtwork(
        items: List<HomeItem>,
        storefront: String,
        localeTag: String
    ): List<HomeItem> = coroutineScope {
        val missing = items.filter { it.type.contains("artist", ignoreCase = true) && it.artworkUrl.isNullOrBlank() }
        if (missing.isEmpty()) return@coroutineScope items

        val catIds = missing.mapNotNull { item ->
            item.catalogId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?: item.id.takeIf { it.all(Char::isDigit) }
        }.distinct()

        val artMap = HashMap<String, Pair<String?, String?>>()
        if (catIds.isNotEmpty()) {
            catIds.chunked(25).map { chunk ->
                async {
                    runCatching {
                        val body = ampApiService.getCatalogArtistsBatchRaw(
                            storefront = storefront,
                            ids = chunk.joinToString(","),
                            locale = localeTag
                        ).string()
                        WebHomeParser.parseArtistArtworkBatch(body)
                    }.getOrDefault(emptyMap())
                }
            }.awaitAll().forEach { artMap.putAll(it) }
        }

        // Respaldo para library-artists sin catalogId en la lista: pedir `/v1/me/library/artists/{id}?include=catalog,albums`
        val unresolvedLib = missing.filter {
            it.type == "library-artists" && it.catalogId.isNullOrBlank()
        }.take(12)
        val libFallbackArt = HashMap<String, Pair<String?, String?>>()
        if (unresolvedLib.isNotEmpty()) {
            unresolvedLib.map { item ->
                async {
                    runCatching {
                        val body = ampApiService.getLibraryArtistRaw(item.id, locale = localeTag).string()
                        val (catId, detail) = WebHomeParser.extractLibraryArtistDetail(body, item.title)
                        val resolvedCat = catId?.takeIf { it.all(Char::isDigit) }
                        if (resolvedCat != null) {
                            val catArt = artMap[resolvedCat] ?: runCatching {
                                WebHomeParser.parseArtistArtworkBatch(
                                    ampApiService.getCatalogArtistsBatchRaw(storefront, resolvedCat, locale = localeTag).string()
                                )[resolvedCat]
                            }.getOrNull()
                            item.id to (catArt?.first to resolvedCat)
                        } else {
                            item.id to (detail?.artworkUrl to null)
                        }
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().forEach { (id, pair) ->
                if (!pair.first.isNullOrBlank() || !pair.second.isNullOrBlank()) {
                    libFallbackArt[id] = pair
                }
            }
        }

        items.map { item ->
            if (!item.artworkUrl.isNullOrBlank()) return@map item
            val catId = item.catalogId?.takeIf { it.all(Char::isDigit) } ?: item.id.takeIf { it.all(Char::isDigit) }
            val hit = catId?.let { artMap[it] }
            if (hit != null && !hit.first.isNullOrBlank()) {
                item.copy(artworkUrl = hit.first, bgColorHex = item.bgColorHex ?: hit.second)
            } else {
                val libHit = libFallbackArt[item.id]
                if (libHit != null) {
                    item.copy(
                        artworkUrl = libHit.first ?: item.artworkUrl,
                        catalogId = libHit.second ?: item.catalogId
                    )
                } else item
            }
        }
    }

    /** Carga Replay completo (por año o por mes) desde `/v1/me/music-summaries`. */
    suspend fun loadReplay(year: Int? = null, monthOption: ReplayPeriodOption? = null): ReplaySummary? = coroutineScope {
        val localeTag = locale()
        val yearOptions = runCatching {
            val searchBody = ampApiService.getReplaySummariesRaw(period = "year", locale = localeTag).string()
            ReplaySummary.parseAvailableYears(searchBody)
        }.onFailure { Log.w(TAG, "replay year search error", it) }.getOrDefault(emptyList())

        val years = yearOptions.map { it.year }
        val targetYear = monthOption?.year ?: year ?: years.firstOrNull() ?: Year.now().value

        val monthsDeferred = async {
            runCatching {
                val monthSearchBody = ampApiService.getReplaySummariesRaw(
                    period = "month",
                    year = targetYear,
                    with = "emptyMonths",
                    locale = localeTag
                ).string()
                ReplaySummary.parseAvailableMonths(monthSearchBody, targetYear)
            }.onFailure { Log.w(TAG, "replay month search error ($targetYear)", it) }.getOrDefault(emptyList())
        }

        val months = monthsDeferred.await()

        var resolvedMonth = monthOption
        var resolvedYear = targetYear
        var detailBody: String? = null

        if (resolvedMonth != null) {
            detailBody = runCatching {
                ampApiService.getReplayMonthRaw(resolvedMonth.id, locale = localeTag).string()
            }.onFailure { Log.w(TAG, "replay month detail error ${resolvedMonth.id}", it) }.getOrNull()
        } else {
            val summaryId = yearOptions.firstOrNull { it.year == resolvedYear }?.id ?: "year-$resolvedYear"
            detailBody = runCatching {
                ampApiService.getReplayYearRaw(summaryId, locale = localeTag).string()
            }.onFailure { Log.w(TAG, "replay year detail error $summaryId", it) }.getOrNull()

            // Si aún no existe el resumen anual completo (ej. año en curso), usar el mes más reciente o año previo
            if (detailBody == null) {
                for (m in months) {
                    detailBody = runCatching {
                        ampApiService.getReplayMonthRaw(m.id, locale = localeTag).string()
                    }.getOrNull()
                    if (detailBody != null) {
                        resolvedMonth = m
                        break
                    }
                }
            }
            if (detailBody == null) {
                val prevOption = yearOptions.firstOrNull { it.year != resolvedYear }
                if (prevOption != null) {
                    detailBody = runCatching {
                        ampApiService.getReplayYearRaw(prevOption.id, locale = localeTag).string()
                    }.onFailure { Log.w(TAG, "replay fallback year error ${prevOption.id}", it) }.getOrNull()
                    if (detailBody != null) resolvedYear = prevOption.year
                }
            }
        }

        Log.d(TAG, "Replay years=$years target=$resolvedYear month=${resolvedMonth?.id} months=${months.size} ok=${detailBody != null}")
        if (detailBody == null) return@coroutineScope null

        ReplaySummary.parseSummaryDetail(
            body = detailBody,
            availableYears = years,
            availableMonths = months,
            requestedYear = resolvedYear,
            requestedMonth = resolvedMonth?.month
        )
    }

    /** Carga el perfil social de un amigo y sus playlists compartidas. */
    suspend fun loadSocialProfile(profileId: String): SocialProfileDetail? {
        val creds = tokenManager.getCredentials()
        val body = runCatching {
            ampApiService.getSocialProfileRaw(creds.storefront, profileId, locale = locale()).string()
        }.onFailure { Log.w(TAG, "social profile error", it) }.getOrNull() ?: return null
        return SocialProfileDetail.parse(body)
    }

    // -----------------------------------------------------------------------------------------
    // Favoritos (★), Biblioteca, Playlists, Créditos y Letras (Apple Music Web)
    // -----------------------------------------------------------------------------------------

    private fun normalizedResourceType(item: HomeItem): String {
        val t = item.type.lowercase()
        return when {
            item.id.startsWith("ra.a-") && t.contains("artist") -> "artists"
            t.contains("library-song") || (item.id.startsWith("i.") && !t.contains("video")) -> "library-songs"
            t.contains("library-album") || item.id.startsWith("l.") -> "library-albums"
            t.contains("library-playlist") || item.id.startsWith("p.") -> "library-playlists"
            t.contains("library-artist") || item.id.startsWith("r.") -> "library-artists"
            t.contains("library-music-video") -> "library-music-videos"
            t.contains("music-video") -> "music-videos"
            t.contains("song") -> "songs"
            t.contains("album") -> "albums"
            t.contains("playlist") || item.id.startsWith("pl.") -> "playlists"
            t.contains("artist") -> "artists"
            t.contains("station") || item.id.startsWith("ra.") -> "stations"
            else -> t.ifBlank { "songs" }
        }
    }

    private fun isLibraryId(id: String, type: String): Boolean =
        type.startsWith("library-") || id.startsWith("i.") || id.startsWith("l.") || id.startsWith("p.") || id.startsWith("r.")

    private suspend fun resolveCatalogSongId(item: HomeItem): String? {
        item.catalogId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.let { return it }
        val lowerType = item.type.lowercase()
        val isContainerOrStation = lowerType.contains("station") ||
            lowerType.contains("playlist") ||
            lowerType.contains("album") ||
            item.id.startsWith("ra.") ||
            item.id.startsWith("pl.") ||
            item.id.startsWith("p.") ||
            item.id.startsWith("l.")
        if (item.id.all(Char::isDigit) && item.id.isNotBlank() && !isContainerOrStation) return item.id
        if (item.id.startsWith("i.") || lowerType == "library-songs") {
            val body = runCatching {
                ampApiService.getLibrarySongRaw(item.id, locale = locale()).string()
            }.getOrNull()
            val libCat = body?.let { MediaDetailsParser.parseItemStatus(item.id, it)?.catalogId }
            if (!libCat.isNullOrBlank() && libCat.all(Char::isDigit)) return libCat
        }
        // Radio en vivo (`ra.*` / `stations`): resolver la canción actual por título + artista cuando el stream ID3 aún no trajo catalogId.
        if (item.title.isNotBlank() && item.subtitle.isNotBlank()) {
            val sf = tokenManager.getCredentials().storefront.ifBlank { DEFAULT_STOREFRONT }
            val query = "${item.title} ${item.subtitle}".trim()
            val searchBody = runCatching {
                ampApiService.searchCatalogRaw(
                    storefront = sf,
                    term = query,
                    types = "songs",
                    limit = 1,
                    locale = locale()
                ).string()
            }.getOrNull()
            val matchedId = searchBody?.let { raw ->
                runCatching {
                    val root = JSONObject(raw)
                    val songs = root.optJSONObject("results")?.optJSONObject("songs")?.optJSONArray("data")
                    songs?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                }.getOrNull()
            }
            if (matchedId != null) return matchedId
        }
        return if (!isContainerOrStation && item.id.isNotBlank() && !item.id.startsWith("i.")) item.id else null
    }

    private suspend fun resolveCatalogArtistId(item: HomeItem): String? {
        item.catalogId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.let { return it }
        if (item.id.startsWith("ra.a-")) {
            return item.id.removePrefix("ra.a-").takeIf { it.all(Char::isDigit) }
        }
        if (item.id.all(Char::isDigit) && item.id.isNotBlank()) return item.id
        if (!item.id.startsWith("r.") && !item.type.equals("library-artists", ignoreCase = true)) {
            return item.id.takeIf { it.isNotBlank() }
        }
        val body = runCatching {
            ampApiService.getLibraryArtistRaw(item.id, locale = locale()).string()
        }.getOrNull() ?: return null
        return WebHomeParser.extractLibraryArtistDetail(body, item.title).first
            ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

    suspend fun loadItemStatus(item: HomeItem): ItemAccountStatus? {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || item.id.isBlank()) return null
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()
        val rawResType = normalizedResourceType(item)
        val isArtist = rawResType == "artists" || rawResType == "library-artists" || item.id.startsWith("ra.a-")
        val resolvedId = if (item.id.startsWith("ra.a-")) item.id.removePrefix("ra.a-") else item.id
        val resType = if (item.id.startsWith("ra.a-")) "artists" else rawResType
        val isLib = isLibraryId(resolvedId, resType)

        val catInclude = when (resType.removePrefix("library-")) {
            "songs", "music-videos" -> "albums,artists"
            "albums" -> "artists"
            else -> null
        }
        val libInclude = when (resType) {
            "library-songs", "library-music-videos" -> "catalog,albums,artists"
            "library-albums" -> "catalog,artists"
            else -> "catalog"
        }

        val body = runCatching {
            if (isLib) {
                ampApiService.getLibraryItemStatusRaw(
                    ids = mapOf("ids[$resType]" to resolvedId),
                    include = libInclude,
                    locale = localeTag
                ).string()
            } else {
                ampApiService.getCatalogItemStatusRaw(
                    storefront = sf,
                    ids = mapOf("ids[$resType]" to resolvedId),
                    include = catInclude,
                    locale = localeTag
                ).string()
            }
        }.onFailure { Log.w(TAG, "loadItemStatus error ($resType/$resolvedId)", it) }.getOrNull()
            ?: return null

        val status = MediaDetailsParser.parseItemStatus(resolvedId, body) ?: return null
        // Si vino desde librería y tenemos catalogId, consultar también el rating en catálogo cuando personalRating faltó
        val catId = status.catalogId?.takeIf { it != resolvedId && it.all(Char::isDigit) }
            ?: item.catalogId?.takeIf { it != resolvedId && it.all(Char::isDigit) }
            ?: if (isArtist && isLib) resolveCatalogArtistId(item) else null
        var resolvedStatus = if (status.catalogId == null && catId != null) status.copy(catalogId = catId) else status
        if (isLib && !resolvedStatus.isFavorite && catId != null) {
            val catType = resType.removePrefix("library-")
            val catBody = runCatching {
                ampApiService.getCatalogItemStatusRaw(
                    storefront = sf,
                    ids = mapOf("ids[$catType]" to catId),
                    include = catInclude,
                    locale = localeTag
                ).string()
            }.getOrNull()
            val catStatus = catBody?.let { MediaDetailsParser.parseItemStatus(catId, it) }
            if (catStatus != null) {
                resolvedStatus = resolvedStatus.copy(
                    isFavorite = resolvedStatus.isFavorite || catStatus.isFavorite,
                    albumId = resolvedStatus.albumId ?: catStatus.albumId,
                    artistId = resolvedStatus.artistId ?: catStatus.artistId
                )
            }
        }
        // Verificación directa en `/v1/me/ratings/{type}` para que la estrella ★ siempre coincida con Apple Music
        if (!resolvedStatus.isFavorite) {
            val ratingFound = runCatching {
                val r1 = ampApiService.getRatingsBatchRaw(resType, resolvedId).string()
                if (resolvedId in MediaDetailsParser.parseFavoriteIdsFromRatings(r1)) true
                else if (catId != null) {
                    val catType = resType.removePrefix("library-")
                    val r2 = ampApiService.getRatingsBatchRaw(catType, catId).string()
                    catId in MediaDetailsParser.parseFavoriteIdsFromRatings(r2)
                } else false
            }.getOrDefault(false)
            if (ratingFound) {
                resolvedStatus = resolvedStatus.copy(isFavorite = true)
            }
        }
        return resolvedStatus
    }

    /** Consulta en lote qué pistas de un álbum o playlist tienen estrella ★ (`value == 1`) en la cuenta del usuario. */
    suspend fun loadFavoriteIdsForAlbum(album: AlbumDetail): Map<String, Boolean> {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || album.tracks.isEmpty()) return emptyMap()

        val titleLower = album.title.lowercase()
        val isFavoritesPlaylist = album.isPlaylist && (
            titleLower.contains("canciones favoritas") ||
                titleLower.contains("favorite songs") ||
                titleLower.contains("favourite songs") ||
                album.id.contains("fav", ignoreCase = true)
            )
        if (isFavoritesPlaylist) {
            val allFav = LinkedHashMap<String, Boolean>()
            album.tracks.forEach { t ->
                if (t.id.isNotBlank()) allFav[t.id] = true
                t.catalogId?.takeIf { it.isNotBlank() }?.let { allFav[it] = true }
            }
            return allFav
        }

        val catalogIds = album.tracks.mapNotNull { t ->
            t.catalogId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?: t.id.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        }.distinct()
        val libraryIds = album.tracks.mapNotNull { t ->
            t.id.takeIf { it.startsWith("i.") }
        }.distinct()

        val favSet = HashSet<String>()
        catalogIds.chunked(80).forEach { chunk ->
            runCatching {
                val body = ampApiService.getRatingsBatchRaw("songs", chunk.joinToString(",")).string()
                favSet += MediaDetailsParser.parseFavoriteIdsFromRatings(body)
            }
        }
        libraryIds.chunked(80).forEach { chunk ->
            runCatching {
                val body = ampApiService.getRatingsBatchRaw("library-songs", chunk.joinToString(",")).string()
                favSet += MediaDetailsParser.parseFavoriteIdsFromRatings(body)
            }
        }

        val result = LinkedHashMap<String, Boolean>()
        album.tracks.forEach { t ->
            val isFav = (t.id in favSet) || (t.catalogId != null && t.catalogId in favSet)
            if (isFav) {
                if (t.id.isNotBlank()) result[t.id] = true
                t.catalogId?.takeIf { it.isNotBlank() }?.let { result[it] = true }
            }
        }
        return result
    }

    /** Consulta en lote `/v1/me/ratings/artists` para sincronizar automáticamente las estrellas ★ de artistas. */
    suspend fun loadFavoriteIdsForArtists(artists: List<HomeItem>): Map<String, Boolean> {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || artists.isEmpty()) return emptyMap()

        val catalogIds = artists.mapNotNull { art ->
            art.catalogId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?: art.id.removePrefix("ra.a-").takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        }.distinct()
        if (catalogIds.isEmpty()) return emptyMap()

        val favSet = HashSet<String>()
        catalogIds.chunked(60).forEach { chunk ->
            runCatching {
                val body = ampApiService.getRatingsBatchRaw("artists", chunk.joinToString(",")).string()
                favSet += MediaDetailsParser.parseFavoriteIdsFromRatings(body)
            }
        }

        val result = LinkedHashMap<String, Boolean>()
        artists.forEach { art ->
            val cleanId = art.catalogId?.takeIf { it.all(Char::isDigit) }
                ?: art.id.removePrefix("ra.a-").takeIf { it.all(Char::isDigit) }
            val isFav = cleanId != null && cleanId in favSet
            if (isFav) {
                if (art.id.isNotBlank()) result[art.id] = true
                if (cleanId != null) result[cleanId] = true
                if (art.title.isNotBlank()) result[com.applemusic.android.ui.home.artistFavoriteNameKey(art.title)] = true
            }
        }
        return result
    }

    /** Marca/desmarca favorito (`★`) usando `/v1/me/favorites` + `/v1/me/ratings`. */
    suspend fun setFavorite(item: HomeItem, makeFavorite: Boolean): Boolean {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || item.id.isBlank()) return false
        val rawType = normalizedResourceType(item)
        val isArtist = rawType == "artists" || rawType == "library-artists"
        val resolvedCatalogId = when {
            isArtist -> resolveCatalogArtistId(item)
            rawType == "library-songs" -> item.catalogId?.takeIf { it.isNotBlank() && !it.startsWith("i.") }
                ?: resolveCatalogSongId(item)
            else -> item.catalogId?.takeIf {
                it.isNotBlank() && !it.startsWith("i.") && !it.startsWith("l.") && !it.startsWith("p.") && !it.startsWith("r.")
            }
        }

        val primaryId = resolvedCatalogId ?: item.id.removePrefix("ra.a-")
        val primaryType = if (isArtist) "artists" else if (primaryId == item.id) rawType else rawType.removePrefix("library-")

        suspend fun sendFavorite(type: String, id: String): Boolean {
            val query = mapOf("ids[$type]" to id)
            return runCatching {
                val res = if (makeFavorite) ampApiService.addFavoriteRaw(query)
                else ampApiService.removeFavoriteRaw(query)
                if (!res.isSuccessful) {
                    val errText = runCatching { res.errorBody()?.string() }.getOrNull().orEmpty()
                    Log.w(TAG, "favorites($makeFavorite) $type/$id HTTP ${res.code()}: $errText")
                } else {
                    Log.d(TAG, "favorites($makeFavorite) $type/$id OK HTTP ${res.code()}")
                }
                res.isSuccessful
            }.onFailure {
                Log.w(TAG, "favorites($makeFavorite) $type/$id exception", it)
            }.getOrDefault(false)
        }

        var favOk = sendFavorite(primaryType, primaryId)
        if (!favOk && item.id != primaryId && !isArtist) {
            favOk = sendFavorite(rawType, item.id)
        } else if (makeFavorite && favOk && item.id.startsWith("i.") && primaryType == "songs") {
            // Sincronizar también en biblioteca cuando el usuario pulsa ★ desde un tema `i.*`
            runCatching {
                val payload = """{"type":"rating","attributes":{"value":1}}"""
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                ampApiService.updateRatingRaw(rawType, item.id, payload)
            }
        }
        if (!makeFavorite && item.id != primaryId && isLibraryId(item.id, rawType) && !isArtist) {
            sendFavorite(rawType, item.id)
        }

        // También sincronizar rating (`personalRating = 1`) usado en vistas de biblioteca
        val ratingOk = runCatching {
            if (makeFavorite) {
                val payload = """{"type":"rating","attributes":{"value":1}}"""
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                ampApiService.updateRatingRaw(primaryType, primaryId, payload).isSuccessful
            } else {
                ampApiService.deleteRatingRaw(primaryType, primaryId).isSuccessful
            }
        }.getOrDefault(false)

        if (favOk || ratingOk) {
            invalidateDetailCache()
            deleteDiskCacheByPrefix("feed_home_v1_")
            if (isArtist) artistCache.remove(primaryId)
        }
        return favOk || (isArtist && ratingOk)
    }

    suspend fun setInLibrary(item: HomeItem, add: Boolean, resolvedLibraryId: String? = null): Boolean {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || item.id.isBlank()) return false
        val resType = normalizedResourceType(item)
        val ok = runCatching {
            if (add) {
                val catId = item.catalogId?.takeIf { it.isNotBlank() } ?: item.id
                val catType = resType.removePrefix("library-")
                ampApiService.addToLibraryRaw(mapOf("ids[$catType]" to catId)).isSuccessful
            } else {
                val libId = resolvedLibraryId ?: item.id.takeIf { isLibraryId(it, resType) }
                    ?: loadItemStatus(item)?.libraryId
                    ?: return@runCatching false
                val libType = if (resType.startsWith("library-")) resType else "library-$resType"
                ampApiService.removeFromLibraryRaw(libType, libId).isSuccessful
            }
        }.onFailure { Log.w(TAG, "setInLibrary($add) error", it) }.getOrDefault(false)
        if (ok) {
            invalidateDetailCache(item.id)
            deleteDiskCacheByPrefix("feed_lib_v1_")
        }
        return ok
    }

    suspend fun deleteUserPlaylist(playlistId: String): Boolean {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || playlistId.isBlank()) return false
        val ok = runCatching {
            ampApiService.removeFromLibraryRaw("library-playlists", playlistId).isSuccessful
        }.onFailure { Log.w(TAG, "deleteUserPlaylist error", it) }.getOrDefault(false)
        if (ok) {
            invalidateDetailCache(playlistId)
            deleteDiskCacheByPrefix("feed_lib_v1_")
        }
        return ok
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, track: HomeItem): Boolean {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || playlistId.isBlank() || track.id.isBlank()) return false
        val key = if (track.id.startsWith("i.") || track.type.contains("library")) {
            "ids[library-songs]"
        } else {
            "ids[songs]"
        }
        val ok = runCatching {
            val res = ampApiService.removeTracksFromPlaylistRaw(playlistId, mapOf(key to track.id))
            if (!res.isSuccessful && key != "ids[library-songs]") {
                ampApiService.removeTracksFromPlaylistRaw(playlistId, mapOf("ids[library-songs]" to track.id)).isSuccessful
            } else {
                res.isSuccessful
            }
        }.onFailure { Log.w(TAG, "removeTrackFromPlaylist error", it) }.getOrDefault(false)
        if (ok) {
            invalidateDetailCache(playlistId)
            deleteDiskCacheByPrefix("feed_lib_v1_")
        }
        return ok
    }

    suspend fun loadUserEditablePlaylists(): List<HomeItem> {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession) return emptyList()
        val body = runCatching {
            ampApiService.getLibraryPlaylistsRaw(limit = 100, locale = locale()).string()
        }.getOrNull() ?: return emptyList()
        return WebHomeParser.parseLibraryList(body).first
    }

    suspend fun addItemToPlaylist(playlistId: String, item: HomeItem): Boolean {
        val creds = tokenManager.getCredentials()
        if (!creds.hasWebSession || playlistId.isBlank() || item.id.isBlank()) return false
        val resType = normalizedResourceType(item)
        val payload = JSONObject().put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", resType)
            )
        ).toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val ok = runCatching {
            ampApiService.addTracksToPlaylistRaw(playlistId, payload).isSuccessful
        }.onFailure { Log.w(TAG, "addItemToPlaylist error", it) }.getOrDefault(false)
        if (ok) {
            invalidateDetailCache(playlistId)
            deleteDiskCacheByPrefix("feed_lib_v1_")
        }
        return ok
    }

    suspend fun createPlaylistWithItem(name: String, item: HomeItem?): Boolean {
        val creds = tokenManager.getCredentials()
        val cleanName = name.trim()
        if (!creds.hasWebSession || cleanName.isEmpty()) return false
        val rootObj = JSONObject().put(
            "attributes",
            JSONObject().put("name", cleanName)
        )
        if (item != null && item.id.isNotBlank()) {
            val resType = normalizedResourceType(item)
            rootObj.put(
                "relationships",
                JSONObject().put(
                    "tracks",
                    JSONObject().put(
                        "data",
                        JSONArray().put(
                            JSONObject()
                                .put("id", item.id)
                                .put("type", resType)
                        )
                    )
                )
            )
        }
        val body = rootObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val ok = runCatching {
            ampApiService.createPlaylistRaw(body).close()
            true
        }.onFailure { Log.w(TAG, "createPlaylistWithItem error", it) }.getOrDefault(false)
        if (ok) {
            invalidateDetailCache()
            deleteDiskCacheByPrefix("feed_lib_v1_")
        }
        return ok
    }

    suspend fun loadSongCredits(item: HomeItem): SongCreditsDetail? {
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val songId = resolveCatalogSongId(item) ?: return null
        val cacheKey = "credits_v2_${sf}_$songId"
        creditsCache[cacheKey]?.let { return it }
        readDiskCache(cacheKey, LYRICS_CACHE_TTL_MS)?.let { cached ->
            MediaDetailsParser.parseSongCredits(cached)?.let {
                creditsCache[cacheKey] = it
                return it
            }
        }
        val body = runCatching {
            ampApiService.getSongDetailsAndCreditsRaw(sf, songId, locale = locale()).string()
        }.onFailure { Log.w(TAG, "loadSongCredits error", it) }.getOrNull() ?: return null
        writeDiskCache(cacheKey, body)
        extractAlbumMotionFromSongJson(body)?.let { songMotionCache["${sf}_$songId"] = it }
        return MediaDetailsParser.parseSongCredits(body)?.also { creditsCache[cacheKey] = it }
    }

    /** Obtiene la portada animada HLS (`motionDetailSquare`) del álbum de una canción en reproducción. */
    suspend fun loadSongMotionArtwork(item: HomeItem): String? {
        val isRadioItem = item.type.contains("station", ignoreCase = true) ||
            item.id.startsWith("ra.") ||
            item.type.contains("radio", ignoreCase = true)
        if (!isRadioItem) {
            item.motionArtworkUrl?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val songId = resolveCatalogSongId(item) ?: return null
        val cacheKey = "${sf}_$songId"
        songMotionCache[cacheKey]?.let { return it.ifBlank { null } }

        val creditsKey = "credits_v2_$cacheKey"
        val body = readDiskCache(creditsKey, LYRICS_CACHE_TTL_MS)
            ?: runCatching {
                ampApiService.getSongDetailsAndCreditsRaw(sf, songId, locale = locale()).string()
            }.getOrNull()?.also { writeDiskCache(creditsKey, it) }

        val motionUrl = body?.let { extractAlbumMotionFromSongJson(it) }
        if (motionUrl != null) {
            songMotionCache[cacheKey] = motionUrl
            return motionUrl
        }

        // Si el álbum no trajo editorialVideo embebido, consultar el álbum directo (también cacheado)
        val albumId = body?.let { MediaDetailsParser.parseSongCredits(it)?.albumId }
        if (!albumId.isNullOrBlank()) {
            val albumMotion = runCatching {
                loadMediaDetail(HomeItem(id = albumId, type = "albums", title = "", subtitle = "", artworkUrl = null))
            }.getOrNull()?.let { it.motionSquareUrl ?: it.motionTallUrl }
            if (!albumMotion.isNullOrBlank()) {
                songMotionCache[cacheKey] = albumMotion
                return albumMotion
            }
        }
        songMotionCache[cacheKey] = ""
        return null
    }

    suspend fun loadSongAudioTraits(item: HomeItem): List<String> =
        loadSongCredits(item)?.audioTraits.orEmpty()

    private fun extractAlbumMotionFromSongJson(body: String): String? = runCatching {
        val root = JSONObject(body)
        val resources = root.optJSONObject("resources") ?: return null
        val albums = resources.optJSONObject("albums") ?: return null
        val keys = albums.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val ev = albums.optJSONObject(k)
                ?.optJSONObject("attributes")
                ?.optJSONObject("editorialVideo")
                ?: continue
            for (vKey in listOf("motionDetailSquare", "motionSquareVideo1x1", "motionDetailTall", "motionTallVideo3x4")) {
                val url = ev.optJSONObject(vKey)?.optString("video")?.takeIf { it.isNotBlank() }
                if (url != null) return url
            }
        }
        null
    }.getOrNull()

    suspend fun loadLyrics(item: HomeItem): SongLyrics? {
        val creds = tokenManager.getCredentials()
        val sf = creds.storefront.ifBlank { DEFAULT_STOREFRONT }
        val localeTag = locale()
        val songId = resolveCatalogSongId(item) ?: return null
        val cacheKey = "lyrics_${sf}_$songId"
        lyricsCache[cacheKey]?.let { return it }
        readDiskCache(cacheKey, LYRICS_CACHE_TTL_MS)?.let { cached ->
            TtmlLyricsParser.parseLyricsResponse(songId, cached)?.let {
                lyricsCache[cacheKey] = it
                return it
            }
        }

        val syllableBody = runCatching {
            ampApiService.getSyllableLyricsRaw(sf, songId, locale = localeTag).string()
        }.getOrNull()
        syllableBody?.let { raw ->
            TtmlLyricsParser.parseLyricsResponse(songId, raw)?.let { parsed ->
                writeDiskCache(cacheKey, raw)
                lyricsCache[cacheKey] = parsed
                return parsed
            }
        }

        val standardBody = runCatching {
            ampApiService.getLyricsRaw(sf, songId, locale = localeTag).string()
        }.getOrNull()
        standardBody?.let { raw ->
            TtmlLyricsParser.parseLyricsResponse(songId, raw)?.let { parsed ->
                writeDiskCache(cacheKey, raw)
                lyricsCache[cacheKey] = parsed
                return parsed
            }
        }

        val detailBody = runCatching {
            ampApiService.getSongDetailsAndCreditsRaw(sf, songId, locale = localeTag).string()
        }.getOrNull()
        return detailBody?.let { raw ->
            TtmlLyricsParser.parseLyricsResponse(songId, raw)?.also { parsed ->
                writeDiskCache(cacheKey, raw)
                lyricsCache[cacheKey] = parsed
            }
        }
    }

    private suspend fun loadFromFallback(
        creds: AppleMusicCredentials,
        localeTag: String,
        webLoginRequired: Boolean
    ): HomeFeedResult {
        if (creds.hasDeveloperToken && creds.hasUserToken) {
            val publicFeed = loadFromPublicApi(creds, localeTag)
            if (publicFeed.sections.isNotEmpty()) {
                return publicFeed.copy(webLoginRequired = webLoginRequired)
            }
        }

        val editorialSections = runCatching {
            val body = ampApiService.getEditorialGroupingsRaw(
                storefront = creds.storefront,
                locale = localeTag
            ).string()
            WebHomeParser.parseGroupings(body)
        }.onFailure { Log.w(TAG, "editorial groupings failed", it) }
            .getOrNull()
            .orEmpty()

        if (editorialSections.isNotEmpty()) {
            return HomeFeedResult(
                mode = ConnectionMode.CATALOG_ONLY,
                sections = editorialSections,
                webLoginRequired = webLoginRequired
            )
        }

        if (!creds.hasDeveloperToken) {
            return HomeFeedResult(
                mode = ConnectionMode.DEMO,
                sections = sampleHomeSections(),
                webLoginRequired = webLoginRequired
            )
        }

        return loadCatalogFallback(creds.storefront, localeTag, warning = null)
            .copy(webLoginRequired = webLoginRequired)
    }

    private suspend fun loadFromPublicApi(
        creds: AppleMusicCredentials,
        localeTag: String
    ): HomeFeedResult = coroutineScope {
        val listenNowDeferred = async {
            runCatching {
                apiService.getListenNow(timezone = currentTimezoneOffset(), locale = localeTag).data
            }
        }
        val classicDeferred = async { runCatching { fetchClassicRecommendations(localeTag) } }

        val listenNowResult = listenNowDeferred.await()
        val classicResult = classicDeferred.await()
        listenNowResult.exceptionOrNull()?.let { Log.w(TAG, "listen-now error", it) }
        classicResult.exceptionOrNull()?.let { Log.w(TAG, "recommendations error", it) }

        val listenNow = listenNowResult.getOrNull().orEmpty()
        val classic = classicResult.getOrNull().orEmpty()

        val coverSections = (listenNow.ifEmpty { classic })
            .mapNotNull { it.toSection() }
            .distinctBy { it.title.lowercase(Locale.getDefault()) + it.style }
            .toMutableList()

        val madeForYou = classic
            .firstOrNull { it.id.startsWith(KIND_MADE_FOR_YOU) || it.titleText().containsAny("hechas para ti", "playlists para ti", "made for you") }
            ?.let { rec ->
                HomeSection(
                    id = "rec_made_for_you",
                    title = rec.titleText(),
                    style = HomeSectionStyle.HERO_PICKS,
                    items = rec.contents().map { it.toHomeItem() }
                )
            }
            ?.takeIf { it.items.isNotEmpty() }

        if (madeForYou != null) {
            coverSections.removeAll {
                it.id.startsWith("rec_$KIND_MADE_FOR_YOU") || it.title.equals(madeForYou.title, ignoreCase = true)
            }
        }

        val sections = arrangeLikeWeb(coverSections, madeForYou)

        if (sections.isEmpty()) {
            val error = listenNowResult.exceptionOrNull() ?: classicResult.exceptionOrNull()
            return@coroutineScope loadCatalogFallback(
                creds.storefront,
                localeTag,
                warning = error?.message ?: strings.noRecommendations
            )
        }

        val finalSections = enrichPlaylistArtistNames(sections, creds.storefront, localeTag)
        HomeFeedResult(mode = ConnectionMode.PERSONALIZED, sections = finalSections)
    }

    private fun arrangeLikeWeb(
        covers: MutableList<HomeSection>,
        madeForYou: HomeSection?
    ): List<HomeSection> {
        val result = covers.toMutableList()

        fun indexOf(kind: String, vararg keys: String) = result.indexOfFirst {
            it.id.startsWith("rec_$kind") || it.title.containsAny(*keys)
        }

        if (madeForYou != null) {
            val nov = indexOf(KIND_NEW_RELEASES, "novedades", "new releases")
            result.add(if (nov >= 0) nov + 1 else minOf(result.size, 1), madeForYou)
        }
        buildTopPicks(result)?.let { result.add(0, it) }
        return result
    }

    private fun buildTopPicks(sections: List<HomeSection>): HomeSection? {
        fun itemsOf(kind: String, vararg keys: String) = sections.firstOrNull {
            it.id.startsWith("rec_$kind") || it.title.containsAny(*keys)
        }?.items.orEmpty()

        val again = itemsOf(KIND_RECENT, "escuchado recientemente", "reproducciones recientes", "recently played")
            .filter { it.type.contains("album") }
            .map { it.copy(badge = strings.badgeListenAgain) }
        val made = itemsOf(KIND_MADE_FOR_YOU, "hechas para ti", "playlists para ti", "made for you").map { it.copy(badge = strings.badgeMadeForYou) }
        val fresh = itemsOf(KIND_NEW_RELEASES, "novedades", "new releases").map { it.copy(badge = strings.badgeNew) }
        val stations = itemsOf(KIND_STATIONS, "estaciones para ti", "emisoras para ti", "stations for you")
        val artistStations = stations
            .filter { it.id.startsWith("ra.") && !it.id.startsWith("ra.u-") }
            .map { it.copy(badge = strings.badgeFeaturing.format(it.title.removeSuffix(" y artistas similares").removeSuffix(" & Similar Artists"))) }
        val personal = stations.filter { it.id.startsWith("ra.u-") }.map { it.copy(badge = strings.badgeMadeForYou) }
        val mood = itemsOf(KIND_MOODS, "estado de ánimo", "find your mood").take(1).map { it.copy(badge = strings.badgeMood) }

        val pattern = listOf(again, made, fresh, again, artistStations, personal, artistStations, fresh, mood)
        val used = mutableSetOf<String>()
        val picks = pattern.mapNotNull { pool -> pool.firstOrNull { used.add(it.id) } }
        if (picks.isEmpty()) return null
        return HomeSection(
            id = "rec_top_picks",
            title = strings.topPicks,
            style = HomeSectionStyle.HERO_PICKS,
            items = picks
        )
    }

    private suspend fun enrichPlaylistArtistNames(
        sections: List<HomeSection>,
        storefront: String,
        localeTag: String
    ): List<HomeSection> = coroutineScope {
        val targets = sections
            .filter { it.style == HomeSectionStyle.HERO_PICKS }
            .flatMap { it.items }
            .filter { it.type == "playlists" && it.id.startsWith("pl.") }
            .map { it.id }
            .distinct()
            .take(20)

        val names: Map<String, String> = targets.map { id ->
            async {
                runCatching {
                    val tracks = apiService.getPlaylistTracks(storefront, id, locale = localeTag).data
                    id to formatArtistNames(tracks.mapNotNull { it.attributes?.artistName })
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().filter { it.second.isNotBlank() }.toMap()

        sections.map { section ->
            if (section.style != HomeSectionStyle.HERO_PICKS) return@map section
            section.copy(items = section.items.map { item ->
                names[item.id]?.let { item.copy(subtitle = it) } ?: item
            })
        }
    }

    private fun formatArtistNames(artistNames: List<String>): String {
        val unique = LinkedHashSet<String>()
        artistNames.forEach { raw ->
            val parts = raw.split(" & ", " feat. ", " ft. ").flatMap { it.split(", ") }
            val merged = mutableListOf<String>()
            parts.map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                if (part.startsWith("The ") && merged.isNotEmpty()) {
                    merged[merged.lastIndex] = merged.last() + ", " + part
                } else {
                    merged.add(part)
                }
            }
            merged.forEach(unique::add)
        }
        if (unique.isEmpty()) return ""
        val picked = mutableListOf<String>()
        for (name in unique) {
            if (picked.size >= 5 || (picked.joinToString(", ") + name).length > 72) break
            picked.add(name)
        }
        return picked.joinToString(", ") + if (unique.size > picked.size) " y más" else ""
    }

    private fun RecommendationResource.titleText(): String =
        attributes?.title?.stringForDisplay.orEmpty()

    private fun RecommendationResource.contents(): List<MediaResource> {
        val direct = relationships?.contents?.data.orEmpty()
        if (direct.isNotEmpty()) return direct
        return relationships?.recommendations?.data.orEmpty()
            .flatMap { it.relationships?.contents?.data.orEmpty() }
    }

    private fun RecommendationResource.toSection(): HomeSection? {
        val items = contents()
        if (items.isEmpty()) return null
        val rawTitle = titleText()

        if (rawTitle.isBlank()) {
            return HomeSection(
                id = "rec_$id",
                title = "",
                style = HomeSectionStyle.SUPER_HERO,
                items = items.map { it.toHomeItem(badge = if (it.type == "stations") strings.live else null) }
            )
        }

        val moreLike = MORE_LIKE_PREFIXES.firstOrNull { rawTitle.startsWith(it, ignoreCase = true) }
        val (subtitle, title) = if (moreLike != null) {
            strings.moreLike to rawTitle.substring(moreLike.length).trim()
        } else {
            attributes?.reason?.stringForDisplay to rawTitle
        }
        val kindPrefix = id.substringBefore('-') + "-"
        val canLoadMore = kindPrefix == KIND_RECENT
        val primary = if (moreLike != null) {
            attributes?.title?.contentIds?.firstOrNull()?.let { albumId ->
                HomeItem(id = albumId, type = "albums", title = rawTitle.substring(moreLike.length).trim(),
                    subtitle = "", artworkUrl = null)
            }
        } else null
        return HomeSection(
            id = "rec_$id",
            title = title,
            subtitle = subtitle,
            hasChevron = canLoadMore || primary != null,
            style = HomeSectionStyle.SQUARE_CAROUSEL,
            items = items.map { it.toHomeItem() },
            canLoadMore = canLoadMore,
            primaryContent = primary
        )
    }

    private suspend fun fetchClassicRecommendations(localeTag: String): List<RecommendationResource> {
        val all = mutableListOf<RecommendationResource>()
        var response = apiService.getRecommendations(locale = localeTag)
        all.addAll(response.data)
        var pages = 1
        while (!response.next.isNullOrBlank() && pages < 3) {
            response = apiService.getRecommendationsByUrl(response.next!!.removePrefix("/"))
            all.addAll(response.data)
            pages++
        }
        return all.distinctBy { it.id }
    }

    private fun MediaResource.toHomeItem(badge: String? = null): HomeItem {
        val attrs = attributes
        val catalogAttrs = relationships?.catalog?.data?.firstOrNull()?.attributes
        val title = attrs?.name ?: catalogAttrs?.name ?: strings.untitled
        val subtitle = when (type) {
            "artists", "library-artists" -> ""
            "stations" -> attrs?.editorialNotes?.short ?: attrs?.plainEditorialNotes?.short ?: ""
            else -> attrs?.artistName
                ?: catalogAttrs?.artistName
                ?: attrs?.artistNames
                ?: attrs?.curatorName
                ?: catalogAttrs?.curatorName
                ?: attrs?.editorialNotes?.short
                ?: ""
        }
        val artwork = attrs?.artwork ?: catalogAttrs?.artwork
        return HomeItem(
            id = id,
            type = type,
            title = title,
            subtitle = subtitle,
            badge = badge,
            artworkUrl = artwork?.urlForSize(600, 600),
            bgColorHex = artwork?.bgColor,
            isExplicit = (attrs?.contentRating ?: catalogAttrs?.contentRating) == "explicit",
            textColorHex = artwork?.textColor1
        )
    }

    private suspend fun loadCatalogFallback(
        storefront: String,
        localeTag: String,
        warning: String?
    ): HomeFeedResult {
        val charts = apiService.getCatalogCharts(storefront = storefront, locale = localeTag)
        val sections = mutableListOf<HomeSection>()

        charts.results?.playlists?.firstOrNull()?.takeIf { it.data.isNotEmpty() }?.let { group ->
            sections.add(
                HomeSection(
                    id = "chart_playlists",
                    title = group.name.ifBlank { strings.topPlaylists },
                    hasChevron = true,
                    style = HomeSectionStyle.HERO_PICKS,
                    items = group.data.map { it.toHomeItem(badge = "Apple Music") }
                )
            )
        }
        charts.results?.albums?.firstOrNull()?.takeIf { it.data.isNotEmpty() }?.let { group ->
            sections.add(
                HomeSection(
                    id = "chart_albums",
                    title = group.name.ifBlank { strings.topAlbums },
                    hasChevron = true,
                    style = HomeSectionStyle.SQUARE_CAROUSEL,
                    items = group.data.map { it.toHomeItem() }
                )
            )
        }
        charts.results?.songs?.firstOrNull()?.takeIf { it.data.isNotEmpty() }?.let { group ->
            sections.add(
                HomeSection(
                    id = "chart_songs",
                    title = group.name.ifBlank { strings.topSongs },
                    hasChevron = true,
                    style = HomeSectionStyle.COMPACT_TRACKS,
                    items = group.data.map { it.toHomeItem() }
                )
            )
        }
        return HomeFeedResult(ConnectionMode.CATALOG_ONLY, sections, warning)
    }

    private fun Throwable.isAuthFailure(): Boolean =
        this is HttpException && (code() == 401 || code() == 403)

    private fun currentTimezoneOffset(): String {
        val totalMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (totalMinutes < 0) "-" else "+"
        val abs = kotlin.math.abs(totalMinutes)
        return String.format(Locale.US, "%s%02d:%02d", sign, abs / 60, abs % 60)
    }

    private fun String.containsAny(vararg keys: String): Boolean {
        val lower = lowercase(Locale.getDefault())
        return keys.any { lower.contains(it) }
    }

    private fun sampleHomeSections(): List<HomeSection> = listOf(
        HomeSection(
            id = "demo_top_picks",
            title = strings.topPicks,
            style = HomeSectionStyle.HERO_PICKS,
            items = listOf(
                HomeItem("demo_1", "playlists", "¡Anímate!", "Rihanna, Black Eyed Peas, Daft Punk y más", "Hechos para ti", null, "FA243C"),
                HomeItem("demo_2", "albums", "RR - Single", "ROSALÍA, Rauw Alejandro", "Popular entre los fans", null, "5E5CE6"),
                HomeItem("demo_3", "albums", "Mi Vida Mi Muerte", "Neton Vega", "Volver a escuchar", null, "0A84FF")
            )
        )
    )

    private companion object {
        const val TAG = "HomeRepository"
        const val MAX_HOME_PAGES = 5
        const val DEFAULT_STOREFRONT = "mx"

        const val KIND_MADE_FOR_YOU = "6-"
        const val KIND_RECENT = "7-"
        const val KIND_NEW_RELEASES = "9-"
        const val KIND_STATIONS = "20-"
        const val KIND_MOODS = "24-"

        const val RECENT_ROW_SIZE = 12
        const val CATALOG_DETAIL_CACHE_TTL_MS = 20 * 60_000L
        const val LIBRARY_DETAIL_CACHE_TTL_MS = 2 * 60_000L
        const val ARTIST_CACHE_TTL_MS = 60 * 60_000L
        const val LYRICS_CACHE_TTL_MS = 7L * 24 * 60 * 60_000L
        const val FEED_SNAPSHOT_TTL_MS = 7L * 24 * 60 * 60_000L

        val MORE_LIKE_PREFIXES = listOf("Más como ", "Similar a ", "More Like ", "More like ", "Similar to ")
    }
}
