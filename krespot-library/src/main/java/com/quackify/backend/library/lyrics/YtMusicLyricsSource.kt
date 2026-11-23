package com.quackify.backend.library.lyrics

import android.content.Context
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.YTM)

object YtMusicLyricsSource {

    private const val MUSIC_HOST = "https://music.youtube.com/youtubei/v1"

    private const val WEB_REMIX_VERSION = "1.20250101.01.00"

    private const val ANDROID_MUSIC_VERSION = "7.21.50"

    private const val CACHE_ENTRIES = 24

    private data class Page(val browseId: String, val params: String)

    private val pageMutex = Mutex()

    private val pageFor = LinkedHashMap<String, Page>()

    suspend fun lyrics(context: Context, videoId: String): List<SyncedLyricLine>? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        val page = rememberPage(videoId) ?: lyricsPage(videoId)?.also { rememberPage(videoId, it) }
            ?: return@withContext null
        timed(page, videoId) ?: plain(page)
    }

    /**
     * The lyrics tab is answered with per-line cues only, so the sweep falls
     * back to whole-line highlighting here; what it buys is the guarantee the
     * words belong to the exact video that is playing.
     */
    private suspend fun timed(page: Page, videoId: String): List<SyncedLyricLine>? {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "ANDROID_MUSIC")
                .put("clientVersion", ANDROID_MUSIC_VERSION)
                .put("hl", "en")
                .put("gl", "US")))
            .put("browseId", page.browseId)
            .apply { if (page.params.isNotBlank()) put("params", page.params) }
            .toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to "com.google.android.apps.youtube.music/$ANDROID_MUSIC_VERSION (Linux; U; Android 13) gzip",
        )
        val response = Net.postJson("$MUSIC_HOST/browse?prettyPrint=false", body, headers, tag = "ytm-lyrics")
        val json = response?.takeIf { it.ok }?.json() ?: run {
            log.w("timed lyrics browse refused ${response?.code ?: "no response"}")
            return null
        }
        val rows = findArray(json, "timedLyricsData") ?: return null
        val lines = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val text = row.optString("lyricLine").trim()
            if (text.isEmpty()) return@mapNotNull null
            val cue = row.optJSONObject("cueRange") ?: return@mapNotNull null
            val start = cue.text("startTimeMilliseconds").toLongOrNull() ?: return@mapNotNull null
            val end = cue.text("endTimeMilliseconds").toLongOrNull()
            SyncedLyricLine(timeMs = start, text = text, sungUntilMs = end?.takeIf { it > start })
        }
        if (lines.isEmpty()) return null
        log.i("timed lyrics for '$videoId': ${lines.size} lines")
        return lines.sortedBy { it.timeMs }
    }

    /** The untimed description shelf the web client is served instead. */
    private suspend fun plain(page: Page): List<SyncedLyricLine>? {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", WEB_REMIX_VERSION)
                .put("hl", "en")
                .put("gl", "US")))
            .put("browseId", page.browseId)
            .apply { if (page.params.isNotBlank()) put("params", page.params) }
            .toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to Net.DESKTOP_UA,
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "X-YouTube-Client-Name" to "67",
            "X-YouTube-Client-Version" to WEB_REMIX_VERSION,
        )
        val response = Net.postJson("$MUSIC_HOST/browse?prettyPrint=false", body, headers, tag = "ytm-lyrics")
        val json = response?.takeIf { it.ok }?.json() ?: return null
        val shelf = objectsNamed(json, "musicDescriptionShelfRenderer").firstOrNull() ?: return null
        val runs = shelf.optJSONObject("description")?.optJSONArray("runs") ?: return null
        val lines = (0 until runs.length()).mapNotNull { index ->
            runs.optJSONObject(index)?.text("text")?.trim()?.takeIf { it.isNotEmpty() }
        }.map { SyncedLyricLine(timeMs = 0L, text = it) }
        return lines.takeIf { it.isNotEmpty() }
    }

    /** Where the lyrics tab lives for this video, out of the watch page's tabs. */
    private suspend fun lyricsPage(videoId: String): Page? {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", WEB_REMIX_VERSION)
                .put("hl", "en")
                .put("gl", "US")))
            .put("videoId", videoId)
            .toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to Net.DESKTOP_UA,
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "X-YouTube-Client-Name" to "67",
            "X-YouTube-Client-Version" to WEB_REMIX_VERSION,
        )
        val response = Net.postJson("$MUSIC_HOST/next?prettyPrint=false", body, headers, tag = "ytm-lyrics")
        val json = response?.takeIf { it.ok }?.json() ?: run {
            log.w("watch page refused ${response?.code ?: "no response"}")
            return null
        }
        val tabs = objectsNamed(json, "tabRenderer").toList()
        val endpoint = tabs.firstOrNull { tab ->
            stringsIn(tab).any { it.equals("Lyrics", ignoreCase = true) }
        }?.let { tab -> objectsNamed(tab, "browseEndpoint").firstOrNull() }
            ?: tabs.drop(1).firstNotNullOfOrNull { tab -> objectsNamed(tab, "browseEndpoint").firstOrNull() }
            ?: return null
        val browseId = endpoint.text("browseId")
        if (browseId.isBlank()) return null
        return Page(browseId, endpoint.text("params"))
    }

    private suspend fun rememberPage(videoId: String, page: Page) = pageMutex.withLock {
        pageFor.remove(videoId)
        pageFor[videoId] = page
        while (pageFor.size > CACHE_ENTRIES) pageFor.remove(pageFor.keys.first())
    }

    private fun rememberPage(videoId: String): Page? = pageFor[videoId]

    private fun findArray(node: Any?, name: String): JSONArray? = when (node) {
        is JSONObject -> node.optJSONArray(name)
            ?: node.keys().asSequence().mapNotNull { findArray(node.opt(it), name) }.firstOrNull()
        is JSONArray -> (0 until node.length()).asSequence()
            .mapNotNull { findArray(node.opt(it), name) }.firstOrNull()
        else -> null
    }

    private fun objectsNamed(node: Any, name: String): Sequence<JSONObject> = sequence {
        when (node) {
            is JSONObject -> {
                val direct = node.optJSONObject(name)
                if (direct != null) yield(direct)
                node.keys().forEach { key -> yieldAll(objectsNamed(node.opt(key) ?: return@forEach, name)) }
            }
            is JSONArray -> for (index in 0 until node.length()) {
                val item = node.opt(index) ?: continue
                yieldAll(objectsNamed(item, name))
            }
        }
    }

    private fun stringsIn(node: Any): List<String> = when (node) {
        is JSONObject -> {
            val direct = node.optString("text").takeIf { it.isNotEmpty() }
                ?: node.optString("simpleText").takeIf { it.isNotEmpty() }
            listOfNotNull(direct) + node.keys().asSequence().flatMap { key ->
                node.opt(key)?.let { stringsIn(it) } ?: emptyList()
            }
        }
        is JSONArray -> (0 until node.length()).flatMap { index ->
            node.opt(index)?.let { stringsIn(it) } ?: emptyList()
        }
        else -> emptyList()
    }

    private fun JSONObject.text(name: String): String =
        if (isNull(name)) "" else optString(name, "")
}
