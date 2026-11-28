package com.quackify.app.lastfm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class LastFmTrack(
    val identity: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L
)

class LastFmPlaybackScrobbler(
    context: Context,
    private val api: LastFmApiClient,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    private var activeIdentity: String? = null
    private var startedAtSeconds = 0L
    private var nowPlayingSent = false
    private var scrobbleSent = false

    fun onPlaybackUpdate(
        track: LastFmTrack?,
        isPlaying: Boolean,
        positionMs: Long,
        sessionKey: String?
    ) {
        if (track == null || track.identity.isBlank() ||
            track.title.isBlank() || track.artist.isBlank()
        ) {
            reset()
            return
        }

        val settings = LastFmSettings.load(appContext)

        if (!settings.scrobblingEnabled) {
            reset()
            return
        }

        if (activeIdentity != track.identity) {
            activeIdentity = track.identity
            startedAtSeconds = 0L
            nowPlayingSent = false
            scrobbleSent = false
        }

        if (!isPlaying || sessionKey.isNullOrBlank()) return

        if (startedAtSeconds == 0L) {
            startedAtSeconds = System.currentTimeMillis() / 1000L
        }

        val identity = track.identity
        val baseParameters = mutableMapOf(
            "sk" to sessionKey,
            "track" to track.title,
            "artist" to track.artist
        )

        track.album?.takeIf { it.isNotBlank() }?.let {
            baseParameters["album"] = it
        }

        if (track.durationMs > 0L) {
            baseParameters["duration"] =
                (track.durationMs / 1000L).toString()
        }

        if (settings.nowPlayingEnabled && !nowPlayingSent) {
            nowPlayingSent = true
            scope.launch {
                try {
                    api.call("track.updateNowPlaying", baseParameters)
                } catch (_: Exception) {
                    if (activeIdentity == identity) {
                        nowPlayingSent = false
                    }
                }
            }
        }

        if (scrobbleSent ||
            track.durationMs < settings.minimumTrackDurationMs ||
            track.durationMs <= 0L
        ) return

        val percentageThreshold =
            track.durationMs * settings.thresholdPercent / 100L

        val threshold = minOf(
            percentageThreshold,
            settings.maximumScrobbleTimeMs
        )

        if (positionMs < threshold) return

        scrobbleSent = true

        val scrobbleParameters = baseParameters.toMutableMap().apply {
            put("timestamp", startedAtSeconds.toString())
        }

        scope.launch {
            try {
                api.call("track.scrobble", scrobbleParameters)
            } catch (_: Exception) {
                if (activeIdentity == identity) {
                    scrobbleSent = false
                }
            }
        }
    }

    fun reset() {
        activeIdentity = null
        startedAtSeconds = 0L
        nowPlayingSent = false
        scrobbleSent = false
    }
}
