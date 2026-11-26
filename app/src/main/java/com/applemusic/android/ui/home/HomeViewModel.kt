package com.applemusic.android.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applemusic.android.auth.AppleMusicCredentials
import com.applemusic.android.auth.TokenManager
import com.applemusic.android.auth.WebTokenProvider
import com.applemusic.android.data.api.NetworkModule
import com.applemusic.android.data.model.ConnectionMode
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.model.HomeSection
import com.applemusic.android.data.model.ItemAccountStatus
import com.applemusic.android.data.model.ReplayPeriodOption
import com.applemusic.android.data.model.SongCreditsDetail
import com.applemusic.android.data.model.SongLyrics
import com.applemusic.android.data.repository.HomeRepository
import com.applemusic.android.data.repository.HomeStrings
import com.applemusic.android.data.repository.LibraryCategoryPage
import com.applemusic.android.data.web.SearchSuggestionsResult
import com.applemusic.android.data.settings.AppFontOption
import com.applemusic.android.data.settings.AppLanguageOption
import com.applemusic.android.data.settings.AppSettingsManager
import com.applemusic.android.data.settings.AppSettingsUiState
import com.applemusic.android.playback.AppleMusicPlayerManager
import com.applemusic.android.playback.AtmosPreferenceMode
import com.applemusic.android.playback.PlaybackUiState
import com.applemusic.android.playback.cast.LiramuCastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val mode: ConnectionMode = ConnectionMode.DEMO,
    val sections: List<HomeSection> = emptyList(),
    val credentials: AppleMusicCredentials = AppleMusicCredentials(),
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val authFeedbackMessage: String? = null,
    val webLoginRequired: Boolean = false
)

enum class SearchScope(val label: String) {
    CATALOG("Apple Music"),
    LIBRARY("Tu biblioteca")
}

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.CATALOG,
    val isSearching: Boolean = false,
    val isLoadingCategories: Boolean = false,
    val categories: List<HomeItem> = emptyList(),
    val suggestions: SearchSuggestionsResult = SearchSuggestionsResult(),
    val results: List<HomeSection> = emptyList(),
    val hasSearched: Boolean = false,
    val recentQueries: List<String> = emptyList(),
    val errorMessage: String? = null
)

enum class LibrarySectionTab(val label: String) {
    RECENT("Reciente"),
    PLAYLISTS("Playlists"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    SONGS("Canciones")
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val selectedCategory: LibrarySectionTab = LibrarySectionTab.RECENT,
    val filterQuery: String = "",
    val pinned: List<HomeItem> = emptyList(),
    val recentlyAdded: LibraryCategoryPage = LibraryCategoryPage(),
    val playlists: LibraryCategoryPage = LibraryCategoryPage(),
    val albums: LibraryCategoryPage = LibraryCategoryPage(),
    val artists: LibraryCategoryPage = LibraryCategoryPage(),
    val songs: LibraryCategoryPage = LibraryCategoryPage(),
    val librarySearchResults: List<HomeSection> = emptyList(),
    val isSearchingLibrary: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null
)

private const val PENDING_RECENT_TTL_MS = 5 * 60_000L

/** Clave en `favoriteOverrides` para saber si un artista es favorito por su nombre (ids de biblioteca y catálogo difieren). */
internal fun artistFavoriteNameKey(name: String): String = "artist-name:" + name.trim().lowercase()

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val settingsManager = AppSettingsManager(application)
    private val webTokenProvider = WebTokenProvider(tokenManager)
    private val apiService = NetworkModule.createApiService(tokenManager, application)
    private val ampApiService = NetworkModule.createAmpApiService(tokenManager, webTokenProvider, application)
    private val repository = HomeRepository(
        apiService = apiService,
        ampApiService = ampApiService,
        webTokenProvider = webTokenProvider,
        tokenManager = tokenManager,
        strings = HomeStrings.from(application),
        cacheDir = application.cacheDir,
        languageOverrideProvider = settingsManager::effectiveLanguageOverride
    )
    private val playerManager = AppleMusicPlayerManager(application, tokenManager)

