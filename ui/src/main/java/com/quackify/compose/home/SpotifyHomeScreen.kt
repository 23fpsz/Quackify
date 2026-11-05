package com.quackify.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
import com.quackify.compose.R
import com.quackify.compose.components.EncoreChip
import com.quackify.compose.components.EncoreProfileAvatar
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.EncorePlayIndicator
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.theme.rememberBottomPadding
import java.util.Calendar

data class QuickAccessItem(
    val title: String,
    val coverColor: Color,
    val uri: String = "",
    val imageUrl: String? = null,
    val isPlaying: Boolean = false,
    val hasFreshnessBadge: Boolean = false,
    val progress: Float? = null
)

data class RecommendationCard(
    val title: String,
    val subtitle: String,
    val coverColor: Color,
    val uri: String = "",
    val imageUrl: String? = null,
    val tag: String = ""
)

private const val TINT_WEIGHT = 0.55f
private const val HEADER_HOLD = 0.16f
private const val SURFACE_RED = 0x12 / 255f
private const val SURFACE_GREEN = 0x12 / 255f
private const val SURFACE_BLUE = 0x12 / 255f

data class ArtistCardItem(
    val name: String,
    val color: Color,
    val uri: String = ""
)

/**
 * 1:1 Pixel-Perfect Spotify Home Screen matching Spotify Android v9.1.92.3058 decompiled architecture:
 * - funkis_home.xml & funkis_filter_chips_header.xml (collapsing sticky filter bar, avatar shortcut, 12dp fade gradient)
 * - shortcut_card.xml (56dp height, 4dp radius, opacity_white_10 background, equalizer bar, 8dp blue freshness dot)
 * - artist_pick_main_card.xml (220dp hero height, artist comment bubble, EncoreListRow)
 * - live_event_card_home.xml (310dp width, 8dp corner radius, calendar badge with month and day)
 * - encore_card.xml & actions_medium_card_default_bar.xml (148dp card width, 4dp artwork radius, quick play)
 * - episode_card_layout.xml & actions_medium_card_episode_bar.xml (vertical podcast episodes feed with action bar)
 * - watchfeedentrypointvideo_card_layout.xml (9:16 portrait video clips preview carousel)

 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SpotifyHomeScreen(
    onPlaylistClick: (String, String) -> Unit,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    whatsNewCount: Int = 0,
    shelves: List<HomeShelf>? = null,
    loading: Boolean = false,
    quickAccessItems: List<QuickAccessItem>? = null,
    recommendations: List<RecommendationCard>? = null,
    modifier: Modifier = Modifier
) {
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Music", "Podcasts")
    val bottomPadding = rememberBottomPadding()

    val quickItems = quickAccessItems.orEmpty()


    val shelfSections = remember(shelves) {
        shelves.orEmpty().filter { it.cards.isNotEmpty() }
    }

    val podcastShelves = remember(shelves) {
        shelves.orEmpty().filter { it.podcasts && it.cards.isNotEmpty() }
    }

    // The tint comes from the first cover on screen, so the backdrop belongs
    // to the artwork instead of a fixed accent. A missing or dull cover falls
    // back to the surface, which keeps the header and the feed one colour.
    val tintSeed = quickItems.firstOrNull()?.coverColor
    val heroTint = remember(tintSeed) {
        if (tintSeed == null) {
            EncoreColors.Background
        } else {
            Color(
                red = tintSeed.red * TINT_WEIGHT + SURFACE_RED * (1f - TINT_WEIGHT),
                green = tintSeed.green * TINT_WEIGHT + SURFACE_GREEN * (1f - TINT_WEIGHT),
                blue = tintSeed.blue * TINT_WEIGHT + SURFACE_BLUE * (1f - TINT_WEIGHT),
            )
        }
    }

    // One gradient behind everything, held flat for the height of the sticky
    // header before it starts fading. The header itself paints the same flat
    // tint, so its bottom edge lands on a colour that has not moved yet and
    // the two never meet on a seam.
    val heroBackdropBrush = Brush.verticalGradient(
        0f to heroTint,
        HEADER_HOLD to heroTint,
        0.55f to EncoreColors.Background,
        1f to EncoreColors.Background
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(heroBackdropBrush)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
        // Sticky Header: Profile Avatar & Filter Chips (funkis_home.xml & funkis_filter_chips_header.xml)
        stickyHeader {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(heroTint)
                    .statusBarsPadding()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar (Clickable -> Opens Profile Drawer) matching faceheader_image
                    EncoreProfileAvatar(
                        userDisplayName = userDisplayName,
                        avatarUrl = userAvatarUrl.ifBlank { null },
                        onClick = onProfileClick,
                        modifier = Modifier.padding(start = 16.dp)
                    )

                    // Filter Chips Row Container with start gradient fade (funkis_filter_chips_header.xml)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(filters.size) { index ->
                                EncoreChip(
                                    text = filters[index],
                                    isSelected = selectedFilterIndex == index,
                                    onClick = {
                                        selectedFilterIndex = if (selectedFilterIndex == index) 0 else index
                                    }
                                )
                            }
                        }

                        // Start edge fade: the chips dissolve into the header
                        // tint they sit on, not into the page background, so
                        // no dark band shows up ahead of the first chip.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(12.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            heroTint,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }

        // If "All" or "Music" is selected: show Greeting and 8-Tile Quick Access Grid
        if (selectedFilterIndex == 0 || selectedFilterIndex == 1) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Greeting matching martini_greeting_header_* (24sp bold)
                    Text(
                        text = getGreeting(userDisplayName),
                        style = SpotifyTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 8-Tile Quick Access Grid (4 rows x 2 columns) matching shortcut_card.xml
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in quickItems.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuickAccessCard(
                                    item = quickItems[i],
                                    onClick = { onPlaylistClick(quickItems[i].title, quickItems[i].uri) },
                                    modifier = Modifier.weight(1f)
                                )
                                if (i + 1 < quickItems.size) {
                                    QuickAccessCard(
                                        item = quickItems[i + 1],
                                        onClick = { onPlaylistClick(quickItems[i + 1].title, quickItems[i + 1].uri) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Nothing from the feed yet: a quiet spinner while it loads, or a hint
        // that it could not be fetched. No made-up shelves or shortcuts.
        if (quickItems.isEmpty() && shelfSections.isEmpty() && podcastShelves.isEmpty()) {
            item(key = "home_feed_status") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = EncoreColors.Green,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Text(
                            text = "Couldn't load your feed",
                            style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                }
            }
        }

        // ==========================
        // TAB 0: "ALL"
        // ==========================
        if (selectedFilterIndex == 0) {
            items(shelfSections, key = { it.id }) { shelf ->
                if (shelf.artists.isNotEmpty()) {
                    ArtistShelfSection(
                        title = shelf.title,
                        artists = shelf.artists,
                        onArtistClick = onArtistClick
                    )
                } else {
                    ShelfSection(
                        title = shelf.title,
                        subtitle = "",
                        cards = shelf.cards,
                        onCardClick = onPlaylistClick
                    )
                }
            }
        }

        // ==========================
        // TAB 1: "MUSIC"
        // ==========================
        if (selectedFilterIndex == 1) {
            items(shelfSections, key = { it.id }) { shelf ->
                if (shelf.artists.isNotEmpty()) {
                    ArtistShelfSection(
                        title = shelf.title,
                        artists = shelf.artists,
                        onArtistClick = onArtistClick
                    )
                } else {
                    ShelfSection(
                        title = shelf.title,
                        subtitle = "",
                        cards = shelf.cards,
                        onCardClick = onPlaylistClick
                    )
                }
            }
        }

        // ==========================
        // TAB 2: "PODCASTS"
        // ==========================
        if (selectedFilterIndex == 2) {
            items(podcastShelves, key = { it.id }) { shelf ->
                ShelfSection(
                    title = shelf.title,
                    subtitle = "",
                    cards = shelf.cards,
                    onCardClick = onPlaylistClick
                )
            }
        }
        }
    }
}

/**
 * Artist Pick Spotlight Card matching `artist_pick_main_card.xml`:
 * - 220dp height hero card with 4dp corner radius
 * - Top-left comment bubble with artist avatar
 * - Dark gradient overlay
 * - Embedded bottom list row with play button
 */
