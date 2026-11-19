package com.quackify.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ConnectivityMonitor
import com.quackify.app.widget.WidgetSync
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.settings.PlayerButtonAction
import com.quackify.compose.settings.PlayerButtonsConfig
import com.quackify.compose.settings.PlayerButtonsStore
import com.quackify.compose.shell.ComposePlaybackState
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The foreground leg of the player: a media notification the system styles
 * after the session, with like / shuffle / repeat around the usual
 * play/pause/previous/next controls, the track's artwork and a seekbar
 * extrapolated from the playback state. Everything the controls do is routed
 * straight into the bridge's listener, so the notification can never disagree
 * with the on-screen player.
 *
 * While nothing is loaded the service still keeps the process alive but
 * posts a quiet placeholder instead of a media card.
 */
class PlaybackService : Service() {

    private val log = Logs.of(Logs.Module.SERVICE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var session: MediaSessionCompat? = null

    private var watcher: Job? = null

    /** Artwork for the current cover, plus the URL it was decoded from. */
    private var art: Bitmap? = null

    private var artUrl = ""

    /** Covers that failed to decode once are not retried on every state tick. */
    private var artRefused = ""

    private var artJob: Job? = null

    /** Identity of the last posted notification; position ticks must not repost. */
    private var lastIdentity = ""

    @Volatile
    private var finished = false

    override fun onCreate() {
        super.onCreate()
        channel()
        // A widget can bring the service up before any activity ran: make sure
        // the process-wide watchdog (and with it the Net offline gate) is alive.
        ConnectivityMonitor.ensureStarted(applicationContext)
        val bridge = PlaybackHost.ensure(this)
        PlaybackHost.markForeground()
        WidgetSync.start(this)
        setUpSession(bridge)
        val foreground = runCatching {
            startForeground(NOTIFICATION_ID, buildNotification(bridge.state))
        }
        if (foreground.isFailure) {
            log.w("startForeground refused from the background, stopping until the app is visible")
            stopSelf()
            return
        }
        watch(bridge)
        log.i("service created with a media session, the app owns nothing once it leaves recents")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            log.w("restarted by the system with nothing to play, leaving the foreground")
            stopPlayback()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                log.i("stop requested, leaving the foreground")
                stopPlayback()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> PlaybackHost.bridge?.listener?.onPlayPause()
            ACTION_NEXT -> PlaybackHost.bridge?.listener?.onSkipNext()
            ACTION_PREVIOUS -> PlaybackHost.bridge?.listener?.onSkipPrevious()
            ACTION_LIKE -> PlaybackHost.bridge?.listener?.onLikeToggle()
            ACTION_SHUFFLE -> PlaybackHost.bridge?.listener?.onShuffleToggle()
            ACTION_REPEAT -> PlaybackHost.bridge?.listener?.onRepeatToggle()
            ACTION_REWIND_15 -> PlaybackHost.bridge?.listener?.onSkipSeek(-15_000L)
            ACTION_FORWARD_15 -> PlaybackHost.bridge?.listener?.onSkipSeek(15_000L)
            ACTION_REFRESH -> {
                val state = PlaybackHost.bridge?.state
                if (state != null) {
                    lastIdentity = ""
                    notify(buildNotification(state))
                    val session = session
                    if (session != null) {
                        val progress = PlaybackHost.bridge?.progressSnapshot ?: com.quackify.app.PlaybackProgressSnapshot(0, 0)
                        updatePlaybackState(session, state, progress, true)
                    }
                }
            }
        }
        PlaybackHost.ensure(this)
        PlaybackHost.markForeground()
        WidgetSync.start(this)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        log.i("task removed, the app is gone from recents")
        stopPlayback()
        PlaybackHost.teardown(this)
        super.onTaskRemoved(rootIntent)
    }

