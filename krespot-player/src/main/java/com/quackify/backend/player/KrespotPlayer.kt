/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
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
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.player

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import com.quackify.backend.connect.command.AudioFile
import com.quackify.backend.connect.command.CommandTrack
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.store.AppPrefs
import com.quackify.backend.core.store.StreamSource
import com.quackify.backend.core.store.StreamSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

private val log = Logs.of(Logs.Module.PLAYER)

private const val LICENCE_SETTLE_MS = 2_000L

private const val LICENCE_SKIP_WINDOW_MS = 4_000L

private const val MANIFEST_CACHE = 8

private const val FILE_ID_CACHE = 64

private const val SOURCE_CACHE = 16

private const val DEEP_LOOKAHEAD = 1

private const val DUCK_LEVEL = 0.2f

private const val PLAYER_PREFS = "quackify.player"

private const val KEY_RESUME_URI = "resumeUri"

private const val KEY_RESUME_FILE = "resumeFile"

private const val KEY_RESUME_FORMAT = "resumeFormat"

private const val KEY_RESUME_AT = "resumeAt"

private const val RESUME_WRITE_MS = 5_000L

private const val CLIENT_STORE = "quackify.client"

private const val CROSSFADE_MS_PREF = "crossfadeMs"

private const val AUTOMIX_PREF = "automix"

private const val SPEC_MARGIN_MS = 24_000L

private const val MAX_SPEC_BUDGET_MS = 120_000L

/** Length disagreement below this is the same cut; a resume crosses it untouched. */
private const val SAME_CUT_MS = 2_000L

/** Head window the queue pre-pulls for the next alternate handover. */
private const val WARM_HEAD_SECONDS = 30L

sealed interface PlayerEvent {
    data class Started(val uri: String = "") : PlayerEvent
    data class Ended(val uri: String = "") : PlayerEvent
    data class Progress(val positionMs: Long, val durationMs: Long) : PlayerEvent
    data class Seeked(val positionMs: Long) : PlayerEvent
    data class Failed(val message: String) : PlayerEvent
}

data class PlayerState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val fileId: String = "",
    val format: Int = 0,
    val bitrate: Int = 0,
    val quality: String = "",
    val trackUri: String = "",
    val isVideo: Boolean = false,
    val source: StreamSource = StreamSource.SPOTIFY,
    val videoId: String = "",
)

private fun resumeStoreFor(account: String): String =
    if (account.isBlank()) PLAYER_PREFS else "$PLAYER_PREFS.$account"

/**
 * A resume point measured on one source's timeline, replanted on another's.
 * The alternate is rarely the same cut: a breath of lead-in here, a padded
 * tail there. The resolve gate keeps grossly wrong takes out; what reaches
 * here is the near miss, and folding the point across the two lengths holds
 * the handover near the same musical moment instead of letting it drift
 * with the longer edit. A point at the very start, or two lengths that
 * agree, needs no fold.
 */
private fun replant(positionMs: Long, fromLengthMs: Long, intoLengthMs: Long): Long {
    if (positionMs <= 0L) return positionMs
    if (fromLengthMs <= 0L || intoLengthMs <= 0L) return positionMs
    if (kotlin.math.abs(fromLengthMs - intoLengthMs) < SAME_CUT_MS) return positionMs
    val folded = positionMs * intoLengthMs / fromLengthMs
    return folded.coerceAtMost((intoLengthMs - SAME_CUT_MS).coerceAtLeast(0L))
}

class KrespotPlayer(private val context: Context) {

    // The resume point belongs to the account that played it, so a bridge
    // minted after an account switch resumes that account's own last track
    // instead of whatever the previous account left on this device.
    private val resumeStore = resumeStoreFor(EncoreAuth.activeAccountId(context))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PlayerEvent> = _events

    @Volatile
    private var engine: AudioEngine? = null

    private var job: Job? = null

    @Volatile
    private var generation = 0

    @Volatile
    private var lastPlayAt = 0L

    @Volatile
    private var licenceSettling = false

    @Volatile
    private var engineIdle = true

    @Volatile
    private var licencePending: CommandTrack? = null

    @Volatile
    private var licenceFrom = 0L

    @Volatile
    private var pausedByFocus = false

    @Volatile
    private var pendingPause = false

    /** A seek that arrived while the engine was still loading, applied at run(). */
    @Volatile
    private var pendingSeekMs = -1L

    @Volatile
    private var spclient: String = ""

    @Volatile
    private var refused: CommandTrack? = null

    @Volatile
    private var refusedFrom: Long = 0L

    private val focus = AudioFocus(context)

    private val automixAnalysis = AutomixAnalysis(context)

    private val video = VideoEngine(context, { spclient }, scope) { event ->
        if (videoActive && event !is PlayerEvent.Progress) {
            _events.tryEmit(event)
        }
        if (videoActive) {
            mirror(event, _state.value.durationMs)
        }
    }

    @Volatile
    private var videoActive = false

    @Volatile
    private var pendingVideoSurface: Surface? = null

    var onFocusPause: (() -> Unit)? = null

    var onFocusResume: (() -> Unit)? = null

    var onFocusDuck: ((Boolean) -> Unit)? = null

    /** Fired whenever the licence endpoint rate limits a request, so the
     * layer above can surface it (the gate keeps retrying on its own). */
    @Volatile
    var onLicenceRateLimited: (() -> Unit)? = null

    @Volatile
    private var licenceRetrying = false

    /** The track whose playback already spent its mid-song source divert: a
     *  fresh playback clears it, a divert re-entry keeps it, so the two
     *  sources cannot hand a dying track back and forth forever. */
    @Volatile
    private var transportDivertUri: String? = null

    init {
        LicenseGate.onOpen = {
            val track = licencePending ?: refused
            if (track != null) {
                refused = null
                licencePending = null
                licenceRetrying = true
                log.i("licence is back, retrying '${track.name.ifBlank { track.uri }}' " +
                        "from ${refusedFrom}ms"
                )
                play(track, spclient, if (licenceFrom > 0L) licenceFrom else refusedFrom)
                licenceRetrying = false
            }
        }
        LicenseGate.onRateLimited = {
            // With another source after spotify the player diverts there and
            // playback carries on, so a refusal is quiet; the toast only
            // matters when spotify is the last source standing and the
            // rate limit is what actually parks the track.
            if (StreamSources.after(context, StreamSource.SPOTIFY).isEmpty()) onLicenceRateLimited?.invoke()
        }
    }

    private val manifests = java.util.concurrent.ConcurrentHashMap<String, Deferred<List<AudioFile>>>()

    private val fileIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val fileIdOrder = java.util.ArrayDeque<String>()

    private val sources = java.util.LinkedHashMap<String, AudioSource>()

    private val resolving = java.util.concurrent.ConcurrentHashMap<String, Deferred<AudioSource?>>()

    private val alternates = java.util.concurrent.ConcurrentHashMap<String, Deferred<YtStream?>>()

    @Volatile
    private var deepJob: Job? = null

