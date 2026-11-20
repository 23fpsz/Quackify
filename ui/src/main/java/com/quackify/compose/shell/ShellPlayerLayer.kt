package com.quackify.compose.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import com.quackify.backend.model.EpisodeComment
import com.quackify.backend.model.ShowDetail
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.player.LyricsOptions
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.player.TranscriptLine
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.util.rememberDominantColor
import kotlinx.coroutines.CoroutineScope


internal class ShellPlayerEnv(
    val flags: ShellUiFlags,
    val local: ShellLocalState,
    val navController: ShellNavController,
    val playbackState: ComposePlaybackState?,
    val progressState: PlaybackProgress?,
    val playerListener: ComposePlayerListener?,
    val shellScope: CoroutineScope,
    val currentScreen: ShellScreen,
    val isPlayerExpanded: Boolean,
    val setIsPlayerExpanded: (Boolean) -> Unit,
    val setCurrentScreen: (ShellScreen) -> Unit,
    val pushCurrentState: () -> Unit,
    val navBackStack: SnapshotStateList<BackStackItem>,
    val selectedNavIndex: Int,
    val setSelectedNavIndex: (Int) -> Unit,
    val searchFieldFocusRequester: FocusRequester,
    val navItems: List<NavItem>,
    val playerIdentity: PlayerIdentity,
    val currentTrackTitle: String,
    val currentArtist: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isLiked: Boolean,
    val currentCoverUrl: String,
    val currentShuffleMode: ShuffleMode,
    val currentRepeatMode: RepeatMode,
    val connectedDevice: String,
    val resolvedAlbumUri: String,
    val resolvedArtistUri: String,
    val currentAlbumName: String,
    val nowPlayingMonthlyListeners: String,
    val nowPlayingArtistBio: String,
    val nowPlayingArtistImageUrl: String?,
    val nowPlayingArtistFollowed: Boolean?,
    val nowPlayingShow: ShowDetail? = null,
    val nowPlayingComments: List<EpisodeComment> = emptyList(),
    val onCommentsRefresh: (() -> Unit)? = null,
    val onEpisodeComment: (suspend (String) -> Boolean)? = null,
    val onLoadCommentReplies: (suspend (String, String?) -> com.quackify.backend.model.EpisodeCommentReplies?)? = null,
    val onCommentReaction: (suspend (String, String) -> Boolean)? = null,
    val onRemoveCommentReaction: (suspend (String) -> Boolean)? = null,
    val onLoadCommentReactions: (suspend (String) -> com.quackify.backend.model.EpisodeCommentReactions?)? = null,
    val nowPlayingLyrics: List<TimeSyncedLyric>,
    val nowPlayingTranscript: List<TranscriptLine>,
    val lyricsProviderName: String = "",
    val canvasFilePath: String?,
    val isCanvasAllowed: Boolean,
    val isCanvasEnabled: Boolean,
    val videoFullscreen: Boolean,
    val playbackOrigin: PlaybackOrigin?,
    val handlePlayPause: () -> Unit,
    val handleSkipNext: () -> Unit,
    val handleSkipPrevious: () -> Unit,
    val handleSkipToPreviousTrack: () -> Unit,
    val handlePreviousSwipe: () -> Unit,
    val onCanvasToggle: ((Boolean) -> Unit)?,
    val onToggleVideoFullscreen: ((Boolean) -> Unit)?,
    val onArtistFollowToggle: ((String, Boolean) -> Unit)?,
    val onShowFollowToggle: ((String, Boolean) -> Unit)? = null,
    val onAddToPlaylist: ((TrackItem) -> Unit)?,
    val openAlbum: (String, String, String) -> Unit,
    val openArtist: (String, String) -> Unit,
    val shareEntity: (String) -> Unit,
)

/**
 * The mini-player bar and the expanded now-playing screen, extracted from
 * EncoreAppShell so that composable stays under the JIT method-size limit.
 */
