package com.quackify.compose.shell

import android.content.Context
import android.widget.Toast
import com.quackify.backend.model.SearchResults
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.screens.getCategoryColor
import com.quackify.compose.R

internal class ShellNavController(
    val nav: ShellNavState,
    val active: ShellActiveEntity,
    private val flags: ShellUiFlags,
    private val searchQuery: () -> String,
    private val onSearchQueryChange: ((String) -> Unit)?,
    private val playerListener: ComposePlayerListener?,
    private val onLoadPlaylist: ((String) -> Unit)?,
    private val onLoadAlbum: ((String, String) -> Unit)?,
    private val onLoadArtist: ((String, String) -> Unit)?,
    private val onOpenShow: ((String) -> Unit)?,
    private val onOpenEpisode: ((String) -> Unit)?,
    private val onOpenUserProfile: ((String, String) -> Unit)?,
    private val libraryItems: List<LibraryItem>?,
    private val searchData: SearchResults?,
    private val nowPlayingArtistImageUrl: String?,
    private val currentArtist: () -> String,
    private val nowPlayingCoverUrl: String?,
    private val nowPlayingAlbumUri: () -> String,
    private val onAddTracksClosed: (() -> Unit)? = null,
    private val onScreenPopped: ((String) -> Unit)? = null,
) {
    var currentScreen
        get() = nav.currentScreen
        set(value) {
            nav.currentScreen = value
        }

    var selectedNavIndex
        get() = nav.selectedNavIndex
        set(value) {
            nav.selectedNavIndex = value
        }

    var isPlayerExpanded
        get() = nav.isPlayerExpanded
        set(value) {
            nav.isPlayerExpanded = value
        }

    fun pushCurrentState() {
        nav.backStack.add(
            BackStackItem(
                key = nav.currentScreenKey,
                screen = nav.currentScreen,
                categoryTitle = active.categoryTitle,
                playlistTitle = active.playlistTitle,
                playlistUri = active.playlistUri,
                playlistCoverUrl = active.playlistCoverUrl,
                albumTitle = active.albumTitle,
                albumArtist = active.albumArtist,
                albumUri = active.albumUri,
                albumCoverUrl = active.albumCoverUrl,
                artistName = active.artistName,
                artistUri = active.artistUri,
                artistAvatarUrl = active.artistAvatarUrl,
                showUri = active.showUri,
                searchQuery = searchQuery()
            )
        )
    }

    fun handleBack() {
        val leavingKey = nav.currentScreenKey
        val leaving = currentScreen
        val stack = nav.backStack
        if (stack.isNotEmpty()) {
            val previous = stack.removeAt(stack.lastIndex)
            if (previous.searchQuery.isNotEmpty()) {
                onSearchQueryChange?.invoke(previous.searchQuery) ?: playerListener?.onSearch(previous.searchQuery)
            }
            if (previous.categoryTitle.isNotEmpty()) {
                active.categoryTitle = previous.categoryTitle
                active.categoryColor = getCategoryColor(previous.categoryTitle)
            }
            if (previous.playlistTitle.isNotEmpty()) {
                active.playlistTitle = previous.playlistTitle
                active.playlistUri = previous.playlistUri
                active.playlistCoverUrl = previous.playlistCoverUrl
                if (previous.screen == ShellScreen.PLAYLIST && previous.playlistUri.isNotEmpty()) {
                    if (onLoadPlaylist != null) onLoadPlaylist.invoke(previous.playlistUri) else playerListener?.onLoadPlaylist(previous.playlistUri)
                }
            }
            if (previous.albumTitle.isNotEmpty()) {
                active.albumTitle = previous.albumTitle
                active.albumArtist = previous.albumArtist
                active.albumUri = previous.albumUri
                active.albumCoverUrl = previous.albumCoverUrl
                if (previous.screen == ShellScreen.ALBUM && previous.albumUri.isNotEmpty()) {
                    if (onLoadAlbum != null) onLoadAlbum.invoke(previous.albumUri, previous.albumTitle) else playerListener?.onLoadAlbum(previous.albumUri, previous.albumTitle, previous.albumArtist)
                }
            }
            if (previous.artistName.isNotEmpty()) {
                active.artistName = previous.artistName
                active.artistUri = previous.artistUri
                if (previous.artistAvatarUrl != null) active.artistAvatarUrl = previous.artistAvatarUrl
                if (previous.screen == ShellScreen.ARTIST && previous.artistUri.isNotEmpty()) {
                    if (onLoadArtist != null) onLoadArtist.invoke(previous.artistUri, previous.artistName) else playerListener?.onLoadArtist(previous.artistUri, previous.artistName)
                }
            }
            if (previous.showUri.isNotEmpty()) {
                active.showUri = previous.showUri
                if (previous.screen == ShellScreen.SHOW) {
                    onOpenShow?.invoke(previous.showUri)
                }
            }
            when (previous.screen) {
                ShellScreen.HOME -> selectedNavIndex = 0
                ShellScreen.SEARCH, ShellScreen.CATEGORY -> selectedNavIndex = 1
                ShellScreen.LIBRARY -> selectedNavIndex = 2
                else -> {}
            }
            if (leaving == ShellScreen.ADD_TRACKS) {
                onAddTracksClosed?.invoke()
            }
            nav.restoreScreen(previous.screen, previous.key)
            if (!leavingKey.startsWith("root_")) {
                onScreenPopped?.invoke(leavingKey)
            }
        } else if (currentScreen != ShellScreen.HOME) {
            currentScreen = ShellScreen.HOME
            selectedNavIndex = 0
        }
    }

    fun openSettings(context: Context, onOpenQuackifySettings: (() -> Unit)?) {
        if (onOpenQuackifySettings != null) {
            onOpenQuackifySettings()
        } else {
            pushCurrentState()
            nav.openScreen(ShellScreen.SETTINGS, "settings")
        }
    }

    fun openAlbum(context: Context, title: String, artist: String, uri: String) {
        val foundEntity = libraryItems?.find { it.title.equals(title, ignoreCase = true) }
        val albumUriToLoad = uri.takeIf { it.startsWith("spotify:album:") }
            ?: foundEntity?.uri?.takeIf { it.startsWith("spotify:album:") }
            ?: searchData?.albums?.find { it.title.equals(title, ignoreCase = true) }
                ?.uri?.takeIf { it.startsWith("spotify:album:") }
        if (albumUriToLoad == null) {
            Toast.makeText(context, context.getString(R.string.couldnt_open, title), Toast.LENGTH_SHORT).show()
            return
        }

        active.albumTitle = title
        active.albumArtist = artist
        active.albumUri = albumUriToLoad
        active.albumCoverUrl = foundEntity?.imageUrl
            ?: nowPlayingCoverUrl?.takeIf { albumUriToLoad == nowPlayingAlbumUri() }

        if (onLoadAlbum != null) onLoadAlbum.invoke(albumUriToLoad, title) else playerListener?.onLoadAlbum(albumUriToLoad, title, active.albumArtist)

        pushCurrentState()
        nav.openScreen(ShellScreen.ALBUM, "album_$albumUriToLoad")
    }

    fun openShow(uri: String) {
        if (uri.isBlank()) return
        pushCurrentState()
        active.showUri = uri
        nav.openScreen(ShellScreen.SHOW, "show_$uri")
        onOpenShow?.invoke(uri)
    }

    fun openPlaylist(context: Context, title: String, uri: String, coverUrl: String? = null) {
        if (uri.isBlank()) {
            Toast.makeText(context, context.getString(R.string.couldnt_open, title), Toast.LENGTH_SHORT).show()
            return
        }
        pushCurrentState()
        active.playlistTitle = title
        active.playlistUri = uri
        active.playlistCoverUrl = coverUrl
        nav.openScreen(ShellScreen.PLAYLIST, "playlist_$uri")
        if (onLoadPlaylist != null) onLoadPlaylist.invoke(uri) else playerListener?.onLoadPlaylist(uri)
    }

    fun openEpisode(uri: String) {
        if (uri.isBlank()) return
        pushCurrentState()
        active.episodeUri = uri
        nav.openScreen(ShellScreen.EPISODE, "episode_$uri")
        onOpenEpisode?.invoke(uri)
    }

    fun openArtist(context: Context, artist: String, uri: String) {
        val artistUriToLoad = uri.takeIf { it.startsWith("spotify:artist:") }
            ?: libraryItems?.find { it.title.equals(artist, ignoreCase = true) }
                ?.uri?.takeIf { it.startsWith("spotify:artist:") }
            ?: searchData?.artists?.find { it.title.equals(artist, ignoreCase = true) }
                ?.uri?.takeIf { it.startsWith("spotify:artist:") }
        if (artistUriToLoad == null) {
            Toast.makeText(context, context.getString(R.string.couldnt_open, artist), Toast.LENGTH_SHORT).show()
            return
        }

        active.artistName = artist
        active.artistUri = artistUriToLoad
        active.artistAvatarUrl = libraryItems?.find { it.title.equals(artist, ignoreCase = true) }?.imageUrl
            ?: searchData?.artists?.find { it.title.equals(artist, ignoreCase = true) }?.imageUrl
                ?.takeIf { it.isNotBlank() }
            ?: nowPlayingArtistImageUrl?.takeIf { artist.equals(currentArtist(), ignoreCase = true) }

        if (onLoadArtist != null) onLoadArtist.invoke(artistUriToLoad, artist) else playerListener?.onLoadArtist(artistUriToLoad, artist)

        pushCurrentState()
        nav.openScreen(ShellScreen.ARTIST, "artist_$artistUriToLoad")
    }

    fun openFolder(item: LibraryItem, onOpenFolder: ((String) -> Unit)?) {
        pushCurrentState()
        active.folderUri = item.uri
        active.folderTitle = item.title
        nav.openScreen(ShellScreen.FOLDER, "folder_${item.uri}")
        onOpenFolder?.invoke(item.uri)
    }

    fun openAddTracks() {
        pushCurrentState()
        nav.openScreen(ShellScreen.ADD_TRACKS, "add_tracks")
    }

    fun openCategory(title: String, color: androidx.compose.ui.graphics.Color) {
        pushCurrentState()
        active.categoryTitle = title
        active.categoryColor = color
        nav.openScreen(ShellScreen.CATEGORY, "category_$title")
    }

    fun openNotifications() {
        pushCurrentState()
        nav.openScreen(ShellScreen.NOTIFICATIONS, "notifications")
    }

    fun openActivity() {
        pushCurrentState()
        nav.openScreen(ShellScreen.ACTIVITY, "activity")
    }

    fun openProfile() {
        pushCurrentState()
        nav.openScreen(ShellScreen.PROFILE, "profile")
    }

    fun openUserProfile(context: Context, uri: String, name: String) {
        if (!uri.startsWith("spotify:user:")) {
            Toast.makeText(context, context.getString(R.string.couldnt_open, name), Toast.LENGTH_SHORT).show()
            return
        }
        pushCurrentState()
        nav.openScreen(ShellScreen.PROFILE, "user_$uri")
        onOpenUserProfile?.invoke(uri, name)
    }

    fun openHistory(onOpenHistory: (() -> Unit)?) {
        pushCurrentState()
        nav.openScreen(ShellScreen.HISTORY, "history")
        onOpenHistory?.invoke()
    }

    fun openProfileDrawer() {
        flags.isProfileDrawerOpen = true
    }
}
