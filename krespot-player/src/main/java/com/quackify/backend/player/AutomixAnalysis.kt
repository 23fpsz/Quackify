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

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.store.AppPrefs
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val log = Logs.of(Logs.Module.PLAYER)

/**
 * Builds beat grids on the device instead of asking a catalogue endpoint for
 * them: a hidden muted player decodes the head of the next track and the tail
 * of the playing one through the normal DRM pipeline, a pass-through audio
 * processor taps the decoded PCM, and [TrackAnalyzer] turns each window into a
 * grid. A window that cannot be decoded in the caller's time budget returns
 * null and the transition falls back to the plain crossfade.
 */
internal class AutomixAnalysis(private val context: Context) {

    private val mutex = Mutex()

    private val generation = java.util.concurrent.atomic.AtomicLong(0L)

    private val main = Handler(Looper.getMainLooper())

    private var sourceFactory: DefaultMediaSourceFactory? = null

    private var player: ExoPlayer? = null

    private val tap = PcmTap()

    suspend fun tailGrid(
        source: AudioSource,
        uri: String,
        durationMs: Long,
        budgetMs: Long,
        stillWanted: () -> Boolean,
    ): TrackGrid? {
        val durationSec = durationMs / 1000.0
        if (durationSec < MIN_TRACK_SECONDS) return null
        val head = GridCache.get(context, "$uri#head")
        if (head != null && durationSec <= FULL_WINDOW_SECONDS) return head
        return windowGrid(source, uri, durationSec - FULL_WINDOW_SECONDS, "$uri#tail", budgetMs, stillWanted)
    }

    suspend fun headGrid(
        source: AudioSource,
        uri: String,
        durationMs: Long,
        budgetMs: Long,
        stillWanted: () -> Boolean,
    ): TrackGrid? {
        val durationSec = durationMs / 1000.0
        if (durationSec < MIN_TRACK_SECONDS) return null
        val tail = GridCache.get(context, "$uri#tail")
        if (tail != null && durationSec <= FULL_WINDOW_SECONDS) return tail
        return windowGrid(source, uri, 0.0, "$uri#head", budgetMs, stillWanted)
    }

    fun interrupt() {
        generation.incrementAndGet()
        main.post { runCatching { player?.stop() } }
    }

    fun release() {
        generation.incrementAndGet()
        main.post {
            runCatching { player?.release() }
            player = null
            sourceFactory = null
        }
    }

    private suspend fun windowGrid(
        source: AudioSource,
        uri: String,
        fromSec: Double,
        cacheKey: String,
        budgetMs: Long,
        stillWanted: () -> Boolean,
    ): TrackGrid? {
        GridCache.get(context, cacheKey)?.let { return it }
        if (budgetMs < MIN_DECODE_BUDGET_MS) return null
        if (!stillWanted()) return null
        val token = generation.get()
        val startedAt = SystemClock.elapsedRealtime()
        val durationSec = source.seektable.totalDuration.toDouble() / source.seektable.timescale
        val start = fromSec.coerceIn(0.0, (durationSec - 2.0).coerceAtLeast(0.0))
        val windowSec = if (start <= 0.0) {
            min(FULL_WINDOW_SECONDS, durationSec - 1.0)
        } else {
            min(FULL_WINDOW_SECONDS, durationSec - start - 0.5)
        }
        if (windowSec < MIN_WINDOW_SECONDS) return null
        val pcm = mutex.withLock {
            decodeWindow(source, uri, start, windowSec, budgetMs, token, stillWanted)
        } ?: return null
        val grid = withContext(Dispatchers.Default) {
            runCatching { TrackAnalyzer.analyze(pcm.samples, pcm.rate, start) }.getOrNull()
        }
        if (grid == null) {
            log.w(
                "no grid for '${uri.takeLast(12)}' at ${"%.0f".format(start)}s " +
                    "after ${SystemClock.elapsedRealtime() - startedAt}ms"
            )
            return null
        }
        GridCache.put(context, cacheKey, grid)
        log.i(
            "grid '${uri.takeLast(12)}' ${"%.1f".format(grid.bpm)}bpm conf=${"%.2f".format(grid.confidence)} " +
                "beats=${grid.beatStarts.size} bars=${grid.barStarts.size} " +
                "in ${SystemClock.elapsedRealtime() - startedAt}ms"
        )
        return grid
    }

    private class Pcm(val samples: FloatArray, val rate: Int)

