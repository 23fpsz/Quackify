package com.quackify.compose.home

import androidx.compose.ui.graphics.Color
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.model.HomeItem
import com.quackify.backend.model.TrackRow
import com.quackify.backend.model.ShelfItem
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.ArtistRelease
import com.quackify.backend.model.HomeSection

private val FallbackCardColor = Color(0xFF535353)

private fun colorOf(item: HomeItem): Color {
    val hex = item.colorHex
    if (hex.length != 7 || hex[0] != '#') return FallbackCardColor
    val rgb = hex.substring(1).toLongOrNull(16) ?: return FallbackCardColor
    return Color(0xFF000000L or rgb)
}

private fun tagOf(kind: String): String = when {
    kind.contains("Playlist", true) -> "PLAYLIST"
    kind.contains("Album", true) -> "ALBUM"
    kind.contains("Artist", true) -> "ARTIST"
    kind.contains("Episode", true) -> "EPISODE"
    kind.contains("Show", true) -> "SHOW"
    else -> kind.uppercase().take(12)
}

data class HomeShelf(
    val id: String,
    val title: String,
    val cards: List<RecommendationCard> = emptyList(),
    val artists: List<ArtistCardItem> = emptyList(),
    val podcasts: Boolean = false,
)

private val PODCAST_TAGS = setOf("EPISODE", "SHOW", "PODCAST", "AUDIOBOOK", "CHAPTER")

private fun List<HomeItem>.podcastShelf(): Boolean {
    if (isEmpty()) return false
    val podcast = count { tagOf(it.kind) in PODCAST_TAGS }
    return podcast * 2 >= size
}

fun List<HomeSection>.toShelves(): List<HomeShelf> = mapNotNull { section ->
    if (section.kind == HomeFeed.SHORTS) return@mapNotNull null
    val cards = section.items.map { item ->
        RecommendationCard(
            title = item.title,
            subtitle = item.subtitle.ifBlank { item.kind },
            coverColor = colorOf(item),
            uri = item.uri,
            imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
            tag = tagOf(item.kind),
        )
    }
    if (cards.isEmpty()) return@mapNotNull null
    val artistsOnly = section.items.all { tagOf(it.kind) == "ARTIST" }
    HomeShelf(
        id = section.id.ifBlank { section.title + section.kind },
        title = section.title,
        cards = if (artistsOnly) emptyList() else cards,
        artists = if (artistsOnly) {
            section.items.map { item ->
                ArtistCardItem(name = item.title, color = colorOf(item), uri = item.uri)
            }
        } else {
            emptyList()
        },
        podcasts = section.items.podcastShelf(),
    )
}

fun List<HomeSection>.toQuickAccess(): List<QuickAccessItem> =
    firstOrNull { it.kind == HomeFeed.SHORTS }?.items?.map { item ->
        QuickAccessItem(
            title = item.title,
            coverColor = colorOf(item),
            uri = item.uri,
            imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
        )
    }.orEmpty()

fun TrackRow.toTrackItem(): TrackItem = TrackItem(
    title = title,
    artist = artists,
    isExplicit = explicit,
    uri = uri,
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    saved = saved,
    durationMs = durationMs,
    albumUri = albumUri,
    artistUri = artistUri,
    discNumber = discNumber,
    trackNumber = trackNumber,
    hasVideo = video,
)

fun ShelfItem.toArtistRelease(): ArtistRelease = ArtistRelease(
    title = title,
    year = subtitle.substringBefore(" \u2022 ").trim(),
    type = subtitle.substringAfter(" \u2022 ", "Album").trim(),
    color = parseHexColor(colorHex) ?: Color(0xFF333333),
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    uri = uri
)