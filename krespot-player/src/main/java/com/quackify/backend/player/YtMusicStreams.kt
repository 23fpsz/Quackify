package com.quackify.backend.player

import com.quackify.backend.connect.command.CommandTrack
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.YTM)

private const val MUSIC_HOST = "https://music.youtube.com/youtubei/v1"

private const val VIDEO_HOST = "https://www.youtube.com/youtubei/v1"

private const val SEARCH_SONGS = "EgWKAQIIAWoKEAkQChAFEAMQBA=="

private const val WEB_REMIX_VERSION = "1.20250101.01.00"

private const val EMBED_ORIGIN = "https://www.youtube.com/"

private const val PROBE_BYTES = 16 * 1024

private const val DURATION_TOLERANCE_MS = 40_000L

private const val CANDIDATES = 4

private const val VIDEO_CACHE = 32

private const val STREAM_CACHE = 8

private const val URL_TTL_MS = 15 * 60_000L

data class YtStream(
    val videoId: String,
    val url: String,
    val bitrate: Int,
    val mime: String,
    val headers: Map<String, String>,
)

private class YtClient(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val clientNumber: String = "",
    val host: String = VIDEO_HOST,
    val androidSdk: Int = 0,
    val embed: Boolean = false,
    val browserOrigin: Boolean = false,
) {

    fun context(): JSONObject {
        val client = JSONObject()
            .put("clientName", clientName)
            .put("clientVersion", clientVersion)
            .put("hl", "en")
            .put("gl", "US")
        if (androidSdk > 0) client.put("androidSdkVersion", androidSdk)
        val root = JSONObject().put("client", client)
        if (embed) {
            root.put("thirdParty", JSONObject().put("embedUrl", EMBED_ORIGIN))
        }
        return root
    }

    fun requestHeaders(): Map<String, String> {
        val headers = linkedMapOf(
            "Content-Type" to "application/json",
            "User-Agent" to userAgent,
            "Accept-Language" to "en-US,en;q=0.9",
        )
        if (clientNumber.isNotBlank()) {
            headers["X-YouTube-Client-Name"] = clientNumber
            headers["X-YouTube-Client-Version"] = clientVersion
        }
        if (browserOrigin) {
            headers["Origin"] = EMBED_ORIGIN
            headers["Referer"] = "$EMBED_ORIGIN/"
        }
        return headers
    }

    fun mediaHeaders(): Map<String, String> {
        val headers = linkedMapOf("User-Agent" to userAgent)
        if (browserOrigin) {
            headers["Referer"] = "$EMBED_ORIGIN/"
        }
        return headers
    }
}

private val CLIENTS = listOf(
    YtClient(
        clientName = "ANDROID",
        clientVersion = "21.26.364",
        userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 13) gzip",
        clientNumber = "3",
        androidSdk = 34,
    ),
    YtClient(
        clientName = "IOS",
        clientVersion = "21.29.1",
        userAgent = "com.google.ios.youtube/21.29.1 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
        clientNumber = "5",
    ),
)

private class YtCandidate(val videoId: String, val title: String)

internal object YtMusicStreams {

    private val streamFor = LinkedHashMap<String, YtStream>()

    private val expiresAt = HashMap<String, Long>()

    suspend fun resolve(track: CommandTrack): YtStream? {
        cached(track.uri)?.let { return it }
        val candidates = search(track)
        if (candidates.isEmpty()) {
            log.w("no youtube music match for '${track.name.ifBlank { track.uri.takeLast(12) }}'")
            return null
        }
        val ranked = candidates.sortedByDescending { candidate -> titleScore(track.name, candidate.title) }
        for (candidate in ranked.take(CANDIDATES)) {
            val stream = streamOf(track, candidate) ?: continue
            remember(track.uri, stream)
            return stream
        }
        log.w("no stream for '${track.name.ifBlank { track.uri.takeLast(12) }}' after ${candidates.size} candidates")
        return null
    }

    private fun cached(trackUri: String): YtStream? = synchronized(streamFor) {
        if (expiresAt[trackUri]?.let { it > System.currentTimeMillis() } != true) return null
        return streamFor[trackUri]
    }

    private fun remember(trackUri: String, stream: YtStream) {
        synchronized(streamFor) {
            streamFor.remove(trackUri)
            streamFor[trackUri] = stream
            expiresAt[trackUri] = System.currentTimeMillis() + URL_TTL_MS
            while (streamFor.size > STREAM_CACHE) {
                val oldest = streamFor.keys.firstOrNull() ?: break
                streamFor.remove(oldest)
                expiresAt.remove(oldest)
            }
        }
    }