    private suspend fun decodeWindow(
        source: AudioSource,
        uri: String,
        fromSec: Double,
        windowSec: Double,
        budgetMs: Long,
        token: Long,
        stillWanted: () -> Boolean,
    ): Pcm? = withContext(Dispatchers.Main) {
        if (token != generation.get() || !stillWanted()) return@withContext null
        val p = ensurePlayer()
        val keySetId = runCatching { offlineKeySetFor(source) }.getOrNull()
        tap.start((windowSec * MAX_TAP_RATE).toInt() + 64)
        try {
            p.setMediaSource(ensureFactory().createMediaSource(itemFor(uri, source, keySetId)))
            p.prepare()
            p.seekTo((fromSec * 1000.0).toLong())
            p.volume = 0f
            p.playbackParameters = PlaybackParameters(ANALYSIS_SPEED)
            p.playWhenReady = true
            val deadline = SystemClock.elapsedRealtime() + budgetMs
            var idlePolls = 0
            while (SystemClock.elapsedRealtime() < deadline) {
                if (token != generation.get() || !stillWanted()) return@withContext null
                val rate = tap.sessionRate
                val collected = tap.collected()
                val needed = if (rate > 0) (windowSec * rate).toInt() + 1 else Int.MAX_VALUE
                val error = p.playerError
                when {
                    error != null -> {
                        log.w("analysis decode failed for '${uri.takeLast(12)}': ${error.errorCodeName}")
                        return@withContext null
                    }
                    collected >= needed -> return@withContext Pcm(tap.snapshot(), rate)
                    p.playbackState == Player.STATE_ENDED -> {
                        if (rate > 0 && collected >= MIN_COLLECTED_SECONDS * rate) {
                            return@withContext Pcm(tap.snapshot(), rate)
                        }
                        log.w("analysis decode ended early for '${uri.takeLast(12)}' with $collected samples")
                        return@withContext null
                    }
                    p.playbackState == Player.STATE_IDLE && collected == 0 && ++idlePolls > 10 -> {
                        log.w("analysis decode never started for '${uri.takeLast(12)}'")
                        return@withContext null
                    }
                }
                delay(POLL_MS)
            }
            val rate = tap.sessionRate
            val collected = tap.collected()
            if (rate > 0 && collected >= MIN_COLLECTED_SECONDS * rate) {
                Pcm(tap.snapshot(), rate)
            } else {
                log.w("analysis decode timed out for '${uri.takeLast(12)}' with $collected samples")
                null
            }
        } finally {
            runCatching {
                p.playWhenReady = false
                p.stop()
                p.clearMediaItems()
            }
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val factory = ensureFactory()
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(tap))
                .build()
        }
        val built = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(factory)
            .build()
        player = built
        log.i("analysis player built")
        return built
    }

    private fun ensureFactory(): DefaultMediaSourceFactory {
        sourceFactory?.let { return it }
        val factory = DefaultMediaSourceFactory(
            SongCache.factory(context).setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
        )
        val manager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, DrmLevel.provider())
            .setMultiSession(true)
            .build(AudioDrmCallback(context) { LicenseEndpoint.of(context) })
        factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { manager })
        sourceFactory = factory
        return factory
    }

    private fun itemFor(uri: String, source: AudioSource, keySetId: ByteArray?): MediaItem {
        val encrypted = source.seektable.pssh.isNotBlank()
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(Uri.parse(source.url))
            .setCustomCacheKey(uri)
            .apply {
                if (encrypted) {
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setForceSessionsForAudioAndVideoTracks(true)
                            .apply { if (keySetId != null) setKeySetId(keySetId) }
                            .build(),
                    )
                }
            }
            .build()
    }

    private suspend fun offlineKeySetFor(source: AudioSource): ByteArray? {
        if (source.seektable.pssh.isBlank()) return null
        LicenseStore.keySetIdFor(context, source.fileId)?.let { return it }
        return OfflineLicence.acquire(context, source)
    }

    private companion object {
        const val FULL_WINDOW_SECONDS = 96.0

        const val MIN_TRACK_SECONDS = 25.0

        const val MIN_WINDOW_SECONDS = 20.0

        const val MIN_COLLECTED_SECONDS = 25.0

        const val MIN_DECODE_BUDGET_MS = 12_000L

        const val POLL_MS = 200L

        const val ANALYSIS_SPEED = 2f

        const val MAX_TAP_RATE = 12_500
    }
}

