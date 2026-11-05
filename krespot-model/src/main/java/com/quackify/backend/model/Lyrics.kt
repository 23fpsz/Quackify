package com.quackify.backend.model

data class TrackLyrics(
    val lines: List<LyricLine>,
    val provider: String,
    val synced: Boolean,
)

data class LyricLine(
    val startTimeMs: Long,
    val text: String,
)
