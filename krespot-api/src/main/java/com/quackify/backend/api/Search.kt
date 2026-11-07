package com.quackify.backend.api

import android.content.Context
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.model.*
import org.json.JSONArray
import org.json.JSONObject
import com.quackify.backend.core.net.GraphQl
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.net.plainText

private val log = Logs.of(Logs.Module.SEARCH)

object Search {

    const val OP_RESULTS = "searchTopResultsList"
    const val OP_SUGGESTIONS = "searchSuggestions"
    const val OP_RECENT = "recentSearches"

    private const val FULL_LIMIT = 50
    private const val SUGGEST_LIMIT = 16

    private fun sectionFilters(): JSONArray = JSONArray().put("GENERIC").put("VIDEO_CONTENT")

    suspend fun query(context: Context, term: String, limit: Int = FULL_LIMIT): SearchResults? {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return null
        val json = call(context, OP_RESULTS, variables(trimmed, limit, prefix = false)) ?: return null
        val results = withSaved(context, parseResults(json, trimmed))
        log.i("search '$trimmed' tracks=${results.tracks.size} artists=${results.artists.size} " +
                "albums=${results.albums.size} playlists=${results.playlists.size} " +
                "sections=${results.sections.map { "${it.title}(${it.items.size})" }} " +
                "chips=${results.chips} totals=${results.totals}"
        )
        return results
    }

    private suspend fun withSaved(context: Context, results: SearchResults): SearchResults {
        val uris = (results.tracks + results.artists + results.albums + results.playlists +
            results.others + results.sections.flatMap { it.items }).map { it.uri }
        val saved = Library.saved(context, uris)
        if (saved.isEmpty()) return results
        fun mark(hit: SearchHit) = if (hit.uri in saved) hit.copy(saved = true) else hit
        return results.copy(
            top = results.top?.let(::mark),
            tracks = results.tracks.map(::mark),
            artists = results.artists.map(::mark),
            albums = results.albums.map(::mark),
            playlists = results.playlists.map(::mark),
            others = results.others.map(::mark),
            sections = results.sections.map { section ->
                section.copy(items = section.items.map(::mark))
            },
        )
    }

