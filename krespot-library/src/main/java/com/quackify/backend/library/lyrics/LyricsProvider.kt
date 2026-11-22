package com.quackify.backend.library.lyrics

import com.quackify.backend.core.store.StreamSource

enum class LyricsProvider(
    val label: String,
    val wordSynced: Boolean,
    val streams: Set<StreamSource> = setOf(StreamSource.SPOTIFY),
) {
    SPOTIFY(
        label = "Spotify",
        wordSynced = false,
    ),
    LRCLIB(
        label = "LRCLIB",
        wordSynced = false,
    ),
    MUSIXMATCH(
        label = "Musixmatch",
        wordSynced = true,
    ),
    LRC_RED(
        label = "lrc.red",
        wordSynced = true,
    ),
    YT_MUSIC(
        label = "YouTube Music",
        wordSynced = false,
        streams = setOf(StreamSource.YT_MUSIC),
    ),
    SIMPMUSIC(
        label = "SimpMusic",
        wordSynced = true,
        streams = setOf(StreamSource.YT_MUSIC),
    ),
    BETTER_LYRICS(
        label = "BetterLyrics",
        wordSynced = true,
        streams = setOf(StreamSource.YT_MUSIC),
    ),
    LYRICS_PLUS(
        label = "LyricsPlus",
        wordSynced = true,
        streams = setOf(StreamSource.YT_MUSIC),
    ),
    KUGOU(
        label = "KuGou",
        wordSynced = false,
        streams = setOf(StreamSource.YT_MUSIC),
    ),
    ;

    companion object {
        val offered: List<LyricsProvider> = entries.toList()

        fun offeredFor(stream: StreamSource): List<LyricsProvider> = offered.filter { stream in it.streams }

        fun ordered(saved: List<LyricsProvider>): List<LyricsProvider> =
            saved.filter { it in offered }.distinct().let { kept ->
                kept + offered.filterNot { it in kept }
            }

        fun orderedFor(saved: List<LyricsProvider>, stream: StreamSource): List<LyricsProvider> =
            ordered(saved).filter { stream in it.streams }
    }
}
