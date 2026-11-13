package com.quackify.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.quackify.app.search.decorateSearchTracks
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.model.HomeSection
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.Credits
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.api.Library
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.net.Pathfinder
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.shell.PlaylistEditDraft
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.HistoryStats
import com.quackify.compose.home.toTrackItem
import com.quackify.compose.home.toArtistRelease
import com.quackify.backend.model.ShelfKind
import androidx.compose.runtime.collectAsState
import com.quackify.backend.api.Search
import com.quackify.backend.api.WhatsNew
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.screens.SettingsLocks
import com.quackify.backend.api.Settings
import com.quackify.compose.screens.EncoreNotification
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.CategoryRecommendationItem
import com.quackify.compose.format.parseHexColor
import androidx.compose.ui.graphics.Color
import com.quackify.backend.api.Browse
import com.quackify.backend.api.Podcasts
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.ShowDetail
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.backend.model.TrackLyrics
import com.quackify.compose.home.toFavoriteArtistsShelf
import com.quackify.compose.home.toQuickAccess
import com.quackify.compose.home.toRecentlyPlayedShelf
import com.quackify.compose.home.toShelves
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.format.formatCount
import com.quackify.compose.screens.LoginScreen
import com.quackify.compose.screens.PathfinderCaptureScreen
import com.quackify.compose.shell.EncoreAppShell
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.util.rememberIsOffline
import com.quackify.backend.core.webview.SilentTokenRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.quackify.backend.core.log.Logs
import com.quackify.backend.library.Krespot
import com.quackify.backend.library.PlaylistSource
import com.quackify.compose.home.toQuickAccessFallback

private val log = Logs.of(Logs.Module.MAIN)

class MainActivity : ComponentActivity() {

    private var resumeSignal by mutableStateOf(0)

