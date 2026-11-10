package com.quackify.compose.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncoreTrackRow
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.components.TrackListSkeleton
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.theme.rememberBottomPadding

/**
 * Spotify Listening History screen matching Spotify APK: plays from the same
 * artist that follow each other collapse into one expandable artist group,
 * the way Spotify's own history groups a run of consecutive plays.
 */
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    currentTrackTitle: String = "",
    currentTrackUri: String = "",
    isPlaying: Boolean = false,
    todayTracks: List<TrackItem> = emptyList(),
    yesterdayTracks: List<TrackItem> = emptyList(),
    earlierTracks: List<TrackItem> = emptyList(),
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bottomPadding = rememberBottomPadding()
    val hasContent = todayTracks.isNotEmpty() ||
        yesterdayTracks.isNotEmpty() ||
        earlierTracks.isNotEmpty()

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = "Back",
                onClick = onBackClick,
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Listening History",
                style = SpotifyTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        }

        if (loading && !hasContent) {
            TrackListSkeleton(
                rows = 9,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            )
        } else if (!hasContent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tracks you play will show up here.",
                    style = SpotifyTheme.typography.bodyMedium.copy(
                        color = EncoreColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding
                )
            ) {
                historySection(
                    label = "Today",
                    tracks = todayTracks,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick,
                    expandedGroups = expandedGroups
                )
                historySection(
                    label = "Yesterday",
                    tracks = yesterdayTracks,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick,
                    expandedGroups = expandedGroups
                )
                historySection(
                    label = "Earlier",
                    tracks = earlierTracks,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick,
                    expandedGroups = expandedGroups
                )

                item {
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

/**
 * One rendered history row: a lone play, or a run of consecutive plays by the
 * same artist collapsed behind an expandable header naming the artist.
 */
private sealed interface HistoryEntry {
    data class Single(val track: TrackItem) : HistoryEntry

    data class Group(
        val artist: String,
        val imageUrl: String?,
        val tracks: List<TrackItem>,
        val id: String,
    ) : HistoryEntry
}

private fun artistKey(track: TrackItem): String =
    track.artistUri.takeIf { it.isNotBlank() } ?: track.artist.lowercase()

/** Runs of 2+ consecutive plays by one artist become a group; singletons stay rows. */
private fun groupByArtist(tracks: List<TrackItem>): List<HistoryEntry> {
    val out = ArrayList<HistoryEntry>(tracks.size)
    var i = 0
    while (i < tracks.size) {
        val head = tracks[i]
        val key = artistKey(head)
        var j = i + 1
        while (j < tracks.size && artistKey(tracks[j]) == key) j++
        val run = tracks.subList(i, j)
        out += if (run.size >= 2) {
            HistoryEntry.Group(
                artist = head.artist,
                imageUrl = head.imageUrl,
                tracks = run.toList(),
                id = "$i-${head.artistUri.ifBlank { head.artist }}",
            )
        } else {
            HistoryEntry.Single(head)
        }
        i = j
    }
    return out
}

private fun LazyListScope.historySection(
    label: String,
    tracks: List<TrackItem>,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    expandedGroups: SnapshotStateMap<String, Boolean>
) {
    if (tracks.isEmpty()) return
    item(key = "header-$label") {
        Text(
            text = label,
            style = SpotifyTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
    }
    val entries = groupByArtist(tracks)
    itemsIndexed(entries, key = { index, entry ->
        when (entry) {
            is HistoryEntry.Single -> "$label-$index-${entry.track.uri}"
            is HistoryEntry.Group -> "group-$label-${entry.id}"
        }
    }) { _, entry ->
        when (entry) {
            is HistoryEntry.Single -> EncoreTrackRow(
                trackNumber = null,
                title = entry.track.title,
                artist = entry.track.artist,
                isExplicit = entry.track.isExplicit,
                imageUrl = entry.track.imageUrl,
                isPlaying = isPlaying && entry.track.title == currentTrackTitle,
                onClick = { onTrackSelect(entry.track) },
                onMoreClick = { onTrackOptionsClick(entry.track) }
            )
            is HistoryEntry.Group -> {
                val groupKey = "$label:${entry.id}"
                val expanded = expandedGroups[groupKey] == true
                HistoryArtistGroup(
                    group = entry,
                    isExpanded = expanded,
                    onToggle = {
                        expandedGroups[groupKey] = !expanded
                    },
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick
                )
            }
        }
    }
}

/** Collapsed-by-default artist header that expands into the run of plays under it. */
@Composable
private fun HistoryArtistGroup(
    group: HistoryEntry.Group,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpotifyArtworkImage(
                imageUrl = group.imageUrl,
                contentDescription = group.artist,
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.artist,
                    style = SpotifyTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = EncoreColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.tracks.size} song" +
                        if (group.tracks.size == 1) "" else "s",
                    style = SpotifyTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary
                    ),
                    maxLines = 1
                )
            }
            Icon(
                painter = painterResource(
                    id = if (isExpanded) R.drawable.encore_icon_chevron_up_24
                    else R.drawable.encore_icon_chevron_down_24
                ),
                contentDescription = if (isExpanded) "Collapse ${group.artist}"
                else "Expand ${group.artist}",
                tint = EncoreColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (isExpanded) {
            group.tracks.forEach { track ->
                EncoreTrackRow(
                    trackNumber = null,
                    title = track.title,
                    artist = track.artist,
                    isExplicit = track.isExplicit,
                    imageUrl = track.imageUrl,
                    isPlaying = isPlaying && track.title == currentTrackTitle,
                    onClick = { onTrackSelect(track) },
                    onMoreClick = { onTrackOptionsClick(track) }
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewHistoryScreen() {
    SpotifyEncoreTheme {
        HistoryScreen(
            onBackClick = {},
            onTrackSelect = {}
        )
    }
}
