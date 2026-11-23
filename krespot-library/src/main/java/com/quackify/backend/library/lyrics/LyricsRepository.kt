package com.quackify.backend.library.lyrics

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.store.StreamSource
import com.quackify.backend.model.TrackLyrics
import com.quackify.backend.model.WordTiming
import com.quackify.backend.model.WordTimings
import com.quackify.backend.model.SyllableTiming
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val log = Logs.of(Logs.Module.LYRICS)

object LyricsRepository {

    data class Result(
        val source: LyricsProvider,
        val lines: List<SyncedLyricLine>,
    )

    suspend fun lyrics(
        context: Context,
        trackUri: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long,
        sources: Set<LyricsProvider> = LyricsProvider.offered.toSet(),
        order: List<LyricsProvider> = LyricsProvider.offered,
        prioritizeWordSynced: Boolean = true,
        isrc: String? = null,
        stream: StreamSource = StreamSource.SPOTIFY,
        videoId: String = "",
    ): Result? = coroutineScope {
        if (!trackUri.startsWith("spotify:track:")) return@coroutineScope null
        val sequence = LyricsProvider.orderedFor(order, stream).filter { it in sources }
        if (sequence.isEmpty()) return@coroutineScope null

        val searchTitle = title.forLyricsSearch()
        val searchArtist = artist.artistForLyricsSearch()

        val racing = sequence.map { provider ->
            provider to async(Dispatchers.IO) {
                runCatching {
                    fetch(context, provider, trackUri, searchTitle, searchArtist, album, durationMs, isrc, videoId)
                }.getOrNull()
            }
        }

        try {
            var lineSynced: Result? = null
            for ((provider, job) in racing) {
                val found = runCatching { job.await() }.getOrNull() ?: continue
                if (found.lines.isEmpty()) continue
                if (found.lines.any { it.isWordSynced }) return@coroutineScope found
                if (!prioritizeWordSynced) return@coroutineScope found
                if (lineSynced == null) lineSynced = found
            }
            lineSynced
        } finally {
            racing.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetch(
        context: Context,
        provider: LyricsProvider,
        trackUri: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        isrc: String?,
        videoId: String,
    ): Result? {
        val lines = when (provider) {
            LyricsProvider.SPOTIFY -> spotifyLines(context, trackUri)
            LyricsProvider.LRCLIB -> LrcLibSource.lyrics(context, title, artist, durationMs)
            LyricsProvider.MUSIXMATCH -> MusixmatchSource.lyrics(title, artist, durationMs)
            LyricsProvider.LRC_RED -> LrcRedSource.lyrics(title, artist, durationMs, isrc)
            LyricsProvider.YT_MUSIC -> YtMusicLyricsSource.lyrics(context, videoId)
            LyricsProvider.SIMPMUSIC -> SimpMusicSource.lyrics(videoId, durationMs)
            LyricsProvider.BETTER_LYRICS -> BetterLyricsSource.lyrics(title, artist, durationMs, album)
            LyricsProvider.LYRICS_PLUS -> LyricsPlusSource.lyrics(title, artist, durationMs, album, isrc)
            LyricsProvider.KUGOU -> KuGouSource.lyrics(title, artist, durationMs, album)
        } ?: return null
        return Result(provider, lines)
    }

    private suspend fun spotifyLines(context: Context, trackUri: String): List<SyncedLyricLine>? {
        val trackLyrics = com.quackify.backend.library.LyricsSource.lyrics(context, trackUri)
            ?: return null
        return trackLyrics.lines.map { line ->
            SyncedLyricLine(timeMs = line.startTimeMs, text = line.text)
        }
    }

    suspend fun asTrackLyrics(result: Result): TrackLyrics = withContext(Dispatchers.Default) {
        TrackLyrics(
            lines = result.lines.map { line ->
                com.quackify.backend.model.LyricLine(
                    startTimeMs = line.timeMs,
                    text = line.text,
                )
            },
            provider = result.source.label,
            synced = true,
            wordTimings = result.lines.map { line ->
                WordTimings(
                    words = line.words.map { word ->
                        WordTiming(
                            startMs = word.startMs,
                            endMs = word.endMs,
                            text = word.text,
                            syllables = word.syllables.map { s ->
                                SyllableTiming(s.startMs, s.endMs, s.startChar, s.endChar)
                            },
                        )
                    },
                    sungUntilMs = line.sungUntilMs,
                )
            },
        )
    }
}
