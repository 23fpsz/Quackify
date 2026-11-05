package com.quackify.backend.model

data class HistoryEntry(
    val track: TrackRow,
    val playedAtMs: Long,
)
