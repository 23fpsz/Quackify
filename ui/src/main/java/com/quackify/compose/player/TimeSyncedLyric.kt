package com.quackify.compose.player

data class TimeSyncedLyric(
    val startTimeMs: Long,
    val text: String,
    val words: List<LyricWordSpan> = emptyList(),
    val sungUntilMs: Long? = null,
    val translation: String = "",
) {
    val isWordSynced: Boolean get() = words.isNotEmpty()

    private val revealCurve: LyricRevealCurve by lazy { LyricRevealCurve.from(text, words) }

    fun revealedChars(positionMs: Long): Float {
        if (words.isEmpty()) return if (positionMs >= startTimeMs) text.length.toFloat() else 0f
        return revealCurve.charsAt(positionMs)
    }
}

/**
 * Word-synced reveal as one monotone cubic Hermite curve through the word and
 * syllable timings (Fritsch-Carlson tangents). A piecewise-linear reveal
 * changes speed abruptly at every word boundary, which reads as a stutter
 * when the vocal rushes one word and stretches the next; blending the slopes
 * of neighbouring segments keeps the boundary gliding while the curve still
 * passes exactly through each word's own start and end.
 */
internal class LyricRevealCurve private constructor(
    private val times: LongArray,
    private val chars: FloatArray,
    private val tangents: FloatArray,
) {
    fun charsAt(positionMs: Long): Float {
        val n = times.size
        if (n == 0) return 0f
        if (n == 1 || positionMs <= times[0]) return chars[0]
        if (positionMs >= times[n - 1]) return chars[n - 1]
        var low = 0
        var high = n - 2
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (times[mid] <= positionMs) low = mid else high = mid - 1
        }
        val spanMs = (times[low + 1] - times[low]).coerceAtLeast(1L)
        val u = ((positionMs - times[low]).toFloat() / spanMs).coerceIn(0f, 1f)
        val c0 = chars[low]
        val c1 = chars[low + 1]
        val m0 = tangents[low] * spanMs
        val m1 = tangents[low + 1] * spanMs
        val u2 = u * u
        val u3 = u2 * u
        return c0 * (2 * u3 - 3 * u2 + 1) + m0 * (u3 - 2 * u2 + u) +
            c1 * (-2 * u3 + 3 * u2) + m1 * (u3 - u2)
    }

    companion object {
        fun from(text: String, words: List<LyricWordSpan>): LyricRevealCurve {
            val times = ArrayList<Long>()
            val chars = ArrayList<Float>()
            var offset = 0
            words.forEach { word ->
                val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
                val end = start + word.text.length
                if (word.syllables.isEmpty()) {
                    push(times, chars, word.startMs, start.toFloat())
                    push(times, chars, word.endMs, end.toFloat())
                } else {
                    word.syllables.forEach { syllable ->
                        push(times, chars, syllable.startMs, (start + syllable.startChar).toFloat())
                        push(times, chars, syllable.endMs, (start + syllable.endChar).toFloat())
                    }
                }
                offset = end
            }
            if (words.isNotEmpty()) {
                push(times, chars, words.last().endMs, text.length.toFloat())
            }
            return build(times, chars)
        }

        private fun push(times: MutableList<Long>, chars: MutableList<Float>, time: Long, at: Float) {
            if (times.isEmpty() || time > times.last()) {
                times += time
                chars += if (chars.isEmpty()) at else maxOf(chars.last(), at)
            } else if (time == times.last()) {
                chars[chars.size - 1] = maxOf(chars.last(), at)
            }
        }

        private fun build(times: List<Long>, chars: List<Float>): LyricRevealCurve {
            val n = times.size
            val t = LongArray(n) { times[it] }
            val c = FloatArray(n) { chars[it] }
            val m = FloatArray(n)
            if (n < 2) return LyricRevealCurve(t, c, m)
            val slopes = FloatArray(n - 1)
            for (i in 0 until n - 1) {
                slopes[i] = (c[i + 1] - c[i]) / (t[i + 1] - t[i]).coerceAtLeast(1L)
            }
            m[0] = slopes[0]
            m[n - 1] = slopes[n - 2]
            for (i in 1 until n - 1) {
                val a = slopes[i - 1]
                val b = slopes[i]
                m[i] = if (a <= 0f || b <= 0f) 0f else 2f * a * b / (a + b)
            }
            return LyricRevealCurve(t, c, m)
        }
    }
}

data class LyricWordSpan(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val syllables: List<LyricSyllableSpan> = emptyList(),
)

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
