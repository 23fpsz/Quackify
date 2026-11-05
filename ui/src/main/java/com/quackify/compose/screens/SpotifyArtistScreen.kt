package com.quackify.compose.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.EncorePlayIndicator
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreSpacers
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.theme.rememberBottomPadding

data class ArtistRelease(
    val title: String,
    val year: String,
    val type: String, // "Album" or "Single"
    val color: Color = Color(0xFF333333),
    val imageUrl: String? = null,
    val uri: String = ""
)

data class PopularTrack(
    val track: TrackItem,
    val streamCount: String
)

/**
 * 1:1 Pixel-Perfect Spotify Artist Screen matching decompiled collection_artist_header_layout.xml:
 * - Edge-to-edge Hero image banner with dynamic gradient
 * - Verified Artist badge with monthly listeners count
 * - Follow / Following pill button with animated state
 * - Continuous gliding play button docking into the top bar (ComposePlayButtonBehavior)
 * - Pinned overlay top bar (transparent at rest, solid heroColor when scrolled)
 * - Top 5 Popular tracks with track numbers, equalizer, stream count, and 3-dots
 * - Popular Releases carousel (Albums & Singles)
 * - "About the artist" biography card
 */
@Composable
fun SpotifyArtistScreen(
    artistName: String,
    monthlyListeners: String,
    heroColor: Color = Color(0xFF7A1C28),
    popularTracks: List<PopularTrack>,
    releases: List<ArtistRelease>,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onArtistOptionsClick: () -> Unit = {},
    onReleaseClick: (ArtistRelease) -> Unit = {},
    onAboutClick: () -> Unit = {},
    onDiscographyClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    headerImageUrl: String? = null,
    biography: String = "",
    verified: Boolean = false,
    isLoading: Boolean = false
) {
    val density = LocalDensity.current
    var isFollowing by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    var actionRowYInRoot by remember { mutableStateOf(Float.MAX_VALUE) }
    var playButtonSlotYInRoot by remember { mutableFloatStateOf(-1f) }
    var isPlayButtonSlotMeasured by remember { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 54.dp
    val toolbarBottom = statusBarTop + toolbarHeight
    val toolbarBottomPx = with(density) { toolbarBottom.toPx() }
    val playButtonSize = 52.dp
    val playButtonHalf = 26.dp
    val pinnedTop = toolbarBottom - playButtonHalf
    val pinnedTopPx = with(density) { pinnedTop.toPx() }
    val heroHeight = 360.dp
    val heroHeightPx = with(density) { heroHeight.toPx() }

    val isHeaderOffScreen = listState.firstVisibleItemIndex > 0

    val dockDistancePx by remember {
        derivedStateOf {
            if (isHeaderOffScreen) {
                0f
            } else if (isPlayButtonSlotMeasured) {
                (playButtonSlotYInRoot - pinnedTopPx).coerceAtLeast(0f)
            } else {
                with(density) { 300.dp.toPx() }
            }
        }
    }
    val dockThresholdPx = with(density) { 56.dp.toPx() }
    val dockFraction = (dockDistancePx / dockThresholdPx).coerceIn(0f, 1f)

    val topBarThreeDotsAlpha = if (isHeaderOffScreen) 0f else dockFraction
    val topBarTitleAlpha = if (isHeaderOffScreen) 1f else (1f - (dockDistancePx / with(density) { 100.dp.toPx() })).coerceIn(0f, 1f)
    val topBarAlpha = if (isHeaderOffScreen) 1f else (1f - (dockDistancePx / with(density) { 180.dp.toPx() })).coerceIn(0f, 1f)
    val topBarBgColor = heroColor.copy(alpha = topBarAlpha)
    val circleBtnAlpha = ((1f - topBarAlpha) * 0.35f).coerceIn(0f, 0.35f)

    val followBorderColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextSecondary,
        label = "followBorder"
    )
    val followTextColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
        label = "followText"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // 1:1 Edge-to-Edge Background Hero Image with Parallax & Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    val scrollOffset = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        heroHeightPx
                    }
                    translationY = -scrollOffset * 0.45f
                    alpha = (1f - (scrollOffset / (heroHeightPx * 0.85f))).coerceIn(0f, 1f)
                }
        ) {
            if (headerImageUrl != null) {
                SpotifyArtworkImage(
                    imageUrl = headerImageUrl,
                    contentDescription = artistName,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(heroColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = EncoreColors.Green,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Top scrim for status bar and navigation buttons contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Bottom gradient fading smoothly into #121212 background (keeping artist face clear)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.78f to Color(0xCC121212),
                                1.0f to Color(0xFF121212)
                            )
                        )
                    )
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            // Overlaid Typography on Hero Section (transparent container over background image)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Base),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        // Giant Artist Title (44sp Bold White)
                        Text(
                            text = artistName,
                            style = SpotifyTheme.typography.headlineLarge.copy(
                                color = Color.White,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp,
                                lineHeight = 46.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Verified Artist Badge
                        if (verified) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_verified_check_active_24),
                                    contentDescription = "Verified Artist",
                                    tint = Color(0xFF3D91F4),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verified Artist",
                                    style = SpotifyTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                        }

                        // Monthly Listeners
                        if (monthlyListeners.isNotBlank()) {
                            Text(
                                text = "$monthlyListeners monthly listeners",
                                style = SpotifyTheme.typography.bodySmall.copy(
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            // Action Row: Latest Release Preview / Follow Pill, 3-dots, Shuffle, Green Play Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Base)
                        .onGloballyPositioned { coords ->
                            actionRowYInRoot = coords.positionInRoot().y
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter)
                    ) {
                        // Latest release preview thumbnail card if available
                        val latestRelease = releases.firstOrNull()
                        if (latestRelease?.imageUrl != null) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onReleaseClick(latestRelease) }
                            ) {
                                SpotifyArtworkImage(
                                    imageUrl = latestRelease.imageUrl,
                                    contentDescription = latestRelease.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            }
                        }

                        // Follow Pill Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(EncoreCornerRadii.FollowPill))
                                .border(1.dp, followBorderColor, RoundedCornerShape(EncoreCornerRadii.FollowPill))
                                .clickable { isFollowing = !isFollowing }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                style = SpotifyTheme.typography.bodySmallBold.copy(color = followTextColor)
                            )
                        }

                        // 3-Dots Artist Options Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onArtistOptionsClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                                contentDescription = "Artist options",
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
                                contentDescription = "Shuffle artist",
                                tint = if (isShuffle) EncoreColors.Green else EncoreColors.TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play Button Slot: rendered directly in the Action Controls row
                        Box(
                            modifier = Modifier
                                .size(playButtonSize)
                                .onGloballyPositioned { coords ->
                                    isPlayButtonSlotMeasured = true
                                    playButtonSlotYInRoot = coords.positionInRoot().y
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Visible directly in the row whenever below the top bar
                            if (!isPlayButtonSlotMeasured || playButtonSlotYInRoot > toolbarBottomPx) {
                                EncorePlayButton(
                                    isPlaying = isPlaying,
                                    onClick = onPlayPauseClick,
                                    size = playButtonSize,
                                    iconSize = 24.dp,
                                    showShuffleBadge = false
                                )
                            }
                        }
                    }
                }
            }

            // "Popular" Header
            item {
                Text(
                    text = "Popular",
                    style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                )
            }

            // Popular Track Rows
            itemsIndexed(popularTracks) { index, popTrack ->
                val track = popTrack.track
                val isCurrentTrack = track.title == currentTrackTitle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackSelect(track) }
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Number or Equalizer
                    Box(
                        modifier = Modifier.width(28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isCurrentTrack && isPlaying) {
                            EncorePlayIndicator(
                                isPlaying = true,
                                color = EncoreColors.Green,
                                size = 14.dp
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = SpotifyTheme.typography.bodyMediumBold.copy(
                                    color = if (isCurrentTrack) EncoreColors.Green else EncoreColors.TextSecondary
                                )
                            )
                        }
                    }

                    // Thumbnail image
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF242424)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.imageUrl != null) {
                            SpotifyArtworkImage(
                                imageUrl = track.imageUrl,
                                contentDescription = track.title,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 4.dp
                            )
                        } else {
                            Text("♫", color = Color.White, fontSize = 20.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                    // Title & Stream count
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = SpotifyTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrentTrack) EncoreColors.Green else Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = popTrack.streamCount,
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1
                        )
                    }

                    // 3-dot context menu
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_more_android_24,
                        contentDescription = "Track options",
                        onClick = { onTrackOptionsClick(track) },
                        tint = EncoreColors.TextSecondary,
                        size = 36.dp,
                        iconSize = 20.dp
                    )
                }
            }

            // "Popular releases" Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Popular releases",
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = "Discography",
                        style = SpotifyTheme.typography.bodyMedium.copy(
                            color = EncoreColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable { onDiscographyClick() }
                    )
                }
            }

            // Popular Releases Carousel
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(releases) { release ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clickable { onReleaseClick(release) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(release.color),
                                contentAlignment = Alignment.Center
                            ) {
                                if (release.imageUrl != null) {
                                    SpotifyArtworkImage(
                                        imageUrl = release.imageUrl,
                                        contentDescription = release.title,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 4.dp
                                    )
                                } else {
                                    Text("♫", color = Color.White, fontSize = 36.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = release.title,
                                style = SpotifyTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${release.type} • ${release.year}",
                                style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                            )
                        }
                    }
                }
            }

            // About Section (Bio Card)
            if (monthlyListeners.isNotBlank() || !biography.isNullOrBlank()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "About",
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EncoreSpacers.Base, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF242424))
                            .clickable { onAboutClick() }
                            .padding(16.dp)
                    ) {
                        Column {
                            if (monthlyListeners.isNotBlank()) {
                                Text(
                                    text = "$monthlyListeners monthly listeners",
                                    style = SpotifyTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                            if (biography.isNotBlank()) {
                                if (monthlyListeners.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text(
                                    text = biography,
                                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Pinned Top Navigation Bar (floats on top of edge-to-edge hero image)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(topBarBgColor)
                .statusBarsPadding()
                .height(toolbarHeight)
                .padding(horizontal = 16.dp)
                .zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button (Locked at 16dp from left border)
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

            // Centered Title in Top Bar when scrolled
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (topBarTitleAlpha > 0.01f) {
                    Text(
                        text = artistName,
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

            // 3-Dots Button (smoothly cross-fades out as play button docks)
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
                            .clickable(onClick = onArtistOptionsClick)
                            .graphicsLayer { alpha = topBarThreeDotsAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                            contentDescription = "Artist options",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Pinned Play Button (matching Spotify ComposePlayButtonBehavior):
        // Floats above top bar at zIndex(20f) as soon as it reaches toolbar, docking at pinnedTopPx
        if (isPlayButtonSlotMeasured && (playButtonSlotYInRoot <= toolbarBottomPx || isHeaderOffScreen)) {
            val targetY = if (isHeaderOffScreen || playButtonSlotYInRoot < pinnedTopPx) {
                pinnedTopPx
            } else {
                playButtonSlotYInRoot
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        translationY = targetY
                    }
                    .zIndex(20f)
            ) {
                EncorePlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPauseClick,
                    size = playButtonSize,
                    iconSize = 24.dp,
                    showShuffleBadge = false
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewSpotifyArtistScreen() {
    SpotifyEncoreTheme {
        SpotifyArtistScreen(
            artistName = "The Weeknd",
            monthlyListeners = "104,239,812",
            heroColor = Color(0xFF6B1A24),
            popularTracks = listOf(
                PopularTrack(TrackItem("Blinding Lights", "The Weeknd"), "4,250,810,912"),
                PopularTrack(TrackItem("Starboy", "The Weeknd, Daft Punk"), "3,119,402,118"),
                PopularTrack(TrackItem("Save Your Tears", "The Weeknd"), "2,845,190,441"),
                PopularTrack(TrackItem("Die For You", "The Weeknd"), "2,301,894,520"),
                PopularTrack(TrackItem("The Hills", "The Weeknd"), "2,190,341,080")
            ),
            releases = listOf(
                ArtistRelease("After Hours", "2020", "Album", Color(0xFF5A1A22)),
                ArtistRelease("Dawn FM", "2022", "Album", Color(0xFF243B55)),
                ArtistRelease("Starboy", "2016", "Album", Color(0xFF8E0E00)),
                ArtistRelease("Timeless", "2024", "Single", Color(0xFF333333))
            ),
            currentTrackTitle = "Starboy",
            isPlaying = true,
            onBackClick = {},
            onPlayPauseClick = {},
            onTrackSelect = {}
        )
    }
}
