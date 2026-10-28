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
import androidx.compose.runtime.mutableStateListOf
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
import com.spotiduck.compose.screens.SpotifyCategoryScreen
import com.spotiduck.compose.screens.getCategoryColor
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
    CATEGORY,
    SETTINGS,
    NOTIFICATIONS,
    HISTORY
}

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
    val artistAvatarUrl: String? = null
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
    libraryItems: List<LibraryItem>? = null,
    searchResults: List<TrackItem>? = null,
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
    var activePlaylistTitle by remember { mutableStateOf("This Is The Weeknd") }
    var activePlaylistUri by remember { mutableStateOf("") }
    var activePlaylistCoverUrl by remember { mutableStateOf<String?>("https://i.scdn.co/image/ab67706f000000021c3fa36c4b92b67f40192e44") }
    var activeAlbumCoverUrl by remember { mutableStateOf<String?>("https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36") }
    var activeArtistAvatarUrl by remember { mutableStateOf<String?>("https://i.scdn.co/image/ab6761610000e5eb968a3770e060cf56306e9f1a") }
    var activeCategoryTitle by remember { mutableStateOf("Pop") }
    var activeCategoryColor by remember { mutableStateOf(Color(0xFF148A08)) }

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
                artistAvatarUrl = activeArtistAvatarUrl
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
        val foundEntity = libraryItems?.find { it.title.equals(title, ignoreCase = true) }
        val resolvedCover = foundEntity?.imageUrl

        val albumInfo = when {
            title.contains("Starboy", ignoreCase = true) -> {
                AlbumInfo("2016", "November 25, 2016", Color(0xFF4F46E5), "spotify:album:2ODvWsOgouMbaA5xf0RkJe")
            }
            title.contains("Random Access Memories", ignoreCase = true) -> {
                AlbumInfo("2013", "May 17, 2013", Color(0xFF6B7280), "spotify:album:4m2880jivSbbyEGAKfITCa")
            }
            title.contains("Dawn FM", ignoreCase = true) -> {
                AlbumInfo("2022", "January 7, 2022", Color(0xFF243B55), "spotify:album:2nLOHgz97rqx9cqvhCyZa5")
            }
            title.contains("After Hours", ignoreCase = true) -> {
                AlbumInfo("2020", "March 20, 2020", Color(0xFF901A1E), "spotify:album:4yP0hdKOgrTwcyWu21s0Ad")
            }
            title.contains("GLORY", ignoreCase = true) -> {
                AlbumInfo("2024", "August 26, 2024", Color(0xFF1E3264), "spotify:album:3iKxL3h4a6G7fWqU4v1I8g")
            }
            title.contains("International Villager", ignoreCase = true) -> {
                AlbumInfo("2011", "November 11, 2011", Color(0xFF8D67AB), "spotify:album:2jP3v1C6m5p6e3U2r7a2B1")
            }
            else -> {
                AlbumInfo("2024", "2024", Color(0xFF333333), foundEntity?.uri?.takeIf { it.isNotEmpty() } ?: "")
            }
        }
        activeAlbumYear = albumInfo.year
        activeAlbumReleaseDate = albumInfo.date
        activeAlbumColor = albumInfo.color
        if (resolvedCover != null) {
            activeAlbumCoverUrl = resolvedCover
        } else if (title.contains("Starboy", ignoreCase = true)) {
            activeAlbumCoverUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"
        } else if (title.contains("After Hours", ignoreCase = true)) {
            activeAlbumCoverUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"
        }

        val albumUriToLoad = foundEntity?.uri?.takeIf { it.startsWith("spotify:album:") } ?: albumInfo.uri
        playerListener?.onLoadAlbum(albumUriToLoad, title, activeAlbumArtist)

        pushCurrentState()
        currentScreen = ShellScreen.ALBUM
    }

    val openArtist: (String, String?) -> Unit = { artist, imageUrl ->
        activeArtistName = artist
        val foundEntity = libraryItems?.find { it.title.equals(artist, ignoreCase = true) }
        val resolvedUrl = imageUrl
            ?: foundEntity?.imageUrl
            ?: when {
                artist.contains("Honey Singh", ignoreCase = true) -> "https://i.scdn.co/image/ab6761610000e5eb2a921d72545592fc624c43ee"
                artist.contains("Weeknd", ignoreCase = true) -> "https://i.scdn.co/image/ab6761610000e5eb968a3770e060cf56306e9f1a"
                artist.contains("Arijit", ignoreCase = true) -> "https://i.scdn.co/image/ab6761610000e5eb0261696c5df3be99da6ed3f3"
                artist.contains("Drake", ignoreCase = true) -> "https://i.scdn.co/image/ab6761610000e5eb4293385d324db8558179afd9"
                artist.contains("Taylor Swift", ignoreCase = true) -> "https://i.scdn.co/image/ab6761610000e5eb5a00969a4698c3132a15fbb0"
                artist.contains("Stephen Sanchez", ignoreCase = true) -> "https://i.scdn.co/image/ab6761610000e5ebb08d4b38bfd2c0b71e191cf0"
                else -> activeArtistAvatarUrl ?: "https://i.scdn.co/image/ab6761610000e5eb2a921d72545592fc624c43ee"
            }
        activeArtistAvatarUrl = resolvedUrl

        val artistUriToLoad = foundEntity?.uri?.takeIf { it.startsWith("spotify:artist:") }
            ?: when {
                artist.contains("Honey Singh", ignoreCase = true) -> "spotify:artist:7uIbLdzzRuQIngLFcu8mgr"
                artist.contains("Weeknd", ignoreCase = true) -> "spotify:artist:1Xyo4u8uXC1ZmMpatF05PJ"
                artist.contains("Arijit", ignoreCase = true) -> "spotify:artist:4YRxDV8wJFPHPTeXepOstw"
                artist.contains("Stephen Sanchez", ignoreCase = true) -> "spotify:artist:1NZsSd7h4SZ8cuUtwi0GQZ"
                else -> ""
            }
        playerListener?.onLoadArtist(artistUriToLoad, artist)

        pushCurrentState()
        currentScreen = ShellScreen.ARTIST
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
                        if (playlist.contains("After Hours", ignoreCase = true) ||
                            playlist.contains("Random Access Memories", ignoreCase = true) ||
                            playlist.contains("Starboy", ignoreCase = true)
                        ) {
                            openAlbum(playlist, "")
                        } else {
                            pushCurrentState()
                            activePlaylistTitle = playlist
                            activePlaylistUri = uri
                            currentScreen = ShellScreen.PLAYLIST
                            if (uri.isNotEmpty()) {
                                playerListener?.onLoadPlaylist(uri)
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
                    },
                    onAlbumClick = { title, artist -> openAlbum(title, artist) },
                    onArtistClick = { artist -> openArtist(artist, null) },
                    onPlaylistClick = { title, uri ->
                        pushCurrentState()
                        activePlaylistTitle = title
                        activePlaylistUri = uri
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
                        pushCurrentState()
                        activePlaylistTitle = itemTitle
                        activePlaylistUri = itemUri
                        currentScreen = ShellScreen.PLAYLIST
                        if (itemUri.isNotEmpty()) {
                            playerListener?.onLoadPlaylist(itemUri)
                        }
                    },
                    onAlbumClick = { title, artist -> openAlbum(title, artist) },
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
                        playlistTracks ?: sampleTracks
                    }
                    SpotifyPlaylistScreen(
                        title = activePlaylistTitle,
                        description = "This is $activePlaylistTitle. The essential tracks, all in one place.",
                        creator = if (activePlaylistTitle.contains("This Is") || activePlaylistTitle.contains("Today's")) "Spotify" else (userDisplayName ?: "中 • OV32LO2D • 中"),
                        saveCount = "6,482,109 saves",
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
                        moreByArtist = listOf(
                            ArtistRelease("After Hours", "2020", "Album", Color(0xFF5A1A22), imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
                            ArtistRelease("Dawn FM", "2022", "Album", Color(0xFF243B55), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734ab25200366c1b3f942d937a"),
                            ArtistRelease("Starboy", "2016", "Album", Color(0xFF8E0E00), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
                            ArtistRelease("Timeless", "2024", "Single", Color(0xFF333333), imageUrl = "https://i.scdn.co/image/ab67616d0000b273b0dd6a5cd1dec96c4119c262")
                        ),
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
                        onArtistClick = { artist -> openArtist(artist, null) },
                        onAlbumClick = { title ->
                            openAlbum(title, activeAlbumArtist)
                        }
                    )
                }
            }
            ShellScreen.ARTIST -> {
                androidx.compose.runtime.key(activeArtistName) {
                    val isHoneySingh = activeArtistName.contains("Honey Singh", ignoreCase = true)
                    val fallbackArtistTracks = if (isHoneySingh) {
                        listOf(
                            PopularTrack(TrackItem("Millionaire", "Yo Yo Honey Singh", imageUrl = "https://i.scdn.co/image/ab67616d0000b2736b4122d64bd9f0298a0ebc82"), "312,410,950"),
                            PopularTrack(TrackItem("Brown Rang", "Yo Yo Honey Singh", imageUrl = "https://i.scdn.co/image/ab67616d0000b273295843b04c8f000bcaaa7c88"), "284,519,040"),
                            PopularTrack(TrackItem("Blue Eyes", "Yo Yo Honey Singh", imageUrl = "https://i.scdn.co/image/ab67616d0000b273c52e466b033aa7cb08cf119a"), "255,109,820"),
                            PopularTrack(TrackItem("Dope Shope", "Yo Yo Honey Singh, Deep Money", imageUrl = "https://i.scdn.co/image/ab67616d0000b273295843b04c8f000bcaaa7c88"), "220,194,520"),
                            PopularTrack(TrackItem("Love Dose", "Yo Yo Honey Singh", imageUrl = "https://i.scdn.co/image/ab67616d0000b273c52e466b033aa7cb08cf119a"), "198,341,080")
                        )
                    } else {
                        sampleTracks.take(5).map { PopularTrack(it, "3,119,402,118") }
                    }

                    val fallbackReleases = if (isHoneySingh) {
                        listOf(
                            ArtistRelease("GLORY", "2024", "Album", Color(0xFF1E3264), imageUrl = "https://i.scdn.co/image/ab67616d0000b2736b4122d64bd9f0298a0ebc82"),
                            ArtistRelease("International Villager", "2011", "Album", Color(0xFF8D67AB), imageUrl = "https://i.scdn.co/image/ab67616d0000b273295843b04c8f000bcaaa7c88"),
                            ArtistRelease("Desi Kalakaar", "2014", "Album", Color(0xFFBA5D07), imageUrl = "https://i.scdn.co/image/ab67616d0000b273c52e466b033aa7cb08cf119a"),
                            ArtistRelease("Millionaire", "2024", "Single", Color(0xFFE91429), imageUrl = "https://i.scdn.co/image/ab67616d0000b2736b4122d64bd9f0298a0ebc82")
                        )
                    } else {
                        listOf(
                            ArtistRelease("After Hours", "2020", "Album", Color(0xFF5A1A22), imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
                            ArtistRelease("Dawn FM", "2022", "Album", Color(0xFF243B55), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734ab25200366c1b3f942d937a"),
                            ArtistRelease("Starboy", "2016", "Album", Color(0xFF8E0E00), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
                            ArtistRelease("Timeless", "2024", "Single", Color(0xFF333333), imageUrl = "https://i.scdn.co/image/ab67616d0000b273b0dd6a5cd1dec96c4119c262")
                        )
                    }

                    val effectiveArtistTracks = if (!artistTracks.isNullOrEmpty()) artistTracks else fallbackArtistTracks
                    val effectiveReleases = if (!artistReleases.isNullOrEmpty()) artistReleases else fallbackReleases
                    val effectiveListeners = artistMonthlyListeners?.takeIf { it.isNotBlank() }
                        ?: (if (isHoneySingh) "20,742,810" else "104,239,812")

                    SpotifyArtistScreen(
                        artistName = activeArtistName,
                        monthlyListeners = effectiveListeners,
                        heroColor = if (isHoneySingh) Color(0xFF2B3A48) else Color(0xFF6B1A24),
                        headerImageUrl = activeArtistAvatarUrl,
                        popularTracks = effectiveArtistTracks,
                        releases = effectiveReleases,
                        currentTrackTitle = currentTrackTitle,
                        isPlaying = isPlaying,
                        onBackClick = handleBack,
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
                            playerListener?.onLoadPlaylist(uri)
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
                    }
                )
            }
            ShellScreen.SETTINGS -> {
                SettingsScreen(
                    onBackClick = handleBack,
                    isComposeUiEnabled = isComposeUiEnabled,
                    onToggleComposeUi = onToggleComposeUi,
                    onOpenSpotiDuckSettings = { onOpenSpotiDuckSettings?.invoke() }
                )
            }
            ShellScreen.NOTIFICATIONS -> {
                NotificationsScreen(
                    onBackClick = handleBack,
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
                                0 -> {
                                    if (currentScreen != ShellScreen.HOME) {
                                        navBackStack.clear()
                                        currentScreen = ShellScreen.HOME
                                    }
                                }
                                1 -> {
                                    if (currentScreen != ShellScreen.SEARCH) {
                                        navBackStack.clear()
                                        currentScreen = ShellScreen.SEARCH
                                    }
                                }
                                2 -> {
                                    if (currentScreen != ShellScreen.LIBRARY) {
                                        navBackStack.clear()
                                        currentScreen = ShellScreen.LIBRARY
                                    }
                                }
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
                pushCurrentState()
                currentScreen = ShellScreen.NOTIFICATIONS
            },
            onListeningHistoryClick = {
                pushCurrentState()
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
            onViewArtist = { artist -> openArtist(artist, null) },
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
            onGoToArtist = { artist -> openArtist(artist, null) }
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
                pushCurrentState()
                activePlaylistTitle = "My Playlist #1"
                currentScreen = ShellScreen.PLAYLIST
            },
            onCreateBlend = {
                pushCurrentState()
                activePlaylistTitle = "Duck + Friend Blend"
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
