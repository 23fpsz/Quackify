package com.spotiduck.compose.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object SpotifyMediaResolver {

    private const val STORAGE_RESOLVE_BASE = "https://gae2-spclient.spotify.com/storage-resolve/v2/files/audio/interactive/10"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder().build()

    /**
     * Resolves an internal 40-character Spotify audio file ID (from Track Manifest format 10 - MP4)
     * directly into authenticated Akamai/Fastly CDN streaming URLs.
     */
    suspend fun resolveCdnUrl(fileId: String, accessToken: String): String = withContext(Dispatchers.IO) {
        val url = "$STORAGE_RESOLVE_BASE/$fileId?version=10000000&product=9&platform=39&alt=json"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Storage Resolve failed for fileId $fileId: HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("Empty storage response")
            val json = JSONObject(body)
            val cdnUrls = json.optJSONArray("cdnurl")
            if (cdnUrls == null || cdnUrls.length() == 0) {
                throw IllegalStateException("No CDN URLs returned for fileId $fileId")
            }
            return@withContext cdnUrls.getString(0)
        }
    }
}
