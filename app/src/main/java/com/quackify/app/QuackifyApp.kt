package com.quackify.app

import android.app.Application
import com.quackify.backend.core.log.Logs

class QuackifyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Logs.capturing = getSharedPreferences(Logs.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(Logs.CAPTURE_PREF_KEY, false)
    }
}