/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * |    \| '__| _ \/ __| '_ \ / _ \| __|
 * |\  \ | |  __/\__ \ |_) | (_) | |_
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     |_|
 *                     |_|
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.player

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong

/** What a transition between two tracks should do, as the planner decided it. */
internal class TransitionSpec(
    val fadeMs: Long,
    val cueMs: Long,
    val incomingRate: Double,
    val phaseLock: Boolean,
    val outSilenceMs: Long,
    val outGrid: TrackGrid?,
    val inGrid: TrackGrid?,
)

/**
 * The most trailing silence a plan may skip over, matching the analyzer's own
 * cap: a longer dead tail is not trusted to be real content loss.
 */
private const val MAX_OUT_SILENCE_MS = 12_000L

/** The most leading silence a plan may cue past on the incoming side. */
private const val MAX_IN_SILENCE_MS = 10_000L

/**
 * Sizes a DJ-style transition from one track's beat grid into the next. The
 * ambition follows the evidence in explicit steps: a trusted grid on both
 * sides and a stretch that can reach the outgoing tempo gets the full blend
 * (whole bars, tempo stretch, phase lock); a trusted grid on the outgoing
 * side alone gets the fade still cut on its bar lines without stretching;
 * anything less - no grid, weak confidence, or tempi too far apart - falls
 * back to a plain crossfade of the manually configured length. [outSilenceMs]
 * moves the whole plan earlier by the track's silent tail, so the blend
 * crosses audible music instead of decaying into dead air; [inSilenceMs]
 * cues the incoming side past its silent intro, so the rising side of the
 * curve lands on the first audible frame rather than on a held breath.
 */
internal object AutomixPlanner {

    private const val MIN_FADE_SECONDS = 4.0

    private const val MAX_FADE_SECONDS = 12.0

    private const val DEFAULT_FADE_BEATS = 16.0

    private const val MIN_INCOMING_RATE = 0.9

    private const val MAX_INCOMING_RATE = 1.25

    private const val MIN_BEATMATCH_CONFIDENCE = 0.55

    private const val MIN_DJ_CONFIDENCE = 0.2

    private const val DEFAULT_FALLBACK_MS = 6_000L

    private const val LOCAL_WINDOW_SECONDS = 8.0

