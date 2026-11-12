package com.quackify.compose.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreChip
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncoreProfileAvatar
import com.quackify.compose.components.LibraryGridSkeleton
import com.quackify.compose.components.LibraryListSkeleton
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.sheets.LibrarySortOption
import com.quackify.compose.sheets.SortDirection
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding

enum class LibraryItemType {
    PLAYLIST,
    PODCAST,
    ALBUM,
    ARTIST,
    FOLDER
}

data class LibraryItem(
    val title: String,
    val subtitle: String,
    val type: LibraryItemType,
    val isPinned: Boolean = false,
    val isDownloaded: Boolean = false,
    val uri: String = "",
    val imageUrl: String? = null,
    val coverGradient: List<Color> = listOf(Color(0xFF2E77D0), Color(0xFF1E3264))
)

/**
 * Spotify Your Library Screen matching Spotify APK:
 * - Profile avatar shortcut
 * - In-library search filter
 * - Plus (+) create menu
 * - Filter pills (Playlists, Podcasts, Albums, Artists, Downloaded)
 * - Sort button ("Recents" etc.) & Grid vs List view toggle
 * - Dynamic List / 2-Column Grid views
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EncoreLibraryScreen(
    onItemClick: (String, String) -> Unit,
    items: List<LibraryItem>? = null,
    onAlbumClick: (String, String, String) -> Unit = { _, _, _ -> },
    onPodcastClick: (String, String) -> Unit = { _, _ -> },
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onProfileClick: () -> Unit = {},
    onPlusClick: () -> Unit = {},
    onSortClick: () -> Unit = {},
    onItemLongPress: (LibraryItem) -> Unit = {},
    onFolderClick: (LibraryItem) -> Unit = {},
    currentSort: LibrarySortOption = LibrarySortOption.RECENTS,
    sortDirection: SortDirection = SortDirection.DESCENDING,
    loading: Boolean = false,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    title: String = "Your Library",
    isFolder: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onFolderOptionsClick: (() -> Unit)? = null,
    onPlayClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var isGridView by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchFilterText by remember { mutableStateOf("") }
    val bottomPadding = rememberBottomPadding()

    val filters = listOf("Playlists", "Podcasts", "Albums", "Artists", "Downloaded")

    val allLibraryItems = items.orEmpty()
    val filteredItems = remember(allLibraryItems, selectedFilter, searchFilterText, currentSort, sortDirection) {
        var result = allLibraryItems.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "Playlists" -> item.type == LibraryItemType.PLAYLIST || item.type == LibraryItemType.FOLDER
                "Podcasts" -> item.type == LibraryItemType.PODCAST
                "Albums" -> item.type == LibraryItemType.ALBUM
                "Artists" -> item.type == LibraryItemType.ARTIST
                "Downloaded" -> item.isDownloaded
                else -> true
            }
            val matchesSearch = searchFilterText.isBlank() ||
                    item.title.contains(searchFilterText, ignoreCase = true) ||
                    item.subtitle.contains(searchFilterText, ignoreCase = true)
            matchesFilter && matchesSearch
        }

        result = when (currentSort) {
            LibrarySortOption.ALPHABETICAL -> {
                if (sortDirection == SortDirection.ASCENDING) {
                    result.sortedBy { it.title.lowercase() }
                } else {
                    result.sortedByDescending { it.title.lowercase() }
                }
            }
            LibrarySortOption.CREATOR -> {
                if (sortDirection == SortDirection.ASCENDING) {
                    result.sortedBy { it.subtitle.lowercase() }
                } else {
                    result.sortedByDescending { it.subtitle.lowercase() }
                }
            }
            LibrarySortOption.RECENTLY_ADDED -> {
                if (sortDirection == SortDirection.DESCENDING) {
                    result
                } else {
                    result.reversed()
                }
            }
            LibrarySortOption.RECENTS -> {
                if (sortDirection == SortDirection.DESCENDING) {
                    result
                } else {
                    result.reversed()
                }
            }
        }
        result
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_chevron_left_24,
                        contentDescription = "Cancel search",
                        onClick = {
                            isSearchActive = false
                            searchFilterText = ""
                        },
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = EncoreColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    BasicTextField(
                        value = searchFilterText,
                        onValueChange = { searchFilterText = it },
                        textStyle = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(EncoreColors.Green),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF282828))
                                    .padding(start = 12.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchFilterText.isEmpty()) {
                                        Text(
                                            text = "Search Your Library",
                                            style = EncoreTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB3B3B3)
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                                if (searchFilterText.isNotEmpty()) {
                                    EncoreIconButton(
                                        iconRes = R.drawable.encore_icon_x_24,
                                        contentDescription = "Clear",
                                        onClick = { searchFilterText = "" },
                                        size = 32.dp,
                                        iconSize = 16.dp,
                                        tint = Color(0xFFB3B3B3)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (isFolder) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_chevron_left_24,
                        contentDescription = "Back",
                        onClick = { onBackClick?.invoke() },
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = EncoreColors.TextPrimary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = title,
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_more_android_24,
                        contentDescription = "Folder options",
                        onClick = { onFolderOptionsClick?.invoke() },
                        size = 40.dp,
                        iconSize = 22.dp,
                        tint = EncoreColors.TextPrimary
                    )

                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_plus_24,
                        contentDescription = "Create",
                        onClick = onPlusClick,
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = EncoreColors.TextPrimary
                    )

                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_play_24,
                        contentDescription = "Play",
                        onClick = { onPlayClick?.invoke() },
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = if (allLibraryItems.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.38f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreProfileAvatar(
                        userDisplayName = userDisplayName,
                        avatarUrl = userAvatarUrl.ifBlank { null },
                        onClick = onProfileClick
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_search_24,
                        contentDescription = "Search in library",
                        onClick = { isSearchActive = true },
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = EncoreColors.TextPrimary
                    )

                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_plus_24,
                        contentDescription = "Create",
                        onClick = onPlusClick,
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = EncoreColors.TextPrimary
                    )
                }
            }

            if (!isFolder) {
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(filters, key = { it }) { filter ->
                        val isSelected = selectedFilter == filter
                        EncoreChip(
                            text = filter,
                            isSelected = isSelected,
                            onClick = {
                                selectedFilter = if (isSelected) null else filter
                            }
                        )
                    }
                }
            }

            if (!isFolder || filteredItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onSortClick)
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_sort_24),
                            contentDescription = "Sort order",
                            tint = EncoreColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentSort.displayName,
                            style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
                        )
                        if (currentSort.reversible) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(
                                    id = if (sortDirection == SortDirection.ASCENDING) {
                                        R.drawable.encore_icon_arrow_up_24
                                    } else {
                                        R.drawable.encore_icon_arrow_down_24
                                    }
                                ),
                                contentDescription = if (sortDirection == SortDirection.ASCENDING) "Sorted ascending" else "Sorted descending",
                                tint = EncoreColors.TextPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    EncoreIconButton(
                        iconRes = if (isGridView) R.drawable.encore_icon_list_view_24 else R.drawable.encore_icon_grid_view_24,
                        contentDescription = if (isGridView) "List view" else "Grid view",
                        onClick = { isGridView = !isGridView },
                        tint = EncoreColors.TextPrimary,
                        size = 32.dp,
                        iconSize = 18.dp
                    )
                }
            }
        }

        if (loading && filteredItems.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
            ) {
                item {
                    if (isGridView) {
                        LibraryGridSkeleton(rows = 5)
                    } else {
                        LibraryListSkeleton(rows = 9)
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                if (isFolder) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "This folder looks empty",
                            style = EncoreTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add playlists from Your Library.",
                            style = EncoreTheme.typography.bodyMedium.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 14.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isSearchActive && searchFilterText.isNotEmpty()) {
                                "Couldn't find \"$searchFilterText\""
                            } else {
                                "Find your favorites"
                            },
                            style = EncoreTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isSearchActive && searchFilterText.isNotEmpty()) {
                                "Try searching again using a different spelling or keyword."
                            } else {
                                "Search everything you've saved, followed, or created."
                            },
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredItems, key = { it.uri }) { item ->
                    val isArtist = item.type == LibraryItemType.ARTIST
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    when (item.type) {
                                        LibraryItemType.ALBUM -> onAlbumClick(item.title, "", item.uri)
                                        LibraryItemType.PODCAST -> onPodcastClick(item.title, item.uri)
                                        LibraryItemType.ARTIST -> onArtistClick(item.title, item.uri)
                                        LibraryItemType.FOLDER -> onFolderClick(item)
                                        else -> onItemClick(item.title, item.uri)
                                    }
                                },
                                onLongClick = { onItemLongPress(item) }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(if (isArtist) CircleShape else RoundedCornerShape(4.dp))
                                .background(Brush.linearGradient(item.coverGradient)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.imageUrl != null) {
                                EncoreArtworkImage(
                                    imageUrl = item.imageUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = if (isArtist) 999.dp else 4.dp
                                )
                            } else if (item.title == "Liked Songs") {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_heart_active_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else if (item.type == LibraryItemType.FOLDER) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_folder_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Text(
                                    text = if (isArtist) "★" else "♫",
                                    fontSize = 28.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.title,
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(1.dp))

                        Text(
                            text = item.subtitle,
                            style = EncoreTheme.typography.bodySmall.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(filteredItems, key = { it.uri }) { item ->
                    val isArtist = item.type == LibraryItemType.ARTIST
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    when (item.type) {
                                        LibraryItemType.ALBUM -> onAlbumClick(item.title, "", item.uri)
                                        LibraryItemType.PODCAST -> onPodcastClick(item.title, item.uri)
                                        LibraryItemType.ARTIST -> onArtistClick(item.title, item.uri)
                                        LibraryItemType.FOLDER -> onFolderClick(item)
                                        else -> onItemClick(item.title, item.uri)
                                    }
                                },
                                onLongClick = { onItemLongPress(item) }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(if (isArtist) CircleShape else RoundedCornerShape(4.dp))
                                .background(Brush.linearGradient(item.coverGradient)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.imageUrl != null) {
                                EncoreArtworkImage(
                                    imageUrl = item.imageUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = if (isArtist) 32.dp else 4.dp
                                )
                            } else if (item.title == "Liked Songs") {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_heart_active_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (item.type == LibraryItemType.FOLDER) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_folder_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Text(
                                    text = if (isArtist) "★" else "♫",
                                    fontSize = 24.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.isPinned) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_pin_24),
                                        contentDescription = "Pinned",
                                        tint = EncoreColors.Green,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Pinned • ",
                                        style = EncoreTheme.typography.bodySmallBold.copy(
                                            color = EncoreColors.Green,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                                if (item.isDownloaded) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_download_24),
                                        contentDescription = "Downloaded",
                                        tint = EncoreColors.Green,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = item.subtitle,
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncoreLibraryScreen() {
    EncoreAppTheme {
        EncoreLibraryScreen(
            onItemClick = { _, _ -> },
            onProfileClick = {},
            onPlusClick = {},
            onSortClick = {}
        )
    }
}
