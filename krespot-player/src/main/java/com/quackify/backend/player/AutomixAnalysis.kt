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
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

private val log = Logs.of(Logs.Module.PLAYER)

/**
 * Builds beat grids on the device instead of asking a catalogue endpoint for
 * them: a hidden muted player decodes the head of the next track and the tail
 * of the playing one through the normal DRM pipeline, a pass-through audio
 * processor taps the decoded PCM, and [TrackAnalyzer] turns each window into a
 * grid. A window that cannot be decoded in the caller's time budget returns
 * null and the transition falls back to the plain crossfade. The same windows
 * also measure the leading silence of the incoming cut, so a blend can be
 * cued at the first audible frame instead of into a silent intro.
 */
internal class AutomixAnalysis(private val context: Context) {

    private val mutex = Mutex()

    private val generation = java.util.concurrent.atomic.AtomicLong(0L)

    private val main = Handler(Looper.getMainLooper())

    private var sourceFactory: DefaultMediaSourceFactory? = null

    private var player: ExoPlayer? = null

    private val tap = PcmTap()

    suspend fun tailGrid(
        cut: Cut,
        durationMs: Long,
        budgetMs: Long,
        stillWanted: () -> Boolean,
    ): TrackGrid? {
        val durationSec = durationMs / 1000.0
        if (durationSec < MIN_TRACK_SECONDS) {
            log.i("automix: '${cut.uri.takeLast(12)}' is too short to grid")
            return null
        }
        val head = GridCache.get(context, "${cut.cacheKey}#head")
        if (head != null && durationSec <= FULL_WINDOW_SECONDS) return head
        return windowGrid(cut, durationSec - FULL_WINDOW_SECONDS, "tail", budgetMs, stillWanted)
    }

    suspend fun headGrid(
        cut: Cut,
        durationMs: Long,
        budgetMs: Long,
        stillWanted: () -> Boolean,
    ): TrackGrid? {
        val durationSec = durationMs / 1000.0
        if (durationSec < MIN_TRACK_SECONDS) {
            log.i("automix: '${cut.uri.takeLast(12)}' is too short to grid")
            return null
        }
        val tail = GridCache.get(context, "${cut.cacheKey}#tail")
        if (tail != null && durationSec <= FULL_WINDOW_SECONDS) return tail
        return windowGrid(cut, 0.0, "head", budgetMs, stillWanted)
    }

    fun tailSilenceSec(cut: Cut): Double = GridCache.getSilence(context, cut.cacheKey)

    fun headSilenceCached(cut: Cut): Double? = GridCache.getHeadSilence(context, cut.cacheKey)

    /**
     * The incoming cut's leading silence, measured now when no decode ever
     * covered its head: a short muted window instead of the full grid pass,
     * inside the caller's budget, null when the time is not there.
     */
    suspend fun headSilence(
        cut: Cut,
        durationMs: Long,
        budgetMs: Long,
        stillWanted: () -> Boolean,
    ): Double? {
        headSilenceCached(cut)?.let { return it }
        val durationSec = durationMs / 1000.0
        if (durationSec < MIN_TRACK_SECONDS) return null
        if (budgetMs < MIN_DECODE_BUDGET_MS) return null
        if (!stillWanted()) return null
        val token = generation.get()
        val windowSec = min(HEAD_SILENCE_WINDOW_SECONDS, durationSec - 1.0)
        if (windowSec < MIN_HEAD_WINDOW_SECONDS) return null
        val pcm = mutex.withLock {
            decodeWindow(cut, 0.0, windowSec, budgetMs, token, stillWanted)
        } ?: return null
        return rememberHeadSilence(cut, pcm)
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
        cut: Cut,
        fromSec: Double,
        side: String,
        budgetMs: Long,
        stillWanted: () -> Boolean,
    ): TrackGrid? {
        val cacheKey = "${cut.cacheKey}#$side"
        GridCache.get(context, cacheKey)?.let { return it }
        if (budgetMs < MIN_DECODE_BUDGET_MS) {
            log.i("automix: only ${budgetMs}ms left to decode '${cut.uri.takeLast(12)}', skipped")
            return null
        }
        if (!stillWanted()) return null
        val token = generation.get()
        val startedAt = SystemClock.elapsedRealtime()
        val durationSec = cut.lengthMs / 1000.0
        val start = fromSec.coerceIn(0.0, (durationSec - 2.0).coerceAtLeast(0.0))
        val windowSec = if (start <= 0.0) {
            min(FULL_WINDOW_SECONDS, durationSec - 1.0)
        } else {
            min(FULL_WINDOW_SECONDS, durationSec - start - 0.5)
        }
        if (windowSec < MIN_WINDOW_SECONDS) return null
        val pcm = mutex.withLock {
            decodeWindow(cut, start, windowSec, budgetMs, token, stillWanted)
        } ?: return null
        rememberTailSilence(cut, pcm, start, durationSec)
        if (start <= 0.0) rememberHeadSilence(cut, pcm)
        val grid = withContext(Dispatchers.Default) {
            runCatching { TrackAnalyzer.analyze(pcm.samples, pcm.rate, start) }.getOrNull()
        }
        if (grid == null) {
            log.w(
                "no grid for '${cut.uri.takeLast(12)}' at ${"%.0f".format(start)}s " +
                    "after ${SystemClock.elapsedRealtime() - startedAt}ms"
            )
            return null
        }
        GridCache.put(context, cacheKey, grid)
        log.i(
            "grid '${cut.uri.takeLast(12)}' ${"%.1f".format(grid.bpm)}bpm conf=${"%.2f".format(grid.confidence)} " +
                "beats=${grid.beatStarts.size} bars=${grid.barStarts.size} " +
                "in ${SystemClock.elapsedRealtime() - startedAt}ms"
        )
        return grid
    }

