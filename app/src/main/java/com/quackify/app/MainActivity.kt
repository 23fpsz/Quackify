package com.quackify.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quackify.app.search.decorateSearchTracks
import com.quackify.backend.api.Browse
import com.quackify.backend.api.ArtistBanStore
import com.quackify.backend.api.Collection2
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.api.Library
import com.quackify.backend.api.Podcasts
import com.quackify.backend.api.Profile
import com.quackify.backend.api.Search
import com.quackify.backend.api.Settings
import com.quackify.backend.api.WhatsNew
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.locale.AppLocale
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ConnectivityMonitor
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
import com.quackify.backend.model.EpisodeComments
import com.quackify.backend.model.EpisodeTranscript
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
import com.quackify.backend.model.UserProfile
import com.quackify.compose.components.QuackifyToastHost
import com.quackify.compose.components.UpdateDialog
import com.quackify.compose.components.UpdateInstallPhase
import com.quackify.compose.components.rememberQuackifyToastState
import com.quackify.compose.format.formatCount
import com.quackify.compose.format.parseHexColor
import com.quackify.app.widget.WidgetSync
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
import com.quackify.compose.player.LyricWordSpan
import com.quackify.compose.player.LyricSyllableSpan
import com.quackify.compose.player.TranscriptLine
import com.quackify.compose.player.TranscriptWordSpan
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.AppUpdate
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.CategoryRecommendationItem
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.EncoreNotification
import com.quackify.compose.screens.HistoryStats
import com.quackify.compose.screens.HomeSplashHost
import com.quackify.compose.screens.resetHomeSplash
import com.quackify.compose.screens.LoginScreen
import com.quackify.compose.screens.PathfinderCaptureScreen
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.ProfileContextCard
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.shell.EncoreAppShell
import com.quackify.compose.shell.ActivityFeedState
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
import androidx.lifecycle.lifecycleScope
import java.util.Calendar

private val log = Logs.of(Logs.Module.MAIN)

class MainActivity : ComponentActivity() {

    internal var canvasResolution by mutableStateOf(1080)
    internal val clientPrefs by lazy {
        com.quackify.backend.core.store.AppPrefs.prefs(applicationContext, CLIENT_PREFS)
    }
    private var resumeSignal by mutableStateOf(0)
    internal var pendingLink by mutableStateOf<SpotLink?>(null)
    internal var videoFullscreen by mutableStateOf(false)
    internal var videoAutoExitSignal by mutableStateOf(0)

    /** Bumped every time the playback notification's body is tapped; the
     * shell watches it and slides the Now Playing view over whatever is
     * on screen. */
    internal var openPlayerSignal by mutableStateOf(0)
    private var pausedAt = 0L
    private var sawStop = false

    /** Flips when the stored session is proven unable to mint tokens, so the
     * composition swaps to the login screen with a toast instead of letting
     * every call fail silently. */
    private var sessionLapsed by mutableStateOf(false)

    private fun markSessionLapsed() {
        if (sessionLapsed) return
        sessionLapsed = true
        EncoreAuth.invalidateToken(applicationContext)
        log.w("the stored session can no longer mint tokens, reopening the login screen")
    }

    internal var homeGreeting by mutableStateOf("")
    internal var homeFacet by mutableStateOf("")
    internal var loading by mutableStateOf(true)
    internal var playHistory by mutableStateOf<List<PlayedTrack>>(emptyList())
    internal var playStats by mutableStateOf(PlayStatsSnapshot())
    internal var searchTerm by mutableStateOf("")
    internal var searchLoading by mutableStateOf(false)
    internal var searchFailed by mutableStateOf(false)
    internal var searchFetch by mutableStateOf(0)
    internal var searchDataFor by mutableStateOf("")
    internal var showUri by mutableStateOf("")
    internal var showLoading by mutableStateOf(false)
    internal var showFetch by mutableStateOf(0)
    internal var episodeUri by mutableStateOf("")
    internal var episodeLoading by mutableStateOf(false)
    internal var episodeFetch by mutableStateOf(0)
    internal var browseUri by mutableStateOf("")
    internal var categoryUri by mutableStateOf("")
    internal var folderUri by mutableStateOf("")
    internal var folderFetch by mutableStateOf(0)
    internal var settingsSwitches by mutableStateOf(SettingsSwitches())
    internal var settingsLocks by mutableStateOf(SettingsLocks())
    internal var settingsProduct by mutableStateOf("")
    internal var settingsCountry by mutableStateOf("")
    internal var settingsLoading by mutableStateOf(false)
    internal var whatsNewLoading by mutableStateOf(false)
    internal var whatsNewFailed by mutableStateOf(false)
    internal var whatsNewFetch by mutableStateOf(0)
    internal var browseFetch by mutableStateOf(0)
    internal var savedAccountsList by mutableStateOf<List<EncoreAuth.Account>>(emptyList())
    internal var addingAccount by mutableStateOf(false)
    internal var userProfileColorHex by mutableStateOf("")
    internal var profileLoading by mutableStateOf(false)
    internal var profileFailed by mutableStateOf(false)
    internal var profileFetch by mutableStateOf(0)
    internal var profileUserId by mutableStateOf("")
    internal var profileFollowLoading by mutableStateOf(false)
    internal var profileFollowFetch by mutableStateOf(0)
    internal var playlistUri by mutableStateOf("")
    internal var playlistLoading by mutableStateOf(false)
    internal var playlistHasMore by mutableStateOf(false)
    internal var playlistLoadingMore by mutableStateOf(false)
    internal var playlistFetch by mutableStateOf(0)
    internal var recommendationsLoading by mutableStateOf(false)
    internal var recommendationsNonce by mutableStateOf(0)
    internal var playlistCanRemoveCover by mutableStateOf(false)
    internal var albumUri by mutableStateOf("")
    internal var albumLoading by mutableStateOf(false)
    internal var albumFailed by mutableStateOf(false)
    internal var albumRetry by mutableStateOf(0)
    internal var albumHasMore by mutableStateOf(false)
    internal var albumLoadingMore by mutableStateOf(false)
    internal var artistUri by mutableStateOf("")
    internal var artistLoading by mutableStateOf(false)
    internal var artistFailed by mutableStateOf(false)
    internal var artistRetry by mutableStateOf(0)
    internal var addTracksLoading by mutableStateOf(false)
    internal var addTracksFetch by mutableStateOf(0)
    private var sawOffline by mutableStateOf(false)
    internal var canvasEnabled by mutableStateOf(true)

