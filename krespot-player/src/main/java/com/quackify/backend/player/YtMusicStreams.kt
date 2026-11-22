package com.quackify.backend.player

import android.content.Context
import com.quackify.backend.connect.command.CommandTrack
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.store.AppPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.YTM)

private const val MUSIC_HOST = "https://music.youtube.com/youtubei/v1"

private const val VIDEO_HOST = "https://www.youtube.com/youtubei/v1"

private const val GAPIS_HOST = "https://youtubei.googleapis.com/youtubei/v1"

private const val SEARCH_SONGS = "EgWKAQIIAWoKEAkQChAFEAMQBA=="

private const val WEB_REMIX_VERSION = "1.20250101.01.00"

private const val MUSIC_ORIGIN = "https://music.youtube.com"

/** The content playback nonce alphabet the device clients mint nonces from. */
private const val NONCE_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private const val DURATION_TOLERANCE_MS = 40_000L

private const val CANDIDATES = 4

private const val VIDEO_CACHE = 32

private const val VIDEO_STORE = "quackify.ytm"

private const val VIDEO_INDEX = "video.keys"

private const val STREAM_CACHE = 8

private const val URL_TTL_MS = 15 * 60_000L

private const val CREDIT_EXACT = 40

private const val CREDIT_SHARED = 15

private const val LENGTH_TIGHT = 30

private const val LENGTH_CLOSE = 10

/** Past this many seconds of drift the candidate is a different take. */
private const val LENGTH_LIMIT_SECONDS = 30L

private const val LENGTH_VETO = -1_000

