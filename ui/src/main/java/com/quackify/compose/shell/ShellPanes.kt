package com.quackify.compose.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.EpisodeRow
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.backend.model.ShowDetail
import com.quackify.backend.model.collectionDuration
import com.quackify.backend.model.collectionDurationIsEstimate
import com.quackify.compose.R
import com.quackify.compose.components.DetailPageSkeleton
import com.quackify.compose.components.EncoreChip
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.components.TrackListSkeleton
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.home.HomeShelf
import com.quackify.compose.home.QuickAccessItem
import com.quackify.compose.home.RecommendationCard
import com.quackify.compose.home.EncoreHomeScreen
import com.quackify.compose.home.toArtistRelease
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.EncoreLibraryScreen
import com.quackify.compose.playlist.EncorePlaylistScreen
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.HistoryScreen
import com.quackify.compose.screens.LogViewerScreen
import com.quackify.compose.screens.NotificationsScreen
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.SettingsScreen
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.screens.EncoreAlbumScreen
import com.quackify.compose.screens.EncoreArtistScreen
import com.quackify.compose.screens.EncoreBrowseScreen
import com.quackify.compose.screens.EncoreEpisodeScreen
import com.quackify.compose.screens.EncoreNotification
import com.quackify.compose.screens.EncoreShowScreen
import com.quackify.compose.screens.getCategoryColor
import com.quackify.compose.search.CategoryCard
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.search.EncoreSearchScreen
import com.quackify.compose.sheets.EditPlaylistSheet
import com.quackify.compose.sheets.LibrarySortOption
import com.quackify.compose.sheets.SortDirection
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.util.rememberDominantColor
import kotlinx.coroutines.launch

internal data class PlayerIdentity(
    val trackLoaded: Boolean,
    val title: String,
    val artist: String,
)

