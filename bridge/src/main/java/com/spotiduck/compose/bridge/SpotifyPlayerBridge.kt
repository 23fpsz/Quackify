package com.spotiduck.compose.bridge

import androidx.compose.ui.graphics.Color
import com.spotiduck.compose.engine.ArtistOverviewResult
import com.spotiduck.compose.engine.NativePlaybackState
import com.spotiduck.compose.engine.PlaybackTrack
import com.spotiduck.compose.engine.SpotifyEntityType
import com.spotiduck.compose.engine.SpotifyLibraryEntity
import com.spotiduck.compose.engine.SpotifyMetadataRepository
import com.spotiduck.compose.engine.SpotifyNativePlayerManager
import com.spotiduck.compose.home.QuickAccessItem
import com.spotiduck.compose.home.RecommendationCard
import com.spotiduck.compose.library.LibraryItem
import com.spotiduck.compose.library.LibraryItemType
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.screens.ArtistRelease
import com.spotiduck.compose.screens.PopularTrack
import com.spotiduck.compose.shell.ComposePlaybackState
import com.spotiduck.compose.shell.ComposePlayerListener
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Clean Bridge Adapter connecting the headless Spotify Native DRM Engine
 * to the Jetpack Compose Encore UI shell.
 *
 * Keeps `:ui` and `:engine` completely decoupled from each other.
 */
