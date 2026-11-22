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
 */
internal object YtStreamPool {

    /** How long an unheld buffer waits for its next reader before teardown. */
    private const val KEEP_ALIVE_MS = 15_000L

    private val pool = LinkedHashMap<String, Entry>()

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
    private class Ref(private val entry: Entry) : MediaDataSource() {

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
            entry.body.readAt(position, buffer, offset, size)

        override fun getSize(): Long = entry.body.size

        override fun close() {
            YtStreamPool.detach(entry)
        }
    }

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
