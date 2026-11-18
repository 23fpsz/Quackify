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

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.quackify.backend.api.AudioFeatures
import com.quackify.backend.core.log.Logs
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val log = Logs.of(Logs.Module.PLAYER)

/**
 * Runs the crossfade between the session player and a spare one. One ExoPlayer
 * renders one track at a time with a single gain, so an overlap needs a second
 * decoder: the spare is loaded with the queued next track at its cue point and
 * started silently, then the two are faded across each other with an
 * equal-power curve (sin/cos, so the summed power stays flat and the blend has
 * no dip in the middle).
 *
 * Halfway through the blend the session moves to the spare - nothing is
 * re-buffered or re-seeked, the two players just trade roles - and the
 * Started/Ended pair the queue advance normally produces is emitted there, so
 * the layer above adopts the new track exactly like a playlist auto-advance.
 *
 * An automix plan adds the DJ parts on top: the incoming track is
 * tempo-stretched to the outgoing tempo from its first rendered frame, its
 * beats are held in phase with the outgoing grid by brief speed nudges, and
 * after the blend the stretch is walked back off one small step per beat
 * rather than snapped, so the listener never hears the tempo lurch.
 */
internal class CrossfadeEngine(
    private val session: () -> ExoPlayer?,
    private val spare: () -> ExoPlayer,
    private val buildSource: (MediaItem) -> MediaSource,
    private val fadeMs: () -> Long,
    private val automixOn: () -> Boolean,
    private val sessionDurationMs: () -> Long,
    private val userRate: () -> Float,
    private val baseVolume: (ExoPlayer) -> Float,
    private val onEvent: (PlayerEvent) -> Unit,
    private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer, uri: String, durationMs: Long) -> Unit,
) {

    private enum class Phase { IDLE, ARMING, FADING, BAILING }

    private class HeldTrack(
        val item: MediaItem,
        val durationMs: Long,
        val fadeMs: Long,
        val cueMs: Long,
        val incomingRate: Double,
        val phaseLock: Boolean,
        val outGrid: AudioFeatures.TrackGrid?,
        val inGrid: AudioFeatures.TrackGrid?,
        val smart: Boolean,
    )

    private class BeatPhase(val since: Double, val interval: Double)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var ticker: Job? = null

    @Volatile
    private var held: HeldTrack? = null

    private var phase = Phase.IDLE

    private var outgoing: ExoPlayer? = null

    private var incoming: ExoPlayer? = null

    private var armed: HeldTrack? = null

    private var handedOff = false

    private var outgoingUri = ""

    private var armDeadline = 0L

    private var bailStartedAt = 0L

    private var bailFromVolume = 0f

    private var nudgeUntil = 0L

    private var lockQuietUntil = 0L

    private var lockCount = 0

    private val lockErrors = DoubleArray(LOCK_SAMPLES)

    private var easing: ExoPlayer? = null

    private var easeRate = 1.0

    private var easeStep = 0.0

    private var easeStepMs = 0L

    private var easeNextAtMs = 0L

    private var easeItemIndex = 0

    fun hold(item: MediaItem, durationMs: Long, spec: TransitionSpec?) {
        scope.launch {
            held = HeldTrack(
                item = item,
                durationMs = durationMs,
                fadeMs = spec?.fadeMs ?: 0L,
                cueMs = spec?.cueMs ?: 0L,
                incomingRate = spec?.incomingRate ?: 1.0,
                phaseLock = spec?.phaseLock ?: false,
                outGrid = spec?.outGrid,
                inGrid = spec?.inGrid,
                smart = spec != null,
            )
            startTicking()
        }
    }

    fun dropHold() {
        scope.launch {
            held = null
            if (phase == Phase.ARMING) finish()
        }
    }

    fun hasHold(): Boolean = held != null

    fun onUserSeek() {
        scope.launch {
            if (phase == Phase.IDLE) return@launch
            val rehold = !handedOff && (phase == Phase.FADING || phase == Phase.ARMING)
            bail()
            if (rehold) reholdArmed()
        }
    }

    /**
     * The session player reached the end of its own track. True when a blend is
     * running and this class takes the ending over - the handoff emits the
     * canonical event pair itself, so the caller must not emit its own Ended.
     */
    fun onSessionEnded(): Boolean {
        return when {
            phase == Phase.FADING && !handedOff -> {
                outgoing?.volume = 0f
                handOff()
                true
            }
            phase == Phase.FADING -> {
                finish()
                false
            }
            phase == Phase.ARMING -> {
                bail()
                false
            }
            else -> false
        }
    }

    fun applyVolumes(base: (ExoPlayer) -> Float) {
        when (phase) {
            Phase.IDLE -> session()?.let { it.volume = base(it) }
            Phase.ARMING -> {
                session()?.let { it.volume = base(it) }
                incoming?.let { it.volume = 0f }
            }
            Phase.FADING -> {
                val armed = armed ?: return
                val out = outgoing ?: return
                val into = incoming ?: return
                applyFadeVolumes(base, armed, out, into)
            }
            Phase.BAILING -> session()?.let { it.volume = base(it) }
        }
    }

    fun applyUserRate(user: Float) {
        when {
            phase == Phase.FADING || phase == Phase.ARMING -> {
                val armed = armed
                outgoing?.setPlaybackSpeed(user)
                incoming?.setPlaybackSpeed(user * (armed?.incomingRate ?: 1.0).toFloat())
            }
            easing != null -> easing?.setPlaybackSpeed(user * easeRate.toFloat())
            else -> session()?.setPlaybackSpeed(user)
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        easing = null
        nudgeUntil = 0L
        lockQuietUntil = 0L
        lockCount = 0
        session()?.let {
            it.volume = baseVolume(it)
            it.setPlaybackSpeed(userRate())
        }
        val spareSide = if (handedOff) outgoing else incoming
        spareSide?.let {
            it.stop()
            it.clearMediaItems()
            it.volume = 1f
        }
        outgoing = null
        incoming = null
        held = null
        armed = null
        outgoingUri = ""
        handedOff = false
        phase = Phase.IDLE
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && (held != null || phase != Phase.IDLE || easing != null)) {
                tick()
                delay(
                    when (phase) {
                        Phase.IDLE -> IDLE_STEP_MS
                        Phase.ARMING -> ARM_STEP_MS
                        Phase.FADING -> FADE_STEP_MS
                        Phase.BAILING -> BAIL_STEP_MS
                    }
                )
            }
        }
    }

    private fun tick() {
        if (phase == Phase.FADING || phase == Phase.BAILING) mirrorPlayWhenReady()
        if (phase == Phase.IDLE && easing != null) driveEase()
        when (phase) {
            Phase.IDLE -> plan()
            Phase.ARMING -> driveArming()
            Phase.FADING -> driveFade()
            Phase.BAILING -> driveBail()
        }
    }

    private fun mirrorPlayWhenReady() {
        val out = outgoing ?: return
        val into = incoming ?: return
        if (handedOff) {
            out.playWhenReady = into.playWhenReady
        } else {
            into.playWhenReady = out.playWhenReady
        }
    }

    private fun plan() {
        val hold = held ?: return
        val player = session() ?: run {
            held = null
            return
        }
        val gateOn = if (hold.smart) automixOn() else fadeMs() > 0L
        if (!gateOn) {
            held = null
            return
        }
        if (!player.isPlaying) return
        if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
            held = null
            return
        }
        val outDuration = sessionDurationMs()
        if (outDuration <= 0L) return
        val configured = if (hold.smart) hold.fadeMs else fadeMs()
        val fade = fadeFor(configured, outDuration, hold.durationMs)
        if (fade <= 0L) return
        val remaining = (outDuration - fade) - player.currentPosition
        if (remaining > fade + ARM_LEAD_MS) return
        arm(hold, fade)
    }

    private fun fadeFor(configured: Long, outDurationMs: Long, inDurationMs: Long): Long {
        val caps = listOfNotNull(
            outDurationMs.takeIf { it > 0L },
            inDurationMs.takeIf { it > 0L },
        )
        if (caps.isEmpty()) return 0L
        return minOf(configured, caps.min() / 3).coerceAtLeast(0L)
    }

    private fun arm(hold: HeldTrack, fade: Long) {
        val out = session() ?: return
        val into = spare()
        held = null
        val armedTrack = HeldTrack(
            item = hold.item,
            durationMs = hold.durationMs,
            fadeMs = fade,
            cueMs = hold.cueMs,
            incomingRate = hold.incomingRate,
            phaseLock = hold.phaseLock,
            outGrid = hold.outGrid,
            inGrid = hold.inGrid,
            smart = hold.smart,
        )
        armed = armedTrack
        outgoingUri = out.currentMediaItem?.mediaId.orEmpty()
        armDeadline = SystemClock.elapsedRealtime() + ARM_TIMEOUT_MS
        handedOff = false
        outgoing = out
        incoming = into
        into.setPlaybackSpeed(out.playbackParameters.speed * armedTrack.incomingRate.toFloat())
        into.volume = 0f
        into.setMediaSource(buildSource(hold.item))
        into.prepare()
        into.seekTo(armedTrack.cueMs)
        into.playWhenReady = false
        phase = Phase.ARMING
        log.i(
            "crossfade armed '${armedTrack.item.mediaId.takeLast(12)}' over ${fade}ms " +
                "at ${out.currentPosition}ms of '${outgoingUri.takeLast(12)}' " +
                "rate=${armedTrack.incomingRate} lock=${armedTrack.phaseLock}"
        )
    }

    private fun driveArming() {
        val out = outgoing ?: return finish()
        val into = incoming ?: return finish()
        val armed = armed ?: return finish()
        if (!stillWorthFading(armed)) return bail()
        if (!out.playWhenReady) {
            bail()
            reholdArmed()
            return
        }
        val expired = SystemClock.elapsedRealtime() > armDeadline
        val ready = into.playbackState == Player.STATE_READY
        if (expired && !ready) return bail()
        val atFadePoint = sessionDurationMs() - out.currentPosition <= armed.fadeMs
        if (atFadePoint && ready) startFade()
    }

    private fun startFade() {
        val into = incoming ?: return bail()
        into.volume = 0f
        into.playWhenReady = true
        lockQuietUntil = SystemClock.elapsedRealtime() + LOCK_START_MS
        lockCount = 0
        phase = Phase.FADING
        log.i("crossfade blending into '${armed?.item?.mediaId?.takeLast(12)}' over ${armed?.fadeMs}ms")
    }

    private fun driveFade() {
        val out = outgoing ?: return finish()
        val into = incoming ?: return finish()
        val armed = armed ?: return finish()
        if (!stillWorthFading(armed)) {
            handOff()
            finish()
            return
        }
        if (!handedOff && into.playbackState == Player.STATE_IDLE) return bail()
        val progress = fadeProgress(armed, out, into)
        applyFadeVolumes(baseVolume, armed, out, into)
        if (armed.phaseLock) holdPhase(armed, out, into, progress)
        if (!handedOff && progress >= HANDOFF_AT) handOff()
        if (progress >= 1f) {
            handOff()
            finish()
        }
    }

    private fun fadeProgress(armed: HeldTrack, out: ExoPlayer, into: ExoPlayer): Float {
        val elapsed = (into.currentPosition - armed.cueMs).coerceAtLeast(0L)
        val wallSpan = armed.fadeMs * armed.incomingRate
        val incomingProgress = if (wallSpan > 0.0) {
            (elapsed / wallSpan).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
        val outRemaining = (sessionDurationMs() - out.currentPosition).coerceAtLeast(0L)
        val endDriven = ((armed.fadeMs - outRemaining).toFloat() / armed.fadeMs).coerceIn(0f, 1f)
        return maxOf(incomingProgress, endDriven)
    }

    private fun applyFadeVolumes(
        base: (ExoPlayer) -> Float,
        armed: HeldTrack,
        out: ExoPlayer,
        into: ExoPlayer,
    ) {
        val progress = fadeProgress(armed, out, into)
        val rise = sin(progress * HALF_PI)
        val fall = cos(progress * HALF_PI)
        val headroom = headroomFor(rise, fall)
        out.volume = base(out) * fall * headroom
        into.volume = base(into) * rise * headroom
    }

    private fun headroomFor(rise: Float, fall: Float): Float {
        val sum = rise + fall
        return if (sum <= 1f) 1f else 1f / sqrt(sum)
    }

    private fun stillWorthFading(armed: HeldTrack): Boolean {
        val gateOn = if (armed.smart) automixOn() else fadeMs() > 0L
        if (!gateOn) return false
        val player = session() ?: return false
        val expected = if (handedOff) armed.item.mediaId else outgoingUri
        return player.currentMediaItem?.mediaId == expected
    }

    private fun handOff() {
        if (handedOff) return
        val out = outgoing ?: return
        val into = incoming ?: return
        val oldUri = outgoingUri
        val newUri = armed?.item?.mediaId.orEmpty()
        handedOff = true
        onHandoff(out, into, newUri, armed?.durationMs ?: 0L)
        onEvent(PlayerEvent.Ended(oldUri))
        onEvent(PlayerEvent.Started(newUri))
    }

    /**
     * Keeps the incoming beats on the outgoing grid by running the incoming
     * track a few percent fast or slow for exactly as long as it takes to close
     * the measured gap. Pulsed rather than continuous: a speed change is only
     * heard once the audio already buffered ahead has played out, so a
     * controller reacting to every reading would chase errors it had already
     * fixed.
     */
    private fun holdPhase(armed: HeldTrack, out: ExoPlayer, into: ExoPlayer, progress: Float) {
        val now = SystemClock.elapsedRealtime()
        if (nudgeUntil != 0L) {
            if (now < nudgeUntil && into.isPlaying) return
            nudgeUntil = 0L
            into.setPlaybackSpeed(userRate() * armed.incomingRate.toFloat())
            lockQuietUntil = now + LOCK_SETTLE_MS
            lockCount = 0
            return
        }
        if (progress >= LOCK_UNTIL || now < lockQuietUntil) return
        if (!out.isPlaying || !into.isPlaying) {
            lockCount = 0
            return
        }
        val error = phaseError(armed, out, into) ?: return
        lockErrors[lockCount++] = error
        if (lockCount < LOCK_SAMPLES) return
        lockCount = 0
        lockErrors.sort()
        val median = lockErrors[LOCK_SAMPLES / 2]
        if (abs(median) < LOCK_DEADBAND_SECONDS) {
            lockQuietUntil = now + LOCK_IN_PHASE_MS
            return
        }
        val base = userRate() * armed.incomingRate
        val factor = if (median > 0.0) {
            1f - NUDGE
        } else {
            minOf(1f + NUDGE, (MAX_TOTAL_SPEED_UP / armed.incomingRate).toFloat())
        }
        if (factor <= 1f && median < 0.0) return
        into.setPlaybackSpeed((base * factor).toFloat())
        nudgeUntil = now + (abs(median) / (base * abs(factor - 1f)) * 1000.0)
            .roundToLong().coerceIn(1L, MAX_NUDGE_MS)
    }

    private fun phaseError(armed: HeldTrack, out: ExoPlayer, into: ExoPlayer): Double? {
        val outGrid = armed.outGrid ?: return null
        val inGrid = armed.inGrid ?: return null
        val outPhase = beatPhase(outGrid, out.currentPosition / 1000.0) ?: return null
        val inPhase = beatPhase(inGrid, into.currentPosition / 1000.0) ?: return null
        val user = userRate().toDouble()
        if (user <= 0.0) return null
        val outWall = outPhase.since / user
        val inWall = inPhase.since / (user * armed.incomingRate)
        val period = minOf(outPhase.interval / user, inPhase.interval / (user * armed.incomingRate))
        if (period <= 0.0) return null
        var diff = (inWall - outWall) % period
        if (diff > period / 2) diff -= period
        if (diff < -period / 2) diff += period
        if (abs(diff) > period * LOCK_MAX_BEATS) return null
        return diff
    }

    private fun beatPhase(grid: AudioFeatures.TrackGrid, positionSec: Double): BeatPhase? {
        val beats = grid.beatStarts
        if (beats.size < 2) return null
        val index = AutomixPlanner.indexAtOrAfter(beats, positionSec)
        if (index <= 0 || index >= beats.size) return null
        val interval = beats[index] - beats[index - 1]
        if (interval <= 0.0 || !interval.isFinite()) return null
        return BeatPhase(positionSec - beats[index - 1], interval)
    }

    private fun beginEase(player: ExoPlayer, armed: HeldTrack) {
        val stretch = armed.incomingRate - 1.0
        if (abs(stretch) < EASE_DONE) {
            player.setPlaybackSpeed(userRate())
            return
        }
        val grid = armed.inGrid
        val beat = grid?.let { beatPhase(it, player.currentPosition / 1000.0)?.interval }
            ?: grid?.bpm?.takeIf { it > 0.0 }?.let { 60.0 / it }
            ?: EASE_FALLBACK_STEP_SECONDS
        easeStep = stretch / rampStepsFor(armed.incomingRate)
        easeRate = armed.incomingRate
        easeStepMs = (beat * 1000.0).roundToLong()
        easeItemIndex = player.currentMediaItemIndex
        easeNextAtMs = player.currentPosition + easeStepMs
        easing = player
        player.setPlaybackSpeed((userRate() * armed.incomingRate).toFloat())
    }

    private fun driveEase() {
        val player = easing ?: return
        val position = player.currentPosition
        if (player !== session() ||
            player.currentMediaItemIndex != easeItemIndex ||
            position < easeNextAtMs - 2 * easeStepMs
        ) {
            endEase()
            return
        }
        if (position < easeNextAtMs) return
        easeRate -= easeStep
        if (abs(easeRate - 1.0) < EASE_DONE) {
            endEase()
            return
        }
        player.setPlaybackSpeed((userRate() * easeRate).toFloat())
        easeNextAtMs += easeStepMs
    }

    private fun endEase() {
        val player = easing ?: return
        easing = null
        player.setPlaybackSpeed(userRate())
    }

    private fun rampStepsFor(rate: Double): Int {
        val stretch = abs(rate - 1.0)
        return if (stretch < EASE_DONE) 0 else ceil(stretch / TEMPO_STEP_PER_BEAT).toInt()
    }

    private fun bail() {
        if (phase == Phase.IDLE || phase == Phase.BAILING) return
        if (!handedOff && phase != Phase.FADING) {
            finish()
            return
        }
        val leaving = (if (handedOff) outgoing else incoming) ?: run {
            finish()
            return
        }
        bailFromVolume = leaving.volume
        bailStartedAt = SystemClock.elapsedRealtime()
        (if (handedOff) incoming else outgoing)?.let { it.volume = baseVolume(it) }
        phase = Phase.BAILING
    }

    private fun driveBail() {
        val leaving = if (handedOff) outgoing else incoming
        if (leaving == null) {
            finish()
            return
        }
        val factor = 1f - (SystemClock.elapsedRealtime() - bailStartedAt).toFloat() / BAIL_MS
        if (factor <= 0f) {
            finish()
            return
        }
        leaving.volume = bailFromVolume * factor
    }

    private fun finish() {
        val armed = armed
        if (handedOff) {
            incoming?.let { player ->
                player.volume = baseVolume(player)
                if (armed != null) beginEase(player, armed)
            }
            outgoing?.let(::retire)
        } else {
            outgoing?.let { it.volume = baseVolume(it) }
            incoming?.let(::retire)
        }
        outgoing = null
        incoming = null
        this.armed = null
        outgoingUri = ""
        handedOff = false
        nudgeUntil = 0L
        phase = Phase.IDLE
    }

    private fun reholdArmed() {
        armed?.let { held = it }
    }

    private fun retire(player: ExoPlayer) {
        player.stop()
        player.clearMediaItems()
        player.volume = 1f
        session()?.let { player.setPlaybackSpeed(it.playbackParameters.speed) }
    }

    private companion object {
        const val IDLE_STEP_MS = 250L

        const val ARM_STEP_MS = 50L

        const val FADE_STEP_MS = 40L

        const val BAIL_STEP_MS = 20L

        const val ARM_LEAD_MS = 4_000L

        const val ARM_TIMEOUT_MS = 12_000L

        const val BAIL_MS = 120L

        const val HANDOFF_AT = 0.5f

        const val HALF_PI = (Math.PI / 2).toFloat()

        const val LOCK_START_MS = 300L

        const val LOCK_SAMPLES = 5

        const val LOCK_DEADBAND_SECONDS = 0.008

        const val LOCK_SETTLE_MS = 400L

        const val LOCK_IN_PHASE_MS = 500L

        const val NUDGE = 0.03f

        const val MAX_NUDGE_MS = 1_500L

        const val LOCK_UNTIL = 0.9f

        const val LOCK_MAX_BEATS = 0.3

        const val TEMPO_STEP_PER_BEAT = 0.0075

        const val MAX_TOTAL_SPEED_UP = 1.12

        const val EASE_DONE = 1e-4

        const val EASE_FALLBACK_STEP_SECONDS = 0.5
    }
}
