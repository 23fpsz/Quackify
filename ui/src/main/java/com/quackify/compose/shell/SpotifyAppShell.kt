package com.quackify.compose.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.quackify.compose.R
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.DetailPageSkeleton
import com.quackify.compose.home.QuickAccessItem
import com.quackify.compose.home.RecommendationCard
import com.quackify.compose.home.SpotifyHomeScreen
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.library.SpotifyLibraryScreen
import com.quackify.compose.playlist.SpotifyPlaylistScreen
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.HistoryScreen
import com.quackify.compose.screens.NotificationsScreen
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.SettingsScreen
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.LogViewerScreen
import com.quackify.compose.screens.SpotifyAlbumScreen
import com.quackify.compose.screens.SpotifyArtistScreen
import com.quackify.compose.search.CategoryCard
import com.quackify.compose.screens.SpotifyCategoryScreen
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.getCategoryColor
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.search.SpotifySearchScreen
import com.quackify.compose.sheets.LibrarySortOption
import com.quackify.compose.sheets.SleepTimerOption
import com.quackify.compose.sheets.ArtistAboutSheet
import com.quackify.compose.sheets.DiscographySheet
import com.quackify.compose.sheets.CreditsSheet
import com.quackify.compose.sheets.AddToPlaylistSheet
import com.quackify.compose.sheets.EditPlaylistSheet
import com.quackify.compose.sheets.EntityOptions
import com.quackify.compose.sheets.EntityOptionsSheet
import com.quackify.compose.sheets.FolderSheet
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import androidx.compose.runtime.mutableStateListOf
import com.quackify.backend.model.ConnectDevice
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
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.ShowDetail
import com.quackify.compose.screens.SpotifyBrowseScreen
import com.quackify.compose.screens.SpotifyEpisodeScreen
import com.quackify.compose.screens.SpotifyShowScreen
import com.quackify.compose.screens.SpotifyNotification
import com.quackify.compose.util.rememberDominantColor
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.format.formatCount
import com.quackify.compose.home.HomeShelf
import com.quackify.compose.home.toArtistRelease
import kotlinx.coroutines.launch

enum class ShellScreen {
    HOME,
    SEARCH,
    LIBRARY,
    PLAYLIST,
    ALBUM,
    ARTIST,
    CATEGORY,
    SETTINGS,
    LOGS,
    NOTIFICATIONS,
    HISTORY,
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

data class PlaylistEditDraft(
    val uri: String,
    val name: String,
    val description: String,
    val coverUrl: String?,
    val canRemoveCover: Boolean,
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
    val albumUri: String = "",
    val albumCoverUrl: String? = null,
    val artistName: String = "",
    val artistUri: String = "",
    val artistAvatarUrl: String? = null,
    val showUri: String = ""
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
    val artistUri: String = "",
    val albumUri: String = "",
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
    expandPlayerSignal: Int = 0,
    isComposeUiEnabled: Boolean = true,
    onToggleComposeUi: (Boolean) -> Unit = {},
    onOpenQuackifySettings: (() -> Unit)? = null,
    homeQuickAccessItems: List<QuickAccessItem>? = null,
    homeRecommendations: List<RecommendationCard>? = null,
    homeShelves: List<HomeShelf>? = null,
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
    onToggleSaved: ((String) -> Unit)? = null,
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
    albumTracks: List<TrackItem>? = null,
    albumDetail: AlbumDetail? = null,
    nowPlayingArtistBio: String = "",
    nowPlayingMonthlyListeners: String = "",
    nowPlayingArtistImageUrl: String? = null,
    nowPlayingLyrics: List<TimeSyncedLyric> = emptyList(),
    canvasFilePath: String? = null,
    isCanvasAllowed: Boolean = true,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: ((Boolean) -> Unit)? = null,
    fullArtworkPlayer: Boolean = true,
    artistDetail: ArtistDetail? = null,
    artistLoading: Boolean = false,
    trackCredits: Credits? = null,
    addToPlaylistChoices: List<PlaylistChoice>? = null,
    onShowCredits: ((String) -> Unit)? = null,
    onAddToPlaylist: ((TrackItem) -> Unit)? = null,
    onPickPlaylist: ((PlaylistChoice, TrackItem) -> Unit)? = null,
    onCreatePlaylistWith: ((TrackItem) -> Unit)? = null,
    onPlaylistDefaultName: (suspend () -> String)? = null,
    onCreatePlaylist: (suspend (name: String, description: String, cover: ByteArray?) -> String?)? = null,
    playlistSaved: Boolean? = null,
    playlistPublished: Boolean? = null,
    playlistEditDraft: PlaylistEditDraft? = null,
    editablePlaylistUris: Set<String> = emptySet(),
    onEditPlaylist: ((uri: String) -> Unit)? = null,
    onEditPlaylistDetails: (suspend (uri: String, name: String, description: String, cover: ByteArray?, clearCover: Boolean) -> Boolean)? = null,
    onDeletePlaylist: (suspend (uri: String) -> Boolean)? = null,
    onDismissEditPlaylist: () -> Unit = {},
    onTogglePlaylistSaved: ((uri: String, saved: Boolean) -> Unit)? = null,
    onTogglePlaylistPublished: ((uri: String, published: Boolean) -> Unit)? = null,
    onRemoveLibraryItem: ((uri: String) -> Unit)? = null,
    onCreateFolder: (suspend (name: String) -> String?)? = null,
    onRenameFolder: ((uri: String, name: String) -> Unit)? = null,
    onDeleteFolder: ((uri: String) -> Unit)? = null,
    onMoveToFolder: ((playlistUri: String, folderUri: String) -> Unit)? = null,
    artistTracks: List<PopularTrack>? = null,
    artistReleases: List<ArtistRelease>? = null,
    artistMonthlyListeners: String? = null,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = ""
) {
    var currentScreen by remember { mutableStateOf(ShellScreen.HOME) }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // A tap on the playback notification bumps this from the activity; the
    // full player slides up over whatever screen is showing, exactly like
    // tapping the mini bar does.
    LaunchedEffect(expandPlayerSignal) {
        if (expandPlayerSignal > 0) isPlayerExpanded = true
    }
    var activePlaylistTitle by remember { mutableStateOf("") }
    var activePlaylistUri by remember { mutableStateOf("") }
    var activePlaylistCoverUrl by remember { mutableStateOf<String?>(null) }
    var activeAlbumCoverUrl by remember { mutableStateOf<String?>(null) }
    var activeArtistAvatarUrl by remember { mutableStateOf<String?>(null) }
    var activeAlbumUri by remember { mutableStateOf("") }
    var activeArtistUri by remember { mutableStateOf("") }
    var activeCategoryTitle by remember { mutableStateOf("") }
    var activeCategoryColor by remember { mutableStateOf(Color(0xFF148A08)) }
    var activeShowUri by remember { mutableStateOf("") }
    var activeEpisodeUri by remember { mutableStateOf("") }
    var activeFolderUri by remember { mutableStateOf("") }
    var activeFolderTitle by remember { mutableStateOf("") }

    // Bottom Sheet States
    var isProfileDrawerOpen by remember { mutableStateOf(false) }
    var isAccountsSheetOpen by remember { mutableStateOf(false) }
    var isTrackOptionsOpen by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackItem?>(null) }
    var isPlaylistOptionsOpen by remember { mutableStateOf(false) }
    var isPlaylistEditMode by remember { mutableStateOf(false) }
    var isAlbumOptionsOpen by remember { mutableStateOf(false) }
    var isArtistOptionsOpen by remember { mutableStateOf(false) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    var currentSortOption by remember { mutableStateOf(LibrarySortOption.RECENTS) }
    var isCreateSheetOpen by remember { mutableStateOf(false) }
    var isDeviceSheetOpen by remember { mutableStateOf(false) }
    var isQueueSheetOpen by remember { mutableStateOf(false) }
    var isSleepTimerOpen by remember { mutableStateOf(false) }
    var isArtistAboutOpen by remember { mutableStateOf(false) }
    var isDiscographyOpen by remember { mutableStateOf(false) }
    var isCreditsOpen by remember { mutableStateOf(false) }
    var isAddToPlaylistOpen by remember { mutableStateOf(false) }
    var libraryItemOptions by remember { mutableStateOf<EntityOptions?>(null) }
    var folderDraft by remember { mutableStateOf<FolderDraft?>(null) }
    var isCreatePlaylistOpen by remember { mutableStateOf(false) }
    var playlistCreateName by remember { mutableStateOf("My Playlist") }
    var creditsTrack by remember { mutableStateOf<TrackItem?>(null) }
    var addTrackTarget by remember { mutableStateOf<TrackItem?>(null) }
    var activeSleepTimer by remember { mutableStateOf<SleepTimerOption?>(null) }
    var activeArtistName by remember { mutableStateOf("") }
    var activeAlbumTitle by remember { mutableStateOf("") }
    var activeAlbumArtist by remember { mutableStateOf("") }
    var activeAlbumYear by remember { mutableStateOf("") }
    var activeAlbumReleaseDate by remember { mutableStateOf("") }
    var activeAlbumColor by remember { mutableStateOf(EncoreColors.Card) }

    val shellScope = rememberCoroutineScope()
    val navBackStack = remember { mutableStateListOf<BackStackItem>() }
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

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
                albumUri = activeAlbumUri,
                albumCoverUrl = activeAlbumCoverUrl,
                artistName = activeArtistName,
                artistUri = activeArtistUri,
                artistAvatarUrl = activeArtistAvatarUrl,
                showUri = activeShowUri
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
                activePlaylistCoverUrl = previous.playlistCoverUrl
                if (previous.screen == ShellScreen.PLAYLIST && previous.playlistUri.isNotEmpty()) {
                    if (onLoadPlaylist != null) onLoadPlaylist?.invoke(previous.playlistUri) else playerListener?.onLoadPlaylist(previous.playlistUri)
                }
            }
            if (previous.albumTitle.isNotEmpty()) {
                activeAlbumTitle = previous.albumTitle
                activeAlbumArtist = previous.albumArtist
                activeAlbumUri = previous.albumUri
                activeAlbumCoverUrl = previous.albumCoverUrl
                if (previous.screen == ShellScreen.ALBUM && previous.albumUri.isNotEmpty()) {
                    if (onLoadAlbum != null) onLoadAlbum?.invoke(previous.albumUri, previous.albumTitle) else playerListener?.onLoadAlbum(previous.albumUri, previous.albumTitle, previous.albumArtist)
                }
            }
            if (previous.artistName.isNotEmpty()) {
                activeArtistName = previous.artistName
                activeArtistUri = previous.artistUri
                if (previous.artistAvatarUrl != null) activeArtistAvatarUrl = previous.artistAvatarUrl
                if (previous.screen == ShellScreen.ARTIST && previous.artistUri.isNotEmpty()) {
                    if (onLoadArtist != null) onLoadArtist?.invoke(previous.artistUri, previous.artistName) else playerListener?.onLoadArtist(previous.artistUri, previous.artistName)
                }
            }
            if (previous.showUri.isNotEmpty()) {
                activeShowUri = previous.showUri
                if (previous.screen == ShellScreen.SHOW) {
                    onOpenShow?.invoke(previous.showUri)
                }
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
        if (onOpenQuackifySettings != null) {
            onOpenQuackifySettings()
        } else {
            pushCurrentState()
            currentScreen = ShellScreen.SETTINGS
        }
    }

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

