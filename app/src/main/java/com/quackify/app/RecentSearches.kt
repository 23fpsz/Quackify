package com.quackify.app

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.store.AppPrefs
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object RecentSearches {

    private const val PREFS = "quackify.client"
    private const val LEGACY_KEY = "recentSearches"
    private const val URIS_KEY = "recentSearches.uris"
    private const val CACHE_KEY = "recentSearches.cache"
    private const val LIMIT = 10
    private const val QUERY_PREFIX = "query:"

    data class Entry(
        val kind: String,
        val uri: String,
        val title: String,
        val subtitle: String,
        val imageUrl: String,
        val colorHex: String,
    )

    private val state = MutableStateFlow<List<Entry>>(emptyList())

    fun observe(): StateFlow<List<Entry>> = state.asStateFlow()

    fun refresh(context: Context) {
        state.value = read(context)
    }

    fun all(context: Context): List<Entry> = read(context)

    fun record(context: Context, hit: SearchHit) {
        if (hit.uri.isBlank() || hit.title.isBlank()) return
        val cache = cache(context)
        cache.put(hit.uri, encode(hit))
        val next = listOf(hit.uri) + keys(context).filterNot { it == hit.uri }
        write(context, next.take(LIMIT), cache)
    }

    fun recordQuery(context: Context, query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        val key = QUERY_PREFIX + text.lowercase()
        val next = listOf(key) + keys(context).filterNot { it == key || isSameQuery(it, text) }
        write(context, next.take(LIMIT), cache(context))
    }

    fun merge(context: Context, hits: List<SearchHit>) {
        val remote = hits.filter { it.uri.isNotBlank() && it.title.isNotBlank() }
        if (remote.isEmpty()) return
        val cache = cache(context)
        remote.forEach { cache.put(it.uri, encode(it)) }
        val uris = remote.map { it.uri }
        val next = (uris + keys(context).filterNot { it in uris }).take(LIMIT)
        write(context, next, cache)
    }

    fun remove(context: Context, key: String) {
        if (key.isBlank()) return
        write(context, keys(context).filterNot { it == key || isSameQuery(it, key) }, cache(context))
    }

    fun promote(context: Context, key: String) {
        if (key.isBlank()) return
        val keys = keys(context)
        val index = keys.indexOfFirst { it == key || isSameQuery(it, key) }
        if (index <= 0) return
        val next = ArrayList(keys)
        next.add(0, next.removeAt(index))
        write(context, next, cache(context))
    }

    fun clear(context: Context) {
        write(context, emptyList(), JSONObject())
    }

    private fun read(context: Context): List<Entry> {
        migrate(context)
        val cache = cache(context)
        val out = ArrayList<Entry>()
        for (key in keys(context)) {
            if (key.startsWith(QUERY_PREFIX)) {
                out += Entry(SearchKind.QUERY.name, "", key.removePrefix(QUERY_PREFIX), "", "", "")
                continue
            }
            val entry = cache.optJSONObject(key)?.let { decode(key, it) } ?: continue
            if (entry.title.isNotBlank()) out += entry
        }
        return out
    }

    private fun write(context: Context, keys: List<String>, cache: JSONObject) {
        val kept = JSONObject()
        keys.filterNot { it.startsWith(QUERY_PREFIX) }.forEach { key ->
            cache.optJSONObject(key)?.let { kept.put(key, it) }
        }
        prefs(context).edit()
            .putString(urisKey(context), JSONArray(keys).toString())
            .putString(cacheKey(context), kept.toString())
            .apply()
        state.value = read(context)
    }

    private fun keys(context: Context): List<String> {
        val raw = prefs(context).getString(urisKey(context), null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun cache(context: Context): JSONObject {
        val raw = prefs(context).getString(cacheKey(context), null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun migrate(context: Context) {
        val prefs = prefs(context)
        if (prefs.getString(urisKey(context), null) != null) return
        val legacy = prefs.getString(LEGACY_KEY, null) ?: return
        val array = runCatching { JSONArray(legacy) }.getOrNull() ?: return
        val cache = JSONObject()
        val keys = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val title = node.optString("title")
            if (title.isBlank()) continue
            val uri = node.optString("uri")
            keys += if (uri.isBlank()) QUERY_PREFIX + title.lowercase() else uri
            if (uri.isNotBlank()) {
                cache.put(
                    uri,
                    JSONObject()
                        .put("kind", node.optString("kind"))
                        .put("title", title)
                        .put("subtitle", node.optString("subtitle"))
                        .put("image", node.optString("image"))
                        .put("color", node.optString("color"))
                )
            }
        }
        prefs.edit()
            .putString(urisKey(context), keys.take(LIMIT).toString())
            .putString(cacheKey(context), cache.toString())
            .remove(LEGACY_KEY)
            .apply()
    }

    private fun isSameQuery(storedKey: String, target: String): Boolean {
        if (!storedKey.startsWith(QUERY_PREFIX)) return false
        val text = storedKey.removePrefix(QUERY_PREFIX)
        return text.equals(target.trim().removePrefix(QUERY_PREFIX), ignoreCase = true)
    }

    private fun encode(hit: SearchHit): JSONObject = JSONObject()
        .put("kind", hit.kind.name)
        .put("title", hit.title)
        .put("subtitle", hit.subtitle)
        .put("image", hit.imageUrl)
        .put("color", hit.colorHex)

    private fun decode(uri: String, node: JSONObject): Entry = Entry(
        kind = node.optString("kind"),
        uri = uri,
        title = node.optString("title"),
        subtitle = node.optString("subtitle"),
        imageUrl = node.optString("image"),
        colorHex = node.optString("color"),
    )

    private fun urisKey(context: Context): String = "$URIS_KEY.${account(context)}"

    private fun cacheKey(context: Context): String = "$CACHE_KEY.${account(context)}"

    private fun account(context: Context): String =
        EncoreAuth.activeAccountId(context).ifBlank { "local" }

    private fun prefs(context: Context) = AppPrefs.prefs(context, PREFS)
}
