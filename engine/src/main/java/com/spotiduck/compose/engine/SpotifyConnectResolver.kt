package com.spotiduck.compose.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Headless Spotify Connect Handshake & Track Manifest Resolver.
 *
 * Simulates a cast_audio endpoint connected to Spotify's Dealer WebSocket
 * to dynamically acquire Widevine MP4 stream file IDs for ANY arbitrary track
 * on free or premium Spotify accounts.
 */
object SpotifyConnectResolver {

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Persistent virtual device identifier
    private val deviceId: String = UUID.randomUUID().toString().replace("-", "").padEnd(40, '0').take(40)

    // In-memory cache: trackUri -> fileId
    private val fileIdCache = ConcurrentHashMap<String, String>()

    /**
     * Dynamically resolves the Spotify 40-character storage fileId for any track URI
     * using the Spotify Connect State machine over the Dealer WebSocket.
     */
    suspend fun resolveFileId(trackUri: String, accessToken: String): String = withContext(Dispatchers.IO) {
        // Return from cache if already resolved
        fileIdCache[trackUri]?.let { return@withContext it }

        val connectionIdDeferred = CompletableDeferred<String>()
        val fileIdDeferred = CompletableDeferred<String>()

        val wsUrl = "wss://dealer.spotify.com/?access_token=$accessToken"
        val wsRequest = Request.Builder().url(wsUrl).build()

        var webSocket: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)

                    // 1. Check for Spotify-Connection-Id header
                    if (!connectionIdDeferred.isCompleted) {
                        val headers = json.optJSONObject("headers")
                        val connId = headers?.optString("Spotify-Connection-Id", "") ?: ""
                        if (connId.isNotEmpty()) {
                            connectionIdDeferred.complete(connId)
                        }
                    }

                    // 2. Check for state_machine replacement containing track manifest
                    if (json.has("payloads")) {
                        val payloads = json.optJSONArray("payloads") ?: JSONArray()
                        for (i in 0 until payloads.length()) {
                            val payload = payloads.optJSONObject(i) ?: continue
                            val stateMachine = payload.optJSONObject("state_machine") ?: continue
                            val tracks = stateMachine.optJSONArray("tracks") ?: continue
                            for (t in 0 until tracks.length()) {
                                val trackObj = tracks.optJSONObject(t) ?: continue
                                val manifest = trackObj.optJSONObject("manifest") ?: continue
                                val fileIdsMp4 = manifest.optJSONArray("file_ids_mp4") ?: JSONArray()
                                for (f in 0 until fileIdsMp4.length()) {
                                    val fileObj = fileIdsMp4.optJSONObject(f) ?: continue
                                    val fId = fileObj.optString("file_id", "")
                                    if (fId.isNotEmpty()) {
                                        fileIdDeferred.complete(fId)
                                        return
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parse errors on ping/keep-alive frames
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connectionIdDeferred.isCompleted) {
                    connectionIdDeferred.completeExceptionally(t)
                }
                if (!fileIdDeferred.isCompleted) {
                    fileIdDeferred.completeExceptionally(t)
                }
            }
        }

        webSocket = httpClient.newWebSocket(wsRequest, listener)

        try {
            // Await Connection ID (max 5s)
            val connectionId = withTimeoutOrNull(5000L) {
                connectionIdDeferred.await()
            } ?: throw IllegalStateException("Timeout awaiting Dealer Spotify-Connection-Id")

            // Register device as cast_audio (avoids PREMIUM_REQUIRED check)
            registerDevice(deviceId, connectionId, accessToken)

            // Send Connect play command for the requested track URI
            sendPlayCommand(deviceId, trackUri, accessToken)

            // Await the track's Widevine MP4 file ID (max 5s)
            val resolvedFileId = withTimeoutOrNull(5000L) {
                fileIdDeferred.await()
            } ?: throw IllegalStateException("Timeout awaiting playback manifest for $trackUri")

            // Cache for subsequent plays
            fileIdCache[trackUri] = resolvedFileId
            return@withContext resolvedFileId

        } finally {
            webSocket.close(1000, "Resolution complete")
        }
    }

    private fun registerDevice(devId: String, connId: String, token: String) {
        val regBody = JSONObject().apply {
            put("device", JSONObject().apply {
                put("brand", "google")
                put("capabilities", JSONObject().apply {
                    put("change_volume", true)
                    put("enable_play_token", true)
                    put("supports_file_media_type", true)
                    put("play_token_lost_behavior", "pause")
                    put("disable_connect", false)
                    put("audio_podcasts", true)
                    put("video_playback", true)
                    put("manifest_formats", JSONArray().apply {
                        put("file_ids_mp4")
                        put("file_ids_mp4_dual")
                    })
                    put("supports_preferred_media_type", true)
                    put("supports_playback_offsets", true)
                    put("supports_playback_speed", true)
                })
                put("device_id", devId)
                put("device_type", "cast_audio")
                put("metadata", JSONObject())
                put("model", "chromecast")
                put("name", "SpotiDuck Player")
                put("platform_identifier", "cast_audio android 14;chromecast")
            })
            put("connection_id", connId)
            put("client_version", "harmony:4.81.1-1f3d48b7f")
            put("volume", 65535)
        }

        val request = Request.Builder()
            .url("https://gae2-spclient.spotify.com/track-playback/v1/devices")
            .post(regBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: ""
            throw IllegalStateException("Device registration failed: HTTP ${response.code} - $err")
        }
    }

    private fun sendPlayCommand(devId: String, trackUri: String, token: String) {
        val playCmdUrl = "https://gae2-spclient.spotify.com/connect-state/v1/player/command/from/$devId/to/$devId"

        val playCmdBody = JSONObject().apply {
            put("command", JSONObject().apply {
                put("context", JSONObject().apply {
                    put("uri", trackUri)
                    put("url", "context://$trackUri")
                    put("metadata", JSONObject())
                    put("pages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("page_url", "context://$trackUri")
                            put("tracks", JSONArray().apply {
                                put(JSONObject().apply { put("uri", trackUri) })
                            })
                        })
                    })
                })
                put("play_origin", JSONObject().apply {
                    put("feature_identifier", "search")
                    put("feature_version", "1.0.0")
                    put("referrer_identifier", "search")
                })
                put("options", JSONObject().apply {
                    put("license", "tft")
                    put("skip_to", JSONObject().apply {
                        put("track_uri", trackUri)
                    })
                    put("player_options_override", JSONObject())
                })
                put("endpoint", "play")
            })
        }

        val request = Request.Builder()
            .url(playCmdUrl)
            .post(playCmdBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: ""
            throw IllegalStateException("Connect play command failed: HTTP ${response.code} - $err")
        }
    }
}
