package com.spotiduck.compose.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.EncoreTrackRow
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding
import kotlinx.coroutines.launch

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
 * 1:1 Pixel-Perfect Spotify Mobile Playlist Screen:
 * - Top Bar: Back button, Centered Title (when scrolled), Green Play FAB (when scrolled) / 3-dots (when at top)
 * - Sticky Header: Filter chips row ([+ Add], [≡ Edit], [✏ Name & details], [⇅ Sort]) pins directly below Top Bar
 * - Dynamic Artwork Shrink: smoothly scales down from 230dp to 100dp with alpha fade as user scrolls
 * - Magnetic Snapping Search Bar: starts hidden; swiping/pulling down snaps it 100% open or closed
 * - Gradient strictly confined to header/play button area, fading completely to solid #121212 black
 * - Action controls row: mini stacked artwork card, (↓) download circle, share, shuffle, and 56dp Green Play FAB
 * - Standard 60dp track rows with 48dp artwork, title, artist, [E] badge, and vertical 3-dots (⋮)
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onPlaylistOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    headerColor: Color = Color(0xFF144D3F),
    coverUrl: String? = null
) {
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isDownloaded by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(PlaylistSort.CUSTOM) }
    var isSearchRevealed by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val scope = rememberCoroutineScope()

    // Handle back button when in search mode
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    // Editable track list
    val currentTracks = remember(tracks) {
        mutableStateListOf<TrackItem>().apply { addAll(tracks) }
    }

    // Recommended tracks matching Spotify
    val recommendedSongs = remember {
        mutableStateListOf(
            TrackItem("Faasle", "Aditya Rikhari", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
            TrackItem("Alag Aasmaan", "Anuv Jain", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
            TrackItem("Another Love", "Tom Odell", true, imageUrl = "https://i.scdn.co/image/ab67616d0000b27320b57e4975f9ee73b64f3319"),
            TrackItem("Tu hai kahan", "AUR", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b273b0dd6a5cd1dec96c4119c262"),
            TrackItem("Maybe My Soulmate Died", "iamnotshane", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b2734ab25200366c1b3f942d937a"),
            TrackItem("Farq hai", "Suzonn", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b273523ec3e30f1489ecde3a9aa8")
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

    // Scroll calculations for fluid header collapse and artwork shrink
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1000f
        }
    }

    val scrollOffsetDp by remember {
        derivedStateOf {
            scrollOffset / 2.75f
        }
    }

    val shrinkProgress by remember {
        derivedStateOf {
            (scrollOffsetDp / 180f).coerceIn(0f, 1f)
        }
    }

    val artworkSize by remember {
        derivedStateOf {
            (230 - (120 * shrinkProgress)).dp
        }
    }

    val artworkAlpha by remember {
        derivedStateOf {
            (1f - (shrinkProgress * 0.95f)).coerceIn(0f, 1f)
        }
    }

    val topBarAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f else (scrollOffsetDp / 140f).coerceIn(0f, 1f)
        }
    }

    val topBarTitleAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f else ((scrollOffsetDp - 140f) / 100f).coerceIn(0f, 1f)
        }
    }

    val showStickyPlayButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || scrollOffsetDp > 260f
        }
    }

    // Magnetic snap nested scroll connection for "Find in playlist" search bar
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When scrolling down content (swiping up), snap search bar closed
                if (available.y < -12f && isSearchRevealed) {
                    isSearchRevealed = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // When at the very top and pulling down, snap search bar open like a magnet
                if (available.y > 18f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    isSearchRevealed = true
                }
                return Offset.Zero
            }
        }
    }

    // Search bar height animation (snaps like a magnet, never in half)
    val animatedSearchHeight by animateDpAsState(
        targetValue = if (isSearchRevealed) 48.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "magneticSearchSnap"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .nestedScroll(nestedScrollConnection)
    ) {
        if (isSearching) {
            // ==========================================
            // SEARCH MODE (Image 3)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF121212))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isSearching = false
                    searchQuery = ""
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                        contentDescription = "Exit search",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

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
                                    style = SpotifyTheme.typography.bodyMedium.copy(
                                        color = EncoreColors.TextSecondary,
                                        fontSize = 14.sp
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
                                    fontSize = 14.sp,
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

                IconButton(onClick = onPlaylistOptionsClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                        contentDescription = "Playlist options",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                if (displayedTracks.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp, horizontal = 24.dp),
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
                    itemsIndexed(displayedTracks) { _, track ->
                        val isCurrentTrack = track.title == currentTrackTitle
                        EncoreTrackRow(
                            trackNumber = null,
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
            }
        } else {
            // ==========================================
            // NORMAL PLAYLIST MODE (Image 1, 2, 4, 5 & Latest Screenshot)
            // ==========================================

            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val topBarBgColor = headerColor.copy(alpha = topBarAlpha * 0.98f)
            val circleBtnAlpha = ((1f - topBarAlpha) * 0.35f).coerceIn(0f, 0.35f)

            val stickyPlayButtonAlpha by animateFloatAsState(
                targetValue = if (showStickyPlayButton) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "stickyPlayAlpha"
            )
            val topBarThreeDotsAlpha = (1f - stickyPlayButtonAlpha).coerceIn(0f, 1f)

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121212))
                ) {
                    // Pinned Top Navigation Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(topBarBgColor)
                            .statusBarsPadding()
                            .height(54.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Button (Locked in place at 16dp from border)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = circleBtnAlpha))
                                .clickable(onClick = onBackClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Centered Title in Top Bar when scrolled (Image 5 & Latest Screenshot)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (topBarTitleAlpha > 0.01f) {
                                Text(
                                    text = title,
                                    style = SpotifyTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 17.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.graphicsLayer { alpha = topBarTitleAlpha }
                                )
                            }
                        }

                        // 3-Dots Button (smoothly fades out as sticky play button activates)
                        Box(
                            modifier = Modifier.size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (topBarThreeDotsAlpha > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = circleBtnAlpha * topBarThreeDotsAlpha))
                                        .clickable(onClick = onPlaylistOptionsClick)
                                        .graphicsLayer { alpha = topBarThreeDotsAlpha },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                                        contentDescription = "Playlist options",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Scrollable LazyColumn with Collapsing Header, Sticky Chips Bar, and Tracks
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding)
                    ) {
                // Header Item: Gradient strictly confined to header area (up to play button)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        headerColor,
                                        headerColor.copy(alpha = 0.85f),
                                        headerColor.copy(alpha = 0.35f),
                                        Color(0xFF121212)
                                    ),
                                    startY = 0f,
                                    endY = 1500f
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            // Magnetic Snapping Search Bar (Image 2)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedSearchHeight)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (animatedSearchHeight > 10.dp) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.14f))
                                            .clickable { isSearching = true }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.encore_icon_search_24),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Find in playlist",
                                                style = SpotifyTheme.typography.bodyMedium.copy(
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Large Centered Cover Artwork (shrinks dynamically in size and fades out on scroll)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(artworkSize)
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(artworkSize)
                                        .graphicsLayer {
                                            alpha = artworkAlpha
                                        }
                                        .shadow(elevation = (14 * (1f - shrinkProgress)).dp, shape = RoundedCornerShape(4.dp))
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
                                            fontSize = 72.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Playlist Title (Left-aligned, bold, 24sp)
                            Text(
                                text = title,
                                style = SpotifyTheme.typography.headlineLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Author / Collaborator Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                // (+) Add Collaborator Circle Pill Button
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable { onPlaylistOptionsClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_plus_24),
                                        contentDescription = "Add collaborators",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Avatar Circle
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF333333)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_user_24),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Creator Name
                                Text(
                                    text = creator,
                                    style = SpotifyTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Metadata Row: 🌐 Globe + Save Count & Duration
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "🌐",
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "$saveCount • 10h 1min",
                                    style = SpotifyTheme.typography.bodySmall.copy(
                                        color = EncoreColors.TextSecondary,
                                        fontSize = 12.5.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action Controls Row: Mini Card, Download (↓), Share | Shuffle, Big Green Play FAB
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Mini Stacked Playlist Preview Card
                                    Box(
                                        modifier = Modifier
                                            .size(width = 30.dp, height = 38.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF282828)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!coverUrl.isNullOrBlank()) {
                                            SpotifyArtworkImage(
                                                imageUrl = coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                cornerRadius = 3.dp
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(headerColor.copy(alpha = 0.8f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("♫", fontSize = 14.sp, color = Color.White)
                                            }
                                        }
                                    }

                                    // Download Circle Button (↓)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (isDownloaded) EncoreColors.Green else Color.White.copy(alpha = 0.12f))
                                            .clickable { isDownloaded = !isDownloaded },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (isDownloaded) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_download_24
                                            ),
                                            contentDescription = "Download",
                                            tint = if (isDownloaded) Color.Black else EncoreColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Share Button (<)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                            .clickable(onClick = onPlaylistOptionsClick),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_share_android_24),
                                            contentDescription = "Share",
                                            tint = EncoreColors.TextSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Shuffle Button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable { isShuffle = !isShuffle },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_shuffle_24),
                                            contentDescription = "Shuffle",
                                            tint = if (isShuffle) EncoreColors.Green else EncoreColors.TextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // Big Spotify Green Play FAB (52dp)
                                    EncorePlayButton(
                                        isPlaying = isPlaying,
                                        onClick = onPlayPauseClick,
                                        size = 52.dp,
                                        iconSize = 24.dp,
                                        showShuffleBadge = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }

                // Sticky Header: Filter Chips Bar pinned directly below the Top Bar (Matches Latest Screenshot)
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(16.dp))

                            PlaylistChip(
                                iconRes = R.drawable.encore_icon_plus_24,
                                label = "Add",
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(displayedTracks.size + 2)
                                    }
                                }
                            )

                            PlaylistChip(
                                iconRes = R.drawable.encore_icon_list_view_24,
                                label = "Edit",
                                onClick = onPlaylistOptionsClick
                            )

                            PlaylistChip(
                                iconRes = R.drawable.encore_icon_edit_24,
                                label = "Name & details",
                                onClick = onPlaylistOptionsClick
                            )

                            PlaylistChip(
                                iconRes = R.drawable.encore_icon_sort_24,
                                label = "Sort",
                                onClick = {
                                    sortOption = when (sortOption) {
                                        PlaylistSort.CUSTOM -> PlaylistSort.TITLE
                                        PlaylistSort.TITLE -> PlaylistSort.ARTIST
                                        PlaylistSort.ARTIST -> PlaylistSort.CUSTOM
                                        else -> PlaylistSort.CUSTOM
                                    }
                                }
                            )

                            // Generous end padding so Sort chip can be scrolled completely clear of the sticky play button
                            Spacer(modifier = Modifier.width(72.dp))
                        }
                    }
                }

                // Track Rows (Solid pure #121212 background, 60dp standard row height, scrolls under sticky chips)
                itemsIndexed(displayedTracks) { _, track ->
                    val isCurrentTrack = track.title == currentTrackTitle
                    EncoreTrackRow(
                        trackNumber = null,
                        title = track.title,
                        artist = track.artist,
                        isPlaying = isCurrentTrack && isPlaying,
                        isExplicit = track.isExplicit,
                        imageUrl = track.imageUrl,
                        onClick = { onTrackSelect(track) },
                        onMoreClick = { onTrackOptionsClick(track) }
                    )
                }

                // "Recommended" Section (add_more_row_blend_playlist.xml)
                if (recommendedSongs.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Text(
                                text = "Recommended",
                                style = SpotifyTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
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
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF242424)),
                                contentAlignment = Alignment.Center
                            ) {
                                SpotifyArtworkImage(
                                    imageUrl = recTrack.imageUrl,
                                    contentDescription = recTrack.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recTrack.title,
                                    style = SpotifyTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (recTrack.isExplicit) {
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .size(13.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "E",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                    Text(
                                        text = recTrack.artist,
                                        style = SpotifyTheme.typography.bodySmall.copy(
                                            color = EncoreColors.TextSecondary,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
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
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    item {
                        // Refresh Recommendations Button
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
                                        recommendedSongs.clear()
                                        recommendedSongs.addAll(
                                            listOf(
                                                TrackItem("Khaab", "Akhil", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
                                                TrackItem("Asal Mein", "Darshan Raval", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
                                                TrackItem("Bye", "Aditya Bhardwaj", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b27320b57e4975f9ee73b64f3319"),
                                                TrackItem("Lamhey", "Anubha Bajaj", false, imageUrl = "https://i.scdn.co/image/ab67616d0000b273b0dd6a5cd1dec96c4119c262")
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

        // Sticky Floating Green Play FAB anchored at TopEnd boundary
        // Sits half in TopBar and half in sticky filter chips row (matches Latest Screenshot media_1793155746362_4d67ef53.jpg)
        if (stickyPlayButtonAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = statusBarTop + 54.dp - 26.dp,
                        end = 16.dp
                    )
                    .graphicsLayer {
                        alpha = stickyPlayButtonAlpha
                        scaleX = 0.8f + (0.2f * stickyPlayButtonAlpha)
                        scaleY = 0.8f + (0.2f * stickyPlayButtonAlpha)
                    }
                    .zIndex(20f)
            ) {
                EncorePlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPauseClick,
                    size = 52.dp,
                    iconSize = 24.dp,
                    showShuffleBadge = false
                )
            }
        }
    }
}
}
}

/**
 * Filter Chip Button matching Spotify Mobile specs (Image 1, 4 & Latest Screenshot):
 * Pill shape, #2A2A2A background, white icon + bold white text.
 */
@Composable
private fun PlaylistChip(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = SpotifyTheme.typography.bodySmallBold.copy(
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyPlaylistScreen() {
    SpotifyEncoreTheme {
        val dummyTracks = listOf(
            TrackItem("Faasle", "Aditya Rikhari", false),
            TrackItem("Alag Aasmaan", "Anuv Jain", false),
            TrackItem("Another Love", "Tom Odell", true),
            TrackItem("Tu hai kahan", "AUR", false),
            TrackItem("Maybe My Soulmate Died", "iamnotshane", false)
        )

        SpotifyPlaylistScreen(
            title = "\\_(°_o)_/¯",
            description = "",
            creator = "中 • OV32LO2D • 中",
            saveCount = "2 saves",
            tracks = dummyTracks,
            currentTrackTitle = "Faasle",
            isPlaying = true,
            onBackClick = {},
            onPlayPauseClick = {},
            onTrackSelect = {}
        )
    }
}
