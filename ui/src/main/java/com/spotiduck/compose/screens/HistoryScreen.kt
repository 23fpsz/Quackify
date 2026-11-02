package com.spotiduck.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncoreTrackRow
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

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
    modifier: Modifier = Modifier
) {
    val bottomPadding = rememberBottomPadding()

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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
        ) {
            // Group: Today
            item {
                Text(
                    text = "Today",
                    style = SpotifyTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(todayTracks) { index, track ->
                val isThisPlaying = isPlaying && track.title == currentTrackTitle
                EncoreTrackRow(
                    trackNumber = null,
                    title = track.title,
                    artist = track.artist,
                    isExplicit = track.isExplicit,
                    isPlaying = isThisPlaying,
                    onClick = { onTrackSelect(track) },
                    onMoreClick = { onTrackOptionsClick(track) }
                )
            }

            // Group: Yesterday
            item {
                Text(
                    text = "Yesterday",
                    style = SpotifyTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(yesterdayTracks) { index, track ->
                val isThisPlaying = isPlaying && track.title == currentTrackTitle
                EncoreTrackRow(
                    trackNumber = null,
                    title = track.title,
                    artist = track.artist,
                    isExplicit = track.isExplicit,
                    isPlaying = isThisPlaying,
                    onClick = { onTrackSelect(track) },
                    onMoreClick = { onTrackOptionsClick(track) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
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
