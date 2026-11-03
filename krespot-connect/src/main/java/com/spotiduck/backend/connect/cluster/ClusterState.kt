package com.spotiduck.backend.connect.cluster

import com.spotiduck.backend.model.ConnectDevice
import org.json.JSONObject

private const val AD_PREFIX = "spotify:ad:"

private const val DELIMITER = "spotify:delimiter"

private const val META_PREFIX = "spotify:meta:"

internal const val QUEUE_PROVIDER = "queue"

private const val LOCAL_ORIGIN = "local"

internal const val FREE_LICENSE = "mft"

private val EMPTY_TRACK = ClusterTrack(
    uri = "",
    uid = "",
    title = "",
    artistName = "",
    artistUri = "",
    imageUrl = "",
    albumTitle = "",
    albumUri = "",
    durationMs = 0L,
    explicit = false,
    creativeId = "",
)

internal const val AUTOPLAY_PROVIDER = "autoplay"

data class AdBreak(
    val maxSecondsRemaining: Int,
    val positionInBreak: Int,
    val totalEstimate: Int,
) {
    val isActive: Boolean get() = totalEstimate > 0
}

data class ClusterTrack(
    val uri: String,
    val uid: String,
    val title: String,
    val artistName: String,
    val artistUri: String,
    val imageUrl: String,
    val albumTitle: String,
    val albumUri: String,
    val durationMs: Long,
    val explicit: Boolean,
    val creativeId: String,
    val provider: String = "",
    val queued: Boolean = false,
    val hidden: Boolean = false,
    val hiddenInQueue: Boolean = false,
    val removed: Boolean = false,
    val raw: String = "",
) {
    val isAd: Boolean get() = uri.startsWith(AD_PREFIX)

    val isMeta: Boolean get() = uri == DELIMITER || uri.startsWith(META_PREFIX)

    val isQueued: Boolean get() = queued || provider == QUEUE_PROVIDER

    val isAutoplay: Boolean get() = provider == AUTOPLAY_PROVIDER

    val isVisible: Boolean get() = !hidden && !hiddenInQueue && !removed && !isMeta
}

data class ClusterState(
    val track: ClusterTrack,
    val nextTracks: List<ClusterTrack>,
    val prevTracks: List<ClusterTrack>,
    val contextUri: String,
    val contextIndex: Int,
    val pageIndex: Int,
    val playbackId: String,
    val sessionId: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val disallowSeeking: Boolean,
    val adBreak: AdBreak?,
    val updateReason: String,
    val queueRevision: String = "",
    val activeDeviceId: String = "",
    val contextDescription: String = "",
    val devices: List<ConnectDevice> = emptyList(),
    val localDeviceId: String = "",
    val shuffle: Boolean = false,
    val repeatContext: Boolean = false,
    val repeatTrack: Boolean = false,
) {
    val isAd: Boolean get() = track.isAd

    val declaredActive: ConnectDevice? get() = devices.firstOrNull { it.active }

    val localDevice: ConnectDevice? get() = devices.firstOrNull { it.local }

    val activeDevice: ConnectDevice?
        get() = declaredActive ?: localDevice.takeIf { devices.size == 1 }

    val activeInferred: Boolean
        get() = declaredActive == null && activeDevice != null

    val licenseRestricted: Boolean
        get() = activeDevice?.license.equals(FREE_LICENSE, ignoreCase = true)

    val remote: List<ConnectDevice>
        get() {
            val at = activeDevice ?: return devices
            return devices.filterNot { it.id == at.id }
        }

    val queued: List<ClusterTrack> get() = nextTracks.filter { it.isVisible && it.isQueued }

    val fromContext: List<ClusterTrack> get() = nextTracks.filter { it.isVisible && !it.isQueued }

    fun window(): List<ClusterTrack> = prevTracks.reversed() + track + nextTracks

    fun pivot(): Int = prevTracks.size

    fun indexOf(position: Int): Int = contextIndex - prevTracks.size + position

    fun anchorOf(uri: String): Int {
        if (uri.isBlank()) return pivot()
        val at = window().indexOfFirst { it.uri == uri }
        return if (at < 0) pivot() else at
    }

    fun next(): ClusterTrack? = nextTracks.firstOrNull()

    fun movedTo(uri: String): ClusterState? {
        val window = window()
        val at = window.indexOfFirst { it.uri == uri }
        if (at < 0 || at == pivot()) return null
        return copy(
            track = window[at],
            prevTracks = window.take(at).reversed(),
            nextTracks = window.drop(at + 1),
            contextIndex = indexOf(at),
            positionMs = 0L,
            durationMs = window[at].durationMs,
        )
    }
}

