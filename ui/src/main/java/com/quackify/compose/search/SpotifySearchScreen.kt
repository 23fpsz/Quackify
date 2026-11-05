package com.quackify.compose.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.R
import com.quackify.compose.components.EncoreChip
import com.quackify.compose.components.EncoreProfileAvatar
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.EncorePlayIndicator
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.format.parseHexColor
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.theme.rememberBottomPadding
import com.quackify.compose.util.rememberIsOffline

data class CategoryCard(
    val title: String,
    val color: Color,
    val imageUrl: String? = null
)

data class SearchArtistItem(
    val name: String,
    val monthlyListeners: String,
    val color: Color,
    val uri: String = ""
)

data class SearchAlbumItem(
    val title: String,
    val artist: String,
    val year: String,
    val color: Color,
    val uri: String = ""
)

data class SearchPlaylistItem(
    val title: String,
    val creator: String,
    val followerCount: String,
    val color: Color,
    val uri: String = ""
)

data class SearchPodcastItem(
    val title: String,
    val publisher: String,
    val color: Color,
    val description: String,
    val uri: String = ""
)

data class SearchHistoryItem(
    val title: String,
    val subtitle: String,
    val isArtist: Boolean = false,
    val isQuery: Boolean = false,
    val color: Color = Color(0xFF1E3264)
)

/**
 * Spotify Search & Browse Screen matching Spotify APK:
 * - Profile avatar shortcut
 * - Interactive Search pill with Voice Mic and Clear button
 * - In-search category filter pills (Top, Songs, Artists, Playlists, Albums, Podcasts)
 * - Top result card, Songs list with 3-dot context actions, and Artists cards
 * - Recent Searches list with "Clear recent searches" button
 * - 2-column colorful Browse All categories grid with angled album peek (96dp category_card_max_height)
 */
