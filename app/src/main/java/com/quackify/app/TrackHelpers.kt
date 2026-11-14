package com.quackify.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quackify.app.search.decorateSearchTracks
import com.quackify.backend.api.Browse
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.api.Library
import com.quackify.backend.api.Podcasts
import com.quackify.backend.api.Profile
import com.quackify.backend.api.Search
import com.quackify.backend.api.Settings
import com.quackify.backend.api.WhatsNew
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.webview.SilentTokenRefresh
import com.quackify.backend.library.Krespot
import com.quackify.backend.library.PlaylistSource
import com.quackify.backend.library.TrackSource
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.Credits
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.HomeSection
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.model.ProfileContext
import com.quackify.backend.model.ProfileTopContent
import com.quackify.backend.model.ProfileUser
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.backend.model.ShelfKind
import com.quackify.backend.model.ShowDetail
import com.quackify.backend.model.TrackLyrics
import com.quackify.backend.model.TrackRow
import com.quackify.backend.model.UserProfile
import com.quackify.compose.components.UpdateDialog
import com.quackify.compose.components.UpdateInstallPhase
import com.quackify.compose.format.formatCount
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.home.toArtistRelease
import com.quackify.compose.home.toFavoriteArtistsShelf
import com.quackify.compose.home.toQuickAccess
import com.quackify.compose.home.toQuickAccessFallback
import com.quackify.compose.home.toRecentlyPlayedShelf
import com.quackify.compose.home.toShelves
import com.quackify.compose.home.toTrackItem
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.AppUpdate
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.CategoryRecommendationItem
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.EncoreNotification
import com.quackify.compose.screens.HistoryStats
import com.quackify.compose.screens.LoginScreen
import com.quackify.compose.screens.PathfinderCaptureScreen
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.ProfileContextCard
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.shell.EncoreAppShell
import com.quackify.compose.shell.PlaylistEditDraft
import com.quackify.compose.shell.ShellDeepLink
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.util.rememberIsOffline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar



private val log = Logs.of(Logs.Module.MAIN)

internal suspend fun decorateSearchTracks(
    context: android.content.Context,
    results: com.quackify.backend.model.SearchResults,
): com.quackify.backend.model.SearchResults {
    fun thin(hit: com.quackify.backend.model.SearchHit) =
        hit.kind == com.quackify.backend.model.SearchKind.TRACK &&
                (hit.imageUrl.isBlank() || hit.subtitle.isBlank())
    val missing = (listOfNotNull(results.top) + results.tracks)
        .filter(::thin)
        .map { it.uri }
        .distinct()
    if (missing.isEmpty()) return results
    val rows = runCatching {
        com.quackify.backend.library.TrackSource.decorate(context, missing)
    }.getOrDefault(emptyMap())
    if (rows.isEmpty()) return results
    fun fill(hit: com.quackify.backend.model.SearchHit): com.quackify.backend.model.SearchHit {
        val row = rows[hit.uri] ?: return hit
        return hit.copy(
            subtitle = hit.subtitle.ifBlank { row.artists },
            imageUrl = hit.imageUrl.ifBlank { row.imageUrl },
            durationMs = row.durationMs.takeIf { it > 0L } ?: hit.durationMs,
            explicit = hit.explicit || row.explicit,
        )
    }
    return results.copy(
        top = results.top?.let(::fill),
        tracks = results.tracks.map(::fill),
    )
}

internal const val FAVORITE_ARTIST_LIMIT = 10

internal const val PLAYLIST_PREFETCH_ROWS = 8

internal data class ArtistStanding(
    val plays: Int,
    val lastPlayedAtMs: Long,
    val latestTrack: TrackItem,
)

/**
 * The history's favorite artists: every artist uri ranked by total plays,
 * the most recent play breaking ties. Each artist is represented by its
 * newest track so the shelf card carries a cover.
 */
internal fun favoriteArtistsOf(history: List<PlayedTrack>): List<TrackItem> =
    history
        .filter { it.item.artistUri.startsWith("spotify:artist:") && it.item.artist.isNotBlank() }
        .groupBy { it.item.artistUri }
        .values
        .map { plays ->
            ArtistStanding(
                plays = plays.sumOf { it.plays },
                lastPlayedAtMs = plays.maxOf { it.playedAtMs },
                latestTrack = plays.maxByOrNull { it.playedAtMs }!!.item,
            )
        }
        .sortedWith(compareByDescending<ArtistStanding> { it.plays }.thenByDescending { it.lastPlayedAtMs })
        .take(FAVORITE_ARTIST_LIMIT)
        .map { it.latestTrack }

