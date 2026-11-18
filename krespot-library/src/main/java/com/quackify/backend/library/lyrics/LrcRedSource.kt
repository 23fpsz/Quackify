package com.quackify.backend.library.lyrics

import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object LrcRedSource {

    private const val BASE = "https://lrc.red/"
    private const val DURATION_TOLERANCE_SECONDS = 3.0
    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        isrc: String?,
    ): List<SyncedLyricLine>? = withContext(Dispatchers.IO) {
        val known = isrc?.let(::documentUrl)
        if (known != null) document(known)?.let { return@withContext it }
        val hit = search(title, artist, durationMs) ?: return@withContext null
        val found = hit?.optString("isrc")?.takeIf { it.isNotBlank() }?.let(::documentUrl)
            ?: return@withContext null
        document(found)
    }

    private suspend fun document(url: String): List<SyncedLyricLine>? {
        val response = Net.get(url, headers(), "lyrics.lrcred") ?: return null
        if (!response.ok) return null
        return TtmlParser.parse(response.body).takeIf { it.isNotEmpty() }
    }

    private suspend fun search(title: String, artist: String, durationMs: Long): JSONObject? {
        val url = "${BASE}search.json?q=" +
            URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val response = Net.get(url, headers(), "lyrics.lrcred") ?: return null
        if (!response.ok) return null
        val hits = response.json()?.optJSONArray("hits") ?: return null
        return best((0 until hits.length()).mapNotNull { i -> hits.optJSONObject(i) }, title, artist, durationMs)
    }

    fun best(hits: List<JSONObject>, title: String, artist: String, durationMs: Long): JSONObject? {
        val wantedTitle = coreOf(title)
        val wantedVersion = versionOf(title)
        val wantedArtists = artistsOf(artist)
        val seconds = durationMs / 1000.0

        fun distance(hit: JSONObject): Double =
            if (durationMs > 0 && !hit.isNull("duration")) abs(hit.optDouble("duration", 0.0) - seconds) else 0.0

        return hits
            .filter { hit ->
                val name = hit.optString("title")
                name.isNotBlank() &&
                    ISRC.matches(hit.optString("isrc").uppercase(Locale.ROOT)) &&
                    coreOf(name) == wantedTitle &&
                    versionOf(name) == wantedVersion &&
                    (wantedArtists.isEmpty() ||
                        artistsOf(hit.optString("artist")).any { it in wantedArtists }) &&
                    distance(hit) <= DURATION_TOLERANCE_SECONDS
            }
            .minByOrNull(::distance)
    }

    private fun coreOf(title: String): String =
        normalized(title.replace(BRACKETED, " ").substringBefore(" - "))
            .ifEmpty { normalized(title) }

    private fun versionOf(title: String): Set<String> {
        val extras = BRACKETED.findAll(title).joinToString(" ") { it.value } +
            " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private fun artistsOf(artist: String): Set<String> =
        artist.split(ARTIST_SEPARATORS).map(::normalized).filter { it.isNotEmpty() }.toSet()

    private fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()

    private fun documentUrl(isrc: String): String? =
        isrc.trim().uppercase(Locale.ROOT).takeIf { ISRC.matches(it) }?.let { "${BASE}s/$it.ttml" }

    private fun headers() = mapOf(
        "User-Agent" to Net.DESKTOP_UA,
        "Accept" to "application/json",
    )

    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")
    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val VERSION_WORDS = setOf(
        "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
        "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended",
    )
}
