package com.quackify.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.quackify.app.PlaybackHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object WidgetSync {

    private const val ACTIVE_INTERVAL_MS = 400L
    private const val IDLE_INTERVAL_MS = 3000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        scope.launch {
            var last: WidgetSnapshot? = null
            while (isActive) {
                val bridge = PlaybackHost.bridge
                if (bridge == null) {
                    delay(IDLE_INTERVAL_MS)
                    continue
                }
                val snapshot = WidgetSnapshot.from(bridge.state, bridge.progress)
                if (snapshot.fingerprint() != last?.fingerprint()) {
                    last = snapshot
                    WidgetSnapshotStore.save(app, snapshot)
                    snapshot.coverUrl.takeIf { it.isNotBlank() }?.let { url ->
                        WidgetArt.prefetch(app, url) { pushNow(app) }
                    }
                    refresh(app)
                }
                delay(ACTIVE_INTERVAL_MS)
            }
        }
    }

    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        scope.launch {
            delay(250L)
            refresh(app)
        }
    }

    private fun pushNow(context: Context) {
        scope.launch { refresh(context) }
    }

    suspend fun refresh(context: Context) {
        QuackifyWidget4x1().updateAll(context)
        QuackifyWidget3x2().updateAll(context)
        QuackifyWidget3x3().updateAll(context)
    }
}
