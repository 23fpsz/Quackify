package com.quackify.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.quackify.backend.api.ArtistBanStore
import com.quackify.backend.api.Browse
import com.quackify.backend.api.Collection2
import com.quackify.backend.api.Settings
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.library.PlaylistSource
import com.quackify.backend.library.TrackSource
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchResults
import com.quackify.compose.format.formatCount
import com.quackify.compose.home.toTrackItem
import com.quackify.compose.player.LyricSyllableSpan
import com.quackify.compose.player.LyricWordSpan
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.player.TranscriptLine
import com.quackify.compose.player.TranscriptWordSpan
import com.quackify.compose.shell.ActivityFeedState
import com.quackify.compose.screens.CategoryRecommendationItem
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.shell.EncoreAppShell
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.backend.model.SearchKind
import com.quackify.compose.shell.ShellDeepLink
import kotlinx.coroutines.launch
import com.quackify.backend.api.Library
import com.quackify.backend.api.Podcasts
import com.quackify.backend.api.Search
import com.quackify.backend.library.Krespot
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.EpisodeComments
import com.quackify.backend.model.EpisodeTranscript
import com.quackify.backend.model.ShowDetail
import com.quackify.backend.model.EpisodeCommentReplies
import com.quackify.backend.model.EpisodeCommentReactions
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.model.TrackLyrics
import com.quackify.compose.player.LyricsOptions
import com.quackify.compose.home.HomeShelf
import com.quackify.compose.home.QuickAccessItem
import com.quackify.compose.home.toFavoriteArtistsShelf
import com.quackify.compose.home.toQuickAccess
import com.quackify.compose.home.toQuickAccessFallback
import com.quackify.compose.home.toRecentlyPlayedShelf
import com.quackify.compose.home.toShelves
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.HistoryStats
import com.quackify.compose.screens.resetHomeSplash
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.store.StreamSource
import java.util.Calendar

private val log = Logs.of(Logs.Module.MAIN)

internal class ShellHomeData(
    val quick: List<QuickAccessItem>,
    val shelves: List<HomeShelf>,
    val historyShelves: List<HomeShelf>,
    val historyToday: List<TrackItem>,
    val historyYesterday: List<TrackItem>,
    val historyEarlier: List<TrackItem>,
    val historyStats: HistoryStats,
)

internal class ShellNowPlayingData(
    val artistUri: String,
    val canvasAllowed: Boolean,
    val showDetail: ShowDetail?,
    val episodeComments: EpisodeComments?,
)

private class NowPlayingWriters(
    val setArtist: (ArtistDetail?) -> Unit,
    val setArtistFollowed: (Boolean?) -> Unit,
    val setLyrics: (TrackLyrics?) -> Unit,
    val setNextLyrics: (TrackLyrics?) -> Unit,
    val setTranscript: (EpisodeTranscript?) -> Unit,
    val setCanvasFile: (String?) -> Unit,
    val setShowDetail: (ShowDetail?) -> Unit,
    val setEpisodeComments: (EpisodeComments?) -> Unit,
)