    private suspend fun search(track: CommandTrack): List<YtCandidate> {
        val query = buildString {
            append(track.name)
            if (track.artists.isNotBlank() && !track.artists.contains(track.name)) {
                append(' ').append(track.artists)
            }
        }.trim()
        if (query.isBlank()) return emptyList()
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", WEB_REMIX_VERSION)
                        .put("hl", "en")
                        .put("gl", "US"),
                ),
            )
            .put("query", query)
            .put("params", SEARCH_SONGS)
        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to Net.DESKTOP_UA,
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "X-YouTube-Client-Name" to "67",
            "X-YouTube-Client-Version" to WEB_REMIX_VERSION,
        )
        val response = Net.postJson("$MUSIC_HOST/search?prettyPrint=false", body.toString(), headers, tag = "ytm-search")
        val json = response?.takeIf { it.ok }?.json()
        if (json == null) {
            log.w("search failed ${response?.code ?: "no response"}")
            return emptyList()
        }
        val parsed = ArrayList<YtCandidate>()
        val seen = HashSet<String>()
        scanResults(json.opt("contents"), parsed, seen)
        log.i("search '${query.take(48)}' -> ${parsed.size} songs")
        return parsed
    }

    private fun scanResults(node: Any?, out: MutableList<YtCandidate>, seen: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                val item = node.optJSONObject("musicResponsiveListItemRenderer")
                if (item != null) {
                    val videoId = item.optJSONObject("playlistItemData")?.text("videoId").orEmpty()
                    if (videoId.isNotBlank() && seen.add(videoId)) {
                        val title = columnText(item.optJSONArray("flexColumns"), 0)
                        out.add(YtCandidate(videoId, title))
                    }
                }
                for (key in node.keys()) scanResults(node.opt(key), out, seen)
            }
            is JSONArray -> for (index in 0 until node.length()) scanResults(node.opt(index), out, seen)
        }
    }

    private fun columnText(columns: JSONArray?, index: Int): String {
        val column = columns?.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?: return ""
        val parts = (0 until column.length()).mapNotNull { index ->
            column.optJSONObject(index)?.text("text").orEmpty().takeIf { it.isNotBlank() }
        }
        return parts.joinToString(" ").replace(Regex("\\s*•\\s*"), " ").trim()
    }

    private fun titleScore(trackName: String, candidateTitle: String): Int {
        val wanted = normalize(trackName)
        val have = normalize(candidateTitle)
        if (wanted.isBlank() || have.isBlank()) return 0
        if (wanted == have) return 3
        if (have.contains(wanted) || wanted.contains(have)) return 2
        return 0
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()

    private suspend fun streamOf(track: CommandTrack, candidate: YtCandidate): YtStream? {
        for (client in CLIENTS) {
            val (lengthSeconds, formats) = playerFormats(client, candidate.videoId) ?: continue
            if (lengthSeconds > 0L && track.durationMs > 0L &&
                kotlin.math.abs(lengthSeconds * 1000L - track.durationMs) > DURATION_TOLERANCE_MS
            ) {
                log.w("'${candidate.videoId}' is ${lengthSeconds}s, the track is ${track.durationMs / 1000}s, wrong take")
                return null
            }
            for (format in orderedFormats(formats)) {
                val url = format.text("url")
                if (url.isBlank()) continue
                if (!probe(url, client.mediaHeaders())) {
                    log.w("itag ${format.optInt("itag")} from ${client.clientName} refused the probe")
                    continue
                }
                val bitrate = format.optInt("averageBitrate", format.optInt("bitrate", 0))
                log.i("'${candidate.videoId}' via ${client.clientName} itag=${format.optInt("itag")} " +
                        "${format.text("mimeType")} ${bitrate}b serves"
                )
                return YtStream(candidate.videoId, url, bitrate, format.text("mimeType"), client.mediaHeaders())
            }
        }
        return null
    }

    private suspend fun playerFormats(client: YtClient, videoId: String): Pair<Long, List<JSONObject>>? {
        val body = JSONObject()
            .put("context", client.context())
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val response = Net.postJson("${client.host}/player?prettyPrint=false", body.toString(), client.requestHeaders(), tag = "ytm-player")
        val json = response?.takeIf { it.ok }?.json()
        if (json == null) {
            log.w("${client.clientName} player ${response?.code ?: "no response"}")
            return null
        }
        val status = json.optJSONObject("playabilityStatus")?.text("status").orEmpty()
        if (status != "OK") {
            log.w("${client.clientName} playability $status")
            return null
        }
        val adaptive = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
        if (adaptive == null) {
            log.w("${client.clientName} playability ok but no streaming data")
            return null
        }
        val formats = (0 until adaptive.length()).mapNotNull { adaptive.optJSONObject(it) }
        if (formats.isEmpty()) {
            log.w("${client.clientName} playability ok but no formats")
            return null
        }
        val length = json.optJSONObject("videoDetails")?.text("lengthSeconds")?.toLongOrNull() ?: 0L
        return length to formats
    }

    private fun orderedFormats(formats: List<JSONObject>): List<JSONObject> = formats.asSequence()
        .filter { it.text("mimeType").startsWith("audio/") }
        .filter { it.text("url").isNotBlank() }
        .sortedWith(
            compareBy(
                { !it.text("mimeType").startsWith("audio/mp4") },
                { -it.optInt("averageBitrate", it.optInt("bitrate", 0)) },
            ),
        )
        .toList()

    private suspend fun probe(url: String, headers: Map<String, String>): Boolean {
        val sample = Net.fetch(url, 0, PROBE_BYTES - 1, headers, tag = "ytm-probe")
        return sample != null && sample.isNotEmpty()
    }
}