    // No song has loaded yet (fresh app open): the mini player and the NPV keep
    // an explicit "No Track" state instead of blank lines, and the transport
    // button stands quiet, so it is obvious nothing is loaded.
    val playerIdentity = playerIdentityOf(
        title = currentTrackTitle,
        artist = currentArtist,
        trackUri = playbackState?.trackUri,
        localTitle = localTrackTitle,
    )

    // Resolved uris of the playing track, used by the player title/artist taps and the three-dot sheet
    val currentQueueRow = remember(playbackState?.trackUri, playbackState?.queue, playbackState?.queueFromContext) {
        (playbackState?.queue.orEmpty() + playbackState?.queueFromContext.orEmpty())
            .firstOrNull { it.uri == playbackState?.trackUri }
    }
    val resolvedAlbumUri = playbackState?.albumUri?.takeIf { it.isNotBlank() }
        ?: currentQueueRow?.albumUri.orEmpty()
    val resolvedArtistUri = playbackState?.artistUri?.takeIf { it.isNotBlank() }
        ?: currentQueueRow?.artistUri.orEmpty()

    val openAlbum: (String, String, String) -> Unit = { title, artist, uri ->
        activeAlbumTitle = title
        activeAlbumArtist = artist
        activeAlbumUri = uri
        val foundEntity = libraryItems?.find { it.title.equals(title, ignoreCase = true) }
        activeAlbumCoverUrl = foundEntity?.imageUrl

        val albumUriToLoad = uri.takeIf { it.startsWith("spotify:album:") }
            ?: foundEntity?.uri?.takeIf { it.startsWith("spotify:album:") }.orEmpty()
        if (onLoadAlbum != null) onLoadAlbum?.invoke(albumUriToLoad, title) else playerListener?.onLoadAlbum(albumUriToLoad, title, activeAlbumArtist)

        pushCurrentState()
        currentScreen = ShellScreen.ALBUM
    }

    val openShow: (String) -> Unit = { uri ->
        pushCurrentState()
        activeShowUri = uri
        currentScreen = ShellScreen.SHOW
        onOpenShow?.invoke(uri)
    }

    val openPlaylist: (String, String) -> Unit = { title, uri ->
        pushCurrentState()
        activePlaylistTitle = title
        activePlaylistUri = uri
        currentScreen = ShellScreen.PLAYLIST
        if (uri.isNotEmpty()) {
            if (onLoadPlaylist != null) onLoadPlaylist?.invoke(uri) else playerListener?.onLoadPlaylist(uri)
        }
    }

    val openEpisode: (String) -> Unit = { uri ->
        pushCurrentState()
        activeEpisodeUri = uri
        currentScreen = ShellScreen.EPISODE
        onOpenEpisode?.invoke(uri)
    }

