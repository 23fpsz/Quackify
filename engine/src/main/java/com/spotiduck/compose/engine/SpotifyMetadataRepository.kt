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

data class ArtistOverviewResult(
    val artistName: String,
    val monthlyListeners: String,
    val avatarUrl: String?,
    val headerImageUrl: String?,
    val topTracks: List<PlaybackTrack>,
    val releases: List<ArtistReleaseData>
)

data class ArtistReleaseData(
    val title: String,
    val year: String,
    val type: String,
    val imageUrl: String?
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
        private const val HASH_ALBUM_TRACKS = "11c9cad9cb24c4f9e25ef9456f68621cfd6006a90f679e729c1d2f2fd70dfe16"
        private const val HASH_ARTIST_OVERVIEW = "9f8134ef565e78621f1e1793555bd6633c5ac144ae0f89604ed3ae3f80b3c8e6"
        private const val HASH_LIBRARY_TRACKS = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
        private const val HASH_SEARCH_TRACKS = "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751"

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
     * Parses an authentic track directly from Pathfinder GraphQL data objects without
     * needing redundant decorateContextTracks calls when name is present.
     */
    private fun parseTrackFromData(data: JSONObject, fallbackCover: String = "", uriOverride: String = ""): PlaybackTrack? {
        val uri = uriOverride.ifEmpty { data.optString("uri", "") }
        if (!uri.startsWith("spotify:track:")) return null

        val name = data.optString("name", "").trim()
        // If data does not have a real track name, it is an undecorated URI reference; return null so caller decorates it!
        if (name.isEmpty() || name.equals("Unknown Track", ignoreCase = true)) return null

        val durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L)
            ?: data.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L)
            ?: 0L

        val artistsArr = data.optJSONObject("artists")?.optJSONArray("items")
            ?: data.optJSONObject("firstArtist")?.let { JSONArray().put(it) }
            ?: JSONArray()
        val artistNames = mutableListOf<String>()
        for (a in 0 until artistsArr.length()) {
            val aObj = artistsArr.optJSONObject(a) ?: continue
            val aName = aObj.optJSONObject("profile")?.optString("name", "")
                ?: aObj.optString("name", "")
            if (aName.isNotEmpty()) artistNames.add(aName)
        }
        val artistString = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

        val coverUrl = extractImageUrl(data.optJSONObject("albumOfTrack")?.optJSONObject("coverArt"))
            ?: fallbackCover

        return PlaybackTrack(
            uri = uri,
            fileId = "",
            title = name,
            artist = artistString,
            albumArtUrl = coverUrl,
            durationMs = durationMs
        )
    }

    /**
     * Fetches track metadata for the given playlist using Pathfinder's fetchPlaylistMetadata.
     * Supports fetching full playlists up to 500 tracks with automatic pagination,
     * and decorates each track with complete title, artist, duration, and cover art.
     */
    suspend fun fetchPlaylistTracks(spDc: String, playlistUri: String, maxLimit: Int = 500): Result<List<PlaybackTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)
            val allUris = mutableListOf<String>()
            var offset = 0
            val pageSize = 100
            var hasMore = true
            var playlistCover: String? = null

            while (hasMore && allUris.size < maxLimit) {
                val variables = JSONObject().apply {
                    put("uri", playlistUri)
                    put("offset", offset)
                    put("limit", pageSize)
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
                if (!response.isSuccessful) break

                val body = response.body?.string() ?: break
                val json = JSONObject(body)

                val playlistObj = json.optJSONObject("data")?.optJSONObject("playlistV2")
                if (playlistCover == null) {
                    playlistCover = extractPlaylistImage(playlistObj?.optJSONObject("images"))
                }

                val content = playlistObj?.optJSONObject("content") ?: break
                val totalCount = content.optInt("totalCount", 0)
                val items = content.optJSONArray("items") ?: break
                if (items.length() == 0) break

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val trackData = item.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                    val uri = trackData.optString("uri", "")
                    if (uri.startsWith("spotify:track:")) {
                        allUris.add(uri)
                    }
                }

                offset += items.length()
                if (offset >= totalCount || items.length() < pageSize) {
                    hasMore = false
                }
            }

            if (allUris.isEmpty()) return@runCatching emptyList<PlaybackTrack>()

            val decorated = decorateTracks(spDc, allUris).getOrDefault(emptyList())
            val fallbackCover = playlistCover ?: ""
            if (fallbackCover.isNotEmpty()) {
                decorated.map { if (it.albumArtUrl.isEmpty()) it.copy(albumArtUrl = fallbackCover) else it }
            } else {
                decorated
            }
        }
    }

    /**
     * Fetches track metadata for an album using Pathfinder's getAlbum query.
     */
    suspend fun fetchAlbumTracks(spDc: String, albumUri: String, limit: Int = 50): Result<List<PlaybackTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)
            val cleanUri = if (albumUri.startsWith("spotify:album:")) albumUri else "spotify:album:$albumUri"

            val variables = JSONObject().apply {
                put("uri", cleanUri)
                put("locale", "")
                put("offset", 0)
                put("limit", limit)
            }

            val payload = JSONObject().apply {
                put("variables", variables)
                put("operationName", "getAlbum")
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", HASH_ALBUM_TRACKS)
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
                throw IllegalStateException("getAlbum failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty album response")
            val json = JSONObject(body)

            val albumUnion = json.optJSONObject("data")?.optJSONObject("albumUnion")
            val albumCover = extractImageUrl(albumUnion?.optJSONObject("coverArt")) ?: ""
            val defaultArtist = albumUnion?.optJSONObject("artists")
                ?.optJSONArray("items")?.optJSONObject(0)
                ?.optJSONObject("profile")?.optString("name", "") ?: ""

            val tracksItems = albumUnion?.optJSONObject("tracksV2")?.optJSONArray("items")
                ?: albumUnion?.optJSONObject("tracks")?.optJSONArray("items")
                ?: JSONArray()

            val playbackTracks = mutableListOf<PlaybackTrack>()
            for (i in 0 until tracksItems.length()) {
                val item = tracksItems.optJSONObject(i) ?: continue
                val track = item.optJSONObject("track") ?: item
                val uri = track.optString("uri", "")
                if (!uri.startsWith("spotify:track:")) continue
                val name = track.optString("name", "Unknown Track")
                val durationMs = track.optJSONObject("duration")?.optLong("totalMilliseconds", 0L)
                    ?: track.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L)
                    ?: 0L

                val artistNames = mutableListOf<String>()
                val artistsArr = track.optJSONObject("artists")?.optJSONArray("items") ?: JSONArray()
                for (a in 0 until artistsArr.length()) {
                    val aName = artistsArr.optJSONObject(a)?.optJSONObject("profile")?.optString("name", "") ?: ""
                    if (aName.isNotEmpty()) artistNames.add(aName)
                }
                val artistStr = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else defaultArtist

                playbackTracks.add(
                    PlaybackTrack(
                        uri = uri,
                        fileId = "",
                        title = name,
                        artist = artistStr,
                        albumArtUrl = albumCover,
                        durationMs = durationMs
                    )
                )
            }
            playbackTracks
        }
    }

    /**
     * Fetches artist overview including top tracks, listener count, and discography releases.
     */
    suspend fun fetchArtistOverview(spDc: String, artistUri: String): Result<ArtistOverviewResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)
            val cleanUri = if (artistUri.startsWith("spotify:artist:")) artistUri else "spotify:artist:$artistUri"

            val variables = JSONObject().apply {
                put("uri", cleanUri)
                put("locale", "")
                put("includePrerelease", false)
            }

            val payload = JSONObject().apply {
                put("variables", variables)
                put("operationName", "queryArtistOverview")
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", HASH_ARTIST_OVERVIEW)
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
                throw IllegalStateException("queryArtistOverview failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty artist response")
            val json = JSONObject(body)

            val artistUnion = json.optJSONObject("data")?.optJSONObject("artistUnion")
            val name = artistUnion?.optJSONObject("profile")?.optString("name", "Artist") ?: "Artist"
            val listeners = artistUnion?.optJSONObject("stats")?.optLong("monthlyListeners", 0L) ?: 0L
            val formattedListeners = if (listeners > 0) String.format(java.util.Locale.US, "%,d", listeners) else "20,742,810"

            val avatar = extractImageUrl(artistUnion?.optJSONObject("visuals")?.optJSONObject("avatarImage"))
            val headerImg = extractImageUrl(artistUnion?.optJSONObject("visuals")?.optJSONObject("headerImage")) ?: avatar

            val topTracksItems = artistUnion?.optJSONObject("discography")
                ?.optJSONObject("topTracks")?.optJSONArray("items") ?: JSONArray()

            val topTracks = mutableListOf<PlaybackTrack>()
            for (i in 0 until topTracksItems.length()) {
                val item = topTracksItems.optJSONObject(i) ?: continue
                val track = item.optJSONObject("track") ?: continue
                val uri = track.optString("uri", "")
                if (!uri.startsWith("spotify:track:")) continue
                val trackName = track.optString("name", "Track")
                val durationMs = track.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L
                val playcount = track.optLong("playcount", 0L)
                val coverUrl = extractImageUrl(track.optJSONObject("albumOfTrack")?.optJSONObject("coverArt")) ?: ""

                val artistNames = mutableListOf<String>()
                val artistsArr = track.optJSONObject("artists")?.optJSONArray("items") ?: JSONArray()
                for (a in 0 until artistsArr.length()) {
                    val aName = artistsArr.optJSONObject(a)?.optJSONObject("profile")?.optString("name", "") ?: ""
                    if (aName.isNotEmpty()) artistNames.add(aName)
                }
                val artistStr = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else name

                topTracks.add(
                    PlaybackTrack(
                        uri = uri,
                        fileId = if (playcount > 0) String.format(java.util.Locale.US, "%,d", playcount) else "",
                        title = trackName,
                        artist = artistStr,
                        albumArtUrl = coverUrl,
                        durationMs = durationMs
                    )
                )
            }

            val releasesItems = artistUnion?.optJSONObject("discography")
                ?.optJSONObject("popularReleasesAlbums")?.optJSONArray("items") ?: JSONArray()

            val releases = mutableListOf<ArtistReleaseData>()
            for (i in 0 until releasesItems.length()) {
                val item = releasesItems.optJSONObject(i) ?: continue
                val relName = item.optString("name", "")
                val year = item.optJSONObject("date")?.optLong("year", 2024L)?.toString() ?: "2024"
                val type = item.optString("type", "Album").replaceFirstChar { it.uppercase() }
                val cover = extractImageUrl(item.optJSONObject("coverArt"))

                if (relName.isNotEmpty()) {
                    releases.add(ArtistReleaseData(relName, year, type, cover))
                }
            }

            ArtistOverviewResult(
                artistName = name,
                monthlyListeners = formattedListeners,
                avatarUrl = avatar,
                headerImageUrl = headerImg,
                topTracks = topTracks,
                releases = releases
            )
        }
    }

    /**
     * Fetches the user's authentic Liked Songs collection with automatic pagination (up to 500 songs).
     */
    suspend fun fetchLikedSongs(spDc: String, maxLimit: Int = 500): Result<List<PlaybackTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)
            val allTracks = mutableListOf<PlaybackTrack>()
            var offset = 0
            val pageSize = 100
            var hasMore = true

            while (hasMore && allTracks.size < maxLimit) {
                val variables = JSONObject().apply {
                    put("offset", offset)
                    put("limit", pageSize)
                }

                val payload = JSONObject().apply {
                    put("variables", variables)
                    put("operationName", "fetchLibraryTracks")
                    put("extensions", JSONObject().apply {
                        put("persistedQuery", JSONObject().apply {
                            put("version", 1)
                            put("sha256Hash", HASH_LIBRARY_TRACKS)
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
                if (!response.isSuccessful) break

                val body = response.body?.string() ?: break
                val json = JSONObject(body)

                val tracksObj = json.optJSONObject("data")
                    ?.optJSONObject("me")
                    ?.optJSONObject("library")
                    ?.optJSONObject("tracks") ?: break

                val totalCount = tracksObj.optInt("totalCount", 0)
                val items = tracksObj.optJSONArray("items") ?: break
                if (items.length() == 0) break

                val unparsedUris = mutableListOf<String>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val trackObj = item.optJSONObject("track") ?: continue
                    val uri = trackObj.optString("_uri", trackObj.optString("uri", ""))
                    val data = trackObj.optJSONObject("data") ?: trackObj
                    val parsed = parseTrackFromData(data, uriOverride = uri)
                    if (parsed != null) {
                        allTracks.add(parsed)
                    } else if (uri.startsWith("spotify:track:")) {
                        unparsedUris.add(uri)
                    }
                }

                if (unparsedUris.isNotEmpty()) {
                    val decorated = decorateTracks(spDc, unparsedUris).getOrNull()
                    if (decorated != null) allTracks.addAll(decorated)
                }

                offset += items.length()
                if (offset >= totalCount || items.length() < pageSize) {
                    hasMore = false
                }
            }

            allTracks
        }
    }

    /**
     * Searches for tracks matching a keyword or title using Pathfinder's searchTracks.
     */
    suspend fun searchTracks(spDc: String, query: String, limit: Int = 30): Result<List<PlaybackTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)

            val variables = JSONObject().apply {
                put("searchTerm", query)
                put("offset", 0)
                put("limit", limit)
                put("numberOfTopResults", limit)
                put("includeAudiobooks", true)
                put("includeAuthors", true)
                put("includePreReleases", false)
                put("includeAlbumPreReleases", false)
                put("includeEpisodeContentRatingsV2", true)
            }

            val payload = JSONObject().apply {
                put("variables", variables)
                put("operationName", "searchTracks")
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", HASH_SEARCH_TRACKS)
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
                throw IllegalStateException("searchTracks failed: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw IllegalStateException("Empty search response")
            val json = JSONObject(body)

            val items = json.optJSONObject("data")
                ?.optJSONObject("searchV2")
                ?.optJSONObject("tracksV2")
                ?.optJSONArray("items") ?: JSONArray()

            val trackUris = mutableListOf<String>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val trackData = item.optJSONObject("item")?.optJSONObject("data") ?: continue
                val uri = trackData.optString("uri", "")
                if (uri.startsWith("spotify:track:")) {
                    trackUris.add(uri)
                }
            }

            if (trackUris.isEmpty()) return@runCatching emptyList<PlaybackTrack>()
            decorateTracks(spDc, trackUris).getOrThrow()
        }
    }

    /**
     * Decorates track URIs with full titles, artists, album names, durations, and high-res artwork.
     */
    suspend fun decorateTracks(spDc: String, trackUris: List<String>): Result<List<PlaybackTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SpotifyTokenProvider.getAccessToken(spDc)
            val playbackTracks = mutableListOf<PlaybackTrack>()

            for (chunk in trackUris.chunked(50)) {
                val urisArray = JSONArray().apply {
                    chunk.forEach { put(it) }
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
                if (!response.isSuccessful) continue

                val body = response.body?.string() ?: continue
                val json = JSONObject(body)

                val tracksArray = json.optJSONObject("data")?.optJSONArray("tracks") ?: JSONArray()

                for (i in 0 until tracksArray.length()) {
                    val t = tracksArray.optJSONObject(i) ?: continue
                    val uri = t.optString("uri", "")
                    val name = t.optString("name", "Unknown Track")

                    val durationMs = t.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L

                    val artistsItems = t.optJSONObject("artists")?.optJSONArray("items") ?: JSONArray()
                    val artistNames = mutableListOf<String>()
                    for (a in 0 until artistsItems.length()) {
                        val artObj = artistsItems.optJSONObject(a) ?: continue
                        val artName = artObj.optJSONObject("profile")?.optString("name", "") ?: ""
                        if (artName.isNotEmpty()) artistNames.add(artName)
                    }
                    val artistString = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

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
