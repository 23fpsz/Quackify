package com.quackify.compose.player

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt

@Composable
internal fun WordSyncedLyricText(
    lyric: TimeSyncedLyric,
    positionMs: Long,
    sungColor: Color,
    unsungColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val revealed = if (lyric.isWordSynced) {
        lyric.revealedChars(positionMs).roundToInt()
    } else {
        if (positionMs >= lyric.startTimeMs) lyric.text.length else 0
    }
    Text(
        text = buildAnnotatedString {
            append(lyric.text)
            if (revealed > 0) {
                addStyle(
                    SpanStyle(color = sungColor),
                    0,
                    revealed.coerceAtMost(lyric.text.length),
                )
            }
        },
        style = style.copy(color = unsungColor),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