@Composable
internal fun MainActivity.rememberHomeFeedData(): ShellHomeData {
    val quick = remember(feed, playHistory, loading) {
        val fromFeed = feed.toQuickAccess()
        when {
            fromFeed.isNotEmpty() -> fromFeed
            loading -> emptyList()
            else -> {
                log.i("feed has no shorts section, deriving the shortcut grid from history")
                playHistory.map { it.item }.toQuickAccessFallback()
            }
        }
    }
    LaunchedEffect(quick) {
        log.i("home quick access tiles=${quick.size}")
    }
    val shelves = remember(feed) { feed.toShelves() }
    val historyShelves = remember(playHistory, homeArtistImages.size) {
        listOfNotNull(
            playHistory.map { it.item }.toRecentlyPlayedShelf(),
            favoriteArtistsOf(playHistory).toFavoriteArtistsShelf(homeArtistImages),
        )
    }
    LaunchedEffect(shelves) {
        log.i("home shelves=${shelves.size} " +
                "grids=${shelves.count { it.grid }} " +
                "cards=${shelves.sumOf { it.cards.size }} " +
                "titles=${shelves.take(8).joinToString(" | ") { it.title.ifBlank { "-" } }}"
        )
    }
    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val startOfYesterday = remember { startOfToday - 24L * 60 * 60 * 1000 }
    val historyToday = remember(playHistory, startOfToday) {
        playHistory.filter { it.playedAtMs >= startOfToday }.map { it.item }
    }
    val historyYesterday = remember(playHistory, startOfToday, startOfYesterday) {
        playHistory
            .filter { it.playedAtMs >= startOfYesterday && it.playedAtMs < startOfToday }
            .map { it.item }
    }
    val historyEarlier = remember(playHistory, startOfYesterday) {
        playHistory.filter { it.playedAtMs in 1 until startOfYesterday }.map { it.item }
    }
    val statsPlaylistImages = remember { mutableStateMapOf<String, String>() }
    val historyStats = remember(playStats, playHistory, homeArtistImages.size, statsPlaylistImages.size) {
        buildHistoryStats(playStats, playHistory, statsPlaylistImages)
    }
    LaunchedEffect(historyStats) {
        val playlistUri = historyStats.topPlaylistUri
        if (playlistUri.startsWith("spotify:playlist:") && !statsPlaylistImages.containsKey(playlistUri)) {
            val cover = runCatching {
                Krespot.collections.collection(this@rememberHomeFeedData, playlistUri)?.imageUrl
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (cover != null) statsPlaylistImages[playlistUri] = cover
        }
        val artistUri = historyStats.topArtistUri
        if (artistUri.startsWith("spotify:artist:") && homeArtistImages[artistUri].isNullOrBlank()) {
            val cover = runCatching {
                Search.section(
                    this@rememberHomeFeedData,
                    SearchKind.ARTIST,
                    historyStats.topArtistName.substringBefore(", "),
                ).firstOrNull { it.uri == artistUri }?.imageUrl
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (cover != null) homeArtistImages[artistUri] = cover
        }
    }
    return ShellHomeData(
        quick = quick,
        shelves = shelves,
        historyShelves = historyShelves,
        historyToday = historyToday,
        historyYesterday = historyYesterday,
        historyEarlier = historyEarlier,
        historyStats = historyStats,
    )
}

@Composable
internal fun MainActivity.rememberNowPlayingData(
    canvasAllowed: Boolean,
): ShellNowPlayingData {
    val writers = NowPlayingWriters(
        setArtist = { nowPlayingArtist = it },
        setArtistFollowed = { nowPlayingArtistFollowed = it },
        setLyrics = { nowPlayingLyrics = it },
        setNextLyrics = { nowPlayingNextLyrics = it },
        setTranscript = { nowPlayingTranscript = it },
        setCanvasFile = { canvasFile = it },
        setShowDetail = { nowPlayingShow = it },
        setEpisodeComments = { nowPlayingComments = it },
    )
    val playback = PlaybackHost.bridge?.state
    val nowPlayingArtistUri = remember(playback?.trackUri, playback?.artistUri, playback?.queue, playback?.queueFromContext) {
        playback?.artistUri.orEmpty().ifBlank {
            ((playback?.queue.orEmpty()) + (playback?.queueFromContext.orEmpty()))
                .firstOrNull { it.uri == playback?.trackUri }?.artistUri.orEmpty()
        }
    }
    val nowPlayingEpisodeUri = remember(playback?.trackUri) {
        playback?.trackUri.orEmpty().takeIf { it.startsWith("spotify:episode:") }
    }
    LaunchedEffect(nowPlayingArtistUri) {
        if (nowPlayingArtistUri.isBlank()) {
            writers.setArtist(null)
            writers.setArtistFollowed(null)
            return@LaunchedEffect
        }
        val cached = artistCache[nowPlayingArtistUri]
        if (cached != null) {
            writers.setArtist(cached)
        } else {
            val fetched = Krespot.artists.artist(this@rememberNowPlayingData, nowPlayingArtistUri)
                ?.also {
                    if (artistCache.size >= 24) artistCache.clear()
                    artistCache[it.uri] = it
                }
            writers.setArtist(fetched)
        }
        val inLibrary = Library.saved(this@rememberNowPlayingData, listOf(nowPlayingArtistUri))
        writers.setArtistFollowed(if (inLibrary.isEmpty()) {
            nowPlayingArtist?.saved == true
        } else {
            nowPlayingArtistUri in inLibrary
        })
    }
    LaunchedEffect(nowPlayingEpisodeUri) {
        if (nowPlayingEpisodeUri == null) {
            writers.setShowDetail(null)
            writers.setEpisodeComments(null)
            return@LaunchedEffect
        }
        val showUri = nowPlayingEpisodeUri.substringBeforeLast(":").let {
            runCatching {
                val episode = Podcasts.episode(this@rememberNowPlayingData, nowPlayingEpisodeUri)
                episode?.showUri.orEmpty()
            }.getOrDefault("")
        }
        if (showUri.startsWith("spotify:show:")) {
            writers.setShowDetail(Podcasts.show(this@rememberNowPlayingData, showUri))
        }
        if (episodeCommentsUri != nowPlayingEpisodeUri) {
            episodeCommentsUri = nowPlayingEpisodeUri
            episodeCommentsNonce++
        }
    }
    LaunchedEffect(
        playback?.trackUri,
        playback?.streamSource,
        playback?.videoId,
        LyricsOptions.translationEnabled,
        LyricsOptions.translationLanguage,
        LyricsOptions.prioritizeWordSynced,
        LyricsOptions.providerOrder,
        LyricsOptions.ytmProviderOrder,
    ) {
        val uri = playback?.trackUri.orEmpty()
        if (!uri.startsWith("spotify:track:")) {
            writers.setLyrics(null)
            return@LaunchedEffect
        }
        val bridge = PlaybackHost.bridge
        val duration = bridge?.progressSnapshot?.durationMs ?: 0L
        val stream = playback?.streamSource
            ?.let { key -> StreamSource.entries.firstOrNull { it.key == key } }
            ?: StreamSource.SPOTIFY
        val order = LyricsOptions.providerOrderFor(stream)
        val repository = Krespot.lyricsRepository
        val result = repository.lyrics(
            context = this@rememberNowPlayingData,
            trackUri = uri,
            title = playback?.title.orEmpty(),
            artist = playback?.artist.orEmpty(),
            durationMs = duration,
            sources = order.toSet(),
            order = order,
            prioritizeWordSynced = LyricsOptions.prioritizeWordSynced,
            stream = stream,
            videoId = playback?.videoId.orEmpty(),
        )
        var trackLyrics = if (result != null) {
            repository.asTrackLyrics(result)
        } else if (stream == StreamSource.SPOTIFY) {
            Krespot.lyrics.lyrics(this@rememberNowPlayingData, uri)
        } else {
            null
        }
        if (trackLyrics != null && LyricsOptions.translationEnabled && LyricsOptions.enabled) {
            val target = LyricsOptions.translationTargetTag(this@rememberNowPlayingData)
            val translated = com.quackify.backend.library.lyrics.LyricsTranslation.translate(
                context = this@rememberNowPlayingData,
                trackId = uri,
                lines = trackLyrics.lines.map { it.text },
                targetLanguageTag = target,
            )
            if (translated is com.quackify.backend.library.lyrics.LyricsTranslation.Result.Translated) {
                val texts = translated.texts
                trackLyrics = trackLyrics.copy(
                    sourceLanguage = translated.sourceLanguage,
                    lines = trackLyrics.lines.mapIndexed { index, line ->
                        val slotIndex = translatedSlotIndex(trackLyrics.lines, index)
                        line.copy(
                            translation = slotIndex?.let { texts.getOrNull(it) }.orEmpty(),
                        )
                    },
                )
            } else if (translated is com.quackify.backend.library.lyrics.LyricsTranslation.Result.SameLanguage) {
                trackLyrics = trackLyrics.copy(sourceLanguage = translated.language)
            }
        }
        writers.setLyrics(trackLyrics)
    }
    // The reader's sneak peek: the first lines of whatever comes next. The
    // lookup re-runs only when the next track actually changes, and it is
    // skipped entirely while the feature is off so the queue costs nothing.
    LaunchedEffect(
        playback?.nextTrack?.uri,
        LyricsOptions.nextTrackPeek,
        LyricsOptions.enabled,
        LyricsOptions.prioritizeWordSynced,
        LyricsOptions.providerOrder,
        LyricsOptions.ytmProviderOrder,
    ) {
        val next = playback?.nextTrack
        if (!LyricsOptions.enabled || !LyricsOptions.nextTrackPeek || next == null ||
            !next.uri.startsWith("spotify:track:")
        ) {
            writers.setNextLyrics(null)
            return@LaunchedEffect
        }
        val bridge = PlaybackHost.bridge
        val repository = Krespot.lyricsRepository
        val order = LyricsOptions.providerOrderFor(
            StreamSource.entries.firstOrNull { it.key == playback?.streamSource } ?: StreamSource.SPOTIFY
        )
        val result = repository.lyrics(
            context = this@rememberNowPlayingData,
            trackUri = next.uri,
            title = next.title,
            artist = next.artist,
            durationMs = next.durationMs,
            sources = order.toSet(),
            order = order,
            prioritizeWordSynced = LyricsOptions.prioritizeWordSynced,
            stream = StreamSource.entries.firstOrNull { it.key == playback?.streamSource } ?: StreamSource.SPOTIFY,
            videoId = "",
        )
        val nextLyrics = if (result != null) {
            repository.asTrackLyrics(result)
        } else {
            Krespot.lyrics.lyrics(this@rememberNowPlayingData, next.uri)
        }
        // Only the opening lines are ever shown; trimming here keeps the
        // cached payload small instead of carrying a whole song for 5 lines.
        writers.setNextLyrics(
            nextLyrics?.let { lyrics ->
                lyrics.copy(lines = lyrics.lines.take(5))
            }
        )
    }
    LaunchedEffect(playback?.trackUri) {
        val uri = playback?.trackUri.orEmpty()
        if (!uri.startsWith("spotify:episode:")) {
            writers.setTranscript(null)
            return@LaunchedEffect
        }
        writers.setTranscript(Krespot.transcripts.transcript(this@rememberNowPlayingData, uri))
    }
    LaunchedEffect(playback?.trackUri, canvasAllowed, canvasResolution) {
        writers.setCanvasFile(null)
        if (!canvasAllowed) return@LaunchedEffect
        val uri = playback?.trackUri.orEmpty()
        if (!uri.startsWith("spotify:track:")) return@LaunchedEffect
        val file = CanvasKit.download(this@rememberNowPlayingData, uri, canvasResolution)
        if (uri == playback?.trackUri) writers.setCanvasFile(file)
    }
    LaunchedEffect(episodeCommentsUri, episodeCommentsNonce) {
        val uri = episodeCommentsUri ?: return@LaunchedEffect
        val comments = runCatching {
            Podcasts.episodeComments(this@rememberNowPlayingData, uri, null)
        }.getOrNull()
        if (episodeCommentsUri == uri) writers.setEpisodeComments(comments)
    }
    return ShellNowPlayingData(
        artistUri = nowPlayingArtistUri,
        canvasAllowed = canvasAllowed,
        showDetail = nowPlayingShow,
        episodeComments = nowPlayingComments,
    )
}

/**
 * The translation result carries one entry per non-blank line, in order, so
 * this maps a line's index onto the entry that translates it.
 */
private fun translatedSlotIndex(
    lines: List<com.quackify.backend.model.LyricLine>,
    lineIndex: Int,
): Int? {
    var slot = 0
    lines.forEachIndexed { index, line ->
        if (index == lineIndex) return if (line.text.isNotBlank()) slot else null
        if (line.text.isNotBlank()) slot++
    }
    return null
}

/**
 * The shell wiring itself. It used to sit inline inside the HomeSplashHost
 * lambda in ShellSection, which put the whole ~700-line EncoreAppShell call
 * into one compiler-generated lambda method and blew past the JVM's 64KB
 * method limit ("Method too large: MainActivity.ShellSection$lambda$291").
 * Giving the call its own composable splits the bytecode across named
 * functions, each far below the limit.
 */
@Composable
internal fun MainActivity.ShellAppHostSection(
    bridge: PlaybackBridge,
    account: EncoreAuth.Account?,
    context: Context,
    loggedIn: Boolean,
    activeAccountId: String,
    onSignedOut: () -> Unit,
    updates: UpdateController,
    scope: kotlinx.coroutines.CoroutineScope,
    home: ShellHomeData,
    nowPlaying: ShellNowPlayingData,
    contributors: PlaylistContributorController,
    activity: ActivityFeedController,
    recents: List<RecentSearches.Entry>,
) {
    val onAddRecommendedBound: (TrackItem) -> Unit = { track -> onAddRecommendedHandler(scope, track) }
    val onSavePlaylistOrderBound: (String, List<TrackItem>) -> Unit = { uri, order -> onSavePlaylistOrderHandler(scope, context, uri, order) }
    val onEpisodeCommentBound: suspend (String) -> Boolean = { text -> onEpisodeCommentHandler(context, text) }
    val onLoadCommentRepliesBound: suspend (String, String?) -> EpisodeCommentReplies? = { commentUri, pageToken -> onLoadCommentRepliesHandler(context, commentUri, pageToken) }
    val onCommentReactionBound: suspend (String, String) -> Boolean = { commentUri, emoji -> onCommentReactionHandler(context, commentUri, emoji) }
    val onRemoveCommentReactionBound: suspend (String) -> Boolean = { commentUri -> onRemoveCommentReactionHandler(context, commentUri) }
    val onLoadCommentReactionsBound: suspend (String) -> EpisodeCommentReactions? = { commentUri -> onLoadCommentReactionsHandler(context, commentUri) }
    val onDeleteCommentBound: suspend (String) -> Boolean = { commentUri -> onDeleteCommentHandler(context, commentUri) }
    val onCanvasToggleBound: (Boolean) -> Unit = { on -> onCanvasToggleHandler(bridge, on) }
    val onCanvasResolutionBound: (Int) -> Unit = { height -> onCanvasResolutionHandler(height) }
    val onAddTrackBound: suspend (String) -> Boolean = { trackUri -> onAddTrackHandler(context, trackUri) }
    val onShowCreditsBound: (String) -> Unit = { uri -> onShowCreditsHandler(scope, uri) }
    val onAddToPlaylistBound: (TrackItem) -> Unit = { track -> onAddToPlaylistHandler(scope, track) }
    val onPickPlaylistBound: (PlaylistChoice, TrackItem) -> Unit = { choice, track -> onPickPlaylistHandler(scope, choice, track) }
    val onTogglePlaylistBound: (PlaylistChoice, TrackItem, Boolean) -> Unit = { choice, track, shouldAdd -> onTogglePlaylistHandler(scope, choice, track, shouldAdd) }
    val onLoadPlaylistFolderBound: suspend (String, String) -> List<PlaylistChoice> = { trackUri, folderUri -> onLoadPlaylistFolderHandler(context, trackUri, folderUri) }
    val onLoadTrackCountBound: suspend (String) -> Int = { playlistUri -> onLoadTrackCountHandler(context, playlistUri) }
    val onCreatePlaylistWithBound: (TrackItem) -> Unit = { track -> onCreatePlaylistWithHandler(scope, track) }
    val onCreatePlaylistBound: suspend (String, String, ByteArray?) -> String? = { name, description, cover -> onCreatePlaylistHandler(context, name, description, cover) }
    val onSearchUsersBound: suspend (String) -> List<SearchHit> = { term -> onSearchUsersHandler(context, term) }
    val onEditPlaylistBound: (String) -> Unit = { uri -> onEditPlaylistHandler(scope, uri) }
    val onEditPlaylistDetailsBound: suspend (String, String, String, ByteArray?, Boolean) -> Boolean = { uri, name, description, cover, clearCover -> onEditPlaylistDetailsHandler(context, uri, name, description, cover, clearCover) }
    val onDeletePlaylistBound: suspend (String) -> Boolean = { uri -> onDeletePlaylistHandler(context, uri) }
    val onTogglePlaylistSavedBound: (String, Boolean) -> Unit = { uri, saved -> onTogglePlaylistSavedHandler(scope, uri, saved) }
    val onRemoveTrackFromPlaylistBound: (String, String) -> Unit = { pUri, tUri -> onRemoveTrackFromPlaylistHandler(scope, pUri, tUri) }
    val onRemoveLibraryItemBound: (String) -> Unit = { uri -> onRemoveLibraryItemHandler(scope, uri) }
    val onSetLibraryItemPinnedBound: (String, Boolean) -> Unit = { uri, pinned -> onSetLibraryItemPinnedHandler(scope, uri, pinned) }
    val onCreateFolderBound: suspend (String) -> String? = { name -> onCreateFolderHandler(context, name) }
    val onRenameFolderBound: (String, String) -> Unit = { uri, name -> onRenameFolderHandler(scope, uri, name) }
    val onDeleteFolderBound: (String) -> Unit = { uri -> onDeleteFolderHandler(scope, uri) }
    val onMoveToFolderBound: (String, String) -> Unit = { playlistUri, targetFolderUri -> onMoveToFolderHandler(scope, playlistUri, targetFolderUri) }
    val onArtistUnbanBound: (String) -> Unit = { uri -> onArtistUnbanHandler(scope, context, uri) }
    val onArtistBanChangedBound: (String, Boolean) -> Unit = { uri, banned -> onArtistBanChangedHandler(uri, banned) }
    val onArtistFollowToggleBound: (String, Boolean) -> Unit = { uri, follow -> onArtistFollowToggleHandler(scope, nowPlaying, uri, follow) }
    val onArtistFollowedBound: suspend (String) -> Boolean? = { uri -> onArtistFollowedHandler(context, uri) }
    val onArtistImageBound: suspend (String) -> String? = { uri -> onArtistImageHandler(context, uri) }
    val onShowFollowToggleBound: (String, Boolean) -> Unit = { uri, follow -> onShowFollowToggleHandler(scope, uri, follow) }
    val onArtistTrackLikeToggleBound: (TrackItem) -> Unit = { track -> onArtistTrackLikeToggleHandler(scope, context, track) }
    val onAlbumTrackLikeToggleBound: (TrackItem) -> Unit = { track -> onAlbumTrackLikeToggleHandler(scope, context, track) }
    val onAlbumSavedToggleBound: (String, Boolean) -> Unit = { uri, saved -> onAlbumSavedToggleHandler(scope, uri, saved) }
    val onPlaylistTrackLikeToggleBound: (TrackItem) -> Unit = { track -> onPlaylistTrackLikeToggleHandler(scope, context, track) }
    val onArtistRadioBound: suspend (String) -> String? = { uri -> onArtistRadioHandler(context, uri) }
    val onSearchSubmitBound: (String) -> Unit = { query -> onSearchSubmitHandler(scope, context, query) }
    val onRemoveRecentSearchBound: (SearchHistoryItem) -> Unit = { item -> onRemoveRecentSearchHandler(scope, context, item) }
    val onPromoteRecentSearchBound: (SearchHistoryItem) -> Unit = { item -> onPromoteRecentSearchHandler(context, item) }
    val onRecordRecentSearchBound: (SearchHit) -> Unit = { hit -> onRecordRecentSearchHandler(scope, context, hit) }
    val onLoadSearchSectionBound: suspend (SearchKind, Int) -> List<SearchHit> = { kind, offset -> onLoadSearchSectionHandler(context, kind, offset) }
    val onToggleSavedBound: (String, Boolean) -> Unit = { uri, saved -> onToggleSavedHandler(scope, uri, saved) }
    val onToggleVideoFullscreenBound: (Boolean) -> Unit = { on -> onToggleVideoFullscreenHandler(on) }
    val onAppIconChangeBound: (String) -> Unit = { option -> onAppIconChangeHandler(context, option) }
    val onOpenCategoryBound: (String) -> Unit = { category -> onOpenCategoryHandler(category) }
    val onOpenShowBound: (String) -> Unit = { uri -> onOpenShowHandler(uri) }
    val onOpenEpisodeBound: (String) -> Unit = { uri -> onOpenEpisodeHandler(uri) }
    val onOpenBrowseBound: (String) -> Unit = { uri -> onOpenBrowseHandler(uri) }
    val onOpenFolderBound: (String) -> Unit = { uri -> onOpenFolderHandler(uri) }
    val onLoadPlaylistBound: (String) -> Unit = { uri -> onLoadPlaylistHandler(uri) }
    val onToggleSettingBound: (String, Boolean) -> Unit = { field, value -> onToggleSettingHandler(scope, account, field, value) }
    val onSwitchAccountBound: (String) -> Unit = { id -> onSwitchAccountHandler(id) }
    EncoreAppShell(
        deeplink = pendingLink?.let { ShellDeepLink(it.kind, it.uri, it.query) },
        onDeeplinkConsumed = { pendingLink = null },
        onDeeplinkSearch = { q -> if (searchTerm == q) searchFetch++ else searchTerm = q },
        updateChecking = updates.checking,
        updateChecked = updates.checked,
        updateError = updates.error,
        updateVersion = updates.appUpdate?.version,
        onCheckUpdates = updates::requestManualCheck,
        onHomeEntered = updates::onHomeEntered,
        userDisplayName = account?.name?.takeIf { it.isNotBlank() } ?: "Quackify",
        userAvatarUrl = account?.imageUrl.orEmpty(),
        userProfileColorHex = userProfileColorHex,
    playbackState = bridge.state,
    progressState = bridge.progress,
    playerListener = bridge.listener,
    expandPlayerSignal = if (loggedIn) openPlayerSignal else 0,
    queueEditResult = bridge.queueEditResult,
    licenceRateLimitedSignal = bridge.licenceRateLimitedSignal,
        homeQuickAccessItems = home.quick,
    homeShelves = if (homeFacet.isBlank() && !loading) home.historyShelves + home.shelves else home.shelves,
    homeChips = homeChips,
    homeFacet = homeFacet,
    homeGreeting = homeGreeting,
    onHomeFacetSelected = { facet -> homeFacet = facet },
    libraryItems = libraryItems,
    libraryLoading = libraryItems == null,
    playlistTracks = playlistTracks,
    playlistDetail = playlistDetail,
    playlistLoading = playlistLoading,
    playlistHasMore = playlistHasMore,
    playlistLoadingMore = playlistLoadingMore,
onLoadMorePlaylistTracks = { onLoadMorePlaylistTracksHandler(scope) },
    playlistRecommendations = playlistRecommendations,
    recommendationsLoading = recommendationsLoading,
    onRefreshRecommendations = { recommendationsNonce++ },
onAddRecommended = onAddRecommendedBound,
onSavePlaylistOrder = onSavePlaylistOrderBound,
    albumTracks = albumTracks,
    albumDetail = albumDetail,
    albumLoading = albumLoading,
    albumFailed = albumFailed,
    albumHasMore = albumHasMore,
    albumLoadingMore = albumLoadingMore,
onLoadMoreAlbumTracks = { onLoadMoreAlbumTracksHandler(scope) },
        onRetryAlbum = { albumRetry++ },
        nowPlayingArtistBio = nowPlayingArtist?.biography.orEmpty(),
        nowPlayingMonthlyListeners = nowPlayingArtist?.monthlyListeners
    ?.takeIf { it > 0L }?.let { formatCount(it) + " monthly listeners" }.orEmpty(),
        nowPlayingArtistImageUrl = nowPlayingArtist?.imageUrl?.takeIf { it.isNotBlank() },
        nowPlayingShow = nowPlaying.showDetail,
        nowPlayingComments = nowPlaying.episodeComments?.comments.orEmpty(),
        onCommentsRefresh = { episodeCommentsNonce++ },
onEpisodeComment = onEpisodeCommentBound,
onLoadCommentReplies = onLoadCommentRepliesBound,
onCommentReaction = onCommentReactionBound,
onRemoveCommentReaction = onRemoveCommentReactionBound,
onLoadCommentReactions = onLoadCommentReactionsBound,
onDeleteComment = onDeleteCommentBound,
        nowPlayingArtistFollowed = nowPlayingArtistFollowed,
        nowPlayingLyrics = remember(nowPlayingLyrics) { mapNowPlayingLyrics(nowPlayingLyrics) },
        nextTrackLyrics = remember(nowPlayingNextLyrics) { mapNowPlayingLyrics(nowPlayingNextLyrics) },
        lyricsProviderName = nowPlayingLyrics?.provider.orEmpty(),
        nowPlayingTranscript = remember(nowPlayingTranscript) { mapNowPlayingTranscript(nowPlayingTranscript) },
        canvasFilePath = canvasFile,
        isCanvasAllowed = nowPlaying.canvasAllowed,
        isCanvasEnabled = canvasEnabled,
onCanvasToggle = onCanvasToggleBound,
        canvasResolution = canvasResolution,
onCanvasResolution = onCanvasResolutionBound,
        artistDetail = artistDetail,
        artistLoading = artistLoading,
        artistFailed = artistFailed,
        onRetryArtist = { artistRetry++ },
        trackCredits = trackCredits,
        addToPlaylistChoices = addToPlaylistChoices,
        addTracksSongs = addTracksSongs,
        addTracksEpisodes = addTracksEpisodes,
        addTracksLiked = addTracksLiked,
        addTracksRecent = addTracksRecent,
        addTracksLoading = addTracksLoading,
        onAddTracksOpen = { addTracksFetch++ },
onAddTrack = onAddTrackBound,
onShowCredits = onShowCreditsBound,
onAddToPlaylist = onAddToPlaylistBound,
onPickPlaylist = onPickPlaylistBound,
onTogglePlaylist = onTogglePlaylistBound,
onLoadPlaylistFolder = onLoadPlaylistFolderBound,
onLoadTrackCount = onLoadTrackCountBound,
onCreatePlaylistWith = onCreatePlaylistWithBound,
    onPlaylistDefaultName = { Krespot.playlists.defaultName(context) },
onCreatePlaylist = onCreatePlaylistBound,
    playlistSaved = playlistSaved,
    playlistPermissions = contributors.permissions,
    contributorsLoading = contributors.loading,
    contributorBusyIds = contributors.busyIds,
    onLoadPlaylistContributors = contributors::refresh,
onSearchUsers = onSearchUsersBound,
    onAddPlaylistContributor = contributors::add,
    onRemovePlaylistContributor = contributors::removeAsContributor,
    onRemovePlaylistMember = contributors::removeFromPlaylist,
    playlistEditDraft = playlistEditDraft,
    editablePlaylistUris = editableUris,
onEditPlaylist = onEditPlaylistBound,
    onDismissEditPlaylist = { playlistEditDraft = null },
onEditPlaylistDetails = onEditPlaylistDetailsBound,
onDeletePlaylist = onDeletePlaylistBound,
onTogglePlaylistSaved = onTogglePlaylistSavedBound,
onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylistBound,
onRemoveLibraryItem = onRemoveLibraryItemBound,
onSetLibraryItemPinned = onSetLibraryItemPinnedBound,
onCreateFolder = onCreateFolderBound,
onRenameFolder = onRenameFolderBound,
onDeleteFolder = onDeleteFolderBound,
onMoveToFolder = onMoveToFolderBound,
    artistTracks = artistTracks,
    artistReleases = artistReleases,
    artistMonthlyListeners = artistListeners,
    artistFollowed = artistFollowed,
    artistBanned = artistBanned,
onArtistUnban = onArtistUnbanBound,
onArtistBanChanged = onArtistBanChangedBound,
onArtistFollowToggle = onArtistFollowToggleBound,
onArtistFollowed = onArtistFollowedBound,
onArtistImage = onArtistImageBound,
onShowFollowToggle = onShowFollowToggleBound,
onArtistTrackLikeToggle = onArtistTrackLikeToggleBound,
onAlbumTrackLikeToggle = onAlbumTrackLikeToggleBound,
    albumSaved = albumSaved,
onAlbumSavedToggle = onAlbumSavedToggleBound,
onPlaylistTrackLikeToggle = onPlaylistTrackLikeToggleBound,
onArtistRadio = onArtistRadioBound,
    profile = profileDetail,
    profileTop = profileTop,
    profileTopUnavailable = profileTopUnavailable,
    profileContexts = profileContexts,
    profileLoading = profileLoading,
    profileFailed = profileFailed,
onLoadProfile = { onLoadProfileHandler() },
onRetryProfile = { onRetryProfileHandler() },
onOpenUserProfile = { uri, _ -> onOpenUserProfileHandler(uri, "ignored") },
    profileFollowers = profileFollowers,
    profileFollowing = profileFollowing,
    profileFollowLoading = profileFollowLoading,
    onLoadProfileFollows = { profileFollowFetch++ },
    homeLoading = loading,
    searchResults = remember(searchHits) { mapSearchResults(searchHits) },    onSearchQueryChange = { query -> searchTerm = query },
onSearchSubmit = onSearchSubmitBound,
    searchSuggestionHits = searchHits,
    browseCategories = browseCategories,
    searchSuggestions = remember(searchHits) { mapSearchSuggestions(searchHits) },    searchRecent = remember(recents) { mapSearchRecent(recents) },    searchLoading = searchLoading,
    searchFailed = searchFailed,
    onRetrySearch = { searchFetch++ },
    searchQuery = searchTerm,
    searchData = searchData,
onClearRecentSearches = { onClearRecentSearchesHandler(scope, context) },
onRemoveRecentSearch = onRemoveRecentSearchBound,
onPromoteRecentSearch = onPromoteRecentSearchBound,
onRecordRecentSearch = onRecordRecentSearchBound,
onLoadSearchSection = onLoadSearchSectionBound,
onToggleSaved = onToggleSavedBound,
    searchSavedOverrides = searchSavedOverrides.toMap(),
    browsePage = browsePage,
    browseLoadingMore = browseLoadingMore,
    categoryShelves = categoryShelves,
    videoFullscreen = videoFullscreen,
    videoAutoExitSignal = videoAutoExitSignal,
onToggleVideoFullscreen = onToggleVideoFullscreenBound,
onAppIconChange = onAppIconChangeBound,
onOpenCategory = onOpenCategoryBound,
    showDetail = showDetail,
    showLoading = showLoading,
    episodeDetail = episodeDetail,
    episodeLoading = episodeLoading,
onOpenShow = onOpenShowBound,
onOpenEpisode = onOpenEpisodeBound,
onOpenBrowse = onOpenBrowseBound,
onLoadMoreBrowse = { onLoadMoreBrowseHandler(scope, context) },
    folderItems = folderItems,
    folderLoading = folderItems == null,
onOpenFolder = onOpenFolderBound,
onLoadPlaylist = onLoadPlaylistBound,
onRetryPlaylist = { onRetryPlaylistHandler() },
    onLoadAlbum = { uri, _ -> albumUri = uri },
    onLoadArtist = { uri, _ -> artistUri = uri },
    settingsSwitches = settingsSwitches,
    settingsLocks = settingsLocks,
    settingsProduct = settingsProduct,
    settingsCountry = settingsCountry,
    settingsLoading = settingsLoading,
onToggleSetting = onToggleSettingBound,
    notifications = whatsNew,
    notificationsLoading = whatsNewLoading,
    notificationsFailed = whatsNewFailed,
    onRefreshWhatsNew = { whatsNewFetch++ },
    activityState = buildActivityFeedState(activity),    historyToday = home.historyToday,
    historyYesterday = home.historyYesterday,
    historyEarlier = home.historyEarlier,
    historyStats = home.historyStats,
    historyLoading = false,
onLogout = { onLogoutHandler(context) },
    savedAccounts = savedAccountsList,
    activeAccountId = activeAccountId,
onSwitchAccount = onSwitchAccountBound,
onAddAccount = { onAddAccountHandler(scope, account) },
onRemoveAccount = { id -> onRemoveAccountHandler(context, account, onSignedOut, id) }
        )

}

/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRemoveAccountHandler(context: Context, account: EncoreAuth.Account?, onSignedOut: () -> Unit, id: String) {
log.i("remove account tapped id=$id")
resetHomeSplash()
AccountSignOut.begin(context, id)
if (forgetAccountAndSwitch(id)) {
    PlaybackHost.reset(context)
    val remaining = EncoreAuth.savedAccounts(context)
    if (remaining.isNotEmpty()) {
        EncoreAuth.switchAccount(context, remaining.last().id)
        Krespot.library.invalidate()
        recreate()
    } else {
        EncoreAuth.logout(context)
        Krespot.library.invalidate()
        onSignedOut()
    }
} else {
    savedAccountsList = EncoreAuth.savedAccounts(context)
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onAddAccountHandler(scope: kotlinx.coroutines.CoroutineScope, account: EncoreAuth.Account?) {
log.i("add account tapped")
scope.launch {
    savedAccountsList = beginAddAccountFlow()
    addingAccount = true
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onSwitchAccountHandler(id: String) {
resetHomeSplash()
switchAccountNow(id)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onLogoutHandler(context: Context) {
log.i("logout tapped")
PlaybackHost.reset(context)
EncoreAuth.logout(context)
Krespot.library.invalidate()
val remaining = EncoreAuth.savedAccounts(context)
if (remaining.isNotEmpty()) {
    EncoreAuth.switchAccount(context, remaining.last().id)
    (context as? Activity)?.recreate()
} else {
    (context as? Activity)?.recreate()
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onToggleSettingHandler(scope: kotlinx.coroutines.CoroutineScope, account: EncoreAuth.Account?, field: String, value: Boolean) {
val before = settingsSwitches
settingsSwitches = when (field) {
    "canvasDisabled" -> before.copy(canvas = !value)
    "musicVideoDisabled" -> before.copy(musicVideos = !value)
    "otherVideoDisabled" -> before.copy(otherVideos = !value)
    "showFollows" -> before.copy(showFollows = !value)
    "showPublishedPlaylists" -> before.copy(showPlaylists = !value)
    else -> before
}
scope.launch {
    val ok = applySettingChange(field, value, account?.id.orEmpty())
    log.i("settings $field -> $value ok=$ok")
    if (!ok) settingsSwitches = before
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRetryPlaylistHandler() {
playlistDetail = null
playlistTracks = null
playlistLoading = true
playlistFetch++
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onLoadPlaylistHandler(uri: String) {
if (uri != playlistUri) {
    playlistUri = uri
    playlistDetail = null
    playlistTracks = null
    playlistHasMore = false
    playlistLoading = true
    playlistRecommendations = emptyList()
    playlistSaved = null
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onOpenFolderHandler(uri: String) {
folderUri = uri
folderFetch++
folderItems = null
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onLoadMoreBrowseHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context) {
val current = browsePage
if (current != null && !browseLoadingMore) {
    val next = current.nextOffset
    val uri = browseUri
    if (next != null && uri.isNotBlank()) {
        scope.launch {
            browseLoadingMore = true
            val more = Browse.page(context, uri, next)
            if (more != null && browsePage === current) {
                browsePage = current.copy(
                    sections = current.sections + more.sections,
                    nextOffset = more.nextOffset,
                )
            }
            browseLoadingMore = false
        }
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onOpenBrowseHandler(uri: String) {
browseUri = uri
browseFetch++
browsePage = null
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onOpenEpisodeHandler(uri: String) {
episodeUri = uri
episodeFetch++
episodeDetail = null
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onOpenShowHandler(uri: String) {
showUri = uri
showFetch++
showDetail = null
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onOpenCategoryHandler(uri: String) {
categoryUri = uri
categoryShelves = null
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onAppIconChangeHandler(context: Context, option: String) {
applyLauncherIcon(context, option)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onToggleVideoFullscreenHandler(on: Boolean) {
videoFullscreen = on
requestedOrientation = if (on) {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
} else {
    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onToggleSavedHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String, saved: Boolean) {
scope.launch {
    val next = !saved
    val ok = toggleSearchSaved(uri, next)
    if (ok) {
        searchSavedOverrides[uri] = next
        searchData = searchData?.withSaved(uri, next)
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onLoadSearchSectionHandler(context: Context, kind: com.quackify.backend.model.SearchKind, offset: Int): List<com.quackify.backend.model.SearchHit> {
return Search.section(context, kind, searchTerm, offset)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRecordRecentSearchHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, hit: com.quackify.backend.model.SearchHit) {
RecentSearches.record(context, hit)
log.i("recorded '${hit.title}' in the recent searches")
if (hit.uri.isNotBlank()) {
    scope.launch { Search.saveRecent(context, listOf(hit.uri)) }
}
val submitted = submittedSearchQuery
if (submitted != null && searchTerm.trim() == submitted) {
    RecentSearches.remove(context, submitted)
}
submittedSearchQuery = null
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onPromoteRecentSearchHandler(context: Context, item: com.quackify.compose.search.SearchHistoryItem) {
RecentSearches.promote(context, item.uri.ifBlank { item.title })
log.i("promoted '${item.title}' in the recent searches")
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRemoveRecentSearchHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, item: com.quackify.compose.search.SearchHistoryItem) {
RecentSearches.remove(context, item.uri.ifBlank { item.title })
log.i("removed '${item.title}' from the recent searches")
serverRecentUri(item.uri, item.title)?.let { uri ->
    scope.launch { Search.removeRecent(context, listOf(uri)) }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onClearRecentSearchesHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context) {
val uris = RecentSearches.all(context)
    .mapNotNull { serverRecentUri(it.uri, it.title) }
    .distinct()
RecentSearches.clear(context)
log.i("cleared the recent searches on this device")
if (uris.isNotEmpty()) {
    scope.launch { Search.removeRecent(context, uris) }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onSearchSubmitHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, query: String) {
RecentSearches.recordQuery(context, query)
submittedSearchQuery = query
scope.launch { Search.saveRecent(context, listOf(Search.searchUriOf(query))) }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onOpenUserProfileHandler(uri: String, ignored: String) {
if (profileUserId != uri) {
    profileUserId = uri
    profileDetail = null
    profileTop = null
    profileTopUnavailable = false
    profileContexts = emptyList()
}
profileFetch++
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRetryProfileHandler() {
profileDetail = null
profileTop = null
profileTopUnavailable = false
profileContexts = emptyList()
profileFetch++
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onLoadProfileHandler() {
if (profileUserId.isNotBlank()) {
    profileUserId = ""
    profileDetail = null
    profileTop = null
    profileTopUnavailable = false
    profileContexts = emptyList()
}
profileFetch++
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onArtistRadioHandler(context: Context, uri: String): String? {
val radio = Krespot.artists.artistRadio(context, uri)
log.i("artist radio '$uri' -> '$radio'")
if (radio == null) {
    Toast.makeText(
        context,
        "Couldn't load the radio",
        Toast.LENGTH_SHORT
    ).show()
}
return radio
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onPlaylistTrackLikeToggleHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, track: com.quackify.compose.playlist.TrackItem) {
togglePlaylistTrackLike(
    context, scope, playlistUri, track,
    getTracks = { playlistTracks }, setTracks = { playlistTracks = it },
    getDetail = { playlistDetail }, setDetail = { playlistDetail = it },
)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onAlbumSavedToggleHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String, saved: Boolean) {
scope.launch {
    val ok = Library.setSaved(applicationContext, listOf(uri), saved)
    log.i("album save '$uri' -> $saved ok=$ok")
    if (ok && uri == albumUri) albumSaved = saved
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onAlbumTrackLikeToggleHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, track: com.quackify.compose.playlist.TrackItem) {
toggleAlbumTrackLike(context, scope, track, { albumTracks }) { albumTracks = it }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onArtistTrackLikeToggleHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, track: com.quackify.compose.playlist.TrackItem) {
toggleArtistTrackLike(context, scope, track, { artistTracks }) { artistTracks = it }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onShowFollowToggleHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String, follow: Boolean) {
scope.launch {
    val ok = Library.setSaved(applicationContext, listOf(uri), follow)
    log.i("show follow '$uri' -> $follow ok=$ok")
    if (ok && uri == showUri) showDetail = showDetail?.copy(saved = follow)
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onArtistImageHandler(context: Context, uri: String): String? {
return artistCache[uri]?.imageUrl?.takeIf { it.isNotBlank() }
    ?: runCatching { Krespot.artists.artist(context, uri) }.getOrNull()
        ?.also {
            if (artistCache.size >= 24) artistCache.clear()
            artistCache[uri] = it
        }
        ?.imageUrl?.takeIf { it.isNotBlank() }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onArtistFollowedHandler(context: Context, uri: String): Boolean? {
val saved = runCatching { Library.saved(context, listOf(uri)) }.getOrNull()
return saved?.let { if (it.isEmpty()) null else uri in it }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onArtistFollowToggleHandler(scope: kotlinx.coroutines.CoroutineScope, nowPlaying: ShellNowPlayingData, uri: String, follow: Boolean) {
scope.launch {
    val ok = setArtistFollow(uri, follow)
    if (!ok) return@launch
    if (uri == artistUri) artistFollowed = follow
    if (uri == nowPlaying.artistUri) nowPlayingArtistFollowed = follow
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onArtistBanChangedHandler(uri: String, banned: Boolean) {
if (uri == artistUri) artistBanned = banned
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onArtistUnbanHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, uri: String) {
scope.launch {
    val ok = Collection2.setArtistBanned(context, uri, false)
    if (ok && uri == artistUri) artistBanned = false
    if (ok) {
        ArtistBanStore.invalidate()
        com.quackify.compose.bans.ArtistBans.setBanned(uri, false)
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onMoveToFolderHandler(scope: kotlinx.coroutines.CoroutineScope, playlistUri: String, targetFolderUri: String) {
scope.launch {
    val ok = moveTrackToFolder(playlistUri, targetFolderUri)
    if (ok && folderUri.isNotBlank()) {
        folderItems = null
        folderFetch++
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onDeleteFolderHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String) {
scope.launch { deleteFolder(uri) }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRenameFolderHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String, name: String) {
scope.launch { renameFolder(uri, name) }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onCreateFolderHandler(context: Context, name: String): String? {
val uri = Krespot.folders.createFolder(context, name)
log.i("create folder '$name' ok=${uri != null}")
return uri
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onSetLibraryItemPinnedHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String, pinned: Boolean) {
scope.launch { setLibraryItemPinned(uri, pinned) }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRemoveLibraryItemHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String) {
scope.launch { removeFromLibrary(uri) }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onRemoveTrackFromPlaylistHandler(scope: kotlinx.coroutines.CoroutineScope, pUri: String, tUri: String) {
scope.launch {
    val ok = removeTrackFromPlaylist(pUri, tUri)
    if (ok && pUri == playlistUri) {
        playlistTracks = playlistTracks?.filterNot { it.uri == tUri }
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onTogglePlaylistSavedHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String, saved: Boolean) {
scope.launch {
    if (setPlaylistSaved(uri, saved)) playlistSaved = saved
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onDeletePlaylistHandler(context: Context, uri: String): Boolean {
val ok = Krespot.playlists.delete(context, uri)
log.i("delete playlist '$uri' ok=$ok")
if (ok) {
    playlistUri = ""
    playlistDetail = null
    playlistTracks = null
        Krespot.library.invalidate()
}
return ok
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onEditPlaylistDetailsHandler(context: Context, uri: String, name: String, description: String, cover: ByteArray?, clearCover: Boolean): Boolean {
val ok = editPlaylistDetails(uri, name, description, cover, clearCover)
if (ok) playlistDetail = Krespot.collections.collection(context, uri)
return ok
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onEditPlaylistHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String) {
playlistEditDraft = null
scope.launch {
    val loaded = if (uri == playlistUri) playlistDetail else null
    playlistEditDraft = buildPlaylistEditDraft(
        uri, loaded, playlistCanRemoveCover,
    )
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onSearchUsersHandler(context: Context, term: String): List<com.quackify.backend.model.SearchHit> {
return Search.section(context, SearchKind.USER, term)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onCreatePlaylistHandler(context: Context, name: String, description: String, cover: ByteArray?): String? {
val uri = Krespot.playlists.create(context, name, "")
log.i("create playlist '$name' ok=${uri != null}")
if (uri != null) {
    val picture = cover?.let { Krespot.playlists.uploadCover(context, uri, it) }
    if (description.isNotBlank() || picture != null) {
        Krespot.playlists.edit(context, uri, name, description, picture)
    }
        Krespot.library.invalidate()
}
return uri
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onCreatePlaylistWithHandler(scope: kotlinx.coroutines.CoroutineScope, track: com.quackify.compose.playlist.TrackItem) {
scope.launch { createPlaylistWithTrack(track.uri) }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onLoadTrackCountHandler(context: Context, playlistUri: String): Int {
return Krespot.playlists.trackCount(context, playlistUri)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onLoadPlaylistFolderHandler(context: Context, trackUri: String, folderUri: String): List<com.quackify.backend.model.PlaylistChoice> {
return Krespot.playlists.editable(context, trackUri, folderUri)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onTogglePlaylistHandler(scope: kotlinx.coroutines.CoroutineScope, choice: com.quackify.backend.model.PlaylistChoice, track: com.quackify.compose.playlist.TrackItem, shouldAdd: Boolean) {
scope.launch {
    togglePlaylistMembership(choice, track.uri, track.title, shouldAdd)
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onPickPlaylistHandler(scope: kotlinx.coroutines.CoroutineScope, choice: com.quackify.backend.model.PlaylistChoice, track: com.quackify.compose.playlist.TrackItem) {
scope.launch { addToPlaylistTarget(choice, track.uri, track.title) }
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onAddToPlaylistHandler(scope: kotlinx.coroutines.CoroutineScope, track: com.quackify.compose.playlist.TrackItem) {
addToPlaylistChoices = null
scope.launch {
    addToPlaylistChoices = fetchEditablePlaylists(track.uri)
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onShowCreditsHandler(scope: kotlinx.coroutines.CoroutineScope, uri: String) {
trackCredits = null
scope.launch {
    trackCredits = fetchCredits(uri)
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onAddTrackHandler(context: Context, trackUri: String): Boolean {
val ok = if (playlistUri == "spotify:collection:tracks") {
    val host = PlaybackHost.bridge
    if (trackUri == host?.state?.trackUri && !host.state.isLiked) {
        host.listener.onLikeToggle()
        true
    } else {
        Library.setSaved(context, listOf(trackUri), true)
    }
} else {
    Krespot.playlists.addTrack(context, playlistUri, trackUri)
}
log.i("add '$trackUri' -> '$playlistUri' ok=$ok")
if (ok) refreshLoadedPlaylist(playlistUri)
return ok
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onCanvasResolutionHandler(height: Int) {
canvasResolution = height
clientPrefs.edit().putInt("canvasResolution", height).apply()
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onCanvasToggleHandler(bridge: PlaybackBridge, on: Boolean) {
canvasEnabled = on
clientPrefs.edit().putBoolean("canvasNpvEnabled", on).apply()
bridge.setCanvas(on)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onDeleteCommentHandler(context: Context, commentUri: String): Boolean {
return Podcasts.deleteComment(context, commentUri)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onLoadCommentReactionsHandler(context: Context, commentUri: String): com.quackify.backend.model.EpisodeCommentReactions? {
return Podcasts.commentReactions(context, commentUri)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onRemoveCommentReactionHandler(context: Context, commentUri: String): Boolean {
return Podcasts.deleteCommentReaction(context, commentUri)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onCommentReactionHandler(context: Context, commentUri: String, emoji: String): Boolean {
return Podcasts.addCommentReaction(context, commentUri, emoji)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onLoadCommentRepliesHandler(context: Context, commentUri: String, pageToken: String?): com.quackify.backend.model.EpisodeCommentReplies? {
return Podcasts.commentReplies(context, commentUri, pageToken)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal suspend fun MainActivity.onEpisodeCommentHandler(context: Context, text: String): Boolean {
val uri = PlaybackHost.bridge?.state?.trackUri.orEmpty()
if (!uri.startsWith("spotify:episode:")) {
    return false
}
return Podcasts.addEpisodeComment(context, uri, text)
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onLoadMoreAlbumTracksHandler(scope: kotlinx.coroutines.CoroutineScope) {
val current = albumTracks
if (!albumLoadingMore && albumHasMore && current != null) {
    scope.launch {
        albumLoadingMore = true
        val page = loadMoreAlbumTracks(current.size, albumDetail?.imageUrl.orEmpty())
        if (page != null) {
            albumTracks = current + page.first
            albumHasMore = page.second
        }
        albumLoadingMore = false
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onSavePlaylistOrderHandler(scope: kotlinx.coroutines.CoroutineScope, context: Context, uri: String, order: List<com.quackify.compose.playlist.TrackItem>) {
scope.launch {
    val moved = savePlaylistOrderMoves(playlistTracks.orEmpty(), uri, order.map { it.uid })
    if (moved > 0) {
        val detail = Krespot.collections.collection(context, uri)
        playlistDetail = detail
        playlistTracks = detail?.tracks?.map { it.toTrackItem() }
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onAddRecommendedHandler(scope: kotlinx.coroutines.CoroutineScope, track: com.quackify.compose.playlist.TrackItem) {
scope.launch {
    val detail = addRecommendedToPlaylist(track.uri, track.title)
    if (detail != null) {
        playlistDetail = detail
        playlistTracks = detail.tracks.map { it.toTrackItem() }
    }
}
}


/** Extracted from the EncoreAppShell wiring to keep the composable
 * method under the JVM 64KB limit. */
internal fun MainActivity.onLoadMorePlaylistTracksHandler(scope: kotlinx.coroutines.CoroutineScope) {
val current = playlistTracks
if (!playlistLoadingMore && playlistHasMore && current != null) {
    scope.launch {
        playlistLoadingMore = true
        val page = loadMorePlaylistTracks(current.size)
        if (page != null) {
            playlistTracks = current + page.first
            playlistHasMore = page.second
        }
        playlistLoadingMore = false
    }
}
}

/** Pure mapping helper extracted from the EncoreAppShell wiring. */
internal fun MainActivity.mapNowPlayingLyrics(nowPlayingLyrics: TrackLyrics?): List<TimeSyncedLyric> {
    val lyrics = nowPlayingLyrics
    return lyrics?.lines?.mapIndexed { index, line ->
        val timings = lyrics.wordTimings.getOrNull(index)
        TimeSyncedLyric(
            startTimeMs = line.startTimeMs,
            text = line.text,
            words = timings?.words?.map { word ->
                LyricWordSpan(
                    startMs = word.startMs,
                    endMs = word.endMs,
                    text = word.text,
                    syllables = word.syllables.map { s ->
                        LyricSyllableSpan(s.startMs, s.endMs, s.startChar, s.endChar)
                    },
                )
            }.orEmpty(),
            sungUntilMs = timings?.sungUntilMs,
            translation = line.translation,
        )
    }.orEmpty()
}


/** Pure mapping helper extracted from the EncoreAppShell wiring. */
internal fun MainActivity.mapNowPlayingTranscript(nowPlayingTranscript: EpisodeTranscript?): List<TranscriptLine> {
    return nowPlayingTranscript?.lines?.map { line ->
        TranscriptLine(
            startMs = line.startMs,
            text = line.text,
            speaker = line.speaker,
            words = line.words.map { span ->
                TranscriptWordSpan(span.startMs, span.startChar, span.endChar)
            },
        )
    }.orEmpty()
}


/** Pure mapping helper extracted from the EncoreAppShell wiring. */
internal fun MainActivity.mapSearchResults(searchHits: List<SearchHit>): List<TrackItem> {
    return searchHits.map { hit ->
            com.quackify.compose.playlist.TrackItem(
                title = hit.title,
                artist = hit.subtitle,
                uri = hit.uri,
                imageUrl = hit.imageUrl,
            )
        }
}


/** Pure mapping helper extracted from the EncoreAppShell wiring. */
internal fun MainActivity.mapSearchSuggestions(searchHits: List<SearchHit>): List<TrackItem> {
    return searchHits.map { hit ->
            TrackItem(
                title = hit.title,
                artist = hit.subtitle,
                uri = hit.uri,
                imageUrl = hit.imageUrl,
            )
        }
}


/** Pure mapping helper extracted from the EncoreAppShell wiring. */
internal fun MainActivity.mapSearchRecent(recents: List<RecentSearches.Entry>): List<SearchHistoryItem> {
    return recents.map { entry ->
            SearchHistoryItem(
                title = entry.title,
                subtitle = entry.subtitle,
                kind = runCatching { SearchKind.valueOf(entry.kind) }
                    .getOrDefault(SearchKind.QUERY),
                colorHex = entry.colorHex,
                uri = entry.uri,
                imageUrl = entry.imageUrl,
            )
        }
}

/** Extracted so the composable stays under the JVM 64KB method cap. */
private fun buildActivityFeedState(activity: ActivityFeedController): ActivityFeedState {
    return ActivityFeedState(
        feed = activity.feed,
        loading = activity.loading,
        failed = activity.failed,
        self = activity.self,
        unread = activity.unread,
        onOpen = activity::open,
        onRetry = activity::open,
        onLeave = activity::leave,
        onDrawerOpened = activity::refreshUnread,
    )
}


























































