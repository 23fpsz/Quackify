package com.quackify.backend.model

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
    val durationMs: Long get() = collectionDuration(tracks.map { it.durationMs }, total)
    val durationIsEstimate: Boolean get() = collectionDurationIsEstimate(tracks.map { it.durationMs }, total)
}

// Only the loaded tracks contribute a real duration. When the collection is
// longer than what was fetched, the average of the loaded ones is scaled to the
// reported total, which is the same estimate the web player shows. The caller
// passes whatever it currently holds, so paging in more tracks narrows it.
fun collectionDuration(durations: List<Long>, total: Int): Long {
    val counted = durations.filter { it > 0L }
    if (counted.isEmpty()) return 0L
    val sum = counted.sum()
    if (total <= 0 || total <= counted.size) return sum
    return sum / counted.size * total
}

fun collectionDurationIsEstimate(durations: List<Long>, total: Int): Boolean {
    val counted = durations.count { it > 0L }
    return counted > 0 && total > counted
}

data class PlaylistChoice(
    val uri: String,
    val name: String,
    val imageUrl: String = "",
)
