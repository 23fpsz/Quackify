package com.spotiduck.compose.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreChip
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

data class SpotifyNotification(
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val isMusic: Boolean = true
)

/**
 * Spotify "What's New" Notifications screen matching native Spotify APK.
 */
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Music", "Podcasts & Shows")
    val bottomPadding = rememberBottomPadding()

    val allNotifications = remember {
        listOf(
            SpotifyNotification(
                title = "New Single: Dancing In The Flames",
                subtitle = "The Weeknd • New release",
                timestamp = "2h ago",
                isMusic = true
            ),
            SpotifyNotification(
                title = "Random Access Memories (10th Anniv.)",
                subtitle = "Daft Punk • Album update",
                timestamp = "1d ago",
                isMusic = true
            ),
            SpotifyNotification(
                title = "#2210 - Lex Fridman",
                subtitle = "The Joe Rogan Experience • New episode",
                timestamp = "2d ago",
                isMusic = false
            ),
            SpotifyNotification(
                title = "Your Release Radar has been updated",
                subtitle = "30 new tracks from your followed artists",
                timestamp = "3d ago",
                isMusic = true
            ),
            SpotifyNotification(
                title = "New Episode: Building Next-Gen Audio",
                subtitle = "The Vergecast • 52 min",
                timestamp = "4d ago",
                isMusic = false
            )
        )
    }

    val filtered = remember(selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> allNotifications.filter { it.isMusic }
            2 -> allNotifications.filter { !it.isMusic }
            else -> allNotifications
        }
    }

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
                text = "What's New",
                style = SpotifyTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        }

        // Filter Pills
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters.size) { index ->
                EncoreChip(
                    text = filters[index],
                    isSelected = selectedFilterIndex == index,
                    onClick = { selectedFilterIndex = index }
                )
            }
        }

        // Notification List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
        ) {
            items(filtered) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayClick(item.title) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork / Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    if (item.isMusic) listOf(Color(0xFF8D67AB), Color(0xFF1E3264))
                                    else listOf(Color(0xFF006450), Color(0xFF1DB954))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.isMusic) "♫" else "🎙",
                            fontSize = 24.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.timestamp,
                            style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                        )
                    }

                    EncorePlayButton(
                        isPlaying = false,
                        onClick = { onPlayClick(item.title) },
                        size = 36.dp,
                        iconSize = 16.dp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Preview
@Composable
fun PreviewNotificationsScreen() {
    SpotifyEncoreTheme {
        NotificationsScreen(onBackClick = {})
    }
}
