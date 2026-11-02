package com.spotiduck.backend.api

import android.content.Context
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.model.*
import org.json.JSONObject
import com.spotiduck.backend.core.net.GraphQl
import com.spotiduck.backend.core.net.Pathfinder
import com.spotiduck.backend.core.net.plainText

private val log = Logs.of(Logs.Module.PODCASTS)

object Podcasts {

    private const val OP_SHOW = "queryShowMetadataV2"
    private const val OP_EPISODES = "queryPodcastEpisodes"
    private const val OP_EPISODE = "getEpisodeOrChapter"
    private const val OP_RELATED_SHOWS = "internalLinkRecommenderShow"
    private const val PAGE = 50

    suspend fun show(context: Context, uri: String): ShowDetail? {
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("show: no token")
            return null
        }
        val union = showUnion(context, token, uri) ?: return null
        val page = episodesPage(context, token, uri, 0, PAGE)
        val rows = page?.rows.orEmpty().distinctBy { it.uri }
        val related = relatedShows(context, token, uri)
        val detail = ShowDetail(
            uri = uri,
            name = union.optString("name").plainText(),
            publisher = union.optJSONObject("publisher")?.optString("name").orEmpty().plainText(),
            description = union.optString("description").plainText(),
            imageUrl = GraphQl.entityImage(union),
            colorHex = GraphQl.visualColor(union).ifBlank { GraphQl.entityColor(union) },
            rating = union.optJSONObject("rating")?.optDouble("averageRating", 0.0) ?: 0.0,
            ratingCount = union.optJSONObject("rating")?.optInt("totalRatingCount", 0) ?: 0,
            saved = union.optBoolean("saved", false),
            mediaType = union.optString("mediaType").plainText().ifBlank { "AUDIO" },
            episodes = rows,
            total = page?.total ?: 0,
            offset = 0,
            fetched = rows.size,
            nextOffset = page?.nextOffset,
            related = related,
        )
        log.i("show $uri '${detail.name}' by '${detail.publisher}' episodes=${detail.fetched}/${detail.total} " +
                "related=${related.size} rating=%.2f(%d) saved=%s media=%s".format(
                    detail.rating,
                    detail.ratingCount,
                    detail.saved,
                    detail.mediaType,
                )
        )
        return detail
    }

    suspend fun episodesMore(context: Context, uri: String, offset: Int): EpisodePage? {
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) return null
        return episodesPage(context, token, uri, offset, PAGE)
    }

    suspend fun episode(context: Context, uri: String): EpisodeDetail? {
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) {
            log.w("episode: no token")
            return null
        }
        val variables = JSONObject()
            .put("uri", uri)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        val union = Pathfinder.queryPost(context, token, OP_EPISODE, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("episodeUnionV2")
            ?: run {
                log.w("episode: no payload for $uri")
                return null
            }
        val show = union.optJSONObject("podcastV2")?.optJSONObject("data")
        val played = union.optJSONObject("playedState")
        val detail = EpisodeDetail(
            uri = uri,
            title = union.optString("name").plainText(),
            description = union.optString("description").plainText(),
            showName = show?.optString("name").orEmpty().plainText(),
            showUri = show?.optString("uri").orEmpty(),
            imageUrl = GraphQl.entityImage(union),
            wideImageUrl = wideImage(union),
            colorHex = GraphQl.visualColor(union).ifBlank { GraphQl.entityColor(union) },
            durationMs = union.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L,
            releaseDate = releaseLabel(union),
            explicit = union.optJSONObject("contentRating")?.optString("label")
                .orEmpty().plainText() == "EXPLICIT",
            playable = union.optJSONObject("playability")?.optBoolean("playable", true) ?: true,
            video = mediaTypes(union).contains("VIDEO"),
            played = played?.optString("state").orEmpty() == "COMPLETED",
            positionMs = played?.optLong("playPositionMilliseconds", 0L) ?: 0L,
            shareUrl = union.optJSONObject("sharingInfo")?.optString("shareUrl").orEmpty(),
        )
        log.i("episode $uri '${detail.title}' show='${detail.showName}' ${detail.releaseDate} " +
                "${detail.durationMs}ms video=${detail.video} played=${detail.played} " +
                "position=${detail.positionMs} playable=${detail.playable}"
        )
        return detail
    }

    private suspend fun showUnion(context: Context, token: String, uri: String): JSONObject? {
        val variables = JSONObject()
            .put("uri", uri)
            .put("includeContentCapabilityTrait", true)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        return Pathfinder.queryPost(context, token, OP_SHOW, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("podcastUnionV2")
    }

    private suspend fun episodesPage(
        context: Context,
        token: String,
        uri: String,
        offset: Int,
        limit: Int,
    ): EpisodePage? {
        val variables = JSONObject()
            .put("uri", uri)
            .put("offset", offset)
            .put("limit", limit)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        val union = Pathfinder.queryPost(context, token, OP_EPISODES, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("podcastUnionV2")
            ?: return null
        val node = union.optJSONObject("episodesV2")
        val items = node?.optJSONArray("items")
        val rows = ArrayList<EpisodeRow>(items?.length() ?: 0)
        for (i in 0 until (items?.length() ?: 0)) {
            val item = items?.optJSONObject(i) ?: continue
            val data = item.optJSONObject("entity")?.optJSONObject("data")
                ?: item.optJSONObject("data")
                ?: continue
            row(data, union)?.let(rows::add)
        }
        val page = EpisodePage(
            rows = rows,
            total = node?.optInt("totalCount", 0) ?: 0,
            offset = offset,
            fetched = rows.size,
            nextOffset = node?.optJSONObject("pagingInfo")
                ?.optInt("nextOffset", -1)
                ?.takeIf { it > offset },
        )
        log.i("episodes $uri offset=$offset -> ${page.fetched}/${page.total} next=${page.nextOffset ?: "none"}"
        )
        return page
    }

    private suspend fun relatedShows(context: Context, token: String, uri: String): List<ShowCard> {
        val variables = JSONObject().put("uri", uri).toString()
        val data = Pathfinder.queryPost(context, token, OP_RELATED_SHOWS, variables)
            ?.optJSONObject("data")
            ?: return emptyList()
        val node = data.optJSONObject("seoRecommendedPodcast")
            ?: data.optJSONObject("internalLinkRecommenderShow")
            ?: return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ShowCard>(items.length())
        for (i in 0 until items.length()) {
            val show = items.optJSONObject(i)?.optJSONObject("data") ?: continue
            val cardUri = show.optString("uri")
            if (cardUri.isBlank()) continue
            out += ShowCard(
                uri = cardUri,
                name = show.optString("name").plainText(),
                publisher = show.optJSONObject("publisher")?.optString("name").orEmpty().plainText(),
                imageUrl = GraphQl.entityImage(show),
            )
        }
        return out
    }

    private fun row(episode: JSONObject, show: JSONObject?): EpisodeRow? {
        val uri = episode.optString("uri")
        if (uri.isBlank()) return null
        val played = episode.optJSONObject("playedState")
        return EpisodeRow(
            uri = uri,
            title = episode.optString("name").plainText(),
            description = episode.optString("description").plainText(),
            releaseDate = releaseLabel(episode),
            durationMs = episode.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L,
            imageUrl = GraphQl.entityImage(episode),
            colorHex = GraphQl.visualColor(episode).ifBlank { GraphQl.entityColor(episode) },
            explicit = episode.optJSONObject("contentRating")?.optString("label")
                .orEmpty().plainText() == "EXPLICIT",
            playable = episode.optJSONObject("playability")?.optBoolean("playable", true) ?: true,
            video = mediaTypes(episode).contains("VIDEO"),
            played = played?.optString("state").orEmpty() == "COMPLETED",
            positionMs = played?.optLong("playPositionMilliseconds", 0L) ?: 0L,
            showName = show?.optString("name").orEmpty().plainText(),
            showUri = show?.optString("uri").orEmpty(),
        )
    }

    private fun mediaTypes(node: JSONObject): List<String> {
        val array = node.optJSONArray("mediaTypes") ?: return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) out += value
        }
        return out
    }

    private fun releaseLabel(node: JSONObject): String {
        val date = node.optJSONObject("releaseDate") ?: return ""
        val iso = date.optString("isoString").plainText()
        if (iso.length < 10) return iso
        val precision = date.optString("precision").plainText()
        val year = iso.substring(0, 4)
        val month = iso.substring(5, 7).toIntOrNull() ?: return year
        val day = iso.substring(8, 10).toIntOrNull()
        return when (precision) {
            "YEAR" -> year
            "MONTH" -> "${MONTH_NAMES[month - 1]} $year"
            else -> if (day == null) "${MONTH_NAMES[month - 1]} $year" else "${MONTH_NAMES[month - 1]} $day, $year"
        }
    }

    private fun wideImage(node: JSONObject): String {
        val visual = node.optJSONObject("visualIdentity") ?: return ""
        val wide = visual.optJSONObject("sixteenByNineCoverImage") ?: return ""
        val sources = (wide.optJSONObject("data") ?: wide).optJSONArray("sources") ?: return ""
        var best = ""
        var bestWidth = -1
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val url = source.optString("url")
            if (url.isBlank()) continue
            val width = source.optInt("width", 0)
            if (width > bestWidth) {
                bestWidth = width
                best = url
            }
        }
        return best
    }

    private val MONTH_NAMES = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
