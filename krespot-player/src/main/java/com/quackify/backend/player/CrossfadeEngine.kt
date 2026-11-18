/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * |    \| '__| _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_
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
import com.quackify.backend.core.log.Logs
import kotlin.math.cos
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
 */
internal class CrossfadeEngine(
    private val session: () -> ExoPlayer?,
    private val spare: () -> ExoPlayer,
    private val buildSource: (MediaItem) -> MediaSource,
    private val fadeMs: () -> Long,
    private val sessionDurationMs: () -> Long,
    private val baseVolume: (ExoPlayer) -> Float,
    private val onEvent: (PlayerEvent) -> Unit,
    private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer, uri: String, durationMs: Long) -> Unit,
) {

    private enum class Phase { IDLE, ARMING, FADING, BAILING }

    private class HeldTrack(val item: MediaItem, val durationMs: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var ticker: Job? = null

    @Volatile
    private var held: HeldTrack? = null

    private var phase = Phase.IDLE

    private var outgoing: ExoPlayer? = null

    private var incoming: ExoPlayer? = null

    private var handedOff = false

    private var armedUri = ""

    private var armedItem: MediaItem? = null

    private var outgoingUri = ""

    private var incomingDurationMs = 0L

    private var fadeSpanMs = 0L

    private var cueMs = 0L

    private var armDeadline = 0L

    private var bailStartedAt = 0L

    private var bailFromVolume = 0f

    fun hold(item: MediaItem, durationMs: Long) {
        scope.launch {
            held = HeldTrack(item, durationMs)
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
            val rehold = phase == Phase.FADING && !handedOff
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
            Phase.FADING -> applyFadeVolumes(base)
            Phase.BAILING -> session()?.let { it.volume = base(it) }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        session()?.let { it.volume = baseVolume(it) }
        val spareSide = if (handedOff) outgoing else incoming
        spareSide?.let {
            it.stop()
            it.clearMediaItems()
            it.volume = 1f
        }
        outgoing = null
        incoming = null
        held = null
        armedUri = ""
        armedItem = null
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
            while (isActive && (held != null || phase != Phase.IDLE)) {
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
        val configured = fadeMs()
        if (configured <= 0L) {
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
        armedUri = hold.item.mediaId
        armedItem = hold.item
        outgoingUri = out.currentMediaItem?.mediaId.orEmpty()
        incomingDurationMs = hold.durationMs
        fadeSpanMs = fade
        cueMs = 0L
        armDeadline = SystemClock.elapsedRealtime() + ARM_TIMEOUT_MS
        handedOff = false
        outgoing = out
        incoming = into
        into.setPlaybackSpeed(out.playbackParameters.speed)
        into.volume = 0f
        into.setMediaSource(buildSource(hold.item))
        into.prepare()
        into.seekTo(cueMs)
        into.playWhenReady = false
        phase = Phase.ARMING
        log.i(
            "crossfade armed '${armedUri.takeLast(12)}' over ${fade}ms " +
                "at ${out.currentPosition}ms of '${outgoingUri.takeLast(12)}'"
        )
    }

    private fun driveArming() {
        val out = outgoing ?: return finish()
        val into = incoming ?: return finish()
        if (!stillWorthFading()) return bail()
        if (!out.playWhenReady) {
            bail()
            reholdArmed()
            return
        }
        val expired = SystemClock.elapsedRealtime() > armDeadline
        val ready = into.playbackState == Player.STATE_READY
        if (expired && !ready) return bail()
        val atFadePoint = sessionDurationMs() - out.currentPosition <= fadeSpanMs
        if (atFadePoint && ready) startFade()
    }

    private fun startFade() {
        val into = incoming ?: return bail()
        into.volume = 0f
        into.playWhenReady = true
        phase = Phase.FADING
        log.i("crossfade blending into '${armedUri.takeLast(12)}' over ${fadeSpanMs}ms")
    }

    private fun driveFade() {
        val out = outgoing ?: return finish()
        val into = incoming ?: return finish()
        if (!stillWorthFading()) {
            handOff()
            finish()
            return
        }
        if (!handedOff && into.playbackState == Player.STATE_IDLE) return bail()
        val progress = fadeProgress(out, into)
        applyFadeVolumes(baseVolume)
        if (!handedOff && progress >= HANDOFF_AT) handOff()
        if (progress >= 1f) {
            handOff()
            finish()
        }
    }

    private fun fadeProgress(out: ExoPlayer, into: ExoPlayer): Float {
        val elapsed = (into.currentPosition - cueMs).coerceAtLeast(0L)
        val incomingProgress = (elapsed.toFloat() / fadeSpanMs).coerceIn(0f, 1f)
        val outRemaining = (sessionDurationMs() - out.currentPosition).coerceAtLeast(0L)
        val endDriven = ((fadeSpanMs - outRemaining).toFloat() / fadeSpanMs).coerceIn(0f, 1f)
        return maxOf(incomingProgress, endDriven)
    }

    private fun applyFadeVolumes(base: (ExoPlayer) -> Float) {
        val out = outgoing ?: return
        val into = incoming ?: return
        val progress = fadeProgress(out, into)
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

    private fun stillWorthFading(): Boolean {
        if (fadeMs() <= 0L) return false
        val player = session() ?: return false
        val expected = if (handedOff) armedUri else outgoingUri
        return player.currentMediaItem?.mediaId == expected
    }

    private fun handOff() {
        if (handedOff) return
        val out = outgoing ?: return
        val into = incoming ?: return
        val oldUri = outgoingUri
        handedOff = true
        onHandoff(out, into, armedUri, incomingDurationMs)
        onEvent(PlayerEvent.Ended(oldUri))
        onEvent(PlayerEvent.Started(armedUri))
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
        if (handedOff) {
            incoming?.let { it.volume = baseVolume(it) }
            outgoing?.let(::retire)
        } else {
            outgoing?.let { it.volume = baseVolume(it) }
            incoming?.let(::retire)
        }
        outgoing = null
        incoming = null
        armedUri = ""
        outgoingUri = ""
        handedOff = false
        phase = Phase.IDLE
    }

    private fun reholdArmed() {
        armedItem?.let { held = HeldTrack(it, incomingDurationMs) }
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
    }
}
