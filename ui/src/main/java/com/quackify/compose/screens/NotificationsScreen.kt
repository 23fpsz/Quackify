package com.quackify.compose.screens

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
import androidx.compose.material3.TextButton
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
import com.quackify.compose.R
import com.quackify.compose.components.EncoreChip
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.components.TrackListSkeleton
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.theme.rememberBottomPadding

data class SpotifyNotification(
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val isMusic: Boolean = true,
    val id: String = "",
    val isNew: Boolean = false,
    val imageUrl: String = "",
    val uri: String = "",
    val kind: String = ""
)

/**
 * Spotify "What's New" Notifications screen matching native Spotify APK.
 */
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit,
    onPlayClick: (SpotifyNotification) -> Unit = {},
    notifications: List<SpotifyNotification> = emptyList(),
    loading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Music", "Podcasts & Shows")
    val bottomPadding = rememberBottomPadding()

    val filtered = remember(selectedFilterIndex, notifications) {
        when (selectedFilterIndex) {
            1 -> notifications.filter { it.isMusic }
            2 -> notifications.filter { !it.isMusic }
            else -> notifications
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
        if (loading) {
            TrackListSkeleton(
                rows = 9,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            )
        } else if (loadFailed && filtered.isEmpty()) {
            // The fetch finished with nothing: say the network is the reason
            // and offer a retry, the same card the playlist screen shows,
            // instead of a premature "all caught up"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_cloud_off_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Couldn't load What's New",
                        style = SpotifyTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Check your internet connection and try again.",
                        style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Text(text = "Try again", color = EncoreColors.Green)
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "You're all caught up",
                        style = SpotifyTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "New releases from the artists and podcasts you follow will show up here.",
                        style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onRetry) {
                        Text(text = "Refresh", color = EncoreColors.Green)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
            ) {
                items(filtered) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayClick(item) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork
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
                            if (item.imageUrl.isNotBlank()) {
                                SpotifyArtworkImage(
                                    imageUrl = item.imageUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 6.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(
                                        id = if (item.isMusic) R.drawable.encore_icon_album_24
                                        else R.drawable.encore_icon_podcasts_24
                                    ),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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
                                text = relativeTime(item.timestamp),
                                style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                            )
                        }

                        EncorePlayButton(
                            isPlaying = false,
                            onClick = { onPlayClick(item) },
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
}

private fun relativeTime(iso: String): String {
    val at = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(iso)?.time
    }.getOrNull() ?: return ""
    val minutes = (System.currentTimeMillis() - at) / 60000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

@Preview
@Composable
fun PreviewNotificationsScreen() {
    SpotifyEncoreTheme {
        NotificationsScreen(onBackClick = {})
    }
}
