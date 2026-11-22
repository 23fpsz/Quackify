package com.quackify.backend.library.lyrics

import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object BetterLyricsSource {

    private const val BASE = "https://lyrics-api.boidu.dev/getLyrics"

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<SyncedLyricLine>? = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$BASE?s=")
            append(URLEncoder.encode(title, "UTF-8"))
            append("&a=")
            append(URLEncoder.encode(artist, "UTF-8"))
            val seconds = durationMs / 1000
            if (seconds > 0) append("&d=").append(seconds)
            if (!album.isNullOrBlank()) {
                append("&al=").append(URLEncoder.encode(album, "UTF-8"))
            }
        }
        val response = Net.get(url, headers(), "lyrics.betterlyrics") ?: return@withContext null
        if (!response.ok) return@withContext null
        val json = response.json() ?: return@withContext null
        val ttml = json.optString("ttml").takeIf { it.isNotBlank() } ?: return@withContext null
        TtmlParser.parse(ttml).takeIf { it.isNotEmpty() }
    }

    private fun headers() = mapOf(
        "User-Agent" to Net.DESKTOP_UA,
        "Accept" to "application/json",
    )
}