@Composable
internal fun BoxScope.ShellPlayerLayer(
    env: ShellPlayerEnv,
    docked: Boolean = false,
    landscapeDual: Boolean = false,
) {
    val bottomBarModifier = Modifier.align(Alignment.BottomCenter)
    val playbackState = env.playbackState
    val progressState = env.progressState
    val currentProgress = progressState?.progress ?: env.local.progress
    val currentPositionMs = progressState?.positionMs ?: env.local.positionMs
    val durationMs = progressState?.durationMs ?: 0L

    val nowPlayingDominantColor by rememberDominantColor(
        imageUrl = env.currentCoverUrl,
        fallbackSeed = env.currentTrackTitle + env.currentArtist,
        defaultColor = Color(0xFF432371)
    )

    val effectiveConnectedDevice = playbackState?.connectedDevice?.takeIf { it.isNotBlank() }
    val activeDevice = playbackState?.devices?.firstOrNull { it.active || it.id == playbackState.activeDeviceId }
        ?: playbackState?.devices?.firstOrNull { it.name == effectiveConnectedDevice }
    val activeDeviceKind = activeDevice?.kind

    if (!env.flags.isPlaylistEditMode && env.currentScreen != ShellScreen.ADD_TRACKS &&
        !env.flags.isLogcatOpen && !landscapeDual
    ) {
        ShellBottomBar(
            title = env.playerIdentity.title,
            artist = env.playerIdentity.artist,
            isPlaying = env.isPlaying,
            isBuffering = env.isBuffering,
            progress = currentProgress,
            isLiked = env.isLiked,
            coverUrl = env.currentCoverUrl,
            coverColor = nowPlayingDominantColor,
            connectedDevice = effectiveConnectedDevice,
            connectedDeviceKind = activeDeviceKind,
            navItems = env.navItems,
            currentNavIndex = env.selectedNavIndex,
            onNavSelect = { index ->
                env.setSelectedNavIndex(index)
                when (index) {
                    0 -> if (env.currentScreen != ShellScreen.HOME) { env.navBackStack.clear(); env.setCurrentScreen(ShellScreen.HOME) }
                    1 -> if (env.currentScreen != ShellScreen.SEARCH) {
                        env.navBackStack.clear(); env.setCurrentScreen(ShellScreen.SEARCH)
                    } else {
                        runCatching { env.searchFieldFocusRequester.requestFocus() }
                    }
                    2 -> if (env.currentScreen != ShellScreen.LIBRARY) { env.navBackStack.clear(); env.setCurrentScreen(ShellScreen.LIBRARY) }
                }
            },
            onSearchDoubleTap = {
                runCatching { env.searchFieldFocusRequester.requestFocus() }
            },
            onPlayPause = env.handlePlayPause,
            onLike = {
                val playingUri = playbackState?.trackUri.orEmpty()
                if (!env.isLiked) {
                    if (env.playerListener != null) env.playerListener.onLikeToggle() else env.local.isLiked = true
                }
                val playingTrack = TrackItem(
                    title = env.currentTrackTitle,
                    artist = env.currentArtist,
                    uri = playingUri,
                    imageUrl = env.currentCoverUrl.takeIf { it.isNotBlank() },
                    saved = true
                )
                env.local.addTrackTarget = playingTrack
                env.onAddToPlaylist?.invoke(playingTrack)
                env.flags.isAddToPlaylistOpen = true
            },
            onDeviceClick = { env.flags.isDeviceSheetOpen = true },
            onBarClick = { env.setIsPlayerExpanded(true) },
            onSkipNext = env.handleSkipNext,
            onSkipPrevious = env.handleSkipPrevious,
            hasTrack = env.playerIdentity.trackLoaded,
            showMiniPlayer = env.playerIdentity.trackLoaded && !docked,
            modifier = bottomBarModifier
        )
    }

    if (!docked) {
        // Conditional composition, not `enabled = ...`: the back dispatcher
        // is LIFO by the moment a callback is ADDED, and BackHandler adds its
        // callback once, on first composition. A callback registered here at
        // app start loses to any screen-level handler added later (Settings'
        // category, a playlist's search mode), so the first back press
        // navigated the screen hidden under the player and only the second
        // one collapsed it. Composing the handler only while the player is
        // actually expanded re-adds it at that moment, making it the newest
        // callback - it then wins over those screen handlers. Handlers inside
        // the player content (lyrics fullscreen) and the sheets/overlays
        // hosts compose after this one, so they stay newer and still win.
        // landscapeDual keeps the player as a persistent side pane rather
        // than an overlay, so back there must keep navigating the content.
        if (!landscapeDual && env.isPlayerExpanded && !env.flags.isLogcatOpen) {
            BackHandler {
                env.setIsPlayerExpanded(false)
            }
        }
        NowPlayingScreenLayer(
            env = env,
            visible = env.isPlayerExpanded && !env.flags.isLogcatOpen,
            docked = false,
            landscapeDual = landscapeDual,
            modifier = Modifier
        )
    }
}

