package com.quackify.backend.api

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.TRACKS)

object AudioFeatures {

    private const val ANALYSIS = "https://api.spotify.com/v1/audio-analysis/"

    private const val FEATURES = "https://api.spotify.com/v1/audio-features?ids="

    private const val CACHE_LIMIT = 24

    /**
     * A track's beat grid: where each beat and each bar starts, in seconds of
     * the track's own timeline, and the tempo the analysis reports for it.
     * A tempo-only result carries no grids and supports stretching but not
     * phase locking.
     */
    data class TrackGrid(
        val bpm: Double,
        val beatStarts: DoubleArray,
        val barStarts: DoubleArray,
    )

    private val cache = LinkedHashMap<String, TrackGrid?>()

    private val lock = Any()

    suspend fun grid(context: Context, uri: String): TrackGrid? {
        val id = uri.substringAfterLast(':')
        if (id.isBlank()) return null
        synchronized(lock) {
            if (cache.containsKey(uri)) return cache[uri]
        }
        val grid = withContext(Dispatchers.IO) {
            runCatching { fetchAnalysis(context, id) }.getOrNull()
                ?: runCatching { fetchFeatures(context, id) }.getOrNull()
        }
        if (grid == null) {
            log.w("no beat grid for '${uri.takeLast(12)}', the analysis and feature endpoints both refused")
        } else {
            log.i(
                "beat grid '${uri.takeLast(12)}' ${"%.1f".format(grid.bpm)}bpm " +
                    "beats=${grid.beatStarts.size} bars=${grid.barStarts.size}"
            )
        }
        synchronized(lock) {
            cache[uri] = grid
            while (cache.size > CACHE_LIMIT) cache.remove(cache.keys.first())
        }
        return grid
    }

    private suspend fun fetchAnalysis(context: Context, id: String): TrackGrid? {
        val response = Net.get("$ANALYSIS$id", headers(context), "audio-analysis")
        val root = response?.takeIf { it.ok }?.json() ?: return null
        val tempo = root.optJSONObject("track")?.optDouble("tempo", 0.0) ?: 0.0
        val beats = starts(root.optJSONArray("beats"))
        val bars = starts(root.optJSONArray("bars"))
        if (beats.size >= 2) return TrackGrid(tempo, beats, bars)
        return tempo.takeIf { it > 0.0 }?.let { TrackGrid(it, DoubleArray(0), DoubleArray(0)) }
    }

    private suspend fun fetchFeatures(context: Context, id: String): TrackGrid? {
        val response = Net.get("$FEATURES$id", headers(context), "audio-features")
        val root = response?.takeIf { it.ok }?.json() ?: return null
        val tempo = root.optDouble("tempo", 0.0)
        return tempo.takeIf { it > 0.0 }?.let { TrackGrid(it, DoubleArray(0), DoubleArray(0)) }
    }

    private fun starts(array: JSONArray?): DoubleArray {
        if (array == null) return DoubleArray(0)
        val out = DoubleArray(array.length())
        var count = 0
        for (i in 0 until array.length()) {
            val start = array.optJSONObject(i)?.optDouble("start", Double.NaN) ?: Double.NaN
            if (start.isFinite()) out[count++] = start
        }
        return out.copyOf(count)
    }

    private suspend fun headers(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("accept-language", "en")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }
}