    private fun stopPlayback() {
        finished = true
        watcher?.cancel()
        artJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        log.i("service destroyed")
        finished = true
        watcher?.cancel()
        artJob?.cancel()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        session?.isActive = false
        session?.release()
        session = null
        PlaybackHost.release()
        super.onDestroy()
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun setUpSession(bridge: PlaybackBridge) {
        val session = MediaSessionCompat(this, "Quackify")
        this.session = session
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                if (!bridge.state.isPlaying) bridge.listener.onPlayPause()
            }

            override fun onPause() {
                if (bridge.state.isPlaying) bridge.listener.onPlayPause()
            }

            override fun onSkipToNext() {
                bridge.listener.onSkipNext()
            }

            override fun onSkipToPrevious() {
                bridge.listener.onSkipPrevious()
            }

            override fun onSeekTo(pos: Long) {
                bridge.listener.onSeek(pos)
            }

            override fun onRewind() {
                bridge.listener.onSkipSeek(-15_000L)
            }

            override fun onFastForward() {
                bridge.listener.onSkipSeek(15_000L)
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    CUSTOM_LIKE -> bridge.listener.onLikeToggle()
                    CUSTOM_SHUFFLE -> bridge.listener.onShuffleToggle()
                    CUSTOM_REPEAT -> bridge.listener.onRepeatToggle()
                    CUSTOM_SEEK_BACK_15, "SEEK_15_SECONDS_BACK", "SEEK_BACK_15_ACTION" ->
                        bridge.listener.onSkipSeek(-15_000L)
                    CUSTOM_SEEK_FORWARD_15, "SEEK_15_SECONDS_FORWARD", "SEEK_FORWARD_15_ACTION" ->
                        bridge.listener.onSkipSeek(15_000L)
                }
            }

