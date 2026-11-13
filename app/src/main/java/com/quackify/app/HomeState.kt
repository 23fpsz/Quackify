/*
 * SpotiDuck-Compose — home feed and listening-history state holder.
 *
 * Owns the home feed (sections, chips, greeting, facet filter), the play
 * history and stats snapshots, the derived home shelves, and the history
 * screen's day buckets. Moved out of MainActivity's shell composable both for
 * cohesion and to keep that composable under the compiler's per-method size
 * limit.
 */

package com.quackify.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.api.Search
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.webview.SilentTokenRefresh
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.HomeSection
import com.quackify.backend.model.SearchKind
import com.quackify.compose.home.HomeShelf
import com.quackify.compose.home.QuickAccessItem
import com.quackify.compose.home.toFavoriteArtistsShelf
import com.quackify.compose.home.toQuickAccess
import com.quackify.compose.home.toQuickAccessFallback
import com.quackify.compose.home.toRecentlyPlayedShelf
import com.quackify.compose.home.toShelves
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.HistoryStats
import java.util.Calendar

private val log = Logs.of(Logs.Module.MAIN)

class HomeState(context: Context) {
    var feed by mutableStateOf<List<HomeSection>>(emptyList())
        internal set
    var chips by mutableStateOf<List<HomeChip>>(emptyList())
        internal set
    var greeting by mutableStateOf("")
        internal set
    var facet by mutableStateOf("")
    var loading by mutableStateOf(true)
        internal set

    /** Written by the playback listener as tracks are played; read by the history screen. */
    var playHistory by mutableStateOf(PlayHistory.all(context))
    var playStats by mutableStateOf(PlayStats.snapshot(context))

    /** Artist uri -> artwork, filled from the feed and then from search. */
    val artistImages = mutableStateMapOf<String, String>()

    var account by mutableStateOf<EncoreAuth.Account?>(null)
        internal set
    var quick by mutableStateOf<List<QuickAccessItem>>(emptyList())
        internal set
    var shelves by mutableStateOf<List<HomeShelf>>(emptyList())
        internal set
    var historyShelves by mutableStateOf<List<HomeShelf>>(emptyList())
        internal set
    var historyToday by mutableStateOf<List<TrackItem>>(emptyList())
        internal set
    var historyYesterday by mutableStateOf<List<TrackItem>>(emptyList())
        internal set
    var historyEarlier by mutableStateOf<List<TrackItem>>(emptyList())
        internal set
    var historyStats by mutableStateOf(HistoryStats())
        internal set

    fun selectFacet(value: String) {
        facet = value
    }

    /** The shelves the home screen renders: history extras only on the default facet. */
    fun homeShelves(): List<HomeShelf> =
        if (facet.isBlank() && !loading) historyShelves + shelves else shelves
}

