package com.quackify.backend.library.lyrics

import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

object SimpMusicSource {

    private const val BASE = "https://api-lyrics.simpmusic.org/v1/"

    private const val DURATION_TOLERANCE_SECONDS = 10

    suspend fun lyrics(videoId: String, durationMs: Long): List<SyncedLyricLine>? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null
            val response = Net.get(BASE + videoId, headers(), "lyrics.simpmusic") ?: return@withContext null
            if (!response.ok) return@withContext null
            val json = response.json() ?: return@withContext null
            if (!json.optBoolean("success", false)) return@withContext null
            val data = json.optJSONArray("data") ?: return@withContext null
            val seconds = (durationMs / 1000).toInt()
            val track = (0 until data.length()).mapNotNull { data.optJSONObject(it) }
                .filter { seconds <= 0 || abs(it.optInt("duration", 0) - seconds) <= DURATION_TOLERANCE_SECONDS }
                .minByOrNull { abs(it.optInt("duration", 0) - seconds) }
                ?: return@withContext null
            track.optString("richSyncLyrics").takeIf { it.isNotBlank() }
                ?.let { EnhancedLrcParser.parse(it) }
                ?.takeIf { it.isNotEmpty() }
                ?: track.optString("syncedLyrics").takeIf { it.isNotBlank() }
                    ?.let { LrcParser.parse(it) }
                    ?.takeIf { it.isNotEmpty() }
        }

    private fun headers() = mapOf(
        "User-Agent" to Net.DESKTOP_UA,
        "Accept" to "application/json",
    )
}
