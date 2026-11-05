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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyTheme

data class EntityOptions(
    val title: String,
    val subtitle: String = "",
    val imageUrl: String? = null,
    val uri: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityOptionsSheet(
    entity: EntityOptions?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenInBrowser: () -> Unit = {},
    canEditPlaylist: Boolean = false,
    onEditPlaylist: () -> Unit = {},
    isSaved: Boolean? = null,
    onSaveToggle: () -> Unit = {},
    canMoveToFolder: Boolean = false,
    onMoveToFolder: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    canGoToRadio: Boolean = false,
    onGoToRadio: () -> Unit = {},
    canDeletePlaylist: Boolean = false,
    onDeletePlaylist: () -> Unit = {},
    isPublished: Boolean? = null,
    canPublish: Boolean = false,
    onPublishToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen || entity == null) return

    val isEpisode = entity.uri.startsWith("spotify:episode:")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpotifyArtworkImage(
                    imageUrl = entity.imageUrl,
                    contentDescription = entity.title,
                    modifier = Modifier.size(56.dp),
                    cornerRadius = 6.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.title,
                        style = SpotifyTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entity.subtitle.isNotBlank()) {
                        Text(
                            text = entity.subtitle,
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (canEditPlaylist) {
                EntityRow(
                    title = "Edit details",
                    iconRes = R.drawable.encore_icon_edit_24,
                    onClick = {
                        onDismiss()
                        onEditPlaylist()
                    }
                )
            }

            if (isEpisode && onAddToPlaylist != null) {
                AddToPlaylistRow {
                    onDismiss()
                    onAddToPlaylist()
                }
            }

            if (isSaved != null) {
                EntityRow(
                    title = when {
                        isEpisode && isSaved -> "Remove from Your Episodes"
                        isEpisode -> "Add to Your Episodes"
                        isSaved -> "Remove from Your Library"
                        else -> "Add to Your Library"
                    },
                    // a removal must read as destructive, never as a checkmark
                    iconRes = when {
                        isSaved -> R.drawable.encore_icon_trash_24
                        else -> R.drawable.encore_icon_plus_24
                    },
                    onClick = {
                        onDismiss()
                        onSaveToggle()
                    }
                )
            }

            if (canMoveToFolder) {
                EntityRow(
                    title = "Move to folder",
                    iconRes = R.drawable.encore_icon_folder_24,
                    onClick = {
                        onDismiss()
                        onMoveToFolder()
                    }
                )
            }

            if (onAddToQueue != null) {
                EntityRow(
                    title = "Add to queue",
                    iconRes = R.drawable.encore_icon_add_to_queue_24,
                    onClick = {
                        onDismiss()
                        onAddToQueue()
                    }
                )
            }

            if (canGoToRadio) {
                EntityRow(
                    title = "Go to artist radio",
                    iconRes = R.drawable.encore_icon_play_24,
                    onClick = {
                        onDismiss()
                        onGoToRadio()
                    }
                )
            }

            if (!isEpisode && onAddToPlaylist != null) {
                AddToPlaylistRow {
                    onDismiss()
                    onAddToPlaylist()
                }
            }

            if (canPublish && isPublished != null) {
                EntityRow(
                    title = if (isPublished) "Remove from profile" else "Add to profile",
                    iconRes = R.drawable.encore_icon_user_24,
                    onClick = {
                        onDismiss()
                        onPublishToggle()
                    }
                )
            }

            EntityRow(
                title = "Copy link",
                iconRes = R.drawable.encore_icon_link_24,
                onClick = {
                    onDismiss()
                    onCopyLink()
                }
            )

            EntityRow(
                title = "Share",
                iconRes = R.drawable.encore_icon_share_android_24,
                onClick = {
                    onDismiss()
                    onShare()
                }
            )

            EntityRow(
                title = "Open in browser",
                iconRes = R.drawable.encore_icon_more_android_24,
                onClick = {
                    onDismiss()
                    onOpenInBrowser()
                }
            )

            if (canDeletePlaylist) {
                HorizontalDivider(
                    color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                EntityRow(
                    title = "Delete playlist",
                    iconRes = R.drawable.encore_icon_x_24,
                    onClick = {
                        onDismiss()
                        onDeletePlaylist()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddToPlaylistRow(onClick: () -> Unit) {
    EntityRow(
        title = "Add to playlist",
        iconRes = R.drawable.encore_icon_add_to_playlist_24,
        onClick = onClick
    )
}

@Composable
private fun EntityRow(
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
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}
