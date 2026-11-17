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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
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

internal object DrmLevel {

    private const val PROP_SECURITY_LEVEL = "securityLevel"

    @Volatile
    var applied = "auto"

    fun provider(): ExoMediaDrm.Provider = ExoMediaDrm.Provider { uuid ->
        val drm = FrameworkMediaDrm.newInstance(uuid)
        val forced = runCatching {
            drm.setPropertyString(PROP_SECURITY_LEVEL, "L3")
            drm.getPropertyString(PROP_SECURITY_LEVEL)
        }.getOrElse { "L1" }
        applied = forced
        log.i("drm security level '$forced' for ${uuid.toString().take(8)}")
        drm
    }
}

private fun shape(bytes: ByteArray, depth: Int = 0, limit: Int = 3): String = runCatching {
    val out = StringBuilder()
    val reader = Reader(bytes)
    var count = 0
    while (!reader.done() && count < 24) {
        count++
        val (field, wire) = reader.tag()
        when (wire) {
            0 -> out.append("$field=").append(reader.varint()).append(' ')
            1 -> {
                reader.skip(1)
                out.append("$field! ").append(' ')
            }
            2 -> {
                val raw = reader.bytes()
                val nested = raw.isNotEmpty() && raw.size > 4 && looksNested(raw)
                if (depth < limit && nested) {
                    out.append("$field{").append(shape(raw, depth + 1, limit)).append("} ")
                } else {
                    out.append("$field#").append(raw.size).append(' ')
                }
            }
            5 -> {
                reader.skip(5)
                out.append("$field~ ")
            }
            else -> return@runCatching out.append("$field?wire$wire").toString().trim()
        }
    }
    out.toString().trim()
}.getOrElse { "shape unreadable ${it.message}" }

