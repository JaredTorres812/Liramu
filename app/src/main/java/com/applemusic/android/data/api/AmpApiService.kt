package com.applemusic.android.data.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Cliente directo contra `https://amp-api.music.apple.com/` usando el Developer Token web
 * (`AMPWebPlay`) y el token de sesión del usuario (`Music-User-Token` / `media-user-token`),
 * igual que el reproductor web oficial sin depender de un WebView oculto.
 */
interface AmpApiService {

    @GET("v1/me/recommendations")
    suspend fun getListenNowRaw(
        @Query("name") name: String = "listen-now",
        @Query("platform") platform: String = "web",
        @Query("timezone") timezone: String,
        @Query("l") locale: String? = null,
        @Query("with") with: String = "friendsMix,library,social",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("art[social-profiles:url]") socialArtUrl: String = "c",
        @Query("relate[editorial-items]") relateEditorialItems: String = "contents",
        @Query("displayFilter[kind]") displayKinds: String = DISPLAY_KINDS,
        @Query("extend") extend: String = "editorialArtwork,editorialCard,editorialVideo,plainEditorialCard,plainEditorialNotes",
        @Query("extend[albums]") extendAlbums: String = "artistUrl",
        @Query("extend[library-albums]") extendLibraryAlbums: String = "artistUrl,editorialVideo",
        @Query("extend[playlists]") extendPlaylists: String = "artistNames,editorialArtwork,editorialVideo",
        @Query("extend[library-playlists]") extendLibraryPlaylists: String = "artistNames,editorialArtwork,editorialVideo",
        @Query("extend[social-profiles]") extendSocialProfiles: String = "topGenreNames",
        @Query("extend[stations]") extendStations: String = "airDate,airTime,supportsAirTimeUpdates",
        @Query("fields[artists]") artistFields: String = "name,artwork,url",
        @Query("format[resources]") resourceFormat: String = "map",
        @Query("include[albums]") includeAlbums: String = "artists",
        @Query("include[songs]") includeSongs: String = "artists",
        @Query("include[music-videos]") includeMusicVideos: String = "artists",
        @Query("include[library-playlists]") includeLibraryPlaylists: String = "catalog",
        @Query("include[library-albums]") includeLibraryAlbums: String = "catalog",
        @Query("include[personal-recommendation]") includePersonalRec: String = "primary-content",
        @Query("include[stations]") includeStations: String = "radio-show",
        @Query("meta[stations]") metaStations: String = "inflectionPoints",
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("types") types: String = TYPES_HOME
    ): ResponseBody

    @GET
    suspend fun getByUrlRaw(@Url url: String): ResponseBody

    @GET("v1/me/recommendations/{id}/view/see-all")
    suspend fun getSeeAllRaw(
        @Path("id") recommendationId: String,
        @Query("action") action: String = "refresh",
        @Query("art[url]") artUrl: String = "f",
        @Query("extend") extend: String = "artistUrl,editorialArtwork,plainEditorialNotes",
        @Query("extend[playlists]") extendPlaylists: String = "artistNames",
        @Query("format[resources]") resourceFormat: String = "map",
        @Query("include[library-playlists]") includeLibraryPlaylists: String = "catalog",
        @Query("include[library-albums]") includeLibraryAlbums: String = "catalog",
        @Query("include[personal-recommendation]") includePersonalRec: String = "contents",
        @Query("limit[contents]") limitContents: Int = 100,
        @Query("name") name: String = "listen-now",
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("platform") platform: String = "web",
        @Query("types") types: String = TYPES_SEE_ALL,
        @Query("with") with: String = "friendsMix,library,social",
        @Query("l") locale: String? = null,
        @Query("timezone") timezone: String
    ): ResponseBody

    @GET("v1/me/storefront")
    suspend fun getMyStorefrontRaw(): ResponseBody