    @Volatile
    private var deepTarget: String = ""

    private val deepWarmed = LinkedHashSet<String>()

    private var resumeWrittenUri = ""

    private var resumeWrittenMs = -1L

    fun rememberFile(trackUri: String, fileId: String) {
        if (trackUri.isBlank() || fileId.length < 32) return
        synchronized(fileIdOrder) {
            if (fileIds.put(trackUri, fileId) == null) fileIdOrder.addLast(trackUri)
            while (fileIdOrder.size > FILE_ID_CACHE) {
                fileIds.remove(fileIdOrder.removeFirst())
            }
        }
    }

    /** Counts a played or warmed track against the offline song cache limit. */
    fun cacheTouched(trackUri: String) {
        SongCache.touch(trackUri, context)
    }

    @Volatile
    var queuedNextUri: String = ""
        private set

    @Volatile
    private var queuedNextTrack: CommandTrack? = null

    @Volatile
    private var queuedGainDb = 0.0

    @Volatile
    private var playingTrack: CommandTrack? = null

    private val queueTicket = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Hands the engine the next track of the queue so ExoPlayer prebuffers it
     * while the current one plays and the transition carries no silence. The
     * item that joins is the cut the transition will really land on: the
     * alternate stream when it leads the stack, the catalogue file with the
     * alternate as the fallback otherwise - so a licence that closes mid-
     * queue still leaves a seamless handover in place. A warm source queues
     * right away; a cold one resolves here and only queues if the engine is
     * still on the track that asked for it. The licence (restored, or freshly
     * acquired offline) is settled before the item joins the playlist, so the
     * prebuffered track never asks the endpoint again at transition time. The
     * bridge checks hasQueuedNext at the end of the track, so a late resolve
     * can never leave the transition hanging - the normal advance simply
     * takes over.
     */
    fun queueNext(track: CommandTrack, spclient: String) {
        if (track.isDirect) return
        this.spclient = spclient
        val live = engine?.takeIf { it.hasMedia } ?: run {
            warmAlternateNext(track)
            return
        }
        val askedFor = _state.value.trackUri
        if (askedFor.isBlank() || live.currentUri != askedFor) return
        if (queuedNextUri == track.uri) return
        // A new next replaces whatever the previous queue held warm.
        YtStreamPool.unpinAll()
        val file = track.best() ?: track.files.firstOrNull()
        val cached = sourceFor(track.uri)
        val alternateLeads = StreamSources.order(context).firstOrNull() == StreamSource.YT_MUSIC
        val ticket = queueTicket.incrementAndGet()
        scope.launch {
            val cut: Cut? = if (alternateLeads) {
                resolveAlternate(track)?.let { Cut.Alternate(it, track.uri) }
            } else {
                val source = cached ?: warmSource(track.uri, spclient)
                if (source != null) {
                    preResolveAlternate(track)
                    Cut.Catalogue(source, track.uri)
                } else {
                    resolveAlternate(track)?.let { Cut.Alternate(it, track.uri) }
                }
            }
            if (cut == null) {
                log.w("seamless: '${track.name}' could not be resolved on any source, the pre-queue was dropped")
                return@launch
            }
            // The analysis reads the alternate back out of the table before
            // the item itself lands on the engine, so the stream has to sit
            // in it from the moment the cut is chosen.
            if (cut is Cut.Alternate) YtStreamTable.serve(track.uri, cut.stream)
            if (ticket != queueTicket.get()) return@launch
            val still = engine
            if (still !== live || still.currentUri != askedFor || _state.value.trackUri != askedFor) {
                return@launch
            }
            if (cut is Cut.Alternate) {
                // The plain queue opens the item's loads only once the current
                // period is done buffering, which on a slow wire is the
                // handover itself: pin the rendition's buffer now with its
                // head window pulled and parked, so the handover's open
                // finds the transition window already on disk.
                YtStreamPool.warmHead(
                    YtStreamTable.keyFor(track.uri),
                    cut.stream,
                    context,
                    (cut.stream.bitrate.coerceIn(64_000, 512_000) / 8L) * WARM_HEAD_SECONDS,
                )
            }
            val spec = automixSpecFor(track, cut)
            val queued = when (cut) {
                is Cut.Catalogue -> still.queueNext(track.uri, cut.source, askedFor, file?.gainDb ?: 0.0, spec)
                is Cut.Alternate -> still.queueNextAlternate(track.uri, cut.stream, askedFor, spec)
            }
            if (!queued) {
                log.i("seamless: the engine left '${askedFor.takeLast(12)}' before '${track.name}' landed")
                return@launch
            }
            if (cut is Cut.Catalogue) SongCache.touch(track.uri, context)
            queuedNextUri = track.uri
            queuedNextTrack = track
            queuedGainDb = if (cut is Cut.Catalogue) file?.gainDb ?: 0.0 else 0.0
            log.i("queued next '${track.name}' " + when {
                cut is Cut.Alternate -> "on the alternate stream"
                cached != null -> "from the warm source"
                else -> "after resolving"
            })
        }
    }

    private val alternateWarming = HashSet<String>()

    /**
     * Nothing is on the engine yet, so there is no playlist to join: the
     * next stream still resolves ahead of time instead, and the eventual
     * handoff walks straight out of the resolve cache.
     */
    private fun warmAlternateNext(track: CommandTrack) {
        if (!StreamSources.order(context).contains(StreamSource.YT_MUSIC)) return
        if (!track.uri.startsWith("spotify:track:")) return
        synchronized(alternateWarming) {
            if (!alternateWarming.add(track.uri)) return
        }
        scope.launch {
            try {
                resolveAlternate(track)
            } finally {
                synchronized(alternateWarming) { alternateWarming.remove(track.uri) }
            }
        }
    }

    private fun alternateDeferred(track: CommandTrack): Deferred<YtStream?> {
        alternates[track.uri]?.let { return it }
        val deferred = scope.async {
            runCatching { YtMusicStreams.resolve(context, track) }.getOrNull()
        }
        deferred.invokeOnCompletion { alternates.remove(track.uri, deferred) }
        val racing = alternates.putIfAbsent(track.uri, deferred)
        if (racing != null) {
            deferred.cancel()
            return racing
        }
        return deferred
    }

    private suspend fun resolveAlternate(track: CommandTrack): YtStream? =
        runCatching { alternateDeferred(track).await() }.getOrNull()

    /**
     * Resolves the fallback stream for a track that is about to play or sit
     * queued on the catalogue cut, while spotify is still serving it fine.
     * The resolve is the expensive leg of a divert — search, player and
     * probe round trips — so warming it here turns a licence refusal
     * mid-song into a cache hit and the handover only pays the engine
     * switch.
     */
    private fun preResolveAlternate(track: CommandTrack) {
        if (StreamSources.after(context, StreamSource.SPOTIFY).isEmpty()) return
        if (!track.uri.startsWith("spotify:track:")) return
        scope.launch { resolveAlternate(track) }
    }

