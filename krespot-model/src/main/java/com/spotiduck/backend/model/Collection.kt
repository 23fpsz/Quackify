package com.spotiduck.backend.model

data class CollectionDetail(
    val uri: String,
    val title: String,
    val creator: String,
    val creatorAvatarUrl: String = "",
    val saveCount: String,
    val description: String,
    val imageUrl: String,
    val colorHex: String,
    val tracks: List<TrackRow>,
    val total: Int,
    val offset: Int = 0,
    val fetched: Int = 0,
    val editable: Boolean = false,
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
    val complete: Boolean get() = fetched > 0 && offset + fetched >= total
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class PlaylistChoice(
    val uri: String,
    val name: String,
    val imageUrl: String = "",
)
