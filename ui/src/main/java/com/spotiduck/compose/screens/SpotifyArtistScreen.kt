package com.spotiduck.compose.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.EncorePlayIndicator
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.EncoreCornerRadii
import com.spotiduck.compose.theme.EncoreSpacers
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

data class ArtistRelease(
    val title: String,
    val year: String,
    val type: String, // "Album" or "Single"
    val color: Color = Color(0xFF333333),
    val imageUrl: String? = null
)

data class PopularTrack(
    val track: TrackItem,
    val streamCount: String
)

/**
 * Spotify Artist Screen matching decompiled `artist_header.xml` and Spotify mobile APK specifications:
 * - Verified Artist badge with monthly listeners count
 * - Dynamic hero banner with gradient fade
 * - Follow/Following pill button
 * - Action controls (Shuffle, 3-dot options, Encore large play button)
 * - Top 5 Popular songs with stream numbers and PlayIndicator equalizer
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
    modifier: Modifier = Modifier,
    headerImageUrl: String? = null
) {
    var isFollowing by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
        }
    }

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
            .background(EncoreColors.Background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            // Hero Header Section with Gradient (stretches edge-to-edge behind status bar)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                ) {
                    if (headerImageUrl != null) {
                        SpotifyArtworkImage(
                            imageUrl = headerImageUrl,
                            contentDescription = artistName,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0.dp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        if (headerImageUrl != null) Color.Transparent else heroColor,
                                        heroColor.copy(alpha = 0.8f),
                                        EncoreColors.Background
                                    )
                                )
                            )
                            .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Base),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            // Verified Artist Badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_verified_check_active_24),
                                    contentDescription = "Verified Artist",
                                    tint = Color(0xFF3D91F4),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(EncoreSpacers.Tighter3))
                                Text(
                                    text = "Verified Artist",
                                    style = SpotifyTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                        Spacer(modifier = Modifier.height(EncoreSpacers.Tighter3))

                        Text(
                            text = artistName,
                            style = SpotifyTheme.typography.headlineLarge.copy(color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(EncoreSpacers.Tighter4))

                        Text(
                            text = "$monthlyListeners monthly listeners",
                            style = SpotifyTheme.typography.bodySmall.copy(color = Color(0xFFE0E0E0))
                        )
                    }
                }
            }
        }

        // Action Row: Follow Pill, 3-dots, Shuffle, Green Play Button
        item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Base),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter)
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

                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_more_android_24,
                            contentDescription = "More options",
                            onClick = onArtistOptionsClick,
                            tint = EncoreColors.TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter)
                    ) {
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_shuffle_24,
                            contentDescription = "Shuffle artist",
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
            }

            // "Popular" Header
            item {
                Text(
                    text = "Popular",
                    style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(EncoreCornerRadii.Base))
                            .background(heroColor.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.imageUrl != null) {
                            SpotifyArtworkImage(
                                imageUrl = track.imageUrl,
                                contentDescription = track.title,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = EncoreCornerRadii.Base
                            )
                        } else {
                            Text(text = "♪", color = Color.White, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = SpotifyTheme.typography.bodyMediumBold.copy(
                                color = if (isCurrentTrack) EncoreColors.Green else EncoreColors.TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = popTrack.streamCount,
                            style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                        )
                    }

                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_more_android_24,
                        contentDescription = "Options",
                        onClick = { onTrackOptionsClick(track) },
                        size = 36.dp,
                        iconSize = 20.dp,
                        tint = EncoreColors.TextSecondary
                    )
                }
            }

            // "Popular Releases" Header
            item {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))
                Text(
                    text = "Popular releases",
                    style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
                    horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Base)
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
                                    .clip(RoundedCornerShape(EncoreCornerRadii.Base))
                                    .background(release.color),
                                contentAlignment = Alignment.Center
                            ) {
                                if (release.imageUrl != null) {
                                    SpotifyArtworkImage(
                                        imageUrl = release.imageUrl,
                                        contentDescription = release.title,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = EncoreCornerRadii.Base
                                    )
                                } else {
                                    Text(text = "💿", fontSize = 40.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter2))
                            Text(
                                text = release.title,
                                style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${release.year} • ${release.type}",
                                style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                            )
                        }
                    }
                }
            }

            // "About" Section Card
            item {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser3))
                Text(
                    text = "About",
                    style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base)
                        .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
                        .background(Color(0xFF282828))
                        .padding(EncoreSpacers.Looser)
                ) {
                    Column {
                        Text(
                            text = "$monthlyListeners monthly listeners",
                            style = SpotifyTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary)
                        )
                        Spacer(modifier = Modifier.height(EncoreSpacers.Tighter2))
                        Text(
                            text = "$artistName is an acclaimed recording artist and songwriter dominating international charts worldwide.",
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        val topBarBgColor by animateColorAsState(
            targetValue = if (isScrolledPastHeader) heroColor.copy(alpha = 0.98f) else Color.Transparent,
            animationSpec = tween(durationMillis = 200),
            label = "artistTopBarBg"
        )

        // Pinned Top Navigation Bar (floats on top of edge-to-edge hero image)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarBgColor)
                .statusBarsPadding()
                .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter2),
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
                        text = artistName,
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_more_android_24,
                    contentDescription = "Artist options",
                    onClick = onArtistOptionsClick,
                    tint = EncoreColors.TextPrimary
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
