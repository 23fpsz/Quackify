package com.quackify.backend.player

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net

private val log = Logs.of(Logs.Module.SOURCE)

/**
 * Init and audio segments kept per file so a play can start from warm bytes:
 * the ahead-of-time prefetch fills it while the previous track still plays,
 * and the engine loader reads through [ensure], which joins a download that
 * is already in flight instead of repeating it.
 */
internal object SegmentStore {

    private const val KEEP_FILES = 3

    private const val PRELOAD_SEGMENTS = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entries = LinkedHashMap<String, Entry>()

    internal class Entry(val fileId: String) {
        @Volatile
        var init: ByteArray? = null

        val segments = ConcurrentHashMap<Int, ByteArray>()

        val inFlight = ConcurrentHashMap<Int, Deferred<ByteArray?>>()
    }

    fun of(fileId: String): Entry? = synchronized(entries) {
        entries.remove(fileId)?.also { entries[fileId] = it }
    }

    /**
     * Makes sure the init segment is local and kicks the first audio segments
     * off in the background; neither waits on the other.
     */
    suspend fun warm(source: AudioSource, fromMs: Long): Entry {
        val entry = touch(source.fileId)
        trim()
        scope.launch { preload(source, fromMs) }
        if (entry.init == null) {
            entry.init = Net.fetch(source.url, 0, source.seektable.offset - 1)
        }
        return entry
    }

    /** Cached segment or a fresh fetch, shared with concurrent callers. */
    suspend fun ensure(source: AudioSource, index: Int): ByteArray? {
        val entry = touch(source.fileId)
        entry.segments[index]?.let { return it }
        val pending = entry.inFlight.getOrPut(index) {
            scope.async { fetch(entry, source, index) }
        }
        return try {
            pending.await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            log.w("segment $index of ${source.fileId.take(8)} " +
                    "${failure.javaClass.simpleName}: ${failure.message}"
            )
            null
        }
    }

    private suspend fun fetch(entry: Entry, source: AudioSource, index: Int): ByteArray? {
        val table = source.seektable
        val start = table.offsetOf(index)
        val bytes = Net.fetch(
            source.url,
            start,
            start + table.sizes[index] - 1,
            mapOf("referer" to "https://open.spotify.com/"),
        )
        if (bytes != null) {
            entry.segments.putIfAbsent(index, bytes)
        } else {
            entry.inFlight.remove(index)
        }
        return bytes
    }

    private suspend fun preload(source: AudioSource, fromMs: Long) {
        val table = source.seektable
        val start = startSegmentFor(table, fromMs)
        val last = minOf(start + PRELOAD_SEGMENTS, table.count)
        for (index in start until last) {
            ensure(source, index)
        }
        log.i("warm ${source.fileId.take(8)} up to seg $last kept=${of(source.fileId)?.segments?.size ?: 0}")
    }

    private fun touch(fileId: String): Entry = synchronized(entries) {
        (entries.remove(fileId) ?: Entry(fileId)).also { entries[fileId] = it }
    }

    private fun trim() {
        synchronized(entries) {
            while (entries.size > KEEP_FILES) {
                val oldest = entries.keys.firstOrNull() ?: break
                entries.remove(oldest)
            }
        }
    }

    private fun startSegmentFor(table: Seektable, fromMs: Long): Int {
        val target = fromMs / 1000.0
        var index = 0
        var cumulative = 0L
        for (i in 0 until table.count) {
            if (cumulative.toDouble() / table.timescale <= target) index = i else break
            cumulative += table.durations[i]
        }
        return index
    }
}
