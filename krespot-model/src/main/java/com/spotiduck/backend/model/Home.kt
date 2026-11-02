package com.spotiduck.backend.model

data class HomeItem(
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val kind: String,
    val colorHex: String = "",
)

data class HomeSection(
    val id: String,
    val title: String,
    val kind: String,
    val items: List<HomeItem>,
)
