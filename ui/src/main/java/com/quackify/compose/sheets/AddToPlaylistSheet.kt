package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.backend.model.PlaylistChoice
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    isOpen: Boolean,
    trackTitle: String,
    choices: List<PlaylistChoice>,
    isLoading: Boolean,
    onPick: (PlaylistChoice) -> Unit,
    onLoadFolder: (suspend (folderUri: String) -> List<PlaylistChoice>)? = null,
    // Answers "how many songs does this playlist hold" for the rows in view;
    // the sheet asks per uri so the counts work inside folders too.
    onLoadTrackCount: (suspend (playlistUri: String) -> Int)? = null,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Folder drill-down: the stack remembers every level above the one shown,
    // each a folder plus the rows it listed, so Back climbs out exactly the
    // way it came in.
    var currentFolder by remember { mutableStateOf<PlaylistChoice?>(null) }
    var currentRows by remember { mutableStateOf<List<PlaylistChoice>?>(null) }
    val parentLevels = remember { mutableStateListOf<Pair<PlaylistChoice?, List<PlaylistChoice>>>() }
    var folderLoading by remember { mutableStateOf(false) }

    val rows = currentRows ?: choices
    val inFolder = currentFolder != null

    // Song count per playlist row, hydrated while the list is on screen.
    // Counts are remembered by uri, so drilling into a folder and back out
    // shows the root's counts again without another round trip.
    val trackCounts = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(rows) {
        val loader = onLoadTrackCount ?: return@LaunchedEffect
        rows.asSequence()
            .filter { it.kind != PlaylistChoice.Kind.FOLDER && !trackCounts.containsKey(it.uri) }
            .map { it.uri }
            .toList()
            .chunked(6)
            .forEach { batch ->
                coroutineScope {
                    batch.map { uri ->
                        async {
                            val count = loader(uri)
                            if (count >= 0) trackCounts[uri] = count
                        }
                    }.awaitAll()
                }
            }
    }

    fun openFolder(folder: PlaylistChoice) {
        val loader = onLoadFolder
        if (loader == null || folderLoading) return
        scope.launch {
            folderLoading = true
            val loaded = loader(folder.uri)
            parentLevels += currentFolder to (currentRows ?: choices)
            currentFolder = folder
            currentRows = loaded
            folderLoading = false
        }
    }

    fun backOutOfFolder() {
        val parent = parentLevels.removeLastOrNull() ?: return
        currentFolder = parent.first
        currentRows = parent.second
    }

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
                .navigationBarsPadding()
        ) {
            Text(
                text = currentFolder?.name ?: "Add to playlist",
                style = SpotifyTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = trackTitle,
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(EncoreColors.PureBlack.copy(alpha = 0.5f))
            )

            when {
                isLoading || folderLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InlineLoadingDots(color = EncoreColors.TextSecondary)
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    if (inFolder) {
                        // Way back out of the folder, above its rows.
                        item(key = "__back__") {
                            val parentName = parentLevels.lastOrNull()?.first?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { backOutOfFolder() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_chevron_left_24),
                                    contentDescription = null,
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = parentName?.let { "Back to $it" } ?: "Back to your playlists",
                                    style = SpotifyTheme.typography.bodyMedium.copy(
                                        color = EncoreColors.TextSecondary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        item(key = "__new__") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCreateNew() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(EncoreColors.TextSecondary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_plus_24),
                                        contentDescription = "New playlist",
                                        tint = EncoreColors.TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = "New playlist",
                                    style = SpotifyTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                    color = EncoreColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (rows.isEmpty()) {
                        item(key = "__empty__") {
                            Text(
                                text = if (inFolder) "This folder is empty"
                                else "No playlists you can add to",
                                style = SpotifyTheme.typography.bodyMedium.copy(
                                    color = EncoreColors.TextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    items(rows, key = { it.uri }) { choice ->
                        if (choice.kind == PlaylistChoice.Kind.FOLDER) {
                            AddToFolderRow(folder = choice, onOpen = { openFolder(choice) })
                        } else {
                            AddToPlaylistRow(
                                choice = choice,
                                songCount = trackCounts[choice.uri],
                                onPick = { onPick(choice) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * A playlist (or the Liked Songs pseudo playlist) the track can be added to.
 * The green check marks the ones the answer flagged as already holding it,
 * and the right-aligned count says how many songs it holds right now.
 */
@Composable
private fun AddToPlaylistRow(
    choice: PlaylistChoice,
    songCount: Int?,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SpotifyArtworkImage(
            imageUrl = choice.imageUrl.takeIf { it.isNotBlank() },
            contentDescription = choice.name,
            modifier = Modifier.size(44.dp),
            cornerRadius = 4.dp
        )
        Text(
            text = choice.name,
            style = SpotifyTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = EncoreColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (songCount != null && songCount >= 0) {
            Text(
                text = "$songCount song" + if (songCount == 1) "" else "s",
                style = SpotifyTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary
                ),
                maxLines = 1
            )
        }
        if (choice.containsTrack) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_check_24),
                contentDescription = "Already in ${choice.name}",
                tint = EncoreColors.Green,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
}

/** A library folder: picking it drills into the playlists inside it. */
@Composable
private fun AddToFolderRow(
    folder: PlaylistChoice,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(EncoreColors.TextSecondary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_folder_24),
                contentDescription = null,
                tint = EncoreColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = folder.name,
            style = SpotifyTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = EncoreColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = R.drawable.encore_icon_chevron_right_24),
            contentDescription = "Open ${folder.name}",
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