@Composable
internal fun ShellDockedPlayer(env: ShellPlayerEnv, modifier: Modifier = Modifier) {
    if (!env.playerIdentity.trackLoaded) return
    NowPlayingScreenLayer(
        env = env,
        visible = !env.flags.isLogcatOpen,
        docked = true,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun NowPlayingScreenLayer(
    env: ShellPlayerEnv,
    visible: Boolean,
    docked: Boolean,
    landscapeDual: Boolean = false,
    modifier: Modifier
) {
    val playbackState = env.playbackState
    val progressState = env.progressState
    val currentProgress = progressState?.progress ?: env.local.progress
    val currentPositionMs = progressState?.positionMs ?: env.local.positionMs
    val durationMs = progressState?.durationMs ?: 0L
    val nowPlayingDominantColor by rememberDominantColor(
        imageUrl = env.currentCoverUrl,
        fallbackSeed = env.currentTrackTitle + env.currentArtist,
        defaultColor = Color(0xFF432371)
    )
    val bridgeContextLabel = playbackState?.queueContextLabel?.takeIf { it.isNotBlank() }
        ?: playbackState?.contextLabel?.takeIf { it.isNotBlank() }
        ?: ""
    val (contextHeader, contextSub) = nowPlayingContext(
        trackLoaded = env.playerIdentity.trackLoaded,
        origin = env.playbackOrigin,
        contextUri = playbackState?.contextUri,
        contextLabel = bridgeContextLabel,
    )
    val remoteCtl = playbackState?.controllingRemote == true
    val effectiveCanShuffle = if (remoteCtl) playbackState?.remoteCanShuffle ?: true else playbackState?.canShuffle ?: true
    val effectiveCanRepeat = if (remoteCtl) playbackState?.remoteCanRepeat ?: true else playbackState?.canRepeat ?: true
    val effectiveCanSkipPrevious = if (remoteCtl) playbackState?.remoteCanSkipPrev ?: true else true
    val effectiveCanSeek = if (remoteCtl) playbackState?.remoteCanSeek ?: true else true

    ShellNowPlaying(
        visible = visible,
        docked = docked,
        landscapeDual = landscapeDual,
        modifier = modifier,
        title = env.playerIdentity.title,
        artist = env.playerIdentity.artist,
        contextHeader = contextHeader,
        contextSub = contextSub,
        isPlaying = env.isPlaying,
        isBuffering = env.isBuffering,
        progress = currentProgress,
        durationMs = durationMs,
        positionMs = currentPositionMs,
        dominantColor = nowPlayingDominantColor,
        isLiked = env.isLiked,
        isExplicit = playbackState?.explicit ?: false,
        monthlyListeners = env.nowPlayingMonthlyListeners,
        artistBio = env.nowPlayingArtistBio,
        artistImageUrl = env.nowPlayingArtistImageUrl,
        isArtistFollowing = env.nowPlayingArtistFollowed == true,
        nowPlayingShow = env.nowPlayingShow,
        nowPlayingComments = env.nowPlayingComments,
        onCommentsRefresh = { env.onCommentsRefresh?.invoke() },
        onEpisodeComment = { text -> env.onEpisodeComment?.invoke(text) ?: false },
        onLoadCommentReplies = { commentUri, pageToken ->
            env.onLoadCommentReplies?.invoke(commentUri, pageToken)
        },
        onCommentReaction = { commentUri, emoji ->
            env.onCommentReaction?.invoke(commentUri, emoji) ?: false
        },
        onRemoveCommentReaction = { commentUri ->
            env.onRemoveCommentReaction?.invoke(commentUri) ?: false
        },
        onLoadCommentReactions = { commentUri ->
            env.onLoadCommentReactions?.invoke(commentUri)
        },
        onArtistFollowToggle = { follow ->
            if (playbackState?.trackUri.orEmpty().startsWith("spotify:episode:")) {
                val showUri = env.nowPlayingShow?.uri.orEmpty()
                if (showUri.startsWith("spotify:show:")) {
                    env.onShowFollowToggle?.invoke(showUri, follow)
                }
            } else if (env.resolvedArtistUri.startsWith("spotify:artist:")) {
                env.onArtistFollowToggle?.invoke(env.resolvedArtistUri, follow)
            }
        },
        // The master switch gates the lyrics list itself; the under-cover
        // toggle only hides the preview line, and both read live state so a
        // Settings change applies to the open player without a reopen.
        lyrics = if (LyricsOptions.enabled) env.nowPlayingLyrics else emptyList(),
        transcript = env.nowPlayingTranscript,
        lyricsProviderName = if (LyricsOptions.enabled) env.lyricsProviderName else "",
        lyricsPreview = LyricsOptions.preview,
        previousTrack = playbackState?.previousTrack,
        nextTrack = playbackState?.nextTrack,
        canvasFilePath = env.canvasFilePath,
        isCanvasAllowed = env.isCanvasAllowed,
        isCanvasEnabled = env.isCanvasEnabled,
        onCanvasToggle = { on -> env.onCanvasToggle?.invoke(on) },
        musicVideoAvailable = playbackState?.musicVideoAvailable ?: false,
        videoActive = playbackState?.videoActive ?: false,
        videoQualities = playbackState?.videoQualities.orEmpty(),
        videoSwitching = playbackState?.videoSwitching ?: false,
        onSwitchVideo = { to -> env.playerListener?.onSwitchVideo(to) },
        videoFullscreen = env.videoFullscreen,
        onToggleVideoFullscreen = { on -> env.onToggleVideoFullscreen?.invoke(on) },
        onVideoQuality = { height -> env.playerListener?.onVideoQuality(height) },
        onVideoSurface = { surface -> env.playerListener?.onVideoSurface(surface) },
        isEpisode = playbackState?.trackUri.orEmpty().startsWith("spotify:episode:"),
        playbackSpeed = playbackState?.playbackSpeed ?: 1f,
        chapters = playbackState?.chapters.orEmpty(),
        onPlaybackSpeed = { speed -> env.playerListener?.onPlaybackSpeed(speed) },
        onSkipSeek = { delta -> env.playerListener?.onSkipSeek(delta) },
        isEnhancedTrack = playbackState?.isEnhanced ?: false,
        shuffleMode = env.currentShuffleMode,
        repeatMode = env.currentRepeatMode,
        canShuffle = effectiveCanShuffle,
        canSmartShuffle = playbackState?.canSmartShuffle ?: false,
        canRepeat = effectiveCanRepeat,
        canSkipPrevious = effectiveCanSkipPrevious,
        canSeek = effectiveCanSeek,
        connectedDevice = env.connectedDevice,
        coverUrl = env.currentCoverUrl,
        onCollapse = { env.setIsPlayerExpanded(false) },
        onPlayPause = env.handlePlayPause,
        onSkipNext = env.handleSkipNext,
        onSkipPrevious = env.handleSkipPrevious,
        onSkipToPreviousClick = env.handleSkipToPreviousTrack,
        onPreviousSwipe = env.handlePreviousSwipe,
        onLike = {
            val playingTrack = TrackItem(
                title = env.currentTrackTitle,
                artist = env.currentArtist,
                uri = playbackState?.trackUri.orEmpty(),
                imageUrl = env.currentCoverUrl.takeIf { it.isNotBlank() },
                saved = env.isLiked
            )
            env.local.addTrackTarget = playingTrack
            env.onAddToPlaylist?.invoke(playingTrack)
            env.flags.isAddToPlaylistOpen = true
        },
        onShuffle = { if (env.playerListener != null) env.playerListener.onShuffleToggle() else env.local.shuffleMode = env.local.shuffleMode.next() },
        onRepeat = { if (env.playerListener != null) env.playerListener.onRepeatToggle() else env.local.repeatMode = env.local.repeatMode.next() },
        onSeek = { p -> if (env.playerListener != null) env.playerListener.onSeek((p * durationMs).toLong()) else { env.local.progress = p; env.local.positionMs = (p * durationMs).toLong() } },
        onMore = {
            env.local.trackForOptions = TrackItem(
                title = env.currentTrackTitle,
                artist = env.currentArtist,
                isExplicit = playbackState?.explicit ?: false,
                uri = playbackState?.trackUri.orEmpty(),
                imageUrl = env.currentCoverUrl.takeIf { it.isNotBlank() },
                saved = env.isLiked,
                albumUri = env.resolvedAlbumUri,
                albumName = env.currentAlbumName,
                artistUri = env.resolvedArtistUri,
                artistUris = playbackState?.artistUris.orEmpty(),
            )
            env.flags.isTrackOptionsFromPlayer = true
            env.flags.isTrackOptionsOpen = true
        },
        onQueue = { env.flags.isQueueSheetOpen = true },
        onDevice = { env.flags.isDeviceSheetOpen = true },
        onShare = { playbackState?.trackUri?.takeIf { it.isNotBlank() }?.let { uri -> env.shareEntity(uri) } },
        onTitleClick = {
            env.setIsPlayerExpanded(false)
            env.openAlbum(env.currentTrackTitle, env.currentArtist, env.resolvedAlbumUri)
        },
        onArtistClick = {
            // A track credited to two or more artists opens the picker sheet
            // so each artist has their own row; a single artist still jumps
            // straight into their page.
            val uris = env.playbackState?.artistUris.orEmpty()
                .filter { it.startsWith("spotify:artist:") }
            if (uris.size > 1) {
                env.local.artistListTrack = null
                env.flags.isArtistListOpen = true
            } else {
                env.setIsPlayerExpanded(false)
                env.openArtist(env.currentArtist, env.resolvedArtistUri)
            }
        },
        onArtistLongClick = {
            env.local.artistOptionsName = env.currentArtist
            env.local.artistOptionsUri = env.resolvedArtistUri
            env.local.artistOptionsAvatarUrl = env.nowPlayingArtistImageUrl
            env.flags.isArtistOptionsFromPlayer = true
            env.flags.isArtistOptionsOpen = true
        }
    )
}