            override fun onStop() {
                log.i("session stopped, leaving the foreground")
                stopSelf()
            }
        })
        session.isActive = true
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0L, 0.0f)
                .build()
        )
    }

    private fun watch(bridge: PlaybackBridge) {
        watcher = scope.launch {
            launch {
                snapshotFlow { bridge.state }.collect { state ->
                    apply(state, bridge.progressSnapshot)
                }
            }
            launch {
                snapshotFlow { bridge.progressSnapshot }.collect { progress ->
                    apply(bridge.state, progress)
                }
            }
        }
    }

    private fun apply(state: ComposePlaybackState, progress: PlaybackProgressSnapshot) {
        if (finished) return
        val hasTrack = state.trackUri.isNotBlank() || state.title.isNotBlank()
        val session = session ?: return
        updatePlaybackState(session, state, progress, hasTrack)
        val isEpisode = state.trackUri.startsWith("spotify:episode:") || state.contextUri.startsWith("spotify:show:")
        val identity = "${state.trackUri}|${state.title}|${state.artist}|" +
            "${state.coverUrl}|${state.isPlaying}|" +
            "${state.contextLabel}|${art != null}|" +
            "${state.isLiked}|${state.shuffleMode}|${state.repeatMode}|$isEpisode"
        if (identity == lastIdentity) return
        lastIdentity = identity
        if (hasTrack) {
            setMetadata(session, state, progress)
            maybeLoadArt(state.coverUrl)
        }
        notify(buildNotification(state))
    }

    /**
     * Position, duration and the play state ride the session's playback
     * state; the system extrapolates the seekbar between updates, so this is
     * cheap to call at the bridge's ~1Hz without touching the notification.
     */
    private fun updatePlaybackState(
        session: MediaSessionCompat,
        state: ComposePlaybackState,
        progress: PlaybackProgressSnapshot,
        hasTrack: Boolean,
    ) {
        if (!hasTrack) {
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0L, 0.0f)
                    .build()
            )
            return
        }
        val playing = state.isPlaying
        val isEpisode = state.trackUri.startsWith("spotify:episode:") || state.contextUri.startsWith("spotify:show:")
        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        if (state.remoteCanSeek) {
            actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
        }
        if (isEpisode) {
            actions = actions or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD
        }
        val playback = PlaybackStateCompat.Builder()
            .setActions(actions)

        val config = PlayerButtonsStore.load(this)
        if (config.andAutoEnabled) {
            if (isEpisode) {
                playback.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_SEEK_BACK_15,
                        "Rewind 15s",
                        R.drawable.encore_icon_seek_back_15,
                    ).build()
                )
                playback.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_SEEK_FORWARD_15,
                        "Forward 15s",
                        R.drawable.encore_icon_seek_forward_15,
                    ).build()
                )
            } else {
                val addedActions = mutableSetOf<String>()
                for (action in config.slots) {
                    if (addedActions.contains(action.token)) continue
                    when (action) {
                        PlayerButtonAction.FAV -> {
                            playback.addCustomAction(
                                PlaybackStateCompat.CustomAction.Builder(
                                    CUSTOM_LIKE, if (state.isLiked) "Unlike" else "Like",
                                    if (state.isLiked) R.drawable.ic_notif_like_on else R.drawable.ic_notif_like
                                ).build()
                            )
                            addedActions.add(action.token)
                        }
                        PlayerButtonAction.SHUFFLE -> {
                            if (state.remoteCanShuffle) {
                                val resId = resolveShuffleAsset(state.shuffleMode)
                                playback.addCustomAction(
                                    PlaybackStateCompat.CustomAction.Builder(
                                        CUSTOM_SHUFFLE,
                                        resolveShuffleLabel(state.shuffleMode),
                                        resId,
                                    ).build()
                                )
                                addedActions.add(action.token)
                            }
                        }
                        PlayerButtonAction.REPEAT -> {
                            if (state.remoteCanRepeat) {
                                val resId = resolveRepeatAsset(state.repeatMode)
                                playback.addCustomAction(
                                    PlaybackStateCompat.CustomAction.Builder(
                                        CUSTOM_REPEAT,
                                        resolveRepeatLabel(state.repeatMode),
                                        resId,
                                    ).build()
                                )
                                addedActions.add(action.token)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        playback.setState(
            when {
                state.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
                playing -> PlaybackStateCompat.STATE_PLAYING
                else -> PlaybackStateCompat.STATE_PAUSED
            },
            progress.positionMs,
            if (playing && !state.isBuffering && progress.durationMs > 0L) 1.0f else 0.0f,
        )
        session.setPlaybackState(playback.build())
    }

    private fun setMetadata(
        session: MediaSessionCompat,
        state: ComposePlaybackState,
        progress: PlaybackProgressSnapshot,
    ) {
        val art = art
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title.ifBlank { "Unknown track" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist.ifBlank { "Unknown artist" })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, progress.durationMs)
        if (art != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
        }
        session.setMetadata(metadata.build())
    }

    private fun maybeLoadArt(url: String) {
        if (url.isBlank()) {
            artUrl = ""
            art = null
            return
        }
        if (url == artUrl || url == artRefused) return
        artUrl = url
        art = null
        artJob?.cancel()
        artJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { fetchArt(url) }
            if (finished) return@launch
            if (bitmap == null) {
                artRefused = url
                log.w("cover '${url.takeLast(28)}' could not be decoded, the notification stays bare")
                return@launch
            }
            art = bitmap
            val state = PlaybackHost.bridge?.state ?: return@launch
            val progress = PlaybackHost.bridge?.progressSnapshot ?: return@launch
            val session = session
            if (session != null) {
                setMetadata(session, state, progress)
            }
            lastIdentity = ""
            notify(buildNotification(state))
        }
    }

    /** Covers are public CDN images; ~512px is all a notification card wants. */
    private fun fetchArt(url: String): Bitmap? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 512 || bounds.outHeight / (sample * 2) >= 512) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun buildNotification(state: ComposePlaybackState): android.app.Notification {
        val hasTrack = state.trackUri.isNotBlank() || state.title.isNotBlank()
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_duck)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (!hasTrack) {
            return builder
                .setContentTitle("Quackify")
                .setContentText("Nothing playing")
                .setStyle(
                    MediaNotificationCompat.MediaStyle()
                        .setMediaSession(session?.sessionToken)
                )
                .build()
        }
        builder
            .setContentTitle(state.title.ifBlank { "Unknown track" })
            .setContentText(state.artist.ifBlank { "Unknown artist" })
            .setLargeIcon(art)
        if (state.contextLabel.isNotBlank()) {
            builder.setSubText(state.contextLabel)
        }
        val isEpisode = state.trackUri.startsWith("spotify:episode:") || state.contextUri.startsWith("spotify:show:")
        if (isEpisode) {
            builder.addAction(R.drawable.ic_notif_previous, "Previous", servicePending(ACTION_PREVIOUS))
            builder.addAction(R.drawable.encore_icon_seek_back_15, "Rewind 15s", servicePending(ACTION_REWIND_15))
            builder.addAction(
                if (state.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (state.isPlaying) "Pause" else "Play",
                servicePending(ACTION_TOGGLE),
            )
            builder.addAction(R.drawable.encore_icon_seek_forward_15, "Forward 15s", servicePending(ACTION_FORWARD_15))
            builder.addAction(R.drawable.ic_notif_next, "Next", servicePending(ACTION_NEXT))

            builder.setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            return builder.build()
        }

        val config = PlayerButtonsStore.load(this)
        val nonNoneSlots = config.slots.filter { it != PlayerButtonAction.NONE }
        val slotsToUse = if (nonNoneSlots.isEmpty()) PlayerButtonsConfig.DEFAULT_SLOTS else nonNoneSlots

        val addedActionIndices = mutableListOf<Int>()
        var actionIdx = 0
        for (action in slotsToUse) {
            when (action) {
                PlayerButtonAction.PLAY -> {
                    builder.addAction(
                        if (state.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                        if (state.isPlaying) "Pause" else "Play",
                        servicePending(ACTION_TOGGLE),
                    )
                    addedActionIndices.add(actionIdx++)
                }
                PlayerButtonAction.PREV -> {
                    builder.addAction(R.drawable.ic_notif_previous, "Previous", servicePending(ACTION_PREVIOUS))
                    addedActionIndices.add(actionIdx++)
                }
                PlayerButtonAction.NEXT -> {
                    builder.addAction(R.drawable.ic_notif_next, "Next", servicePending(ACTION_NEXT))
                    addedActionIndices.add(actionIdx++)
                }
                PlayerButtonAction.FAV -> {
                    builder.addAction(
                        if (state.isLiked) R.drawable.ic_notif_like_on else R.drawable.ic_notif_like,
                        if (state.isLiked) "Unlike" else "Like",
                        servicePending(ACTION_LIKE),
                    )
                    addedActionIndices.add(actionIdx++)
                }
                PlayerButtonAction.REPEAT -> {
                    val resId = resolveRepeatAsset(state.repeatMode)
                    builder.addAction(
                        resId,
                        resolveRepeatLabel(state.repeatMode),
                        servicePending(ACTION_REPEAT),
                    )
                    addedActionIndices.add(actionIdx++)
                }
                PlayerButtonAction.SHUFFLE -> {
                    val resId = resolveShuffleAsset(state.shuffleMode)
                    builder.addAction(
                        resId,
                        resolveShuffleLabel(state.shuffleMode),
                        servicePending(ACTION_SHUFFLE),
                    )
                    addedActionIndices.add(actionIdx++)
                }
                PlayerButtonAction.NONE -> {}
            }
        }

        val compactIndices = mutableListOf<Int>()
        val prevIdx = slotsToUse.indexOf(PlayerButtonAction.PREV)
        val playIdx = slotsToUse.indexOf(PlayerButtonAction.PLAY)
        val nextIdx = slotsToUse.indexOf(PlayerButtonAction.NEXT)
        if (playIdx in addedActionIndices) {
            if (prevIdx in addedActionIndices && prevIdx != playIdx) compactIndices.add(prevIdx)
            compactIndices.add(playIdx)
            if (nextIdx in addedActionIndices && nextIdx != playIdx && nextIdx != prevIdx) compactIndices.add(nextIdx)
        }
        val finalCompact = if (compactIndices.isNotEmpty()) {
            compactIndices.sorted().take(3).toIntArray()
        } else {
            addedActionIndices.take(3).toIntArray()
        }

        builder.setStyle(
            MediaNotificationCompat.MediaStyle()
                .setMediaSession(session?.sessionToken)
                .setShowActionsInCompactView(*finalCompact)
        )
        return builder.build()
    }

    private fun contentIntent(): PendingIntent {
        val open = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .putExtra(EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, 0, open, pendingFlags())
    }

    private fun servicePending(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            pendingFlags(),
        )

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun notify(notification: android.app.Notification) {
        if (finished) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun resolveRepeatAsset(mode: RepeatMode): Int = when (mode) {
        RepeatMode.REPEAT_ONE -> R.drawable.repone
        RepeatMode.REPEAT_ALL -> R.drawable.repon
        RepeatMode.OFF -> R.drawable.repoff
    }

    private fun resolveShuffleAsset(mode: ShuffleMode): Int = when (mode) {
        ShuffleMode.SMART_SHUFFLE -> R.drawable.smart_shuffle
        ShuffleMode.SHUFFLE -> R.drawable.shuffle
        ShuffleMode.OFF -> R.drawable.shuffleoff
    }

    private fun resolveRepeatLabel(mode: RepeatMode): String = when (mode) {
        RepeatMode.REPEAT_ONE -> "Repeat One"
        RepeatMode.REPEAT_ALL -> "Repeat All"
        RepeatMode.OFF -> "Repeat Off"
    }

    private fun resolveShuffleLabel(mode: ShuffleMode): String = when (mode) {
        ShuffleMode.SMART_SHUFFLE -> "Smart Shuffle"
        ShuffleMode.SHUFFLE -> "Shuffle On"
        ShuffleMode.OFF -> "Shuffle Off"
    }

    companion object {
        const val ACTION_STOP = "com.quackify.app.STOP"
        const val ACTION_REFRESH = "com.quackify.app.REFRESH"
        const val EXTRA_OPEN_PLAYER = "com.quackify.app.OPEN_PLAYER"
        private const val ACTION_TOGGLE = "com.quackify.app.TOGGLE"
        private const val ACTION_NEXT = "com.quackify.app.NEXT"
        private const val ACTION_PREVIOUS = "com.quackify.app.PREVIOUS"
        private const val ACTION_LIKE = "com.quackify.app.LIKE"
        private const val ACTION_SHUFFLE = "com.quackify.app.SHUFFLE"
        private const val ACTION_REPEAT = "com.quackify.app.REPEAT"
        const val ACTION_REWIND_15 = "com.quackify.app.REWIND_15"
        const val ACTION_FORWARD_15 = "com.quackify.app.FORWARD_15"
        private const val CUSTOM_LIKE = "com.quackify.app.custom.LIKE"
        private const val CUSTOM_SHUFFLE = "com.quackify.app.custom.SHUFFLE"
        private const val CUSTOM_REPEAT = "com.quackify.app.custom.REPEAT"
        private const val CUSTOM_SEEK_BACK_15 = "com.quackify.app.custom.SEEK_BACK_15"
        private const val CUSTOM_SEEK_FORWARD_15 = "com.quackify.app.custom.SEEK_FORWARD_15"
        const val CHANNEL = "quackify-playback"
        const val NOTIFICATION_ID = 1
    }
}
