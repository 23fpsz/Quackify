package com.quackify.compose.shell

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.quackify.backend.model.EpisodeComment
import com.quackify.backend.model.ShowDetail
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.player.EncoreNowPlayingScreen
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.player.TranscriptLine
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreAnimation

/** The screen itself only fades: the artwork carries the movement. */
private const val NowPlayingRevealFadeMs = 160

@Composable
internal fun ShellNowPlaying(
    visible: Boolean,
    docked: Boolean = false,
    landscapeDual: Boolean = false,
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    contextHeader: String,
    contextSub: String,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    progress: Float,
    durationMs: Long,
    positionMs: Long,
    dominantColor: Color,
    isLiked: Boolean,
    isExplicit: Boolean,
    monthlyListeners: String,
    artistBio: String,
    artistImageUrl: String?,
    isArtistFollowing: Boolean = false,
    onArtistFollowToggle: (Boolean) -> Unit = {},
    nowPlayingShow: ShowDetail? = null,
    nowPlayingComments: List<EpisodeComment> = emptyList(),
    onCommentsRefresh: () -> Unit = {},
    onEpisodeComment: suspend (String) -> Boolean = { false },
    onLoadCommentReplies: suspend (String, String?) -> com.quackify.backend.model.EpisodeCommentReplies? = { _, _ -> null },
    onCommentReaction: suspend (String, String) -> Boolean = { _, _ -> false },
    onRemoveCommentReaction: suspend (String) -> Boolean = { false },
    onLoadCommentReactions: suspend (String) -> com.quackify.backend.model.EpisodeCommentReactions? = { null },
    selfUserUri: String = "",
    onDeleteComment: suspend (String) -> Boolean = { false },
    isEnhancedTrack: Boolean = false,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    canShuffle: Boolean,
    canSmartShuffle: Boolean,
    canRepeat: Boolean,
    canSkipPrevious: Boolean = true,
    canSeek: Boolean = true,
    connectedDevice: String,
    coverUrl: String,
    lyrics: List<TimeSyncedLyric> = emptyList(),
    nextTrackLyrics: List<TimeSyncedLyric> = emptyList(),
    transcript: List<TranscriptLine> = emptyList(),
    lyricsProviderName: String = "",
    lyricsPreview: Boolean = true,
    previousTrack: TrackItem? = null,
    nextTrack: TrackItem? = null,
    canvasFilePath: String? = null,
    isCanvasAllowed: Boolean = true,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: (Boolean) -> Unit = {},
    musicVideoAvailable: Boolean = false,
    videoActive: Boolean = false,
    streamSource: String = "",
    videoQualities: List<Int> = emptyList(),
    videoSwitching: Boolean = false,
    onSwitchVideo: (Boolean) -> Unit = {},
    videoFullscreen: Boolean = false,
    onToggleVideoFullscreen: (Boolean) -> Unit = {},
    onVideoQuality: (Int) -> Unit = {},
    onVideoSurface: (android.view.Surface?) -> Unit = {},
    isEpisode: Boolean = false,
    playbackSpeed: Float = 1f,
    chapters: List<ChapterMarker> = emptyList(),
    onPlaybackSpeed: (Float) -> Unit = {},
    onSkipSeek: (Long) -> Unit = {},
    onCollapse: () -> Unit,
    onExpandPlayer: () -> Unit = {},
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipToPreviousClick: () -> Unit = {},
    onPreviousSwipe: () -> Unit = onSkipPrevious,
    onLike: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    onMore: () -> Unit,
    onQueue: () -> Unit,
    onDevice: () -> Unit,
    onShare: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    onArtistLongClick: (() -> Unit)? = null,
) {
    val surface: @Composable () -> Unit = {
        EncoreNowPlayingScreen(
            docked = docked,
            title = title,
            artist = artist,
            contextHeaderTitle = contextHeader,
            albumOrPlaylistContext = contextSub,
            isPlaying = isPlaying,
            isLoading = isBuffering,
            progress = progress,
            durationMs = durationMs,
            currentPositionMs = positionMs,
            dominantColor = dominantColor,
            isLiked = isLiked,
            isExplicit = isExplicit,
            monthlyListeners = monthlyListeners,
            artistBio = artistBio,
            artistImageUrl = artistImageUrl,
            isArtistFollowing = isArtistFollowing,
            onArtistFollowToggle = onArtistFollowToggle,
            nowPlayingShow = nowPlayingShow,
            nowPlayingComments = nowPlayingComments,
            onCommentsRefresh = onCommentsRefresh,
            onEpisodeComment = onEpisodeComment,
            onLoadCommentReplies = onLoadCommentReplies,
            onCommentReaction = onCommentReaction,
            onRemoveCommentReaction = onRemoveCommentReaction,
            onLoadCommentReactions = onLoadCommentReactions,
            selfUserUri = selfUserUri,
            onDeleteComment = onDeleteComment,
            isEnhancedTrack = isEnhancedTrack,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            canShuffle = canShuffle,
            canSmartShuffle = canSmartShuffle,
            canRepeat = canRepeat,
            canSkipPrevious = canSkipPrevious,
            canSeek = canSeek,
            connectedDeviceName = connectedDevice,
            coverUrl = coverUrl,
            lyrics = lyrics,
            nextTrackLyrics = nextTrackLyrics,
            transcript = transcript,
            lyricsProviderName = lyricsProviderName,
            showLyricsPreview = lyricsPreview,
            previousTrack = previousTrack,
            nextTrack = nextTrack,
            canvasFilePath = canvasFilePath,
            isCanvasAllowed = isCanvasAllowed,
            isCanvasEnabled = isCanvasEnabled,
            onCanvasToggle = onCanvasToggle,
            musicVideoAvailable = musicVideoAvailable,
            videoActive = videoActive,
            streamSource = streamSource,
            videoQualities = videoQualities,
            videoSwitching = videoSwitching,
            onSwitchVideo = onSwitchVideo,
            videoFullscreen = videoFullscreen,
            onToggleVideoFullscreen = onToggleVideoFullscreen,
            onVideoQuality = onVideoQuality,
            onVideoSurface = onVideoSurface,
            isEpisode = isEpisode,
            playbackSpeed = playbackSpeed,
            chapters = chapters,
            onPlaybackSpeed = onPlaybackSpeed,
            onSkipSeek = onSkipSeek,
            onCollapseClick = onCollapse,
            onExpandPlayerClick = onExpandPlayer,
            onPlayPauseClick = onPlayPause,
            onSkipNextClick = onSkipNext,
            onSkipPreviousClick = onSkipPrevious,
            onSkipToPreviousClick = onSkipToPreviousClick,
            onSwipePreviousClick = onPreviousSwipe,
            onLikeClick = onLike,
            onShuffleClick = onShuffle,
            onRepeatClick = onRepeat,
            onSeek = onSeek,
            onMoreClick = onMore,
            onQueueClick = onQueue,
            onDeviceClick = onDevice,
            onShareClick = onShare,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onArtistLongClick = onArtistLongClick
        )
    }

    if (docked) {
        if (!visible) return
        DockedPlayerPane(modifier = modifier) { surface() }
    } else if (landscapeDual) {
        DualViewPlayerPane(modifier = modifier) { surface() }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = NowPlayingRevealFadeMs)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = EncoreAnimation.NowPlayingExitDuration,
                    easing = EncoreAnimation.DecelerateInterpolator
                )
            )
        ) {
            surface()
        }
    }
}

