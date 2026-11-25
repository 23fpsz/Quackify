package com.quackify.compose.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * estimate nudges it, one far away (a seek) snaps to it
 *
 * While paused, every reading is the truth, including a seek made while
 * paused.
 *
 * Returned as [State] rather than a plain value on purpose: read inside a
 * draw lambda or a [derivedStateOf], only the draw phase (or the derived
 * computation) re-runs each frame. Read in composition, the whole screen
 * would recompose sixty times a second — and a plain Long return would freeze
 * at whatever the first composition saw, which is exactly how the fullscreen
 * view once ended up stuck on the first line.
 */
@Composable
internal fun rememberLyricClock(
    reportedPositionMs: () -> Long,
    isPlaying: Boolean,
    trackKey: Any? = Unit,
    offsetMs: () -> Long = { LyricsOptions.offsetMs },
): State<Long> {
    val clock = remember(trackKey) { mutableLongStateOf(0L) }
    var lastReported by remember(trackKey) { mutableLongStateOf(0L) }
    var lastFrameNanos by remember(trackKey) { mutableLongStateOf(0L) }
    // The frame loop is launched once per track, but the position lambda
    // comes from whatever composition first created it — capture it through
    // rememberUpdatedState so the loop keeps calling the freshest one and
    // keeps seeing the position state that lambda reads. Reading the position
    // through a derivedStateOf here instead froze the steering on the first
    // composition's value — the lambda reads no snapshot state of its own,
    // so the derived value computed once and never again: seeks were never
    // followed, drift was never corrected, and after the screen had been off
    // the sweep resumed from where it froze instead of where the song was.
    val currentReportPosition by rememberUpdatedState(reportedPositionMs)

    // The user's lyrics offset, applied where the clock is read rather than
    // where it is written — folding it into the stored value instead would
    // make every player reading look offset and poison the steering. Positive
    // (lyrics felt late) shifts the sweep earlier, negative (felt early)
    // later. Collected as state so dragging the offset slider retimes the
    // sweep mid-line, live.
    val offsetState = remember(trackKey) { mutableLongStateOf(0L) }
    LaunchedEffect(trackKey) {
        snapshotFlow { offsetMs() }.collect { offsetState.longValue = it }
    }

    // Paused: follow the readings exactly, so a seek while paused lands
    // immediately and nothing drifts.
    LaunchedEffect(isPlaying, trackKey) {
        if (isPlaying) return@LaunchedEffect
        snapshotFlow { currentReportPosition() }.collect { reading ->
            clock.longValue = reading
        }
    }

    LaunchedEffect(isPlaying, trackKey) {
        if (!isPlaying) return@LaunchedEffect
        lastFrameNanos = 0L
        lastReported = currentReportPosition()
        clock.longValue = lastReported
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val elapsed = ((frameNanos - lastFrameNanos) / 1_000_000L).coerceIn(0L, 250L)
                    clock.longValue += elapsed
                }
                lastFrameNanos = frameNanos
            }
            val reading = currentReportPosition()
            if (reading != lastReported) {
                val estimate = clock.longValue
                // Steer, don't set: small corrections ease in, big jumps
                // (seeks) are taken whole. Reading jitter only ever nudges
                // the clock forward and in small steps, so the boundary
                // never snaps or rewinds mid-word — both read as a stutter
                // on fast words.
                val delta = reading - estimate
                clock.longValue = when {
                    abs(delta) <= 60L -> estimate + (delta / 4).coerceAtLeast(0L)
                    abs(delta) < 600L -> estimate + delta / 2
                    else -> reading
                }
                lastReported = reading
            }
        }
    }
    // Read-time offset: both states are re-created with the track, so the
    // derived computation is keyed to them.
    return remember(clock, offsetState) {
        derivedStateOf { clock.longValue + offsetState.longValue }
    }
}

/**
 * The line currently being sung, as a fraction of the whole lyrics list.
 * A [derivedStateOf] over the frame clock, so it only recomposes the lines
 * whose "active" bit actually flipped (a handful of times per song) rather
 * than everything reading the clock.
 */
@Composable
internal fun rememberActiveLyricIndex(
    clock: State<Long>,
    lyrics: List<TimeSyncedLyric>,
): State<Int> = remember(lyrics, clock) {
    derivedStateOf { lyrics.indexOfLast { clock.value >= it.startTimeMs }.coerceAtLeast(0) }
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
 * A wrapped line is handled a visual line at a time: the ones the boundary
 * has already passed are lit whole, the one holding the boundary is cut at
 * it, and the ones after it stay with the dim copy. Clipping at a single x
 * across the whole block instead lit the second row of a two-row line in
 * lock-step with the first, which read as two lines sweeping together.
 *
 * The clip is recomputed in the draw phase only; a frame costs one clip and
 * one redraw of already-measured text, no recomposition.
 */
@Composable
internal fun WordSyncedLyricSweepText(
    lyric: TimeSyncedLyric,
    positionMs: State<Long>,
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
                    val now = positionMs.value
                    val revealed = lyric.revealedChars(now)
                    if (revealed <= 0f) return@drawWithContent
                    val textLength = lyric.text.length
                    if (revealed >= textLength) {
                        drawContent()
                        return@drawWithContent
                    }
                    val revealedIndex = revealed.toInt().coerceIn(0, textLength - 1)
                    // Find the visual row holding the boundary: rows before it
                    // are lit whole, rows after it not at all, and only the
                    // boundary row is cut. A single rect spanning the block's
                    // full height instead lit every row at the same x — which
                    // read as two wrapped rows sweeping together.
                    var row = 0
                    while (row < measured.lineCount - 1 &&
                        revealedIndex >= measured.getLineEnd(row, visibleEnd = false)
                    ) {
                        row++
                    }
                    // Interpolate the boundary's x within the character it is
                    // passing through, so the sweep moves continuously rather
                    // than a letter at a time.
                    val fraction = revealed - revealedIndex
                    val left = measured.getHorizontalPosition(revealedIndex, usePrimaryDirection = true)
                    val right = measured.getHorizontalPosition(
                        (revealedIndex + 1).coerceAtMost(textLength),
                        usePrimaryDirection = true,
                    )
                    val boundary = left + (right - left) * fraction
                    val boundaryTop = measured.getLineTop(row)
                    val boundaryBottom = measured.getLineBottom(row)
                    // The boundary row, cut at the boundary.
                    clipRect(left = 0f, top = boundaryTop, right = boundary, bottom = boundaryBottom) {
                        this@drawWithContent.drawContent()
                    }
                    if (row > 0) {
                        // Everything above it is already sung: lit whole.
                        clipRect(left = 0f, top = 0f, right = size.width, bottom = boundaryTop) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
        )
    }
}