    internal var feed by mutableStateOf<List<HomeSection>>(emptyList())
    internal var homeChips by mutableStateOf<List<HomeChip>>(emptyList())
    internal var searchData by mutableStateOf<SearchResults?>(null)
    internal var searchHits by mutableStateOf<List<SearchHit>>(emptyList())
    internal val searchSavedOverrides = mutableStateMapOf<String, Boolean>()
    private var recentsSyncedAccount by mutableStateOf<String?>(null)
    private var recentsSyncedAt = 0L
    internal var submittedSearchQuery by mutableStateOf<String?>(null)
    internal var showDetail by mutableStateOf<ShowDetail?>(null)
    internal var episodeDetail by mutableStateOf<EpisodeDetail?>(null)
    internal var browsePage by mutableStateOf<BrowsePage?>(null)
    internal var browseLoadingMore by mutableStateOf(false)
    internal var categoryShelves by mutableStateOf<List<CategoryShelf>?>(null)
    internal var browseCategories by mutableStateOf<List<BrowseCard>>(emptyList())
    internal val homeArtistImages = mutableStateMapOf<String, String>()
    internal val artistCache = mutableMapOf<String, ArtistDetail>()
    internal var folderItems by mutableStateOf<List<LibraryItem>?>(null)
    internal var libraryItems by mutableStateOf<List<LibraryItem>?>(null)
    internal var whatsNew by mutableStateOf<List<EncoreNotification>>(emptyList())
    internal var profileDetail by mutableStateOf<UserProfile?>(null)
    internal var profileTop by mutableStateOf<ProfileTopContent?>(null)
    internal var profileTopUnavailable by mutableStateOf(false)
    internal var profileContexts by mutableStateOf<List<ProfileContextCard>>(emptyList())
    internal var profileFollowers by mutableStateOf<List<ProfileUser>>(emptyList())
    internal var profileFollowing by mutableStateOf<List<ProfileUser>>(emptyList())
    internal var playlistDetail by mutableStateOf<CollectionDetail?>(null)
    internal var playlistTracks by mutableStateOf<List<TrackItem>?>(null)
    internal var playlistRecommendations by mutableStateOf<List<TrackItem>>(emptyList())
    internal var playlistSaved by mutableStateOf<Boolean?>(null)
    internal var playlistEditDraft by mutableStateOf<PlaylistEditDraft?>(null)
    internal var editableUris by mutableStateOf<Set<String>>(emptySet())
    internal var albumDetail by mutableStateOf<AlbumDetail?>(null)
    internal var albumTracks by mutableStateOf<List<TrackItem>?>(null)
    internal var albumSaved by mutableStateOf<Boolean?>(null)
    internal var artistDetail by mutableStateOf<ArtistDetail?>(null)
    internal var trackCredits by mutableStateOf<Credits?>(null)
    internal var addToPlaylistChoices by mutableStateOf<List<PlaylistChoice>?>(null)
    internal var addTracksSongs by mutableStateOf<List<TrackItem>>(emptyList())
    internal var addTracksEpisodes by mutableStateOf<List<TrackItem>>(emptyList())
    internal var addTracksLiked by mutableStateOf<List<TrackItem>>(emptyList())
    internal var addTracksRecent by mutableStateOf<List<TrackItem>>(emptyList())
    internal var artistTracks by mutableStateOf<List<PopularTrack>?>(null)
    internal var artistReleases by mutableStateOf<List<ArtistRelease>?>(null)
    internal var artistListeners by mutableStateOf<String?>(null)
    internal var artistFollowed by mutableStateOf<Boolean?>(null)
    internal var artistBanned by mutableStateOf<Boolean?>(null)
    internal var nowPlayingArtist by mutableStateOf<ArtistDetail?>(null)
    internal var nowPlayingArtistFollowed by mutableStateOf<Boolean?>(null)
    internal var nowPlayingLyrics by mutableStateOf<TrackLyrics?>(null)
    internal var nowPlayingTranscript by mutableStateOf<EpisodeTranscript?>(null)
    internal var canvasFile by mutableStateOf<String?>(null)
    internal var nowPlayingShow by mutableStateOf<ShowDetail?>(null)
    internal var nowPlayingComments by mutableStateOf<EpisodeComments?>(null)
    internal var episodeCommentsUri by mutableStateOf<String?>(null)
    internal var episodeCommentsNonce by mutableStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal++
        }
        pendingLink = SpotLinks.parse(intent?.data)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        Pathfinder.silentRefresh = { context, reason -> SilentTokenRefresh.refresh(context, reason) }
        SilentTokenRefresh.onSessionDead = { markSessionLapsed() }
        playHistory = PlayHistory.all(applicationContext)
        playStats = PlayStats.snapshot(applicationContext)
        RecentSearches.refresh(applicationContext)
        savedAccountsList = EncoreAuth.savedAccounts(applicationContext)
        userProfileColorHex = EncoreAuth.account(applicationContext)?.colorHex.orEmpty()
        canvasEnabled = clientPrefs.getBoolean("canvasNpvEnabled", true)
        com.quackify.compose.player.LyricsOptions.load(applicationContext)
        com.quackify.compose.player.PlayerViewOptions.load(applicationContext)
        syncLauncherIcon(applicationContext)
        canvasResolution = clientPrefs.getInt("canvasResolution", 1080)
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
        // One process-wide connectivity watchdog: it powers the shell's offline
        // banner, the Net offline gate (nothing is sent while offline), the
        // dealer's instant redial and the reconnect recovery below.
        ConnectivityMonitor.ensureStarted(applicationContext)
        setContent {
            EncoreAppTheme {
                val context = LocalContext.current
                var loggedIn by remember { mutableStateOf(EncoreAuth.isLoggedIn(context)) }
                val updates = rememberUpdateController()
                UpdateEffects(updates)
                val sessionToast = rememberQuackifyToastState()
                val sessionExpiredText = context.getString(R.string.session_expired_toast)
                val sessionLapsedNow = sessionLapsed
                LaunchedEffect(sessionLapsedNow) {
                    if (sessionLapsedNow) {
                        loggedIn = false
                        sessionToast.show(sessionExpiredText)
                    }
                }
                LaunchedEffect(loggedIn) {
                    if (loggedIn) this@MainActivity.sessionLapsed = false
                }

                Box(Modifier.fillMaxSize()) {
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
                        LaunchedEffect(Unit) {
                            playStats = PlayStats.recordLogin(context)
                        }
                        var tokenChecked by remember { mutableStateOf(false) }
                        LaunchedEffect(tokenChecked, resumeSignal, homeFacet) {
                            if (!tokenChecked) return@LaunchedEffect
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
                        LaunchedEffect(Unit) {
                            val before = EncoreAuth.token(context)
                            SilentTokenRefresh.ensureFresh(context, "startup")
                            tokenChecked = true
                            if (EncoreAuth.token(context) != before) retryScreensLeftEmpty()
                            while (true) {
                                delay(TOKEN_CHECK_INTERVAL_MS)
                                val previous = EncoreAuth.token(context)
                                SilentTokenRefresh.ensureFresh(context, "periodic")
                                if (EncoreAuth.token(context) != previous) retryScreensLeftEmpty()
                            }
                        }
                        val account = remember(loggedIn) { EncoreAuth.account(context) }
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

                        val playlistNames = remember { java.util.concurrent.ConcurrentHashMap<String, String>() }
                        val scope = rememberCoroutineScope()


                        LaunchedEffect(browseFetch) {
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
                            val privacy = Settings.privacy(context, account?.id.orEmpty())
                            if (privacy != null) {
                                settingsSwitches = settingsSwitches.copy(
                                    showFollows = privacy.showFollows,
                                    showPlaylists = privacy.showPublishedPlaylists,
                                )
                            }
                            settingsLoading = false
                        }

                        val activeAccountId = remember { EncoreAuth.activeAccountId(context) }
                        LaunchedEffect(Unit) {
                            if (EncoreAuth.activeAccountId(context).isBlank()) {
                                EncoreAuth.userId(context)
                                EncoreAuth.storeCurrentAccount(context)
                            }
                            // onCreate snapshots this list while still logged out, so on a
                            // fresh install it is empty, and the first login stores the
                            // account before this branch composes - the blank-id guard
                            // above never reloads it and the account manager sheet shows
                            // nothing but "Add Account" until some other flow resyncs.
                            // Always resync from storage when the logged-in ui comes up.
                            savedAccountsList = EncoreAuth.savedAccounts(context)
                            val missing = savedAccountsList.filter { it.colorHex.isBlank() }
                            if (missing.isNotEmpty()) {
                                log.i("backfilling profile colors for ${missing.size} account(s)")
                                missing.forEach { saved ->
                                    val color = runCatching {
                                        Profile.profile(context, saved.id)
                                    }.getOrNull()?.colorHex.orEmpty()
                                    if (color.isNotBlank()) {
                                        EncoreAuth.saveAccountColor(context, saved.id, color)
                                        log.i("profile color backfilled for '${saved.name}' -> $color")
                                    }
                                }
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

                        LaunchedEffect(profileFetch) {
                            if (profileFetch == 0) return@LaunchedEffect
                            profileLoading = true
                            profileFailed = false
                            coroutineScope {
                                val target = profileUserId
                                val profile = async { runCatching { Profile.profile(context, target) }.getOrNull() }.await()
                                val isSelf = target.isBlank() || profile?.isCurrentUser == true
                                val (top, recent) = if (isSelf) {
                                    val topJob = async { runCatching { Profile.topContent(context) }.getOrNull() }
                                    val recentJob = async { runCatching { Profile.recentlyPlayed(context) }.getOrNull() }
                                    topJob.await() to recentJob.await()
                                } else {
                                    val topJob = async {
                                        runCatching { Profile.topContent(context, target) }.getOrNull()
                                    }
                                    topJob.await() to null
                                }
                                profileDetail = profile
                                if (isSelf && !profile?.colorHex.isNullOrBlank()) {
                                    userProfileColorHex = profile!!.colorHex
                                    EncoreAuth.saveProfileColor(context, profile!!.colorHex)
                                }
                                profileTop = top
                                profileTopUnavailable = !isSelf && top == null
                                profileContexts = if (isSelf) {
                                    resolveProfileContexts(context, profile, recent)
                                } else {
                                    profile?.publicPlaylists.orEmpty().map {
                                        ProfileContextCard(
                                            uri = it.uri,
                                            title = it.name,
                                            subtitle = "Playlist",
                                            imageUrl = it.imageUrl,
                                        )
                                    }
                                }
                                profileFailed = profile == null && top == null
                                log.i("profile loaded self=$isSelf failed=${profile == null} " +
                                        "topArtists=${top?.topArtists?.size ?: 0} " +
                                        "topUnavailable=${profileTopUnavailable} " +
                                        "contexts=${profileContexts.size}"
                                )
                            }
                            profileLoading = false
                        }

                        LaunchedEffect(profileFollowFetch) {
                            if (profileFollowFetch == 0) return@LaunchedEffect
                            profileFollowLoading = true
                            coroutineScope {
                                val id = profileUserId
                                val followersJob = async { runCatching { Profile.followers(context, id) }.getOrNull() }
                                val followingJob = async { runCatching { Profile.following(context, id) }.getOrNull() }
                                profileFollowers = followersJob.await().orEmpty()
                                profileFollowing = followingJob.await().orEmpty()
                            }
                            profileFollowLoading = false
                        }

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
                                if (browseCategories.isEmpty()) browseFetch++
                                // Refetch whatever entity screen was left open
                                // with nothing on it when the connection died.
                                if (playlistUri.isNotBlank() && playlistDetail == null) playlistFetch++
                                if (albumUri.isNotBlank() && albumDetail == null) albumRetry++
                                if (artistUri.isNotBlank() && artistDetail == null) artistRetry++
                            }
                        }

                        LaunchedEffect(addTracksFetch) {
                            // keyed on the sheet opening only, so opening a playlist does not fetch every library playlist
                            val uri = playlistUri
                            if (uri.isBlank()) return@LaunchedEffect
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
                                        ?.filter { it.kind == ShelfKind.PLAYLIST && it.uri != uri }
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
                            coroutineScope {
                                val detailDeferred = async {
                                    Krespot.collections.collection(context, uri, hydrateSaved = false)
                                }
                                val savedDeferred = async {
                                    runCatching { Krespot.rootlist.saved(context, uri) }.getOrDefault(false)
                                }
                                val detail = detailDeferred.await()
                                if (uri == playlistUri) {
                                    playlistDetail = detail
                                    playlistTracks = detail?.tracks?.map { it.toTrackItem() }
                                    playlistHasMore = detail?.hasMore == true
                                    playlistLoading = false
                                    playlistSaved = savedDeferred.await()
                                    playlistCanRemoveCover = false
                                    if (detail?.editable == true) {
                                        playlistCanRemoveCover = Krespot.playlists.hasCustomCover(context, uri)
                                    }
                                    val unknown = detail?.tracks.orEmpty()
                                        .filter { it.saved == null }
                                        .map { it.uri }
                                        .filter { it.isNotBlank() }
                                    if (unknown.isNotEmpty()) {
                                        val savedRows = Library.saved(context, unknown)
                                        playlistTracks = playlistTracks?.map { item ->
                                            if (item.saved == null) item.copy(saved = item.uri in savedRows) else item
                                        }
                                    }
                                }
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
                                playlistRecommendations = buildPlaylistRecommendations(context, tracks, inPlaylist)
                            } finally {
                                recommendationsLoading = false
                            }
                        }

                        LaunchedEffect(albumUri, albumRetry) {
                            if (albumUri.isBlank()) {
                                albumDetail = null
                                albumTracks = null
                                albumSaved = null
                                albumLoading = false
                                albumFailed = false
                                return@LaunchedEffect
                            }
                            val uri = albumUri
                            albumDetail = null
                            albumTracks = null
                            albumSaved = null
                            albumFailed = false
                            albumHasMore = false
                            albumLoading = true
                            try {
                                val detail = Krespot.albums.album(context, uri)
                                if (uri != albumUri) return@LaunchedEffect
                                albumDetail = detail
                                albumTracks = detail?.tracks?.map { it.toTrackItem() }
                                    ?.let { withSavedStates(context, it) }
                                albumHasMore = detail != null &&
                                    detail.fetched < detail.total && detail.tracks.isNotEmpty()
                                albumFailed = detail == null
                                val inLibrary = Library.saved(context, listOf(uri))
                                albumSaved = if (inLibrary.isEmpty()) detail?.saved == true else uri in inLibrary
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
                                artistBanned = null
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
                            artistBanned = null
                            artistFailed = false
                            artistLoading = true
                            try {
                                val load = loadArtistDetail(context, uri)
                                if (uri != artistUri) return@LaunchedEffect
                                artistDetail = load.detail
                                artistFailed = load.detail == null
                                artistTracks = load.tracks
                                artistReleases = load.releases
                                artistListeners = load.listeners
                                artistFollowed = load.followed
                                artistBanned = runCatching { Collection2.artistBanned(context, uri) }.getOrNull()
                            } finally {
                                artistLoading = false
                            }
                        }

        val libraryRevision by Krespot.library.revision.collectAsState()

        LaunchedEffect(libraryRevision) {
            val page = Krespot.library.page(context)
            libraryItems = page?.items?.map { item ->
                LibraryItem(
                    title = item.title,
                    subtitle = item.subtitle,
                    type = when {
                        item.uri.startsWith("spotify:show:") -> LibraryItemType.PODCAST
                        item.kind == ShelfKind.ARTIST -> LibraryItemType.ARTIST
                        item.kind == ShelfKind.ALBUM -> LibraryItemType.ALBUM
                        item.kind == ShelfKind.FOLDER -> LibraryItemType.FOLDER
                        item.kind == ShelfKind.SHOW || item.kind == ShelfKind.EPISODE -> LibraryItemType.PODCAST
                        else -> LibraryItemType.PLAYLIST
                    },
                    uri = item.uri,
                    imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
                    isPinned = item.pinned,
                    isPinnable = item.pinnable,
                )
            }
            editableUris = Krespot.playlists.editable(context, "").map { it.uri }.toSet()
        }

                        LaunchedEffect(artistUri, artistTracks?.size, libraryRevision) {
                            val rows = artistTracks ?: return@LaunchedEffect
                            if (artistUri.isBlank()) return@LaunchedEffect
                            artistTracks = withSavedPopularTracks(context, rows)
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
                                    isPinned = item.pinned,
                                    isPinnable = item.pinnable,
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

                        LaunchedEffect(loggedIn, pendingLink) {
                            val link = pendingLink ?: return@LaunchedEffect
                            if (link.kind != "track" && link.kind != "station") return@LaunchedEffect
                            pendingLink = null
                            PlaybackHost.bridge?.listener?.onPlayUri(link.uri)
                        }

                        val hostGeneration by PlaybackHost.generation
                        val bridge = remember(hostGeneration) { PlaybackHost.ensure(context) }
                        val playback = bridge.state

                        LaunchedEffect(playback.videoActive, playback.trackUri) {
                            if (playback.videoActive || !videoFullscreen) return@LaunchedEffect
                            var waited = 0
                            while (waited < VIDEO_AUTO_EXIT_MAX_MS) {
                                if (!videoFullscreen || bridge.state.videoActive) return@LaunchedEffect
                                if (waited >= VIDEO_AUTO_EXIT_MIN_MS &&
                                    !bridge.state.videoSwitching &&
                                    !bridge.state.musicVideoAvailable
                                ) {
                                    break
                                }
                                delay(250L)
                                waited += 250
                            }
                            if (!videoFullscreen || bridge.state.videoActive) return@LaunchedEffect
                            val hasVideo = bridge.state.musicVideoAvailable
                            videoFullscreen = false
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            if (!hasVideo) videoAutoExitSignal++
                        }

                        LaunchedEffect(videoFullscreen) {
                            val controller = WindowCompat.getInsetsController(window, window.decorView)
                            if (videoFullscreen) {
                                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                            } else {
                                controller.show(WindowInsetsCompat.Type.systemBars())
                            }
                        }
                        LaunchedEffect(settingsSwitches.musicVideos, settingsSwitches.otherVideos) {
                            bridge.setVideoPolicy(settingsSwitches.musicVideos, settingsSwitches.otherVideos)
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
                            val addable = playback.trackUri.startsWith("spotify:track:") ||
                                    playback.trackUri.startsWith("spotify:episode:")
                            if (!addable || playback.title.isBlank()) {
                                return@LaunchedEffect
                            }
                            val cover = playback.coverUrl.takeIf { it.isNotBlank() }
                            val head = playHistory.firstOrNull()?.item
                            if (head?.uri == playback.trackUri && head.title == playback.title &&
                                head.artistUri == playback.artistUri && head.imageUrl == cover
                            ) {
                                return@LaunchedEffect
                            }
                            playHistory = recordNowPlaying(
                                playback.trackUri, playback.title, playback.artist, playback.artistUri,
                                playback.albumUri, cover.orEmpty(), playback.contextUri, playback.contextLabel,
                                playback.explicit, playlistNames, playback.artistUris,
                            )
                            playStats = PlayStats.snapshot(context)
                        }

                        val historyAlbumAttempts = remember { mutableSetOf<String>() }
                        LaunchedEffect(playHistory) {
                            val missing = playHistory
                                .map { it.item }
                                .filter {
                                    (it.albumUri.isBlank() || it.artistUris.isEmpty()) &&
                                        it.uri.startsWith("spotify:track:") &&
                                        it.uri !in historyAlbumAttempts
                                }
                                .map { it.uri }
                                .distinct()
                                .take(50)
                            if (missing.isEmpty()) return@LaunchedEffect
                            historyAlbumAttempts += missing
                            val rows = runCatching {
                                TrackSource.decorate(context, missing)
                            }.getOrNull().orEmpty()
                            if (rows.isEmpty()) return@LaunchedEffect
                            playHistory = PlayHistory.applyAlbums(context, rows)
                        }

                        LaunchedEffect(playback.isPlaying) {
                            if (!playback.isPlaying) return@LaunchedEffect
                            while (true) {
                                delay(10_000L)
                                PlayStats.addPlayedMs(context, 10_000L)
                                playStats = PlayStats.snapshot(context)
                            }
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
                            val searched = runSearch(term)
                            searchHits = searched.second
                            searchData = searched.first
                            searchDataFor = term
                            searchSavedOverrides.clear()
                            searchFailed = searched.first == null
                            searchLoading = false
                            if (searched.first != null) {
                                log.i("searched '$term'")
                                RecentSearches.recordQuery(context, term)
                                submittedSearchQuery = term
                            }
                            val results = searched.first
                            if (results != null && results.savedCandidates.isNotEmpty()) {
                                val saved = Library.saved(applicationContext, results.savedCandidates)
                                val current = searchData
                                if (saved.isNotEmpty() && current != null && current.query == term) {
                                    searchData = current.withSaved(saved)
                                }
                            }
                            if (results != null) {
                                delay(RECENT_SEARCH_SETTLE_MS)
                                if (searchTerm.trim() == term) {
                                    Search.saveRecent(context, listOf(Search.searchUriOf(term)))
                                }
                            }
                        }

                        LaunchedEffect(searchTerm, offlineNow, loggedIn, activeAccountId, resumeSignal) {
                            if (searchTerm.isNotBlank() || offlineNow || !loggedIn) return@LaunchedEffect
                            if (recentsSyncedAccount == activeAccountId &&
                                System.currentTimeMillis() - recentsSyncedAt < RECENTS_SYNC_THROTTLE_MS
                            ) return@LaunchedEffect
                            var remote = Search.recent(context)
                            var attempts = 0
                            while (remote.isEmpty() && attempts < 2) {
                                attempts++
                                delay(2500L)
                                remote = Search.recent(context)
                            }
                            recentsSyncedAccount = activeAccountId
                            recentsSyncedAt = System.currentTimeMillis()
                            if (remote.isNotEmpty()) {
                                RecentSearches.merge(context, remote)
                                log.i("reconciled ${remote.size} recent searches with the account")
                            }
                        }

                        ShellSection(
                            bridge = bridge,
                            account = account,
                            context = context,
                            loggedIn = loggedIn,
                            activeAccountId = activeAccountId,
                            onSignedOut = { loggedIn = false },
                            updates = updates,
                        )

                        if (addingAccount) {
                            LoginScreen(
                                onLoggedIn = {
                                    log.i("add account completed")
                                    finishAddAccount()
                                },
                                isDuplicateAccount = { candidate ->
                                    candidate.id.isNotBlank() &&
                                        savedAccountsList.any { it.id == candidate.id }
                                },
                                onDuplicateLogin = { duplicate ->
                                    refuseDuplicateAccount(duplicate.id, duplicate.name, activeAccountId)
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
                            finishLogin()
                            loggedIn = true
                        },
                        onBackExhausted = {
                            if (handleLoginBack()) {
                                loggedIn = true
                            } else {
                                (context as? Activity)?.finish()
                            }
                        }
                    )
                }

                QuackifyToastHost(
                    state = sessionToast,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 32.dp),
                )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        WidgetSync.start(applicationContext)
    }

    override fun onPause() {
        super.onPause()
        pausedAt = System.currentTimeMillis()
        sawStop = false
    }

    override fun onStop() {
        super.onStop()
        sawStop = true
        lifecycleScope.launch {
            WidgetSync.refresh(applicationContext)
        }
    }

    override fun onDestroy() {
        if (isChangingConfigurations) {
            super.onDestroy()
            return
        }
        if (sawStop && PlaybackHost.started && isFinishing) {
            log.i("activity finished by the user, handing the stream back and stopping the service")
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

    /**
     * Refetches the top-level screens that came back empty because they ran
     * with the dead token before a renewal landed: home, the library,
     * what's new and browse all retry the same way the offline recovery does.
     */
    private fun retryScreensLeftEmpty() {
        if (!loading && feed.isEmpty()) resumeSignal++
        if (libraryItems.isNullOrEmpty()) Krespot.library.invalidate()
        if (whatsNew.isEmpty()) whatsNewFetch++
        if (browseCategories.isEmpty()) browseFetch++
    }


    internal suspend fun loadMorePlaylistTracks(currentSize: Int): Pair<List<TrackItem>, Boolean>? {
        val page = Krespot.collections.collectionMore(applicationContext, playlistUri, currentSize)
            ?: return null
        log.i("playlist more '$playlistUri' -> ${page.rows.size} rows hasMore=${page.hasMore}")
        return page.rows.map { it.toTrackItem() } to page.hasMore
    }

    internal suspend fun loadMoreAlbumTracks(currentSize: Int, coverUrl: String): Pair<List<TrackItem>, Boolean>? {
        val page = Krespot.albums.albumMore(
            applicationContext,
            albumUri,
            currentSize,
            albumDetail?.imageUrl.orEmpty()
        ) ?: return null
        val rows = page.rows.map { it.toTrackItem() }
        log.i("album more '$albumUri' -> ${rows.size} rows hasMore=${page.hasMore}")
        return withSavedStates(applicationContext, rows) to page.hasMore
    }

    internal suspend fun addRecommendedToPlaylist(trackUri: String, trackTitle: String): CollectionDetail? {
        val ok = Krespot.playlists.addTrack(applicationContext, playlistUri, trackUri)
        log.i("recommend '$trackTitle' into playlist ok=$ok")
        if (!ok) return null
        return Krespot.collections.collection(applicationContext, playlistUri)
    }

    internal suspend fun savePlaylistOrderMoves(
        before: List<TrackItem>,
        uri: String,
        target: List<String>,
    ): Int {
        if (target.any { it.isBlank() }) {
            log.w("playlist order save '$uri': missing uids, skipping")
            return -1
        }
        val current = before.map { it.uid }.toMutableList()
        var moved = 0
        for (i in target.indices) {
            if (current.getOrNull(i) == target[i]) continue
            val anchor = current.getOrNull(i)
            if (anchor.isNullOrBlank()) continue
            if (!Krespot.playlists.moveItems(
                    applicationContext, uri, listOf(target[i]),
                    PlaylistSource.Position.BeforeUid(anchor),
                )
            ) continue
            current.remove(target[i])
            current.add(i, target[i])
            moved++
        }
        log.i("playlist order save '$uri' -> $moved move(s) of ${target.size}")
        return moved
    }

    internal suspend fun fetchCredits(uri: String): Credits? =
        if (uri.isBlank()) null else Krespot.albums.credits(applicationContext, uri)

    internal suspend fun fetchEditablePlaylists(trackUri: String): List<PlaylistChoice> =
        if (trackUri.isBlank()) emptyList() else Krespot.playlists.editable(applicationContext, trackUri)

    internal suspend fun addToPlaylistTarget(choice: PlaylistChoice, trackUri: String, trackTitle: String) {
        if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
            val saved = Library.setSaved(applicationContext, listOf(trackUri), true)
            log.i("add '$trackTitle' to Liked Songs ok=$saved")
            if (saved) refreshLoadedPlaylist(choice.uri)
        } else {
            val added = Krespot.playlists.addTrack(applicationContext, choice.uri, trackUri)
            log.i("add '$trackTitle' to '${choice.name}' ok=$added")
            if (added) refreshLoadedPlaylist(choice.uri)
        }
    }

    internal suspend fun togglePlaylistMembership(
        choice: PlaylistChoice,
        trackUri: String,
        trackTitle: String,
        shouldAdd: Boolean,
    ) {
        if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
            val host = PlaybackHost.bridge
            if (trackUri == host?.state?.trackUri && host.state.isLiked != shouldAdd) {
                host.listener.onLikeToggle()
                refreshLoadedPlaylist(choice.uri)
            } else {
                val saved = Library.setSaved(applicationContext, listOf(trackUri), shouldAdd)
                log.i("${if (shouldAdd) "add" else "remove"} '$trackTitle' to/from Liked Songs ok=$saved")
                if (saved) refreshLoadedPlaylist(choice.uri)
            }
        } else {
            val res = if (shouldAdd) {
                Krespot.playlists.addTrack(applicationContext, choice.uri, trackUri)
            } else {
                Krespot.playlists.removeTrack(applicationContext, choice.uri, trackUri)
            }
            log.i("${if (shouldAdd) "add" else "remove"} '$trackTitle' to/from '${choice.name}' ok=$res")
            if (res) refreshLoadedPlaylist(choice.uri)
        }
    }

    internal fun refreshLoadedPlaylist(uri: String) {
        if (uri != playlistUri) return
        playlistDetail = null
        playlistTracks = null
        playlistHasMore = false
        playlistLoading = true
        playlistFetch++
    }

    internal suspend fun createPlaylistWithTrack(trackUri: String) {
        val name = Krespot.playlists.defaultName(applicationContext)
        val uri = Krespot.playlists.create(applicationContext, name, trackUri)
        log.i("create playlist '$name' ok=${uri != null}")
        if (uri != null) Krespot.library.invalidate()
    }

    internal suspend fun setPlaylistSaved(uri: String, saved: Boolean): Boolean {
        val ok = Krespot.rootlist.setSaved(applicationContext, uri, saved)
        log.i("playlist '$uri' in library -> $saved ok=$ok")
        if (ok) Krespot.library.invalidate()
        return ok
    }

    internal suspend fun removeTrackFromPlaylist(pUri: String, tUri: String): Boolean {
        val ok = Krespot.playlists.removeTrack(applicationContext, pUri, tUri)
        log.i("remove track '$tUri' from playlist '$pUri' ok=$ok")
        return ok
    }

    internal suspend fun removeFromLibrary(uri: String) {
        val ok = if (uri.startsWith("spotify:playlist:")) {
            Krespot.rootlist.remove(applicationContext, uri)
        } else {
            Library.setSaved(applicationContext, listOf(uri), false)
        }
        log.i("remove '$uri' from library ok=$ok")
    }

    internal suspend fun setLibraryItemPinned(uri: String, pinned: Boolean) {
        val ok = Krespot.library.setPinned(applicationContext, uri, pinned)
        log.i("pin '$uri' -> $pinned ok=$ok")
        if (!ok) {
            Toast.makeText(
                applicationContext,
                "Couldn't update the pin",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    internal suspend fun renameFolder(uri: String, name: String) {
        val ok = Krespot.folders.renameFolder(applicationContext, uri, name)
        log.i("rename folder '$uri' -> '$name' ok=$ok")
    }

    internal suspend fun deleteFolder(uri: String) {
        val ok = Krespot.folders.deleteFolder(applicationContext, uri)
        log.i("delete folder '$uri' ok=$ok")
    }

    internal suspend fun moveTrackToFolder(playlistUri: String, targetFolderUri: String): Boolean {
        val ok = if (targetFolderUri.isBlank()) {
            Krespot.folders.removeFromFolder(applicationContext, playlistUri)
        } else {
            Krespot.folders.moveIntoFolder(applicationContext, playlistUri, targetFolderUri)
        }
        log.i("move '$playlistUri' into '$targetFolderUri' ok=$ok")
        return ok
    }

    internal suspend fun setArtistFollow(uri: String, follow: Boolean): Boolean {
        val ok = Library.setSaved(applicationContext, listOf(uri), follow)
        log.i("artist follow '$uri' -> $follow ok=$ok")
        return ok
    }

    internal suspend fun toggleSearchSaved(uri: String, next: Boolean): Boolean {
        val ok = Library.setSaved(applicationContext, listOf(uri), next)
        log.i("search heart '$uri' -> $next ok=$ok")
        return ok
    }

    private suspend fun recordNowPlaying(
        trackUri: String,
        title: String,
        artist: String,
        artistUri: String,
        albumUri: String,
        coverUrl: String,
        contextUri: String,
        contextLabel: String,
        explicit: Boolean,
        playlistNames: java.util.concurrent.ConcurrentHashMap<String, String>,
        artistUris: List<String> = emptyList(),
    ): List<PlayedTrack> = withContext(Dispatchers.Default) {
        PlayStats.recordListen(applicationContext)
        PlayStats.tallyArtist(applicationContext, artistUri, artist)
        if (contextUri.startsWith("spotify:playlist:")) {
            val resolved = contextLabel.ifBlank {
                playlistNames[contextUri] ?: runCatching {
                    Krespot.collections.collection(applicationContext, contextUri)?.title
                }.getOrNull()?.trim().orEmpty().also { name ->
                    if (name.isNotBlank()) playlistNames[contextUri] = name
                }
            }
            PlayStats.tallyPlaylist(applicationContext, contextUri, resolved.ifBlank { "Playlist" })
        }
        PlayHistory.record(
            applicationContext,
            TrackItem(
                title = title,
                artist = artist,
                uri = trackUri,
                imageUrl = coverUrl,
                artistUri = artistUri,
                artistUris = artistUris,
                albumUri = albumUri,
                isExplicit = explicit,
            ),
            System.currentTimeMillis(),
        )
    }

    internal fun serverRecentUri(uri: String, title: String): String? = when {
        uri.startsWith("spotify:search:") ->
            Search.searchUriOf(Search.queryOfSearchUri(uri).ifBlank { title })
        uri.startsWith("spotify:") -> uri
        title.isNotBlank() -> Search.searchUriOf(title)
        else -> null
    }

    private suspend fun runSearch(term: String): Pair<com.quackify.backend.model.SearchResults?, List<SearchHit>> {
        delay(280)
        return coroutineScope {
            val suggestions = async { Search.suggest(applicationContext, term) }
            val results = async { Search.query(applicationContext, term) }
            val decorated = results.await()?.let { decorateSearchTracks(applicationContext, it) }
            decorated to suggestions.await()
        }
    }

    internal fun buildHistoryStats(
        stats: PlayStatsSnapshot,
        history: List<PlayedTrack>,
        playlistImages: Map<String, String> = emptyMap(),
    ): HistoryStats {
        val replayed = history
            .groupBy { it.item.title.trim().lowercase() to it.item.artist.trim().lowercase() }
            .filterValues { rows -> rows.sumOf { it.plays } > 1 }
            .mapValues { (_, rows) -> rows.sumOf { it.plays } to rows.first().item }
            .maxByOrNull { it.value.first }
        return HistoryStats(
            totalPlayedMs = stats.totalMs,
            listenStreakDays = stats.listenStreak,
            loginStreakDays = stats.loginStreak,
            topArtistName = stats.topArtistName,
            topArtistUri = stats.topArtistUri,
            topArtistPlays = stats.topArtistPlays,
            topArtistImageUrl = homeArtistImages[stats.topArtistUri]?.takeIf { it.isNotBlank() },
            topPlaylistName = stats.topPlaylistName,
            topPlaylistUri = stats.topPlaylistUri,
            topPlaylistPlays = stats.topPlaylistPlays,
            topPlaylistImageUrl = playlistImages[stats.topPlaylistUri]?.takeIf { it.isNotBlank() },
            topReplayTitle = replayed?.value?.second?.title.orEmpty(),
            topReplayArtist = replayed?.value?.second?.artist.orEmpty(),
            topReplayPlays = replayed?.value?.first ?: 0,
            topReplayImageUrl = replayed?.value?.second?.imageUrl,
        )
    }

    internal suspend fun buildPlaylistEditDraft(
        uri: String,
        loaded: CollectionDetail?,
        canRemoveCover: Boolean,
    ): PlaylistEditDraft? {
        return loaded?.let {
            PlaylistEditDraft(
                uri = uri,
                name = it.title,
                description = it.description,
                coverUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                canRemoveCover = canRemoveCover,
            )
        } ?: Krespot.collections.collection(applicationContext, uri)?.let {
            PlaylistEditDraft(
                uri = uri,
                name = it.title,
                description = it.description,
                coverUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                canRemoveCover = if (it.editable) Krespot.playlists.hasCustomCover(applicationContext, uri) else false,
            )
        }
    }

    internal suspend fun editPlaylistDetails(
        uri: String,
        name: String,
        description: String,
        cover: ByteArray?,
        clearCover: Boolean,
    ): Boolean {
        val picture = cover?.let { Krespot.playlists.uploadCover(applicationContext, uri, it) }
        val effective = when {
            picture != null -> picture
            clearCover -> ""
            else -> null
        }
        val ok = Krespot.playlists.edit(applicationContext, uri, name, description, effective)
        log.i("edit playlist '$uri' cover=${picture != null} clear=$clearCover ok=$ok")
        if (ok) Krespot.library.invalidate()
        return ok
    }

    internal suspend fun applySettingChange(field: String, value: Boolean, accountId: String): Boolean =
        when (field) {
            "showFollows" -> Settings.setPrivacy(applicationContext, accountId, showFollows = !value)
            "showPublishedPlaylists" -> Settings.setPrivacy(applicationContext, accountId, showPublishedPlaylists = !value)
            else -> Settings.setAttribute(applicationContext, field, value)
        }

    internal fun switchAccountNow(id: String) {
        log.i("switch account tapped id=$id")
        PlaybackHost.reset(applicationContext)
        if (EncoreAuth.switchAccount(applicationContext, id)) {
            Krespot.library.invalidate()
            recreate()
        }
    }

    internal suspend fun beginAddAccountFlow(): List<EncoreAuth.Account> {
        // Snapshot the current account first so cancelling the flow can
        // restore it, then empty the WebView cookie jar: the live sp_dc
        // otherwise makes the login page auto-redirect into the player,
        // which the poll resolves as the already-saved (duplicate) account
        // and refuses, bouncing the user back without a login form.
        EncoreAuth.userId(applicationContext, force = true)
        EncoreAuth.storeCurrentAccount(applicationContext)
        EncoreAuth.clearCookies()
        return EncoreAuth.savedAccounts(applicationContext)
    }

    internal fun forgetAccountAndSwitch(id: String): Boolean =
        EncoreAuth.forgetAccount(applicationContext, id)

    private fun finishAddAccount() {
        EncoreAuth.storeCurrentAccount(applicationContext)
        PlaybackHost.reset(applicationContext)
        Krespot.library.invalidate()
        addingAccount = false
        recreate()
    }

    private fun refuseDuplicateAccount(id: String, name: String, activeId: String) {
        log.w("add account refused: '$name' ($id) is already saved")
        if (activeId.isBlank() || !EncoreAuth.switchAccount(applicationContext, activeId)) {
            EncoreAuth.invalidateToken(applicationContext)
            EncoreAuth.applySession(applicationContext)
        }
        addingAccount = false
        Toast.makeText(
            this,
            "'${name.ifBlank { "This account" }}' is already added",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun finishLogin() {
        EncoreAuth.storeCurrentAccount(applicationContext)
        PlaybackHost.reset(applicationContext)
        Krespot.library.invalidate()
    }

    private fun handleLoginBack(): Boolean {
        if (EncoreAuth.savedAccounts(applicationContext).isEmpty()) {
            log.i("login backed out, leaving the app")
            return false
        }
        log.i("login backed out with a saved account, restoring its session")
        EncoreAuth.applySession(applicationContext)
        if (EncoreAuth.token(applicationContext).isBlank()) {
            finish()
            return false
        }
        return true
    }

    @Composable
    private fun ShellSection(
        bridge: PlaybackBridge,
        account: EncoreAuth.Account?,
        context: Context,
        loggedIn: Boolean,
        activeAccountId: String,
        onSignedOut: () -> Unit,
        updates: UpdateController,
    ) {
        val scope = rememberCoroutineScope()
        val playback = bridge.state
        val contributors = rememberPlaylistContributorController(context, playlistUri)
        val activity = remember { ActivityFeedController(context.applicationContext, scope) }
        LaunchedEffect(activity.fetchTick) {
            if (activity.fetchTick > 0) activity.load()
        }
        LaunchedEffect(Unit) {
            activity.refreshUnread()
        }
        val home = rememberHomeFeedData()
        val recents by RecentSearches.observe().collectAsState()
        LaunchedEffect(activeAccountId) {
            RecentSearches.refresh(context)
        }
        val nowPlaying = rememberNowPlayingData(canvasAllowed = settingsSwitches.canvas)
        val splashUrls = remember(feed) {
            feed.asSequence()
                .flatMap { it.items.asSequence() }
                .mapNotNull { item -> item.imageUrl.takeIf { it.isNotBlank() } }
                .distinct()
                .take(28)
                .toList()
        }
        HomeSplashHost(
            loading = loading,
            urls = splashUrls,
        ) {
            ShellAppHostSection(
                bridge = bridge,
                account = account,
                context = context,
                loggedIn = loggedIn,
                activeAccountId = activeAccountId,
                onSignedOut = onSignedOut,
                updates = updates,
                scope = scope,
                home = home,
                nowPlaying = nowPlaying,
                contributors = contributors,
                activity = activity,
                recents = recents,
            )
        }
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) {
            openPlayerSignal++
        }
        SpotLinks.parse(intent.data)?.let { pendingLink = it }
    }

    private companion object {
        const val CLIENT_PREFS = "quackify.client"
        const val RESUME_REFRESH_AFTER_MS = 60_000L
        const val TOKEN_STILL_GOOD_MS = 60_000L
        const val TOKEN_CHECK_INTERVAL_MS = 48L * 60 * 60 * 1000L
        const val RECENTS_SYNC_THROTTLE_MS = 5 * 60_000L
        const val RECENT_SEARCH_SETTLE_MS = 4_000L
        const val VIDEO_AUTO_EXIT_MIN_MS = 1_500L
        const val VIDEO_AUTO_EXIT_MAX_MS = 3_000L
    }
}

/**
 * The ranked search answer decorates only its first row fully: every track
 * below the top result can arrive with a bare name and uri, which left the
 * Songs list without covers or artist lines. One decorateContextTracks
 * batch fills exactly those rows before the list reaches the screen.
 */