    private fun rememberTailSilence(cut: Cut, pcm: Pcm, windowStartSec: Double, durationSec: Double) {
        if (pcm.rate <= 0) return
        val windowSec = pcm.samples.size.toDouble() / pcm.rate
        if (durationSec - (windowStartSec + windowSec) > END_COVER_TOLERANCE_SECONDS) return
        val silence = trailingSilenceSec(pcm.samples, pcm.rate, windowStartSec, durationSec)
        GridCache.putSilence(context, cut.cacheKey, silence)
        if (silence >= MIN_TAIL_SILENCE_SECONDS) {
            log.i(
                "automix: '${cut.uri.takeLast(12)}' runs silent for the last ${"%.1f".format(silence)}s, " +
                    "blends will stop at the audible end"
            )
        }
    }

    private fun rememberHeadSilence(cut: Cut, pcm: Pcm): Double {
        val silence = leadingSilenceSec(pcm.samples, pcm.rate)
        GridCache.putHeadSilence(context, cut.cacheKey, silence)
        if (silence >= MIN_HEAD_SILENCE_SECONDS) {
            log.i(
                "automix: '${cut.uri.takeLast(12)}' opens silent for the first " +
                    "${"%.1f".format(silence)}s, blends will cue past it"
            )
        }
        return silence
    }

    private fun trailingSilenceSec(samples: FloatArray, rate: Int, windowStartSec: Double, durationSec: Double): Double {
        val windowSec = samples.size.toDouble() / rate
        if (windowSec < MIN_MEASURED_TAIL_SECONDS) return 0.0
        val windowSamples = (MEASURE_WINDOW_SECONDS * rate).toInt()
        if (windowSamples <= 0) return 0.0
        val windows = minOf((windowSec / MEASURE_WINDOW_SECONDS).toInt(), samples.size / windowSamples)
        if (windows < 4) return 0.0
        val levels = DoubleArray(windows)
        for (index in 0 until windows) {
            val from = index * windowSamples
            var sum = 0.0
            for (cursor in from until from + windowSamples) {
                val sample = samples[cursor].toDouble()
                sum += sample * sample
            }
            levels[index] = sqrt(sum / windowSamples)
        }
        val noiseFloor = percentile(levels, 0.05)
        val reference = percentile(levels, 0.85)
        if (reference <= 0.0) return 0.0
        val activeThreshold = maxOf(0.0025, minOf(noiseFloor * 2.6, reference * 0.28), reference * 0.1)
        val silenceThreshold = maxOf(0.0015, minOf(activeThreshold * 0.25, reference * 0.04))
        var quietStart = windows
        while (quietStart > 0 && levels[quietStart - 1] < silenceThreshold) {
            quietStart--
        }
        if (quietStart == 0) return 0.0
        val silence = durationSec - (windowStartSec + quietStart * MEASURE_WINDOW_SECONDS)
        if (silence < MIN_TAIL_SILENCE_SECONDS) return 0.0
        return minOf(silence, MAX_TAIL_SILENCE_SECONDS)
    }

