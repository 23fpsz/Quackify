package com.quackify.compose.sheets

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/** One credited artist on the playing track. */
data class ArtistListEntry(
    val name: String,
    val uri: String,
    val imageUrl: String = "",
)

/**
 * The multi-artist picker: tapping the artist line (or the about-the-artist
 * card) on a track credited to two or more artists opens this sheet instead
 * of jumping straight into the first one. Each row names the artist and a
 * "View artist" affordance that opens their page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistListSheet(
    artists: List<ArtistListEntry>,
    onDismiss: () -> Unit,
    onViewArtist: (ArtistListEntry) -> Unit,
) {
    if (artists.isEmpty()) {
        onDismiss()
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.artists),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            artists.forEach { artist ->
                ArtistListRow(
                    artist = artist,
                    onClick = {
                        onDismiss()
                        onViewArtist(artist)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ArtistListRow(
    artist: ArtistListEntry,
    onClick: () -> Unit,
) {
    val fallbackColor = Color(0xFFE8115B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artist.imageUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 24.dp,
                    fallbackColor = fallbackColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(fallbackColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = artist.name.firstOrNull()?.uppercase() ?: "A",
                        style = EncoreTheme.typography.titleMedium.copy(color = Color.White)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.go_to_artist),
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary
                ),
                maxLines = 1
            )
        }
        Text(
            text = stringResource(R.string.view_artist),
            style = EncoreTheme.typography.bodySmallBold.copy(
                color = EncoreColors.Green
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