@Composable
private fun QuickAccessCard(
    item: QuickAccessItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork (56dp full left bleed with rounded corner start)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    .background(item.coverColor),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl != null) {
                    SpotifyArtworkImage(
                        imageUrl = item.imageUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 0.dp
                    )
                } else if (item.title == "Liked Songs") {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_heart_active_24),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = "♫",
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }

            // Title (2 lines max, BodySmallBold 13sp)
            Text(
                text = item.title,
                style = SpotifyTheme.typography.bodySmallBold.copy(color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f)
            )

            // Accessory Slot: Equalizer (12dp) or Freshness Blue Dot (8dp)
            if (item.isPlaying) {
                EncorePlayIndicator(
                    isPlaying = true,
                    color = EncoreColors.Green,
                    size = 12.dp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else if (item.hasFreshnessBadge) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4687D6))
                )
            }
        }

        // Optional Episode Progress Bar placeholder (4dp height from dimens.xml)
        if (item.progress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 64.dp, end = 8.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = item.progress.coerceIn(0f, 1f))
                        .background(EncoreColors.Green)
                )
            }
        }
    }
}

/**
 * Section Heading matching decompiled `HeadingWithDescription.smali` and `EnrichedHeading.smali`:
 * - Title: 20sp TitleLarge / TitleMedium
 * - Optional Subtitle: 12sp Marginal, text subdued
 * - Optional "See all" chevron action
 */
