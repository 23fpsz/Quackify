package com.quackify.compose.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncoreTrackRow
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.theme.rememberBottomPadding

/**
 * Spotify Listening History screen matching Spotify APK.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        // Sticky Header
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = EncoreColors.Green,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
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
                    onTrackOptionsClick = onTrackOptionsClick
                )
                historySection(
                    label = "Yesterday",
                    tracks = yesterdayTracks,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick
                )
                historySection(
                    label = "Earlier",
                    tracks = earlierTracks,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick
                )

                item {
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

private fun LazyListScope.historySection(
    label: String,
    tracks: List<TrackItem>,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit
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
    itemsIndexed(tracks, key = { index, track -> "$label-$index-${track.uri}" }) { _, track ->
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
