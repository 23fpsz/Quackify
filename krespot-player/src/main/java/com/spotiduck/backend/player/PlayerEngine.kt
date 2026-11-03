package com.spotiduck.backend.player

import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Base64
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.core.net.Net

private val log = Logs.of(Logs.Module.ENGINE)

private const val LOOKAHEAD = 3
private const val LICENSE_REFRESH_SECONDS = 300L
private const val PROGRESS_STEP_MS = 100L
private const val CACHE_SEGMENTS = 32
private const val SEEK_TAIL_MS = 1000L
private const val BEAT_MS = 15000L

internal class PlayerEngine(
    private val source: AudioSource,
    private val init: Init,
    private val widevine: Widevine,
    private val gainDb: Double,
    private val openedCrypto: Deferred<MediaCrypto?>,
    private val scope: CoroutineScope,
    private val emit: (PlayerEvent) -> Unit,
) {

    @Volatile
    private var stopped = false

    @Volatile
    private var paused = false

    @Volatile
    private var duckLevel = 1f

    @Volatile
    private var pendingSeekSeconds: Double? = null

    @Volatile
    private var reachedEnd = false

    @Volatile
    private var positionMs = 0L

    private var sink: AudioSink? = null
    private var codec: MediaCodec? = null
    private var format: MediaFormat? = null

    private var mediaCursor = 0L
    private var windowStart = 0L
    private var windowEnd = 0L
    private var gain = 1f

    @Volatile
    private var reportedMs = 0L

    private var beatAt = 0L
    private var keyId: ByteArray? = null
    private var peak = 0
    private var samples = 0
    private var underrunsAt = -1
    private var cryptoLogged = false

    private val cache = ConcurrentHashMap<Int, ByteArray>()
    private val bufferInfo = MediaCodec.BufferInfo()

    val durationMs: Long get() = init.playableMs

    val currentPositionMs: Long get() = positionMs

    fun stop() {
        stopped = true
        paused = false
    }

    fun pause() {
        paused = true
        sink?.pause()
        log.i("engine paused at ${positionMs}ms")
        report()
    }

    fun duck(level: Float) {
        duckLevel = level.coerceIn(0f, 1f)
        sink?.volume(duckLevel)
    }

    fun resume() {
        paused = false
        sink?.resume()
        log.i("engine resumed at ${positionMs}ms")
        report()
    }

    fun seekTo(ms: Long) {
        val limit = (durationMs - SEEK_TAIL_MS).coerceAtLeast(0L)
        pendingSeekSeconds = ms.coerceIn(0L, limit) / 1000.0
    }

    suspend fun run(fromMs: Long) {
        stopped = false
        reachedEnd = false
        gain = 10.0.pow(gainDb / 20.0).toFloat()
        log.i("run ${init.sampleRate}Hz ${init.channels}ch mime=${init.mime} asc=${init.asc.size}B " +
                "kid=${init.kid.toHex()} psshKid=${psshKeyId(source.seektable.pssh)?.toHex()} " +
                "iv=${init.ivSize} priming=${init.priming} " +
                "playable=${init.playableMs}ms gain=${gainDb}dB"
        )
        val opening = scope.async(Dispatchers.IO) {
            val built = AudioSink(init.sampleRate, init.channels)
            if (!currentCoroutineContext().isActive) {
                built.release()
                throw CancellationException("the sink was not needed")
            }
            built
        }
        val crypto = try {
            openedCrypto.await()
        } catch (cancellation: CancellationException) {
            opening.cancel()
            throw cancellation
        }
        if (crypto == null) {
            opening.cancel()
            emit(PlayerEvent.Failed(widevine.reason.ifBlank { "no drm session" }))
            return
        }
        val decoderFormat = MediaFormat.createAudioFormat(init.mime, init.sampleRate, init.channels).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(init.asc))
        }
        format = decoderFormat
        val decoder = runCatching {
            MediaCodec.createDecoderByType(init.mime).also {
                it.configure(decoderFormat, null, crypto, 0)
                it.start()
            }
        }.getOrElse {
            log.e("decoder ${it.javaClass.simpleName}: ${it.message}")
            opening.cancel()
            emit(PlayerEvent.Failed("decoder unavailable"))
            return
        }
        codec = decoder
        val audio = try {
            opening.await()
        } catch (cancellation: CancellationException) {
            opening.cancel()
            throw cancellation
        }
        sink = audio
        audio.volume(duckLevel)
        audio.start()
        emit(PlayerEvent.Started)

        var from = fromMs / 1000.0
        try {
            while (!stopped) {
                val next = decodeFrom(from)
                if (next == null) break
                log.i("seek -> ${"%.3f".format(next)}s")
                emit(PlayerEvent.Seeked((next * 1000).roundToLong()))
                resetCodec()
                audio.flush()
                from = next
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            log.e("run failed ${failure.javaClass.simpleName}: ${failure.message}")
            emit(PlayerEvent.Failed(failure.message.orEmpty()))
        } finally {
            audio.release()
            sink = null
            runCatching {
                decoder.stop()
                decoder.release()
            }
            codec = null
        }

        if (reachedEnd) {
            log.i("ended at ${"%.2f".format(positionMs / 1000.0)}s")
            emit(PlayerEvent.Ended)
        } else {
            log.i("stopped at ${"%.2f".format(positionMs / 1000.0)}s")
        }
    }

    private fun resetCodec() {
        val decoder = codec ?: return
        val decoderFormat = format ?: return
        runCatching {
            decoder.stop()
            decoder.configure(decoderFormat, null, widevine.mediaCrypto, 0)
            decoder.start()
        }
    }

    private suspend fun decodeFrom(fromSeconds: Double): Double? = coroutineScope {
        val decoder = codec ?: return@coroutineScope null
        val audio = sink ?: return@coroutineScope null
        val table = source.seektable
        val rate = init.sampleRate
        val cryptoInfo = MediaCodec.CryptoInfo()
        val clear = intArrayOf(0)
        val encrypted = IntArray(1)

        val frameFrames = init.defaultDuration.toLong() * rate / init.timescale
        val preRoll = 3L * frameFrames
        val preRollSeconds = 4.0 * init.defaultDuration / init.timescale
        var startSegment = 0
        for (i in 0 until table.count) {
            if (table.startSeconds(i) <= fromSeconds) startSegment = i else break
        }
        if (startSegment > 0 && fromSeconds - table.startSeconds(startSegment) < preRollSeconds) {
            startSegment -= 1
        }

        mediaCursor = -1
        windowStart = init.primingFrames + (fromSeconds * rate).roundToLong()
        windowEnd = init.primingFrames + init.playableFrames
        positionMs = (fromSeconds * 1000).roundToLong()
        reportedMs = positionMs
        keyId = init.kid
        beatAt = 0L
        peak = 0
        cryptoLogged = false
        val decodeFromFrame = (windowStart - preRoll).coerceAtLeast(0L)

        if (windowStart >= windowEnd) {
            log.i("seek ${"%.3f".format(fromSeconds)}s is past the end of ${durationMs}ms")
            reachedEnd = true
            emit(PlayerEvent.Seeked(durationMs))
            return@coroutineScope null
        }
        if (fromSeconds > 0.0) {
            log.i("seek ${"%.3f".format(fromSeconds)}s seg=$startSegment/${table.count} " +
                    "segStart=${"%.3f".format(table.startSeconds(startSegment))}s " +
                    "dur=${durationMs}ms cached=${cache.containsKey(startSegment)}"
            )
        }

        val queue = Channel<ByteArray>(LOOKAHEAD)
        val loader = launch(Dispatchers.IO) {
            var index = startSegment
            var offset = table.offsetOf(startSegment)
            try {
                while (isActive && index < table.count && !stopped) {
                    val size = table.sizes[index]
                    val bytes = cache[index] ?: runCatching {
                        Net.fetch(
                            source.url,
                            offset,
                            offset + size - 1,
                            mapOf("referer" to "https://open.spotify.com/"),
                        )
                    }.getOrElse {
                        log.w("segment $index failed ${it.javaClass.simpleName}: ${it.message}")
                        null
                    }
                    if (bytes == null) break
                    remember(index, bytes)
                    offset += size
                    index++
                    queue.send(bytes)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } finally {
                queue.close()
            }
        }

        try {
            var index = startSegment
            while (index < table.count) {
                if (stopped || reachedEnd) break
                val seek = pendingSeekSeconds
                if (seek != null) {
                    pendingSeekSeconds = null
                    return@coroutineScope seek
                }
                while (paused && !stopped) {
                    val pending = pendingSeekSeconds
                    if (pending != null) {
                        pendingSeekSeconds = null
                        return@coroutineScope pending
                    }
                    delay(50)
                }

                val bytes = queue.receiveCatching().getOrNull() ?: break
                index++
                val fragment = Mp4.parseFragment(bytes, init) ?: continue
                val secure = fragment.entry == init.encryptedEntry
                if (!cryptoLogged && secure && fragment.samples.isNotEmpty()) {
                    cryptoLogged = true
                    val first = fragment.samples.first()
                    log.d("crypto seg=${index - 1} entry=${fragment.entry} enc=${init.encryptedEntry} " +
                            "kid=${init.kid.toHex()} iv=${first.iv?.toHex() ?: "none"} " +
                            "size=${first.size} off=${first.offset} " +
                            "sub=${first.subsamples?.size?.div(2) ?: 0} " +
                            "head=${bytes.copyOfRange(first.offset, minOf(first.offset + 8, bytes.size)).toHex()}"
                    )
                }
                var time = fragment.baseTime
                var frame = fragment.baseTime * rate / init.timescale
                for (sample in fragment.samples) {
                    if (stopped || reachedEnd) break
                    while (paused && !stopped) {
                        val parked = pendingSeekSeconds
                        if (parked != null) {
                            pendingSeekSeconds = null
                            return@coroutineScope parked
                        }
                        delay(50)
                    }
                    val pending = pendingSeekSeconds
                    if (pending != null) {
                        pendingSeekSeconds = null
                        return@coroutineScope pending
                    }
                    val frames = init.frames(sample.duration)
                    val pts = time * 1_000_000L / init.timescale
                    time += sample.duration
                    if (frame + frames <= decodeFromFrame) {
                        frame += frames
                        continue
                    }
                    if (mediaCursor < 0) {
                        mediaCursor = frame
                        log.d("decode seg ${index - 1} frame=$frame " +
                                "discard=${"%.3f".format((windowStart - frame).toDouble() / rate)}s " +
                                "sub=${sample.subsamples?.size?.div(2) ?: 0}"
                        )
                    }
                    val iv = sample.iv
                    queueSample(decoder, audio, bytes, sample, pts, if (secure) iv else null, cryptoInfo, clear, encrypted)
                    drain(decoder, audio, false)
                    beat(audio, index, table.count)
                    frame += frames
                }

                drain(decoder, audio, false)
                refreshIfNeeded()
                beat(audio, index, table.count)
            }
        } finally {
            loader.cancel()
            queue.close()
        }

        if (!stopped && !reachedEnd) {
            val index = decoder.dequeueInputBuffer(100_000)
            if (index >= 0) decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            while (!drain(decoder, audio, true)) {
                if (stopped) break
            }
        }

        val seek = pendingSeekSeconds
        if (seek != null) {
            pendingSeekSeconds = null
            return@coroutineScope seek
        }
        null
    }

    private suspend fun refreshIfNeeded() {
        val remaining = widevine.remainingSeconds
        if (remaining <= 0L || remaining >= LICENSE_REFRESH_SECONDS) return
        val decoder = codec ?: return
        val audio = sink ?: return
        val decoderFormat = format ?: return
        log.i("license ${remaining}s left, refreshing")
        repeat(3) { drain(decoder, audio, true) }
        decoder.stop()
        val crypto = widevine.refresh() ?: widevine.mediaCrypto
        if (crypto == null) {
            log.w("refresh failed and the old session is gone")
            return
        }
        decoder.configure(decoderFormat, null, crypto, 0)
        decoder.start()
    }

    private fun beat(audio: AudioSink, index: Int, total: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - beatAt < BEAT_MS) return
        if (paused || stopped) return
        beatAt = now
        val playing = audio.alive()
        val loudest = peak
        val measured = samples > 0
        val total_underruns = audio.underruns()
        val new_underruns = if (underrunsAt < 0 || total_underruns < 0) {
            0
        } else {
            (total_underruns - underrunsAt).coerceAtLeast(0)
        }
        underrunsAt = total_underruns
        peak = 0
        samples = 0
        val line = "beat pos=${positionMs}ms seg=$index/$total playing=$playing " +
                "underruns=+${new_underruns} of ${total_underruns} " +
                "peak=${if (measured) loudest else "none"}"
        if (!playing || (measured && loudest == 0) || new_underruns > 0) log.w(line) else log.v(line)
        if (!playing) {
            log.w("sink stopped consuming at ${positionMs}ms, rebuilding")
            audio.rebuild()
        }
    }

    private fun remember(index: Int, bytes: ByteArray) {
        cache[index] = bytes
        while (cache.size > CACHE_SEGMENTS) {
            val far = cache.keys.maxByOrNull { kotlin.math.abs(it - index) } ?: break
            cache.remove(far)
        }
    }

    private fun queueSample(
        decoder: MediaCodec,
        audio: AudioSink,
        source: ByteArray,
        sample: Sample,
        pts: Long,
        iv: ByteArray?,
        cryptoInfo: MediaCodec.CryptoInfo,
        clear: IntArray,
        encrypted: IntArray,
    ) {
        while (true) {
            if (stopped || reachedEnd) return
            val index = decoder.dequeueInputBuffer(10_000)
            if (index >= 0) {
                val buffer = decoder.getInputBuffer(index) ?: error("no input buffer")
                buffer.clear()
                buffer.put(source, sample.offset, sample.size)
                if (iv != null) {
                    val subsamples = sample.subsamples
                    if (subsamples == null) {
                        encrypted[0] = sample.size
                        cryptoInfo.set(1, clear, encrypted, keyId ?: init.kid, iv, MediaCodec.CRYPTO_MODE_AES_CTR)
                    } else {
                        val pairs = subsamples.size / 2
                        val clearParts = IntArray(pairs)
                        val encryptedParts = IntArray(pairs)
                        for (pair in 0 until pairs) {
                            clearParts[pair] = subsamples[pair * 2]
                            encryptedParts[pair] = subsamples[pair * 2 + 1]
                        }
                        cryptoInfo.set(pairs, clearParts, encryptedParts, keyId ?: init.kid, iv, MediaCodec.CRYPTO_MODE_AES_CTR)
                    }
                    decoder.queueSecureInputBuffer(index, 0, cryptoInfo, pts, 0)
                } else {
                    decoder.queueInputBuffer(index, 0, sample.size, pts, 0)
                }
                return
            }
            drain(decoder, audio, false)
        }
    }

    private fun drain(decoder: MediaCodec, audio: AudioSink, blocking: Boolean): Boolean {
        while (true) {
            val index = decoder.dequeueOutputBuffer(bufferInfo, if (blocking) 20_000 else 0)
            when {
                index >= 0 -> {
                    if (bufferInfo.size > 0) writePcm(decoder, index, audio)
                    decoder.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> return false
            }
        }
    }

    private fun writePcm(decoder: MediaCodec, index: Int, audio: AudioSink) {
        val out = decoder.getOutputBuffer(index) ?: return
        out.position(bufferInfo.offset)
        out.limit(bufferInfo.offset + bufferInfo.size)
        val pcm = ShortArray(bufferInfo.size / 2)
        out.asShortBuffer().get(pcm)

        val frames = pcm.size / init.channels
        if (frames == 0) return

        val blockStart = mediaCursor
        val blockEnd = blockStart + frames
        mediaCursor = blockEnd

        val from = maxOf(blockStart, windowStart)
        val to = minOf(blockEnd, windowEnd)
        if (to <= from) {
            if (mediaCursor >= windowEnd) reachedEnd = true
            return
        }

        val skip = (from - blockStart).toInt()
        val keep = (to - from).toInt()
        val slice = if (skip == 0 && keep == frames) {
            pcm
        } else {
            pcm.copyOfRange(skip * init.channels, (skip + keep) * init.channels)
        }

        if (gain != 1f) {
            for (i in slice.indices) {
                slice[i] = (slice[i] * gain).roundToInt().coerceIn(-32_768, 32_767).toShort()
            }
        }

        var loudest = 0
        for (value in slice) {
            val magnitude = if (value < 0) -value.toInt() else value.toInt()
            if (magnitude > loudest) loudest = magnitude
        }
        if (loudest > peak) peak = loudest
        samples++

        audio.write(slice, slice.size).let { written ->
            if (written < 0) {
                log.w("sink write $written at ${positionMs}ms, rebuilding")
                audio.rebuild()
            }
        }
        if (!paused) positionMs = (to - init.primingFrames) * 1000L / init.sampleRate
        if (pendingSeekSeconds == null && !paused) report()
        if (mediaCursor >= windowEnd) reachedEnd = true
    }

    private fun report() {
        val position = positionMs
        if (position < reportedMs) {
            reportedMs = position
            return
        }
        if (position - reportedMs < PROGRESS_STEP_MS) return
        reportedMs = position
        emit(PlayerEvent.Progress(position, durationMs))
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun psshKeyId(pssh: String): ByteArray? {
    val raw = runCatching { Base64.decode(pssh, Base64.DEFAULT) }.getOrNull() ?: return null
    var index = 0
    while (index + 17 < raw.size) {
        if ((raw[index].toInt() and 0xff) == 0x12 && (raw[index + 1].toInt() and 0xff) == 0x10) {
            return raw.copyOfRange(index + 2, index + 18)
        }
        index++
    }
    return null
}
