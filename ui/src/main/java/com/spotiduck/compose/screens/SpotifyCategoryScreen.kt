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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.SpotifyArtworkImage
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

/**
 * Recommendation & Hub models for dedicated Category / Genre screens
 */
data class CategoryShelf(
    val title: String,
    val subtitle: String? = null,
    val items: List<CategoryRecommendationItem>
)

data class CategoryRecommendationItem(
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val coverColor: Color = Color(0xFF282828),
    val uri: String = "",
    val isAlbum: Boolean = false,
    val artistName: String? = null
)

data class CategorySubgenre(
    val title: String,
    val color: Color
)

/**
 * Helper to return signature Spotify genre colors for browse cards and category hub headers.
 */
fun getCategoryColor(title: String): Color = when (title.trim()) {
    "Podcasts" -> Color(0xFFE13300)
    "Live Events" -> Color(0xFF7358FF)
    "Made For You" -> Color(0xFF1E3264)
    "New Releases" -> Color(0xFFE8115B)
    "Pop" -> Color(0xFF148A08)
    "Hip-Hop" -> Color(0xFFBC5900)
    "Rock" -> Color(0xFFE91429)
    "Latin" -> Color(0xFFE1118C)
    "Dance/Electronic" -> Color(0xFFD84000)
    "Discover" -> Color(0xFF8D67AB)
    "Indie" -> Color(0xFF608108)
    "Workout" -> Color(0xFF777777)
    "R&B" -> Color(0xFFDC148C)
    "Chill" -> Color(0xFF432371)
    "Sleep" -> Color(0xFF1E3264)
    "Gaming" -> Color(0xFFE8115B)
    "Bollywood" -> Color(0xFFBA5D00)
    "Punjabi" -> Color(0xFF8D67AB)
    "Hindi" -> Color(0xFF148A08)
    "Tamil" -> Color(0xFFE1118C)
    "Telugu" -> Color(0xFFD84000)
    "Music" -> Color(0xFF1E3264)
    else -> Color(0xFF148A08)
}

/**
 * 1:1 Pixel-Perfect Spotify Category & Genre Recommendation Screen.
 *
 * Implements Spotify's Hub / Assisted Curation Recommendation architecture:
 * - Collapsible sticky top bar with back navigation and smooth title crossfade on scroll
 * - Dynamic category gradient starting at Y=0 tinted with the signature genre color
 * - Large Category Title header matching Encore typography
 * - Multi-shelf recommendation carousels (Featured Playlists, New Releases, Radios, Curated Mixes)
 * - Subcategory grid cards matching Spotify's 2-column browse style
 * - Safe mini player bottom padding via [rememberBottomPadding]
 */
