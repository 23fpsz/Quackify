package com.spotiduck.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.spotiduck.compose.R
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.EncoreCornerRadii
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class PodcastEpisode(
    val id: String,
    val title: String,
    val description: String,
    val releaseDate: String,
    val duration: String,
    val statusText: String? = null,
    val isExplicit: Boolean = false,
    val imageUrl: String? = null,
    val isPlayed: Boolean = false
)

val DefaultPodcastEpisodes = listOf(
    PodcastEpisode(
        id = "fo-580",
        title = "How Extreme Heat Is Becoming India's \$180 Billion Problem | Arunabha Ghosh | FO580 Raj Shamani",
        description = "Checkout Mobius: https://mobiusf.org/ Get your hand-picked playbook here: https://www.figuringout.co/pdf/fo-580 Guest Suggestion Form: https://forms.gle/bnaeY3FpoFU9ZjA47 Disclaimer: This video is intended solely for educational purposes.",
        releaseDate = "Tue",
        duration = "1h 15m",
        statusText = null,
        imageUrl = "https://i.scdn.co/image/ab6765630000ba8aa218ee3778a87b1cbf3bb552"
    ),
    PodcastEpisode(
        id = "fo-579",
        title = "How Osama Bin Laden Was Found, Killed & Raid Strategy | Robert J. O'Neill | FO579 Raj Shamani",
        description = "Download Porter Here: https://porter.in/enterprise Get your hand-picked playbook here: https://www.figuringout.co/pdf/fo-579 Guest Suggestion Form: https://forms.gle/bnaeY3FpoFU9ZjA47",
        releaseDate = "Sat",
        duration = "1h 50m left",
        statusText = "Finished",
        imageUrl = "https://i.scdn.co/image/ab6765630000ba8aa218ee3778a87b1cbf3bb552"
    ),
    PodcastEpisode(
        id = "fo-578",
        title = "How To Build An Unbreakable Mind & Push Beyond Your Limits | Ayyoub El Osrouti | FO578 Raj Shamani",
        description = "Checkout ASUS ExpertBook Series: https://www.flipkart.com/bbd-eb-intrigue-at-store Get your hand-picked playbook here: https://www.figuringout.co/pdf/fo-578 Guest Suggestion Form: https://forms.gle/bnaeY3FpoFU9ZjA4",
        releaseDate = "Thu",
        duration = "2h 50m",
        statusText = "50K+ plays",
        imageUrl = "https://i.scdn.co/image/ab6765630000ba8aa218ee3778a87b1cbf3bb552"
    )
)

private val RelatedPodcastShows = listOf(
    "The Ranveer Show" to "BeerBiceps",
    "WTF is with Nikhil Kamath" to "Nikhil Kamath",
    "The Joe Rogan Experience" to "Joe Rogan"
)

