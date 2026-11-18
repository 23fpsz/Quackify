package com.quackify.backend.library.lyrics

object LrcParser {

    private val STAMP = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{2,3})]""")
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>""")

    fun parse(lrc: String): List<SyncedLyricLine> {
        val all = lrc.lineSequence().mapNotNull { line ->
            val match = STAMP.find(line) ?: return@mapNotNull null
            val body = line.substring(match.range.last + 1)
            val text = body.replace(WORD_STAMP, "").trim()
            if (text.isEmpty()) return@mapNotNull null
            SyncedLyricLine(
                timeMs = msOf(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                text = text,
                words = parseWordRuns(body),
            )
        }.sortedBy { it.timeMs }.toList()
        return all
    }

    private fun parseWordRuns(body: String): List<LyricWord> {
        val marks = WORD_STAMP.findAll(body).toList()
        if (marks.size < 2) return emptyList()
        val runs = marks.mapIndexed { index, mark ->
            val until = marks.getOrNull(index + 1)?.range?.first ?: body.length
            msOf(mark.groupValues[1], mark.groupValues[2], mark.groupValues[3]) to
                body.substring(mark.range.last + 1, until)
        }
        return wordsFromRuns(
            runs.mapIndexed { index, (startMs, text) ->
                TimedRun(startMs, runs.getOrNull(index + 1)?.first ?: startMs, text)
            },
        )
    }

    private fun msOf(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = when (fraction.length) {
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> 0L
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }
}
