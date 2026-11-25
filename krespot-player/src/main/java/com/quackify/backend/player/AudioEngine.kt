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
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.store.AppPrefs
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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

private val log = Logs.of(Logs.Module.PLAYER)

private const val PROGRESS_MS = 500L

/** A buffer that neither advances nor errors for this long while play is wanted is a dead stream. */
private const val STALL_LIMIT_MS = 4_000L

/** Position movement below this counts as standing still. */
private const val STALL_JITTER_MS = 250L

/** Shorter measured tails are treated as measurement noise, not a trim. */
private const val MIN_TAIL_TRIM_MS = 500L

private const val CLIENT_STORE = "quackify.client"

private const val CROSSFADE_MS_PREF = "crossfadeMs"

private const val AUTOMIX_PREF = "automix"

private const val MAX_CROSSFADE_MS = 12_000L

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
            if (response?.code == 429) {
                com.quackify.backend.core.net.ClientToken.resetDeviceId(context)
            }
            if (response?.code != 403) {
                LicenseGate.hold(response?.code ?: 0, (response?.retryAfterSeconds ?: 0L) * 1000L)
            }
            throw IOException("audio licence ${response?.code ?: "failed"}")
        }
        LicenseGate.clear()
        // A streaming licence with persist=0 is the server saying this client
        // never gets persistable licences: let the offline path stand down
        // instead of it burning a 400 on every first play.
        LicencePolicy.policyOf(body.bytes)?.takeIf { it[2] == 0L }?.let {
            OfflineLicence.noteStreamingOnly(context)
        }
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

    // A DefaultDrmSessionManager is refcounted and pinned to one playback
    // looper, so sharing one between the session and spare players lets their
    // interleaved prepare and release calls drop the MediaDrm while a period
    // on the other player is still preparing: every player keeps its own kit.
    private class Kit(val factory: DefaultMediaSourceFactory, val manager: DefaultDrmSessionManager)

    private var sessionKit: Kit? = null

    private var spareKit: Kit? = null

    private val licensedFiles = java.util.LinkedHashSet<String>()

    private val offlineKeySets = java.util.concurrent.ConcurrentHashMap<String, String>()

    val hasMedia: Boolean get() = loaded

    private val main = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null

    private var sparePlayer: ExoPlayer? = null

    private var crossfade: CrossfadeEngine? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var started = false

    @Volatile
    private var announced = false

    @Volatile
    private var lastItemUri = ""

    /**
     * The measured silent tail of the item the session is on, as uri to ms of
     * quiet. The trim only ever acts past the measured audible end and only
     * when no blend owns the ending, so a measurement that arrives too late
     * for the crossfade still ends the track at its audible end instead of
     * decaying into dead air.
     */
    @Volatile
    private var tailSilence: Pair<String, Long>? = null

    private val itemDurations = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    var durationMs: Long = 0L
        private set

    val currentUri: String
        get() = runOnMain { player?.currentMediaItem?.mediaId }.orEmpty()

    val hasQueuedNext: Boolean
        get() {
            val queued = runOnMain {
                val p = player ?: return@runOnMain false
                p.mediaItemCount > p.currentMediaItemIndex + 1
            } ?: false
            return queued || crossfade?.hasHold() == true
        }

    @Volatile
    private var progressJob: Job? = null

    @Volatile
    private var lastPositionMs = 0L

    @Volatile
    private var stallLastPositionMs = 0L

    @Volatile
    private var stallSinceWallMs = 0L

    @Volatile
    private var stallNotified = false

    @Volatile
    private var duckLevel = 1f

    @Volatile
    private var userLevel = 1f

    @Volatile
    private var gainFactor = 1f

    @Volatile
    private var spareGainFactor = 1f

    @Volatile
    private var userRate = 1f

    val currentPositionMs: Long
        get() = runOnMain { player?.currentPosition } ?: lastPositionMs

    val isPlaying: Boolean
        get() = runOnMain { player?.isPlaying } ?: false

    val isBuffering: Boolean
        get() = runOnMain { player?.playbackState == Player.STATE_BUFFERING } ?: false

    fun decoderFormat(): DecoderFormat? = runOnMain { player?.decoderAudioFormat() }

    fun durationFor(uri: String): Long = itemDurations[uri] ?: 0L

    suspend fun prepare(
        source: AudioSource,
        uri: String,
        fromMs: Long,
        autoplay: Boolean,
        gainDb: Double,
    ): Boolean =
        withContext(Dispatchers.Main) {
            crossfade?.stop()
            spareGainFactor = 1f
            val totalMs = source.seektable.totalDuration * 1000L / source.seektable.timescale
            val factory = ensureSessionKit().factory
            val keySetId = offlineKeySetFor(source)
            if (keySetId != null) offlineKeySets[uri] = source.fileId else offlineKeySets.remove(uri)
            val reused = !licensedFiles.add(source.fileId)
            log.i(
                if (reused) "licence reused for '${source.fileId.take(12)}' and its ${licensedFiles.size} sibling(s)"
                else "audio drm session asked for '${source.fileId.take(12)}'"
            )
            gainFactor = 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
            durationMs = totalMs
            announced = false
            lastItemUri = uri
            tailSilence = null
            resetStall()
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

    private fun newKit(role: String): Kit {
        // The routed stack sits between ExoPlayer and the wire: catalogue
        // reads pass through the song cache (online reads seed it while
        // streaming, offline reads come back from it, and the custom key -
        // the track uri - survives CDN url rotation), alternate-rendition
        // reads open the stream buffer instead, so one session player serves
        // both cuts of a track.
        val factory = DefaultMediaSourceFactory(RoutedSources(context))
        val manager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, DrmLevel.provider())
            .setMultiSession(true)
            .setSessionKeepaliveMs(600_000)
            .build(AudioDrmCallback(context) { LicenseEndpoint.of(context) })
        factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { manager })
        log.i("$role drm kit built")
        return Kit(factory, manager)
    }

    private fun ensureSessionKit(): Kit =
        sessionKit ?: newKit("session").also { sessionKit = it }

    private fun ensureSpareKit(): Kit =
        spareKit ?: newKit("spare").also { spareKit = it }


    /**
     * A cached offline key set lets the DRM session restore its keys without
     * a license roundtrip; an unknown track pays one offline acquisition that
     * then serves every later play. Re-throws the rate-limit refusal so the
     * caller surfaces it exactly like the online path does.
     */
    private suspend fun offlineKeySetFor(source: AudioSource): ByteArray? {
        return null
    }

    /**
     * Hands the queue the next track with its licence already settled: the
     * item joins the playlist carrying a key set id whenever one is available,
     * so the prebuffered track restores keys instead of asking the endpoint
     * again at transition time. [whileOn] is the uri the engine must still be
     * on when the item lands - the licence roundtrip can outlive a skip.
     */
    suspend fun queueNext(
        uri: String,
        source: AudioSource,
        whileOn: String,
        gainDb: Double,
        spec: TransitionSpec?,
    ): Boolean {
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
        val totalMs = source.seektable.totalDuration * 1000L / source.seektable.timescale
        val item = itemFor(uri, source, keySetId)
        val heldForFade = spec != null || configuredCrossfadeMs() > 0L
        val added = runOnMain {
            val p = player ?: return@runOnMain false
            if (p.currentMediaItem?.mediaId != whileOn) return@runOnMain false
            if (heldForFade) {
                true
            } else {
                val current = p.currentMediaItemIndex
                if (p.mediaItemCount > current + 1) p.removeMediaItems(current + 1, p.mediaItemCount)
                p.addMediaItem(item)
                true
            }
        } ?: false
        if (!added) return false
        if (uri.isNotBlank()) itemDurations[uri] = totalMs
        if (heldForFade) {
            spareGainFactor = 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
            crossfadeEngine()?.hold(item, totalMs, spec)
            log.i(
                "audio held next '$uri' '${source.fileId.take(12)}' for a crossfade " +
                    if (source.seektable.pssh.isNotBlank()) "drm" else "clear"
            )
        } else {
            log.i("audio queued next '$uri' '${source.fileId.take(12)}' " +
                    if (source.seektable.pssh.isNotBlank()) "drm" else "clear"
            )
        }
        return true
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

    /**
     * Plays the alternate stream for a track on the same session player as
     * the catalogue files: the item carries no licence and a rendition-keyed
     * cache key, so its loads route to the stream buffer while the queue,
     * the crossfade and the blend logic keep working off the track uri.
     */
    suspend fun prepareAlternate(
        uri: String,
        stream: YtStream,
        fromMs: Long,
        autoplay: Boolean,
    ): Boolean =
        withContext(Dispatchers.Main) {
            crossfade?.stop()
            spareGainFactor = 1f
            gainFactor = 1f
            YtStreamTable.serve(uri, stream)
            val totalMs = stream.lengthMs
            durationMs = totalMs
            announced = false
            lastItemUri = uri
            tailSilence = null
            resetStall()
            if (uri.isNotBlank()) itemDurations[uri] = totalMs
            // The player instance survives track changes: swapping the media
            // source keeps the renderers, the codec and the audio sink warm, so
            // only the first play pays the ExoPlayer build cost.
            val live = player ?: ExoPlayer.Builder(context)
                .setMediaSourceFactory(ensureSessionKit().factory)
                .build()
                .also {
                    it.addListener(listener)
                    player = it
                }
            live.setMediaSource(ensureSessionKit().factory.createMediaSource(itemForAlternate(uri, stream)))
            live.prepare()
            live.seekTo(fromMs.coerceIn(0L, (totalMs - 1000L).coerceAtLeast(0L)))
            live.playWhenReady = autoplay
            started = autoplay
            loaded = true
            applyVolume()
            log.i("audio engine ready on the alternate '${stream.videoId}' ${totalMs}ms at ${fromMs}ms " +
                    "autoplay=$autoplay reused=${player !== live}"
            )
            true
        }

    /**
     * The alternate-rendition queue hand: [queueNext] for a stream instead
     * of a catalogue file. The item joins the same playlist (or the same
     * crossfade hold) its Spotify sibling would, so a handover between the
     * two sources carries no engine teardown.
     */
    suspend fun queueNextAlternate(
        uri: String,
        stream: YtStream,
        whileOn: String,
        spec: TransitionSpec?,
    ): Boolean {
        YtStreamTable.serve(uri, stream)
        val totalMs = stream.lengthMs
        val item = itemForAlternate(uri, stream)
        val heldForFade = spec != null || configuredCrossfadeMs() > 0L
        val added = runOnMain {
            val p = player ?: return@runOnMain false
            if (p.currentMediaItem?.mediaId != whileOn) return@runOnMain false
            if (heldForFade) {
                true
            } else {
                val current = p.currentMediaItemIndex
                if (p.mediaItemCount > current + 1) p.removeMediaItems(current + 1, p.mediaItemCount)
                p.addMediaItem(item)
                true
            }
        } ?: false
        if (!added) return false
        if (uri.isNotBlank()) itemDurations[uri] = totalMs
        if (heldForFade) {
            spareGainFactor = 1f
            crossfadeEngine()?.hold(item, totalMs, spec)
            log.i("audio held next '$uri' on the alternate stream for a crossfade")
        } else {
            log.i("audio queued next '$uri' on the alternate stream")
        }
        return true
    }

    private fun itemForAlternate(uri: String, stream: YtStream): MediaItem =
        MediaItem.Builder()
            .setMediaId(uri)
            .setUri(Uri.parse(stream.url))
            // The rendition key doubles as the router's signal: the loading
            // side opens the stream buffer instead of the song cache, and no
            // licence session is ever asked for this item.
            .setCustomCacheKey(YtStreamTable.keyFor(uri))
            .build()

    /**
     * Plays a direct url - an ad body - on the same session player as the
     * catalogue files and the alternates: the item carries the ad marker as
     * its cache key, so its loads route to the stream buffer instead of the
     * song cache, and no licence session is ever asked for it. The length
     * is only known when the manifest carried it; the player settles it
     * once its own parse reports one.
     */
    suspend fun prepareDirect(
        uri: String,
        url: String,
        fromMs: Long,
        autoplay: Boolean,
        trackDurationMs: Long,
    ): Boolean =
        withContext(Dispatchers.Main) {
            crossfade?.stop()
            spareGainFactor = 1f
            gainFactor = 1f
            durationMs = trackDurationMs
            announced = false
            lastItemUri = uri
            tailSilence = null
            resetStall()
            if (uri.isNotBlank() && trackDurationMs > 0L) itemDurations[uri] = trackDurationMs
            // The player instance survives track changes: swapping the media
            // source keeps the renderers, the codec and the audio sink warm, so
            // only the first play pays the ExoPlayer build cost.
            val live = player ?: ExoPlayer.Builder(context)
                .setMediaSourceFactory(ensureSessionKit().factory)
                .build()
                .also {
                    it.addListener(listener)
                    player = it
                }
            live.setMediaSource(ensureSessionKit().factory.createMediaSource(itemForDirect(uri, url)))
            live.prepare()
            val bounded = if (trackDurationMs > 1_000L) {
                fromMs.coerceIn(0L, trackDurationMs - 1_000L)
            } else {
                fromMs.coerceAtLeast(0L)
            }
            live.seekTo(bounded)
            live.playWhenReady = autoplay
            started = autoplay
            loaded = true
            applyVolume()
            log.i("audio engine ready on the direct url at ${bounded}ms " +
                    "autoplay=$autoplay reused=${player !== live}"
            )
            true
        }

    private fun itemForDirect(uri: String, url: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(uri)
            .setUri(Uri.parse(url))
            // The ad marker routes the loads to the stream buffer - the body
            // is a one-shot impression url, it must not sit in the song
            // cache - while the media id stays the ad's own track uri.
            .setCustomCacheKey(uri + YtStreamTable.AD_SUFFIX)
            .build()

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
        runOnMain {
            crossfade?.onUserSeek()
            player?.seekTo(ms)
        }
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

    fun retune(gainDb: Double) {
        gainFactor = 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
        applyVolume()
    }

    fun rate(mult: Float) {
        val clamped = mult.coerceIn(0.5f, 3.5f)
        userRate = clamped
        runOnMain {
            val cross = crossfade
            if (cross != null) {
                cross.applyUserRate(clamped)
            } else {
                val parameters = PlaybackParameters(clamped)
                player?.playbackParameters = parameters
                sparePlayer?.playbackParameters = parameters
            }
        }
    }

    fun clearQueuedNext() {
        runOnMain { crossfade?.dropHold() }
    }

    fun stop() {
        started = false
        loaded = false
        durationMs = 0L
        lastItemUri = ""
        tailSilence = null
        resetStall()
        progressJob?.cancel()
        progressJob = null
        runOnMain {
            crossfade?.stop()
            player?.stop()
            sparePlayer?.stop()
        }
    }

    fun release() {
        started = false
        loaded = false
        durationMs = 0L
        lastItemUri = ""
        tailSilence = null
        progressJob?.cancel()
        progressJob = null
        runOnMain {
            crossfade?.release()
            crossfade = null
            player?.release()
            player = null
            sparePlayer?.release()
            sparePlayer = null
            sessionKit = null
            spareKit = null
            licensedFiles.clear()
            offlineKeySets.clear()
        }
    }

    private fun applyVolume() {
        runOnMain {
            val cross = crossfade
            if (cross != null) {
                cross.applyVolumes(::volumeOf)
            } else {
                player?.let { it.volume = volumeOf(it) }
            }
        }
    }

    private fun volumeOf(p: ExoPlayer): Float {
        val gain = if (p === player) gainFactor else if (p === sparePlayer) spareGainFactor else 1f
        return (duckLevel * userLevel * gain).coerceIn(0f, 1f)
    }

    private fun configuredCrossfadeMs(): Long =
        AppPrefs.getInt(context, CLIENT_STORE, CROSSFADE_MS_PREF, 0)
            .toLong().coerceIn(0L, MAX_CROSSFADE_MS)

    private fun ensureSparePlayer(): ExoPlayer {
        sparePlayer?.let { return it }
        val kit = ensureSpareKit()
        val built = ExoPlayer.Builder(context)
            .setMediaSourceFactory(kit.factory)
            .build()
        sparePlayer = built
        log.i("spare player built for crossfades")
        return built
    }

    private fun crossfadeEngine(): CrossfadeEngine? {
        crossfade?.let { return it }
        if (player == null) return null
        val engine = CrossfadeEngine(
            session = { player },
            spare = { ensureSparePlayer() },
            buildSource = { item -> ensureSpareKit().factory.createMediaSource(item) },
            fadeMs = { configuredCrossfadeMs() },
            automixOn = { AppPrefs.getBoolean(context, CLIENT_STORE, AUTOMIX_PREF, false) },
            sessionDurationMs = { durationMs },
            userRate = { userRate },
            baseVolume = ::volumeOf,
            onEvent = { event -> onEvent(event) },
            onHandoff = ::onCrossfadeHandoff,
        )
        crossfade = engine
        return engine
    }

    private fun onCrossfadeHandoff(out: ExoPlayer, into: ExoPlayer, uri: String, durationMs: Long) {
        out.removeListener(listener)
        into.addListener(listener)
        player = into
        sparePlayer = out
        val handedKit = sessionKit
        sessionKit = spareKit
        spareKit = handedKit
        lastItemUri = uri
        tailSilence = null
        announced = true
        if (durationMs > 0L) {
            this.durationMs = durationMs
            itemDurations[uri] = durationMs
        }
        gainFactor = spareGainFactor
        spareGainFactor = 1f
        log.i("crossfade handed the session to '${uri.takeLast(12)}'")
    }

    private fun resetStall() {
        stallLastPositionMs = 0L
        stallSinceWallMs = 0L
        stallNotified = false
    }

    private fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && started) {
                val read = runOnMain {
                    val p = player ?: return@runOnMain null
                    Triple(p.currentPosition, p.playbackState, p.playWhenReady)
                }
                val position = read?.first ?: lastPositionMs
                if (position >= 0L) {
                    lastPositionMs = position
                    onEvent(PlayerEvent.Progress(position, durationMs))
                }
                trimSilentTail(position)
                if (read != null) watchStall(position, read.second, read.third)
                delay(PROGRESS_MS.milliseconds)
            }
        }
    }

    /**
     * A stream that dies mid-song without an error code leaves the player
     * buffering forever at the position where its buffer ran dry. The watchdog
     * turns that frozen hold into a transport failure so the layer above can
     * hand the track to the next source at the frozen spot, and it speaks once
     * per stall - re-armed only when the position moves again.
     */
    private fun watchStall(position: Long, playbackState: Int, playWhenReady: Boolean) {
        if (abs(position - stallLastPositionMs) > STALL_JITTER_MS) {
            stallLastPositionMs = position
            stallSinceWallMs = 0L
            stallNotified = false
            return
        }
        if (stallNotified || !playWhenReady || playbackState != Player.STATE_BUFFERING || !announced) {
            stallSinceWallMs = 0L
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (stallSinceWallMs == 0L) {
            stallSinceWallMs = now
            return
        }
        if (now - stallSinceWallMs < STALL_LIMIT_MS) return
        stallNotified = true
        log.w(
            "the stream stalled at ${position}ms for ${(now - stallSinceWallMs) / 1000}s, " +
                "reporting it as a transport failure"
        )
        onEvent(PlayerEvent.Failed("stall at ${position}ms"))
    }

    /**
     * Files the measured silent tail of the item the session is currently
     * on. Noted the moment a measurement exists - even if the crossfade
     * never arms - so every path past the measurable point can act on it.
     * A silence below the floor clears any earlier note.
     */
    fun noteTailSilence(uri: String, silenceMs: Long) {
        tailSilence = if (silenceMs >= MIN_TAIL_TRIM_MS) uri to silenceMs else null
    }

    /**
     * The audible end stands in for the real one when no blend owns the
     * ending: past it the session would only render silence, so the ended
     * event fires there - exactly the sequence a natural end runs - and the
     * normal advance lands the next track on top of the quiet tail instead
     * of after it. The blend paths are left alone: an armed-and-ready spare
     * is a beat from starting, and a running blend hands the session over at
     * this very point on its own.
     */
    private fun trimSilentTail(position: Long) {
        val (uri, silence) = tailSilence ?: return
        if (silence <= 0L || durationMs <= silence) return
        if (uri != lastItemUri || position < durationMs - silence) return
        if (crossfade?.ownsTheEnding() == true) return
        val playing = runOnMain { player?.isPlaying } ?: return
        if (!playing) return
        tailSilence = null
        log.i("trimming '${uri.takeLast(12)}' at the audible end, ${silence}ms of tail silence skipped")
        if (crossfade?.onSessionEnded() == true) return
        lastPositionMs = durationMs
        onEvent(PlayerEvent.Ended(uri))
        lastItemUri = ""
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val newUri = item?.mediaId.orEmpty()
            val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            if (auto && lastItemUri.isNotBlank()) {
                onEvent(PlayerEvent.Ended(lastItemUri))
            }
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                announced = false
                lastPositionMs = 0L
                lastItemUri = newUri
                resetStall()
                itemDurations[newUri]?.let { durationMs = it }
            }
            if (auto) {
                val ready = runOnMain { player?.playbackState == Player.STATE_READY } ?: false
                if (ready && !announced) {
                    announced = true
                    onEvent(PlayerEvent.Started(newUri))
                    if (started) startProgress()
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    // An item whose length nobody knew upfront - a direct url
                    // - settles its duration as soon as the player reports
                    // one, so the transport and the bridge stop guessing.
                    if (durationMs <= 0L) {
                        val real = runOnMain { player?.duration } ?: C.TIME_UNSET
                        if (real != C.TIME_UNSET && real > 0L) {
                            durationMs = real
                            if (lastItemUri.isNotBlank()) itemDurations[lastItemUri] = real
                        }
                    }
                    if (!announced) {
                        announced = true
                        onEvent(PlayerEvent.Started(lastItemUri))
                        if (started) startProgress()
                    }
                }
                Player.STATE_ENDED -> {
                    if (crossfade?.onSessionEnded() == true) return
                    lastPositionMs = durationMs
                    // A trim already ended this item; the natural end that
                    // follows must not advance the queue a second time.
                    if (lastItemUri.isNotBlank()) onEvent(PlayerEvent.Ended(lastItemUri))
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
            onEvent(PlayerEvent.Failed("${error.errorCodeName}: ${error.message ?: "audio error"}"))
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
