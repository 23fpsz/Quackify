package com.spotiduck.compose.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.spotiduck.compose.R
import com.spotiduck.compose.components.RepeatMode
import com.spotiduck.compose.components.ShuffleMode
import com.spotiduck.compose.home.QuickAccessItem
import com.spotiduck.compose.home.RecommendationCard
import com.spotiduck.compose.home.SpotifyHomeScreen
import com.spotiduck.compose.library.LibraryItem
import com.spotiduck.compose.library.SpotifyLibraryScreen
import com.spotiduck.compose.playlist.SpotifyPlaylistScreen
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.screens.ArtistRelease
import com.spotiduck.compose.screens.HistoryScreen
import com.spotiduck.compose.screens.NotificationsScreen
import com.spotiduck.compose.screens.PopularTrack
import com.spotiduck.compose.screens.SettingsScreen
import com.spotiduck.compose.screens.SettingsSwitches
import com.spotiduck.compose.screens.SettingsLocks
import com.spotiduck.compose.screens.SpotifyAlbumScreen
import com.spotiduck.compose.screens.SpotifyPodcastScreen
import com.spotiduck.compose.screens.SpotifyArtistScreen
import com.spotiduck.compose.screens.SpotifyCategoryScreen
import com.spotiduck.compose.screens.CategoryShelf
import com.spotiduck.compose.screens.getCategoryColor
import com.spotiduck.compose.search.SearchHistoryItem
import com.spotiduck.compose.search.SpotifySearchScreen
import com.spotiduck.compose.sheets.LibrarySortOption
import com.spotiduck.compose.sheets.SleepTimerOption
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import androidx.compose.runtime.mutableStateListOf
import com.spotiduck.backend.model.ConnectDevice
import com.spotiduck.backend.model.SearchHit
import com.spotiduck.backend.model.SearchKind
import com.spotiduck.backend.model.SearchResults
import com.spotiduck.backend.model.BrowsePage
import com.spotiduck.backend.model.EpisodeDetail
import com.spotiduck.backend.model.ShowDetail
import com.spotiduck.compose.screens.SpotifyBrowseScreen
import com.spotiduck.compose.screens.SpotifyEpisodeScreen
import com.spotiduck.compose.screens.SpotifyShowScreen
import com.spotiduck.compose.screens.SpotifyNotification
import com.spotiduck.compose.format.parseHexColor
import com.spotiduck.compose.home.HomeShelf

enum class ShellScreen {
    HOME,
    SEARCH,
    LIBRARY,
    PLAYLIST,
    ALBUM,
    ARTIST,
    CATEGORY,
    SETTINGS,
    NOTIFICATIONS,
    HISTORY,
    PODCAST,
    FOLDER,
    SHOW,
    EPISODE,
    BROWSE
}

data class NavEntry(
    val screen: ShellScreen,
    val uri: String = "",
    val title: String = "",
)

internal enum class FolderMode { CREATE, RENAME, PICKER }

internal data class FolderDraft(
    val mode: FolderMode,
    val folderUri: String = "",
    val title: String = "",
    val folders: List<Pair<String, String>> = emptyList(),
)

data class NavItem(
    val title: String,
    val iconRes: Int,
    val activeIconRes: Int = iconRes
)

data class BackStackItem(
    val screen: ShellScreen,
    val categoryTitle: String = "",
    val playlistTitle: String = "",
    val playlistUri: String = "",
    val playlistCoverUrl: String? = null,
    val albumTitle: String = "",
    val albumArtist: String = "",
    val albumCoverUrl: String? = null,
    val artistName: String = "",
    val artistAvatarUrl: String? = null,
    val podcastTitle: String = "",
    val podcastCreator: String = "",
    val podcastCoverUrl: String? = null
)

internal data class ShellInputs(
    val quickAccess: List<QuickAccessItem>?,
    val shelves: List<HomeShelf>?,
    val homeLoading: Boolean,
    val searchQuery: String,
    val onSearchQueryChange: (String) -> Unit,
    val searchData: SearchResults?,
    val suggestions: List<SearchHit>,
    val recent: List<SearchHit>,
    val onClearRecent: () -> Unit,
    val onToggleSaved: (String) -> Unit,
    val onLoadSection: suspend (SearchKind, Int) -> List<SearchHit>,
    val searchLoading: Boolean,
    val playlistTracks: List<TrackItem>?,
    val displayName: String,
    val avatarUrl: String,
    val onLogout: () -> Unit,
)