internal const val PROFILE_CONTEXT_LIMIT = 12

/**
 * The recently-played feed only carries context uris, so each entry is turned
 * into a displayable card: artists come straight from the profile response,
 * playlists and albums are resolved through their existing sources.
 */
internal suspend fun resolveProfileContexts(
    context: android.content.Context,
    profile: UserProfile?,
    contexts: List<ProfileContext>?,
): List<ProfileContextCard> {
    if (contexts.isNullOrEmpty()) return emptyList()
    val artistByUri = profile?.recentlyPlayedArtists?.associateBy { it.uri }.orEmpty()
    return contexts.take(PROFILE_CONTEXT_LIMIT).mapNotNull { entry ->
        when {
            entry.uri.startsWith("spotify:artist:") -> {
                val artist = artistByUri[entry.uri] ?: return@mapNotNull null
                ProfileContextCard(entry.uri, artist.name, "Artist", artist.imageUrl)
            }
            entry.uri.startsWith("spotify:playlist:") -> {
                val detail = runCatching {
                    Krespot.collections.collection(context, entry.uri)
                }.getOrNull() ?: return@mapNotNull null
                ProfileContextCard(entry.uri, detail.title, "Playlist", detail.imageUrl)
            }
            entry.uri.startsWith("spotify:album:") -> {
                val detail = runCatching {
                    Krespot.albums.album(context, entry.uri)
                }.getOrNull() ?: return@mapNotNull null
                ProfileContextCard(entry.uri, detail.title, detail.artists, detail.imageUrl)
            }
            else -> null
        }
    }
}

internal suspend fun withSavedStates(context: Context, rows: List<TrackItem>): List<TrackItem> {
    val uris = rows.map { it.uri }.filter { it.isNotBlank() }
    if (uris.isEmpty()) return rows
    val liked = Library.saved(context, uris)
    return rows.map { it.copy(saved = it.uri in liked) }
}

internal suspend fun withSavedPopularTracks(context: Context, rows: List<PopularTrack>): List<PopularTrack> {
    val uris = rows.map { it.track.uri }.filter { it.isNotBlank() }
    if (uris.isEmpty()) return rows
    val liked = Library.saved(context, uris)
    return rows.map { popTrack ->
        popTrack.copy(track = popTrack.track.copy(saved = popTrack.track.uri in liked))
    }
}

internal fun toggleArtistTrackLike(
    context: Context,
    scope: CoroutineScope,
    track: TrackItem,
    getTracks: () -> List<PopularTrack>?,
    setTracks: (List<PopularTrack>?) -> Unit,
) {
    val next = track.saved != true
    val swap = { liked: Boolean ->
        setTracks(
            getTracks()?.map { popTrack ->
                if (popTrack.track.uri == track.uri) {
                    popTrack.copy(track = popTrack.track.copy(saved = liked))
                } else {
                    popTrack
                }
            }
        )
    }
    swap(next)
    scope.launch {
        val ok = Library.setSaved(context, listOf(track.uri), next)
        log.i("artist heart '${track.title}' -> $next ok=$ok")
        if (!ok) swap(!next)
    }
}

internal fun toggleAlbumTrackLike(
    context: Context,
    scope: CoroutineScope,
    track: TrackItem,
    getTracks: () -> List<TrackItem>?,
    setTracks: (List<TrackItem>?) -> Unit,
) {
    val next = track.saved != true
    val swap = { liked: Boolean ->
        setTracks(getTracks()?.map { if (it.uri == track.uri) it.copy(saved = liked) else it })
    }
    swap(next)
    scope.launch {
        val ok = Library.setSaved(context, listOf(track.uri), next)
        log.i("album heart '${track.title}' -> $next ok=$ok")
        if (!ok) swap(!next)
    }
}

internal fun togglePlaylistTrackLike(
    context: Context,
    scope: CoroutineScope,
    playlistUri: String,
    track: TrackItem,
    getTracks: () -> List<TrackItem>?,
    setTracks: (List<TrackItem>?) -> Unit,
    getDetail: () -> CollectionDetail?,
    setDetail: (CollectionDetail?) -> Unit,
) {
    val next = track.saved != true
    val isLikedSongs = playlistUri == "spotify:collection:tracks"
    if (!next && isLikedSongs) {
        setTracks(getTracks()?.filterNot { it.uri == track.uri })
        setDetail(getDetail()?.let { it.copy(total = (it.total - 1).coerceAtLeast(0)) })
    } else {
        setTracks(getTracks()?.map { if (it.uri == track.uri) it.copy(saved = next) else it })
    }
    scope.launch {
        val ok = Library.setSaved(context, listOf(track.uri), next)
        log.i("playlist heart '${track.title}' -> $next ok=$ok")
        if (!ok) {
            val fresh = Krespot.collections.collection(context, playlistUri)
            setDetail(fresh)
            setTracks(fresh?.tracks?.map { it.toTrackItem() })
        }
    }
}

