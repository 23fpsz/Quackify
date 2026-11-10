package com.quackify.backend.player

import android.content.Context
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.quackify.backend.connect.command.AudioFile
import com.quackify.backend.connect.command.CommandTrack
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net

private val log = Logs.of(Logs.Module.PLAYER)

private const val MANIFEST_CACHE = 8

private const val FILE_ID_CACHE = 64

private const val SOURCE_CACHE = 8

private const val DEEP_LOOKAHEAD = 1

private const val KEY_SETTLE_MS = 300L

private const val DUCK_LEVEL = 0.2f

private const val PLAYER_PREFS = "quackify.player"

private const val KEY_RESUME_URI = "resumeUri"

private const val KEY_RESUME_FILE = "resumeFile"

private const val KEY_RESUME_FORMAT = "resumeFormat"

private const val KEY_RESUME_AT = "resumeAt"

private const val RESUME_WRITE_MS = 5_000L

sealed interface PlayerEvent {
    data object Started : PlayerEvent
    data object Ended : PlayerEvent
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
)

class KrespotPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PlayerEvent> = _events

    @Volatile
    private var engine: PlayerEngine? = null

    @Volatile
    private var mp3: Mp3Engine? = null

    private var job: Job? = null

    @Volatile
    private var generation = 0

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

    private val widevine = Widevine(context) { spclient }

    private val focus = AudioFocus(context)

    var onFocusPause: (() -> Unit)? = null

    var onFocusResume: (() -> Unit)? = null

    var onFocusDuck: ((Boolean) -> Unit)? = null

    init {
        LicenseGate.onOpen = {
            val track = refused
            if (track != null) {
                refused = null
                log.i("licence is back, retrying '${track.name.ifBlank { track.uri }}' " +
                        "from ${refusedFrom}ms"
                )
                play(track, spclient, refusedFrom)
            }
        }
    }

    private val manifests = java.util.concurrent.ConcurrentHashMap<String, Deferred<AudioFile?>>()

    private val fileIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val fileIdOrder = java.util.ArrayDeque<String>()

    private val sources = java.util.LinkedHashMap<String, AudioSource>()

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
                val file = manifestFor(track.uri) ?: return@launch
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
            deepWarmed.removeAll(drop)
            drop
        }
        if (stale.isEmpty()) return
        stale.forEach { uri ->
            val fileId = sourceFor(uri)?.fileId ?: fileIds[uri] ?: return@forEach
            SegmentStore.evict(fileId)
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
                val known = fileIds[track.uri]
                val file = manifestFor(track.uri)
                if (known == null && file == null) return@forEach
                if (file != null) rememberFile(track.uri, file.fileId)
                val source = SourceResolver.resolve(
                    context,
                    spclient,
                    track.uri,
                    fileId = known ?: file!!.fileId,
                    format = file?.format ?: 0,
                    prefetched = file,
                ) ?: return@forEach
                rememberSource(track.uri, source)
                widevine.open(source.seektable.pssh)
                SegmentStore.warm(source, 0L)
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

    private suspend fun manifestFor(trackUri: String): AudioFile? {
        val pending = manifests[trackUri] ?: return null
        val file = runCatching { pending.await() }.getOrNull()
        if (file == null) manifests.remove(trackUri, pending)
        return file
    }

    init {
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
        scope.launch(Dispatchers.IO) {
            val started = android.os.SystemClock.elapsedRealtime()
            runCatching { AudioSink(44_100, 2).release() }
            val sink = android.os.SystemClock.elapsedRealtime() - started
            val decoder = warmDecoder()
            log.i("audio sink warmed in ${sink}ms, decoder in ${decoder}ms")
        }
        scope.launch { widevine.warm() }
    }

    private fun warmDecoder(): Long {
        val started = android.os.SystemClock.elapsedRealtime()
        runCatching {
            val format = android.media.MediaFormat
                .createAudioFormat("audio/mp4a-latm", 44_100, 2)
                .apply { setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(byteArrayOf(0x12, 0x10))) }
            val codec = android.media.MediaCodec.createDecoderByType("audio/mp4a-latm")
            try {
                codec.configure(format, null, null, 0)
                codec.start()
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        }
        return android.os.SystemClock.elapsedRealtime() - started
    }

    fun resumeLast(spclient: String) {
        scope.launch {
            val prefs = context.getSharedPreferences(PLAYER_PREFS, Context.MODE_PRIVATE)
            val uri = prefs.getString(KEY_RESUME_URI, "").orEmpty()
            val file = prefs.getString(KEY_RESUME_FILE, "").orEmpty()
            val format = prefs.getInt(KEY_RESUME_FORMAT, 0)
            val at = prefs.getLong(KEY_RESUME_AT, 0L).coerceAtLeast(0L)
            if (!uri.startsWith("spotify:track:") || file.length < 32) {
                log.i("resume: no remembered track to prefetch")
                return@launch
            }
            if (sourceFor(uri) != null) return@launch
            val source = runCatching {
                SourceResolver.resolve(context, spclient, uri, fileId = file, format = format)
            }.getOrNull()
            if (source == null) {
                log.i("resume: '${uri.takeLast(12)}' did not resolve, autoplay takes the cold path")
                return@launch
            }
            rememberSource(uri, source)
            widevine.open(source.seektable.pssh)
            SegmentStore.warm(source, at)
            log.i("resume: '${uri.takeLast(12)}' file=${source.fileId.take(8)} " +
                    "licence and segments warmed at ${at}ms"
            )
        }
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
        context.getSharedPreferences(PLAYER_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RESUME_URI, current.trackUri)
            .putString(KEY_RESUME_FILE, file)
            .putInt(KEY_RESUME_FORMAT, current.format)
            .putLong(KEY_RESUME_AT, current.positionMs)
            .apply()
    }

    val positionMs: Long
        get() = engine?.currentPositionMs ?: mp3?.currentPositionMs ?: _state.value.positionMs

    val durationMs: Long get() = _state.value.durationMs

    val isPlaying: Boolean get() = _state.value.playing

    val format: Int get() = _state.value.format

    val bitrate: Int get() = _state.value.bitrate

    val quality: String get() = _state.value.quality

    val hasSource: Boolean get() = engine != null || mp3 != null || job?.isActive == true

    fun play(
        track: CommandTrack,
        spclient: String,
        fromMs: Long = 0L,
    ) {
        if (track.isDirect) {
            playDirect(track)
            return
        }
        val file = track.best() ?: track.files.firstOrNull()
        this.spclient = spclient
        stop()
        if (!focus.acquire()) {
            log.w("play refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        _state.value = PlayerState(
            playing = true,
            buffering = true,
            durationMs = track.durationMs,
            trackUri = track.uri,
        )
        log.i("play '${track.name}' ${track.uri} " +
                "file=${file?.fileId ?: "by uri"} fmt=${file?.format ?: 0} ${file?.bitrate ?: 0}b"
        )
        val startedAt = SystemClock.elapsedRealtime()
        job = scope.launch {
            var player: PlayerEngine? = null
            try {
                val source = sourceFor(track.uri)
                    ?: SourceResolver.resolve(
                        context,
                        spclient,
                        track.uri,
                        file?.fileId.orEmpty(),
                        file?.format ?: 0,
                        prefetched = if (file == null) manifestFor(track.uri) else null,
                    )?.also { rememberSource(track.uri, it) }
                if (source == null) {
                    _events.emit(PlayerEvent.Failed("no source"))
                    return@launch
                }
                rememberFile(track.uri, source.fileId)
                val warmed = async(Dispatchers.IO) { SegmentStore.warm(source, fromMs) }
                val drm = async(Dispatchers.IO) {
                    val left = KEY_SETTLE_MS - (SystemClock.elapsedRealtime() - startedAt)
                    if (left > 0L) delay(left)
                    if (id != generation) {
                        log.i("play was skipped before its key, nothing asked")
                        return@async null
                    }
                    widevine.open(source.seektable.pssh)
                }
                val entry = warmed.await()
                val initBytes = entry.init
                    ?: Net.fetch(source.url, 0, source.seektable.offset - 1)?.also { entry.init = it }
                if (initBytes == null) {
                    drm.cancel()
                    _events.emit(PlayerEvent.Failed("no init range"))
                    return@launch
                }
                if (id != generation) {
                    log.i("play was skipped while its media was warming up")
                    return@launch
                }
                val parsed = Mp4.parseInit(initBytes)
                val init = if (parsed.mediaDuration > 0) {
                    parsed
                } else {
                    parsed.copy(mediaDuration = source.seektable.totalDuration + parsed.priming)
                }
                player = PlayerEngine(source, init, widevine, file?.gainDb ?: 0.0, drm, scope, entry.segments) { event ->
                    if (id == generation) {
                        mirror(event, init.playableMs)
                        if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                    }
                }
                engine = player
                _state.value = PlayerState(
                    playing = !pendingPause,
                    paused = pendingPause,
                    buffering = !pendingPause,
                    durationMs = init.playableMs,
                    fileId = source.fileId,
                    format = source.format,
                    bitrate = source.bitrate,
                    quality = source.quality,
                    trackUri = track.uri,
                )
                if (pendingPause) {
                    log.i("play was asked to stay paused, holding it at ${fromMs}ms")
                    player.pause()
                }
                val startAt = if (pendingSeekMs >= 0L) pendingSeekMs.also { pendingSeekMs = -1L } else fromMs
                if (startAt != fromMs) {
                    log.i("a seek parked while the engine was loading, starting at ${startAt}ms")
                }
                player.run(startAt)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("play failed ${failure.javaClass.simpleName}: ${failure.message}")
                if (LicenseGate.remainingMs() > 0L) {
                    refused = track
                    refusedFrom = fromMs
                    log.w("the licence is closed for ${LicenseGate.remainingMs() / 1000}s, " +
                            "keeping '${track.name.ifBlank { track.uri }}' for a retry"
                    )
                }
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            } finally {
                if (player != null && engine === player) engine = null
            }
        }
    }

    private fun playDirect(track: CommandTrack) {
        val file = track.bestDirect()
        val url = file?.url.orEmpty()
        if (url.isBlank()) {
            log.w("play '${track.uri}': no direct url in the manifest")
            _events.tryEmit(PlayerEvent.Failed("no direct url"))
            return
        }
        stop()
        if (!focus.acquire()) {
            log.w("play direct refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        pendingSeekMs = -1L
        _state.value = PlayerState(playing = true, buffering = true, trackUri = track.uri)
        log.i("play direct '${track.name.ifBlank { track.uri }}' ${file?.bitrate ?: 0}b ${url.take(72)}")
        job = scope.launch {
            var player: Mp3Engine? = null
            try {
                player = Mp3Engine(url) { event ->
                    if (id == generation) {
                        mirror(event, _state.value.durationMs)
                        if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                    }
                }
                mp3 = player
                _state.value = PlayerState(playing = true, buffering = true, trackUri = track.uri)
                player.run()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("play direct failed ${failure.javaClass.simpleName}: ${failure.message}")
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            } finally {
                if (player != null && mp3 === player) mp3 = null
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
        engine?.pause()
        mp3?.pause()
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
        engine?.resume()
        mp3?.resume()
        _state.update { it.copy(playing = true, paused = false, buffering = false) }
    }

    fun duck(level: Float) {
        engine?.duck(level)
        mp3?.duck(level)
    }

    /** User volume on the local sink, kept separate from audio-focus ducking. */
    fun setVolume(level: Float) {
        engine?.volume(level)
        mp3?.volume(level)
    }

    fun seekTo(ms: Long) {
        val engine = engine
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

    fun stop() {
        generation++
        pendingPause = false
        pendingSeekMs = -1L
        refused = null
        deepJob?.cancel()
        deepTarget = ""
        engine?.stop()
        engine = null
        mp3?.stop()
        mp3 = null
        job?.cancel()
        job = null
        focus.abandon()
        _state.update { it.copy(playing = false, paused = false, buffering = false) }
    }

    fun release() {
        stop()
        scope.launch { widevine.close() }
        LicenseEndpoint.reset()
    }

    fun reset() {
        stop()
        _state.value = PlayerState()
        scope.launch { widevine.close() }
        LicenseEndpoint.reset()
        log.i("reset, engine and state cleared")
    }

    private fun mirror(event: PlayerEvent, durationMs: Long) {
        _state.update { current ->
            when (event) {
                is PlayerEvent.Progress -> current.copy(positionMs = event.positionMs, durationMs = event.durationMs)
                is PlayerEvent.Seeked -> current.copy(positionMs = event.positionMs)
                PlayerEvent.Started -> if (current.paused) {
                    current.copy(buffering = false, durationMs = durationMs)
                } else {
                    current.copy(playing = true, paused = false, buffering = false, durationMs = durationMs)
                }
                PlayerEvent.Ended -> current.copy(playing = false, buffering = false, positionMs = durationMs)
                is PlayerEvent.Failed -> current.copy(playing = false, buffering = false, trackUri = "")
            }
        }
        persistResume()
    }
}
