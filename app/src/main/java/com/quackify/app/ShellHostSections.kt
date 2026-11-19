package com.quackify.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.quackify.backend.api.Library
import com.quackify.backend.api.Podcasts
import com.quackify.backend.api.Search
import com.quackify.backend.library.Krespot
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.EpisodeComments
import com.quackify.backend.model.EpisodeTranscript
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.ShowDetail
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
import com.quackify.backend.core.log.Logs
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
        LyricsOptions.translationEnabled,
        LyricsOptions.translationLanguage,
        LyricsOptions.prioritizeWordSynced,
        LyricsOptions.providerOrder,
    ) {
        val uri = playback?.trackUri.orEmpty()
        if (!uri.startsWith("spotify:track:")) {
            writers.setLyrics(null)
            return@LaunchedEffect
        }
        val bridge = PlaybackHost.bridge
        val duration = bridge?.progressSnapshot?.durationMs ?: 0L
        val repository = Krespot.lyricsRepository
        val result = repository.lyrics(
            context = this@rememberNowPlayingData,
            trackUri = uri,
            title = playback?.title.orEmpty(),
            artist = playback?.artist.orEmpty(),
            durationMs = duration,
            sources = LyricsOptions.providerOrder.toSet(),
            order = LyricsOptions.providerOrder,
            prioritizeWordSynced = LyricsOptions.prioritizeWordSynced,
        )
        var trackLyrics = if (result != null) {
            repository.asTrackLyrics(result)
        } else {
            Krespot.lyrics.lyrics(this@rememberNowPlayingData, uri)
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