    val settingsState: StateFlow<AppSettingsUiState> = settingsManager.state
    private val _uiState = MutableStateFlow(
        HomeUiState(credentials = tokenManager.getCredentials())
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val playbackState: StateFlow<PlaybackUiState> = playerManager.state

    private val _newState = MutableStateFlow(HomeUiState(isLoading = true))
    val newState: StateFlow<HomeUiState> = _newState.asStateFlow()

    private val _radioState = MutableStateFlow(HomeUiState(isLoading = true))
    val radioState: StateFlow<HomeUiState> = _radioState.asStateFlow()

    private val searchPrefs = application.getSharedPreferences("apple_music_search", Context.MODE_PRIVATE)
    private val _searchState = MutableStateFlow(SearchUiState(recentQueries = readRecentSearches()))
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _libraryState = MutableStateFlow(LibraryUiState(isLoading = true))
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    @Volatile
    private var loadingNew = false

    @Volatile
    private var loadingRadio = false

    @Volatile
    private var loadingLibrary = false

    private var suggestionJob: Job? = null
    private var searchJob: Job? = null
    private var librarySearchJob: Job? = null

    // Favoritos de artistas (declarados antes del init: loadHome puede usarlos de inmediato)
    private val favoriteShelfRegex = Regex("favorit|favori|favourite|lieblings", RegexOption.IGNORE_CASE)
    /** Claves (ids y nombre) de los artistas que aparecen en la sección "Artistas favoritos". */
    private val shelfFavoriteArtistKeys: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** Artistas a los que el usuario quitó la estrella en esta sesión. */
    private val locallyUnfavoritedArtistKeys: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    init {
        val initialSettings = settingsManager.state.value
        playerManager.setAudioTransitionConfig(initialSettings.crossfadeSeconds, initialSettings.autoMixEnabled)
        preloadCachedFeeds()
        loadHome(isRefresh = false)
        loadNew(isRefresh = false)
        loadRadio(isRefresh = false)
        loadLibrary(isRefresh = false)
        refreshSearchCategories()
        refreshMySocialProfile()
        viewModelScope.launch { settingsManager.trimCachesToLimit() }
        // Chromecast: la TV sigue la canción actual (audio con MusicKit JS, portada animada y letra en seguimiento).
        LiramuCastManager.init(application)
        LiramuCastManager.bind(
            scope = viewModelScope,
            playback = playerManager.state,
            credentials = { tokenManager.getCredentials() },
            loadLyrics = { item -> runCatching { repository.loadLyrics(item) }.getOrNull() },
            loadMotionArtwork = { item -> runCatching { repository.loadSongMotionArtwork(item) }.getOrNull() }
        )
        viewModelScope.launch {
            var lastStationQueried = ""
            playerManager.state.collect { pState ->
                val err = pState.errorMessage
                if (!err.isNullOrBlank()) {
                    _uiState.update { it.copy(authFeedbackMessage = err) }
                    playerManager.clearError()
                }
                val cur = pState.currentItem
                val stId = cur?.id.orEmpty()
                if (stId.startsWith("ra.") && stId != lastStationQueried) {
                    lastStationQueried = stId
                    launch {
                        val info = runCatching { repository.loadLiveStationShowInfo(stId) }.getOrNull()
                        if (info != null) {
                            playerManager.applyLiveStationFallbackMetadata(stId, info.first, info.second)
                        }
                    }
                } else if (!stId.startsWith("ra.")) {
                    lastStationQueried = ""
                }
            }
        }
    }

    private fun preloadCachedFeeds() {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedHome = repository.loadCachedHomeFeed()
            val cachedNew = repository.loadCachedNewFeed()
            val cachedRadio = repository.loadCachedRadioFeed()
            val cachedLib = repository.loadCachedLibraryOverview()
            val cachedCats = repository.loadCachedSearchCategories()
            val creds = tokenManager.getCredentials()

            if (!cachedHome.isNullOrEmpty()) {
                // La estrella de favoritos ya funciona con el feed guardado, sin esperar a la red.
                syncFavoriteArtistsFromFeed(cachedHome)
                _uiState.update { current ->
                    if (current.sections.isEmpty()) current.copy(
                        isLoading = false,
                        mode = if (creds.hasWebSession) ConnectionMode.PERSONALIZED else current.mode,
                        sections = withPendingRecent(cachedHome)
                    ) else current
                }
            }
            if (!cachedNew.isNullOrEmpty()) {
                _newState.update { current ->
                    if (current.sections.isEmpty()) current.copy(isLoading = false, sections = cachedNew) else current
                }
            }
            if (!cachedRadio.isNullOrEmpty()) {
                _radioState.update { current ->
                    if (current.sections.isEmpty()) current.copy(isLoading = false, sections = cachedRadio) else current
                }
            }
            if (cachedLib != null) {
                _libraryState.update { current ->
                    if (!current.hasLoaded) current.copy(
                        isLoading = false,
                        hasLoaded = true,
                        recentlyAdded = cachedLib.recentlyAdded,
                        playlists = cachedLib.playlists,
                        albums = cachedLib.albums,
                        artists = cachedLib.artists,
                        songs = cachedLib.songs
                    ) else current
                }
            }
            if (!cachedCats.isNullOrEmpty()) {
                _searchState.update { current ->
                    if (current.categories.isEmpty()) current.copy(isLoadingCategories = false, categories = cachedCats) else current
                }
            }
        }
    }

    private fun refreshSearchCategories() {
        viewModelScope.launch {
            val cats = runCatching { repository.loadSearchCategories() }.getOrDefault(emptyList())
            if (cats.isNotEmpty()) {
                _searchState.update { it.copy(isLoadingCategories = false, categories = cats) }
            } else {
                _searchState.update { it.copy(isLoadingCategories = false) }
            }
        }
    }

    fun refresh() {
        loadHome(isRefresh = true)
    }

    fun loadNewIfNeeded() {
        if (_newState.value.sections.isEmpty()) loadNew(isRefresh = false)
    }

    fun refreshNew() = loadNew(isRefresh = true)

    fun loadRadioIfNeeded() {
        if (_radioState.value.sections.isEmpty()) loadRadio(isRefresh = false)
    }

    fun refreshRadio() = loadRadio(isRefresh = true)