    private fun automixEnabled(): Boolean =
        AppPrefs.getBoolean(context, CLIENT_STORE, AUTOMIX_PREF, false)

    /**
     * Plans the DJ transition into [next] when automix is on and a beat grid
     * was measured on the device for the sides of the transition, so the fade
     * the engine holds is already sized in whole bars with the incoming
     * track's tempo stretch decided. The grids are decoded from the cuts that
     * will really meet - the playing rendition's tail against the queued
     * rendition's head - and filed under each cut's own cache key, so a grid
     * measured on the catalogue file is never reused for the alternate edit
     * of the same track. The analysis runs inside the time the current track
     * has left; a window that does not finish in it simply leaves the planner
     * without grids and the plan falls back to the plain fade, so the settings
     * warning's promised crossfade happens even with the crossfade slider at
     * zero. Both paths anchor on the outgoing track's measured audible end, so
     * a silent tail no longer swallows the blend; a plain fade with automix
     * off reuses an already measured tail without decoding anything new.
     */
    private suspend fun automixSpecFor(next: CommandTrack, nextCut: Cut): TransitionSpec? {
        val crossfadeMs = AppPrefs.getInt(context, CLIENT_STORE, CROSSFADE_MS_PREF, 0).toLong()
        if (!automixEnabled()) {
            if (crossfadeMs <= 0L) {
                log.i("automix declined: the toggle is off")
                return null
            }
            val currentUri = _state.value.trackUri
            val currentCut = currentCutFor(currentUri) ?: return null
            val outDurationMs = _state.value.durationMs
            val silenceMs = tailSilenceMsFor(currentCut)
            // The trim rides on the measurement alone, so it holds even when
            // the plain fade never arms.
            engine?.noteTailSilence(currentUri, silenceMs)
            if (silenceMs <= 0L || outDurationMs <= 0L) return null
            log.i("automix is off, pulling the plain fade ${silenceMs}ms earlier over the measured tail silence")
            return AutomixPlanner.plan(
                null,
                null,
                outDurationMs,
                nextCut.lengthMs,
                crossfadeMs,
                silenceMs,
            )
        }
        if (AutomixPlanner.unmixable(next.name)) {
            log.i("automix declined: '${next.name}' is spoken word or live")
            return null
        }
        val currentUri = _state.value.trackUri
        if (!currentUri.startsWith("spotify:track:")) {
            log.i("automix declined: the playing item is not a track")
            return null
        }
        if (!next.uri.startsWith("spotify:track:")) {
            log.i("automix declined: '${next.name}' is not a track")
            return null
        }
        val currentCut = currentCutFor(currentUri) ?: run {
            log.i("automix declined: no resolved rendition for the playing track")
            return null
        }
        val outDurationMs = _state.value.durationMs
        if (outDurationMs <= 0L) {
            log.i("automix declined: the playing duration is unknown")
            return null
        }
        val nextDurationMs = nextCut.lengthMs
        val ticket = queueTicket.get()
        val stillWanted = {
            queueTicket.get() == ticket && _state.value.trackUri == currentUri
        }
        val remaining = outDurationMs - _state.value.positionMs
        val budget = (remaining - SPEC_MARGIN_MS).coerceIn(0L, MAX_SPEC_BUDGET_MS)
        val startedAt = SystemClock.elapsedRealtime()
        val outGrid = automixAnalysis.tailGrid(currentCut, outDurationMs, budget, stillWanted)
        val outSilenceMs = tailSilenceMsFor(currentCut)
        // Noted the moment it is measured, not when the hold lands: a late or
        // failed queue must not cost the track its audible end.
        engine?.noteTailSilence(currentUri, outSilenceMs)
        val inGrid = if (outGrid != null) {
            val left = (budget - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)
            automixAnalysis.headGrid(nextCut, nextDurationMs, left, stillWanted)
        } else {
            null
        }
        if (outGrid == null && inGrid == null) {
            log.i("automix: no grid on either side of '${next.name}', blending with the plain fade")
        }
        val spec = AutomixPlanner.plan(
            outGrid,
            inGrid,
            outDurationMs,
            nextDurationMs,
            crossfadeMs,
            outSilenceMs,
        )
        log.i(
            "automix into '${next.name}' fade=${spec.fadeMs}ms rate=${spec.incomingRate} " +
                "lock=${spec.phaseLock} shift=${spec.outSilenceMs}ms"
        )
        return spec
    }

    /**
     * The cut the playing track is actually on: the alternate stream when the
     * engine is playing one, the catalogue file otherwise. Automix plans the
     * transition over the cuts that will really meet, so the blend's timing
     * survives a mid-queue handover between the two sources.
     */
    private fun currentCutFor(uri: String): Cut? {
        if (!uri.startsWith("spotify:track:")) return null
        if (_state.value.source == StreamSource.YT_MUSIC) {
            val stream = YtStreamTable.of(YtStreamTable.keyFor(uri)) ?: return null
            return Cut.Alternate(stream, uri)
        }
        val source = sourceFor(uri) ?: return null
        return Cut.Catalogue(source, uri)
    }

    private fun tailSilenceMsFor(cut: Cut): Long =
        if (cut.uri.startsWith("spotify:track:")) {
            (automixAnalysis.tailSilenceSec(cut) * 1000.0).roundToLong().coerceAtLeast(0L)
        } else {
            0L
        }

    fun clearQueuedNext() {
        queueTicket.incrementAndGet()
        queuedNextUri = ""
        queuedNextTrack = null
        engine?.clearQueuedNext()
    }

    fun hasSourceFor(trackUri: String): Boolean = sourceFor(trackUri) != null
    fun prefetch(trackUri: String, spclient: String) {
        if (trackUri.isBlank() || spclient.isBlank()) return
        if (manifests.containsKey(trackUri)) return
        if (manifests.size >= MANIFEST_CACHE) {
            manifests.entries.firstOrNull { it.key != trackUri }?.let { (key, pending) ->
                manifests.remove(key)
                pending.cancel()
            }
        }
        manifests[trackUri] = scope.async { SourceResolver.manifest(context, spclient, trackUri) }
    }

    /** Fire-and-forget full source warm for a visible row: manifest, seektable and CDN url. */
    fun warmTrack(trackUri: String, spclient: String) {
        if (trackUri.isBlank() || spclient.isBlank()) return
        if (!trackUri.startsWith("spotify:track:")) return
        if (sourceFor(trackUri) != null) return
        warmDeferred(trackUri, spclient)
    }

