package com.spotiduck.compose.home

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreChip
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.EncorePlayIndicator
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding
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

data class ArtistCardItem(
    val name: String,
    val color: Color,
    val uri: String = ""
)

data class ArtistPickItem(
    val artistName: String,
    val note: String,
    val title: String,
    val subtitle: String,
    val heroColor: Color = Color(0xFF901A1E),
    val uri: String = ""
)

data class LiveEventItem(
    val title: String,
    val month: String,
    val day: String,
    val dateText: String,
    val location: String,
    val color: Color = Color(0xFF6B1A24),
    val uri: String = ""
)

data class PodcastEpisodeItem(
    val showName: String,
    val episodeTitle: String,
    val description: String,
    val duration: String,
    val date: String,
    val color: Color = Color(0xFF006450),
    val progress: Float? = null
)

data class PodcastShowItem(
    val title: String,
    val publisher: String,
    val color: Color,
    val uri: String = ""
)

data class AudiobookItem(
    val title: String,
    val author: String,
    val narrator: String,
    val duration: String,
    val rating: String,
    val color: Color = Color(0xFF1E3264),
    val uri: String = ""
)

data class ClipItem(
    val title: String,
    val artistName: String,
    val color: Color,
    val durationText: String = "0:30",
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
 * - Audiobooks subfeed with 2:3 portrait book cards
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SpotifyHomeScreen(
    onPlaylistClick: (String, String) -> Unit,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    quickAccessItems: List<QuickAccessItem>? = null,
    recommendations: List<RecommendationCard>? = null,
    modifier: Modifier = Modifier
) {
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Music", "Podcasts", "Audiobooks")
    val bottomPadding = rememberBottomPadding()

    val quickItems = remember(quickAccessItems) {
        if (!quickAccessItems.isNullOrEmpty()) {
            quickAccessItems
        } else {
            listOf(
                QuickAccessItem("Liked Songs", Color(0xFF4A154B), "spotify:collection:tracks"),
                QuickAccessItem("Discover Weekly", Color(0xFF1E3264), "spotify:search:Discover Weekly"),
                QuickAccessItem("The Joe Rogan Experience", Color(0xFF006450), "spotify:search:The Joe Rogan Experience", hasFreshnessBadge = true, progress = 0.65f),
                QuickAccessItem("Daily Mix 1", Color(0xFF8D67AB), "spotify:search:Daily Mix 1", isPlaying = true),
                QuickAccessItem("Today's Top Hits", Color(0xFFE8115B), "spotify:search:Today's Top Hits"),
                QuickAccessItem("Release Radar", Color(0xFF509BF5), "spotify:search:Release Radar"),
                QuickAccessItem("Rock Classics", Color(0xFFBA5D07), "spotify:search:Rock Classics"),
                QuickAccessItem("Chill Hits", Color(0xFF432371), "spotify:search:Chill Hits")
            )
        }
    }

    val madeForYou = remember(recommendations) {
        if (!recommendations.isNullOrEmpty()) {
            recommendations
        } else {
            listOf(
                RecommendationCard("Daily Mix 1", "The Weeknd, Drake, Travis Scott", Color(0xFF8D67AB), tag = "MIX"),
                RecommendationCard("Daily Mix 2", "Daft Punk, Justice, Kavinsky", Color(0xFF1E3264), tag = "MIX"),
                RecommendationCard("Daily Mix 3", "Coldplay, Imagine Dragons, OneRepublic", Color(0xFF432371), tag = "MIX"),
                RecommendationCard("Discover Weekly", "Your weekly mixtape of fresh music", Color(0xFF2E77D0), tag = "PLAYLIST")
            )
        }
    }

    val popularArtists = remember {
        listOf(
            ArtistCardItem("The Weeknd", Color(0xFFE91429)),
            ArtistCardItem("Daft Punk", Color(0xFF509BF5)),
            ArtistCardItem("Taylor Swift", Color(0xFFE8115B)),
            ArtistCardItem("Drake", Color(0xFFBA5D07)),
            ArtistCardItem("Billie Eilish", Color(0xFF148A08)),
            ArtistCardItem("Dua Lipa", Color(0xFF8D67AB))
        )
    }

    val jumpBackIn = remember {
        listOf(
            RecommendationCard("After Hours", "Album • The Weeknd", Color(0xFF901A1E), tag = "ALBUM"),
            RecommendationCard("Random Access Memories", "Album • Daft Punk", Color(0xFF6B7280), tag = "ALBUM"),
            RecommendationCard("Starboy", "Album • The Weeknd", Color(0xFF4F46E5), tag = "ALBUM"),
            RecommendationCard("Nightcall", "Single • Kavinsky", Color(0xFF7C3AED), tag = "SINGLE")
        )
    }

    val heavyRotation = remember {
        listOf(
            RecommendationCard("Mega Hit Mix", "A mega mix of 75 chart toppers", Color(0xFF148A08), tag = "PLAYLIST"),
            RecommendationCard("All Out 2010s", "The biggest songs of the 2010s", Color(0xFFBC5900), tag = "PLAYLIST"),
            RecommendationCard("Pop Rising", "Who's rising in pop right now", Color(0xFFE8115B), tag = "PLAYLIST"),
            RecommendationCard("Beast Mode", "Get your beast mode on with hype music", Color(0xFF333333), tag = "PLAYLIST")
        )
    }

    val artistPick = remember {
        ArtistPickItem(
            artistName = "The Weeknd",
            note = "Posted an update",
            title = "After Hours (Deluxe)",
            subtitle = "Album • 17 songs",
            heroColor = Color(0xFF7A1C28),
            uri = "spotify:album:after_hours"
        )
    }

    val liveEvents = remember {
        listOf(
            LiveEventItem(
                title = "The Weeknd: After Hours Til Dawn",
                month = "OCT",
                day = "28",
                dateText = "Sat • 8:00 PM",
                location = "MetLife Stadium • East Rutherford, NJ",
                color = Color(0xFF7A1C28)
            ),
            LiveEventItem(
                title = "Daft Punk Tribute Spectacular",
                month = "NOV",
                day = "14",
                dateText = "Tue • 9:30 PM",
                location = "Terminal 5 • New York, NY",
                color = Color(0xFF1E3264)
            ),
            LiveEventItem(
                title = "Justice & Kavinsky Live",
                month = "DEC",
                day = "02",
                dateText = "Sat • 10:00 PM",
                location = "Brooklyn Mirage • Brooklyn, NY",
                color = Color(0xFF432371)
            )
        )
    }

    val podcastShows = remember {
        listOf(
            PodcastShowItem("The Joe Rogan Experience", "Joe Rogan", Color(0xFF006450)),
            PodcastShowItem("Huberman Lab", "Scicomm Media", Color(0xFF1E3264)),
            PodcastShowItem("The Vergecast", "The Verge", Color(0xFF8D67AB)),
            PodcastShowItem("Lex Fridman Podcast", "Lex Fridman", Color(0xFF333333)),
            PodcastShowItem("Hardcore History", "Dan Carlin", Color(0xFFBA5D07))
        )
    }

    val podcastEpisodes = remember {
        listOf(
            PodcastEpisodeItem(
                showName = "The Joe Rogan Experience",
                episodeTitle = "#2210 - Lex Fridman & AI Breakthroughs",
                description = "Lex Fridman returns to discuss autonomous AI agents, robotics advancements, and human consciousness.",
                duration = "2 hr 45 min",
                date = "Oct 16",
                color = Color(0xFF006450),
                progress = 0.65f
            ),
            PodcastEpisodeItem(
                showName = "Huberman Lab",
                episodeTitle = "Optimizing Focus, Memory & Deep Work",
                description = "Science-based protocols to maximize cognitive bandwidth, focus state, and physical endurance.",
                duration = "1 hr 58 min",
                date = "Oct 14",
                color = Color(0xFF1E3264)
            ),
            PodcastEpisodeItem(
                showName = "The Vergecast",
                episodeTitle = "The Future of Mobile Platforms & Open Engines",
                description = "Deep dive into next-gen mobile architecture, streaming audio, and open client-side engines.",
                duration = "1 hr 12 min",
                date = "Oct 12",
                color = Color(0xFF8D67AB)
            )
        )
    }

    val audiobooks = remember {
        listOf(
            AudiobookItem(
                title = "Atomic Habits",
                author = "James Clear",
                narrator = "Narrated by James Clear",
                duration = "5 hr 35 min",
                rating = "4.9",
                color = Color(0xFFBA5D07)
            ),
            AudiobookItem(
                title = "Dune",
                author = "Frank Herbert",
                narrator = "Narrated by Scott Brick, Orlagh Cassidy",
                duration = "21 hr 2 min",
                rating = "4.8",
                color = Color(0xFF8E0E00)
            ),
            AudiobookItem(
                title = "Steve Jobs",
                author = "Walter Isaacson",
                narrator = "Narrated by Dylan Baker",
                duration = "25 hr 6 min",
                rating = "4.8",
                color = Color(0xFF333333)
            ),
            AudiobookItem(
                title = "The Psychology of Money",
                author = "Morgan Housel",
                narrator = "Narrated by Chris Hill",
                duration = "5 hr 48 min",
                rating = "4.7",
                color = Color(0xFF148A08)
            )
        )
    }

    val clips = remember {
        listOf(
            ClipItem("Behind the track: Blinding Lights", "The Weeknd", Color(0xFF7A1C28), "0:30"),
            ClipItem("Making of Random Access Memories", "Daft Punk", Color(0xFF1E3264), "0:45"),
            ClipItem("Studio Sessions: Midnight City", "M83", Color(0xFF432371), "0:25"),
            ClipItem("Live at Brooklyn Mirage", "Kavinsky", Color(0xFFBA5D07), "0:35")
        )
    }

    // Top Backdrop Tint Brush
    val heroBackdropBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF38151A),
            EncoreColors.Background,
            EncoreColors.Background
        )
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
                    .background(EncoreColors.Background.copy(alpha = 0.98f))
                    .statusBarsPadding()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar (Clickable -> Opens Profile Drawer) matching faceheader_image
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91429))
                            .clickable(onClick = onProfileClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (userDisplayName.trim().firstOrNull() ?: 'D').uppercaseChar().toString(),
                            style = SpotifyTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    // Filter Chips Row Container with start gradient fade (funkis_filter_chips_header.xml)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // If a category filter is active, show dismiss "X" chip to quickly reset to "All"
                            if (selectedFilterIndex != 0) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(EncoreColors.CardElevated)
                                            .clickable { selectedFilterIndex = 0 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_x_24),
                                            contentDescription = "Clear filter",
                                            tint = EncoreColors.TextPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

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

                        // Start edge fade gradient matching @drawable/filter_row_start_gradient (12dp width)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(12.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            EncoreColors.Background,
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
                        text = getGreeting(),
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

        // ==========================
        // TAB 0: "ALL"
        // ==========================
        if (selectedFilterIndex == 0) {
            // Artist Pick Spotlight Card (artist_pick_main_card.xml)
            item {
                ArtistPickCard(
                    pick = artistPick,
                    onArtistClick = { onArtistClick(artistPick.artistName) },
                    onPlayClick = { onPlaylistClick(artistPick.title, artistPick.uri) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Shelf: "Made for you"
            item {
                ShelfSection(
                    title = "Made for you",
                    subtitle = "Get recommendations based on what you listen to",
                    cards = madeForYou,
                    onCardClick = onPlaylistClick
                )
            }

            // Shelf: "Clips & Previews" (watchfeedentrypointvideo_card_layout.xml)
            item {
                ClipsShelfSection(
                    title = "Clips & Previews",
                    subtitle = "Short video stories from your top artists",
                    clips = clips,
                    onClipClick = { clip -> onPlaylistClick(clip.title, clip.uri) }
                )
            }

            // Shelf: "Popular artists"
            item {
                ArtistShelfSection(
                    title = "Popular artists",
                    subtitle = "Artists you might like",
                    artists = popularArtists,
                    onArtistClick = onArtistClick
                )
            }

            // Shelf: "Live events near you" (live_event_card_home.xml)
            item {
                LiveEventsShelfSection(
                    title = "Live events near you",
                    subtitle = "Concerts and tours happening around you",
                    events = liveEvents,
                    onEventClick = { event -> onPlaylistClick(event.title, event.uri) }
                )
            }

            // Shelf: "Jump back in"
            item {
                ShelfSection(
                    title = "Jump back in",
                    subtitle = "Your recent favorites and heavy rotation",
                    cards = jumpBackIn,
                    onCardClick = onPlaylistClick
                )
            }

            // Section: "Episodes for you" (Podcasts list)
            item {
                SectionHeading(
                    title = "Episodes for you",
                    subtitle = "Handpicked episodes based on your interests"
                )
            }

            items(podcastEpisodes) { episode ->
                PodcastEpisodeCard(
                    episode = episode,
                    onPlayClick = { onPlaylistClick(episode.episodeTitle, "spotify:search:${episode.episodeTitle}") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // ==========================
        // TAB 1: "MUSIC"
        // ==========================
        if (selectedFilterIndex == 1) {
            // AI DJ Hero Card (Spotify Music Tab signature AI DJ set)
            item {
                SpotifyDJCard(
                    onPlayClick = { onPlaylistClick("DJ Xavier", "spotify:playlist:dj") },
                    isPlaying = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Artist Pick Spotlight
            item {
                ArtistPickCard(
                    pick = artistPick,
                    onArtistClick = { onArtistClick(artistPick.artistName) },
                    onPlayClick = { onPlaylistClick(artistPick.title, artistPick.uri) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Shelf: "Made for you"
            item {
                ShelfSection(
                    title = "Made for you",
                    subtitle = "Personalized playlists and mixes",
                    cards = madeForYou,
                    onCardClick = onPlaylistClick
                )
            }

            // Shelf: "Clips & Previews"
            item {
                ClipsShelfSection(
                    title = "Music Clips",
                    subtitle = "Trending videos and behind the scenes",
                    clips = clips,
                    onClipClick = { clip -> onPlaylistClick(clip.title, clip.uri) }
                )
            }

            // Shelf: "Popular artists"
            item {
                ArtistShelfSection(
                    title = "Popular artists",
                    subtitle = "Trending now across Spotify",
                    artists = popularArtists,
                    onArtistClick = onArtistClick
                )
            }

            // Shelf: "Heavy rotation"
            item {
                ShelfSection(
                    title = "Heavy rotation",
                    subtitle = "Tracks you have on repeat",
                    cards = heavyRotation,
                    onCardClick = onPlaylistClick
                )
            }

            // Shelf: "Jump back in"
            item {
                ShelfSection(
                    title = "Jump back in",
                    subtitle = "Jump back to where you left off",
                    cards = jumpBackIn,
                    onCardClick = onPlaylistClick
                )
            }
        }

        // ==========================
        // TAB 2: "PODCASTS"
        // ==========================
        if (selectedFilterIndex == 2) {
            // Shelf: "Your Shows" (Subscribed podcasts)
            item {
                SectionHeading(
                    title = "Your Shows",
                    subtitle = "Shows you follow and listen to regularly"
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(podcastShows) { show ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onPlaylistClick(show.title, show.uri) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(show.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = show.title,
                                style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = show.publisher,
                                style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Section: "New Episodes" vertical list
            item {
                SectionHeading(
                    title = "New Episodes",
                    subtitle = "Latest releases from your library and trending shows"
                )
            }

            items(podcastEpisodes) { episode ->
                PodcastEpisodeCard(
                    episode = episode,
                    onPlayClick = { onPlaylistClick(episode.episodeTitle, "spotify:search:${episode.episodeTitle}") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // ==========================
        // TAB 3: "AUDIOBOOKS"
        // ==========================
        if (selectedFilterIndex == 3) {
            item {
                SectionHeading(
                    title = "Popular Audiobooks",
                    subtitle = "Most listened audiobooks this week"
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(audiobooks) { book ->
                        AudiobookCard(
                            book = book,
                            onClick = { onPlaylistClick(book.title, book.uri) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeading(
                    title = "Best Sellers in Fiction",
                    subtitle = "Top rated novels and stories"
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(audiobooks.reversed()) { book ->
                        AudiobookCard(
                            book = book,
                            onClick = { onPlaylistClick(book.title, book.uri) }
                        )
                    }
                }
            }
        }
    }

    // Floating Endless DJ Button (matches endless_dj_button.xml, 48dp, #0A59B8)
    EndlessDJFloatingButton(
        onClick = { onPlaylistClick("DJ Xavier", "spotify:playlist:dj") },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = bottomPadding)
    )
}
}

/**
 * 8-tile Quick Access Card matching decompiled `shortcut_card.xml`:
 * - 56dp height, 4dp corner radius
 * - opacity_white_10 background (Color.White.copy(alpha = 0.10f))
 * - 56dp x 56dp artwork with top-start & bottom-start 4dp radius
 * - 2-line max title with BodySmallBold (13sp bold, white)
 * - 12dp equalizer indicator when playing
 * - 8dp circular blue freshness dot (Color(0xFF4687D6)) for updated podcast tiles
 * - 3dp episode progress bar anchored at bottom
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
 * Artist Pick Spotlight Card matching `artist_pick_main_card.xml`:
 * - 220dp height hero card with 4dp corner radius
 * - Top-left comment bubble with artist avatar
 * - Dark gradient overlay
 * - Embedded bottom list row with play button
 */
@Composable
private fun ArtistPickCard(
    pick: ArtistPickItem,
    onArtistClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(pick.heroColor)
            .clickable(onClick = onPlayClick)
    ) {
        // Gradient fade overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Top Comment Bubble View (artist_pick_comment)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onArtistClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE91429)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (pick.artistName.trim().firstOrNull() ?: 'A').uppercaseChar().toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "${pick.artistName} • ${pick.note}",
                style = SpotifyTheme.typography.marginal.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }

        // Bottom Content Bar (EncoreListRow with 48dp artwork and play button)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF5A1A22)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "♫", color = Color.White, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pick.title,
                    style = SpotifyTheme.typography.bodyMediumBold.copy(color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pick.subtitle,
                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            EncorePlayButton(
                isPlaying = false,
                onClick = onPlayClick,
                size = 44.dp,
                iconSize = 20.dp
            )
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
 * Live Event / Concert Card matching `live_event_card_home.xml`:
 * - 310dp width, 8dp corner radius, #1E1E1E background
 * - Left 100dp x 100dp artwork with calendar badge
 * - Date text, Title, Location, and save action
 */
@Composable
private fun LiveEventsShelfSection(
    title: String,
    events: List<LiveEventItem>,
    onEventClick: (LiveEventItem) -> Unit,
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
            items(events) { event ->
                Row(
                    modifier = Modifier
                        .width(310.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .clickable { onEventClick(event) }
                ) {
                    // Left artwork with calendar badge
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(event.color)
                    ) {
                        // Calendar badge (white rounded box top-left)
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = event.month,
                                style = SpotifyTheme.typography.marginalBold.copy(
                                    fontSize = 9.sp,
                                    color = Color.Black
                                )
                            )
                            Text(
                                text = event.day,
                                style = SpotifyTheme.typography.titleSmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            )
                        }
                    }

                    // Content on the right
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = event.dateText,
                            style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = event.title,
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = Color.White),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = event.location,
                            style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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

/**
 * Watch Feed / Clips Preview Carousel matching `watchfeedentrypointvideo_card_layout.xml`
 * - 9:16 portrait video preview cards (140dp x 248dp)
 * - 8dp corner radius
 * - Vertical dark gradient overlay with video duration indicator
 * - 24dp artist avatar, overline, title (3 lines max)
 */
@Composable
private fun ClipsShelfSection(
    title: String,
    clips: List<ClipItem>,
    onClipClick: (ClipItem) -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(clips) { clip ->
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(248.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(clip.color)
                        .clickable { onClipClick(clip) }
                ) {
                    // Dark gradient overlay matching entry_point_video_card_gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )

                    // Top-right video badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = clip.durationText,
                            style = SpotifyTheme.typography.marginal.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    // Bottom info block: Artist avatar + overline + title
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (clip.artistName.trim().firstOrNull() ?: 'A').uppercaseChar().toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = clip.artistName,
                                style = SpotifyTheme.typography.marginal.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = clip.title,
                            style = SpotifyTheme.typography.bodySmallBold.copy(
                                fontSize = 13.sp,
                                color = Color.White,
                                lineHeight = 16.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistShelfSection(
    title: String,
    artists: List<ArtistCardItem>,
    onArtistClick: (String) -> Unit,
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
                        .clickable { onArtistClick(artist.name) }
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

/**
 * 2:3 aspect ratio Audiobook Card for Audiobooks shelf
 */
@Composable
private fun AudiobookCard(
    book: AudiobookItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        // Book Cover (124dp x 186dp, 2:3 ratio) with simulated spine
        Box(
            modifier = Modifier
                .width(124.dp)
                .height(186.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(book.color)
        ) {
            // Book Spine Highlight on left edge
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AUDIOBOOK",
                    style = SpotifyTheme.typography.tagLabel.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )

                Text(
                    text = book.title,
                    style = SpotifyTheme.typography.bodyMediumBold.copy(color = Color.White),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = book.title,
            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = book.author,
            style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "★ ${book.rating} • ${book.duration}",
            style = SpotifyTheme.typography.marginal.copy(
                fontSize = 10.sp,
                color = EncoreColors.TextSecondary
            )
        )
    }
}

/**
 * Podcast Episode Card matching decompiled:
 * - `episode_card_layout.xml`
 * - `actions_medium_card_text_container_episode.xml`
 * - `actions_medium_card_episode_bar.xml` (bottom action bar with Add (+), More (...), Share, 8dp freshness dot, 11sp duration, 32dp x 4dp progress bar, and 32dp play button)
 */
@Composable
private fun PodcastEpisodeCard(
    episode: PodcastEpisodeItem,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EncoreColors.Card)
            .clickable(onClick = onPlayClick)
            .padding(16.dp)
    ) {
        Column {
            // Header: 48dp artwork + show info + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(episode.color, Color(0xFF1DB954))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎙", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${episode.showName} • ${episode.date}",
                        style = SpotifyTheme.typography.bodySmallBold.copy(
                            fontSize = 12.sp,
                            color = EncoreColors.TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episode.episodeTitle,
                        style = SpotifyTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description (maxLines 2, 13sp)
            Text(
                text = episode.description,
                style = SpotifyTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = EncoreColors.TextSecondary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Bar matching actions_medium_card_episode_bar.xml
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Actions: Add (+) button, More Options (...), and Share
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(
                            id = if (isAdded) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24
                        ),
                        contentDescription = if (isAdded) "Added to Your Episodes" else "Add to Your Episodes",
                        tint = if (isAdded) EncoreColors.Green else EncoreColors.TextSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { isAdded = !isAdded }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                        contentDescription = "More episode options",
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_share_android_24),
                        contentDescription = "Share episode",
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Right Actions: Freshness dot, Duration, Progress bar, Play button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Freshness Blue Dot (8dp)
                    if (episode.progress == null) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4687D6))
                        )
                    }

                    // Duration (11sp Marginal)
                    Text(
                        text = episode.duration,
                        style = SpotifyTheme.typography.marginal.copy(
                            fontSize = 11.sp,
                            color = EncoreColors.TextSecondary
                        )
                    )

                    // Optional Progress Bar (32dp x 4dp matching home_episode_card_progress_width/height)
                    if (episode.progress != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = episode.progress.coerceIn(0f, 1f))
                                    .background(EncoreColors.Green)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    EncorePlayButton(
                        isPlaying = false,
                        onClick = onPlayClick,
                        size = 32.dp,
                        iconSize = 14.dp
                    )
                }
            }
        }
    }
}

private fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
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