data class ComposePlaybackState(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val isPlaying: Boolean = false,
    val progress: Float = 0.0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLiked: Boolean = false,
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false,
    val shuffleMode: ShuffleMode = if (isShuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF,
    val repeatMode: RepeatMode = if (isRepeat) RepeatMode.REPEAT_ALL else RepeatMode.OFF,
    val connectedDevice: String = "This Phone",
    val trackUri: String = "",
    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
    val controllingRemote: Boolean = false,
    val remoteCanSeek: Boolean = true,
    val remoteCanSkipPrev: Boolean = true,
    val remoteCanShuffle: Boolean = true,
    val remoteCanRepeat: Boolean = true,
    val devices: List<ConnectDevice> = emptyList(),
    val queue: List<TrackItem> = emptyList(),
    val queueFromContext: List<TrackItem> = emptyList(),
    val queueContext: String = "",
    val queueContextLabel: String = "",
    val contextUri: String = "",
    val contextLabel: String = "",
    val explicit: Boolean = false,
)

interface ComposePlayerListener {
    fun onPlayPause() {}
    fun onSkipNext() {}
    fun onSkipPrevious() {}
    fun onSeek(positionMs: Long) {}
    fun onLikeToggle() {}
    fun onShuffleToggle() {}
    fun onRepeatToggle() {}
    fun onTrackSelect(title: String, artist: String) {}
    fun onPlayUri(uri: String) {}
    fun onPlayTrack(uri: String, contextUri: String, index: Int) { onPlayUri(uri) }
    fun onListRendered(uris: List<String>) {}
    fun onQueueAdd(uri: String) {}
    fun onSelectDevice(deviceId: String) {}
    fun onDeviceSheetVisible(visible: Boolean) {}
    fun onVolumeChange(deviceId: String, level: Int) {}
    fun onQueueSet(uris: List<String>) {}
    fun onSearch(query: String) {}
    fun onLoadPlaylist(uri: String) {}
    fun onLoadAlbum(uri: String, albumName: String = "", artist: String = "") {}
    fun onLoadArtist(uri: String, artistName: String = "") {}
    fun onRefreshLibrary() {}
    fun onRefreshHome() {}
}

private data class AlbumInfo(
    val year: String,
    val date: String,
    val color: Color,
    val uri: String
)

/**
 * Spotify App Shell containing:
 * - 3-tab Bottom Navigation (Home, Search, Your Library)
 * - Persistent floating Mini-Player
 * - Animated Full Now Playing Player expansion
 * - Profile side sheet, Track context options sheet, Library create sheet, and Sort sheet
 * - Sub-screens: Settings, What's New notifications, Listening History, and Playlist view
 */
@Composable
fun SpotifyAppShell(
    modifier: Modifier = Modifier,
    playbackState: ComposePlaybackState? = null,
    playerListener: ComposePlayerListener? = null,
    isComposeUiEnabled: Boolean = true,
    onToggleComposeUi: (Boolean) -> Unit = {},
    onOpenSpotiDuckSettings: (() -> Unit)? = null,
    homeQuickAccessItems: List<QuickAccessItem>? = null,
    homeRecommendations: List<RecommendationCard>? = null,
    homeShelves: List<HomeShelf>? = null,
    libraryItems: List<LibraryItem>? = null,
    searchResults: List<TrackItem>? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    searchSuggestions: List<TrackItem> = emptyList(),
    searchRecent: List<SearchHistoryItem> = emptyList(),
    searchLoading: Boolean = false,
    searchQuery: String = "",
    searchData: SearchResults? = null,
    onClearRecentSearches: (() -> Unit)? = null,
    onLoadSearchSection: (suspend (SearchKind, Int) -> List<SearchHit>)? = null,
    onToggleSaved: ((String) -> Unit)? = null,
    homeLoading: Boolean = false,
    onLogout: (() -> Unit)? = null,
    settingsSwitches: SettingsSwitches = SettingsSwitches(),
    settingsLocks: SettingsLocks = SettingsLocks(),
    settingsProduct: String = "",
    settingsCountry: String = "",
    settingsLoading: Boolean = false,
    onToggleSetting: ((String, Boolean) -> Unit)? = null,
    browsePage: BrowsePage? = null,
    categoryShelves: List<CategoryShelf>? = null,
    onOpenCategory: ((String) -> Unit)? = null,
    showDetail: ShowDetail? = null,
    episodeDetail: EpisodeDetail? = null,
    notifications: List<SpotifyNotification> = emptyList(),
    notificationsLoading: Boolean = false,
    folderItems: List<LibraryItem>? = null,
    onOpenFolder: ((String) -> Unit)? = null,
    onLoadPlaylist: ((String) -> Unit)? = null,
    onLoadAlbum: ((String, String) -> Unit)? = null,
    onLoadArtist: ((String, String) -> Unit)? = null,
    onOpenShow: ((String) -> Unit)? = null,
    onOpenEpisode: ((String) -> Unit)? = null,
    onOpenBrowse: ((String) -> Unit)? = null,
    onLoadMoreBrowse: (() -> Unit)? = null,
    onLoadMoreEpisodes: (() -> Unit)? = null,
    playlistTracks: List<TrackItem>? = null,
    albumTracks: List<TrackItem>? = null,
    artistTracks: List<PopularTrack>? = null,
    artistReleases: List<ArtistRelease>? = null,
    artistMonthlyListeners: String? = null,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = ""
) {
    var currentScreen by remember { mutableStateOf(ShellScreen.HOME) }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var activePlaylistTitle by remember { mutableStateOf("") }
    var activePlaylistUri by remember { mutableStateOf("") }
    var activePlaylistCoverUrl by remember { mutableStateOf<String?>(null) }
    var activeAlbumCoverUrl by remember { mutableStateOf<String?>(null) }
    var activeArtistAvatarUrl by remember { mutableStateOf<String?>(null) }
    var activeCategoryTitle by remember { mutableStateOf("") }
    var activeCategoryColor by remember { mutableStateOf(Color(0xFF148A08)) }
    var activePodcastTitle by remember { mutableStateOf("") }
    var activePodcastCreator by remember { mutableStateOf("") }
    var activePodcastCoverUrl by remember { mutableStateOf<String?>(null) }
    var activeShowUri by remember { mutableStateOf("") }
    var activeFolderUri by remember { mutableStateOf("") }
    var activeFolderTitle by remember { mutableStateOf("") }

    // Bottom Sheet States
    var isProfileDrawerOpen by remember { mutableStateOf(false) }
    var isTrackOptionsOpen by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackItem?>(null) }
    var isPlaylistOptionsOpen by remember { mutableStateOf(false) }
    var isAlbumOptionsOpen by remember { mutableStateOf(false) }
    var isArtistOptionsOpen by remember { mutableStateOf(false) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    var currentSortOption by remember { mutableStateOf(LibrarySortOption.RECENTS) }
    var isCreateSheetOpen by remember { mutableStateOf(false) }
    var isDeviceSheetOpen by remember { mutableStateOf(false) }
    var isQueueSheetOpen by remember { mutableStateOf(false) }
    var isSleepTimerOpen by remember { mutableStateOf(false) }
    var activeSleepTimer by remember { mutableStateOf<SleepTimerOption?>(null) }
    var activeArtistName by remember { mutableStateOf("") }
    var activeAlbumTitle by remember { mutableStateOf("") }
    var activeAlbumArtist by remember { mutableStateOf("") }
    var activeAlbumYear by remember { mutableStateOf("") }
    var activeAlbumReleaseDate by remember { mutableStateOf("") }
    var activeAlbumColor by remember { mutableStateOf(Color(0xFF901A1E)) }

    val navBackStack = remember { mutableStateListOf<BackStackItem>() }

    fun pushCurrentState() {
        navBackStack.add(
            BackStackItem(
                screen = currentScreen,
                categoryTitle = activeCategoryTitle,
                playlistTitle = activePlaylistTitle,
                playlistUri = activePlaylistUri,
                playlistCoverUrl = activePlaylistCoverUrl,
                albumTitle = activeAlbumTitle,
                albumArtist = activeAlbumArtist,
                albumCoverUrl = activeAlbumCoverUrl,
                artistName = activeArtistName,
                artistAvatarUrl = activeArtistAvatarUrl,
                podcastTitle = activePodcastTitle,
                podcastCreator = activePodcastCreator,
                podcastCoverUrl = activePodcastCoverUrl
            )
        )
    }

    val handleBack: () -> Unit = {
        if (navBackStack.isNotEmpty()) {
            val previous = navBackStack.removeAt(navBackStack.lastIndex)
            currentScreen = previous.screen
            if (previous.categoryTitle.isNotEmpty()) {
                activeCategoryTitle = previous.categoryTitle
                activeCategoryColor = getCategoryColor(previous.categoryTitle)
            }
            if (previous.playlistTitle.isNotEmpty()) {
                activePlaylistTitle = previous.playlistTitle
                activePlaylistUri = previous.playlistUri
                if (previous.playlistCoverUrl != null) activePlaylistCoverUrl = previous.playlistCoverUrl
            }
            if (previous.albumTitle.isNotEmpty()) {
                activeAlbumTitle = previous.albumTitle
                activeAlbumArtist = previous.albumArtist
                if (previous.albumCoverUrl != null) activeAlbumCoverUrl = previous.albumCoverUrl
            }
            if (previous.podcastTitle.isNotEmpty()) {
                activePodcastTitle = previous.podcastTitle
                activePodcastCreator = previous.podcastCreator
                if (previous.podcastCoverUrl != null) activePodcastCoverUrl = previous.podcastCoverUrl
            }
            if (previous.artistName.isNotEmpty()) {
                activeArtistName = previous.artistName
                if (previous.artistAvatarUrl != null) activeArtistAvatarUrl = previous.artistAvatarUrl
            }
            when (previous.screen) {
                ShellScreen.HOME -> selectedNavIndex = 0
                ShellScreen.SEARCH, ShellScreen.CATEGORY -> selectedNavIndex = 1
                ShellScreen.LIBRARY -> selectedNavIndex = 2
                else -> {}
            }
        } else if (currentScreen != ShellScreen.HOME) {
            currentScreen = ShellScreen.HOME
            selectedNavIndex = 0
        }
    }

    val handleOpenSettings: () -> Unit = {
        if (onOpenSpotiDuckSettings != null) {
            onOpenSpotiDuckSettings()
        } else {
            pushCurrentState()
            currentScreen = ShellScreen.SETTINGS
        }
    }

    val userQueueList = remember { mutableStateListOf<TrackItem>() }

    var localIsPlaying by remember { mutableStateOf(false) }
    var localTrackTitle by remember { mutableStateOf("") }
    var localArtist by remember { mutableStateOf("") }
    var localProgress by remember { mutableFloatStateOf(0f) }
    var localPositionMs by remember { mutableLongStateOf(0L) }
    val localDurationMs = 0L
    var localIsLiked by remember { mutableStateOf(false) }
    var localShuffleMode by remember { mutableStateOf(ShuffleMode.OFF) }
    var localRepeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var localDevice by remember { mutableStateOf("") }

    // Resolved playback properties: External state takes precedence if provided
    val currentTrackTitle = playbackState?.title ?: localTrackTitle
    val currentArtist = playbackState?.artist ?: localArtist
    val isPlaying = playbackState?.isPlaying ?: localIsPlaying
    val currentProgress = playbackState?.progress ?: localProgress
    val currentPositionMs = playbackState?.positionMs ?: localPositionMs
    val durationMs = if ((playbackState?.durationMs ?: 0L) > 0L) playbackState!!.durationMs else localDurationMs
    val isLiked = playbackState?.isLiked ?: localIsLiked
    val currentShuffleMode = playbackState?.shuffleMode ?: localShuffleMode
    val currentRepeatMode = playbackState?.repeatMode ?: localRepeatMode
    val connectedDevice = playbackState?.connectedDevice ?: localDevice
    val currentCoverUrl = playbackState?.coverUrl ?: ""

    val activeAlbumTracks = albumTracks.orEmpty()

    val openAlbum: (String, String) -> Unit = { title, artist ->
        activeAlbumTitle = title
        activeAlbumArtist = artist
        val foundEntity = libraryItems?.find { it.title.equals(title, ignoreCase = true) }
        val resolvedCover = foundEntity?.imageUrl

        if (resolvedCover != null) activeAlbumCoverUrl = resolvedCover

        val albumUriToLoad = foundEntity?.uri?.takeIf { it.startsWith("spotify:album:") }.orEmpty()
        if (onLoadAlbum != null) onLoadAlbum?.invoke(albumUriToLoad, title) else playerListener?.onLoadAlbum(albumUriToLoad, title, activeAlbumArtist)

        pushCurrentState()
        currentScreen = ShellScreen.ALBUM
    }

    val openPodcast: (String, String, String?) -> Unit = { title, creator, coverUrl ->
        pushCurrentState()
        activePodcastTitle = title
        activePodcastCreator = creator
        if (coverUrl != null) activePodcastCoverUrl = coverUrl
        currentScreen = ShellScreen.PODCAST
    }

    val openArtist: (String, String?) -> Unit = { artist, imageUrl ->
        activeArtistName = artist
        val foundEntity = libraryItems?.find { it.title.equals(artist, ignoreCase = true) }
        val resolvedUrl = imageUrl ?: foundEntity?.imageUrl ?: activeArtistAvatarUrl
        activeArtistAvatarUrl = resolvedUrl

        val artistUriToLoad = foundEntity?.uri?.takeIf { it.startsWith("spotify:artist:") }.orEmpty()
        if (onLoadArtist != null) onLoadArtist?.invoke(artistUriToLoad, artist) else playerListener?.onLoadArtist(artistUriToLoad, artist)

        pushCurrentState()
        currentScreen = ShellScreen.ARTIST
    }

    val handleSkipNext: () -> Unit = {
        if (playerListener != null) {
            playerListener.onSkipNext()
        }
    }

    val handleSkipPrevious: () -> Unit = {
        if (playerListener != null) {
            playerListener.onSkipPrevious()
        }
    }

    val navItems = remember {
        listOf(
            NavItem("Home", R.drawable.encore_icon_home_24, R.drawable.encore_icon_home_active_24),
            NavItem("Search", R.drawable.encore_icon_search_24, R.drawable.encore_icon_search_active_24),
            NavItem("Your Library", R.drawable.encore_icon_your_library_24, R.drawable.encore_icon_your_library_active_24)
        )
    }

    // System Back Handler
    BackHandler(
        enabled = isPlayerExpanded ||
                isProfileDrawerOpen ||
                isTrackOptionsOpen ||
                isPlaylistOptionsOpen ||
                isAlbumOptionsOpen ||
                isArtistOptionsOpen ||
                isSortSheetOpen ||
                isCreateSheetOpen ||
                navBackStack.isNotEmpty() ||
                currentScreen != ShellScreen.HOME
    ) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (isProfileDrawerOpen) {
            isProfileDrawerOpen = false
        } else if (isTrackOptionsOpen) {
            isTrackOptionsOpen = false
        } else if (isPlaylistOptionsOpen) {
            isPlaylistOptionsOpen = false
        } else if (isAlbumOptionsOpen) {
            isAlbumOptionsOpen = false
        } else if (isArtistOptionsOpen) {
            isArtistOptionsOpen = false
        } else if (isSortSheetOpen) {
            isSortSheetOpen = false
        } else if (isCreateSheetOpen) {
            isCreateSheetOpen = false
        } else {
            handleBack()
        }
    }

    val currentNavIndex = when (currentScreen) {
        ShellScreen.HOME -> 0
        ShellScreen.SEARCH, ShellScreen.CATEGORY -> 1
        ShellScreen.LIBRARY -> 2
        else -> selectedNavIndex
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.PureBlack)
    ) {
        // Active Screen Content
        when (currentScreen) {
            ShellScreen.HOME -> {
                SpotifyHomeScreen(
                    onPlaylistClick = { playlist, uri ->
                        val foundItem = homeQuickAccessItems?.find { it.uri == uri || it.title == playlist }
                        val foundRec = homeRecommendations?.find { it.uri == uri || it.title == playlist }
                        val foundLib = libraryItems?.find { it.uri == uri || it.title == playlist }
                        val cover = foundItem?.imageUrl ?: foundRec?.imageUrl ?: foundLib?.imageUrl
                        if (cover != null) {
                            activePlaylistCoverUrl = cover
                            activeAlbumCoverUrl = cover
                        }
                        if (uri.startsWith("spotify:album:")) {
                            openAlbum(playlist, "")
                        } else {
                            pushCurrentState()
                            activePlaylistTitle = playlist
                            activePlaylistUri = uri
                            currentScreen = ShellScreen.PLAYLIST
                            if (uri.isNotEmpty()) {
                                if (onLoadPlaylist != null) onLoadPlaylist?.invoke(uri) else playerListener?.onLoadPlaylist(uri)
                            }
                        }
                    },
                    onProfileClick = { isProfileDrawerOpen = true },
                    onNotificationsClick = {
                        pushCurrentState()
                        currentScreen = ShellScreen.NOTIFICATIONS
                    },
                    onHistoryClick = {
                        pushCurrentState()
                        currentScreen = ShellScreen.HISTORY
                    },
                    onSettingsClick = handleOpenSettings,
                    quickAccessItems = homeQuickAccessItems,
                    recommendations = homeRecommendations,
                    shelves = homeShelves,
                    loading = homeLoading,
                    whatsNewCount = notifications.count { it.isNew },
                    onArtistClick = { artist -> openArtist(artist, null) },
                    userDisplayName = userDisplayName,
                    userAvatarUrl = userAvatarUrl
                )
            }
            ShellScreen.SEARCH -> {
                SpotifySearchScreen(
                    onCategoryClick = { category ->
                        pushCurrentState()
                        activeCategoryTitle = category
                        activeCategoryColor = getCategoryColor(category)
                        currentScreen = ShellScreen.CATEGORY
                        onOpenCategory?.invoke(category)
                    },
                    onAlbumClick = { title, artist -> openAlbum(title, artist) },
                    onArtistClick = { artist -> openArtist(artist, null) },
                    onPlaylistClick = { title, uri ->
                        pushCurrentState()
                        activePlaylistTitle = title
                        activePlaylistUri = uri
                        currentScreen = ShellScreen.PLAYLIST
                        if (uri.isNotEmpty()) {
                            if (onLoadPlaylist != null) onLoadPlaylist?.invoke(uri) else playerListener?.onLoadPlaylist(uri)
                        }
                    },
                    onTrackSelect = { track ->
                        if (playerListener != null) {
                            if (track.uri.isNotEmpty()) {
                                playerListener.onPlayUri(track.uri)
                            } else {
                                playerListener.onTrackSelect(track.title, track.artist)
                            }
                        } else {
                            localTrackTitle = track.title
                            localArtist = track.artist
                            localIsPlaying = true
                            localProgress = 0.05f
                            localPositionMs = 12000L
                        }
                    },
                    onTrackOptionsClick = { track ->
                        selectedTrackForOptions = track
                        isTrackOptionsOpen = true
                    },
                    onProfileClick = { isProfileDrawerOpen = true },
                    searchResults = searchResults,
                    onSearchQueryChange = { query ->
                        onSearchQueryChange?.invoke(query) ?: playerListener?.onSearch(query)
                    },
                    recentSearches = searchRecent,
                    suggestions = searchSuggestions,
                    loading = searchLoading,
                    onClearRecent = { onClearRecentSearches?.invoke() },
                    onToggleSaved = { uri -> onToggleSaved?.invoke(uri) },
                    userDisplayName = userDisplayName,
                    userAvatarUrl = userAvatarUrl,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying
                )
            }
            ShellScreen.LIBRARY -> {
                SpotifyLibraryScreen(
                    onItemClick = { itemTitle, itemUri ->
                        val foundItem = libraryItems?.find { it.uri == itemUri || it.title == itemTitle }
                        if (foundItem?.imageUrl != null) {
                            activePlaylistCoverUrl = foundItem.imageUrl
                        }
                        pushCurrentState()
                        activePlaylistTitle = itemTitle
                        activePlaylistUri = itemUri
                        currentScreen = ShellScreen.PLAYLIST
                        if (itemUri.isNotEmpty()) {
                            if (onLoadPlaylist != null) onLoadPlaylist?.invoke(itemUri) else playerListener?.onLoadPlaylist(itemUri)
                        }
                    },
                    onAlbumClick = { title, artist -> openAlbum(title, artist) },
                    onPodcastClick = { title, creator ->
                        val foundItem = libraryItems?.find { it.title == title }
                        openPodcast(title, creator.removePrefix("Podcast • "), foundItem?.imageUrl)
                    },
                    onArtistClick = { artist -> openArtist(artist, null) },
                    items = libraryItems,
                    userDisplayName = userDisplayName,
                    userAvatarUrl = userAvatarUrl,
                    onProfileClick = { isProfileDrawerOpen = true },
                    onPlusClick = { isCreateSheetOpen = true },
                    onSortClick = { isSortSheetOpen = true },
                    currentSort = currentSortOption
                )
            }
            ShellScreen.PLAYLIST -> {
                androidx.compose.runtime.key(activePlaylistUri.ifEmpty { activePlaylistTitle }) {
                    val effectivePlaylistTracks = if (activePlaylistUri.isNotEmpty()) {
                        playlistTracks ?: emptyList()
                    } else {
                        playlistTracks ?: emptyList()
                    }
                    SpotifyPlaylistScreen(
                        title = activePlaylistTitle,
                        description = if (activePlaylistTitle.contains("This Is")) "This is $activePlaylistTitle. The essential tracks, all in one place." else "",
                        creator = if (activePlaylistTitle.contains("This Is") || activePlaylistTitle.contains("Today's")) "Spotify" else userDisplayName,
                        saveCount = "",
                        tracks = effectivePlaylistTracks,
                        currentTrackTitle = currentTrackTitle,
                        isPlaying = isPlaying,
                        headerColor = Color(0xFF144D3F),
                        coverUrl = activePlaylistCoverUrl ?: playlistTracks?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
                        onBackClick = handleBack,
                        onPlayPauseClick = {
                            if (playerListener != null) {
                                if (activePlaylistUri.isNotEmpty() && !isPlaying) {
                                    playerListener.onPlayUri(activePlaylistUri)
                                } else {
                                    playerListener.onPlayPause()
                                }
                            } else {
                                localIsPlaying = !localIsPlaying
                            }
                        },
                        onTrackSelect = { track ->
                            if (playerListener != null) {
                                if (track.uri.isNotEmpty()) {
                                    playerListener.onPlayUri(track.uri)
                                } else {
                                    playerListener.onTrackSelect(track.title, track.artist)
                                }
                            } else {
                                localTrackTitle = track.title
                                localArtist = track.artist
                                localIsPlaying = true
                                localProgress = 0.05f
                                localPositionMs = 12000L
                            }
                        },
                        onTrackOptionsClick = { track ->
                            selectedTrackForOptions = track
                            isTrackOptionsOpen = true
                        },
                        onPlaylistOptionsClick = { isPlaylistOptionsOpen = true }
                    )
                }
            }
            ShellScreen.ALBUM -> {
                androidx.compose.runtime.key(activeAlbumTitle, activeAlbumArtist) {
                    val effectiveAlbumTracks = if (!albumTracks.isNullOrEmpty()) albumTracks else activeAlbumTracks
                    SpotifyAlbumScreen(
                        albumTitle = activeAlbumTitle,
                        artistName = activeAlbumArtist,
                        releaseYear = activeAlbumYear,
                        releaseDate = activeAlbumReleaseDate,
                        coverColor = activeAlbumColor,
                        tracks = effectiveAlbumTracks,
                        currentTrackTitle = currentTrackTitle,
                        isPlaying = isPlaying,
                        coverUrl = activeAlbumCoverUrl,
                        moreByArtist = emptyList(),
                        onBackClick = handleBack,
                        onPlayPauseClick = {
                            if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                        },
                        onTrackSelect = { track ->
                            if (playerListener != null) {
                                if (track.uri.isNotEmpty()) {
                                    playerListener.onPlayUri(track.uri)
                                } else {
                                    playerListener.onTrackSelect(track.title, track.artist)
                                }
                            }
                        },
                        onTrackOptionsClick = { track ->
                            selectedTrackForOptions = track
                            isTrackOptionsOpen = true
                        },
                        onAlbumOptionsClick = { isAlbumOptionsOpen = true },
                        onArtistClick = { artist -> openArtist(artist, null) },
                        onAlbumClick = { title ->
                            openAlbum(title, activeAlbumArtist)
                        }
                    )
                }
            }
            ShellScreen.ARTIST -> {
                androidx.compose.runtime.key(activeArtistName) {
                    SpotifyArtistScreen(
                        artistName = activeArtistName,
                        monthlyListeners = artistMonthlyListeners?.takeIf { it.isNotBlank() }.orEmpty(),
                        heroColor = EncoreColors.Card,
                        headerImageUrl = activeArtistAvatarUrl,
                        popularTracks = artistTracks.orEmpty(),
                        releases = artistReleases.orEmpty(),
                        currentTrackTitle = currentTrackTitle,
                        isPlaying = isPlaying,
                        onBackClick = handleBack,
                        onPlayPauseClick = {
                            if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                        },
                        onTrackSelect = { track ->
                            if (playerListener != null) {
                                playerListener.onTrackSelect(track.title, track.artist)
                            }
                        },
                        onTrackOptionsClick = { track ->
                            selectedTrackForOptions = track
                            isTrackOptionsOpen = true
                        },
                        onArtistOptionsClick = { isArtistOptionsOpen = true },
                        onReleaseClick = { release ->
                            openAlbum(release.title, activeArtistName)
                        }
                    )
                }
            }
            ShellScreen.CATEGORY -> {
                SpotifyCategoryScreen(
                    categoryTitle = activeCategoryTitle,
                    categoryColor = activeCategoryColor,
                    onBackClick = handleBack,
                    onPlaylistClick = { title, uri ->
                        val foundItem = homeQuickAccessItems?.find { it.uri == uri || it.title == title }
                        val foundRec = homeRecommendations?.find { it.uri == uri || it.title == title }
                        val foundLib = libraryItems?.find { it.uri == uri || it.title == title }
                        val cover = foundItem?.imageUrl ?: foundRec?.imageUrl ?: foundLib?.imageUrl
                        if (cover != null) {
                            activePlaylistCoverUrl = cover
                        }
                        pushCurrentState()
                        activePlaylistTitle = title
                        activePlaylistUri = uri
                        currentScreen = ShellScreen.PLAYLIST
                        if (uri.isNotEmpty()) {
                            if (onLoadPlaylist != null) onLoadPlaylist?.invoke(uri) else playerListener?.onLoadPlaylist(uri)
                        }
                    },
                    onAlbumClick = { title, artist ->
                        openAlbum(title, artist)
                    },
                    onArtistClick = { artist ->
                        openArtist(artist, null)
                    },
                    onSubcategoryClick = { subcategory ->
                        pushCurrentState()
                        activeCategoryTitle = subcategory
                        activeCategoryColor = getCategoryColor(subcategory)
                        currentScreen = ShellScreen.CATEGORY
                    },
                    onTrackSelect = { track ->
                        if (playerListener != null) {
                            if (track.uri.isNotEmpty()) {
                                playerListener.onPlayUri(track.uri)
                            } else {
                                playerListener.onTrackSelect(track.title, track.artist)
                            }
                        } else {
                            localTrackTitle = track.title
                            localArtist = track.artist
                            localIsPlaying = true
                            localProgress = 0.05f
                            localPositionMs = 12000L
                        }
                    },
                    shelves = categoryShelves
                )
            }
            ShellScreen.SETTINGS -> {
                SettingsScreen(
                    onBackClick = handleBack,
                    onLogout = { onLogout?.invoke() },
                    switches = settingsSwitches,
                    locks = settingsLocks,
                    product = settingsProduct,
                    country = settingsCountry,
                    loading = settingsLoading,
                    onToggleMusicVideos = { onToggleSetting?.invoke("musicVideos", it) },
                    onToggleCanvas = { onToggleSetting?.invoke("canvas", it) },
                    onToggleOtherVideos = { onToggleSetting?.invoke("otherVideos", it) },
                    onToggleShowFollows = { onToggleSetting?.invoke("showFollows", it) },
                    onToggleShowPlaylists = { onToggleSetting?.invoke("showPlaylists", it) }
                )
            }
            ShellScreen.NOTIFICATIONS -> {
                NotificationsScreen(
                    onBackClick = handleBack,
                    notifications = notifications,
                    loading = notificationsLoading,
                    onPlayClick = { title ->
                        pushCurrentState()
                        activePlaylistTitle = title
                        currentScreen = ShellScreen.PLAYLIST
                    }
                )
            }
            ShellScreen.HISTORY -> {
                HistoryScreen(
                    onBackClick = handleBack,
                    onTrackSelect = { track ->
                        if (playerListener != null) {
                            playerListener.onTrackSelect(track.title, track.artist)
                        } else {
                            localTrackTitle = track.title
                            localArtist = track.artist
                            localIsPlaying = true
                            localProgress = 0.05f
                            localPositionMs = 12000L
                        }
                    },
                    onTrackOptionsClick = { track ->
                        selectedTrackForOptions = track
                        isTrackOptionsOpen = true
                    },
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying
                )
            }
            ShellScreen.PODCAST -> {
                SpotifyPodcastScreen(
                    showTitle = activePodcastTitle,
                    publisherName = activePodcastCreator,
                    coverUrl = activePodcastCoverUrl,
                    onBackClick = handleBack,
                    onPlayPauseClick = {
                        if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                    },
                    onEpisodeSelect = { ep ->
                        if (playerListener != null) {
                            playerListener.onTrackSelect(ep.title, activePodcastCreator)
                        } else {
                            localTrackTitle = ep.title
                            localArtist = activePodcastCreator
                            localIsPlaying = true
                        }
                    },
                    currentEpisodeId = "",
                    isPlaying = isPlaying
                )
            }

            ShellScreen.BROWSE -> {
                val page = browsePage
                if (page == null) {
                    onOpenBrowse?.invoke(activeCategoryTitle)
                } else {
                    SpotifyBrowseScreen(
                        title = page.title.ifBlank { activeCategoryTitle },
                        subtitle = page.subtitle,
                        colorHex = page.colorHex,
                        sections = page.sections,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = page.nextOffset != null,
                        onBackClick = handleBack,
                        onCardClick = { title, uri ->
                            if (uri.startsWith("spotify:show:")) {
                                onOpenShow?.invoke(uri)
                            } else {
                                pushCurrentState()
                                activePlaylistTitle = title
                                activePlaylistUri = uri
                                currentScreen = ShellScreen.PLAYLIST
                                if (uri.isNotEmpty()) if (onLoadPlaylist != null) onLoadPlaylist?.invoke(uri) else playerListener?.onLoadPlaylist(uri)
                            }
                        },
                        onLoadMore = { onLoadMoreBrowse?.invoke() }
                    )
                }
            }

            ShellScreen.SHOW -> {
                val show = showDetail
                if (show == null) {
                    onOpenShow?.invoke(activeShowUri)
                } else {
                    SpotifyShowScreen(
                        showName = show.name,
                        publisher = show.publisher,
                        description = show.description,
                        imageUrl = show.imageUrl.takeIf { it.isNotBlank() },
                        headerColor = parseHexColor(show.colorHex) ?: EncoreColors.Card,
                        rating = show.rating,
                        ratingCount = show.ratingCount,
                        isFollowing = show.saved,
                        episodes = show.episodes,
                        total = show.total,
                        isPlaying = isPlaying,
                        currentTrackTitle = currentTrackTitle,
                        onEpisodeClick = { episode -> onOpenEpisode?.invoke(episode.uri) },
                        onBackClick = handleBack,
                        onPlayPauseClick = {
                            if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                        },
                        onLoadMore = { onLoadMoreEpisodes?.invoke() }
                    )
                }
            }

            ShellScreen.EPISODE -> {
                val episode = episodeDetail
                if (episode == null) {
                    onOpenEpisode?.invoke("")
                } else {
                    SpotifyEpisodeScreen(
                        title = episode.title,
                        showName = episode.showName,
                        description = episode.description,
                        imageUrl = episode.imageUrl.takeIf { it.isNotBlank() },
                        wideImageUrl = episode.wideImageUrl.takeIf { it.isNotBlank() },
                        headerColor = parseHexColor(episode.colorHex) ?: EncoreColors.Card,
                        metaLabel = episode.releaseDate,
                        durationMs = episode.durationMs,
                        isPlaying = isPlaying,
                        onBackClick = handleBack,
                        onPlayPauseClick = {
                            if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                        },
                        onShowClick = { onOpenShow?.invoke(episode.showUri) }
                    )
                }
            }

            ShellScreen.FOLDER -> {
                SpotifyLibraryScreen(
                    onItemClick = { itemTitle, itemUri ->
                        val foundItem = folderItems?.find { it.uri == itemUri || it.title == itemTitle }
                        if (foundItem?.uri?.startsWith("spotify:user:") == true) {
                            pushCurrentState()
                            activeFolderUri = foundItem.uri
                            activeFolderTitle = foundItem.title
                            currentScreen = ShellScreen.FOLDER
                            onOpenFolder?.invoke(foundItem.uri)
                        } else {
                            pushCurrentState()
                            activePlaylistTitle = itemTitle
                            activePlaylistUri = itemUri
                            currentScreen = ShellScreen.PLAYLIST
                            if (itemUri.isNotEmpty()) if (onLoadPlaylist != null) onLoadPlaylist?.invoke(itemUri) else playerListener?.onLoadPlaylist(itemUri)
                        }
                    },
                    items = folderItems,
                    onProfileClick = { isProfileDrawerOpen = true }
                )
            }
        }

        ShellBottomBar(
            title = currentTrackTitle,
            artist = currentArtist,
            isPlaying = isPlaying,
            progress = currentProgress,
            isLiked = isLiked,
            coverUrl = currentCoverUrl,
            connectedDevice = connectedDevice,
            navItems = navItems,
            currentNavIndex = selectedNavIndex,
            onNavSelect = { index ->
                selectedNavIndex = index
                when (index) {
                    0 -> if (currentScreen != ShellScreen.HOME) { navBackStack.clear(); currentScreen = ShellScreen.HOME }
                    1 -> if (currentScreen != ShellScreen.SEARCH) { navBackStack.clear(); currentScreen = ShellScreen.SEARCH }
                    2 -> if (currentScreen != ShellScreen.LIBRARY) { navBackStack.clear(); currentScreen = ShellScreen.LIBRARY }
                }
            },
            onPlayPause = { if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying },
            onLike = { if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = !localIsLiked },
            onDeviceClick = { isDeviceSheetOpen = true },
            onBarClick = { isPlayerExpanded = true },
            onSkipNext = handleSkipNext,
            onSkipPrevious = handleSkipPrevious,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Full Now Playing Screen
        val (contextHeader, contextSub) = when (currentScreen) {
            ShellScreen.PLAYLIST -> "Playing from playlist" to activePlaylistTitle
            ShellScreen.ALBUM -> "Playing from album" to activeAlbumTitle
            ShellScreen.ARTIST -> "Playing from artist" to activeArtistName
            else -> "Playing from playlist" to activePlaylistTitle
        }
        ShellNowPlaying(
            visible = isPlayerExpanded,
            title = currentTrackTitle,
            artist = currentArtist,
            contextHeader = contextHeader,
            contextSub = contextSub,
            isPlaying = isPlaying,
            progress = currentProgress,
            durationMs = durationMs,
            positionMs = currentPositionMs,
            dominantColor = EncoreColors.Card,
            isLiked = isLiked,
            isExplicit = playbackState?.explicit ?: false,
            shuffleMode = currentShuffleMode,
            repeatMode = currentRepeatMode,
            connectedDevice = connectedDevice,
            coverUrl = currentCoverUrl,
            onCollapse = { isPlayerExpanded = false },
            onPlayPause = { if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying },
            onSkipNext = handleSkipNext,
            onSkipPrevious = handleSkipPrevious,
            onLike = { if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = !localIsLiked },
            onShuffle = { if (playerListener != null) playerListener.onShuffleToggle() else localShuffleMode = localShuffleMode.next() },
            onRepeat = { if (playerListener != null) playerListener.onRepeatToggle() else localRepeatMode = localRepeatMode.next() },
            onSeek = { p -> if (playerListener != null) playerListener.onSeek((p * durationMs).toLong()) else { localProgress = p; localPositionMs = (p * durationMs).toLong() } },
            onMore = { selectedTrackForOptions = TrackItem(currentTrackTitle, currentArtist, isLiked); isTrackOptionsOpen = true },
            onQueue = { isQueueSheetOpen = true },
            onDevice = { isDeviceSheetOpen = true }
        )

        ShellSheets(
            flags = SheetFlags(
                profileDrawer = isProfileDrawerOpen,
                trackOptions = isTrackOptionsOpen,
                playlistOptions = isPlaylistOptionsOpen,
                albumOptions = isAlbumOptionsOpen,
                artistOptions = isArtistOptionsOpen,
                sort = isSortSheetOpen,
                create = isCreateSheetOpen,
                device = isDeviceSheetOpen,
                queue = isQueueSheetOpen,
                sleepTimer = isSleepTimerOpen
            ),
            selectedTrack = selectedTrackForOptions,
            devices = playbackState?.devices.orEmpty(),
            connectedDevice = connectedDevice,
            queueFromContext = playbackState?.queueFromContext.orEmpty(),
            currentTrackTitle = currentTrackTitle,
            currentTrackArtist = currentArtist,
            playlistTitle = activePlaylistTitle,
            playlistCoverUrl = activePlaylistCoverUrl,
            albumTitle = activeAlbumTitle,
            albumArtist = activeAlbumArtist,
            albumCoverUrl = activeAlbumCoverUrl,
            artistName = activeArtistName,
            artistAvatarUrl = activeArtistAvatarUrl,
            sortOption = currentSortOption,
            sleepTimer = activeSleepTimer,
            userDisplayName = userDisplayName,
            userAvatarUrl = userAvatarUrl,
            onDismissProfile = { isProfileDrawerOpen = false },
            onDismissTrackOptions = { isTrackOptionsOpen = false },
            onDismissPlaylistOptions = { isPlaylistOptionsOpen = false },
            onDismissAlbumOptions = { isAlbumOptionsOpen = false },
            onDismissArtistOptions = { isArtistOptionsOpen = false },
            onDismissSort = { isSortSheetOpen = false },
            onDismissCreate = { isCreateSheetOpen = false },
            onDismissDevice = { isDeviceSheetOpen = false },
            onDismissQueue = { isQueueSheetOpen = false },
            onDismissSleepTimer = { isSleepTimerOpen = false },
            onWhatsNew = { pushCurrentState(); currentScreen = ShellScreen.NOTIFICATIONS },
            onHistory = { pushCurrentState(); currentScreen = ShellScreen.HISTORY },
            onSettings = handleOpenSettings,
            onLogout = { onLogout?.invoke() },
            onLikeToggle = { liked -> if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = liked },
            onAddToQueue = { userQueueList.add(it) },
            onViewArtist = { artist -> openArtist(artist, null) },
            onViewAlbum = { album -> openAlbum(album, selectedTrackForOptions?.artist ?: "") },
            onSleepTimerClick = { isSleepTimerOpen = true },
            onGoToArtist = { artist -> openArtist(artist, null) },
            onSortSelected = { selected -> currentSortOption = selected },
            onCreatePlaylist = { pushCurrentState(); activePlaylistTitle = "New Playlist"; currentScreen = ShellScreen.PLAYLIST },
            onCreateBlend = { pushCurrentState(); activePlaylistTitle = "Blend"; currentScreen = ShellScreen.PLAYLIST },
            onSelectDevice = { id -> localDevice = id; isDeviceSheetOpen = false; playerListener?.onSelectDevice(id) },
            onDeviceVisible = { visible -> playerListener?.onDeviceSheetVisible(visible) },
            onVolumeChange = { id, level -> playerListener?.onVolumeChange(id, level) },
            onQueueTrackSelect = { track ->
                if (playerListener != null) {
                    playerListener.onTrackSelect(track.title, track.artist)
                } else {
                    localTrackTitle = track.title
                    localArtist = track.artist
                    localIsPlaying = true
                    localProgress = 0.05f
                    localPositionMs = 12000L
                }
                userQueueList.remove(track)
            },
            onRemoveFromQueue = { track -> userQueueList.remove(track) },
            onClearQueue = { userQueueList.clear() },
            onSleepTimerSelected = { option -> activeSleepTimer = option }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyAppShell() {
    SpotifyEncoreTheme {
        SpotifyAppShell()
    }
}
