package com.spotiduck.compose.library

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreChip
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncoreProfileAvatar
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.sheets.LibrarySortOption
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

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
fun SpotifyLibraryScreen(
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
    loading: Boolean = false,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    title: String = "Your Library",
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var isGridView by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchFilterText by remember { mutableStateOf("") }
    val bottomPadding = rememberBottomPadding()

    // Authentic Spotify Library filters (matching @string/your_library_content_filter_*)
    val filters = listOf("Playlists", "Podcasts", "Albums", "Artists", "Downloaded")

    val allLibraryItems = items.orEmpty()
    val filteredItems = remember(allLibraryItems, selectedFilter, searchFilterText, currentSort) {
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

        // Apply sort
        result = when (currentSort) {
            LibrarySortOption.ALPHABETICAL -> result.sortedBy { it.title }
            LibrarySortOption.CREATOR -> result.sortedBy { it.subtitle }
            else -> result // Recents / Recently added keeps default pinned order
        }
        result
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        // Header Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isSearchActive) {
                // In-Library Search Header (matching library_search_header_layout.xml)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back / Cancel icon button (icon_back, chevron_left)
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

                    // Search EditText field (@drawable/library_search_field_background, 48dp minHeight, cornerRadius 4dp, gray_15 #282828)
                    BasicTextField(
                        value = searchFilterText,
                        onValueChange = { searchFilterText = it },
                        textStyle = SpotifyTheme.typography.bodyMedium.copy(
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
                                            style = SpotifyTheme.typography.bodyMedium.copy(
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
            } else {
                // Standard Title Header: Profile Avatar + "Your Library" + Search + Plus
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
                        style = SpotifyTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // In-Library Search Icon (toggles search field)
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_search_24,
                        contentDescription = "Search in library",
                        onClick = { isSearchActive = true },
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = EncoreColors.TextPrimary
                    )

                    // Plus (+) Create Button
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

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips Row (matching your_library_content_filter_* tokens and library_chip_* dimensions)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedFilter != null) {
                    item {
                        // library_clear_chips_circle_size = 32dp, library_clear_chips_icon_size = 16dp
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EncoreColors.CardElevated)
                                .clickable { selectedFilter = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_x_24),
                                contentDescription = "Clear filter",
                                tint = EncoreColors.TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                items(filters) { filter ->
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

            Spacer(modifier = Modifier.height(12.dp))

            // Sort & View Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sort Button (Recents etc.)
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
                        style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
                    )
                }

                // Grid / List View Toggle Button
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

        // Library Items Content: Loading State, Empty State, List View OR Grid View
        if (loading && filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = EncoreColors.Green,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isSearchActive && searchFilterText.isNotEmpty()) {
                            "Couldn't find \"$searchFilterText\""
                        } else {
                            "Find your favorites"
                        },
                        style = SpotifyTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSearchActive && searchFilterText.isNotEmpty()) {
                            "Try searching again using a different spelling or keyword."
                        } else {
                            "Search everything you've saved, followed, or created."
                        },
                        style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                    )
                }
            }
        } else if (isGridView) {
            // 2-Column Grid View
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredItems) { item ->
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
                                .height(140.dp)
                                .clip(if (isArtist) CircleShape else RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(item.coverGradient)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.imageUrl != null) {
                                SpotifyArtworkImage(
                                    imageUrl = item.imageUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = if (isArtist) 70.dp else 6.dp
                                )
                            } else if (item.title == "Liked Songs") {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_heart_active_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else if (item.type == LibraryItemType.FOLDER) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_folder_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Text(
                                    text = if (isArtist) "★" else "♫",
                                    fontSize = 40.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.title,
                            style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = item.subtitle,
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            // Full-Width List View
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(filteredItems) { item ->
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
                                SpotifyArtworkImage(
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
                                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
                                        style = SpotifyTheme.typography.bodySmallBold.copy(
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
                                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
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
fun PreviewSpotifyLibraryScreen() {
    SpotifyEncoreTheme {
        SpotifyLibraryScreen(
            onItemClick = { _, _ -> },
            onProfileClick = {},
            onPlusClick = {},
            onSortClick = {}
        )
    }
}
