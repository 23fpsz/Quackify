package com.quackify.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.quackify.app.auto.AutoMediaTree
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.ConnectivityMonitor
import com.quackify.app.widget.WidgetSync
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.resolveRemoteImageUrl
import com.quackify.compose.playlist.TrackItem
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
 * Implements MediaBrowserServiceCompat to power the full Android Auto and
 * automotive head-unit experience with dynamic Home, Recents, Library, and Browse.
 */
class PlaybackService : MediaBrowserServiceCompat() {

    private val log = Logs.of(Logs.Module.SERVICE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var session: MediaSessionCompat? = null

    private var watcher: Job? = null

    private var lastFmScrobbler: com.quackify.backend.core.lastfm.LastFmPlaybackScrobbler? = null

    /** Artwork for the current track and cover URL. Stored together so stale bitmaps never leak. */
    private var currentArtTrackUri = ""
    private var currentArtUrl = ""
    private var currentArt: Bitmap? = null
    private var artRefused = ""
    private var artJob: Job? = null

    /** Identity of the last posted notification; position ticks must not repost. */
    private var lastIdentity = ""

    private val autoMediaTree by lazy { AutoMediaTree(this) }

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
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(bridge.state), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(bridge.state))
            }
        }.onFailure {
            log.w("startForeground skipped in background/bind mode (${it.message}), service remaining active for Android Auto")
        }
        watch(bridge)
        log.i("service created with a media session, the app owns nothing once it leaves recents")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            log.w("restarted by the system with null intent, keeping session active")
            return START_STICKY
        }
        val session = session
        if (session != null && Intent.ACTION_MEDIA_BUTTON == intent.action) {
            MediaButtonReceiver.handleIntent(session, intent)
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
        log.i("task removed, checking playback state")
        val isPlaying = PlaybackHost.bridge?.state?.isPlaying == true
        if (isPlaying) {
            log.i("playback active, keeping service alive for background/auto")
            return
        }
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

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

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
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)
        session.setSessionActivity(contentIntent())
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                log.i("onPlay received")
                PlaybackHost.startService(this@PlaybackService)
                if (bridge.state.trackUri.isNotBlank()) {
                    if (!bridge.state.isPlaying) bridge.listener.onPlayPause()
                } else {
                    bridge.listener.onPlayUri("spotify:collection:tracks")
                }
            }

            override fun onPause() {
                log.i("onPause received")
                if (bridge.state.isPlaying) bridge.listener.onPlayPause()
            }

            override fun onSkipToNext() {
                log.i("onSkipToNext received")
                PlaybackHost.startService(this@PlaybackService)
                bridge.listener.onSkipNext()
            }

            override fun onSkipToPrevious() {
                log.i("onSkipToPrevious received")
                PlaybackHost.startService(this@PlaybackService)
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

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                log.i("onPlayFromMediaId received: mediaId=$mediaId")
                PlaybackHost.startService(this@PlaybackService)
                session?.let { s ->
                    val playback = PlaybackStateCompat.Builder()
                        .setActions(
                            PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP
                        )
                        .setState(PlaybackStateCompat.STATE_BUFFERING, 0L, 1.0f, SystemClock.elapsedRealtime())
                        .build()
                    s.setPlaybackState(playback)
                }
                if (!mediaId.isNullOrBlank()) {
                    if (mediaId.startsWith("spotify:track:") || mediaId.startsWith("spotify:episode:")) {
                        val extraCtx = extras?.getString("com.spotify.music.extra.CONTEXT_URI")
                        val rawCtx = extraCtx?.takeIf { it.isNotBlank() } ?: autoMediaTree.lastBrowsedContext.takeIf { it.isNotBlank() } ?: ""
                        val validCtx = rawCtx.takeIf {
                            it.startsWith("spotify:playlist:") ||
                            it.startsWith("spotify:album:") ||
                            it.startsWith("spotify:artist:") ||
                            it.startsWith("spotify:show:") ||
                            it.startsWith("spotify:collection:") ||
                            it.startsWith("spotify:user:")
                        }
                        if (!validCtx.isNullOrBlank()) {
                            bridge.listener.onPlayTrack(mediaId, validCtx, -1)
                        } else {
                            bridge.listener.onPlayUri(mediaId)
                        }
                    } else {
                        bridge.listener.onPlayUri(mediaId)
                    }
                } else {
                    onPlay()
                }
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                log.i("onPlayFromSearch received: query=$query")
                PlaybackHost.startService(this@PlaybackService)
                if (!query.isNullOrBlank()) {
                    bridge.listener.onPlayUri(query)
                } else {
                    onPlay()
                }
            }

            override fun onSkipToQueueItem(queueId: Long) {
                val idx = queueId.toInt()
                val queue = bridge.state.queue
                if (idx in queue.indices) {
                    val track = queue[idx]
                    bridge.listener.onPlayTrack(track.uri, bridge.state.contextUri, idx)
                }
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
        sessionToken = session.sessionToken
        session.setExtras(Bundle().apply {
            putBoolean("android.media.session.extra.SLOT_RESERVATION_SKIP_TO_PREV", true)
            putBoolean("android.media.session.extra.SLOT_RESERVATION_SKIP_TO_NEXT", true)
        })
        val hasTrack = bridge.state.trackUri.isNotBlank() || bridge.state.title.isNotBlank()
        updatePlaybackState(session, bridge.state, bridge.progressSnapshot, hasTrack)
        setMetadata(session, bridge.state, bridge.progressSnapshot)
        if (hasTrack) {
            val cover = bridge.state.coverUrl.ifBlank { bridge.coverUrlFor(bridge.state.trackUri) }
            maybeLoadArt(bridge.state.trackUri, cover)
        }
    }

    private fun promoteForeground(state: ComposePlaybackState) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(state), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(state))
            }
        }.onFailure {
            log.w("promoteForeground skipped: ${it.message}")
        }
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

        // Last.fm playback integration. Missing credentials/session never
        // interrupts normal playback.
        if (
            BuildConfig.LASTFM_API_KEY.isNotBlank() &&
            BuildConfig.LASTFM_SHARED_SECRET.isNotBlank()
        ) {
            runCatching {
                val scrobbler = lastFmScrobbler
                    ?: com.quackify.backend.core.lastfm.LastFmPlaybackScrobbler(
                        applicationContext,
                        com.quackify.backend.core.lastfm.LastFmApiClient(
                            BuildConfig.LASTFM_API_KEY,
                            BuildConfig.LASTFM_SHARED_SECRET
                        ),
                        scope
                    ).also { lastFmScrobbler = it }

                val track = if (
                    state.trackUri.startsWith("spotify:track:")
                ) {
                    com.quackify.backend.core.lastfm.LastFmTrack(
                        identity = state.trackUri,
                        title = state.title,
                        artist = state.artist,
                        durationMs = progress.durationMs
                    )
                } else {
                    null
                }

                scrobbler.onPlaybackUpdate(
                    track = track,
                    isPlaying = state.isPlaying,
                    positionMs = progress.positionMs,
                    sessionKey = com.quackify.backend.core.lastfm.LastFmSecureStore
                        .getSessionKey(applicationContext)
                )
            }.onFailure {
                log.w("Last.fm playback integration failed: ${it.message}")
            }
        }

        val hasTrack = state.trackUri.isNotBlank() || state.title.isNotBlank()
        val session = session ?: return
        log.d("apply: track='${state.title}' playing=${state.isPlaying} buffering=${state.isBuffering} pos=${progress.positionMs}/${progress.durationMs}")
        updatePlaybackState(session, state, progress, hasTrack)

        val queueItems = state.queue.take(50).mapIndexed { index, track ->
            val desc = MediaDescriptionCompat.Builder()
                .setMediaId(track.uri)
                .setTitle(track.title)
                .setSubtitle(track.artist)
                .apply {
                    val img = resolveRemoteImageUrl(track.imageUrl?.takeIf { it.isNotBlank() })
                    if (img != null) setIconUri(android.net.Uri.parse(img))
                }
                .build()
            MediaSessionCompat.QueueItem(desc, index.toLong())
        }
        session.setQueue(queueItems)

        val isEpisode = state.trackUri.startsWith("spotify:episode:") || state.contextUri.startsWith("spotify:show:")
        val effectiveCover = state.coverUrl.ifBlank { PlaybackHost.bridge?.coverUrlFor(state.trackUri).orEmpty() }
        val resolvedCover = resolveRemoteImageUrl(effectiveCover).orEmpty()
        val hasArt = currentArt != null && currentArtTrackUri == state.trackUri && currentArtUrl == resolvedCover
        val identity = "${state.trackUri}|${state.title}|${state.artist}|" +
            "$resolvedCover|${state.isPlaying}|" +
            "${state.contextLabel}|$hasArt|" +
            "${state.isLiked}|${state.shuffleMode}|${state.repeatMode}|$isEpisode"
        if (identity == lastIdentity) return
        lastIdentity = identity
        log.i("setMetadata: title='${state.title}' artist='${state.artist}' playing=${state.isPlaying}")
        setMetadata(session, state, progress)
        if (hasTrack) {
            maybeLoadArt(state.trackUri, resolvedCover)
        }
        if (state.isPlaying) {
            promoteForeground(state)
            if (hasTrack && state.trackUri.isNotBlank()) {
                runCatching {
                    PlayHistory.record(
                        applicationContext,
                        TrackItem(
                            title = state.title,
                            artist = state.artist,
                            uri = state.trackUri,
                            imageUrl = state.coverUrl.takeIf { it.isNotBlank() },
                            artistUri = state.artistUri,
                            albumUri = state.albumUri,
                            isExplicit = state.explicit,
                            durationMs = progress.durationMs,
                        ),
                        System.currentTimeMillis(),
                    )
                }
                val ctxUri = state.contextUri
                if (ctxUri.startsWith("spotify:playlist:")) {
                    val title = state.contextLabel.ifBlank { "Playlist" }
                    PlayStats.tallyPlaylist(applicationContext, ctxUri, title)
                }
            }
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
        val playing = state.isPlaying
        val isEpisode = state.trackUri.startsWith("spotify:episode:") || state.contextUri.startsWith("spotify:show:")
        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
            PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
            PlaybackStateCompat.ACTION_STOP
        if (state.remoteCanSeek) {
            actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
        }
        if (isEpisode) {
            actions = actions or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD
        }
        val playback = PlaybackStateCompat.Builder()
            .setActions(actions)

        val activeIdx = state.queue.indexOfFirst { it.uri == state.trackUri }
        if (activeIdx >= 0) {
            playback.setActiveQueueItemId(activeIdx.toLong())
        }

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
        val playbackState = when {
            state.isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            playing -> PlaybackStateCompat.STATE_PLAYING
            hasTrack -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_NONE
        }
        val speed = if (playing && !state.isBuffering) state.playbackSpeed else 0.0f
        playback.setState(
            playbackState,
            progress.positionMs,
            speed,
            SystemClock.elapsedRealtime(),
        )
        session.setPlaybackState(playback.build())
    }

    private fun setMetadata(
        session: MediaSessionCompat,
        state: ComposePlaybackState,
        progress: PlaybackProgressSnapshot,
    ) {
        val title = state.title.ifBlank { "Quackify" }
        val artist = state.artist.ifBlank { "Ready to play" }
        val album = state.contextLabel.ifBlank { state.artist }
        val duration = if (progress.durationMs > 0L) progress.durationMs else -1L

        val effectiveCover = state.coverUrl.ifBlank { PlaybackHost.bridge?.coverUrlFor(state.trackUri).orEmpty() }
        val resolvedCover = resolveRemoteImageUrl(effectiveCover).orEmpty()
        val matchingArt = if (currentArt != null && currentArtTrackUri == state.trackUri && currentArtUrl == resolvedCover) {
            currentArt
        } else {
            null
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, state.trackUri)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        if (resolvedCover.isNotBlank()) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, resolvedCover)
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, resolvedCover)
            metadata.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, resolvedCover)
        }
        if (matchingArt != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, matchingArt)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, matchingArt)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, matchingArt)
        }
        session.setMetadata(metadata.build())
    }

    private fun maybeLoadArt(trackUri: String, rawUrl: String) {
        val effective = rawUrl.ifBlank { PlaybackHost.bridge?.coverUrlFor(trackUri).orEmpty() }
        val target = resolveRemoteImageUrl(effective).orEmpty()
        if (trackUri.isBlank() || target.isBlank()) {
            artJob?.cancel()
            currentArtTrackUri = ""
            currentArtUrl = ""
            currentArt = null
            artRefused = ""
            return
        }
        if (currentArt != null && currentArtTrackUri == trackUri && currentArtUrl == target) return
        if (currentArtTrackUri == trackUri && currentArtUrl == target && artJob?.isActive == true) return
        if (target == artRefused) return

        currentArtTrackUri = trackUri
        currentArtUrl = target
        currentArt = null
        artJob?.cancel()
        artJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { fetchArt(target) }
            if (finished) return@launch
            if (bitmap == null) {
                artRefused = target
                log.w("cover '${target.takeLast(28)}' could not be decoded, the notification stays bare")
                return@launch
            }
            val currentBridge = PlaybackHost.bridge ?: return@launch
            val currentState = currentBridge.state
            val progress = currentBridge.progressSnapshot
            if (currentState.trackUri != trackUri) {
                log.i("cover decoded for '${trackUri.takeLast(12)}' but player is now at '${currentState.trackUri.takeLast(12)}', discarding")
                return@launch
            }
            currentArt = bitmap
            val session = session
            if (session != null) {
                setMetadata(session, currentState, progress)
            }
            lastIdentity = ""
            notify(buildNotification(currentState))
        }
    }

    /** Covers are public CDN images; ~512px is all a notification card wants. */
    private fun fetchArt(rawUrl: String): Bitmap? = runCatching {
        val resolved = resolveRemoteImageUrl(rawUrl) ?: return null
        val connection = URL(resolved).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
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
        val effectiveCover = state.coverUrl.ifBlank { PlaybackHost.bridge?.coverUrlFor(state.trackUri).orEmpty() }
        val resolvedCover = resolveRemoteImageUrl(effectiveCover).orEmpty()
        val matchingArt = if (currentArt != null && currentArtTrackUri == state.trackUri && currentArtUrl == resolvedCover) {
            currentArt
        } else {
            null
        }

        builder
            .setContentTitle(state.title.ifBlank { "Unknown track" })
            .setContentText(state.artist.ifBlank { "Unknown artist" })
            .setLargeIcon(matchingArt)
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

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? = autoMediaTree.getRoot(clientPackageName, clientUid, rootHints)

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        autoMediaTree.loadChildren(parentId, result, scope)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        autoMediaTree.search(query, extras, result, scope)
    }

    companion object {
        const val ROOT_ID = AutoMediaTree.ROOT_ID
        const val TAB_HOME = AutoMediaTree.TAB_HOME
        const val TAB_RECENTS = AutoMediaTree.TAB_RECENTS
        const val TAB_BROWSE = AutoMediaTree.TAB_BROWSE
        const val TAB_LIBRARY = AutoMediaTree.TAB_LIBRARY
        const val NODE_LIBRARY_PLAYLISTS = AutoMediaTree.NODE_LIBRARY_PLAYLISTS
        const val NODE_LIBRARY_ALBUMS = AutoMediaTree.NODE_LIBRARY_ALBUMS
        const val NODE_LIBRARY_ARTISTS = AutoMediaTree.NODE_LIBRARY_ARTISTS
        const val NODE_LIBRARY_PODCASTS = AutoMediaTree.NODE_LIBRARY_PODCASTS
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