    private fun percentile(values: DoubleArray, fraction: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.copyOf().also { it.sort() }
        return sorted[((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)]
    }

    private fun leadingSilenceSec(samples: FloatArray, rate: Int): Double {
        if (rate <= 0) return 0.0
        val windowSamples = (MEASURE_WINDOW_SECONDS * rate).toInt()
        if (windowSamples <= 0) return 0.0
        val windows = minOf(samples.size / windowSamples, MAX_HEAD_SILENCE_WINDOWS)
        if (windows < 2) return 0.0
        val levels = DoubleArray(windows)
        for (index in 0 until windows) {
            val from = index * windowSamples
            var sum = 0.0
            for (cursor in from until from + windowSamples) {
                val sample = samples[cursor].toDouble()
                sum += sample * sample
            }
            levels[index] = sqrt(sum / windowSamples)
        }
        val noiseFloor = percentile(levels, 0.05)
        val reference = percentile(levels, 0.85)
        if (reference <= 0.0) return 0.0
        val activeThreshold = maxOf(0.0025, minOf(noiseFloor * 2.6, reference * 0.28), reference * 0.1)
        val silenceThreshold = maxOf(0.0015, minOf(activeThreshold * 0.25, reference * 0.04))
        var quiet = 0
        while (quiet < windows && levels[quiet] < silenceThreshold) quiet++
        val silence = quiet * MEASURE_WINDOW_SECONDS
        if (silence < MIN_HEAD_SILENCE_SECONDS) return 0.0
        return minOf(silence, MAX_HEAD_SILENCE_SECONDS)
    }

    private class Pcm(val samples: FloatArray, val rate: Int)

    private suspend fun decodeWindow(
        cut: Cut,
        fromSec: Double,
        windowSec: Double,
        budgetMs: Long,
        token: Long,
        stillWanted: () -> Boolean,
    ): Pcm? = withContext(Dispatchers.Main) {
        if (token != generation.get() || !stillWanted()) return@withContext null
        val p = ensurePlayer()
        val item = when (cut) {
            is Cut.Catalogue -> {
                val keySetId = runCatching { offlineKeySetFor(cut.source) }.getOrNull()
                itemFor(cut.uri, cut.source, keySetId)
            }
            is Cut.Alternate -> {
                // The table holds the stream the queue committed to; the
                // routed factory reads it back for the muted decode window.
                val stream = YtStreamTable.of(cut.cacheKey)
                if (stream == null) {
                    log.w("analysis: the alternate for '${cut.uri.takeLast(12)}' left the table, no window")
                    return@withContext null
                }
                MediaItem.Builder()
                    .setMediaId(cut.uri)
                    .setUri(Uri.parse(stream.url))
                    .setCustomCacheKey(cut.cacheKey)
                    .build()
            }
        }
        tap.start((windowSec * MAX_TAP_RATE).toInt() + 64)
        try {
            p.setMediaSource(ensureFactory().createMediaSource(item))
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
                        log.w("analysis decode failed for '${cut.uri.takeLast(12)}': ${error.errorCodeName}")
                        return@withContext null
                    }
                    collected >= needed -> return@withContext Pcm(tap.snapshot(), rate)
                    p.playbackState == Player.STATE_ENDED -> {
                        if (rate > 0 && collected >= MIN_COLLECTED_SECONDS * rate) {
                            return@withContext Pcm(tap.snapshot(), rate)
                        }
                        log.w("analysis decode ended early for '${cut.uri.takeLast(12)}' with $collected samples")
                        return@withContext null
                    }
                    p.playbackState == Player.STATE_IDLE && collected == 0 && ++idlePolls > 10 -> {
                        log.w("analysis decode never started for '${cut.uri.takeLast(12)}'")
                        return@withContext null
                    }
                }
                delay(POLL_MS.milliseconds)
            }
            val rate = tap.sessionRate
            val collected = tap.collected()
            if (rate > 0 && collected >= MIN_COLLECTED_SECONDS * rate) {
                Pcm(tap.snapshot(), rate)
            } else {
                log.w("analysis decode timed out for '${cut.uri.takeLast(12)}' with $collected samples")
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
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
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
        // The same routed stack the engines play through: a catalogue window
        // decodes out of the song cache, an alternate window out of the
        // stream buffer, and the licence manager only ever engages for an
        // item that carries a drm configuration.
        val factory = DefaultMediaSourceFactory(RoutedSources(context))
        val manager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, DrmLevel.provider())
            .setMultiSession(true)
            .setSessionKeepaliveMs(600_000)
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

