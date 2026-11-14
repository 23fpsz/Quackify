package com.quackify.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.quackify.app.PlaybackHost

class WidgetPlaybackAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ACTION_KEY] ?: return
        val bridge = PlaybackHost.ensure(context)
        when (action) {
            ACTION_TOGGLE -> bridge.listener.onPlayPause()
            ACTION_NEXT -> bridge.listener.onSkipNext()
            ACTION_PREVIOUS -> bridge.listener.onSkipPrevious()
            ACTION_LIKE -> bridge.listener.onLikeToggle()
        }
        WidgetSync.start(context)
        WidgetSync.refreshSoon(context)
    }

    companion object {
        val ACTION_KEY = ActionParameters.Key<String>("quackify.widget.action")
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
        const val ACTION_LIKE = "like"
    }
}
