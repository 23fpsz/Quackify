package com.spotiduck.compose.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class SpotifyEntityType {
    PLAYLIST,
    ALBUM,
    ARTIST,
    PODCAST,
    PSEUDO_PLAYLIST
}

data class SpotifyLibraryEntity(
    val uri: String,
    val name: String,
    val type: SpotifyEntityType,
    val subtitle: String,
    val imageUrl: String? = null,
    val isPinned: Boolean = false
)

/**
 * Headless Spotify Metadata Repository.
 *
 * Interfaces directly with Spotify's Pathfinder GraphQL backend
 * using persistent SHA-256 query hashes. Zero Compose dependencies.
 */
class SpotifyMetadataRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()
) {

    companion object {
        private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

        // Pathfinder persistent query hashes reverse-engineered from official Web Player
        private const val HASH_LIBRARY_V3 = "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455"
        private const val HASH_DECORATE_TRACKS = "383de00240775c39a6afe0b1055dc562b2a3930894201f9762f3fc32a74971c7"
        private const val HASH_PLAYLIST_METADATA = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Fetches the user's authentic library items (playlists, saved albums, followed artists, liked songs)
     * using the official Pathfinder libraryV3 query.
     */
    suspend fun fetchLibrary(spDc: String, limit: Int = 50): Result<List<SpotifyLibraryEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)

            val variables = JSONObject().apply {
                put("order", JSONObject.NULL)
                put("textFilter", "")
                put("features", JSONArray().apply {
                    put("LIKED_SONGS")
                    put("YOUR_EPISODES_V2")
                    put("PRERELEASES")
                })
                put("limit", limit)
                put("offset", 0)
                put("flatten", false)
                put("expandedFolders", JSONArray())
                put("folderUri", JSONObject.NULL)
                put("includeFoldersWhenFlattening", true)
            }

            val payload = JSONObject().apply {
                put("variables", variables)
                put("operationName", "libraryV3")
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", HASH_LIBRARY_V3)
                    })
                })
            }

            val request = Request.Builder()
                .url(PATHFINDER_URL)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .header("app-platform", "WebPlayer")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("libraryV3 query failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty library response")
            val json = JSONObject(body)

            val itemsArray = json.optJSONObject("data")
                ?.optJSONObject("me")
                ?.optJSONObject("libraryV3")
                ?.optJSONArray("items") ?: JSONArray()

            val entities = mutableListOf<SpotifyLibraryEntity>()

            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(i) ?: continue
                val isPinned = itemObj.optBoolean("pinned", false)
                val wrapper = itemObj.optJSONObject("item") ?: continue
                val data = wrapper.optJSONObject("data") ?: continue

                val typeName = data.optString("__typename", "")
                val uri = data.optString("uri", wrapper.optString("_uri", ""))

                val (name, type, subtitle, imageUrl) = parseEntity(typeName, data)
                entities.add(
                    SpotifyLibraryEntity(
                        uri = uri,
                        name = name,
                        type = type,
                        subtitle = subtitle,
                        imageUrl = imageUrl,
                        isPinned = isPinned
                    )
                )
            }

            entities
        }
    }

    /**
     * Fetches track metadata for the given playlist using Pathfinder's fetchPlaylistMetadata.
     */
    suspend fun fetchPlaylistTracks(spDc: String, playlistUri: String, limit: Int = 30): Result<List<PlaybackTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)

            val variables = JSONObject().apply {
                put("uri", playlistUri)
                put("offset", 0)
                put("limit", limit)
                put("enableWatchFeedEntrypoint", true)
            }

            val payload = JSONObject().apply {
                put("variables", variables)
                put("operationName", "fetchPlaylistMetadata")
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", HASH_PLAYLIST_METADATA)
                    })
                })
            }

            val request = Request.Builder()
                .url(PATHFINDER_URL)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .header("app-platform", "WebPlayer")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("fetchPlaylistMetadata failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty playlist response")
            val json = JSONObject(body)

            val items = json.optJSONObject("data")
                ?.optJSONObject("playlistV2")
                ?.optJSONObject("content")
                ?.optJSONArray("items") ?: JSONArray()

            val trackUris = mutableListOf<String>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val trackData = item.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                val uri = trackData.optString("uri", "")
                if (uri.startsWith("spotify:track:")) {
                    trackUris.add(uri)
                }
            }

            if (trackUris.isEmpty()) {
                return@runCatching emptyList<PlaybackTrack>()
            }

            decorateTracks(spDc, trackUris).getOrThrow()
        }
    }

    /**
     * Decorates track URIs with full titles, artists, album names, durations, and high-res artwork.
     */
    suspend fun decorateTracks(spDc: String, trackUris: List<String>): Result<List<PlaybackTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)

            val urisArray = JSONArray().apply {
                trackUris.forEach { put(it) }
            }

            val variables = JSONObject().apply {
                put("uris", urisArray)
            }

            val payload = JSONObject().apply {
                put("variables", variables)
                put("operationName", "decorateContextTracks")
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", HASH_DECORATE_TRACKS)
                    })
                })
            }

            val request = Request.Builder()
                .url(PATHFINDER_URL)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .header("app-platform", "WebPlayer")
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("decorateContextTracks failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty decorate response")
            val json = JSONObject(body)

            val tracksArray = json.optJSONObject("data")?.optJSONArray("tracks") ?: JSONArray()
            val playbackTracks = mutableListOf<PlaybackTrack>()

            for (i in 0 until tracksArray.length()) {
                val t = tracksArray.optJSONObject(i) ?: continue
                val uri = t.optString("uri", "")
                val name = t.optString("name", "Unknown Track")

                val durationMs = t.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L

                // Extract artist names
                val artistsItems = t.optJSONObject("artists")?.optJSONArray("items") ?: JSONArray()
                val artistNames = mutableListOf<String>()
                for (a in 0 until artistsItems.length()) {
                    val artObj = artistsItems.optJSONObject(a) ?: continue
                    val artName = artObj.optJSONObject("profile")?.optString("name", "") ?: ""
                    if (artName.isNotEmpty()) artistNames.add(artName)
                }
                val artistString = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

                // Extract cover art
                val coverArtSources = t.optJSONObject("albumOfTrack")
                    ?.optJSONObject("coverArt")
                    ?.optJSONArray("sources") ?: JSONArray()

                var coverUrl = ""
                for (c in 0 until coverArtSources.length()) {
                    val s = coverArtSources.optJSONObject(c) ?: continue
                    val url = s.optString("url", "")
                    if (url.isNotEmpty()) {
                        coverUrl = url
                        break
                    }
                }

                playbackTracks.add(
                    PlaybackTrack(
                        uri = uri,
                        fileId = "",
                        title = name,
                        artist = artistString,
                        albumArtUrl = coverUrl,
                        durationMs = durationMs
                    )
                )
            }

            playbackTracks
        }
    }

    private data class ParsedEntity(
        val name: String,
        val type: SpotifyEntityType,
        val subtitle: String,
        val imageUrl: String?
    )

    private fun parseEntity(typeName: String, data: JSONObject): ParsedEntity {
        return when (typeName) {
            "PseudoPlaylist" -> {
                ParsedEntity(
                    name = "Liked Songs",
                    type = SpotifyEntityType.PSEUDO_PLAYLIST,
                    subtitle = "Playlist • Pinned",
                    imageUrl = null
                )
            }
            "Artist" -> {
                val name = data.optJSONObject("profile")?.optString("name", "Unknown Artist") ?: "Unknown Artist"
                val img = extractImageUrl(data.optJSONObject("visuals")?.optJSONObject("avatarImage"))
                ParsedEntity(
                    name = name,
                    type = SpotifyEntityType.ARTIST,
                    subtitle = "Artist",
                    imageUrl = img
                )
            }
            "Album" -> {
                val name = data.optString("name", "Unknown Album")
                val artistName = data.optJSONObject("artists")
                    ?.optJSONArray("items")
                    ?.optJSONObject(0)
                    ?.optJSONObject("profile")
                    ?.optString("name", "") ?: ""
                val subtitle = if (artistName.isNotEmpty()) "Album • $artistName" else "Album"
                val img = extractImageUrl(data.optJSONObject("coverArt"))
                ParsedEntity(
                    name = name,
                    type = SpotifyEntityType.ALBUM,
                    subtitle = subtitle,
                    imageUrl = img
                )
            }
            else -> { // Playlist or other
                val name = data.optString("name", "Unknown Playlist")
                val owner = data.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name", "Spotify") ?: "Spotify"
                val img = extractPlaylistImage(data.optJSONObject("images"))
                ParsedEntity(
                    name = name,
                    type = SpotifyEntityType.PLAYLIST,
                    subtitle = "Playlist • $owner",
                    imageUrl = img
                )
            }
        }
    }

    private fun extractImageUrl(container: JSONObject?): String? {
        val sources = container?.optJSONArray("sources") ?: return null
        for (i in 0 until sources.length()) {
            val url = sources.optJSONObject(i)?.optString("url", "") ?: ""
            if (url.isNotEmpty()) return url
        }
        return null
    }

    private fun extractPlaylistImage(imagesObj: JSONObject?): String? {
        val items = imagesObj?.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val sources = item.optJSONArray("sources") ?: continue
            for (s in 0 until sources.length()) {
                val url = sources.optJSONObject(s)?.optString("url", "") ?: ""
                if (url.isNotEmpty()) return url
            }
        }
        return null
    }
}
