package com.quackify.backend.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private const val FILE = "quackify_secure"

    // The store is now built from more than one thread: QuackifyApp warms it
    // up on a background thread while the main thread races toward its first
    // read. A plain field could hand the reader a half-published instance, so
    // the cache is volatile and creation happens under a lock — the same
    // double-checked shape PlaybackHost.ensure() already uses.
    @Volatile
    private var cached: SharedPreferences? = null

    private val gate = Any()

    /**
     * Builds the encrypted store ahead of the first reader. The Keystore
     * handshake (master key + keybox) is the expensive part — worst on the
     * very first run, when the master key has to be cut — and until now every
     * reader paid it lazily on whatever thread got there first, which on the
     * UI path was mid-composition. Calling this from Application.onCreate
     * lets a background thread pay the cost while the first frame is still
     * being drawn. A reader that arrives before the store is ready simply
     * waits on the same lock the lazy path always used, so it can never be
     * slower than the old behavior.
     */
    fun warmup(context: Context) {
        get(context)
    }

    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        val app = context.applicationContext
        return synchronized(gate) {
            cached ?: create(app).also { prefs ->
                cached = prefs
            }
        }
    }

    /** Keystore work plus the encrypted file's first open; slow, off-main-thread only. */
    private fun create(app: Context): SharedPreferences =
        runCatching {
            val key = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
}
