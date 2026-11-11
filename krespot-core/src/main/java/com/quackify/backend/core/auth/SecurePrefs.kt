package com.quackify.backend.core.auth

import android.content.Context
import android.content.SharedPreferences

object SecurePrefs {

    private const val FILE = "quackify_secure"

    @Volatile
    private var cached: SharedPreferences? = null

    fun warmup(context: Context) {
        get(context)
    }

    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        cached = prefs
        return prefs
    }
}
