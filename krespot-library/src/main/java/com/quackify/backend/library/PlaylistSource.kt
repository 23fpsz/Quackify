/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.library

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import org.json.JSONArray
import org.json.JSONObject
import com.quackify.backend.core.net.GraphQl
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net
import com.quackify.backend.model.PlaylistChoice

private val log = Logs.of(Logs.Module.PLAYLIST)

object PlaylistSource {

    const val OP_EDITABLE = "editablePlaylists"
    const val OP_ADD_TRACK = "addToPlaylist"

    const val OP_MOVE_ITEMS = "moveItemsInPlaylist"

    const val DUMMY_TRACK_URI = "spotify:track:0000000000000000000000"

    private const val BASE = "https://spclient.wg.spotify.com/playlist/v2"

    private const val UPLOAD = "https://image-upload.spotify.com/v4"

    private const val APP_VERSION = "9.1.92.3058"

    /** How long a fetched song count stays fresh; a sheet reopen inside it never re-reads. */
    private const val COUNT_TTL_MS = 5 * 60_000L

    /** uri -> (fetched at, count); -1 counts are not cached. */
    private val countCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()

    private suspend fun headers(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("app-platform", "WebPlayer")
        put("spotify-app-version", APP_VERSION)
        put("Origin", "https://open.spotify.com")
        put("Referer", "https://open.spotify.com/")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }

    private suspend fun protoHeaders(context: Context): Map<String, String> =
        headers(context) + mapOf("Content-Type" to "application/json;charset=UTF-8")

