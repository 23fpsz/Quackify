package com.spotiduck.backend.library

import android.content.Context
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.backend.core.log.Logs
import org.json.JSONArray
import org.json.JSONObject
import com.spotiduck.backend.core.net.GraphQl
import com.spotiduck.backend.core.net.Pathfinder
import com.spotiduck.backend.core.net.ClientToken
import com.spotiduck.backend.core.net.Net
import com.spotiduck.backend.model.PlaylistChoice

private val log = Logs.of(Logs.Module.PLAYLIST)

object PlaylistSource {

    const val OP_EDITABLE = "editablePlaylists"
    const val OP_ADD_TRACK = "addToPlaylist"

    private const val BASE = "https://spclient.wg.spotify.com/playlist/v2"

    private const val UPLOAD = "https://image-upload.spotify.com/v4"

    private const val APP_VERSION = "9.1.92.3058"

    private suspend fun headers(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${SpotifyAuth.token(context)}")
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

    suspend fun editable(context: Context, trackUri: String): List<PlaylistChoice> {
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("editable: no token")
            return emptyList()
        }
        val uris = JSONArray()
        if (trackUri.isNotBlank()) uris.put(trackUri)
        val variables = JSONObject()
            .put("offset", 0)
            .put("limit", 50)
            .put("textFilter", "")
            .put("folderUri", JSONObject.NULL)
            .put("uris", uris)
            .toString()
        val json = Pathfinder.queryPost(context, token, OP_EDITABLE, variables) ?: return emptyList()
        val page = json.optJSONObject("data")
            ?.optJSONObject("me")
            ?.optJSONObject("editablePlaylists")
        if (page == null) {
            log.w("editable: no data.me.editablePlaylists, keys=" +
                    json.optJSONObject("data")?.keys()?.asSequence()?.toList()
            )
            return emptyList()
        }
        val items = page.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<PlaylistChoice>(items.length())
        var skipped = 0
        for (i in 0 until items.length()) {
            val entry = items.optJSONObject(i) ?: continue
            val wrapper = entry.optJSONObject("item") ?: continue
            val data = wrapper.optJSONObject("data") ?: continue
            val uri = data.optString("uri")
            val name = data.optString("name")
            val type = data.optString("__typename")
            if (type != "Playlist" || !uri.startsWith("spotify:playlist:") || name.isBlank()) {
                skipped += 1
                continue
            }
            out += PlaylistChoice(
                uri = uri,
                name = name,
                imageUrl = GraphQl.largestSource(
                    data.optJSONObject("images")?.optJSONArray("items")?.optJSONObject(0),
                ),
            )
        }
        log.i("editable: ${out.size} playlist(s), $skipped skipped (folders and pseudo playlists " +
                "cannot hold a track) for $trackUri"
        )
        return out
    }

    suspend fun defaultName(context: Context): String {
        val taken = editable(context, "").size
        val name = "My Playlist #${taken + 1}"
        log.i("defaultName: $taken writable playlist(s), picking '$name'")
        return name
    }

    suspend fun create(context: Context, name: String, trackUri: String): String? {
        val token = SpotifyAuth.token(context)
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
        return ok
    }

    suspend fun delete(context: Context, playlistUri: String): Boolean {
        if (id(playlistUri).isBlank()) return false
        log.i("delete $playlistUri via the rootlist, there is no DELETE for a playlist")
        val ok = Rootlist.remove(context, playlistUri)
        log.i("delete $playlistUri ok=$ok")
        return ok
    }

    suspend fun revision(context: Context, playlistUri: String): String? {
        val playlist = id(playlistUri)
        if (playlist.isBlank()) return null
        val response = Net.get(
            "$BASE/playlist/$playlist?decorate=revision",
            headers(context),
            "playlist/revision",
        ) ?: return null
        val revision = response.json()?.optString("revision").orEmpty()
        log.i("revision $playlistUri -> '$revision' http=${response.code}")
        return revision.takeIf { it.isNotBlank() }
    }

    suspend fun moveTrack(context: Context, playlistUri: String, from: Int, to: Int): Boolean {
        val playlist = id(playlistUri)
        if (playlist.isBlank() || from < 0 || to < 0 || from == to) {
            log.w("moveTrack: bad input '$playlistUri' from=$from to=$to")
            return false
        }
        val base = revision(context, playlistUri)
        if (base.isNullOrBlank()) {
            log.w("moveTrack: no revision, an index op is rejected without baseRevision")
            return false
        }
        val op = JSONObject().put("kind", "MOV").put(
            "mov",
            JSONObject()
                .put("fromIndex", from)
                .put("toIndex", to)
                .put("length", 1)
                .put("items", JSONArray()),
        )
        val delta = JSONObject()
            .put("baseRevision", base)
            .put(
                "deltas",
                JSONArray().put(
                    JSONObject()
                        .put("ops", JSONArray().put(op))
                        .put(
                            "info",
                            JSONObject().put("source", JSONObject().put("client", "WEBPLAYER")),
                        ),
                ),
            )
            .put("wantResultingRevisions", false)
            .put("wantSyncResult", false)
            .put("nonces", JSONArray())
            .toString()
        val response = Net.postJson(
            "$BASE/playlist/$playlist/changes",
            delta,
            protoHeaders(context),
            "playlist/moveTrack",
        )
        val ok = response?.ok == true
        log.i("moveTrack $playlistUri $from -> $to ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        return ok
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
        val token = SpotifyAuth.token(context)
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
        val ok = payload != null && errors == 0
        log.i("addTrack $trackUri -> $playlistUri ok=$ok errors=$errors " +
                "keys=${data?.keys()?.asSequence()?.toList()}"
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }
}