    val handleTrackPlay: (TrackItem) -> Unit = { track ->
        if (playerListener != null) {
            if (track.uri.isNotEmpty()) {
                val rows = when (currentScreen) {
                    ShellScreen.ARTIST -> artistTracks.orEmpty().map { it.track.uri }
                    ShellScreen.PLAYLIST -> playlistTracks.orEmpty().map { it.uri }
                    ShellScreen.ALBUM -> albumTracks.orEmpty().map { it.uri }
                    else -> emptyList()
                }
                val context = when (currentScreen) {
                    ShellScreen.ARTIST -> activeArtistUri
                    ShellScreen.PLAYLIST -> activePlaylistUri
                    ShellScreen.ALBUM -> activeAlbumUri
                    else -> ""
                }
                val index = rows.indexOfFirst { it == track.uri }
                if (context.isNotBlank() && index >= 0) {
                    playerListener.onPlayTrack(track.uri, context, index)
                } else {
                    playerListener.onPlayUri(track.uri)
                }
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
    }

    val handlePlayPause: () -> Unit = {
        if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
    }

    val handleTrackOptions: (TrackItem) -> Unit = { track ->
        selectedTrackForOptions = track
        isTrackOptionsOpen = true
    }

    val handlePodcastOpen: (String, String) -> Unit = { _, uri ->
        if (uri.isNotEmpty()) openShow(uri)
    }

    val openProfileDrawer: () -> Unit = { isProfileDrawerOpen = true }

    val openArtist: (String, String) -> Unit = { artist, uri ->
        activeArtistName = artist
        activeArtistUri = uri
        activeArtistAvatarUrl = libraryItems?.find { it.title.equals(artist, ignoreCase = true) }?.imageUrl
            ?: nowPlayingArtistImageUrl?.takeIf { artist.equals(currentArtist, ignoreCase = true) }

        val artistUriToLoad = uri.takeIf { it.startsWith("spotify:artist:") }
            ?: libraryItems?.find { it.title.equals(artist, ignoreCase = true) }
                ?.uri?.takeIf { it.startsWith("spotify:artist:") }.orEmpty()
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
                isAccountsSheetOpen ||
                isTrackOptionsOpen ||
                isPlaylistOptionsOpen ||
                isAlbumOptionsOpen ||
                isArtistOptionsOpen ||
                isSortSheetOpen ||
                isCreateSheetOpen ||
                isCreatePlaylistOpen ||
                playlistEditDraft != null ||
                libraryItemOptions != null ||
                folderDraft != null ||
                navBackStack.isNotEmpty() ||
                currentScreen != ShellScreen.HOME
    ) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (isProfileDrawerOpen) {
            isProfileDrawerOpen = false
        } else if (isAccountsSheetOpen) {
            isAccountsSheetOpen = false
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
        } else if (isCreatePlaylistOpen) {
            isCreatePlaylistOpen = false
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

    val currentNavIndex = when (currentScreen) {
        ShellScreen.HOME -> 0
        ShellScreen.SEARCH, ShellScreen.CATEGORY -> 1
        ShellScreen.LIBRARY -> 2
        else -> selectedNavIndex
    }

    fun copyEntityLink(uri: String) {
        val url = entityUrl(uri) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Spotify link", url))
    }

    fun shareEntity(uri: String) {
        val url = entityUrl(uri) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    fun openEntityInBrowser(uri: String) {
        val url = entityUrl(uri) ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    val libraryFolderChoices = remember(libraryItems) {
        libraryItems.orEmpty()
            .filter { it.type == LibraryItemType.FOLDER }
            .map { it.uri to it.title }
    }

    val handleLibraryLongPress: (LibraryItem) -> Unit = { item ->
        if (item.type == LibraryItemType.FOLDER) {
            folderDraft = FolderDraft(mode = FolderMode.RENAME, folderUri = item.uri, title = item.title)
        } else {
            libraryItemOptions = EntityOptions(
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                uri = item.uri,
            )
        }
    }

    val handleFolderClick: (LibraryItem) -> Unit = { item ->
        pushCurrentState()
        activeFolderUri = item.uri
        activeFolderTitle = item.title
        currentScreen = ShellScreen.FOLDER
        onOpenFolder?.invoke(item.uri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.PureBlack)
    ) {
        // Active Screen Content
        // A finished load that still has no rows at all means the fetch itself
        // failed (an empty playlist comes back as a non-null empty list).
        val playlistLoadFailed = !playlistLoading && playlistTracks == null
        ShellScreenSlot {
        when (currentScreen) {
            ShellScreen.HOME -> {
                SpotifyHomeScreen(
                    onPlaylistClick = { playlist, uri ->
                        val foundItem = homeQuickAccessItems?.find { it.uri == uri || it.title == playlist }
                        val foundRec = homeRecommendations?.find { it.uri == uri || it.title == playlist }
                        val foundLib = libraryItems?.find { it.uri == uri || it.title == playlist }
                        val cover = foundItem?.imageUrl ?: foundRec?.imageUrl ?: foundLib?.imageUrl
                        activePlaylistCoverUrl = cover
                        activeAlbumCoverUrl = cover
                        if (uri.startsWith("spotify:album:")) {
                            openAlbum(playlist, "", uri)
                        } else if (uri.startsWith("spotify:show:")) {
                            openShow(uri)
                        } else if (uri.startsWith("spotify:episode:")) {
                            openEpisode(uri)
                        } else {
                            openPlaylist(playlist, uri)
                        }
                    },
                    onProfileClick = openProfileDrawer,
                    onNotificationsClick = {
                        pushCurrentState()
                        currentScreen = ShellScreen.NOTIFICATIONS
                    },
                    onHistoryClick = {
                        pushCurrentState()
                        currentScreen = ShellScreen.HISTORY
                        onOpenHistory?.invoke()
                    },
                    onSettingsClick = handleOpenSettings,
                    quickAccessItems = homeQuickAccessItems,
                    recommendations = homeRecommendations,
                    shelves = homeShelves,
                    loading = homeLoading,
                    whatsNewCount = notifications.count { it.isNew },
                    onArtistClick = openArtist,
                    userDisplayName = userDisplayName,
                    userAvatarUrl = userAvatarUrl
                )
            }
            ShellScreen.SEARCH -> {
                SpotifySearchScreen(
                    onCategoryClick = { category ->
                        pushCurrentState()
                        activeCategoryTitle = category.title
                        activeCategoryColor = category.color
                        if (category.uri.isNotBlank()) {
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
                    onEpisodeClick = { _, uri -> if (uri.isNotEmpty()) openEpisode(uri) },
                    onShowClick = { _, uri -> if (uri.isNotEmpty()) openShow(uri) },
                    onPlaylistClick = openPlaylist,
                    onTrackSelect = handleTrackPlay,
                    onTrackOptionsClick = handleTrackOptions,
                    onProfileClick = openProfileDrawer,
                    searchResults = searchResults,
                    searchData = searchData,
                    onSearchQueryChange = { query ->
                        onSearchQueryChange?.invoke(query) ?: playerListener?.onSearch(query)
                    },
                    recentSearches = searchRecent,
                    suggestions = searchSuggestions,
                    suggestionHits = searchSuggestionHits,
                    loading = searchLoading,
                    loadFailed = searchFailed,
                    onRetry = { onRetrySearch?.invoke() },
                    onClearRecent = { onClearRecentSearches?.invoke() },
                    onRemoveRecent = { item -> onRemoveRecentSearch?.invoke(item.title) },
                    onToggleSaved = { uri -> onToggleSaved?.invoke(uri) },
                    onLoadSection = onLoadSearchSection,
                    browseCategories = remember(browseCategories) {
                        browseCategories.orEmpty().map { card ->
                            CategoryCard(
                                title = card.title,
                                color = parseHexColor(card.colorHex) ?: getCategoryColor(card.title),
                                uri = card.uri,
                                imageUrl = card.imageUrl.takeIf { it.isNotBlank() }
                            )
                        }
                    },
                    userDisplayName = userDisplayName,
                    userAvatarUrl = userAvatarUrl,
                    currentTrackTitle = currentTrackTitle,
                    currentTrackUri = playbackState?.trackUri.orEmpty(),
                    isPlaying = isPlaying
                )
            }
            ShellScreen.LIBRARY -> {
                SpotifyLibraryScreen(
                    onItemClick = { itemTitle, itemUri ->
                        val foundItem = libraryItems?.find { it.uri == itemUri || it.title == itemTitle }
                        activePlaylistCoverUrl = foundItem?.imageUrl
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
                    onPlusClick = { isCreateSheetOpen = true },
                    onSortClick = { isSortSheetOpen = true },
                    currentSort = currentSortOption
                )
            }
            ShellScreen.PLAYLIST -> ShellPlaylistPane(
                activePlaylistTitle = activePlaylistTitle,
                activePlaylistUri = activePlaylistUri,
                activePlaylistCoverUrl = activePlaylistCoverUrl,
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
                onTogglePlaylistSaved = onTogglePlaylistSaved,
                handleBack = handleBack,
                handleTrackOptions = handleTrackOptions,
                shareEntity = ::shareEntity,
                onLocalPlayPause = { localIsPlaying = !localIsPlaying },
                onLocalTrack = { track ->
                    localTrackTitle = track.title
                    localArtist = track.artist
                    localIsPlaying = true
                    localProgress = 0.05f
                    localPositionMs = 12000L
                },
                onPlaylistOptions = { isPlaylistOptionsOpen = true },
                onEditModeChange = { isEditing -> isPlaylistEditMode = isEditing }
            )
            ShellScreen.ALBUM -> ShellAlbumPane(
                activeAlbumTitle = activeAlbumTitle,
                activeAlbumArtist = activeAlbumArtist,
                activeAlbumUri = activeAlbumUri,
                activeAlbumCoverUrl = activeAlbumCoverUrl,
                activeAlbumYear = activeAlbumYear,
                activeAlbumReleaseDate = activeAlbumReleaseDate,
                activeAlbumColor = activeAlbumColor,
                albumDetail = albumDetail,
                albumTracks = albumTracks,
                currentTrackTitle = currentTrackTitle,
                isPlaying = isPlaying,
                playerListener = playerListener,
                handleBack = handleBack,
                handlePlayPause = handlePlayPause,
                handleTrackOptions = handleTrackOptions,
                openArtist = openArtist,
                openAlbum = openAlbum,
                onAlbumOptions = { isAlbumOptionsOpen = true }
            )
            ShellScreen.ARTIST -> {
                androidx.compose.runtime.key(activeArtistName) {
                    SpotifyArtistScreen(
                        artistName = activeArtistName,
                        monthlyListeners = artistMonthlyListeners?.takeIf { it.isNotBlank() }.orEmpty(),
                        heroColor = artistDetail?.colorHex?.let { parseHexColor(it) } ?: EncoreColors.Card,
                        headerImageUrl = artistDetail?.headerImageUrl?.takeIf { it.isNotBlank() }
                            ?: artistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                            ?: activeArtistAvatarUrl,
                        popularTracks = artistTracks.orEmpty(),
                        releases = artistReleases.orEmpty(),
                        currentTrackTitle = currentTrackTitle,
                        isPlaying = isPlaying,
                        biography = artistDetail?.biography.orEmpty(),
                        verified = artistDetail?.verified == true,
                        isLoading = artistLoading,
                        onBackClick = handleBack,
                        onPlayPauseClick = handlePlayPause,
                        onTrackSelect = handleTrackPlay,
                        onTrackOptionsClick = handleTrackOptions,
                        onArtistOptionsClick = { isArtistOptionsOpen = true },
                        onReleaseClick = { release ->
                            openAlbum(release.title, activeArtistName, release.uri)
                        },
                        onAboutClick = { isArtistAboutOpen = true },
                        onDiscographyClick = { isDiscographyOpen = true }
                    )
                }
            }
            ShellScreen.CATEGORY -> {
                SpotifyCategoryScreen(
                    categoryTitle = activeCategoryTitle,
                    categoryColor = activeCategoryColor,
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
                        activePlaylistCoverUrl = cover
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
                        activeCategoryTitle = subcategory
                        activeCategoryColor = getCategoryColor(subcategory)
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
                            activeEpisodeUri = item.uri
                            currentScreen = ShellScreen.EPISODE
                            onOpenEpisode?.invoke(item.uri)
                        }
                        item.uri.isNotEmpty() -> {
                            pushCurrentState()
                            activePlaylistTitle = item.title
                            activePlaylistUri = item.uri
                            currentScreen = ShellScreen.PLAYLIST
                            if (onLoadPlaylist != null) onLoadPlaylist?.invoke(item.uri) else playerListener?.onLoadPlaylist(item.uri)
                        }
                    }
                }
            )
            ShellScreen.HISTORY -> ShellHistoryPane(
                onBackClick = handleBack,
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
                todayTracks = historyToday,
                yesterdayTracks = historyYesterday,
                earlierTracks = historyEarlier,
                loading = historyLoading,
                currentTrackTitle = currentTrackTitle,
                isPlaying = isPlaying
            )
            ShellScreen.BROWSE -> ShellBrowsePane(
                page = browsePage,
                fallbackTitle = activeCategoryTitle,
                onBack = handleBack,
                onOpenBrowse = { uri, title ->
                    activeCategoryTitle = title
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
                retryUri = activeShowUri,
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
                retryUri = activeEpisodeUri,
                isPlaying = isPlaying,
                onBack = handleBack,
                onRetry = { uri -> onOpenEpisode?.invoke(uri) },
                onPlayPause = handlePlayPause,
                onShowClick = { uri -> openShow(uri) }
            )

            ShellScreen.FOLDER -> ShellFolderPane(
                items = folderItems,
                loading = folderLoading,
                title = activeFolderTitle.ifBlank { "Your Library" },
                userDisplayName = userDisplayName,
                userAvatarUrl = userAvatarUrl,
                onItemClick = { itemTitle, itemUri ->
                    val foundItem = folderItems?.find { it.uri == itemUri || it.title == itemTitle }
                    activePlaylistCoverUrl = foundItem?.imageUrl
                    pushCurrentState()
                    activePlaylistTitle = itemTitle
                    activePlaylistUri = itemUri
                    currentScreen = ShellScreen.PLAYLIST
                    if (itemUri.isNotEmpty()) if (onLoadPlaylist != null) onLoadPlaylist?.invoke(itemUri) else playerListener?.onLoadPlaylist(itemUri)
                },
                onItemLongPress = handleLibraryLongPress,
                onFolderClick = handleFolderClick,
                onProfileClick = openProfileDrawer
            )
        }
        }

        val nowPlayingDominantColor by rememberDominantColor(
            imageUrl = currentCoverUrl,
            fallbackSeed = currentTrackTitle + currentArtist,
            defaultColor = Color(0xFF432371)
        )

        if (!isPlaylistEditMode) {
            ShellBottomBar(
                title = playerIdentity.title,
                artist = playerIdentity.artist,
                isPlaying = isPlaying,
                progress = currentProgress,
                isLiked = isLiked,
                coverUrl = currentCoverUrl,
                coverColor = nowPlayingDominantColor,
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
                onPlayPause = handlePlayPause,
                onLike = { if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = !localIsLiked },
                onDeviceClick = { isDeviceSheetOpen = true },
                onBarClick = { isPlayerExpanded = true },
                onSkipNext = handleSkipNext,
                onSkipPrevious = handleSkipPrevious,
                hasTrack = playerIdentity.trackLoaded,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Full Now Playing Screen
        val (contextHeader, contextSub) = nowPlayingContext(
            trackLoaded = playerIdentity.trackLoaded,
            screen = currentScreen,
            playlistTitle = activePlaylistTitle,
            albumTitle = activeAlbumTitle,
            artistName = activeArtistName,
        )
        ShellNowPlaying(
            visible = isPlayerExpanded,
            title = playerIdentity.title,
            artist = playerIdentity.artist,
            contextHeader = contextHeader,
            contextSub = contextSub,
            isPlaying = isPlaying,
            progress = currentProgress,
            durationMs = durationMs,
            positionMs = currentPositionMs,
            dominantColor = nowPlayingDominantColor,
            isLiked = isLiked,
            isExplicit = playbackState?.explicit ?: false,
            monthlyListeners = nowPlayingMonthlyListeners,
            artistBio = nowPlayingArtistBio,
            artistImageUrl = nowPlayingArtistImageUrl,
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
            onLike = { if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = !localIsLiked },
            onShuffle = { if (playerListener != null) playerListener.onShuffleToggle() else localShuffleMode = localShuffleMode.next() },
            onRepeat = { if (playerListener != null) playerListener.onRepeatToggle() else localRepeatMode = localRepeatMode.next() },
            onSeek = { p -> if (playerListener != null) playerListener.onSeek((p * durationMs).toLong()) else { localProgress = p; localPositionMs = (p * durationMs).toLong() } },
            onMore = {
                selectedTrackForOptions = TrackItem(
                    title = currentTrackTitle,
                    artist = currentArtist,
                    isExplicit = playbackState?.explicit ?: false,
                    uri = playbackState?.trackUri.orEmpty(),
                    imageUrl = currentCoverUrl.takeIf { it.isNotBlank() },
                    saved = isLiked,
                    albumUri = resolvedAlbumUri,
                    artistUri = resolvedArtistUri,
                )
                isTrackOptionsOpen = true
            },
            onQueue = { isQueueSheetOpen = true },
            onDevice = { isDeviceSheetOpen = true },
            onShare = { playbackState?.trackUri?.takeIf { it.isNotBlank() }?.let(::shareEntity) },
            onTitleClick = {
                isPlayerExpanded = false
                openAlbum(currentTrackTitle, currentArtist, resolvedAlbumUri)
            },
            onArtistClick = {
                isPlayerExpanded = false
                openArtist(currentArtist, resolvedArtistUri)
            }
        )

        val isPlaylistUri = activePlaylistUri.startsWith("spotify:playlist:")
        val playlistEntity = if (activePlaylistUri.isBlank() && activePlaylistTitle.isBlank()) null
        else EntityOptions(
            title = playlistDetail?.title?.takeIf { it.isNotBlank() } ?: activePlaylistTitle,
            subtitle = if (isPlaylistUri)
                "Playlist • " + (playlistDetail?.creator?.takeIf { it.isNotBlank() } ?: userDisplayName)
            else "",
            imageUrl = activePlaylistCoverUrl ?: playlistDetail?.imageUrl?.takeIf { it.isNotBlank() },
            uri = activePlaylistUri,
        )

        ShellSheets(
            flags = SheetFlags(
                profileDrawer = isProfileDrawerOpen,
                accounts = isAccountsSheetOpen,
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
            currentTrackUri = playbackState?.trackUri.orEmpty(),
            currentTrackCoverUrl = currentCoverUrl.takeIf { it.isNotBlank() },
            playlistEntity = playlistEntity,
            playlistEditable = isPlaylistUri && playlistDetail?.editable == true,
            playlistSaved = if (isPlaylistUri) playlistSaved else null,
            playlistPublished = if (isPlaylistUri) playlistPublished else null,
            albumTitle = activeAlbumTitle,
            albumArtist = activeAlbumArtist,
            albumCoverUrl = activeAlbumCoverUrl,
            artistName = activeArtistName,
            artistAvatarUrl = activeArtistAvatarUrl,
            sortOption = currentSortOption,
            sleepTimer = activeSleepTimer,
            userDisplayName = userDisplayName,
            userAvatarUrl = userAvatarUrl,
            savedAccounts = savedAccounts,
            activeAccountId = activeAccountId,
            canvasAvailable = isCanvasAllowed && canvasFilePath != null,
            isCanvasEnabled = isCanvasEnabled,
            onCanvasToggle = { onCanvasToggle?.invoke(!isCanvasEnabled) },
            onDismissProfile = { isProfileDrawerOpen = false },
            onDismissAccounts = { isAccountsSheetOpen = false },
            onSwitchAccount = { id -> onSwitchAccount?.invoke(id) },
            onAddAccount = { onAddAccount?.invoke() },
            onRemoveAccount = { id -> onRemoveAccount?.invoke(id) },
            onOpenAccounts = { isAccountsSheetOpen = true },
            onDismissTrackOptions = { isTrackOptionsOpen = false },
            onDismissPlaylistOptions = { isPlaylistOptionsOpen = false },
            onDismissAlbumOptions = { isAlbumOptionsOpen = false },
            onDismissArtistOptions = { isArtistOptionsOpen = false },
            onDismissSort = { isSortSheetOpen = false },
            onDismissCreate = { isCreateSheetOpen = false },
            onDismissDevice = { isDeviceSheetOpen = false },
            onDismissQueue = { isQueueSheetOpen = false },
            onDismissSleepTimer = { isSleepTimerOpen = false },
            onAddToPlaylist = {
                selectedTrackForOptions?.let { track ->
                    isAddToPlaylistOpen = true
                    addTrackTarget = track
                    onAddToPlaylist?.invoke(track)
                }
            },
            onShowCredits = {
                selectedTrackForOptions?.let { track ->
                    isCreditsOpen = true
                    creditsTrack = track
                    onShowCredits?.invoke(track.uri)
                }
            },
            onEditPlaylist = {
                if (isPlaylistUri && activePlaylistUri.isNotEmpty()) onEditPlaylist?.invoke(activePlaylistUri)
            },
            onTogglePlaylistSaved = {
                if (isPlaylistUri && activePlaylistUri.isNotEmpty()) {
                    onTogglePlaylistSaved?.invoke(activePlaylistUri, !(playlistSaved ?: false))
                }
            },
            onTogglePlaylistPublished = {
                if (isPlaylistUri && activePlaylistUri.isNotEmpty()) {
                    onTogglePlaylistPublished?.invoke(activePlaylistUri, !(playlistPublished ?: false))
                }
            },
            onDeletePlaylist = {
                shellScope.launch {
                    val ok = onDeletePlaylist?.invoke(activePlaylistUri) ?: false
                    if (ok) handleBack()
                }
            },
            onCopyLink = { uri -> copyEntityLink(uri) },
            onShare = { uri -> shareEntity(uri) },
            onOpenInBrowser = { uri -> openEntityInBrowser(uri) },
            onWhatsNew = { pushCurrentState(); currentScreen = ShellScreen.NOTIFICATIONS },
            onHistory = {
                pushCurrentState()
                currentScreen = ShellScreen.HISTORY
                onOpenHistory?.invoke()
            },
            onSettings = handleOpenSettings,
            onLogout = { onLogout?.invoke() },
            onLikeToggle = { liked -> if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = liked },
            onAddToQueue = { track -> playerListener?.onQueueAdd(track.uri) },
            onViewArtist = { artist, uri ->
                isTrackOptionsOpen = false
                isPlayerExpanded = false
                openArtist(artist, uri)
            },
            onViewAlbum = { album, uri ->
                isTrackOptionsOpen = false
                isPlayerExpanded = false
                openAlbum(album, selectedTrackForOptions?.artist ?: "", uri)
            },
            onSleepTimerClick = { isSleepTimerOpen = true },
            onGoToArtist = { artist ->
                isAlbumOptionsOpen = false
                isPlayerExpanded = false
                openArtist(artist, albumDetail?.artistUri.orEmpty())
            },
            onSortSelected = { selected -> currentSortOption = selected },
            onCreatePlaylist = {
                shellScope.launch {
                    playlistCreateName = onPlaylistDefaultName?.invoke() ?: "My Playlist"
                    isCreatePlaylistOpen = true
                }
            },
            onCreateBlend = { pushCurrentState(); activePlaylistTitle = "Duck + Friend Blend"; currentScreen = ShellScreen.PLAYLIST },
            onCreateFolder = { folderDraft = FolderDraft(mode = FolderMode.CREATE) },
            onSelectDevice = { id -> localDevice = id; isDeviceSheetOpen = false; playerListener?.onSelectDevice(id) },
            onDeviceVisible = { visible -> playerListener?.onDeviceSheetVisible(visible) },
            onVolumeChange = { id, level -> playerListener?.onVolumeChange(id, level) },
            userQueue = playbackState?.queue.orEmpty(),
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
            onSleepTimerSelected = { option -> activeSleepTimer = option }
        )

        if (isArtistAboutOpen) {
            ArtistAboutSheet(
                isOpen = isArtistAboutOpen,
                artistName = activeArtistName,
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
                onDismiss = { isArtistAboutOpen = false },
                onLinkClick = { }
            )
        }

        if (isDiscographyOpen) {
            DiscographySheet(
                isOpen = isDiscographyOpen,
                artistUri = activeArtistUri,
                artistName = activeArtistName,
                onDismiss = { isDiscographyOpen = false },
                onOpenRelease = { title, uri -> openAlbum(title, activeArtistName, uri) }
            )
        }

        if (isAddToPlaylistOpen) {
            AddToPlaylistSheet(
                isOpen = true,
                trackTitle = addTrackTarget?.title.orEmpty(),
                choices = addToPlaylistChoices.orEmpty(),
                isLoading = addToPlaylistChoices == null,
                onPick = { choice ->
                    addTrackTarget?.let { track -> onPickPlaylist?.invoke(choice, track) }
                    isAddToPlaylistOpen = false
                },
                onCreateNew = {
                    addTrackTarget?.let { track -> onCreatePlaylistWith?.invoke(track) }
                    isAddToPlaylistOpen = false
                },
                onDismiss = { isAddToPlaylistOpen = false }
            )
        }

        if (isCreditsOpen) {
            CreditsSheet(
                isOpen = true,
                title = creditsTrack?.title.orEmpty(),
                subtitle = creditsTrack?.artist.orEmpty(),
                imageUrl = creditsTrack?.imageUrl,
                credits = trackCredits,
                onDismiss = { isCreditsOpen = false },
                onArtistClick = { uri, name -> openArtist(name, uri) }
            )
        }

        ShellPlaylistEditSheets(
            createOpen = isCreatePlaylistOpen,
            createName = playlistCreateName,
            editDraft = playlistEditDraft,
            onCreateSave = onCreatePlaylist,
            onCreated = { uri, name ->
                isCreatePlaylistOpen = false
                pushCurrentState()
                activePlaylistTitle = name
                activePlaylistUri = uri
                currentScreen = ShellScreen.PLAYLIST
                if (onLoadPlaylist != null) onLoadPlaylist?.invoke(uri) else playerListener?.onLoadPlaylist(uri)
            },
            onDismissCreate = { isCreatePlaylistOpen = false },
            onEditSave = onEditPlaylistDetails,
            onDismissEdit = onDismissEditPlaylist
        )

        libraryItemOptions?.let { entity ->
            val editable = entity.uri in editablePlaylistUris
            val removable = isCollectionUri(entity.uri).not()
            EntityOptionsSheet(
                entity = entity,
                isOpen = true,
                onDismiss = { libraryItemOptions = null },
                canEditPlaylist = editable,
                onEditPlaylist = { onEditPlaylist?.invoke(entity.uri) },
                isSaved = if (removable) true else null,
                onSaveToggle = { if (removable) onRemoveLibraryItem?.invoke(entity.uri) },
                canMoveToFolder = entity.uri.startsWith("spotify:playlist:"),
                onMoveToFolder = {
                    folderDraft = FolderDraft(
                        mode = FolderMode.PICKER,
                        folderUri = entity.uri,
                        title = entity.title,
                        folders = libraryFolderChoices,
                    )
                },
                canDeletePlaylist = editable,
                onDeletePlaylist = {
                    shellScope.launch {
                        onDeletePlaylist?.invoke(entity.uri)
                    }
                },
                onCopyLink = { copyEntityLink(entity.uri) },
                onAddToQueue = when {
                    entity.uri.startsWith("spotify:episode:") -> {
                        { playerListener?.onQueueAdd(entity.uri) }
                    }
                    entity.uri.startsWith("spotify:album:") -> {
                        {
                            val rows = albumDetail?.tracks.orEmpty()
                            if (rows.isEmpty()) {
                                playerListener?.onQueueAdd(entity.uri)
                            } else {
                                rows.forEach { row -> playerListener?.onQueueAdd(row.uri) }
                            }
                        }
                    }
                    else -> null
                },
                onShare = { shareEntity(entity.uri) },
                onOpenInBrowser = { openEntityInBrowser(entity.uri) }
            )
        }

        folderDraft?.let { draft ->
            FolderSheet(
                draft = draft,
                onDismiss = { folderDraft = null },
                onSubmit = { name ->
                    when (draft.mode) {
                        FolderMode.CREATE -> shellScope.launch {
                            val uri = onCreateFolder?.invoke(name)
                            if (uri != null) folderDraft = null
                        }
                        FolderMode.RENAME -> {
                            folderDraft = null
                            onRenameFolder?.invoke(draft.folderUri, name)
                        }
                        FolderMode.PICKER -> {}
                    }
                },
                onPick = { folderUri ->
                    folderDraft = null
                    onMoveToFolder?.invoke(draft.folderUri, folderUri)
                },
                onDelete = if (draft.mode == FolderMode.RENAME) {
                    {
                        folderDraft = null
                        onDeleteFolder?.invoke(draft.folderUri)
                    }
                } else null
            )
        }
    }
}

internal fun entityUrl(uri: String): String? {
    if (!uri.startsWith("spotify:")) return null
    val type = uri.removePrefix("spotify:").substringBefore(":")
    val id = uri.substringAfterLast(":")
    return if (type.isNotBlank() && id.isNotBlank()) "https://open.spotify.com/$type/$id" else null
}

internal fun isCollectionUri(uri: String): Boolean =
    uri.startsWith("spotify:collection:") ||
        uri.endsWith(":collection") ||
        uri == "spotify:user:@:collection"

@Composable
private fun ShellLoadingState(loading: Boolean, onRetry: () -> Unit) {
    if (loading) {
        DetailPageSkeleton(
            modifier = Modifier
                .fillMaxSize()
                .background(EncoreColors.Background)
                .statusBarsPadding()
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Couldn't load",
                style = SpotifyTheme.typography.titleMedium,
                color = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(text = "Try again", color = EncoreColors.Green)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyAppShell() {
    SpotifyEncoreTheme {
        SpotifyAppShell()
    }
}

/**
 * Hosts the active-screen switch inside its own composable lambda: [SpotifyAppShell]
 * sits at the JVM 64KB method limit, and lifting this block into a lambda keeps
 * the generated shell method small while leaving the screen code exactly as is.
 */
@Composable
private fun ShellScreenSlot(content: @Composable () -> Unit) {
    content()
}

/**
 * Explicit "No Track" identity for the mini player and the NPV while no song
 * has loaded, so the bars never sit on blank lines after a fresh app open.
 * Kept outside [SpotifyAppShell] to hold its generated method under the JVM limit.
 */
private data class PlayerIdentity(
    val trackLoaded: Boolean,
    val title: String,
    val artist: String,
)

private fun playerIdentityOf(
    title: String,
    artist: String,
    trackUri: String?,
    localTitle: String,
): PlayerIdentity = PlayerIdentity(
    trackLoaded = title.isNotBlank() ||
        trackUri?.isNotBlank() == true ||
        (trackUri == null && localTitle.isNotBlank()),
    title = title.ifBlank { "No Track" },
    artist = artist.ifBlank { "No Artist" },
)

/** What the NPV header should say, including the explicit nothing-loaded state. */
private fun nowPlayingContext(
    trackLoaded: Boolean,
    screen: ShellScreen,
    playlistTitle: String,
    albumTitle: String,
    artistName: String,
): Pair<String, String> = when {
    !trackLoaded -> "Nothing playing" to "Pick a song to get started"
    screen == ShellScreen.PLAYLIST -> "Playing from playlist" to playlistTitle
    screen == ShellScreen.ALBUM -> "Playing from album" to albumTitle
    screen == ShellScreen.ARTIST -> "Playing from artist" to artistName
    else -> "Playing from playlist" to playlistTitle
}

/** Settings branch of the shell, extracted to keep [SpotifyAppShell] under the jvm method limit. */
@Composable
private fun ShellSettingsPane(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    switches: SettingsSwitches,
    locks: SettingsLocks,
    product: String,
    country: String,
    loading: Boolean,
    onToggleSetting: ((String, Boolean) -> Unit)?,
    onToggleFullArtwork: (Boolean) -> Unit = {},
    onOpenLogViewer: () -> Unit = {}
) {
    SettingsScreen(
        onBackClick = onBackClick,
        onLogout = onLogout,
        switches = switches,
        locks = locks,
        product = product,
        country = country,
        loading = loading,
        onToggleMusicVideos = { onToggleSetting?.invoke("musicVideoDisabled", !it) },
        onToggleCanvas = { onToggleSetting?.invoke("canvasDisabled", !it) },
        onToggleOtherVideos = { onToggleSetting?.invoke("otherVideoDisabled", !it) },
        onToggleShowFollows = { onToggleSetting?.invoke("showFollows", !it) },
        onToggleShowPlaylists = { onToggleSetting?.invoke("showPublishedPlaylists", !it) },
        onToggleFullArtwork = onToggleFullArtwork,
        onOpenLogViewer = onOpenLogViewer
    )
}

/** Hidden log-viewer branch of the shell, opened from the Version row in Settings. */
@Composable
private fun ShellLogsPane(
    onBackClick: () -> Unit
) {
    LogViewerScreen(
        onBackClick = onBackClick
    )
}

/** Notifications branch of the shell. */
@Composable
private fun ShellNotificationsPane(
    onBackClick: () -> Unit,
    notifications: List<SpotifyNotification>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onPlay: (SpotifyNotification) -> Unit
) {
    NotificationsScreen(
        onBackClick = onBackClick,
        notifications = notifications,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = onRetry,
        onPlayClick = onPlay
    )
}

/** Album branch of the shell. */
@Composable
private fun ShellAlbumPane(
    activeAlbumTitle: String,
    activeAlbumArtist: String,
    activeAlbumUri: String,
    activeAlbumCoverUrl: String?,
    activeAlbumYear: String,
    activeAlbumReleaseDate: String,
    activeAlbumColor: Color,
    albumDetail: AlbumDetail?,
    albumTracks: List<TrackItem>?,
    currentTrackTitle: String,
    isPlaying: Boolean,
    playerListener: ComposePlayerListener?,
    handleBack: () -> Unit,
    handlePlayPause: () -> Unit,
    handleTrackOptions: (TrackItem) -> Unit,
    openArtist: (String, String) -> Unit,
    openAlbum: (String, String, String) -> Unit,
    onAlbumOptions: () -> Unit
) {
    androidx.compose.runtime.key(activeAlbumTitle, activeAlbumArtist) {
        val albumDominantColor by rememberDominantColor(
            imageUrl = activeAlbumCoverUrl ?: albumDetail?.imageUrl?.takeIf { it.isNotBlank() },
            fallbackSeed = activeAlbumTitle,
            defaultColor = activeAlbumColor
        )
        LaunchedEffect(albumTracks, activeAlbumUri) {
            if (!albumTracks.isNullOrEmpty()) {
                playerListener?.onListRendered(albumTracks.map { it.uri })
            }
        }
        SpotifyAlbumScreen(
            albumTitle = albumDetail?.title?.takeIf { it.isNotBlank() } ?: activeAlbumTitle,
            artistName = albumDetail?.artists?.takeIf { it.isNotBlank() } ?: activeAlbumArtist,
            releaseYear = albumDetail?.year ?: activeAlbumYear,
            releaseDate = albumDetail?.releaseDate ?: activeAlbumReleaseDate,
            coverColor = albumDetail?.colorHex?.let { parseHexColor(it) } ?: albumDominantColor,
            tracks = albumTracks ?: emptyList(),
            copyrightNotice = albumDetail?.copyright.orEmpty(),
            phonographicNotice = albumDetail?.phonographic.orEmpty(),
            currentTrackTitle = currentTrackTitle,
            isPlaying = isPlaying,
            coverUrl = activeAlbumCoverUrl ?: albumDetail?.imageUrl?.takeIf { it.isNotBlank() },
            moreByArtist = remember(albumDetail) {
                albumDetail?.moreByArtist?.map { it.toArtistRelease() }.orEmpty()
            },
            onBackClick = handleBack,
            onPlayPauseClick = handlePlayPause,
            onTrackSelect = { track ->
                if (playerListener != null) {
                    if (track.uri.isNotEmpty()) {
                        val index = albumTracks.orEmpty().indexOfFirst { it.uri == track.uri }
                        if (index >= 0 && activeAlbumUri.startsWith("spotify:album:")) {
                            playerListener.onPlayTrack(track.uri, activeAlbumUri, index)
                        } else {
                            playerListener.onPlayUri(track.uri)
                        }
                    } else {
                        playerListener.onTrackSelect(track.title, track.artist)
                    }
                }
            },
            onTrackOptionsClick = handleTrackOptions,
            onAlbumOptionsClick = onAlbumOptions,
            onArtistClick = { artist -> openArtist(artist, albumDetail?.artistUri.orEmpty()) },
            onAlbumClick = { release -> openAlbum(release.title, activeAlbumArtist, release.uri) }
        )
    }
}

/** Playlist branch of the shell. */
@Composable
private fun ShellPlaylistPane(
    activePlaylistTitle: String,
    activePlaylistUri: String,
    activePlaylistCoverUrl: String?,
    playlistDetail: CollectionDetail?,
    playlistTracks: List<TrackItem>?,
    playlistLoading: Boolean,
    playlistHasMore: Boolean,
    playlistLoadingMore: Boolean,
    onLoadMorePlaylistTracks: (() -> Unit)?,
    playlistLoadFailed: Boolean,
    onRetryPlaylist: (() -> Unit)?,
    currentTrackTitle: String,
    isPlaying: Boolean,
    userDisplayName: String,
    playerListener: ComposePlayerListener?,
    playlistSaved: Boolean?,
    playlistRecommendations: List<TrackItem>,
    recommendationsLoading: Boolean,
    onRefreshRecommendations: (() -> Unit)?,
    onAddRecommended: ((TrackItem) -> Unit)?,
    onTogglePlaylistSaved: ((String, Boolean) -> Unit)?,
    handleBack: () -> Unit,
    handleTrackOptions: (TrackItem) -> Unit,
    shareEntity: (String) -> Unit,
    onLocalPlayPause: () -> Unit,
    onLocalTrack: (TrackItem) -> Unit,
    onPlaylistOptions: () -> Unit,
    onEditModeChange: (Boolean) -> Unit = {}
) {
    androidx.compose.runtime.key(activePlaylistUri.ifEmpty { activePlaylistTitle }) {
        LaunchedEffect(playlistTracks, activePlaylistUri) {
            if (!playlistTracks.isNullOrEmpty()) {
                playerListener?.onListRendered(playlistTracks.map { it.uri })
            }
        }
        val resolvedPlaylistCover = activePlaylistCoverUrl ?: playlistTracks?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
        val playlistDominantColor by rememberDominantColor(
            imageUrl = resolvedPlaylistCover,
            fallbackSeed = activePlaylistTitle,
            defaultColor = Color(0xFF144D3F)
        )
        SpotifyPlaylistScreen(
            title = playlistDetail?.title?.takeIf { it.isNotBlank() } ?: activePlaylistTitle,
            description = playlistDetail?.description.orEmpty(),
            creator = playlistDetail?.creator?.takeIf { it.isNotBlank() } ?: userDisplayName,
            saveCount = playlistDetail?.saveCount.orEmpty(),
            tracks = playlistTracks ?: emptyList(),
            loading = playlistLoading,
            hasMore = playlistHasMore,
            loadingMore = playlistLoadingMore,
            onLoadMore = { onLoadMorePlaylistTracks?.invoke() },
            loadFailed = playlistLoadFailed,
            onRetry = { onRetryPlaylist?.invoke() },
            currentTrackTitle = currentTrackTitle,
            isPlaying = isPlaying,
            headerColor = playlistDetail?.colorHex?.let { parseHexColor(it) } ?: playlistDominantColor,
            coverUrl = activePlaylistCoverUrl
                ?: playlistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                ?: playlistTracks?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
            onBackClick = handleBack,
            onPlayPauseClick = {
                if (playerListener != null) {
                    if (activePlaylistUri.isNotEmpty() && !isPlaying) {
                        val firstTrack = playlistTracks
                            ?.firstOrNull { it.uri.startsWith("spotify:track:") }
                        if (firstTrack != null && activePlaylistUri.startsWith("spotify:playlist:")) {
                            playerListener.onPlayTrack(firstTrack.uri, activePlaylistUri, 0)
                        } else {
                            playerListener.onPlayUri(activePlaylistUri)
                        }
                    } else {
                        playerListener.onPlayPause()
                    }
                } else {
                    onLocalPlayPause()
                }
            },
            onTrackSelect = { track ->
                if (playerListener != null) {
                    if (track.uri.isNotEmpty()) {
                        val index = playlistTracks.orEmpty().indexOfFirst { it.uri == track.uri }
                        if (index >= 0 && activePlaylistUri.startsWith("spotify:playlist:")) {
                            playerListener.onPlayTrack(track.uri, activePlaylistUri, index)
                        } else {
                            playerListener.onPlayUri(track.uri)
                        }
                    } else {
                        playerListener.onTrackSelect(track.title, track.artist)
                    }
                } else {
                    onLocalTrack(track)
                }
            },
            onTrackOptionsClick = handleTrackOptions,
            onPlaylistOptionsClick = onPlaylistOptions,
            onShareClick = {
                if (activePlaylistUri.isNotEmpty()) shareEntity(activePlaylistUri)
            },
            isSaved = playlistSaved,
            onSaveToggle = {
                if (activePlaylistUri.startsWith("spotify:playlist:") && activePlaylistUri.isNotEmpty()) {
                    onTogglePlaylistSaved?.invoke(activePlaylistUri, !(playlistSaved ?: false))
                }
            },
            recommendedTracks = playlistRecommendations,
            recommendationsLoading = recommendationsLoading,
            onRecommendRefresh = { onRefreshRecommendations?.invoke() },
            onAddRecommended = { track -> onAddRecommended?.invoke(track) },
            onEditModeChange = onEditModeChange
        )
    }
}

/** History branch of the shell. */
@Composable
private fun ShellHistoryPane(
    onBackClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    todayTracks: List<TrackItem>,
    yesterdayTracks: List<TrackItem>,
    earlierTracks: List<TrackItem>,
    loading: Boolean,
    currentTrackTitle: String,
    isPlaying: Boolean
) {
    HistoryScreen(
        onBackClick = onBackClick,
        onTrackSelect = onTrackSelect,
        onTrackOptionsClick = onTrackOptionsClick,
        todayTracks = todayTracks,
        yesterdayTracks = yesterdayTracks,
        earlierTracks = earlierTracks,
        loading = loading,
        currentTrackTitle = currentTrackTitle,
        isPlaying = isPlaying
    )
}

/** Browse branch of the shell. */
@Composable
private fun ShellBrowsePane(
    page: BrowsePage?,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenBrowse: (String, String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    onOpenPlaylist: (String, String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (page == null) {
        SpotifyBrowseScreen(
            title = fallbackTitle,
            subtitle = "",
            colorHex = "",
            sections = emptyList(),
            isLoading = true,
            isLoadingMore = false,
            hasMore = false,
            onBackClick = onBack,
            onCardClick = { _, _ -> },
            onLoadMore = {}
        )
    } else {
        SpotifyBrowseScreen(
            title = page.title.ifBlank { fallbackTitle },
            subtitle = page.subtitle,
            colorHex = page.colorHex,
            sections = page.sections,
            isLoading = false,
            isLoadingMore = false,
            hasMore = page.nextOffset != null,
            onBackClick = onBack,
            onCardClick = { title, uri ->
                when {
                    uri.startsWith("spotify:show:") -> onOpenShow(uri)
                    uri.startsWith("spotify:page:") -> onOpenBrowse(uri, title)
                    uri.startsWith("spotify:album:") -> onOpenAlbum(title, uri)
                    uri.startsWith("spotify:") -> onOpenPlaylist(title, uri)
                    else -> Unit
                }
            },
            onLoadMore = onLoadMore
        )
    }
}

/** Podcast show branch of the shell. */
@Composable
private fun ShellShowPane(
    show: ShowDetail?,
    loading: Boolean,
    retryUri: String,
    isPlaying: Boolean,
    currentTrackTitle: String,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onPlayPause: () -> Unit,
    onLoadMore: () -> Unit
) {
    if (show == null) {
        ShellLoadingState(
            loading = loading,
            onRetry = { onRetry(retryUri) }
        )
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
            onEpisodeClick = { episode -> onEpisodeClick(episode.uri) },
            onBackClick = onBack,
            onPlayPauseClick = onPlayPause,
            onLoadMore = onLoadMore
        )
    }
}

/** Podcast episode branch of the shell. */
@Composable
private fun ShellEpisodePane(
    episode: EpisodeDetail?,
    loading: Boolean,
    retryUri: String,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onPlayPause: () -> Unit,
    onShowClick: (String) -> Unit
) {
    if (episode == null) {
        ShellLoadingState(
            loading = loading,
            onRetry = { onRetry(retryUri) }
        )
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
            onBackClick = onBack,
            onPlayPauseClick = onPlayPause,
            onShowClick = { onShowClick(episode.showUri) }
        )
    }
}

/** Folder view branch of the shell. */
@Composable
private fun ShellFolderPane(
    items: List<LibraryItem>?,
    loading: Boolean,
    title: String,
    userDisplayName: String,
    userAvatarUrl: String,
    onItemClick: (String, String) -> Unit,
    onItemLongPress: (LibraryItem) -> Unit,
    onFolderClick: (LibraryItem) -> Unit,
    onProfileClick: () -> Unit
) {
    SpotifyLibraryScreen(
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        onFolderClick = onFolderClick,
        items = items,
        loading = loading,
        title = title,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        onProfileClick = onProfileClick
    )
}

/** Create + edit playlist bottom sheets, extracted to keep the shell method small. */
@Composable
private fun ShellPlaylistEditSheets(
    createOpen: Boolean,
    createName: String,
    editDraft: PlaylistEditDraft?,
    onCreateSave: (suspend (String, String, ByteArray?) -> String?)?,
    onCreated: (String, String) -> Unit,
    onDismissCreate: () -> Unit,
    onEditSave: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    onDismissEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    if (createOpen) {
        EditPlaylistSheet(
            isOpen = true,
            title = "Create playlist",
            saveLabel = "Create",
            initialName = createName,
            initialDescription = "",
            coverUrl = null,
            canRemoveCover = false,
            onSave = { name, description, cover, _ ->
                scope.launch {
                    val uri = onCreateSave?.invoke(name, description, cover)
                    if (!uri.isNullOrEmpty()) onCreated(uri, name)
                }
            },
            onDismiss = onDismissCreate
        )
    }
    if (editDraft != null) {
        EditPlaylistSheet(
            isOpen = true,
            initialName = editDraft.name,
            initialDescription = editDraft.description,
            coverUrl = editDraft.coverUrl,
            canRemoveCover = editDraft.canRemoveCover,
            onSave = { name, description, cover, clearCover ->
                val uri = editDraft.uri
                scope.launch {
                    val ok = onEditSave?.invoke(uri, name, description, cover, clearCover) ?: false
                    if (ok) onDismissEdit()
                }
            },
            onDismiss = onDismissEdit
        )
    }
}