        const val MEASURE_WINDOW_SECONDS = 0.25

        const val MIN_MEASURED_TAIL_SECONDS = 2.0

        const val MIN_TAIL_SILENCE_SECONDS = 0.5

        const val MAX_TAIL_SILENCE_SECONDS = 12.0

        const val END_COVER_TOLERANCE_SECONDS = 2.0

        const val HEAD_SILENCE_WINDOW_SECONDS = 12.0

        const val MIN_HEAD_WINDOW_SECONDS = 2.0

        const val MIN_HEAD_SILENCE_SECONDS = 0.3

        const val MAX_HEAD_SILENCE_SECONDS = 10.0

        const val MAX_HEAD_SILENCE_WINDOWS = 48
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
        if (inputBuffer.hasRemaining()) {
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
        }
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

    private const val SILENCE_SUFFIX = "#silence"

    private const val HEAD_SILENCE_SUFFIX = "#headsilence"

    private const val LIMIT = 48

    private val lock = Any()

    private val memory = LinkedHashMap<String, TrackGrid>()

    private val memorySilence = LinkedHashMap<String, Double>()

    private val memoryHeadSilence = LinkedHashMap<String, Double>()

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
            touchIndex(prefs, editor, key)
            editor.apply()
        }
    }

    fun getSilence(context: Context, uri: String): Double {
        synchronized(lock) { memorySilence[uri]?.let { return it } }
        val raw = runCatching {
            AppPrefs.prefs(context, STORE).getString(uri + SILENCE_SUFFIX, null)
        }.getOrNull() ?: return 0.0
        val value = raw.toDoubleOrNull() ?: return 0.0
        if (!value.isFinite() || value < 0.0) return 0.0
        synchronized(lock) { memorySilence[uri] = value }
        return value
    }

    fun putSilence(context: Context, uri: String, seconds: Double) {
        synchronized(lock) { memorySilence[uri] = seconds }
        runCatching {
            val key = uri + SILENCE_SUFFIX
            val prefs = AppPrefs.prefs(context, STORE)
            val editor = prefs.edit().putString(key, seconds.toString())
            touchIndex(prefs, editor, key)
            editor.apply()
        }
    }

    fun getHeadSilence(context: Context, key: String): Double? {
        synchronized(lock) { memoryHeadSilence[key]?.let { return it } }
        val raw = runCatching {
            AppPrefs.prefs(context, STORE).getString(key + HEAD_SILENCE_SUFFIX, null)
        }.getOrNull() ?: return null
        val value = raw.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0.0) return null
        synchronized(lock) { memoryHeadSilence[key] = value }
        return value
    }

    fun putHeadSilence(context: Context, key: String, seconds: Double) {
        synchronized(lock) { memoryHeadSilence[key] = seconds }
        runCatching {
            val storeKey = key + HEAD_SILENCE_SUFFIX
            val prefs = AppPrefs.prefs(context, STORE)
            val editor = prefs.edit().putString(storeKey, seconds.toString())
            touchIndex(prefs, editor, storeKey)
            editor.apply()
        }
    }

    private fun touchIndex(prefs: AppPrefs.Prefs, editor: AppPrefs.Editor, key: String) {
        val keys = prefs.getString(KEY_INDEX, "").orEmpty()
            .split(',').filter { it.isNotBlank() }.toMutableList()
        keys.remove(key)
        keys.add(key)
        while (keys.size > LIMIT) {
            editor.remove(keys.removeAt(0))
        }
        editor.putString(KEY_INDEX, keys.joinToString(","))
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
