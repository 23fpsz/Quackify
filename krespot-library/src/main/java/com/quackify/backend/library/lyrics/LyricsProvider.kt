package com.quackify.backend.library.lyrics

enum class LyricsProvider(
    val label: String,
    val wordSynced: Boolean,
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
    ;

    companion object {
        val offered: List<LyricsProvider> = entries.toList()

        fun ordered(saved: List<LyricsProvider>): List<LyricsProvider> =
            saved.filter { it in offered }.distinct().let { kept ->
                kept + offered.filterNot { it in kept }
            }
    }
}
