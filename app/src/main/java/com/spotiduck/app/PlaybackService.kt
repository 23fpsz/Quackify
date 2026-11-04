package com.spotiduck.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.spotiduck.backend.core.log.Logs

class PlaybackService : Service() {

    override fun onCreate() {
        super.onCreate()
        channel()
        PlaybackHost.ensure(this)
        PlaybackHost.markForeground()
        startForeground(NOTIFICATION_ID, notification())
        log.i("service created, the app owns nothing once it leaves recents")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            log.i("stop requested, leaving the foreground")
            stopSelf()
            return START_NOT_STICKY
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
        PlaybackHost.release()
        super.onDestroy()
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setContentTitle("SpotiDuck")
        .setContentText("Reproduciendo")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        const val ACTION_STOP = "com.spotiduck.app.STOP"
        private val log = Logs.of(Logs.Module.SERVICE)
        const val CHANNEL = "spotiduck-playback"
        const val NOTIFICATION_ID = 1
    }
}
