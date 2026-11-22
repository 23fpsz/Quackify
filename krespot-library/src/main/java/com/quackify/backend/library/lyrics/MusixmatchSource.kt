package com.quackify.backend.library.lyrics

import android.util.Base64
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

object MusixmatchSource {

    private const val BASE = "https://apic.musixmatch.com/ws/1.1"
    private const val APP_ID = "mobile-app-v1.0"
    private const val SEARCH_PAGE = "https://www.musixmatch.com/search"
    private const val FALLBACK_SIGNING_SECRET = "f09016176ba43a1cfd1031fbd6b3d26c"

    private val tokenMutex = Mutex()
    private var cachedSecret: String? = null
    private var cachedToken: String? = null
    private val processGuid = UUID.randomUUID().toString()

    private val client: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): List<SyncedLyricLine>? = withContext(Dispatchers.IO) {
        val seconds = (durationMs / 1000).toInt()
        val track = bestTrack(title, artist, seconds) ?: return@withContext null
        if (track.optInt("has_richsync", 0) != 0) {
            val rich = fetchRichSync(track.optLong("track_id"))
                ?.let(::parseRichSyncBody)
                ?.takeIf { lines -> lines.any { it.isWordSynced } }
            if (rich != null) return@withContext rich
        }
        if (track.optInt("has_subtitles", 0) != 0) {
            val lrc = fetchSubtitle(track.optLong("track_id"))?.let(::subtitleToLrc)
                ?.takeIf { it.isNotBlank() }
            if (lrc != null) return@withContext LrcParser.parse(lrc).takeIf { it.isNotEmpty() }
        }
        null
    }

    private suspend fun bestTrack(title: String, artist: String, seconds: Int): JSONObject? {
        val tracks = searchTrack(title, artist) ?: return null
        return tracks.maxByOrNull { score(it, title, artist, seconds) }
    }

    private fun score(track: JSONObject, title: String, artist: String, seconds: Int): Double {
        var score = 0.0
        val name = track.optString("track_name").trim().lowercase(Locale.ROOT)
        val targetTitle = title.trim().lowercase(Locale.ROOT)
        score += when {
            name == targetTitle -> 80.0
            name.contains(targetTitle) || targetTitle.contains(name) -> 40.0
            else -> 0.0
        }
        if (track.optString("artist_name").trim().lowercase(Locale.ROOT)
                .contains(artist.trim().lowercase(Locale.ROOT))
        ) {
            score += 40.0
        }
        val length = track.optInt("track_length", -1)
        if (length > 0) {
            val diff = abs(length - seconds)
            score += when {
                diff <= 2 -> 30.0
                diff <= 5 -> 15.0
                diff <= 10 -> 5.0
                else -> -20.0
            }
        }
        return score
    }

    private suspend fun searchTrack(title: String, artist: String): List<JSONObject>? {
        val response = signedGet { token ->
            "$BASE/track.search?app_id=$APP_ID&format=json" +
                "&q_track=${encode(title)}&q_artist=${encode(artist)}" +
                "&f_has_lyrics=1&s_track_rating=desc&quorum_factor=1&page_size=10&page=1" +
                "&usertoken=$token"
        } ?: return null
        val body = runCatching { JSONObject(response) }.getOrNull() ?: return null
        val list = body.optJSONObject("message")?.optJSONObject("body")
            ?.optJSONArray("track_list") ?: return null
        return (0 until list.length()).mapNotNull { i ->
            list.optJSONObject(i)?.optJSONObject("track")
        }
    }

    private suspend fun fetchSubtitle(trackId: Long): String? {
        val response = signedGet { token ->
            "$BASE/track.subtitle.get?app_id=$APP_ID&format=json" +
                "&track_id=$trackId&subtitle_format=mxm&usertoken=$token"
        } ?: return null
        return runCatching { JSONObject(response) }.getOrNull()
            ?.optJSONObject("message")?.optJSONObject("body")
            ?.optJSONObject("subtitle")?.optString("subtitle_body")
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchRichSync(trackId: Long): String? {
        val response = signedGet { token ->
            "$BASE/track.richsync.get?app_id=$APP_ID&format=json" +
                "&track_id=$trackId&usertoken=$token"
        } ?: return null
        return runCatching { JSONObject(response) }.getOrNull()
            ?.optJSONObject("message")?.optJSONObject("body")
            ?.optJSONObject("richsync")?.optString("richsync_body")
            ?.takeIf { it.isNotBlank() }
    }

    fun parseRichSyncBody(body: String): List<SyncedLyricLine> {
        val entries = runCatching {
            org.json.JSONArray(body)
        }.getOrNull() ?: return emptyList()
        val lines = (0 until entries.length()).mapNotNull { i ->
            val entry = entries.optJSONObject(i) ?: return@mapNotNull null
            val lineStart = secondsToMs(entry.optDouble("ts", 0.0))
            val lineEnd = maxOf(lineStart, secondsToMs(entry.optDouble("te", 0.0)))
            var previousStart = lineStart
            val fragments = entry.optJSONArray("l") ?: return@mapNotNull null
            val runs = ArrayList<TimedRun>(fragments.length())
            for (j in 0 until fragments.length()) {
                val fragment = fragments.optJSONObject(j) ?: continue
                val text = fragment.optString("c")
                if (text.isEmpty()) continue
                val start = maxOf(
                    lineStart,
                    previousStart,
                    secondsToMs(entry.optDouble("ts", 0.0) + fragment.optDouble("o", 0.0)),
                )
                val next = fragments.optJSONObject(j + 1)
                    ?.let { secondsToMs(entry.optDouble("ts", 0.0) + it.optDouble("o", 0.0)) }
                    ?: lineEnd
                previousStart = start
                runs += TimedRun(start, maxOf(start, minOf(lineEnd, next)), text)
            }
            val words = wordsFromRuns(runs, spacingIsExplicit = true)
            val text = entry.optString("x").trim().ifEmpty { words.joinToString(" ") { it.text } }
            if (text.isEmpty()) return@mapNotNull null
            SyncedLyricLine(
                timeMs = minOf(lineStart, words.firstOrNull()?.startMs ?: lineStart),
                text = text,
                words = words,
                sungUntilMs = lineEnd.takeIf { it > lineStart },
            )
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun subtitleToLrc(subtitleBody: String): String {
        val lines = runCatching { org.json.JSONArray(subtitleBody) }.getOrNull() ?: return ""
        return buildString {
            for (i in 0 until lines.length()) {
                val line = lines.optJSONObject(i) ?: continue
                val text = line.optString("text")
                if (text.isBlank()) continue
                val totalMs = secondsToMs(line.optJSONObject("time")?.optDouble("total", 0.0) ?: 0.0)
                appendLine(
                    "[%02d:%02d.%03d]".format(
                        Locale.US,
                        totalMs / 1000 / 60,
                        (totalMs / 1000) % 60,
                        totalMs % 1000,
                    ) + text,
                )
            }
        }.trim()
    }

    private fun secondsToMs(seconds: Double): Long = (seconds * 1_000.0).toLong()

    private suspend fun signedGet(buildUrl: (token: String) -> String): String? {
        val secret = getSecret()
        val token = getToken(secret) ?: return null
        val first = apiGet(sign(buildUrl(token), secret))
        if (first != null && !looksUnauthorized(first)) return first
        synchronized(this) { cachedToken = null; cachedSecret = null }
        val freshSecret = getSecret()
        val freshToken = getToken(freshSecret) ?: return null
        return apiGet(sign(buildUrl(freshToken), freshSecret))
    }

    private fun looksUnauthorized(body: String): Boolean =
        runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("message")?.optJSONObject("header")
            ?.optInt("status_code", 0)
            ?.let { it == 401 || it == 402 } ?: false

    private suspend fun getToken(secret: String): String? = cachedToken ?: tokenMutex.withLock {
        cachedToken ?: fetchToken(secret)?.also {
            synchronized(this) { cachedToken = it }
        }
    }

    private fun fetchToken(secret: String): String? {
        val url = "$BASE/token.get?app_id=$APP_ID&guid=$processGuid&format=json"
        val body = apiGet(sign(url, secret)) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("message")?.optJSONObject("body")?.optString("user_token")
            ?.takeIf { it.isNotBlank() }
    }

    private fun getSecret(): String = cachedSecret ?: runCatching {
        val page = browserGet(SEARCH_PAGE, "text/html,application/xhtml+xml")
            ?: error("search page unavailable")
        val script = APP_SCRIPT.find(page)?.groupValues?.get(1)
            ?: error("application script not found")
        val scriptUrl = java.net.URI(SEARCH_PAGE).resolve(script).toString()
        val javascript = browserGet(scriptUrl, "*/*") ?: error("application script unavailable")
        val encoded = ENCODED_SECRET.find(javascript)?.groupValues?.get(1)
            ?: error("signing key not found")
        String(Base64.decode(encoded.reversed(), Base64.DEFAULT), Charsets.UTF_8)
            .takeIf { it.isNotBlank() }
            ?: error("empty signing key")
    }.getOrElse { FALLBACK_SIGNING_SECRET }.also {
        synchronized(this) { cachedSecret = it }
    }

    private fun sign(url: String, secret: String): String {
        val normalized = url.replace("%20", "+").replace(" ", "+")
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal("$normalized$date".toByteArray(Charsets.UTF_8))
        val signature = Base64.encodeToString(raw, Base64.NO_WRAP)
        return "$normalized&signature=${URLEncoder.encode(signature, "UTF-8")}" +
            "&signature_protocol=sha256"
    }

    private fun apiGet(url: String): String? = request(url, "application/json, text/plain, */*")

    private fun browserGet(url: String, accept: String): String? =
        request(url, accept, cookie = "mxm_bab=AB")

    private fun request(url: String, accept: String, cookie: String? = null): String? = runCatching {
        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", Net.DESKTOP_UA)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply { if (cookie != null) header("Cookie", cookie) }
            .build()
        client.newCall(request).execute().use { response: okhttp3.Response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private val APP_SCRIPT = Regex(
        """src=["']([^"']*/_next/static/chunks/pages/_app-[^"']+\.js)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val ENCODED_SECRET = Regex("""from\(\s*["']([^"']+)["']\s*\.split""")
}
