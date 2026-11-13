/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (into whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch

private val log = Logs.of(Logs.Module.PLAYER)

private const val PROGRESS_MS = 500L

internal class AudioDrmCallback(
    private val context: Context,
    private val licenseUrl: suspend () -> String,
) : MediaDrmCallback {

    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response {
        val url = runBlocking { licenseUrl() }
        val response = runBlocking {
            Net.postBlob(
                url,
                request.data,
                "application/octet-stream",
                PlayerHeaders.license(context),
                tag = "audio-license",
            )
        }
        val body = response?.takeIf { it.ok }
        if (body == null) {
            LicenseGate.hold(0L)
            throw IOException("audio licence ${response?.code ?: "failed"}")
        }
        LicenseGate.clear()
        log.i("audio licence ${body.bytes.size}B")
        return MediaDrmCallback.Response(body.bytes)
    }

    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): MediaDrmCallback.Response {
        val signed = String(request.data, Charsets.UTF_8)
        val separator = if (request.defaultUrl.contains("?")) "&" else "?"
        val url = request.defaultUrl + separator + "signedRequest=" + java.net.URLEncoder.encode(signed, "UTF-8")
        val response = runBlocking {
            Net.postBlob(url, ByteArray(0), "application/octet-stream", emptyMap(), tag = "provision")
        }
        val body = response?.takeIf { it.ok } ?: throw IOException("provision ${response?.code ?: "failed"}")
        return MediaDrmCallback.Response(body.bytes)
    }
}

internal class AudioEngine(
    private val context: Context,
    private val spclient: () -> String,
    private val scope: CoroutineScope,
    private val onEvent: (PlayerEvent) -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null

    @Volatile
    private var started = false

    @Volatile
    private var announced = false

    @Volatile
    var durationMs: Long = 0L
        private set

    @Volatile
    private var progressJob: Job? = null

    @Volatile
    private var lastPositionMs = 0L

    @Volatile
    private var duckLevel = 1f

    @Volatile
    private var userLevel = 1f

    @Volatile
    private var gainFactor = 1f

    val currentPositionMs: Long
        get() = runOnMain { player?.currentPosition } ?: lastPositionMs

    val isPlaying: Boolean
        get() = runOnMain { player?.isPlaying } ?: false

    val isBuffering: Boolean
        get() = runOnMain { player?.playbackState == Player.STATE_BUFFERING } ?: false

    suspend fun prepare(source: AudioSource, fromMs: Long, autoplay: Boolean, gainDb: Double): Boolean =
        withContext(Dispatchers.Main) {
            release()
            val totalMs = source.seektable.totalDuration * 1000L / source.seektable.timescale
            val encrypted = source.seektable.pssh.isNotBlank()
            val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
            val mediaItem: MediaItem
            if (encrypted) {
                val callback = AudioDrmCallback(context) { LicenseEndpoint.of(context, spclient()) }
                var cachedManager: DrmSessionManager? = null
                factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { _: MediaItem ->
                    val existing = cachedManager
                    if (existing != null) return@DrmSessionManagerProvider existing
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(callback)
                        .also { cachedManager = it }
                })
                mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(source.url))
                    .setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setForceSessionsForAudioAndVideoTracks(true)
                            .build(),
                    )
                    .build()
                log.i("audio drm session asked for '${source.fileId.take(12)}'")
            } else {
                log.i("clear content, no drm session asked")
                mediaItem = MediaItem.fromUri(Uri.parse(source.url))
            }
            gainFactor = Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(0f, 1f)
            durationMs = totalMs
            announced = false
            val created = ExoPlayer.Builder(context)
                .setMediaSourceFactory(factory)
                .build()
            created.addListener(listener)
            created.setMediaSource(factory.createMediaSource(mediaItem))
            player = created
            created.prepare()
            created.seekTo(fromMs.coerceIn(0L, (totalMs - 1000L).coerceAtLeast(0L)))
            created.playWhenReady = autoplay
            started = autoplay
            log.i("audio engine ready '${source.fileId.take(12)}' ${totalMs}ms at ${fromMs}ms autoplay=$autoplay")
            true
        }

    fun play() {
        started = true
        runOnMain { player?.play() }
        startProgress()
    }

    fun pause() {
        started = false
        runOnMain { player?.pause() }
    }

    fun resume() {
        started = true
        runOnMain { player?.play() }
        startProgress()
    }

    fun seekTo(ms: Long) {
        lastPositionMs = ms
        runOnMain { player?.seekTo(ms) }
        onEvent(PlayerEvent.Seeked(ms))
    }

    fun duck(level: Float) {
        duckLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    fun volume(level: Float) {
        userLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    fun rate(mult: Float) {
        runOnMain { player?.playbackParameters = PlaybackParameters(mult.coerceIn(0.5f, 3.5f)) }
    }

    fun stop() {
        started = false
        progressJob?.cancel()
        progressJob = null
        runOnMain {
            player?.stop()
            player?.release()
            player = null
        }
    }

    private fun applyVolume() {
        runOnMain { player?.volume = (duckLevel * userLevel * gainFactor).coerceIn(0f, 1f) }
    }

    private fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && started) {
                val position = runOnMain { player?.currentPosition } ?: lastPositionMs
                if (position >= 0L) {
                    lastPositionMs = position
                    onEvent(PlayerEvent.Progress(position, durationMs))
                }
                delay(PROGRESS_MS)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> if (!announced) {
                    announced = true
                    onEvent(PlayerEvent.Started)
                    if (started) startProgress()
                }
                Player.STATE_ENDED -> {
                    lastPositionMs = durationMs
                    onEvent(PlayerEvent.Ended)
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) startProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            log.e("audio playback failed ${error.errorCodeName}: ${error.message}")
            onEvent(PlayerEvent.Failed(error.message ?: "audio error"))
        }
    }

    private fun release() {
        progressJob?.cancel()
        progressJob = null
        runOnMain {
            player?.release()
            player = null
        }
    }

    private inline fun <T> runOnMain(crossinline block: () -> T?): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        val done = CountDownLatch(1)
        main.post {
            result = block()
            done.countDown()
        }
        done.await()
        return result
    }
}
