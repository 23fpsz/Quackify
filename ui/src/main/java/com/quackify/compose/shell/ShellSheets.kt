package com.quackify.compose.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.quackify.backend.model.ConnectDevice
import com.quackify.compose.playlist.TrackItem
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.compose.sheets.AccountsSheet
import com.quackify.compose.sheets.AlbumOptionsSheet
import com.quackify.compose.sheets.ArtistOptionsSheet
import com.quackify.compose.sheets.EntityOptions
import com.quackify.compose.sheets.EntityOptionsSheet
import com.quackify.compose.sheets.LibraryCreateSheet
import com.quackify.compose.sheets.LibrarySortOption
import com.quackify.compose.sheets.LibrarySortSheet
import com.quackify.compose.sheets.ProfileDrawerSheet
import com.quackify.compose.sheets.SleepTimerOption
import com.quackify.compose.sheets.SleepTimerSheet
import com.quackify.compose.sheets.SpotifyConnectDeviceSheet
import com.quackify.compose.sheets.SpotifyQueueSheet
import com.quackify.compose.sheets.TrackOptionsSheet
import com.quackify.compose.sheets.toSpotifyDevice

internal data class SheetFlags(
    val profileDrawer: Boolean = false,
    val accounts: Boolean = false,
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
    userQueue: List<TrackItem>,
    queueFromContext: List<TrackItem>,
    /** null while the taste-profile contains check is loading. */
    trackTasteExcluded: Boolean? = null,
    onToggleTasteExclusion: (Boolean) -> Unit = {},
    queueSourceContext: String,
    currentTrackTitle: String,
    currentTrackArtist: String,
    currentTrackUri: String,
    currentTrackCoverUrl: String?,
    playlistEntity: EntityOptions?,
    playlistEditable: Boolean,
    playlistSaved: Boolean?,
    playlistPublished: Boolean?,
    albumTitle: String,
    albumArtist: String,
    albumCoverUrl: String?,
    albumUri: String = "",
    artistName: String,
    artistAvatarUrl: String?,
    artistUri: String = "",
    artistFollowing: Boolean = false,
    onArtistFollowToggle: () -> Unit = {},
    onArtistRadio: () -> Unit = {},
    sortOption: LibrarySortOption,
    sleepTimer: SleepTimerOption?,
    userDisplayName: String,
    userAvatarUrl: String,
    savedAccounts: List<SpotifyAuth.Account>,
    activeAccountId: String,
    isFolder: Boolean = false,
    canvasAvailable: Boolean = false,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: () -> Unit = {},
    onDismissProfile: () -> Unit,
    onDismissAccounts: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onDismissTrackOptions: () -> Unit,
    onDismissPlaylistOptions: () -> Unit,
    onDismissAlbumOptions: () -> Unit,
    onDismissArtistOptions: () -> Unit,
    onDismissSort: () -> Unit,
    onDismissCreate: () -> Unit,
    onDismissDevice: () -> Unit,
    onDismissQueue: () -> Unit,
    onDismissSleepTimer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowCredits: () -> Unit,
    onEditPlaylist: () -> Unit,
    onTogglePlaylistSaved: () -> Unit,
    onTogglePlaylistPublished: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onWhatsNew: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onOpenAccounts: () -> Unit,
    onLikeToggle: (Boolean) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onViewArtist: (String, String) -> Unit,
    onViewAlbum: (String, String) -> Unit,
    onSleepTimerClick: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onSortSelected: (LibrarySortOption) -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateBlend: () -> Unit,
    onCreateFolder: () -> Unit = {},
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
        onAccountsClick = onOpenAccounts,
        onWhatsNewClick = onWhatsNew,
        onListeningHistoryClick = onHistory,
        onSettingsClick = onSettings,
        onLogoutClick = onLogout,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl
    )

    AccountsSheet(
        isOpen = flags.accounts,
        onDismiss = onDismissAccounts,
        accounts = savedAccounts,
        activeAccountId = activeAccountId,
        onSwitchAccount = onSwitchAccount,
        onAddAccount = onAddAccount,
        onRemoveAccount = onRemoveAccount
    )

    TrackOptionsSheet(
        track = selectedTrack,
        isOpen = flags.trackOptions,
        onDismiss = onDismissTrackOptions,
        onLikeToggle = onLikeToggle,
        onAddToPlaylist = onAddToPlaylist,
        onAddToQueue = { selectedTrack?.let(onAddToQueue) },
        onViewArtist = onViewArtist,
        onViewAlbum = onViewAlbum,
        onShare = { selectedTrack?.uri?.takeIf { it.isNotBlank() }?.let(onShare) },
        onSleepTimerClick = onSleepTimerClick,
        onShowCredits = onShowCredits,
        canvasAvailable = canvasAvailable,
        isCanvasEnabled = isCanvasEnabled,
        onCanvasToggle = onCanvasToggle,
        isTasteExcluded = trackTasteExcluded,
        onToggleTasteExclusion = onToggleTasteExclusion
    )

    EntityOptionsSheet(
        entity = playlistEntity,
        isOpen = flags.playlistOptions,
        onDismiss = onDismissPlaylistOptions,
        canEditPlaylist = playlistEditable,
        onEditPlaylist = onEditPlaylist,
        isSaved = playlistSaved,
        onSaveToggle = onTogglePlaylistSaved,
        canDeletePlaylist = playlistEditable,
        onDeletePlaylist = onDeletePlaylist,
        isPublished = playlistPublished,
        canPublish = playlistEditable,
        onPublishToggle = onTogglePlaylistPublished,
        onCopyLink = { playlistEntity?.uri?.let(onCopyLink) },
        onShare = { playlistEntity?.uri?.let(onShare) },
        onOpenInBrowser = { playlistEntity?.uri?.let(onOpenInBrowser) }
    )

    AlbumOptionsSheet(
        title = albumTitle,
        artist = albumArtist,
        coverUrl = albumCoverUrl,
        isOpen = flags.albumOptions,
        onDismiss = onDismissAlbumOptions,
        onGoToArtist = onGoToArtist,
        onShare = { albumUri.takeIf { it.startsWith("spotify:album:") }?.let(onShare) }
    )

    ArtistOptionsSheet(
        artistName = artistName,
        avatarUrl = artistAvatarUrl,
        isOpen = flags.artistOptions,
        onDismiss = onDismissArtistOptions,
        isFollowing = artistFollowing,
        onFollowToggle = onArtistFollowToggle,
        onGoToRadio = onArtistRadio,
        onShare = { artistUri.takeIf { it.startsWith("spotify:artist:") }?.let(onShare) }
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
        onCreateFolder = onCreateFolder,
        showBlend = !isFolder,
        showHeader = !isFolder,
        onDismiss = onDismissCreate
    )

    if (flags.device) {
        // The bridge only refreshes the cluster the moment the sheet opens
        // (and polls fast while it is open) when it hears about it.
        LaunchedEffect(Unit) { onDeviceVisible(true) }
        val mapped = devices.map { it.toSpotifyDevice() }
        val current = mapped.firstOrNull { it.isCurrent }
        SpotifyConnectDeviceSheet(
            onDismissRequest = {
                onDismissDevice()
                onDeviceVisible(false)
            },
            currentDeviceName = connectedDevice,
            devices = mapped,
            initialVolume = current?.volumeFraction?.takeIf { it >= 0f } ?: 0.7f,
            onVolumeChange = onVolumeChange,
            onSelectDevice = { device -> onSelectDevice(device.id) }
        )
    }

    if (flags.queue) {
        val queued = userQueue.filter { it.uri.isNotBlank() && it.title.isNotBlank() }
        val upcoming = queueFromContext
            .filter { it.uri.isNotBlank() }
            .dropWhile { it.uri == currentTrackUri }
            .filter { it.title.isNotBlank() }
        SpotifyQueueSheet(
            currentTrackTitle = currentTrackTitle,
            currentTrackArtist = currentTrackArtist,
            currentTrackCoverUrl = currentTrackCoverUrl,
            queueSourceContext = queueSourceContext,
            userQueue = queued,
            nextFromContext = upcoming,
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
