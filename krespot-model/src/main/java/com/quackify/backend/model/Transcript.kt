/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |
 *                     |_|
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.model

data class EpisodeTranscript(
    val language: String,
    val showName: String,
    val episodeName: String,
    val synced: Boolean,
    val lines: List<TranscriptLine>,
)

data class TranscriptLine(
    val startMs: Long,
    val text: String,
    val speaker: Boolean,
    val words: List<TranscriptWordSpan>,
)

data class TranscriptWordSpan(
    val startMs: Long,
    val startChar: Int,
    val endChar: Int,
)
