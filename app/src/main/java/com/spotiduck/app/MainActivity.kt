package com.spotiduck.app

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.backend.api.HomeFeed
import com.spotiduck.backend.model.HomeSection
import com.spotiduck.backend.api.Library
import com.spotiduck.backend.core.net.Net
import com.spotiduck.backend.core.net.Pathfinder
import com.spotiduck.compose.library.LibraryItem
import com.spotiduck.compose.library.LibraryItemType
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.screens.PopularTrack
import com.spotiduck.compose.screens.ArtistRelease
import com.spotiduck.compose.home.toTrackItem
import com.spotiduck.compose.home.toArtistRelease
import com.spotiduck.backend.model.ShelfKind
import androidx.compose.runtime.collectAsState
import com.spotiduck.backend.api.Search
import com.spotiduck.backend.api.WhatsNew
import com.spotiduck.compose.screens.SettingsSwitches
import com.spotiduck.compose.screens.SettingsLocks
import com.spotiduck.backend.api.Settings
import com.spotiduck.compose.screens.SpotifyNotification
import com.spotiduck.compose.search.SearchHistoryItem
import com.spotiduck.compose.screens.CategoryShelf
import com.spotiduck.compose.screens.CategoryRecommendationItem
import com.spotiduck.compose.format.parseHexColor
import androidx.compose.ui.graphics.Color
import com.spotiduck.backend.api.Browse
import com.spotiduck.backend.api.Podcasts
import com.spotiduck.backend.model.BrowsePage
import com.spotiduck.backend.model.EpisodeDetail
import com.spotiduck.backend.model.ShowDetail
import com.spotiduck.backend.model.SearchHit
import com.spotiduck.backend.model.SearchResults
import com.spotiduck.compose.home.toQuickAccess
import com.spotiduck.compose.home.toShelves
import com.spotiduck.compose.screens.LoginScreen
import com.spotiduck.compose.screens.PathfinderCaptureScreen
import com.spotiduck.compose.shell.SpotifyAppShell
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.backend.core.webview.SilentTokenRefresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.library.Krespot

private val log = Logs.of(Logs.Module.MAIN)

