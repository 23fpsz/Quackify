package com.quackify.backend.model

enum class ShelfKind { LIKED, PLAYLIST, ALBUM, ARTIST, SHOW, EPISODE, FOLDER, UNKNOWN }

data class ShelfItem(
    val uri: String,
    val title: String,
    val subtitle: String,
    val kind: ShelfKind,
    val imageUrl: String = "",
    val colorHex: String = "",
    val pinned: Boolean = false,
    val count: Int = 0,
)

data class LibraryPage(
    val items: List<ShelfItem>,
    val filters: List<String>,
    val sort: String,
    val total: Int,
    val offset: Int = 0,
    val fetched: Int = 0,
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
}

data class TrackPage(
    val rows: List<TrackRow>,
    val total: Int,
    val offset: Int,
    val fetched: Int,
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
}
