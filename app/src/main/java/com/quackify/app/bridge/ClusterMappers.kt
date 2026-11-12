package com.quackify.app.bridge

import com.quackify.backend.connect.cluster.ClusterTrack
import com.quackify.backend.connect.command.CommandTrack
import com.quackify.backend.connect.command.ContentType
import com.quackify.compose.playlist.TrackItem

fun progressOf(position: Long, duration: Long): Float =
    if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

fun ClusterTrack.asCommandTrack(): CommandTrack = CommandTrack(
    uri = uri,
    name = title,
    artists = artistName,
    album = "",
    albumUri = albumUri,
    durationMs = durationMs,
    imageUrl = imageUrl,
    explicit = explicit,
    contentType = when {
        uri.startsWith("spotify:episode:") -> ContentType.EPISODE
        uri.startsWith("spotify:ad:") -> ContentType.AD
        else -> ContentType.TRACK
    },
    files = emptyList(),
    dualFiles = emptyList(),
    directFiles = emptyList(),
)

fun ClusterTrack.asTrackItem(): TrackItem = TrackItem(
    title = title,
    artist = artistName,
    uri = uri,
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    durationMs = durationMs,
    isExplicit = explicit,
    albumUri = albumUri,
    artistUri = artistUri,
    isEnhanced = isEnhanced,
)