@Composable
fun rememberHomeState(context: Context, loggedIn: Boolean, resumeSignal: Int): HomeState {
    val state = remember { HomeState(context.applicationContext) }

    LaunchedEffect(Unit) {
        state.playStats = PlayStats.recordLogin(context)
    }
    LaunchedEffect(resumeSignal, state.facet) {
        if (resumeSignal == 0) state.loading = true
        val payload = HomeFeed.load(context, state.facet)
        if (payload.sections.isNotEmpty() || resumeSignal == 0) {
            state.feed = payload.sections
            state.greeting = payload.greeting
        }
        if (payload.chips.isNotEmpty()) state.chips = payload.chips
        state.loading = false
        log.i("home feed loaded facet='${state.facet}' sections=${state.feed.size} " +
                "chips=${state.chips.size} signal=$resumeSignal"
        )
    }
    LaunchedEffect(resumeSignal) {
        if (resumeSignal > 0) SilentTokenRefresh.maybeRefresh(context)
    }
    LaunchedEffect(state.feed) {
        state.feed.forEach { section ->
            section.items.forEach { item ->
                if (item.uri.startsWith("spotify:artist:") && item.imageUrl.isNotBlank()) {
                    state.artistImages[item.uri] = item.imageUrl
                }
            }
        }
    }
    LaunchedEffect(state.playHistory, state.loading) {
        if (state.loading) return@LaunchedEffect
        favoriteArtistsOf(state.playHistory).forEach { artist ->
            val uri = artist.artistUri
            if (state.artistImages.containsKey(uri)) return@forEach
            val fetched = runCatching {
                Search.section(context, SearchKind.ARTIST, artist.artist.substringBefore(", "))
                    .firstOrNull { it.uri == uri }
            }.getOrNull()?.imageUrl?.takeIf { it.isNotBlank() }
            if (fetched != null) state.artistImages[uri] = fetched
        }
    }

    // Derived during composition so the first frame already carries them.
    state.account = remember(loggedIn) { EncoreAuth.account(context) }
    val quick = remember(state.feed, state.playHistory, state.loading) {
        val fromFeed = state.feed.toQuickAccess()
        when {
            fromFeed.isNotEmpty() -> fromFeed
            state.loading -> emptyList()
            else -> {
                log.i("feed has no shorts section, deriving the shortcut grid from history")
                state.playHistory.map { it.item }.toQuickAccessFallback()
            }
        }
    }
    val shelves = remember(state.feed) { state.feed.toShelves() }
    val historyShelves = remember(state.playHistory, state.artistImages.size) {
        listOfNotNull(
            state.playHistory.map { it.item }.toRecentlyPlayedShelf(),
            favoriteArtistsOf(state.playHistory).toFavoriteArtistsShelf(state.artistImages),
        )
    }
    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val startOfYesterday = remember { startOfToday - 24L * 60 * 60 * 1000 }
    val historyToday = remember(state.playHistory, startOfToday) {
        state.playHistory.filter { it.playedAtMs >= startOfToday }.map { it.item }
    }
    val historyYesterday = remember(state.playHistory, startOfToday, startOfYesterday) {
        state.playHistory
            .filter { it.playedAtMs >= startOfYesterday && it.playedAtMs < startOfToday }
            .map { it.item }
    }
    val historyEarlier = remember(state.playHistory, startOfYesterday) {
        state.playHistory.filter { it.playedAtMs in 1 until startOfYesterday }.map { it.item }
    }
    val historyStats = remember(state.playStats, state.playHistory) {
        val replayed = state.playHistory.filter { it.plays > 1 }.maxByOrNull { it.plays }
        HistoryStats(
            totalPlayedMs = state.playStats.totalMs,
            listenStreakDays = state.playStats.listenStreak,
            loginStreakDays = state.playStats.loginStreak,
            topArtistName = state.playStats.topArtistName,
            topArtistPlays = state.playStats.topArtistPlays,
            topPlaylistName = state.playStats.topPlaylistName,
            topPlaylistPlays = state.playStats.topPlaylistPlays,
            topReplayTitle = replayed?.item?.title.orEmpty(),
            topReplayArtist = replayed?.item?.artist.orEmpty(),
            topReplayPlays = replayed?.plays ?: 0,
        )
    }
    LaunchedEffect(quick) {
        log.i("home quick access tiles=${quick.size}")
    }
    LaunchedEffect(shelves) {
        log.i("home shelves=${shelves.size} " +
                "grids=${shelves.count { it.grid }} " +
                "cards=${shelves.sumOf { it.cards.size }} " +
                "titles=${shelves.take(8).joinToString(" | ") { it.title.ifBlank { "-" } }}"
        )
    }
    state.quick = quick
    state.shelves = shelves
    state.historyShelves = historyShelves
    state.historyToday = historyToday
    state.historyYesterday = historyYesterday
    state.historyEarlier = historyEarlier
    state.historyStats = historyStats
    return state
}

private const val FAVORITE_ARTIST_LIMIT = 10

private data class ArtistStanding(
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
