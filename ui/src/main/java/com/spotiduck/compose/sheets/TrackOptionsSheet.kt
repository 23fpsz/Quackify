package com.spotiduck.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme

/**
 * Spotify Track Context Options Sheet (3-dot menu).
 * Includes cover art, track info, Like toggle, and Spotify actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: TrackItem?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onLikeToggle: (Boolean) -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onViewArtist: (String) -> Unit = {},
    onViewAlbum: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen || track == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isLiked by remember(track) { mutableStateOf(track.isExplicit) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EncoreColors.TextSecondary.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track Art Thumbnail (Center)
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF8D67AB), Color(0xFF1E3264))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♫",
                    fontSize = 40.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = track.title,
                style = SpotifyTheme.typography.titleLarge.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track.artist,
                style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Icons Row: Like, Ban, Add to Playlist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        isLiked = !isLiked
                        onLikeToggle(isLiked)
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isLiked) R.drawable.encore_icon_heart_active_24 else R.drawable.encore_icon_heart_24
                        ),
                        contentDescription = "Like",
                        tint = if (isLiked) EncoreColors.Green else EncoreColors.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isLiked) "Liked" else "Like",
                        style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        onDismiss()
                        onAddToPlaylist()
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_add_to_playlist_24),
                        contentDescription = "Add to playlist",
                        tint = EncoreColors.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add to playlist",
                        style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        onDismiss()
                        onAddToQueue()
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_add_to_queue_24),
                        contentDescription = "Add to queue",
                        tint = EncoreColors.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add to queue",
                        style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )
                }
            }

            HorizontalDivider(
                color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Context Menu List Items
            OptionRowItem(
                title = "View artist",
                iconRes = R.drawable.encore_icon_user_24,
                onClick = {
                    onDismiss()
                    onViewArtist(track.artist)
                }
            )

            OptionRowItem(
                title = "View album",
                iconRes = R.drawable.encore_icon_playlist_24,
                onClick = {
                    onDismiss()
                    onViewAlbum(track.title)
                }
            )

            OptionRowItem(
                title = "Share",
                iconRes = R.drawable.encore_icon_share_android_24,
                onClick = {
                    onDismiss()
                    onShare()
                }
            )

            OptionRowItem(
                title = "Sleep timer",
                iconRes = R.drawable.encore_icon_time_24,
                onClick = {
                    onDismiss()
                    onSleepTimerClick()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OptionRowItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = EncoreColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = SpotifyTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview
@Composable
fun PreviewTrackOptionsSheet() {
    SpotifyEncoreTheme {
        TrackOptionsSheet(
            track = TrackItem("Starboy", "The Weeknd, Daft Punk", true),
            isOpen = true,
            onDismiss = {}
        )
    }
}
