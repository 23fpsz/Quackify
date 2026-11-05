package com.quackify.backend.connect.command

import java.util.concurrent.ConcurrentHashMap
import com.quackify.backend.connect.cluster.ClusterState

object TrackUid {

    private const val LIMIT = 512

    private val known = ConcurrentHashMap<String, String>()

    fun get(uri: String): String = known[uri].orEmpty()

    fun remember(uri: String, uid: String) {
        if (uri.isBlank() || uid.isBlank()) return
        if (known.size >= LIMIT) known.clear()
        known[uri] = uid
    }

    fun learn(cluster: ClusterState?) {
        if (cluster == null) return
        learn(listOf(cluster.track) + cluster.nextTracks + cluster.prevTracks)
    }

    fun learn(command: PlaybackCommand?) {
        if (command == null) return
        command.states.forEach { state ->
            val uri = command.tracks.getOrNull(state.trackIndex)?.uri.orEmpty()
            remember(uri, state.trackUid)
        }
    }

    fun learn(tracks: List<com.quackify.backend.connect.cluster.ClusterTrack>) {
        tracks.forEach { remember(it.uri, it.uid) }
    }

    fun forget() {
        known.clear()
    }
}
