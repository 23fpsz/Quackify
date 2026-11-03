package com.spotiduck.backend.model

data class BrowseCard(
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val colorHex: String,
)

data class BrowseSection(
    val title: String,
    val cards: List<BrowseCard>,
)

data class BrowsePage(
    val uri: String,
    val title: String,
    val subtitle: String,
    val colorHex: String,
    val imageUrl: String,
    val sections: List<BrowseSection>,
    val nextOffset: Int?,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}
