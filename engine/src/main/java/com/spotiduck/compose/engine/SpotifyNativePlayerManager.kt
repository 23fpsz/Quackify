package com.spotiduck.compose.engine

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackTrack(
    val uri: String,
    val fileId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val durationMs: Long
)

data class NativePlaybackState(
    val currentTrack: PlaybackTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

class SpotifyNativePlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "SpotifyNativePlayer"
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(NativePlaybackState())
    val playbackState: StateFlow<NativePlaybackState> = _playbackState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var currentSpDc: String? = null

    /**
     * Callback fired when current track finishes playing.
     */
    var onTrackEnded: (() -> Unit)? = null

    init {
        initExoPlayer()
    }

    private fun initExoPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        val isBuffering = state == Player.STATE_BUFFERING
                        val stateStr = when(state) {
                            Player.STATE_IDLE -> "STATE_IDLE"
                            Player.STATE_BUFFERING -> "STATE_BUFFERING"
                            Player.STATE_READY -> "STATE_READY"
                            Player.STATE_ENDED -> "STATE_ENDED"
                            else -> "STATE_$state"
                        }
                        Log.i(TAG, "ExoPlayer onPlaybackStateChanged: $stateStr, position=${currentPosition}ms, duration=${duration}ms")
                        _playbackState.value = _playbackState.value.copy(
                            isBuffering = isBuffering,
                            durationMs = if (duration > 0) duration else _playbackState.value.durationMs
                        )
                        if (state == Player.STATE_ENDED) {
                            Log.i(TAG, "Track ended naturally, firing onTrackEnded")
                            onTrackEnded?.invoke()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.i(TAG, "ExoPlayer onIsPlayingChanged: $isPlaying")
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = isPlaying,
                            isBuffering = false
                        )
                        if (isPlaying) {
                            startProgressTracking()
                        } else {
                            stopProgressTracking()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "ExoPlayer onPlayerError: ${error.message} (code=${error.errorCodeName})", error)
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = error.localizedMessage
                        )
                        stopProgressTracking()
                    }
                })
            }
    }

    fun setSessionCookie(spDc: String) {
        currentSpDc = spDc
    }

    fun playTrack(track: PlaybackTrack) {
        Log.i(TAG, "playTrack called: '${track.title}' by '${track.artist}' (uri=${track.uri}, fileId=${track.fileId})")
        scope.launch {
            try {
                _playbackState.value = _playbackState.value.copy(
                    currentTrack = track,
                    isBuffering = true,
                    durationMs = track.durationMs,
                    errorMessage = null
                )

                val spDc = currentSpDc ?: throw IllegalStateException("No sp_dc session cookie configured")
                val token = SpotifyTokenProvider.getAccessToken(spDc)

                // If fileId is blank, dynamically resolve via SpotifyConnectResolver handshake
                val fileId = if (track.fileId.isNotBlank() && track.fileId.length == 40) {
                    track.fileId
                } else {
                    SpotifyConnectResolver.resolveFileId(track.uri, token)
                }
                Log.i(TAG, "Resolved fileId=$fileId for '${track.title}'")

                // Resolve CDN streaming URL directly from Spotify Akamai/Fastly CDN
                val cdnStreamUrl = SpotifyMediaResolver.resolveCdnUrl(fileId, token)
                Log.i(TAG, "Resolved CDN URL: $cdnStreamUrl")

                // Configure Widevine DRM headers and license endpoint
                val drmHeaders = mapOf(
                    "Authorization" to "Bearer $token",
                    "Origin" to "https://open.spotify.com",
                    "Referer" to "https://open.spotify.com/",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )

                val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri("https://gae2-spclient.spotify.com/widevine-license/v1/audio/license")
                    .setLicenseRequestHeaders(drmHeaders)
                    .setMultiSession(true)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(cdnStreamUrl)
                    .setDrmConfiguration(drmConfig)
                    .setMimeType(MimeTypes.AUDIO_MP4)
                    .build()

                exoPlayer?.let { player ->
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    Log.i(TAG, "ExoPlayer prepared and started play() for '${track.title}'")
                }

            } catch (e: Exception) {
                Log.e(TAG, "playTrack failed for '${track.title}': ${e.message}", e)
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "Playback failed: ${e.message}"
                )
            }
        }
    }

    fun resume() {
        Log.i(TAG, "resume()")
        exoPlayer?.play()
    }

    fun pause() {
        Log.i(TAG, "pause()")
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        Log.i(TAG, "seekTo($positionMs)")
        exoPlayer?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = player.currentPosition,
                        durationMs = if (player.duration > 0) player.duration else _playbackState.value.durationMs
                    )
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracking()
        exoPlayer?.release()
        exoPlayer = null
    }
}
