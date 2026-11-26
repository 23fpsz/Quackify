package com.quackify.app.widget

import android.content.Context
import com.quackify.app.PlaybackBridge
import com.quackify.backend.library.Krespot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Album name for the widget's third line. Playback state doesn't carry it when this device
 * plays (tracks are published with an empty album title), so it falls back to a one-off
 * album lookup, cached per album so a whole album costs one request.
 */
object WidgetAlbumNames {

    private const val MAX_CACHED = 32

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // A failed lookup is stored as "" so it isn't retried on every widget tick.
    private val names = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(MAX_CACHED, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > MAX_CACHED
        },
    )

    private val inFlight = mutableSetOf<String>()

    /** Album name for the current track, or "" while unknown (a widget refresh follows the lookup). */
    fun nameFor(context: Context, bridge: PlaybackBridge): String {
        val state = bridge.state
        bridge.albumTitleFor(state.trackUri).takeIf { it.isNotBlank() }?.let { return it }

        val albumUri = state.albumUri
        if (albumUri.isBlank()) return ""
        if (state.queueContext == albumUri && state.contextLabel.isNotBlank()) return state.contextLabel

        names[albumUri]?.let { return it }
        lookup(context.applicationContext, albumUri)
        return ""
    }

    private fun lookup(context: Context, albumUri: String) {
        synchronized(inFlight) {
            if (!inFlight.add(albumUri)) return
        }
        scope.launch {
            val title = runCatching { Krespot.albums.album(context, albumUri)?.title }.getOrNull().orEmpty()
            names[albumUri] = title
            synchronized(inFlight) { inFlight.remove(albumUri) }
            if (title.isNotBlank()) WidgetSync.refreshSoon(context)
        }
    }
}