/**
 * Right pane of the landscape dual view: the expanded now playing screen sits
 * beside the app content instead of covering it. The screen is measured with
 * the pane's own size so the portrait arrangement (artwork on top, controls
 * below) fits the tall narrow pane.
 */
@Composable
internal fun DualViewPlayerPane(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val base = LocalConfiguration.current
        val paneWidth = maxWidth.value.toInt().coerceAtLeast(1)
        val paneHeight = maxHeight.value.toInt().coerceAtLeast(1)
        val paneConfiguration = remember(base, paneWidth, paneHeight) {
            Configuration(base).apply {
                screenWidthDp = paneWidth
                screenHeightDp = paneHeight
                smallestScreenWidthDp = minOf(paneWidth, paneHeight)
                orientation = if (paneWidth > paneHeight) {
                    Configuration.ORIENTATION_LANDSCAPE
                } else {
                    Configuration.ORIENTATION_PORTRAIT
                }
            }
        }
        CompositionLocalProvider(LocalConfiguration provides paneConfiguration) {
            content()
        }
    }
}

/**
 * Side pane of the tablet split layout: the now playing screen is measured
 * with the pane's own size so it keeps its portrait arrangement instead of
 * inheriting the tablet's landscape window metrics.
 */
@Composable
private fun DockedPlayerPane(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val base = LocalConfiguration.current
        val paneWidth = maxWidth.value.toInt().coerceAtLeast(1)
        val paneHeight = maxHeight.value.toInt().coerceAtLeast(1)
        val paneConfiguration = remember(base, paneWidth, paneHeight) {
            Configuration(base).apply {
                screenWidthDp = paneWidth
                screenHeightDp = paneHeight
                smallestScreenWidthDp = minOf(paneWidth, paneHeight)
                orientation = if (paneWidth > paneHeight) {
                    Configuration.ORIENTATION_LANDSCAPE
                } else {
                    Configuration.ORIENTATION_PORTRAIT
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CompositionLocalProvider(LocalConfiguration provides paneConfiguration) {
                content()
            }
        }
    }
}
