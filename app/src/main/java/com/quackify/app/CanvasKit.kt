package com.quackify.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.quackify.backend.connect.dealer.DealerHost
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val log = Logs.of(Logs.Module.TRACKS)

/**
 * Client-side Spotify Canvas loader.
 *
 * The canvas for a track is resolved from the Pathfinder GraphQL api (the
 * same `canvas` persisted query the web player uses). The answer carries a
 * direct url plus a `fileId` for the encoded source video on the segments
 * cdn; real clients feed that id to the manifests/v9 api, which answers the
 * full rendition ladder (mp4, ts and webm variants from 180p up to 1080p)
 * together with signed init/segment url templates.
 *
 * The direct url points at a canvaz.scdn.co single file whose resolution
 * depends on what was uploaded for that canvas (720p or 1080p in practice),
 * so the ladder is preferred: we take the best `mp4` profile it offers at
 * or under 720p, which is where the web player's own canvas ABR settles —
 * the 1080p top rung decodes at ~9 Mbps into a card-sized view and
 * visibly stutters. The manifest is scoped per encoding, so its
 * init url derives numbered segment urls exactly like the legacy segmented
 * ones did:
 *
 *   .../profiles/5/inits/mp4?fauth=...   (init)
 *   .../profiles/5/0.mp4?fauth=...       (media segments)
 *   .../profiles/5/4.mp4?fauth=...
 *
 * When the manifest is unreachable we fall back to the direct file, and when
 * even that url is segmented (an older delivery format without a manifest
 * stride) the +1/+4 probe stepper still applies.
 *
 * We download the init plus the available segments and stitch them into one
 * playable file; a concatenated fMP4 is a valid progressive mp4. The CDN
 * fragments can, however, carry their master's media timeline — sample
 * timestamps near 1e9 ms have been seen — which plays fine until a fragment
 * boundary breaks the timeline; from there every frame lands "hours late",
 * the decoder drops it and the looping restart turns into a flush storm that
 * freezes the canvas mid-video. The stitched file is therefore remuxed with
 * normalized (0-based, monotonic) sample timestamps before it is cached.
 */
object CanvasKit {

    private const val PATHFINDER = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val CANVAS_HASH = "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"
    private const val INIT_MARKER = "/inits/mp4"
    private const val MAX_SEGMENTS = 16
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val MAX_CACHED = 16

    /** Playback pick: the web player's canvas ABR settles at 720p; the
     * 1080p top rung (~9 Mbps) stutters in the card-sized artwork slot. */
    private const val PLAYBACK_RESOLUTION_CAP = 720

    /** Hard ceiling for degenerate ladders with no rung at or under the
     * playback cap. */
    private const val MAX_RESOLUTION = 1080

    /** A canvas to download: its init url plus the segment stride when known. */
    private class Target(val initUrl: String, val stride: Int?)

    /** Resolve the best available canvas url for a track, null when the track has none. */
    suspend fun canvasUrl(context: Context, trackUri: String): String? =
        resolve(context, trackUri)?.initUrl

