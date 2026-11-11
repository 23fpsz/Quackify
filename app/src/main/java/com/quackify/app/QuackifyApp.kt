package com.quackify.app

import android.app.Application
import com.quackify.backend.core.log.Logs

class QuackifyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Logs.capturing = com.quackify.backend.core.store.AppPrefs.prefs(this, Logs.PREFS_NAME)
            .getBoolean(Logs.CAPTURE_PREF_KEY, false)
    }
}