@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : ComponentActivity() {

    private var resumeSignal by mutableStateOf(0)
    private var pausedAt = 0L
    private var sawStop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        Pathfinder.silentRefresh = { context, reason -> SilentTokenRefresh.refresh(context, reason) }
        Net.onUnauthorized = { sent, reason ->
            val current = SpotifyAuth.token(this)
            val left = SpotifyAuth.tokenRemainingMs(this)
            when {
                current.isNotBlank() && current != sent -> {
                    log.i("$reason 401, a newer token is already in the store")
                    current
                }
                left > TOKEN_STILL_GOOD_MS -> {
                    log.w("$reason 401 with ${left / 1000}s left, not spending a refresh on it")
                    null
                }
                SilentTokenRefresh.refresh(this, reason) -> {
                    SpotifyAuth.token(this).takeIf { it.isNotBlank() && it != sent }
                }
                else -> {
                    log.w("$reason 401 and the silent refresh failed, giving up on this call")
                    null
                }
            }
        }
        Library.onLibraryChanged = { Krespot.library.invalidate() }
        setContent {
            SpotifyEncoreTheme {
                val context = LocalContext.current
                var loggedIn by remember { mutableStateOf(SpotifyAuth.isLoggedIn(context)) }
                log.v("composition loggedIn=$loggedIn")

                if (loggedIn) {
                    var capturing by remember {
                        val tokenUsable = SpotifyAuth.token(context).isNotBlank() &&
                            !SpotifyAuth.tokenExpired(context)
                        mutableStateOf(Pathfinder.shouldCapture(context, tokenUsable))
                    }
                    val ttl = SpotifyAuth.tokenRemainingMs(context)
                        .let { if (it < 0L) "unknown" else "${it / 1000}s" }
                    log.v("loggedIn capturing=$capturing ops=${Pathfinder.count()} token=$ttl")
                    if (capturing) {
                        PathfinderCaptureScreen(onDone = {
                            log.i("capture finished ops=${Pathfinder.count()}")
                            capturing = false
                        })
                    } else {
                        var feed by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
                        var loading by remember { mutableStateOf(true) }
                        LaunchedEffect(resumeSignal) {
                            if (resumeSignal == 0) loading = true
                            val fresh = HomeFeed.load(context)
                            if (fresh.isNotEmpty() || resumeSignal == 0) feed = fresh
                            loading = false
                            log.i("home feed loaded sections=${feed.size} signal=$resumeSignal"
                            )
                        }
                        LaunchedEffect(resumeSignal) {
                            if (resumeSignal > 0) SilentTokenRefresh.maybeRefresh(context)
                        }
                        val account = remember(loggedIn) { SpotifyAuth.account(context) }
                        val quick = remember(feed) { feed.toQuickAccess() }
                        val shelves = remember(feed) { feed.toShelves() }
                        LaunchedEffect(shelves) {
                            log.i("home shelves=${shelves.size} podcasts=${shelves.count { it.podcasts }} " +
                                    "titles=${shelves.take(8).joinToString(" | ") { it.title.ifBlank { "-" } }}"
                            )
                        }

                        var searchTerm by remember { mutableStateOf("") }
                        var searchData by remember { mutableStateOf<SearchResults?>(null) }
                        var searchHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
                        var searchLoading by remember { mutableStateOf(false) }
                        val recentSearches = remember { mutableStateListOf<SearchHit>() }
                        val scope = rememberCoroutineScope()

                        var showUri by remember { mutableStateOf("") }
                        var showDetail by remember { mutableStateOf<ShowDetail?>(null) }
                        var episodeUri by remember { mutableStateOf("") }
                        var episodeDetail by remember { mutableStateOf<EpisodeDetail?>(null) }
                        var browseUri by remember { mutableStateOf("") }
                        var browsePage by remember { mutableStateOf<BrowsePage?>(null) }
                        var categoryUri by remember { mutableStateOf("") }
                        var categoryShelves by remember { mutableStateOf<List<CategoryShelf>?>(null) }

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
                        var whatsNew by remember { mutableStateOf<List<SpotifyNotification>>(emptyList()) }
                        var whatsNewLoading by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            whatsNewLoading = true
                            val feed = WhatsNew.feed(context)
                            whatsNew = feed?.items.orEmpty().map { item ->
                                SpotifyNotification(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    timestamp = item.timestamp,
                                    isMusic = item.isMusic,
                                    id = item.id,
                                    isNew = item.isNew,
                                    imageUrl = item.imageUrl,
                                )
                            }
                            whatsNewLoading = false
                        }
                        var playlistUri by remember { mutableStateOf("") }
                        var playlistTracks by remember { mutableStateOf<List<TrackItem>?>(null) }
                        var albumUri by remember { mutableStateOf("") }
                        var albumTracks by remember { mutableStateOf<List<TrackItem>?>(null) }
                        var artistUri by remember { mutableStateOf("") }
                        var artistTracks by remember { mutableStateOf<List<PopularTrack>?>(null) }
                        var artistReleases by remember { mutableStateOf<List<ArtistRelease>?>(null) }
                        var artistListeners by remember { mutableStateOf<String?>(null) }

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
                                        ShelfKind.SHOW, ShelfKind.EPISODE -> LibraryItemType.PODCAST
                                        else -> LibraryItemType.PLAYLIST
                                    },
                                    uri = item.uri,
                                    imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
                                )
                            }
                        }

                        LaunchedEffect(playlistUri) {
                            if (playlistUri.isBlank()) return@LaunchedEffect
                            playlistTracks = Krespot.collections.collection(context, playlistUri)
                                ?.tracks?.map { it.toTrackItem() }
                        }

                        LaunchedEffect(albumUri) {
                            if (albumUri.isBlank()) return@LaunchedEffect
                            albumTracks = Krespot.albums.album(context, albumUri)
                                ?.tracks?.map { it.toTrackItem() }
                        }

                        LaunchedEffect(artistUri) {
                            if (artistUri.isBlank()) return@LaunchedEffect
                            val detail = Krespot.artists.artist(context, artistUri)
                            artistTracks = detail?.topTracks?.map { PopularTrack(it.toTrackItem(), "") }
                            artistReleases = detail?.albums?.map { it.toArtistRelease() }
                            artistListeners = detail?.monthlyListeners?.takeIf { it > 0L }?.toString()
                        }

                        LaunchedEffect(folderUri) {
                            if (folderUri.isBlank()) return@LaunchedEffect
                            val page = Krespot.library.folder(context, folderUri)
                            folderItems = page?.items?.map { item ->
                                LibraryItem(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    type = when {
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

                        LaunchedEffect(showUri) {
                            if (showUri.isBlank()) return@LaunchedEffect
                            showDetail = Podcasts.show(context, showUri)
                        }

                        LaunchedEffect(episodeUri) {
                            if (episodeUri.isBlank()) return@LaunchedEffect
                            episodeDetail = Podcasts.episode(context, episodeUri)
                        }

                        LaunchedEffect(browseUri) {
                            if (browseUri.isBlank()) return@LaunchedEffect
                            browsePage = Browse.page(context, browseUri)
                        }

                        val bridge = remember { PlaybackHost.ensure(context) }
                        LaunchedEffect(bridge) {
                            log.i("starting playback bridge device=${bridge.deviceId}")
                            PlaybackHost.startService(context)
                        }

                        LaunchedEffect(Unit) {
                            val recent = Search.recent(context)
                            if (recent.isNotEmpty() && recentSearches.isEmpty()) {
                                recentSearches.addAll(recent)
                            }
                        }

                        LaunchedEffect(searchTerm) {
                            val term = searchTerm.trim()
                            if (term.isEmpty()) {
                                searchData = null
                                searchHits = emptyList()
                                searchLoading = false
                                return@LaunchedEffect
                            }
                            delay(280)
                            searchLoading = true
                            searchHits = Search.suggest(context, term)
                            searchData = Search.query(context, term)
                            searchLoading = false
                        }
                        SpotifyAppShell(
                                userDisplayName = account?.name?.takeIf { it.isNotBlank() } ?: "SpotiDuck",
                                userAvatarUrl = account?.imageUrl.orEmpty(),
                            playbackState = bridge.state,
                            playerListener = bridge.listener,
                                homeQuickAccessItems = quick,
                            homeShelves = shelves,
                            libraryItems = libraryItems,
                            playlistTracks = playlistTracks,
                            albumTracks = albumTracks,
                            artistTracks = artistTracks,
                            artistReleases = artistReleases,
                            artistMonthlyListeners = artistListeners,
                            homeLoading = loading,
                            searchResults = searchHits.map { hit ->
                                com.spotiduck.compose.playlist.TrackItem(
                                    title = hit.title,
                                    artist = hit.subtitle,
                                    uri = hit.uri,
                                    imageUrl = hit.imageUrl,
                                )
                            },
                            onSearchQueryChange = { query -> searchTerm = query },
                            searchSuggestions = searchHits.map { hit ->
                                TrackItem(
                                    title = hit.title,
                                    artist = hit.subtitle,
                                    uri = hit.uri,
                                    imageUrl = hit.imageUrl,
                                )
                            },
                            searchRecent = recentSearches.map { hit ->
                                SearchHistoryItem(
                                    title = hit.title,
                                    subtitle = hit.subtitle,
                                    isArtist = hit.uri.startsWith("spotify:artist:"),
                                )
                            },
                            searchLoading = searchLoading,
                            searchQuery = searchTerm,
                            searchData = searchData,
                            onClearRecentSearches = {
                                recentSearches.clear()
                            },
                            onLoadSearchSection = { kind, offset ->
                                Search.section(context, kind, searchTerm, offset)
                            },
                            onToggleSaved = { uri ->
                                scope.launch {
                                    val next = !(searchData?.tracks.orEmpty()
                                        .firstOrNull { it.uri == uri }?.saved ?: false)
                                    val ok = Library.setSaved(context, listOf(uri), next)
                                    log.i("search heart '$uri' -> $next ok=$ok")
                                }
                            },
                            browsePage = browsePage,
                            categoryShelves = categoryShelves,
                            onOpenCategory = { category ->
                                categoryUri = "spotify:genre:" + category.lowercase().replace(" ", "-")
                                categoryShelves = null
                            },
                            showDetail = showDetail,
                            episodeDetail = episodeDetail,
                            onOpenShow = { uri ->
                                showUri = uri
                                showDetail = null
                            },
                            onOpenEpisode = { uri ->
                                episodeUri = uri
                                episodeDetail = null
                            },
                            onOpenBrowse = { uri ->
                                browseUri = uri
                                browsePage = null
                            },
                            folderItems = folderItems,
                            onOpenFolder = { uri ->
                                folderUri = uri
                                folderItems = null
                            },
                            onLoadPlaylist = { uri -> playlistUri = uri },
                            onLoadAlbum = { uri, _ -> albumUri = uri },
                            onLoadArtist = { uri, _ -> artistUri = uri },
                            settingsSwitches = settingsSwitches,
                            settingsLocks = settingsLocks,
                            settingsProduct = settingsProduct,
                            settingsCountry = settingsCountry,
                            settingsLoading = settingsLoading,
                            onToggleSetting = { field, value ->
                                scope.launch {
                                    val ok = Settings.setAttribute(context, field, !value)
                                    log.i("settings $field -> $value ok=$ok")
                                }
                            },
                            notifications = whatsNew,
                            notificationsLoading = whatsNewLoading,
                            onLogout = {
                                log.i("logout tapped")
                                SpotifyAuth.logout(context)
                                PlaybackHost.reset(context)
                                Krespot.library.invalidate()
                                loggedIn = false
                            }
                        )
                    }
                } else {
                    LoginScreen(onLoggedIn = {
                        log.i("login completed")
                        PlaybackHost.reset(context)
                        Krespot.library.invalidate()
                        loggedIn = true
                    })
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

    private companion object {
        const val RESUME_REFRESH_AFTER_MS = 60_000L
        const val TOKEN_STILL_GOOD_MS = 60_000L
    }
}
