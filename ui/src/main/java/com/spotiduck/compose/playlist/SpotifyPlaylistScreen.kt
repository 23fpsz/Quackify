package com.spotiduck.compose.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import com.spotiduck.compose.components.SpotifyArtworkImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreHeartButton
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.EncoreTrackRow
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

data class TrackItem(
    val title: String,
    val artist: String,
    val isExplicit: Boolean = false,
    val uri: String = "",
    val imageUrl: String? = null
)

enum class PlaylistSort(val label: String) {
    CUSTOM("Custom order"),
    TITLE("Title"),
    ARTIST("Artist"),
    RECENTLY_ADDED("Recently added")
}

/**
 * 1:1 Spotify Playlist Screen matching decompiled:
 * - `playlist_header_layout.xml` (dynamic gradient, pinned scroll header)
 * - `playlist_all_songs_header_content.xml` (title auto-sizing, author metadata)
 * - `add_more_row_blend_playlist.xml` (interactive "Recommended" songs with add '+' button)
 * - In-playlist "Find in playlist" search bar & sort filter
 */
@Composable
fun SpotifyPlaylistScreen(
    title: String,
    description: String,
    creator: String,
    saveCount: String,
    tracks: List<TrackItem>,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    modifier: Modifier = Modifier,
    headerColor: Color = Color(0xFFE8115B),
    coverUrl: String? = null
) {
    var isLiked by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(PlaylistSort.CUSTOM) }
    val bottomPadding = rememberBottomPadding()

    // Editable track list to allow adding recommendations in real-time
    val currentTracks = remember(tracks) {
        mutableStateListOf<TrackItem>().apply { addAll(tracks) }
    }

    // Recommended tracks to add matching `add_more_row_blend_playlist.xml`
    val recommendedSongs = remember {
        mutableStateListOf(
            TrackItem("Taste", "Sabrina Carpenter", true),
            TrackItem("Diet Mountain Dew", "Lana Del Rey"),
            TrackItem("LUNCH", "Billie Eilish", true),
            TrackItem("Pink Pony Club", "Chappell Roan"),
            TrackItem("Casual", "Chappell Roan", true),
            TrackItem("Good Luck, Babe!", "Chappell Roan")
        )
    }

    val displayedTracks by remember {
        derivedStateOf {
            var list = if (searchQuery.isBlank()) {
                currentTracks.toList()
            } else {
                currentTracks.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
                }
            }
            when (sortOption) {
                PlaylistSort.TITLE -> list.sortedBy { it.title }
                PlaylistSort.ARTIST -> list.sortedBy { it.artist }
                else -> list
            }
        }
    }

    val listState = rememberLazyListState()
    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 380
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            headerColor,
            headerColor.copy(alpha = 0.6f),
            EncoreColors.Background,
            EncoreColors.Background
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        val topBarBgColor by animateColorAsState(
            targetValue = if (isScrolledPastHeader) headerColor.copy(alpha = 0.98f) else Color.Transparent,
            animationSpec = tween(durationMillis = 200),
            label = "playlistTopBarBg"
        )

        // Pinned Top App Bar (Title fades in when scrolled past header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarBgColor)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_arrow_left_24,
                    contentDescription = "Back",
                    onClick = onBackClick,
                    tint = EncoreColors.TextPrimary
                )

                AnimatedVisibility(
                    visible = isScrolledPastHeader,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Secondary Play Button in pinned top bar when scrolled
                AnimatedVisibility(
                    visible = isScrolledPastHeader,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    EncorePlayButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPauseClick,
                        size = 40.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_search_24,
                    contentDescription = "Search playlist",
                    onClick = { isSearchVisible = !isSearchVisible },
                    tint = EncoreColors.TextPrimary
                )
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_more_android_24,
                    contentDescription = "Playlist options",
                    onClick = {},
                    tint = EncoreColors.TextPrimary
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            // Header Item: Cover Art + Title + Description + Meta + Actions
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Square Cover Image (160dp x 160dp)
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterHorizontally)
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(headerColor.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverUrl.isNullOrBlank()) {
                            SpotifyArtworkImage(
                                imageUrl = coverUrl,
                                contentDescription = title,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 4.dp
                            )
                        } else {
                            Text(
                                text = "♫",
                                fontSize = 60.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        style = SpotifyTheme.typography.headlineLarge
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = description,
                        style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Creator + Saves row with avatar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_user_24),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Text(
                            text = creator,
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
                        )
                        Text(
                            text = "• $saveCount",
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Row (Heart, Download, Add Collaborator, More, Shuffle, Play Button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EncoreHeartButton(
                                isLiked = isLiked,
                                onLikeToggle = { isLiked = !isLiked },
                                size = 40.dp,
                                iconSize = 24.dp
                            )

                            EncoreIconButton(
                                iconRes = if (isDownloaded) R.drawable.encore_icon_downloaded_24 else R.drawable.encore_icon_download_24,
                                contentDescription = "Download",
                                onClick = { isDownloaded = !isDownloaded },
                                isActive = isDownloaded,
                                activeTint = EncoreColors.Green,
                                tint = EncoreColors.TextSecondary
                            )

                            EncoreIconButton(
                                iconRes = R.drawable.encore_icon_user_24,
                                contentDescription = "Add collaborators",
                                onClick = {},
                                tint = EncoreColors.TextSecondary
                            )

                            EncoreIconButton(
                                iconRes = R.drawable.encore_icon_more_android_24,
                                contentDescription = "More",
                                onClick = {},
                                tint = EncoreColors.TextSecondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EncoreIconButton(
                                iconRes = R.drawable.encore_icon_shuffle_24,
                                contentDescription = "Shuffle",
                                onClick = { isShuffle = !isShuffle },
                                isActive = isShuffle,
                                activeTint = EncoreColors.Green,
                                tint = EncoreColors.TextSecondary
                            )

                            EncorePlayButton(
                                isPlaying = isPlaying,
                                onClick = onPlayPauseClick,
                                size = 52.dp,
                                iconSize = 26.dp,
                                showShuffleBadge = isShuffle
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Find in playlist" search bar & Sort Filter Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search Box (matches modern_search_header_field_background)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF282828))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_search_24),
                                    contentDescription = null,
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Find in playlist",
                                            style = SpotifyTheme.typography.bodySmall.copy(
                                                color = EncoreColors.TextSecondary
                                            )
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        cursorBrush = SolidColor(EncoreColors.Green),
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_x_24),
                                        contentDescription = "Clear search",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }

                        // Sort Button (cycles sort options on tap)
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF282828))
                                .clickable {
                                    sortOption = when (sortOption) {
                                        PlaylistSort.CUSTOM -> PlaylistSort.TITLE
                                        PlaylistSort.TITLE -> PlaylistSort.ARTIST
                                        PlaylistSort.ARTIST -> PlaylistSort.RECENTLY_ADDED
                                        PlaylistSort.RECENTLY_ADDED -> PlaylistSort.CUSTOM
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_sort_24),
                                    contentDescription = "Sort playlist",
                                    tint = if (sortOption != PlaylistSort.CUSTOM) EncoreColors.Green else EncoreColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sortOption.label,
                                    style = SpotifyTheme.typography.marginal.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (sortOption != PlaylistSort.CUSTOM) EncoreColors.Green else Color.White,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Playlist Track rows
            if (displayedTracks.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Couldn't find \"$searchQuery\"",
                            style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching for a different track title or artist",
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                }
            } else {
                itemsIndexed(displayedTracks) { index, track ->
                    val isCurrentTrack = track.title == currentTrackTitle
                    EncoreTrackRow(
                        trackNumber = index + 1,
                        title = track.title,
                        artist = track.artist,
                        isPlaying = isCurrentTrack && isPlaying,
                        isExplicit = track.isExplicit,
                        imageUrl = track.imageUrl,
                        onClick = { onTrackSelect(track) },
                        onMoreClick = { onTrackOptionsClick(track) }
                    )
                }
            }

            // "Recommended" Songs Section matching add_more_row_blend_playlist.xml
            if (recommendedSongs.isNotEmpty() && searchQuery.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Text(
                            text = "Recommended",
                            style = SpotifyTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Based on what's in this playlist",
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                }

                itemsIndexed(recommendedSongs) { _, recTrack ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackSelect(recTrack) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork 48dp
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF242424)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (recTrack.imageUrl != null) {
                                SpotifyArtworkImage(
                                    imageUrl = recTrack.imageUrl,
                                    contentDescription = recTrack.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            } else {
                                Text(text = "♫", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title + Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recTrack.title,
                                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (recTrack.isExplicit) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "E", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                                Text(
                                    text = recTrack.artist,
                                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Circular '+' Add Button to instantly add to playlist
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .clickable {
                                    currentTracks.add(recTrack)
                                    recommendedSongs.remove(recTrack)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_plus_24),
                                contentDescription = "Add to playlist",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                item {
                    // Refresh Recommendations Button (matches Spotify white-bordered pill)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                                .clickable {
                                    // Refresh list
                                    recommendedSongs.clear()
                                    recommendedSongs.addAll(
                                        listOf(
                                            TrackItem("Please Please Please", "Sabrina Carpenter", true),
                                            TrackItem("Birds of a Feather", "Billie Eilish"),
                                            TrackItem("Espresso", "Sabrina Carpenter", true),
                                            TrackItem("Chihiro", "Billie Eilish"),
                                            TrackItem("Femininomenon", "Chappell Roan", true)
                                        )
                                    )
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Refresh",
                                style = SpotifyTheme.typography.bodySmallBold.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyPlaylistScreen() {
    SpotifyEncoreTheme {
        val dummyTracks = listOf(
            TrackItem("Espresso", "Sabrina Carpenter", true),
            TrackItem("Birds of a Feather", "Billie Eilish"),
            TrackItem("Good Luck, Babe!", "Chappell Roan"),
            TrackItem("Please Please Please", "Sabrina Carpenter", true),
            TrackItem("Not Like Us", "Kendrick Lamar", true),
            TrackItem("A Bar Song (Tipsy)", "Shaboozey", true),
            TrackItem("Too Sweet", "Hozier")
        )

        SpotifyPlaylistScreen(
            title = "Today's Top Hits",
            description = "Sabrina Carpenter is on top of the Hottest 50!",
            creator = "Spotify",
            saveCount = "34,251,029 saves • 3h 15m",
            tracks = dummyTracks,
            currentTrackTitle = "Espresso",
            isPlaying = true,
            onBackClick = {},
            onPlayPauseClick = {},
            onTrackSelect = {}
        )
    }
}
