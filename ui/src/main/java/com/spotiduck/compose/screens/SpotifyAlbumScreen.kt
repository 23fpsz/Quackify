package com.spotiduck.compose.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
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
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreHeartButton
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.EncorePlayIndicator
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

data class AlbumDisc(
    val discNumber: Int,
    val tracks: List<TrackItem>
)

/**
 * 1:1 Pixel-Perfect Spotify Album Screen matching Spotify Mobile specs:
 * - Collapsible overlay top bar (transparent at rest, solid coverColor when scrolled)
 * - Album artwork starting directly under status bar at statusBarTop + 8.dp with smooth scaling
 * - Single continuous gliding play button implementing Spotify's ComposePlayButtonBehavior
 * - Continuous header gradient extending from Y=0 and smoothly fading to pure #121212
 * - Official Encore download, heart, and 3-dot option buttons
 * - Track rows with disc headers, numbers, and copyrights section
 * - "More by [Artist]" discography shelf
 */
@Composable
fun SpotifyAlbumScreen(
    albumTitle: String,
    artistName: String,
    releaseYear: String,
    releaseDate: String = "March 20, 2020",
    coverColor: Color = Color(0xFF6B1A24),
    tracks: List<TrackItem>,
    discs: List<AlbumDisc>? = null,
    copyrightNotice: String = "© 2020 Republic Records, a division of UMG Recordings, Inc.",
    phonographicNotice: String = "℗ 2020 Republic Records, a division of UMG Recordings, Inc.",
    currentTrackTitle: String = "",
    isPlaying: Boolean = false,
    moreByArtist: List<ArtistRelease>? = null,
    onBackClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {},
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onAlbumOptionsClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    coverUrl: String? = null
) {
    val density = LocalDensity.current
    var isLiked by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
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
    val topBarBgColor = coverColor.copy(alpha = topBarAlpha)
    val circleBtnAlpha = ((1f - topBarAlpha) * 0.35f).coerceIn(0f, 0.35f)
    val topOffsetPx = with(density) { (toolbarHeight - 8.dp).toPx().toInt() }

    val currentGradientHeightDp by remember {
        derivedStateOf {
            if (isHeaderOffScreen) {
                toolbarBottom
            } else if (isPlayButtonSlotMeasured && playButtonSlotYInRoot > 0f) {
                maxOf(toolbarBottom, with(density) { (playButtonSlotYInRoot + playButtonSize.toPx() + with(density) { 14.dp.toPx() }).toDp() })
            } else {
                toolbarBottom + 390.dp
            }
        }
    }

    // Dynamic Artwork Shrink Calculation
    val scrollOffset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 500
    val shrinkFraction = (scrollOffset / with(density) { 200.dp.toPx() }).coerceIn(0f, 1f)
    val artworkSize = 240.dp - (50.dp * shrinkFraction)
    val artworkAlpha = (1f - (shrinkFraction * 0.35f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Collapsible Header Gradient Background
        // Spans from top of screen (Y = 0) down to the bottom of the action row,
        // dynamically bounded so it smoothly contracts into toolbarBottom without scroll bleed or popping.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentGradientHeightDp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to coverColor,
                            0.35f to coverColor.copy(alpha = 0.90f),
                            0.65f to coverColor.copy(alpha = 0.55f),
                            0.85f to coverColor.copy(alpha = 0.20f),
                            1.0f to Color(0xFF121212)
                        )
                    )
                )
        )

        // Scrollable LazyColumn with Collapsing Header and Tracks
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + toolbarHeight,
                bottom = bottomPadding
            )
        ) {
            // Header Item: shifted up by topOffsetPx so artwork sits directly at statusBarTop + 8.dp
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height - topOffsetPx) {
                                placeable.placeRelative(0, -topOffsetPx)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        // Hero Album Artwork
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(artworkSize)
                                    .graphicsLayer {
                                        alpha = artworkAlpha
                                    }
                                    .shadow(elevation = (14 * (1f - shrinkFraction)).dp, shape = RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(coverColor.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!coverUrl.isNullOrBlank()) {
                                    SpotifyArtworkImage(
                                        imageUrl = coverUrl,
                                        contentDescription = albumTitle,
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Album Title (Left-aligned, bold, 24sp)
                        Text(
                            text = albumTitle,
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

                        // Artist Pill (Avatar + Name)
                        Row(
                            modifier = Modifier
                                .clickable { onArtistClick(artistName) }
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(coverColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (artistName.trim().firstOrNull() ?: 'A').uppercaseChar().toString(),
                                    style = SpotifyTheme.typography.bodySmallBold.copy(fontSize = 11.sp, color = Color.White)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = artistName,
                                style = SpotifyTheme.typography.bodySmallBold.copy(
                                    color = EncoreColors.TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Album Type & Year
                        Text(
                            text = "Album • $releaseYear",
                            style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Controls Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .onGloballyPositioned { coords ->
                                    actionRowYInRoot = coords.positionInRoot().y
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Download Button (Matches official Spotify 1:1)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { isDownloaded = !isDownloaded },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isDownloaded) R.drawable.encore_icon_downloaded_24 else R.drawable.encore_icon_download_24
                                        ),
                                        contentDescription = if (isDownloaded) "Downloaded" else "Download",
                                        tint = if (isDownloaded) EncoreColors.Green else EncoreColors.TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Heart / Add to library Button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { isLiked = !isLiked },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isLiked) R.drawable.encore_icon_heart_active_24 else R.drawable.encore_icon_heart_24
                                        ),
                                        contentDescription = "Save album",
                                        tint = if (isLiked) EncoreColors.Green else EncoreColors.TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 3-Dots Album Options Button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onAlbumOptionsClick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                                        contentDescription = "Album options",
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

                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }

            // If discs are specified, group by disc separator (album_track_row_layout.xml)
            if (discs != null && discs.size > 1) {
                discs.forEach { disc ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_album_24),
                                contentDescription = null,
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DISC ${disc.discNumber}",
                                style = SpotifyTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = EncoreColors.TextSecondary
                                )
                            )
                        }
                    }

                    itemsIndexed(disc.tracks) { index, track ->
                        val isCurrentTrack = track.title == currentTrackTitle
                        AlbumTrackRow(
                            trackNumber = index + 1,
                            track = track,
                            isPlaying = isCurrentTrack && isPlaying,
                            onClick = { onTrackSelect(track) },
                            onMoreClick = { onTrackOptionsClick(track) }
                        )
                    }
                }
            } else {
                // Single disc: list all tracks
                itemsIndexed(tracks) { index, track ->
                    val isCurrentTrack = track.title == currentTrackTitle
                    AlbumTrackRow(
                        trackNumber = index + 1,
                        track = track,
                        isPlaying = isCurrentTrack && isPlaying,
                        onClick = { onTrackSelect(track) },
                        onMoreClick = { onTrackOptionsClick(track) }
                    )
                }
            }

            // Footer: Release date, song count & runtime, and Copyrights (copyrights_layout.xml)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = releaseDate,
                        style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val totalDuration = calculateTotalDuration(tracks.size)
                    Text(
                        text = "${tracks.size} songs • $totalDuration",
                        style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = copyrightNotice,
                        style = SpotifyTheme.typography.marginal.copy(
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = EncoreColors.TextSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = phonographicNotice,
                        style = SpotifyTheme.typography.marginal.copy(
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = EncoreColors.TextSecondary
                        )
                    )
                }
            }

            // "More by [Artist]" shelf
            if (!moreByArtist.isNullOrEmpty()) {
                item {
                    Text(
                        text = "More by $artistName",
                        style = SpotifyTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(moreByArtist) { release ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { onAlbumClick(release.title) }
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
                                        Text(text = "♫", color = Color.White, fontSize = 36.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = release.title,
                                    style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
            }
        }

        // Pinned Top Navigation Bar (Overlays top of screen, transparent at rest, solid when scrolled)
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
                        text = albumTitle,
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
                            .clickable(onClick = onAlbumOptionsClick)
                            .graphicsLayer { alpha = topBarThreeDotsAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                            contentDescription = "Album options",
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

/**
 * Album Track Row matching Spotify's album_track_row_layout:
 * - Numbered left index or 3-bar Encore equalizer
 * - Title and secondary artist subtitle
 * - Explicit [E] badge
 * - 3-dot context action
 */
@Composable
private fun AlbumTrackRow(
    trackNumber: Int,
    track: TrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number (28dp width) or animated play indicator
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                EncorePlayIndicator(
                    isPlaying = true,
                    color = EncoreColors.Green,
                    size = 14.dp
                )
            } else {
                Text(
                    text = trackNumber.toString(),
                    style = SpotifyTheme.typography.bodyMedium.copy(
                        color = EncoreColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Track title & artist
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = track.title,
                style = SpotifyTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) EncoreColors.Green else EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(width = 14.dp, height = 14.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(EncoreColors.TextSecondary.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "E",
                            style = SpotifyTheme.typography.tagLabel.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        )
                    }
                }

                Text(
                    text = track.artist,
                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3-dot context menu
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_more_android_24,
            contentDescription = "Track options",
            onClick = onMoreClick,
            tint = EncoreColors.TextSecondary,
            size = 36.dp,
            iconSize = 20.dp
        )
    }
}

private fun calculateTotalDuration(trackCount: Int): String {
    val totalSeconds = trackCount * 215 // avg 3m 35s per song
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes} min"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyAlbumScreen() {
    SpotifyEncoreTheme {
        SpotifyAlbumScreen(
            albumTitle = "After Hours",
            artistName = "The Weeknd",
            releaseYear = "2020",
            releaseDate = "March 20, 2020",
            tracks = listOf(
                TrackItem("Alone Again", "The Weeknd"),
                TrackItem("Too Late", "The Weeknd"),
                TrackItem("Hardest To Love", "The Weeknd"),
                TrackItem("Scared To Live", "The Weeknd"),
                TrackItem("Snowchild", "The Weeknd", isExplicit = true),
                TrackItem("Escape From LA", "The Weeknd", isExplicit = true),
                TrackItem("Heartless", "The Weeknd", isExplicit = true),
                TrackItem("Faith", "The Weeknd", isExplicit = true),
                TrackItem("Blinding Lights", "The Weeknd"),
                TrackItem("In Your Eyes", "The Weeknd"),
                TrackItem("Save Your Tears", "The Weeknd"),
                TrackItem("Repeat After Me (Interlude)", "The Weeknd"),
                TrackItem("After Hours", "The Weeknd"),
                TrackItem("Until I Bleed Out", "The Weeknd")
            ),
            currentTrackTitle = "Blinding Lights",
            isPlaying = true
        )
    }
}
