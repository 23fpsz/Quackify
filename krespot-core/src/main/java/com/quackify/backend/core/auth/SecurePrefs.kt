package com.quackify.backend.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    private const val FILE = "quackify_secure"

    @Volatile
    private var cached: SharedPreferences? = null

    private val gate = Any()

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
