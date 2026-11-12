package com.quackify.backend.library

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.GraphQl
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.net.plainText
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.TrackPage
import com.quackify.backend.model.TrackRow
import org.json.JSONObject
import com.quackify.backend.core.net.plainText

private val log = Logs.of(Logs.Module.COLLECTION)

internal const val LIKED_URI = "spotify:collection:tracks"
private const val LIKED_COVER = "https://misc.scdn.co/liked-songs/liked-songs-640.png"

object CollectionSource {

    private const val OP_LIKED = "fetchLibraryTracks"
    private const val OP_PLAYLIST = "fetchPlaylist"

    fun canonical(uri: String): String =
        if (uri.startsWith("spotify:user:") && uri.endsWith(":collection")) LIKED_URI else uri

    suspend fun collection(context: Context, uri: String): CollectionDetail? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val target = canonical(uri)
        return if (target.startsWith("spotify:collection:")) {
            liked(context, token, target)
        } else {
            playlist(context, token, target)
        }
    }

    suspend fun collectionMore(context: Context, uri: String, offset: Int): TrackPage? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val target = canonical(uri)
        if (target.startsWith("spotify:collection:")) {
            val page = likedPage(context, token, target, offset, PAGE) ?: return null
            return TrackPage(
                rows = likedRows(page),
                total = page.optInt("totalCount", 0),
                offset = offset,
                fetched = page.optJSONArray("items")?.length() ?: 0,
            )
        }
        val playlist = playlistPage(context, token, uri, offset, PAGE) ?: return null
        val content = playlist.optJSONObject("content")
        return TrackPage(
            rows = withSaved(context, playlistRows(content)),
            total = content?.optInt("totalCount", 0) ?: 0,
            offset = offset,
            fetched = content?.optJSONArray("items")?.length() ?: 0,
        )
    }

    private suspend fun liked(context: Context, token: String, uri: String): CollectionDetail? {
        val page = likedPage(context, token, uri, 0, PAGE) ?: return null
        val total = page.optInt("totalCount", 0)
        val rows = likedRows(page)
        log.i("liked $uri -> ${rows.size}/$total")
        return CollectionDetail(
            uri = uri,
            title = "Liked Songs",
            creator = "",
            saveCount = if (total == 1) "1 song" else "$total songs",
            description = "",
            imageUrl = LIKED_COVER,
            colorHex = "#5018F0",
            tracks = rows,
            total = total,
            offset = 0,
            fetched = page.optJSONArray("items")?.length() ?: 0,
        )
    }

    private suspend fun likedPage(
        context: Context,
        token: String,
        uri: String,
        offset: Int,
        limit: Int,
    ): JSONObject? {
        val variables = JSONObject()
            .put("uri", uri)
            .put("offset", offset)
            .put("limit", limit)
            .toString()
        return Pathfinder.queryPost(context, token, OP_LIKED, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("me")
            ?.optJSONObject("library")
            ?.optJSONObject("tracks")
    }

    private fun likedRows(page: JSONObject): List<TrackRow> {
        val items = page.optJSONArray("items")
        val rows = ArrayList<TrackRow>(items?.length() ?: 0)
        for (i in 0 until (items?.length() ?: 0)) {
            val node = items?.optJSONObject(i)?.optJSONObject("track") ?: continue
            track(node.optJSONObject("data"), node.optString("_uri"), savedHint = true)?.let(rows::add)
        }
        return rows
    }

    private suspend fun playlistPage(
        context: Context,
        token: String,
        uri: String,
        offset: Int,
        limit: Int,
    ): JSONObject? {
        val variables = JSONObject()
            .put("uri", uri)
            .put("offset", offset)
            .put("limit", limit)
            .put("enableWatchFeedEntrypoint", false)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        return Pathfinder.queryPost(context, token, OP_PLAYLIST, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("playlistV2")
    }

    private fun playlistRows(content: JSONObject?): List<TrackRow> {
        val items = content?.optJSONArray("items")
        val rows = ArrayList<TrackRow>(items?.length() ?: 0)
        for (i in 0 until (items?.length() ?: 0)) {
            val entry = items?.optJSONObject(i) ?: continue
            val node = entry.optJSONObject("itemV2") ?: continue
            track(node.optJSONObject("data"), "")?.let { rows.add(it.copy(uid = entry.optString("uid"))) }
        }
        return rows
    }

    private suspend fun playlist(context: Context, token: String, uri: String): CollectionDetail? {
        val playlist = playlistPage(context, token, uri, 0, PAGE) ?: return null
        val content = playlist.optJSONObject("content")
        val rows = withSaved(context, playlistRows(content))
        val total = content?.optInt("totalCount", rows.size) ?: rows.size
        val owner = playlist.optJSONObject("ownerV2")?.optJSONObject("data")
        val followers = playlist.optLong("followers", 0L)
        val capabilities = playlist.optJSONObject("currentUserCapabilities")
        val editable = capabilities?.optBoolean("canEditItems", false) == true ||
            capabilities?.optBoolean("canEditMetadata", false) == true
        val detail = CollectionDetail(
            uri = uri,
            title = playlist.optString("name").plainText(),
            creator = owner?.optString("name").orEmpty(),
            creatorAvatarUrl = GraphQl.userAvatar(owner),
            saveCount = when {
                followers > 0L -> {
                    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(followers)
                    if (followers == 1L) "1 save" else "$formatted saves"
                }
                total == 1 -> "1 song"
                total > 0 -> "$total songs"
                else -> ""
            },
            description = playlist.optString("description").plainText(),
            imageUrl = GraphQl.entityImage(playlist),
            colorHex = GraphQl.visualColor(playlist).ifBlank { GraphQl.entityColor(playlist) },
            tracks = rows,
            total = total,
            offset = 0,
            fetched = content?.optJSONArray("items")?.length() ?: 0,
            editable = editable,
        )
        log.i("playlist $uri '${detail.title}' -> ${rows.size}/$total owner='${detail.creator}' " +
                "followers=$followers editable=$editable saved=${rows.count { it.saved == true }} " +
                "video=${rows.count { it.video }}"
        )
        return detail
    }
}
