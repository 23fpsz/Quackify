package com.quackify.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.shell.ComposePlaybackState
import com.quackify.compose.shell.ComposePlayerListener
import com.quackify.backend.connect.KrespotConnect
import com.quackify.backend.connect.cluster.ClusterState
import com.quackify.backend.connect.cluster.ClusterTrack
import com.quackify.backend.model.ConnectDevice
import com.quackify.backend.model.DeviceKind
import com.quackify.backend.connect.command.CommandTrack
import com.quackify.backend.connect.command.ContentType
import com.quackify.backend.connect.command.PlaybackCommand
import com.quackify.backend.connect.command.TrackUid
import com.quackify.backend.connect.state.DebugSource
import com.quackify.backend.connect.state.Snapshot
import com.quackify.backend.player.KrespotPlayer
import com.quackify.backend.player.PlayerEvent
import com.quackify.backend.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import com.quackify.backend.core.log.Logs
import com.quackify.backend.library.Krespot
import com.quackify.backend.library.TrackSource
import com.quackify.backend.api.Library
import android.os.SystemClock

private val log = Logs.of(Logs.Module.BRIDGE)

/** Shared local prefs file the client keeps its UI flags in. */
const val CLIENT_PREFS = "quackify.client"

/** Settings toggle: start playback as soon as the session's track loads. */
const val AUTOPLAY_ON_STARTUP_PREF = "autoplayOnStartup"

/** Settings toggle: keep playing from a station when the context runs out. */
const val AUTOPLAY_ON_END_PREF = "autoplayOnEnd"

class PlaybackBridge(private val context: Context) {

    private val connect = KrespotConnect(context)