private fun JSONObject.text(name: String): String =
    if (isNull(name)) "" else optString(name, "")

private fun JSONObject.int(name: String): Int = text(name).toIntOrNull() ?: 0

private fun JSONObject.long(name: String): Long = text(name).toLongOrNull() ?: 0L

private fun JSONObject.bool(name: String): Boolean = text(name).equals("true", ignoreCase = true)

private fun parseTrack(node: JSONObject?): ClusterTrack? {
    if (node == null) return null
    val metadata = node.optJSONObject("metadata") ?: JSONObject()
    val uri = node.text("uri")
    if (uri.isBlank()) return null
    return ClusterTrack(
        uri = uri,
        uid = node.text("uid"),
        title = metadata.text("title"),
        artistName = metadata.text("artist_name"),
        artistUri = metadata.text("artist_uri"),
        imageUrl = metadata.text("image_url").ifBlank { metadata.text("image_large_url") },
        albumTitle = metadata.text("album_title"),
        albumUri = metadata.text("album_uri"),
        durationMs = metadata.long("duration"),
        explicit = metadata.bool("is_explicit"),
        creativeId = metadata.text("creative_id"),
        provider = node.text("provider"),
        queued = metadata.text("is_queued") == "true",
        hidden = metadata.text("hidden") == "true",
        hiddenInQueue = metadata.text("hidden_in_queue") == "true",
        removed = (node.optJSONArray("removed")?.length() ?: 0) > 0,
        raw = metadata.toString(),
    )
}

private fun parseTracks(node: org.json.JSONArray?): List<ClusterTrack> {
    if (node == null) return emptyList()
    return (0 until node.length()).mapNotNull { parseTrack(node.optJSONObject(it)) }
}

private fun parseAdBreak(node: JSONObject?): AdBreak? {
    if (node == null || node.length() == 0) return null
    return AdBreak(
        maxSecondsRemaining = node.int("max_seconds_remaining"),
        positionInBreak = node.int("position_in_current_ad_break"),
        totalEstimate = node.int("total_ads_in_break_estimate"),
    )
}

object ClusterParser {

    fun parse(payload: String, localDeviceId: String = ""): ClusterState? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val cluster = root.optJSONObject("cluster") ?: root
        val player = cluster.optJSONObject("player_state") ?: return null
        val track = parseTrack(player.optJSONObject("track")) ?: EMPTY_TRACK
        val restrictions = player.optJSONObject("restrictions")
        val options = player.optJSONObject("options") ?: JSONObject()
        val index = player.optJSONObject("index")
        val declared = root.text("active_device_id")
        val origin = player.optJSONObject("play_origin")?.text("device_identifier").orEmpty()
        val activeId = declared.ifBlank {
            if (origin == LOCAL_ORIGIN) localDeviceId else ""
        }
        return ClusterState(
            track = track,
            nextTracks = parseTracks(player.optJSONArray("next_tracks")),
            prevTracks = parseTracks(player.optJSONArray("prev_tracks")),
            contextUri = player.text("context_uri"),
            contextIndex = index?.optInt("track", 0) ?: 0,
            pageIndex = index?.optInt("page", 0) ?: 0,
            playbackId = player.text("playback_id"),
            sessionId = player.text("session_id"),
            isPlaying = player.optBoolean("is_playing", false),
            positionMs = player.optLong("position_as_of_timestamp", 0L),
            durationMs = player.optLong("duration", 0L),
            disallowSeeking = (restrictions?.optJSONArray("disallow_seeking_reasons")?.length() ?: 0) > 0,
            adBreak = parseAdBreak(player.optJSONObject("ad_break_context")),
            updateReason = root.text("update_reason"),
            queueRevision = player.text("queue_revision"),
            activeDeviceId = activeId,
            contextDescription = player.optJSONObject("context_metadata")?.text("context_description").orEmpty(),
            devices = parseDevices(cluster, activeId, localDeviceId),
            localDeviceId = localDeviceId,
            shuffle = options.bool("shuffling_context"),
            repeatContext = options.bool("repeating_context"),
            repeatTrack = options.bool("repeating_track"),
        )
    }
}
