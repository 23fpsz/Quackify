package com.spotiduck.backend.model

data class HistoryEntry(
    val track: TrackRow,
    val playedAtMs: Long,
)
