package com.spotiduck.backend.connect

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import com.spotiduck.backend.connect.cluster.ClusterParser
import com.spotiduck.backend.connect.cluster.ClusterState
import com.spotiduck.backend.connect.cluster.ClusterTrack
import com.spotiduck.backend.connect.cluster.Device
import com.spotiduck.backend.connect.command.CommandClient
import com.spotiduck.backend.connect.command.CommandParser
import com.spotiduck.backend.connect.command.CommandTrack
import com.spotiduck.backend.connect.command.PlaybackCommand
import com.spotiduck.backend.connect.command.Registration
import com.spotiduck.backend.connect.command.TrackUid
import com.spotiduck.backend.connect.dealer.Dealer
import com.spotiduck.backend.connect.dealer.DealerFrame
import com.spotiduck.backend.connect.dealer.DealerHost
import com.spotiduck.backend.connect.dealer.DealerUri
import com.spotiduck.backend.connect.state.Melody
import com.spotiduck.backend.connect.state.Snapshot
import com.spotiduck.backend.connect.state.StateReporter
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.core.webview.SilentTokenRefresh
import org.json.JSONObject

private val log = Logs.of(Logs.Module.CONNECT)

private const val TRANSFER_TRIES = 6

private const val TRANSFER_RETRY_MS = 500L

class KrespotConnect(private val context: Context) {

    val deviceId: String = Device.id(context)

    private val dealer = Dealer()

    private val reporter = StateReporter(context, deviceId) { spclient }

    val melody = Melody(context, { spclient }, { _command.value?.playbackSessionId.orEmpty() })

    private val _command = MutableStateFlow<PlaybackCommand?>(null)
    val command: StateFlow<PlaybackCommand?> = _command

    private val _cluster = MutableStateFlow<ClusterState?>(null)
    val cluster: StateFlow<ClusterState?> = _cluster

    private val _pushes = MutableSharedFlow<PlaybackCommand>(extraBufferCapacity = 8)
    val pushes: SharedFlow<PlaybackCommand> = _pushes

    private val _connectionId = MutableStateFlow("")
    val connectionId: StateFlow<String> = _connectionId

    private val _registered = MutableStateFlow(false)
    val registered: StateFlow<Boolean> = _registered

    @Volatile
    private var spclient: String = ""

    @Volatile
    private var connected: Boolean = false

    @Volatile
    var accepts: ((PlaybackCommand) -> Boolean)? = null

    val playback: PlaybackCommand? get() = _command.value

    val spclientHost: String get() = spclient

    fun frames(): Flow<DealerFrame> = flow {
        var attempt = 0
        while (true) {
            val host = DealerHost.resolve(context)
            if (host == null) {
                attempt++
                log.w("apresolve returned no dealer, retry $attempt")
                delay(backoff(attempt))
                continue
            }
            spclient = host.second
            val token = SpotifyAuth.token(context)
            log.i("device=$deviceId dealer=${host.first} token=${token.length}B")
            try {
                emitAll(dealer.open(host.first, token).onEach(::absorb))
                log.w("dealer stream ended")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                val message = failure.message.orEmpty()
                log.w("dealer failed ${failure.javaClass.simpleName}: $message")
                if (message.contains("401")) {
                    val refreshed = SilentTokenRefresh.refresh(context, "dealer")
                    log.i("dealer 401, token refreshed=$refreshed")
                }
            }
            if (connected) {
                connected = false
                attempt = 0
            }
            attempt++
            delay(backoff(attempt))
        }
    }