@Composable
fun SpotifyCategoryScreen(
    categoryTitle: String,
    categoryColor: Color = getCategoryColor(categoryTitle),
    onBackClick: () -> Unit = {},
    onPlaylistClick: (String, String) -> Unit = { _, _ -> },
    onAlbumClick: (String, String) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {},
    onSubcategoryClick: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val bottomPadding = rememberBottomPadding()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 48.dp

    val (shelves, subgenres) = remember(categoryTitle, categoryColor) {
        getCategoryContent(categoryTitle, categoryColor)
    }

    // Top bar scroll alpha calculations
    val scrollOffset by remember {
        derivedStateOf {
            listState.firstVisibleItemScrollOffset + listState.firstVisibleItemIndex * 400
        }
    }
    val topBarAlpha by remember {
        derivedStateOf {
            ((scrollOffset - 30f) / 70f).coerceIn(0f, 1f)
        }
    }
    val topBarTitleAlpha by remember {
        derivedStateOf {
            ((scrollOffset - 80f) / 50f).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.PureBlack)
    ) {
        // Dynamic Genre Header Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            categoryColor.copy(alpha = 0.82f),
                            categoryColor.copy(alpha = 0.35f),
                            EncoreColors.PureBlack
                        )
                    )
                )
        )

        // Main Scrollable Category Feed
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBarHeight + toolbarHeight)
        ) {
            // Large Category Title Header
            item(key = "category_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = categoryTitle,
                        style = SpotifyTheme.typography.headlineLarge.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            // Recommendation Shelves
            items(shelves, key = { it.title }) { shelf ->
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    // Shelf Header
                    Text(
                        text = shelf.title,
                        style = SpotifyTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    if (!shelf.subtitle.isNullOrBlank()) {
                        Text(
                            text = shelf.subtitle,
                            style = SpotifyTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = EncoreColors.TextSecondary
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Card Carousel
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(shelf.items, key = { it.title + it.uri }) { item ->
                            Column(
                                modifier = Modifier
                                    .width(148.dp)
                                    .clickable {
                                        if (item.isAlbum) {
                                            onAlbumClick(item.title, item.artistName ?: "")
                                        } else {
                                            onPlaylistClick(item.title, item.uri.ifEmpty { "spotify:playlist:${item.title}" })
                                        }
                                    }
                            ) {
                                // Artwork Box with floating play button
                                Box(
                                    modifier = Modifier
                                        .size(148.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(item.coverColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!item.imageUrl.isNullOrBlank()) {
                                        SpotifyArtworkImage(
                                            imageUrl = item.imageUrl,
                                            contentDescription = item.title,
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

                                    // Floating Quick Play Button
                                    EncorePlayButton(
                                        isPlaying = false,
                                        onClick = {
                                            if (item.isAlbum) {
                                                onAlbumClick(item.title, item.artistName ?: "")
                                            } else {
                                                onPlaylistClick(
                                                    item.title,
                                                    item.uri.ifEmpty { "spotify:playlist:${item.title}" }
                                                )
                                            }
                                        },
                                        size = 36.dp,
                                        iconSize = 16.dp,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .shadow(elevation = 6.dp, shape = CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Item Title
                                Text(
                                    text = item.title,
                                    style = SpotifyTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = EncoreColors.TextPrimary,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Item Subtitle / Description
                                Text(
                                    text = item.subtitle,
                                    style = SpotifyTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = EncoreColors.TextSecondary,
                                        lineHeight = 16.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Subcategories / Explore Section
            if (subgenres.isNotEmpty()) {
                item(key = "subcategories_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 28.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = "Explore $categoryTitle",
                            style = SpotifyTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                // Chunk subgenres into pairs of 2 for a clean grid layout
                items(subgenres.chunked(2), key = { pair -> pair.joinToString { it.title } }) { rowPair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (sub in rowPair) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(sub.color)
                                    .clickable { onSubcategoryClick(sub.title) }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = sub.title,
                                    style = SpotifyTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    ),
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                            }
                        }
                        if (rowPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Bottom Spacing for MiniPlayer and Navigation Bar
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(bottomPadding + 16.dp))
            }
        }

        // Sticky Overlay Top Bar with Smooth Back Navigation and Title
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .background(categoryColor.copy(alpha = topBarAlpha))
                .statusBarsPadding()
                .height(toolbarHeight)
                .padding(horizontal = 16.dp)
        ) {
            // Back Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = (0.4f * (1f - topBarAlpha)).coerceIn(0f, 0.4f)))
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
            if (topBarTitleAlpha > 0.01f) {
                Text(
                    text = categoryTitle,
                    style = SpotifyTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = topBarTitleAlpha),
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.align(Alignment.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Returns curated Spotify recommendations and subcategories for the selected category.
 */
private fun getCategoryContent(
    title: String,
    categoryColor: Color
): Pair<List<CategoryShelf>, List<CategorySubgenre>> {
    return when (title.trim()) {
        "Pop" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Popular Pop Playlists",
                    subtitle = "The most played pop songs right now",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Today's Top Hits",
                            subtitle = "Dua Lipa is on top of the Hottest 50!",
                            imageUrl = "https://i.scdn.co/image/ab67706f000000021c3fa36c4b92b67f40192e44",
                            coverColor = Color(0xFF148A08),
                            uri = "spotify:playlist:todaystophits"
                        ),
                        CategoryRecommendationItem(
                            title = "Pop Rising",
                            subtitle = "Who's next in pop music. Cover: Chappell Roan",
                            coverColor = Color(0xFFE8115B),
                            uri = "spotify:playlist:poprising"
                        ),
                        CategoryRecommendationItem(
                            title = "Mega Hit Mix",
                            subtitle = "A mega mix of 75 favorites from the last few years",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:playlist:megahitmix"
                        ),
                        CategoryRecommendationItem(
                            title = "Hit Rewind",
                            subtitle = "Listen to all the hits you may have forgotten",
                            coverColor = Color(0xFFBC5900),
                            uri = "spotify:playlist:hitrewind"
                        ),
                        CategoryRecommendationItem(
                            title = "Pop Sauce",
                            subtitle = "The smoothest flavors in pop. Cover: Sabrina Carpenter",
                            coverColor = Color(0xFFD84000),
                            uri = "spotify:playlist:popsauce"
                        )
                    )
                ),
                CategoryShelf(
                    title = "New Pop Releases",
                    subtitle = "Fresh singles and albums",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Short n' Sweet",
                            subtitle = "Sabrina Carpenter • Album",
                            imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452",
                            coverColor = Color(0xFF6B1A24),
                            isAlbum = true,
                            artistName = "Sabrina Carpenter"
                        ),
                        CategoryRecommendationItem(
                            title = "HIT ME HARD AND SOFT",
                            subtitle = "Billie Eilish • Album",
                            coverColor = Color(0xFF1E3264),
                            isAlbum = true,
                            artistName = "Billie Eilish"
                        ),
                        CategoryRecommendationItem(
                            title = "Radical Optimism",
                            subtitle = "Dua Lipa • Album",
                            coverColor = Color(0xFFE1118C),
                            isAlbum = true,
                            artistName = "Dua Lipa"
                        ),
                        CategoryRecommendationItem(
                            title = "GUTS (spilled)",
                            subtitle = "Olivia Rodrigo • Album",
                            coverColor = Color(0xFF432371),
                            isAlbum = true,
                            artistName = "Olivia Rodrigo"
                        )
                    )
                ),
                CategoryShelf(
                    title = "Pop Radio & Mixes",
                    subtitle = "Continuous music tailored to your taste",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Pop Radio",
                            subtitle = "With Taylor Swift, Ariana Grande, and more",
                            coverColor = Color(0xFF148A08),
                            uri = "spotify:radio:pop"
                        ),
                        CategoryRecommendationItem(
                            title = "2010s Pop Mix",
                            subtitle = "Iconic anthems from Katy Perry, Rihanna, and Pitbull",
                            coverColor = Color(0xFF7358FF),
                            uri = "spotify:mix:2010spop"
                        ),
                        CategoryRecommendationItem(
                            title = "Dance Pop Hits",
                            subtitle = "Upbeat danceable pop anthems",
                            coverColor = Color(0xFFD84000),
                            uri = "spotify:mix:dancepop"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("Dance Pop", Color(0xFFD84000)),
                CategorySubgenre("Indie Pop", Color(0xFF608108)),
                CategorySubgenre("Synth-Pop", Color(0xFF8D67AB)),
                CategorySubgenre("K-Pop", Color(0xFFE1118C)),
                CategorySubgenre("Teen Pop", Color(0xFFE8115B)),
                CategorySubgenre("Bedroom Pop", Color(0xFF432371))
            )
            shelves to subgenres
        }
        "Rock" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Popular Rock Playlists",
                    subtitle = "Rock legends & epic songs that continue to inspire",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Rock Classics",
                            subtitle = "Rock legends & epic songs. Cover: Queen",
                            coverColor = Color(0xFFE91429),
                            uri = "spotify:playlist:rockclassics"
                        ),
                        CategoryRecommendationItem(
                            title = "Rock This",
                            subtitle = "The hottest new rock tracks. Cover: Linkin Park",
                            coverColor = Color(0xFF777777),
                            uri = "spotify:playlist:rockthis"
                        ),
                        CategoryRecommendationItem(
                            title = "90s Rock Renaissance",
                            subtitle = "Grunge, alt-rock, and punk rock essentials",
                            coverColor = Color(0xFFBC5900),
                            uri = "spotify:playlist:90srock"
                        ),
                        CategoryRecommendationItem(
                            title = "Hard Rock",
                            subtitle = "Heavy riffs, pounding drums, and pure adrenaline",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:playlist:hardrock"
                        )
                    )
                ),
                CategoryShelf(
                    title = "Essential Rock Albums",
                    subtitle = "Masterpieces that defined rock history",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Hybrid Theory",
                            subtitle = "Linkin Park • Album",
                            coverColor = Color(0xFF535353),
                            isAlbum = true,
                            artistName = "Linkin Park"
                        ),
                        CategoryRecommendationItem(
                            title = "Nevermind",
                            subtitle = "Nirvana • Album",
                            coverColor = Color(0xFF1E3264),
                            isAlbum = true,
                            artistName = "Nirvana"
                        ),
                        CategoryRecommendationItem(
                            title = "The Dark Side of the Moon",
                            subtitle = "Pink Floyd • Album",
                            coverColor = Color(0xFF282828),
                            isAlbum = true,
                            artistName = "Pink Floyd"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("Classic Rock", Color(0xFFE91429)),
                CategorySubgenre("Indie Rock", Color(0xFF608108)),
                CategorySubgenre("Alternative", Color(0xFF7358FF)),
                CategorySubgenre("Hard Rock", Color(0xFFBC5900)),
                CategorySubgenre("Punk", Color(0xFFE8115B)),
                CategorySubgenre("Metal", Color(0xFF333333))
            )
            shelves to subgenres
        }
        "Podcasts" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Trending Podcasts",
                    subtitle = "Episodes people can't stop listening to",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "The Joe Rogan Experience",
                            subtitle = "Long-form conversations with guests from all walks of life",
                            coverColor = Color(0xFFE13300),
                            uri = "spotify:show:joerogan"
                        ),
                        CategoryRecommendationItem(
                            title = "Huberman Lab",
                            subtitle = "Science and science-based tools for everyday life",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:show:huberman"
                        ),
                        CategoryRecommendationItem(
                            title = "Anything Goes with Emma",
                            subtitle = "Thoughts, conversations, and advice from Emma Chamberlain",
                            coverColor = Color(0xFF8D67AB),
                            uri = "spotify:show:emmachamberlain"
                        ),
                        CategoryRecommendationItem(
                            title = "The Daily",
                            subtitle = "This is what the news should sound like. Hosted by Michael Barbaro",
                            coverColor = Color(0xFF535353),
                            uri = "spotify:show:thedaily"
                        )
                    )
                ),
                CategoryShelf(
                    title = "Stories & Horror",
                    subtitle = "Real horror and immersive paranormal thrillers",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Horror Station",
                            subtitle = "Spine-chilling paranormal stories and true horrors",
                            coverColor = Color(0xFF6B1A24),
                            uri = "spotify:show:horrorstation"
                        ),
                        CategoryRecommendationItem(
                            title = "Radio Rental",
                            subtitle = "True, scary, and strange stories from around the world",
                            coverColor = Color(0xFFBC5900),
                            uri = "spotify:show:radiorental"
                        ),
                        CategoryRecommendationItem(
                            title = "Serial Killers",
                            subtitle = "A psychological journey into history's most notorious minds",
                            coverColor = Color(0xFF282828),
                            uri = "spotify:show:serialkillers"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("True Crime", Color(0xFF6B1A24)),
                CategorySubgenre("Comedy", Color(0xFFBC5900)),
                CategorySubgenre("News & Politics", Color(0xFF1E3264)),
                CategorySubgenre("Educational", Color(0xFF608108)),
                CategorySubgenre("Health & Fitness", Color(0xFF148A08)),
                CategorySubgenre("Business & Tech", Color(0xFF7358FF))
            )
            shelves to subgenres
        }
        "Made For You" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Your Daily Mixes",
                    subtitle = "Personalized playlists based on your listening habits",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Daily Mix 1",
                            subtitle = "The Weeknd, Daft Punk, Post Malone, and more",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:playlist:dailymix1"
                        ),
                        CategoryRecommendationItem(
                            title = "Daily Mix 2",
                            subtitle = "Taylor Swift, Olivia Rodrigo, Sabrina Carpenter, and more",
                            coverColor = Color(0xFFE8115B),
                            uri = "spotify:playlist:dailymix2"
                        ),
                        CategoryRecommendationItem(
                            title = "Daily Mix 3",
                            subtitle = "Coldplay, Imagine Dragons, OneRepublic, and more",
                            coverColor = Color(0xFF148A08),
                            uri = "spotify:playlist:dailymix3"
                        ),
                        CategoryRecommendationItem(
                            title = "Daily Mix 4",
                            subtitle = "Pritam, Arijit Singh, Mohit Chauhan, and more",
                            coverColor = Color(0xFFBC5900),
                            uri = "spotify:playlist:dailymix4"
                        )
                    )
                ),
                CategoryShelf(
                    title = "Uniquely Yours",
                    subtitle = "Playlists crafted just for you",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Discover Weekly",
                            subtitle = "Your weekly mixtape of fresh music. Updated every Monday",
                            coverColor = Color(0xFF8D67AB),
                            uri = "spotify:playlist:discoverweekly"
                        ),
                        CategoryRecommendationItem(
                            title = "Release Radar",
                            subtitle = "Catch all the latest music from artists you follow. Updates Friday",
                            coverColor = Color(0xFF7358FF),
                            uri = "spotify:playlist:releaseradar"
                        ),
                        CategoryRecommendationItem(
                            title = "On Repeat",
                            subtitle = "The songs you love right now, all in one place",
                            coverColor = Color(0xFFD84000),
                            uri = "spotify:playlist:onrepeat"
                        ),
                        CategoryRecommendationItem(
                            title = "Repeat Rewind",
                            subtitle = "The songs you couldn't get enough of in the past",
                            coverColor = Color(0xFF608108),
                            uri = "spotify:playlist:repeatrewind"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("Chill Mix", Color(0xFF432371)),
                CategorySubgenre("Upbeat Mix", Color(0xFFE8115B)),
                CategorySubgenre("Focus Mix", Color(0xFF1E3264)),
                CategorySubgenre("Workout Mix", Color(0xFFD84000))
            )
            shelves to subgenres
        }
        "New Releases" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Hot New Albums & Singles",
                    subtitle = "The most anticipated drops of the week",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Short n' Sweet",
                            subtitle = "Sabrina Carpenter • Album",
                            coverColor = Color(0xFFE8115B),
                            isAlbum = true,
                            artistName = "Sabrina Carpenter"
                        ),
                        CategoryRecommendationItem(
                            title = "Chromakopia",
                            subtitle = "Tyler, The Creator • Album",
                            coverColor = Color(0xFF608108),
                            isAlbum = true,
                            artistName = "Tyler, The Creator"
                        ),
                        CategoryRecommendationItem(
                            title = "The Great Impersonator",
                            subtitle = "Halsey • Album",
                            coverColor = Color(0xFF432371),
                            isAlbum = true,
                            artistName = "Halsey"
                        )
                    )
                ),
                CategoryShelf(
                    title = "New Playlists",
                    subtitle = "Handpicked by Spotify editors",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "New Music Friday",
                            subtitle = "The best new releases of the week from all genres",
                            coverColor = Color(0xFFE8115B),
                            uri = "spotify:playlist:newmusicfriday"
                        ),
                        CategoryRecommendationItem(
                            title = "Release Radar",
                            subtitle = "New tracks from artists you love. Updates every Friday",
                            coverColor = Color(0xFF7358FF),
                            uri = "spotify:playlist:releaseradar"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("New Pop", Color(0xFF148A08)),
                CategorySubgenre("New Hip-Hop", Color(0xFFBC5900)),
                CategorySubgenre("New Rock", Color(0xFFE91429)),
                CategorySubgenre("New Dance", Color(0xFFD84000))
            )
            shelves to subgenres
        }
        "Hip-Hop" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Popular Hip-Hop Playlists",
                    subtitle = "The essential playlists defining rap culture",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "RapCaviar",
                            subtitle = "New music from Kendrick Lamar, Future, and Metro Boomin",
                            coverColor = Color(0xFFBC5900),
                            uri = "spotify:playlist:rapcaviar"
                        ),
                        CategoryRecommendationItem(
                            title = "Get Turnt",
                            subtitle = "Hype hip-hop for the gym and the pre-game",
                            coverColor = Color(0xFFE91429),
                            uri = "spotify:playlist:getturnt"
                        ),
                        CategoryRecommendationItem(
                            title = "Most Necessary",
                            subtitle = "The official voice of generation next",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:playlist:mostnecessary"
                        ),
                        CategoryRecommendationItem(
                            title = "Gold School",
                            subtitle = "Classic 90s and 2000s rap royalty",
                            coverColor = Color(0xFF777777),
                            uri = "spotify:playlist:goldschool"
                        )
                    )
                ),
                CategoryShelf(
                    title = "Featured Albums",
                    subtitle = "Recent chart toppers",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "GNX",
                            subtitle = "Kendrick Lamar • Album",
                            coverColor = Color(0xFF282828),
                            isAlbum = true,
                            artistName = "Kendrick Lamar"
                        ),
                        CategoryRecommendationItem(
                            title = "WE DON'T TRUST YOU",
                            subtitle = "Future & Metro Boomin • Album",
                            coverColor = Color(0xFF535353),
                            isAlbum = true,
                            artistName = "Future"
                        ),
                        CategoryRecommendationItem(
                            title = "UTOPIA",
                            subtitle = "Travis Scott • Album",
                            coverColor = Color(0xFF1E3264),
                            isAlbum = true,
                            artistName = "Travis Scott"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("Trap", Color(0xFFBC5900)),
                CategorySubgenre("Old School", Color(0xFF777777)),
                CategorySubgenre("Chill Rap", Color(0xFF432371)),
                CategorySubgenre("UK Drill", Color(0xFFE91429))
            )
            shelves to subgenres
        }
        "Chill" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Popular Chill Playlists",
                    subtitle = "Peaceful, calm, and cozy selections",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Chill Hits",
                            subtitle = "Kick back to the best new and recent chill hits",
                            coverColor = Color(0xFF432371),
                            uri = "spotify:playlist:chillhits"
                        ),
                        CategoryRecommendationItem(
                            title = "Lo-Fi Beats",
                            subtitle = "Beats to relax, study, and focus to",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:playlist:lofi"
                        ),
                        CategoryRecommendationItem(
                            title = "Night Rain",
                            subtitle = "Gentle rain sounds and soothing sleep ambient",
                            coverColor = Color(0xFF282828),
                            uri = "spotify:playlist:nightrain"
                        )
                    )
                ),
                CategoryShelf(
                    title = "Acoustic & Instrumental",
                    subtitle = "Stripped back strings and piano chords",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Acoustic Concentration",
                            subtitle = "Calm acoustic instrumentals to keep you centered",
                            coverColor = Color(0xFF608108),
                            uri = "spotify:playlist:acousticfocus"
                        ),
                        CategoryRecommendationItem(
                            title = "Peaceful Piano",
                            subtitle = "Relax and indulge with beautiful piano pieces",
                            coverColor = Color(0xFF8D67AB),
                            uri = "spotify:playlist:peacefulpiano"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("Lo-Fi", Color(0xFF432371)),
                CategorySubgenre("Acoustic", Color(0xFF608108)),
                CategorySubgenre("Ambient", Color(0xFF1E3264)),
                CategorySubgenre("Focus", Color(0xFF7358FF))
            )
            shelves to subgenres
        }
        "Workout" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "High Energy Playlists",
                    subtitle = "Get pumped and crush your workout",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Beast Mode",
                            subtitle = "Get your beast mode on with high energy hip hop",
                            coverColor = Color(0xFF777777),
                            uri = "spotify:playlist:beastmode"
                        ),
                        CategoryRecommendationItem(
                            title = "Workout Beats",
                            subtitle = "Dance and electronic beats to power your cardio",
                            coverColor = Color(0xFFD84000),
                            uri = "spotify:playlist:workoutbeats"
                        ),
                        CategoryRecommendationItem(
                            title = "Pumped Pop",
                            subtitle = "High tempo pop hits to keep the adrenaline going",
                            coverColor = Color(0xFFE8115B),
                            uri = "spotify:playlist:pumpedpop"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("Cardio", Color(0xFFD84000)),
                CategorySubgenre("Weight Lifting", Color(0xFF777777)),
                CategorySubgenre("Running", Color(0xFF148A08)),
                CategorySubgenre("Yoga & Cool Down", Color(0xFF432371))
            )
            shelves to subgenres
        }
        "Dance/Electronic" -> {
            val shelves = listOf(
                CategoryShelf(
                    title = "Top Dance Playlists",
                    subtitle = "Club bangers, festivals, and late night sets",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "Mint",
                            subtitle = "The world's biggest dance & EDM tracks",
                            coverColor = Color(0xFFD84000),
                            uri = "spotify:playlist:mint"
                        ),
                        CategoryRecommendationItem(
                            title = "Dance Party",
                            subtitle = "Non-stop party anthems from Calvin Harris, David Guetta, and Tiësto",
                            coverColor = Color(0xFF7358FF),
                            uri = "spotify:playlist:danceparty"
                        ),
                        CategoryRecommendationItem(
                            title = "Deep House Relax",
                            subtitle = "Mellow deep house grooves for after hours",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:playlist:deephouse"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("EDM", Color(0xFFD84000)),
                CategorySubgenre("House", Color(0xFF7358FF)),
                CategorySubgenre("Techno", Color(0xFF282828)),
                CategorySubgenre("Drum & Bass", Color(0xFFE91429))
            )
            shelves to subgenres
        }
        else -> {
            // General Hub Layout for any other category
            val shelves = listOf(
                CategoryShelf(
                    title = "Popular $title Playlists",
                    subtitle = "The biggest hits and essential tracks in $title",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "$title Hits",
                            subtitle = "The most popular songs in $title right now",
                            coverColor = categoryColor,
                            uri = "spotify:playlist:${title.lowercase()}_hits"
                        ),
                        CategoryRecommendationItem(
                            title = "$title Classics",
                            subtitle = "Timeless classics and essential anthems",
                            coverColor = categoryColor.copy(alpha = 0.7f),
                            uri = "spotify:playlist:${title.lowercase()}_classics"
                        ),
                        CategoryRecommendationItem(
                            title = "Fresh $title",
                            subtitle = "The latest additions and rising tracks",
                            coverColor = Color(0xFF1E3264),
                            uri = "spotify:playlist:${title.lowercase()}_fresh"
                        )
                    )
                ),
                CategoryShelf(
                    title = "$title Radio & Mixes",
                    subtitle = "Endless recommendations tailored to your taste",
                    items = listOf(
                        CategoryRecommendationItem(
                            title = "$title Radio",
                            subtitle = "With artists you love and new discoveries",
                            coverColor = categoryColor,
                            uri = "spotify:radio:${title.lowercase()}"
                        ),
                        CategoryRecommendationItem(
                            title = "$title Mix",
                            subtitle = "A customized mix based on your listening history",
                            coverColor = Color(0xFF7358FF),
                            uri = "spotify:mix:${title.lowercase()}"
                        )
                    )
                )
            )
            val subgenres = listOf(
                CategorySubgenre("$title Essentials", categoryColor),
                CategorySubgenre("Acoustic $title", Color(0xFF608108)),
                CategorySubgenre("Chill $title", Color(0xFF432371)),
                CategorySubgenre("Live $title", Color(0xFFE8115B))
            )
            shelves to subgenres
        }
    }
}
