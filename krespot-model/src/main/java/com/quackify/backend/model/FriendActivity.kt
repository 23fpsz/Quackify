package com.quackify.backend.model

data class FriendActivityEntry(
    val userUri: String,
    val userName: String,
    val userAvatarUrl: String = "",
    val trackUri: String = "",
    val trackName: String = "",
    val trackImageUrl: String = "",
    val albumUri: String = "",
    val albumName: String = "",
    val artistUris: List<String> = emptyList(),
    val artistNames: List<String> = emptyList(),
    val contextUri: String = "",
    val contextName: String = "",
    val timestampMs: Long = 0L,
) {
    val userId: String get() = userUri.removePrefix("spotify:user:")
    val artistLine: String get() = artistNames.filter { it.isNotBlank() }.joinToString(", ")
    val primaryArtistUri: String get() = artistUris.firstOrNull { it.isNotBlank() }.orEmpty()
    val primaryArtistName: String get() = artistNames.firstOrNull { it.isNotBlank() }.orEmpty()
}

data class FriendActivityFeed(
    val entries: List<FriendActivityEntry> = emptyList(),
)
