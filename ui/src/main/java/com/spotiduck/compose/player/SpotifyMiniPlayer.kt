package com.spotiduck.compose.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreHeartButton
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.theme.EncoreAnimation
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.EncoreControlSizes
import com.spotiduck.compose.theme.EncoreCornerRadii
import com.spotiduck.compose.theme.EncoreSpacers
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Floating Mini Player Bar matching Spotify Mobile's `now_playing_bar.xml` specifications.
 * Supports swiping the track title/artist (CarouselView) to skip tracks with real-time translation and spring physics.
 */
@Composable
fun SpotifyMiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    progress: Float, // 0.0f to 1.0f
    isLiked: Boolean,
    onPlayPauseClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipNextClick: () -> Unit = {},
    onSkipPreviousClick: () -> Unit = {},
    connectedDeviceName: String? = null,
    coverColor: Color = Color(0xFF5A3E85),
    coverUrl: String? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val titleOffsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EncoreControlSizes.FloatingPlayerMargin)
            .padding(bottom = EncoreControlSizes.FloatingPlayerMargin)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(EncoreCornerRadii.Larger2))
            .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
            .background(EncoreColors.Card)
            .clickable(onClick = onBarClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(EncoreControlSizes.FloatingPlayerHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 40dp Album Cover Art with 4dp radius (floating_now_playing_bar_cover_art_radius)
                com.spotiduck.compose.components.SpotifyArtworkImage(
                    imageUrl = coverUrl,
                    contentDescription = title,
                    shape = RoundedCornerShape(EncoreCornerRadii.Base),
                    fallbackColor = coverColor,
                    modifier = Modifier.size(EncoreControlSizes.FloatingPlayerCoverSize)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track title & artist (CarouselView swipe-to-skip gesture with real-time motion)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                        .graphicsLayer {
                            translationX = titleOffsetX.value
                            alpha = (1f - (titleOffsetX.value.absoluteValue / 250f)).coerceIn(0.2f, 1f)
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val offset = titleOffsetX.value
                                        val threshold = 50f
                                        if (offset < -threshold) {
                                            titleOffsetX.animateTo(-250f, tween(150, easing = EncoreAnimation.DecelerateInterpolator))
                                            onSkipNextClick()
                                            titleOffsetX.snapTo(250f)
                                            titleOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                        } else if (offset > threshold) {
                                            titleOffsetX.animateTo(250f, tween(150, easing = EncoreAnimation.DecelerateInterpolator))
                                            onSkipPreviousClick()
                                            titleOffsetX.snapTo(-250f)
                                            titleOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                        } else {
                                            titleOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    coroutineScope.launch {
                                        titleOffsetX.snapTo(titleOffsetX.value + dragAmount)
                                    }
                                }
                            )
                        }
                ) {
                    Text(
                        text = title,
                        style = SpotifyTheme.typography.bodySmallBold.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = EncoreColors.TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (connectedDeviceName != null) "🎧 $connectedDeviceName • $artist" else artist,
                        style = SpotifyTheme.typography.marginal.copy(
                            color = if (connectedDeviceName != null) EncoreColors.Green else EncoreColors.TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Connect device icon button (now_playing_bar_actions_button_size = 40dp)
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_devices_24,
                    contentDescription = "Connect to device",
                    onClick = onDeviceClick,
                    size = EncoreControlSizes.FloatingPlayerButtonSize,
                    iconSize = 20.dp,
                    tint = if (connectedDeviceName != null) EncoreColors.Green else EncoreColors.TextSecondary
                )

                // Like / Heart button with signature bouncy physics (now_playing_bar_actions_button_size = 40dp)
                EncoreHeartButton(
                    isLiked = isLiked,
                    onLikeToggle = onLikeClick,
                    size = EncoreControlSizes.FloatingPlayerButtonSize,
                    iconSize = 22.dp
                )

                // Play / Pause button (now_playing_bar_actions_button_size = 40dp)
                EncoreIconButton(
                    iconRes = if (isPlaying) R.drawable.encore_icon_pause_24 else R.drawable.encore_icon_play_24,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPauseClick,
                    size = EncoreControlSizes.FloatingPlayerButtonSize,
                    iconSize = 24.dp,
                    tint = EncoreColors.TextPrimary,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // 2dp horizontal progress indicator pinned to the bottom of the bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = EncoreColors.TextPrimary,
                trackColor = EncoreColors.ProgressBarBackground
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyMiniPlayer() {
    SpotifyEncoreTheme {
        var isPlaying by remember { mutableStateOf(true) }
        var isLiked by remember { mutableStateOf(true) }

        SpotifyMiniPlayer(
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            isPlaying = isPlaying,
            progress = 0.45f,
            isLiked = isLiked,
            connectedDeviceName = "Galaxy Buds2 Pro",
            onPlayPauseClick = { isPlaying = !isPlaying },
            onLikeClick = { isLiked = !isLiked },
            onDeviceClick = {},
            onBarClick = {}
        )
    }
}