    /** Bumped every time the playback notification's body is tapped; the
     * shell watches it and slides the Now Playing view over whatever is
     * on screen. */
    private var openPlayerSignal by mutableStateOf(0)
    private var pausedAt = 0L
    private var sawStop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal++
        }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        Pathfinder.silentRefresh = { context, reason -> SilentTokenRefresh.refresh(context, reason) }
        val appContext = applicationContext
        Net.onUnauthorized = { sent, reason ->
            val current = EncoreAuth.token(appContext)
            val left = EncoreAuth.tokenRemainingMs(appContext)
            when {
                current.isNotBlank() && current != sent -> {
                    log.i("$reason 401, a newer token is already in the store")
                    current
                }
                left > TOKEN_STILL_GOOD_MS -> {
                    log.w("$reason 401 with ${left / 1000}s left, not spending a refresh on it")
                    null
                }
                SilentTokenRefresh.refresh(appContext, reason) -> {
                    EncoreAuth.token(appContext).takeIf { it.isNotBlank() && it != sent }
                }
                else -> {
                    log.w("$reason 401 and the silent refresh failed, giving up on this call")
                    null
                }
            }
        }
        Library.onLibraryChanged = { Krespot.library.invalidate() }
        setContent {
            EncoreAppTheme {
                val context = LocalContext.current
                var loggedIn by remember { mutableStateOf(EncoreAuth.isLoggedIn(context)) }


                if (loggedIn) {
                    var capturing by remember {
                        val tokenUsable = EncoreAuth.token(context).isNotBlank() &&
                            !EncoreAuth.tokenExpired(context)
                        mutableStateOf(Pathfinder.shouldCapture(context, tokenUsable))
                    }
                    remember(loggedIn, capturing) {
                        val ttl = EncoreAuth.tokenRemainingMs(context)
                            .let { if (it < 0L) "unknown" else "${it / 1000}s" }
                        log.v("composition loggedIn=$loggedIn capturing=$capturing " +
                                "ops=${Pathfinder.count()} token=$ttl")
                        true
                    }
                    if (capturing) {
                        PathfinderCaptureScreen(onDone = {
                            log.i("capture finished ops=${Pathfinder.count()}")
                            capturing = false
                        })
                    } else {
                        var feed by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
                        var homeChips by remember { mutableStateOf<List<HomeChip>>(emptyList()) }
                        var homeGreeting by remember { mutableStateOf("") }
                        var homeFacet by remember { mutableStateOf("") }
                        var loading by remember { mutableStateOf(true) }
                        var playHistory by remember { mutableStateOf(PlayHistory.all(context)) }
                        var playStats by remember { mutableStateOf(PlayStats.snapshot(context)) }
                        LaunchedEffect(Unit) {
                            playStats = PlayStats.recordLogin(context)
                        }
                        LaunchedEffect(resumeSignal, homeFacet) {
                            if (resumeSignal == 0) loading = true
                            val payload = HomeFeed.load(context, homeFacet)
                            if (payload.sections.isNotEmpty() || resumeSignal == 0) {
                                feed = payload.sections
                                homeGreeting = payload.greeting
                            }
                            if (payload.chips.isNotEmpty()) homeChips = payload.chips
                            loading = false
                            log.i("home feed loaded facet='$homeFacet' sections=${feed.size} " +
                                    "chips=${homeChips.size} signal=$resumeSignal"
                            )
                        }
                        LaunchedEffect(resumeSignal) {
                            if (resumeSignal > 0) SilentTokenRefresh.maybeRefresh(context)
                        }
                        val account = remember(loggedIn) { EncoreAuth.account(context) }
                        val quick = remember(feed, playHistory, loading) {
                            val fromFeed = feed.toQuickAccess()
                            when {
                                fromFeed.isNotEmpty() -> fromFeed
                                loading -> emptyList()
                                else -> {
                                    log.i("feed has no shorts section, deriving the shortcut grid from history")
                                    playHistory.map { it.item }.toQuickAccessFallback()
                                }
                            }
                        }
                        LaunchedEffect(quick) {
                            log.i("home quick access tiles=${quick.size}")
                        }
                        val shelves = remember(feed) { feed.toShelves() }
                        val homeArtistImages = remember { mutableStateMapOf<String, String>() }
                        LaunchedEffect(feed) {
                            feed.forEach { section ->
                                section.items.forEach { item ->
                                    if (item.uri.startsWith("spotify:artist:") && item.imageUrl.isNotBlank()) {
                                        homeArtistImages[item.uri] = item.imageUrl
                                    }
                                }
                            }
                        }
                        LaunchedEffect(playHistory, loading) {
                            if (loading) return@LaunchedEffect
                            favoriteArtistsOf(playHistory).forEach { artist ->
                                val uri = artist.artistUri
                                if (homeArtistImages.containsKey(uri)) return@forEach
                                val fetched = runCatching {
                                    Search.section(context, com.quackify.backend.model.SearchKind.ARTIST, artist.artist.substringBefore(", "))
                                        .firstOrNull { it.uri == uri }
                                }.getOrNull()?.imageUrl?.takeIf { it.isNotBlank() }
                                if (fetched != null) homeArtistImages[uri] = fetched
                            }
                        }
                        val historyShelves = remember(playHistory, homeArtistImages.size) {
                            listOfNotNull(
                                playHistory.map { it.item }.toRecentlyPlayedShelf(),
                                favoriteArtistsOf(playHistory).toFavoriteArtistsShelf(homeArtistImages),
                            )
                        }
                        LaunchedEffect(shelves) {
                            log.i("home shelves=${shelves.size} " +
                                    "grids=${shelves.count { it.grid }} " +
                                    "cards=${shelves.sumOf { it.cards.size }} " +
                                    "titles=${shelves.take(8).joinToString(" | ") { it.title.ifBlank { "-" } }}"
                            )
                        }

                        var searchTerm by remember { mutableStateOf("") }
                        var searchData by remember { mutableStateOf<SearchResults?>(null) }
                        var searchHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
                        var searchLoading by remember { mutableStateOf(false) }
                        var searchFailed by remember { mutableStateOf(false) }
                        var searchFetch by remember { mutableStateOf(0) }
                        var searchDataFor by remember { mutableStateOf("") }
                        val searchSavedOverrides = remember { mutableStateMapOf<String, Boolean>() }
                        var recentSearches by remember { mutableStateOf(RecentSearches.all(context)) }
                        val scope = rememberCoroutineScope()

                        var showUri by remember { mutableStateOf("") }
                        var showDetail by remember { mutableStateOf<ShowDetail?>(null) }
                        var showLoading by remember { mutableStateOf(false) }
                        var showFetch by remember { mutableStateOf(0) }
                        var episodeUri by remember { mutableStateOf("") }
                        var episodeDetail by remember { mutableStateOf<EpisodeDetail?>(null) }
                        var episodeLoading by remember { mutableStateOf(false) }
                        var episodeFetch by remember { mutableStateOf(0) }
                        var browseUri by remember { mutableStateOf("") }
                        var browseFetch by remember { mutableStateOf(0) }
                        var browsePage by remember { mutableStateOf<BrowsePage?>(null) }
                        var categoryUri by remember { mutableStateOf("") }
                        var categoryShelves by remember { mutableStateOf<List<CategoryShelf>?>(null) }
                        var browseCategories by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }

                        LaunchedEffect(Unit) {
                            browseCategories = Browse.all(context)
                            log.i("browse categories: ${browseCategories.size}")
                        }

                        LaunchedEffect(categoryUri) {
                            if (categoryUri.isBlank()) return@LaunchedEffect
                            val page = Browse.page(context, categoryUri)
                            categoryShelves = page?.sections?.map { section ->
                                CategoryShelf(
                                    title = section.title,
                                    items = section.cards.map { card ->
                                        CategoryRecommendationItem(
                                            title = card.title,
                                            subtitle = card.subtitle,
                                            imageUrl = card.imageUrl.takeIf { it.isNotBlank() },
                                            coverColor = parseHexColor(card.colorHex) ?: Color(0xFF282828),
                                            uri = card.uri,
                                            isAlbum = card.uri.startsWith("spotify:album:"),
                                        )
                                    },
                                )
                            }
                        }
                        var folderUri by remember { mutableStateOf("") }
                        var folderItems by remember { mutableStateOf<List<LibraryItem>?>(null) }
                        var folderFetch by remember { mutableStateOf(0) }

                        var libraryItems by remember { mutableStateOf<List<LibraryItem>?>(null) }
                        var settingsSwitches by remember { mutableStateOf(SettingsSwitches()) }
                        var settingsLocks by remember { mutableStateOf(SettingsLocks()) }
                        var settingsProduct by remember { mutableStateOf("") }
                        var settingsCountry by remember { mutableStateOf("") }
                        var settingsLoading by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            settingsLoading = true
                            val attrs = Settings.attributes(context)
                            if (attrs != null) {
                                settingsSwitches = SettingsSwitches(
                                    musicVideos = !attrs.musicVideoDisabled,
                                    canvas = !attrs.canvasDisabled,
                                    otherVideos = !attrs.otherVideoDisabled,
                                )
                                settingsLocks = SettingsLocks(
                                    musicVideos = attrs.musicVideoLocked,
                                    canvas = attrs.canvasLocked,
                                    otherVideos = attrs.otherVideoLocked,
                                )
                                settingsProduct = attrs.product
                                settingsCountry = attrs.country
                            }
                            settingsLoading = false
                        }
                        var whatsNew by remember { mutableStateOf<List<EncoreNotification>>(emptyList()) }
                        var whatsNewLoading by remember { mutableStateOf(false) }
                        var whatsNewFailed by remember { mutableStateOf(false) }
                        var whatsNewFetch by remember { mutableStateOf(0) }

                        var savedAccountsList by remember { mutableStateOf(EncoreAuth.savedAccounts(context)) }
                        val activeAccountId = remember { EncoreAuth.activeAccountId(context) }
                        var addingAccount by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            if (EncoreAuth.activeAccountId(context).isBlank()) {
                                EncoreAuth.userId(context)
                                EncoreAuth.storeCurrentAccount(context)
                                savedAccountsList = EncoreAuth.savedAccounts(context)
                            }
                        }

                        LaunchedEffect(whatsNewFetch) {
                            whatsNewLoading = true
                            val feed = WhatsNew.feed(context)
                            whatsNewFailed = feed == null
                            whatsNew = feed?.items.orEmpty().map { item ->
                                EncoreNotification(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    timestamp = item.timestamp,
                                    isMusic = item.isMusic,
                                    id = item.id,
                                    isNew = item.isNew,
                                    imageUrl = item.imageUrl,
                                    uri = item.uri,
                                    kind = item.kind,
                                )
                            }
                            whatsNewLoading = false
                        }
                        var playlistUri by remember { mutableStateOf("") }
                        var playlistDetail by remember { mutableStateOf<CollectionDetail?>(null) }
                        var playlistTracks by remember { mutableStateOf<List<TrackItem>?>(null) }
                        var playlistLoading by remember { mutableStateOf(false) }
                        var playlistHasMore by remember { mutableStateOf(false) }
                        var playlistLoadingMore by remember { mutableStateOf(false) }
                        var playlistFetch by remember { mutableStateOf(0) }
                        var playlistRecommendations by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
                        var recommendationsLoading by remember { mutableStateOf(false) }
                        var recommendationsNonce by remember { mutableStateOf(0) }
                        var playlistSaved by remember { mutableStateOf<Boolean?>(null) }
                        var playlistCanRemoveCover by remember { mutableStateOf(false) }
                        var playlistEditDraft by remember { mutableStateOf<PlaylistEditDraft?>(null) }
                        var editableUris by remember { mutableStateOf<Set<String>>(emptySet()) }
                        var albumUri by remember { mutableStateOf("") }
                        var albumDetail by remember { mutableStateOf<AlbumDetail?>(null) }
                        var albumTracks by remember { mutableStateOf<List<TrackItem>?>(null) }
                        var albumLoading by remember { mutableStateOf(false) }
                        var albumFailed by remember { mutableStateOf(false) }
                        var albumRetry by remember { mutableStateOf(0) }
                        var albumHasMore by remember { mutableStateOf(false) }
                        var albumLoadingMore by remember { mutableStateOf(false) }
                        var artistUri by remember { mutableStateOf("") }
                        var artistDetail by remember { mutableStateOf<ArtistDetail?>(null) }
                        var artistLoading by remember { mutableStateOf(false) }
                        var artistFailed by remember { mutableStateOf(false) }
                        var artistRetry by remember { mutableStateOf(0) }
                        var trackCredits by remember { mutableStateOf<Credits?>(null) }
                        var addToPlaylistChoices by remember { mutableStateOf<List<PlaylistChoice>?>(null) }
                        var addTracksSongs by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
                        var addTracksEpisodes by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
                        var addTracksLiked by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
                        var addTracksRecent by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
                        var addTracksLoading by remember { mutableStateOf(false) }
                        var addTracksFetch by remember { mutableStateOf(0) }
                        var artistTracks by remember { mutableStateOf<List<PopularTrack>?>(null) }
                        var artistReleases by remember { mutableStateOf<List<ArtistRelease>?>(null) }
                        var artistListeners by remember { mutableStateOf<String?>(null) }
                        var artistFollowed by remember { mutableStateOf<Boolean?>(null) }

                        val libraryRevision by Krespot.library.revision.collectAsState()

                        LaunchedEffect(libraryRevision) {
                            val page = Krespot.library.page(context)
                            libraryItems = page?.items?.map { item ->
                                LibraryItem(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    type = when (item.kind) {
                                        ShelfKind.ARTIST -> LibraryItemType.ARTIST
                                        ShelfKind.ALBUM -> LibraryItemType.ALBUM
                                        ShelfKind.FOLDER -> LibraryItemType.FOLDER
                                        ShelfKind.SHOW, ShelfKind.EPISODE -> LibraryItemType.PODCAST
                                        else -> LibraryItemType.PLAYLIST
                                    },
                                    uri = item.uri,
                                    imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
                                )
                            }
                            editableUris = Krespot.playlists.editable(context, "").map { it.uri }.toSet()
                        }

                        var sawOffline by remember { mutableStateOf(false) }
                        val offlineNow = rememberIsOffline()
                        LaunchedEffect(offlineNow) {
                            if (offlineNow) {
                                sawOffline = true
                            } else if (sawOffline) {
                                sawOffline = false
                                log.i("network is back, retrying the screens that came back empty")
                                if (feed.isEmpty()) resumeSignal++
                                if (libraryItems.isNullOrEmpty()) Krespot.library.invalidate()
                                if (whatsNew.isEmpty()) whatsNewFetch++
                            }
                        }

                        LaunchedEffect(playlistUri, addTracksFetch) {
                            if (playlistUri.isBlank()) return@LaunchedEffect
                            addTracksLoading = true
                            try {
                                coroutineScope {
                                    val likedDeferred = async {
                                        Krespot.collections.collection(context, "spotify:collection:tracks")
                                    }
                                    val pageDeferred = async { Krespot.library.page(context) }
                                    addTracksLiked = likedDeferred.await()
                                        ?.tracks?.map { it.toTrackItem() }
                                        .orEmpty()
                                    val page = pageDeferred.await()
                                    addTracksEpisodes = run {
                                        val libraryEpisodes = page?.items
                                            ?.filter { it.uri.startsWith("spotify:episode:") }
                                            ?.map {
                                                TrackItem(
                                                    title = it.title,
                                                    artist = it.subtitle,
                                                    uri = it.uri,
                                                    imageUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                                                )
                                            }
                                            .orEmpty()
                                        val seenEpisodes = libraryEpisodes.map { it.uri }.toHashSet()
                                        libraryEpisodes + PlayHistory.all(context)
                                            .filter {
                                                it.item.uri.startsWith("spotify:episode:") &&
                                                        seenEpisodes.add(it.item.uri)
                                            }
                                            .map { it.item }
                                    }
                                    val sourceUris = page?.items
                                        ?.filter { it.kind == ShelfKind.PLAYLIST && it.uri != playlistUri }
                                        ?.map { it.uri }
                                        .orEmpty()
                                    val seen = HashSet<String>()
                                    addTracksSongs = sourceUris
                                        .map { async { Krespot.collections.collection(context, it) } }
                                        .awaitAll()
                                        .asSequence()
                                        .flatMap { it?.tracks.orEmpty().asSequence() }
                                        .filter { seen.add(it.uri) }
                                        .map { it.toTrackItem() }
                                        .toList()
                                    addTracksRecent = PlayHistory.all(context).map { it.item }
                                }
                            } finally {
                                addTracksLoading = false
                            }
                        }

                        LaunchedEffect(playlistUri, playlistFetch) {
                            if (playlistUri.isBlank()) return@LaunchedEffect
                            val uri = playlistUri
                            playlistLoading = true
                            val detail = Krespot.collections.collection(context, uri)
                            if (uri != playlistUri) return@LaunchedEffect
                            playlistDetail = detail
                            playlistTracks = detail?.tracks?.map { it.toTrackItem() }
                            playlistHasMore = detail?.hasMore == true
                            playlistLoading = false
                            playlistSaved = Krespot.rootlist.saved(context, uri)
                            playlistCanRemoveCover = false
                            val editable = detail?.editable == true
                            coroutineScope {
                                val saved = async { Krespot.rootlist.saved(context, uri) }
                                val cover = if (editable) {
                                    async { Krespot.playlists.hasCustomCover(context, uri) }
                                } else null
                                playlistSaved = saved.await()
                                playlistCanRemoveCover = cover?.await() ?: false
                            }
                        }

                        LaunchedEffect(playlistUri, playlistTracks, recommendationsNonce) {
                            val tracks = playlistTracks
                            if (!playlistUri.startsWith("spotify:playlist:") || tracks == null) {
                                playlistRecommendations = emptyList()
                                return@LaunchedEffect
                            }
                            val inPlaylist = tracks.map { it.uri }
                                .filter { it.startsWith("spotify:track:") }
                                .toHashSet()
                            recommendationsLoading = true
                            try {
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
                                playlistRecommendations = picks.values.toList()
                                log.i("recommended ${picks.size} client track(s) from ${seedArtists.size} artist(s)")
                            } finally {
                                recommendationsLoading = false
                            }
                        }

                        LaunchedEffect(albumUri, albumRetry) {
                            if (albumUri.isBlank()) {
                                albumDetail = null
                                albumTracks = null
                                albumLoading = false
                                albumFailed = false
                                return@LaunchedEffect
                            }
                            val uri = albumUri
                            albumDetail = null
                            albumTracks = null
                            albumFailed = false
                            albumHasMore = false
                            albumLoading = true
                            try {
                                val detail = Krespot.albums.album(context, uri)
                                if (uri != albumUri) return@LaunchedEffect
                                albumDetail = detail
                                albumTracks = detail?.tracks?.map { it.toTrackItem() }
                                albumHasMore = detail != null &&
                                    detail.fetched < detail.total && detail.tracks.isNotEmpty()
                                albumFailed = detail == null
                            } finally {
                                if (uri == albumUri) albumLoading = false
                            }
                        }

                        LaunchedEffect(artistUri, artistRetry) {
                            if (artistUri.isBlank()) {
                                artistDetail = null
                                artistTracks = null
                                artistReleases = null
                                artistListeners = null
                                artistFollowed = null
                                artistLoading = false
                                artistFailed = false
                                return@LaunchedEffect
                            }
                            val uri = artistUri
                            artistDetail = null
                            artistTracks = null
                            artistReleases = null
                            artistListeners = null
                            artistFollowed = null
                            artistFailed = false
                            artistLoading = true
                            try {
                                val detail = Krespot.artists.artist(context, uri)
                                if (uri != artistUri) return@LaunchedEffect
                                artistDetail = detail
                                artistFailed = detail == null
                                artistTracks = detail?.topTracks?.map { row ->
                                    val countFormatted = if (row.plays > 0L) {
                                        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(row.plays)
                                    } else ""
                                    PopularTrack(row.toTrackItem(), countFormatted)
                                }
                                var releaseRows = detail?.releases.orEmpty()
                                if (releaseRows.isEmpty()) {
                                    releaseRows = Krespot.artists.discography(context, uri, "all")?.rows.orEmpty()
                                }
                                artistReleases = releaseRows.map { it.toArtistRelease() }
                                artistListeners = detail?.monthlyListeners?.takeIf { it > 0L }?.let { formatCount(it) }
                                val inLibrary = Library.saved(context, listOf(uri))
                                artistFollowed = if (inLibrary.isEmpty()) {
                                    detail?.saved == true
                                } else {
                                    uri in inLibrary
                                }
                            } finally {
                                artistLoading = false
                            }
                        }

                        LaunchedEffect(folderUri, folderFetch) {
                            if (folderUri.isBlank()) return@LaunchedEffect
                            val page = Krespot.library.folder(context, folderUri)
                            folderItems = page?.items?.map { item ->
                                LibraryItem(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    type = when {
                                        item.uri.startsWith("spotify:user:") -> LibraryItemType.FOLDER
                                        item.uri.startsWith("spotify:artist:") -> LibraryItemType.ARTIST
                                        item.uri.startsWith("spotify:album:") -> LibraryItemType.ALBUM
                                        item.uri.startsWith("spotify:show:") -> LibraryItemType.PODCAST
                                        else -> LibraryItemType.PLAYLIST
                                    },
                                    uri = item.uri,
                                    imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
                                )
                            }
                        }

                        LaunchedEffect(showUri, showFetch) {
                            if (showUri.isBlank()) return@LaunchedEffect
                            val uri = showUri
                            showLoading = true
                            val detail = Podcasts.show(context, uri)
                            if (uri != showUri) return@LaunchedEffect
                            showDetail = detail
                            showLoading = false
                        }

                        LaunchedEffect(episodeUri, episodeFetch) {
                            if (episodeUri.isBlank()) return@LaunchedEffect
                            val uri = episodeUri
                            episodeLoading = true
                            val detail = Podcasts.episode(context, uri)
                            if (uri != episodeUri) return@LaunchedEffect
                            episodeDetail = detail
                            episodeLoading = false
                        }

                        LaunchedEffect(browseUri, browseFetch) {
                            if (browseUri.isBlank()) return@LaunchedEffect
                            browsePage = Browse.page(context, browseUri)
                        }

                        val bridge = remember { PlaybackHost.ensure(context) }
                        val playback = bridge.state
                        val nowPlayingArtistUri = remember(playback.trackUri, playback.artistUri, playback.queue, playback.queueFromContext) {
                            playback.artistUri.ifBlank {
                                (playback.queue + playback.queueFromContext)
                                    .firstOrNull { it.uri == playback.trackUri }?.artistUri.orEmpty()
                            }
                        }
                        var nowPlayingArtist by remember { mutableStateOf<ArtistDetail?>(null) }
                        var nowPlayingArtistFollowed by remember { mutableStateOf<Boolean?>(null) }
                        var nowPlayingLyrics by remember { mutableStateOf<TrackLyrics?>(null) }
                        val artistCache = remember { mutableMapOf<String, ArtistDetail>() }
                        LaunchedEffect(nowPlayingArtistUri) {
                            if (nowPlayingArtistUri.isBlank()) {
                                nowPlayingArtist = null
                                nowPlayingArtistFollowed = null
                                return@LaunchedEffect
                            }
                            val cached = artistCache[nowPlayingArtistUri]
                            if (cached != null) {
                                nowPlayingArtist = cached
                            } else {
                                nowPlayingArtist = Krespot.artists.artist(context, nowPlayingArtistUri)
                                    ?.also {
                                        if (artistCache.size >= 24) artistCache.clear()
                                        artistCache[it.uri] = it
                                    }
                            }
                            val inLibrary = Library.saved(context, listOf(nowPlayingArtistUri))
                            nowPlayingArtistFollowed = if (inLibrary.isEmpty()) {
                                nowPlayingArtist?.saved == true
                            } else {
                                nowPlayingArtistUri in inLibrary
                            }
                        }
                        LaunchedEffect(playback.trackUri) {
                            val uri = playback.trackUri
                            if (!uri.startsWith("spotify:track:")) {
                                nowPlayingLyrics = null
                                return@LaunchedEffect
                            }
                            nowPlayingLyrics = Krespot.lyrics.lyrics(context, uri)
                        }
                        var canvasFile by remember { mutableStateOf<String?>(null) }
                        val canvasAllowed = settingsSwitches.canvas
                        val clientPrefs = remember { com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS) }
                        var canvasEnabled by remember { mutableStateOf(clientPrefs.getBoolean("canvasNpvEnabled", true)) }
                        var fullArtworkPlayer by remember {
                            mutableStateOf(clientPrefs.getBoolean("fullArtworkPlayer", true))
                        }
                        LaunchedEffect(playback.trackUri, canvasAllowed) {
                            canvasFile = null
                            if (!canvasAllowed) return@LaunchedEffect
                            val uri = playback.trackUri
                            if (!uri.startsWith("spotify:track:")) return@LaunchedEffect
                            val file = CanvasKit.download(context, uri)
                            if (uri == playback.trackUri) canvasFile = file
                        }
                        LaunchedEffect(playlistTracks) {
                            val rows = playlistTracks.orEmpty()
                                .take(PLAYLIST_PREFETCH_ROWS)
                                .mapNotNull { it.uri.takeIf { uri -> uri.startsWith("spotify:track:") } }
                            if (rows.isNotEmpty()) bridge.prefetchUris(rows)
                        }
                        LaunchedEffect(bridge) {
                            log.i("starting playback bridge device=${bridge.deviceId}")
                            PlaybackHost.startService(context)
                        }

                        LaunchedEffect(playback.trackUri, playback.title, playback.artistUri) {
                            val now = playback
                            val addable = now.trackUri.startsWith("spotify:track:") ||
                                    now.trackUri.startsWith("spotify:episode:")
                            if (!addable || now.title.isBlank()) {
                                return@LaunchedEffect
                            }
                            val cover = now.coverUrl.takeIf { it.isNotBlank() }
                            val head = playHistory.firstOrNull()?.item
                            if (head?.uri == now.trackUri && head.title == now.title &&
                                head.artistUri == now.artistUri && head.imageUrl == cover
                            ) {
                                return@LaunchedEffect
                            }
                            playHistory = withContext(Dispatchers.Default) {
                                PlayStats.recordListen(context)
                                PlayStats.tallyArtist(context, now.artistUri, now.artist)
                                if (now.contextUri.startsWith("spotify:playlist:")) {
                                    PlayStats.tallyPlaylist(context, now.contextUri, now.contextLabel.ifBlank { "Playlist" })
                                }
                                PlayHistory.record(
                                    context,
                                    TrackItem(
                                        title = now.title,
                                        artist = now.artist,
                                        uri = now.trackUri,
                                        imageUrl = cover,
                                        artistUri = now.artistUri,
                                        isExplicit = now.explicit,
                                    ),
                                    System.currentTimeMillis(),
                                )
                            }
                            playStats = PlayStats.snapshot(context)
                        }

                        LaunchedEffect(playback.isPlaying) {
                            if (!playback.isPlaying) return@LaunchedEffect
                            while (true) {
                                delay(10_000L)
                                PlayStats.addPlayedMs(context, 10_000L)
                                playStats = PlayStats.snapshot(context)
                            }
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
                        val historyRows = playHistory
                        val historyToday = remember(playHistory, startOfToday) {
                            historyRows.filter { it.playedAtMs >= startOfToday }.map { it.item }
                        }
                        val historyYesterday = remember(playHistory, startOfToday, startOfYesterday) {
                            historyRows
                                .filter { it.playedAtMs >= startOfYesterday && it.playedAtMs < startOfToday }
                                .map { it.item }
                        }
                        val historyEarlier = remember(playHistory, startOfYesterday) {
                            historyRows.filter { it.playedAtMs in 1 until startOfYesterday }.map { it.item }
                        }
                        val historyStats = remember(playStats, playHistory) {
                            val replayed = playHistory.filter { it.plays > 1 }.maxByOrNull { it.plays }
                            HistoryStats(
                                totalPlayedMs = playStats.totalMs,
                                listenStreakDays = playStats.listenStreak,
                                loginStreakDays = playStats.loginStreak,
                                topArtistName = playStats.topArtistName,
                                topArtistPlays = playStats.topArtistPlays,
                                topPlaylistName = playStats.topPlaylistName,
                                topPlaylistPlays = playStats.topPlaylistPlays,
                                topReplayTitle = replayed?.item?.title.orEmpty(),
                                topReplayArtist = replayed?.item?.artist.orEmpty(),
                                topReplayPlays = replayed?.plays ?: 0,
                            )
                        }

                        LaunchedEffect(searchTerm, searchFetch, offlineNow) {
                            val term = searchTerm.trim()
                            if (term.isEmpty()) {
                                searchData = null
                                searchHits = emptyList()
                                searchLoading = false
                                searchFailed = false
                                searchSavedOverrides.clear()
                                return@LaunchedEffect
                            }
                            if (offlineNow) {
                                searchLoading = false
                                searchFailed = false
                                if (searchDataFor != term) {
                                    searchHits = emptyList()
                                    searchData = null
                                }
                                return@LaunchedEffect
                            }
                            searchLoading = true
                            searchFailed = false
                            delay(280)
                            searchHits = Search.suggest(context, term)
                            val results = Search.query(context, term)
                            searchData = results?.let { decorateSearchTracks(context, it) }
                            searchDataFor = term
                            searchSavedOverrides.clear()
                            searchFailed = results == null
                            searchLoading = false
                            if (results != null) {
                                log.i("searched '$term'")
                            }
                        }
                        EncoreAppShell(
                                userDisplayName = account?.name?.takeIf { it.isNotBlank() } ?: "Quackify",
                                userAvatarUrl = account?.imageUrl.orEmpty(),
                            playbackState = bridge.state,
                            playerListener = bridge.listener,
                            expandPlayerSignal = if (loggedIn) openPlayerSignal else 0,
                                homeQuickAccessItems = quick,
                            homeShelves = if (homeFacet.isBlank() && !loading) historyShelves + shelves else shelves,
                            homeChips = homeChips,
                            homeFacet = homeFacet,
                            homeGreeting = homeGreeting,
                            onHomeFacetSelected = { facet -> homeFacet = facet },
                            libraryItems = libraryItems,
                            libraryLoading = libraryItems == null,
                            playlistTracks = playlistTracks,
                            playlistDetail = playlistDetail,
                            playlistLoading = playlistLoading,
                            playlistHasMore = playlistHasMore,
                            playlistLoadingMore = playlistLoadingMore,
                            onLoadMorePlaylistTracks = {
                                val current = playlistTracks
                                if (!playlistLoadingMore && playlistHasMore && current != null) {
                                    scope.launch {
                                        playlistLoadingMore = true
                                        val page = Krespot.collections.collectionMore(context, playlistUri, current.size)
                                        if (page != null && page.rows.isNotEmpty()) {
                                            playlistTracks = current + page.rows.map { it.toTrackItem() }
                                            playlistHasMore = page.hasMore
                                        } else if (page != null) {
                                            playlistHasMore = page.hasMore
                                        }
                                        playlistLoadingMore = false
                                        log.i("playlist more '$playlistUri' -> ${page?.rows?.size ?: 0} rows hasMore=${page?.hasMore}")
                                    }
                                }
                            },
                            playlistRecommendations = playlistRecommendations,
                            recommendationsLoading = recommendationsLoading,
                            onRefreshRecommendations = { recommendationsNonce++ },
                            onAddRecommended = { track ->
                                scope.launch {
                                    val ok = Krespot.playlists.addTrack(context, playlistUri, track.uri)
                                    log.i("recommend '${track.title}' into playlist ok=$ok")
                                    if (ok) {
                                        val detail = Krespot.collections.collection(context, playlistUri)
                                        playlistDetail = detail
                                        playlistTracks = detail?.tracks?.map { it.toTrackItem() }
                                    }
                                }
                            },
                            onSavePlaylistOrder = { uri, order ->
                                scope.launch {
                                    val before = playlistTracks.orEmpty()
                                    val current = before.map { it.uid }.toMutableList()
                                    val target = order.map { it.uid }
                                    if (target.any { it.isBlank() }) {
                                        log.w("playlist order save '$uri': missing uids, skipping")
                                        return@launch
                                    }
                                    var moved = 0
                                    for (i in target.indices) {
                                        if (current.getOrNull(i) == target[i]) continue
                                        val anchor = current.getOrNull(i)
                                        if (anchor.isNullOrBlank()) continue
                                        if (!Krespot.playlists.moveItems(
                                                context, uri, listOf(target[i]),
                                                PlaylistSource.Position.BeforeUid(anchor),
                                            )
                                        ) continue
                                        current.remove(target[i])
                                        current.add(i, target[i])
                                        moved++
                                    }
                                    log.i("playlist order save '$uri' -> $moved move(s) of ${target.size}")
                                    if (moved > 0) {
                                        val detail = Krespot.collections.collection(context, uri)
                                        playlistDetail = detail
                                        playlistTracks = detail?.tracks?.map { it.toTrackItem() }
                                    }
                                }
                            },
                            albumTracks = albumTracks,
                            albumDetail = albumDetail,
                            albumLoading = albumLoading,
                            albumFailed = albumFailed,
                            albumHasMore = albumHasMore,
                            albumLoadingMore = albumLoadingMore,
                            onLoadMoreAlbumTracks = {
                                val current = albumTracks
                                if (!albumLoadingMore && albumHasMore && current != null) {
                                    scope.launch {
                                        albumLoadingMore = true
                                        val page = Krespot.albums.albumMore(context, albumUri, current.size)
                                        if (page != null && page.rows.isNotEmpty()) {
                                            albumTracks = current + page.rows.map { it.toTrackItem() }
                                            albumHasMore = page.hasMore
                                        } else if (page != null) {
                                            albumHasMore = page.hasMore
                                        }
                                        albumLoadingMore = false
                                        log.i("album more '$albumUri' -> ${page?.rows?.size ?: 0} rows hasMore=${page?.hasMore}")
                                    }
                                }
                            },
                            onRetryAlbum = { albumRetry++ },
                            nowPlayingArtistBio = nowPlayingArtist?.biography.orEmpty(),
                            nowPlayingMonthlyListeners = nowPlayingArtist?.monthlyListeners
                                ?.takeIf { it > 0L }?.let { formatCount(it) + " monthly listeners" }.orEmpty(),
                            nowPlayingArtistImageUrl = nowPlayingArtist?.imageUrl?.takeIf { it.isNotBlank() },
                            nowPlayingArtistFollowed = nowPlayingArtistFollowed,
                            nowPlayingLyrics = remember(nowPlayingLyrics) {
                                nowPlayingLyrics?.lines?.map {
                                    TimeSyncedLyric(it.startTimeMs, it.text)
                                }.orEmpty()
                            },
                            canvasFilePath = canvasFile,
                            isCanvasAllowed = canvasAllowed,
                            isCanvasEnabled = canvasEnabled,
                            onCanvasToggle = { on ->
                                canvasEnabled = on
                                clientPrefs.edit().putBoolean("canvasNpvEnabled", on).apply()
                                bridge.setCanvas(on)
                            },
                            artistDetail = artistDetail,
                            artistLoading = artistLoading,
                            artistFailed = artistFailed,
                            onRetryArtist = { artistRetry++ },
                            trackCredits = trackCredits,
                            addToPlaylistChoices = addToPlaylistChoices,
                            addTracksSongs = addTracksSongs,
                            addTracksEpisodes = addTracksEpisodes,
                            addTracksLiked = addTracksLiked,
                            addTracksRecent = addTracksRecent,
                            addTracksLoading = addTracksLoading,
                            onAddTracksOpen = { addTracksFetch++ },
                            onAddTrack = { trackUri ->
                                val ok = Krespot.playlists.addTrack(context, playlistUri, trackUri)
                                log.i("add '$trackUri' -> '$playlistUri' ok=$ok")
                                ok
                            },
                            onShowCredits = { uri ->
                                trackCredits = null
                                scope.launch {
                                    trackCredits = if (uri.isBlank()) null else Krespot.albums.credits(context, uri)
                                }
                            },
                            onAddToPlaylist = { track ->
                                addToPlaylistChoices = null
                                scope.launch {
                                    addToPlaylistChoices = if (track.uri.isBlank()) emptyList()
                                    else Krespot.playlists.editable(context, track.uri)
                                }
                            },
                            onPickPlaylist = { choice, track ->
                                scope.launch {
                                    val name = choice.name
                                    if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
                                        val saved = Library.setSaved(context, listOf(track.uri), true)
                                        log.i("add '${track.title}' to Liked Songs ok=$saved")
                                    } else {
                                        val added = Krespot.playlists.addTrack(context, choice.uri, track.uri)
                                        log.i("add '${track.title}' to '$name' ok=$added")
                                    }
                                }
                            },
                            onTogglePlaylist = { choice, track, shouldAdd ->
                                scope.launch {
                                    val name = choice.name
                                    if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
                                        if (track.uri == bridge.state.trackUri && bridge.state.isLiked != shouldAdd) {
                                            bridge.listener.onLikeToggle()
                                        } else {
                                            val saved = Library.setSaved(context, listOf(track.uri), shouldAdd)
                                            log.i("${if (shouldAdd) "add" else "remove"} '${track.title}' to/from Liked Songs ok=$saved")
                                        }
                                    } else {
                                        val res = if (shouldAdd) {
                                            Krespot.playlists.addTrack(context, choice.uri, track.uri)
                                        } else {
                                            Krespot.playlists.removeTrack(context, choice.uri, track.uri)
                                        }
                                        log.i("${if (shouldAdd) "add" else "remove"} '${track.title}' to/from '$name' ok=$res")
                                    }
                                }
                            },
                            onLoadPlaylistFolder = { trackUri, folderUri ->
                                Krespot.playlists.editable(context, trackUri, folderUri)
                            },
                            onLoadTrackCount = { playlistUri ->
                                Krespot.playlists.trackCount(context, playlistUri)
                            },
                            onCreatePlaylistWith = { track ->
                                scope.launch {
                                    val name = Krespot.playlists.defaultName(context)
                                    val uri = Krespot.playlists.create(context, name, track.uri)
                                    log.i("create playlist '$name' ok=${uri != null}")
                                    if (uri != null) {
                                        Krespot.library.invalidate()
                                    }
                                }
                            },
                            onPlaylistDefaultName = { Krespot.playlists.defaultName(context) },
                            onCreatePlaylist = { name, description, cover ->
                                val uri = Krespot.playlists.create(context, name, "")
                                log.i("create playlist '$name' ok=${uri != null}")
                                if (uri != null) {
                                    val picture = cover?.let { Krespot.playlists.uploadCover(context, uri, it) }
                                    if (description.isNotBlank() || picture != null) {
                                        Krespot.playlists.edit(context, uri, name, description, picture)
                                    }
                                    Krespot.library.invalidate()
                                }
                                uri
                            },
                            playlistSaved = playlistSaved,
                            playlistEditDraft = playlistEditDraft,
                            editablePlaylistUris = editableUris,
                            onEditPlaylist = { uri ->
                                playlistEditDraft = null
                                scope.launch {
                                    val loaded = if (uri == playlistUri) playlistDetail else null
                                    playlistEditDraft = loaded?.let {
                                        PlaylistEditDraft(
                                            uri = uri,
                                            name = it.title,
                                            description = it.description,
                                            coverUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                                            canRemoveCover = playlistCanRemoveCover,
                                        )
                                    } ?: Krespot.collections.collection(context, uri)?.let {
                                        PlaylistEditDraft(
                                            uri = uri,
                                            name = it.title,
                                            description = it.description,
                                            coverUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                                            canRemoveCover = if (it.editable) Krespot.playlists.hasCustomCover(context, uri) else false,
                                        )
                                    }
                                }
                            },
                            onDismissEditPlaylist = { playlistEditDraft = null },
                            onEditPlaylistDetails = { uri, name, description, cover, clearCover ->
                                val picture = cover?.let { Krespot.playlists.uploadCover(context, uri, it) }
                                val effective = when {
                                    picture != null -> picture
                                    clearCover -> ""
                                    else -> null
                                }
                                val ok = Krespot.playlists.edit(context, uri, name, description, effective)
                                log.i("edit playlist '$uri' cover=${picture != null} clear=$clearCover ok=$ok")
                                if (ok) {
                                    Krespot.library.invalidate()
                                    playlistDetail = Krespot.collections.collection(context, uri)
                                }
                                ok
                            },
                            onDeletePlaylist = { uri ->
                                val ok = Krespot.playlists.delete(context, uri)
                                log.i("delete playlist '$uri' ok=$ok")
                                if (ok) {
                                    playlistUri = ""
                                    playlistDetail = null
                                    playlistTracks = null
                                    Krespot.library.invalidate()
                                }
                                ok
                            },
                            onTogglePlaylistSaved = { uri, saved ->
                                scope.launch {
                                    val ok = Krespot.rootlist.setSaved(context, uri, saved)
                                    log.i("playlist '$uri' in library -> $saved ok=$ok")
                                    if (ok) {
                                        Krespot.library.invalidate()
                                        playlistSaved = saved
                                    }
                                }
                            },
                            onRemoveTrackFromPlaylist = { pUri, tUri ->
                                scope.launch {
                                    val ok = Krespot.playlists.removeTrack(context, pUri, tUri)
                                    log.i("remove track '$tUri' from playlist '$pUri' ok=$ok")
                                    if (ok && pUri == playlistUri) {
                                        playlistTracks = playlistTracks?.filterNot { it.uri == tUri }
                                    }
                                }
                            },
                            onRemoveLibraryItem = { uri ->
                                scope.launch {
                                    val ok = if (uri.startsWith("spotify:playlist:")) {
                                        Krespot.rootlist.remove(context, uri)
                                    } else {
                                        Library.setSaved(context, listOf(uri), false)
                                    }
                                    log.i("remove '$uri' from library ok=$ok")
                                }
                            },
                            onCreateFolder = { name ->
                                val uri = Krespot.folders.createFolder(context, name)
                                log.i("create folder '$name' ok=${uri != null}")
                                uri
                            },
                            onRenameFolder = { uri, name ->
                                scope.launch {
                                    val ok = Krespot.folders.renameFolder(context, uri, name)
                                    log.i("rename folder '$uri' -> '$name' ok=$ok")
                                }
                            },
                            onDeleteFolder = { uri ->
                                scope.launch {
                                    val ok = Krespot.folders.deleteFolder(context, uri)
                                    log.i("delete folder '$uri' ok=$ok")
                                }
                            },
                            onMoveToFolder = { playlistUri, targetFolderUri ->
                                scope.launch {
                                    val ok = if (targetFolderUri.isBlank()) {
                                        Krespot.folders.removeFromFolder(context, playlistUri)
                                    } else {
                                        Krespot.folders.moveIntoFolder(context, playlistUri, targetFolderUri)
                                    }
                                    log.i("move '$playlistUri' into '$targetFolderUri' ok=$ok")
                                    if (ok && folderUri.isNotBlank()) {
                                        folderItems = null
                                        folderFetch++
                                    }
                                }
                            },
                            artistTracks = artistTracks,
                            artistReleases = artistReleases,
                            artistMonthlyListeners = artistListeners,
                            artistFollowed = artistFollowed,
                            onArtistFollowToggle = { uri, follow ->
                                scope.launch {
                                    val ok = Library.setSaved(context, listOf(uri), follow)
                                    log.i("artist follow '$uri' -> $follow ok=$ok")
                                    if (!ok) return@launch
                                    if (uri == artistUri) artistFollowed = follow
                                    if (uri == nowPlayingArtistUri) nowPlayingArtistFollowed = follow
                                }
                            },
                            onArtistRadio = { uri ->
                                val radio = Krespot.artists.artistRadio(context, uri)
                                log.i("artist radio '$uri' -> '$radio'")
                                if (radio == null) {
                                    Toast.makeText(
                                        context,
                                        "Couldn't load the radio",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                radio
                            },
                            homeLoading = loading,
                            searchResults = remember(searchHits) {
                                searchHits.map { hit ->
                                    com.quackify.compose.playlist.TrackItem(
                                        title = hit.title,
                                        artist = hit.subtitle,
                                        uri = hit.uri,
                                        imageUrl = hit.imageUrl,
                                    )
                                }
                            },
                            onSearchQueryChange = { query -> searchTerm = query },
                            searchSuggestionHits = searchHits,
                            browseCategories = browseCategories,
                            searchSuggestions = remember(searchHits) {
                                searchHits.map { hit ->
                                    TrackItem(
                                        title = hit.title,
                                        artist = hit.subtitle,
                                        uri = hit.uri,
                                        imageUrl = hit.imageUrl,
                                    )
                                }
                            },
                            searchRecent = remember(recentSearches) {
                                recentSearches.map { entry ->
                                    SearchHistoryItem(
                                        title = entry.title,
                                        subtitle = entry.subtitle,
                                        kind = runCatching { SearchKind.valueOf(entry.kind) }
                                            .getOrDefault(SearchKind.QUERY),
                                        colorHex = entry.colorHex,
                                        uri = entry.uri,
                                        imageUrl = entry.imageUrl,
                                    )
                                }
                            },
                            searchLoading = searchLoading,
                            searchFailed = searchFailed,
                            onRetrySearch = { searchFetch++ },
                            searchQuery = searchTerm,
                            searchData = searchData,
                            onClearRecentSearches = {
                                RecentSearches.clear(context)
                                recentSearches = emptyList()
                                log.i("cleared the recent searches on this device")
                            },
                            onRemoveRecentSearch = { item ->
                                RecentSearches.remove(context, item.uri.ifBlank { item.title })
                                recentSearches = RecentSearches.all(context)
                                log.i("removed '${item.title}' from the recent searches")
                            },
                            onRecordRecentSearch = { hit ->
                                RecentSearches.record(context, hit)
                                recentSearches = RecentSearches.all(context)
                            },
                            onLoadSearchSection = { kind, offset ->
                                Search.section(context, kind, searchTerm, offset)
                            },
                            onToggleSaved = { uri, saved ->
                                scope.launch {
                                    val next = !saved
                                    val ok = Library.setSaved(context, listOf(uri), next)
                                    log.i("search heart '$uri' -> $next ok=$ok")
                                    if (ok) {
                                        searchSavedOverrides[uri] = next
                                        searchData = searchData?.withSaved(uri, next)
                                    }
                                }
                            },
                            searchSavedOverrides = searchSavedOverrides.toMap(),
                            browsePage = browsePage,
                            categoryShelves = categoryShelves,
                            fullArtworkPlayer = fullArtworkPlayer,
                            onToggleFullArtwork = { on ->
                                fullArtworkPlayer = on
                                clientPrefs.edit().putBoolean("fullArtworkPlayer", on).apply()
                            },
                            onOpenCategory = { category ->
                                categoryUri = "spotify:genre:" + category.lowercase().replace(" ", "-")
                                categoryShelves = null
                            },
                            showDetail = showDetail,
                            showLoading = showLoading,
                            episodeDetail = episodeDetail,
                            episodeLoading = episodeLoading,
                            onOpenShow = { uri ->
                                showUri = uri
                                showFetch++
                                showDetail = null
                            },
                            onOpenEpisode = { uri ->
                                episodeUri = uri
                                episodeFetch++
                                episodeDetail = null
                            },
                            onOpenBrowse = { uri ->
                                browseUri = uri
                                browseFetch++
                                browsePage = null
                            },
                            folderItems = folderItems,
                            folderLoading = folderItems == null,
                            onOpenFolder = { uri ->
                                folderUri = uri
                                folderFetch++
                                folderItems = null
                            },
                            onLoadPlaylist = { uri ->
                                if (uri != playlistUri) {
                                    playlistUri = uri
                                    playlistDetail = null
                                    playlistTracks = null
                                    playlistHasMore = false
                                    playlistLoading = true
                                    playlistRecommendations = emptyList()
                                    playlistSaved = null
                                }
                            },
                            onRetryPlaylist = {
                                playlistDetail = null
                                playlistTracks = null
                                playlistLoading = true
                                playlistFetch++
                            },
                            onLoadAlbum = { uri, _ -> albumUri = uri },
                            onLoadArtist = { uri, _ -> artistUri = uri },
                            settingsSwitches = settingsSwitches,
                            settingsLocks = settingsLocks,
                            settingsProduct = settingsProduct,
                            settingsCountry = settingsCountry,
                            settingsLoading = settingsLoading,
                            onToggleSetting = { field, value ->
                                val before = settingsSwitches
                                settingsSwitches = when (field) {
                                    "canvasDisabled" -> before.copy(canvas = !value)
                                    "musicVideoDisabled" -> before.copy(musicVideos = !value)
                                    "otherVideoDisabled" -> before.copy(otherVideos = !value)
                                    "showFollows" -> before.copy(showFollows = !value)
                                    "showPublishedPlaylists" -> before.copy(showPlaylists = !value)
                                    else -> before
                                }
                                scope.launch {
                                    val ok = when (field) {
                                        "showFollows" -> Settings.setPrivacy(
                                            context, account?.id.orEmpty(), showFollows = !value,
                                        )
                                        "showPublishedPlaylists" -> Settings.setPrivacy(
                                            context, account?.id.orEmpty(), showPublishedPlaylists = !value,
                                        )
                                        else -> Settings.setAttribute(context, field, value)
                                    }
                                    log.i("settings $field -> $value ok=$ok")
                                    if (!ok) settingsSwitches = before
                                }
                            },
                            notifications = whatsNew,
                            notificationsLoading = whatsNewLoading,
                            notificationsFailed = whatsNewFailed,
                            onRefreshWhatsNew = { whatsNewFetch++ },
                            historyToday = historyToday,
                            historyYesterday = historyYesterday,
                            historyEarlier = historyEarlier,
                            historyStats = historyStats,
                            historyLoading = false,
                            onLogout = {
                                log.i("logout tapped")
                                PlaybackHost.reset(context)
                                EncoreAuth.logout(context)
                                Krespot.library.invalidate()
                                val remaining = EncoreAuth.savedAccounts(context)
                                if (remaining.isNotEmpty()) {
                                    EncoreAuth.switchAccount(context, remaining.last().id)
                                    (context as? Activity)?.recreate()
                                } else {
                                    loggedIn = false
                                }
                            },
                            savedAccounts = savedAccountsList,
                            activeAccountId = activeAccountId,
                            onSwitchAccount = { id ->
                                log.i("switch account tapped id=$id")
                                PlaybackHost.reset(context)
                                if (EncoreAuth.switchAccount(context, id)) {
                                    Krespot.library.invalidate()
                                    (context as? Activity)?.recreate()
                                }
                            },
                            onAddAccount = {
                                log.i("add account tapped")
                                scope.launch {
                                    EncoreAuth.userId(context, force = true)
                                    EncoreAuth.storeCurrentAccount(context)
                                    savedAccountsList = EncoreAuth.savedAccounts(context)
                                    EncoreAuth.beginAddAccount(context)
                                    addingAccount = true
                                }
                            },
                            onRemoveAccount = { id ->
                                log.i("remove account tapped id=$id")
                                val wasActive = EncoreAuth.forgetAccount(context, id)
                                if (wasActive) {
                                    PlaybackHost.reset(context)
                                    val remaining = EncoreAuth.savedAccounts(context)
                                    if (remaining.isNotEmpty()) {
                                        EncoreAuth.switchAccount(context, remaining.last().id)
                                        Krespot.library.invalidate()
                                        (context as? Activity)?.recreate()
                                    } else {
                                        EncoreAuth.logout(context)
                                        Krespot.library.invalidate()
                                        loggedIn = false
                                    }
                                } else {
                                    savedAccountsList = EncoreAuth.savedAccounts(context)
                                }
                            }
                        )

                        if (addingAccount) {
                            LoginScreen(
                                onLoggedIn = {
                                    log.i("add account completed")
                                    EncoreAuth.storeCurrentAccount(context)
                                    PlaybackHost.reset(context)
                                    Krespot.library.invalidate()
                                    addingAccount = false
                                    (context as? Activity)?.recreate()
                                },
                                isDuplicateAccount = { candidate ->
                                    candidate.id.isNotBlank() &&
                                        savedAccountsList.any { it.id == candidate.id }
                                },
                                onDuplicateLogin = { duplicate ->
                                    log.w("add account refused: '${duplicate.name}' (${duplicate.id}) is already saved")
                                    if (activeAccountId.isBlank() ||
                                        !EncoreAuth.switchAccount(context, activeAccountId)
                                    ) {
                                        EncoreAuth.invalidateToken(context)
                                        EncoreAuth.applySession(context)
                                    }
                                    addingAccount = false
                                    Toast.makeText(
                                        context,
                                        "'${duplicate.name.ifBlank { "This account" }}' is already added",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onBackExhausted = {
                                    log.i("add account cancelled, restoring the active session")
                                    EncoreAuth.applySession(context)
                                    addingAccount = false
                                }
                            )
                        }
                    }
                } else {
                    LoginScreen(
                        onLoggedIn = {
                            log.i("login completed")
                            EncoreAuth.storeCurrentAccount(context)
                            PlaybackHost.reset(context)
                            Krespot.library.invalidate()
                            loggedIn = true
                        },
                        onBackExhausted = {
                            if (EncoreAuth.savedAccounts(context).isNotEmpty()) {
                                log.i("login backed out with a saved account, restoring its session")
                                EncoreAuth.applySession(context)
                                if (EncoreAuth.token(context).isNotBlank()) {
                                    loggedIn = true
                                } else {
                                    (context as? Activity)?.finish()
                                }
                            } else {
                                log.i("login backed out, leaving the app")
                                (context as? Activity)?.finish()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pausedAt = System.currentTimeMillis()
        sawStop = false
    }

    override fun onStop() {
        super.onStop()
        sawStop = true
    }

    override fun onDestroy() {
        if (isChangingConfigurations) {
            super.onDestroy()
            return
        }
        if (sawStop && PlaybackHost.started) {
            log.i("activity gone, handing the stream back and stopping the service")
            PlaybackHost.teardown(this)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val away = if (pausedAt == 0L) 0L else System.currentTimeMillis() - pausedAt
        pausedAt = 0L
        if (away > RESUME_REFRESH_AFTER_MS) {
            log.i("back after ${away / 1000}s, refreshing")
            resumeSignal++
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) {
            openPlayerSignal++
        }
    }

    private companion object {
        const val RESUME_REFRESH_AFTER_MS = 60_000L
        const val TOKEN_STILL_GOOD_MS = 60_000L
    }
}

/**
 * The ranked search answer decorates only its first row fully: every track
 * below the top result can arrive with a bare name and uri, which left the
 * Songs list without covers or artist lines. One decorateContextTracks
 * batch fills exactly those rows before the list reaches the screen.
 */
private suspend fun decorateSearchTracks(
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

private const val FAVORITE_ARTIST_LIMIT = 10

private const val PLAYLIST_PREFETCH_ROWS = 8

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
private fun favoriteArtistsOf(history: List<PlayedTrack>): List<TrackItem> =
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
