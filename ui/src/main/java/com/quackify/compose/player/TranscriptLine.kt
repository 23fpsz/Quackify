package com.quackify.compose.player

data class TranscriptLine(
    val startMs: Long,
    val text: String,
    val speaker: Boolean,
    val words: List<TranscriptWordSpan> = emptyList(),
)

data class TranscriptWordSpan(
    val startMs: Long,
    val startChar: Int,
    val endChar: Int,
)
