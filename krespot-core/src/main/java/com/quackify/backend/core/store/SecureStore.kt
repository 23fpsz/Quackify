package com.quackify.backend.core.store

import android.content.Context
import android.content.SharedPreferences

object SecureStore {

    private const val PREFS = "quackify_secure"

    @Volatile
    private var cache: MutableMap<String, SecureEntry>? = null

    private val lock = Any()

    fun getString(context: Context, key: String, default: String? = null): String? {
        val entry = ensure(context)[key] ?: return default
        return entry.stringValue ?: default
    }

    fun getLong(context: Context, key: String, default: Long = 0L): Long {
        val entry = ensure(context)[key] ?: return default
        return entry.longValue ?: default
    }

    fun putString(context: Context, key: String, value: String?) {
        if (value == null) {
            remove(context, key)
            return
        }
        val entry = SecureEntry(key = key, stringValue = value)
        synchronized(lock) {
            ensure(context)[key] = entry
            db(context).dao().put(entry)
        }
    }

    fun putLong(context: Context, key: String, value: Long) {
        val entry = SecureEntry(key = key, longValue = value)
        synchronized(lock) {
            ensure(context)[key] = entry
            db(context).dao().put(entry)
        }
    }

    fun remove(context: Context, key: String) {
        synchronized(lock) {
            ensure(context).remove(key)
            db(context).dao().remove(key)
        }
    }

    fun edit(context: Context): Editor = Editor(context.applicationContext)

    fun prefs(context: Context): SecurePrefsCompat = SecurePrefsCompat(context.applicationContext)

    private fun db(context: Context): SecureStoreDb = SecureStoreDb.get(context.applicationContext)

    private fun ensure(context: Context): MutableMap<String, SecureEntry> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val app = context.applicationContext
            val database = db(app)
            migrate(app, database)
            val map = database.dao().all().associateBy { it.key }.toMutableMap()
            cache = map
            return map
        }
    }

    private fun migrate(context: Context, database: SecureStoreDb) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        if (all.isEmpty()) return
        val dao = database.dao()
        for ((key, value) in all) {
            val entry = when (value) {
                is String -> SecureEntry(key = key, stringValue = value)
                is Long -> SecureEntry(key = key, longValue = value)
                is Int -> SecureEntry(key = key, longValue = value.toLong())
                is Boolean -> SecureEntry(key = key, stringValue = value.toString())
                is Float -> SecureEntry(key = key, stringValue = value.toString())
                else -> null
            }
            if (entry != null) dao.put(entry)
        }
        prefs.edit().clear().apply()
    }

    class Editor(private val context: Context) {
        private val ops = mutableListOf<() -> Unit>()

        fun putString(key: String, value: String?): Editor {
            ops.add { putString(context, key, value) }
            return this
        }

        fun putLong(key: String, value: Long): Editor {
            ops.add { putLong(context, key, value) }
            return this
        }

        fun remove(key: String): Editor {
            ops.add { remove(context, key) }
            return this
        }

        fun clear(): Editor {
            ops.add {
                synchronized(lock) {
                    ensure(context).clear()
                    db(context).dao().clear()
                }
            }
            return this
        }

        fun apply() {
            for (op in ops) op()
        }

        fun commit(): Boolean {
            apply()
            return true
        }
    }

    class SecurePrefsCompat(private val context: Context) {
        fun getString(key: String, default: String?): String? = getString(context, key, default)
        fun getLong(key: String, default: Long): Long = getLong(context, key, default)
        fun edit(): Editor = edit(context)
    }
}
