
package com.quackify.app

import android.content.Context
import com.quackify.compose.playlist.TrackItem
import org.json.JSONArray
import org.json.JSONObject

data class PlayedTrack(
    val item: TrackItem,
    val playedAtMs: Long,
    val plays: Int = 1,
)

object PlayHistory {

    private const val PREFS = "quackify.client"
    private const val KEY = "playHistory"
    private const val LIMIT = 200

    fun all(context: Context): List<PlayedTrack> {
        val raw = prefs(context).getString(KEY, null)
            ?: return emptyList()

        val array = runCatching {
            JSONArray(raw)
        }.getOrNull() ?: return emptyList()

        val out = ArrayList<PlayedTrack>(array.length())

        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue

            val uri = node.optString("uri")
            if (!uri.startsWith("spotify:track:") && !uri.startsWith("spotify:episode:")) continue

            val candidate = PlayedTrack(
                item = TrackItem(
                    title = node.optString("title"),
                    artist = node.optString("artist"),
                    uri = uri,
                    imageUrl = node.optString("image")
                        .takeIf { it.isNotBlank() },
                    albumUri = node.optString("albumUri"),
                    artistUri = node.optString("artistUri"),
                    isExplicit = node.optBoolean("explicit", false),
                    durationMs = node.optLong("durationMs", 0L),
                ),
                playedAtMs = node.optLong("at", 0L),
                plays = node.optInt("plays", 1).coerceAtLeast(1),
            )

            // The list is stored newest-first: collapse re-plays of the same
            // song (different URIs of a re-release count as the same song)
            // into the most recent entry.
            if (out.none { sameSong(it.item, candidate.item) }) {
                out += candidate
            }
        }

        return out
    }

    fun record(
        context: Context,
        track: TrackItem,
        playedAtMs: Long,
    ): List<PlayedTrack> {
        if (!track.uri.startsWith("spotify:track:") &&
            !track.uri.startsWith("spotify:episode:")
        ) {
            return all(context)
        }

        val previous = all(context)

        // Keep an already stored cover if this play carries none
        val enriched = if (track.imageUrl.isNullOrBlank()) {
            track.copy(
                imageUrl = previous
                    .firstOrNull { it.item.uri == track.uri }
                    ?.item?.imageUrl
            )
        } else {
            track
        }

        val plays = previous.firstOrNull { sameSong(it.item, track) }?.plays ?: 0
        val next = (
                listOf(PlayedTrack(enriched, playedAtMs, plays + 1)) +
                        previous.filterNot {
                            sameSong(it.item, track)
                        }
                ).take(LIMIT)

        write(context, next)

        return next
    }

    /**
     * Two entries describe the same song when they share the track URI, or
     * when their title and artist match — re-releases and regional variants
     * of a song carry different URIs but identical metadata.
     */
    private fun sameSong(a: TrackItem, b: TrackItem): Boolean {
        if (a.uri.isNotEmpty() && a.uri == b.uri) return true
        return a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
                a.artist.trim().equals(b.artist.trim(), ignoreCase = true)
    }

    private fun write(
        context: Context,
        entries: List<PlayedTrack>,
    ) {
        val array = JSONArray()

        entries.forEach { played ->
            array.put(
                JSONObject()
                    .put("uri", played.item.uri)
                    .put("title", played.item.title)
                    .put("artist", played.item.artist)
                    .put("image", played.item.imageUrl.orEmpty())
                    .put("albumUri", played.item.albumUri)
                    .put("artistUri", played.item.artistUri)
                    .put("explicit", played.item.isExplicit)
                    .put("durationMs", played.item.durationMs)
                    .put("at", played.playedAtMs)
                    .put("plays", played.plays)
            )
        }

        prefs(context)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
}