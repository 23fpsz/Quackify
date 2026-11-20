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
 * You may view and audit this source code for personal and educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.player

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A track's beat grid in its own timeline seconds, with the analyzer's trust in it. */
internal data class TrackGrid(
    val bpm: Double,
    val beatStarts: DoubleArray,
    val barStarts: DoubleArray,
    val confidence: Double,
)

/**
 * Turns a mono PCM window into a beat grid: spectral-flux onset envelopes, a
 * tempo from energy-normalized autocorrelation with an octave-folded metrical
 * pass, a beat grid tracked forward through the envelope with a phase-locked
 * loop, and the bar lines picked on bass-band onset strength. Pure Kotlin, no
 * platform dependencies, safe to run on any dispatcher.
 */
internal object TrackAnalyzer {

    private const val FRAME = 512

    private const val HOP = 128

    private const val LOW_BAND_HZ = 150.0

    private const val MIN_SEARCH_BPM = 70.0

    private const val MAX_SEARCH_BPM = 200.0

    private const val MIN_VALID_BPM = 60.0

    private const val MAX_VALID_BPM = 220.0

    private const val MIN_TEMPO_CORRELATION = 0.12

    private const val PHASE_WINDOW_SECONDS = 30.0

    private const val ONSET_GATE = 0.15

    private const val PHASE_GAIN = 0.22

    private const val INTERVAL_GAIN = 0.01

    private const val MAX_INTERVAL_DRIFT = 0.03

    private const val MIN_BEATS = 8

    private const val DOWNBEAT_BEAT_LIMIT = 256

    private const val BASS_COMB_WEIGHT = 2.0

    fun analyze(samples: FloatArray, sampleRate: Int, startOffsetSec: Double): TrackGrid? {
        if (sampleRate <= 0 || samples.size < sampleRate * 12L) return null
        val envelopes = onsetEnvelopes(samples, sampleRate)
        val envelope = envelopes.full
        val lowEnvelope = envelopes.low
        if (envelope.size < 64) return null

        val framesPerSecond = sampleRate.toDouble() / HOP
        val lag = tempoLag(envelope, framesPerSecond) ?: return null
        val refined = parabolicRefine(envelope, lag)

        var beatLag = refined
        var phase = combPhase(envelope, lowEnvelope, beatLag, framesPerSecond * PHASE_WINDOW_SECONDS)
        var firstBeat = firstOnset(envelope, phase.position, beatLag)
        var grid = trackBeats(envelope, firstBeat, beatLag)
        if (grid.intervals.size >= 8) {
            beatLag = median(grid.intervals)
            phase = combPhase(envelope, lowEnvelope, beatLag, envelope.size.toDouble())
            firstBeat = firstOnset(envelope, phase.position, beatLag)
            grid = trackBeats(envelope, firstBeat, beatLag)
        }
        if (grid.beats.size < MIN_BEATS) return null

        val bpm = framesPerSecond * 60.0 / beatLag
        if (!bpm.isFinite() || bpm < MIN_VALID_BPM || bpm > MAX_VALID_BPM) return null

        val bars = downbeats(envelopes, grid.beats, framesPerSecond)
        val frameCenter = FRAME / 2.0 / sampleRate
        val beats = DoubleArray(grid.beats.size) { startOffsetSec + grid.beats[it] / framesPerSecond + frameCenter }
        val barStarts = DoubleArray(bars.size) { startOffsetSec + bars[it] / framesPerSecond + frameCenter }
        val confidence = confidence(envelope, grid, framesPerSecond, beatLag)
        if (!confidence.isFinite() || confidence <= 0.0) return null
        return TrackGrid(bpm, beats, barStarts, confidence)
    }

    private class Envelopes(val full: DoubleArray, val low: DoubleArray)

    private class Grid(val beats: DoubleArray, val intervals: DoubleArray)

    private class Phase(val position: Double, val score: Double)