class SpotifyPlayerBridge(
    val playerManager: SpotifyNativePlayerManager,
    private val metadataRepository: SpotifyMetadataRepository = SpotifyMetadataRepository(),
    private val queue: MutableList<PlaybackTrack> = mutableListOf(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : ComposePlayerListener {

    var currentTrackIndex: Int = 0
        private set

    // In-memory caches to guarantee instant 0ms transitions without stale cross-contamination
    private val playlistCache = ConcurrentHashMap<String, List<TrackItem>>()
    private val albumCache = ConcurrentHashMap<String, List<TrackItem>>()
    private val artistCache = ConcurrentHashMap<String, ArtistOverviewResult>()

    private var activePlaylistUri: String? = null
    private var activeAlbumUri: String? = null
    private var activeArtistUri: String? = null

    // Vibrant accent colors for QuickAccess tiles
    private val accentColors = listOf(
        Color(0xFF450AF5),
        Color(0xFFE8115B),
        Color(0xFF901A1E),
        Color(0xFF1E3A8A),
        Color(0xFF2E77D0),
        Color(0xFF107C41),
        Color(0xFFB45309),
        Color(0xFF6B21A8)
    )

    private val _libraryItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    val libraryItems: StateFlow<List<LibraryItem>> = _libraryItems.asStateFlow()

    private val _quickAccessItems = MutableStateFlow<List<QuickAccessItem>>(emptyList())
    val quickAccessItems: StateFlow<List<QuickAccessItem>> = _quickAccessItems.asStateFlow()

    private val _recommendations = MutableStateFlow<List<RecommendationCard>>(emptyList())
    val recommendations: StateFlow<List<RecommendationCard>> = _recommendations.asStateFlow()

    private val _uiTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val uiTracks: StateFlow<List<TrackItem>> = _uiTracks.asStateFlow()

    private val _albumTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val albumTracks: StateFlow<List<TrackItem>> = _albumTracks.asStateFlow()

    private val _artistTopTracks = MutableStateFlow<List<PopularTrack>>(emptyList())
    val artistTopTracks: StateFlow<List<PopularTrack>> = _artistTopTracks.asStateFlow()

    private val _artistReleases = MutableStateFlow<List<ArtistRelease>>(emptyList())
    val artistReleases: StateFlow<List<ArtistRelease>> = _artistReleases.asStateFlow()

    private val _artistMonthlyListeners = MutableStateFlow<String>("")
    val artistMonthlyListeners: StateFlow<String> = _artistMonthlyListeners.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentSpDc: String? = null

    /**
     * Observable Compose Playback State mapped automatically from engine state events.
     */
    val composePlaybackState: StateFlow<ComposePlaybackState> = playerManager.playbackState
        .map { nativeState ->
            val track = nativeState.currentTrack ?: queue.getOrNull(currentTrackIndex)
            val durationMs = if (nativeState.durationMs > 0) nativeState.durationMs else (track?.durationMs ?: 0L)
            val positionMs = nativeState.currentPositionMs
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

            ComposePlaybackState(
                title = track?.title ?: "No track playing",
                artist = track?.artist ?: "Unknown artist",
                coverUrl = track?.albumArtUrl ?: "",
                isPlaying = nativeState.isPlaying,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                connectedDevice = "Spotify Native (Widevine L3/L1)"
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ComposePlaybackState(
                title = queue.firstOrNull()?.title ?: "",
                artist = queue.firstOrNull()?.artist ?: "",
                coverUrl = queue.firstOrNull()?.albumArtUrl ?: "",
                isPlaying = false,
                progress = 0f,
                positionMs = 0L,
                durationMs = queue.firstOrNull()?.durationMs ?: 0L,
                connectedDevice = "Spotify Native (Widevine L3/L1)"
            )
        )

    init {
        updateUiTracks()
        playerManager.onTrackEnded = {
            onSkipNext()
        }
    }

    private fun updateUiTracks() {
        _uiTracks.value = queue.map {
            TrackItem(
                title = it.title,
                artist = it.artist,
                isExplicit = false,
                uri = it.uri,
                imageUrl = it.albumArtUrl
            )
        }
    }

    /**
     * Loads live user library and playlists from Spotify's Pathfinder backend.
     */
    fun loadUserData(spDc: String) {
        if (spDc.isBlank()) return
        currentSpDc = spDc

        scope.launch {
            _isLoading.value = true
            val libraryResult = metadataRepository.fetchLibrary(spDc)
            libraryResult.onSuccess { entities ->
                if (entities.isNotEmpty()) {
                    // 1. Map to Library Items
                    val items = entities.map { it.toLibraryItem() }.toMutableList()
                    if (items.none { it.type == LibraryItemType.PODCAST }) {
                        items.add(LibraryItem("Figuring Out with Raj Shamani", "Podcast • Raj Shamani", LibraryItemType.PODCAST, imageUrl = "https://i.scdn.co/image/ab6765630000ba8aa218ee3778a87b1cbf3bb552"))
                    }
                    _libraryItems.value = items

                    // 2. Map top items to Quick Access tiles
                    _quickAccessItems.value = entities.take(8).mapIndexed { index, entity ->
                        QuickAccessItem(
                            title = entity.name,
                            coverColor = accentColors[index % accentColors.size],
                            uri = entity.uri,
                            imageUrl = entity.imageUrl
                        )
                    }

                    // 3. Map subsequent items to Recommendation Cards
                    _recommendations.value = entities.drop(6).map { entity ->
                        RecommendationCard(
                            title = entity.name,
                            subtitle = entity.subtitle,
                            coverColor = Color(0xFF1E3264),
                            uri = entity.uri,
                            imageUrl = entity.imageUrl,
                            tag = when (entity.type) {
                                SpotifyEntityType.ALBUM -> "ALBUM"
                                SpotifyEntityType.ARTIST -> "ARTIST"
                                else -> "PLAYLIST"
                            }
                        )
                    }

                    // 4. Preload first playlist if available
                    val firstPlaylist = entities.firstOrNull {
                        it.type == SpotifyEntityType.PLAYLIST && it.uri.startsWith("spotify:playlist:")
                    }
                    if (firstPlaylist != null) {
                        val tracksResult = metadataRepository.fetchPlaylistTracks(spDc, firstPlaylist.uri, maxLimit = 100)
                        tracksResult.onSuccess { tracks ->
                            if (tracks.isNotEmpty()) {
                                val uiList = tracks.map {
                                    TrackItem(
                                        title = it.title,
                                        artist = it.artist,
                                        isExplicit = false,
                                        uri = it.uri,
                                        imageUrl = it.albumArtUrl
                                    )
                                }
                                playlistCache[firstPlaylist.uri] = uiList
                                if (activePlaylistUri == firstPlaylist.uri) {
                                    _uiTracks.value = uiList
                                }
                            }
                        }
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun setInitialPresets(
        initialTracks: List<PlaybackTrack>,
        initialQuickAccess: List<QuickAccessItem>,
        initialRecommendations: List<RecommendationCard>,
        initialLibrary: List<LibraryItem>
    ) {
        if (initialTracks.isNotEmpty()) setQueue(initialTracks)
        if (_quickAccessItems.value.isEmpty()) _quickAccessItems.value = initialQuickAccess
        if (_recommendations.value.isEmpty()) _recommendations.value = initialRecommendations
        if (_libraryItems.value.isEmpty()) _libraryItems.value = initialLibrary
    }

    fun setQueue(tracks: List<PlaybackTrack>, startIndex: Int = 0) {
        queue.clear()
        queue.addAll(tracks)
        currentTrackIndex = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        updateUiTracks()
    }

    fun getUiTracks(): List<TrackItem> = _uiTracks.value

    override fun onPlayPause() {
        val state = playerManager.playbackState.value
        if (state.isPlaying) {
            playerManager.pause()
        } else if (state.currentTrack != null) {
            playerManager.resume()
        } else if (queue.isNotEmpty()) {
            playerManager.playTrack(queue[currentTrackIndex])
        }
    }

    override fun onSkipNext() {
        if (queue.isEmpty()) return
        currentTrackIndex = (currentTrackIndex + 1) % queue.size
        playerManager.playTrack(queue[currentTrackIndex])
    }

    override fun onSkipPrevious() {
        if (queue.isEmpty()) return
        currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else queue.size - 1
        playerManager.playTrack(queue[currentTrackIndex])
    }

    override fun onSeek(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    override fun onTrackSelect(title: String, artist: String) {
        val index = queue.indexOfFirst { it.title.equals(title, ignoreCase = true) }
        if (index != -1) {
            currentTrackIndex = index
            playerManager.playTrack(queue[index])
        }
    }

    override fun onPlayUri(uri: String) {
        if (uri.startsWith("spotify:playlist:") || uri.startsWith("spotify:album:") || uri == "spotify:collection:tracks") {
            onLoadPlaylist(uri)
            val first = queue.firstOrNull()
            if (first != null) {
                currentTrackIndex = 0
                playerManager.playTrack(first)
            }
            return
        }

        val index = queue.indexOfFirst { it.uri.equals(uri, ignoreCase = true) }
        if (index != -1) {
            currentTrackIndex = index
            playerManager.playTrack(queue[index])
        } else {
            val dynamicTrack = PlaybackTrack(
                uri = uri,
                fileId = "",
                title = "Streaming Track",
                artist = "Spotify",
                albumArtUrl = "",
                durationMs = 0L
            )
            queue.add(dynamicTrack)
            currentTrackIndex = queue.size - 1
            updateUiTracks()
            playerManager.playTrack(dynamicTrack)
        }
    }

    override fun onLoadPlaylist(uri: String) {
        activePlaylistUri = uri
        val cached = playlistCache[uri]
        if (cached != null) {
            _uiTracks.value = cached
        } else {
            // Immediately clear uiTracks so the previous playlist's tracks NEVER leak into the new screen!
            _uiTracks.value = emptyList()
        }

        val spDc = currentSpDc ?: return
        scope.launch {
            _isLoading.value = true
            val result = when {
                uri == "spotify:collection:tracks" || uri.contains("liked", ignoreCase = true) -> {
                    metadataRepository.fetchLikedSongs(spDc, maxLimit = 500)
                }
                uri.startsWith("spotify:search:") -> {
                    val query = uri.removePrefix("spotify:search:")
                    metadataRepository.searchTracks(spDc, query, limit = 50)
                }
                else -> {
                    metadataRepository.fetchPlaylistTracks(spDc, uri, maxLimit = 500)
                }
            }

            result.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    val uiList = tracks.map {
                        TrackItem(
                            title = it.title,
                            artist = it.artist,
                            isExplicit = false,
                            uri = it.uri,
                            imageUrl = it.albumArtUrl
                        )
                    }
                    playlistCache[uri] = uiList
                    if (activePlaylistUri == uri) {
                        _uiTracks.value = uiList
                    }
                    setQueue(tracks, 0)
                }
            }
            _isLoading.value = false
        }
    }

    override fun onLoadAlbum(uri: String, albumName: String, artist: String) {
        val cacheKey = if (uri.isNotBlank()) uri else "$albumName::$artist"
        activeAlbumUri = cacheKey
        val cached = albumCache[cacheKey]
        if (cached != null) {
            _albumTracks.value = cached
        } else {
            _albumTracks.value = emptyList()
        }

        val spDc = currentSpDc ?: return
        scope.launch {
            _isLoading.value = true
            val result = if (uri.startsWith("spotify:album:")) {
                metadataRepository.fetchAlbumTracks(spDc, uri, limit = 100)
            } else if (albumName.isNotBlank()) {
                metadataRepository.searchTracks(spDc, "$albumName $artist".trim(), limit = 50)
            } else {
                Result.success(emptyList())
            }

            result.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    val uiList = tracks.map {
                        TrackItem(
                            title = it.title,
                            artist = it.artist,
                            isExplicit = false,
                            uri = it.uri,
                            imageUrl = it.albumArtUrl
                        )
                    }
                    albumCache[cacheKey] = uiList
                    if (activeAlbumUri == cacheKey) {
                        _albumTracks.value = uiList
                    }
                    setQueue(tracks, 0)
                }
            }
            _isLoading.value = false
        }
    }

    override fun onLoadArtist(uri: String, artistName: String) {
        val cacheKey = if (uri.isNotBlank()) uri else artistName
        activeArtistUri = cacheKey
        val cached = artistCache[cacheKey]
        if (cached != null) {
            _artistTopTracks.value = cached.topTracks.map {
                PopularTrack(
                    track = TrackItem(
                        title = it.title,
                        artist = it.artist,
                        isExplicit = false,
                        uri = it.uri,
                        imageUrl = it.albumArtUrl
                    ),
                    streamCount = it.fileId.ifEmpty { "312,410,950" }
                )
            }
            _artistReleases.value = cached.releases.map {
                ArtistRelease(
                    title = it.title,
                    year = it.year,
                    type = it.type,
                    imageUrl = it.imageUrl
                )
            }
            if (cached.monthlyListeners.isNotBlank()) {
                _artistMonthlyListeners.value = cached.monthlyListeners
            }
        } else {
            _artistTopTracks.value = emptyList()
        }

        val spDc = currentSpDc ?: return
        scope.launch {
            _isLoading.value = true
            if (uri.startsWith("spotify:artist:")) {
                val result = metadataRepository.fetchArtistOverview(spDc, uri)
                result.onSuccess { overview ->
                    artistCache[cacheKey] = overview
                    if (activeArtistUri == cacheKey) {
                        if (overview.topTracks.isNotEmpty()) {
                            _artistTopTracks.value = overview.topTracks.map {
                                PopularTrack(
                                    track = TrackItem(
                                        title = it.title,
                                        artist = it.artist,
                                        isExplicit = false,
                                        uri = it.uri,
                                        imageUrl = it.albumArtUrl
                                    ),
                                    streamCount = it.fileId.ifEmpty { "312,410,950" }
                                )
                            }
                            setQueue(overview.topTracks, 0)
                        }
                        if (overview.releases.isNotEmpty()) {
                            _artistReleases.value = overview.releases.map {
                                ArtistRelease(
                                    title = it.title,
                                    year = it.year,
                                    type = it.type,
                                    imageUrl = it.imageUrl
                                )
                            }
                        }
                        if (overview.monthlyListeners.isNotBlank()) {
                            _artistMonthlyListeners.value = overview.monthlyListeners
                        }
                    }
                }
            } else if (artistName.isNotBlank()) {
                val searchResult = metadataRepository.searchTracks(spDc, artistName, limit = 20)
                searchResult.onSuccess { tracks ->
                    if (tracks.isNotEmpty() && activeArtistUri == cacheKey) {
                        _artistTopTracks.value = tracks.take(10).map {
                            PopularTrack(
                                track = TrackItem(
                                    title = it.title,
                                    artist = it.artist,
                                    isExplicit = false,
                                    uri = it.uri,
                                    imageUrl = it.albumArtUrl
                                ),
                                streamCount = "312,410,950"
                            )
                        }
                        setQueue(tracks, 0)
                    }
                }
            }
            _isLoading.value = false
        }
    }

    override fun onSearch(query: String) {
        val spDc = currentSpDc ?: return
        if (query.isBlank()) return
        scope.launch {
            val result = metadataRepository.searchTracks(spDc, query, limit = 30)
            result.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    _uiTracks.value = tracks.map {
                        TrackItem(
                            title = it.title,
                            artist = it.artist,
                            isExplicit = false,
                            uri = it.uri,
                            imageUrl = it.albumArtUrl
                        )
                    }
                }
            }
        }
    }

    private fun SpotifyLibraryEntity.toLibraryItem(): LibraryItem {
        val uiType = when (type) {
            SpotifyEntityType.PLAYLIST, SpotifyEntityType.PSEUDO_PLAYLIST -> LibraryItemType.PLAYLIST
            SpotifyEntityType.ALBUM -> LibraryItemType.ALBUM
            SpotifyEntityType.ARTIST -> LibraryItemType.ARTIST
            SpotifyEntityType.PODCAST -> LibraryItemType.PODCAST
        }

        return LibraryItem(
            title = name,
            subtitle = subtitle,
            type = uiType,
            isPinned = isPinned,
            uri = uri,
            imageUrl = imageUrl
        )
    }
}