@Composable
fun SpotifySearchScreen(
    onCategoryClick: (String) -> Unit,
    onTrackSelect: (TrackItem) -> Unit = {},
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAlbumClick: (String, String, String) -> Unit = { _, _, _ -> },
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onPodcastClick: (String, String) -> Unit = { _, _ -> },
    searchData: SearchResults? = null,
    onPlaylistClick: (String, String) -> Unit = { _, _ -> },
    searchResults: List<TrackItem>? = null,
    onSearchQueryChange: (String) -> Unit = {},
    recentSearches: List<SearchHistoryItem> = emptyList(),
    suggestions: List<TrackItem> = emptyList(),
    loading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onToggleSaved: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    onRemoveRecent: (SearchHistoryItem) -> Unit = {},
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    currentTrackTitle: String = "",
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchTab by remember { mutableIntStateOf(0) }
    var isSearchBarFocused by remember { mutableStateOf(false) }
    val searchTabs = listOf("Top", "Songs", "Artists", "Playlists", "Albums", "Podcasts")
    val focusManager = LocalFocusManager.current
    val bottomPadding = rememberBottomPadding()

    // The same live connectivity the shell's offline banner uses: searching
    // with the radio off must say so instead of spinning on "Searching...".
    val isOffline = rememberIsOffline()

    // Voice search: hand the query to the system speech recognizer and feed the
    // transcript straight into the search flow
    val appContext = LocalContext.current
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to listen to?")
        }
    }
    val canVoiceSearch = remember {
        appContext.packageManager.queryIntentActivities(speechIntent, 0).isNotEmpty()
    }
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (heard.isNotBlank()) {
            searchQuery = heard
            onSearchQueryChange(heard)
        }
    }

    val recentSearches = remember(recentSearches) {
        mutableStateListOf<SearchHistoryItem>().apply { addAll(recentSearches) }
    }

    val categories = remember {
        listOf(
            CategoryCard("Podcasts", Color(0xFFE13300)),
            CategoryCard("Live Events", Color(0xFF7358FF)),
            CategoryCard("Made For You", Color(0xFF1E3264)),
            CategoryCard("New Releases", Color(0xFFE8115B)),
            CategoryCard("Pop", Color(0xFF148A08)),
            CategoryCard("Hip-Hop", Color(0xFFBC5900)),
            CategoryCard("Rock", Color(0xFFE91429)),
            CategoryCard("Latin", Color(0xFFE1118C)),
            CategoryCard("Dance/Electronic", Color(0xFFD84000)),
            CategoryCard("Discover", Color(0xFF8D67AB)),
            CategoryCard("Indie", Color(0xFF608108)),
            CategoryCard("Workout", Color(0xFF777777)),
            CategoryCard("R&B", Color(0xFFDC148C)),
            CategoryCard("Chill", Color(0xFF432371)),
            CategoryCard("Sleep", Color(0xFF1E3264)),
            CategoryCard("Gaming", Color(0xFFE8115B))
        )
    }
    val allSearchableTracks = remember(searchData) {
        searchData?.tracks.orEmpty().map { it.toTrackItem() }
    }
    val allSearchableArtists = remember(searchData) {
        searchData?.artists.orEmpty().map { hit ->
            SearchArtistItem(
                name = hit.title,
                monthlyListeners = "",
                color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                uri = hit.uri
            )
        }
    }
    val allSearchableAlbums = remember(searchData) {
        searchData?.albums.orEmpty().map { hit ->
            SearchAlbumItem(
                title = hit.title,
                artist = hit.subtitle,
                year = "",
                color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                uri = hit.uri
            )
        }
    }
    val allSearchablePlaylists = remember(searchData) {
        searchData?.playlists.orEmpty().map { hit ->
            SearchPlaylistItem(
                title = hit.title,
                creator = hit.subtitle,
                followerCount = "",
                color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                uri = hit.uri
            )
        }
    }

    val allSearchablePodcasts = remember(searchData) {
        searchData?.others.orEmpty()
            .filter { it.kind == SearchKind.SHOW || it.kind == SearchKind.EPISODE }
            .map { hit ->
                SearchPodcastItem(
                    title = hit.title,
                    publisher = hit.subtitle,
                    color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                    description = "",
                    uri = hit.uri
                )
            }
    }

    val filteredTracks = remember(searchQuery, searchResults, suggestions, loading, searchData) {
        when {
            searchQuery.isBlank() -> emptyList()
            // The full search knows the covers and artist lines; the
            // as-you-type suggestions only carry names, so once the real
            // results land they take over the list.
            !searchData?.tracks.isNullOrEmpty() -> searchData!!.tracks.map { it.toTrackItem() }
            !searchResults.isNullOrEmpty() -> searchResults
            !loading && suggestions.isNotEmpty() -> suggestions
            else -> allSearchableTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredArtists = remember(searchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchableArtists.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredAlbums = remember(searchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchableAlbums.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredPlaylists = remember(searchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchablePlaylists.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.creator.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredPodcasts = remember(searchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchablePodcasts.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.publisher.contains(searchQuery, ignoreCase = true)
        }
    }

    val isSearching = searchQuery.isNotBlank() || isSearchBarFocused
    val showRecentSearches = isSearchBarFocused && searchQuery.isEmpty() && recentSearches.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        // Sticky Header: Avatar, "Search" Title & Modern Search Header Field
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!isSearching) {
                // Browse Header: Profile Avatar + "Search" Title + Camera Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreProfileAvatar(
                        userDisplayName = userDisplayName,
                        avatarUrl = userAvatarUrl.ifBlank { null },
                        onClick = onProfileClick
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Search",
                        style = SpotifyTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Camera Button (find_header_profile.xml: encore_camera_button_size)
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_camera_24,
                        contentDescription = "Scan Spotify Code",
                        onClick = { /* Open Camera Scan */ },
                        size = 40.dp,
                        iconSize = 24.dp,
                        tint = EncoreColors.TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Spotify Search Pill Bar (matching modern_search_header.xml)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearching) {
                    // Back button view (48dp x 48dp)
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_chevron_left_24,
                        contentDescription = "Back",
                        onClick = {
                            searchQuery = ""
                            isSearchBarFocused = false
                            focusManager.clearFocus()
                        },
                        size = 48.dp,
                        iconSize = 24.dp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                // Input box with gray_15 background (#282828) or white when idle
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchQueryChange(it)
                    },
                    textStyle = SpotifyTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (isSearching) Color.White else Color(0xFF121212)
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(EncoreColors.Green),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_search_24),
                                contentDescription = "Search",
                                tint = if (isSearching) EncoreColors.TextSecondary else Color(0xFF121212),
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "What do you want to listen to?",
                                        style = SpotifyTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSearching) EncoreColors.TextSecondary else Color(0xFF757575)
                                        )
                                    )
                                }
                                innerTextField()
                            }

                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_x_24),
                                    contentDescription = "Clear search",
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            searchQuery = ""
                                            onSearchQueryChange("")
                                            focusManager.clearFocus()
                                        }
                                )
                            } else if (isSearchBarFocused && canVoiceSearch) {
                                // Voice search mic — only visible when search bar is focused (active state)
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_mic_24),
                                    contentDescription = "Voice search",
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable {
                                            runCatching { voiceSearchLauncher.launch(speechIntent) }
                                        }
                                )
                            }
                            // Idle state: no icon on right side of pill (avoids duplicate mic confusion)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSearching) Color(0xFF282828) else Color.White)
                        .onFocusChanged { isSearchBarFocused = it.isFocused }
                        .padding(horizontal = 12.dp)
                )
            }

            // Category filter chips: ONLY visible when a search query is typed (matches filter_compose visibility="gone" default in search_fragment_layout.xml)
            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchTabs.size) { index ->
                        EncoreChip(
                            text = searchTabs[index],
                            isSelected = selectedSearchTab == index,
                            onClick = { selectedSearchTab = index }
                        )
                    }
                }
            }
        }

        // Content Area: Switched dynamically based on state
        // 1. Idle (unfocused, empty query): Categories Browse Grid
        // 2. Focused & Empty query: Recent Searches List (history_row_search_layout.xml)
        // 3. Query entered: Search suggestions & Search results
        if (showRecentSearches) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EncoreColors.Background),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
            ) {
                // Header: "Recent searches"
                item {
                    Text(
                        text = "Recent searches",
                        style = SpotifyTheme.typography.headlineLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = EncoreColors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                // History rows matching history_row_search_layout.xml:
                // - artwork (48dp, circle for artist, 4dp rounded for tracks/playlists)
                // - title (BodyMedium, encore_row_title)
                // - subtitle (BodySmall, encore_row_subtitle)
                // - remove button (spacer_48 = 48dp tap area, encore_accessory tint)
                items(recentSearches, key = { it.title }) { searchItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                searchQuery = searchItem.title
                                onSearchQueryChange(searchItem.title)
                            }
                            .padding(start = 16.dp, end = 0.dp)
                            .height(64.dp), // minHeight = 64.0dp from history_row_search_layout.xml
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork view (@dimen/track_row_cover_art_size = 48.0dp)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(if (searchItem.isArtist) CircleShape else RoundedCornerShape(4.dp))
                                .background(searchItem.color),
                            contentAlignment = Alignment.Center
                        ) {
                            if (searchItem.isQuery) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_search_24),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = if (searchItem.isArtist) "★" else "♫",
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Subtitle column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = searchItem.title,
                                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = EncoreColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = searchItem.subtitle,
                                style = SpotifyTheme.typography.bodySmall,
                                color = EncoreColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Remove button matching ClearButtonView (spacer_48 = 48.0dp, encore_icon_x)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    recentSearches.remove(searchItem)
                                    onRemoveRecent(searchItem)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_x_24),
                                contentDescription = "Remove from recent searches",
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Centered "Clear recent searches" button matching history_heading_clear_search_layout.xml
                // Uses Encore.Button.Secondary.Small (pill outlined button, 32dp height, BodySmallBold)
                if (recentSearches.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        BorderStroke(1.dp, Color(0xFF727272)),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                recentSearches.clear()
                                onClearRecent()
                            }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Clear recent searches",
                                    style = SpotifyTheme.typography.bodySmallBold.copy(
                                        color = EncoreColors.TextPrimary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else if (searchQuery.isNotEmpty()) {
            val hasResults = filteredTracks.isNotEmpty() ||
                    filteredArtists.isNotEmpty() ||
                    filteredAlbums.isNotEmpty() ||
                    filteredPlaylists.isNotEmpty() ||
                    filteredPodcasts.isNotEmpty()

            if (loading && !isOffline) {
                // Debounce + fetch in flight: show feedback instead of a premature
                // "Couldn't find" while the results have not landed yet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = EncoreColors.Green,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching \"$searchQuery\"...",
                            style = SpotifyTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (!hasResults && (isOffline || loadFailed)) {
                // Nothing came back because the network is gone or the query
                // itself failed - the same card the playlist screen shows,
                // with a retry instead of a misleading "couldn't find"
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_cloud_off_24),
                            contentDescription = null,
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isOffline) "You're offline" else "Couldn't load search results",
                            style = SpotifyTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Check your internet connection and try again.",
                            style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onRetry) {
                            Text(text = "Try again", color = EncoreColors.Green)
                        }
                    }
                }
            } else if (!hasResults) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Couldn't find \"$searchQuery\"",
                            style = SpotifyTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try searching again using a different spelling or keyword.",
                            style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // TAB 0: "Top" (Composite: Top Result + Songs + Artists + Albums + Playlists)
                    if (selectedSearchTab == 0) {
                        // Top Result Card
                        val topHit = searchData?.top
                        val topTrack = if (topHit == null) filteredTracks.firstOrNull() else null
                        if (topHit != null || topTrack != null) {
                            item {
                                val top = topHit?.toTrackItem() ?: topTrack!!
                                val topLabel = topHit?.kind?.sectionLabel() ?: "Song"
                                val openTopResult = {
                                    val hit = topHit
                                    if (hit == null) {
                                        onTrackSelect(top)
                                    } else {
                                        when (hit.kind) {
                                            SearchKind.ARTIST -> onArtistClick(hit.title, hit.uri)
                                            SearchKind.ALBUM -> onAlbumClick(hit.title, hit.subtitle, hit.uri)
                                            SearchKind.PLAYLIST -> onPlaylistClick(hit.title, hit.uri)
                                            SearchKind.SHOW, SearchKind.EPISODE -> onPodcastClick(hit.title, hit.uri)
                                            else -> onTrackSelect(hit.toTrackItem())
                                        }
                                    }
                                }
                                val isThisPlaying = isPlaying && top.title == currentTrackTitle

                                Text(
                                    text = "Top result",
                                    style = SpotifyTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EncoreColors.Card)
                                        .clickable { openTopResult() }
                                        .padding(16.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF8D67AB), Color(0xFF1E3264))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (top.imageUrl != null) {
                                                SpotifyArtworkImage(
                                                    imageUrl = top.imageUrl,
                                                    contentDescription = top.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    cornerRadius = 4.dp
                                                )
                                            } else {
                                                Text(text = "♫", fontSize = 32.sp, color = Color.White)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = top.title,
                                            style = SpotifyTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = topLabel,
                                                style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary)
                                            )
                                            Text(
                                                text = " • ${top.artist}",
                                                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    EncorePlayButton(
                                        isPlaying = isThisPlaying,
                                        onClick = { openTopResult() },
                                        size = 48.dp,
                                        iconSize = 22.dp,
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        // Songs subset (up to 4)
                        if (filteredTracks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Songs",
                                    style = SpotifyTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredTracks.take(4)) { track ->
                                val isThisPlaying = isPlaying && track.title == currentTrackTitle
                                SearchTrackRow(
                                    track = track,
                                    isPlaying = isThisPlaying,
                                    onClick = { onTrackSelect(track) },
                                    onMoreClick = { onTrackOptionsClick(track) },
                                    onLikeClick = { onToggleSaved(track.uri) }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Artists subset (up to 2)
                        if (filteredArtists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Artists",
                                    style = SpotifyTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredArtists.take(2)) { artist ->
                                ArtistRow(
                                    artist = artist,
                                    onArtistClick = { onArtistClick(artist.name, artist.uri) }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Albums subset
                        if (filteredAlbums.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Albums",
                                    style = SpotifyTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredAlbums.take(2)) { album ->
                                AlbumRow(
                                    album = album,
                                    onClick = { onAlbumClick(album.title, album.artist, album.uri) }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    // TAB 1: "Songs"
                    if (selectedSearchTab == 1) {
                        items(filteredTracks) { track ->
                            val isThisPlaying = isPlaying && track.title == currentTrackTitle
                            SearchTrackRow(
                                track = track,
                                isPlaying = isThisPlaying,
                                onClick = { onTrackSelect(track) },
                                onMoreClick = { onTrackOptionsClick(track) },
                                onLikeClick = { onToggleSaved(track.uri) }
                            )
                        }
                    }

                    // TAB 2: "Artists"
                    if (selectedSearchTab == 2) {
                        items(filteredArtists) { artist ->
                            ArtistRow(
                                artist = artist,
                                onArtistClick = { onArtistClick(artist.name, artist.uri) }
                            )
                        }
                    }

                    // TAB 3: "Playlists"
                    if (selectedSearchTab == 3) {
                        items(filteredPlaylists) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.title, playlist.uri) }
                            )
                        }
                    }

                    // TAB 4: "Albums"
                    if (selectedSearchTab == 4) {
                        items(filteredAlbums) { album ->
                            AlbumRow(
                                album = album,
                                onClick = { onAlbumClick(album.title, album.artist, album.uri) }
                            )
                        }
                    }

                    // TAB 5: "Podcasts"
                    if (selectedSearchTab == 5) {
                        items(filteredPodcasts) { podcast ->
                            PodcastRow(
                                podcast = podcast,
                                onClick = { onPodcastClick(podcast.title, podcast.uri) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(bottomPadding))
                    }
                }
            }
        } else {
            // Idle State: Browse All Categories Grid (recent searches now shown as dropdown overlay)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "Browse all",
                        style = SpotifyTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Category Cards (96dp category_card_max_height from dimens.xml, 4dp corner radius)
                items(categories) { category ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(category.color)
                            .clickable { onCategoryClick(category.title) }
                    ) {
                        Text(
                            text = category.title,
                            style = SpotifyTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .fillMaxWidth(0.65f)
                        )

                        // Rotated Art Thumbnail Peek (offset 16dp x, 6dp y, 25 deg rotate)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 16.dp, y = 6.dp)
                                .rotate(25f)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!category.imageUrl.isNullOrBlank()) {
                                SpotifyArtworkImage(
                                    imageUrl = category.imageUrl,
                                    contentDescription = category.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            } else {
                                Text(text = "♫", fontSize = 24.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search Track Row matching decompiled `track_row_search_layout.xml`:
 * - 48dp cover art thumbnail (`@dimen/track_row_cover_art_size`)
 * - Title: `TextAppearance.Encore.BodyMedium`, colored green when playing
 * - Subtitle: `TextAppearance.Encore.BodySmall` with explicit badge + "Song • <artist>"
 * - Animated play indicator if track is currently playing
 * - Context action: 3-dots more button (`encore_icon_more_android_24`)
 *
 * Search results display cover artwork, NOT album track numbers (1, 2, 3...)!
 */
@Composable
private fun SearchTrackRow(
    track: TrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLikeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork thumbnail (48dp x 48dp, 4dp corner radius)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(EncoreColors.Card),
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
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_track_24),
                    contentDescription = null,
                    tint = EncoreColors.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle Column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    EncorePlayIndicator(
                        isPlaying = true,
                        color = EncoreColors.Green,
                        size = 12.dp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(
                    text = track.title,
                    style = SpotifyTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPlaying) EncoreColors.Green else EncoreColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(EncoreColors.TextSecondary)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "E",
                            style = SpotifyTheme.typography.tagLabel.copy(
                                color = EncoreColors.PureBlack,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Text(
                    text = "Song • ${track.artist}",
                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Like button
        EncoreIconButton(
            iconRes = if (track.saved == true) R.drawable.encore_icon_heart_active_24
            else R.drawable.encore_icon_heart_24,
            contentDescription = if (track.saved == true) "Remove ${track.title} from Liked Songs"
            else "Save ${track.title} to Liked Songs",
            onClick = onLikeClick,
            size = 36.dp,
            iconSize = 20.dp,
            tint = if (track.saved == true) EncoreColors.Green else EncoreColors.TextSecondary
        )

        // More options button (encore_icon_more_android_24)
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_more_android_24,
            contentDescription = "More options for ${track.title}",
            onClick = onMoreClick,
            size = 36.dp,
            iconSize = 20.dp
        )
    }
}

@Composable
private fun ArtistRow(
    artist: SearchArtistItem,
    onArtistClick: () -> Unit
) {
    var isFollowing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onArtistClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(artist.color),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "★", color = Color.White, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Artist",
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isFollowing) EncoreColors.Green else EncoreColors.Card)
                .clickable { isFollowing = !isFollowing }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                style = SpotifyTheme.typography.bodySmallBold.copy(
                    color = if (isFollowing) Color.Black else EncoreColors.TextPrimary
                )
            )
        }
    }
}

@Composable
private fun AlbumRow(
    album: SearchAlbumItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(album.color),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "♫", color = Color.White, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Album • ${album.year} • ${album.artist}",
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: SearchPlaylistItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(playlist.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_playlist_24),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Playlist • By ${playlist.creator}",
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun SearchKind.sectionLabel(): String = when (this) {
    SearchKind.TRACK -> "Song"
    SearchKind.ARTIST -> "Artist"
    SearchKind.ALBUM -> "Album"
    SearchKind.PLAYLIST -> "Playlist"
    SearchKind.SHOW, SearchKind.EPISODE -> "Podcast"
    else -> "Result"
}

private fun SearchHit.toTrackItem(): TrackItem = TrackItem(
    title = title,
    artist = subtitle,
    isExplicit = explicit,
    uri = uri,
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    saved = saved,
    durationMs = durationMs,
    hasVideo = video
)

@Composable
private fun PodcastRow(
    podcast: SearchPodcastItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(podcast.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Podcast • ${podcast.publisher}",
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifySearchScreen() {
    SpotifyEncoreTheme {
        SpotifySearchScreen(
            onCategoryClick = {},
            onTrackSelect = {},
            onTrackOptionsClick = {},
            onProfileClick = {}
        )
    }
}