    @GET("v1/me/recent/played")
    suspend fun getRecentlyPlayedRaw(
        @Query("l") locale: String? = null,
        @Query("limit") limit: Int = 12,
        @Query("offset") offset: Int = 0,
        @Query("include") include: String = "tracks",
        @Query("include[albums]") includeAlbums: String = "catalog,tracks,artists",
        @Query("include[library-albums]") includeLibraryAlbums: String = "catalog",
        @Query("include[library-playlists]") includeLibraryPlaylists: String = "catalog",
        @Query("include[songs]") includeSongs: String = "catalog,artists",
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/recent/played/tracks")
    suspend fun getRecentPlayedTracksRaw(
        @Query("l") locale: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/catalog/{storefront}/albums/{id}")
    suspend fun getAlbumRaw(
        @Path("storefront") storefront: String,
        @Path("id") albumId: String,
        @Query("include") include: String = "tracks,artists,record-labels",
        @Query("extend") extend: String = "editorialNotes,editorialArtwork,editorialVideo,audioTraits",
        @Query("views") views: String = "appears-on,more-by-artist,other-versions,related-videos,you-might-also-like",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/albums/{id}")
    suspend fun getLibraryAlbumRaw(
        @Path("id") albumId: String,
        @Query("include") include: String = "tracks,catalog,artists",
        @Query("extend[albums]") extendAlbums: String = "editorialArtwork,editorialVideo,audioTraits",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/catalog/{storefront}/playlists/{id}")
    suspend fun getCatalogPlaylistRaw(
        @Path("storefront") storefront: String,
        @Path("id") playlistId: String,
        @Query("include") include: String = "tracks,curator",
        @Query("extend") extend: String = "editorialNotes,editorialArtwork,editorialVideo,artistNames",
        @Query("views") views: String = "featured-artists,more-by-curator",
        @Query("limit[view.more-by-curator]") moreByCuratorLimit: Int = 15,
        @Query("limit[view.featured-artists]") featuredArtistsLimit: Int = 15,
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/playlists/{id}")
    suspend fun getLibraryPlaylistRaw(
        @Path("id") playlistId: String,
        @Query("include") include: String = "tracks,catalog",
        @Query("include[library-songs]") includeLibrarySongs: String = "catalog,artists,albums",
        @Query("extend[playlists]") extendPlaylists: String = "editorialArtwork,editorialVideo",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/catalog/{storefront}/playlists/{id}/tracks")
    suspend fun getPlaylistTracksRaw(
        @Path("storefront") storefront: String,
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/catalog/{storefront}/artists/{id}")
    suspend fun getArtistRaw(
        @Path("storefront") storefront: String,
        @Path("id") artistId: String,
        @Query("views") views: String = "latest-release,top-songs,featured-albums,full-albums,singles,live-albums,compilation-albums,appears-on-albums,featured-playlists,playlists,top-music-videos,music-videos,similar-artists",
        @Query("extend") extend: String = "artistBio,bornOrFormed,editorialArtwork,editorialVideo,hero,origin",
        @Query("include[songs]") includeSongs: String = "albums",
        @Query("limit[artists:top-songs]") topSongsLimit: Int = 20,
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/catalog/{storefront}/artists")
    suspend fun getCatalogArtistsBatchRaw(
        @Path("storefront") storefront: String,
        @Query("ids") ids: String,
        @Query("extend") extend: String = "editorialArtwork",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    /** Categorías visuales reales de la pestaña Buscar (`name=search-landing`). */
    @GET("v1/recommendations/{storefront}")
    suspend fun getSearchLandingRaw(
        @Path("storefront") storefront: String,
        @Query("name") name: String = "search-landing",
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("extend") extend: String = "editorialArtwork,editorialCard,plainEditorialNotes",
        @Query("relate[editorial-items]") relateEditorialItems: String = "contents",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("format[resources]") resourceFormat: String = "map",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    /** Mapa de amigos que escucharon cada ítem (`badgingMap`). */
    @GET("v1/social/badging-map")
    suspend fun getSocialBadgingMapRaw(): ResponseBody

    @GET("v1/social/{storefront}/social-profiles")
    suspend fun getSocialProfilesBatchRaw(
        @Path("storefront") storefront: String,
        @Query("ids") ids: String,
        @Query("art[url]") artUrl: String = "c",
        @Query("extend") extend: String = "topGenreNames",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/social/{storefront}/social-profiles/{id}")
    suspend fun getSocialProfileRaw(
        @Path("storefront") storefront: String,
        @Path("id") profileId: String,
        @Query("include") include: String = "shared-playlists",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("extend") extend: String = "topGenreNames",
        @Query("l") locale: String? = null
    ): ResponseBody

    /** Respaldo editorial de Explorar/Inicio/Novedades. */
    @GET("v1/editorial/{storefront}/groupings")
    suspend fun getEditorialGroupingsRaw(
        @Path("storefront") storefront: String,
        @Query("name") name: String = "music",
        @Query("platform") platform: String = "web",
        @Query("l") locale: String? = null,
        @Query("extend") extend: String = "editorialArtwork,editorialCard,editorialVideo,plainEditorialCard,plainEditorialNotes,artistUrl",
        @Query("extend[playlists]") extendPlaylists: String = "artistNames,editorialArtwork,editorialVideo",
        @Query("include[albums]") includeAlbums: String = "artists",
        @Query("include[songs]") includeSongs: String = "artists",
        @Query("include[music-videos]") includeMusicVideos: String = "artists",
        @Query("relate[editorial-items]") relateEditorialItems: String = "contents",
        @Query("relate[editorial-elements]") relateEditorialElements: String = "contents",
        @Query("omit[resource:artists]") omitArtistsRel: String = "relationships",
        @Query("fields[artists]") artistFields: String = "name,url,artwork,editorialArtwork,genreNames,editorialNotes",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("format[resources]") resourceFormat: String = "map",
        @Query("extend[stations]") extendStations: String? = null,
        @Query("include[stations]") includeStations: String? = null
    ): ResponseBody

    @GET("v1/editorial/{storefront}/rooms/{id}")
    suspend fun getEditorialRoomRaw(
        @Path("storefront") storefront: String,
        @Path("id") roomId: String,
        @Query("platform") platform: String = "web",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/editorial/{storefront}/rooms/{id}/contents")
    suspend fun getEditorialRoomContentsRaw(
        @Path("storefront") storefront: String,
        @Path("id") roomId: String,
        @Query("platform") platform: String = "web",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/catalog/{storefront}/{curatorType}/{id}/playlists")
    suspend fun getCuratorPlaylistsRaw(
        @Path("storefront") storefront: String,
        @Path("curatorType") curatorType: String,
        @Path("id") curatorId: String,
        @Query("limit") limit: Int = 50,
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/catalog/{storefront}/{curatorType}/{id}")
    suspend fun getCuratorRaw(
        @Path("storefront") storefront: String,
        @Path("curatorType") curatorType: String,
        @Path("id") curatorId: String,
        @Query("include") include: String = "playlists,grouping",
        @Query("platform") platform: String = "web",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/editorial/{storefront}/groupings/{id}")
    suspend fun getEditorialGroupingByIdRaw(
        @Path("storefront") storefront: String,
        @Path("id") groupingId: String,
        @Query("platform") platform: String = "web",
        @Query("l") locale: String? = null,
        @Query("extend") extend: String = "editorialArtwork,editorialCard,editorialVideo,plainEditorialCard,plainEditorialNotes,artistUrl",
        @Query("extend[playlists]") extendPlaylists: String = "artistNames,editorialArtwork,editorialVideo",
        @Query("include[albums]") includeAlbums: String = "artists",
        @Query("include[songs]") includeSongs: String = "artists",
        @Query("include[music-videos]") includeMusicVideos: String = "artists",
        @Query("relate[editorial-items]") relateEditorialItems: String = "contents",
        @Query("relate[editorial-elements]") relateEditorialElements: String = "contents",
        @Query("omit[resource:artists]") omitArtistsRel: String = "relationships",
        @Query("fields[artists]") artistFields: String = "name,url,artwork,editorialArtwork,genreNames,editorialNotes",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("format[resources]") resourceFormat: String = "map"
    ): ResponseBody

    @GET("v1/editorial/{storefront}/multiplex/{id}")
    suspend fun getEditorialMultiplexRaw(
        @Path("storefront") storefront: String,
        @Path("id") multiplexId: String,
        @Query("extend") extend: String = "editorialArtwork,editorialCard,plainEditorialCard,plainEditorialNotes,artistUrl",
        @Query("extend[playlists]") extendPlaylists: String = "artistNames,editorialArtwork",
        @Query("relate[editorial-items]") relateEditorialItems: String = "contents",
        @Query("relate[editorial-elements]") relateEditorialElements: String = "contents",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("format[resources]") resourceFormat: String = "map",
        @Query("platform") platform: String = "web",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/catalog/{storefront}/charts")
    suspend fun getCatalogChartsFullRaw(
        @Path("storefront") storefront: String,
        @Query("types") types: String = "songs,albums,playlists,music-videos",
        @Query("genre") genreId: String? = null,
        @Query("with") with: String = "dailyGlobalTopCharts,cityCharts",
        @Query("limit") limit: Int = 25,
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/catalog/{storefront}/search")
    suspend fun searchCatalogRaw(
        @Path("storefront") storefront: String,
        @Query("term") term: String,
        @Query("types") types: String = "activities,albums,apple-curators,artists,curators,editorial-items,music-movies,music-videos,playlists,record-labels,songs,stations",
        @Query("with") with: String = "topResults,serverBubbles,lyrics,lyricHighlights",
        @Query("extend") extend: String = "artistUrl,editorialArtwork,editorialVideo",
        @Query("limit") limit: Int = 25,
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    /** Perfiles sociales y playlists de usuarios en Buscar (`/v1/social/{storefront}/search`, igual que Apple Music Web). */
    @GET("v1/social/{storefront}/search")
    suspend fun searchSocialRaw(
        @Path("storefront") storefront: String,
        @Query("term") term: String,
        @Query("types") types: String = "social-profiles,playlists",
        @Query("with") with: String = "serverBubbles,lyrics",
        @Query("limit") limit: Int = 20,
        @Query("art[social-profiles:url]") socialArtUrl: String = "c",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/catalog/{storefront}/search/suggestions")
    suspend fun searchSuggestionsRaw(
        @Path("storefront") storefront: String,
        @Query("term") term: String,
        @Query("kinds") kinds: String = "terms,topResults",
        @Query("types") types: String = "activities,albums,artists,editorial-items,music-movies,music-videos,playlists,record-labels,songs,stations",
        @Query("limit") limit: Int = 10,
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/recently-added")
    suspend fun getLibraryRecentlyAddedRaw(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("include[library-albums]") includeAlbums: String = "catalog",
        @Query("include[library-playlists]") includePlaylists: String = "catalog",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/playlists")
    suspend fun getLibraryPlaylistsRaw(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("include") include: String = "catalog",
        @Query("extend") extend: String = "editorialArtwork",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/albums")
    suspend fun getLibraryAlbumsRaw(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("include") include: String = "catalog",
        @Query("extend") extend: String = "editorialArtwork",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/artists")
    suspend fun getLibraryArtistsRaw(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("include") include: String = "catalog",
        @Query("extend") extend: String = "editorialArtwork",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/artists/{id}")
    suspend fun getLibraryArtistRaw(
        @Path("id") artistId: String,
        @Query("include") include: String = "catalog,albums",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/songs")
    suspend fun getLibrarySongsRaw(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("include") include: String = "catalog,albums,artists",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/search")
    suspend fun searchLibraryRaw(
        @Query("term") term: String,
        @Query("types") types: String = "library-songs,library-albums,library-artists,library-playlists,library-music-videos",
        @Query("limit") limit: Int = 25,
        @Query("include[library-artists]") includeArtists: String = "catalog",
        @Query("include[library-songs]") includeSongs: String = "catalog,albums,artists",
        @Query("include[library-albums]") includeAlbums: String = "catalog",
        @Query("include[library-playlists]") includePlaylists: String = "catalog",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    // -----------------------------------------------------------------------------------------
    // Favoritos (★), Biblioteca, Playlists, Créditos y Letras sincronizadas (Apple Music)
    // -----------------------------------------------------------------------------------------

    @GET("v1/catalog/{storefront}")
    suspend fun getCatalogItemStatusRaw(
        @Path("storefront") storefront: String,
        @QueryMap ids: Map<String, String>,
        @Query("extend") extend: String = "inFavorites,personalRating,inLibrary",
        @Query("relate") relate: String = "library",
        @Query("include") include: String? = null,
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library")
    suspend fun getLibraryItemStatusRaw(
        @QueryMap ids: Map<String, String>,
        @Query("extend") extend: String = "inFavorites,personalRating,inLibrary",
        @Query("include") include: String? = null,
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/ratings/{type}")
    suspend fun getRatingsBatchRaw(
        @Path("type") type: String,
        @Query("ids") ids: String,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/pins")
    suspend fun getLibraryPinsRaw(
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web",
        @Query("include") include: String = "albums,playlists,artists,songs,library-albums,library-playlists,library-artists,library-songs",
        @Query("extend") extend: String = "editorialArtwork",
        @Query("limit") limit: Int = 25
    ): ResponseBody

    @POST("v1/me/library/pins")
    suspend fun addLibraryPinRaw(
        @QueryMap ids: Map<String, String>,
        @Query("platform") platform: String = "web"
    ): Response<ResponseBody>

    @DELETE("v1/me/library/pins")
    suspend fun removeLibraryPinRaw(
        @QueryMap ids: Map<String, String>,
        @Query("platform") platform: String = "web"
    ): Response<ResponseBody>

    @POST("v1/me/favorites")
    suspend fun addFavoriteRaw(
        @QueryMap ids: Map<String, String>
    ): Response<ResponseBody>

    @DELETE("v1/me/favorites")
    suspend fun removeFavoriteRaw(
        @QueryMap ids: Map<String, String>
    ): Response<ResponseBody>

    @PUT("v1/me/ratings/{type}/{id}")
    suspend fun updateRatingRaw(
        @Path("type") type: String,
        @Path("id") id: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @DELETE("v1/me/ratings/{type}/{id}")
    suspend fun deleteRatingRaw(
        @Path("type") type: String,
        @Path("id") id: String
    ): Response<ResponseBody>

    @POST("v1/me/library")
    suspend fun addToLibraryRaw(
        @QueryMap ids: Map<String, String>
    ): Response<ResponseBody>

    @DELETE("v1/me/library/{type}/{id}")
    suspend fun removeFromLibraryRaw(
        @Path("type") type: String,
        @Path("id") id: String
    ): Response<ResponseBody>

    @POST("v1/me/library/playlists/{id}/tracks")
    suspend fun addTracksToPlaylistRaw(
        @Path("id") playlistId: String,
        @Body body: RequestBody,
        @Query("representation") representation: String = "resources"
    ): Response<ResponseBody>

    @DELETE("v1/me/library/playlists/{id}/tracks")
    suspend fun removeTracksFromPlaylistRaw(
        @Path("id") playlistId: String,
        @QueryMap ids: Map<String, String>,
        @Query("mode") mode: String = "all"
    ): Response<ResponseBody>

    @GET("v1/me/social/profile")
    suspend fun getMySocialProfileRaw(
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/catalog/{storefront}/stations/{id}")
    suspend fun getStationDetailsRaw(
        @Path("storefront") storefront: String,
        @Path("id") stationId: String,
        @Query("include") include: String = "events,radio-show",
        @Query("extend[stations]") extendStations: String = "airDate,airTime,supportsAirTimeUpdates,editorialArtwork",
        @Query("extend[station-events]") extendEvents: String = "editorialArtwork,editorialVideo",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @POST("v1/me/library/playlists")
    suspend fun createPlaylistRaw(
        @Body body: RequestBody
    ): ResponseBody

    @GET("v1/catalog/{storefront}/songs/{id}")
    suspend fun getSongDetailsAndCreditsRaw(
        @Path("storefront") storefront: String,
        @Path("id") songId: String,
        @Query("include") include: String = "albums,artists,credits,lyrics,music-videos",
        @Query("extend") extend: String = "lyricsExcerpt,offers,audioTraits",
        @Query("extend[albums]") extendAlbums: String = "editorialVideo,editorialArtwork",
        @Query("fields[artists]") artistFields: String = "name,url,artwork",
        @Query("fields[albums]") albumFields: String = "artistName,artistUrl,artwork,name,url,editorialVideo,editorialArtwork",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/me/library/songs/{id}")
    suspend fun getLibrarySongRaw(
        @Path("id") librarySongId: String,
        @Query("include") include: String = "catalog,albums,artists",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    @GET("v1/catalog/{storefront}/songs/{id}/syllable-lyrics")
    suspend fun getSyllableLyricsRaw(
        @Path("storefront") storefront: String,
        @Path("id") songId: String,
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/catalog/{storefront}/songs/{id}/lyrics")
    suspend fun getLyricsRaw(
        @Path("storefront") storefront: String,
        @Path("id") songId: String,
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/catalog/{storefront}/music-videos/{id}")
    suspend fun getMusicVideoDetailsRaw(
        @Path("storefront") storefront: String,
        @Path("id") videoId: String,
        @Query("include") include: String = "albums,artists",
        @Query("art[url]") artUrl: String = "c,f",
        @Query("l") locale: String? = null,
        @Query("platform") platform: String = "web"
    ): ResponseBody

    /** Apple Music Replay anual/mensual (como `replay.music.apple.com` y `replay.ejs`). */
    @GET("v1/me/music-summaries/search")
    suspend fun getReplaySummariesRaw(
        @Query("extend") extend: String = "inLibrary",
        @Query("period") period: String = "year",
        @Query("year") year: Int? = null,
        @Query("with") with: String? = null,
        @Query("fields[music-summaries]") fields: String = "period,year,month,name",
        @Query("include[music-summaries]") include: String = "playlist",
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/me/music-summaries/{yearSummaryId}")
    suspend fun getReplayYearRaw(
        @Path("yearSummaryId") yearSummaryId: String,
        @Query("extend") extend: String = "inLibrary,editorialArtwork,editorialVideo,hero",
        @Query("views") views: String = "top-artists,top-albums,top-songs",
        @Query("include[music-summaries]") includeSummaries: String = "playlist",
        @Query("include[artist-period-summaries]") includeArtist: String = "artist",
        @Query("include[album-period-summaries]") includeAlbum: String = "album",
        @Query("include[song-period-summaries]") includeSong: String = "song",
        @Query("include[playlists]") includePlaylists: String = "tracks",
        @Query("art[artists:url]") artistArt: String = "c",
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("l") locale: String? = null
    ): ResponseBody

    @GET("v1/me/music-summaries/{monthSummaryId}")
    suspend fun getReplayMonthRaw(
        @Path("monthSummaryId") monthSummaryId: String,
        @Query("extend") extend: String = "inLibrary,editorialArtwork,editorialVideo,hero",
        @Query("views") views: String = "top-artists,top-albums,top-songs-for-months",
        @Query("include[music-summaries]") includeSummaries: String = "playlist",
        @Query("include[artist-period-summaries]") includeArtist: String = "artist",
        @Query("include[album-period-summaries]") includeAlbum: String = "album",
        @Query("include[monthly-song-period-summaries]") includeMonthlySong: String = "song",
        @Query("include[playlists]") includePlaylists: String = "tracks",
        @Query("art[artists:url]") artistArt: String = "c",
        @Query("omit[resource]") omitResource: String = "autos",
        @Query("l") locale: String? = null
    ): ResponseBody

    companion object {
        const val DISPLAY_KINDS =
            "MusicCircleCoverShelf,MusicConcertsEmptyShelf,MusicCoverGrid,MusicCoverShelf," +
                "MusicNotesHeroShelf,MusicSocialCardShelf,MusicSuperHeroShelf"

        const val TYPES_HOME =
            "activities,albums,apple-curators,artists,concerts,curators,editorial-items," +
                "library-albums,library-artists,library-playlists,music-videos,playlists," +
                "social-profiles,social-upsells,songs,stations,uploaded-audios,uploaded-videos"

        const val TYPES_SEE_ALL =
            "albums,artists,concerts,editorial-items,library-albums,library-playlists," +
                "playlists,stations,uploaded-audios"
    }
}