    fun loadSearchCategoriesIfNeeded() {
        val current = _searchState.value
        if (current.categories.isNotEmpty() || current.isLoadingCategories) return
        _searchState.update { it.copy(isLoadingCategories = true) }
        viewModelScope.launch {
            val cats = runCatching { repository.loadSearchCategories() }.getOrDefault(emptyList())
            _searchState.update { it.copy(isLoadingCategories = false, categories = cats) }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchState.update {
            it.copy(
                query = newQuery,
                errorMessage = null,
                results = if (newQuery.isBlank()) emptyList() else it.results,
                hasSearched = if (newQuery.isBlank()) false else it.hasSearched,
                suggestions = if (newQuery.isBlank()) SearchSuggestionsResult() else it.suggestions
            )
        }
        suggestionJob?.cancel()
        val trimmed = newQuery.trim()
        if (trimmed.length < 2 || _searchState.value.scope == SearchScope.LIBRARY) {
            if (_searchState.value.scope == SearchScope.LIBRARY && trimmed.isNotBlank()) {
                submitSearch(trimmed, saveToHistory = false)
            }
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(260L)
            val res = runCatching { repository.searchSuggestions(trimmed) }
                .getOrDefault(SearchSuggestionsResult())
            if (_searchState.value.query.trim() == trimmed) {
                _searchState.update { it.copy(suggestions = res) }
            }
        }
    }

    fun onSearchScopeChange(scope: SearchScope) {
        if (_searchState.value.scope == scope) return
        _searchState.update { it.copy(scope = scope, errorMessage = null) }
        val q = _searchState.value.query.trim()
        if (q.isNotBlank()) {
            submitSearch(q, saveToHistory = false)
        }
    }

    fun submitSearch(term: String = _searchState.value.query, saveToHistory: Boolean = true) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        suggestionJob?.cancel()
        searchJob?.cancel()
        val updatedHistory = if (saveToHistory) saveRecentSearch(trimmed) else _searchState.value.recentQueries
        _searchState.update {
            it.copy(
                query = trimmed,
                isSearching = true,
                hasSearched = true,
                errorMessage = null,
                recentQueries = updatedHistory,
                suggestions = SearchSuggestionsResult()
            )
        }
        val scope = _searchState.value.scope
        searchJob = viewModelScope.launch {
            runCatching {
                when (scope) {
                    SearchScope.CATALOG -> repository.searchCatalog(trimmed)
                    SearchScope.LIBRARY -> repository.searchLibrary(trimmed)
                }
            }.onSuccess { sections ->
                _searchState.update {
                    it.copy(isSearching = false, results = sections, errorMessage = null)
                }
            }.onFailure { err ->
                _searchState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = err.localizedMessage ?: "No se pudo completar la búsqueda."
                    )
                }
            }
        }
    }

    fun clearSearch() {
        suggestionJob?.cancel()
        searchJob?.cancel()
        _searchState.update {
            it.copy(
                query = "",
                isSearching = false,
                hasSearched = false,
                results = emptyList(),
                suggestions = SearchSuggestionsResult(),
                errorMessage = null
            )
        }
    }

    fun clearRecentSearches() {
        searchPrefs.edit().remove("recent_queries").apply()
        _searchState.update { it.copy(recentQueries = emptyList()) }
    }

    private fun readRecentSearches(): List<String> {
        val raw = searchPrefs.getString("recent_queries", "").orEmpty()
        return raw.split('\u001F').map { it.trim() }.filter { it.isNotEmpty() }.take(10)
    }

    private fun saveRecentSearch(term: String): List<String> {
        val next = (listOf(term) + readRecentSearches().filterNot { it.equals(term, ignoreCase = true) }).take(10)
        searchPrefs.edit().putString("recent_queries", next.joinToString("\u001F")).apply()
        return next
    }

    @Volatile
    private var lastLibraryRefreshMs: Long = 0L

    fun loadLibraryIfNeeded(force: Boolean = false) {
        val current = _libraryState.value
        if (!current.hasLoaded) {
            // Hidratación instantánea desde disco para abrir Biblioteca al instante
            val cached = runCatching { repository.readCachedLibraryOverview() }.getOrNull()
            if (cached != null) {
                _libraryState.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        pinned = cached.pinned,
                        recentlyAdded = cached.recentlyAdded,
                        playlists = cached.playlists,
                        albums = cached.albums,
                        artists = cached.artists,
                        songs = cached.songs
                    )
                }
            }
            loadLibrary(isRefresh = false, silent = cached != null)
            return
        }
        val stale = (System.currentTimeMillis() - lastLibraryRefreshMs) > 20_000L
        if (force || stale) {
            loadLibrary(isRefresh = force, silent = !force)
        }
    }

    fun refreshLibrary() = loadLibrary(isRefresh = true, silent = false)

    fun selectLibraryCategory(cat: LibrarySectionTab) {
        _libraryState.update { it.copy(selectedCategory = cat) }
    }

    fun onLibraryFilterChange(query: String) {
        _libraryState.update {
            it.copy(
                filterQuery = query,
                librarySearchResults = if (query.isBlank()) emptyList() else it.librarySearchResults
            )
        }
        librarySearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _libraryState.update { it.copy(isSearchingLibrary = false, librarySearchResults = emptyList()) }
            return
        }
        librarySearchJob = viewModelScope.launch {
            _libraryState.update { it.copy(isSearchingLibrary = true) }
            delay(280L)
            val results = runCatching { repository.searchLibrary(trimmed) }.getOrDefault(emptyList())
            if (_libraryState.value.filterQuery.trim() == trimmed) {
                _libraryState.update { it.copy(isSearchingLibrary = false, librarySearchResults = results) }
            }
        }
    }

    fun loadMoreLibrary(cat: LibrarySectionTab) {
        val current = _libraryState.value
        if (current.isLoadingMore) return
        val nextUrl = when (cat) {
            LibrarySectionTab.RECENT -> current.recentlyAdded.nextUrl
            LibrarySectionTab.PLAYLISTS -> current.playlists.nextUrl
            LibrarySectionTab.ALBUMS -> current.albums.nextUrl
            LibrarySectionTab.ARTISTS -> current.artists.nextUrl
            LibrarySectionTab.SONGS -> current.songs.nextUrl
        } ?: return

        _libraryState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.loadMoreLibraryPage(nextUrl) }
                .onSuccess { page ->
                    _libraryState.update { st ->
                        fun merge(old: LibraryCategoryPage): LibraryCategoryPage =
                            LibraryCategoryPage(
                                items = (old.items + page.items).distinctBy { "${it.type}_${it.id}" },
                                nextUrl = page.nextUrl
                            )
                        when (cat) {
                            LibrarySectionTab.RECENT -> st.copy(isLoadingMore = false, recentlyAdded = merge(st.recentlyAdded))
                            LibrarySectionTab.PLAYLISTS -> st.copy(isLoadingMore = false, playlists = merge(st.playlists))
                            LibrarySectionTab.ALBUMS -> st.copy(isLoadingMore = false, albums = merge(st.albums))
                            LibrarySectionTab.ARTISTS -> st.copy(isLoadingMore = false, artists = merge(st.artists))
                            LibrarySectionTab.SONGS -> st.copy(isLoadingMore = false, songs = merge(st.songs))
                        }
                    }
                }
                .onFailure {
                    _libraryState.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    private fun loadLibrary(isRefresh: Boolean, silent: Boolean = false) {
        if (loadingLibrary && !isRefresh) return
        loadingLibrary = true
        viewModelScope.launch {
            if (!silent) {
                _libraryState.update {
                    it.copy(
                        isLoading = !isRefresh && !it.hasLoaded,
                        isRefreshing = isRefresh,
                        errorMessage = null
                    )
                }
            }
            runCatching { repository.loadLibraryOverview(forceRefresh = isRefresh || silent) }
                .onSuccess { data ->
                    lastLibraryRefreshMs = System.currentTimeMillis()
                    _libraryState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hasLoaded = true,
                            pinned = data.pinned,
                            recentlyAdded = data.recentlyAdded,
                            playlists = data.playlists,
                            albums = data.albums,
                            artists = data.artists,
                            songs = data.songs,
                            errorMessage = null
                        )
                    }
                    syncFavoriteArtistsInBackground(data.artists.items)
                }
                .onFailure { err ->
                    _libraryState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = err.localizedMessage ?: "No se pudo cargar tu biblioteca."
                        )
                    }
                }
            loadingLibrary = false
        }
    }

    private fun loadRadio(isRefresh: Boolean) {
        if (loadingRadio) return
        loadingRadio = true
        viewModelScope.launch {
            _radioState.update {
                it.copy(
                    isLoading = !isRefresh && it.sections.isEmpty(),
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }
            runCatching { repository.loadRadioFeed() }
                .onSuccess { sections ->
                    _radioState.update {
                        if (sections.isEmpty()) {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = "No se pudo cargar la programación de Radio."
                            )
                        } else {
                            it.copy(isLoading = false, isRefreshing = false, sections = sections, errorMessage = null)
                        }
                    }
                }
                .onFailure { error ->
                    _radioState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage ?: "No se pudo cargar la programación de Radio."
                        )
                    }
                }
            loadingRadio = false
        }
    }

    private fun loadNew(isRefresh: Boolean) {
        if (loadingNew) return
        loadingNew = true
        viewModelScope.launch {
            _newState.update {
                it.copy(
                    isLoading = !isRefresh && it.sections.isEmpty(),
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }
            runCatching { repository.loadNewFeed() }
                .onSuccess { sections ->
                    _newState.update {
                        if (sections.isEmpty()) {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = "No se pudieron cargar las novedades."
                            )
                        } else {
                            it.copy(isLoading = false, isRefreshing = false, sections = sections, errorMessage = null)
                        }
                    }
                }
                .onFailure { error ->
                    _newState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage ?: "No se pudieron cargar las novedades."
                        )
                    }
                }
            loadingNew = false
        }
    }

    private fun loadHome(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && it.sections.isEmpty(),
                    isRefreshing = isRefresh,
                    errorMessage = null,
                    credentials = tokenManager.getCredentials()
                )
            }

            runCatching { repository.loadHomeFeed() }
                .onSuccess { feed ->
                    syncFavoriteArtistsFromFeed(feed.sections)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            mode = feed.mode,
                            sections = withPendingRecent(feed.sections),
                            warningMessage = feed.warningMessage,
                            webLoginRequired = feed.webLoginRequired,
                            errorMessage = null,
                            credentials = tokenManager.getCredentials()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage ?: "No se pudo conectar con Apple Music"
                        )
                    }
                }
        }
    }

    @Volatile
    private var refreshingRecent = false

    private val pendingRecent = LinkedHashMap<String, Pair<HomeItem, Long>>()

    private fun isRecentRow(section: HomeSection) =
        section.id.startsWith("web_7-") || section.id.startsWith("rec_7-")

    private fun withPendingRecent(sections: List<HomeSection>): List<HomeSection> {
        val now = System.currentTimeMillis()
        pendingRecent.entries.removeAll { (_, pair) ->
            val item = pair.first
            (now - pair.second > PENDING_RECENT_TTL_MS) ||
                item.type.contains("video", ignoreCase = true) ||
                item.type.contains("movie", ignoreCase = true)
        }
        if (pendingRecent.isEmpty()) return sections
        return sections.map { section ->
            if (!isRecentRow(section)) return@map section
            val pending = pendingRecent.values.sortedByDescending { it.second }.map { it.first }
            val size = section.items.size.coerceAtLeast(1)
            val merged = (pending + section.items).distinctBy { it.id }.take(size)
            if (merged.map { it.id } == section.items.map { it.id }) section else section.copy(items = merged)
        }
    }

    fun refreshRecentlyPlayed() {
        val state = _uiState.value
        if (refreshingRecent || state.isLoading || state.isRefreshing || state.sections.isEmpty()) return
        refreshingRecent = true
        viewModelScope.launch {
            try {
                val updated = runCatching { repository.refreshRecentlyPlayed(state.sections) }.getOrNull()
                if (updated != null) {
                    updated.firstOrNull { isRecentRow(it) }?.items?.forEach { pendingRecent.remove(it.id) }
                    _uiState.update { current ->
                        if (current.sections === state.sections) current.copy(sections = withPendingRecent(updated))
                        else current
                    }
                }
            } finally {
                refreshingRecent = false
            }
        }
    }

    suspend fun loadMediaDetail(item: HomeItem) =
        runCatching { repository.loadMediaDetail(item) }.getOrNull()

    suspend fun loadArtist(artistId: String) =
        runCatching { repository.loadArtist(artistId) }.getOrNull()

    /** Resuelve un artista por nombre (usado al tocar el artista desde el reproductor). */
    private val resolvedArtistCache = java.util.concurrent.ConcurrentHashMap<String, HomeItem>()

    suspend fun resolveArtistByName(artistName: String): HomeItem? {
        val clean = artistName.trim()
        if (clean.isBlank()) return null
        val cacheKey = clean.lowercase()
        resolvedArtistCache[cacheKey]?.let { return it }
        val fromSuggestions = runCatching { repository.searchSuggestions(clean) }.getOrNull()
            ?.topResults
            ?.firstOrNull { it.type.contains("artist", ignoreCase = true) }
        val resolved = fromSuggestions ?: run {
            val sections = runCatching { repository.searchCatalog(clean) }.getOrDefault(emptyList())
            sections
                .flatMap { it.items }
                .firstOrNull { it.type.contains("artist", ignoreCase = true) }
        }
        // Se cachea para que el selector de artistas (foto de perfil) y abrir el perfil no repitan la búsqueda.
        if (resolved != null) resolvedArtistCache[cacheKey] = resolved
        return resolved
    }

    suspend fun loadReplay(year: Int? = null, monthOption: ReplayPeriodOption? = null) =
        runCatching { repository.loadReplay(year, monthOption) }.getOrNull()

    suspend fun loadSocialProfile(profileId: String) =
        runCatching { repository.loadSocialProfile(profileId) }.getOrNull()

    suspend fun loadListeningHistory(): List<HomeItem> =
        runCatching { repository.loadListeningHistory() }.getOrDefault(emptyList())

    suspend fun loadFullSection(section: HomeSection): List<HomeItem> =
        runCatching { repository.loadFullSection(section) }.getOrDefault(section.items)

    suspend fun loadEditorialSubpage(linkItem: HomeItem): List<HomeSection> =
        runCatching { repository.loadEditorialSubpage(linkItem) }.getOrDefault(emptyList())

    fun onPlayItem(
        item: HomeItem,
        container: HomeItem? = null,
        startIndex: Int = 0,
        shuffle: Boolean = false
    ) {
        val recentEntry = container ?: item
        val isVideoEntry = recentEntry.type.contains("video", ignoreCase = true) ||
            recentEntry.type.contains("movie", ignoreCase = true) ||
            item.type.contains("video", ignoreCase = true) ||
            item.type.contains("movie", ignoreCase = true)
        if (!isVideoEntry && recentEntry.type !in NON_PLAYABLE_TYPES) {
            pendingRecent.remove(recentEntry.id)
            pendingRecent[recentEntry.id] = recentEntry.copy(badge = null) to System.currentTimeMillis()
            _uiState.update { it.copy(sections = withPendingRecent(it.sections)) }
            // Sincronizar automáticamente "Escuchado recientemente" con Apple Music unos segundos después
            viewModelScope.launch {
                delay(6_000L)
                refreshRecentlyPlayed()
            }
        }
        val preloadedTracks = repository.getCachedContainerTracks(container?.id)
        playerManager.playItem(
            item = item,
            container = container,
            startIndex = startIndex,
            shuffle = shuffle,
            preloadedTracks = preloadedTracks
        )
    }

    fun onPlayTrackList(
        tracks: List<HomeItem>,
        startIndex: Int = 0,
        contextTitle: String? = null,
        shuffle: Boolean = false
    ) {
        if (tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        val first = tracks[safeIdx]
        val container = HomeItem(
            id = "tracklist_${contextTitle.orEmpty().hashCode()}",
            type = "playlists",
            title = contextTitle?.takeIf { it.isNotBlank() } ?: first.subtitle.ifBlank { first.title },
            subtitle = first.subtitle,
            artworkUrl = first.artworkUrl
        )
        playerManager.playItem(
            item = first,
            container = container,
            startIndex = safeIdx,
            shuffle = shuffle,
            preloadedTracks = tracks
        )
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun skipNext() = playerManager.skipNext()
    fun skipPrevious() = playerManager.skipPrevious()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun attachVideoHost(host: android.view.ViewGroup, isFallback: Boolean = false) =
        playerManager.attachVideoHost(host, isFallback)
    fun detachVideoHost(host: android.view.ViewGroup) =
        playerManager.detachVideoHost(host)
    fun resumeVideoIfIntended() = playerManager.resumeVideoIfIntended()

    fun playNext(item: HomeItem) {
        playerManager.playNext(item)
        showFeedback("Se reproducirá a continuación: ${item.title}")
    }

    fun playLater(item: HomeItem) {
        playerManager.playLater(item)
        showFeedback("Agregado al final de la cola: ${item.title}")
    }

    fun startStation(item: HomeItem) {
        playerManager.startStationForItem(item)
        showFeedback("Creando estación de ${item.title}...")
    }

    fun skipToQueueIndex(index: Int) = playerManager.skipToQueueIndex(index)
    fun toggleAutoplay() = playerManager.toggleAutoplay()
    fun cycleRepeatMode() = playerManager.cycleRepeatMode()
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun cycleAudioTransitionMode() = playerManager.cycleAudioTransitionMode()
    fun setSleepTimerMinutes(minutes: Int) = playerManager.setSleepTimerMinutes(minutes)
    suspend fun loadArtistArtwork(artistId: String): String? = repository.loadArtistArtwork(artistId)

    private val _favoriteOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val favoriteOverrides: StateFlow<Map<String, Boolean>> = _favoriteOverrides.asStateFlow()

    /**
     * Marca con estrella ★ a los artistas de la fila "Artistas favoritos" de Home.
     * La fila se detecta por título (contiene "favorit"/"favori"...) o, si Apple la titula distinto, por ser una fila
     * de artistas en círculos cuyos artistas son todos de TU biblioteca (ids `r.*`); las filas de recomendaciones
     * traen artistas de catálogo (ids numéricos) y no se tocan.
     */
    private fun syncFavoriteArtistsFromFeed(sections: List<com.applemusic.android.data.model.HomeSection>) {
        val favSections = sections.filter { sec ->
            favoriteShelfRegex.containsMatchIn(sec.title) ||
                (sec.style == com.applemusic.android.data.model.HomeSectionStyle.ARTIST_CIRCLES &&
                    sec.items.isNotEmpty() &&
                    sec.items.all {
                        it.type.contains("library-artist", ignoreCase = true) ||
                            it.id.startsWith("r.") ||
                            it.id.startsWith("ra.a-")
                    })
        }
        if (favSections.isEmpty()) return
        val favArtistMap = LinkedHashMap<String, Boolean>()
        favSections
            .flatMap { it.items }
            .filter {
                it.type.isBlank() ||
                    it.type.contains("artist", ignoreCase = true) ||
                    it.id.startsWith("ra.a-")
            }
            .forEach { art ->
                artistKeysOf(art).forEach { k -> favArtistMap[k] = true }
            }
        // Lo que el usuario quitó en esta sesión no se vuelve a marcar aunque la fila tarde en refrescarse.
        favArtistMap.keys.removeAll(locallyUnfavoritedArtistKeys)
        synchronized(shelfFavoriteArtistKeys) {
            shelfFavoriteArtistKeys.clear()
            shelfFavoriteArtistKeys.addAll(favArtistMap.keys)
        }
        _favoriteOverrides.update { map -> map + favArtistMap }

        // Además verificar en lote contra /v1/me/ratings/artists para sincronización automática real
        val allHomeArtists = sections.flatMap { it.items }.filter {
            it.type.contains("artist", ignoreCase = true) || it.id.startsWith("ra.a-")
        }
        if (allHomeArtists.isNotEmpty()) {
            syncFavoriteArtistsInBackground(allHomeArtists)
        }
    }

    private fun syncFavoriteArtistsInBackground(artists: List<HomeItem>) {
        if (artists.isEmpty()) return
        viewModelScope.launch {
            val synced = runCatching { repository.loadFavoriteIdsForArtists(artists) }.getOrDefault(emptyMap())
            if (synced.isNotEmpty()) {
                val filtered = synced.filterKeys { it !in locallyUnfavoritedArtistKeys }
                if (filtered.isNotEmpty()) {
                    synchronized(shelfFavoriteArtistKeys) {
                        shelfFavoriteArtistKeys.addAll(filtered.keys)
                    }
                    _favoriteOverrides.update { map -> map + filtered }
                }
            }
        }
    }

    private fun artistKeysOf(item: HomeItem): List<String> {
        val stripped = if (item.id.startsWith("ra.a-")) item.id.removePrefix("ra.a-") else null
        return listOfNotNull(
            item.id.takeIf { it.isNotBlank() },
            stripped?.takeIf { it.isNotBlank() },
            item.catalogId?.takeIf { it.isNotBlank() },
            item.title.takeIf { it.isNotBlank() }?.let { artistFavoriteNameKey(it) }
        ).distinct()
    }

    suspend fun loadItemStatus(item: HomeItem): ItemAccountStatus? {
        val status = runCatching { repository.loadItemStatus(item) }.getOrNull()
        val isArtist = item.type.contains("artist", ignoreCase = true) || item.id.startsWith("ra.a-")
        val artistKeys = if (isArtist) artistKeysOf(item) else emptyList()
        val baseKeys = listOfNotNull(
            item.id.takeIf { it.isNotBlank() },
            item.catalogId?.takeIf { it.isNotBlank() },
            status?.catalogId?.takeIf { it.isNotBlank() },
            status?.libraryId?.takeIf { it.isNotBlank() }
        )
        val extraStatusKeys = status?.let { st ->
            if (isArtist) listOfNotNull(st.catalogId, st.libraryId).filter { it.isNotBlank() } else emptyList()
        }.orEmpty()
        val allKeys = (baseKeys + artistKeys + extraStatusKeys).distinct()
        val inShelf = isArtist &&
            allKeys.any { it in shelfFavoriteArtistKeys } &&
            allKeys.none { it in locallyUnfavoritedArtistKeys }
        val effectiveFavorite = (status?.isFavorite == true) || inShelf
        if (status != null || isArtist) {
            _favoriteOverrides.update { map ->
                map + allKeys.associateWith { effectiveFavorite }
            }
        }
        return status
    }

    fun syncFavoritesForAlbum(album: com.applemusic.android.data.model.AlbumDetail) {
        viewModelScope.launch {
            val favs = runCatching { repository.loadFavoriteIdsForAlbum(album) }.getOrDefault(emptyMap())
            if (favs.isNotEmpty()) {
                _favoriteOverrides.update { map -> map + favs }
            }
        }
    }

    fun toggleFavorite(item: HomeItem, currentlyFavorite: Boolean?) {
        val isArtist = item.type.contains("artist", ignoreCase = true) || item.id.startsWith("ra.a-")
        val keys = if (isArtist) artistKeysOf(item) else listOfNotNull(
            item.id.takeIf { it.isNotBlank() },
            item.catalogId?.takeIf { it.isNotBlank() }
        )
        val prev = currentlyFavorite
            ?: keys.firstNotNullOfOrNull { _favoriteOverrides.value[it] }
            ?: false
        val target = !prev
        _favoriteOverrides.update { map -> map + keys.associateWith { target } }
        if (isArtist) {
            if (target) {
                locallyUnfavoritedArtistKeys.removeAll(keys.toSet())
                shelfFavoriteArtistKeys.addAll(keys)
            } else {
                locallyUnfavoritedArtistKeys.addAll(keys)
                shelfFavoriteArtistKeys.removeAll(keys.toSet())
            }
            updateFavoriteArtistsShelfOptimistically(item, target)
        }
        viewModelScope.launch {
            val ok = runCatching { repository.setFavorite(item, target) }.getOrDefault(false)
            if (ok) {
                showFeedback(if (target) "Agregado a favoritos ★" else "Quitado de favoritos")
                if (isArtist) {
                    loadHome(isRefresh = true)
                }
            } else {
                _favoriteOverrides.update { map -> map + keys.associateWith { prev } }
                if (isArtist) {
                    if (prev) {
                        locallyUnfavoritedArtistKeys.removeAll(keys.toSet())
                        shelfFavoriteArtistKeys.addAll(keys)
                    } else {
                        locallyUnfavoritedArtistKeys.addAll(keys)
                        shelfFavoriteArtistKeys.removeAll(keys.toSet())
                    }
                    updateFavoriteArtistsShelfOptimistically(item, prev)
                }
                showFeedback("No se pudo actualizar favoritos")
            }
        }
    }

    private fun updateFavoriteArtistsShelfOptimistically(artist: HomeItem, makeFavorite: Boolean) {
        val keys = artistKeysOf(artist).toSet()
        _uiState.update { state ->
            val idx = state.sections.indexOfFirst { sec ->
                favoriteShelfRegex.containsMatchIn(sec.title) ||
                    (sec.style == com.applemusic.android.data.model.HomeSectionStyle.ARTIST_CIRCLES &&
                        sec.items.any { artistKeysOf(it).any { k -> k in keys } })
            }
            if (idx < 0) return@update state
            val updatedSections = state.sections.toMutableList()
            val shelf = updatedSections[idx]
            val filtered = shelf.items.filterNot { item -> artistKeysOf(item).any { k -> k in keys } }
            val newItems = if (makeFavorite) {
                val entry = artist.copy(
                    id = artist.catalogId?.takeIf { it.all(Char::isDigit) } ?: artist.id.removePrefix("ra.a-"),
                    type = "artists",
                    subtitle = ""
                )
                listOf(entry) + filtered
            } else {
                filtered
            }
            updatedSections[idx] = shelf.copy(items = newItems)
            state.copy(sections = updatedSections)
        }
    }

    fun isPinned(item: HomeItem): Boolean {
        val keys = setOfNotNull(item.id.takeIf { it.isNotBlank() }, item.catalogId?.takeIf { it.isNotBlank() })
        if (keys.isEmpty()) return false
        return _libraryState.value.pinned.any { it.id in keys || (it.catalogId != null && it.catalogId in keys) }
    }

    fun togglePin(item: HomeItem) {
        val currentlyPinned = isPinned(item)
        val makePinned = !currentlyPinned
        val keys = setOfNotNull(item.id.takeIf { it.isNotBlank() }, item.catalogId?.takeIf { it.isNotBlank() })
        // Actualización instantánea en memoria sin esperar refresco manual
        _libraryState.update { st ->
            val filtered = st.pinned.filterNot { it.id in keys || (it.catalogId != null && it.catalogId in keys) }
            val nextPinned = if (makePinned) (listOf(item) + filtered).take(6) else filtered
            st.copy(pinned = nextPinned)
        }
        showFeedback(if (makePinned) "Fijado en Biblioteca" else "Quitado de Fijados")
        viewModelScope.launch {
            val synced = runCatching { repository.setLibraryPin(item, makePinned) }.getOrNull()
            if (synced != null) {
                _libraryState.update { it.copy(pinned = synced) }
            }
        }
    }

    fun toggleInLibrary(item: HomeItem, currentlyInLibrary: Boolean, libraryId: String? = null) {
        val target = !currentlyInLibrary
        val keys = setOfNotNull(
            item.id.takeIf { it.isNotBlank() },
            item.catalogId?.takeIf { it.isNotBlank() },
            libraryId?.takeIf { it.isNotBlank() }
        )
        viewModelScope.launch {
            val ok = runCatching { repository.setInLibrary(item, target, libraryId) }.getOrDefault(false)
            if (ok) {
                showFeedback(if (target) "Agregado a tu biblioteca" else "Eliminado de tu biblioteca")
                // Actualizar "Agregado recientemente" al instante sin esperar recarga manual
                _libraryState.update { st ->
                    val filteredRecent = st.recentlyAdded.items.filterNot {
                        it.id in keys || (it.catalogId != null && it.catalogId in keys)
                    }
                    val nextRecent = if (target) listOf(item) + filteredRecent else filteredRecent
                    st.copy(recentlyAdded = st.recentlyAdded.copy(items = nextRecent))
                }
                delay(800L)
                loadLibrary(isRefresh = true, silent = true)
            } else {
                showFeedback("No se pudo actualizar tu biblioteca")
            }
        }
    }

    suspend fun loadUserPlaylists(): List<HomeItem> =
        runCatching { repository.loadUserEditablePlaylists() }.getOrDefault(emptyList())

    fun addToPlaylist(playlistId: String, playlistName: String, item: HomeItem) {
        viewModelScope.launch {
            val ok = runCatching { repository.addItemToPlaylist(playlistId, item) }.getOrDefault(false)
            showFeedback(
                if (ok) "Agregado a \"$playlistName\""
                else "No se pudo agregar a \"$playlistName\""
            )
        }
    }

    fun createPlaylistAndAdd(name: String, item: HomeItem?) {
        viewModelScope.launch {
            val ok = runCatching { repository.createPlaylistWithItem(name, item) }.getOrDefault(false)
            if (ok) {
                showFeedback("Playlist \"$name\" creada")
                loadLibraryIfNeeded(force = true)
            } else {
                showFeedback("No se pudo crear la playlist")
            }
        }
    }

    fun deletePlaylist(playlistItem: HomeItem, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { repository.deleteUserPlaylist(playlistItem.id) }.getOrDefault(false)
            if (ok) {
                _libraryState.update { st ->
                    st.copy(
                        playlists = st.playlists.copy(items = st.playlists.items.filterNot { it.id == playlistItem.id }),
                        recentlyAdded = st.recentlyAdded.copy(items = st.recentlyAdded.items.filterNot { it.id == playlistItem.id })
                    )
                }
                showFeedback("Playlist \"${playlistItem.title}\" eliminada")
                onDeleted()
                loadLibraryIfNeeded(force = true)
            } else {
                showFeedback("No se pudo eliminar la playlist")
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackItem: HomeItem, onRemoved: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { repository.removeTrackFromPlaylist(playlistId, trackItem) }.getOrDefault(false)
            if (ok) {
                showFeedback("Se eliminó \"${trackItem.title}\" de la playlist")
                onRemoved()
                loadLibraryIfNeeded(force = true)
            } else {
                showFeedback("No se pudo quitar de la playlist")
            }
        }
    }

    fun refreshMySocialProfile() {
        viewModelScope.launch {
            val profile = runCatching { repository.loadMySocialProfile() }.getOrNull()
            if (profile != null) settingsManager.saveUserProfile(profile)
        }
    }

    fun setFontOption(option: AppFontOption) {
        settingsManager.setFontOption(option)
        showFeedback("Fuente cambiada a ${option.label}")
    }

    fun setLanguageOption(option: AppLanguageOption) {
        settingsManager.setLanguageOption(option)
        repository.invalidateFeedAndDetailCaches()
        showFeedback("Idioma actualizado a ${option.label}")
        loadHome(isRefresh = true)
        loadNew(isRefresh = true)
        loadRadio(isRefresh = true)
        loadLibrary(isRefresh = true)
        refreshSearchCategories()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        settingsManager.setCrossfadeSeconds(seconds)
        playerManager.setAudioTransitionConfig(seconds, settingsManager.state.value.autoMixEnabled)
    }

    fun setAutoMixEnabled(enabled: Boolean) {
        settingsManager.setAutoMixEnabled(enabled)
        playerManager.setAudioTransitionConfig(settingsManager.state.value.crossfadeSeconds, enabled)
    }

    fun setEqualizerEnabled(enabled: Boolean) = settingsManager.setEqualizerEnabled(enabled)
    fun applyEqualizerPreset(index: Int) = settingsManager.applyEqualizerPreset(index)
    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) = settingsManager.setEqualizerBandLevel(bandIndex, levelMb)
    fun setBassBoostStrength(strength: Int) = settingsManager.setBassBoostStrength(strength)

    fun refreshCacheStats() {
        viewModelScope.launch { settingsManager.refreshCacheStats() }
    }

    fun setCacheLimitMb(limitMb: Int) {
        settingsManager.setCacheLimitMb(limitMb)
        viewModelScope.launch { settingsManager.trimCachesToLimit() }
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            settingsManager.clearAllCaches()
            repository.invalidateFeedAndDetailCaches()
            showFeedback("Caché liberada")
        }
    }

    suspend fun loadSongCredits(item: HomeItem): SongCreditsDetail? =
        runCatching { repository.loadSongCredits(item) }.getOrNull()

    suspend fun loadLyrics(item: HomeItem): SongLyrics? =
        runCatching { repository.loadLyrics(item) }.getOrNull()

    suspend fun loadSongMotionArtwork(item: HomeItem): String? =
        runCatching { repository.loadSongMotionArtwork(item) }.getOrNull()

    suspend fun loadSongAudioTraits(item: HomeItem): List<String> =
        runCatching { repository.loadSongAudioTraits(item) }.getOrDefault(emptyList())

    fun setAtmosMode(mode: AtmosPreferenceMode) {
        playerManager.setAtmosMode(mode)
        showFeedback("Dolby Atmos: ${mode.label}")
    }

    fun showFeedback(message: String) {
        _uiState.update { it.copy(authFeedbackMessage = message) }
    }

    fun onWebSessionCaptured(userToken: String, devToken: String) {
        tokenManager.saveWebSession(webUserToken = userToken, webDeveloperToken = devToken)
        playerManager.prewarmWebPlayer()
        _uiState.update {
            it.copy(
                authFeedbackMessage = "Sesión de Apple Music conectada.",
                webLoginRequired = false,
                credentials = tokenManager.getCredentials()
            )
        }
        refreshMySocialProfile()
        loadHome(isRefresh = true)
        loadLibraryIfNeeded(force = true)
    }

    fun signOut() {
        tokenManager.clearRuntimeCredentials()
        settingsManager.saveUserProfile(null)
        _uiState.update {
            it.copy(
                authFeedbackMessage = "Sesión cerrada.",
                webLoginRequired = true,
                credentials = tokenManager.getCredentials()
            )
        }
        _libraryState.value = LibraryUiState(isLoading = false, hasLoaded = false)
        loadHome(isRefresh = true)
    }

    fun consumeAuthFeedback() {
        _uiState.update { it.copy(authFeedbackMessage = null) }
    }

    override fun onCleared() {
        settingsManager.release()
        playerManager.release()
        super.onCleared()
    }

    private companion object {
        val NON_PLAYABLE_TYPES = setOf("concerts", "social-upsells", "editorial-items", "social-profiles")
    }
}