    private val SPOKEN_TEXT = Regex(
        """\b(podcast|episode|audiobook|concert)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val LIVE_TEXT = Regex(
        """[(\[]\s*(?:[^)\]]*\s)?live(?:\s+(?:at|from|in|on|version|session|recording|performance|edit)\b[^)\]]*)?\s*[)\]]""" +
            """|[-–—]\s*live(?:\s+(?:at|from|in|on|version|session|recording|performance|edit)\b.*)?\s*$""" +
            """|^\s*live\s*(?::|\s(?:at|from|in|on)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /** Spoken word and live recordings are left alone: mixing them is never wanted. */
    fun unmixable(title: String): Boolean =
        SPOKEN_TEXT.containsMatchIn(title) || LIVE_TEXT.containsMatchIn(title)

    fun plan(
        out: TrackGrid?,
        into: TrackGrid?,
        outDurationMs: Long,
        inDurationMs: Long,
        fallbackFadeMs: Long,
        outSilenceMs: Long = 0L,
        inSilenceMs: Long = 0L,
    ): TransitionSpec {
        val inDuration = inDurationMs / 1000.0
        val nominal = outDurationMs / 1000.0
        val silenceMs = outSilenceMs.coerceIn(0L, MAX_OUT_SILENCE_MS)
        val trimmed = nominal - silenceMs / 1000.0
        val planSilenceMs: Long
        val outDuration: Double
        if (trimmed > MIN_FADE_SECONDS + 1.0) {
            outDuration = trimmed
            planSilenceMs = silenceMs
        } else {
            outDuration = nominal
            planSilenceMs = 0L
        }
        if (outDuration <= 0.0 || inDuration <= 0.0) {
            return plain(out, into, fallbackFadeMs, outDuration, inDuration, planSilenceMs)
        }
        val outBpm = localBpm(out, outDuration - LOCAL_WINDOW_SECONDS)
        if (out == null || outBpm <= 0.0 || out.beatStarts.size < 2 || out.confidence < MIN_DJ_CONFIDENCE) {
            return plain(out, into, fallbackFadeMs, outDuration, inDuration, planSilenceMs, inSilenceMs)
        }
        val inBpm = localBpm(into, 0.0)
        val rate = if (inBpm > 0.0) 1.0 / foldedRatio(outBpm, inBpm) else 1.0
        val beatmatched = into != null &&
            inBpm > 0.0 &&
            into.beatStarts.size >= 2 &&
            out.confidence >= MIN_BEATMATCH_CONFIDENCE &&
            into.confidence >= MIN_BEATMATCH_CONFIDENCE &&
            rate >= MIN_INCOMING_RATE &&
            rate <= MAX_INCOMING_RATE
        val beatSeconds = 60.0 / outBpm
        val fade = (DEFAULT_FADE_BEATS * beatSeconds).coerceIn(MIN_FADE_SECONDS, MAX_FADE_SECONDS)
        val start = barStartNear(out, outDuration, outDuration - fade, beatSeconds)
        val span = (outDuration - start).coerceIn(MIN_FADE_SECONDS, MAX_FADE_SECONDS)
        return TransitionSpec(
            fadeMs = (span * 1000.0).roundToLong(),
            cueMs = incomingCueMs(inSilenceMs, inDurationMs, into, beatmatched, beatSeconds),
            incomingRate = if (beatmatched) roundRate(rate) else 1.0,
            phaseLock = beatmatched,
            outSilenceMs = planSilenceMs,
            outGrid = out,
            inGrid = into,
        )
    }

    private fun plain(
        out: TrackGrid?,
        into: TrackGrid?,
        fallbackFadeMs: Long,
        outDuration: Double,
        inDuration: Double,
        outSilenceMs: Long,
        inSilenceMs: Long = 0L,
    ): TransitionSpec {
        val fallback = (if (fallbackFadeMs > 0L) fallbackFadeMs else DEFAULT_FALLBACK_MS) / 1000.0
        val span = if (outDuration > 0.0 && inDuration > 0.0) {
            minOf(fallback, outDuration / 3.0, inDuration / 3.0)
        } else {
            fallback
        }
        return TransitionSpec(
            fadeMs = (span * 1000.0).roundToLong(),
            cueMs = incomingCueMs(inSilenceMs, (inDuration * 1000.0).toLong(), null, false, 0.0),
            incomingRate = 1.0,
            phaseLock = false,
            outSilenceMs = outSilenceMs,
            outGrid = out,
            inGrid = into,
        )
    }

    /**
     * Where the incoming track should start rendering: past its leading
     * silence, snapped to the incoming grid's own structure when a beatmatch
     * is on and a bar line sits within a bar of the audible start, and never
     * so far in that the blend eats a quarter of the track.
     */
    private fun incomingCueMs(
        inSilenceMs: Long,
        inDurationMs: Long,
        into: TrackGrid?,
        beatmatched: Boolean,
        beatSeconds: Double,
    ): Long {
        if (inDurationMs <= 0L) return 0L
        val ceiling = minOf(MAX_IN_SILENCE_MS, inDurationMs / 4)
        val raw = inSilenceMs.coerceIn(0L, ceiling)
        if (raw <= 0L) return 0L
        if (!beatmatched || into == null) return raw
        val snapLimitMs = raw + (4 * beatSeconds * 1000.0).roundToLong()
        val target = raw / 1000.0
        val bars = into.barStarts
        if (bars.isNotEmpty()) {
            val barIndex = indexAtOrAfter(bars, target)
            if (barIndex < bars.size) {
                val bar = (bars[barIndex] * 1000.0).roundToLong()
                if (bar in raw..snapLimitMs) return bar
            }
        }
        val beats = into.beatStarts
        if (beats.isNotEmpty()) {
            val beatIndex = indexAtOrAfter(beats, target)
            if (beatIndex < beats.size) {
                val beat = (beats[beatIndex] * 1000.0).roundToLong()
                if (beat in raw..snapLimitMs) return beat
            }
        }
        return raw
    }

    /** The tempo around [atSec] read off the grid's own beats, so drift is honoured; the reported tempo otherwise. */
    private fun localBpm(grid: TrackGrid?, atSec: Double): Double {
        if (grid == null) return 0.0
        val beats = grid.beatStarts
        if (beats.size < 2) return grid.bpm.takeIf { it > 0.0 } ?: 0.0
        val from = indexAtOrAfter(beats, atSec)
        val to = indexAtOrAfter(beats, atSec + LOCAL_WINDOW_SECONDS)
        if (to - from >= 2) {
            val interval = (beats[to - 1] - beats[from]) / (to - from - 1)
            if (interval > 0.0) return 60.0 / interval
        }
        return grid.bpm.takeIf { it > 0.0 } ?: 0.0
    }

    /**
     * The outgoing grid's bar line closest to [target], when one lies within a
     * bar of it and leaves a usable fade before the track ends.
     */
    private fun barStartNear(
        out: TrackGrid?,
        outDuration: Double,
        target: Double,
        beatSeconds: Double,
    ): Double {
        val bars = out?.barStarts
        if (bars == null || bars.isEmpty()) return target
        val bar = 4 * beatSeconds
        var best = target
        var bestDistance = Double.MAX_VALUE
        for (start in bars) {
            if (start > outDuration - MIN_FADE_SECONDS) continue
            if (start < outDuration - MAX_FADE_SECONDS - bar) continue
            val distance = abs(start - target)
            if (distance < bestDistance) {
                bestDistance = distance
                best = start
            }
        }
        return best
    }

    internal fun indexAtOrAfter(values: DoubleArray, target: Double): Int {
        var low = 0
        var high = values.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (values[mid] < target) low = mid + 1 else high = mid
        }
        return low
    }

    private fun foldedRatio(outBpm: Double, inBpm: Double): Double {
        var ratio = inBpm / outBpm
        while (ratio > 1.5) ratio /= 2
        while (ratio < 0.67) ratio *= 2
        return ratio
    }

    private fun roundRate(rate: Double): Double = round(rate * 10_000.0) / 10_000.0
}
