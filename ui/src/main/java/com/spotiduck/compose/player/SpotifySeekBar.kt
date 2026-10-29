package com.spotiduck.compose.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyTheme
import kotlin.math.roundToInt

/**
 * 1:1 Spotify Native NPV Scrubber SeekBar matching decompiled:
 * - `seekbar_progress_default.xml` (3dp track thickness, 1.5dp corner radius, 30% white background)
 * - `seekbar_thumb_default.xml` (32dp touch target, 4dp idle thumb dot, 12dp active thumb dot)
 * - Scrubbing time preview tooltip floating directly above the thumb during user drag
 */
@Composable
fun SpotifySeekBar(
    progress: Float, // 0.0f to 1.0f
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.30f),
    bufferProgress: Float = 0f,
    isLoading: Boolean = false
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember(progress) { mutableFloatStateOf(progress) }

    // Smooth continuous progress interpolation between periodic player ticks (120fps / 60fps)
    val smoothProgress by animateFloatAsState(
        targetValue = if (isDragging) dragProgress else progress.coerceIn(0f, 1f),
        animationSpec = if (isDragging) snap() else tween(durationMillis = 250, easing = LinearEasing),
        label = "smoothProgress"
    )
    val displayProgress = if (isDragging) dragProgress else smoothProgress

    // Infinite pulse transition for song loading wave travelling from progress thumb towards the right
    val infiniteTransition = rememberInfiniteTransition(label = "seekbarLoadingTransition")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    // Thumb size: 8dp resting (crisp and visible matching Spotify), 12dp when user is scrubbing
    val thumbDotSize by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 8.dp,
        animationSpec = spring(stiffness = 600f),
        label = "thumbDotSize"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp), // 24dp touch area ensuring 10dp vertical alignment to timestamps
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val thumbOffsetPx = (displayProgress * widthPx).coerceIn(0f, widthPx)

        // Drag & Tap Pointer Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(widthPx) {
                    detectTapGestures(
                        onPress = { offset ->
                            val newProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                            dragProgress = newProgress
                            isDragging = true
                            tryAwaitRelease()
                            isDragging = false
                            onSeek(dragProgress)
                        }
                    )
                }
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek(dragProgress)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val newProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                            dragProgress = newProgress
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track Background (4dp height, 2dp corner radius, 30% opacity white)
            // When isLoading is true, an animated light pulse sweeps outward from the thumb across the track
            if (isLoading) {
                val remainingTrack = (widthPx - thumbOffsetPx).coerceAtLeast(1f)
                val waveLengthPx = (remainingTrack * 0.40f).coerceIn(60f, 240f)
                val waveCenterPx = thumbOffsetPx + (remainingTrack + waveLengthPx) * pulsePhase - (waveLengthPx / 2f)

                val pulseBrush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to inactiveColor,
                        ((waveCenterPx - waveLengthPx) / widthPx).coerceIn(0f, 1f) to inactiveColor,
                        (waveCenterPx / widthPx).coerceIn(0f, 1f) to Color.White.copy(alpha = 0.95f),
                        ((waveCenterPx + waveLengthPx) / widthPx).coerceIn(0f, 1f) to inactiveColor,
                        1.0f to inactiveColor
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(pulseBrush)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(inactiveColor)
                )
            }

            // Buffer Progress (if any, 50% opacity white)
            if (bufferProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferProgress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.50f))
                )
            }

            // Active Track Progress (100% solid white, 4dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDragging) EncoreColors.Green else activeColor)
            )

            // Thumb (32dp touch box with centered circular white dot)
            val thumbOffsetPx = (displayProgress * widthPx).coerceIn(0f, widthPx)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (thumbOffsetPx - with(density) { 16.dp.toPx() }).roundToInt(),
                            y = 0
                        )
                    }
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(thumbDotSize)
                        .shadow(
                            elevation = if (isDragging) 4.dp else 0.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(if (isDragging) Color.White else activeColor)
                )
            }
        }

        // Floating Time Tooltip while dragging
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val tooltipX = (displayProgress * widthPx) - with(density) { 24.dp.toPx() }
                    IntOffset(
                        x = tooltipX.coerceIn(0f, widthPx - with(density) { 48.dp.toPx() }).roundToInt(),
                        y = with(density) { (-30).dp.roundToPx() }
                    )
                }
        ) {
            val previewMs = (displayProgress * durationMs).toLong()
            val totalSeconds = (previewMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val formatted = String.format("%d:%02d", minutes, seconds)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF282828))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatted,
                    style = SpotifyTheme.typography.marginal.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
