package com.quackify.backend.library

import android.content.Context
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net
import com.quackify.backend.model.LyricLine
import com.quackify.backend.model.TrackLyrics
import org.json.JSONObject

private val log = Logs.of(Logs.Module.LYRICS)

object LyricsSource {

    private const val SPCLIENT = "https://spclient.wg.spotify.com"

    suspend fun lyrics(context: Context, trackUri: String): TrackLyrics? {
        val id = trackUri.substringAfterLast(":", "")
        if (!trackUri.startsWith("spotify:track:") || id.isBlank()) return null
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("lyrics: no token")
            return null
        }
        val url = "$SPCLIENT/color-lyrics/v2/track/$id" +
            "?format=json&vocalRemoval=false&market=from_token"
        val response = Net.get(url, spHeaders(context), "lyrics") ?: return null
        if (!response.ok) {
            log.w("lyrics $id http ${response.code}")
            return null
        }
        val node = response.json()?.optJSONObject("lyrics") ?: run {
            log.i("lyrics $id none")
            return null
        }
        val lines = node.optJSONArray("lines") ?: return null
        val parsed = (0 until lines.length()).mapNotNull { i ->
            val line = lines.optJSONObject(i) ?: return@mapNotNull null
            val text = line.optString("words").trim()
            if (text.isBlank()) null
            else LyricLine(
                startTimeMs = line.optString("startTimeMs").toLongOrNull() ?: 0L,
                text = text,
            )
        }
        if (parsed.isEmpty()) return null
        val synced = node.optString("syncType") == "LINE_SYNCED"
        val provider = node.optString("provider")
        log.i("lyrics $id lines=${parsed.size} synced=$synced provider='$provider'")
        return TrackLyrics(lines = parsed, provider = provider, synced = synced)
    }

    private suspend fun spHeaders(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${SpotifyAuth.token(context)}")
        put("Accept", "application/json")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }
}
