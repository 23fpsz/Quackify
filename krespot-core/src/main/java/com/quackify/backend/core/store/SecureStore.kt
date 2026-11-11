package com.quackify.backend.core.store

import android.content.Context

object SecureStore {

    private const val STORE = "quackify_secure"

    fun prefs(context: Context): AppPrefs.Prefs = AppPrefs.prefs(context, STORE)

    fun edit(context: Context): AppPrefs.Editor = AppPrefs.edit(context, STORE)

    fun getString(context: Context, key: String, default: String? = null): String? =
        AppPrefs.getString(context, STORE, key, default)

    fun getLong(context: Context, key: String, default: Long = 0L): Long =
        AppPrefs.getLong(context, STORE, key, default)
}
