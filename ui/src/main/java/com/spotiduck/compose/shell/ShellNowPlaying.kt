package com.spotiduck.compose.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.spotiduck.compose.components.RepeatMode
import com.spotiduck.compose.components.ShuffleMode
import com.spotiduck.compose.player.SpotifyNowPlayingScreen
import com.spotiduck.compose.player.TimeSyncedLyric
import com.spotiduck.compose.player.defaultLyricsList
import com.spotiduck.compose.theme.EncoreAnimation

@Composable
internal fun ShellNowPlaying(
    visible: Boolean,
    title: String,
    artist: String,
    contextHeader: String,
    contextSub: String,
    isPlaying: Boolean,
    progress: Float,
    durationMs: Long,
    positionMs: Long,
    dominantColor: Color,
    isLiked: Boolean,
    isExplicit: Boolean,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    connectedDevice: String,
    coverUrl: String,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onLike: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    onMore: () -> Unit,
    onQueue: () -> Unit,
    onDevice: () -> Unit,
    lyrics: List<TimeSyncedLyric> = defaultLyricsList,
    lyricsSnippet: String = "",
    monthlyListeners: String = "",
    artistBio: String = ""
) {
    AnimatedVisibility(
        visible = visible,
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
            title = title,
            artist = artist,
            contextHeaderTitle = contextHeader,
            albumOrPlaylistContext = contextSub,
            isPlaying = isPlaying,
            progress = progress,
            durationMs = durationMs,
            currentPositionMs = positionMs,
            dominantColor = dominantColor,
            isLiked = isLiked,
            isExplicit = isExplicit,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            connectedDeviceName = connectedDevice,
            coverUrl = coverUrl,
            onCollapseClick = onCollapse,
            onPlayPauseClick = onPlayPause,
            onSkipNextClick = onSkipNext,
            onSkipPreviousClick = onSkipPrevious,
            onLikeClick = onLike,
            onShuffleClick = onShuffle,
            onRepeatClick = onRepeat,
            onSeek = onSeek,
            onMoreClick = onMore,
            onQueueClick = onQueue,
            onDeviceClick = onDevice,
            lyrics = lyrics,
            lyricsSnippet = lyricsSnippet,
            monthlyListeners = monthlyListeners,
            artistBio = artistBio
        )
    }
}
