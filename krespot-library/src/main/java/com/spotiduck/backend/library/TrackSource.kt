package com.spotiduck.backend.library

import android.content.Context
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.core.net.Pathfinder
import com.spotiduck.backend.model.TrackRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.TRACKS)

object TrackSource {

    private const val OP_DECORATE = "decorateContextTracks"
    private const val BATCH = 50

    suspend fun decorate(context: Context, uris: List<String>): Map<String, TrackRow> {
        val unique = uris.filter { it.startsWith("spotify:track:") }.distinct()
        if (unique.isEmpty()) return emptyMap()
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("decorate: no token")
            return emptyMap()
        }
        val chunks = unique.chunked(BATCH)
        val answers = coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    val variables = JSONObject().put("uris", JSONArray(chunk)).toString()
                    Pathfinder.queryPost(context, token, OP_DECORATE, variables)?.let { json ->
                        json.optJSONObject("data")?.optJSONArray("tracks")?.let { chunk to it }
                    }
                }
            }.awaitAll()
        }
        val out = LinkedHashMap<String, TrackRow>()
        for ((chunk, tracks) in answers.filterNotNull()) {
            for (i in 0 until tracks.length()) {
                val node = tracks.optJSONObject(i) ?: continue
                val row = track(node, chunk.getOrNull(i).orEmpty()) ?: continue
                out[row.uri] = row
            }
        }
        log.i("decorate ${out.size} of ${unique.size} track(s) in ${chunks.size} batch(es)")
        return out
    }
}
