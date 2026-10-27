package com.spotiduck.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import com.spotiduck.compose.components.EncoreHeartButton
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.components.EncorePlayIndicator
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
 * 1:1 Pixel-Perfect Spotify Album Screen matching:
 * - album_track_row_layout.xml (disc separator with encore_icon_album_24, track numbers)
 * - copyrights_layout.xml & copyright_row.xml (release date, song count, legal P/C line)
 * - Hero header with dynamic gradient, artist avatar pill, and Encore action controls
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
    var isLiked by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 380
        }
    }

    val topBarBgColor by animateColorAsState(
        targetValue = if (isScrolledPastHeader) coverColor.copy(alpha = 0.98f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "albumTopBarBg"
    )

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            coverColor,
            coverColor.copy(alpha = 0.65f),
            EncoreColors.Background,
            EncoreColors.Background
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        // Top App Bar: Back Arrow, Title on scroll, Heart, More
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarBgColor)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_chevron_left_24,
                    contentDescription = "Back",
                    onClick = onBackClick,
                    size = 48.dp,
                    iconSize = 24.dp
                )

                AnimatedVisibility(
                    visible = isScrolledPastHeader,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = albumTitle,
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                EncoreHeartButton(
                    isLiked = isLiked,
                    onLikeToggle = { isLiked = !isLiked },
                    size = 48.dp,
                    iconSize = 24.dp
                )
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_more_android_24,
                    contentDescription = "Album options",
                    onClick = onAlbumOptionsClick,
                    size = 48.dp,
                    iconSize = 24.dp
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            // Album Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Big Album Art
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .align(Alignment.CenterHorizontally)
                            .shadow(elevation = 20.dp, shape = RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(coverColor.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverUrl != null) {
                            SpotifyArtworkImage(
                                imageUrl = coverUrl,
                                contentDescription = albumTitle,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 4.dp
                            )
                        } else {
                            Text(
                                text = "♫",
                                fontSize = 68.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Album Title
                    Text(
                        text = albumTitle,
                        style = SpotifyTheme.typography.headlineLarge.copy(fontSize = 24.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Artist Pill (Avatar + Name)
                    Row(
                        modifier = Modifier
                            .clickable { onArtistClick(artistName) }
                            .padding(vertical = 4.dp),
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
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Album Type & Year
                    Text(
                        text = "Album • $releaseYear",
                        style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Controls Row: Download, More, Shuffle, Play Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EncoreIconButton(
                                iconRes = if (isDownloaded) R.drawable.encore_icon_downloaded_24 else R.drawable.encore_icon_download_24,
                                contentDescription = "Download album",
                                onClick = { isDownloaded = !isDownloaded },
                                isActive = isDownloaded,
                                activeTint = EncoreColors.Green,
                                tint = EncoreColors.TextSecondary
                            )

                            EncoreIconButton(
                                iconRes = R.drawable.encore_icon_more_android_24,
                                contentDescription = "More",
                                onClick = onAlbumOptionsClick,
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