@Composable
private fun SectionHeading(
    title: String,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onSeeAllClick != null) Modifier.clickable(onClick = onSeeAllClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SpotifyTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = SpotifyTheme.typography.marginal.copy(
                        fontSize = 12.sp,
                        color = EncoreColors.TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onSeeAllClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_chevron_right_24),
                contentDescription = "See all $title",
                tint = EncoreColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Reusable Shelf Section matching `encore_card.xml`:
 * - 148dp card width with 1:1 artwork (4dp corner radius from dimens.xml)
 * - Title and subtitle
 * - Floating circular quick play button matching actions_medium_card_default_bar.xml
 */
@Composable
private fun ShelfSection(
    title: String,
    cards: List<RecommendationCard>,
    onCardClick: (String, String) -> Unit,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        SectionHeading(
            title = title,
            subtitle = subtitle,
            onSeeAllClick = onSeeAllClick
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(cards) { card ->
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clickable { onCardClick(card.title, card.uri.ifEmpty { "spotify:search:${card.title}" }) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(card.coverColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (card.imageUrl != null) {
                            SpotifyArtworkImage(
                                imageUrl = card.imageUrl,
                                contentDescription = card.title,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 4.dp
                            )
                        } else {
                            Text(
                                text = "♫",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 44.sp
                            )
                        }

                        // Corner tag if present (e.g. "MIX")
                        if (card.tag.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = card.tag,
                                    style = SpotifyTheme.typography.tagLabel.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }

                        // Floating Quick Play Button matching actions_medium_card_default_bar.xml
                        EncorePlayButton(
                            isPlaying = false,
                            onClick = { onCardClick(card.title, card.uri.ifEmpty { "spotify:search:${card.title}" }) },
                            size = 36.dp,
                            iconSize = 16.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .shadow(elevation = 6.dp, shape = CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.title,
                        style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = card.subtitle,
                        style = SpotifyTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = EncoreColors.TextSecondary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistShelfSection(
    title: String,
    artists: List<ArtistCardItem>,
    onArtistClick: (String, String) -> Unit,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        SectionHeading(
            title = title,
            subtitle = subtitle,
            onSeeAllClick = onSeeAllClick
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists) { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(130.dp)
                        .clickable { onArtistClick(artist.name, artist.uri) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(artist.color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "★",
                            color = Color.White,
                            fontSize = 38.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Artist",
                        style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )
                }
            }
        }
    }
}

private fun getGreeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val base = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val who = name.trim()
    return if (who.isBlank()) base else "$base, $who"
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyHomeScreen() {
    SpotifyEncoreTheme {
        SpotifyHomeScreen(
            onPlaylistClick = { _, _ -> },
            onProfileClick = {},
            onNotificationsClick = {},
            onHistoryClick = {},
            onSettingsClick = {}
        )
    }
}
