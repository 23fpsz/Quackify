package com.quackify.app

import android.content.Context
import com.quackify.backend.connect.dealer.DealerHost
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net
import org.json.JSONObject
import java.io.File
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
 * so the ladder is preferred: we take the best `mp4` profile it offers,
 * which is the top rendition. The manifest is scoped per encoding, so its
 * init url derives numbered segment urls exactly like the legacy segmented
 * ones did:
 *
 *   .../profiles/4/inits/mp4?fauth=...   (init)
 *   .../profiles/4/0.mp4?fauth=...       (media segments)
 *   .../profiles/4/4.mp4?fauth=...
 *
 * When the manifest is unreachable we fall back to the direct file, and when
 * even that url is segmented (an older delivery format without a manifest
 * stride) the +1/+4 probe stepper still applies.
 *
 * We download the init plus the available segments and stitch them into one
 * playable file; a concatenated fMP4 is a valid progressive mp4.
 */
object CanvasKit {

    private const val PATHFINDER = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val CANVAS_HASH = "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"
    private const val INIT_MARKER = "/inits/mp4"
    private const val MAX_SEGMENTS = 16
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val MAX_CACHED = 16

    /** A canvas to download: its init url plus the segment stride when known. */
    private class Target(val initUrl: String, val stride: Int?)

    /** Resolve the best available canvas url for a track, null when the track has none. */
    suspend fun canvasUrl(context: Context, trackUri: String): String? =
        resolve(context, trackUri)?.initUrl

    /**
     * Ask the canvas query, then upgrade to the manifest ladder: the direct
     * canvaz file varies in quality, the ladder always tops out at 1080p.
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
     * mp4 profile and rebuild its init url from the signed template. The
     * template tokens are scoped to the whole encoding, so segment urls
     * follow from the init url by name like the legacy segmented ones.
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
        var stride = 0
        for (i in 0 until contents.length()) {
            val content = contents.optJSONObject(i) ?: continue
            if (content.optString("encoding_id") != encodingId) continue
            stride = content.optInt("segment_length", 0)
            val profiles = content.optJSONArray("profiles") ?: continue
            for (j in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(j) ?: continue
                if (profile.optString("file_type") != "mp4") continue
                if (profile.optInt("video_resolution", 0) <= 0) continue
                if (best == null ||
                    profile.optInt("video_resolution") > best.optInt("video_resolution")
                ) {
                    best = profile
                }
            }
        }
        if (best == null) return null
        val initUrl = baseUrl.trimEnd('/') + "/" + initTemplate.trimStart('/')
            .replace("{{profile_id}}", best.optString("id"))
            .replace("{{file_type}}", "mp4")
        log.i(
            "canvas $fileId manifest ${best.optInt("video_resolution")}p " +
                "profile ${best.optString("id")} stride $stride",
        )
        return Target(initUrl, stride.takeIf { it > 0 })
    }

    /** Download and stitch the canvas of a track, returning a playable local file path. */
    suspend fun download(context: Context, trackUri: String): String? = withContext(Dispatchers.IO) {
        val id = trackUri.substringAfterLast(":")
        val dir = File(context.cacheDir, "canvas")
        // v2: renditions from the manifest ladder; v1 files predate it
        val cache = File(dir, "v2-$id.mp4")
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
            trim(dir)
            log.i("canvas $id stitched ${parts.size} part(s) ${total}B -> ${cache.name}")
            cache.absolutePath
        }.onFailure {
            log.w("canvas $id write failed: ${it.message}")
        }.getOrNull()
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
