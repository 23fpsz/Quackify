package com.quackify.backend.model

data class TrackRow(
    val uri: String,
    val uid: String = "",
    val title: String,
    val artists: String,
    val album: String,
    val imageUrl: String,
    val colorHex: String,
    val durationMs: Long,
    val explicit: Boolean,
    val playable: Boolean,
    val plays: Long = 0L,
    val saved: Boolean? = null,
    val albumUri: String = "",
    val artistUri: String = "",
    val discNumber: Int = 1,
    val trackNumber: Int = 0,
    val video: Boolean = false,
)

data class TrackItem(
    val title: String,
    val artist: String,
    val isExplicit: Boolean = false,
    val uri: String = "",
    val imageUrl: String? = null,
    val saved: Boolean? = null,
    val durationMs: Long = 0L,
    val albumUri: String = "",
    val artistUri: String = "",
    val albumName: String = "",
    val discNumber: Int = 1,
    val trackNumber: Int = 0,
    val hasVideo: Boolean = false,
)
