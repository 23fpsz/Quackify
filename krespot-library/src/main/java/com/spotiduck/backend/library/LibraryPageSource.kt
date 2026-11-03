package com.spotiduck.backend.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.core.net.GraphQl
import com.spotiduck.backend.core.net.Pathfinder
import com.spotiduck.backend.core.net.plainText
import com.spotiduck.backend.model.LibraryPage
import com.spotiduck.backend.model.ShelfItem
import com.spotiduck.backend.model.ShelfKind
import org.json.JSONArray
import org.json.JSONObject
import com.spotiduck.backend.core.net.plainText

private val log = Logs.of(Logs.Module.LIBRARY_PAGE)

internal const val PAGE = 50

object LibraryPageSource {

    private const val OP_PAGE = "libraryV3"
    private const val CACHE_MS = 5 * 60_000L

    private data class Cached(val page: LibraryPage, val at: Long)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()

    private val revisions = MutableStateFlow(0)

    val revision: StateFlow<Int> = revisions.asStateFlow()

    fun invalidate() {
        cache.clear()
        revisions.value += 1
        log.i("cache cleared, revision ${revisions.value}")
    }

    private fun cacheKey(order: String?, textFilter: String, folderUri: String?): String =
        "${order.orEmpty()}|$textFilter|${folderUri.orEmpty()}"

    private val FEATURES = JSONArray()
        .put("LIKED_SONGS")
        .put("YOUR_EPISODES_V2")
        .put("PRERELEASES")
        .put("PRERELEASES_V2")
        .put("CLIPS")
        .put("EVENTS")

    suspend fun page(
        context: Context,
        order: String? = null,
        textFilter: String = "",
        folderUri: String? = null,
        offset: Int = 0,
    ): LibraryPage? {
        val key = cacheKey(order, textFilter, folderUri)
        val now = System.currentTimeMillis()
        if (offset == 0) {
            cache[key]?.takeIf { now - it.at < CACHE_MS }?.let {
                log.i("page cache hit '$key' -> ${it.page.items.size}/${it.page.total}")
                return it.page
            }
        }
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("page: no token")
            return null
        }
        val variables = JSONObject()
            .put("order", order ?: JSONObject.NULL)
            .put("textFilter", textFilter)
            .put("features", FEATURES)
            .put("limit", PAGE)
            .put("offset", offset)
            .put("flatten", false)
            .put("expandedFolders", JSONArray())
            .put("folderUri", folderUri ?: JSONObject.NULL)
            .put("includeFoldersWhenFlattening", true)
            .toString()
        val json = Pathfinder.queryPost(context, token, OP_PAGE, variables) ?: return null
        val lib = json.optJSONObject("data")
            ?.optJSONObject("me")
            ?.optJSONObject("libraryV3")
            ?: run {
                log.w("page: no data.me.libraryV3")
                return null
            }
        val items = lib.optJSONArray("items")
        val shelves = ArrayList<ShelfItem>(items?.length() ?: 0)
        for (i in 0 until (items?.length() ?: 0)) {
            shelf(items?.optJSONObject(i))?.let(shelves::add)
        }
        val filters = ArrayList<String>()
        val available = lib.optJSONArray("availableFilters")
        for (i in 0 until (available?.length() ?: 0)) {
            available?.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(filters::add)
        }
        val page = LibraryPage(
            items = shelves,
            filters = filters,
            sort = lib.optJSONObject("selectedSortOrder")?.optString("id").orEmpty(),
            total = lib.optInt("totalCount", shelves.size),
            offset = offset,
            fetched = items?.length() ?: 0,
        )
        log.i("page offset=$offset filter='$textFilter' order=${page.sort} -> " +
                "${shelves.size}/${page.fetched} of ${page.total} filters=$filters " +
                "kinds=${shelves.groupingBy { it.kind }.eachCount()}"
        )
        if (offset == 0) cache[key] = Cached(page, System.currentTimeMillis())
        return page
    }

    suspend fun folder(context: Context, uri: String): LibraryPage? {
        if (uri.isBlank()) return null
        val page = page(context, folderUri = uri)
        log.i("folder $uri -> ${page?.items?.size ?: 0}/${page?.total ?: 0}")
        return page
    }

    private fun shelf(node: JSONObject?): ShelfItem? {
        if (node == null) return null
        val wrapper = node.optJSONObject("item") ?: return null
        val data = wrapper.optJSONObject("data") ?: return null
        val uri = data.optString("uri").ifBlank { wrapper.optString("_uri") }
        if (uri.isBlank()) return null
        val pinned = node.optBoolean("pinned", false)
        val name = data.optString("name").plainText()
        return when (val type = data.optString("__typename")) {
            "PseudoPlaylist" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Playlist • ${data.optInt("count")} songs",
                kind = ShelfKind.LIKED,
                imageUrl = GraphQl.largestSource(data.optJSONObject("image")),
                colorHex = GraphQl.colorHex(data.optJSONObject("image")),
                pinned = pinned,
                count = data.optInt("count"),
            )
            "Playlist" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Playlist • " + (
                    data.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name").orEmpty()
                        .plainText().ifBlank { "Spotify" }
                    ),
                kind = ShelfKind.PLAYLIST,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
            )
            "Album" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Album • " + GraphQl.artistNames(data),
                kind = ShelfKind.ALBUM,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
            )
            "Artist" -> ShelfItem(
                uri = uri,
                title = name.ifBlank { data.optJSONObject("profile")?.optString("name").orEmpty() },
                subtitle = "Artist",
                kind = ShelfKind.ARTIST,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
            )
            "Folder" -> {
                val lists = data.optInt("playlistCount")
                val sub = data.optInt("folderCount")
                val parts = ArrayList<String>(2)
                if (lists > 0) parts += "$lists playlist" + if (lists == 1) "" else "s"
                if (sub > 0) parts += "$sub folder" + if (sub == 1) "" else "s"
                ShelfItem(
                    uri = uri,
                    title = name,
                    subtitle = if (parts.isEmpty()) "Folder" else parts.joinToString(", "),
                    kind = ShelfKind.FOLDER,
                    pinned = pinned,
                    count = lists,
                )
            }
            "Show" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Podcast • " + data.optJSONObject("publisher")?.optString("name").orEmpty(),
                kind = ShelfKind.SHOW,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
            )
            else -> ShelfItem(
                uri = uri,
                title = name.ifBlank { type },
                subtitle = type,
                kind = ShelfKind.UNKNOWN,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
            )
        }
    }
}
