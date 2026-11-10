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
import androidx.compose.ui.graphics.RectangleShape
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
import com.quackify.backend.model.CityStat
import com.quackify.compose.components.EncorePlayIndicator
import com.quackify.compose.components.ShimmerBlock
import com.quackify.compose.components.ShimmerHost
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.components.rememberSmoothColor
import com.quackify.compose.format.formatCount
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
 * - Popular Releases carousel plus dedicated Albums and Singles & EPs carousels
 * - "Gallery" photo strip and "About the artist" biography card
 */
@Composable
fun SpotifyArtistScreen(
    artistName: String,
    monthlyListeners: String,
    heroColor: Color = Color(0xFF7A1C28),
    popularTracks: List<PopularTrack>,
    releases: List<ArtistRelease>,
    /** The overview's dedicated albums group; falls back to splitting [releases]. */
    albums: List<ArtistRelease> = emptyList(),
    /** The overview's singles group (EPs included); falls back to splitting [releases]. */
    singles: List<ArtistRelease> = emptyList(),
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
    followers: Long = 0L,
    worldRank: Int = 0,
    topCities: List<CityStat> = emptyList(),
    gallery: List<String> = emptyList(),
    isLoading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    /** The artist's real follow state from the library; the pill only flips
     *  once addToLibrary/removeFromLibrary has answered. */
    isFollowing: Boolean = false,
    onFollowToggle: (Boolean) -> Unit = {}
) {
    val density = LocalDensity.current
    var isShuffle by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    // Discography sections: prefer the overview's dedicated groups; when it
    // came back thin, split the popular-releases rows by their own type tags
    // so the sections appear whenever the artist actually has any.
    val albumSection = albums.ifEmpty {
        releases.filter { it.type.equals("Album", ignoreCase = true) }
    }
    val singleSection = singles.ifEmpty {
        releases.filter { it.type.equals("Single", ignoreCase = true) || it.type.equals("EP", ignoreCase = true) }
    }

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
    val smoothHeroColor by rememberSmoothColor(target = heroColor)
    val topBarBgColor = smoothHeroColor.copy(alpha = topBarAlpha)
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
                        .background(smoothHeroColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        ShimmerHost {
                            ShimmerBlock(
                                modifier = Modifier.fillMaxSize(),
                                shape = RectangleShape,
                                base = smoothHeroColor,
                                highlight = smoothHeroColor.copy(alpha = 0.65f)
                            )
                        }
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
                                .clickable { onFollowToggle(!isFollowing) }
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

            if (isLoading && popularTracks.isEmpty()) {
                item { ArtistLoadingRows() }
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

                    if (track.saved == true) {
                        Icon(
                            painter = painterResource(R.drawable.encore_icon_heart_active_24),
                            contentDescription = "In Liked Songs",
                            tint = EncoreColors.Green,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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

            if (loadFailed && !isLoading) {
                item {
                    ArtistLoadError(
                        title = "Couldn't load this artist",
                        onRetry = onRetry
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
            item { ArtistReleaseCarousel(releases, onReleaseClick) }

            // "Albums": the overview's dedicated albums group (the whole list,
            // not only the popular picks).
            if (albumSection.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Albums",
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )
                }
                item { ArtistReleaseCarousel(albumSection, onReleaseClick) }
            }

            // "Singles & EPs": the overview's singles group (Spotify files EPs
            // under it), with the same type-split fallback.
            if (singleSection.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Singles & EPs",
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )
                }
                item { ArtistReleaseCarousel(singleSection, onReleaseClick) }
            }

            // About Section (Bio Card)
            val aboutStatLine = listOfNotNull(
                followers.takeIf { it > 0L }?.let { formatCount(it) + " followers" },
                worldRank.takeIf { it > 0 }?.let { "#$it in the world" },
            ).joinToString(" • ")
            if (monthlyListeners.isNotBlank() || biography.isNotBlank() ||
                aboutStatLine.isNotBlank() || topCities.isNotEmpty()
            ) {
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
                    ) {
                        Column {
                            // Photo banner like Spotify's About card; the card clip
                            // rounds the top corners for free.
                            gallery.firstOrNull()?.let { photo ->
                                SpotifyArtworkImage(
                                    imageUrl = photo,
                                    contentDescription = artistName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(132.dp),
                                    cornerRadius = 0.dp
                                )
                            }
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (monthlyListeners.isNotBlank()) {
                                    Text(
                                        text = "$monthlyListeners monthly listeners",
                                        style = SpotifyTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                                if (aboutStatLine.isNotBlank()) {
                                    if (monthlyListeners.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Text(
                                        text = aboutStatLine,
                                        style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                                    )
                                }
                                if (topCities.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Popular in " + topCities.take(3).joinToString(", ") { it.city },
                                        style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (biography.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = biography,
                                        style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // "Gallery": the artist's photo strip, whenever the overview brought
            // any (the About card only ever shows its first photo; the About
            // sheet keeps the full grid).
            if (gallery.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Gallery",
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(gallery.take(10)) { photo ->
                            Box(
                                modifier = Modifier
                                    .width(232.dp)
                                    .height(174.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF242424))
                            ) {
                                SpotifyArtworkImage(
                                    imageUrl = photo,
                                    contentDescription = "$artistName photo",
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 8.dp
                                )
                            }
                        }
                    }
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

            // 3-Dots Button (smoothly cross-fades out as play button docks)
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
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

/** One release tile: 140dp cover, bold title, "Type • Year" line. */
@Composable
private fun ArtistReleaseCard(
    release: ArtistRelease,
    onReleaseClick: (ArtistRelease) -> Unit
) {
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

@Composable
private fun ArtistReleaseCarousel(
    releases: List<ArtistRelease>,
    onReleaseClick: (ArtistRelease) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(releases) { release ->
            ArtistReleaseCard(release, onReleaseClick)
        }
    }
}

@Composable
private fun ArtistLoadingRows() {
    ShimmerHost {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(
                        modifier = Modifier
                            .width(16.dp)
                            .height(12.dp),
                        shape = RoundedCornerShape(2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ShimmerBlock(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(12.dp),
                            shape = RoundedCornerShape(2.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(10.dp),
                            shape = RoundedCornerShape(2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistLoadError(title: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EncoreSpacers.Base, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = SpotifyTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try again",
            style = SpotifyTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = EncoreColors.Green
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
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