    private val player = KrespotPlayer(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var dealer: Job? = null

    private var deviceWatch: Job? = null

    private var remoteTick: Job? = null

    @Volatile
    var deviceSheetOpen: Boolean = false

    /** Settings "Autoplay on startup": read from the local prefs at start(). */
    private var autoplayOnStartup: Boolean = false
    private var autoplayPending: Boolean = false
    private var autoplayArmedAt: Long = 0L

    /** Canvas loop enabled by the user; rides along every playback state report as is_video_on. */
    @Volatile
    var canvasOn: Boolean = false
        private set

    private var mirrors: Job? = null

    private var thresholdTrack = ""

    @Volatile
    private var screen: List<String> = emptyList()

    @Volatile
    private var chainRows: List<String> = emptyList()

    @Volatile
    private var chainContext: String = ""

    @Volatile
    private var held: CommandTrack? = null
    private var blankSeen = ""

    private val hydrateTried = HashSet<String>()

    @Volatile
    private var pendingTrack = ""

    @Volatile
    private var pendingContext = ""

    @Volatile
    private var pendingIndex = -1

    @Volatile
    private var pendingAt = 0L

    @Volatile
    private var resumeUri = ""

    @Volatile
    private var resumeAt = 0L

    @Volatile
    private var queuedTail: List<TrackItem> = emptyList()

    // Shuffle/repeat the user just asked for: shown immediately and applied
    // to local advancement until the cluster push confirms (or the override
    // expires because the server refused).
    @Volatile
    private var pendingShuffle: Boolean? = null

    @Volatile
    private var pendingRepeatContext: Boolean? = null

    @Volatile
    private var pendingRepeatTrack: Boolean? = null

    @Volatile
    private var pendingModeAt = 0L

    private val decorated = java.util.concurrent.ConcurrentHashMap<String, TrackItem>()

    private val undecoratable = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    private var decorateJob: Job? = null

    @Volatile
    private var decoratePending: List<String> = emptyList()

    @Volatile
    private var resumeRow = -1

    @Volatile
    private var advancing = false

    @Volatile
    private var clusterSeen = ""

    @Volatile
    private var activeSeen = ""

    @Volatile
    private var engineSeen = ""

    val deviceId: String get() = connect.deviceId

    init {
        connect.accepts = accept@{ command ->
            if (command.isControl) return@accept true
            if (command.isReplaceState) return@accept true
            val track = command.currentTrack
            val named = track?.uri.orEmpty()
            val waiting = pendingActive()
            val sameContext = waiting &&
                pendingContext.isNotBlank() &&
                track?.contextUri == pendingContext
            val accepted = command.isAd ||
                    (waiting && (named == pendingTrack || sameContext)) ||
                    (!waiting && (!player.hasSource || player.state.value.trackUri == named))
            if (!accepted) {
                log.i("command dropped '${named.takeLast(12)}' " +
                        "pending='${pendingTrack.takeLast(12)}' waiting=$waiting " +
                        "ctx='${track?.contextUri?.takeLast(12).orEmpty()}' " +
                        "engine='${player.state.value.trackUri.takeLast(12)}'"
                )
            } else if (waiting && sameContext && named != pendingTrack) {
                log.i("command accepted: the server started '${track?.name}' from the context we " +
                        "asked for instead of the row we named"
                )
            }
            accepted
        }
    }

    var state by mutableStateOf(ComposePlaybackState())
        private set

    var cluster: ClusterState? by mutableStateOf(null)
        private set

    val listener = object : ComposePlayerListener {
        override fun onPlayPause() {
            scope.launch { toggle() }
        }

        override fun onSkipNext() {
            scope.launch { skipTo(next = true) }
        }

        override fun onSkipPrevious() {
            scope.launch { skipTo(next = false) }
        }

        override fun onSeek(positionMs: Long) {
            val cluster = connect.cluster.value
            val remoteActive = cluster?.activeDevice?.let { it.id != deviceId } == true
            if (remoteActive) {
                scope.launch { seekRemote(positionMs) }
                return
            }
            if (!player.hasSource) {
                log.w("seek $positionMs ignored, nothing loaded")
                return
            }
            val command = connect.playback
            if (command != null && (command.isAd || command.disallowSeeking)) {
                val why = if (command.isAd) "an ad is playing" else "the state disallows seeking"
                log.w("seek $positionMs ignored, $why")
                return
            }
            val duration = player.durationMs.takeIf { it > 0L } ?: state.durationMs
            val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs
            log.i("seek $positionMs -> ${target}ms of ${duration}ms")
            state = state.copy(
                positionMs = target,
                durationMs = duration,
                progress = progressOf(target, duration),
            )
            scope.launch { player.seekTo(target) }
        }

        override fun onShuffleToggle() {
            scope.launch { toggleShuffle() }
        }

        override fun onRepeatToggle() {
            scope.launch { toggleRepeat() }
        }

        override fun onPlayUri(uri: String) {
            val context = screenContext(uri)
            captureChain(context)
            scope.launch {
                val first = connect.cluster.value
                    ?.takeIf { it.contextUri == uri }
                    ?.window()
                    ?.firstOrNull { it.isVisible }
                if (first != null) playTrack(first.uri, uri, 0) else playTrack(uri, context, -1)
            }
        }

        override fun onPlayTrack(uri: String, contextUri: String, index: Int) {
            captureChain(contextUri)
            scope.launch { playTrack(uri, contextUri, index) }
        }

        override fun onListRendered(uris: List<String>) {
            if (uris == screen) return
            screen = uris
            log.d("screen list now ${uris.size} rows, head=${uris.firstOrNull()?.takeLast(12)}")
        }

        override fun onLoadPlaylist(uri: String) {
            if (uri.isBlank()) return
            log.i("load playlist '${uri.takeLast(12)}'")
            scope.launch { connect.refreshCluster() }
        }

        override fun onLoadAlbum(uri: String, albumName: String, artist: String) {
            if (uri.isBlank()) return
            log.i("load album '${uri.takeLast(12)}' by '$artist'")
            scope.launch { connect.refreshCluster() }
        }

        override fun onLoadArtist(uri: String, artistName: String) {
            if (uri.isBlank()) return
            log.i("load artist '${uri.takeLast(12)}'")
            scope.launch { connect.refreshCluster() }
        }

        override fun onDeviceSheetVisible(visible: Boolean) {
            deviceSheetOpen = visible
            if (visible) scope.launch { connect.refreshCluster() }
        }

        override fun onSelectDevice(targetId: String) {
            scope.launch {
                val target = connect.cluster.value?.devices?.firstOrNull { it.id == targetId }
                    ?: if (targetId == deviceId) localDeviceRow(active = false) else null
                if (target == null) {
                    log.w("device select: '" + targetId + "' is not in the cluster any more")
                    return@launch
                }
                if (!target.canTransfer) {
                    log.w("device select: '" + target.name + "' cannot take the stream")
                    return@launch
                }
                val backHome = target.id == deviceId
                log.i("device select '" + target.name + "' -> " +
                        if (backHome) "taking the stream back here" else "transfer"
                )
                if (backHome) {
                    takeBackHere()
                    return@launch
                }
                val ok = connect.transferTo(target.id)
                if (!ok) {
                    log.w("device select: the transfer to '" + target.name + "' was refused")
                    return@launch
                }
            }
        }

        override fun onVolumeChange(deviceId: String, level: Int) {
            scope.launch {
                val target = connect.cluster.value?.devices?.firstOrNull { it.id == deviceId }
                if (target == null) return@launch
                if (target.local) {
                    // our own device: apply the volume natively and let the
                    // server know so other clients can see the new level
                    player.setVolume(level / 65535f)
                    log.i("volume: local sink -> ${level.coerceIn(0, 65535)}/65535")
                    connect.setVolumeOn(target.id, level)
                    return@launch
                }
                if (!target.canControlVolume) {
                    log.w("volume: '" + target.name + "' does not take a volume command")
                    return@launch
                }
                connect.setVolumeOn(target.id, level)
            }
        }

        override fun onLikeToggle() {
            val uri = state.trackUri
            if (!uri.startsWith("spotify:track:")) {
                log.w("like ignored, the state has no track uri")
                return
            }
            val next = !state.isLiked
            state = state.copy(isLiked = next)
            scope.launch {
                val ok = Library.setSaved(context, listOf(uri), next)
                log.i("like '${uri.takeLast(12)}' -> $next ok=$ok")
                if (!ok) state = state.copy(isLiked = !next)
            }
        }

        override fun onQueueAdd(uri: String) {
            scope.launch {
                val ok = connect.addToQueue(uri)
                log.i("queue += '$uri' ok=$ok")
                if (!ok) return@launch
                if (queuedTail.none { it.uri == uri }) {
                    queuedTail = queuedTail + TrackItem(title = "", artist = "", uri = uri)
                }
                publishQueue(connect.cluster.value)
                connect.refreshCluster()
            }
        }

        override fun onQueueSet(uris: List<String>) {
            scope.launch {
                val cluster = connect.cluster.value
                if (cluster == null) {
                    log.w("queue set ignored, no cluster yet")
                    return@launch
                }
                val known = (cluster.queued + cluster.fromContext)
                    .associateBy { it.uri }
                val local = queuedTail.associateBy { it.uri }
                val tracks = uris.mapNotNull { uri ->
                    known[uri] ?: local[uri]?.let { row ->
                        ClusterTrack(
                            uri = uri,
                            uid = "",
                            title = row.title,
                            artistName = row.artist,
                            artistUri = row.artistUri,
                            imageUrl = row.imageUrl.orEmpty(),
                            albumTitle = "",
                            albumUri = row.albumUri,
                            durationMs = row.durationMs,
                            explicit = row.isExplicit,
                            creativeId = "",
                            provider = "queue",
                        )
                    }
                }
                if (tracks.size != uris.size) {
                    log.w("queue set: ${uris.size - tracks.size} of ${uris.size} rows have no metadata")
                }
                val ok = connect.setQueue(tracks)
                log.i("queue = ${tracks.size} rows ok=$ok")
                if (!ok) return@launch
                queuedTail = uris.map { local[it] ?: TrackItem(title = "", artist = "", uri = it) }
                publishQueue(cluster)
                connect.refreshCluster()
            }
        }
    }

    fun start() {
        if (dealer != null) return
        log.i("start device=" + deviceId)
        autoplayOnStartup = context.getSharedPreferences(CLIENT_PREFS, Context.MODE_PRIVATE)
            .getBoolean(AUTOPLAY_ON_STARTUP_PREF, false)
        autoplayPending = true
        autoplayArmedAt = SystemClock.elapsedRealtime()
        if (autoplayOnStartup) log.i("autoplay on startup is armed")
        player.onFocusPause = { scope.launch { reportPaused() } }
        player.onFocusResume = { scope.launch { reportResumed() } }
        player.onFocusDuck = { ducked ->
            if (ducked) log.i("another app is talking over us, the volume is down") else
                log.i("the other app is done, the volume is back")
        }
        player.warm()
        connect.holdsStream = false
        deviceWatch = scope.launch {
            while (true) {
                delay(if (deviceSheetOpen || state.controllingRemote) DEVICE_WATCH_MS else DEVICE_POLL_MS)
                when {
                    // Someone else owns the stream: watch the cluster the
                    // ordinary observer way.
                    player.hasSource && remoteTarget().isNotBlank() -> {
                        connect.holdsStream = false
                        connect.refreshCluster()
                    }
                    // This phone owns the stream: publishing its state is the
                    // poll, the cluster comes back with the answer. While the
                    // engine sits paused on an old source and the cluster
                    // names another active device, re-claiming the lead here
                    // is exactly what made "Listening on" flip-flop between
                    // devices - watch instead until playback starts again.
                    player.hasSource -> {
                        val other = connect.cluster.value?.activeDeviceId
                            ?.takeIf { it.isNotBlank() && it != deviceId }
                        if (other != null && !player.isPlaying) {
                            connect.holdsStream = false
                            connect.refreshCluster()
                        } else {
                            connect.holdsStream = true
                            publishLocalFromEngine()
                        }
                    }
                    else -> {
                        connect.holdsStream = false
                        connect.refreshCluster()
                    }
                }
            }
        }
        remoteTick = scope.launch {
            while (true) {
                delay(REMOTE_TICK_MS)
                advanceRemote()
            }
        }
        dealer = scope.launch {
            runCatching { connect.frames().collect { } }
                .onFailure { log.i("dealer stopped ${it.javaClass.simpleName}: ${it.message}") }
        }
        mirrors = scope.launch {
            launch { connect.cluster.collect(::mirror) }
            launch { connect.command.collect(::mirrorState) }
            launch { connect.pushes.collect(::smokeReport) }
            launch {
                connect.transfers.collect {
                    log.i("a transfer landed here, taking the stream back")
                    connect.refreshCluster()
                    takeBackHere()
                }
            }
            launch { connect.registered.collect { log.i("registered=$it") } }
            launch {
                connect.connectionId.collect {
                    if (it.isNotBlank()) log.i("connection=${it.take(24)}")
                }
            }
            launch { player.events.collect(::onPlayerEvent) }
            launch {
                snapshotFlow { state.trackUri }
                    .filter { it.startsWith("spotify:track:") }
                    .collect { uri ->
                        val saved = Library.saved(context, listOf(uri))
                        if (state.trackUri == uri) {
                            state = state.copy(isLiked = uri in saved)
                        }
                    }
            }
            launch {
                player.state.collect { playing ->
                    if (state.controllingRemote) return@collect
                    val waiting = held
                    val engineUri = playing.trackUri
                    val engineOwns = player.hasSource && engineUri.isNotBlank()
                    val local = engineOwns && (waiting == null || engineUri == waiting.uri)
                    val position = if (engineOwns) playing.positionMs else state.positionMs
                    val duration = if (engineOwns) {
                        playing.durationMs.takeIf { it > 0L } ?: state.durationMs
                    } else {
                        state.durationMs
                    }
                    val next = state.copy(
                        title = if (local && waiting != null) waiting.name else state.title,
                        artist = if (local && waiting != null) waiting.artists else state.artist,
                        coverUrl = if (local && waiting != null) waiting.imageUrl else state.coverUrl,
                        trackUri = if (engineOwns) engineUri else state.trackUri,
                        artistUri = if (engineOwns && engineUri != state.trackUri) artistUriOf(engineUri)
                        else state.artistUri,
                        albumUri = if (engineOwns && engineUri != state.trackUri) albumUriOf(engineUri)
                        else state.albumUri,
                        isPlaying = if (engineOwns) playing.playing else state.isPlaying,
                        positionMs = position,
                        durationMs = duration,
                        progress = progressOf(position, duration),
                    )
                    // The engine ticks every 100ms, but nothing on screen can
                    // show a sub-half-second crawl: only write the position when
                    // it actually moved that far or the identity changed, so the
                    // whole shell stops recomposing at 10Hz while playing.
                    val identityChanged = next.trackUri != state.trackUri ||
                        next.title != state.title || next.artist != state.artist ||
                        next.coverUrl != state.coverUrl || next.artistUri != state.artistUri ||
                        next.albumUri != state.albumUri || next.isPlaying != state.isPlaying ||
                        next.durationMs != state.durationMs
                    if (!identityChanged && kotlin.math.abs(next.positionMs - state.positionMs) < 400L) {
                        threshold(playing)
                        return@collect
                    }
                    state = next
                    if (local && waiting != null) {
                        held = null
                        log.i("held '${waiting.name}' took the engine")
                    }
                    if (engineUri.isNotBlank() && engineUri != engineSeen) {
                        engineSeen = engineUri
                        publishQueue(connect.cluster.value)
                    }
                    threshold(playing)
                }
            }
        }
    }

    fun handOff() {
        pauseRemoteOnExit()
    }

    fun stop() {
        pauseRemoteOnExit()
        connect.holdsStream = false
        runCatching {
            runBlocking { withTimeoutOrNull(2_000L) { connect.unregister() } }
        }.onFailure { log.w("unregister on stop failed ${it.javaClass.simpleName}") }
        dealer?.cancel()
        dealer = null
        deviceWatch?.cancel()
        deviceWatch = null
        remoteTick?.cancel()
        remoteTick = null
        mirrors?.cancel()
        mirrors = null
        player.release()
        connect.reset()
        thresholdTrack = ""
        held = null
        clearPending()
        chainRows = emptyList()
        chainContext = ""
        queuedTail = emptyList()
        decorated.clear()
        undecoratable.clear()
        resumeRow = -1
        advancing = false
        clusterSeen = ""
        activeSeen = ""
        engineSeen = ""
        cluster = null
        state = ComposePlaybackState()
        scope.cancel()
        hydrateTried.clear()
        log.i("bridge stopped, every collector and the socket are gone")
    }

    fun reset() {
        log.i("reset, dropping the device state of the previous session")
        dealer?.cancel()
        dealer = null
        // stop() cancels these two; reset() used to leave both loops and the
        // whole bridge graph they retain alive across every logout and
        // account switch.
        deviceWatch?.cancel()
        deviceWatch = null
        remoteTick?.cancel()
        remoteTick = null
        mirrors?.cancel()
        mirrors = null
        player.reset()
        connect.reset()
        thresholdTrack = ""
        held = null
        clearPending()
        chainRows = emptyList()
        chainContext = ""
        queuedTail = emptyList()
        decorated.clear()
        undecoratable.clear()
        resumeRow = -1
        advancing = false
        clusterSeen = ""
        activeSeen = ""
        engineSeen = ""
        hydrateTried.clear()
        cluster = null
        state = ComposePlaybackState()
    }

    private fun markPending(uri: String, index: Int, context: String = "") {
        pendingTrack = uri
        pendingIndex = index
        pendingContext = context
        pendingAt = System.currentTimeMillis()
    }

    private fun clearPending() {
        pendingTrack = ""
        pendingIndex = -1
        pendingContext = ""
        pendingAt = 0L
    }

    private fun pendingActive(): Boolean =
        pendingTrack.isNotBlank() && System.currentTimeMillis() - pendingAt < PENDING_MS

    suspend fun playFromCluster(offset: Int = 0): Boolean {
        val cluster = connect.cluster.value
        if (cluster == null) {
            log.w("play: no cluster yet, registered=${connect.registered.value}")
            return false
        }
        val target = targetFrom(cluster, offset)
        val track = target?.track ?: cluster.track
        val index = target?.index ?: cluster.contextIndex
        log.i("playFromCluster offset=$offset anchor=${target?.anchor ?: -1} pivot=${cluster.pivot()} " +
                "ctxIdx=$index '${track.title}' uid=${track.uid.take(12)} context='${cluster.contextUri}'"
        )
        if (track.uri.startsWith("spotify:track:") || track.uri.startsWith("spotify:episode:")) {
            player.prefetch(track.uri, connect.spclientHost)
        }
        markPending(track.uri, index, cluster.contextUri)
        val active = cluster.activeDevice
        val to = if (active != null && active.id != deviceId) active.id else ""
        if (to.isNotBlank()) {
            log.i("play: the stream stays on the remote device '${active!!.name}'")
        }
        val ok = connect.play(cluster.contextUri, track.uri, track.uid, index, targetDeviceId = to)
        if (!ok) {
            clearPending()
            log.w("playFromCluster: the server refused '${track.title}', dropping the wait")
        }
        return ok
    }

    private fun modeOverrideFresh(): Boolean =
        System.currentTimeMillis() - pendingModeAt < MODE_OVERRIDE_MS

    private fun shuffleOn(cluster: ClusterState?): Boolean {
        if (modeOverrideFresh()) pendingShuffle?.let { return it }
        return cluster?.shuffle == true
    }

    private fun repeatContextOn(cluster: ClusterState?): Boolean {
        if (modeOverrideFresh()) pendingRepeatContext?.let { return it }
        return cluster?.repeatContext == true
    }

    private fun repeatTrackOn(cluster: ClusterState?): Boolean {
        if (modeOverrideFresh()) pendingRepeatTrack?.let { return it }
        return cluster?.repeatTrack == true
    }

    private fun rememberModes(shuffle: Boolean?, repeatContext: Boolean?, repeatTrack: Boolean?) {
        pendingShuffle = shuffle
        pendingRepeatContext = repeatContext
        pendingRepeatTrack = repeatTrack
        pendingModeAt = if (shuffle != null || repeatContext != null || repeatTrack != null) {
            System.currentTimeMillis()
        } else {
            0L
        }
    }

    private fun publishModes() {
        val cluster = connect.cluster.value
        val shuffle = shuffleOn(cluster)
        val repeatContext = repeatContextOn(cluster)
        val repeatTrack = repeatTrackOn(cluster)
        val shuffleMode = if (shuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF
        val repeatMode = when {
            repeatTrack -> RepeatMode.REPEAT_ONE
            repeatContext -> RepeatMode.REPEAT_ALL
            else -> RepeatMode.OFF
        }
        if (state.shuffleMode != shuffleMode || state.repeatMode != repeatMode) {
            state = state.copy(shuffleMode = shuffleMode, repeatMode = repeatMode)
        }
    }

    private suspend fun toggleShuffle() {
        val cluster = connect.cluster.value
        val target = cluster?.activeDevice?.id?.takeIf { it.isNotBlank() } ?: deviceId
        val next = !shuffleOn(cluster)
        log.i("shuffle toggle on '${target.take(8)}' -> $next")
        if (!connect.setShuffleOn(target, next)) {
            log.w("shuffle: '${target.take(8)}' refused the option, applying it locally anyway")
        }
        rememberModes(shuffle = next, repeatContext = null, repeatTrack = null)
        publishModes()
        // Give the dealer push a moment, then fall back to the cluster truth.
        delay(MODE_SETTLE_MS)
        runCatching { connect.refreshCluster() }
        publishModes()
    }

    private suspend fun toggleRepeat() {
        val cluster = connect.cluster.value
        val target = cluster?.activeDevice?.id?.takeIf { it.isNotBlank() } ?: deviceId
        val nowContext = repeatContextOn(cluster)
        val nowTrack = repeatTrackOn(cluster)
        val nextContext: Boolean
        val nextTrack: Boolean
        when {
            !nowContext && !nowTrack -> {
                nextContext = true
                nextTrack = false
            }
            nowContext -> {
                nextContext = false
                nextTrack = true
            }
            else -> {
                nextContext = false
                nextTrack = false
            }
        }
        log.i("repeat toggle on '${target.take(8)}' -> context=$nextContext track=$nextTrack")
        if (!connect.setRepeatOn(target, nextContext, nextTrack)) {
            log.w("repeat: '${target.take(8)}' refused the option, applying it locally anyway")
        }
        rememberModes(shuffle = null, repeatContext = nextContext, repeatTrack = nextTrack)
        publishModes()
        delay(MODE_SETTLE_MS)
        runCatching { connect.refreshCluster() }
        publishModes()
    }

    private suspend fun toggle() {
        val cluster = connect.cluster.value
        val active = cluster?.activeDevice
        if (active != null && active.id != deviceId) {
            toggleRemote(cluster, active)
            return
        }
        val command = connect.playback
        if (command == null) {
            toggleLocal()
            return
        }
        if (!player.hasSource) {
            val track = command.currentTrack
            if (track == null) {
                log.w("toggle: no track to start")
                return
            }
            if (remoteTarget().isNotBlank()) {
                log.i("toggle: another device is playing, this one stays quiet")
                return
            }
            val from = command.currentState?.initialPositionMs ?: 0L
            log.i("toggle: nothing loaded, starting '${track.name}' from=${from}ms")
            player.play(track, connect.spclientHost, from)
            return
        }
        val wasPlaying = player.isPlaying
        if (wasPlaying) player.pause() else player.resume()
        val source = if (wasPlaying) DebugSource.PAUSE else DebugSource.RESUME
        val snapshot = Snapshot.of(
            command,
            source,
            positionMs = player.positionMs,
            speed = if (wasPlaying) 0.0 else 1.0,
            paused = wasPlaying,
            videoOn = canvasOn,
        )
        if (snapshot == null) {
            log.w("toggle: no snapshot")
            return
        }
        log.i("toggle -> paused=$wasPlaying source=$source pos=${player.positionMs}")
        connect.report(snapshot)
    }

    private suspend fun toggleRemote(cluster: ClusterState, active: com.quackify.backend.model.ConnectDevice) {
        connect.refreshCluster()
        val fresh = connect.cluster.value ?: cluster
        val playing = fresh.isPlaying
        log.i("toggle on the remote device '" + active.name + "', the cluster says it is " +
                (if (playing) "playing" else "paused")
        )
        val ok = connect.setPausedOn(active.id, playing)
        if (!ok) {
            log.w("toggle: '" + active.name + "' refused the command")
            return
        }
        // Remember what we just asked for: some clients are slow to publish
        // their paused state (or never do), and the next cluster read would
        // flip the play button straight back to playing.
        rememberRemoteIntent(!playing)
        state = state.copy(isPlaying = !playing)
        delay(SKIP_SETTLE_MS)
        connect.refreshCluster()
    }

    /** Toggle the canvas loop and push it into the next playback state report. */
    fun setCanvas(on: Boolean) {
        canvasOn = on
        log.i("canvas $on")
        scope.launch {
            val command = connect.playback ?: return@launch
            if (!player.hasSource) {
                log.i("canvas $on noted, this device is not the one playing")
                return@launch
            }
            val at = player.positionMs
            val snapshot = Snapshot.of(
                command,
                DebugSource.SEEK,
                positionMs = at,
                previousPositionMs = at,
                speed = if (player.isPlaying) 1.0 else 0.0,
                paused = !player.isPlaying,
                videoOn = on,
            ) ?: return@launch
            val accepted = connect.report(snapshot)
            log.i("canvas $on reported accepted=$accepted")
        }
    }

    private suspend fun reportPaused() {
        val command = connect.playback
        if (command == null) {
            if (player.hasSource && remoteTarget().isBlank()) {
                log.i("focus pause: no command, publishing the local state")
                publishLocalFromEngine(playing = false)
            } else {
                log.i("focus pause: no command, nothing to report")
            }
            return
        }
        val snapshot = Snapshot.of(
            command,
            DebugSource.PAUSE,
            positionMs = player.positionMs,
            speed = 0.0,
            paused = true,
            videoOn = canvasOn,
        )
        if (snapshot == null) {
            log.w("focus pause: no snapshot")
            return
        }
        val accepted = connect.report(snapshot)
        log.i("focus pause reported accepted=$accepted pos=${player.positionMs}")
    }

    private suspend fun reportResumed() {
        val command = connect.playback
        if (command == null) {
            if (player.hasSource && remoteTarget().isBlank()) {
                log.i("focus resume: no command, publishing the local state")
                publishLocalFromEngine(playing = true)
            } else {
                log.i("focus resume: no command, nothing to report")
            }
            return
        }
        val snapshot = Snapshot.of(
            command,
            DebugSource.RESUME,
            positionMs = player.positionMs,
            speed = 1.0,
            paused = false,
            videoOn = canvasOn,
        )
        if (snapshot == null) {
            log.w("focus resume: no snapshot")
            return
        }
        val accepted = connect.report(snapshot)
        log.i("focus resume reported accepted=$accepted pos=${player.positionMs}")
    }

    /** What we last told a remote device to do, so a stale cluster read cannot undo it. */
    @Volatile
    private var remoteIntentPlaying: Boolean? = null

    @Volatile
    private var remoteIntentAt = 0L

    private fun rememberRemoteIntent(playing: Boolean) {
        remoteIntentPlaying = playing
        remoteIntentAt = System.currentTimeMillis()
    }

    private fun remoteIntent(): Boolean? =
        if (System.currentTimeMillis() - remoteIntentAt < REMOTE_INTENT_MS) remoteIntentPlaying else null

    /**
     * No server state machine, no remote target: this phone just plays. The
     * cluster and the engine between them carry everything needed - the
     * track, the queue around it and where to come in - and publishing the
     * member state makes the server side follow instead of the other way
     * around.
     */
    private suspend fun toggleLocal() {
        if (player.hasSource) {
            if (player.isPlaying) {
                log.i("toggle: pausing the engine here")
                player.pause()
                publishLocalFromEngine(playing = false)
            } else {
                log.i("toggle: resuming the engine here")
                player.resume()
                publishLocalFromEngine(playing = true)
            }
            return
        }
        val cluster = connect.cluster.value
        if (cluster == null) {
            log.w("toggle: no command and no cluster, nothing to play")
            return
        }
        val track = cluster.track
        if (track.uri.isBlank()) {
            log.w("toggle: the cluster has no track, nothing to play")
            return
        }
        val at = cluster.positionNow()
        log.i("toggle: starting '${track.title}' here at ${at}ms, this phone takes the stream")
        startLocal(track, cluster.contextUri, cluster.contextIndex, at, playing = true)
    }

    /**
     * Starts the engine on a track and claims the account's stream: the UI
     * moves first so the screen answers instantly, the engine follows and
     * the published member state makes every other client follow too.
     */
    private suspend fun startLocal(
        track: ClusterTrack,
        contextUri: String,
        contextIndex: Int,
        positionMs: Long,
        playing: Boolean,
    ) {
        var row = track
        if (row.title.isBlank() && row.uri.startsWith("spotify:track:")) {
            runCatching { TrackSource.decorate(context, listOf(row.uri))[row.uri] }.getOrNull()?.let { found ->
                row = row.copy(
                    title = found.title,
                    artistName = found.artists,
                    artistUri = found.artistUri,
                    imageUrl = found.imageUrl.orEmpty(),
                    albumUri = found.albumUri,
                    durationMs = if (found.durationMs > 0L) found.durationMs else row.durationMs,
                )
                decorated[row.uri] = TrackItem(
                    title = found.title,
                    artist = found.artists,
                    uri = row.uri,
                    imageUrl = found.imageUrl.takeIf { it.isNotBlank() },
                    durationMs = found.durationMs,
                    isExplicit = found.explicit,
                    albumUri = found.albumUri,
                    artistUri = found.artistUri,
                )
            }
        }
        val duration = row.durationMs.takeIf { it > 0L } ?: state.durationMs
        state = state.copy(
            title = row.title,
            artist = row.artistName,
            coverUrl = row.imageUrl,
            trackUri = row.uri,
            artistUri = row.artistUri.ifBlank { state.artistUri.takeIf { state.trackUri == row.uri }.orEmpty() },
            albumUri = row.albumUri.ifBlank { state.albumUri.takeIf { state.trackUri == row.uri }.orEmpty() },
            isPlaying = playing,
            positionMs = positionMs,
            durationMs = duration,
            progress = progressOf(positionMs, duration),
            contextUri = contextUri,
            controllingRemote = false,
        )
        player.play(row.asCommandTrack(), connect.spclientHost, positionMs)
        if (!playing) player.pause()
        connect.holdsStream = true
        publishLocalFromEngine(positionMs, playing)
        publishQueue(connect.cluster.value)
    }

    /** The cluster row for what the engine plays, thin when nothing knows it yet. */
    private fun localTrackRow(): ClusterTrack? {
        val uri = player.state.value.trackUri.takeIf { it.isNotBlank() } ?: return null
        connect.cluster.value?.window()?.firstOrNull { it.uri == uri }?.let { return it }
        return clusterTrackFor(uri)
    }

    private fun clusterTrackFor(uri: String): ClusterTrack {
        val shown = decorated[uri]
        val sameShown = state.trackUri == uri
        return ClusterTrack(
            uri = uri,
            uid = TrackUid.get(uri),
            title = shown?.title?.takeIf { it.isNotBlank() }
                ?: state.title.takeIf { it.isNotBlank() && sameShown }.orEmpty(),
            artistName = shown?.artist?.takeIf { it.isNotBlank() }
                ?: state.artist.takeIf { it.isNotBlank() && sameShown }.orEmpty(),
            artistUri = shown?.artistUri?.takeIf { it.isNotBlank() }
                ?: state.artistUri.takeIf { sameShown }.orEmpty(),
            imageUrl = shown?.imageUrl?.takeIf { it.isNotBlank() }
                ?: state.coverUrl.takeIf { sameShown }.orEmpty(),
            albumTitle = "",
            albumUri = shown?.albumUri?.takeIf { it.isNotBlank() }.orEmpty(),
            durationMs = shown?.durationMs?.takeIf { it > 0L }
                ?: state.durationMs.takeIf { it > 0L } ?: 0L,
            explicit = shown?.isExplicit ?: false,
            creativeId = "",
        )
    }

    /** Publishes the engine's current state as this phone's player state. */
    private suspend fun publishLocalFromEngine(positionMs: Long = -1L, playing: Boolean? = null) {
        val track = localTrackRow() ?: return
        val at = connect.cluster.value
        val uri = track.uri
        val index = at?.takeIf { it.track.uri == uri }?.contextIndex
            ?: rowOf(chainFor(), uri).takeIf { it >= 0 }
            ?: at?.window()?.indexOfFirst { it.uri == uri }?.takeIf { it >= 0 }?.let { at.indexOf(it) }
            ?: 0
        connect.publishLocalState(
            track = track,
            contextUri = at?.contextUri?.takeIf { it.isNotBlank() } ?: chainContext,
            contextIndex = index,
            positionMs = if (positionMs >= 0L) positionMs else player.positionMs,
            playing = playing ?: player.isPlaying,
            durationMs = player.durationMs.takeIf { it > 0L } ?: track.durationMs,
            shuffle = shuffleOn(at),
            repeatContext = repeatContextOn(at),
            repeatTrack = repeatTrackOn(at),
            queueRevision = at?.queueRevision.orEmpty(),
        )
    }

    private fun resumePositionFor(uri: String): Long? {
        if (uri.isBlank() || uri != resumeUri) return null
        val at = resumeAt
        resumeUri = ""
        resumeAt = 0L
        return at.takeIf { it > 0L }
    }

    private suspend fun skipTo(next: Boolean) {
        val cluster = connect.cluster.value
        val active = cluster?.activeDevice
        val remote = active != null && active.id != deviceId
        if (remote) {
            val what = if (next) "next" else "previous"
            log.i("skip $what on the remote device '${active!!.name}'")
            if (connect.skipOn(active.id, next)) {
                delay(SKIP_SETTLE_MS)
                settleSkip(active.id)
                return
            }
            log.w("skip: '${active.name}' refused the $what, leaving the stream where it is")
            return
        }
        skipLocal(forward = next)
    }

    private fun applyReplaceState(command: PlaybackCommand) {
        val track = command.currentTrack ?: run {
            if (command.isTransfer) {
                // A transfer with no state machine behind it only says "the
                // stream is yours now": take it over from the cluster the way
                // the device picker would, instead of dropping the move.
                log.i("transfer pushed with no machine, taking the stream from the cluster")
                scope.launch {
                    connect.refreshCluster()
                    takeBackHere()
                }
            } else {
                log.w("replace_state: it names no track")
            }
            return
        }
        log.i("replace_state: '${track.name}' idx=${command.stateIndex} " +
                "paused=${command.paused} sm=${command.stateMachineId.take(10)}"
        )
        connect.learn(command)
        TrackUid.learn(command)
        val loaded = player.state.value.trackUri
        val moved = command.seekToMs >= 0L
        val position = if (moved) command.seekToMs else state.positionMs
        val nextArtistUri = if (track.uri != state.trackUri) artistUriOf(track.uri) else state.artistUri
        val nextAlbumUri = if (track.uri != state.trackUri) albumUriOf(track.uri) else state.albumUri
        state = state.copy(
            title = track.name,
            artist = track.artists,
            coverUrl = track.imageUrl,
            trackUri = track.uri,
            artistUri = nextArtistUri,
            albumUri = nextAlbumUri,
            isPlaying = if (loaded == track.uri && player.hasSource) player.isPlaying
            else !command.paused,
            controllingRemote = false,
            positionMs = position,
            progress = progressOf(position, state.durationMs),
        )
        val from = command.startAtMs
        if (loaded == track.uri) {
            val at = if (moved) from else player.positionMs
            log.i("replace_state: the engine already holds it, following paused=${command.paused} " +
                    "seek_to=${command.seekToMs} at ${at}ms"
            )
            if (moved) player.seekTo(from)
            if (command.paused) {
                player.pause()
            } else {
                player.resume()
                if (moved) player.seekTo(from)
            }
            return
        }
        log.i("replace_state: loading '${track.name}' from ${from}ms paused=${command.paused} " +
                "seek_to=${command.seekToMs}"
        )
        player.play(track, connect.spclientHost, from)
        if (command.paused) player.pause()
    }

    private fun applyControl(command: PlaybackCommand) {
        val what = command.endpoint
        log.i("control '$what' from the server, applying it here")
        when (what) {
            "pause" -> {
                if (player.hasSource && player.isPlaying) player.pause()
                state = state.copy(isPlaying = false)
            }
            "resume" -> {
                if (player.hasSource && !player.isPlaying) player.resume()
                state = state.copy(isPlaying = true)
            }
            "skip_next" -> scope.launch { skipLocal(forward = true) }
            "skip_prev" -> scope.launch { skipLocal(forward = false) }
            "seek_to" -> {
                val at = command.startAtMs
                scope.launch { player.seekTo(at) }
            }
            else -> log.w("control '$what' is not handled here yet")
        }
    }

    private suspend fun skipLocal(forward: Boolean) {
        if (!forward) {
            previous()
            return
        }
        val cluster = connect.cluster.value ?: run {
            log.w("skip: no cluster to move through")
            return
        }
        val window = cluster.window()
        val here = playingUri(cluster)
        val at = window.indexOfFirst { it.uri == here }
        if (at < 0) {
            log.w("skip: '${here.takeLast(12)}' is not in the cluster window")
            return
        }
        val step = if (forward) 1 else -1
        var cursor = at + step
        while (cursor in window.indices && !window[cursor].isVisible) cursor += step
        if (cursor !in window.indices) {
            // Manual skip past the edge: wrap around while the context repeats.
            if (repeatContextOn(cluster)) {
                cursor = if (forward) {
                    window.indexOfFirst { it.isVisible }
                } else {
                    window.indexOfLast { it.isVisible }
                }
            }
            if (cursor !in window.indices) {
                log.w("skip: there is no ${if (forward) "next" else "previous"} row to move to")
                return
            }
        }
        if (forward && shuffleOn(cluster)) {
            val pool = window.indices.filter { i ->
                window[i].isVisible && window[i].uri != here
            }
            if (pool.isNotEmpty()) cursor = pool.random()
        }
        val row = window[cursor]
        val index = if (row.isQueued) -1 else cluster.indexOf(cursor)
        log.i("skip ${if (forward) "next" else "previous"} here: '${row.title}' idx=$index")
        markPending(row.uri, index, cluster.contextUri)
        if (connect.playback != null && connect.play(cluster.contextUri, row.uri, row.uid, index)) {
            state = state.copy(
                title = row.title,
                artist = row.artistName,
                coverUrl = row.imageUrl,
                trackUri = row.uri,
                artistUri = row.artistUri,
                albumUri = row.albumUri,
                positionMs = 0L,
                progress = 0f,
            )
            return
        }
        log.i("skip: the server has no say here, starting '${row.title}' on this phone")
        startLocal(row, cluster.contextUri, index, 0L, playing = true)
        clearPending()
    }

    /**
     * The user picked this phone in "Listening on": take the stream back
     * without asking the server for permission - becoming the active member
     * is what transfer means for the device that ends up with the audio.
     */
    private suspend fun takeBackHere() {
        val at = connect.cluster.value
        val track = at?.track
        if (at == null || track == null || track.uri.isBlank()) {
            if (player.hasSource) {
                log.i("back home: nothing to take over, claiming the engine's stream")
                connect.holdsStream = true
                publishLocalFromEngine()
            } else {
                log.w("back home: nothing to take over yet")
            }
            return
        }
        val old = at.activeDevice?.takeIf { it.id != deviceId }
        if (old != null && at.isPlaying) {
            log.i("back home: pausing '${old.name}' before the move")
            connect.setPausedOn(old.id, playing = true)
            rememberRemoteIntent(false)
        }
        if (player.hasSource && player.state.value.trackUri == track.uri) {
            log.i("back home: the engine already holds '${track.title}', claiming the stream")
            connect.holdsStream = true
            val where = at.positionNow()
            val playing = at.isPlaying
            // Take the session over where the other device left it: come in
            // at its position in its play state, the way a fresh load would.
            // The engine used to keep its own idea of both while the claimed
            // state described the remote one.
            if (kotlin.math.abs(player.positionMs - where) > RESYNC_MS) player.seekTo(where)
            if (playing && !player.isPlaying) player.resume()
            if (!playing && player.isPlaying) player.pause()
            publishLocalFromEngine(where, playing)
            return
        }
        log.i("back home: taking the stream at ${at.positionNow()}ms of '${track.title}' " +
                "paused=${!at.isPlaying}"
        )
        startLocal(track, at.contextUri, at.contextIndex, at.positionNow(), playing = at.isPlaying)
    }

    private suspend fun settleSkip(remoteId: String) {
        val before = state.trackUri
        repeat(SKIP_TRIES) { attempt ->
            connect.refreshCluster()
            val at = connect.cluster.value
            if (at != null && at.track.uri != before && at.activeDevice?.id == remoteId) {
                log.i("skip landed on '${at.track.uri.takeLast(12)}' at attempt ${attempt + 1}")
                hydrateCurrent(at)
                return
            }
            delay(SKIP_RETRY_MS)
        }
        log.w("skip: the cluster still names '${state.trackUri.takeLast(12)}' after ${SKIP_TRIES} reads")
    }

    private suspend fun previous() {
        val loaded = player.positionMs
        if (player.hasSource && loaded > RESTART_MS) {
            log.i("previous: at ${loaded}ms, restarting the track instead")
            player.seekTo(0L)
            return
        }
        val current = state.trackUri
        val rows = chainFor()
        val at = rowOf(rows, current)
        val row = if (at > 0) rows[at - 1] else null
        if (row != null) {
            log.i("previous: row ${at - 1} of ${rows.size} from '${current.takeLast(12)}'")
            resumeRow = at - 1
            playTrack(row, chainContext, at - 1)
            return
        }
        val cluster = connect.cluster.value
        if (cluster == null) {
            log.w("previous: nothing to go back to")
            return
        }
        val window = cluster.window()
        val anchor = cluster.anchorOf(current)
        var back = anchor - 1
        while (back >= 0 && !window[back].isVisible) back--
        val track = window.getOrNull(back)
        if (track == null) {
            log.w("previous: '${current.takeLast(12)}' is at the start of the window")
            return
        }
        log.i("previous: window $back ctxIdx=${cluster.indexOf(back)} '${track.title}'")
        resumeRow = -1
        playTrack(track.uri, cluster.contextUri, cluster.indexOf(back))
    }

    private suspend fun playTrack(uri: String, contextUri: String, hint: Int) {
        val cluster = connect.cluster.value
        if (cluster != null && contextUri.isNotBlank()) {
            val window = cluster.window()
            val at = window.indexOfFirst { it.uri == uri }
            if (at >= 0 && contextUri == cluster.contextUri) {
                val track = window[at]
                val index = cluster.indexOf(at)
                log.i("playTrack '$uri' from window[$at] ctxIdx=$index")
                playConfirmed(cluster.contextUri, uri, track.uid, index)
                return
            }
        }
        val context = contextUri.ifBlank { if (uri.startsWith("spotify:")) uri else "spotify:track:$uri" }
        val single = context.startsWith("spotify:track:") || context.startsWith("spotify:episode:")
        val known = if (chainContext == context) chainRows.indexOfFirst { it == uri } else -1
        val index = when {
            single -> -1
            known >= 0 -> known
            hint >= 0 -> hint
            else -> 0
        }
        val uid = TrackUid.get(uri)
        log.i("playTrack '$uri' outside the cluster window, '$context' ctxIdx=$index " +
                "chain=$known hint=$hint uid=${uid.take(12).ifBlank { "none" }}"
        )
        playConfirmed(context, uri, uid, index)
    }

    private fun remoteTarget(): String {
        val active = connect.cluster.value?.activeDevice ?: return ""
        return if (active.id != deviceId) active.id else ""
    }

    private suspend fun playConfirmed(context: String, uri: String, uid: String, index: Int) {
        if (uri.startsWith("spotify:track:") || uri.startsWith("spotify:episode:")) {
            player.prefetch(uri, connect.spclientHost)
        }
        val remote = remoteTarget()
        if (remote.isNotBlank()) {
            markPending(uri, index, context)
            if (!connect.play(context, uri, uid, index, targetDeviceId = remote)) {
                clearPending()
                log.w("playFromCluster: the server refused '${uri.takeLast(12)}', dropping the wait")
            }
            return
        }
        // This phone is the intended player. The command round trip is only
        // worth it while the server runs a playback session for this
        // device; with no session behind it the command can only come back
        // refused (410 COMMAND_FAILED, then 404 once the member expires),
        // which used to leave the play button dead.
        if (connect.playback == null) {
            startLocalConfirmed(context, uri, index)
            return
        }
        markPending(uri, index, context)
        if (!connect.play(context, uri, uid, index, targetDeviceId = "")) {
            clearPending()
            startLocalConfirmed(context, uri, index)
            return
        }
        val confirmed = awaitTrack(uri, context, CONFIRM_MS)
        if (confirmed && correctRow(context, uri, index)) return
        connect.refreshCluster()
        val live = connect.playback
        if (live != null && live.isAd) {
            log.i("play '$uri': an ad break is in the way, the track follows it")
            return
        }
        val settled = connect.cluster.value ?: run {
            clearPending()
            return
        }
        if (settled.track.uri == uri) {
            log.i("play '$uri': the cluster already names it at ctxIdx=${settled.contextIndex}")
            if (remoteTarget().isNotBlank()) {
                log.i("play '$uri': another device is playing it, this one stays quiet")
                return
            }
            val engine = player.state.value.trackUri
            if (!player.hasSource || engine != uri) {
                log.i("play '$uri': no push came, starting the engine from the cluster")
                player.play(settled.track.asCommandTrack(), connect.spclientHost, 0L)
            }
            return
        }
        val picked = live?.currentTrack?.uri.orEmpty()
        val at = settled.window().indexOfFirst { it.uri == uri }
        if (at < 0) {
            log.w("play '$uri': the cluster still describes '${settled.contextUri.take(32)}', " +
                    "not the context we asked for; retrying in '${context.take(32)}' at idx=$index"
            )
            if (connect.play(context, uri, uid, index, targetDeviceId = remoteTarget())) {
                awaitTrack(uri, context, CONFIRM_MS)
            } else {
                clearPending()
                startLocalConfirmed(context, uri, index)
            }
            return
        }
        log.w("play '$uri': the server picked '$picked' instead, retrying at ctxIdx=${settled.indexOf(at)}")
        markPending(uri, settled.indexOf(at), settled.contextUri)
        connect.play(
            settled.contextUri,
            uri,
            settled.window()[at].uid,
            settled.indexOf(at),
            targetDeviceId = remoteTarget(),
        )
    }

    /** Starts a row on this phone when the server cannot be asked to do it. */
    private suspend fun startLocalConfirmed(context: String, uri: String, index: Int) {
        val cluster = connect.cluster.value
        val row = cluster?.window()?.firstOrNull { it.uri == uri && it.isVisible }
            ?: cluster?.takeIf { it.track.uri == uri }?.track
            ?: clusterTrackFor(uri)
        val resume = resumePositionFor(uri) ?: 0L
        log.i("play '$uri': starting it here at ${resume}ms ctxIdx=$index ctx='${context.takeLast(24)}'")
        startLocal(row, context, index, resume, playing = true)
        clearPending()
    }

    private suspend fun correctRow(context: String, uri: String, index: Int): Boolean {
        val started = connect.playback?.currentTrack ?: return true
        if (started.uri == uri) return true
        if (started.contextUri != context) return true
        if (uri == context) return true
        var learned = TrackUid.get(uri)
        if (learned.isBlank()) {
            connect.refreshCluster()
            learned = TrackUid.get(uri)
        }
        if (learned.isBlank()) {
            log.w("play '$uri': the server started '${started.name}' and the uid of the row we wanted " +
                    "is still unknown, leaving it"
            )
            return true
        }
        log.i("play '$uri': the server started '${started.name}', asking again with the uid it just " +
                "taught us (${learned.take(12)}) at idx=$index"
        )
        markPending(uri, index, context)
        if (!connect.play(context, uri, learned, index, targetDeviceId = remoteTarget())) {
            clearPending()
            return true
        }
        return awaitTrack(uri, context, CONFIRM_MS)
    }

    private suspend fun awaitTrack(uri: String, context: String, millis: Long): Boolean {
        fun settled(): Boolean {
            val track = connect.playback?.currentTrack ?: return false
            if (track.uri == uri) return true
            return context.isNotBlank() && track.contextUri == context
        }
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            if (settled()) return true
            delay(120L)
        }
        return settled()
    }

    private fun flushHeld() {
        val track = held ?: return
        held = null
        log.w("held '${track.name}' never reached the engine, showing it anyway")
        val duration = track.durationMs.takeIf { it > 0L } ?: state.durationMs
        state = state.copy(
            title = track.name,
            artist = track.artists,
            coverUrl = track.imageUrl,
            trackUri = track.uri,
            isPlaying = false,
            positionMs = 0L,
            durationMs = duration,
            progress = 0f,
        )
    }

    private suspend fun settleCluster(uri: String, index: Int) {
        repeat(CLUSTER_TRIES) { attempt ->
            if (attempt > 0) delay(CLUSTER_RETRY_MS)
            if (!connect.refreshCluster()) return
            val at = connect.cluster.value ?: return
            if (at.track.uri != uri) {
                log.w("cluster: the server names '${at.track.title}' instead of '$uri'")
                return
            }
            if (index <= 0 || at.contextIndex != 0) return
            log.w("cluster: idx=${at.contextIndex} but the row asked for $index, trying again")
        }
    }

    private suspend fun seekRemote(positionMs: Long) {
        val cluster = connect.cluster.value ?: return
        val active = cluster.activeDevice ?: return
        if (active.id == deviceId) return
        if (cluster.disallowSeeking) {
            log.w("seek $positionMs ignored, the cluster disallows seeking")
            return
        }
        val duration = cluster.durationMs.takeIf { it > 0L } ?: state.durationMs
        val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs
        log.i("seek $positionMs -> ${target}ms on '${active.name}'")
        state = state.copy(
            positionMs = target,
            durationMs = duration,
            progress = progressOf(target, duration),
        )
        if (!connect.seekOn(active.id, target)) {
            log.w("seek: '${active.name}' refused it")
            return
        }
        delay(SKIP_SETTLE_MS)
        connect.refreshCluster()
    }

    private fun pauseRemoteOnExit() {
        val active = connect.cluster.value?.activeDevice ?: return
        if (active.id == deviceId) {
            log.i("leaving, this device holds the stream, nothing to hand over")
            return
        }
        if (!state.isPlaying) {
            log.i("leaving, '${active.name}' is already paused")
            return
        }
        log.i("leaving, pausing '${active.name}'")
        runCatching {
            runBlocking { withTimeoutOrNull(2_000L) { connect.setPausedOn(active.id, playing = true) } }
        }.onFailure { log.w("pause on exit failed ${it.javaClass.simpleName}") }
        // teardown runs handOff() and then stop(); without this flip the
        // second pauseRemoteOnExit() sends the very same pause again and
        // blocks the main thread for another round trip.
        state = state.copy(isPlaying = false)
    }

    private fun advanceRemote() {
        if (!state.controllingRemote || !state.isPlaying) return
        if (player.hasSource) return
        val duration = state.durationMs
        if (duration <= 0L) return
        val next = (state.positionMs + REMOTE_TICK_MS).coerceAtMost(duration)
        if (next == state.positionMs) return
        state = state.copy(
            positionMs = next,
            progress = progressOf(next, duration),
        )
    }

    private fun progressOf(position: Long, duration: Long): Float =
        if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    /**
     * Settings "Autoplay on startup": the first track the cluster names after
     * the app opens starts playing here right away — exactly what pressing
     * play on the loaded track does. It never steals the stream another
     * device is holding, skips ads, and only waits a short window before
     * giving up so a late cluster never surprises anyone.
     */
    private fun maybeAutoplayStartup(cluster: ClusterState) {
        if (!autoplayPending) return
        autoplayPending = false
        if (!autoplayOnStartup) return
        if (SystemClock.elapsedRealtime() - autoplayArmedAt > AUTOPLAY_WINDOW_MS) {
            log.i("autoplay: the session track came too late, staying quiet")
            return
        }
        if (cluster.track.uri.startsWith(AD_PREFIX)) return
        if (player.hasSource) return
        val remote = remoteTarget()
        if (remote.isNotBlank()) {
            log.i("autoplay: '${remote.take(12)}' owns the stream, not taking it")
            return
        }
        log.i("autoplay: the loaded track is here, starting playback")
        scope.launch { toggleLocal() }
    }

    private fun hydrateCurrent(cluster: ClusterState) {
        val uri = cluster.track.uri
        if (uri.isBlank() || uri in hydrateTried) return
        if (cluster.track.title.isNotBlank() && cluster.track.artistName.isNotBlank()) return
        if (uri.startsWith(AD_PREFIX)) {
            hydrateTried += uri
            log.i("hydrate: an ad is playing, it carries no title")
            state = state.copy(
                title = "Advertisement",
                artist = "",
                trackUri = uri,
                isPlaying = if (player.hasSource) player.isPlaying else cluster.isPlaying,
                positionMs = cluster.positionMs,
                durationMs = cluster.durationMs.takeIf { it > 0L } ?: state.durationMs,
            )
            return
        }
        hydrateTried += uri
        scope.launch {
            val row = runCatching { TrackSource.decorate(context, listOf(uri))[uri] }.getOrNull()
            if (row == null) {
                hydrateTried -= uri
                log.w("hydrate: nothing came back for '${uri.takeLast(12)}', will retry")
                return@launch
            }
            val at = connect.cluster.value ?: return@launch
            if (at.track.uri != uri) return@launch
            log.i("hydrate: '${uri.takeLast(12)}' is '${row.title}' by '${row.artists}'")
            decorated[uri] = TrackItem(
                title = row.title,
                artist = row.artists,
                uri = uri,
                imageUrl = row.imageUrl.takeIf { it.isNotBlank() },
                durationMs = row.durationMs,
                isExplicit = row.explicit,
                albumUri = row.albumUri,
                artistUri = row.artistUri,
            )
            state = state.copy(
                title = row.title,
                artist = row.artists,
                coverUrl = row.imageUrl.orEmpty(),
                trackUri = uri,
                artistUri = row.artistUri.ifBlank { state.artistUri.takeIf { state.trackUri == uri }.orEmpty() },
            )
        }
    }

    private fun artistUriOf(uri: String): String {
        if (uri.isBlank()) return ""
        val at = cluster
        if (at != null) {
            if (at.track.uri == uri && at.track.artistUri.isNotBlank()) return at.track.artistUri
            at.window().firstOrNull { it.uri == uri && it.artistUri.isNotBlank() }?.let { return it.artistUri }
        }
        decorated[uri]?.let { if (it.artistUri.isNotBlank()) return it.artistUri }
        (state.queue + state.queueFromContext).firstOrNull { it.uri == uri && it.artistUri.isNotBlank() }
            ?.let { return it.artistUri }
        return ""
    }

    private fun albumUriOf(uri: String): String {
        if (uri.isBlank()) return ""
        val at = cluster
        if (at != null) {
            if (at.track.uri == uri && at.track.albumUri.isNotBlank()) return at.track.albumUri
            at.window().firstOrNull { it.uri == uri && it.albumUri.isNotBlank() }?.let { return it.albumUri }
        }
        decorated[uri]?.let { if (it.albumUri.isNotBlank()) return it.albumUri }
        (state.queue + state.queueFromContext).firstOrNull { it.uri == uri && it.albumUri.isNotBlank() }
            ?.let { return it.albumUri }
        return ""
    }

    private fun mirror(cluster: ClusterState?) {
        if (cluster == null) return
        watchForIncomingTransfer(cluster)
        if (cluster.track.uri.isBlank()) {
            blankSeen = ""
            this.cluster = cluster
            publishDevices(cluster)
            publishQueue(cluster)
            return
        }
        maybeAutoplayStartup(cluster)
        if (cluster.track.title.isBlank() && cluster.track.artistName.isBlank()) {
            if (blankSeen != BLANK) {
                blankSeen = BLANK
                log.i("the cluster names '${cluster.track.uri.takeLast(12)}' with no metadata, " +
                        "keeping the track block as it is")
            }
            hydrateCurrent(cluster)
            this.cluster = cluster
            publishDevices(cluster)
            publishQueue(cluster)
            val remoteNow = cluster.activeDevice?.let { it.id != deviceId } == true
            if (!remoteNow && player.hasSource) {
                state = state.copy(isPlaying = player.isPlaying)
            }
            if (remoteNow) {
                val duration = cluster.durationMs.takeIf { it > 0L } ?: state.durationMs
                val changed = cluster.track.uri != state.trackUri
                val intent = remoteIntent()
                val effPlaying = intent ?: cluster.isPlaying
                val live = if (intent == false) cluster.positionMs else cluster.positionNow()
                val resync = changed || state.isPlaying != effPlaying ||
                        kotlin.math.abs(live - state.positionMs) > RESYNC_MS
                val position = if (resync) live else state.positionMs
                state = state.copy(
                    title = if (changed) "" else state.title,
                    artist = if (changed) "" else state.artist,
                    coverUrl = if (changed) "" else state.coverUrl,
                    trackUri = cluster.track.uri,
                    isPlaying = effPlaying,
                    positionMs = position,
                    durationMs = duration,
                    progress = progressOf(position, duration),
                )
            }
            return
        }
        blankSeen = ""
        this.cluster = cluster
        publishDevices(cluster)
        hydrateCurrent(cluster)
        TrackUid.learn(cluster)
        if (cluster.activeDevice?.id != deviceId) {
            log.v("prefetch skipped, another device owns the stream")
        } else {
            player.prefetchAhead(cluster.nextTracks.take(KEY_LOOKAHEAD), connect.spclientHost)
        }
        if (cluster.activeDeviceId.isNotBlank() && cluster.activeDeviceId != activeSeen) {
            activeSeen = cluster.activeDeviceId
            log.i("the account's active device is " +
                    if (cluster.activeDeviceId == deviceId) "this one" else cluster.activeDeviceId.take(12)
            )
        }
        if (chainRows.isEmpty() && chainContext.isBlank() &&
            cluster.contextUri.isNotBlank() && cluster.nextTracks.isNotEmpty()
        ) {
            chainRows = cluster.window().filter { it.isVisible }.map { it.uri }
            chainContext = cluster.contextUri
            log.i("chain from the cluster: ${chainRows.size} rows in " +
                    "'${chainContext.take(32)}' at '${cluster.track.uri.takeLast(12)}' " +
                    "queued=${cluster.queued.size}"
            )
        }
        val loaded = player.state.value.trackUri
        val stale = player.hasSource && loaded.isNotBlank() && cluster.track.uri != loaded
        val signature = "${cluster.track.uri}|${cluster.isPlaying}|${cluster.contextIndex}|" +
                "${cluster.nextTracks.size}|${cluster.prevTracks.size}|${cluster.contextUri}|" +
                "${cluster.track.title}|$stale"
        val moved = signature != clusterSeen
        if (moved) {
            clusterSeen = signature
            if (stale) {
                log.w("queue kept: the cluster names '${cluster.track.uri.takeLast(12)}' at " +
                        "idx=${cluster.contextIndex}, the engine has '${loaded.takeLast(12)}'"
                )
            }
            log.i("cluster ${cluster.updateReason} '${cluster.track.title}' by " +
                    "'${cluster.track.artistName}' playing=${cluster.isPlaying} " +
                    "pos=${cluster.positionMs}/${cluster.durationMs} " +
                    "ctxIdx=${cluster.contextIndex} next=${cluster.nextTracks.size} " +
                    "prev=${cluster.prevTracks.size} uid=${cluster.track.uid.take(12)}"
            )
        }
        publishQueue(cluster)
        publishModes()
        val clusterArtist = cluster.track.artistUri
        if (cluster.track.uri == state.trackUri && clusterArtist.isNotBlank() && state.artistUri != clusterArtist) {
            state = state.copy(artistUri = clusterArtist)
        }
        val remoteActive = cluster.activeDevice?.let { it.id != deviceId } == true
        // A cluster with no active device at all still describes the last
        // session; while nothing is loaded here this screen keeps following
        // it instead of freezing on a stale play state. Once the engine owns
        // the stream the phone IS the player, so a stale read must not drag
        // the UI back to the dead session.
        val nothingActive = cluster.activeDeviceId.isBlank() && cluster.activeDevice == null &&
                !player.hasSource
        val following = remoteActive || nothingActive
        if (connect.playback != null && !following) return
        if (remoteActive) hydrateCurrent(cluster)
        if (!following && player.hasSource) return
        val local = player.hasSource && !following
        val intent = remoteIntent()
        // A followed cluster that says it is playing is only worth trusting
        // while the session behind it is alive. The frame a fresh launch reads
        // is the previous session's last snapshot, and once this phone
        // registers the same stale state comes back with us named active, so
        // the clock would be extrapolated from a twelve minute old timestamp
        // and the bar would crawl with no audio behind it. The engine is the
        // only thing that can say "playing" for this device, so a cluster
        // without a live snapshot never does.
        val fresh = cluster.stateAtMs > 0L &&
                System.currentTimeMillis() - cluster.stateAtMs <= LIVE_STATE_MS
        val followPlaying = if (nothingActive || !player.hasSource) {
            cluster.isPlaying && fresh
        } else {
            cluster.isPlaying
        }
        val position = when {
            local -> state.positionMs
            followPlaying -> cluster.positionNow()
            else -> cluster.positionMs
        }
        val duration = cluster.durationMs.takeIf { it > 0L } ?: cluster.track.durationMs
        // A remote device often echoes the playing track with a thinner
        // metadata block (artist_name is the first field to disappear), which
        // used to blank the artist line and the artist card in the NPV. Keep
        // what this screen already resolved for the same track instead.
        val sameTrack = cluster.track.uri == state.trackUri
        state = state.copy(
            title = cluster.track.title.ifBlank { if (sameTrack) state.title else "" },
            artist = cluster.track.artistName.ifBlank { if (sameTrack) state.artist else "" },
            coverUrl = cluster.track.imageUrl.ifBlank { if (sameTrack) state.coverUrl else "" },
            trackUri = cluster.track.uri,
            artistUri = cluster.track.artistUri
                .ifBlank { if (sameTrack) state.artistUri else "" }
                .ifBlank { artistUriOf(cluster.track.uri) },
            isPlaying = if (local) player.isPlaying else (intent ?: followPlaying),
            positionMs = position,
            durationMs = duration,
            progress = progressOf(position, duration),
            explicit = if (sameTrack) state.explicit else cluster.track.explicit,
            contextUri = cluster.contextUri,
            contextLabel = contextLabelOf(cluster),
        )
        watchForVanishedActive(cluster)
    }

    private fun contextLabelOf(cluster: ClusterState): String {
        if (cluster.fromContext.isNotEmpty() && cluster.fromContext.all { it.isAutoplay }) return ""
        return cluster.contextDescription.trim()
    }

    /** Consecutive cluster reads that no longer name the device that held the stream. */
    @Volatile
    private var goneReads = 0

    /** The context+track the last takeover was fired for, to avoid repeats. */
    @Volatile
    private var takeOverTried = ""

    /**
     * Watches for the device that held the stream dropping out of the
     * cluster: once it is gone from the device list and nothing became
     * active in its place, this phone brings the session home instead of
     * leaving "Listening on" without a current device. A single blank read
     * is not enough - the server briefly unsets the active device while a
     * remote re-publishes its state - so it takes two in a row, and the
     * device has to really be missing, not just idle.
     */
    private fun watchForVanishedActive(cluster: ClusterState) {
        val holder = activeSeen
        val gone = holder.isNotBlank() &&
                cluster.activeDeviceId.isBlank() &&
                cluster.devices.isNotEmpty() &&
                cluster.devices.none { it.id == holder }
        if (!gone || player.hasSource || pendingActive()) {
            goneReads = 0
            return
        }
        val track = cluster.track
        if (track.uri.isBlank() || track.isAd) {
            goneReads = 0
            return
        }
        goneReads++
        if (goneReads < GONE_READS) return
        goneReads = 0
        val stamp = "${cluster.contextUri}|${track.uid.ifBlank { track.uri }}"
        if (stamp == takeOverTried) return
        takeOverTried = stamp
        scope.launch { takeOverFromGoneDevice(cluster) }
    }

    private suspend fun takeOverFromGoneDevice(cluster: ClusterState) {
        val track = cluster.track
        val at = cluster.positionNow()
        log.i("the device that held the stream left the cluster, " +
                "taking it back here at ${at}ms of '${track.title}' " +
                "paused=${!cluster.isPlaying}"
        )
        resumeUri = ""
        resumeAt = 0L
        startLocal(track, cluster.contextUri, cluster.contextIndex, at, playing = cluster.isPlaying)
    }

    /**
     * Another client moved the account's stream onto this phone from its own
     * device picker: the cluster flips the active device to this one long
     * before any command lands here. If nothing picks the stream up the phone
     * sits silent while the server waits for it, and once it gives up the
     * phone vanishes from every "Listening on" list. Watch for that flip
     * while nothing is loaded here and take the stream over.
     */
    private fun watchForIncomingTransfer(cluster: ClusterState) {
        if (cluster.activeDeviceId != deviceId) return
        // Only a handover we saw happen: a cluster that still names this phone
        // from a previous life (a cold start after the app died while it
        // played) must not come back to life on its own.
        if (activeSeen.isBlank() || activeSeen == deviceId) return
        if (player.hasSource || pendingActive()) return
        val track = cluster.track
        if (track.uri.isBlank() || track.isAd) return
        val age = System.currentTimeMillis() - cluster.stateAtMs
        if (cluster.stateAtMs <= 0L || age > TRANSFER_FRESH_MS) return
        val stamp = "${cluster.contextUri}|${track.uid.ifBlank { track.uri }}"
        if (stamp == takeOverTried) return
        takeOverTried = stamp
        log.i("the stream was moved here, taking it at ${cluster.positionNow()}ms " +
                "of '${track.title}' paused=${!cluster.isPlaying}"
        )
        scope.launch { takeBackHere() }
    }

    private fun publishDevices(cluster: ClusterState) {
        val active = cluster.activeDevice
        val mine = active != null && active.id == deviceId
        val label = active?.name.orEmpty()
        val remote = cluster.remote.map { it.name }
        val devices = withLocalDevice(cluster)
        if (state.devices != devices || state.activeDeviceName != label ||
            state.controllingRemote != !mine
        ) {
            log.i("devices=${devices.size} active='$label' ours=$mine " +
                    "injected=${cluster.devices.none { it.id == deviceId }} " +
                    "inferred=${cluster.activeInferred} remote=${remote.joinToString("|")}"
            )
        }
        // With no active device left in the cluster this screen keeps
        // following the session instead of pretending the phone took over;
        // the takeover path makes it ours for real.
        val noneActive = active == null && !player.hasSource
        val remoteActive = (!mine || noneActive) && !player.hasSource
        state = state.copy(
            devices = devices,
            activeDeviceName = label,
            activeDeviceId = active?.id.orEmpty(),
            controllingRemote = remoteActive,
            remoteCanSeek = !remoteActive || !cluster.disallowSeeking,
            remoteCanSkipPrev = !remoteActive || !cluster.licenseRestricted,
            remoteCanShuffle = !remoteActive || !cluster.licenseRestricted,
            remoteCanRepeat = !remoteActive || !cluster.licenseRestricted,
            connectedDevice = label.ifBlank {
                if (mine || player.hasSource || noneActive) "Quackify" else ""
            },
        )
        if (active != null && !mine && player.hasSource && player.isPlaying) {
            log.i("the stream moved to '$label', stopping this device")
            player.pause()
        }
    }

    /**
     * The account's device list only names devices the server knows about, so
     * while the player registration has not landed (or failed) this phone is
     * invisible in "Listening on". Synthesize the local row so it can always
     * be picked; the server row replaces it once the registration answers.
     */
    private fun withLocalDevice(cluster: ClusterState): List<ConnectDevice> {
        val fromServer = cluster.devices
        if (fromServer.any { it.id == deviceId }) return fromServer
        return fromServer + localDeviceRow(active = cluster.activeDeviceId == deviceId)
    }

    private fun localDeviceRow(active: Boolean): ConnectDevice = ConnectDevice(
        id = deviceId,
        name = "Quackify",
        kind = DeviceKind.COMPUTER,
        canPlay = true,
        volume = 0,
        volumeSteps = 64,
        isControllable = true,
        disableVolume = false,
        supportsTransfer = true,
        supportsLogout = false,
        supportsCommandRequest = false,
        supportsSleepTimer = false,
        isObservable = true,
        audioQuality = "",
        brand = "spotify",
        model = "web_player",
        softwareVersion = "",
        license = "",
        active = active,
        local = true,
    )

    private fun publishQueue(cluster: ClusterState?) {
        val loaded = player.state.value.trackUri
        val anchor = (if (pendingActive()) pendingTrack else "")
            .ifBlank { loaded }
            .ifBlank { cluster?.track?.uri.orEmpty() }
        if (anchor.isNotBlank() && queuedTail.any { it.uri == anchor }) {
            queuedTail = queuedTail.filterNot { it.uri == anchor }
        }
        val server = cluster != null
        val live = connect.playback?.currentTrack?.contextUri.orEmpty()
        val serverRows = if (server) cluster!!.fromContext.map(::row) else emptyList()
        val context = live.ifBlank {
            if (server) cluster!!.contextUri else ""
        }.takeIf { it.isNotBlank() && !it.startsWith("spotify:track:") }
            ?: chainContext.ifBlank { if (server) cluster!!.contextUri else "" }
        val radio = server && cluster!!.fromContext.let { it.isNotEmpty() && it.all { row -> row.isAutoplay } }
        val label = if (server && !radio) cluster!!.contextDescription else ""
        val queued = if (server) {
            val onServer = cluster!!.queued.map(::row)
            val known = onServer.mapTo(HashSet()) { it.uri }
            onServer + queuedTail.filterNot { it.uri in known }
        } else {
            queuedTail
        }
        val queuedUris = buildSet {
            if (server) cluster!!.queued.forEach { add(it.uri) }
            queued.forEach { add(it.uri) }
        }
        val chain = chainFor()
        val chainTail = if (anchor.isNotBlank() && chain.isNotEmpty()) {
            val at = chain.indexOf(anchor)
            if (at >= 0) {
                chain.drop(at + 1).filterNot { it in queuedUris }.take(20)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
        val knownUpcoming = serverRows.mapTo(HashSet()) { it.uri }
        val sparse = serverRows.filter { it.title.isBlank() }.map { it.uri }
        val missing = (sparse + chainTail.filterNot { it in knownUpcoming })
            .filterNot { decorated.containsKey(it) || it in undecoratable }
            .distinct()
        if (missing.isNotEmpty()) scheduleDecorate(missing)
        val chainRowsFilled = chainTail.map { uri ->
            decorated[uri] ?: TrackItem(title = "", artist = "", uri = uri)
        }
        val upcoming = (serverRows.map { if (it.title.isBlank()) decorated[it.uri] ?: it else it } +
                chainRowsFilled)
            .filterNot { it.uri == anchor }
        if (queued == state.queue && upcoming == state.queueFromContext &&
            context == state.queueContext && label == state.queueContextLabel
        ) {
            return
        }
        state = state.copy(
            queue = queued,
            queueFromContext = upcoming,
            queueContext = context,
            queueContextLabel = label,
        )
        log.i("queue ${queued.size} queued + ${upcoming.size} next from " +
                "'${label.ifBlank { context.ifBlank { "?" } }}' " +
                "${if (server) "the server" else "the list on screen"}"
        )
    }

    private fun scheduleDecorate(uris: List<String>) {
        if (uris.isEmpty()) return
        if (uris == decoratePending && decorateJob?.isActive == true) return
        decoratePending = uris
        decorateJob?.cancel()
        decorateJob = scope.launch {
            val rows = runCatching { TrackSource.decorate(context, uris) }.getOrDefault(emptyMap())
            decoratePending = emptyList()
            if (!isActive) return@launch
            uris.forEach { uri -> if (rows[uri] == null) undecoratable += uri }
            rows.forEach { (uri, track) ->
                decorated[uri] = TrackItem(
                    title = track.title,
                    artist = track.artists,
                    uri = track.uri,
                    imageUrl = track.imageUrl.takeIf { it.isNotBlank() },
                    durationMs = track.durationMs,
                    isExplicit = track.explicit,
                    albumUri = track.albumUri,
                    artistUri = track.artistUri,
                )
            }
            log.i("decorated ${rows.size} of ${uris.size} upcoming row(s)")
            publishQueue(connect.cluster.value)
        }
    }

    private fun screenContext(uri: String): String {
        val known = connect.playback?.currentTrack?.contextUri.orEmpty()
        if (known.isNotBlank() && !known.startsWith("spotify:track:")) return known
        if (screen.size >= 2 && screen.any { it == uri }) {
            return chainContext.takeIf { it.isNotBlank() && it != uri }.orEmpty()
        }
        return ""
    }

    private fun captureChain(contextUri: String) {
        if (contextUri.isBlank()) {
            if (chainRows.isNotEmpty() || chainContext.isNotBlank()) {
                chainRows = emptyList()
                chainContext = ""
                resumeRow = -1
                log.i("chain: reset, this play carries no context")
            }
            return
        }
        val rows = screen
        if (rows.size < 2) {
            log.w("chain: the list on screen has ${rows.size} rows, nothing to follow")
            return
        }
        chainRows = rows
        chainContext = contextUri
        resumeRow = -1
        log.i("chain: ${rows.size} rows in '${chainContext.ifBlank { "one track per row" }}' " +
                "from '${rows.first().takeLast(12)}'")
    }

    private fun ClusterTrack.asCommandTrack(): CommandTrack = CommandTrack(
        uri = uri,
        name = title,
        artists = artistName,
        album = "",
        albumUri = albumUri,
        durationMs = durationMs,
        imageUrl = imageUrl,
        explicit = explicit,
        contentType = ContentType.TRACK,
        files = emptyList(),
        dualFiles = emptyList(),
        directFiles = emptyList(),
    )

    private fun row(track: ClusterTrack): TrackItem = TrackItem(
        title = track.title,
        artist = track.artistName,
        uri = track.uri,
        imageUrl = track.imageUrl.takeIf { it.isNotBlank() },
        durationMs = track.durationMs,
        isExplicit = track.explicit,
        albumUri = track.albumUri,
        artistUri = track.artistUri,
    )

    private fun mirrorState(command: PlaybackCommand?) {
        if (command == null) return
        if (command.isControl) return
        val track = command.currentTrack ?: return
        val loaded = player.state.value.trackUri
        val bothAds = loaded.startsWith(AD_PREFIX) && track.uri.startsWith(AD_PREFIX)
        if (!bothAds && player.hasSource && loaded.isNotBlank() && loaded != track.uri) {
            if (held?.uri != track.uri) {
                log.i("hold '${track.name}' until the engine leaves '${loaded.takeLast(12)}'")
            }
            held = track
            return
        }
        held = null
        val local = player.hasSource
        val position = if (local) {
            player.positionMs
        } else {
            cluster?.takeIf { it.track.uri == track.uri }?.positionMs
                ?: command.currentState?.initialPositionMs
                ?: state.positionMs
        }
        val duration = track.durationMs.takeIf { it > 0L } ?: state.durationMs
        val nextArtistUri = if (track.uri != state.trackUri) artistUriOf(track.uri) else state.artistUri
        val nextAlbumUri = if (track.uri != state.trackUri) albumUriOf(track.uri) else state.albumUri
        state = state.copy(
            title = track.name,
            artist = track.artists,
            coverUrl = track.imageUrl,
            trackUri = track.uri,
            artistUri = nextArtistUri,
            albumUri = nextAlbumUri,
            isPlaying = if (local) player.isPlaying else !command.paused,
            positionMs = position,
            durationMs = duration,
            progress = progressOf(position, duration),
            explicit = track.explicit,
            contextUri = track.contextUri.ifBlank { state.contextUri },
            contextLabel = command.contextDescription.trim(),
        )
    }

    private fun smokeReport(command: PlaybackCommand) {
        if (command.isControl) {
            applyControl(command)
            return
        }
        if (command.isReplaceState) {
            applyReplaceState(command)
            return
        }
        val track = command.currentTrack ?: return
        TrackUid.learn(command)
        log.i("push sm=${command.stateMachineId.take(10)} idx=${command.stateIndex} " +
                "state=${command.stateId.take(8)} tracks=${command.tracks.size} states=${command.states.size} " +
                "'${track.name}' ad=${command.isAd}"
        )
        if (remoteTarget().isNotBlank()) {
            log.i("push belongs to the remote device, this one stays quiet")
            return
        }
        val loaded = player.state.value.trackUri
        val bothAds = loaded.startsWith(AD_PREFIX) && track.uri.startsWith(AD_PREFIX)
        if (!command.isAd && !bothAds && !pendingActive() && player.hasSource && loaded != track.uri) {
            log.w("push ignored: the engine owns '${loaded.takeLast(12)}', the server names " +
                    "'${track.uri.takeLast(12)}' at idx=${command.stateIndex} " +
                    "sm=${command.stateMachineId.take(10)}"
            )
            return
        }
        if (!track.isDirect) player.prefetch(track.uri, connect.spclientHost)
        scope.launch {
            val snapshot = Snapshot.of(command, DebugSource.BEFORE_TRACK_LOAD, 0L, 0L, speed = 0.0)
            if (snapshot == null) {
                log.w("report: no snapshot")
                flushHeld()
                return@launch
            }
            val accepted = connect.report(snapshot)
            log.i("report before_track_load state=${command.stateId.take(8)} accepted=$accepted " +
                    "next=${connect.playback?.stateId?.take(8)}"
            )
            if (accepted && pendingActive() && track.uri == pendingTrack) {
                val index = pendingIndex
                clearPending()
                scope.launch { settleCluster(track.uri, index) }
            }
            val resume = resumePositionFor(track.uri)
                ?: command.currentState?.initialPositionMs?.takeIf { it > 0L }
                ?: cluster?.takeIf { it.track.uri == track.uri }?.positionMs?.takeIf { it > 0L }
                ?: 0L
            log.i("audio '${track.name}' from=${resume}ms direct=${track.isDirect} files=${track.files.size}"
            )
            val loaded = player.state.value.trackUri
            if (player.hasSource && loaded == track.uri) {
                log.i("audio already on '$loaded', not restarting")
                return@launch
            }
            player.play(track, connect.spclientHost, resume)
        }
    }

    private fun onPlayerEvent(event: PlayerEvent) {
        val command = connect.playback
        if (command == null) {
            // No server playback session behind the engine: the local state
            // publish is the report, and advancement walks the queue here.
            when (event) {
                PlayerEvent.Started -> scope.launch { publishLocalFromEngine() }
                is PlayerEvent.Seeked -> scope.launch { publishLocalFromEngine(event.positionMs) }
                PlayerEvent.Ended -> {
                    val ended = player.state.value.trackUri
                    log.i("player ended '$ended' with no command, advancing on this phone")
                    scope.launch { advance(ended) }
                }
                is PlayerEvent.Failed -> flushHeld()
                is PlayerEvent.Progress -> Unit
            }
            return
        }
        val track = command.currentTrack
        val ad = command.isAd
        val raw = if (ad) AD_THRESHOLD_MS else player.positionMs
        val duration = if (ad) AD_THRESHOLD_MS else player.durationMs
        val position = if (duration > 0L) raw.coerceAtMost(duration) else raw
        when (event) {
            PlayerEvent.Started -> {
                log.i("player started pos=$position dur=${player.durationMs}ms ad=$ad")
                scope.launch {
                    val snapshot = Snapshot.of(
                        command,
                        DebugSource.STARTED_PLAYING,
                        positionMs = position,
                        previousPositionMs = 0L,
                        speed = if (player.isPlaying) 1.0 else 0.0,
                        paused = !player.isPlaying,
                        durationMs = duration,
                        format = player.format.takeIf { it > 0 }?.toString().orEmpty(),
                        bitrate = player.bitrate,
                        quality = player.quality,
                        videoOn = canvasOn,
                    )
                    if (snapshot == null) {
                        log.w("started: no snapshot")
                        return@launch
                    }
                    val accepted = connect.report(snapshot)
                    if (track != null) connect.melody.audioParams(track, position)
                    log.i("report started_playing accepted=$accepted pos=$position ad=$ad " +
                            "fmt=${snapshot.format} ${snapshot.bitrate}b ${snapshot.quality}"
                    )
                }
            }

            is PlayerEvent.Seeked -> {
                log.i("player seeked ${event.positionMs}ms")
                scope.launch {
                    val snapshot = Snapshot.of(
                        command,
                        DebugSource.SEEK,
                        positionMs = event.positionMs,
                        previousPositionMs = event.positionMs,
                        speed = 1.0,
                        videoOn = canvasOn,
                    )
                    snapshot?.let { connect.report(it) }
                }
            }

            PlayerEvent.Ended -> {
                log.i("player ended at $raw clamped=$position dur=$duration ad=$ad")
                scope.launch {
                    if (ad) {
                        val thresholdSnapshot = connect.playback?.let {
                            Snapshot.of(
                                it,
                                DebugSource.PLAYED_THRESHOLD_REACHED,
                                positionMs = AD_THRESHOLD_MS,
                                previousPositionMs = AD_THRESHOLD_MS,
                                speed = 1.0,
                                durationMs = AD_THRESHOLD_MS,
                                videoOn = canvasOn,
                            )
                        }
                        val accepted = thresholdSnapshot?.let { connect.report(it) }
                        log.i("report played_threshold_reached accepted=$accepted ad=true")
                    }
                    val finalized = connect.playback?.let {
                        Snapshot.of(
                            it,
                            DebugSource.TRACK_DATA_FINALIZED,
                            positionMs = position,
                            previousPositionMs = position,
                            speed = 1.0,
                            durationMs = duration,
                            videoOn = canvasOn,
                        )
                    }
                    if (finalized == null) {
                        log.w("report track_data_finalized: no snapshot")
                    } else {
                        val accepted = connect.report(finalized)
                        log.i("report track_data_finalized accepted=$accepted ad=$ad pos=$position")
                    }
                    advance(command.currentTrack?.uri.orEmpty().ifBlank { player.state.value.trackUri })
                }
            }

            is PlayerEvent.Progress -> Unit

            is PlayerEvent.Failed -> {
                log.w("player failed: ${event.message}")
                if (event.message.startsWith("license") || event.message.startsWith("licence")) {
                    scope.launch { blameTheStream(event.message) }
                    if (event.message.contains("429")) {
                        log.w("a 429 does not clear the block on screen, the last track keeps its metadata " +
                                "until the balance comes back")
                        return
                    }
                }
                flushHeld()
            }
        }
    }

    private suspend fun blameTheStream(message: String) {
        val fetched = connect.refreshCluster()
        val at = connect.cluster.value
        val active = at?.activeDeviceId.orEmpty()
        log.w("$message, active=${active.ifBlank { "?" }} ours=${active == deviceId} " +
                "playing=${at?.isPlaying} refreshed=$fetched " +
                "context='${at?.contextUri.orEmpty()}'"
        )
        when {
            active.isNotBlank() && active != deviceId ->
                log.w("the stream is held by another device, this account allows one at a time")
            active.isBlank() ->
                log.w("no active device in the cluster, so the 429 is not another device holding the stream")
            else ->
                log.w("this device holds the stream and was still refused, the 429 came from the edge")
        }
    }

    private fun threshold(playing: PlayerState) {
        if (playing.format <= 0) return
        if (playing.positionMs < THRESHOLD_MS) return
        if (playing.trackUri.isBlank() || thresholdTrack == playing.trackUri) return
        thresholdTrack = playing.trackUri
        log.i("threshold at ${playing.positionMs}ms '${playing.trackUri}'")
        scope.launch {
            val command = connect.playback ?: return@launch
            val snapshot = Snapshot.of(
                command,
                DebugSource.PLAYED_THRESHOLD_REACHED,
                positionMs = playing.positionMs,
                previousPositionMs = playing.positionMs,
                speed = 1.0,
                durationMs = playing.durationMs,
                format = playing.format.toString(),
                bitrate = playing.bitrate,
                quality = playing.quality,
            )
            val accepted = snapshot?.let { connect.report(it) }
            log.i("report played_threshold_reached accepted=$accepted pos=${playing.positionMs}")
        }
    }

    private fun chainFor(): List<String> {
        if (chainContext.isBlank()) return emptyList()
        val cluster = connect.cluster.value
        val playing = cluster?.contextUri.orEmpty()
        if (playing.isNotBlank() && playing != chainContext) {
            val single = playing.startsWith("spotify:track:") ||
                playing.startsWith("spotify:episode:")
            val barren = cluster == null || cluster.nextTracks.isEmpty()
            if (!single && !barren) {
                log.w("chain '$chainContext' is not the playing context '$playing', following the server")
                return emptyList()
            }
            log.i("chain '$chainContext' kept, the server still names " +
                    "'${playing.takeLast(12)}' with ${cluster?.nextTracks?.size ?: 0} next row(s)")
        }
        return chainRows
    }

    private fun rowOf(rows: List<String>, uri: String): Int {
        if (uri.isBlank()) return -1
        if (resumeRow in rows.indices && rows[resumeRow] == uri) return resumeRow
        return rows.indexOfFirst { it == uri }
    }

    private suspend fun advance(ended: String) {
        if (advancing) {
            log.w("advance: already running for the track that just ended, not chaining a second time")
            return
        }
        advancing = true
        try {
            runAdvance(ended)
        } finally {
            advancing = false
        }
    }

    private suspend fun runAdvance(endedIn: String) {
        val ended = endedIn
        val rows = chainFor()
        val at = rowOf(rows, ended)
        resumeRow = if (at >= 0) at + 1 else -1
        val server = connect.cluster.value
        val serverQueued = server?.queued.orEmpty()
        val queued = queuedTail.firstOrNull()
        // With no playback session behind the engine there is no server next
        // to wait for; asking would only stall the gap between tracks.
        val next = when {
            connect.playback == null -> null
            queued != null || serverQueued.isNotEmpty() -> awaitServerNext(ended)
            else -> serverNext(ended)
        }
        val moved = next?.currentTrack
        val engine = player.state.value.trackUri
        val engineOnEnded = engine.isBlank() || engine == ended ||
                (ended.startsWith(AD_PREFIX) && engine.startsWith(AD_PREFIX))
        if (!engineOnEnded) {
            log.i("advance: the engine already moved to '${engine.takeLast(12)}', nothing to do")
            return
        }
        val fromQueue = moved != null &&
                (moved.uri == queued?.uri || serverQueued.any { it.uri == moved.uri })
        if (next != null && moved != null) {
            log.i("advance -> '${moved.name}' from the server, the context stays put " +
                    "sm=${next.stateMachineId.take(10)} queued=$fromQueue"
            )
            queuedTail = queuedTail.filterNot { it.uri == moved.uri }
            startFromServer(next, moved)
            return
        }
        if (queued != null && serverQueued.isNotEmpty() && serverQueued.none { it.uri == queued.uri }) {
            queuedTail = queuedTail.drop(1)
            log.w("advance: '${queued.uri.takeLast(12)}' is no longer queued on the server, skipping it")
        } else if (queued != null) {
            queuedTail = queuedTail.drop(1)
            publishQueue(server)
            log.i("advance -> the queued '${queued.title.ifBlank { queued.uri.takeLast(12) }}', " +
                    "the list resumes at row $resumeRow of ${rows.size}")
            playTrack(queued.uri, queued.uri, -1)
            return
        }
        if (repeatTrackOn(server) && ended.isNotBlank() && !ended.startsWith(AD_PREFIX)) {
            log.i("advance -> repeating '${ended.takeLast(12)}' (repeat one)")
            playTrack(
                ended,
                chainContext.ifBlank { server?.contextUri.orEmpty() },
                if (at >= 0) at else -1,
            )
            return
        }
        if (shuffleOn(server) && !ended.startsWith(AD_PREFIX)) {
            val pool = rows.ifEmpty {
                server?.window()?.filter { it.isVisible }?.map { it.uri }.orEmpty()
            }.filter { it.isNotBlank() && it != ended }
            if (pool.isNotEmpty()) {
                val pick = pool.random()
                log.i("advance -> '${pick.takeLast(12)}' shuffled out of ${pool.size} rows")
                playTrack(
                    pick,
                    chainContext.ifBlank { server?.contextUri.orEmpty() },
                    rows.indexOf(pick),
                )
                return
            }
        }
        if (resumeRow in rows.indices) {
            val row = rows[resumeRow]
            log.i("advance -> '$row' from the list that started it, row $resumeRow of ${rows.size} " +
                    "in '$chainContext'"
            )
            playTrack(row, chainContext, resumeRow)
            return
        }
        if (next != null && moved != null) {
            startFromServer(next, moved)
            return
        }
        val command = connect.playback
        if (command != null) {
            log.i("advance: no transition, state=${command.stateId.take(8)} " +
                    "idx=${command.stateIndex}/${command.states.size} adv=${command.currentState?.advanceIndex} " +
                    "track=${command.currentTrack?.uri?.takeLast(12)}"
            )
        }
        val cluster = connect.cluster.value ?: return
        if (cluster.window().none { it.uri == ended }) {
            log.w("advance: '$ended' is not in the cluster window, not guessing")
            return
        }
        val target = targetFrom(cluster, 0)
        if (target == null || target.track.uri == ended) {
            if (repeatContextOn(cluster) && !ended.startsWith(AD_PREFIX)) {
                val firstRow = rows.firstOrNull { it.isNotBlank() && it != ended }
                    ?: cluster.window().firstOrNull { it.isVisible && it.uri != ended }?.uri
                if (firstRow != null) {
                    log.i("advance -> wrapping to '${firstRow.takeLast(12)}' (repeat all)")
                    playTrack(
                        firstRow,
                        chainContext.ifBlank { cluster.contextUri },
                        rows.indexOf(firstRow),
                    )
                    return
                }
            }
            log.w("advance: end of the context at '${command?.currentTrack?.name ?: ended.takeLast(12)}'")
            autoplayFrom(ended)
            return
        }
        log.i("advance -> '${target.track.title}' from the cluster row")
        playTrack(target.track.uri, cluster.contextUri, target.index)
    }

    private suspend fun autoplayFrom(ended: String) {
        if (ended.isBlank() || ended.startsWith(AD_PREFIX)) return
        if (!autoplayEnabled()) {
            log.i("autoplay: off, staying quiet at the end of the context")
            return
        }
        val seed = ended.takeIf { it.startsWith("spotify:track:") }
            ?: chainRows.lastOrNull { it.startsWith("spotify:track:") }
            ?: return
        val radio = runCatching { Krespot.artists.artistRadio(context, seed) }.getOrNull()
        if (radio.isNullOrBlank()) {
            log.w("autoplay: no station for '${seed.takeLast(12)}', staying quiet")
            return
        }
        log.i("autoplay -> '$radio' from the station seeded by '${seed.takeLast(12)}'")
        chainRows = emptyList()
        chainContext = radio
        resumeRow = -1
        playTrack(radio, radio, 0)
    }

    private fun autoplayEnabled(): Boolean =
        context.getSharedPreferences(CLIENT_PREFS, Context.MODE_PRIVATE)
            .getBoolean(AUTOPLAY_ON_END_PREF, true)

    private fun serverNext(ended: String): PlaybackCommand? {
        val command = connect.playback ?: return null
        val same = command.currentTrack?.uri.orEmpty() == ended
        val following = if (same) command.following() else command
        val track = following?.currentTrack ?: return null
        return if (track.uri.isBlank() || track.uri == ended) null else following
    }

    private suspend fun awaitServerNext(ended: String): PlaybackCommand? {
        serverNext(ended)?.let { return it }
        val until = System.currentTimeMillis() + ADVANCE_WAIT_MS
        while (System.currentTimeMillis() < until) {
            delay(120L)
            val engine = player.state.value.trackUri
            if (engine.isNotBlank() && engine != ended) return null
            serverNext(ended)?.let { return it }
        }
        log.i("advance: the server stayed on '${ended.takeLast(12)}' for ${ADVANCE_WAIT_MS}ms")
        return null
    }

    private suspend fun startFromServer(next: PlaybackCommand, track: CommandTrack) {
        if (remoteTarget().isNotBlank()) {
            log.i("advance: another device owns the stream, not starting the engine here")
            return
        }
        val from = next.currentState?.initialPositionMs ?: 0L
        log.i("advance -> '${track.name}' sm=${next.stateMachineId.take(10)} " +
                "state=${next.stateId.take(8)} tracks=${next.tracks.size} from=${from}ms"
        )
        player.play(track, connect.spclientHost, from)
        val snapshot = Snapshot.of(next, DebugSource.BEFORE_TRACK_LOAD, 0L, 0L, speed = 0.0)
        if (snapshot == null) {
            log.w("advance: no snapshot for '${track.name}'")
            return
        }
        val accepted = connect.report(snapshot)
        log.i("advance before_track_load accepted=$accepted state=${snapshot.stateId.take(8)}")
        if (accepted) settleCluster(track.uri, -1)
    }

    private class Target(val track: ClusterTrack, val index: Int, val anchor: Int)

    private fun playingUri(cluster: ClusterState): String {
        val engine = player.state.value.trackUri
        if (engine.isNotBlank() && cluster.window().any { it.uri == engine }) return engine
        val commanded = connect.playback?.currentTrack?.uri.orEmpty()
        if (commanded.isNotBlank()) return commanded
        return cluster.track.uri
    }

    private fun targetFrom(cluster: ClusterState, offset: Int): Target? {
        val window = cluster.window()
        val anchor = cluster.anchorOf(playingUri(cluster))
        var seen = -1
        for (position in (anchor + 1) until window.size) {
            val row = window[position]
            if (!row.isVisible) continue
            seen++
            if (seen != offset) continue
            return Target(row, if (row.isQueued) -1 else cluster.indexOf(position), anchor)
        }
        return null
    }

    private companion object {
        const val KEY_LOOKAHEAD = 6

    const val AD_THRESHOLD_MS = 1000L
        const val AD_PREFIX = "spotify:ad:"
        const val THRESHOLD_MS = 30_000L
        const val CONFIRM_MS = 2500L
        const val CLUSTER_TRIES = 3
        const val DEVICE_WATCH_MS = 4_000L
        const val DEVICE_POLL_MS = 15_000L
        const val SKIP_SETTLE_MS = 600L
        const val REMOTE_TICK_MS = 1_000L
        const val RESYNC_MS = 3_000L
        const val SKIP_TRIES = 5
        const val SKIP_RETRY_MS = 400L
        const val CLUSTER_RETRY_MS = 400L
        const val RESTART_MS = 3000L
        const val PENDING_MS = 10_000L
        const val ADVANCE_WAIT_MS = 900L
        const val LIVE_STATE_MS = 30_000L
        const val MODE_SETTLE_MS = 1200L
        const val MODE_OVERRIDE_MS = 8000L
        const val REMOTE_INTENT_MS = 10_000L
        const val BLANK = "blank"
        const val AUTOPLAY_WINDOW_MS = 30_000L
        const val GONE_READS = 2
        const val TRANSFER_FRESH_MS = 120_000L
    }
}
