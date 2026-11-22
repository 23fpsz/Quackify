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

import android.media.MediaDataSource
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlin.time.Duration.Companion.milliseconds

private val log = Logs.of(Logs.Module.MP3)

/** Whole body already in hand: the extractor reads straight out of memory. */
internal class BytesSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val count = minOf(size.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, count)
        return count
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() = Unit
}

/**
 * Plays while it downloads. The extractor's reads pull ranges off the wire on
 * demand into a growing local copy, and a background pull keeps fetching
 * forward, so playback starts once the header lands instead of waiting for
 * the whole file. Servers that ignore ranges fall back to [BytesSource] in
 * [open], which is the whole-download behaviour the wire protocol allows.
 */
internal class StreamBuffer private constructor(
    private val url: String,
    private val headers: Map<String, String>,
    private val total: Long,
    private val scratch: File,
    private val body: RandomAccessFile,
    private val tag: String,
    seeded: Int = 0,
) : MediaDataSource() {

    private val lock = ReentrantLock()
    private val progressed = lock.newCondition()

    private val chunks = (((total + CHUNK - 1) / CHUNK).coerceAtLeast(1L)).toInt()

    private val present = BooleanArray(chunks)

    private val busy = BooleanArray(chunks)

    @Volatile
    private var closed = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val totalBytes: Long get() = total

    init {
        // chunk 0 usually rode along with the open probe, but only counts as
        // present when the probe filled the whole pull
        lock.withLock {
            present[0] = seeded >= minOf(CHUNK, total)
        }
        scope.launch { pullLoop() }
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size <= 0) return 0
        if (position >= total) return -1
        val until = minOf(position + size.toLong(), total)
        ensure(position, until)
        val count = (until - position).toInt()
        synchronized(body) {
            body.seek(position)
            var read = 0
            while (read < count) {
                val got = body.read(buffer, offset + read, count - read)
                if (got < 0) throw IOException("short read at ${position + read} of $total")
                read += got
            }
        }
        return count
    }

    override fun getSize(): Long = total

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            progressed.signalAll()
        }
        scope.cancel()
        runCatching { body.close() }
        runCatching { scratch.delete() }
        log.i("$tag closed at ${have()} of ${total}B")
    }

    /** Every chunk covering [from, until) sits in the scratch file when this returns. */
    private fun ensure(from: Long, until: Long) {
        var index = (from / CHUNK).toInt()
        val last = ((until - 1) / CHUNK).toInt()
        while (index <= last) {
            if (!claim(index)) pullNow(index)
            index++
        }
    }

    /**
     * True when the chunk is present, waiting for an in-flight fetch if one
     * is already on it. False hands the fetch duty to the caller.
     */
    private fun claim(index: Int): Boolean {
        lock.withLock {
            while (true) {
                if (closed) throw IOException("stream closed")
                if (present[index]) return true
                if (!busy[index]) {
                    busy[index] = true
                    return false
                }
                progressed.await()
            }
        }
        return true
    }

    /** The wire pull, callable from the extractor's blocking readAt thread. */
    private fun pullNow(index: Int) = runBlocking { pull(index) }

    private suspend fun pull(index: Int) {
        try {
            val from = index.toLong() * CHUNK
            val to = minOf(from + CHUNK, total) - 1
            val got = Net.fetch(url, from.toInt(), to.toInt(), headers, "$tag#$index")
            if (got == null || got.isEmpty()) throw IOException("pull $index came back empty")
            // a server that ignored the range handed the whole body; keep the slice
            val take = minOf(got.size, (to - from + 1).toInt())
            synchronized(body) {
                body.seek(from)
                body.write(got, 0, take)
            }
            lock.withLock { present[index] = true }
        } finally {
            lock.withLock {
                busy[index] = false
                progressed.signalAll()
            }
        }
    }

    /**
     * The background pull: keeps fetching forward so the decode side reads a
     * warm file instead of waiting on the wire, and refills the holes a seek
     * leaves behind it.
     */
    private suspend fun pullLoop() {
        var failures = 0
        while (!closed) {
            val index = lock.withLock {
                if (closed) return
                var scan = 0
                while (scan < chunks && (present[scan] || busy[scan])) scan++
                if (scan >= chunks) -1
                else {
                    busy[scan] = true
                    scan
                }
            }

            if (index < 0) {
                // The condition cannot be awaited from a coroutine: it needs
                // the monitor held, and a pooled worker parked on it would sit
                // un-cancellable. A short poll is all a prefetcher needs to
                // notice fresh holes or closure.
                delay(250.milliseconds)
                continue
            }

            try {
                pull(index)
                failures = 0
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                failures++
                log.w("$tag pull $index failed ${failure.javaClass.simpleName}: " +
                        "${failure.message}, $failures down")
                if (failures >= 6) {
                    log.w("$tag prefetch gave up, reads will pull on their own")
                    return
                }
                delay((250L * failures).milliseconds)
            }
        }
    }

    private fun have(): Long {
        var held = 0L
        for (index in 0 until chunks) if (present[index]) held++
        return held * CHUNK
    }

    companion object {
        private const val CHUNK = 64L * 1024

        /**
         * Opens the url for progressive playback: one range probe learns the
         * exact length and lands the first chunk, then reads pull on demand.
         * A server that answers the probe with a full 200 body gets that body
         * served as a plain in-memory source instead.
         */
        suspend fun open(
            url: String,
            headers: Map<String, String>,
            cacheDir: File?,
            tag: String,
        ): MediaDataSource? {
            val probe =
                Net.blob(url, headers + mapOf("Range" to "bytes=0-${CHUNK - 1}"), "$tag-open")
                    ?: return null.also { log.w("$tag open: no response") }
            if (!probe.ok) return null.also { log.w("$tag open: refused ${probe.code}") }
            if (probe.code == 200) {
                log.i("$tag open: ranges ignored, ${probe.size}B in hand")
                return BytesSource(probe.bytes)
            }
            val total = probe.header("content-range")
                .substringAfter('/').trim().toLongOrNull()
                ?: return null.also { log.w("$tag open: no length in the reply") }
            if (total <= 0) return null.also { log.w("$tag open: empty stream") }
            val spill = cacheDir
                ?: return null.also { log.w("$tag open: nowhere to spill the stream") }
            return runInterruptible(Dispatchers.IO) {
                try {
                    val scratch = File.createTempFile("quackify-", ".stream", spill)
                    val body = RandomAccessFile(scratch, "rw")
                    val seeded = minOf(probe.size.toLong(), CHUNK).toInt()
                    try {
                        body.setLength(total)
                        if (seeded > 0) {
                            body.seek(0)
                            body.write(probe.bytes, 0, seeded)
                        }
                    } catch (failure: Throwable) {
                        runCatching { body.close() }
                        runCatching { scratch.delete() }
                        throw failure
                    }
                    log.i("$tag open: ${total}B streams in ${CHUNK}B pulls")
                    StreamBuffer(url, headers, total, scratch, body, tag, seeded)
                } catch (failure: Throwable) {
                    log.e("$tag open failed ${failure.javaClass.simpleName}: ${failure.message}")
                    null
                }
            }
        }
    }
}
