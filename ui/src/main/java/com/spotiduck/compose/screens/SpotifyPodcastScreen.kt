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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.EncoreCornerRadii
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

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

/**
 * 1:1 Pixel-Perfect Spotify Podcast / Creator Webpage (CWP) Screen:
 * - Two-column Header Layout: 112dp cover art on left, Bold title & publisher on right
 * - Dot-separated metadata badges: Rank badge • Rating • Category
 * - CWP Action Bar: Trailer teaser play button, Follow pill, Notification bell, Settings gear
 * - Navigation Tabs: Episodes (with active white underline indicator), About, More like this
 * - Sort & Filter dropdown pill: "All episodes • Newest"
 * - Authentic 16:9 widescreen episode cards with snippet, metadata, action buttons, and 32dp solid white play circle
 */
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
    var isFollowing by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = remember { listOf("Episodes", "About", "More like this") }
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 54.dp

    // Top Bar Collapsing Transition
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1000f else listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val collapseThresholdPx = with(density) { 140.dp.toPx() }
    val collapseFraction = (scrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
    val topBarBgColor = coverColor.copy(alpha = collapseFraction)
    val topBarTitleAlpha = ((collapseFraction - 0.5f) * 2f).coerceIn(0f, 1f)
    val circleBtnAlpha = ((1f - collapseFraction) * 0.35f).coerceIn(0f, 0.35f)

    val followBorderColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextSecondary,
        label = "podcastFollowBorder"
    )
    val followTextColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
        label = "podcastFollowText"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Collapsible Header Gradient Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to coverColor,
                            0.45f to coverColor.copy(alpha = 0.85f),
                            0.75f to coverColor.copy(alpha = 0.35f),
                            1.0f to Color(0xFF121212)
                        )
                    )
                )
        )

        // Scrollable LazyColumn with Collapsing Header, Tabs, Filter, and Episode Rows
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + toolbarHeight,
                bottom = bottomPadding
            )
        ) {
            // CWP Show Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
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

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Navigation Tabs Bar: Episodes, About, More like this
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTabIndex == index
                            Column(
                                modifier = Modifier
                                    .clickable { selectedTabIndex = index }
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
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> {
                    // Episodes Tab
                    item {
                        // Sort / Filter Pill Row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(Color(0xFF282828))
                                    .clickable { }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
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

                    // Episodes List
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
                1 -> {
                    // About Tab
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
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
                            Spacer(modifier = Modifier.height(20.dp))

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
                    // More like this Tab
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "More Like This",
                                style = SpotifyTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            listOf(
                                "The Ranveer Show" to "BeerBiceps",
                                "WTF is with Nikhil Kamath" to "Nikhil Kamath",
                                "The Joe Rogan Experience" to "Joe Rogan"
                            ).forEach { (title, host) ->
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
