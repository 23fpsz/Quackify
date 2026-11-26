package com.quackify.backend.api

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.model.FriendActivityEntry
import com.quackify.backend.model.FriendActivityFeed
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.SOCIAL)

object FriendActivity {

    private const val BUDDYLIST = "https://spclient.wg.spotify.com/presence-view/v1/buddylist"

    suspend fun feed(context: Context): FriendActivityFeed? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("feed: no token")
            return null
        }
        val response = Net.get(BUDDYLIST, headers(token), "friend-activity/feed")
        if (response == null || !response.ok) {
            log.w("feed http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}")
            return null
        }
        val root = response.json() ?: return null
        val raw = root.optJSONArray("friends") ?: JSONArray()
        val entries = (0 until raw.length()).mapNotNull { parse(raw.optJSONObject(it)) }
        log.i("feed ${entries.size} friend(s)")
        return FriendActivityFeed(entries)
    }

    private fun parse(node: JSONObject?): FriendActivityEntry? {
        if (node == null) return null
        val user = node.optJSONObject("user") ?: return null
        val track = node.optJSONObject("track") ?: return null
        val userName = user.optString("name")
        val trackUri = track.optString("uri")
        if (userName.isBlank() || trackUri.isBlank()) return null
        val artistUris = ArrayList<String>(4)
        val artistNames = ArrayList<String>(4)
        track.optJSONObject("artist")?.let { one ->
            val uri = one.optString("uri")
            if (uri.isNotBlank()) {
                artistUris.add(uri)
                artistNames.add(one.optString("name"))
            }
        }
        track.optJSONArray("artists")?.let { many ->
            (0 until many.length()).forEach { index ->
                many.optJSONObject(index)?.let { one ->
                    val uri = one.optString("uri")
                    if (uri.isNotBlank() && uri !in artistUris) {
                        artistUris.add(uri)
                        artistNames.add(one.optString("name"))
                    }
                }
            }
        }
        return FriendActivityEntry(
            userUri = user.optString("uri"),
            userName = userName,
            userAvatarUrl = user.optString("imageUrl"),
            trackUri = trackUri,
            trackName = track.optString("name"),
            trackImageUrl = track.optString("imageUrl"),
            albumUri = track.optJSONObject("album")?.optString("uri").orEmpty(),
            albumName = track.optJSONObject("album")?.optString("name").orEmpty(),
            artistUris = artistUris,
            artistNames = artistNames,
            contextUri = track.optJSONObject("context")?.optString("uri").orEmpty(),
            contextName = track.optJSONObject("context")?.optString("name").orEmpty(),
            timestampMs = node.optLong("timestamp", 0L),
        )
    }

    private fun headers(token: String): Map<String, String> = buildMap {
        put("Authorization", "Bearer $token")
        put("Accept", "application/json")
        put("accept-language", "en")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
    }
}
