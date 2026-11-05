package com.quackify.app

import android.content.Context
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
 * same `canvas` persisted query the web player uses), which answers with an
 * init segment url. The actual video is a fragmented mp4 spread over
 * numbered segments next to the init one:
 *
 *   .../profiles/6/inits/mp4?fauth=...   (init)
 *   .../profiles/6/0.mp4?fauth=...       (media segments)
 *   .../profiles/6/4.mp4?fauth=...
 *
 * Segment numbering is not always contiguous (some canvases step by 4),
 * so the stepper probes +1 first and falls back to +4 before giving up.
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

    /** Resolve the canvas init url for a track, null when the track has none. */
    suspend fun canvasUrl(context: Context, trackUri: String): String? {
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
        val url = response.json()
            ?.optJSONObject("data")
            ?.optJSONObject("trackUnion")
            ?.optJSONObject("canvas")
            ?.optString("url").orEmpty()
        return url.takeIf { it.startsWith("http") }
            ?.also { log.i("canvas $id -> ${it.substringBefore('?')}") }
    }

    /** Download and stitch the canvas of a track, returning a playable local file path. */
    suspend fun download(context: Context, trackUri: String): String? = withContext(Dispatchers.IO) {
        val id = trackUri.substringAfterLast(":")
        val dir = File(context.cacheDir, "canvas")
        val cache = File(dir, "$id.mp4")
        if (cache.isFile && cache.length() > 0L) return@withContext cache.absolutePath

        val initUrl = canvasUrl(context, trackUri) ?: return@withContext null
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
                    val segment = "$base$candidate.mp4" + if (query.isBlank()) "" else "?$query"
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
