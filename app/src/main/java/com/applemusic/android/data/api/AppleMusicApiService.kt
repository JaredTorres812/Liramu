package com.applemusic.android.data.api

import com.applemusic.android.data.model.ChartsResponse
import com.applemusic.android.data.model.MediaListResponse
import com.applemusic.android.data.model.RecommendationResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface AppleMusicApiService {

    /**
     * Home ("Inicio") igual que music.apple.com.
     *
     * Con `name=listen-now` + `platform=iphone` + `displayFilter[kind]` la API pública devuelve
     * las mismas secciones de la web, en el mismo orden y con TODOS sus elementos
     * (sin estos parámetros recorta los álbumes de las secciones de género).
     * Las secciones tipo "hero" (MusicNotesHeroShelf) no se pueden pedir aquí porque traen
     * `editorial-items`, que la API pública no permite; esas se arman en HomeRepository.
     */
    @GET("v1/me/recommendations")
    suspend fun getListenNow(
        @Query("name") name: String = "listen-now",
        @Query("platform") platform: String = "iphone",
        @Query("timezone") timezone: String,
        @Query("l") locale: String? = null,
        @Query("with") with: String = "library",
        @Query("types") types: String = LISTEN_NOW_TYPES,
        @Query("displayFilter[kind]") displayKinds: String = "MusicCoverShelf,MusicSuperHeroShelf",
        @Query("extend") extend: String = "editorialNotes,plainEditorialNotes",
        @Query("include[library-playlists]") includeLibraryPlaylists: String = "catalog",
        @Query("include[library-albums]") includeLibraryAlbums: String = "catalog",
        @Query("limit") limit: Int = 30
    ): RecommendationResponse

    /** Recomendaciones clásicas: de aquí sale "Playlists hechas para ti". */
    @GET("v1/me/recommendations")
    suspend fun getRecommendations(
        @Query("l") locale: String? = null,
        @Query("extend") extend: String = "editorialNotes",
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): RecommendationResponse

    @GET
    suspend fun getRecommendationsByUrl(@Url url: String): RecommendationResponse

    @GET("v1/me/recent/played")
    suspend fun getRecentlyPlayed(
        @Query("l") locale: String? = null,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): MediaListResponse

    /** País e idiomas de la cuenta del usuario. */
    @GET("v1/me/storefront")
    suspend fun getMyStorefrontRaw(): okhttp3.ResponseBody

    /** Canciones de una playlist: se usan para el texto "Artista, Artista y más" como en la web. */
    @GET("v1/catalog/{storefront}/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("storefront") storefront: String,
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 40,
        @Query("l") locale: String? = null
    ): MediaListResponse

    /** Respaldo para la página de álbum si no hay sesión web. */
    @GET("v1/catalog/{storefront}/albums/{id}")
    suspend fun getAlbumRaw(
        @Path("storefront") storefront: String,
        @Path("id") albumId: String,
        @Query("include") include: String = "tracks",
        @Query("extend") extend: String = "editorialNotes",
        @Query("l") locale: String? = null
    ): okhttp3.ResponseBody

    @GET("v1/catalog/{storefront}/charts")
    suspend fun getCatalogCharts(
        @Path("storefront") storefront: String,
        @Query("types") types: String = "playlists,albums,songs",
        @Query("genre") genreId: String? = null,
        @Query("l") locale: String? = null,
        @Query("limit") limit: Int = 12
    ): ChartsResponse

    companion object {
        const val LISTEN_NOW_TYPES =
            "activities,albums,apple-curators,artists,curators,library-albums,library-playlists," +
                "music-videos,playlists,songs,stations"
    }
}
