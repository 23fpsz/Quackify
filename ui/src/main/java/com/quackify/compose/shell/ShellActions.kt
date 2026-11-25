package com.quackify.compose.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.quackify.backend.api.Search
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.sheets.EntityOptions
import androidx.compose.ui.res.stringResource
import com.quackify.compose.R

internal class ShellActions(
    private val flags: ShellUiFlags,
    private val local: ShellLocalState,
    private val navController: ShellNavController,
    private val playerListener: ComposePlayerListener?,
    private val playlistTracks: List<TrackItem>?,
    private val albumTracks: List<TrackItem>?,
    private val artistTracks: List<com.quackify.compose.screens.PopularTrack>?,
    private val searchQuery: () -> String,
    private val clipboard: ClipboardManager,
    /** True while the network watchdog says there is no internet; actions that
     * would need to dial out are refused before they start. */
    private val offlineGate: () -> Boolean = { false },
    /** Fired whenever one of those actions is refused, so the shell can toast. */
    private val onBlocked: () -> Unit = {},
) {
    private val active get() = navController.active

    /** Runs [action] unless we are offline; refuses with [onBlocked] then. */
    private inline fun whenOnline(action: () -> Unit) {
        if (offlineGate()) {
            onBlocked()
            return
        }
        action()
    }

    /** For panes and sheets that build their own play lambdas: returns true —
     * firing the offline toast — when the action must be refused because the
     * network watchdog says there is no internet. */
    fun blockedOffline(): Boolean {
        if (!offlineGate()) return false
        onBlocked()
        return true
    }

    fun onTrackPlay(track: TrackItem) {
        whenOnline { onTrackPlayInternal(track) }
    }

    private fun onTrackPlayInternal(track: TrackItem) {
        local.playbackOrigin = when (navController.currentScreen) {
            ShellScreen.SEARCH -> PlaybackOrigin(ShellScreen.SEARCH, searchQuery().trim())
            ShellScreen.PLAYLIST -> PlaybackOrigin(ShellScreen.PLAYLIST, active.playlistTitle)
            ShellScreen.ALBUM -> PlaybackOrigin(ShellScreen.ALBUM, active.albumTitle)
            ShellScreen.ARTIST -> PlaybackOrigin(ShellScreen.ARTIST, active.artistName)
            else -> null
        }
        if (playerListener != null) {
            if (track.uri.isNotEmpty()) {
                val rows = when (navController.currentScreen) {
                    ShellScreen.ARTIST -> artistTracks.orEmpty().map { it.track.uri }
                    ShellScreen.PLAYLIST -> playlistTracks.orEmpty().map { it.uri }
                    ShellScreen.ALBUM -> albumTracks.orEmpty().map { it.uri }
                    else -> emptyList()
                }
                val contextUri = when (navController.currentScreen) {
                    ShellScreen.ARTIST -> active.artistUri
                    ShellScreen.PLAYLIST -> active.playlistUri
                    ShellScreen.ALBUM -> active.albumUri
                    ShellScreen.SEARCH -> Search.searchUriOf(searchQuery().trim())
                    else -> ""
                }
                val index = rows.indexOfFirst { it == track.uri }
                if (contextUri.isNotBlank() && index >= 0) {
                    playerListener.onPlayTrack(track.uri, contextUri, index)
                } else if (contextUri.startsWith("spotify:search:")) {
                    playerListener.onPlayTrack(track.uri, contextUri, -1)
                } else {
                    playerListener.onPlayUri(track.uri)
                }
            } else {
                playerListener.onTrackSelect(track.title, track.artist)
            }
        } else {
            localPlay(track)
        }
    }

    fun localPlay(track: TrackItem) {
        local.trackTitle = track.title
        local.artist = track.artist
        local.isPlaying = true
        local.progress = 0.05f
        local.positionMs = 12000L
    }

    fun playUriShortcut(uri: String, title: String) {
        whenOnline {
            local.playbackOrigin = PlaybackOrigin(ShellScreen.HOME, "Recently played")
            if (playerListener != null) {
                playerListener.onPlayUri(uri)
            } else {
                local.trackTitle = title
                local.isPlaying = true
                local.progress = 0.05f
            }
        }
    }

    fun playPause() {
        if (playerListener != null) playerListener.onPlayPause() else local.isPlaying = !local.isPlaying
    }

    fun skipNext() {
        whenOnline { playerListener?.onSkipNext() }
    }

    fun skipPrevious() {
        whenOnline { playerListener?.onSkipPrevious() }
    }

    fun skipToPreviousTrack() {
        whenOnline { playerListener?.onSkipToPreviousTrack() }
    }

    fun previousSwipe() {
        playerListener?.onPreviousSwipe()
    }

    fun openTrackOptions(track: TrackItem) {
        local.trackForOptions = track
        flags.isTrackOptionsFromPlayer = false
        flags.isTrackOptionsOpen = true
    }

    fun libraryLongPress(item: LibraryItem) {
        if (item.type == LibraryItemType.FOLDER) {
            local.activeFolderOptionsNested = navController.currentScreen == ShellScreen.FOLDER
            local.activeFolderOptionsItem = item
        } else {
            local.activeEntityInsideFolder = navController.currentScreen == ShellScreen.FOLDER
            local.libraryItemOptions = EntityOptions(
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                uri = item.uri,
                pinned = item.isPinned,
                pinnable = item.isPinnable,
            )
        }
    }

    fun copyEntityLink(uri: String) {
        val url = entityUrl(uri) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Track link", url))
    }

    fun shareEntity(context: Context, uri: String) {
        val url = entityUrl(uri) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
