package com.spotiduck.compose.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.spotiduck.compose.bridge.SpotifyPlayerBridge
import com.spotiduck.compose.engine.PlaybackTrack
import com.spotiduck.compose.engine.SpotifyNativePlayerManager
import com.spotiduck.compose.home.QuickAccessItem
import com.spotiduck.compose.home.RecommendationCard
import com.spotiduck.compose.library.LibraryItem
import com.spotiduck.compose.library.LibraryItemType
import com.spotiduck.compose.shell.SpotifyAppShell
import com.spotiduck.compose.theme.SpotifyEncoreTheme

class MainActivity : ComponentActivity() {

    private lateinit var playerManager: SpotifyNativePlayerManager
    private lateinit var playerBridge: SpotifyPlayerBridge

    private val presetTracks = listOf(
        PlaybackTrack(
            uri = "spotify:track:7eQl3Yqv35ioqUfveKHitE",
            fileId = "484dba2c06bd9875346a29ddf5993d817cd3209f",
            title = "Tum Se Hi",
            artist = "Pritam, Mohit Chauhan, Irshad Kamil",
            albumArtUrl = "https://i.scdn.co/image/ab67616d0000b27352fe6875028c892308ffc2f7",
            durationMs = 321225L
        ),
        PlaybackTrack(
            uri = "spotify:track:1Y3LN4zO1Edc2EluIoSPJN",
            fileId = "26bbc85956463c7d4d85ed758aad6957e3db7a3c",
            title = "Until I Found You (Em Beihold Version)",
            artist = "Stephen Sanchez, Em Beihold",
            albumArtUrl = "https://i.scdn.co/image/ab67616d0000b2732bf0876d42b90a8852ad6244",
            durationMs = 176440L
        ),
        PlaybackTrack(
            uri = "spotify:track:5Z2GQ0eBKoatNYqbGayihP",
            fileId = "013433e03758ed4010a880e38a1ca6398c18b9e2",
            title = "Lamhey",
            artist = "Anubha Bajaj",
            albumArtUrl = "https://i.scdn.co/image/ab67616d0000b273d4115528421f86756c827429",
            durationMs = 148966L
        )
    )

    private val sampleQuickAccess = listOf(
        QuickAccessItem("Liked Songs", Color(0xFF450AF5)),
        QuickAccessItem("Jab We Met", Color(0xFFE8115B), imageUrl = "https://i.scdn.co/image/ab67616d0000b27352fe6875028c892308ffc2f7"),
        QuickAccessItem("After Hours", Color(0xFF901A1E), imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
        QuickAccessItem("Starboy", Color(0xFF1E3A8A), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
        QuickAccessItem("Until I Found You", Color(0xFF2E77D0), imageUrl = "https://i.scdn.co/image/ab67616d0000b2732bf0876d42b90a8852ad6244"),
        QuickAccessItem("Lamhey", Color(0xFF107C41), imageUrl = "https://i.scdn.co/image/ab67616d0000b273d4115528421f86756c827429")
    )

    private val sampleRecommendations = listOf(
        RecommendationCard("This Is The Weeknd", "The essential tracks, all in one place.", Color(0xFF5A1A22), imageUrl = "https://i.scdn.co/image/ab67706f000000021c3fa36c4b92b67f40192e44", tag = "PLAYLIST"),
        RecommendationCard("Hot Hits India", "Catch the hottest tracks from India.", Color(0xFFB91C1C), imageUrl = "https://i.scdn.co/image/ab67706f00000002d29bb596da60de8f596bfa82", tag = "PLAYLIST"),
        RecommendationCard("Chill Hits", "Kick back to the best new and recent chill hits.", Color(0xFF047857), imageUrl = "https://i.scdn.co/image/ab67706f00000002c05001ffae0ee89f5bc3a677", tag = "PLAYLIST"),
        RecommendationCard("After Hours", "The Weeknd • 2020", Color(0xFF901A1E), imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36", tag = "ALBUM"),
        RecommendationCard("Starboy", "The Weeknd • 2016", Color(0xFF1E3A8A), imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452", tag = "ALBUM")
    )

    private val sampleLibraryItems = listOf(
        LibraryItem("Liked Songs", "Playlist • 1,234 songs", LibraryItemType.PLAYLIST, isPinned = true),
        LibraryItem("Jab We Met", "Album • Pritam", LibraryItemType.ALBUM, imageUrl = "https://i.scdn.co/image/ab67616d0000b27352fe6875028c892308ffc2f7"),
        LibraryItem("The Weeknd", "Artist", LibraryItemType.ARTIST, imageUrl = "https://i.scdn.co/image/ab6761610000e5eb968a3770e060cf56306e9f1a"),
        LibraryItem("After Hours", "Album • The Weeknd", LibraryItemType.ALBUM, imageUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
        LibraryItem("Starboy", "Album • The Weeknd", LibraryItemType.ALBUM, imageUrl = "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"),
        LibraryItem("Stephen Sanchez", "Artist", LibraryItemType.ARTIST, imageUrl = "https://i.scdn.co/image/ab6761610000e5ebb08d4b38bfd2c0b71e191cf0")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        playerManager = SpotifyNativePlayerManager(this)

        // Read session cookie from assets
        val spDc = try {
            assets.open("sp_dc.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            ""
        }
        playerManager.setSessionCookie(spDc)

        playerBridge = SpotifyPlayerBridge(playerManager, queue = presetTracks.toMutableList())
        playerBridge.setInitialPresets(
            initialTracks = presetTracks,
            initialQuickAccess = sampleQuickAccess,
            initialRecommendations = sampleRecommendations,
            initialLibrary = sampleLibraryItems
        )

        // Load authentic user library, quick access items, and playlists if session cookie exists
        if (spDc.isNotBlank()) {
            playerBridge.loadUserData(spDc)
        }

        setContent {
            val composeState by playerBridge.composePlaybackState.collectAsState()
            val uiTracks by playerBridge.uiTracks.collectAsState()
            val quickAccess by playerBridge.quickAccessItems.collectAsState()
            val recommendations by playerBridge.recommendations.collectAsState()
            val libraryItems by playerBridge.libraryItems.collectAsState()

            SpotifyEncoreTheme {
                SpotifyAppShell(
                    playbackState = composeState,
                    playerListener = playerBridge,
                    playlistTracks = uiTracks,
                    homeQuickAccessItems = quickAccess,
                    homeRecommendations = recommendations,
                    libraryItems = libraryItems
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}
