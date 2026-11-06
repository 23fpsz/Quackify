package com.quackify.backend.model

data class HomeItem(
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val kind: String,
    val colorHex: String = "",
    val caption: String = "",
)

data class HomeSection(
    val id: String,
    val title: String,
    val kind: String,
    val items: List<HomeItem>,
)

data class HomeChip(
    val id: String,
    val label: String,
    val subChips: List<HomeChip> = emptyList(),
)

data class HomeFeedPayload(
    val greeting: String = "",
    val chips: List<HomeChip> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)