    private fun onsetEnvelopes(samples: FloatArray, sampleRate: Int): Envelopes {
        val frames = 1 + (samples.size - FRAME) / HOP
        val fps = sampleRate.toDouble() / HOP
        val bins = FRAME / 2
        val lowBins = max(2, (LOW_BAND_HZ * FRAME / sampleRate).toInt()).coerceAtMost(bins - 1)
        val full = DoubleArray(frames)
        val low = DoubleArray(frames)
        val previous = DoubleArray(bins)
        val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2.0 * Math.PI * it / (FRAME - 1)) }
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        for (frame in 0 until frames) {
            val start = frame * HOP
            for (i in 0 until FRAME) {
                re[i] = samples[start + i] * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            var flux = 0.0
            var lowFlux = 0.0
            for (bin in 1 until bins) {
                val magnitude = ln(1.0 + sqrt(re[bin] * re[bin] + im[bin] * im[bin]))
                val rise = max(0.0, magnitude - previous[bin])
                flux += rise
                if (bin < lowBins) lowFlux += rise
                previous[bin] = magnitude
            }
            full[frame] = flux
            low[frame] = lowFlux
        }
        normalizeEnvelope(full, fps)
        normalizeEnvelope(low, fps)
        return Envelopes(full, low)
    }

    private fun normalizeEnvelope(envelope: DoubleArray, fps: Double) {
        if (envelope.isEmpty()) return
        val radius = max(2, (fps * 0.35).toInt())
        val prefix = DoubleArray(envelope.size + 1)
        for (i in envelope.indices) prefix[i + 1] = prefix[i] + envelope[i]
        for (i in envelope.indices) {
            val left = max(0, i - radius)
            val right = min(envelope.size, i + radius + 1)
            val mean = (prefix[right] - prefix[left]) / max(1, right - left)
            envelope[i] = max(0.0, envelope[i] - mean * 1.08)
        }
        var peak = 0.0
        for (value in envelope) if (value > peak) peak = value
        if (peak > 0.0) {
            for (i in envelope.indices) envelope[i] = sqrt(envelope[i] / peak)
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val size = re.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var length = 2
        while (length <= size) {
            val angle = -2.0 * Math.PI / length
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            var start = 0
            while (start < size) {
                var weightRe = 1.0
                var weightIm = 0.0
                val half = length / 2
                for (offset in 0 until half) {
                    val evenRe = re[start + offset]
                    val evenIm = im[start + offset]
                    val oddRe = re[start + offset + half] * weightRe - im[start + offset + half] * weightIm
                    val oddIm = re[start + offset + half] * weightIm + im[start + offset + half] * weightRe
                    re[start + offset] = evenRe + oddRe
                    im[start + offset] = evenIm + oddIm
                    re[start + offset + half] = evenRe - oddRe
                    im[start + offset + half] = evenIm - oddIm
                    val nextRe = weightRe * stepRe - weightIm * stepIm
                    weightIm = weightRe * stepIm + weightIm * stepRe
                    weightRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
    }

    private fun correlation(envelope: DoubleArray, lag: Int): Double {
        if (lag <= 0 || lag >= envelope.size) return 0.0
        var cross = 0.0
        var leftEnergy = 0.0
        var rightEnergy = 0.0
        for (i in lag until envelope.size) {
            val left = envelope[i]
            val right = envelope[i - lag]
            cross += left * right
            leftEnergy += left * left
            rightEnergy += right * right
        }
        return cross / sqrt(max(1e-12, leftEnergy * rightEnergy))
    }

    private fun tempoLag(envelope: DoubleArray, fps: Double): Int? {
        val minLag = max(2, floor(fps * 60.0 / MAX_SEARCH_BPM).toInt())
        val maxLag = ceil(fps * 60.0 / MIN_SEARCH_BPM).toInt()
        if (maxLag >= envelope.size) return null
        var best = minLag
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val bpm = fps * 60.0 / lag
            val prior = exp(-((bpm - 118.0) / 75.0).pow(2.0))
            val score = correlation(envelope, lag) + 0.45 * correlation(envelope, lag * 2) + 0.06 * prior
            if (score > bestScore) {
                bestScore = score
                best = lag
            }
        }
        var metrical = best
        var metricalScore = Double.NEGATIVE_INFINITY
        for (ratio in doubleArrayOf(0.5, 1.0, 2.0)) {
            val candidate = Math.round(best * ratio).toInt()
            if (candidate < minLag || candidate > maxLag) continue
            val bpm = fps * 60.0 / candidate
            val prior = exp(-0.5 * (log2(bpm / 120.0) / 0.9).pow(2.0))
            val score = correlation(envelope, candidate) * prior
            if (score > metricalScore) {
                metricalScore = score
                metrical = candidate
            }
        }
        return metrical
    }

    private fun parabolicRefine(envelope: DoubleArray, lag: Int): Double {
        if (lag <= 1 || lag >= envelope.size - 1) return lag.toDouble()
        val left = correlation(envelope, lag - 1)
        val center = correlation(envelope, lag)
        val right = correlation(envelope, lag + 1)
        val denominator = left - 2.0 * center + right
        if (abs(denominator) < 1e-9) return lag.toDouble()
        return lag + (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
    }

    private fun sampleEnvelope(envelope: DoubleArray, position: Double): Double {
        if (position < 0.0 || position >= envelope.size - 1) return 0.0
        val left = position.toInt()
        val fraction = position - left
        return envelope[left] * (1.0 - fraction) + envelope[left + 1] * fraction
    }

    // A quantized lag walks out of phase with the music over a long window, so
    // the first phase read covers a short stretch and the locked interval is
    // re-phased over the whole envelope before the final grid is laid down.
    // The bass band carries the weighting so the grid lands on the kick's
    // phase whenever the music offers one.
    private fun combPhase(envelope: DoubleArray, low: DoubleArray, lag: Double, limitFrames: Double): Phase {
        val limit = min(limitFrames, envelope.size - 1.0)
        val phaseCount = max(1, Math.round(lag).toInt())
        var best = 0.0
        var bestScore = Double.NEGATIVE_INFINITY
        for (phase in 0 until phaseCount) {
            var score = 0.0
            var count = 0
            var position = phase.toDouble()
            while (position < limit) {
                score += sampleEnvelope(envelope, position) + BASS_COMB_WEIGHT * sampleEnvelope(low, position)
                count++
                position += lag
            }
            score /= max(1, count)
            if (score > bestScore) {
                bestScore = score
                best = phase.toDouble()
            }
        }
        return Phase(best, bestScore)
    }

    private fun firstOnset(envelope: DoubleArray, position: Double, lag: Double): Double {
        val end = envelope.size - 1.0
        var anchored = position
        while (anchored < end && sampleEnvelope(envelope, anchored) < ONSET_GATE) {
            anchored += lag
        }
        return anchored
    }

    // The grid is tracked forward with a phase-locked loop rather than
    // extrapolated: each beat is predicted from the running grid, the envelope
    // is searched a quarter beat either side for the onset that actually
    // happened, and the error feeds back as a fast phase correction plus a
    // slow interval correction. Without this, a 0.15% tempo error accumulates
    // hundreds of milliseconds of phase drift across the window.
    private fun trackBeats(envelope: DoubleArray, firstBeat: Double, lag: Double): Grid {
        val beats = ArrayList<Double>(256)
        val intervals = ArrayList<Double>(128)
        val radius = lag * 0.25
        val maxDrift = lag * MAX_INTERVAL_DRIFT
        var position = firstBeat
        var interval = lag
        val end = envelope.size - 1.0
        while (position <= end + 1e-6) {
            beats.add(max(0.0, position))
            val predicted = position + interval
            if (predicted > end + 1e-6) break
            var found = 0.0
            var strength = 0.0
            if (predicted + radius < end) {
                val low = max(0, floor(predicted - radius).toInt())
                val high = min(envelope.size - 1, ceil(predicted + radius).toInt())
                for (frame in low..high) {
                    if (envelope[frame] > strength) {
                        strength = envelope[frame]
                        found = frame.toDouble()
                    }
                }
            }
            if (strength > ONSET_GATE && found > 0.0 && found + 1.0 < end) {
                val left = sampleEnvelope(envelope, found - 1.0)
                val center = sampleEnvelope(envelope, found)
                val right = sampleEnvelope(envelope, found + 1.0)
                val denominator = left - 2.0 * center + right
                if (abs(denominator) > 1e-9) {
                    found += (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
                }
                val error = found - predicted
                position = predicted + PHASE_GAIN * error
                interval = (interval + INTERVAL_GAIN * error).coerceIn(lag - maxDrift, lag + maxDrift)
                intervals.add(interval)
            } else {
                position = predicted
            }
        }
        return Grid(beats.toDoubleArray(), intervals.toDoubleArray())
    }

    private fun downbeats(envelopes: Envelopes, beats: DoubleArray, fps: Double): DoubleArray {
        var bestOffset = 0
        var bestScore = Double.NEGATIVE_INFINITY
        val limit = min(beats.size, DOWNBEAT_BEAT_LIMIT)
        for (offset in 0 until 4) {
            var lowScore = 0.0
            var fullScore = 0.0
            var count = 0
            var beat = offset
            while (beat < limit) {
                val position = beats[beat] * fps
                if (sampleEnvelope(envelopes.full, position) >= ONSET_GATE) {
                    lowScore += sampleEnvelope(envelopes.low, position)
                    fullScore += sampleEnvelope(envelopes.full, position)
                    count++
                }
                beat += 4
            }
            val score = (lowScore + 0.4 * fullScore) / max(1, count)
            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }
        val bars = ArrayList<Double>(beats.size / 4 + 1)
        var beat = bestOffset
        while (beat < beats.size) {
            bars.add(beats[beat])
            beat += 4
        }
        return bars.toDoubleArray()
    }

    private fun confidence(
        envelope: DoubleArray,
        grid: Grid,
        fps: Double,
        lag: Double,
    ): Double {
        val tempoStrength = correlation(envelope, max(1, Math.round(lag).toInt())).coerceIn(0.0, 1.0)
        if (tempoStrength < MIN_TEMPO_CORRELATION) return 0.0
        var beatStrength = 0.0
        for (beat in grid.beats) beatStrength += sampleEnvelope(envelope, beat * fps)
        beatStrength = if (grid.beats.isEmpty()) 0.0 else (beatStrength / grid.beats.size).coerceIn(0.0, 1.0)
        val regularity = intervalRegularity(grid.intervals)
        return (0.45 * tempoStrength + 0.30 * beatStrength + 0.25 * regularity).coerceIn(0.0, 1.0)
    }

    private fun intervalRegularity(intervals: DoubleArray): Double {
        if (intervals.size < 8) return 0.5
        val sorted = intervals.copyOf().also { it.sort() }
        val median = sorted[sorted.size / 2]
        if (median <= 0.0) return 0.0
        val q1 = sorted[sorted.size / 4]
        val q3 = sorted[(sorted.size * 3) / 4]
        return (1.0 - (q3 - q1) / median).coerceIn(0.0, 1.0)
    }

    private fun median(values: DoubleArray): Double {
        val sorted = values.copyOf().also { it.sort() }
        return sorted[sorted.size / 2]
    }
}
