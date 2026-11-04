package com.spotiduck.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme

/**
 * Spotify Library Create Bottom Sheet (+ button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCreateSheet(
    isOpen: Boolean,
    onCreatePlaylist: () -> Unit,
    onCreateBlend: () -> Unit,
    onCreateFolder: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .navigationBarsPadding()
        ) {
            Text(
                text = "Create",
                style = SpotifyTheme.typography.titleLarge.copy(fontSize = 18.sp),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            CreateOptionItem(
                title = "Playlist",
                subtitle = "Build a playlist with songs, or episodes",
                iconRes = R.drawable.encore_icon_playlist_24,
                onClick = {
                    onDismiss()
                    onCreatePlaylist()
                }
            )

            CreateOptionItem(
                title = "Folder",
                subtitle = "Organize and group your playlists",
                iconRes = R.drawable.encore_icon_folder_24,
                onClick = {
                    onDismiss()
                    onCreateFolder()
                }
            )

            CreateOptionItem(
                title = "Blend",
                subtitle = "Combine tastes in a playlist with friends",
                iconRes = R.drawable.encore_icon_user_24,
                onClick = {
                    onDismiss()
                    onCreateBlend()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CreateOptionItem(
    title: String,
    subtitle: String,
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
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SpotifyTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
    }
}

@Preview
@Composable
fun PreviewLibraryCreateSheet() {
    SpotifyEncoreTheme {
        LibraryCreateSheet(
            isOpen = true,
            onCreatePlaylist = {},
            onCreateBlend = {},
            onCreateFolder = {},
            onDismiss = {}
        )
    }
}