    fun prefetchAhead(
        tracks: List<com.quackify.backend.connect.cluster.ClusterTrack>,
        spclient: String,
        nowPlaying: String = "",
    ) {
        val window = windowOf(tracks)
        if (tracks.isEmpty() || spclient.isBlank()) {
            if (window.isEmpty()) {
                deepJob?.cancel()
                deepTarget = ""
            }
            dropStaleWarmups(window, nowPlaying)
            return
        }
        tracks.forEach { track ->
            if (track.isAd || track.isMeta || !track.isVisible) return@forEach
            if (!track.uri.startsWith("spotify:track:")) return@forEach
            if (fileIds.containsKey(track.uri)) return@forEach
            prefetch(track.uri, spclient)
            scope.launch {
                val file = manifestFor(track.uri).firstOrNull() ?: return@launch
                rememberFile(track.uri, file.fileId)
            }
        }
        val target = window.firstOrNull().orEmpty()
        if (target.isBlank()) {
            deepJob?.cancel()
            deepTarget = ""
            dropStaleWarmups(window, nowPlaying)
            return
        }
        if (deepJob?.isActive == true && deepTarget == target) {
            dropStaleWarmups(window, nowPlaying)
            return
        }
        deepJob?.cancel()
        deepTarget = target
        dropStaleWarmups(window, nowPlaying)
        deepJob = scope.launch { deepWarm(tracks, spclient) }
    }

    private fun windowOf(
        tracks: List<com.quackify.backend.connect.cluster.ClusterTrack>,
    ): List<String> = tracks.asSequence()
        .filter { !it.isAd && !it.isMeta && it.isVisible && it.uri.startsWith("spotify:track:") }
        .take(DEEP_LOOKAHEAD)
        .map { it.uri }
        .toList()

    private fun dropStaleWarmups(window: List<String>, nowPlaying: String) {
        val keep = window.toHashSet()
        val playing = _state.value.trackUri
        val stale = synchronized(deepWarmed) {
            val drop = deepWarmed.filter { it != playing && it != nowPlaying && it !in keep }
            deepWarmed.removeAll(drop.toSet())
            drop
        }
        if (stale.isEmpty()) return
        stale.forEach { uri ->
            sourceFor(uri) ?: fileIds[uri] ?: return@forEach
        }
        log.i("dropped ${stale.size} stale warmup(s): " +
                stale.joinToString { it.takeLast(12) })
    }

    /**
     * Resolves, licenses and preloads the next few tracks so advancing
     * the queue starts from warm bytes instead of a cold pipeline.
     */
    private suspend fun deepWarm(
        tracks: List<com.quackify.backend.connect.cluster.ClusterTrack>,
        spclient: String,
    ) {
        tracks.asSequence()
            .filter { !it.isAd && !it.isMeta && it.isVisible && it.uri.startsWith("spotify:track:") }
            .take(DEEP_LOOKAHEAD)
            .forEach { track ->
                if (sourceFor(track.uri) != null) return@forEach
                val source = warmSource(track.uri, spclient) ?: return@forEach
                SongCache.touch(track.uri, context)
                synchronized(deepWarmed) { deepWarmed.add(track.uri) }
                log.i("deep warm '${track.title}' file=${source.fileId.take(8)}")
            }
    }

    private fun rememberSource(trackUri: String, source: AudioSource) {
        if (trackUri.isBlank()) return
        synchronized(sources) {
            sources.remove(trackUri)
            sources[trackUri] = source
            while (sources.size > SOURCE_CACHE) {
                val oldest = sources.keys.firstOrNull() ?: break
                sources.remove(oldest)
            }
        }
    }

    private fun sourceFor(trackUri: String): AudioSource? = synchronized(sources) { sources[trackUri] }

    private suspend fun warmSource(
        trackUri: String,
        spclient: String,
        hintFile: String = "",
        hintFormat: Int = 0,
    ): AudioSource? =
        runCatching { warmDeferred(trackUri, spclient, hintFile, hintFormat).await() }.getOrNull()

    private fun warmDeferred(
        trackUri: String,
        spclient: String,
        hintFile: String = "",
        hintFormat: Int = 0,
    ): Deferred<AudioSource?> {
        resolving[trackUri]?.let { return it }
        val deferred = scope.async {
            runCatching {
                val known = hintFile.ifBlank { fileIds[trackUri].orEmpty() }
                val fromKnown = known.takeIf { it.isNotBlank() }
                    ?.let { SourceResolver.resolve(context, spclient, trackUri, it, hintFormat) }
                fromKnown
                    ?: SourceResolver.resolve(context, spclient, trackUri, "", 0, manifestFor(trackUri))
            }.getOrNull()?.also { source ->
                rememberSource(trackUri, source)
                rememberFile(trackUri, source.fileId)
            }
        }
        deferred.invokeOnCompletion { resolving.remove(trackUri, deferred) }
        val racing = resolving.putIfAbsent(trackUri, deferred)
        if (racing != null) {
            deferred.cancel()
            return racing
        }
        return deferred
    }

    private suspend fun manifestFor(trackUri: String): List<AudioFile> {
        val pending = manifests[trackUri] ?: return emptyList()
        val files = runCatching { pending.await() }.getOrNull() ?: emptyList()
        if (files.isEmpty()) manifests.remove(trackUri, pending)
        return files
    }

    private fun forgetSource(trackUri: String) {
        synchronized(sources) { sources.remove(trackUri) }
    }

    init {
        adoptLegacyResume()
        focus.onPause = {
            log.i("the audio focus went to another app, pausing")
            pausedByFocus = true
            pauseInternal()
            onFocusPause?.invoke()
        }
        focus.onResume = {
            if (pausedByFocus) {
                log.i("the audio focus came back, resuming")
                pausedByFocus = false
                resumeInternal()
                onFocusResume?.invoke()
            } else {
                log.i("the audio focus came back, but the user paused in the meantime")
            }
        }
        focus.onDuck = { ducked ->
            duck(if (ducked) DUCK_LEVEL else 1f)
            onFocusDuck?.invoke(ducked)
        }
    }

    fun warm() {
        video.prewarmDrm()
    }

    fun resumeLast(spclient: String) {
        scope.launch {
            val prefs = AppPrefs.prefs(context, resumeStore)
            val uri = prefs.getString(KEY_RESUME_URI, "").orEmpty()
            val file = prefs.getString(KEY_RESUME_FILE, "").orEmpty()
            val format = prefs.getInt(KEY_RESUME_FORMAT, 0)
            val at = prefs.getLong(KEY_RESUME_AT, 0L).coerceAtLeast(0L)
            if (!uri.startsWith("spotify:track:") || file.length < 32) {
                log.i("resume: no remembered track to prefetch")
                return@launch
            }
            if (sourceFor(uri) != null) return@launch
            val source = warmSource(uri, spclient, hintFile = file, hintFormat = format)
            if (source == null) {
                log.i("resume: '${uri.takeLast(12)}' did not resolve, autoplay takes the cold path")
                return@launch
            }
            log.i("resume: '${uri.takeLast(12)}' file=${source.fileId.take(8)} source warmed at ${at}ms")
        }
    }

