package com.quackify.compose.shell

import com.quackify.compose.sheets.SpotifyCreateNameDialog
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.quackify.compose.R
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.home.QuickAccessItem
import com.quackify.compose.home.RecommendationCard
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.library.SpotifyLibraryScreen
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.SpotifyCategoryScreen
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.getCategoryColor
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.sheets.ArtistAboutSheet
import com.quackify.compose.sheets.DiscographySheet
import com.quackify.compose.share.Share
import com.quackify.compose.sheets.CreditsSheet
import com.quackify.compose.sheets.AddToPlaylistSheet
import com.quackify.compose.sheets.EntityOptions
import com.quackify.compose.theme.EncoreColors
import com.quackify.backend.api.Collection2
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.Credits
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.ShowDetail
import com.quackify.compose.screens.SpotifyNotification
import com.quackify.compose.util.rememberDominantColor
import com.quackify.compose.format.formatCount
import com.quackify.compose.home.HomeShelf
import kotlinx.coroutines.launch

@Composable
internal fun ShellScreenSlot(content: @Composable () -> Unit) {
    content()
}

@Composable
internal fun ShellOverlaySlot(content: @Composable () -> Unit) {
    content()
}


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
    expandPlayerSignal: Int = 0,
    isComposeUiEnabled: Boolean = true,
    onToggleComposeUi: (Boolean) -> Unit = {},
    onOpenQuackifySettings: (() -> Unit)? = null,
    homeQuickAccessItems: List<QuickAccessItem>? = null,
    homeRecommendations: List<RecommendationCard>? = null,
    homeShelves: List<HomeShelf>? = null,
    homeChips: List<HomeChip> = emptyList(),
    homeFacet: String = "",
    homeGreeting: String = "",
    onHomeFacetSelected: (String) -> Unit = {},
    libraryItems: List<LibraryItem>? = null,
    libraryLoading: Boolean = false,
    searchResults: List<TrackItem>? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    searchSuggestions: List<TrackItem> = emptyList(),
    searchSuggestionHits: List<SearchHit> = emptyList(),
    searchRecent: List<SearchHistoryItem> = emptyList(),
    browseCategories: List<BrowseCard>? = null,
    searchLoading: Boolean = false,
    searchFailed: Boolean = false,
    onRetrySearch: (() -> Unit)? = null,
    searchQuery: String = "",
    searchData: SearchResults? = null,
    onClearRecentSearches: (() -> Unit)? = null,
    onRemoveRecentSearch: ((String) -> Unit)? = null,
    onLoadSearchSection: (suspend (SearchKind, Int) -> List<SearchHit>)? = null,
    onToggleSaved: ((uri: String, currentlySaved: Boolean) -> Unit)? = null,
    searchSavedOverrides: Map<String, Boolean> = emptyMap(),
    homeLoading: Boolean = false,
    onLogout: (() -> Unit)? = null,
    settingsSwitches: SettingsSwitches = SettingsSwitches(),
    settingsLocks: SettingsLocks = SettingsLocks(),
    settingsProduct: String = "",
    settingsCountry: String = "",
    settingsLoading: Boolean = false,
    onToggleSetting: ((String, Boolean) -> Unit)? = null,
    onToggleFullArtwork: (Boolean) -> Unit = {},
    browsePage: BrowsePage? = null,
    categoryShelves: List<CategoryShelf>? = null,
    onOpenCategory: ((String) -> Unit)? = null,
    showDetail: ShowDetail? = null,
    showLoading: Boolean = false,
    episodeDetail: EpisodeDetail? = null,
    episodeLoading: Boolean = false,
    notifications: List<SpotifyNotification> = emptyList(),
    notificationsLoading: Boolean = false,
    notificationsFailed: Boolean = false,
    onRefreshWhatsNew: (() -> Unit)? = null,
    historyToday: List<TrackItem> = emptyList(),
    historyYesterday: List<TrackItem> = emptyList(),
    historyEarlier: List<TrackItem> = emptyList(),
    historyLoading: Boolean = false,
    onOpenHistory: (() -> Unit)? = null,
    savedAccounts: List<com.quackify.backend.core.auth.SpotifyAuth.Account> = emptyList(),
    activeAccountId: String = "",
    onSwitchAccount: ((String) -> Unit)? = null,
    onAddAccount: (() -> Unit)? = null,
    onRemoveAccount: ((String) -> Unit)? = null,
    folderItems: List<LibraryItem>? = null,
    folderLoading: Boolean = false,
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
    playlistDetail: CollectionDetail? = null,
    playlistLoading: Boolean = false,
    playlistHasMore: Boolean = false,
    playlistLoadingMore: Boolean = false,
    onLoadMorePlaylistTracks: (() -> Unit)? = null,
    onRetryPlaylist: (() -> Unit)? = null,
    playlistRecommendations: List<TrackItem> = emptyList(),
    recommendationsLoading: Boolean = false,
    onRefreshRecommendations: (() -> Unit)? = null,
    onAddRecommended: ((TrackItem) -> Unit)? = null,
    onSavePlaylistOrder: ((String, List<TrackItem>) -> Unit)? = null,
    albumTracks: List<TrackItem>? = null,
    albumDetail: AlbumDetail? = null,
    albumLoading: Boolean = false,
    albumFailed: Boolean = false,
    onRetryAlbum: (() -> Unit)? = null,
    nowPlayingArtistBio: String = "",
    nowPlayingMonthlyListeners: String = "",
    nowPlayingArtistImageUrl: String? = null,
    nowPlayingArtistFollowed: Boolean? = null,
    nowPlayingLyrics: List<TimeSyncedLyric> = emptyList(),
    canvasFilePath: String? = null,
    isCanvasAllowed: Boolean = true,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: ((Boolean) -> Unit)? = null,
    fullArtworkPlayer: Boolean = true,
    artistDetail: ArtistDetail? = null,
    artistLoading: Boolean = false,
    artistFailed: Boolean = false,
    onRetryArtist: (() -> Unit)? = null,
    trackCredits: Credits? = null,
    addToPlaylistChoices: List<PlaylistChoice>? = null,
    addTracksSongs: List<TrackItem> = emptyList(),
    addTracksEpisodes: List<TrackItem> = emptyList(),
    addTracksLiked: List<TrackItem> = emptyList(),
    addTracksRecent: List<TrackItem> = emptyList(),
    addTracksLoading: Boolean = false,
    onAddTracksOpen: (() -> Unit)? = null,
    onAddTrack: (suspend (String) -> Boolean)? = null,
    onShowCredits: ((String) -> Unit)? = null,
    onAddToPlaylist: ((TrackItem) -> Unit)? = null,
    onPickPlaylist: ((PlaylistChoice, TrackItem) -> Unit)? = null,
    onTogglePlaylist: ((PlaylistChoice, TrackItem, Boolean) -> Unit)? = null,
    onCreatePlaylistWith: ((TrackItem) -> Unit)? = null,
    onLoadPlaylistFolder: (suspend (trackUri: String, folderUri: String) -> List<PlaylistChoice>)? = null,
    onLoadTrackCount: (suspend (playlistUri: String) -> Int)? = null,
    onPlaylistDefaultName: (suspend () -> String)? = null,
    onCreatePlaylist: (suspend (name: String, description: String, cover: ByteArray?) -> String?)? = null,
    playlistSaved: Boolean? = null,
    playlistEditDraft: PlaylistEditDraft? = null,
    editablePlaylistUris: Set<String> = emptySet(),
    onEditPlaylist: ((uri: String) -> Unit)? = null,
    onEditPlaylistDetails: (suspend (uri: String, name: String, description: String, cover: ByteArray?, clearCover: Boolean) -> Boolean)? = null,
    onDeletePlaylist: (suspend (uri: String) -> Boolean)? = null,
    onDismissEditPlaylist: () -> Unit = {},
    onTogglePlaylistSaved: ((uri: String, saved: Boolean) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((playlistUri: String, trackUri: String) -> Unit)? = null,
    onRemoveLibraryItem: ((uri: String) -> Unit)? = null,
    onCreateFolder: (suspend (name: String) -> String?)? = null,
    onRenameFolder: ((uri: String, name: String) -> Unit)? = null,
    onDeleteFolder: ((uri: String) -> Unit)? = null,
    onMoveToFolder: ((playlistUri: String, folderUri: String) -> Unit)? = null,
    artistTracks: List<PopularTrack>? = null,
    artistReleases: List<ArtistRelease>? = null,
    artistMonthlyListeners: String? = null,
    /** null while the library contains-check for the open artist is loading. */
    artistFollowed: Boolean? = null,
    onArtistFollowToggle: ((artistUri: String, follow: Boolean) -> Unit)? = null,
    /** Resolves the artist's radio playlist uri (null when it cannot be built). */
    onArtistRadio: (suspend (artistUri: String) -> String?)? = null,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = ""
) {
    val flags = remember { ShellUiFlags() }
    val active = remember { ShellActiveEntity() }
    val navState = remember { ShellNavState() }
    val local = remember { ShellLocalState() }
    val searchQueryNow = searchQuery
    val navController = ShellNavController(
        nav = navState,
        active = active,
        flags = flags,
        searchQuery = { searchQueryNow },
        onSearchQueryChange = onSearchQueryChange,
        playerListener = playerListener,
        onLoadPlaylist = onLoadPlaylist,
        onLoadAlbum = onLoadAlbum,
        onLoadArtist = onLoadArtist,
        onOpenShow = onOpenShow,
        onOpenEpisode = onOpenEpisode,
        onAddTracksClosed = { onRetryPlaylist?.invoke() },
        libraryItems = libraryItems,
        searchData = searchData,
        nowPlayingArtistImageUrl = nowPlayingArtistImageUrl,
        currentArtist = { playbackState?.artist ?: local.artist },
    )
    var currentScreen by navController.nav::currentScreen
    var selectedNavIndex by navController.nav::selectedNavIndex
    var isPlayerExpanded by navController.nav::isPlayerExpanded

    LaunchedEffect(expandPlayerSignal) {
        if (expandPlayerSignal > 0) isPlayerExpanded = true
    }

    var trackTasteExcluded by local::trackTasteExcluded
    val tasteExclusionCache = remember { mutableMapOf<String, Boolean>() }
    var currentSortOption by local::sortOption
    var currentSortDirection by local::sortDirection
    var playbackOrigin by local::playbackOrigin
    var libraryItemOptions by local::libraryItemOptions
    var activeFolderOptionsItem by local::activeFolderOptionsItem
    var folderDraft by local::folderDraft
    var playlistCreateName by local::playlistCreateName
    var creditsTrack by local::creditsTrack
    var addTrackTarget by local::addTrackTarget
    var activeSleepTimer by local::sleepTimer
    val selectedTrackForOptions = local.trackForOptions

    val shellScope = rememberCoroutineScope()
    val navBackStack = navController.nav.backStack
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val actions = ShellActions(
        flags = flags,
        local = local,
        navController = navController,
        playerListener = playerListener,
        playlistTracks = playlistTracks,
        albumTracks = albumTracks,
        artistTracks = artistTracks,
        searchQuery = { searchQueryNow },
        clipboard = clipboard,
    )

    val pushCurrentState: () -> Unit = { navController.pushCurrentState() }
    val handleBack: () -> Unit = { navController.handleBack() }

    LaunchedEffect(flags.isTrackOptionsOpen, selectedTrackForOptions?.uri) {
        val uri = selectedTrackForOptions?.uri.orEmpty()
        if (!flags.isTrackOptionsOpen || !uri.startsWith("spotify:track:")) {
            trackTasteExcluded = null
            return@LaunchedEffect
        }
        trackTasteExcluded = tasteExclusionCache[uri]
        if (trackTasteExcluded == null) {
            val excluded = Collection2.excludedFromTasteProfile(context, uri)
            if (uri == selectedTrackForOptions?.uri) {
                tasteExclusionCache[uri] = excluded
                trackTasteExcluded = excluded
            }
        }
    }

    val handleOpenSettings: () -> Unit = {
        if (onOpenQuackifySettings != null) {
            onOpenQuackifySettings()
        } else {
            pushCurrentState()
            currentScreen = ShellScreen.SETTINGS
        }
    }

    var localIsPlaying by local::isPlaying
    var localTrackTitle by local::trackTitle
    var localArtist by local::artist
    var localProgress by local::progress
    var localPositionMs by local::positionMs
    val localDurationMs = 0L
    var localIsLiked by local::isLiked
    var localShuffleMode by local::shuffleMode
    var localRepeatMode by local::repeatMode
    var localDevice by local::device

    val currentTrackTitle = playbackState?.title ?: localTrackTitle
    val currentArtist = playbackState?.artist ?: localArtist
    val isPlaying = playbackState?.isPlaying ?: localIsPlaying
    val isBuffering = playbackState?.isBuffering ?: false
    val currentProgress = playbackState?.progress ?: localProgress
    val currentPositionMs = playbackState?.positionMs ?: localPositionMs
    val durationMs = if ((playbackState?.durationMs ?: 0L) > 0L) playbackState!!.durationMs else localDurationMs
    val isLiked = playbackState?.isLiked ?: localIsLiked
    val currentShuffleMode = playbackState?.shuffleMode ?: localShuffleMode
    val currentRepeatMode = playbackState?.repeatMode ?: localRepeatMode
    val connectedDevice = playbackState?.connectedDevice ?: localDevice
    val currentCoverUrl = playbackState?.coverUrl ?: ""

    val playerIdentity = playerIdentityOf(
        title = currentTrackTitle,
        artist = currentArtist,
        trackUri = playbackState?.trackUri,
        localTitle = localTrackTitle,
    )

    val currentQueueRow = remember(playbackState?.trackUri, playbackState?.queue, playbackState?.queueFromContext) {
        (playbackState?.queue.orEmpty() + playbackState?.queueFromContext.orEmpty())
            .firstOrNull { it.uri == playbackState?.trackUri }
    }
    val resolvedAlbumUri = playbackState?.albumUri?.takeIf { it.isNotBlank() }
        ?: currentQueueRow?.albumUri.orEmpty()
    val resolvedArtistUri = playbackState?.artistUri?.takeIf { it.isNotBlank() }
        ?: currentQueueRow?.artistUri.orEmpty()

    val openAlbum: (String, String, String) -> Unit = { title, artist, uri ->
        navController.openAlbum(context, title, artist, uri)
    }

    val openShow: (String) -> Unit = { uri -> navController.openShow(uri) }

    val openPlaylist: (String, String) -> Unit = { title, uri ->
        navController.openPlaylist(context, title, uri)
    }

    val openEpisode: (String) -> Unit = { uri -> navController.openEpisode(uri) }

    val handleTrackPlay: (TrackItem) -> Unit = { track -> actions.onTrackPlay(track) }

    val handlePlayPause: () -> Unit = { actions.playPause() }

    val handleTrackOptions: (TrackItem) -> Unit = { track -> actions.openTrackOptions(track) }

    val handlePodcastOpen: (String, String) -> Unit = { _, uri ->
        if (uri.isNotEmpty()) openShow(uri)
    }

    val openProfileDrawer: () -> Unit = { navController.openProfileDrawer() }

    val openArtist: (String, String) -> Unit = { artist, uri ->
        navController.openArtist(context, artist, uri)
    }

    val handleSkipNext: () -> Unit = { actions.skipNext() }

    val handleSkipPrevious: () -> Unit = { actions.skipPrevious() }

    val navItems = remember {
        listOf(
            NavItem("Home", R.drawable.encore_icon_home_24, R.drawable.encore_icon_home_active_24),
            NavItem("Search", R.drawable.encore_icon_search_24, R.drawable.encore_icon_search_active_24),
            NavItem("Your Library", R.drawable.encore_icon_your_library_24, R.drawable.encore_icon_your_library_active_24)
        )
    }

    val searchFieldFocusRequester = remember { FocusRequester() }

    BackHandler(
        enabled = isPlayerExpanded ||
                flags.isProfileDrawerOpen ||
                flags.isAccountsSheetOpen ||
                flags.isTrackOptionsOpen ||
                flags.isPlaylistOptionsOpen ||
                flags.isAlbumOptionsOpen ||
                flags.isArtistOptionsOpen ||
                flags.isSortSheetOpen ||
                flags.isCreateSheetOpen ||
                flags.isCreatePlaylistOpen ||
                playlistEditDraft != null ||
                libraryItemOptions != null ||
                folderDraft != null ||
                navBackStack.isNotEmpty() ||
                currentScreen != ShellScreen.HOME
    ) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (flags.isProfileDrawerOpen) {
            flags.isProfileDrawerOpen = false
        } else if (flags.isAccountsSheetOpen) {
            flags.isAccountsSheetOpen = false
        } else if (flags.isTrackOptionsOpen) {
            flags.isTrackOptionsOpen = false
        } else if (flags.isPlaylistOptionsOpen) {
            flags.isPlaylistOptionsOpen = false
        } else if (flags.isAlbumOptionsOpen) {
            flags.isAlbumOptionsOpen = false
        } else if (flags.isArtistOptionsOpen) {
            flags.isArtistOptionsOpen = false
        } else if (flags.isSortSheetOpen) {
            flags.isSortSheetOpen = false
        } else if (flags.isCreateSheetOpen) {
            flags.isCreateSheetOpen = false
        } else if (flags.isCreatePlaylistOpen) {
            flags.isCreatePlaylistOpen = false
        } else if (playlistEditDraft != null) {
            onDismissEditPlaylist()
        } else if (libraryItemOptions != null) {
            libraryItemOptions = null
        } else if (folderDraft != null) {
            folderDraft = null
        } else {
            handleBack()
        }
    }

    val copyEntityLink: (String) -> Unit = { uri -> actions.copyEntityLink(uri) }

    val shareEntity: (String) -> Unit = { uri -> actions.shareEntity(context, uri) }

    val libraryFolderChoices = remember(libraryItems) {
        libraryItems.orEmpty()
            .filter { it.type == LibraryItemType.FOLDER }
            .map { it.uri to it.title }
    }

    val handleLibraryLongPress: (LibraryItem) -> Unit = { item -> actions.libraryLongPress(item) }

    val handleFolderClick: (LibraryItem) -> Unit = { item ->
        navController.openFolder(item, onOpenFolder)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.PureBlack)
    ) {
        val playlistLoadFailed = !playlistLoading && playlistTracks == null
        ShellScreenSlot {
        when (currentScreen) {
            ShellScreen.HOME -> ShellHomePane(
                quickAccessItems = homeQuickAccessItems,
                recommendations = homeRecommendations,
                shelves = homeShelves,
                chips = homeChips,
                selectedFacet = homeFacet,
                greeting = homeGreeting,
                loading = homeLoading,
                libraryItems = libraryItems,
                notifications = notifications,
                userDisplayName = userDisplayName,
                userAvatarUrl = userAvatarUrl,
                onFacetSelected = onHomeFacetSelected,
                onOpenNotifications = {
                    pushCurrentState()
                    currentScreen = ShellScreen.NOTIFICATIONS
                },
                onOpenHistory = {
                    pushCurrentState()
                    currentScreen = ShellScreen.HISTORY
                    onOpenHistory?.invoke()
                },
                onOpenSettings = handleOpenSettings,
                onOpenProfile = openProfileDrawer,
                onArtistClick = openArtist,
                onCoverResolved = { cover ->
                    active.playlistCoverUrl = cover
                    active.albumCoverUrl = cover
                },
                onPlayTrackShortcut = { uri, title -> actions.playUriShortcut(uri, title) },
                onOpenAlbum = openAlbum,
                onOpenShow = openShow,
                onOpenEpisode = openEpisode,
                onOpenPlaylist = openPlaylist,
            )
            ShellScreen.SEARCH -> ShellSearchPane(
                searchResults = searchResults,
                searchData = searchData,
                searchRecent = searchRecent,
                searchSuggestions = searchSuggestions,
                searchSuggestionHits = searchSuggestionHits,
                searchLoading = searchLoading,
                searchFailed = searchFailed,
                searchSavedOverrides = searchSavedOverrides,
                browseCategories = browseCategories,
                userDisplayName = userDisplayName,
                userAvatarUrl = userAvatarUrl,
                currentTrackTitle = currentTrackTitle,
                currentTrackUri = playbackState?.trackUri.orEmpty(),
                isPlaying = isPlaying,
                searchFieldFocusRequester = searchFieldFocusRequester,
                initialQuery = searchQuery,
                playerListener = playerListener,
                onSearchQueryChange = onSearchQueryChange,
                onRetrySearch = { onRetrySearch?.invoke() },
                onClearRecentSearches = { onClearRecentSearches?.invoke() },
                onRemoveRecentSearch = { item -> onRemoveRecentSearch?.invoke(item.title) },
                onToggleSaved = { uri, currentlySaved -> onToggleSaved?.invoke(uri, currentlySaved) },
                onLoadSearchSection = onLoadSearchSection,
                onCategoryClick = { category ->
                    pushCurrentState()
                    active.categoryTitle = category.title
                    active.categoryColor = category.color
                    if (category.uri.startsWith("spotify:page:")) {
                        currentScreen = ShellScreen.BROWSE
                        onOpenBrowse?.invoke(category.uri)
                    } else {
                        currentScreen = ShellScreen.CATEGORY
                        onOpenCategory?.invoke(category.title)
                    }
                },
                onAlbumClick = openAlbum,
                onArtistClick = openArtist,
                onPodcastClick = handlePodcastOpen,
                onOpenEpisode = openEpisode,
                onOpenShow = openShow,
                onOpenPlaylist = openPlaylist,
                onTrackSelect = handleTrackPlay,
                onTrackOptionsClick = handleTrackOptions,
                onProfileClick = openProfileDrawer,
            )
            ShellScreen.LIBRARY -> {
                SpotifyLibraryScreen(
                    onItemClick = { itemTitle, itemUri ->
                        val foundItem = libraryItems?.find { it.uri == itemUri || it.title == itemTitle }
                        active.playlistCoverUrl = foundItem?.imageUrl
                        openPlaylist(itemTitle, itemUri)
                    },
                    onItemLongPress = handleLibraryLongPress,
                    onFolderClick = handleFolderClick,
                    onAlbumClick = openAlbum,
                    onPodcastClick = handlePodcastOpen,
                    onArtistClick = openArtist,
                    items = libraryItems,
                    loading = libraryLoading,
                    userDisplayName = userDisplayName,
                    userAvatarUrl = userAvatarUrl,
                    onProfileClick = openProfileDrawer,
                    onPlusClick = { flags.isCreateSheetOpen = true },
                    onSortClick = { flags.isSortSheetOpen = true },
                    currentSort = currentSortOption,
                    sortDirection = currentSortDirection
                )
            }
            ShellScreen.PLAYLIST -> ShellPlaylistPane(
                activePlaylistTitle = active.playlistTitle,
                activePlaylistUri = active.playlistUri,
                activePlaylistCoverUrl = active.playlistCoverUrl,
                playlistDetail = playlistDetail,
                playlistTracks = playlistTracks,
                playlistLoading = playlistLoading,
                playlistHasMore = playlistHasMore,
                playlistLoadingMore = playlistLoadingMore,
                onLoadMorePlaylistTracks = onLoadMorePlaylistTracks,
                playlistLoadFailed = playlistLoadFailed,
                onRetryPlaylist = onRetryPlaylist,
                currentTrackTitle = currentTrackTitle,
                isPlaying = isPlaying,
                userDisplayName = userDisplayName,
                playerListener = playerListener,
                playlistSaved = playlistSaved,
                playlistRecommendations = playlistRecommendations,
                recommendationsLoading = recommendationsLoading,
                onRefreshRecommendations = onRefreshRecommendations,
                onAddRecommended = onAddRecommended,
                onSavePlaylistOrder = onSavePlaylistOrder,
                onEditPlaylistDetails = onEditPlaylistDetails,
                onDeletePlaylist = { uri ->
                    shellScope.launch {
                        val ok = onDeletePlaylist?.invoke(uri) ?: false
                        if (ok) handleBack()
                    }
                },
                onTogglePlaylistSaved = onTogglePlaylistSaved,
                handleBack = handleBack,
                handleTrackOptions = handleTrackOptions,
                shareEntity = shareEntity,
                onLocalPlayPause = { local.isPlaying = !local.isPlaying },
                onLocalTrack = { track -> actions.localPlay(track) },
                onPlaylistOptions = { flags.isPlaylistOptionsOpen = true },
                onEditModeChange = { isEditing -> flags.isPlaylistEditMode = isEditing },
                onChangeTitle = { flags.isRenamePlaylistDialogOpen = true },
                onAddTracksClick = {
                    navController.openAddTracks()
                    onAddTracksOpen?.invoke()
                },
                onEditCoverClick = {
                    val uri = active.playlistUri
                    if (uri.startsWith("spotify:playlist:") && playlistDetail?.editable == true) {
                        onEditPlaylist?.invoke(uri)
                    } else {
                        flags.isPlaylistOptionsOpen = true
                    }
                },
                onPlaybackOrigin = { playbackOrigin = it }
            )
            ShellScreen.ALBUM -> ShellAlbumPane(
                activeAlbumTitle = active.albumTitle,
                activeAlbumArtist = active.albumArtist,
                activeAlbumUri = active.albumUri,
                activeAlbumCoverUrl = active.albumCoverUrl,
                activeAlbumYear = active.albumYear,
                activeAlbumReleaseDate = active.albumReleaseDate,
                activeAlbumColor = active.albumColor,
                albumDetail = albumDetail,
                albumTracks = albumTracks,
                albumLoading = albumLoading,
                albumFailed = albumFailed,
                onRetryAlbum = onRetryAlbum,
                currentTrackTitle = currentTrackTitle,
                isPlaying = isPlaying,
                playerListener = playerListener,
                handleBack = handleBack,
                handlePlayPause = handlePlayPause,
                handleTrackOptions = handleTrackOptions,
                openArtist = openArtist,
                openAlbum = openAlbum,
                onAlbumOptions = { flags.isAlbumOptionsOpen = true },
                onPlaybackOrigin = { playbackOrigin = it }
            )
            ShellScreen.ARTIST -> ShellArtistPane(
                artistName = active.artistName,
                artistAvatarUrl = active.artistAvatarUrl,
                artistUri = active.artistUri,
                artistDetail = artistDetail,
                artistTracks = artistTracks,
                artistReleases = artistReleases,
                artistMonthlyListeners = artistMonthlyListeners,
                artistLoading = artistLoading,
                artistFailed = artistFailed,
                onRetryArtist = onRetryArtist,
                artistFollowed = artistFollowed,
                currentTrackTitle = currentTrackTitle,
                isPlaying = isPlaying,
                onBackClick = handleBack,
                onPlayPauseClick = handlePlayPause,
                onTrackSelect = handleTrackPlay,
                onTrackOptionsClick = handleTrackOptions,
                onFollowToggle = { uri, follow ->
                    onArtistFollowToggle?.invoke(uri, follow)
                },
                onReleaseClick = { release ->
                    openAlbum(release.title, active.artistName, release.uri)
                },
                onAboutClick = { flags.isArtistAboutOpen = true },
                onDiscographyClick = { flags.isDiscographyOpen = true },
                onOptionsClick = { flags.isArtistOptionsOpen = true }
            )
            ShellScreen.CATEGORY -> {
                SpotifyCategoryScreen(
                    categoryTitle = active.categoryTitle,
                    categoryColor = active.categoryColor,
                    onBackClick = handleBack,
                    onPlaylistClick = { title, uri ->
                        if (uri.startsWith("spotify:show:")) {
                            openShow(uri)
                            return@SpotifyCategoryScreen
                        }
                        val foundItem = homeQuickAccessItems?.find { it.uri == uri || it.title == title }
                        val foundRec = homeRecommendations?.find { it.uri == uri || it.title == title }
                        val foundLib = libraryItems?.find { it.uri == uri || it.title == title }
                        val cover = foundItem?.imageUrl ?: foundRec?.imageUrl ?: foundLib?.imageUrl
                        active.playlistCoverUrl = cover
                        openPlaylist(title, uri)
                    },
                    onAlbumClick = { title, artist, uri ->
                        openAlbum(title, artist, uri)
                    },
                    onArtistClick = { artist, uri ->
                        openArtist(artist, uri)
                    },
                    onSubcategoryClick = { subcategory ->
                        pushCurrentState()
                        active.categoryTitle = subcategory
                        active.categoryColor = getCategoryColor(subcategory)
                        currentScreen = ShellScreen.CATEGORY
                    },
                    onTrackSelect = handleTrackPlay,
                    shelves = categoryShelves
                )
            }
            ShellScreen.SETTINGS -> ShellSettingsPane(
                onBackClick = handleBack,
                onLogout = { onLogout?.invoke() },
                switches = settingsSwitches,
                locks = settingsLocks,
                product = settingsProduct,
                country = settingsCountry,
                loading = settingsLoading,
                onToggleSetting = onToggleSetting,
                onToggleFullArtwork = onToggleFullArtwork,
                onOpenLogViewer = {
                    pushCurrentState()
                    currentScreen = ShellScreen.LOGS
                }
            )
            ShellScreen.LOGS -> ShellLogsPane(
                onBackClick = handleBack
            )
            ShellScreen.NOTIFICATIONS -> ShellNotificationsPane(
                onBackClick = handleBack,
                notifications = notifications,
                loading = notificationsLoading,
                loadFailed = notificationsFailed,
                onRetry = { onRefreshWhatsNew?.invoke() },
                onPlay = { item ->
                    when {
                        item.uri.startsWith("spotify:album:") -> openAlbum(item.title, "", item.uri)
                        item.uri.startsWith("spotify:episode:") -> {
                            pushCurrentState()
                            active.episodeUri = item.uri
                            currentScreen = ShellScreen.EPISODE
                            onOpenEpisode?.invoke(item.uri)
                        }
                        item.uri.isNotEmpty() -> {
                            pushCurrentState()
                            active.playlistTitle = item.title
                            active.playlistUri = item.uri
                            currentScreen = ShellScreen.PLAYLIST
                            if (onLoadPlaylist != null) onLoadPlaylist?.invoke(item.uri) else playerListener?.onLoadPlaylist(item.uri)
                        }
                    }
                }
            )
            ShellScreen.HISTORY -> ShellHistoryPane(
                onBackClick = handleBack,
                onTrackSelect = { track -> actions.onTrackPlay(track) },
                onTrackOptionsClick = { track -> actions.openTrackOptions(track) },
                todayTracks = historyToday,
                yesterdayTracks = historyYesterday,
                earlierTracks = historyEarlier,
                loading = historyLoading,
                currentTrackTitle = currentTrackTitle,
                isPlaying = isPlaying
            )
            ShellScreen.ADD_TRACKS -> ShellAddTracksPane(
                targetTitle = active.playlistTitle,
                songs = addTracksSongs,
                episodes = addTracksEpisodes,
                recent = addTracksRecent,
                liked = addTracksLiked,
                loading = addTracksLoading,
                onBack = handleBack,
                onAdd = { uri -> onAddTrack?.invoke(uri) ?: false }
            )
            ShellScreen.BROWSE -> ShellBrowsePane(
                page = browsePage,
                fallbackTitle = active.categoryTitle,
                onBack = handleBack,
                onOpenBrowse = { uri, title ->
                    active.categoryTitle = title
                    onOpenBrowse?.invoke(uri)
                },
                onOpenShow = { uri -> onOpenShow?.invoke(uri) },
                onOpenAlbum = { title, uri -> openAlbum(title, "", uri) },
                onOpenPlaylist = openPlaylist,
                onLoadMore = { onLoadMoreBrowse?.invoke() }
            )

            ShellScreen.SHOW -> ShellShowPane(
                show = showDetail,
                loading = showLoading,
                retryUri = active.showUri,
                isPlaying = isPlaying,
                currentTrackTitle = currentTrackTitle,
                onBack = handleBack,
                onRetry = { uri -> onOpenShow?.invoke(uri) },
                onEpisodeClick = openEpisode,
                onPlayPause = handlePlayPause,
                onLoadMore = { onLoadMoreEpisodes?.invoke() }
            )

            ShellScreen.EPISODE -> ShellEpisodePane(
                episode = episodeDetail,
                loading = episodeLoading,
                retryUri = active.episodeUri,
                isPlaying = isPlaying,
                onBack = handleBack,
                onRetry = { uri -> onOpenEpisode?.invoke(uri) },
                onPlayPause = handlePlayPause,
                onShowClick = { uri -> openShow(uri) }
            )

            ShellScreen.FOLDER -> ShellFolderPane(
                items = folderItems,
                loading = folderLoading,
                title = active.folderTitle.ifBlank { "Your Library" },
                userDisplayName = userDisplayName,
                userAvatarUrl = userAvatarUrl,
                onItemClick = { itemTitle, itemUri ->
                    val foundItem = folderItems?.find { it.uri == itemUri || it.title == itemTitle }
                    active.playlistCoverUrl = foundItem?.imageUrl
                    pushCurrentState()
                    active.playlistTitle = itemTitle
                    active.playlistUri = itemUri
                    currentScreen = ShellScreen.PLAYLIST
                    if (itemUri.isNotEmpty()) if (onLoadPlaylist != null) onLoadPlaylist?.invoke(itemUri) else playerListener?.onLoadPlaylist(itemUri)
                },
                onItemLongPress = handleLibraryLongPress,
                onFolderClick = handleFolderClick,
                onProfileClick = openProfileDrawer,
                onPlusClick = { flags.isCreateSheetOpen = true },
                onSortClick = { flags.isSortSheetOpen = true },
                currentSort = currentSortOption,
                sortDirection = currentSortDirection,
                onBackClick = { handleBack() },
                onFolderOptionsClick = {
                    local.activeFolderOptionsNested = false
                    activeFolderOptionsItem = LibraryItem(
                        title = active.folderTitle,
                        subtitle = "${folderItems?.size ?: 0} playlists",
                        type = LibraryItemType.FOLDER,
                        uri = active.folderUri
                    )
                },
                onPlayClick = {
                    val firstItem = folderItems?.firstOrNull()
                    if (firstItem != null && firstItem.type == LibraryItemType.PLAYLIST) {
                        openPlaylist(firstItem.title, firstItem.uri)
                    }
                }
            )
        }
        }

        val nowPlayingDominantColor by rememberDominantColor(
            imageUrl = currentCoverUrl,
            fallbackSeed = currentTrackTitle + currentArtist,
            defaultColor = Color(0xFF432371)
        )

        val remoteConnected = playbackState?.controllingRemote == true && !playbackState.activeDeviceName.isNullOrBlank()
        val effectiveConnectedDevice = if (remoteConnected) playbackState?.activeDeviceName else null
        val activeDevice = playbackState?.devices?.firstOrNull { it.active || it.id == playbackState.activeDeviceId }
            ?: playbackState?.devices?.firstOrNull { it.name == effectiveConnectedDevice }
        val activeDeviceKind = activeDevice?.kind

        if (!flags.isPlaylistEditMode && currentScreen != ShellScreen.ADD_TRACKS) {
            ShellBottomBar(
                title = playerIdentity.title,
                artist = playerIdentity.artist,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                progress = currentProgress,
                isLiked = isLiked,
                coverUrl = currentCoverUrl,
                coverColor = nowPlayingDominantColor,
                connectedDevice = effectiveConnectedDevice,
                connectedDeviceKind = activeDeviceKind,
                navItems = navItems,
                currentNavIndex = selectedNavIndex,
                onNavSelect = { index ->
                    selectedNavIndex = index
                    when (index) {
                        0 -> if (currentScreen != ShellScreen.HOME) { navBackStack.clear(); currentScreen = ShellScreen.HOME }
                        1 -> if (currentScreen != ShellScreen.SEARCH) {
                            navBackStack.clear(); currentScreen = ShellScreen.SEARCH
                        } else {
                            runCatching { searchFieldFocusRequester.requestFocus() }
                        }
                        2 -> if (currentScreen != ShellScreen.LIBRARY) { navBackStack.clear(); currentScreen = ShellScreen.LIBRARY }
                    }
                },
                onSearchDoubleTap = {
                    runCatching { searchFieldFocusRequester.requestFocus() }
                },
                onPlayPause = handlePlayPause,
                onLike = {
                    val playingUri = playbackState?.trackUri.orEmpty()
                    if (!isLiked) {
                        if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = true
                    }
                    val playingTrack = TrackItem(
                        title = currentTrackTitle,
                        artist = currentArtist,
                        uri = playingUri,
                        imageUrl = currentCoverUrl.takeIf { it.isNotBlank() },
                        saved = true
                    )
                    local.addTrackTarget = playingTrack
                    onAddToPlaylist?.invoke(playingTrack)
                    flags.isAddToPlaylistOpen = true
                },
                onDeviceClick = { flags.isDeviceSheetOpen = true },
                onBarClick = { isPlayerExpanded = true },
                onSkipNext = handleSkipNext,
                onSkipPrevious = handleSkipPrevious,
                hasTrack = playerIdentity.trackLoaded,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        val (contextHeader, contextSub) = nowPlayingContext(
            trackLoaded = playerIdentity.trackLoaded,
            origin = playbackOrigin,
            screen = currentScreen,
            playlistTitle = active.playlistTitle,
            albumTitle = active.albumTitle,
            artistName = active.artistName,
            searchQuery = searchQuery,
        )
        ShellNowPlaying(
            visible = isPlayerExpanded,
            title = playerIdentity.title,
            artist = playerIdentity.artist,
            contextHeader = contextHeader,
            contextSub = contextSub,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            progress = currentProgress,
            durationMs = durationMs,
            positionMs = currentPositionMs,
            dominantColor = nowPlayingDominantColor,
            isLiked = isLiked,
            isExplicit = playbackState?.explicit ?: false,
            monthlyListeners = nowPlayingMonthlyListeners,
            artistBio = nowPlayingArtistBio,
            artistImageUrl = nowPlayingArtistImageUrl,
            isArtistFollowing = nowPlayingArtistFollowed == true,
            onArtistFollowToggle = { follow ->
                if (resolvedArtistUri.startsWith("spotify:artist:")) {
                    onArtistFollowToggle?.invoke(resolvedArtistUri, follow)
                }
            },
            lyrics = nowPlayingLyrics,
            canvasFilePath = canvasFilePath,
            isCanvasAllowed = isCanvasAllowed,
            isCanvasEnabled = isCanvasEnabled,
            fullArtwork = fullArtworkPlayer,
            onCanvasToggle = { on -> onCanvasToggle?.invoke(on) },
            shuffleMode = currentShuffleMode,
            repeatMode = currentRepeatMode,
            connectedDevice = connectedDevice,
            coverUrl = currentCoverUrl,
            onCollapse = { isPlayerExpanded = false },
            onPlayPause = handlePlayPause,
            onSkipNext = handleSkipNext,
            onSkipPrevious = handleSkipPrevious,
            onLike = {
                val playingTrack = TrackItem(
                    title = currentTrackTitle,
                    artist = currentArtist,
                    uri = playbackState?.trackUri.orEmpty(),
                    imageUrl = currentCoverUrl.takeIf { it.isNotBlank() },
                    saved = isLiked
                )
                local.addTrackTarget = playingTrack
                onAddToPlaylist?.invoke(playingTrack)
                flags.isAddToPlaylistOpen = true
            },
            onShuffle = { if (playerListener != null) playerListener.onShuffleToggle() else localShuffleMode = localShuffleMode.next() },
            onRepeat = { if (playerListener != null) playerListener.onRepeatToggle() else localRepeatMode = localRepeatMode.next() },
            onSeek = { p -> if (playerListener != null) playerListener.onSeek((p * durationMs).toLong()) else { localProgress = p; localPositionMs = (p * durationMs).toLong() } },
            onMore = {
                local.trackForOptions = TrackItem(
                    title = currentTrackTitle,
                    artist = currentArtist,
                    isExplicit = playbackState?.explicit ?: false,
                    uri = playbackState?.trackUri.orEmpty(),
                    imageUrl = currentCoverUrl.takeIf { it.isNotBlank() },
                    saved = isLiked,
                    albumUri = resolvedAlbumUri,
                    artistUri = resolvedArtistUri,
                )
                flags.isTrackOptionsFromPlayer = true
                flags.isTrackOptionsOpen = true
            },
            onQueue = { flags.isQueueSheetOpen = true },
            onDevice = { flags.isDeviceSheetOpen = true },
            onShare = { playbackState?.trackUri?.takeIf { it.isNotBlank() }?.let { uri -> shareEntity(uri) } },
            onTitleClick = {
                isPlayerExpanded = false
                openAlbum(currentTrackTitle, currentArtist, resolvedAlbumUri)
            },
            onArtistClick = {
                isPlayerExpanded = false
                openArtist(currentArtist, resolvedArtistUri)
            }
        )
        val isPlaylistUri = active.playlistUri.startsWith("spotify:playlist:")
        val playlistEntity = if (active.playlistUri.isBlank() && active.playlistTitle.isBlank()) null
        else EntityOptions(
            title = playlistDetail?.title?.takeIf { it.isNotBlank() } ?: active.playlistTitle,
            subtitle = if (isPlaylistUri)
                "Playlist • " + (playlistDetail?.creator?.takeIf { it.isNotBlank() } ?: userDisplayName)
            else "",
            imageUrl = active.playlistCoverUrl ?: playlistDetail?.imageUrl?.takeIf { it.isNotBlank() },
            uri = active.playlistUri,
        )

        ShellSheets(
            flags = SheetFlags(
                profileDrawer = flags.isProfileDrawerOpen,
                accounts = flags.isAccountsSheetOpen,
                trackOptions = flags.isTrackOptionsOpen,
                playlistOptions = flags.isPlaylistOptionsOpen,
                albumOptions = flags.isAlbumOptionsOpen,
                artistOptions = flags.isArtistOptionsOpen,
                sort = flags.isSortSheetOpen,
                create = flags.isCreateSheetOpen,
                device = flags.isDeviceSheetOpen,
                queue = flags.isQueueSheetOpen,
                sleepTimer = flags.isSleepTimerOpen
            ),
            selectedTrack = local.trackForOptions,
            devices = playbackState?.devices.orEmpty(),
            connectedDevice = connectedDevice,
            queueFromContext = playbackState?.queueFromContext.orEmpty(),
            canAddToQueue = local.trackForOptions?.uri
                ?.takeIf { it.isNotBlank() }
                ?.let { uri ->
                    uri != playbackState?.trackUri &&
                        playbackState?.queue.orEmpty().none { it.uri == uri }
                } ?: false,
            currentTrackTitle = currentTrackTitle,
            currentTrackArtist = currentArtist,
            currentTrackUri = playbackState?.trackUri.orEmpty(),
            currentTrackCoverUrl = currentCoverUrl.takeIf { it.isNotBlank() },
            playlistEntity = playlistEntity,
            playlistEditable = isPlaylistUri && playlistDetail?.editable == true,
            playlistSaved = if (isPlaylistUri) playlistSaved else null,
            albumTitle = active.albumTitle,
            albumArtist = active.albumArtist,
            albumCoverUrl = active.albumCoverUrl,
            albumUri = active.albumUri.ifBlank { albumDetail?.uri.orEmpty() },
            artistName = active.artistName,
            artistAvatarUrl = active.artistAvatarUrl
                ?: artistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                ?: artistDetail?.headerImageUrl?.takeIf { it.isNotBlank() },
            artistUri = active.artistUri,
            artistFollowing = artistFollowed == true,
            onArtistFollowToggle = {
                if (active.artistUri.startsWith("spotify:artist:")) {
                    onArtistFollowToggle?.invoke(active.artistUri, !(artistFollowed == true))
                }
            },
            onArtistRadio = {
                flags.isArtistOptionsOpen = false
                val artistUriNow = active.artistUri
                if (artistUriNow.startsWith("spotify:artist:")) {
                    shellScope.launch {
                        val radio = onArtistRadio?.invoke(artistUriNow)
                        if (radio != null && radio.startsWith("spotify:playlist:")) {
                            openPlaylist(active.artistName + " Radio", radio)
                        }
                    }
                }
            },
            sortOption = local.sortOption,
            sortDirection = local.sortDirection,
            sleepTimer = local.sleepTimer,
            userDisplayName = userDisplayName,
            userAvatarUrl = userAvatarUrl,
            savedAccounts = savedAccounts,
            activeAccountId = activeAccountId,
            isFolder = currentScreen == ShellScreen.FOLDER,
            canvasAvailable = flags.isTrackOptionsFromPlayer && isCanvasAllowed && canvasFilePath != null,
            isCanvasEnabled = isCanvasEnabled,
            onCanvasToggle = { onCanvasToggle?.invoke(!isCanvasEnabled) },
            onDismissProfile = { flags.isProfileDrawerOpen = false },
            onDismissAccounts = { flags.isAccountsSheetOpen = false },
            onSwitchAccount = { id -> onSwitchAccount?.invoke(id) },
            onAddAccount = { onAddAccount?.invoke() },
            onRemoveAccount = { id -> onRemoveAccount?.invoke(id) },
            onOpenAccounts = { flags.isAccountsSheetOpen = true },
            onDismissTrackOptions = { flags.isTrackOptionsOpen = false },
            onDismissPlaylistOptions = { flags.isPlaylistOptionsOpen = false },
            onDismissAlbumOptions = { flags.isAlbumOptionsOpen = false },
            onDismissArtistOptions = { flags.isArtistOptionsOpen = false },
            onDismissSort = { flags.isSortSheetOpen = false },
            onDismissCreate = { flags.isCreateSheetOpen = false },
            onDismissDevice = { flags.isDeviceSheetOpen = false },
            onDismissQueue = { flags.isQueueSheetOpen = false },
            onDismissSleepTimer = { flags.isSleepTimerOpen = false },
            onAddToPlaylist = {
                local.trackForOptions?.let { track ->
                    flags.isAddToPlaylistOpen = true
                    local.addTrackTarget = track
                    onAddToPlaylist?.invoke(track)
                }
            },
            onShowCredits = {
                local.trackForOptions?.let { track ->
                    flags.isCreditsOpen = true
                    local.creditsTrack = track
                    onShowCredits?.invoke(track.uri)
                }
            },
            onEditPlaylist = {
                if (isPlaylistUri && active.playlistUri.isNotEmpty()) onEditPlaylist?.invoke(active.playlistUri)
            },
            onTogglePlaylistSaved = {
                if (isPlaylistUri && active.playlistUri.isNotEmpty()) {
                    onTogglePlaylistSaved?.invoke(active.playlistUri, !(playlistSaved ?: false))
                }
            },
            onDeletePlaylist = {
                shellScope.launch {
                    val ok = onDeletePlaylist?.invoke(active.playlistUri) ?: false
                    if (ok) handleBack()
                }
            },
            onCopyLink = { uri -> copyEntityLink(uri) },
            onShare = { uri -> shareEntity(uri) },
            onWhatsNew = { pushCurrentState(); currentScreen = ShellScreen.NOTIFICATIONS },
            onHistory = {
                pushCurrentState()
                currentScreen = ShellScreen.HISTORY
                onOpenHistory?.invoke()
            },
            onSettings = handleOpenSettings,
            onLogout = { onLogout?.invoke() },
            onLikeToggle = { liked -> if (playerListener != null) playerListener.onLikeToggle() else local.isLiked = liked },
            onAddToQueue = { track -> playerListener?.onQueueAdd(track.uri) },
            onViewArtist = { artist, uri ->
                flags.isTrackOptionsOpen = false
                isPlayerExpanded = false
                openArtist(artist, uri)
            },
            onViewAlbum = { album, uri ->
                flags.isTrackOptionsOpen = false
                isPlayerExpanded = false
                openAlbum(album, local.trackForOptions?.artist ?: "", uri)
            },
            onSleepTimerClick = { flags.isSleepTimerOpen = true },
            onGoToArtist = { artist ->
                flags.isAlbumOptionsOpen = false
                isPlayerExpanded = false
                openArtist(artist, albumDetail?.artistUri.orEmpty())
            },            onSortSelected = { selected, direction -> local.sortOption = selected; local.sortDirection = direction },
            onCreatePlaylist = {
                shellScope.launch {
                    local.playlistCreateName = onPlaylistDefaultName?.invoke() ?: "My Playlist"
                    flags.isCreatePlaylistOpen = true
                }
            },
            onCreateFolder = { local.folderDraft = FolderDraft(mode = FolderMode.CREATE) },
            onSelectDevice = { id -> localDevice = id; flags.isDeviceSheetOpen = false; playerListener?.onSelectDevice(id) },
            onDeviceVisible = { visible -> playerListener?.onDeviceSheetVisible(visible) },
            onVolumeChange = { id, level -> playerListener?.onVolumeChange(id, level) },
            userQueue = playbackState?.queue.orEmpty(),
            trackTasteExcluded = trackTasteExcluded,
            onToggleTasteExclusion = toggle@{ exclude ->
                val track = local.trackForOptions
                val uri = track?.uri.orEmpty()
                if (!uri.startsWith("spotify:track:")) return@toggle
                val previous = trackTasteExcluded
                trackTasteExcluded = exclude
                tasteExclusionCache[uri] = exclude
                shellScope.launch {
                    val ok = Collection2.setExcludedFromTasteProfile(context, uri, exclude)
                    if (uri == local.trackForOptions?.uri) {
                        if (!ok) {
                            trackTasteExcluded = previous
                            if (previous != null) tasteExclusionCache[uri] = previous
                        }
                        Toast.makeText(
                            context,
                            when {
                                ok && exclude -> "Excluded from your taste profile"
                                ok -> "Included in your taste profile"
                                else -> "Couldn't update your taste profile"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            queueSourceContext = playbackState?.queueContextLabel?.takeIf { it.isNotBlank() }
                ?: playbackState?.contextLabel?.takeIf { it.isNotBlank() } ?: "",
            onQueueTrackSelect = { track ->
                if (playerListener != null) {
                    if (track.uri.isNotEmpty()) {
                        playerListener.onPlayUri(track.uri)
                    } else {
                        playerListener.onTrackSelect(track.title, track.artist)
                    }
                }
            },
            onRemoveFromQueue = { track ->
                val queue = playbackState?.queue.orEmpty()
                val context = playbackState?.queueFromContext.orEmpty()
                val remaining = queue.filterNot { it.uri == track.uri }.map { it.uri }
                if (remaining.size != queue.size) {
                    playerListener?.onQueueSet(remaining + context.map { it.uri })
                }
            },
            onClearQueue = {
                val context = playbackState?.queueFromContext.orEmpty()
                playerListener?.onQueueSet(context.map { it.uri })
            },
            onSleepTimerSelected = { option -> local.sleepTimer = option },
            onRemoveTrackFromPlaylist = if (currentScreen == ShellScreen.PLAYLIST && isPlaylistUri && playlistDetail?.editable == true && active.playlistUri.isNotBlank()) {
                {
                    val trackUri = local.trackForOptions?.uri.orEmpty()
                    val playlistUri = active.playlistUri
                    flags.isTrackOptionsOpen = false
                    if (trackUri.isNotBlank() && playlistUri.isNotBlank()) {
                        onRemoveTrackFromPlaylist?.invoke(playlistUri, trackUri)
                    }
                }
            } else null,
            onSongRadio = {
                val trackNow = local.trackForOptions
                flags.isTrackOptionsOpen = false
                isPlayerExpanded = false
                if (trackNow != null && trackNow.uri.startsWith("spotify:track:")) {
                    shellScope.launch {
                        val radio = onArtistRadio?.invoke(trackNow.uri)
                        if (radio != null && radio.startsWith("spotify:playlist:")) {
                            openPlaylist(trackNow.title + " Radio", radio)
                        }
                    }
                }
            },
            onAlbumRadio = {
                flags.isAlbumOptionsOpen = false
                isPlayerExpanded = false
                val albumUriNow = active.albumUri.ifBlank { albumDetail?.uri.orEmpty() }
                if (albumUriNow.startsWith("spotify:album:")) {
                    shellScope.launch {
                        val radio = onArtistRadio?.invoke(albumUriNow)
                        if (radio != null && radio.startsWith("spotify:playlist:")) {
                            openPlaylist(active.albumTitle + " Radio", radio)
                        }
                    }
                }
            },
            onAddToPlaylistFromAlbum = {
                flags.isAlbumOptionsOpen = false
                local.addTrackTarget = TrackItem(
                    title = active.albumTitle,
                    artist = active.albumArtist,
                    uri = active.albumUri.ifBlank { albumDetail?.uri.orEmpty() }
                )
                flags.isAddToPlaylistOpen = true
            }
        )

        if (flags.isArtistAboutOpen) {
            ArtistAboutSheet(
                isOpen = flags.isArtistAboutOpen,
                artistName = active.artistName,
                imageUrl = artistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                    ?: artistDetail?.headerImageUrl?.takeIf { it.isNotBlank() },
                monthlyListeners = artistDetail?.monthlyListeners?.takeIf { it > 0L }
                    ?.let { formatCount(it) + " monthly listeners" }.orEmpty(),
                followers = artistDetail?.followers?.takeIf { it > 0L }
                    ?.let { formatCount(it) + " followers" }.orEmpty(),
                worldRank = artistDetail?.worldRank ?: 0,
                biography = artistDetail?.biography.orEmpty(),
                topCities = artistDetail?.topCities.orEmpty(),
                externalLinks = artistDetail?.externalLinks.orEmpty(),
                gallery = artistDetail?.gallery.orEmpty(),
                onDismiss = { flags.isArtistAboutOpen = false },
                onLinkClick = { link -> Share.openUrl(context, link) }
            )
        }

        if (flags.isDiscographyOpen) {
            DiscographySheet(
                isOpen = flags.isDiscographyOpen,
                artistUri = active.artistUri,
                artistName = active.artistName,
                onDismiss = { flags.isDiscographyOpen = false },
                onOpenRelease = { uri, title -> openAlbum(title, active.artistName, uri) }
            )
        }

        if (flags.isAddToPlaylistOpen) {
            val target = local.addTrackTarget
            val targetIsLiked = if (target?.uri == playbackState?.trackUri) isLiked else (target?.saved == true)
            AddToPlaylistSheet(
                isOpen = true,
                trackTitle = target?.title.orEmpty(),
                isTargetLiked = targetIsLiked,
                choices = addToPlaylistChoices.orEmpty(),
                isLoading = addToPlaylistChoices == null,
                onPick = { choice ->
                    val target = local.addTrackTarget
                    if (target != null) {
                        if (target.uri.startsWith("spotify:album:")) {
                            val rows = albumTracks.orEmpty()
                            rows.forEach { row -> onPickPlaylist?.invoke(choice, row) }
                        } else {
                            onPickPlaylist?.invoke(choice, target)
                        }
                    }
                },
                onToggle = { choice, isAdding ->
                    val target = local.addTrackTarget
                    if (target != null) {
                        if (target.uri.startsWith("spotify:album:")) {
                            val rows = albumTracks.orEmpty()
                            rows.forEach { row ->
                                onTogglePlaylist?.invoke(choice, row, isAdding)
                                    ?: if (isAdding) onPickPlaylist?.invoke(choice, row) else Unit
                            }
                        } else {
                            onTogglePlaylist?.invoke(choice, target, isAdding)
                                ?: if (isAdding) onPickPlaylist?.invoke(choice, target) else Unit
                        }
                    }
                },
                onLoadFolder = onLoadPlaylistFolder?.let { loader ->
                    { folderUri -> loader(local.addTrackTarget?.uri.orEmpty(), folderUri) }
                },
                onLoadTrackCount = onLoadTrackCount,
                onCreateNew = {
                    local.addTrackTarget?.let { track -> onCreatePlaylistWith?.invoke(track) }
                    flags.isAddToPlaylistOpen = false
                },
                onDismiss = { flags.isAddToPlaylistOpen = false }
            )
        }

        if (flags.isCreditsOpen) {
            CreditsSheet(
                isOpen = true,
                title = local.creditsTrack?.title.orEmpty(),
                subtitle = local.creditsTrack?.artist.orEmpty(),
                imageUrl = local.creditsTrack?.imageUrl,
                credits = trackCredits,
                onDismiss = { flags.isCreditsOpen = false },
                onArtistClick = { uri, name -> openArtist(name, uri) }
            )
        }

        SpotifyCreateNameDialog(
            isOpen = flags.isCreatePlaylistOpen,
            title = "Give your playlist a name",
            initialName = local.playlistCreateName,
            confirmLabel = "Create",
            cancelLabel = "Cancel",
            onConfirm = { name ->
                flags.isCreatePlaylistOpen = false
                shellScope.launch {
                    val uri = onCreatePlaylist?.invoke(name, "", null)
                    if (!uri.isNullOrEmpty()) {
                        pushCurrentState()
                        active.playlistTitle = name
                        active.playlistUri = uri
                        currentScreen = ShellScreen.PLAYLIST
                        if (onLoadPlaylist != null) onLoadPlaylist?.invoke(uri) else playerListener?.onLoadPlaylist(uri)
                        if (active.folderUri.isNotBlank()) {
                            onMoveToFolder?.invoke(uri, active.folderUri)
                        }
                    }
                }
            },
            onDismiss = { flags.isCreatePlaylistOpen = false }
        )

        ShellPlaylistEditSheets(
            editDraft = playlistEditDraft,
            onEditSave = onEditPlaylistDetails,
            onDismissEdit = onDismissEditPlaylist,
            onDeletePlaylist = onDeletePlaylist,
            onDeleteAsk = { title, message, onConfirm ->
                local.deleteAsk = DeleteAsk(title, message, onConfirm)
            }
        )

        ShellEntitySheets(
            flags = flags,
            local = local,
            active = active,
            navController = navController,
            actions = actions,
            context = context,
            shellScope = shellScope,
            libraryFolderChoices = libraryFolderChoices,
            editablePlaylistUris = editablePlaylistUris,
            playlistDescription = playlistDetail?.description.orEmpty(),
            albumTracks = albumTracks,
            folderItems = folderItems,
            onEditPlaylist = onEditPlaylist,
            onRemoveLibraryItem = onRemoveLibraryItem,
            onDeletePlaylist = onDeletePlaylist,
            onMoveToFolder = onMoveToFolder,
            onCreateFolder = onCreateFolder,
            onRenameFolder = onRenameFolder,
            onDeleteFolder = onDeleteFolder,
            onEditPlaylistDetails = onEditPlaylistDetails,
            onCopyEntityLink = copyEntityLink,
            onShareEntity = shareEntity,
            playerListener = playerListener,
        )

    }
}
