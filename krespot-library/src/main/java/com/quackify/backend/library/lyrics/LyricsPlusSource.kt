package com.quackify.backend.library.lyrics

import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

object LyricsPlusSource {

    private val MIRRORS = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app",
    )

    private val lastGood = AtomicReference<String?>(null)

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        isrc: String?,
    ): List<SyncedLyricLine>? = coroutineScope {
        val remembered = lastGood.get()
        val hosts = remembered
            ?.let { listOf(it) + MIRRORS.filterNot { mirror -> mirror == it } }
            ?: MIRRORS
        val pending = hosts.map { host ->
            host to async(Dispatchers.IO) { fetch(host, title, artist, durationMs, album, isrc) }
        }.toMutableList()
        try {
            while (pending.isNotEmpty()) {
                val (host, lines) = select {
                    pending.forEach { (host, job) -> job.onAwait { host to it } }
                }
                pending.removeAll { it.first == host }
                if (!lines.isNullOrEmpty()) {
                    lastGood.set(host)
                    return@coroutineScope lines
                }
            }
            null
        } finally {
            pending.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetch(
        host: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        isrc: String?,
    ): List<SyncedLyricLine>? = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$host/v2/lyrics/get?title=")
            append(URLEncoder.encode(title, "UTF-8"))
            append("&artist=")
            append(URLEncoder.encode(artist, "UTF-8"))
            val seconds = durationMs / 1000
            if (seconds > 0) append("&duration=").append(seconds)
            if (!album.isNullOrBlank()) {
                append("&album=").append(URLEncoder.encode(album, "UTF-8"))
            }
            if (!isrc.isNullOrBlank()) append("&isrc=").append(isrc)
        }
        val response = Net.get(url, headers(), "lyrics.lyricsplus") ?: return@withContext null
        if (!response.ok) return@withContext null
        val json = response.json() ?: return@withContext null
        parse(json)
    }

    private fun parse(json: JSONObject): List<SyncedLyricLine> {
        val rows = json.optJSONArray("lyrics") ?: return emptyList()
        val lines = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val start = row.optLong("time", -1L)
            if (start < 0L) return@mapNotNull null
            val syllables = row.optJSONArray("syllabus")
            val runs = syllables?.let { marks ->
                (0 until marks.length()).mapNotNull { i ->
                    val mark = marks.optJSONObject(i) ?: return@mapNotNull null
                    val text = mark.optString("text").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val at = mark.optLong("time", -1L)
                    if (at < 0L) return@mapNotNull null
                    TimedRun(at, at + mark.optLong("duration", 0L), text)
                }
            }.orEmpty()
            val words = wordsFromRuns(runs, spacingIsExplicit = true)
            if (words.isNotEmpty()) {
                SyncedLyricLine(
                    timeMs = minOf(start, words.first().startMs),
                    text = words.joinToString(" ") { it.text },
                    words = words,
                )
            } else {
                val text = row.optString("text").trim()
                if (text.isEmpty()) return@mapNotNull null
                val duration = row.optLong("duration", 0L)
                SyncedLyricLine(
                    timeMs = start,
                    text = text,
                    sungUntilMs = duration.takeIf { it > 0L }?.let { start + it },
                )
            }
        }.sortedBy { it.timeMs }
        return lines
    }

    private fun headers() = mapOf(
        "User-Agent" to Net.DESKTOP_UA,
        "Accept" to "application/json",
    )
}
