package com.spotiduck.compose.bridge

import androidx.compose.ui.graphics.Color
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
import com.spotiduck.compose.shell.ComposePlaybackState
import com.spotiduck.compose.shell.ComposePlayerListener
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
                    _libraryItems.value = entities.map { it.toLibraryItem() }

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

                    // 4. If user has a playlist, fetch its actual tracks to populate the queue
                    val firstPlaylist = entities.firstOrNull {
                        it.type == SpotifyEntityType.PLAYLIST && it.uri.startsWith("spotify:playlist:")
                    }
                    if (firstPlaylist != null) {
                        val tracksResult = metadataRepository.fetchPlaylistTracks(spDc, firstPlaylist.uri, limit = 20)
                        tracksResult.onSuccess { tracks ->
                            if (tracks.isNotEmpty()) {
                                // Keep any existing playable fileId tracks in the front if available
                                val combined = queue.filter { it.fileId.isNotEmpty() } + tracks
                                setQueue(combined)
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
        setQueue(initialTracks)
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
        if (uri.startsWith("spotify:playlist:") || uri.startsWith("spotify:album:")) {
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
            // Track clicked that isn't yet in queue: play it dynamically and append to queue!
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
        val spDc = currentSpDc ?: return
        scope.launch {
            val result = metadataRepository.fetchPlaylistTracks(spDc, uri, limit = 50)
            result.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    val currentUri = playerManager.playbackState.value.currentTrack?.uri
                    val idx = if (currentUri != null) {
                        tracks.indexOfFirst { it.uri.equals(currentUri, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
                    } else {
                        0
                    }
                    setQueue(tracks, idx)
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
