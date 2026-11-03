package com.spotiduck.compose.shell

import androidx.compose.runtime.Composable
import com.spotiduck.backend.model.ConnectDevice
import com.spotiduck.compose.playlist.TrackItem
import com.spotiduck.compose.sheets.AlbumOptionsSheet
import com.spotiduck.compose.sheets.ArtistOptionsSheet
import com.spotiduck.compose.sheets.LibraryCreateSheet
import com.spotiduck.compose.sheets.LibrarySortOption
import com.spotiduck.compose.sheets.LibrarySortSheet
import com.spotiduck.compose.sheets.PlaylistOptionsSheet
import com.spotiduck.compose.sheets.ProfileDrawerSheet
import com.spotiduck.compose.sheets.SleepTimerOption
import com.spotiduck.compose.sheets.SleepTimerSheet
import com.spotiduck.compose.sheets.SpotifyConnectDeviceSheet
import com.spotiduck.compose.sheets.SpotifyQueueSheet
import com.spotiduck.compose.sheets.TrackOptionsSheet
import com.spotiduck.compose.sheets.toSpotifyDevice

internal data class SheetFlags(
    val profileDrawer: Boolean = false,
    val trackOptions: Boolean = false,
    val playlistOptions: Boolean = false,
    val albumOptions: Boolean = false,
    val artistOptions: Boolean = false,
    val sort: Boolean = false,
    val create: Boolean = false,
    val device: Boolean = false,
    val queue: Boolean = false,
    val sleepTimer: Boolean = false,
)

@Composable
internal fun ShellSheets(
    flags: SheetFlags,
    selectedTrack: TrackItem?,
    devices: List<ConnectDevice>,
    connectedDevice: String,
    queueFromContext: List<TrackItem>,
    currentTrackTitle: String,
    currentTrackArtist: String,
    playlistTitle: String,
    playlistCoverUrl: String?,
    albumTitle: String,
    albumArtist: String,
    albumCoverUrl: String?,
    artistName: String,
    artistAvatarUrl: String?,
    sortOption: LibrarySortOption,
    sleepTimer: SleepTimerOption?,
    userDisplayName: String,
    userAvatarUrl: String,
    onDismissProfile: () -> Unit,
    onDismissTrackOptions: () -> Unit,
    onDismissPlaylistOptions: () -> Unit,
    onDismissAlbumOptions: () -> Unit,
    onDismissArtistOptions: () -> Unit,
    onDismissSort: () -> Unit,
    onDismissCreate: () -> Unit,
    onDismissDevice: () -> Unit,
    onDismissQueue: () -> Unit,
    onDismissSleepTimer: () -> Unit,
    onWhatsNew: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onLikeToggle: (Boolean) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onViewArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit,
    onSleepTimerClick: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onSortSelected: (LibrarySortOption) -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateBlend: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onDeviceVisible: (Boolean) -> Unit,
    onVolumeChange: (String, Int) -> Unit,
    onQueueTrackSelect: (TrackItem) -> Unit,
    onRemoveFromQueue: (TrackItem) -> Unit,
    onClearQueue: () -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
) {
    ProfileDrawerSheet(
        isOpen = flags.profileDrawer,
        onDismiss = onDismissProfile,
        onViewProfileClick = {},
        onWhatsNewClick = onWhatsNew,
        onListeningHistoryClick = onHistory,
        onSettingsClick = onSettings,
        onLogoutClick = onLogout,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl
    )

    TrackOptionsSheet(
        track = selectedTrack,
        isOpen = flags.trackOptions,
        onDismiss = onDismissTrackOptions,
        onLikeToggle = onLikeToggle,
        onAddToPlaylist = {},
        onAddToQueue = { selectedTrack?.let(onAddToQueue) },
        onViewArtist = onViewArtist,
        onViewAlbum = onViewAlbum,
        onShare = {},
        onSleepTimerClick = onSleepTimerClick
    )

    PlaylistOptionsSheet(
        title = playlistTitle,
        creator = "Spotify",
        coverUrl = playlistCoverUrl,
        isOpen = flags.playlistOptions,
        onDismiss = onDismissPlaylistOptions
    )

    AlbumOptionsSheet(
        title = albumTitle,
        artist = albumArtist,
        coverUrl = albumCoverUrl,
        isOpen = flags.albumOptions,
        onDismiss = onDismissAlbumOptions,
        onGoToArtist = onGoToArtist
    )

    ArtistOptionsSheet(
        artistName = artistName,
        avatarUrl = artistAvatarUrl,
        isOpen = flags.artistOptions,
        onDismiss = onDismissArtistOptions
    )

    LibrarySortSheet(
        isOpen = flags.sort,
        currentSort = sortOption,
        onSortSelected = onSortSelected,
        onDismiss = onDismissSort
    )

    LibraryCreateSheet(
        isOpen = flags.create,
        onCreatePlaylist = onCreatePlaylist,
        onCreateBlend = onCreateBlend,
        onDismiss = onDismissCreate
    )

    if (flags.device) {
        SpotifyConnectDeviceSheet(
            onDismissRequest = {
                onDismissDevice()
                onDeviceVisible(false)
            },
            currentDeviceName = connectedDevice,
            devices = devices.map { it.toSpotifyDevice() },
            onVolumeChange = onVolumeChange,
            onSelectDevice = { device -> onSelectDevice(device.id) }
        )
    }

    if (flags.queue) {
        SpotifyQueueSheet(
            currentTrackTitle = currentTrackTitle,
            currentTrackArtist = currentTrackArtist,
            queueSourceContext = playlistTitle,
            userQueue = emptyList(),
            nextFromContext = queueFromContext.filter { it.title != currentTrackTitle },
            onDismissRequest = onDismissQueue,
            onTrackSelect = onQueueTrackSelect,
            onRemoveFromQueue = onRemoveFromQueue,
            onClearQueue = onClearQueue
        )
    }

    if (flags.sleepTimer) {
        SleepTimerSheet(
            activeOption = sleepTimer,
            onOptionSelected = onSleepTimerSelected,
            onDismiss = onDismissSleepTimer
        )
    }
}