    private fun adoptLegacyResume() {
        if (resumeStore == PLAYER_PREFS) return
        val legacy = AppPrefs.prefs(context, PLAYER_PREFS)
        val uri = legacy.getString(KEY_RESUME_URI, "").orEmpty()
        if (uri.startsWith("spotify:track:")) {
            val scoped = AppPrefs.prefs(context, resumeStore)
            if (scoped.getString(KEY_RESUME_URI, "").orEmpty().isBlank()) {
                scoped.edit()
                    .putString(KEY_RESUME_URI, uri)
                    .putString(KEY_RESUME_FILE, legacy.getString(KEY_RESUME_FILE, "").orEmpty())
                    .putInt(KEY_RESUME_FORMAT, legacy.getInt(KEY_RESUME_FORMAT, 0))
                    .putLong(KEY_RESUME_AT, legacy.getLong(KEY_RESUME_AT, 0L))
                    .apply()
            }
        }
        legacy.edit()
            .remove(KEY_RESUME_URI)
            .remove(KEY_RESUME_FILE)
            .remove(KEY_RESUME_FORMAT)
            .remove(KEY_RESUME_AT)
            .apply()
    }

    private fun persistResume() {
        val current = _state.value
        val file = current.fileId
        if (file.isBlank() || !current.trackUri.startsWith("spotify:track:")) return
        if (current.trackUri == resumeWrittenUri &&
            kotlin.math.abs(current.positionMs - resumeWrittenMs) < RESUME_WRITE_MS
        ) return
        resumeWrittenUri = current.trackUri
        resumeWrittenMs = current.positionMs
        AppPrefs.prefs(context, resumeStore).edit()
            .putString(KEY_RESUME_URI, current.trackUri)
            .putString(KEY_RESUME_FILE, file)
            .putInt(KEY_RESUME_FORMAT, current.format)
            .putLong(KEY_RESUME_AT, current.positionMs)
            .apply()
    }

    val positionMs: Long
        get() = if (videoActive) video.positionMs else engine?.takeIf { it.hasMedia }?.currentPositionMs
            ?: _state.value.positionMs

    val durationMs: Long get() = _state.value.durationMs

    fun durationForItem(uri: String): Long = engine?.durationFor(uri) ?: 0L

    val isPlaying: Boolean get() = if (videoActive) video.isPlaying else _state.value.playing

    val format: Int get() = _state.value.format

    val bitrate: Int get() = _state.value.bitrate

    val quality: String get() = _state.value.quality

    /**
     * One read of the live pipeline for the now-playing sheet: the engine's
     * measured decoder format beside whatever the serving source declared
     * (catalogue file, alternate stream or music video dash). Null while
     * nothing is on the player.
     */
    fun audioPipeline(): AudioPipelineSnapshot? {
        if (videoActive) {
            if (_state.value.trackUri.isBlank()) return null
            val decoder = video.decoderFormat()
            return AudioPipelineSnapshot(
                sourceKey = AudioPipelineSnapshot.SOURCE_VIDEO,
                mimeType = decoder?.mimeType,
                codecs = decoder?.codecs,
                sampleRateHz = decoder?.sampleRateHz,
                channels = decoder?.channels,
                bitDepth = decoder?.bitDepth,
                decoderBitrateBps = decoder?.bitrateBps,
            )
        }
        val uri = _state.value.trackUri
        if (uri.isBlank()) return null
        val decoder = engine?.decoderFormat()
        val catalogue = sourceFor(uri)
        val alternate = YtStreamTable.of(YtStreamTable.keyFor(uri))
        return AudioPipelineSnapshot(
            sourceKey = _state.value.source.key,
            mimeType = decoder?.mimeType ?: alternate?.mime,
            codecs = decoder?.codecs,
            sampleRateHz = decoder?.sampleRateHz,
            channels = decoder?.channels,
            bitDepth = decoder?.bitDepth,
            decoderBitrateBps = decoder?.bitrateBps,
            declaredBitrateBps = catalogue?.bitrate ?: alternate?.bitrate,
            declaredQuality = catalogue?.quality,
            fileId = catalogue?.fileId,
            videoId = alternate?.videoId ?: _state.value.videoId,
        )
    }

    val hasSource: Boolean
        get() = videoActive || (engine?.hasMedia == true && !engineIdle) || job?.isActive == true

    val hasQueuedNext: Boolean get() = !videoActive && (engine?.takeIf { it.hasMedia }?.hasQueuedNext ?: false)

    val currentUri: String
        get() = engine?.takeIf { it.hasMedia }?.currentUri ?: _state.value.trackUri

