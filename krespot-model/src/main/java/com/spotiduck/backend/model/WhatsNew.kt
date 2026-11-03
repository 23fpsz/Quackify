package com.spotiduck.backend.model

data class WhatsNewItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val timestamp: String,
    val isNew: Boolean,
    val isMusic: Boolean,
    val kind: String,
)

data class WhatsNewFeed(
    val items: List<WhatsNewItem> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int? = null,
    val seen: List<String> = emptyList(),
) {
    val hasMore: Boolean get() = nextOffset != null
    val newCount: Int get() = items.count { it.isNew }
}