    suspend fun editable(
        context: Context,
        trackUri: String,
        folderUri: String? = null,
    ): List<PlaylistChoice> {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("editable: no token")
            return emptyList()
        }
        val uris = JSONArray()
        uris.put(trackUri.ifBlank { DUMMY_TRACK_URI })
        val out = ArrayList<PlaylistChoice>()
        var skipped = 0
        var offset = 0
        while (offset < 500) {
            val variables = JSONObject()
                .put("offset", offset)
                .put("limit", 50)
                .put("textFilter", "")
                .put("folderUri", folderUri ?: JSONObject.NULL)
                .put("uris", uris)
                .toString()
            val json = Pathfinder.queryPost(context, token, OP_EDITABLE, variables) ?: break
            val page = json.optJSONObject("data")
                ?.optJSONObject("me")
                ?.optJSONObject("editablePlaylists")
            if (page == null) {
                log.w("editable: no data.me.editablePlaylists, keys=" +
                        json.optJSONObject("data")?.keys()?.asSequence()?.toList()
                )
                break
            }
            val items = page.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val wrapper = entry.optJSONObject("item") ?: continue
                val data = wrapper.optJSONObject("data") ?: continue
                val contains = entry.optBoolean("curates", false)
                val parsed = when (wrapper.optString("__typename")) {
                    "PlaylistResponseWrapper" -> {
                        val uri = data.optString("uri")
                        val name = data.optString("name")
                        if (uri.startsWith("spotify:playlist:") && name.isNotBlank()) {
                            PlaylistChoice(
                                uri = uri,
                                name = name,
                                imageUrl = GraphQl.largestSource(
                                    data.optJSONObject("images")?.optJSONArray("items")?.optJSONObject(0),
                                ),
                                containsTrack = contains,
                            )
                        } else null
                    }
                    "LibraryPseudoPlaylistResponseWrapper" -> {
                        if (data.optString("uri") == PlaylistChoice.LIKED_SONGS_URI) {
                            PlaylistChoice(
                                uri = PlaylistChoice.LIKED_SONGS_URI,
                                name = data.optString("name").ifBlank { "Liked Songs" },
                                imageUrl = GraphQl.largestSource(data.optJSONObject("image")),
                                kind = PlaylistChoice.Kind.LIKED_SONGS,
                                containsTrack = contains,
                            )
                        } else null
                    }
                    "LibraryFolderResponseWrapper" -> {
                        val uri = data.optString("uri")
                        val name = data.optString("name")
                        if (uri.isNotBlank() && name.isNotBlank()) {
                            PlaylistChoice(uri = uri, name = name, kind = PlaylistChoice.Kind.FOLDER)
                        } else null
                    }
                    else -> null
                }
                if (parsed != null) out += parsed else skipped += 1
            }
            offset += items.length()
            val total = page.optInt("totalCount", offset)
            if (offset >= total || items.length() < 50) break
        }
        log.i(
            "editable: ${out.count { it.kind == PlaylistChoice.Kind.PLAYLIST }} playlist(s), " +
                    "${out.count { it.kind == PlaylistChoice.Kind.FOLDER }} folder(s), " +
                    "${out.count { it.kind == PlaylistChoice.Kind.LIKED_SONGS }} liked songs, " +
                    "$skipped skipped, ${out.count { it.containsTrack }} already hold the track" +
                    (folderUri?.let { " inside $it" } ?: "") + " for $trackUri"
        )
        return out
    }

    /**
     * How many tracks a collection holds, for the add-to-playlist sheet's rows.
     * A playlist reads the playlist/v2 length decoration in one small GET; the
     * pinned Liked Songs pseudo playlist has no playlist/v2 entry, so its count
     * comes from the library page's PseudoPlaylist node (a cached read when the
     * library was already listed). Answers sit in [countCache] for [COUNT_TTL_MS]
     * so paging in and out of folders never re-reads a playlist twice.
     */
    suspend fun trackCount(context: Context, uri: String): Int {
        if (uri == PlaylistChoice.LIKED_SONGS_URI) return likedCount(context)
        val playlist = id(uri)
        if (playlist.isBlank()) return -1
        countCache[uri]?.let { (at, count) ->
            if (System.currentTimeMillis() - at < COUNT_TTL_MS) {
                log.i("trackCount $uri -> $count (cached)")
                return count
            }
        }
        val response = Net.get(
            "$BASE/playlist/$playlist?decorate=revision,length",
            headers(context),
            "playlist/length",
        ) ?: return -1
        val json = response.json()
        val count = json?.optInt("length", -1) ?: -1
        log.i("trackCount $uri -> $count http=${response.code}")
        if (count >= 0) countCache[uri] = System.currentTimeMillis() to count
        return count
    }

    private suspend fun likedCount(context: Context): Int {
        val page = LibraryPageSource.page(context) ?: return -1
        val count = page.items
            .firstOrNull { it.uri == PlaylistChoice.LIKED_SONGS_URI && it.count > 0 }
            ?.count ?: -1
        log.i("trackCount liked songs -> $count of ${page.items.size} library row(s)")
        return count
    }

    /** Keeps a cached count honest after a write the app itself performed. */
    private fun bumpCount(playlistUri: String, delta: Int) {
        countCache[playlistUri]?.let { (at, count) ->
            countCache[playlistUri] = at to (count + delta).coerceAtLeast(0)
        }
    }

    suspend fun defaultName(context: Context): String {
        val taken = editable(context, "").count { it.kind == PlaylistChoice.Kind.PLAYLIST }
        val name = "My Playlist #${taken + 1}"
        log.i("defaultName: $taken writable playlist(s), picking '$name'")
        return name
    }

    suspend fun create(context: Context, name: String, trackUri: String): String? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("create: no token")
            return null
        }
        val delta = JSONObject()
            .put(
                "ops",
                JSONArray().put(
                    JSONObject()
                        .put("kind", "UPDATE_LIST_ATTRIBUTES")
                        .put(
                            "updateListAttributes",
                            JSONObject().put(
                                "newAttributes",
                                JSONObject().put(
                                    "values",
                                    JSONObject().put("name", name),
                                ),
                            ),
                        ),
                ),
            )
            .toString()
        val response = Net.postJson(
            "$BASE/playlist",
            delta,
            protoHeaders(context),
            "playlist/create",
        )
        val uri = response?.json()?.optString("uri").orEmpty()
        if (response?.ok != true || uri.isBlank()) {
            log.w("create '$name' http=${response?.code ?: -1} type=${response?.contentType.orEmpty()} " +
                    "bytes=${response?.bytes ?: -1} ${response?.body?.take(200).orEmpty()}"
            )
            return null
        }
        log.i("create '$name' -> $uri")
        Rootlist.add(context, uri)
        if (trackUri.isNotBlank()) addTrack(context, uri, trackUri)
        return uri
    }

    private fun id(playlistUri: String): String =
        playlistUri.removePrefix("spotify:playlist:").takeIf { it != playlistUri }.orEmpty()

    suspend fun removeTrack(context: Context, playlistUri: String, trackUri: String): Boolean {
        val playlist = id(playlistUri)
        if (playlist.isBlank() || trackUri.isBlank()) {
            log.w("removeTrack: bad input '$playlistUri' '$trackUri'")
            return false
        }
        val delta = JSONObject()
            .put(
                "deltas",
                JSONArray().put(
                    JSONObject()
                        .put(
                            "ops",
                            JSONArray().put(
                                JSONObject()
                                    .put("kind", "REM")
                                    .put(
                                        "rem",
                                        JSONObject()
                                            .put(
                                                "items",
                                                JSONArray().put(JSONObject().put("uri", trackUri)),
                                            )
                                            .put("itemsAsKey", true),
                                    ),
                            ),
                        )
                        .put(
                            "info",
                            JSONObject().put("source", JSONObject().put("client", "WEBPLAYER")),
                        ),
                ),
            )
            .toString()
        val response = Net.postJson(
            "$BASE/playlist/$playlist/changes",
            delta,
            protoHeaders(context),
            "playlist/removeTrack",
        )
        val ok = response?.ok == true
        log.i("removeTrack $trackUri from $playlistUri ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        if (ok) bumpCount(playlistUri, -1)
        return ok
    }

    suspend fun delete(context: Context, playlistUri: String): Boolean {
        if (id(playlistUri).isBlank()) return false
        log.i("delete $playlistUri via the rootlist, there is no DELETE for a playlist")
        val ok = Rootlist.remove(context, playlistUri)
        log.i("delete $playlistUri ok=$ok")
        return ok
    }

    suspend fun moveItems(
        context: Context,
        playlistUri: String,
        uids: List<String>,
        position: Position,
    ): Boolean {
        val playlist = id(playlistUri)
        if (playlist.isBlank() || uids.isEmpty()) {
            log.w("moveItems: bad input '$playlistUri' uids=${uids.size}")
            return false
        }
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("moveItems: no token")
            return false
        }
        val newPosition = JSONObject().put("fromUid", JSONObject.NULL)
        when (position) {
            Position.TOP -> newPosition.put("moveType", "TOP_OF_PLAYLIST")
            Position.BOTTOM -> newPosition.put("moveType", "BOTTOM_OF_PLAYLIST")
            is Position.BeforeUid -> newPosition.put("moveType", "BEFORE_UID").put("fromUid", position.uid)
            is Position.AfterUid -> newPosition.put("moveType", "AFTER_UID").put("fromUid", position.uid)
        }
        val variables = JSONObject()
            .put("playlistUri", playlistUri)
            .put("uids", JSONArray(uids))
            .put("newPosition", newPosition)
            .toString()
        val json = Pathfinder.queryPost(context, token, OP_MOVE_ITEMS, variables, fallbackToGet = false)
        val payload = json?.optJSONObject("data")?.optJSONObject("moveItemsInPlaylist")
        val errors = json?.optJSONArray("errors")?.length() ?: 0
        val ok = payload != null && errors == 0 &&
            payload.optString("__typename") != "GenericError"
        log.i("moveItems ${uids.size} uid(s) $position in $playlistUri ok=$ok errors=$errors")
        return ok
    }

    sealed interface Position {
        object TOP : Position
        object BOTTOM : Position
        data class BeforeUid(val uid: String) : Position
        data class AfterUid(val uid: String) : Position
    }

    suspend fun hasCustomCover(context: Context, playlistUri: String): Boolean {
        val playlist = id(playlistUri)
        if (playlist.isBlank()) return false
        val response = Net.get(
            "$BASE/playlist/$playlist?decorate=revision,length,attributes,timestamp",
            headers(context),
            "playlist/attributes",
        ) ?: return false
        val attributes = response.json()?.optJSONObject("attributes") ?: return false
        val custom = attributes.has("picture")
        log.i("hasCustomCover $playlistUri -> $custom " +
                "keys=${attributes.keys().asSequence().toList()}"
        )
        return custom
    }

    suspend fun uploadCover(context: Context, playlistUri: String, jpeg: ByteArray): String? {
        val playlist = id(playlistUri)
        if (playlist.isBlank() || jpeg.isEmpty()) {
            log.w("uploadCover: bad input '$playlistUri' ${jpeg.size}B")
            return null
        }
        val upload = Net.postBytes(
            "$UPLOAD/playlist",
            jpeg,
            "image/jpeg",
            headers(context),
            "playlist/coverUpload",
        )
        val token = upload?.json()?.optString("uploadToken").orEmpty()
        if (upload?.ok != true || token.isBlank()) {
            log.w("uploadCover: upload http=${upload?.code ?: -1} bytes=${jpeg.size} " +
                    upload?.body?.take(200).orEmpty()
            )
            return null
        }
        val register = Net.postJson(
            "$BASE/playlist/$playlist/register-image",
            JSONObject().put("uploadToken", token).toString(),
            protoHeaders(context),
            "playlist/registerImage",
        )
        val picture = register?.json()?.optString("picture").orEmpty()
        if (register?.ok != true || picture.isBlank()) {
            log.w("uploadCover: register http=${register?.code ?: -1} " +
                    register?.body?.take(200).orEmpty()
            )
            return null
        }
        log.i("uploadCover $playlistUri -> ${picture.length}B picture id")
        return picture
    }

    suspend fun edit(
        context: Context,
        playlistUri: String,
        name: String? = null,
        description: String? = null,
        picture: String? = null,
    ): Boolean {
        val playlist = id(playlistUri)
        if (playlist.isBlank()) {
            log.w("edit: bad uri '$playlistUri'")
            return false
        }
        if (name != null && name.isBlank()) {
            log.w("edit: refusing an empty name, the web player throws on it")
            return false
        }
        val values = JSONObject()
        val noValue = JSONArray()
        name?.let { values.put("name", it.take(100)) }
        if (description != null) {
            if (description.isBlank()) noValue.put("LIST_DESCRIPTION")
            else values.put("description", description.take(300))
        }
        if (picture != null) {
            if (picture.isBlank()) noValue.put("LIST_PICTURE")
            else values.put("picture", picture)
        }
        if (values.length() == 0 && noValue.length() == 0) {
            log.i("edit $playlistUri: nothing changed")
            return true
        }
        val delta = JSONObject()
            .put(
                "deltas",
                JSONArray().put(
                    JSONObject()
                        .put(
                            "ops",
                            JSONArray().put(
                                JSONObject()
                                    .put("kind", "UPDATE_LIST_ATTRIBUTES")
                                    .put(
                                        "updateListAttributes",
                                        JSONObject().put(
                                            "newAttributes",
                                            JSONObject()
                                                .put("values", values)
                                                .put("noValue", noValue),
                                        ),
                                    ),
                            ),
                        )
                        .put(
                            "info",
                            JSONObject().put("source", JSONObject().put("client", "WEBPLAYER")),
                        ),
                ),
            )
            .toString()
        val response = Net.postJson(
            "$BASE/playlist/$playlist/changes",
            delta,
            protoHeaders(context),
            "playlist/edit",
        )
        val ok = response?.ok == true
        log.i("edit $playlistUri values=${values.keys().asSequence().toList()} " +
                "noValue=${(0 until noValue.length()).map { noValue.optString(it) }} ok=$ok " +
                "http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}"
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }

    suspend fun addTrack(context: Context, playlistUri: String, trackUri: String): Boolean {
        if (playlistUri.isBlank() || trackUri.isBlank()) return false
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("addTrack: no token")
            return false
        }
        val variables = JSONObject()
            .put("playlistItemUris", JSONArray().put(trackUri))
            .put("playlistUri", playlistUri)
            .put(
                "newPosition",
                JSONObject().put("moveType", "TOP_OF_PLAYLIST").put("fromUid", JSONObject.NULL),
            )
            .put("interactionId", JSONObject.NULL)
            .toString()
        val json = Pathfinder.queryPost(context, token, OP_ADD_TRACK, variables, fallbackToGet = false)
        val data = json?.optJSONObject("data")
        val payload = data?.optJSONObject("addItemsToPlaylist")
            ?: data?.optJSONObject("AddItemsToPlaylistPayload")
        val errors = json?.optJSONArray("errors")?.length() ?: 0
        val typename = payload?.optString("__typename").orEmpty()
        val ok = payload != null && errors == 0 &&
            typename != "NotFound" && typename != "GenericError"
        log.i("addTrack $trackUri -> $playlistUri ok=$ok errors=$errors " +
                "keys=${data?.keys()?.asSequence()?.toList()}"
        )
        if (ok) {
            LibraryPageSource.invalidate()
            bumpCount(playlistUri, +1)
        }
        return ok
    }
}
