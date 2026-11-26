package com.applemusic.android.data.model

import kotlinx.serialization.Serializable

// --- Apple Music API DTOs ---

@Serializable
data class RecommendationResponse(
    val next: String? = null,
    val data: List<RecommendationResource> = emptyList()
)

@Serializable
data class RecommendationResource(
    val id: String,
    val type: String = "personal-recommendation",
    val attributes: RecommendationAttributes? = null,
    val relationships: RecommendationRelationships? = null
)

@Serializable
data class RecommendationAttributes(
    val title: RecommendationTitle? = null,
    val isGroupRecommendation: Boolean = false,
    val resourceTypes: List<String> = emptyList(),
    val nextUpdateDate: String? = null,
    val reason: RecommendationReason? = null
)

@Serializable
data class RecommendationTitle(
    val stringForDisplay: String? = null,
    /** En "Más como X" / "Similar a X": id del álbum X. */
    val contentIds: List<String> = emptyList()
)

@Serializable
data class RecommendationReason(
    val stringForDisplay: String? = null
)

@Serializable
data class RecommendationRelationships(
    val contents: RecommendationContents? = null,
    val recommendations: RecommendationNestedContents? = null
)

@Serializable
data class RecommendationContents(
    val data: List<MediaResource> = emptyList()
)

@Serializable
data class RecommendationNestedContents(
    val data: List<RecommendationResource> = emptyList()
)

@Serializable
data class MediaListResponse(
    val next: String? = null,
    val data: List<MediaResource> = emptyList()
)



@Serializable
data class ChartsResponse(
    val results: ChartsResults? = null
)

@Serializable
data class ChartsResults(
    val albums: List<ChartGroup> = emptyList(),
    val playlists: List<ChartGroup> = emptyList(),
    val songs: List<ChartGroup> = emptyList()
)

@Serializable
data class ChartGroup(
    val name: String = "",
    val chart: String = "",
    val data: List<MediaResource> = emptyList()
)

@Serializable
data class MediaResource(
    val id: String,
    val type: String = "",
    val href: String? = null,
    val attributes: MediaAttributes? = null,
    val relationships: MediaRelationships? = null
)

@Serializable
data class MediaRelationships(
    val catalog: MediaListResponse? = null
)

@Serializable
data class MediaAttributes(
    val name: String? = null,
    val artistName: String? = null,
    val curatorName: String? = null,
    val albumName: String? = null,
    val releaseDate: String? = null,
    val genreNames: List<String> = emptyList(),
    val artwork: Artwork? = null,
    val editorialNotes: EditorialNotes? = null,
    val url: String? = null,
    val isExplicit: Boolean? = null,
    val contentRating: String? = null,
    val kind: String? = null,
    val isLive: Boolean? = null,
    val artistNames: String? = null,
    val plainEditorialNotes: EditorialNotes? = null,
    val description: EditorialNotes? = null
)

@Serializable
data class Artwork(
    val width: Int? = null,
    val height: Int? = null,
    val url: String = "",
    val bgColor: String? = null,
    val textColor1: String? = null,
    val textColor2: String? = null
) {
    fun urlForSize(width: Int = 500, height: Int = 500): String =
        url.replace("{w}", width.toString()).replace("{h}", height.toString())
}

@Serializable
data class EditorialNotes(
    val short: String? = null,
    val standard: String? = null,
    val tagline: String? = null
)

// --- UI Domain Models for Home ---

enum class ConnectionMode {
    PERSONALIZED, // Developer Token + Music User Token
    CATALOG_ONLY, // Developer Token only (shows Apple Music Charts)
    DEMO          // No tokens configured yet (shows Material 3 preview data)
}

@Serializable
enum class HomeSectionStyle {
    HERO_PICKS,      // Tarjetas verticales (Sugerencias destacadas, Replay, Playlists hechas para ti)
    SUPER_HERO,      // Tarjeta ancha (radio en vivo destacada)
    SQUARE_CAROUSEL,
    ARTIST_CIRCLES,
    COVER_GRID,
    COMPACT_TRACKS,
    EXPLORE_LINKS    // Pills/tarjetas de géneros y categorías ("Más para explorar")
}

@Serializable
data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val hasChevron: Boolean = false,
    val style: HomeSectionStyle = HomeSectionStyle.SQUARE_CAROUSEL,
    val items: List<HomeItem>,
    /** true si Apple tiene más elementos que los del Home ("Ver todo" trae la lista completa). */
    val canLoadMore: Boolean = false,
    /** "Más como X": al tocar el título se abre la página de X (como en la web). */
    val primaryContent: HomeItem? = null
)

@Serializable
data class FriendBadge(
    val id: String,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class HomeItem(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val artworkUrl: String?,
    val bgColorHex: String? = null,
    val isExplicit: Boolean = false,
    /** Imagen vertical de pantalla completa (ej. tarjeta "Replay mensual"). */
    val tallArtworkUrl: String? = null,
    /** Color de texto sugerido por Apple para el artwork (hex sin #). */
    val textColorHex: String? = null,
    /** URL canónica en Apple Music si existe. */
    val url: String? = null,
    /** ID de catálogo subyacente (útil para cruzar con `badgingMap` si el ítem es de librería). */
    val catalogId: String? = null,
    /** Stream HLS animado (`editorialVideo`) de Apple Music si existe. */
    val motionArtworkUrl: String? = null,
    /** Fotos de perfil de amigos que escucharon este contenido (`socialBadge`). */
    val friendBadges: List<FriendBadge> = emptyList()
)
