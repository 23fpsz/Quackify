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

    /** Marks a cache key as a one-shot direct url that streams, never caches. */
    const val AD_SUFFIX = "#ad"

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

/** The referer a direct url - an ad body - is served under. */
private val AD_HEADERS = mapOf("referer" to "https://open.spotify.com/")

/** A one-shot direct url streamed through the same pool the renditions use. */
private class DirectBody(
    override val url: String,
    override val headers: Map<String, String>,
) : StreamBody {
    override val head: ByteArray? get() = null
    override val total: Long get() = 0L
}

/**
 * The loading side of a streamed body: ExoPlayer's progressive reads pull
 * straight out of the key's shared stream buffer, so play-while-download
 * carries over to the unified pipeline unchanged - a resolve's probe seeds
 * the head where one ran, reads fetch ranges on demand and a background
 * pull runs ahead while a reader holds the buffer. One instance per open,
 * but every open of the same key - the session engine, the crossfade
 * spare, the muted analysis player, the re-open a seek performs - attaches
 * to the one buffer the pool holds for it, so each range crosses the wire
 * once and a seek re-opens warm instead of from a fresh scratch file.
 */
internal class YtStreamSource(
    private val context: Context,
    private val key: String,
    private val body: StreamBody,
) : DataSource {

    private val listeners = ArrayList<TransferListener>()

    private var opened: MediaDataSource? = null

    private var cursor = 0L

    private var limit = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        // Blocking is the loader thread's job; the pool hands back the key's
        // shared buffer - a resolve's seed is already in it, and a body
        // without one probes the wire here, learning its length the same
        // open the extractor path always paid. The length only exists once
        // the buffer is open, so the cursor math reads it off the body.
        val opened = runBlocking { YtStreamPool.attach(key, body, context) }
            ?: throw IOException("the alternate stream did not open")
        this.opened = opened
        val total = opened.size
        val from = dataSpec.position.coerceIn(0L, total)
        val until = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            (from + dataSpec.length).coerceAtMost(total)
        } else {
            total
        }
        cursor = from
        limit = until
        log.i("${if (key.endsWith(YtStreamTable.AD_SUFFIX)) "ad" else "ytm"} open at $from of $total, ${until - from}B to go")
        return until - from
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = opened ?: throw IOException("the alternate stream was never opened")
        if (cursor >= limit) return C.RESULT_END_OF_INPUT
        val want = minOf(length.toLong(), limit - cursor).toInt()
        if (want <= 0) return C.RESULT_END_OF_INPUT
        val got = source.readAt(cursor, buffer, offset, want)
        if (got <= 0) return C.RESULT_END_OF_INPUT
        cursor += got
        return got
    }

    override fun getUri(): Uri? =
        opened?.let { Uri.parse(body.url) }

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        val source = opened
        opened = null
        if (source != null) runCatching { source.close() }
    }
}

/**
 * The one data source factory the engines share. Reads for a catalogue
 * item flow through the song cache exactly as before; reads whose data
 * spec carries an alternate rendition key or the direct-url marker open
 * the stream buffer instead, so both cuts of a track and a one-shot ad
 * body play through the same ExoPlayer session - the queue, the crossfade
 * and the blends never see the difference.
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
            // The key is the router's whole signal: an alternate item always
            // carries the rendition key, a direct url carries the ad marker,
            // and a catalogue item carries neither.
            val key = dataSpec.key.orEmpty()
            val alternate = key.takeIf { it.endsWith(YtStreamTable.SUFFIX) }
                ?.let { YtStreamTable.of(it) }
            val routed = when {
                alternate != null -> YtStreamSource(context, key, alternate)
                key.endsWith(YtStreamTable.AD_SUFFIX) ->
                    YtStreamSource(context, key, DirectBody(dataSpec.uri.toString(), AD_HEADERS))
                else -> null
            }
            val picked: DataSource = if (routed != null) {
                listeners.forEach(routed::addTransferListener)
                routed
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
