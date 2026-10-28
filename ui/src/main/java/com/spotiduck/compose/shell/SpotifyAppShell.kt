package com.spotiduck.compose.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotiduck.compose.theme.EncoreAnimation
import kotlinx.coroutines.launch
import com.spotiduck.compose.R
import com.spotiduck.compose.components.RepeatMode
import com.spotiduck.compose.components.ShuffleMode
import com.spotiduck.compose.home.QuickAccessItem
import com.spotiduck.compose.home.RecommendationCard
import com.spotiduck.compose.home.SpotifyHomeScreen
import com.spotiduck.compose.library.LibraryItem
import com.spotiduck.compose.library.SpotifyLibraryScreen
import com.spotiduck.compose.player.SpotifyMiniPlayer
import com.spotiduck.compose.player.SpotifyNowPlayingScreen
import com.spotiduck.compose.playlist.SpotifyPlaylistScreen
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.screens.ArtistRelease
import com.spotiduck.compose.screens.HistoryScreen
import com.spotiduck.compose.screens.NotificationsScreen
import com.spotiduck.compose.screens.PopularTrack
import com.spotiduck.compose.screens.SettingsScreen
import com.spotiduck.compose.screens.SpotifyAlbumScreen
import com.spotiduck.compose.screens.SpotifyArtistScreen
import com.spotiduck.compose.search.SpotifySearchScreen
import com.spotiduck.compose.sheets.LibraryCreateSheet
import com.spotiduck.compose.sheets.AlbumOptionsSheet
import com.spotiduck.compose.sheets.ArtistOptionsSheet
import com.spotiduck.compose.sheets.LibrarySortOption
import com.spotiduck.compose.sheets.LibrarySortSheet
import com.spotiduck.compose.sheets.PlaylistOptionsSheet
import com.spotiduck.compose.sheets.ProfileDrawerSheet
import com.spotiduck.compose.sheets.SleepTimerOption
import com.spotiduck.compose.sheets.SleepTimerSheet
import com.spotiduck.compose.sheets.SpotifyConnectDeviceSheet
import com.spotiduck.compose.sheets.SpotifyQueueSheet
import com.spotiduck.compose.sheets.TrackOptionsSheet
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import androidx.compose.runtime.mutableStateListOf

enum class ShellScreen {
    HOME,
    SEARCH,
    LIBRARY,
    PLAYLIST,
    ALBUM,
    ARTIST,
    SETTINGS,
    NOTIFICATIONS,
    HISTORY
}

data class NavItem(
    val title: String,
    val iconRes: Int,
    val activeIconRes: Int = iconRes
)