    suspend fun suggest(context: Context, term: String, limit: Int = SUGGEST_LIMIT): List<SearchHit> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        val json = call(context, OP_SUGGESTIONS, suggestionVariables(trimmed, limit)) ?: return emptyList()
        val hits = topHits(json).take(limit)
        // The dropdown rows carry hearts now: ask the library which entities are
        // already in it so the first press toggles the right way around.
        val entities = hits.filter { it.kind != SearchKind.QUERY && it.uri.isNotBlank() }
        val saved = if (entities.isEmpty()) emptySet() else Library.saved(context, entities.map { it.uri })
        val marked = if (saved.isEmpty()) hits else hits.map { if (it.uri in saved) it.copy(saved = true) else it }
        log.i("suggest '$trimmed' -> ${hits.size} hits " +
                "queries=${hits.count { it.kind == SearchKind.QUERY }} " +
                "entities=${hits.count { it.kind != SearchKind.QUERY }} " +
                hits.map { it.title }.take(4)
        )
        return marked
    }

    private val SECTION_OPS = mapOf(
        SearchKind.TRACK to Triple("searchTracks", "tracksV2", 20),
        SearchKind.ARTIST to Triple("searchArtists", "artists", 30),
        SearchKind.ALBUM to Triple("searchAlbums", "albumsV2", 30),
        SearchKind.PLAYLIST to Triple("searchPlaylists", "playlists", 30),
        SearchKind.USER to Triple("searchUsers", "users", 30),
        SearchKind.EPISODE to Triple("searchFullEpisodes", "episodes", 30),
        SearchKind.SHOW to Triple("searchPodcasts", "podcasts", 30),
        SearchKind.GENRE to Triple("searchGenres", "genres", 30),
    )

    suspend fun section(context: Context, kind: SearchKind, term: String, offset: Int = 0): List<SearchHit> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        val spec = SECTION_OPS[kind] ?: return emptyList()
        val variables = JSONObject()
            .put("includePreReleases", false)
            .put("includeAlbumPreReleases", false)
            .put("numberOfTopResults", 20)
            .put("searchTerm", trimmed)
            .put("offset", offset)
            .put("limit", spec.third)
            // .put("includeAudiobooks", true)
            .put("includeAuthors", true)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        val json = call(context, spec.first, variables) ?: return emptyList()
        val items = searchV2(json)?.optJSONObject(spec.second)?.optJSONArray("items") ?: return emptyList()
        val hits = (0 until items.length()).mapNotNull { hit(items.optJSONObject(it)) }
        val saved = Library.saved(context, hits.map { it.uri })
        log.i("section ${spec.first} '$trimmed' offset=$offset -> ${hits.size} hits, ${saved.size} saved"
        )
        if (saved.isEmpty()) return hits
        return hits.map { if (it.uri in saved) it.copy(saved = true) else it }
    }

    suspend fun recent(context: Context): List<SearchHit> {
        val variables = JSONObject()
            .put("limit", FULL_LIMIT)
            .put("includeAuthors", false)
            .put("includeEpisodeContentRatingsV2", false)
            .toString()
        val json = call(context, OP_RECENT, variables) ?: return emptyList()
        val items = json.optJSONObject("data")
            ?.optJSONObject("recentSearches")
            ?.optJSONObject("recentSearchesItems")
            ?.optJSONArray("items")
            ?: return emptyList()
        val hits = (0 until items.length()).mapNotNull { hit(items.optJSONObject(it)) }
        log.i("recent searches -> ${hits.size}")
        return hits
    }

    private suspend fun call(context: Context, operation: String, variables: String): JSONObject? {
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("$operation: no token")
            return null
        }
        return Pathfinder.queryPost(context, token, operation, variables)
    }

    private fun variables(term: String, limit: Int, prefix: Boolean): String = JSONObject()
        .put("query", term)
        .put("limit", limit)
        .put("offset", 0)
        .put("numberOfTopResults", limit)
        .put("includeArtistHasConcertsField", false)
        // .put("includeAudiobooks", true)
        .put("includeAuthors", true)
        .put("includePreReleases", true)
        .put("includeAlbumPreReleases", false)
        .put("includeEpisodeContentRatingsV2", true)
        .put("isPrefix", if (prefix) true else JSONObject.NULL)
        .put("sectionFilters", sectionFilters())
        .toString()

    private fun suggestionVariables(term: String, limit: Int): String = JSONObject()
        .put("query", term)
        .put("limit", limit)
        .put("numberOfTopResults", limit)
        .put("offset", 0)
        .put("includeAuthors", true)
        .put("includeAlbumPreReleases", true)
        .put("includeEpisodeContentRatingsV2", true)
        .toString()

    private fun searchV2(json: JSONObject): JSONObject? =
        json.optJSONObject("data")?.optJSONObject("searchV2")

    private fun parseResults(json: JSONObject, term: String): SearchResults {
        val search = searchV2(json)
        val hits = topHits(json).filter { it.kind != SearchKind.QUERY }
        val totals = LinkedHashMap<SearchKind, Int>()
        if (search != null) {
            COUNT_FIELDS.forEach { (field, kind) ->
                val count = search.optJSONObject(field)?.optInt("totalCount", 0) ?: 0
                if (count > 0) totals[kind] = count
            }
        }
        val sections = topSections(json)
        val chips = chipsOf(search, sections, hits)
        // topResultsV2 carries the whole ranking, but only its first entry
        // comes back fully decorated: the rows further down can arrive with
        // nothing but a name and a uri. The dedicated sections (tracksV2,
        // artists, ...) always carry covers and artist lines, so prefer them
        // and keep the top-results order as the fallback.
        val sectionTracks = sectionHits(search, "tracksV2")
        val sectionArtists = sectionHits(search, "artists")
        val sectionAlbums = sectionHits(search, "albumsV2")
        val sectionPlaylists = sectionHits(search, "playlists")
        return SearchResults(
            query = term,
            top = hits.firstOrNull(),
            tracks = merge(sectionTracks, hits, SearchKind.TRACK),
            artists = merge(sectionArtists, hits, SearchKind.ARTIST),
            albums = merge(sectionAlbums, hits, SearchKind.ALBUM),
            playlists = merge(sectionPlaylists, hits, SearchKind.PLAYLIST),
            others = hits.filter {
                it.kind != SearchKind.TRACK && it.kind != SearchKind.ARTIST &&
                    it.kind != SearchKind.ALBUM && it.kind != SearchKind.PLAYLIST
            },
            sections = sections,
            totals = totals,
            chips = chips,
        )
    }

    /** Rows of one searchV2 section, each a full entity. */
    private fun sectionHits(search: JSONObject?, field: String): List<SearchHit> {
        val items = search?.optJSONObject(field)?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { hit(items.optJSONObject(it)) }
    }

    /** Section rows first, then any top-result row the section did not name. */
    private fun merge(
        section: List<SearchHit>,
        hits: List<SearchHit>,
        kind: SearchKind,
    ): List<SearchHit> {
        val ranked = hits.filter { it.kind == kind }
        if (section.isEmpty()) return ranked
        val seen = section.mapTo(HashSet()) { it.uri }
        return section + ranked.filterNot { it.uri in seen }
    }

    private val COUNT_FIELDS = listOf(
        "tracksV2" to SearchKind.TRACK,
        "artists" to SearchKind.ARTIST,
        "albumsV2" to SearchKind.ALBUM,
        "playlists" to SearchKind.PLAYLIST,
        "users" to SearchKind.USER,
        "episodes" to SearchKind.EPISODE,
        "podcasts" to SearchKind.SHOW,
        "genres" to SearchKind.GENRE,
    )

    private val CHIP_SPEC = mapOf(
        "TRACKS" to (SearchKind.TRACK to "tracksV2"),
        "VIDEOS" to (SearchKind.TRACK to ""),
        "ARTISTS" to (SearchKind.ARTIST to "artists"),
        "ALBUMS" to (SearchKind.ALBUM to "albumsV2"),
        "PLAYLISTS" to (SearchKind.PLAYLIST to "playlists"),
        "USERS" to (SearchKind.USER to "users"),
        "EPISODES" to (SearchKind.EPISODE to "episodes"),
        "PODCASTS" to (SearchKind.SHOW to "podcasts"),
        "GENRES" to (SearchKind.GENRE to "genres"),
    )

    private fun chipsOf(
        search: JSONObject?,
        sections: List<SearchSection>,
        hits: List<SearchHit>,
    ): List<SearchKind> {
        val hasVideo = hits.any { it.video } || sections.any { it.sectionType == "VIDEO_CONTENT" }
        val order = search?.optJSONObject("chipOrder")?.optJSONArray("items")
        if (order == null) {
            val fallback = LinkedHashSet<SearchKind>()
            hits.mapTo(fallback) { it.kind }
            sections.forEach { section -> section.items.mapTo(fallback) { it.kind } }
            return fallback.filter { it != SearchKind.QUERY }
        }
        val out = LinkedHashSet<SearchKind>()
        for (i in 0 until order.length()) {
            val name = order.optJSONObject(i)?.optString("typeName").orEmpty()
            val spec = CHIP_SPEC[name] ?: continue
            if (spec.first == SearchKind.UNKNOWN) continue
            val present = if (name == "VIDEOS") {
                hasVideo
            } else {
                (search.optJSONObject(spec.second)?.optInt("totalCount", 0) ?: 0) > 0
            }
            if (present) out += spec.first
        }
        return out.toList()
    }

    private fun topHits(json: JSONObject): List<SearchHit> {
        val items = searchV2(json)
            ?.optJSONObject("topResultsV2")
            ?.optJSONArray("itemsV2")
            ?: return emptyList()
        return (0 until items.length()).mapNotNull { hit(items.optJSONObject(it)) }
    }

    private fun topSections(json: JSONObject): List<SearchSection> {
        val items = searchV2(json)
            ?.optJSONObject("topResultsV2")
            ?.optJSONArray("itemsV2")
            ?: return emptyList()
        val out = ArrayList<SearchSection>()
        for (i in 0 until items.length()) {
            val node = items.optJSONObject(i) ?: continue
            if (node.optString("__typename") != "SearchSectionEntity") continue
            val data = node.optJSONObject("data") ?: continue
            val nested = data.optJSONArray("items") ?: continue
            val hits = (0 until nested.length()).mapNotNull { hit(nested.optJSONObject(it)) }
            if (hits.isEmpty()) continue
            out += SearchSection(
                title = data.optJSONObject("title")?.optString("transformedLabel").orEmpty().plainText(),
                sectionType = data.optString("sectionType"),
                attributionUri = data.optString("attributionUri"),
                items = hits,
            )
        }
        return out
    }

    private fun hit(node: JSONObject?): SearchHit? {
        if (node == null) return null
        val wrapper = node.optJSONObject("item") ?: node
        if (wrapper.optString("__typename") == "SearchAutoCompleteEntity") {
            val data = wrapper.optJSONObject("data") ?: return null
            val text = data.optString("text")
            if (text.isBlank()) return null
            return SearchHit(
                kind = SearchKind.QUERY,
                uri = data.optString("uri"),
                title = text.plainText(),
                subtitle = "",
            )
        }
        val data = wrapper.optJSONObject("data") ?: return null
        val uri = data.optString("uri")
        if (uri.isBlank()) return null
        val parsed = when {
            uri.startsWith("spotify:track:") -> track(data)
            uri.startsWith("spotify:album:") -> album(data)
            uri.startsWith("spotify:artist:") -> artist(data)
            uri.startsWith("spotify:playlist:") -> playlist(data)
            uri.startsWith("spotify:episode:") -> episode(data)
            uri.startsWith("spotify:show:") -> show(data)
            uri.startsWith("spotify:user:") -> user(data)
            else -> null
        }
        return parsed?.let { it.copy(title = it.title.plainText(), subtitle = it.subtitle.plainText()) }
    }

    private fun track(data: JSONObject): SearchHit? {
        val album = data.optJSONObject("albumOfTrack")
        val artists = GraphQl.artistNames(data)
        val title = data.optString("name")
        if (title.isBlank()) return null
        val video = data.optString("trackMediaType") == "VIDEO"
        val wide = data.optJSONObject("visualIdentity")
            ?.optJSONObject("sixteenByNineCoverImage")
            ?.optJSONObject("image")
            ?.optJSONObject("data")
        return SearchHit(
            kind = SearchKind.TRACK,
            uri = data.optString("uri"),
            title = title,
            subtitle = if (artists.isBlank()) album?.optString("name").orEmpty() else artists,
            imageUrl = if (video) GraphQl.largestSource(wide) else GraphQl.entityImage(album),
            colorHex = GraphQl.entityColor(album),
            durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L,
            explicit = data.optJSONObject("contentRating")?.optString("label") == "EXPLICIT",
            video = video,
        )
    }

    private fun album(data: JSONObject): SearchHit? {
        val title = data.optString("name")
        if (title.isBlank()) return null
        return SearchHit(
            kind = SearchKind.ALBUM,
            uri = data.optString("uri"),
            title = title,
            subtitle = GraphQl.artistNames(data),
            imageUrl = GraphQl.entityImage(data),
            colorHex = GraphQl.entityColor(data),
        )
    }

    private fun artist(data: JSONObject): SearchHit? {
        val name = data.optJSONObject("profile")?.optString("name").orEmpty()
        if (name.isBlank()) return null
        return SearchHit(
            kind = SearchKind.ARTIST,
            uri = data.optString("uri"),
            title = name,
            subtitle = "Artist",
            imageUrl = GraphQl.entityImage(data),
            colorHex = GraphQl.entityColor(data),
        )
    }

    private fun playlist(data: JSONObject): SearchHit? {
        val title = data.optString("name")
        if (title.isBlank()) return null
        val owner = data.optJSONObject("ownerV2")
            ?.optJSONObject("data")
            ?.optString("name")
            .orEmpty()
        return SearchHit(
            kind = SearchKind.PLAYLIST,
            uri = data.optString("uri"),
            title = title,
            subtitle = owner,
            imageUrl = GraphQl.entityImage(data),
            colorHex = GraphQl.entityColor(data),
        )
    }

    private fun episode(data: JSONObject): SearchHit? {
        val title = data.optString("name")
        if (title.isBlank()) return null
        val showName = data.optJSONObject("podcastV2")
            ?.optJSONObject("data")
            ?.optString("name")
            .orEmpty()
        return SearchHit(
            kind = SearchKind.EPISODE,
            uri = data.optString("uri"),
            title = title,
            subtitle = showName,
            imageUrl = GraphQl.entityImage(data),
            colorHex = GraphQl.entityColor(data),
            durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L,
        )
    }

    private fun show(data: JSONObject): SearchHit? {
        val title = data.optString("name")
        if (title.isBlank()) return null
        return SearchHit(
            kind = SearchKind.SHOW,
            uri = data.optString("uri"),
            title = title,
            subtitle = data.optJSONObject("publisher")?.optString("name").orEmpty(),
            imageUrl = GraphQl.entityImage(data),
            colorHex = GraphQl.entityColor(data),
        )
    }

    private fun user(data: JSONObject): SearchHit? {
        val title = data.optString("name").ifBlank { data.optString("username") }
        if (title.isBlank()) return null
        val handle = data.optString("username")
        return SearchHit(
            kind = SearchKind.USER,
            uri = data.optString("uri"),
            title = title,
            subtitle = if (handle.isBlank()) "Profile" else "Profile • @$handle",
            imageUrl = GraphQl.largestSource(data.optJSONObject("avatar")),
        )
    }
}
