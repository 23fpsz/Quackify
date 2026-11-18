package com.quackify.backend.library.lyrics

import android.content.Context
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

object LrcLibSource {

    private const val BASE = "https://lrclib.net/api"
    private const val AGENT = "Quackify (https://github.com/23fpsz/Quackify)"

    suspend fun lyrics(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long,
    ): List<SyncedLyricLine>? = withContext(Dispatchers.IO) {
        val seconds = (durationMs / 1000).toInt()
        val exact = runCatching { exactMatch(context, title, artist, seconds) }.getOrNull()
        val synced = exact ?: runCatching { bestSearchHit(context, title, artist, seconds) }.getOrNull()
        synced?.let(LrcParser::parse)?.takeIf { it.isNotEmpty() }
    }

    private suspend fun exactMatch(context: Context, title: String, artist: String, seconds: Int): String? {
        val url = buildString {
            append("$BASE/get?track_name=")
            append(java.net.URLEncoder.encode(title, "UTF-8"))
            append("&artist_name=")
            append(java.net.URLEncoder.encode(artist, "UTF-8"))
            append("&duration=$seconds")
        }
        val response = Net.get(url, headers(), "lyrics.lrclib") ?: return null
        if (!response.ok) return null
        return response.json()?.optString("syncedLyrics")?.takeIf { it.isNotBlank() }
    }

    private suspend fun bestSearchHit(context: Context, title: String, artist: String, seconds: Int): String? {
        val url = buildString {
            append("$BASE/search?track_name=")
            append(java.net.URLEncoder.encode(title, "UTF-8"))
            append("&artist_name=")
            append(java.net.URLEncoder.encode(artist, "UTF-8"))
        }
        val response = Net.get(url, headers(), "lyrics.lrclib") ?: return null
        if (!response.ok) return null
        val hits = response.jsonArray() ?: return null
        return (0 until hits.length()).mapNotNull { i -> hits.optJSONObject(i) }
            .filter { it.optString("syncedLyrics").isNotBlank() }
            .minByOrNull { abs(it.optDouble("duration", 0.0) - seconds) }
            ?.optString("syncedLyrics")?.takeIf { it.isNotBlank() }
    }

    private fun headers() = mapOf(
        "User-Agent" to AGENT,
        "Accept" to "application/json",
    )
}
