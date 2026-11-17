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
 * You may view and audit this source code for personal and educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.library

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net
import com.quackify.backend.model.EpisodeTranscript
import com.quackify.backend.model.TranscriptLine
import com.quackify.backend.model.TranscriptWordSpan
import org.json.JSONObject

private val log = Logs.of(Logs.Module.TRANSCRIPTS)

object TranscriptSource {

    private const val SPCLIENT = "https://spclient.wg.spotify.com"

    suspend fun transcript(context: Context, episodeUri: String): EpisodeTranscript? {
        val id = episodeUri.substringAfterLast(":", "")
        if (!episodeUri.startsWith("spotify:episode:") || id.isBlank()) return null
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("transcript: no token")
            return null
        }
        val url = "$SPCLIENT/transcript-read-along/v2/episode/$id" +
            "?format=json&maxSentenceLength=500&excludeCC=true"
        val response = Net.get(url, spHeaders(context), "transcript") ?: return null
        if (!response.ok) {
            log.w("transcript $id http ${response.code}")
            return null
        }
        val root = response.json() ?: run {
            log.i("transcript $id none")
            return null
        }
        val sections = root.optJSONArray("section") ?: run {
            log.i("transcript $id none")
            return null
        }
        val lines = (0 until sections.length()).mapNotNull { i ->
            val section = sections.optJSONObject(i) ?: return@mapNotNull null
            parseSection(section)
        }
        if (lines.isEmpty()) return null
        val status = root.optString("timeSyncedStatus")
        val transcript = EpisodeTranscript(
            language = root.optString("language"),
            showName = root.optString("showName"),
            episodeName = root.optString("episodeName"),
            synced = status.contains("SYNCED", ignoreCase = true),
            lines = lines,
        )
        log.i("transcript $id lines=${lines.size} sentences=${lines.count { !it.speaker }} synced=${transcript.synced}")
        return transcript
    }

    private fun parseSection(section: JSONObject): TranscriptLine? {
        val startMs = section.optLong("startMs", 0L)
        val title = section.optJSONObject("title")?.optString("title").orEmpty().trim()
        if (section.has("title")) {
            if (title.isEmpty()) return null
            return TranscriptLine(startMs = startMs, text = title, speaker = true, words = emptyList())
        }
        val music = section.optJSONObject("musicClosedCaption")
        if (music != null) {
            val text = music.optString("text").trim()
            if (text.isEmpty()) return null
            return TranscriptLine(startMs = startMs, text = text, speaker = false, words = emptyList())
        }
        val sentence = section.optJSONObject("text")?.optJSONObject("sentence")
            ?: section.optJSONObject("fallback")?.optJSONObject("sentence")
            ?: return null
        return parseSentence(startMs, sentence)
    }

    private fun parseSentence(startMs: Long, sentence: JSONObject): TranscriptLine? {
        val text = sentence.optString("text")
        if (text.isBlank()) return null
        val highlights = sentence.optJSONArray("highlight")
        val words = mutableListOf<TranscriptWordSpan>()
        var cursor = 0
        if (highlights != null) {
            for (i in 0 until highlights.length()) {
                val span = highlights.optJSONObject(i) ?: continue
                val numChars = span.optInt("numChars", 0)
                if (numChars <= 0) continue
                val start = cursor.coerceAtMost(text.length)
                val end = (cursor + numChars).coerceAtMost(text.length)
                if (end > start) {
                    words += TranscriptWordSpan(
                        startMs = span.optLong("startMs", startMs),
                        startChar = start,
                        endChar = end,
                    )
                }
                cursor += numChars
            }
        }
        return TranscriptLine(startMs = startMs, text = text, speaker = false, words = words)
    }

    private suspend fun spHeaders(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }
}
