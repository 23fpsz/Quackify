package com.quackify.backend.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.model.*
import org.json.JSONArray
import org.json.JSONObject
import com.quackify.backend.core.net.GraphQl
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.net.plainText

private val log = Logs.of(Logs.Module.HOME)

object HomeFeed {

    const val SHORTS = "HomeShortsSectionData"
    const val GENERIC = "HomeGenericSectionData"
    const val BASELINE = "HomeFeedBaselineSectionData"
    const val RECENT = "HomeRecentlyPlayedSectionData"

    suspend fun load(context: Context): List<HomeSection> {
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("no token, cannot load the home feed")
            return emptyList()
        }
        // The tap truncates the captured variables at 400 chars; a cut string
        // must not take the home screen down with an uncaught JSONException.
        val captured = Pathfinder.variablesOf("home")
        val base = runCatching { JSONObject(captured) }.getOrNull()
        if (base == null && captured.isNotBlank()) {
            log.w("the captured home variables do not parse, running with the defaults")
        }
        val variables = (base ?: JSONObject())
            .put("sp_t", Pathfinder.sessionToken())
            .toString()
        val json = Pathfinder.query(
            context = context,
            token = token,
            operation = "home",
            variables = variables,
        ) ?: return emptyList()
        val sections = withContext(Dispatchers.Default) { parse(json) }
        val colored = sections.sumOf { section -> section.items.count { it.colorHex.isNotBlank() } }
        log.i("parsed ${sections.size} sections, ${sections.sumOf { it.items.size }} items, $colored with extracted color")
        sections.take(6).forEach { s ->
            log.i("  ${s.kind} '${s.title}' -> ${s.items.size} items")
            s.items.take(2).forEach { i ->
                log.i("     ${i.kind} '${i.title}' / '${i.subtitle}' img=${i.imageUrl.isNotBlank()} color=${i.colorHex.ifBlank { "-" }}")
            }
        }
        return sections
    }

    private fun SpotifyAuthToken(context: Context): String =
        SpotifyAuth.token(context)

    fun parse(json: JSONObject): List<HomeSection> {
        val array = json.optJSONObject("data")
            ?.optJSONObject("home")
            ?.optJSONObject("sectionContainer")
            ?.optJSONObject("sections")
            ?.optJSONArray("items")
            ?: return emptyList()
        val out = ArrayList<HomeSection>(array.length())
        for (i in 0 until array.length()) {
            val section = array.optJSONObject(i) ?: continue
            val data = section.optJSONObject("data")
            val kind = data?.optString("__typename").orEmpty()
            val title = label(data?.optJSONObject("title"))
                ?: label(data?.optJSONObject("sectionTitle"))
                ?: label(section.optJSONObject("sectionTitle"))
                ?: ""
            val items = section.optJSONObject("sectionItems")?.optJSONArray("items")
                ?: continue
            val parsed = (0 until items.length()).mapNotNull { item(items.optJSONObject(it)) }
            if (parsed.isEmpty()) continue
            out += HomeSection(
                id = section.optString("uri"),
                title = title,
                kind = kind,
                items = parsed,
            )
        }
        return out
    }

    private fun item(node: JSONObject?): HomeItem? {
        if (node == null) return null
        val content = node.optJSONObject("content") ?: node
        val entity = content.optJSONObject("data") ?: node.optJSONObject("data")
        val uri = content.optString("uri").ifBlank { node.optString("uri") }
        if (uri.isBlank()) return null
        val kind = entity?.optString("__typename")
            ?: content.optString("__typename").takeIf { it != "UnknownType" }
            ?: ""
        val title = nameOf(entity, uri)
        if (title.isBlank() && uri !in COLLECTION_URIS) return null
        return HomeItem(
            uri = uri,
            title = title.ifBlank { collectionLabel(uri) },
            subtitle = subtitleOf(entity),
            imageUrl = imageOf(entity),
            kind = kind,
            colorHex = colorOf(entity),
        )
    }

    private fun nameOf(entity: JSONObject?, uri: String): String {
        if (entity == null) return collectionLabel(uri)
        val raw = entity.optString("name").takeIf { it.isNotBlank() }
            ?: entity.optString("title").takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("profile")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: return ""
        return raw.plainText()
    }

    private fun subtitleOf(entity: JSONObject?): String {
        if (entity == null) return ""
        val raw = artists(entity)?.takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("owner")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("publisher")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("podcast")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: entity.optString("description").takeIf { it.isNotBlank() }
            ?: return ""
        return raw.plainText().take(80)
    }

    private fun artists(entity: JSONObject): String? {
        val items = entity.optJSONObject("artists")?.optJSONArray("items") ?: return null
        val names = ArrayList<String>(items.length())
        for (i in 0 until items.length()) {
            val a = items.optJSONObject(i) ?: continue
            val name = a.optString("name").ifBlank {
                a.optJSONObject("profile")?.optString("name").orEmpty()
            }
            if (name.isNotBlank()) names += name
        }
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun imageOf(entity: JSONObject?): String = GraphQl.entityImage(entity)

    private fun colorOf(entity: JSONObject?): String = GraphQl.entityColor(entity)

    private fun label(obj: JSONObject?): String? {
        if (obj == null) return null
        val raw = obj.optString("transformedLabel").takeIf { it.isNotBlank() }
            ?: obj.optString("text").takeIf { it.isNotBlank() }
            ?: obj.optString("title").takeIf { it.isNotBlank() }
            ?: return null
        return raw.plainText()
    }

    private val COLLECTION_URIS = setOf(
        "spotify:user:@:collection",
        "spotify:collection:tracks",
    )

    private fun collectionLabel(uri: String): String =
        if (uri in COLLECTION_URIS) "Liked Songs" else ""
}
