package com.quackify.compose.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quackify.backend.core.store.AppPrefs

/**
 * Layout choices for the now playing screen. The landscape dual view keeps
 * the app content beside the player as a side pane; turning it off makes the
 * expanded player cover the whole screen instead.
 */
object PlayerViewOptions {

    private const val PREFS = "quackify.client"

    private const val KEY_LANDSCAPE_DUAL = "landscapeDualViewEnabled"

    var landscapeDualView by mutableStateOf(true)
        private set

    fun load(context: Context) {
        landscapeDualView = AppPrefs.prefs(context, PREFS).getBoolean(KEY_LANDSCAPE_DUAL, true)
    }

    fun setLandscapeDualView(context: Context, value: Boolean) {
        landscapeDualView = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_LANDSCAPE_DUAL, value).apply()
    }
}
