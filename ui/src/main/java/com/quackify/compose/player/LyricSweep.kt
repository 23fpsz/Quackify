package com.quackify.compose.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs

/**
 * The song position, ticking every frame.
 *
 * The player reports where it is only about twice a second, which is fine for
 * a scrubber and far too coarse for a highlight that has to keep up with a
 * singer. Between readings this advances the last known position at one
 * millisecond per frame millisecond, and each new reading steers — rather
 * than sets — the clock: a reading only a little ahead of or behind the
 * estimate nudges it, one far away (a seek) snaps to it. Adapted from
 * BitChord's LyricClock.
 *
 * While paused, every reading is the truth, including a seek made while
 * paused.
 *
 * Read the returned state inside a draw lambda or a derivedStateOf, not in
 * composition: only the draw phase should re-run each frame.
 */
@Composable
internal fun rememberLyricClock(
    reportedPositionMs: () -> Long,
    isPlaying: Boolean,
    trackKey: Any? = Unit,
): Long {
    val clock = remember(trackKey) { mutableLongStateOf(0L) }
    var lastReported by remember(trackKey) { mutableLongStateOf(0L) }
    var lastFrameNanos by remember(trackKey) { mutableLongStateOf(0L) }

    // Paused: follow the readings exactly, so a seek while paused lands
    // immediately and nothing drifts.
    LaunchedEffect(isPlaying, trackKey) {
        if (isPlaying) return@LaunchedEffect
        snapshotFlow { reportedPositionMs() }.collect { reported ->
            clock.longValue = reported
        }
    }

    LaunchedEffect(isPlaying, trackKey) {
        if (!isPlaying) return@LaunchedEffect
        lastFrameNanos = 0L
        lastReported = reportedPositionMs()
        clock.longValue = lastReported
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val elapsed = ((frameNanos - lastFrameNanos) / 1_000_000L).coerceIn(0L, 250L)
                    clock.longValue += elapsed
                }
                lastFrameNanos = frameNanos
            }
            val reported = reportedPositionMs()
            if (reported != lastReported) {
                val estimate = clock.longValue
                // Steer, don't set: small corrections ease in, big jumps
                // (seeks) are taken whole. A reading only slightly behind the
                // estimate is reporting jitter, not truth — rewinding the sweep
                // by a few frames reads as a stutter, so it is held.
                val delta = reported - estimate
                clock.longValue = when {
                    delta in -60L..60L -> reported.coerceAtLeast(estimate - 8L)
                    abs(delta) < 600L -> estimate + delta / 2
                    else -> reported
                }
                lastReported = reported
            }
        }
    }
    return clock.longValue
}

/**
 * A word-synced lyric line with the sung part lit, the rest dimmed, and the
 * boundary travelling smoothly across the words in time with the vocal.
 *
 * Two copies of the same text stacked: a dim one, and a bright one clipped in
 * the draw phase to whatever fraction of the width corresponds to
 * [TimeSyncedLyric.revealedChars]. Same string, same style, same layout, so
 * the bright copy lands exactly on top of the dim one and the boundary moves
 * at a steady sub-letter rate instead of jumping a character at a time.
 *
 * The clip is recomputed in the draw phase only; a frame costs one clip and
 * one redraw of already-measured text, no recomposition.
 */
@Composable
internal fun WordSyncedLyricSweepText(
    lyric: TimeSyncedLyric,
    positionMs: Long,
    sungColor: Color,
    unsungColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    var layout by remember(lyric.text) { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = lyric.text,
            style = style.copy(color = unsungColor),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
        )
        Text(
            text = lyric.text,
            style = style.copy(color = sungColor),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val measured = layout ?: return@drawWithContent
                    val revealed = lyric.revealedChars(positionMs)
                    if (revealed <= 0f) return@drawWithContent
                    val textLength = lyric.text.length
                    if (revealed >= textLength) {
                        drawContent()
                        return@drawWithContent
                    }
                    // Interpolate the boundary's x within the character it is
                    // passing through, so the sweep moves continuously rather
                    // than a letter at a time.
                    val index = revealed.toInt().coerceIn(0, textLength - 1)
                    val fraction = revealed - index
                    val left = measured.getHorizontalPosition(index, usePrimaryDirection = true)
                    val right = measured.getHorizontalPosition(
                        (index + 1).coerceAtMost(textLength),
                        usePrimaryDirection = true,
                    )
                    val boundary = left + (right - left) * fraction
                    clipRect(left = 0f, top = 0f, right = boundary, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}