private fun looksNested(raw: ByteArray): Boolean {
    val head = raw[0].toInt() and 255
    val field = head ushr 3
    val wire = head and 7
    return field in 1..15 && wire in 0..5 && wire != 3 && wire != 4
}

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
                null,
                PlayerHeaders.license(context),
                tag = "audio-license",
            )
        }
        val body = response?.takeIf { it.ok }
        if (body == null) {
            LicenseEndpoint.invalidate(context)
            if (response?.code != 403) {
                LicenseGate.hold(response?.code ?: 0, (response?.retryAfterSeconds ?: 0L) * 1000L)
            }
            throw IOException("audio licence ${response?.code ?: "failed"}")
        }
        LicenseGate.clear()
        log.i("audio licence ${body.bytes.size}B level=${DrmLevel.applied} ${LicencePolicy.describe(body.bytes)}")
        log.i("audio licence shape ${shape(body.bytes)}")
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
    private val scope: CoroutineScope,
    var onEvent: (PlayerEvent) -> Unit,
) {

    private var kit: DefaultMediaSourceFactory? = null

    private val licensedFiles = java.util.LinkedHashSet<String>()

    private val offlineKeySets = java.util.concurrent.ConcurrentHashMap<String, String>()

    val hasMedia: Boolean get() = loaded

    private val main = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null

    private var drmManager: DefaultDrmSessionManager? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var started = false

    @Volatile
    private var announced = false

    @Volatile
    private var lastItemUri = ""

    private val itemDurations = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    var durationMs: Long = 0L
        private set

    val currentUri: String
        get() = runOnMain { player?.currentMediaItem?.mediaId }.orEmpty()

    val hasQueuedNext: Boolean
        get() = runOnMain { player?.let { it.mediaItemCount > it.currentMediaItemIndex + 1 } } ?: false

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

    fun durationFor(uri: String): Long = itemDurations[uri] ?: 0L

    suspend fun prepare(
        source: AudioSource,
        uri: String,
        fromMs: Long,
        autoplay: Boolean,
        gainDb: Double,
    ): Boolean =
        withContext(Dispatchers.Main) {
            val totalMs = source.seektable.totalDuration * 1000L / source.seektable.timescale
            val factory = ensureKit()
            val keySetId = offlineKeySetFor(source)
            if (keySetId != null) offlineKeySets[uri] = source.fileId else offlineKeySets.remove(uri)
            val reused = !licensedFiles.add(source.fileId)
            log.i(
                if (reused) "licence reused for '${source.fileId.take(12)}' and its ${licensedFiles.size} sibling(s)"
                else "audio drm session asked for '${source.fileId.take(12)}'"
            )
            gainFactor = Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(0f, 1f)
            durationMs = totalMs
            announced = false
            lastItemUri = uri
            if (uri.isNotBlank()) itemDurations[uri] = totalMs
            // The player instance survives track changes: swapping the media
            // source keeps the renderers, the codec and the audio sink warm, so
            // only the first play pays the ExoPlayer build cost.
            val live = player ?: ExoPlayer.Builder(context)
                .setMediaSourceFactory(factory)
                .build()
                .also {
                    it.addListener(listener)
                    player = it
                }
            live.setMediaSource(factory.createMediaSource(itemFor(uri, source, keySetId)))
            live.prepare()
            live.seekTo(fromMs.coerceIn(0L, (totalMs - 1000L).coerceAtLeast(0L)))
            live.playWhenReady = autoplay
            started = autoplay
            loaded = true
            log.i("audio engine ready '${source.fileId.take(12)}' ${totalMs}ms at ${fromMs}ms " +
                    "autoplay=$autoplay reused=${player !== live}"
            )
            true
        }

    private fun ensureKit(): DefaultMediaSourceFactory {
        kit?.let { return it }
        // The song cache sits between ExoPlayer and the CDN: online reads
        // seed it while streaming, offline reads come back from it, and
        // the custom key (the track uri) survives CDN url rotation.
        val factory = DefaultMediaSourceFactory(
            SongCache.factory(context).setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
        )
        val manager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, DrmLevel.provider())
            .setMultiSession(true)
            .build(AudioDrmCallback(context) { LicenseEndpoint.of(context) })
        factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { manager })
        drmManager = manager
        kit = factory
        log.i("drm kit built, one player and one session manager for the whole run")
        return factory
    }


    /**
     * A cached offline key set lets the DRM session restore its keys without
     * a license roundtrip; an unknown track pays one offline acquisition that
     * then serves every later play. Re-throws the rate-limit refusal so the
     * caller surfaces it exactly like the online path does.
     */
    private suspend fun offlineKeySetFor(source: AudioSource): ByteArray? {
        if (source.seektable.pssh.isBlank()) return null
        LicenseStore.keySetIdFor(context, source.fileId)?.let {
            log.i("offline licence restored for '${source.fileId.take(12)}'")
            return it
        }
        return OfflineLicence.acquire(context, source)
    }

    /**
     * Hands the queue the next track with its licence already settled: the
     * item joins the playlist carrying a key set id whenever one is available,
     * so the prebuffered track restores keys instead of asking the endpoint
     * again at transition time. [whileOn] is the uri the engine must still be
     * on when the item lands - the licence roundtrip can outlive a skip.
     */
    suspend fun queueNext(uri: String, source: AudioSource, whileOn: String): Boolean {
        val keySetId = try {
            offlineKeySetFor(source)
        } catch (refusal: IOException) {
            // Rate limited while pre-licensing: queueing would only ask again
            // through the online path and burn another strike, so the normal
            // advance (with its gate-aware retry) takes over instead.
            log.w("queued licence refused, dropping the prebuffer for '$uri': ${refusal.message}")
            return false
        }
        if (keySetId != null) offlineKeySets[uri] = source.fileId else offlineKeySets.remove(uri)
        val added = runOnMain {
            val p = player ?: return@runOnMain false
            if (p.currentMediaItem?.mediaId != whileOn) return@runOnMain false
            val current = p.currentMediaItemIndex
            if (p.mediaItemCount > current + 1) p.removeMediaItems(current + 1, p.mediaItemCount)
            p.addMediaItem(itemFor(uri, source, keySetId))
            true
        } ?: false
        if (added) {
            log.i("audio queued next '$uri' '${source.fileId.take(12)}' " +
                    if (source.seektable.pssh.isNotBlank()) "drm" else "clear"
            )
        }
        return added
    }

    fun skipTo(uri: String): Boolean {
        val moved = runOnMain {
            val p = player ?: return@runOnMain false
            for (index in 0 until p.mediaItemCount) {
                if (p.getMediaItemAt(index).mediaId == uri) {
                    p.seekTo(index, 0L)
                    return@runOnMain true
                }
            }
            false
        } ?: false
        if (moved) log.i("audio skipped to '$uri'")
        return moved
    }

    private fun itemFor(uri: String, source: AudioSource, keySetId: ByteArray? = null): MediaItem {
        val encrypted = source.seektable.pssh.isNotBlank()
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(Uri.parse(source.url))
            // The cache key is the track uri, not the resolved CDN url, so a
            // re-resolved url still reads the copy already on disk.
            .setCustomCacheKey(uri)
            .apply {
                if (encrypted) {
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setForceSessionsForAudioAndVideoTracks(true)
                            .apply { if (keySetId != null) setKeySetId(keySetId) }
                            .build(),
                    )
                }
            }
            .build()
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
        onEvent?.invoke(PlayerEvent.Seeked(ms))
    }

    fun duck(level: Float) {
        duckLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    fun volume(level: Float) {
        userLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    fun retune(gainDb: Double) {
        gainFactor = Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(0f, 1f)
        applyVolume()
    }

    fun rate(mult: Float) {
        runOnMain { player?.playbackParameters = PlaybackParameters(mult.coerceIn(0.5f, 3.5f)) }
    }

    fun stop() {
        started = false
        loaded = false
        durationMs = 0L
        lastItemUri = ""
        progressJob?.cancel()
        progressJob = null
        runOnMain { player?.stop() }
    }

    fun release() {
        started = false
        loaded = false
        durationMs = 0L
        lastItemUri = ""
        progressJob?.cancel()
        progressJob = null
        runOnMain {
            player?.release()
            player = null
            drmManager?.release()
            drmManager = null
            kit = null
            licensedFiles.clear()
            offlineKeySets.clear()
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
                    onEvent?.invoke(PlayerEvent.Progress(position, durationMs))
                }
                delay(PROGRESS_MS)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val newUri = item?.mediaId.orEmpty()
            val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            if (auto && lastItemUri.isNotBlank()) {
                onEvent?.invoke(PlayerEvent.Ended(lastItemUri))
            }
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                announced = false
                lastPositionMs = 0L
                lastItemUri = newUri
                itemDurations[newUri]?.let { durationMs = it }
            }
            if (auto) {
                val ready = runOnMain { player?.playbackState == Player.STATE_READY } ?: false
                if (ready && !announced) {
                    announced = true
                    onEvent?.invoke(PlayerEvent.Started(newUri))
                    if (started) startProgress()
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> if (!announced) {
                    announced = true
                    onEvent?.invoke(PlayerEvent.Started(lastItemUri))
                    if (started) startProgress()
                }
                Player.STATE_ENDED -> {
                    lastPositionMs = durationMs
                    onEvent?.invoke(PlayerEvent.Ended(lastItemUri))
                    lastItemUri = ""
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) startProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            log.e("audio playback failed ${error.errorCodeName}: ${error.message}")
            if (error.errorCodeName.contains("DRM")) {
                val fileId = offlineKeySets.remove(lastItemUri)
                if (fileId != null) {
                    LicenseStore.forget(context, fileId)
                    log.w("dropped the offline licence for '${lastItemUri.takeLast(12)}' after a drm error")
                }
            }
            onEvent?.invoke(PlayerEvent.Failed("${error.errorCodeName}: ${error.message ?: "audio error"}"))
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
        if (!done.await(2500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            log.w("the main thread did not answer in 2500ms, giving up on the read")
        }
        return result
    }
}

internal class Reader(private val data: ByteArray) {
    private var pos = 0

    fun done(): Boolean = pos >= data.size

    fun tag(): Pair<Int, Int> {
        val value = varint()
        return (value ushr 3).toInt() to (value and 7L).toInt()
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (shift <= 63) {
            if (pos >= data.size) throw IllegalStateException("truncated varint")
            val byte = data[pos++].toInt() and 255
            result = result or ((byte and 127).toLong() shl shift)
            if (byte and 128 == 0) return result
            shift += 7
        }
        throw IllegalStateException("varint overflow")
    }

    fun bytes(): ByteArray {
        val length = varint().toInt()
        if (length < 0 || pos + length > data.size) throw IllegalStateException("truncated field")
        return data.copyOfRange(pos, pos + length).also { pos += length }
    }

    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> pos += 8
            2 -> bytes()
            5 -> pos += 4
            else -> throw IllegalStateException("wire $wire")
        }
    }
}
