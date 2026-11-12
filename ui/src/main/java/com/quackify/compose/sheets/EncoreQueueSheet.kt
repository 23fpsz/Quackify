package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreExplicitTag
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayIndicator
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreSpacers
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme

/**
 * Spotify Queue Screen & Bottom Sheet matching `layout_queue_sheet_content.xml`
 * and `layout_queue_controls.xml` from decompiled Spotify Mobile resources:
 * - "Now playing" item with animated Encore equalizer indicator
 * - "Next in queue" with dismiss action and reordering handles
 * - "Next from: [Context]" with upcoming queue items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyQueueSheet(
    currentTrackTitle: String,
    currentTrackArtist: String,
    currentTrackCoverUrl: String? = null,
    queueSourceContext: String, // e.g. "Today's Top Hits"
    userQueue: List<TrackItem>,
    nextFromContext: List<TrackItem>,
    onDismissRequest: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onRemoveFromQueue: (TrackItem) -> Unit = {},
    onClearQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Background,
        contentColor = EncoreColors.TextPrimary,
        shape = RoundedCornerShape(topStart = EncoreCornerRadii.Larger3, topEnd = EncoreCornerRadii.Larger3),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_chevron_down_24,
                    contentDescription = "Collapse queue",
                    onClick = onDismissRequest,
                    size = 40.dp,
                    iconSize = 24.dp,
                    tint = EncoreColors.TextPrimary
                )

                Text(
                    text = "Queue",
                    style = SpotifyTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                if (userQueue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text(
                            text = "Clear queue",
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.Green)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    Text(
                        text = "Now playing",
                        style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpotifyArtworkImage(
                            imageUrl = currentTrackCoverUrl,
                            contentDescription = currentTrackTitle,
                            modifier = Modifier.size(48.dp),
                            cornerRadius = EncoreCornerRadii.Base
                        )

                        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrackTitle,
                                style = SpotifyTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTrackArtist,
                                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        EncorePlayIndicator(
                            isPlaying = true,
                            color = EncoreColors.Green,
                            size = 14.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                }

                if (userQueue.isNotEmpty()) {
                    item {
                        Text(
                            text = "Next in queue",
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                        )
                    }

                    itemsIndexed(userQueue) { _, track ->
                        QueueTrackRow(
                            track = track,
                            showDragHandle = true,
                            onSelect = { onTrackSelect(track) },
                            onRemove = { onRemoveFromQueue(track) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                    }
                }

                if (nextFromContext.isNotEmpty()) {
                    item {
                        Text(
                            text = if (queueSourceContext.isBlank()) {
                                "Next up"
                            } else {
                                "Next from: $queueSourceContext"
                            },
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                        )
                    }

                    itemsIndexed(nextFromContext) { _, track ->
                        QueueTrackRow(
                            track = track,
                            showDragHandle = false,
                            onSelect = { onTrackSelect(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: TrackItem,
    showDragHandle: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpotifyArtworkImage(
            imageUrl = track.imageUrl,
            contentDescription = track.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = EncoreCornerRadii.Base
        )

        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = SpotifyTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    EncoreExplicitTag(modifier = Modifier.padding(end = 4.dp))
                }
                if (track.isEnhanced) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_enhance_badge_16),
                        contentDescription = "Smart Shuffle recommendation",
                        tint = EncoreColors.Green,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(12.dp)
                    )
                }
                Text(
                    text = track.artist,
                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onRemove != null) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_x_24,
                contentDescription = "Remove from queue",
                onClick = onRemove,
                size = 36.dp,
                iconSize = 18.dp,
                tint = EncoreColors.TextSecondary
            )
        }

        if (showDragHandle) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_sort_24),
                contentDescription = "Reorder",
                tint = EncoreColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview
@Composable
fun PreviewSpotifyQueueSheet() {
    SpotifyEncoreTheme {
        SpotifyQueueSheet(
            currentTrackTitle = "Starboy",
            currentTrackArtist = "The Weeknd, Daft Punk",
            queueSourceContext = "Today's Top Hits",
            userQueue = listOf(
                TrackItem("Blinding Lights", "The Weeknd"),
                TrackItem("Save Your Tears", "The Weeknd")
            ),
            nextFromContext = listOf(
                TrackItem("Espresso", "Sabrina Carpenter", true),
                TrackItem("Birds of a Feather", "Billie Eilish"),
                TrackItem("Good Luck, Babe!", "Chappell Roan")
            ),
            onDismissRequest = {},
            onTrackSelect = {}
        )
    }
}
