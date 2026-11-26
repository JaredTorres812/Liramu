package com.applemusic.android.data.repository

import android.content.Context
import com.applemusic.android.R

/** Textos que arma la app (no los que manda Apple), en el idioma del teléfono. */
data class HomeStrings(
    val topPicks: String = "Top Picks for You",
    val recentlyPlayed: String = "Recently Played",
    val madeForYou: String = "Made for You",
    val moreLike: String = "More Like",
    val badgeListenAgain: String = "Listen Again",
    val badgeMadeForYou: String = "Made for You",
    val badgeNew: String = "New Release",
    val badgeFeaturing: String = "Featuring %s",
    val badgeMood: String = "Mood for You",
    val live: String = "LIVE",
    val untitled: String = "Untitled",
    val topPlaylists: String = "Top Playlists",
    val topAlbums: String = "Top Albums",
    val topSongs: String = "Top Songs",
    val noRecommendations: String = "Couldn't load personalized recommendations."
) {
    companion object {
        fun from(context: Context) = HomeStrings(
            topPicks = context.getString(R.string.home_top_picks),
            recentlyPlayed = context.getString(R.string.home_recently_played),
            madeForYou = context.getString(R.string.home_made_for_you),
            moreLike = context.getString(R.string.home_more_like),
            badgeListenAgain = context.getString(R.string.badge_listen_again),
            badgeMadeForYou = context.getString(R.string.badge_made_for_you),
            badgeNew = context.getString(R.string.badge_new_release),
            badgeFeaturing = context.getString(R.string.badge_featuring),
            badgeMood = context.getString(R.string.badge_mood),
            live = context.getString(R.string.badge_live),
            untitled = context.getString(R.string.untitled),
            topPlaylists = context.getString(R.string.top_playlists),
            topAlbums = context.getString(R.string.top_albums),
            topSongs = context.getString(R.string.top_songs),
            noRecommendations = context.getString(R.string.error_no_recommendations)
        )
    }
}