internal suspend fun buildPlaylistRecommendations(
    context: Context,
    tracks: List<TrackItem>,
    inPlaylist: Set<String>,
): List<TrackItem> {
    val seedArtists = if (inPlaylist.isNotEmpty()) {
        tracks
            .filter { it.artistUri.startsWith("spotify:artist:") }
            .groupingBy { it.artistUri }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
            .shuffled()
            .take(3)
    } else {
        val candidateArtists = LinkedHashSet<String>()
        val likedDetail = Krespot.collections.collection(context, "spotify:user:@me:collection")
        likedDetail?.tracks?.forEach { t ->
            if (t.artistUri.startsWith("spotify:artist:")) candidateArtists.add(t.artistUri)
        }
        if (candidateArtists.size < 5) {
            val libraryPage = Krespot.library.page(context)
            libraryPage?.items?.forEach { item ->
                if (item.uri.startsWith("spotify:artist:")) {
                    candidateArtists.add(item.uri)
                }
            }
        }
        if (candidateArtists.isNotEmpty()) {
            candidateArtists.shuffled().take(4)
        } else {
            listOf(
                "spotify:artist:4YRxDV8wJFPHPTeXepOstw",
                "spotify:artist:3g2kUQ6tHLLbmkV7T4GPdf",
                "spotify:artist:246dkjvS1zLTtiykYqq8Gv"
            )
        }
    }
    val picks = LinkedHashMap<String, TrackItem>()
    val overviews = coroutineScope {
        seedArtists.map { artist ->
            async { Krespot.artists.artist(context, artist) }
        }.awaitAll()
    }
    for (detail in overviews.filterNotNull()) {
        for (row in detail.topTracks) {
            if (row.uri in inPlaylist || picks.containsKey(row.uri)) continue
            picks[row.uri] = row.toTrackItem()
            if (picks.size >= 10) break
        }
        if (picks.size >= 10) break
    }
    log.i("recommended ${picks.size} client track(s) from ${seedArtists.size} artist(s)")
    return picks.values.toList()
}

internal data class ArtistLoad(
    val detail: ArtistDetail?,
    val tracks: List<PopularTrack>,
    val releases: List<ArtistRelease>,
    val listeners: String?,
    val followed: Boolean?,
)

internal suspend fun loadArtistDetail(context: Context, uri: String): ArtistLoad {
    val detail = Krespot.artists.artist(context, uri)
    var releaseRows = detail?.releases.orEmpty()
    if (releaseRows.isEmpty()) {
        releaseRows = Krespot.artists.discography(context, uri, "all")?.rows.orEmpty()
    }
    val inLibrary = Library.saved(context, listOf(uri))
    return ArtistLoad(
        detail = detail,
        tracks = popularTracks(context, uri, detail?.topTracks.orEmpty()),
        releases = releaseRows.map { it.toArtistRelease() },
        listeners = detail?.monthlyListeners?.takeIf { it > 0L }?.let { formatCount(it) },
        followed = if (inLibrary.isEmpty()) detail?.saved == true else uri in inLibrary,
    )
}

private fun TrackRow.toPopularTrack(): PopularTrack {
    val countFormatted = if (plays > 0L) {
        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(plays)
    } else ""
    return PopularTrack(toTrackItem(), countFormatted)
}

private suspend fun popularTracks(
    context: Context,
    artistUri: String,
    top: List<TrackRow>,
): List<PopularTrack> {
    val topByUri = top.associateBy { it.uri }
    val order = runCatching { Krespot.artists.popularTrackUris(context, artistUri) }
        .getOrDefault(emptyList())
    if (order.isEmpty()) return top.map { it.toPopularTrack() }
    val missing = order.filterNot { topByUri.containsKey(it) }
    val decorated = if (missing.isEmpty()) emptyMap() else {
        runCatching { TrackSource.decorate(context, missing) }.getOrDefault(emptyMap())
    }
    val rows = order.mapNotNull { uri -> topByUri[uri] ?: decorated[uri] }
        .filter { it.title.isNotBlank() }
    if (rows.size < top.size) return top.map { it.toPopularTrack() }
    return rows.map { it.toPopularTrack() }
}
