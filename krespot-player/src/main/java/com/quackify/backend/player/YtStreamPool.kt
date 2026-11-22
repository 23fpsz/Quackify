/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * | |\  \ | |  __/\__ \ |_) | (_) | |_
 * \_| \/_|  \___||___/ .__/ \___/ \__|
 *                     | |
 *                     |_|
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (into whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.player

import android.content.Context
import android.media.MediaDataSource
import com.quackify.backend.core.log.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = Logs.of(Logs.Module.MP3)

/**
 * One stream buffer per alternate rendition, shared by every reader. The
 * session engine's loads, the crossfade spare, the muted analysis player and
 * the re-opens ExoPlayer's loader performs on seeks all attach to the same
 * growing local copy, so each range of the file crosses the wire exactly
 * once - the analysis window a playing track is about to reach is already
 * in the scratch file by the time playback reads it, instead of being
 * fetched a second time by a private buffer. Readers come and go; the
 * buffer lives while any of them holds it, plus a short grace that keeps a
 * seek's re-open or a held crossfade source from starting the download
 * over. While nobody holds it the background pull parks, so a skipped
 * track's tail is not fetched for a grace period nobody wanted.
 *
 * The queue's next transition gets a pin instead: the head window the
 * handover will read is pulled ahead of time and the buffer is held across
 * the whole track that plays before it, because the handover can be
 * minutes out and no grace bridges that.
 */
internal object YtStreamPool {

    /** How long an unheld buffer waits for its next reader before teardown. */
    private const val KEEP_ALIVE_MS = 15_000L

    private val pool = LinkedHashMap<String, Entry>()

    /** The queue's one warm slot: the next handover's pinned buffer. */
    private var pinned: Pin? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Entry(
        val key: String,
        val url: String,
        val body: MediaDataSource,
    ) {
        var refs = 0

        /** Set once the grace expired: a dying entry can never be revived. */
        var closing = false

        var teardown: Job? = null
    }

    /** A reader's handle: drops its reference on [close], nothing more. */
    private class Ref(val entry: Entry) : MediaDataSource() {

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
            entry.body.readAt(position, buffer, offset, size)

        override fun getSize(): Long = entry.body.size

        override fun close() {
            YtStreamPool.detach(entry)
        }
    }

    /** One held reference the queue owns on behalf of a future handover. */
    private class Pin(val key: String, val entry: Entry)

    /**
     * Hands out a reference to the rendition's shared buffer, opening the
     * stream on first use. The url is the identity: a rendition re-resolved
     * to a different url never reuses the old entry's bytes, and an entry
     * whose rendition moved on dies with its last reader instead of serving
     * the wrong cut.
     */
    suspend fun attach(key: String, stream: YtStream, context: Context): MediaDataSource? {
        synchronized(pool) {
            held(key, stream)?.let { return Ref(it) }
        }
        val opened = StreamBuffer.open(
            stream.url,
            stream.headers,
            context.cacheDir,
            "ytm:" + key.removeSuffix(YtStreamTable.SUFFIX).takeLast(8),
            stream.head,
            stream.total,
        ) ?: return null
        synchronized(pool) {
            // Lost the open race: the winner's bytes serve, ours were wasted.
            held(key, stream)?.let {
                runCatching { opened.close() }
                return Ref(it)
            }
            val entry = Entry(key, stream.url, opened)
            pool[key] = entry
            entry.refs = 1
            log.i("ytm buffer opened for '${key.takeLast(16)}', ${stream.total}B")
            return Ref(entry)
        }
    }

    /**
     * Pins the rendition's buffer and pulls its first [bytes], so the
     * queue's next handover lands on a warm head instead of a cold open.
     * The pin holds the buffer (and any window an analysis decode already
     * pulled into it) across the whole track that plays before the
     * handover; the background pull parks while the pin is the only holder,
     * so pinning costs exactly the one window. A reader that opens in the
     * meantime - a held crossfade source, the analysis - un-parks the pull
     * as usual and the window keeps filling.
     */
    fun warmHead(key: String, stream: YtStream, context: Context, bytes: Long) {
        scope.launch(Dispatchers.IO) {
            val opened = attach(key, stream, context) ?: return@launch
            val entry = (opened as Ref).entry
            val (previous, park) = synchronized(pool) {
                val old = pinned
                pinned = Pin(key, entry)
                // Only a lone pin parks: anyone else reading wants the pull
                // running, and the park would silently cap their fill.
                old to (entry.refs <= 1)
            }
            if (park) (entry.body as? StreamBuffer)?.prefetch(false)
            previous?.let { detach(it.entry) }
            try {
                val window = bytes.coerceIn(0L, entry.body.size)
                val chunk = ByteArray(64 * 1024)
                var pulled = 0L
                while (pulled < window) {
                    val got = entry.body.readAt(pulled, chunk, 0, minOf(chunk.size.toLong(), window - pulled).toInt())
                    if (got <= 0) break
                    pulled += got
                }
                log.i("ytm warm head for '${key.takeLast(16)}': $pulled of ${window}B parked for the handover")
            } catch (failure: Throwable) {
                log.w("ytm warm head for '${key.takeLast(16)}' failed " +
                        "${failure.javaClass.simpleName}: ${failure.message}")
            }
        }
    }

    /** Drops the queue's pin, whichever rendition it holds. */
    fun unpinAll() {
        val pin = synchronized(pool) { pinned.also { pinned = null } } ?: return
        detach(pin.entry)
    }

    /**
     * The live entry for [stream], revived by cancelling its teardown. The
     * caller must hold [pool]: this is the pool's whole critical section.
     */
    private fun held(key: String, stream: YtStream): Entry? {
        val entry = pool[key] ?: return null
        if (entry.closing || entry.url != stream.url) return null
        entry.teardown?.cancel()
        entry.teardown = null
        entry.refs++
        (entry.body as? StreamBuffer)?.prefetch(true)
        return entry
    }

    private fun detach(entry: Entry) {
        synchronized(pool) {
            entry.refs--
            if (entry.refs > 0) return
            // No reader left: park the background pull so the grace window
            // does not fetch a file nobody is reading, then tear the buffer
            // down once the grace says nobody is coming back either.
            (entry.body as? StreamBuffer)?.prefetch(false)
            entry.teardown?.cancel()
            entry.teardown = scope.launch {
                delay(KEEP_ALIVE_MS)
                val body = synchronized(pool) {
                    if (entry.refs > 0 || entry.closing) return@launch
                    entry.closing = true
                    if (pool[entry.key] === entry) pool.remove(entry.key)
                    entry.body
                }
                runCatching { body.close() }
                log.i("ytm buffer for '${entry.key.takeLast(16)}' torn down after the grace")
            }
        }
    }
}