    fun play(
        track: CommandTrack,
        spclient: String,
        fromMs: Long = 0L,
        divert: Boolean = false,
    ) {
        if (!licenceRetrying) LicenseGate.resetStrikes()
        val sinceLast = System.currentTimeMillis() - lastPlayAt
        licenceSettling = lastPlayAt > 0L && sinceLast < LICENCE_SKIP_WINDOW_MS
        lastPlayAt = System.currentTimeMillis()
        if (track.isDirect) {
            playDirect(track, fromMs)
            return
        }
        val file = track.best() ?: track.files.firstOrNull()
        this.spclient = spclient
        if (!divert) transportDivertUri = null
        stop()
        if (!focus.acquire()) {
            log.w("play refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        playingTrack = track
        queuedNextUri = ""
        queuedNextTrack = null
        _state.value = PlayerState(
            playing = true,
            buffering = true,
            durationMs = track.durationMs,
            trackUri = track.uri,
        )
        log.i("play '${track.name}' ${track.uri} " +
                "file=${file?.fileId ?: "by uri"} fmt=${file?.format ?: 0} ${file?.bitrate ?: 0}b"
        )
        job = scope.launch {
            var player: AudioEngine? = null
            try {
                val stack = StreamSources.order(context)
                if (!divert && stack.firstOrNull() == StreamSource.YT_MUSIC) {
                    val alternate = resolveAlternate(track)
                    if (alternate != null) {
                        playYtMusic(track, alternate, fromMs)
                        return@launch
                    }
                    log.w("youtube music leads the stack but the track did not resolve, trying spotify")
                }
                val source = sourceFor(track.uri) ?: warmSource(track.uri, spclient)
                if (source == null) {
                    if (stack.firstOrNull() != StreamSource.YT_MUSIC) {
                        val alternate = resolveAlternate(track)
                        if (alternate != null) {
                            playYtMusic(track, alternate, fromMs)
                            return@launch
                        }
                    }
                    if (id == generation) settleStopped()
                    _events.emit(PlayerEvent.Failed("no source"))
                    return@launch
                }
                rememberFile(track.uri, source.fileId)
                SongCache.touch(track.uri, context)
                preResolveAlternate(track)
                if (licenceSettling) {
                    log.i("licence deferred, the user is still skipping")
                    delay(LICENCE_SETTLE_MS.milliseconds)
                    if (id != generation) {
                        log.i("licence skipped, playback moved on before settling")
                        return@launch
                    }
                }
                if (id != generation) {
                    log.i("play was skipped while its source resolved")
                    return@launch
                }
                val totalMs = source.seektable.totalDuration * 1000L / source.seektable.timescale
                val sink: (PlayerEvent) -> Unit = { event ->
                    if (id == generation) {
                        val failedEvent = event as? PlayerEvent.Failed
                        val failed = failedEvent?.message
                            ?.takeIf { it.contains("DRM_LICENSE_ACQUISITION_FAILED") }
                        if (failed != null) {
                            // A queued track may already have taken the slot by
                            // the time the licence for the previous one comes
                            // back refused; retrying the stale track would loop
                            // the same two rows, so whatever is waiting is the
                            // one to keep.
                            val queued = queuedNextTrack
                            val liveUri = player?.currentUri.orEmpty().ifBlank { _state.value.trackUri }
                            val waiting = when {
                                queued != null && liveUri == queuedNextUri -> queued
                                liveUri.isNotBlank() && liveUri != track.uri ->
                                    playingTrack?.takeIf { it.uri == liveUri } ?: track
                                else -> track
                            }
                            val movedOn = waiting.uri != track.uri
                            val from = player?.currentPositionMs ?: 0L
                            val refusedSource = sourceFor(waiting.uri)
                            if (refusedSource != null && !LicenseGate.rateLimited) {
                                SourceResolver.blockFormat(refusedSource.format)
                                forgetSource(waiting.uri)
                                log.w("the licence refused format ${refusedSource.format}, trying the next one")
                                scope.launch {
                                    if (id == generation) play(waiting, spclient, from)
                                }
                            } else if (refusedSource != null) {
                                if (!divertToAlternate(waiting, from)) {
                                    log.w("the licence is rate limited, keeping format ${refusedSource.format}")
                                    licencePending = waiting
                                    licenceFrom = if (movedOn) 0L else fromMs
                                }
                            } else if (LicenseGate.remainingMs() > 0L) {
                                if (!divertToAlternate(waiting, from)) {
                                    refused = waiting
                                    refusedFrom = from
                                    log.w("the licence is closed for ${LicenseGate.remainingMs() / 1000}s, " +
                                            "keeping '${waiting.name.ifBlank { waiting.uri }}' for a retry"
                                    )
                                }
                            } else if (!divertToAlternate(waiting, from, failedEvent)) {
                                log.w("the licence failed on '${waiting.name.ifBlank { waiting.uri }}', " +
                                        "no alternate could take the track"
                                )
                                mirror(event, player?.durationMs ?: totalMs)
                                _events.tryEmit(event)
                            }
                        } else {
                            if (event is PlayerEvent.Failed && transportDivertUri == null &&
                                _state.value.positionMs > 0L &&
                                divertTransport(track, StreamSource.SPOTIFY, id, _state.value.positionMs, event)
                            ) {
                                // The divert owns this failure and re-emits it
                                // only if the resume falls through, so the
                                // bridge's gapless-failure advance cannot race
                                // the mid-song handover.
                            } else {
                                mirror(event, player?.durationMs ?: totalMs)
                                if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                            }
                        }
                    }
                }
                player = engine?.also { it.onEvent = sink }
                    ?: AudioEngine(context, scope, sink).also { engine = it }
                val startAt = if (pendingSeekMs >= 0L) pendingSeekMs.also { pendingSeekMs = -1L } else fromMs
                val ready = player.prepare(source, track.uri, startAt, autoplay = !pendingPause, file?.gainDb ?: 0.0)
                if (!ready || id != generation) {
                    player.stop()
                    if (id == generation) {
                        settleStopped()
                        _events.emit(PlayerEvent.Failed("prepare"))
                    } else {
                        log.i("play was superseded before the engine came up")
                    }
                    return@launch
                }
                engine = player
                engineIdle = false
                _state.value = PlayerState(
                    playing = !pendingPause,
                    paused = pendingPause,
                    buffering = !pendingPause,
                    durationMs = totalMs,
                    fileId = source.fileId,
                    format = source.format,
                    bitrate = source.bitrate,
                    quality = source.quality,
                    trackUri = track.uri,
                )
                if (pendingPause) {
                    log.i("play was asked to stay paused, holding it at ${startAt}ms")
                }
                if (pendingSeekMs >= 0L) {
                    val parked = pendingSeekMs.also { pendingSeekMs = -1L }
                    log.i("a seek parked while the engine was loading, applying at ${parked}ms")
                    player.seekTo(parked)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("play failed ${failure.javaClass.simpleName}: ${failure.message}")
                player?.stop()
                if (player != null && engine === player) engineIdle = true
                val refusedSource = sourceFor(track.uri)
                if (refusedSource != null &&
                    failure.message.orEmpty().contains("DRM_LICENSE_ACQUISITION_FAILED") &&
                    !LicenseGate.rateLimited
                ) {
                    SourceResolver.blockFormat(refusedSource.format)
                    forgetSource(track.uri)
                    log.w("the licence refused format ${refusedSource.format}, trying the next one")
                    play(track, spclient, fromMs)
                    return@launch
                }
                if (LicenseGate.remainingMs() > 0L) {
                    if (!divertToAlternate(track, fromMs)) {
                        refused = track
                        refusedFrom = fromMs
                        licencePending = track
                        licenceFrom = fromMs
                        log.w("the licence is closed for ${LicenseGate.remainingMs() / 1000}s, " +
                                "keeping '${track.name.ifBlank { track.uri }}' for a retry"
                        )
                    }
                    return@launch
                }
                val message = failure.message.orEmpty()
                if (message.contains("DRM_LICENSE_ACQUISITION_FAILED") &&
                    divertToAlternate(track, fromMs, PlayerEvent.Failed(message))
                ) {
                    return@launch
                }
                _events.emit(PlayerEvent.Failed(message))
            }
        }
    }

    private fun divertToAlternate(
        track: CommandTrack,
        fromMs: Long,
        failure: PlayerEvent.Failed? = null,
    ): Boolean {
        if (StreamSources.after(context, StreamSource.SPOTIFY).isEmpty()) return false
        refused = track
        refusedFrom = fromMs
        licencePending = track
        licenceFrom = fromMs
        val id = generation
        scope.launch {
            val alternate = resolveAlternate(track)
            if (alternate == null) {
                log.w("the licence is refused and youtube music did not resolve, the track stays parked")
                if (failure != null && id == generation) {
                    mirror(failure, _state.value.durationMs)
                    _events.emit(failure)
                }
                return@launch
            }
            licencePending = null
            refused = null
            log.i("the licence is refused, diverting '${track.name.ifBlank { track.uri }}' to youtube music")
            playYtMusic(track, alternate, fromMs)
        }
        return true
    }

    /**
     * A transport failure mid-song hands the track to the next source in the
     * user's order at the parked position, so a stream that dies partway
     * carries on from where it stopped instead of surfacing a dead player.
     * One divert per playback: a source that dies on both ends reports its
     * failure rather than the two handing the track back and forth.
     *
     * The claim happens before the caller surfaces the failure: the bridge
     * reacts to a visible Failed while a gapless next is queued by advancing
     * the hard way, which races this resume and turns a mid-song handover
     * into a skip. While the divert runs the failure is swallowed; it is
     * emitted only when the resume falls through, so the hard advance is the
     * fallback instead of the winner.
     *
     * Returns false when no divert is possible and the caller must surface
     * the failure immediately.
     */
    private fun divertTransport(
        track: CommandTrack,
        from: StreamSource,
        id: Int,
        positionMs: Long,
        failure: PlayerEvent.Failed,
    ): Boolean {
        val next = StreamSources.after(context, from).firstOrNull() ?: return false
        transportDivertUri = track.uri
        log.w("the ${from.key} stream died at ${positionMs}ms, handing " +
                "'${track.name.ifBlank { track.uri }}' to ${next.key}"
        )
        _state.update { it.copy(buffering = true) }
        scope.launch {
            if (id != generation) return@launch
            if (next == StreamSource.YT_MUSIC) {
                val alternate = resolveAlternate(track)
                if (alternate == null) {
                    divertFailed(failure, id)
                    return@launch
                }
                playYtMusic(track, alternate, positionMs, divert = true)
            } else {
                // The death spot rides the stream's timeline; the next source
                // plays the track's own cut, so fold it back across first.
                val folded = replant(positionMs, _state.value.durationMs, track.durationMs)
                if (folded != positionMs) {
                    log.i("the handover spot ${positionMs}ms folds to ${folded}ms on the track's cut")
                }
                play(track, spclient, folded, divert = true)
            }
        }
        return true
    }

    /** The resume fell through: hand the original failure to the bridge so the normal advance takes over. */
    private suspend fun divertFailed(event: PlayerEvent.Failed, id: Int) {
        log.w("the alternate did not resolve, the failure stands")
        if (id != generation) return
        transportDivertUri = null
        mirror(event, _state.value.durationMs)
        _events.emit(event)
    }

    private fun playYtMusic(track: CommandTrack, stream: YtStream, fromMs: Long = 0L, divert: Boolean = false) {
        if (!divert) transportDivertUri = null
        val parked = pendingSeekMs.takeIf { it >= 0L }?.also { pendingSeekMs = -1L } ?: -1L
        stop()
        if (!focus.acquire()) {
            log.w("play youtube music refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        playingTrack = track
        _state.value = PlayerState(
            playing = true,
            buffering = true,
            durationMs = stream.lengthMs,
            bitrate = stream.bitrate,
            trackUri = track.uri,
            source = StreamSource.YT_MUSIC,
            videoId = stream.videoId,
        )
        log.i("play youtube music '${track.name.ifBlank { track.uri }}' " +
                "${stream.bitrate}b video=${stream.videoId}"
        )
        job = scope.launch {
            var player: AudioEngine? = null
            try {
                // The resume point rides the track's timeline; the stream is
                // the alternate cut's, so fold it across before the engine
                // seeks — a handover at 2:00 of a 3:00 track must not land at
                // 2:00 of a 3:20 edit.
                val requestedMs = if (parked >= 0L) parked else fromMs
                val startAtMs = replant(requestedMs, track.durationMs, stream.lengthMs)
                if (startAtMs != requestedMs) {
                    log.i("resume ${requestedMs}ms folds to ${startAtMs}ms: the stream runs " +
                            "${stream.lengthMs}ms against the track's ${track.durationMs}ms"
                    )
                }
                // The alternate rides the same session player as the catalogue
                // files, so the queue, the crossfade and the blends keep
                // working across a handover instead of restarting between two
                // engines.
                val sink: (PlayerEvent) -> Unit = { event ->
                    if (id == generation) {
                        if (event is PlayerEvent.Failed && transportDivertUri == null &&
                            _state.value.positionMs > 0L &&
                            divertTransport(track, StreamSource.YT_MUSIC, id, _state.value.positionMs, event)
                        ) {
                            // Same guard as the spotify sink: the failure stays
                            // hidden while the handover back is still possible.
                        } else {
                            mirror(event, player?.durationMs ?: stream.lengthMs)
                            if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                        }
                    }
                }
                player = engine?.also { it.onEvent = sink }
                    ?: AudioEngine(context, scope, sink).also { engine = it }
                val ready = player.prepareAlternate(track.uri, stream, startAtMs, autoplay = !pendingPause)
                if (!ready || id != generation) {
                    player.stop()
                    if (id == generation) {
                        settleStopped()
                        _events.emit(PlayerEvent.Failed("prepare"))
                    } else {
                        log.i("play was superseded before the engine came up")
                    }
                    return@launch
                }
                engineIdle = false
                if (pendingPause) {
                    log.i("play was asked to stay paused, holding it at ${startAtMs}ms")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("play youtube music failed ${failure.javaClass.simpleName}: ${failure.message}")
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            }
        }
    }

    private fun playDirect(track: CommandTrack, fromMs: Long = 0L) {
        val file = track.bestDirect()
        val url = file?.url.orEmpty()
        if (url.isBlank()) {
            log.w("play '${track.uri}': no direct url in the manifest")
            _events.tryEmit(PlayerEvent.Failed("no direct url"))
            return
        }
        val parked = pendingSeekMs.takeIf { it >= 0L }?.also { pendingSeekMs = -1L } ?: -1L
        stop()
        if (!focus.acquire()) {
            log.w("play direct refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        playingTrack = track
        _state.value = PlayerState(playing = true, buffering = true, trackUri = track.uri)
        log.i("play direct '${track.name.ifBlank { track.uri }}' ${file?.bitrate ?: 0}b ${url.take(72)}")
        job = scope.launch {
            var player: AudioEngine? = null
            try {
                // The ad body rides the same session player as the catalogue
                // files and the alternates - one engine, one focus path, one
                // set of transport semantics - streamed out of the pool
                // instead of the song cache, so a one-shot impression url
                // never sits on disk.
                val sink: (PlayerEvent) -> Unit = { event ->
                    if (id == generation) {
                        mirror(event, player?.durationMs ?: track.durationMs)
                        if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                    }
                }
                player = engine?.also { it.onEvent = sink }
                    ?: AudioEngine(context, scope, sink).also { engine = it }
                val startAtMs = if (parked >= 0L) parked else fromMs
                val ready = player.prepareDirect(
                    track.uri, url, startAtMs,
                    autoplay = !pendingPause, trackDurationMs = track.durationMs,
                )
                if (!ready || id != generation) {
                    player.stop()
                    if (id == generation) {
                        settleStopped()
                        _events.emit(PlayerEvent.Failed("prepare"))
                    } else {
                        log.i("play was superseded before the engine came up")
                    }
                    return@launch
                }
                engineIdle = false
                if (pendingPause) {
                    log.i("play was asked to stay paused, holding it at ${startAtMs}ms")
                }
                if (pendingSeekMs >= 0L) {
                    val seek = pendingSeekMs.also { pendingSeekMs = -1L }
                    log.i("a seek parked while the engine was loading, applying at ${seek}ms")
                    player.seekTo(seek)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("play direct failed ${failure.javaClass.simpleName}: ${failure.message}")
                player?.stop()
                if (player != null && engine === player) engineIdle = true
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            }
        }
    }

    fun pause() {
        pausedByFocus = false
        pauseInternal()
        focus.abandon()
    }

    private fun pauseInternal() {
        pendingPause = true
        if (videoActive) video.pause()
        engine?.pause()
        _state.update { it.copy(playing = false, paused = true, buffering = false) }
    }

    fun resume() {
        pausedByFocus = false
        resumeInternal()
    }

    private fun resumeInternal() {
        if (!focus.acquire()) {
            log.w("resume refused, another app holds the audio focus")
            return
        }
        pendingPause = false
        if (videoActive) video.play()
        engine?.resume()
        _state.update { it.copy(playing = true, paused = false, buffering = false) }
    }

    fun duck(level: Float) {
        if (videoActive) video.duck(level)
        engine?.duck(level)
    }

    /** User volume on the local sink, kept separate from audio-focus ducking. */
    fun setVolume(level: Float) {
        if (videoActive) video.duck(level)
        engine?.volume(level)
    }

    fun seekTo(ms: Long) {
        if (videoActive) {
            log.i("video seek -> ${ms}ms")
            video.seekTo(ms)
            _state.update { it.copy(positionMs = ms) }
            return
        }
        val engine = engine?.takeIf { it.hasMedia }
        if (engine == null) {
            if (job?.isActive == true) {
                pendingSeekMs = ms
                _state.update { it.copy(positionMs = ms) }
                log.i("seek -> ${ms}ms parked, the engine is still loading")
            } else {
                log.w("seek -> ${ms}ms ignored, nothing is running")
            }
            return
        }
        log.i("seek -> ${ms}ms")
        engine.seekTo(ms)
        _state.update { it.copy(positionMs = engine.currentPositionMs) }
    }

    /** Nothing is running: the state must not keep claiming it plays. */
    private fun settleStopped() {
        _state.update { it.copy(playing = false, paused = false, buffering = false) }
    }

    fun stop() {
        generation++
        pendingPause = false
        pendingSeekMs = -1L
        queuedNextUri = ""
        queuedNextTrack = null
        playingTrack = null
        refused = null
        automixAnalysis.interrupt()
        YtStreamPool.unpinAll()
        stopVideo()
        deepJob?.cancel()
        deepTarget = ""
        engine?.stop()
        engineIdle = true
        job?.cancel()
        job = null
        focus.abandon()
        _state.update { it.copy(playing = false, paused = false, buffering = false) }
    }

    fun release() {
        stop()
        engine?.release()
        engine = null
        automixAnalysis.release()
        LicenseEndpoint.reset()
    }

    /** Hands the surface the video renders into; kept until the engine starts. */
    fun videoSurface(surface: Surface?) {
        pendingVideoSurface = surface
        video.setSurface(surface)
    }

    /** Constrains the video rendition by height; 0 or below restores auto. */
    fun selectVideoQuality(height: Int) {
        video.selectQuality(height)
    }

    fun playbackRate(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.5f)
        if (state.value.isVideo) {
            video.rate(clamped)
        } else {
            engine?.rate(clamped)
        }
        log.i("playback rate -> ${clamped}x")
    }

    /**
     * Plays a music video entity: resolves its source manifest, builds the DASH
     * session and takes over playback from the audio pipeline at fromMs.
     * Returns false when the switch did not engage (unknown uri shape or no
     * audio focus), true once the video session is on the engine.
     */
    fun playVideo(videoUri: String, audioTrackUri: String, durationMs: Long, fromMs: Long): Boolean {
        if (!videoUri.startsWith("spotify:track:") && !videoUri.startsWith("spotify:episode:")) return false
        stopAudio()
        if (!focus.acquire()) {
            log.w("video play refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return false
        }
        val id = ++generation
        pendingPause = false
        videoActive = true
        _state.value = PlayerState(
            playing = true,
            buffering = true,
            durationMs = durationMs,
            trackUri = audioTrackUri,
            isVideo = true,
        )
        log.i("play video '$videoUri' of '$audioTrackUri' from ${fromMs}ms")
        job = scope.launch {
            try {
                val source = VideoManifests.resolve(context, spclient, videoUri)
                if (source == null) {
                    log.w("video '$videoUri' did not resolve, falling back to audio")
                    videoActive = false
                    _events.emit(PlayerEvent.Failed("no video source"))
                    return@launch
                }
                if (id != generation) return@launch
                val ready = video.prepare(source.manifest, fromMs, autoplay = !pendingPause)
                if (!ready) {
                    videoActive = false
                    _events.emit(PlayerEvent.Failed("video engine failed"))
                    return@launch
                }
                if (id != generation) return@launch
                video.setSurface(pendingVideoSurface)
                _state.value = PlayerState(
                    playing = !pendingPause,
                    paused = pendingPause,
                    buffering = !pendingPause,
                    durationMs = source.manifest.durationMs,
                    trackUri = audioTrackUri,
                    isVideo = true,
                )
                if (pendingPause) video.pause()
                if (!pendingPause) video.play()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("video play failed ${failure.javaClass.simpleName}: ${failure.message}")
                videoActive = false
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            }
        }
        return true
    }

    /** Leaves video mode; the caller resumes audio playback at fromMs. */
    fun stopVideo() {
        if (!videoActive) return
        videoActive = false
        video.stop()
        _state.update { it.copy(isVideo = false, playing = false, buffering = false) }
        log.i("video mode stopped")
    }

    private fun stopAudio() {
        generation++
        engine?.stop()
        job?.cancel()
        job = null
    }

    fun reset() {
        stop()
        engine?.release()
        engine = null
        _state.value = PlayerState()
        LicenseEndpoint.reset()
        log.i("reset, engine and state cleared")
    }

    private fun mirror(event: PlayerEvent, durationMs: Long) {
        _state.update { current ->
            when (event) {
                is PlayerEvent.Progress -> current.copy(positionMs = event.positionMs, durationMs = event.durationMs)
                is PlayerEvent.Seeked -> current.copy(positionMs = event.positionMs)
                is PlayerEvent.Started -> {
                    val itemMs = engine?.durationFor(event.uri) ?: 0L
                    val settled = itemMs.takeIf { it > 0L } ?: durationMs
                    val moved = event.uri.isNotBlank() && event.uri != current.trackUri
                    val base = if (moved) {
                        current.copy(trackUri = event.uri, positionMs = 0L, durationMs = settled)
                    } else {
                        current.copy(durationMs = settled)
                    }
                    if (event.uri == queuedNextUri) {
                        queuedNextTrack?.let { playingTrack = it }
                        engine?.retune(queuedGainDb)
                    }
                    if (current.paused) {
                        base.copy(buffering = false)
                    } else {
                        base.copy(playing = true, paused = false, buffering = false)
                    }
                }
                is PlayerEvent.Ended -> current.copy(playing = false, buffering = false, positionMs = durationMs)
                is PlayerEvent.Failed -> current.copy(playing = false, buffering = false, trackUri = "")
            }
        }
        persistResume()
    }
}