internal fun playerIdentityOf(
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

internal fun nowPlayingContext(
    trackLoaded: Boolean,
    origin: PlaybackOrigin?,
    screen: ShellScreen,
    playlistTitle: String,
    albumTitle: String,
    artistName: String,
    searchQuery: String,
): Pair<String, String> {
    fun searchSub(query: String): String {
        val q = query.trim()
        return if (q.isBlank()) "Search" else "$q in Search"
    }
    return when {
        !trackLoaded -> "Nothing playing" to "Pick a song to get started"
        origin != null -> when (origin.screen) {
            ShellScreen.SEARCH -> "Playing from search" to searchSub(origin.label)
            ShellScreen.ALBUM -> "Playing from album" to origin.label
            ShellScreen.ARTIST -> "Playing from artist" to origin.label
            ShellScreen.HOME -> "Playing from home" to origin.label
            else -> "Playing from playlist" to origin.label
        }
        screen == ShellScreen.SEARCH -> "Playing from search" to searchSub(searchQuery)
        screen == ShellScreen.PLAYLIST -> "Playing from playlist" to playlistTitle
        screen == ShellScreen.ALBUM -> "Playing from album" to albumTitle
        screen == ShellScreen.ARTIST -> "Playing from artist" to artistName
        else -> "Playing from playlist" to playlistTitle
    }
}

@Composable
internal fun ShellLoadingState(loading: Boolean, onRetry: () -> Unit) {
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
                style = EncoreTheme.typography.titleMedium,
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
fun PreviewEncoreAppShell() {
    EncoreAppTheme {
        EncoreAppShell()
    }
}

@Composable
internal fun ShellSettingsPane(
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

@Composable
internal fun ShellLogsPane(
    onBackClick: () -> Unit
) {
    LogViewerScreen(
        onBackClick = onBackClick
    )
}

@Composable
internal fun ShellNotificationsPane(
    onBackClick: () -> Unit,
    notifications: List<EncoreNotification>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onPlay: (EncoreNotification) -> Unit
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

@Composable
internal fun ShellAlbumPane(
    activeAlbumTitle: String,
    activeAlbumArtist: String,
    activeAlbumUri: String,
    activeAlbumCoverUrl: String?,
    activeAlbumYear: String,
    activeAlbumReleaseDate: String,
    activeAlbumColor: Color,
    albumDetail: AlbumDetail?,
    albumTracks: List<TrackItem>?,
    albumLoading: Boolean,
    albumFailed: Boolean,
    onRetryAlbum: (() -> Unit)?,
    currentTrackTitle: String,
    isPlaying: Boolean,
    playerListener: ComposePlayerListener?,
    handleBack: () -> Unit,
    handlePlayPause: () -> Unit,
    handleTrackOptions: (TrackItem) -> Unit,
    openArtist: (String, String) -> Unit,
    openAlbum: (String, String, String) -> Unit,
    onAlbumOptions: () -> Unit,
    onPlaybackOrigin: (PlaybackOrigin?) -> Unit = {}
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
        EncoreAlbumScreen(
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
            isLoading = albumLoading,
            loadFailed = albumFailed,
            onRetry = { onRetryAlbum?.invoke() },
            coverUrl = activeAlbumCoverUrl ?: albumDetail?.imageUrl?.takeIf { it.isNotBlank() },
            artistImageUrl = albumDetail?.artistImageUrl?.takeIf { it.isNotBlank() },
            moreByArtist = remember(albumDetail) {
                albumDetail?.moreByArtist?.map { it.toArtistRelease() }.orEmpty()
            },
            onBackClick = handleBack,
            onPlayPauseClick = handlePlayPause,
            onTrackSelect = { track ->
                onPlaybackOrigin(PlaybackOrigin(ShellScreen.ALBUM, activeAlbumTitle))
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

@Composable
internal fun ShellSearchPane(
    searchResults: List<TrackItem>?,
    searchData: SearchResults?,
    searchRecent: List<SearchHistoryItem>,
    searchSuggestions: List<TrackItem>,
    searchSuggestionHits: List<SearchHit>,
    searchLoading: Boolean,
    searchFailed: Boolean,
    searchSavedOverrides: Map<String, Boolean>,
    browseCategories: List<BrowseCard>?,
    userDisplayName: String,
    userAvatarUrl: String,
    currentTrackTitle: String,
    currentTrackUri: String,
    isPlaying: Boolean,
    searchFieldFocusRequester: FocusRequester,
    initialQuery: String,
    playerListener: ComposePlayerListener?,
    onSearchQueryChange: ((String) -> Unit)?,
    onRetrySearch: () -> Unit,
    onClearRecentSearches: () -> Unit,
    onRemoveRecentSearch: (SearchHistoryItem) -> Unit,
    onToggleSaved: (String, Boolean) -> Unit,
    onLoadSearchSection: (suspend (SearchKind, Int) -> List<SearchHit>)?,
    onCategoryClick: (CategoryCard) -> Unit,
    onAlbumClick: (String, String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onPodcastClick: (String, String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenPlaylist: (String, String) -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onProfileClick: () -> Unit,
) {
    EncoreSearchScreen(
        onCategoryClick = onCategoryClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onPodcastClick = onPodcastClick,
        onEpisodeClick = { _, uri -> if (uri.isNotEmpty()) onOpenEpisode(uri) },
        onShowClick = { _, uri -> if (uri.isNotEmpty()) onOpenShow(uri) },
        onPlaylistClick = onOpenPlaylist,
        onTrackSelect = onTrackSelect,
        onTrackOptionsClick = onTrackOptionsClick,
        onProfileClick = onProfileClick,
        searchFieldFocusRequester = searchFieldFocusRequester,
        initialQuery = initialQuery,
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
        onRetry = onRetrySearch,
        onClearRecent = onClearRecentSearches,
        onRemoveRecent = onRemoveRecentSearch,
        onToggleSaved = onToggleSaved,
        savedOverrides = searchSavedOverrides,
        onLoadSection = onLoadSearchSection,
        browseCategories = remember(browseCategories) {
            browseCategories.orEmpty().map { card ->
                CategoryCard(
                    title = card.title,
                    color = parseHexColor(card.colorHex) ?: getCategoryColor(card.title),
                    uri = card.uri,
                    imageUrl = card.imageUrl.takeIf { it.isNotBlank() },
                    subtitle = card.subtitle
                )
            }
        },
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        currentTrackTitle = currentTrackTitle,
        currentTrackUri = currentTrackUri,
        isPlaying = isPlaying
    )
}

@Composable
internal fun ShellArtistPane(
    artistName: String,
    artistAvatarUrl: String?,
    artistUri: String,
    artistDetail: ArtistDetail?,
    artistTracks: List<PopularTrack>?,
    artistReleases: List<ArtistRelease>?,
    artistMonthlyListeners: String?,
    artistLoading: Boolean,
    artistFailed: Boolean,
    onRetryArtist: (() -> Unit)?,
    artistFollowed: Boolean?,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onFollowToggle: (String, Boolean) -> Unit,
    onReleaseClick: (ArtistRelease) -> Unit,
    onAboutClick: () -> Unit,
    onDiscographyClick: () -> Unit,
    onOptionsClick: () -> Unit,
) {
    androidx.compose.runtime.key(artistName) {
        EncoreArtistScreen(
            artistName = artistName,
            monthlyListeners = artistMonthlyListeners?.takeIf { it.isNotBlank() }.orEmpty(),
            heroColor = artistDetail?.colorHex?.let { parseHexColor(it) } ?: EncoreColors.Card,
            headerImageUrl = artistDetail?.headerImageUrl?.takeIf { it.isNotBlank() }
                ?: artistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                ?: artistAvatarUrl,
            popularTracks = artistTracks.orEmpty(),
            releases = artistReleases.orEmpty(),
            albums = artistDetail?.albums.orEmpty().map { it.toArtistRelease() },
            singles = artistDetail?.singles.orEmpty().map { it.toArtistRelease() },
            currentTrackTitle = currentTrackTitle,
            isPlaying = isPlaying,
            biography = artistDetail?.biography.orEmpty(),
            verified = artistDetail?.verified == true,
            followers = artistDetail?.followers ?: 0L,
            worldRank = artistDetail?.worldRank ?: 0,
            topCities = artistDetail?.topCities.orEmpty(),
            gallery = artistDetail?.gallery.orEmpty(),
            isLoading = artistLoading,
            loadFailed = artistFailed,
            onRetry = { onRetryArtist?.invoke() },
            onBackClick = onBackClick,
            onPlayPauseClick = onPlayPauseClick,
            onTrackSelect = onTrackSelect,
            onTrackOptionsClick = onTrackOptionsClick,
            onArtistOptionsClick = onOptionsClick,
            isFollowing = artistFollowed == true,
            onFollowToggle = { follow ->
                if (artistUri.startsWith("spotify:artist:")) {
                    onFollowToggle(artistUri, follow)
                }
            },
            onReleaseClick = onReleaseClick,
            onAboutClick = onAboutClick,
            onDiscographyClick = onDiscographyClick
        )
    }
}

@Composable
internal fun ShellHomePane(
    quickAccessItems: List<QuickAccessItem>?,
    recommendations: List<RecommendationCard>?,
    shelves: List<HomeShelf>?,
    chips: List<HomeChip>,
    selectedFacet: String,
    greeting: String,
    loading: Boolean,
    libraryItems: List<LibraryItem>?,
    notifications: List<EncoreNotification>,
    userDisplayName: String,
    userAvatarUrl: String,
    onFacetSelected: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onCoverResolved: (String?) -> Unit,
    onPlayTrackShortcut: (String, String) -> Unit,
    onOpenAlbum: (String, String, String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenPlaylist: (String, String) -> Unit,
) {
    EncoreHomeScreen(
        onPlaylistClick = { playlist, uri ->
            val foundItem = quickAccessItems?.find { it.uri == uri || it.title == playlist }
            val foundRec = recommendations?.find { it.uri == uri || it.title == playlist }
            val foundLib = libraryItems?.find { it.uri == uri || it.title == playlist }
            onCoverResolved(foundItem?.imageUrl ?: foundRec?.imageUrl ?: foundLib?.imageUrl)
            if (uri.startsWith("spotify:album:")) {
                onOpenAlbum(playlist, "", uri)
            } else if (uri.startsWith("spotify:show:")) {
                onOpenShow(uri)
            } else if (uri.startsWith("spotify:episode:")) {
                onOpenEpisode(uri)
            } else if (uri.startsWith("spotify:artist:")) {
                onArtistClick(foundItem?.title ?: playlist, uri)
            } else if (uri.startsWith("spotify:track:")) {
                onPlayTrackShortcut(uri, foundItem?.title ?: playlist)
            } else {
                onOpenPlaylist(playlist, uri)
            }
        },
        onProfileClick = onOpenProfile,
        onNotificationsClick = onOpenNotifications,
        onHistoryClick = onOpenHistory,
        onSettingsClick = onOpenSettings,
        quickAccessItems = quickAccessItems,
        recommendations = recommendations,
        shelves = shelves,
        loading = loading,
        chips = chips,
        selectedFacet = selectedFacet,
        onFacetSelected = onFacetSelected,
        greeting = greeting,
        whatsNewCount = notifications.count { it.isNew },
        onArtistClick = onArtistClick,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl
    )
}

@Composable
internal fun ShellPlaylistPane(
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
    onSavePlaylistOrder: ((String, List<TrackItem>) -> Unit)?,
    onEditPlaylistDetails: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    onDeletePlaylist: ((String) -> Unit)?,
    onTogglePlaylistSaved: ((String, Boolean) -> Unit)?,
    handleBack: () -> Unit,
    handleTrackOptions: (TrackItem) -> Unit,
    shareEntity: (String) -> Unit,
    onLocalPlayPause: () -> Unit,
    onLocalTrack: (TrackItem) -> Unit,
    onPlaylistOptions: () -> Unit,
    onEditModeChange: (Boolean) -> Unit = {},
    onChangeTitle: (() -> Unit)? = null,
    onAddTracksClick: (() -> Unit)? = null,
    onEditCoverClick: (() -> Unit)? = null,
    onPlaybackOrigin: (PlaybackOrigin?) -> Unit = {}
) {
    androidx.compose.runtime.key(activePlaylistUri.ifEmpty { activePlaylistTitle }) {
        LaunchedEffect(playlistTracks, activePlaylistUri) {
            if (!playlistTracks.isNullOrEmpty()) {
                playerListener?.onListRendered(playlistTracks.map { it.uri })
            }
        }
        val resolvedPlaylistCover = activePlaylistCoverUrl ?: playlistTracks?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
        val playlistTotal = playlistDetail?.total ?: 0
        val playlistDurations = remember(playlistTracks) {
            playlistTracks.orEmpty().map { it.durationMs }
        }
        val playlistDurationMs = remember(playlistDurations, playlistTotal) {
            collectionDuration(playlistDurations, playlistTotal)
        }
        val playlistDurationIsEstimate = remember(playlistDurations, playlistTotal) {
            collectionDurationIsEstimate(playlistDurations, playlistTotal)
        }
        val playlistDominantColor by rememberDominantColor(
            imageUrl = resolvedPlaylistCover,
            fallbackSeed = activePlaylistTitle,
            defaultColor = Color(0xFF144D3F)
        )
        EncorePlaylistScreen(
            title = playlistDetail?.title?.takeIf { it.isNotBlank() } ?: activePlaylistTitle,
            description = playlistDetail?.description.orEmpty(),
            creator = playlistDetail?.creator?.takeIf { it.isNotBlank() } ?: if (activePlaylistUri.startsWith("spotiduck:local:playlist:") || activePlaylistUri.isEmpty()) userDisplayName else "",
            creatorAvatarUrl = playlistDetail?.creatorAvatarUrl.orEmpty(),
            saveCount = playlistDetail?.saveCount.orEmpty(),
            totalDurationMs = playlistDurationMs,
            totalIsEstimate = playlistDurationIsEstimate,
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
                        onPlaybackOrigin(PlaybackOrigin(ShellScreen.PLAYLIST, activePlaylistTitle))
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
                    onPlaybackOrigin(PlaybackOrigin(ShellScreen.PLAYLIST, activePlaylistTitle))
                    onLocalPlayPause()
                }
            },
            onTrackSelect = { track ->
                onPlaybackOrigin(PlaybackOrigin(ShellScreen.PLAYLIST, activePlaylistTitle))
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
            onChangeTitle = onChangeTitle,
            onAddTracksClick = onAddTracksClick,
            onEditCoverClick = onEditCoverClick,
            onEditModeChange = onEditModeChange,
            onSaveOrder = { order -> onSavePlaylistOrder?.invoke(activePlaylistUri, order) },
            onEditDetails = { name, description, cover, clearCover ->
                onEditPlaylistDetails?.invoke(activePlaylistUri, name, description, cover, clearCover)
            },
            onDeletePlaylist = { onDeletePlaylist?.invoke(activePlaylistUri) },
            isEditable = playlistDetail?.editable == true
        )
    }
}

@Composable
internal fun ShellHistoryPane(
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

@Composable
internal fun ShellBrowsePane(
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
        EncoreBrowseScreen(
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
        EncoreBrowseScreen(
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

@Composable
internal fun ShellShowPane(
    show: ShowDetail?,
    loading: Boolean,
    retryUri: String,
    isPlaying: Boolean,
    currentTrackTitle: String,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onPlayEpisode: (EpisodeRow) -> Unit = {},
    onPlayPause: () -> Unit,
    onLoadMore: () -> Unit
) {
    if (show == null) {
        ShellLoadingState(
            loading = loading,
            onRetry = { onRetry(retryUri) }
        )
    } else {
        EncoreShowScreen(
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
            onPlayEpisode = onPlayEpisode,
            onBackClick = onBack,
            onPlayPauseClick = onPlayPause,
            onLoadMore = onLoadMore
        )
    }
}

@Composable
internal fun ShellEpisodePane(
    episode: EpisodeDetail?,
    loading: Boolean,
    retryUri: String,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onPlayPause: () -> Unit,
    onPlay: () -> Unit = {},
    onShowClick: (String) -> Unit
) {
    if (episode == null) {
        ShellLoadingState(
            loading = loading,
            onRetry = { onRetry(retryUri) }
        )
    } else {
        EncoreEpisodeScreen(
            title = episode.title,
            showName = episode.showName,
            description = episode.description,
            imageUrl = episode.imageUrl.takeIf { it.isNotBlank() },
            wideImageUrl = episode.wideImageUrl.takeIf { it.isNotBlank() },
            headerColor = parseHexColor(episode.colorHex) ?: EncoreColors.Card,
            metaLabel = episode.releaseDate,
            durationMs = episode.durationMs,
            positionMs = episode.positionMs,
            isVideo = episode.video,
            isPlaying = isPlaying,
            isCurrent = isCurrent,
            onBackClick = onBack,
            onPlayPauseClick = onPlayPause,
            onPlayClick = onPlay,
            onShowClick = { onShowClick(episode.showUri) }
        )
    }
}

@Composable
internal fun ShellFolderPane(
    items: List<LibraryItem>?,
    loading: Boolean,
    title: String,
    userDisplayName: String,
    userAvatarUrl: String,
    onItemClick: (String, String) -> Unit,
    onItemLongPress: (LibraryItem) -> Unit,
    onFolderClick: (LibraryItem) -> Unit,
    onProfileClick: () -> Unit,
    onPlusClick: () -> Unit = {},
    onSortClick: () -> Unit = {},
    currentSort: LibrarySortOption = LibrarySortOption.RECENTS,
    sortDirection: SortDirection = SortDirection.DESCENDING,
    onBackClick: () -> Unit = {},
    onFolderOptionsClick: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    EncoreLibraryScreen(
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        onFolderClick = onFolderClick,
        items = items,
        loading = loading,
        title = title,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        onProfileClick = onProfileClick,
        onPlusClick = onPlusClick,
        onSortClick = onSortClick,
        currentSort = currentSort,
        sortDirection = sortDirection,
        isFolder = true,
        onBackClick = onBackClick,
        onFolderOptionsClick = onFolderOptionsClick,
        onPlayClick = onPlayClick
    )
}

@Composable
internal fun ShellPlaylistEditSheets(
    editDraft: PlaylistEditDraft?,
    onEditSave: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    onDismissEdit: () -> Unit,
    onDeletePlaylist: (suspend (String) -> Boolean)?,
    onDeleteAsk: (String, String, () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
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
            onDeletePlaylist = {
                onDismissEdit()
                onDeleteAsk(
                    "Delete playlist?",
                    "\"${editDraft.name}\" will be deleted from Your Library and your followers won't have access anymore.",
                ) { scope.launch { onDeletePlaylist?.invoke(editDraft.uri) } }
            },
            onDismiss = onDismissEdit
        )
    }
}

private enum class AddTracksFilter(val label: String) {
    SONGS("Songs"),
    EPISODES("Episodes"),
    RECENT("Recently played"),
    LIKED("Liked songs")
}

@Composable
internal fun ShellAddTracksPane(
    targetTitle: String,
    songs: List<TrackItem>,
    episodes: List<TrackItem>,
    recent: List<TrackItem>,
    liked: List<TrackItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onAdd: suspend (String) -> Boolean
) {
    var filter by remember { mutableStateOf(AddTracksFilter.SONGS) }
    val added = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val items = when (filter) {
        AddTracksFilter.SONGS -> songs
        AddTracksFilter.EPISODES -> episodes
        AddTracksFilter.RECENT -> recent
        AddTracksFilter.LIKED -> liked
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = "Back",
                onClick = onBack,
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Add tracks",
                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp)
                )
                Text(
                    text = "To $targetTitle",
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AddTracksFilter.entries.forEach { option ->
                EncoreChip(
                    text = option.label,
                    isSelected = option == filter,
                    onClick = { filter = option }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            loading -> TrackListSkeleton(
                rows = 9,
                modifier = Modifier.fillMaxSize()
            )
            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing here yet",
                    color = EncoreColors.TextSecondary
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.uri }) { item ->
                    AddTrackRow(
                        item = item,
                        added = item.uri in added,
                        onAdd = {
                            scope.launch {
                                if (onAdd(item.uri)) added.add(item.uri)
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AddTrackRow(
    item: TrackItem,
    added: Boolean,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            EncoreArtworkImage(
                imageUrl = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 4.dp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = item.artist,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { if (!added) onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (added) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24),
                contentDescription = if (added) "Added" else "Add",
                tint = if (added) EncoreColors.Green else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
