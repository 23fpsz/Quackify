package com.spotiduck.compose.playlist

sealed interface PlaylistEditAction {
    data class Remove(val index: Int, val track: TrackItem) : PlaylistEditAction
    data class Move(val fromIndex: Int, val toIndex: Int) : PlaylistEditAction
    data class BulkOrder(val previousList: List<TrackItem>, val newList: List<TrackItem>) : PlaylistEditAction
}