/**
 * Pass-through sink processor that keeps a mono, quarter-rate copy of every
 * decoded frame. The user processors run ahead of the silence skipper and the
 * speed stretcher in the sink's chain, so the copy is the pristine decoder
 * output on the content's own timeline.
 */
private class PcmTap : BaseAudioProcessor() {

    private val lock = Any()

    private var buffer = FloatArray(0)

    private var count = 0

    private var channels = 0

    private var encoding = 0

    private var decimSum = 0f

    private var decimStep = 0

    @Volatile
    var sessionRate = 0
        private set

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        sessionRate = if (inputAudioFormat.sampleRate >= 4) inputAudioFormat.sampleRate / 4 else 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (channels > 0 && sessionRate > 0) {
            tap(inputBuffer)
        }
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    private fun tap(input: ByteBuffer) {
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * channels
        val frames = input.remaining() / frameBytes
        if (frames <= 0) return
        val base = input.position()
        synchronized(lock) {
            for (frame in 0 until frames) {
                var mono = 0f
                val offset = base + frame * frameBytes
                for (channel in 0 until channels) {
                    mono += if (encoding == C.ENCODING_PCM_FLOAT) {
                        input.getFloat(offset + channel * 4)
                    } else {
                        input.getShort(offset + channel * 2) / 32768f
                    }
                }
                mono /= channels
                decimSum += mono
                decimStep++
                if (decimStep == DECIMATION) {
                    if (count >= buffer.size) {
                        buffer = buffer.copyOf(maxOf(buffer.size * 2, count + 8192))
                    }
                    buffer[count++] = decimSum / DECIMATION
                    decimSum = 0f
                    decimStep = 0
                }
            }
        }
    }

    fun start(expected: Int) {
        synchronized(lock) {
            buffer = FloatArray(expected.coerceAtLeast(1024))
            count = 0
            decimSum = 0f
            decimStep = 0
        }
    }

    fun collected(): Int {
        synchronized(lock) { return count }
    }

    fun snapshot(): FloatArray = synchronized(lock) { buffer.copyOf(count) }

    private companion object {
        const val DECIMATION = 4
    }
}

private object GridCache {

    private const val STORE = "quackify.automix"

    private const val KEY_INDEX = "grid.keys"

    private const val LIMIT = 48

    private val lock = Any()

    private val memory = LinkedHashMap<String, TrackGrid>()

    fun get(context: Context, key: String): TrackGrid? {
        synchronized(lock) { memory[key]?.let { return it } }
        val raw = runCatching {
            AppPrefs.prefs(context, STORE).getString(key, null)
        }.getOrNull() ?: return null
        val grid = runCatching {
            val json = JSONObject(raw)
            TrackGrid(
                json.optDouble("bpm", 0.0),
                csvToDoubles(json.optString("beats")),
                csvToDoubles(json.optString("bars")),
                json.optDouble("conf", 0.0),
            )
        }.getOrNull() ?: return null
        if (!grid.bpm.isFinite() || grid.bpm <= 0.0 || grid.beatStarts.size < 2) return null
        synchronized(lock) {
            memory.remove(key)
            memory[key] = grid
        }
        return grid
    }

    fun put(context: Context, key: String, grid: TrackGrid) {
        synchronized(lock) {
            memory.remove(key)
            memory[key] = grid
        }
        runCatching {
            val prefs = AppPrefs.prefs(context, STORE)
            val json = JSONObject()
                .put("bpm", grid.bpm)
                .put("conf", grid.confidence)
                .put("beats", doublesToCsv(grid.beatStarts))
                .put("bars", doublesToCsv(grid.barStarts))
            val editor = prefs.edit().putString(key, json.toString())
            val keys = prefs.getString(KEY_INDEX, "").orEmpty()
                .split(',').filter { it.isNotBlank() }.toMutableList()
            keys.remove(key)
            keys.add(key)
            while (keys.size > LIMIT) {
                editor.remove(keys.removeAt(0))
            }
            editor.putString(KEY_INDEX, keys.joinToString(",")).apply()
        }
    }

    private fun doublesToCsv(values: DoubleArray): String = buildString {
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append(String.format(Locale.US, "%.3f", value))
        }
    }

    private fun csvToDoubles(csv: String): DoubleArray =
        csv.split(',').mapNotNull { it.toDoubleOrNull() }.toDoubleArray()
}
