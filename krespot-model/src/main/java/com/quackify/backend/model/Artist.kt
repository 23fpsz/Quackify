package com.quackify.backend.model

data class ArtistLink(
    val name: String,
    val url: String,
)

data class CityStat(
    val city: String,
    val listeners: Long,
)

data class ConcertItem(
    val title: String,
    val venue: String,
    val city: String,
    val dateLabel: String,
    val festival: Boolean,
    val uri: String,
)

data class ArtistDetail(
    val uri: String,
    val name: String,
    val imageUrl: String,
    val headerImageUrl: String = "",
    val gallery: List<String> = emptyList(),
    val colorHex: String,
    val monthlyListeners: Long,
    val followers: Long,
    val biography: String,
    val verified: Boolean = false,
    val saved: Boolean = false,
    val topTracks: List<TrackRow>,
    val releases: List<ShelfItem>,
    val pinned: ShelfItem? = null,
    val pinnedComment: String = "",
    val albums: List<ShelfItem> = emptyList(),
    val singles: List<ShelfItem> = emptyList(),
    val compilations: List<ShelfItem> = emptyList(),
    val featuring: List<ShelfItem> = emptyList(),
    val appearsOn: List<ShelfItem> = emptyList(),
    val artistPlaylists: List<ShelfItem> = emptyList(),
    val relatedArtists: List<ShelfItem> = emptyList(),
    val videos: List<TrackRow> = emptyList(),
    val topCities: List<CityStat> = emptyList(),
    val worldRank: Int = 0,
    val merch: List<MerchItem> = emptyList(),
    val externalLinks: List<ArtistLink> = emptyList(),
    val discoveredOn: List<ShelfItem> = emptyList(),
    val concerts: List<ConcertItem> = emptyList(),
)
