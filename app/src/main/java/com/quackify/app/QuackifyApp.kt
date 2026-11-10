package com.quackify.app

import android.app.Application
import android.os.SystemClock
import com.quackify.backend.core.auth.SecurePrefs
import com.quackify.backend.core.log.Logs

/**
 * Process entry point, registered in the manifest so it runs before the
 * activity and the playback service alike — whichever component the system
 * resurrects first, the one-time work below has already started.
 */
class QuackifyApp : Application() {

    private val log = Logs.of(Logs.Module.MAIN)

    override fun onCreate() {
        super.onCreate()
        // Honor the "Logging" toggle from the log viewer before anything
        // else writes — the warmup thread below logs, and a paused capture
        // must stay paused across restarts. Fresh installs default to off:
        // no capture until the viewer's Logging chip is switched on.
        Logs.capturing = getSharedPreferences(Logs.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(Logs.CAPTURE_PREF_KEY, false)
        // The Keystore handshake behind EncryptedSharedPreferences is slow,
        // and every entry into the app reads the store almost immediately:
        // the activity's first composition asks SpotifyAuth.isLoggedIn(),
        // the service's bridge mints its connect device id. Warming it here
        // on a background thread means those first reads usually find the
        // store already built instead of paying the cost mid-frame. If the
        // store is still building when a reader lands, the reader waits on
        // SecurePrefs' own lock — exactly what the lazy path did before —
        // so no path gets slower.
        val startedAt = SystemClock.elapsedRealtime()
        Thread {
            SecurePrefs.warmup(this@QuackifyApp)
            log.i("secure prefs warmed in ${SystemClock.elapsedRealtime() - startedAt}ms")
        }.apply { name = "SecurePrefsWarmup" }.start()
    }
}
