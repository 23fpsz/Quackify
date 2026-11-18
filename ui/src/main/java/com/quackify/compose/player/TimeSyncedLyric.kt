package com.quackify.compose.player

/**
 */
data class TimeSyncedLyric(
    val startTimeMs: Long,
    val text: String,
    val words: List<LyricWordSpan> = emptyList(),
    val sungUntilMs: Long? = null,
) {
    val isWordSynced: Boolean get() = words.isNotEmpty()

    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= startTimeMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) return start + word.charsSungAt(positionMs)
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }
}

data class LyricWordSpan(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val syllables: List<LyricSyllableSpan> = emptyList(),
) {
    fun charsSungAt(positionMs: Long): Float {
        if (positionMs <= startMs) return 0f
        if (positionMs >= endMs) return text.length.toFloat()
        if (syllables.isEmpty()) {
            val span = (endMs - startMs).coerceAtLeast(1L)
            return (positionMs - startMs).toFloat() / span * text.length
        }
        for (syllable in syllables) {
            if (positionMs < syllable.startMs) return syllable.startChar.toFloat()
            if (positionMs < syllable.endMs) {
                val span = (syllable.endMs - syllable.startMs).coerceAtLeast(1L)
                val through = (positionMs - syllable.startMs).toFloat() / span
                return syllable.startChar + through * (syllable.endChar - syllable.startChar)
            }
        }
        return text.length.toFloat()
    }
}

data class LyricSyllableSpan(
    val startMs: Long,
    val endMs: Long,
    val startChar: Int,
    val endChar: Int,
)

val defaultLyricsList = listOf(
    TimeSyncedLyric(0L, "I'm tryna put you in the worst mood, ah"),
    TimeSyncedLyric(4000L, "P1 cleaner than your church shoes, ah"),
    TimeSyncedLyric(8000L, "Milli point two just to hurt you, ah"),
    TimeSyncedLyric(12000L, "All red Lamb' leanin' on you, ah"),
    TimeSyncedLyric(16000L, "None of these toys on a lease too, ah"),
    TimeSyncedLyric(20000L, "Made your whole year in a week too, yah"),
    TimeSyncedLyric(24000L, "Main bitch out your league too, ah"),
    TimeSyncedLyric(28000L, "Side bitch out of your league too, ah"),
    TimeSyncedLyric(32000L, "House so empty need a centerpiece"),
    TimeSyncedLyric(36000L, "20 racks a table cut from ebony"),
    TimeSyncedLyric(40000L, "Cut that ivory into skinny pieces"),
    TimeSyncedLyric(44000L, "Then she clean it with her face, man I love my baby"),
    TimeSyncedLyric(48000L, "Look what you've done"),
    TimeSyncedLyric(52000L, "I'm a motherfuckin' starboy"),
    TimeSyncedLyric(56000L, "Look what you've done"),
    TimeSyncedLyric(60000L, "I'm a motherfuckin' starboy")
)