data class ComposePlaybackState(
    val title: String = "Starboy",
    val artist: String = "The Weeknd, Daft Punk",
    val coverUrl: String = "",
    val isPlaying: Boolean = false,
    val progress: Float = 0.0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 230000L,
    val isLiked: Boolean = false,
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false,
    val shuffleMode: ShuffleMode = if (isShuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF,
    val repeatMode: RepeatMode = if (isRepeat) RepeatMode.REPEAT_ALL else RepeatMode.OFF,
    val connectedDevice: String = "This Phone"
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
    fun onSearch(query: String) {}
    fun onLoadPlaylist(uri: String) {}
    fun onRefreshLibrary() {}
    fun onRefreshHome() {}
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
    isComposeUiEnabled: Boolean = true,
    onToggleComposeUi: (Boolean) -> Unit = {},
    onOpenSpotiDuckSettings: (() -> Unit)? = null,
    homeQuickAccessItems: List<QuickAccessItem>? = null,
    homeRecommendations: List<RecommendationCard>? = null,
    libraryItems: List<LibraryItem>? = null,
    searchResults: List<TrackItem>? = null,
    playlistTracks: List<TrackItem>? = null,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = ""
) {
    var currentScreen by remember { mutableStateOf(ShellScreen.HOME) }
    var previousScreen by remember { mutableStateOf(ShellScreen.HOME) }

    val handleOpenSettings: () -> Unit = {
        if (onOpenSpotiDuckSettings != null) {
            onOpenSpotiDuckSettings()
        } else {
            previousScreen = currentScreen
            currentScreen = ShellScreen.SETTINGS
        }
    }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var activePlaylistTitle by remember { mutableStateOf("This Is The Weeknd") }
    var activePlaylistUri by remember { mutableStateOf("") }
    var activePlaylistCoverUrl by remember { mutableStateOf<String?>("https://i.scdn.co/image/ab67706f000000021c3fa36c4b92b67f40192e44") }
    var activeAlbumCoverUrl by remember { mutableStateOf<String?>("https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36") }
    var activeArtistAvatarUrl by remember { mutableStateOf<String?>("https://i.scdn.co/image/ab6761610000e5eb968a3770e060cf56306e9f1a") }

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
    var activeArtistName by remember { mutableStateOf("The Weeknd") }
    var activeAlbumTitle by remember { mutableStateOf("After Hours") }
    var activeAlbumArtist by remember { mutableStateOf("The Weeknd") }
    var activeAlbumYear by remember { mutableStateOf("2020") }
    var activeAlbumReleaseDate by remember { mutableStateOf("March 20, 2020") }
    var activeAlbumColor by remember { mutableStateOf(Color(0xFF901A1E)) }

    val userQueueList = remember {
        mutableStateListOf(
            TrackItem("Blinding Lights", "The Weeknd"),
            TrackItem("Save Your Tears", "The Weeknd")
        )
    }

    // Local fallback playback state (used if playbackState == null)
    var localIsPlaying by remember { mutableStateOf(true) }
    var localTrackTitle by remember { mutableStateOf("Starboy") }
    var localArtist by remember { mutableStateOf("The Weeknd, Daft Punk") }
    var localProgress by remember { mutableFloatStateOf(0.42f) }
    var localPositionMs by remember { mutableLongStateOf(96000L) }
    val localDurationMs = 230000L
    var localIsLiked by remember { mutableStateOf(true) }
    var localShuffleMode by remember { mutableStateOf(ShuffleMode.OFF) }
    var localRepeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var localDevice by remember { mutableStateOf("Galaxy Buds2 Pro") }

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

    val sampleTracks = remember {
        listOf(
            TrackItem("Starboy", "The Weeknd, Daft Punk", true, imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
            TrackItem("Blinding Lights", "The Weeknd", imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
            TrackItem("Save Your Tears", "The Weeknd", imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
            TrackItem("Die For You", "The Weeknd", imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
            TrackItem("One Of The Girls", "The Weeknd, JENNIE, Lily-Rose Depp", true, imageUrl = "https://i.scdn.co/image/ab67616d0000b273b0dd6a5cd1dec96c4119c262"),
            TrackItem("I Feel It Coming", "The Weeknd, Daft Punk", imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
            TrackItem("The Hills", "The Weeknd", true, imageUrl = "https://i.scdn.co/image/ab67616d0000b27320b57e4975f9ee73b64f3319"),
            TrackItem("Can't Feel My Face", "The Weeknd", imageUrl = "https://i.scdn.co/image/ab67616d0000b27320b57e4975f9ee73b64f3319")
        )
    }

    val afterHoursTracks = remember {
        listOf(
            TrackItem("Alone Again", "The Weeknd"),
            TrackItem("Too Late", "The Weeknd"),
            TrackItem("Hardest To Love", "The Weeknd"),
            TrackItem("Scared To Live", "The Weeknd"),
            TrackItem("Snowchild", "The Weeknd", isExplicit = true),
            TrackItem("Escape From LA", "The Weeknd", isExplicit = true),
            TrackItem("Heartless", "The Weeknd", isExplicit = true),
            TrackItem("Faith", "The Weeknd", isExplicit = true),
            TrackItem("Blinding Lights", "The Weeknd"),
            TrackItem("In Your Eyes", "The Weeknd"),
            TrackItem("Save Your Tears", "The Weeknd"),
            TrackItem("Repeat After Me (Interlude)", "The Weeknd"),
            TrackItem("After Hours", "The Weeknd"),
            TrackItem("Until I Bleed Out", "The Weeknd")
        )
    }

    val starboyTracks = remember {
        listOf(
            TrackItem("Starboy", "The Weeknd, Daft Punk", isExplicit = true),
            TrackItem("Party Monster", "The Weeknd", isExplicit = true),
            TrackItem("False Alarm", "The Weeknd", isExplicit = true),
            TrackItem("Reminder", "The Weeknd", isExplicit = true),
            TrackItem("Rockin'", "The Weeknd"),
            TrackItem("Secrets", "The Weeknd"),
            TrackItem("True Colors", "The Weeknd"),
            TrackItem("Stargirl Interlude", "The Weeknd, Lana Del Rey"),
            TrackItem("Sidewalks", "The Weeknd, Kendrick Lamar", isExplicit = true),
            TrackItem("Six Feet Under", "The Weeknd", isExplicit = true),
            TrackItem("Love to Lay", "The Weeknd"),
            TrackItem("A Lonely Night", "The Weeknd"),
            TrackItem("Attention", "The Weeknd"),
            TrackItem("Ordinary Life", "The Weeknd", isExplicit = true),
            TrackItem("Nothing Without You", "The Weeknd"),
            TrackItem("All I Know", "The Weeknd, Future", isExplicit = true),
            TrackItem("Die For You", "The Weeknd"),
            TrackItem("I Feel It Coming", "The Weeknd, Daft Punk")
        )
    }

    val ramTracks = remember {
        listOf(
            TrackItem("Give Life Back to Music", "Daft Punk"),
            TrackItem("The Game of Love", "Daft Punk"),
            TrackItem("Giorgio by Moroder", "Daft Punk"),
            TrackItem("Within", "Daft Punk"),
            TrackItem("Instant Crush", "Daft Punk, Julian Casablancas"),
            TrackItem("Lose Yourself to Dance", "Daft Punk, Pharrell Williams"),
            TrackItem("Touch", "Daft Punk, Paul Williams"),
            TrackItem("Get Lucky", "Daft Punk, Pharrell Williams"),
            TrackItem("Beyond", "Daft Punk"),
            TrackItem("Motherboard", "Daft Punk"),
            TrackItem("Fragments of Time", "Daft Punk, Todd Edwards"),
            TrackItem("Doin' It Right", "Daft Punk, Panda Bear"),
            TrackItem("Contact", "Daft Punk")
        )
    }

    val activeAlbumTracks = when {
        activeAlbumTitle.contains("Starboy", ignoreCase = true) -> starboyTracks
        activeAlbumTitle.contains("Random Access Memories", ignoreCase = true) -> ramTracks
        else -> afterHoursTracks
    }

    val openAlbum: (String, String) -> Unit = { title, artist ->
        activeAlbumTitle = title
        activeAlbumArtist = if (artist.isNotEmpty()) artist else if (title.contains("Random Access Memories", ignoreCase = true)) "Daft Punk" else "The Weeknd"
        when {
            title.contains("Starboy", ignoreCase = true) -> {
                activeAlbumYear = "2016"
                activeAlbumReleaseDate = "November 25, 2016"
                activeAlbumColor = Color(0xFF4F46E5)
                activeAlbumCoverUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"
            }
            title.contains("Random Access Memories", ignoreCase = true) -> {
                activeAlbumYear = "2013"
                activeAlbumReleaseDate = "May 17, 2013"
                activeAlbumColor = Color(0xFF6B7280)
                activeAlbumCoverUrl = "https://i.scdn.co/image/ab67616d0000b2739b9b36b0e22870b9f542d937"
            }
            title.contains("Dawn FM", ignoreCase = true) -> {
                activeAlbumYear = "2022"
                activeAlbumReleaseDate = "January 7, 2022"
                activeAlbumColor = Color(0xFF243B55)
                activeAlbumCoverUrl = "https://i.scdn.co/image/ab67616d0000b2734ab25200366c1b3f942d937a"
            }
            else -> {
                activeAlbumYear = "2020"
                activeAlbumReleaseDate = "March 20, 2020"
                activeAlbumColor = Color(0xFF901A1E)
                activeAlbumCoverUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"
            }
        }
        previousScreen = currentScreen
        currentScreen = ShellScreen.ALBUM
    }

    val handleSkipNext: () -> Unit = {
        if (playerListener != null) {
            playerListener.onSkipNext()
        } else {
            val currentIndex = sampleTracks.indexOfFirst { it.title == currentTrackTitle }
            val nextIndex = (currentIndex + 1).rem(sampleTracks.size)
            val next = sampleTracks[nextIndex]
            localTrackTitle = next.title
            localArtist = next.artist
            localProgress = 0.0f
            localPositionMs = 0L
        }
    }

    val handleSkipPrevious: () -> Unit = {
        if (playerListener != null) {
            playerListener.onSkipPrevious()
        } else {
            val currentIndex = sampleTracks.indexOfFirst { it.title == currentTrackTitle }
            val prevIndex = if (currentIndex <= 0) sampleTracks.size - 1 else currentIndex - 1
            val prev = sampleTracks[prevIndex]
            localTrackTitle = prev.title
            localArtist = prev.artist
            localProgress = 0.0f
            localPositionMs = 0L
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
        } else if (currentScreen == ShellScreen.PLAYLIST ||
            currentScreen == ShellScreen.ALBUM ||
            currentScreen == ShellScreen.ARTIST ||
            currentScreen == ShellScreen.SETTINGS ||
            currentScreen == ShellScreen.NOTIFICATIONS ||
            currentScreen == ShellScreen.HISTORY
        ) {
            currentScreen = previousScreen
        } else {
            currentScreen = ShellScreen.HOME
            selectedNavIndex = 0
        }
    }

    val currentNavIndex = when (currentScreen) {
        ShellScreen.HOME -> 0
        ShellScreen.SEARCH -> 1
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
                        if (playlist.contains("After Hours", ignoreCase = true) ||
                            playlist.contains("Random Access Memories", ignoreCase = true) ||
                            playlist.contains("Starboy", ignoreCase = true)
                        ) {
                            openAlbum(playlist, "")
                        } else {
                            activePlaylistTitle = playlist
                            activePlaylistUri = uri
                            previousScreen = ShellScreen.HOME
                            currentScreen = ShellScreen.PLAYLIST
                            if (uri.isNotEmpty()) {
                                playerListener?.onLoadPlaylist(uri)
                            }
                        }
                    },
                    onProfileClick = { isProfileDrawerOpen = true },
                    onNotificationsClick = {
                        previousScreen = ShellScreen.HOME
                        currentScreen = ShellScreen.NOTIFICATIONS
                    },
                    onHistoryClick = {
                        previousScreen = ShellScreen.HOME
                        currentScreen = ShellScreen.HISTORY
                    },
                    onSettingsClick = handleOpenSettings,
                    quickAccessItems = homeQuickAccessItems,
                    recommendations = homeRecommendations,
                    onArtistClick = { artist ->
                        activeArtistName = artist
                        previousScreen = ShellScreen.HOME
                        currentScreen = ShellScreen.ARTIST
                    },
                    userDisplayName = userDisplayName,
                    userAvatarUrl = userAvatarUrl
                )
            }
            ShellScreen.SEARCH -> {
                SpotifySearchScreen(
                    onCategoryClick = { category ->
                        activePlaylistTitle = category
                        activePlaylistUri = "spotify:search:$category"
                        previousScreen = ShellScreen.SEARCH
                        currentScreen = ShellScreen.PLAYLIST
                        playerListener?.onLoadPlaylist("spotify:search:$category")
                    },
                    onAlbumClick = { title, artist -> openAlbum(title, artist) },
                    onArtistClick = { artist ->
                        activeArtistName = artist
                        previousScreen = ShellScreen.SEARCH
                        currentScreen = ShellScreen.ARTIST
                    },
                    onPlaylistClick = { title, uri ->
                        activePlaylistTitle = title
                        activePlaylistUri = uri
                        previousScreen = ShellScreen.SEARCH
                        currentScreen = ShellScreen.PLAYLIST
                        if (uri.isNotEmpty()) {
                            playerListener?.onLoadPlaylist(uri)
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
                    onSearchQueryChange = { query -> playerListener?.onSearch(query) },
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
                        activePlaylistTitle = itemTitle
                        activePlaylistUri = itemUri
                        previousScreen = ShellScreen.LIBRARY
                        currentScreen = ShellScreen.PLAYLIST
                        if (itemUri.isNotEmpty()) {
                            playerListener?.onLoadPlaylist(itemUri)
                        }
                    },
                    onAlbumClick = { title, artist -> openAlbum(title, artist) },
                    onArtistClick = { artist ->
                        activeArtistName = artist
                        previousScreen = ShellScreen.LIBRARY
                        currentScreen = ShellScreen.ARTIST
                    },
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
                SpotifyPlaylistScreen(
                    title = activePlaylistTitle,
                    description = "This is $activePlaylistTitle. The essential tracks, all in one place.",
                    creator = if (activePlaylistTitle.contains("This Is") || activePlaylistTitle.contains("Today's")) "Spotify" else (userDisplayName ?: "中 • OV32LO2D • 中"),
                    saveCount = "6,482,109 saves",
                    tracks = playlistTracks ?: sampleTracks,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    headerColor = Color(0xFF144D3F),
                    coverUrl = activePlaylistCoverUrl ?: playlistTracks?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
                    onBackClick = { currentScreen = previousScreen },
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
            ShellScreen.ALBUM -> {
                SpotifyAlbumScreen(
                    albumTitle = activeAlbumTitle,
                    artistName = activeAlbumArtist,
                    releaseYear = activeAlbumYear,
                    releaseDate = activeAlbumReleaseDate,
                    coverColor = activeAlbumColor,
                    tracks = activeAlbumTracks,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    coverUrl = activeAlbumCoverUrl,
                    moreByArtist = listOf(
                        ArtistRelease("After Hours", "2020", "Album", Color(0xFF5A1A22), imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
                        ArtistRelease("Dawn FM", "2022", "Album", Color(0xFF243B55), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734ab25200366c1b3f942d937a"),
                        ArtistRelease("Starboy", "2016", "Album", Color(0xFF8E0E00), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
                        ArtistRelease("Timeless", "2024", "Single", Color(0xFF333333), imageUrl = "https://i.scdn.co/image/ab67616d0000b273b0dd6a5cd1dec96c4119c262")
                    ),
                    onBackClick = { currentScreen = previousScreen },
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
                    onAlbumOptionsClick = { isAlbumOptionsOpen = true },
                    onArtistClick = { artist ->
                        activeArtistName = artist
                        previousScreen = currentScreen
                        currentScreen = ShellScreen.ARTIST
                    },
                    onAlbumClick = { title ->
                        openAlbum(title, activeAlbumArtist)
                    }
                )
            }
            ShellScreen.ARTIST -> {
                SpotifyArtistScreen(
                    artistName = activeArtistName,
                    monthlyListeners = "104,239,812",
                    heroColor = Color(0xFF6B1A24),
                    headerImageUrl = activeArtistAvatarUrl,
                    popularTracks = sampleTracks.take(5).map { PopularTrack(it, "3,119,402,118") },
                    releases = listOf(
                        ArtistRelease("After Hours", "2020", "Album", Color(0xFF5A1A22), imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
                        ArtistRelease("Dawn FM", "2022", "Album", Color(0xFF243B55), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734ab25200366c1b3f942d937a"),
                        ArtistRelease("Starboy", "2016", "Album", Color(0xFF8E0E00), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
                        ArtistRelease("Timeless", "2024", "Single", Color(0xFF333333), imageUrl = "https://i.scdn.co/image/ab67616d0000b273b0dd6a5cd1dec96c4119c262")
                    ),
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onBackClick = { currentScreen = previousScreen },
                    onPlayPauseClick = {
                        if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                    },
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
                    onArtistOptionsClick = { isArtistOptionsOpen = true },
                    onReleaseClick = { release ->
                        openAlbum(release.title, activeArtistName)
                    }
                )
            }
            ShellScreen.SETTINGS -> {
                SettingsScreen(
                    onBackClick = { currentScreen = previousScreen },
                    isComposeUiEnabled = isComposeUiEnabled,
                    onToggleComposeUi = onToggleComposeUi,
                    onOpenSpotiDuckSettings = { onOpenSpotiDuckSettings?.invoke() }
                )
            }
            ShellScreen.NOTIFICATIONS -> {
                NotificationsScreen(
                    onBackClick = { currentScreen = previousScreen },
                    onPlayClick = { title ->
                        activePlaylistTitle = title
                        previousScreen = ShellScreen.NOTIFICATIONS
                        currentScreen = ShellScreen.PLAYLIST
                    }
                )
            }
            ShellScreen.HISTORY -> {
                HistoryScreen(
                    onBackClick = { currentScreen = previousScreen },
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
        }

        // Bottom UI Layer: Floating Mini Player + Bottom Navigation Bar (vertical_main.xml)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xD9000000), // bottom_gradient: #d9000000 (85% black)
                            EncoreColors.PureBlack // bottom_gradient_black_fade & window insets
                        )
                    )
                )
        ) {
            // Floating Mini Player (docked above bottom nav)
            SpotifyMiniPlayer(
                title = currentTrackTitle,
                artist = currentArtist,
                isPlaying = isPlaying,
                progress = currentProgress,
                isLiked = isLiked,
                coverUrl = currentCoverUrl,
                connectedDeviceName = connectedDevice,
                onPlayPauseClick = {
                    if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                },
                onLikeClick = {
                    if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = !localIsLiked
                },
                onDeviceClick = { isDeviceSheetOpen = true },
                onBarClick = { isPlayerExpanded = true },
                onSkipNextClick = handleSkipNext,
                onSkipPreviousClick = handleSkipPrevious
            )

            // Bottom Navigation Bar (design_bottom_navigation_height = 56dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EncoreColors.PureBlack)
                    .navigationBarsPadding()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = currentNavIndex == index
                    SpotifyBottomNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = {
                            selectedNavIndex = index
                            when (index) {
                                0 -> currentScreen = ShellScreen.HOME
                                1 -> currentScreen = ShellScreen.SEARCH
                                2 -> currentScreen = ShellScreen.LIBRARY
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Full Now Playing Screen (Sliding Overlay matching nowplaying_activity_enter/exit 150ms/200ms)
        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = EncoreAnimation.NowPlayingEnterDuration,
                    easing = EncoreAnimation.DecelerateInterpolator
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = EncoreAnimation.NowPlayingExitDuration,
                    easing = EncoreAnimation.DecelerateInterpolator
                )
            )
        ) {
            SpotifyNowPlayingScreen(
                title = currentTrackTitle,
                artist = currentArtist,
                albumOrPlaylistContext = if (currentScreen == ShellScreen.PLAYLIST) activePlaylistTitle else "Today's Top Hits",
                isPlaying = isPlaying,
                progress = currentProgress,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                isLiked = isLiked,
                isExplicit = currentTrackTitle.contains("Starboy", ignoreCase = true) ||
                        currentTrackTitle.contains("One Of The Girls", ignoreCase = true) ||
                        currentTrackTitle.contains("The Hills", ignoreCase = true) ||
                        currentTrackTitle.contains("Heartless", ignoreCase = true) ||
                        currentTrackTitle.contains("Snowchild", ignoreCase = true),
                shuffleMode = currentShuffleMode,
                repeatMode = currentRepeatMode,
                connectedDeviceName = connectedDevice,
                coverUrl = currentCoverUrl,
                onCollapseClick = { isPlayerExpanded = false },
                onPlayPauseClick = {
                    if (playerListener != null) playerListener.onPlayPause() else localIsPlaying = !localIsPlaying
                },
                onSkipNextClick = handleSkipNext,
                onSkipPreviousClick = handleSkipPrevious,
                onLikeClick = {
                    if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = !localIsLiked
                },
                onShuffleClick = {
                    if (playerListener != null) {
                        playerListener.onShuffleToggle()
                    } else {
                        localShuffleMode = localShuffleMode.next()
                    }
                },
                onRepeatClick = {
                    if (playerListener != null) {
                        playerListener.onRepeatToggle()
                    } else {
                        localRepeatMode = localRepeatMode.next()
                    }
                },
                onSeek = { progress ->
                    if (playerListener != null) {
                        val targetMs = (progress * durationMs).toLong()
                        playerListener.onSeek(targetMs)
                    } else {
                        localProgress = progress
                        localPositionMs = (progress * durationMs).toLong()
                    }
                },
                onMoreClick = {
                    val currentTrack = sampleTracks.find { it.title == currentTrackTitle }
                        ?: TrackItem(currentTrackTitle, currentArtist, isLiked)
                    selectedTrackForOptions = currentTrack
                    isTrackOptionsOpen = true
                },
                onQueueClick = { isQueueSheetOpen = true },
                onDeviceClick = { isDeviceSheetOpen = true }
            )
        }

        // Bottom Sheets & Dialogs
        ProfileDrawerSheet(
            isOpen = isProfileDrawerOpen,
            onDismiss = { isProfileDrawerOpen = false },
            onViewProfileClick = {},
            onWhatsNewClick = {
                previousScreen = currentScreen
                currentScreen = ShellScreen.NOTIFICATIONS
            },
            onListeningHistoryClick = {
                previousScreen = currentScreen
                currentScreen = ShellScreen.HISTORY
            },
            onSettingsClick = handleOpenSettings,
            userDisplayName = userDisplayName,
            userAvatarUrl = userAvatarUrl
        )

        TrackOptionsSheet(
            track = selectedTrackForOptions,
            isOpen = isTrackOptionsOpen,
            onDismiss = { isTrackOptionsOpen = false },
            onLikeToggle = { liked ->
                if (playerListener != null) playerListener.onLikeToggle() else localIsLiked = liked
            },
            onAddToPlaylist = {},
            onAddToQueue = {
                selectedTrackForOptions?.let { userQueueList.add(it) }
            },
            onViewArtist = { artist ->
                activeArtistName = artist
                previousScreen = currentScreen
                currentScreen = ShellScreen.ARTIST
            },
            onViewAlbum = { album ->
                openAlbum(album, selectedTrackForOptions?.artist ?: "")
            },
            onShare = {},
            onSleepTimerClick = { isSleepTimerOpen = true }
        )

        PlaylistOptionsSheet(
            title = activePlaylistTitle,
            creator = "Spotify",
            coverUrl = activePlaylistCoverUrl,
            isOpen = isPlaylistOptionsOpen,
            onDismiss = { isPlaylistOptionsOpen = false }
        )

        AlbumOptionsSheet(
            title = activeAlbumTitle,
            artist = activeAlbumArtist,
            coverUrl = activeAlbumCoverUrl,
            isOpen = isAlbumOptionsOpen,
            onDismiss = { isAlbumOptionsOpen = false },
            onGoToArtist = { artist ->
                activeArtistName = artist
                previousScreen = currentScreen
                currentScreen = ShellScreen.ARTIST
            }
        )

        ArtistOptionsSheet(
            artistName = activeArtistName,
            avatarUrl = activeArtistAvatarUrl,
            isOpen = isArtistOptionsOpen,
            onDismiss = { isArtistOptionsOpen = false }
        )

        LibrarySortSheet(
            isOpen = isSortSheetOpen,
            currentSort = currentSortOption,
            onSortSelected = { selected -> currentSortOption = selected },
            onDismiss = { isSortSheetOpen = false }
        )

        LibraryCreateSheet(
            isOpen = isCreateSheetOpen,
            onCreatePlaylist = {
                activePlaylistTitle = "My Playlist #1"
                previousScreen = ShellScreen.LIBRARY
                currentScreen = ShellScreen.PLAYLIST
            },
            onCreateBlend = {
                activePlaylistTitle = "Duck + Friend Blend"
                previousScreen = ShellScreen.LIBRARY
                currentScreen = ShellScreen.PLAYLIST
            },
            onDismiss = { isCreateSheetOpen = false }
        )

        if (isDeviceSheetOpen) {
            SpotifyConnectDeviceSheet(
                onDismissRequest = { isDeviceSheetOpen = false },
                currentDeviceName = connectedDevice,
                onSelectDevice = { device ->
                    localDevice = device.name
                    isDeviceSheetOpen = false
                }
            )
        }

        if (isQueueSheetOpen) {
            SpotifyQueueSheet(
                currentTrackTitle = currentTrackTitle,
                currentTrackArtist = currentArtist,
                queueSourceContext = activePlaylistTitle,
                userQueue = userQueueList,
                nextFromContext = sampleTracks.filter { it.title != currentTrackTitle },
                onDismissRequest = { isQueueSheetOpen = false },
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
                    userQueueList.remove(track)
                },
                onRemoveFromQueue = { track ->
                    userQueueList.remove(track)
                },
                onClearQueue = {
                    userQueueList.clear()
                }
            )
        }

        if (isSleepTimerOpen) {
            SleepTimerSheet(
                activeOption = activeSleepTimer,
                onOptionSelected = { option -> activeSleepTimer = option },
                onDismiss = { isSleepTimerOpen = false }
            )
        }
    }
}

/**
 * Spotify Bottom Navigation Tab Item with signature bouncy scale spring physics and icon crossfade.
 */
@Composable
private fun SpotifyBottomNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val bounceScale = remember { Animatable(1f) }

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) EncoreColors.TextPrimary else EncoreColors.TextSecondary,
        animationSpec = tween(durationMillis = 150),
        label = "bottomNavTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                coroutineScope.launch {
                    bounceScale.snapTo(0.85f)
                    bounceScale.animateTo(
                        1.0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                onClick()
            }
    ) {
        val iconToRender = if (isSelected) item.activeIconRes else item.iconRes
        Box(
            modifier = Modifier.scale(bounceScale.value),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = iconToRender,
                animationSpec = tween(durationMillis = 150, easing = EncoreAnimation.DecelerateInterpolator),
                label = "bottomNavIconCrossfade"
            ) { targetIcon ->
                Icon(
                    painter = painterResource(id = targetIcon),
                    contentDescription = item.title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = item.title,
            style = SpotifyTheme.typography.marginal.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = iconTint
            ),
            modifier = Modifier.padding(top = 2.dp)
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
