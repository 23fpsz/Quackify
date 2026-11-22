/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * |\  \ | |  __/\__ \ |_) | (_) | |_
 * \_| \/_|  \___||___/ .__/ \___/ \__|
 *                     | |
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
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.quackify.backend.core.log.Logs
import java.io.IOException
import kotlinx.coroutines.runBlocking

private val log = Logs.of(Logs.Module.MP3)

/**
 * One playable cut of a track. Beat grids and tail-silence measurements
 * belong to the cut they were decoded from - the alternate stream is a
 * different edit of the same song - so every analysis key rides the cut's
 * own cache key, while the queue and the bridge keep addressing the track
 * by its plain uri.
 */
internal sealed interface Cut {
    val uri: String
    val cacheKey: String
    val lengthMs: Long

    /** The catalogue's own file, with its licence and seektable. */
    class Catalogue(val source: AudioSource, override val uri: String) : Cut {
        override val cacheKey: String get() = uri
        override val lengthMs: Long
            get() = source.seektable.totalDuration * 1000L / source.seektable.timescale
    }

    /** The youtube music stream standing in for the track. */
    class Alternate(val stream: YtStream, override val uri: String) : Cut {
        override val cacheKey: String get() = uri + YtStreamTable.SUFFIX
        override val lengthMs: Long get() = stream.lengthMs
    }
}

/**
 * The resolved alternates the loading side of the pipeline may be asked for,
 * keyed by rendition. [serve] runs the moment a stream is committed to
 * playback or to the queue - before the media source that reads it exists -
 * and the entries carry only the url, its headers and the probe seed, so a
 * bounded table is all playback ever needs.
 */
internal object YtStreamTable {

    /** Marks a cache key as the alternate rendition of a track uri. */
    const val SUFFIX = "#ytm"

    private const val LIMIT = 8

    private val table = LinkedHashMap<String, YtStream>()

    fun keyFor(uri: String): String = uri + SUFFIX

    @Synchronized
    fun serve(uri: String, stream: YtStream) {
        val key = keyFor(uri)
        table.remove(key)
        table[key] = stream
        while (table.size > LIMIT) {
            table.remove(table.keys.firstOrNull() ?: break)
        }
    }

    @Synchronized
    fun of(key: String): YtStream? = table[key]
}

/**
 * The loading side of an alternate stream: ExoPlayer's progressive reads
 * pull straight out of the stream buffer, so play-while-download carries
 * over to the unified pipeline unchanged - the probe at resolve time seeds
 * the head, reads fetch ranges on demand and a background pull runs ahead.
 * One instance per open: the buffer owns a scratch file and a pull loop
 * that [close] tears down, and ExoPlayer's loader re-opens with a fresh
 * position on every seek.
 */
internal class YtStreamSource(
    private val context: Context,
    private val stream: YtStream,
) : DataSource {

    private val listeners = ArrayList<TransferListener>()

    private var body: MediaDataSource? = null

    private var cursor = 0L

    private var limit = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val total = stream.total
        val from = dataSpec.position.coerceIn(0L, total)
        val until = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            (from + dataSpec.length).coerceAtMost(total)
        } else {
            total
        }
        // Blocking is the loader thread's job; the seed carries the probe's
        // head and length, so no wire roundtrip happens here.
        val opened = runBlocking {
            StreamBuffer.open(stream.url, stream.headers, context.cacheDir, "ytm", stream.head, stream.total)
        } ?: throw IOException("the alternate stream did not open")
        body = opened
        cursor = from
        limit = until
        log.i("ytm open at $from of $total, ${until - from}B to go")
        return until - from
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = body ?: throw IOException("the alternate stream was never opened")
        if (cursor >= limit) return C.RESULT_END_OF_INPUT
        val want = minOf(length.toLong(), limit - cursor).toInt()
        if (want <= 0) return C.RESULT_END_OF_INPUT
        val got = source.readAt(cursor, buffer, offset, want)
        if (got <= 0) return C.RESULT_END_OF_INPUT
        cursor += got
        return got
    }

    override fun getUri(): Uri? =
        body?.let { Uri.parse(stream.url) }

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        val source = body
        body = null
        if (source != null) runCatching { source.close() }
    }
}

/**
 * The one data source factory the engines share. Reads for a catalogue
 * item flow through the song cache exactly as before; reads whose data
 * spec carries an alternate rendition key open the stream buffer instead,
 * so both cuts of a track play through the same ExoPlayer session - the
 * queue, the crossfade and the blends never see the difference.
 */
internal class RoutedSources(private val context: Context) : DataSource.Factory {

    private val catalogue: DataSource.Factory = SongCache.factory(context)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))

    override fun createDataSource(): DataSource = Route(context, catalogue.createDataSource())

    private class Route(
        private val context: Context,
        private val catalogue: DataSource,
    ) : DataSource {

        private val listeners = ArrayList<TransferListener>()

        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            catalogue.addTransferListener(transferListener)
            listeners.add(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            // The rendition key is the router's whole signal: an alternate
            // item always carries one, a catalogue item never does.
            val stream = dataSpec.key
                ?.takeIf { it.endsWith(YtStreamTable.SUFFIX) }
                ?.let { YtStreamTable.of(it) }
            val picked = if (stream != null) {
                YtStreamSource(context, stream).also { source ->
                    listeners.forEach(source::addTransferListener)
                }
            } else {
                catalogue
            }
            active = picked
            return picked.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checkNotNull(active).read(buffer, offset, length)

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders ?: emptyMap()

        override fun close() {
            active?.let { runCatching { it.close() } }
            active = null
        }
    }
}