    /**
     * Ask the canvas query, then upgrade to the manifest ladder: the direct
     * canvaz file varies in quality, the ladder offers a capped 720p pick.
     */
    private suspend fun resolve(context: Context, trackUri: String): Target? {
        val id = trackUri.substringAfterLast(":")
        if (!trackUri.startsWith("spotify:track:") || id.isBlank()) return null
        val token = SpotifyAuth.token(context)
        if (token.isBlank()) return null
        // The exact header set every other pathfinder call sends: without the
        // browser User-Agent (and app version) the edge answers
        // 403 "Client/request not allowed" for this operation.
        val headers = mutableMapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/json",
            "app-platform" to "WebPlayer",
            "spotify-app-version" to "9.1.92.3058",
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
            "User-Agent" to Net.DESKTOP_UA,
        )
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { headers["client-token"] = it }
        val body = JSONObject()
            .put("operationName", "canvas")
            .put("variables", JSONObject().put("trackUri", trackUri))
            .put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject().put("version", 1).put("sha256Hash", CANVAS_HASH),
                ),
            )
            .toString()
        var response = Net.postJson(PATHFINDER, body, headers, "canvas") ?: return null
        if (response.code == 403) {
            // a stale client-token also produces a 403 here; mint a fresh one once
            log.w("canvas $id 403, retrying with a fresh client-token")
            ClientToken.get(context, force = true).takeIf { it.isNotBlank() }?.let {
                headers["client-token"] = it
            }
            response = Net.postJson(PATHFINDER, body, headers, "canvas") ?: return null
        }
        if (!response.ok) {
            log.i("canvas $id none (${response.code})")
            return null
        }
        val canvas = response.json()
            ?.optJSONObject("data")
            ?.optJSONObject("trackUnion")
            ?.optJSONObject("canvas")
            ?: return null
        val url = canvas.optString("url")
        if (!url.startsWith("http")) return null
        log.i("canvas $id -> ${url.substringBefore('?')}")
        val fileId = canvas.optString("fileId")
        if (fileId.isNotBlank()) {
            manifestTarget(context, fileId, headers)?.let { return it }
        }
        return Target(url, null)
    }

    /**
     * manifests/v9 knows every rendition of a canvas source. Pick the best
     * mp4 profile at or under the playback cap and rebuild its init url
     * from the signed template. The template tokens are scoped to the whole
     * encoding, so segment urls follow from the init url by name like the
     * legacy segmented ones.
     */
    private suspend fun manifestTarget(
        context: Context,
        fileId: String,
        headers: Map<String, String>,
    ): Target? {
        val spclient = DealerHost.resolve(context)?.second ?: return null
        val response = Net.get(
            "https://$spclient/manifests/v9/json/sources/$fileId/options/supports_drm",
            headers,
            "canvas/manifest",
        )
        if (response == null || !response.ok) {
            log.w("canvas $fileId manifest miss (${response?.code ?: "network"})")
            return null
        }
        val root = response.json() ?: return null
        val initTemplate = root.optString("initialization_template")
        val baseUrl = root.optJSONArray("base_urls")?.optString(0).orEmpty()
        if (!initTemplate.startsWith("v1/") || baseUrl.isBlank()) return null
        // the template targets one encoding of the source; only its profiles apply
        val encodingId = initTemplate.substringAfter("/encodings/", "").substringBefore("/")
        val contents = root.optJSONArray("contents") ?: return null
        var best: JSONObject? = null
        var fallback: JSONObject? = null
        var stride = 0
        for (i in 0 until contents.length()) {
            val content = contents.optJSONObject(i) ?: continue
            if (content.optString("encoding_id") != encodingId) continue
            // the endpoint only speaks options/supports_drm: renditions of a
            // drm-scoped encoding arrive ciphered and the stitcher cannot
            // decrypt, so such manifests fall back to the direct canvaz file
            if ((content.optJSONArray("encryption_infos")?.length() ?: 0) > 0) continue
            stride = content.optInt("segment_length", 0)
            val profiles = content.optJSONArray("profiles") ?: continue
            for (j in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(j) ?: continue
                if (profile.optString("file_type") != "mp4") continue
                val resolution = profile.optInt("video_resolution", 0)
                if (resolution <= 0) continue
                if (resolution <= PLAYBACK_RESOLUTION_CAP) {
                    if (best == null ||
                        resolution > best.optInt("video_resolution")
                    ) {
                        best = profile
                    }
                } else if (resolution <= MAX_RESOLUTION &&
                    (fallback == null ||
                        resolution < fallback.optInt("video_resolution"))
                ) {
                    fallback = profile
                }
            }
        }
        // degenerate ladders with no rung at or under the cap take their
        // lowest rung above it instead of nothing
        val chosen = best ?: fallback ?: return null
        val initUrl = baseUrl.trimEnd('/') + "/" + initTemplate.trimStart('/')
            .replace("{{profile_id}}", chosen.optString("id"))
            .replace("{{file_type}}", "mp4")
        log.i(
            "canvas $fileId manifest ${chosen.optInt("video_resolution")}p " +
                "profile ${chosen.optString("id")} stride $stride",
        )
        return Target(initUrl, stride.takeIf { it > 0 })
    }

    /** Download and stitch the canvas of a track, returning a playable local file path. */
    suspend fun download(context: Context, trackUri: String): String? = withContext(Dispatchers.IO) {
        val id = trackUri.substringAfterLast(":")
        val dir = File(context.cacheDir, "canvas")
        // v4: 720p-capped ladder picks; plain-name files cached the 1080p rung
        val cache = File(dir, "v4-$id.mp4")
        if (cache.isFile && cache.length() > 0L) return@withContext cache.absolutePath

        val target = resolve(context, trackUri) ?: return@withContext null
        val initUrl = target.initUrl
        val headers = mapOf(
            "User-Agent" to Net.DESKTOP_UA,
            "Referer" to "https://open.spotify.com/",
        )

        val init = Net.blob(initUrl, headers, "canvas/init")?.takeIf { it.ok }?.bytes ?: run {
            log.w("canvas $id init failed")
            return@withContext null
        }

        val parts = ArrayList<ByteArray>(MAX_SEGMENTS + 1)
        parts += init
        var total = init.size

        val segmented = initUrl.substringBefore('?').endsWith(INIT_MARKER)
        if (segmented) {
            val base = initUrl.substringBefore('?').removeSuffix(INIT_MARKER)
            val query = initUrl.substringAfter('?', "")
            if (target.stride != null) {
                // manifest numbering: segment names are start timestamps spaced
                // `stride` apart and the loop opens at 0
                var at = 0
                while (parts.size - 1 < MAX_SEGMENTS && total < MAX_BYTES) {
                    val segment = "$base/$at.mp4" + if (query.isBlank()) "" else "?$query"
                    val blob = Net.blob(segment, headers, "canvas/seg") ?: break
                    if (!blob.ok || blob.size <= 0) break
                    parts += blob.bytes
                    total += blob.size
                    at += target.stride
                }
            } else {
                // legacy numbering: not always contiguous (some canvases step by
                // 4), so the stepper probes +1 first and falls back to +4
            var index = 0
            var step = 0 // 0 = numbering still unknown, probe both strides
            while (parts.size - 1 < MAX_SEGMENTS && total < MAX_BYTES) {
                val candidates = when (step) {
                    1 -> listOf(index + 1)
                    4 -> listOf(index + 4)
                    else -> listOf(index + 1, index + 4)
                }
                var advanced = -1
                for (candidate in candidates) {
                    val segment = "$base/$candidate.mp4" + if (query.isBlank()) "" else "?$query"
                    val blob = Net.blob(segment, headers, "canvas/seg") ?: continue
                    if (blob.ok && blob.size > 0) {
                        parts += blob.bytes
                        total += blob.size
                        advanced = candidate
                        break
                    }
                }
                if (advanced < 0) break
                step = advanced - index
                index = advanced
                }
            }
            if (parts.size < 2) {
                log.w("canvas $id has no media segments, skipping")
                return@withContext null
            }
        }

        runCatching {
            dir.mkdirs()
            cache.outputStream().use { out -> parts.forEach(out::write) }
            normalizeTimestamps(cache)
            trim(dir)
            log.i("canvas $id stitched ${parts.size} part(s) ${total}B -> ${cache.name}")
            cache.absolutePath
        }.onFailure {
            log.w("canvas $id write failed: ${it.message}")
        }.getOrNull()
    }

    /** Longest inter-sample step kept as-is; longer hops (and any backwards
     * jump) are replaced with the previous sane step. */
    private const val MAX_GAP_US = 1_000_000L

    /** Step assumed between samples when the raw timeline offers no sane one
     * at all (a ~30fps canvas frame). */
    private const val DEFAULT_GAP_US = 33_333L

    /** Per-track timeline rewriter: the first sample lands at 0, every sane
     * inter-frame gap is kept as-is, broken steps fall back to the previous
     * sane one, so the output grows monotonically frame by frame. */
    private class Rebase {
        private var lastRaw = Long.MIN_VALUE
        private var lastOut = 0L
        private var lastGap = -1L

        fun next(raw: Long): Long {
            if (lastRaw == Long.MIN_VALUE) {
                lastRaw = raw
                return 0L
            }
            val gap = raw - lastRaw
            val step = when {
                gap in 1..MAX_GAP_US -> gap.also { lastGap = it }
                lastGap in 1..MAX_GAP_US -> lastGap
                else -> DEFAULT_GAP_US.also { lastGap = it }
            }
            lastOut += step
            lastRaw = raw
            return lastOut
        }
    }

    /**
     * Remux the stitched canvas through MediaExtractor + MediaMuxer while
     * rewriting every sample's presentation time to a clean 0-based,
     * monotonically increasing timeline (see [Rebase]). On any failure the
     * original stitched file is kept untouched, which plays exactly like the
     * previous behavior.
     */
    private fun normalizeTimestamps(cache: File) {
        val tmp = File(cache.parentFile, cache.name + ".tmp")
        val ok = runCatching {
            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            var started = false
            try {
                extractor.setDataSource(cache.absolutePath)
                muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val tracks = HashMap<Int, Pair<Int, Rebase>>()
                for (track in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(track)
                    if (format.getString(MediaFormat.KEY_MIME) == null) continue
                    val muxTrack = muxer.addTrack(format)
                    extractor.selectTrack(track)
                    tracks[track] = muxTrack to Rebase()
                }
                check(tracks.isNotEmpty()) { "no tracks to remux" }
                muxer.start()
                started = true
                val buffer = ByteBuffer.allocateDirect(1 shl 20)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val mapped = tracks[extractor.sampleTrackIndex]
                    if (mapped != null) {
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = mapped.second.next(extractor.sampleTime)
                        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        muxer.writeSampleData(mapped.first, buffer, info)
                    }
                    extractor.advance()
                }
            } finally {
                runCatching { extractor.release() }
                if (started) runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
            }
            check(started && tmp.isFile && tmp.length() > 0L) { "muxer produced nothing" }
            true
        }.getOrElse {
            log.w("canvas remux failed, keeping the raw stitch: ${it.message}")
            false
        }
        if (ok && cache.delete()) {
            if (tmp.renameTo(cache)) {
                log.i("canvas ${cache.name} remuxed onto a normalized timeline")
            } else {
                // rename across a mount point never happens in one cache dir,
                // but a plain copy keeps the file alive if it somehow does
                runCatching {
                    tmp.inputStream().use { input -> cache.outputStream().use { input.copyTo(it) } }
                }.onFailure { log.w("canvas remux replace failed: ${it.message}") }
                tmp.delete()
            }
        } else {
            tmp.delete()
        }
    }

    /** Keep the canvas cache small: drop the oldest files beyond [MAX_CACHED]. */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_CACHED) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHED)
            .forEach { file ->
                if (file.delete()) log.i("canvas cache dropped ${file.name}")
            }
    }
}
