package com.spotiduck.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.compose.shell.ComposePlaybackState
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
 * after the session, with play/pause/previous/next controls, the track's
 * artwork and a seekbar extrapolated from the playback state. Everything the
 * controls do is routed straight into the bridge's listener, so the
 * notification can never disagree with the on-screen player.
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

    override fun onCreate() {
        super.onCreate()
        channel()
        val bridge = PlaybackHost.ensure(this)
        PlaybackHost.markForeground()
        setUpSession(bridge)
        startForeground(NOTIFICATION_ID, buildNotification(bridge.state))
        watch(bridge)
        log.i("service created with a media session, the app owns nothing once it leaves recents")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                log.i("stop requested, leaving the foreground")
                stopSelf()
                return START_NOT_STICKY
            }
            // Pre-13 notification buttons arrive here; 13+ goes through the
            // session callback. Both end up in the same bridge calls.
            ACTION_TOGGLE -> PlaybackHost.bridge?.listener?.onPlayPause()
            ACTION_NEXT -> PlaybackHost.bridge?.listener?.onSkipNext()
            ACTION_PREVIOUS -> PlaybackHost.bridge?.listener?.onSkipPrevious()
        }
        PlaybackHost.ensure(this)
        PlaybackHost.markForeground()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        log.i("task removed, the app is gone from recents")
        PlaybackHost.teardown(this)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        log.i("service destroyed")
        watcher?.cancel()
        artJob?.cancel()
        scope.cancel()
        session?.isActive = false
        session?.release()
        session = null
        PlaybackHost.release()
        super.onDestroy()
    }

    private fun channel() {
        // Notification channels only exist from API 26; on 24/25 the
        // compat builder posts straight to the legacy notification path.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun setUpSession(bridge: PlaybackBridge) {
        val session = MediaSessionCompat(this, "SpotiDuck")
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

            override fun onStop() {
                // The user swiped the media card away while paused.
                log.i("session stopped, leaving the foreground")
                stopSelf()
            }
        })
        // Android 15+ refuses a mediaPlayback startForeground() without an
        // active session, so this goes live before the notification does;
        // a STATE_NONE playback state keeps idle sessions out of the system's
        // media carousel.
        session.isActive = true
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0L, 0.0f)
                .build()
        )
    }

    private fun watch(bridge: PlaybackBridge) {
        watcher = scope.launch {
            snapshotFlow { bridge.state }.collect { state -> apply(state) }
        }
    }

    private fun apply(state: ComposePlaybackState) {
        val hasTrack = state.trackUri.isNotBlank() || state.title.isNotBlank()
        val session = session ?: return
        updatePlaybackState(session, state, hasTrack)
        val identity = "${state.trackUri}|${state.title}|${state.artist}|" +
            "${state.coverUrl}|${state.isPlaying}|" +
            "${state.contextLabel}|${art != null}"
        if (identity == lastIdentity) return
        lastIdentity = identity
        if (hasTrack) {
            setMetadata(session, state)
            maybeLoadArt(state.coverUrl)
        }
        notify(buildNotification(state))
    }

    /**
     * Position, duration and the play state ride the session's playback
     * state; the system extrapolates the seekbar between updates, so this is
     * cheap to call at the bridge's ~1Hz without touching the notification.
     */
    private fun updatePlaybackState(session: MediaSessionCompat, state: ComposePlaybackState, hasTrack: Boolean) {
        if (!hasTrack) {
            // Idle: STATE_NONE keeps the live-but-empty session out of the
            // system carousel until a track actually lands.
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0L, 0.0f)
                    .build()
            )
            return
        }
        val playing = state.isPlaying
        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        if (state.remoteCanSeek) {
            actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    state.positionMs,
                    if (playing) 1.0f else 0.0f,
                )
                .build()
        )
    }

    private fun setMetadata(session: MediaSessionCompat, state: ComposePlaybackState) {
        val art = art
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title.ifBlank { "Unknown track" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist.ifBlank { "Unknown artist" })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
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
            if (bitmap == null) {
                artRefused = url
                log.w("cover '${url.takeLast(28)}' could not be decoded, the notification stays bare")
                return@launch
            }
            art = bitmap
            val state = PlaybackHost.bridge?.state ?: return@launch
            val session = session
            if (session != null) {
                setMetadata(session, state)
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
            .setSmallIcon(R.drawable.ic_stat_media)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (!hasTrack) {
            return builder
                .setContentTitle("SpotiDuck")
                .setContentText("Nothing playing")
                .build()
        }
        builder
            .setContentTitle(state.title.ifBlank { "Unknown track" })
            .setContentText(state.artist.ifBlank { "Unknown artist" })
            .setLargeIcon(art)
        if (state.contextLabel.isNotBlank()) {
            builder.setSubText(state.contextLabel)
        }
        builder
            .addAction(R.drawable.ic_notif_previous, "Previous", servicePending(ACTION_PREVIOUS))
            .addAction(
                if (state.isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                if (state.isPlaying) "Pause" else "Play",
                servicePending(ACTION_TOGGLE),
            )
            .addAction(R.drawable.ic_notif_next, "Next", servicePending(ACTION_NEXT))
        builder.setStyle(
            MediaNotificationCompat.MediaStyle()
                .setMediaSession(session?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        return builder.build()
    }

    private fun contentIntent(): PendingIntent {
        val open = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_STOP = "com.spotiduck.app.STOP"
        private const val ACTION_TOGGLE = "com.spotiduck.app.TOGGLE"
        private const val ACTION_NEXT = "com.spotiduck.app.NEXT"
        private const val ACTION_PREVIOUS = "com.spotiduck.app.PREVIOUS"
        const val CHANNEL = "spotiduck-playback"
        const val NOTIFICATION_ID = 1
    }
}