private val ARTIST_SEPARATORS = Regex(
    """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
    RegexOption.IGNORE_CASE,
)

private val TYPE_LABELS = setOf("song", "video", "album", "single", "ep", "artist")

private val DURATION = Regex("""(\d+):(\d{2})""")

private val BRACKETED = Regex("""[(\[]([^()\[\]]*)[)\]]""")

private val FEATURING = Regex("""\b(feat|ft|featuring)\b.*""")

private val NON_ALNUM = Regex("""[^\p{L}\p{N}]""")

private val WHITESPACE = Regex("""\s+""")

private val VERSION_WORDS = setOf(
    "remix", "rmx", "refix", "flip", "bootleg", "mashup", "medley", "live",
    "concert", "unplugged", "acoustic", "instrumental", "karaoke", "vocals",
    "vocal", "acapella", "acappella", "backing", "stems", "stem", "cover",
    "demo", "reprise", "remake", "rework", "extended", "edit", "version",
    "mix", "dub", "vip", "session", "sessions", "sped", "slowed", "reverb",
    "nightcore", "lofi", "orchestral", "symphonic", "part", "pt", "chapter",
)

private val NEUTRAL_SEGMENTS = setOf(
    "albumversion", "originalversion", "originalmix", "singleversion",
    "radioversion", "radioedit", "stereoversion", "monoversion",
    "studioversion", "fullversion", "standardversion", "explicitversion",
    "deluxeversion", "originaltrack",
)

private val TRAILING_TAKES = setOf(
    "instrumental", "karaoke", "acapella", "acappella", "remix", "rmx", "nightcore",
)

private val TRAILING_NOISE = setOf(
    "song", "songs", "video", "audio", "lyrics", "lyric", "official", "full",
    "hd", "hq", "4k", "mp3", "ost", "soundtrack",
)

private fun artistNames(credit: String): List<String> =
    credit.split(ARTIST_SEPARATORS)
        .map { it.trim().lowercase().replace(NON_ALNUM, "") }
        .filter { it.length > 1 }
        .distinct()

data class YtStream(
    val videoId: String,
    override val url: String,
    val bitrate: Int,
    val mime: String,
    override val headers: Map<String, String>,
    override val head: ByteArray,
    override val total: Long,
    /** The video's own length in ms, so callers never have to fall back on the track metadata. */
    val lengthMs: Long,
) : StreamBody

private class YtClient(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val clientNumber: String = "",
    val host: String = VIDEO_HOST,
    val androidSdk: Int = 0,
    /** The site this client runs on, for browser-shaped clients only. */
    val origin: String = "",
    /**
     * Web-shaped clients: they need the visitor's session context, a signature
     * timestamp and a proof-of-origin token before innertube serves them.
     */
    val webFamily: Boolean = false,
    /** Cobalt-style TV clients announce their own user agent in the context. */
    val uaInContext: Boolean = false,
    /**
     * Device clients that ride the googleapis gateway: their player endpoint
     * takes the video id and a content playback nonce as query params.
     */
    val gapis: Boolean = false,
    /**
     * Whether the client can carry a po token. The googleapis gateway serves
     * the visionos player without any attestation at all.
     */
    val usesAtt: Boolean = true,
    val apiFormat: String = "",
    val clientScreen: String = "",
    val platform: String = "",
    val deviceMake: String = "",
    val deviceModel: String = "",
    val osName: String = "",
    val osVersion: String = "",
) {

    fun context(visitorData: String): JSONObject {
        val client = JSONObject()
            .put("clientName", clientName)
            .put("clientVersion", clientVersion)
            .put("hl", "en")
            .put("gl", "US")
        if (androidSdk > 0) client.put("androidSdkVersion", androidSdk)
        if (uaInContext) client.put("userAgent", userAgent)
        if (clientScreen.isNotBlank()) client.put("clientScreen", clientScreen)
        if (platform.isNotBlank()) client.put("platform", platform)
        if (deviceMake.isNotBlank()) client.put("deviceMake", deviceMake)
        if (deviceModel.isNotBlank()) client.put("deviceModel", deviceModel)
        if (osName.isNotBlank()) client.put("osName", osName)
        if (osVersion.isNotBlank()) client.put("osVersion", osVersion)
        if (visitorData.isNotBlank()) client.put("visitorData", visitorData)
        return JSONObject().put("client", client)
    }

    fun requestHeaders(visitorData: String): Map<String, String> {
        val headers = linkedMapOf(
            "Content-Type" to "application/json",
            "User-Agent" to userAgent,
            "Accept-Language" to "en-US,en;q=0.9",
        )
        if (clientNumber.isNotBlank()) {
            headers["X-YouTube-Client-Name"] = clientNumber
            headers["X-YouTube-Client-Version"] = clientVersion
        }
        if (origin.isNotBlank()) {
            headers["Origin"] = origin
            headers["Referer"] = "$origin/"
        }
        if (apiFormat.isNotBlank()) headers["X-Goog-Api-Format-Version"] = apiFormat
        if (webFamily) {
            headers["X-Goog-Api-Format-Version"] = "1"
            if (origin.isNotBlank()) headers["X-Origin"] = origin
            if (visitorData.isNotBlank()) headers["X-Goog-Visitor-Id"] = visitorData
        }
        return headers
    }

    fun mediaHeaders(): Map<String, String> {
        val headers = linkedMapOf("User-Agent" to userAgent)
        if (origin.isNotBlank()) {
            headers["Origin"] = origin
            headers["Referer"] = "$origin/"
        }
        return headers
    }
}

private val CLIENTS = listOf(
    // The googleapis gateway the visionos app rides: no attestation, no
    // signature timestamp, and the audio urls come out direct. It leads the
    // chain so a cold resolve never has to prime the webview attestation.
    YtClient(
        clientName = "VISIONOS",
        clientVersion = "1.04",
        userAgent = "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; US)",
        host = GAPIS_HOST,
        gapis = true,
        usesAtt = false,
        apiFormat = "2",
        clientScreen = "WATCH",
        platform = "MOBILE",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1",
        osName = "visionOS",
        osVersion = "26.6.0.23O770",
    ),
    // The device clients take the content-bound po token in the player request
    // body; with it they hand out direct urls and want no token on the urls.
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
    YtClient(
        clientName = "WEB_REMIX",
        clientVersion = "1.20260707.12.00",
        userAgent = Net.DESKTOP_UA,
        clientNumber = "67",
        host = MUSIC_HOST,
        origin = MUSIC_ORIGIN,
        webFamily = true,
    ),
    YtClient(
        clientName = "TVHTML5_SIMPLY",
        clientVersion = "1.0",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
                "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
        clientNumber = "75",
        host = MUSIC_HOST,
        origin = MUSIC_ORIGIN,
        webFamily = true,
    ),
)

private class YtCandidate(
    val videoId: String,
    val title: String,
    val subtitle: String = "",
    val squareArt: Boolean = true,
) {
    val artists: List<String> by lazy { subtitleArtists() }

    val durationSeconds: Long by lazy {
        subtitle.split('•').lastOrNull()?.trim()?.let(DURATION::matchEntire)?.groupValues
            ?.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    private fun subtitleArtists(): List<String> =
        subtitle.split('•').firstOrNull()?.trim().orEmpty()
            .split(ARTIST_SEPARATORS)
            .map { it.trim().lowercase().replace(NON_ALNUM, "") }
            .filter { it.length > 1 && it !in TYPE_LABELS }
            .distinct()
}

/** A match that served before: the video it landed on and the visitor the search that found it rode. */
private class VideoMatch(val videoId: String, val visitor: String)

/** The search reply: its candidates plus the visitor the server handed out. */
private class SearchMatch(val candidates: List<YtCandidate>, val visitorData: String)

internal object YtMusicStreams {

    private val streamFor = LinkedHashMap<String, YtStream>()

    private val expiresAt = HashMap<String, Long>()

    suspend fun resolve(context: Context, track: CommandTrack): YtStream? {
        cached(track.uri)?.let { return it }
        val remembered = recallVideo(context, track.uri)
        if (remembered != null) {
            val stream = streamOf(context, track, YtCandidate(remembered.videoId, track.name), remembered.visitor)
            if (stream != null) {
                remember(track.uri, stream)
                return stream
            }
            log.w("the remembered video '${remembered.videoId}' stopped serving, searching fresh")
            forgetVideo(context, track.uri)
        }
        val found = search(track)
        if (found.candidates.isEmpty()) {
            log.w("no youtube music match for '${track.name.ifBlank { track.uri.takeLast(12) }}'")
            return null
        }
        val ranked = officialRank(track, found.candidates).ifEmpty {
            log.w("no official audio candidate for '${track.name.ifBlank { track.uri.takeLast(12) }}'")
            return null
        }
        for (candidate in ranked.take(CANDIDATES)) {
            val stream = streamOf(context, track, candidate, found.visitorData) ?: continue
            remember(track.uri, stream)
            rememberVideo(context, track.uri, stream.videoId, found.visitorData)
            return stream
        }
        log.w("no stream for '${track.name.ifBlank { track.uri.takeLast(12) }}' after ${found.candidates.size} candidates")
        return null
    }

    /**
     * The catalogue's own audio for the track: a song row, the same take, the
     * same credit. A music video, a cover by another artist or a sped-up edit
     * is dropped rather than streamed, so the audio lines up with the Spotify
     * master the queue asked for.
     */
    private fun officialRank(track: CommandTrack, candidates: List<YtCandidate>): List<YtCandidate> {
        val wanted = TitleParts.of(track.name)
        val wantedArtists = artistNames(track.artists)
        return candidates.mapNotNull { candidate ->
            if (!candidate.squareArt) return@mapNotNull null
            val have = TitleParts.of(candidate.title)
            if (have.core.isBlank() || wanted.core.isBlank()) return@mapNotNull null
            if (!titlesMatch(wanted, have)) return@mapNotNull null
            if (wanted.versions != have.versions) return@mapNotNull null
            val credit = creditScore(wantedArtists, candidate.artists) ?: return@mapNotNull null
            val length = lengthScore(track.durationMs / 1000, candidate.durationSeconds)
            candidate to (credit + length)
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun titlesMatch(wanted: TitleParts, have: TitleParts): Boolean {
        if (wanted.core == have.core) return true
        if (wanted.core.length <= 3 || have.core.length <= 3) return false
        return wanted.core.contains(have.core) || have.core.contains(wanted.core)
    }

    private fun creditScore(wanted: List<String>, have: List<String>): Int? {
        if (wanted.isEmpty() || have.isEmpty()) return 0
        val shared = wanted.any { w -> have.any { h -> w == h || w.contains(h) || h.contains(w) } }
        if (!shared) return null
        return if (wanted == have) CREDIT_EXACT else CREDIT_SHARED
    }

    private fun lengthScore(wantedSec: Long, haveSec: Long): Int = when {
        wantedSec <= 0L || haveSec <= 0L -> 0
        else -> when (val drift = kotlin.math.abs(wantedSec - haveSec)) {
            in 0..3 -> LENGTH_TIGHT
            in 4..10 -> LENGTH_CLOSE
            else -> if (drift > LENGTH_LIMIT_SECONDS) LENGTH_VETO else 0
        }
    }

    private class TitleParts private constructor(val core: String, val versions: Set<String>) {

        companion object {
            fun of(raw: String): TitleParts {
                var text = raw.lowercase().replace(Regex("[\"“”‘’]"), " ")
                val versions = sortedSetOf<String>()
                repeat(3) {
                    text = text.replace(BRACKETED) { match ->
                        classify(match.groupValues[1], versions)
                        " "
                    }
                }
                text = text.replace(FEATURING, " ")
                var words = text.split(WHITESPACE)
                    .map { it.replace(NON_ALNUM, "") }
                    .filter { it.isNotEmpty() }
                while (words.size > 1) {
                    val last = words.last()
                    if (last in TRAILING_TAKES) {
                        versions += last
                        words = words.dropLast(1)
                    } else if (last in TRAILING_NOISE) {
                        words = words.dropLast(1)
                    } else break
                }
                return TitleParts(words.joinToString(""), versions)
            }

            private fun classify(segment: String, versions: MutableSet<String>) {
                val words = segment.split(WHITESPACE)
                    .map { it.replace(NON_ALNUM, "") }
                    .filter { it.isNotEmpty() }
                if (words.isEmpty() || words.joinToString("") in NEUTRAL_SEGMENTS) return
                val marks = words.filter { it in VERSION_WORDS }
                if (marks.isNotEmpty()) versions += marks
            }
        }
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

    /** Persists the match so a later process start skips the search round trip. */
    private fun rememberVideo(context: Context, uri: String, videoId: String, visitor: String) {
        runCatching {
            val prefs = AppPrefs.prefs(context, VIDEO_STORE)
            val editor = prefs.edit()
                .putString(uri, JSONObject().put("v", videoId).put("vis", visitor).toString())
            val keys = prefs.getString(VIDEO_INDEX, "").orEmpty()
                .split(',').filter { it.isNotBlank() }.toMutableList()
            keys.remove(uri)
            keys.add(uri)
            while (keys.size > VIDEO_CACHE) editor.remove(keys.removeAt(0))
            editor.putString(VIDEO_INDEX, keys.joinToString(","))
            editor.apply()
        }
    }

    private fun recallVideo(context: Context, uri: String): VideoMatch? {
        val raw = runCatching {
            AppPrefs.prefs(context, VIDEO_STORE).getString(uri, null)
        }.getOrNull() ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val videoId = json.optString("v").takeIf { it.isNotBlank() } ?: return null
        return VideoMatch(videoId, json.optString("vis"))
    }

    private fun forgetVideo(context: Context, uri: String) {
        runCatching {
            val prefs = AppPrefs.prefs(context, VIDEO_STORE)
            val keys = prefs.getString(VIDEO_INDEX, "").orEmpty()
                .split(',').filter { it.isNotBlank() }.toMutableList()
            if (!keys.remove(uri)) return
            prefs.edit().remove(uri).putString(VIDEO_INDEX, keys.joinToString(",")).apply()
        }
    }

    private suspend fun search(track: CommandTrack): SearchMatch {
        val query = buildString {
            append(track.name)
            if (track.artists.isNotBlank() && !track.artists.contains(track.name)) {
                append(' ').append(track.artists)
            }
        }.trim()
        if (query.isBlank()) return SearchMatch(emptyList(), "")
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
            return SearchMatch(emptyList(), "")
        }
        val parsed = ArrayList<YtCandidate>()
        val seen = HashSet<String>()
        scanResults(json.opt("contents"), parsed, seen)
        // the reply's visitor rides along: the device clients borrow it when no
        // attestation session is around to provide one of its own
        val visitor = json.optJSONObject("responseContext")?.text("visitorData")
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            .orEmpty()
        log.i("search '${query.take(48)}' -> ${parsed.size} songs")
        return SearchMatch(parsed, visitor)
    }

    private fun scanResults(node: Any?, out: MutableList<YtCandidate>, seen: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                val item = node.optJSONObject("musicResponsiveListItemRenderer")
                if (item != null) {
                    val videoId = item.optJSONObject("playlistItemData")?.text("videoId").orEmpty()
                    if (videoId.isNotBlank() && seen.add(videoId)) {
                        val title = columnText(item.optJSONArray("flexColumns"), 0)
                        val subtitle = columnText(item.optJSONArray("flexColumns"), 1)
                        out.add(YtCandidate(videoId, title, subtitle, squareArt(item)))
                    }
                }
                for (key in node.keys()) scanResults(node.opt(key), out, seen)
            }
            is JSONArray -> for (index in 0 until node.length()) scanResults(node.opt(index), out, seen)
        }
    }

    /**
     * Catalogue songs ship square album art; a music-video upload ships a
     * widescreen still. Missing dimensions count as square so a row is never
     * dropped over a field YouTube left out.
     */
    private fun squareArt(item: JSONObject): Boolean {
        val thumbs = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val last = thumbs?.optJSONObject(thumbs.length() - 1) ?: return true
        val width = last.text("width").toDoubleOrNull() ?: return true
        val height = last.text("height").toDoubleOrNull() ?: return true
        if (width <= 0.0 || height <= 0.0) return true
        return width / height in 0.85..1.15
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
        return parts.joinToString(" ").replace(Regex("\\s*•\\s*"), " • ").trim()
    }

    private suspend fun streamOf(
        context: Context,
        track: CommandTrack,
        candidate: YtCandidate,
        visitor: String,
    ): YtStream? {
        for (client in CLIENTS) {
            val session = if (client.usesAtt) PoTokenGate.session(context) else null
            if (client.webFamily && session == null) continue
            // The player request body carries the content-bound token (minted
            // per video); the stream urls carry the session-bound one instead.
            val contentToken = session?.let { PoTokenGate.videoToken(context, candidate.videoId) }
            val sessionToken = session?.requestToken
            val visitorData = session?.visitorData ?: visitor
            val poToken = contentToken ?: sessionToken?.takeIf { client.webFamily }
            var reply = playerFormats(client, candidate.videoId, visitorData, poToken)
            if (reply == null && client.webFamily && contentToken != null && contentToken != sessionToken) {
                // the surfaces disagree across sources; give the other binding a shot
                reply = playerFormats(client, candidate.videoId, visitorData, sessionToken)
            }
            if (reply == null) continue
            if (reply.lengthSeconds > 0L && track.durationMs > 0L &&
                kotlin.math.abs(reply.lengthSeconds * 1000L - track.durationMs) > DURATION_TOLERANCE_MS
            ) {
                log.w("'${candidate.videoId}' is ${reply.lengthSeconds}s, the track is ${track.durationMs / 1000}s, wrong take")
                return null
            }
            var served = false
            for (format in orderedFormats(reply.formats)) {
                val base = format.text("url")
                if (base.isBlank()) continue
                // the device clients want their urls bare; the web family wants
                // the session-bound token riding the query string
                val urls = ArrayList<String>()
                urls.add(base)
                if (sessionToken != null) urls.add(base.withPot(sessionToken))
                if (contentToken != null && contentToken != sessionToken) urls.add(base.withPot(contentToken))
                var picked: String? = null
                var seeded: Probe? = null
                for (url in urls) {
                    val probed = probe(url, client.mediaHeaders()) ?: continue
                    picked = url
                    seeded = probed
                    break
                }
                if (picked == null || seeded == null) {
                    log.w("itag ${format.optInt("itag")} from ${client.clientName} refused the probe")
                    continue
                }
                served = true
                val bitrate = format.optInt("averageBitrate", format.optInt("bitrate", 0))
                log.i("'${candidate.videoId}' via ${client.clientName} itag=${format.optInt("itag")} " +
                        "${format.text("mimeType")} ${bitrate}b serves"
                )
                return YtStream(
                    candidate.videoId,
                    picked,
                    bitrate,
                    format.text("mimeType"),
                    client.mediaHeaders(),
                    seeded.head,
                    seeded.total,
                    reply.lengthSeconds * 1000L,
                )
            }
            if (!served) {
                log.w("${client.clientName} offered no direct audio url for '${candidate.videoId}'")
            }
        }
        return null
    }

    private fun String.withPot(token: String): String =
        this + (if (contains('?')) "&" else "?") + "pot=$token"

    /** A fresh content playback nonce, the device gateway's request salt. */
    private fun nonce(): String = buildString {
        repeat(12) { append(NONCE_ALPHABET[NONCE_ALPHABET.indices.random()]) }
    }

    private class PlayerReply(val lengthSeconds: Long, val formats: List<JSONObject>)

    private suspend fun playerFormats(
        client: YtClient,
        videoId: String,
        visitorData: String,
        poToken: String?,
    ): PlayerReply? {
        val body = JSONObject()
            .put("context", client.context(visitorData))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        if (client.webFamily) {
            body.put("playbackContext", JSONObject().put("contentPlaybackContext",
                JSONObject().put("signatureTimestamp", SignatureStamp.current())))
        }
        if (poToken != null) {
            body.put("serviceIntegrityDimensions",
                JSONObject().put("poToken", poToken))
        }
        val endpoint = if (client.gapis) {
            // the googleapis gateway wants the video id and a fresh playback
            // nonce up front, both as query params and in the body
            val nonce = nonce()
            body.put("cpn", nonce)
            "${client.host}/player?prettyPrint=false&t=$nonce&id=$videoId"
        } else {
            "${client.host}/player?prettyPrint=false"
        }
        val response = Net.postJson(
            endpoint,
            body.toString(),
            client.requestHeaders(visitorData),
            tag = "ytm-player",
        )
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
        return PlayerReply(length, formats)
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

    private class Probe(val head: ByteArray, val total: Long)

    /**
     * The serving check doubles as the stream seed: it pulls the first buffer
     * chunk and the total length out of the range reply, so playback starts
     * off bytes already in hand instead of re-opening the url.
     */
    private suspend fun probe(url: String, headers: Map<String, String>): Probe? {
        val sample = Net.blob(
            url,
            headers + mapOf("Range" to "bytes=0-${StreamBuffer.CHUNK - 1}"),
            tag = "ytm-probe",
        )?.takeIf { it.ok && it.bytes.isNotEmpty() } ?: return null
        val total = if (sample.code == 206) {
            sample.header("content-range").substringAfter('/').trim().toLongOrNull()
        } else {
            sample.bytes.size.toLong()
        }
        return Probe(sample.bytes, total ?: 0L)
    }
}

/**
 * The signature timestamp the web clients borrow from the web player build;
 * fetched rarely and cached hard because it only moves with player releases.
 */
private object SignatureStamp {

    private const val FRESH_MS = 12 * 60 * 60_000L

    @Volatile
    private var stamp: Int = 0

    @Volatile
    private var fetchedAt: Long = 0L

    private val lock = Mutex()

    suspend fun current(): Int {
        stamp.takeIf { it > 0 && System.currentTimeMillis() - fetchedAt < FRESH_MS }?.let { return it }
        return lock.withLock {
            stamp.takeIf { it > 0 && System.currentTimeMillis() - fetchedAt < FRESH_MS }?.let { return it }
            val found = fetch() ?: stamp
            if (found > 0) {
                stamp = found
                fetchedAt = System.currentTimeMillis()
            }
            found
        }
    }

    private suspend fun fetch(): Int? {
        val api = Net.get(
            "https://www.youtube.com/iframe_api",
            mapOf("User-Agent" to Net.DESKTOP_UA),
            tag = "ytm-sts",
        )?.takeIf { it.ok }?.body ?: run {
            log.w("iframe api refused, no signature timestamp")
            return null
        }
        // the api body escapes forward slashes inside its javascript strings
        val hash = Regex("""/s/player/([a-f0-9]{8})/""").find(api.replace("\\/", "/"))
            ?.groupValues?.getOrNull(1) ?: run {
            log.w("no player hash in the iframe api")
            return null
        }
        val base = Net.get(
            "https://www.youtube.com/s/player/$hash/player_ias.vflset/en_US/base.js",
            mapOf("User-Agent" to Net.DESKTOP_UA),
            tag = "ytm-sts",
        )?.takeIf { it.ok }?.body ?: run {
            log.w("player script $hash refused")
            return null
        }
        val value = Regex("""signatureTimestamp[=:]"?(\d{4,6})"?""").find(base)?.groupValues?.getOrNull(1)
        val parsed = value?.toIntOrNull() ?: 0
        if (parsed > 0) log.i("signature timestamp $parsed from player $hash")
        else log.w("no signature timestamp in player $hash")
        return parsed.takeIf { it > 0 }
    }
}