/**
 * 1:1 Pixel-Perfect Spotify Podcast / Creator Webpage (CWP) Screen:
 * - Collapsible Show Header: Two-column layout (112dp cover art on left, Bold title & publisher on right)
 * - Pinned Navigation Tabs: Episodes, About, More like this (stays directly below the Top Bar when scrolling)
 * - Gradient ends strictly before the Tabs Bar, keeping Tabs & Content over pure #121212 black background
 * - HorizontalPager supporting smooth swiping between Episodes, About, and More like this with animated sliding transitions
 * - Authentic 16:9 widescreen episode cards with snippet, metadata, action controls, and 32dp solid white play circle
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyPodcastScreen(
    showTitle: String = "Figuring Out with Raj Shamani",
    publisherName: String = "Raj Shamani",
    rankBadge: String? = "#1 in Top Podcasts",
    rating: String = "4.6 (6.8K)",
    category: String = "Educational",
    coverColor: Color = Color(0xFF2E3A40),
    coverUrl: String? = "https://i.scdn.co/image/ab6765630000ba8aa218ee3778a87b1cbf3bb552",
    episodes: List<PodcastEpisode> = DefaultPodcastEpisodes,
    currentEpisodeId: String = "",
    isPlaying: Boolean = false,
    onBackClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onEpisodeSelect: (PodcastEpisode) -> Unit = {},
    onEpisodeOptionsClick: (PodcastEpisode) -> Unit = {},
    onShowOptionsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var isFollowing by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 54.dp
    val toolbarBottom = statusBarTop + toolbarHeight
    val toolbarBottomPx = with(density) { toolbarBottom.toPx() }
    val tabsHeight = 48.dp
    val tabsHeightPx = with(density) { tabsHeight.toPx() }

    val tabs = remember { listOf("Episodes", "About", "More like this") }
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }

    val episodesListState = rememberLazyListState()
    val aboutListState = rememberLazyListState()
    val moreListState = rememberLazyListState()

    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    var headerContentHeightPx by remember { mutableFloatStateOf(with(density) { 260.dp.toPx() }) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    val maxCollapsePx = headerContentHeightPx.coerceAtLeast(1f)
    val collapseFraction = (-headerOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
    val topBarBgColor = coverColor.copy(alpha = collapseFraction)
    val topBarTitleAlpha = ((collapseFraction - 0.4f) * 1.67f).coerceIn(0f, 1f)
    val circleBtnAlpha = ((1f - collapseFraction) * 0.35f).coerceIn(0f, 0.35f)

    val followBorderColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextSecondary,
        label = "podcastFollowBorder"
    )
    val followTextColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
        label = "podcastFollowText"
    )

    // Coordinated Nested Scroll Connection:
    // Collapses header on scroll up; expands header when active list is at top
    val nestedScrollConnection = remember(pagerState.currentPage, maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Scrolling UP (delta < 0): collapse header first before child scrolls
                if (delta < 0f && headerOffsetPx > -maxCollapsePx) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumed)
                }

                // Scrolling DOWN (delta > 0) when current child is at top: expand header immediately
                val activeList = when (pagerState.currentPage) {
                    0 -> episodesListState
                    1 -> aboutListState
                    else -> moreListState
                }
                if (delta > 0f && activeList.firstVisibleItemIndex == 0 && activeList.firstVisibleItemScrollOffset == 0 && headerOffsetPx < 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                // Scrolling DOWN (delta > 0) after child reached top: expand header
                if (delta > 0f && headerOffsetPx < 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumedY = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    val availablePagerHeightPx = if (rootHeightPx > 0f) {
        (rootHeightPx - toolbarBottomPx - tabsHeightPx).coerceAtLeast(0f)
    } else {
        with(density) { 600.dp.toPx() }
    }
    val availablePagerHeightDp = with(density) { availablePagerHeightPx.toDp() }

    // Dynamic Gradient Height:
    // Reaches exactly down to where the tabs bar begins, and contracts to toolbarBottom on collapse.
    // Never bleeds into the tabs bar or child lists.
    val currentGradientHeightPx = (toolbarBottomPx + headerContentHeightPx + headerOffsetPx).coerceAtLeast(toolbarBottomPx)
    val currentGradientHeightDp = with(density) { currentGradientHeightPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .onGloballyPositioned { coords ->
                rootHeightPx = coords.size.height.toFloat()
            }
            .nestedScroll(nestedScrollConnection)
    ) {
        // Collapsible Header Gradient Background (terminates before tabs bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentGradientHeightDp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to coverColor,
                            0.40f to coverColor.copy(alpha = 0.85f),
                            0.70f to coverColor.copy(alpha = 0.40f),
                            0.90f to coverColor.copy(alpha = 0.15f),
                            1.0f to Color(0xFF121212)
                        )
                    )
                )
        )

        // Main Scrolling Column (Header + Pinned Tabs + HorizontalPager)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
        ) {
            // Spacer for Top Navigation Bar
            Spacer(modifier = Modifier.height(toolbarBottom))

            // Show Header Content (Collapsible show identity & actions)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = (1f - collapseFraction * 1.3f).coerceIn(0f, 1f)
                    }
                    .onGloballyPositioned { coords ->
                        if (coords.size.height > 0) {
                            headerContentHeightPx = coords.size.height.toFloat()
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val newOffset = (headerOffsetPx + dragAmount).coerceIn(-maxCollapsePx, 0f)
                            headerOffsetPx = newOffset
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Two-Column Header: Cover Art (112x112dp) on Left, Title & Creator on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Show Artwork
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(coverColor.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverUrl.isNullOrBlank()) {
                            SpotifyArtworkImage(
                                imageUrl = coverUrl,
                                contentDescription = showTitle,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 8.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Show Title & Creator
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = showTitle,
                            style = SpotifyTheme.typography.headlineLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp,
                                color = Color.White
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = publisherName,
                            style = SpotifyTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Row: Middle dot separated badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!rankBadge.isNullOrBlank()) {
                        Text(
                            text = rankBadge,
                            style = SpotifyTheme.typography.marginal.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = " • ",
                            style = SpotifyTheme.typography.marginal.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Text(
                        text = "★ $rating",
                        style = SpotifyTheme.typography.marginal.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Text(
                        text = " • ",
                        style = SpotifyTheme.typography.marginal.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    Text(
                        text = category,
                        style = SpotifyTheme.typography.marginal.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Header Action Bar: Teaser circular play, Follow pill, Bell, Gear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Trailer / Teaser Preview Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_play_24),
                            contentDescription = "Play teaser",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Follow Pill Button
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(EncoreCornerRadii.FollowPill))
                            .border(1.dp, followBorderColor, RoundedCornerShape(EncoreCornerRadii.FollowPill))
                            .clickable { isFollowing = !isFollowing }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            style = SpotifyTheme.typography.bodySmallBold.copy(
                                color = followTextColor,
                                fontSize = 13.sp
                            )
                        )
                    }

                    // Notification Bell
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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

                    // Settings Gear
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSettingsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_settings_24),
                            contentDescription = "Show settings",
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Pinned Navigation Tabs Bar (Directly below Top Bar when scrolled, pure #121212 black background)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tabsHeight)
                    .background(Color(0xFF121212))
                    .zIndex(5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        Column(
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = title,
                                style = SpotifyTheme.typography.bodyMedium.copy(
                                    color = if (isSelected) Color.White else EncoreColors.TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(2.5.dp)
                                        .background(Color.White, RoundedCornerShape(1.dp))
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.5.dp))
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )
            }

            // HorizontalPager: Smooth swiping between Episodes, About, and More like this with animated slide
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(availablePagerHeightDp)
                    .background(Color(0xFF121212))
            ) { page ->
                when (page) {
                    0 -> {
                        // Page 0: Episodes List
                        LazyColumn(
                            state = episodesListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomPadding)
                        ) {
                            // Sticky Sort / Filter Dropdown Pill (Stays directly below the Tabs Bar)
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF121212))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0xFF282828))
                                            .clickable { }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "All episodes • Newest",
                                                style = SpotifyTheme.typography.bodySmallBold.copy(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                painter = painterResource(id = R.drawable.encore_icon_chevron_down_24),
                                                contentDescription = "Filter",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
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
                    }
                    1 -> {
                        // Page 1: About Tab
                        LazyColumn(
                            state = aboutListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = bottomPadding + 24.dp
                            )
                        ) {
                            item {
                                Text(
                                    text = "About",
                                    style = SpotifyTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Figuring Out with Raj Shamani is India's top podcast where business, mental resilience, personal finance, and high-impact life journeys collide with world-class founders, authors, scientists, and creators.",
                                    style = SpotifyTheme.typography.bodySmall.copy(
                                        color = EncoreColors.TextSecondary,
                                        lineHeight = 20.sp,
                                        fontSize = 14.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "Ratings & Reviews",
                                    style = SpotifyTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "★ $rating • $category",
                                    style = SpotifyTheme.typography.bodyMedium.copy(
                                        color = EncoreColors.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                    2 -> {
                        // Page 2: More Like This Tab
                        LazyColumn(
                            state = moreListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = bottomPadding + 24.dp
                            )
                        ) {
                            item {
                                Text(
                                    text = "More Like This",
                                    style = SpotifyTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            items(RelatedPodcastShows) { (title, host) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF242424)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = title,
                                            style = SpotifyTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Text(
                                            text = "Podcast • $host",
                                            style = SpotifyTheme.typography.bodySmall.copy(
                                                color = EncoreColors.TextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pinned Top Navigation Bar (Layered above sliding header at zIndex 10f)
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

            // 3-Dots Show Options Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = circleBtnAlpha))
                    .clickable(onClick = onShowOptionsClick),
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

/**
 * Spotify Podcast Episode Row matching official mobile APK hierarchy:
 * - Top: 16:9 widescreen artwork (114x64dp, 6dp radius) + Title (15sp bold, maxLines = 3)
 * - Description: 2-line snippet (13sp textSecondary)
 * - Metadata line: Day • Duration • Status (e.g. "Tue • 1h 15m" or "Sat • 1h 50m left • Finished")
 * - Footer:
 *     - Left: Add (+), Download (↓), Share, More (...)
 *     - Right: 32dp solid white circle with pure black play triangle
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
    var isEpisodeAdded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Top Row: 16:9 Widescreen Artwork + Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 16:9 Widescreen Thumbnail (114dp x 64dp)
            Box(
                modifier = Modifier
                    .size(width = 114.dp, height = 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF242424)),
                contentAlignment = Alignment.Center
            ) {
                if (!episode.imageUrl.isNullOrBlank()) {
                    SpotifyArtworkImage(
                        imageUrl = episode.imageUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 6.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title
            Text(
                text = episode.title,
                style = SpotifyTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) EncoreColors.Green else Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Body Description Snippet (maxLines = 2)
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

        Spacer(modifier = Modifier.height(8.dp))

        // Episode Metadata: Day • Duration • Status (e.g. "Tue • 1h 15m" or "Sat • 1h 50m left • Finished")
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

            val metaText = buildString {
                append(episode.releaseDate)
                append(" • ")
                append(episode.duration)
                if (!episode.statusText.isNullOrBlank()) {
                    append(" • ")
                    append(episode.statusText)
                }
            }

            Text(
                text = metaText,
                style = SpotifyTheme.typography.marginal.copy(
                    color = EncoreColors.TextSecondary,
                    fontSize = 12.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Footer Row: [+] [↓] [Share] [...] on left, (▶) 32dp white play button on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Action Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Add to Your Episodes
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { isEpisodeAdded = !isEpisodeAdded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isEpisodeAdded) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24
                        ),
                        contentDescription = "Add episode",
                        tint = if (isEpisodeAdded) EncoreColors.Green else EncoreColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Download Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                        .size(40.dp)
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
                        .size(40.dp)
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
            }

            // Right: Primary Play Button (32dp solid white circle with pure black play triangle)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.encore_icon_pause_24 else R.drawable.encore_icon_play_24
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyPodcastScreen() {
    SpotifyEncoreTheme {
        SpotifyPodcastScreen()
    }
}
