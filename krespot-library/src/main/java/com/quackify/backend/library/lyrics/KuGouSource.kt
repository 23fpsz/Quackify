package com.quackify.backend.library.lyrics

import android.util.Base64
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

object KuGouSource {

    private const val SEARCH_SONGS = "https://mobileservice.kugou.com/api/v3/search/song"

    private const val SEARCH_LYRICS = "https://lyrics.kugou.com/search"

    private const val DOWNLOAD = "https://lyrics.kugou.com/download"

    private const val DURATION_TOLERANCE_SECONDS = 8

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<SyncedLyricLine>? = withContext(Dispatchers.IO) {
        val keyword = keyword(title, artist, album)
        val seconds = (durationMs / 1000).toInt()
        val candidate = songHashes(keyword, seconds)?.firstNotNullOfOrNull { hash ->
            searchCandidates(hash = hash)?.firstOrNull()
        } ?: searchCandidates(keyword = keyword, seconds = seconds)?.firstOrNull()
            ?: return@withContext null
        val lrc = download(candidate) ?: return@withContext null
        LrcParser.parse(lrc).takeIf { it.isNotEmpty() }
    }

    private fun keyword(title: String, artist: String, album: String?): String = buildString {
        append(title.replace(Regex("""[(（].*?[)）]"""), "").trim())
        append(" - ")
        append(artist.replace(Regex("""[(（].*?[)）]"""), "").trim())
        if (!album.isNullOrBlank()) append(' ').append(album)
    }.trim()

    private suspend fun songHashes(keyword: String, seconds: Int): List<String>? {
        val url = "$SEARCH_SONGS?version=9108&plat=0&pagesize=8&showtype=0" +
            "&keyword=${encode(keyword)}"
        val json = getJson(url) ?: return null
        val info = json.optJSONObject("data")?.optJSONArray("info") ?: return null
        return (0 until info.length()).mapNotNull { info.optJSONObject(it) }
            .filter { seconds <= 0 || abs(it.optInt("duration", -1) - seconds) <= DURATION_TOLERANCE_SECONDS }
            .sortedBy { abs(it.optInt("duration", -1) - seconds) }
            .map { it.optString("hash") }
            .filter { it.isNotBlank() }
    }

    private suspend fun searchCandidates(hash: String? = null, keyword: String? = null, seconds: Int = -1): List<JSONObject>? {
        val url = buildString {
            append("$SEARCH_LYRICS?ver=1&man=yes&client=pc")
            if (hash != null) append("&hash=").append(encode(hash))
            if (keyword != null) {
                append("&keyword=").append(encode(keyword))
                if (seconds > 0) append("&duration=").append(seconds * 1000)
            }
        }
        if (hash == null && keyword == null) return null
        val json = getJson(url) ?: return null
        val candidates = json.optJSONArray("candidates") ?: return null
        return (0 until candidates.length()).mapNotNull { candidates.optJSONObject(it) }
    }

    private suspend fun download(candidate: JSONObject): String? {
        val id = candidate.optString("id")
        val accessKey = candidate.optString("accesskey")
        if (id.isBlank() || accessKey.isBlank()) return null
        val url = "$DOWNLOAD?fmt=lrc&charset=utf8&client=pc&ver=1" +
            "&id=${encode(id)}&accesskey=${encode(accessKey)}"
        val json = getJson(url) ?: return null
        val content = json.optString("content").takeIf { it.isNotBlank() } ?: return null
        val decoded = runCatching {
            String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return decoded.takeUnless { isInstrumentalNotice(it) }
    }

    /**
     * A song KuGou holds no words for still answers with a single stamped
     * "pure music, please enjoy" line; that is a miss, not lyrics.
     */
    private fun isInstrumentalNotice(lrc: String): Boolean {
        val lines = lrc.lineSequence()
            .map { it.replace(Regex("""\[\d{2}:\d{2}\.\d{2,3}]"""), "").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return lines.isNotEmpty() && lines.all { INSTRUMENTAL.containsMatchIn(it) }
    }

    private suspend fun getJson(url: String): JSONObject? {
        val response = Net.get(url, headers(), "lyrics.kugou") ?: return null
        if (!response.ok) return null
        return response.json()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun headers() = mapOf(
        "User-Agent" to Net.DESKTOP_UA,
        "Accept" to "application/json",
    )

    private val INSTRUMENTAL = Regex("""纯音乐.*欣赏""")
}
