package com.quackify.app.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat.BrowserRoot
import androidx.media.MediaBrowserServiceCompat.Result
import com.quackify.app.R
import com.quackify.backend.api.Browse
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.api.Podcasts
import com.quackify.backend.api.Profile
import com.quackify.backend.api.Search
import com.quackify.backend.core.log.Logs
import com.quackify.backend.library.Krespot
import com.quackify.backend.model.ShelfKind
import com.quackify.compose.settings.PlayerButtonsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Builds and serves the entire Android Auto browse hierarchy:
 * Home, Recently Played, Browse Categories, and Library (Playlists, Albums, Artists, Podcasts).
 */
class AutoMediaTree(private val context: Context) {

    private val log = Logs.of(Logs.Module.SERVICE)

    @Volatile
    var lastBrowsedContext: String = ""
        private set

    fun getRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        val config = PlayerButtonsStore.load(context)
        if (!config.andAutoEnabled) return null
        val extras = Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putBoolean("android.media.browse.AUTO_TABS_OPT_IN_HINT", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    fun loadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
        scope: CoroutineScope
    ) {
        if (parentId == ROOT_ID) {
            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            items.add(createTabItem(TAB_HOME, context.getString(R.string.android_auto_navigation_home), R.drawable.ic_eis_home, isGrid = true))
            items.add(createTabItem(TAB_RECENTS, context.getString(R.string.android_auto_navigation_recently_played), R.drawable.ic_eis_recently_played, isGrid = false))
            items.add(createTabItem(TAB_BROWSE, context.getString(R.string.android_auto_navigation_browse), R.drawable.ic_eis_browse, isGrid = true))
            items.add(createTabItem(TAB_LIBRARY, context.getString(R.string.android_auto_navigation_your_library), R.drawable.ic_eis_your_library, isGrid = false))
            result.sendResult(items)
            return
        }

        result.detach()
        scope.launch {
            try {
                val items = mutableListOf<MediaBrowserCompat.MediaItem>()
                when (parentId) {
                    TAB_HOME -> {
                        val seen = mutableSetOf<String>()
                        val cached = HomeFeed.cachedPayload
                        val feed = cached ?: runCatching { HomeFeed.load(context) }.getOrNull()
                        feed?.sections?.forEach { section ->
                            if (section.kind !in listOf("HomeShortsSectionData", "HomeWatchFeedSectionData")) {
                                section.items.forEach { item ->
                                    if (seen.add(item.uri)) {
                                        val isTrack = item.uri.startsWith("spotify:track:") || item.uri.startsWith("spotify:episode:")
                                        if (isTrack) {
                                            items.add(
                                                createPlayableItem(
                                                    id = item.uri,
                                                    title = item.title,
                                                    subtitle = item.subtitle,
                                                    iconUri = item.imageUrl.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        } else {
                                            items.add(
                                                createBrowsableOrPlayableItem(
                                                    id = item.uri,
                                                    title = item.title,
                                                    subtitle = item.subtitle,
                                                    iconUri = item.imageUrl.takeIf { it.isNotBlank() },
                                                    groupTitle = section.title.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (items.isEmpty()) {
                            val page = runCatching { Krespot.library.page(context) }.getOrNull()
                            if (seen.add("spotify:collection:tracks")) {
                                items.add(
                                    createBrowsableOrPlayableItem(
                                        id = "spotify:collection:tracks",
                                        title = context.getString(R.string.collection_liked_songs_title),
                                        subtitle = "Playlist",
                                        iconUri = "https://misc.scdn.co/liked-songs/liked-songs-640.png"
                                    )
                                )
                            }
                            page?.items?.take(15)?.forEach { shelf ->
                                if (seen.add(shelf.uri)) {
                                    items.add(
                                        createBrowsableOrPlayableItem(
                                            id = shelf.uri,
                                            title = shelf.title,
                                            subtitle = shelf.subtitle.ifBlank { "Library" },
                                            iconUri = shelf.imageUrl.takeIf { it.isNotBlank() }
                                        )
                                    )
                                }
                            }
                        }
                    }
                    TAB_RECENTS -> {
                        val seen = mutableSetOf<String>()

                        // 1. Spotify's server-side recently played contexts (playlists, albums, artists, shows)
                        val recentContexts = runCatching {
                            withTimeoutOrNull(2500) {
                                Profile.recentlyPlayed(context)
                            }
                        }.getOrNull()
                        recentContexts?.forEach { entry ->
                            if (seen.add(entry.uri)) {
                                when {
                                    entry.uri.startsWith("spotify:playlist:") -> {
                                        val col = runCatching { Krespot.collections.collection(context, entry.uri) }.getOrNull()
                                        if (col != null) {
                                            items.add(
                                                createBrowsableOrPlayableItem(
                                                    id = col.uri,
                                                    title = col.title,
                                                    subtitle = "Playlist",
                                                    iconUri = col.imageUrl.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                    entry.uri.startsWith("spotify:album:") -> {
                                        val alb = runCatching { Krespot.albums.album(context, entry.uri) }.getOrNull()
                                        if (alb != null) {
                                            items.add(
                                                createBrowsableOrPlayableItem(
                                                    id = alb.uri,
                                                    title = alb.title,
                                                    subtitle = if (alb.artists.isNotBlank()) "Album • by ${alb.artists}" else "Album",
                                                    iconUri = alb.imageUrl.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                    entry.uri.startsWith("spotify:artist:") -> {
                                        val art = runCatching { Krespot.artists.artist(context, entry.uri) }.getOrNull()
                                        if (art != null) {
                                            items.add(
                                                createBrowsableOrPlayableItem(
                                                    id = art.uri,
                                                    title = art.name,
                                                    subtitle = "Artist",
                                                    iconUri = art.imageUrl.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                    entry.uri.startsWith("spotify:show:") -> {
                                        val show = runCatching { Podcasts.show(context, entry.uri) }.getOrNull()
                                        if (show != null) {
                                            items.add(
                                                createBrowsableOrPlayableItem(
                                                    id = show.uri,
                                                    title = show.name,
                                                    subtitle = if (show.publisher.isNotBlank()) "Podcast • by ${show.publisher}" else "Podcast",
                                                    iconUri = show.imageUrl.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                    entry.uri == "spotify:collection:tracks" -> {
                                        items.add(
                                            createBrowsableOrPlayableItem(
                                                id = "spotify:collection:tracks",
                                                title = context.getString(R.string.collection_liked_songs_title),
                                                subtitle = "Playlist",
                                                iconUri = "https://misc.scdn.co/liked-songs/liked-songs-640.png"
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Home feed recent & quick-access items (HomeShortsSectionData & HomeRecentlyPlayedSectionData)
                        val feed = HomeFeed.cachedPayload ?: runCatching {
                            withTimeoutOrNull(2000) { HomeFeed.load(context) }
                        }.getOrNull()
                        feed?.sections?.forEach { section ->
                            val isRecentOrShorts = section.kind == HomeFeed.RECENT ||
                                    section.kind == "HomeShortsSectionData" ||
                                    section.title.contains("Recent", ignoreCase = true) ||
                                    section.title.contains("Jump back in", ignoreCase = true)
                            if (isRecentOrShorts) {
                                section.items.forEach { item ->
                                    if (seen.add(item.uri)) {
                                        val isTrack = item.uri.startsWith("spotify:track:") || item.uri.startsWith("spotify:episode:")
                                        if (isTrack) {
                                            items.add(
                                                createPlayableItem(
                                                    id = item.uri,
                                                    title = item.title,
                                                    subtitle = item.subtitle.ifBlank { "Recently Played" },
                                                    iconUri = item.imageUrl.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        } else {
                                            items.add(
                                                createBrowsableOrPlayableItem(
                                                    id = item.uri,
                                                    title = item.title,
                                                    subtitle = item.subtitle.ifBlank { "Recently Played" },
                                                    iconUri = item.imageUrl.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Fallback to library only if still empty
                        if (items.isEmpty()) {
                            val page = runCatching { Krespot.library.page(context) }.getOrNull()
                            page?.items?.take(12)?.forEach { shelf ->
                                if (seen.add(shelf.uri)) {
                                    items.add(
                                        createBrowsableOrPlayableItem(
                                            id = shelf.uri,
                                            title = shelf.title,
                                            subtitle = shelf.subtitle.ifBlank { "Recently Played" },
                                            iconUri = shelf.imageUrl.takeIf { it.isNotBlank() }
                                        )
                                    )
                                }
                            }
                        }
                    }
                    TAB_BROWSE -> {
                        val cached = Browse.cachedCards
                        val cards = cached ?: runCatching { Browse.all(context) }.getOrNull().orEmpty()
                        cards.forEach { card ->
                            items.add(
                                createBrowsableItem(
                                    id = card.uri,
                                    title = card.title,
                                    subtitle = card.subtitle,
                                    iconUri = card.imageUrl.takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }
                    TAB_LIBRARY -> {
                        items.add(createCategoryListItem(NODE_LIBRARY_PLAYLISTS, context.getString(R.string.your_library_content_filter_playlists), R.drawable.ic_eis_playlists))
                        items.add(createCategoryListItem(NODE_LIBRARY_ALBUMS, context.getString(R.string.your_library_content_filter_albums), R.drawable.ic_eis_albums))
                        items.add(createCategoryListItem(NODE_LIBRARY_ARTISTS, context.getString(R.string.your_library_content_filter_artists), R.drawable.ic_eis_artists))
                        items.add(createCategoryListItem(NODE_LIBRARY_PODCASTS, context.getString(R.string.your_library_content_filter_podcasts), R.drawable.ic_eis_podcasts))
                    }
                    NODE_LIBRARY_PLAYLISTS -> {
                        val seen = mutableSetOf<String>()
                        if (seen.add("spotify:collection:tracks")) {
                            items.add(
                                createBrowsableOrPlayableItem(
                                    id = "spotify:collection:tracks",
                                    title = context.getString(R.string.collection_liked_songs_title),
                                    subtitle = "Playlist",
                                    iconUri = "https://misc.scdn.co/liked-songs/liked-songs-640.png"
                                )
                            )
                        }
                        val page = runCatching { Krespot.library.page(context) }.getOrNull()
                        page?.items?.forEach { shelf ->
                            if ((shelf.kind == ShelfKind.PLAYLIST || shelf.kind == ShelfKind.FOLDER) && seen.add(shelf.uri)) {
                                items.add(
                                    createBrowsableOrPlayableItem(
                                        id = shelf.uri,
                                        title = shelf.title,
                                        subtitle = shelf.subtitle.ifBlank { "Playlist" },
                                        iconUri = shelf.imageUrl.takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                    }
                    NODE_LIBRARY_ALBUMS -> {
                        val page = runCatching { Krespot.library.page(context) }.getOrNull()
                        page?.items?.forEach { shelf ->
                            if (shelf.kind == ShelfKind.ALBUM) {
                                items.add(
                                    createBrowsableOrPlayableItem(
                                        id = shelf.uri,
                                        title = shelf.title,
                                        subtitle = shelf.subtitle.ifBlank { "Album" },
                                        iconUri = shelf.imageUrl.takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                    }
                    NODE_LIBRARY_ARTISTS -> {
                        val page = runCatching { Krespot.library.page(context) }.getOrNull()
                        page?.items?.forEach { shelf ->
                            if (shelf.kind == ShelfKind.ARTIST) {
                                items.add(
                                    createBrowsableItem(
                                        id = shelf.uri,
                                        title = shelf.title,
                                        subtitle = shelf.subtitle.ifBlank { "Artist" },
                                        iconUri = shelf.imageUrl.takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                    }
                    NODE_LIBRARY_PODCASTS -> {
                        val page = runCatching { Krespot.library.page(context) }.getOrNull()
                        page?.items?.forEach { shelf ->
                            if (shelf.kind == ShelfKind.SHOW || shelf.kind == ShelfKind.EPISODE) {
                                items.add(
                                    createBrowsableItem(
                                        id = shelf.uri,
                                        title = shelf.title,
                                        subtitle = shelf.subtitle.ifBlank { "Podcast" },
                                        iconUri = shelf.imageUrl.takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                    }
                    "spotify:collection:tracks" -> {
                        lastBrowsedContext = parentId
                        val detail = runCatching { Krespot.collections.collection(context, "spotify:collection:tracks") }.getOrNull()
                        detail?.tracks?.forEach { track ->
                            items.add(
                                createPlayableItem(
                                    id = track.uri,
                                    title = track.title,
                                    subtitle = if (track.artists.isNotBlank()) "by ${track.artists}" else track.album,
                                    iconUri = track.imageUrl.takeIf { it.isNotBlank() },
                                    contextUri = parentId
                                )
                            )
                        }
                    }
                    else -> {
                        when {
                            parentId.startsWith("spotify:playlist:") -> {
                                lastBrowsedContext = parentId
                                val detail = runCatching { Krespot.collections.collection(context, parentId) }.getOrNull()
                                detail?.tracks?.forEach { track ->
                                    items.add(
                                        createPlayableItem(
                                            id = track.uri,
                                            title = track.title,
                                            subtitle = if (track.artists.isNotBlank()) "by ${track.artists}" else track.album,
                                            iconUri = track.imageUrl.takeIf { it.isNotBlank() },
                                            contextUri = parentId
                                        )
                                    )
                                }
                            }
                            parentId.startsWith("spotify:album:") -> {
                                lastBrowsedContext = parentId
                                val album = runCatching { Krespot.albums.album(context, parentId) }.getOrNull()
                                album?.tracks?.forEach { track ->
                                    items.add(
                                        createPlayableItem(
                                            id = track.uri,
                                            title = track.title,
                                            subtitle = if (track.artists.isNotBlank()) "by ${track.artists}" else album.title,
                                            iconUri = track.imageUrl.takeIf { it.isNotBlank() } ?: album.imageUrl.takeIf { it.isNotBlank() },
                                            contextUri = parentId
                                        )
                                    )
                                }
                            }
                            parentId.startsWith("spotify:artist:") -> {
                                lastBrowsedContext = parentId
                                val artist = runCatching { Krespot.artists.artist(context, parentId) }.getOrNull()
                                artist?.topTracks?.forEach { track ->
                                    items.add(
                                        createPlayableItem(
                                            id = track.uri,
                                            title = track.title,
                                            subtitle = if (track.artists.isNotBlank()) "by ${track.artists}" else artist.name,
                                            iconUri = track.imageUrl.takeIf { it.isNotBlank() } ?: artist.imageUrl.takeIf { it.isNotBlank() },
                                            contextUri = parentId
                                        )
                                    )
                                }
                            }
                            parentId.startsWith("spotify:show:") -> {
                                lastBrowsedContext = parentId
                                val show = runCatching { Podcasts.show(context, parentId) }.getOrNull()
                                show?.episodes?.forEach { episode ->
                                    items.add(
                                        createPlayableItem(
                                            id = episode.uri,
                                            title = episode.title,
                                            subtitle = if (episode.releaseDate.isNotBlank()) "${episode.releaseDate} • ${show.name}" else show.name,
                                            iconUri = episode.imageUrl.takeIf { it.isNotBlank() } ?: show.imageUrl.takeIf { it.isNotBlank() },
                                            contextUri = parentId
                                        )
                                    )
                                }
                            }
                            parentId.startsWith("spotify:folder:") -> {
                                val folder = runCatching { Krespot.library.folder(context, parentId) }.getOrNull()
                                folder?.items?.forEach { shelf ->
                                    items.add(
                                        createBrowsableOrPlayableItem(
                                            id = shelf.uri,
                                            title = shelf.title,
                                            subtitle = shelf.subtitle,
                                            iconUri = shelf.imageUrl.takeIf { it.isNotBlank() }
                                        )
                                    )
                                }
                            }
                            else -> {
                                val browsePage = runCatching { Browse.page(context, parentId) }.getOrNull()
                                if (browsePage != null && !browsePage.isEmpty) {
                                    for (section in browsePage.sections) {
                                        for (card in section.cards) {
                                            val isPlayable = card.uri.startsWith("spotify:track:") || card.uri.startsWith("spotify:episode:")
                                            if (isPlayable) {
                                                items.add(
                                                    createPlayableItem(
                                                        id = card.uri,
                                                        title = card.title,
                                                        subtitle = card.subtitle,
                                                        iconUri = card.imageUrl.takeIf { it.isNotBlank() },
                                                        contextUri = parentId
                                                    )
                                                )
                                            } else {
                                                items.add(
                                                    createBrowsableOrPlayableItem(
                                                        id = card.uri,
                                                        title = card.title,
                                                        subtitle = card.subtitle,
                                                        iconUri = card.imageUrl.takeIf { it.isNotBlank() },
                                                        groupTitle = section.title.takeIf { it.isNotBlank() },
                                                        contextUri = card.uri
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                result.sendResult(items)
            } catch (e: Throwable) {
                log.w("onLoadChildren failed for $parentId", e)
                result.sendResult(emptyList())
            }
        }
    }

    fun search(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>,
        scope: CoroutineScope
    ) {
        if (query.isBlank()) {
            result.sendResult(emptyList())
            return
        }
        result.detach()
        scope.launch {
            try {
                val searchResults = Search.query(context, query)
                val items = mutableListOf<MediaBrowserCompat.MediaItem>()
                val hits = listOfNotNull(searchResults?.top) +
                    searchResults?.tracks.orEmpty() +
                    searchResults?.playlists.orEmpty() +
                    searchResults?.albums.orEmpty()
                for (hit in hits.take(20)) {
                    val isBrowsable = hit.uri.startsWith("spotify:playlist:") ||
                        hit.uri.startsWith("spotify:album:") ||
                        hit.uri.startsWith("spotify:artist:")
                    if (isBrowsable) {
                        items.add(
                            createBrowsableOrPlayableItem(
                                id = hit.uri,
                                title = hit.title,
                                subtitle = hit.subtitle,
                                iconUri = hit.imageUrl.takeIf { it.isNotBlank() }
                            )
                        )
                    } else {
                        items.add(
                            createPlayableItem(
                                id = hit.uri,
                                title = hit.title,
                                subtitle = hit.subtitle,
                                iconUri = hit.imageUrl.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                result.sendResult(items)
            } catch (e: Throwable) {
                log.w("onSearch failed for '$query'", e)
                result.sendResult(emptyList())
            }
        }
    }

    private fun createTabItem(id: String, title: String, iconRes: Int, isGrid: Boolean = true): MediaBrowserCompat.MediaItem {
        val iconUri = Uri.parse("android.resource://${context.packageName}/drawable/${context.resources.getResourceEntryName(iconRes)}")
        val style = if (isGrid) 2 else 1
        val extras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", style)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", style)
        }
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setIconUri(iconUri)
            .setExtras(extras)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createCategoryListItem(id: String, title: String, iconRes: Int): MediaBrowserCompat.MediaItem {
        val iconUri = Uri.parse("android.resource://${context.packageName}/drawable/${context.resources.getResourceEntryName(iconRes)}")
        val extras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            putInt("android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT", 1)
        }
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setIconUri(iconUri)
            .setExtras(extras)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createBrowsableItem(
        id: String,
        title: String,
        subtitle: String = "",
        iconUri: String? = null,
        groupTitle: String? = null
    ): MediaBrowserCompat.MediaItem {
        val extras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT", 2)
            if (!groupTitle.isNullOrBlank()) {
                putString("android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT", groupTitle)
            }
        }
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .apply {
                if (subtitle.isNotBlank()) setSubtitle(subtitle)
                if (!iconUri.isNullOrBlank()) setIconUri(Uri.parse(iconUri))
            }
            .setExtras(extras)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createPlayableItem(
        id: String,
        title: String,
        subtitle: String = "",
        iconUri: String? = null,
        contextUri: String? = null
    ): MediaBrowserCompat.MediaItem {
        val extras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            putInt("android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT", 1)
            if (!contextUri.isNullOrBlank()) {
                putString("com.spotify.music.extra.CONTEXT_URI", contextUri)
            }
        }
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .apply {
                if (subtitle.isNotBlank()) setSubtitle(subtitle)
                if (!iconUri.isNullOrBlank()) setIconUri(Uri.parse(iconUri))
            }
            .setExtras(extras)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun createBrowsableOrPlayableItem(
        id: String,
        title: String,
        subtitle: String = "",
        iconUri: String? = null,
        groupTitle: String? = null,
        contextUri: String? = null
    ): MediaBrowserCompat.MediaItem {
        val extras = Bundle().apply {
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT", 2)
            if (!groupTitle.isNullOrBlank()) {
                putString("android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT", groupTitle)
            }
            putString("com.spotify.music.extra.CONTEXT_URI", contextUri ?: id)
        }
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .apply {
                if (subtitle.isNotBlank()) setSubtitle(subtitle)
                if (!iconUri.isNullOrBlank()) setIconUri(Uri.parse(iconUri))
            }
            .setExtras(extras)
            .build()
        return MediaBrowserCompat.MediaItem(
            desc,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    companion object {
        const val ROOT_ID = "spotify_media_browser_root_android_auto"
        const val TAB_HOME = "com.spotify.androidauto.home"
        const val TAB_RECENTS = "com.spotify.recently-played"
        const val TAB_BROWSE = "com.spotify.browse"
        const val TAB_LIBRARY = "com.spotify.your-library"
        const val NODE_LIBRARY_PLAYLISTS = "com.spotify.your-playlists"
        const val NODE_LIBRARY_ALBUMS = "com.spotify.your-albums"
        const val NODE_LIBRARY_ARTISTS = "com.spotify.your-artists"
        const val NODE_LIBRARY_PODCASTS = "com.spotify.your-podcasts"
    }
}
