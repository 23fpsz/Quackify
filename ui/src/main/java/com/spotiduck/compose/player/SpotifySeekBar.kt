package com.spotiduck.compose.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
    bufferProgress: Float = 0f
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember(progress) { mutableFloatStateOf(progress) }

    val displayProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    // Thumb size: 4dp resting, 12dp when user is scrubbing
    val thumbDotSize by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 4.dp,
        animationSpec = spring(stiffness = 600f),
        label = "thumbDotSize"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp), // 32dp touch area matching seekbar_thumb_default.xml
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()

        // Drag & Tap Pointer Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
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
            // Track Background (3dp height, 1.5dp corner radius, 30% opacity white)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(inactiveColor)
            )

            // Buffer Progress (if any, 50% opacity white)
            if (bufferProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferProgress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.50f))
                )
            }

            // Active Track Progress (100% solid white, 3dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayProgress)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
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