    private fun backoff(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 4)
        return (1000L shl shift).coerceAtMost(15_000L)
    }

    private suspend fun absorb(frame: DealerFrame) {
        when {
            frame.isConnection -> {
                connected = true
                _connectionId.value = frame.connectionId
                log.i("connection ${frame.connectionId.take(24)} raw=${frame.connectionIdRaw.length}B"
                )
                register(frame.connectionId, frame.connectionIdRaw)
            }

            frame.uri == DealerUri.TRACK_COMMAND -> frame.payloads.forEach { payload ->
                val parsed = CommandParser.parse(payload) ?: return@forEach
                if (accepts?.invoke(parsed) == false) {
                    log.w("command dropped: '${parsed.currentTrack?.name}' idx=${parsed.stateIndex} " +
                            "sm=${parsed.stateMachineId.take(10)} is not what this device is playing"
                    )
                    return@forEach
                }
                if (parsed.isControl) {
                    _pushes.tryEmit(parsed)
                    log.i("control '${parsed.endpoint}' from the dealer")
                    return@forEach
                }
                if (parsed.isReplaceState) {
                    log.i("replace_state pushed, the bridge decides what to load")
                    transferLandedByPush()
                    _pushes.tryEmit(parsed)
                    return@forEach
                }
                _command.value = parsed
                TrackUid.learn(parsed)
                reconcile(parsed)
                _pushes.tryEmit(parsed)
                log.v("command '${parsed.contextDescription}' tracks=${parsed.tracks.size} " +
                        "state=${parsed.stateId.take(8)} track=${parsed.currentTrack?.name}" +
                        if (parsed.isAd) " AD break" else ""
                )
            }

            frame.uri == DealerUri.CLUSTER -> frame.payloads.forEach { payload ->
                val parsed = withContext(Dispatchers.Default) { ClusterParser.parse(payload, deviceId) }
                if (parsed == null) {
                    log.w("cluster frame did not parse, ${payload.length}B " +
                            "head=${payload.take(140)}"
                    )
                    return@forEach
                }
                _cluster.value = parsed
                TrackUid.learn(parsed)
                if (parsed.isAd || parsed.adBreak != null) {
                    log.i("cluster ${parsed.updateReason} ad=${parsed.track.uri.take(24)} " +
                            "break=${parsed.adBreak?.positionInBreak}/${parsed.adBreak?.totalEstimate} " +
                            "seek=${!parsed.disallowSeeking}"
                    )
                }
            }

            else -> log.v("frame ${frame.uri.take(56)} ${frame.payloads.size}p")
        }
    }

    private suspend fun register(connectionId: String, connectionIdRaw: String) {
        if (connectionId.isBlank() || spclient.isBlank()) return
        val host = spclient
        val started = SystemClock.elapsedRealtime()
        val player = Registration.player(context, host, deviceId, connectionIdRaw)
        val observer = Registration.observer(context, host, deviceId, connectionId)
        val body = observer?.takeIf { it.ok }?.body.orEmpty()
        if (body.isNotBlank()) {
            val parsed = withContext(Dispatchers.Default) { ClusterParser.parse(body, deviceId) }
            if (parsed != null) {
                _cluster.value = parsed
                TrackUid.learn(parsed)
                log.i("cluster '${parsed.track.title}' idx=${parsed.contextIndex} " +
                        "next=${parsed.nextTracks.size} prev=${parsed.prevTracks.size} " +
                        "context=${parsed.contextUri.take(32)}"
                )
            }
        }
        _registered.value = player?.ok == true
        if (_registered.value) reporter.seed(Registration.initialSeq(player))
        log.i("registration observer=${observer?.code ?: "-"} player=${player?.code ?: "-"} " +
                "registered=${_registered.value} in ${SystemClock.elapsedRealtime() - started}ms"
        )
    }

    suspend fun refreshCluster(): Boolean {
        val connection = _connectionId.value
        if (spclient.isBlank() || connection.isBlank()) {
            log.w("cluster refresh: the dealer is not connected yet")
            return false
        }
        val response = Registration.observer(context, spclient, deviceId, connection)
        val body = response?.body.orEmpty()
        if (response?.ok != true || body.isBlank()) {
            log.w("cluster refresh: observer ${response?.code ?: "no response"}")
            return false
        }
        val parsed = withContext(Dispatchers.Default) { ClusterParser.parse(body, deviceId) }
        if (parsed == null) {
            log.w("cluster refresh: ${body.length}B with no player_state")
            return false
        }
        _cluster.value = parsed
        TrackUid.learn(parsed)
        if (parsed.devices.isEmpty()) {
            log.w("cluster refresh: the reply named no devices, root keys " +
                    runCatching { JSONObject(body).keys().asSequence().joinToString(",") }
                        .getOrDefault("-")
            )
        }
        log.v("cluster refresh '${parsed.track.title}' ctx=${parsed.contextUri.take(32)} " +
                "idx=${parsed.contextIndex} next=${parsed.nextTracks.size} " +
                "prev=${parsed.prevTracks.size}"
        )
        return true
    }

    suspend fun play(
        contextUri: String,
        trackUri: String,
        trackUid: String,
        trackIndex: Int,
        feature: String = "playlist",
        targetDeviceId: String = "",
    ): Boolean {
        if (spclient.isBlank()) {
            log.w("play: dealer not connected yet")
            return false
        }
        if (trackUri.isBlank()) {
            log.w("play: empty track uri")
            return false
        }
        val response = CommandClient.play(
            context = context,
            spclient = spclient,
            deviceId = deviceId,
            contextUri = contextUri,
            trackUri = trackUri,
            trackUid = trackUid,
            trackIndex = trackIndex,
            feature = feature,
            targetDeviceId = targetDeviceId,
        )
        log.i("play '$trackUri' idx=$trackIndex uid=${trackUid.take(12)} -> ${response?.code ?: "no response"} " +
                response?.body?.take(400).orEmpty()
        )
        return response?.ok == true
    }

    suspend fun addToQueue(trackUri: String): Boolean {
        if (spclient.isBlank() || trackUri.isBlank()) {
            log.w("queue: nothing to add, spclient='$spclient' uri='$trackUri'")
            return false
        }
        val response = CommandClient.addToQueue(context, spclient, deviceId, trackUri)
        return response?.ok == true
    }

    suspend fun setQueue(tracks: List<ClusterTrack>): Boolean {
        if (spclient.isBlank()) {
            log.w("queue: dealer not connected yet")
            return false
        }
        val revision = _cluster.value?.queueRevision.orEmpty()
        val response = CommandClient.setQueue(context, spclient, deviceId, tracks, revision)
        return response?.ok == true
    }

    suspend fun transferTo(targetDeviceId: String, restorePaused: String = CommandClient.RESTORE): Boolean {
        if (spclient.isBlank()) {
            log.w("transfer: dealer not connected yet")
            return false
        }
        if (targetDeviceId == deviceId) {
            val active = _cluster.value?.activeDevice
            if (active != null && active.id == deviceId) {
                log.i("transfer: this device is already the active one, nothing to do")
                return false
            }
            log.i("transfer: taking the stream back from another device")
        }
        val response = CommandClient.transfer(context, spclient, deviceId, targetDeviceId, restorePaused)
        val ok = response?.ok == true
        if (ok) {
            log.i("transfer accepted, watching for it to land")
            settleTransfer(targetDeviceId)
        } else {
            log.w("transfer to " + targetDeviceId.take(8) + " answered " + (response?.code ?: "nothing"))
        }
        return ok
    }

    @Volatile
    private var transferTarget: String = ""

    private fun transferLandedByPush() {
        if (transferTarget.isBlank()) return
        log.i("transfer landed by the pushed state, the active device is " + transferTarget.take(8))
        transferTarget = ""
    }

    private suspend fun settleTransfer(targetDeviceId: String) {
        transferTarget = targetDeviceId
        repeat(TRANSFER_TRIES) { attempt ->
            if (transferTarget.isBlank()) return
            refreshCluster()
            val at = _cluster.value ?: return
            val nowActive = at.activeDeviceId.ifBlank { at.activeDevice?.id.orEmpty() }
            if (nowActive == targetDeviceId) {
                transferTarget = ""
                log.i("transfer landed on attempt " + (attempt + 1) + ", the active device is now " + targetDeviceId.take(8))
                return
            }
            delay(TRANSFER_RETRY_MS)
        }
        if (transferTarget.isBlank()) return
        transferTarget = ""
        log.w("transfer never landed, the active device is still " +
                (_cluster.value?.activeDeviceId?.take(8) ?: "unknown")
        )
    }

    suspend fun setPausedOn(targetDeviceId: String, playing: Boolean): Boolean {
        if (spclient.isBlank()) {
            log.w("pause: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.setPaused(context, spclient, deviceId, target, paused = playing)
        if (response?.ok == true) return true
        if (response?.code == 403) {
            val body = response.body.orEmpty()
            if (body.contains("already_paused")) {
                log.i("pause: '${target.take(8)}' is already paused, nothing to send")
                return true
            }
            if (body.contains("already_playing")) {
                log.i("resume: '${target.take(8)}' is already playing, nothing to send")
                return true
            }
        }
        return false
    }

    suspend fun seekOn(targetDeviceId: String, positionMs: Long): Boolean {
        if (spclient.isBlank()) {
            log.w("seek: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.seekTo(context, spclient, deviceId, target, positionMs)
        return response?.ok == true
    }

    suspend fun skipOn(targetDeviceId: String, next: Boolean): Boolean {
        if (spclient.isBlank()) {
            log.w("skip: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.skip(context, spclient, deviceId, target, next)
        return response?.ok == true
    }

    suspend fun setVolumeOn(targetDeviceId: String, level: Int): Boolean {
        if (spclient.isBlank()) return false
        val response = CommandClient.volume(context, spclient, deviceId, targetDeviceId, level)
        return response?.ok == true
    }

    suspend fun start(track: CommandTrack, stateId: String, positionMs: Long): Boolean {        val announced = melody.playbackStart(track, stateId, positionMs)
        val started = melody.start(track, stateId, positionMs, paused = false)
        if (!track.isDirect) melody.keysystem()
        return announced && started
    }

    suspend fun report(snapshot: Snapshot): Boolean {
        val next = reporter.report(snapshot) ?: return false
        _command.value = next
        reconcile(next)
        return true
    }

    fun reset() {
        _command.value = null
        _cluster.value = null
        _registered.value = false
        _connectionId.value = ""
        TrackUid.forget()
        reporter.reset()
        spclient = ""
        connected = false
        Device.forget(context)
        log.i("reset, command, cluster and registration dropped")
    }

    fun learn(command: PlaybackCommand) {
        _command.value = command
        reconcile(command)
        log.i("the engine follows a pushed state, sm=${command.stateMachineId.take(10)} " +
                "idx=${command.stateIndex} paused=${command.paused}"
        )
    }

    private fun reconcile(command: PlaybackCommand) {
        val cluster = _cluster.value ?: return
        val uri = command.currentTrack?.uri.orEmpty()
        if (uri.isBlank() || uri == cluster.track.uri) return
        val moved = cluster.movedTo(uri) ?: return
        _cluster.value = moved
        log.i("cluster follows to '${moved.track.title}' idx=${moved.contextIndex} " +
                "prev=${moved.prevTracks.size} next=${moved.nextTracks.size}"
        )
    }
}
