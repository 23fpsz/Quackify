/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
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

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import kotlin.time.Duration.Companion.milliseconds

private val log = Logs.of(Logs.Module.MP3)

private const val MIME_PREFIX = "audio/"
private const val PROGRESS_STEP_MS = 250L

internal class Mp3Engine(
    private val url: String,
    private val emit: (PlayerEvent) -> Unit,
    private val headers: Map<String, String> = mapOf("referer" to "https://open.spotify.com/"),
    private val startAtMs: Long = 0L,
    private val cacheDir: File? = null,
    private val seed: ByteArray? = null,
    private val seedTotal: Long = 0L,
) {

    @Volatile
    private var stopped = false

    @Volatile
    private var paused = false

    @Volatile
    private var positionMs = 0L

    @Volatile
    private var durationMs = 0L

    @Volatile
    private var seekAtMs = -1L

    private var sink: AudioSink? = null

    val currentPositionMs: Long get() = positionMs

    fun stop() {
        stopped = true
        paused = false
    }

    fun pause() {
        paused = true
        sink?.pause()
    }

    fun resume() {
        paused = false
        sink?.resume()
    }

    fun duck(level: Float) {
        sink?.duck(level)
    }

    fun volume(level: Float) {
        sink?.volume(level)
    }

    fun seekTo(ms: Long) {
        seekAtMs = ms.coerceAtLeast(0L)
    }

    suspend fun run() {
        stopped = false
        val source = withContext(Dispatchers.IO) { openSource() }
        if (source == null) {
            emit(PlayerEvent.Failed("ad download failed"))
            return
        }
        try {
            decode(source)
        } finally {
            runCatching { source.close() }
        }
    }

    /**
     * The data source the extractor reads: a streaming pull when the server
     * honors ranges, the whole body in memory when it does not.
     */
    private suspend fun openSource(): MediaDataSource? {
        val streamed = cacheDir?.let { dir ->
            runCatching { StreamBuffer.open(url, headers, dir, "ad", seed, seedTotal) }.getOrNull()
        }
        if (streamed != null) return streamed
        val whole = Net.blob(url, headers, tag = "ad").also {
            log.i("ad bytes ${it?.code ?: "no response"} ${it?.size ?: 0}B")
        }?.takeIf { it.ok } ?: return null
        return BytesSource(whole.bytes)
    }

    private suspend fun decode(source: MediaDataSource) {
        val extractor = MediaExtractor()
        val prepared = runCatching {
            extractor.setDataSource(source)
            val index = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(MIME_PREFIX)
            } ?: error("no audio track")
            extractor.selectTrack(index)
            extractor.getTrackFormat(index)
        }
        val format = prepared.getOrElse {
            log.e("extractor ${it.javaClass.simpleName}: ${it.message}")
            extractor.release()
            emit(PlayerEvent.Failed("ad container unreadable"))
            return
        }
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION) / 1000L
        } else {
            0L
        }
        if (startAtMs > 0L) {
            extractor.seekTo(startAtMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            positionMs = extractor.sampleTime / 1000L
        }
        log.i("ad $mime ${sampleRate}Hz ${channels}ch ${durationMs}ms")

        val codec = runCatching {
            MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }
        }.getOrElse {
            log.e("decoder ${it.javaClass.simpleName}: ${it.message}")
            extractor.release()
            emit(PlayerEvent.Failed("ad decoder unavailable"))
            return
        }
        val audio = AudioSink(sampleRate, channels)
        sink = audio
        audio.start()
        emit(PlayerEvent.Started())

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var reported = 0L
        try {
            while (!stopped && !outputDone) {
                while (paused && !stopped) delay(50.milliseconds)
                val wanted = seekAtMs
                if (wanted >= 0L) {
                    seekAtMs = -1L
                    val bounded = if (durationMs > 0L) {
                        wanted.coerceAtMost((durationMs - 1000L).coerceAtLeast(0L))
                    } else {
                        wanted
                    }
                    extractor.seekTo(bounded * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    codec.flush()
                    sink?.flush()
                    positionMs = extractor.sampleTime / 1000L
                    reported = positionMs
                    inputDone = false
                    outputDone = false
                    emit(PlayerEvent.Seeked(positionMs))
                    log.i("seek landed at ${positionMs}ms of ${durationMs}ms")
                }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index < 0) break
                    if (info.size > 0) write(codec, index, audio, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                        break
                    }
                }
                if (positionMs - reported >= PROGRESS_STEP_MS) {
                    reported = positionMs
                    emit(PlayerEvent.Progress(positionMs, durationMs))
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            // a stopped stream fails its pending read on the way out; that is
            // the teardown, not a playback error
            if (stopped) {
                log.i("ad stopped mid-read ${failure.javaClass.simpleName}: ${failure.message}")
            } else {
                log.e("ad run failed ${failure.javaClass.simpleName}: ${failure.message}")
                emit(PlayerEvent.Failed(failure.message.orEmpty()))
            }
        } finally {
            audio.release()
            sink = null
            runCatching {
                codec.stop()
                codec.release()
            }
            extractor.release()
        }

        if (!stopped && outputDone) {
            log.i("ad ended at ${positionMs}ms of ${durationMs}ms")
            emit(PlayerEvent.Ended())
        }
    }

    private fun write(codec: MediaCodec, index: Int, audio: AudioSink, info: MediaCodec.BufferInfo) {
        val out = codec.getOutputBuffer(index) ?: return
        out.position(info.offset)
        out.limit(info.offset + info.size)
        val pcm = ShortArray(info.size / 2)
        out.asShortBuffer().get(pcm)
        if (pcm.isEmpty()) return
        audio.write(pcm, pcm.size)
        positionMs = info.presentationTimeUs / 1000L
    }
}
