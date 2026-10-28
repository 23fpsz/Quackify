package com.spotiduck.compose.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.EncoreCornerRadii
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding
import kotlinx.coroutines.launch

data class PodcastEpisode(
    val id: String,
    val title: String,
    val description: String,
    val releaseDate: String,
    val duration: String,
    val isExplicit: Boolean = false,
    val imageUrl: String? = null,
    val isPlayed: Boolean = false
)

/**
 * 1:1 Pixel-Perfect Spotify Podcast / Show Screen matching Spotify Mobile specs:
 * - Collapsible overlay top bar (transparent at rest, solid coverColor when scrolled)
 * - Show artwork with 8dp rounded squircle corners starting directly under status bar
 * - Continuous gliding play button implementing Spotify's ComposePlayButtonBehavior
 * - Continuous header gradient smoothly fading to pure #121212
 * - Pinned filter chips row: All episodes, Unplayed, Downloaded, Sort
 * - Detailed episode cards with date, description, duration play pill, and action controls
 */
@Composable
fun SpotifyPodcastScreen(
    showTitle: String,
    publisherName: String,
    rating: String = "4.8 (124K)",
    category: String = "Technology",
    coverColor: Color = Color(0xFF1E3A8A),
    episodes: List<PodcastEpisode>,
    currentEpisodeId: String = "",
    isPlaying: Boolean = false,
    onBackClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onEpisodeSelect: (PodcastEpisode) -> Unit = {},
    onEpisodeOptionsClick: (PodcastEpisode) -> Unit = {},
    onShowOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    coverUrl: String? = null
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var isFollowing by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All episodes") }
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

    val chipsHeight = 48.dp

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

    val chipsYPx = if (isPlayButtonSlotMeasured) playButtonSlotYInRoot + with(density) { (playButtonSize + 14.dp).toPx() } else with(density) { (statusBarTop + 410.dp).toPx() }
    val pinnedChipsYPx = if (isHeaderOffScreen) toolbarBottomPx else maxOf(toolbarBottomPx, chipsYPx)
    val pinnedChipsYDp = with(density) { pinnedChipsYPx.toDp() }

    val currentGradientHeightDp by remember {
        derivedStateOf {
            if (isHeaderOffScreen) {
                toolbarBottom
            } else if (isPlayButtonSlotMeasured && playButtonSlotYInRoot > 0f) {
                maxOf(toolbarBottom, with(density) { (playButtonSlotYInRoot + playButtonSize.toPx() + with(density) { 14.dp.toPx() }).toDp() })
            } else {
                toolbarBottom + 360.dp
            }
        }
    }

    val followBorderColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextSecondary,
        label = "podcastFollowBorder"
    )
    val followTextColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
        label = "podcastFollowText"
    )

    // Dynamic Artwork Shrink Calculation
    val scrollOffset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 400
    val shrinkFraction = (scrollOffset / with(density) { 180.dp.toPx() }).coerceIn(0f, 1f)
    val artworkSize = 180.dp - (40.dp * shrinkFraction)
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

        // Scrollable LazyColumn with Collapsing Header, Chips Spacer, and Episodes
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + toolbarHeight,
                bottom = bottomPadding
            )
        ) {
            // Header Item: drawn shifted up by topOffsetPx so artwork sits directly at statusBarTop + 8.dp
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
                        // Hero Show Artwork (Squircle)
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
                                    .shadow(elevation = (14 * (1f - shrinkFraction)).dp, shape = RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(coverColor.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!coverUrl.isNullOrBlank()) {
                                    SpotifyArtworkImage(
                                        imageUrl = coverUrl,
                                        contentDescription = showTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 12.dp
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_album_24),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Show Title (Bold 24sp)
                        Text(
                            text = showTitle,
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

                        // Publisher Name
                        Text(
                            text = publisherName,
                            style = SpotifyTheme.typography.bodyMedium.copy(
                                color = EncoreColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Rating & Category
                        Text(
                            text = "★ $rating • $category",
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
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

                                // Bell / Notification Button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { hasNotifications = !hasNotifications },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (hasNotifications) R.drawable.encore_icon_notifications_active_24 else R.drawable.encore_icon_notification_new_24
                                        ),
                                        contentDescription = "Notifications",
                                        tint = if (hasNotifications) EncoreColors.Green else EncoreColors.TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 3-Dots Show Options Button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onShowOptionsClick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                                        contentDescription = "Show options",
                                        tint = EncoreColors.TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
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

                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }

            // Reserved slot for the pinned filter chips bar
            item {
                Spacer(modifier = Modifier.height(chipsHeight))
            }

            // Episodes List Header
            item {
                Text(
                    text = "All Episodes",
                    style = SpotifyTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // Episode Rows
            items(episodes, key = { it.id }) { episode ->
                val isCurrentEpisode = episode.id == currentEpisodeId
                PodcastEpisodeRow(
                    episode = episode,
                    isPlaying = isCurrentEpisode && isPlaying,
                    onClick = { onEpisodeSelect(episode) },
                    onOptionsClick = { onEpisodeOptionsClick(episode) }
                )
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Pinned Filter Chips Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = pinnedChipsYDp)
                .background(Color(0xFF121212))
                .zIndex(9f)
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

                listOf("All episodes", "Unplayed", "Downloaded").forEach { chip ->
                    val isSelected = selectedFilter == chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (isSelected) EncoreColors.Green else Color(0xFF2A2A2A))
                            .clickable { selectedFilter = chip }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chip,
                            style = SpotifyTheme.typography.bodySmallBold.copy(
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable { }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_sort_24),
                            contentDescription = "Sort",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sort",
                            style = SpotifyTheme.typography.bodySmallBold.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(72.dp))
            }
        }

        // Pinned Top Navigation Bar
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
            // Back Button
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
                        text = showTitle,
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

            // 3-Dots Button
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
                            .clickable(onClick = onShowOptionsClick)
                            .graphicsLayer { alpha = topBarThreeDotsAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                            contentDescription = "Show options",
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
 * Spotify Podcast Episode Row:
 * - Thumbnail image
 * - Date & duration
 * - Title (bold)
 * - Description (2 lines max)
 * - Action controls: Play pill, Add to your episodes, Download, Share, 3-dots
 */
@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEpisodeDownloaded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Episode Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF242424)),
                contentAlignment = Alignment.Center
            ) {
                if (episode.imageUrl != null) {
                    SpotifyArtworkImage(
                        imageUrl = episode.imageUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 8.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_album_24),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = episode.title,
                    style = SpotifyTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) EncoreColors.Green else Color.White,
                        fontSize = 15.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                Text(
                    text = episode.description,
                    style = SpotifyTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Date & duration + Action Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Date & Duration
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (episode.isExplicit) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
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
                    text = "${episode.releaseDate} • ${episode.duration}",
                    style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                )
            }

            // Actions: Download, Share, 3-dots, Play Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { isEpisodeDownloaded = !isEpisodeDownloaded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isEpisodeDownloaded) R.drawable.encore_icon_downloaded_24 else R.drawable.encore_icon_download_24
                        ),
                        contentDescription = "Download episode",
                        tint = if (isEpisodeDownloaded) EncoreColors.Green else EncoreColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Share Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_share_android_24),
                        contentDescription = "Share episode",
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3-Dots Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOptionsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                        contentDescription = "Episode options",
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Play Pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onClick)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.encore_icon_pause_24 else R.drawable.encore_icon_play_24
                        ),
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyPodcastScreen() {
    SpotifyEncoreTheme {
        SpotifyPodcastScreen(
            showTitle = "The Joe Rogan Experience",
            publisherName = "Joe Rogan",
            rating = "4.9 (1.2M)",
            category = "Comedy & Interviews",
            coverColor = Color(0xFF1E3A8A),
            episodes = listOf(
                PodcastEpisode(
                    id = "1",
                    title = "#2210 - Brian Simpson",
                    description = "Brian Simpson is a stand-up comedian and host of the 'BS with Brian Simpson' podcast. His special 'Live from the Mothership' is streaming on Netflix.",
                    releaseDate = "Yesterday",
                    duration = "2 hr 46 min"
                ),
                PodcastEpisode(
                    id = "2",
                    title = "#2209 - Lex Fridman",
                    description = "Lex Fridman is an AI researcher at MIT and host of the Lex Fridman Podcast.",
                    releaseDate = "3 days ago",
                    duration = "3 hr 12 min"
                )
            )
        )
    }
}
