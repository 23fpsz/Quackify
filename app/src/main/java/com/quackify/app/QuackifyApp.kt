package com.quackify.app

import android.app.Application
import android.os.SystemClock
import com.quackify.backend.core.auth.SecurePrefs
import com.quackify.backend.core.log.Logs

class QuackifyApp : Application() {

    private val log = Logs.of(Logs.Module.MAIN)

    override fun onCreate() {
        super.onCreate()
        Logs.capturing = getSharedPreferences(Logs.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(Logs.CAPTURE_PREF_KEY, false)
        val startedAt = SystemClock.elapsedRealtime()
        Thread {
            SecurePrefs.warmup(this@QuackifyApp)
            log.i("secure prefs warmed in ${SystemClock.elapsedRealtime() - startedAt}ms")
        }.apply { name = "SecurePrefsWarmup" }.start()
    }
}