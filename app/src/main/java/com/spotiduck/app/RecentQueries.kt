package com.spotiduck.app

import android.content.Context
import org.json.JSONArray

object RecentQueries {

    private const val PREFS = "spotiduck.client"
    private const val KEY = "recentQueries"
    private const val LIMIT = 20

    fun all(context: Context): List<String> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) out += value
        }
        return out
    }

    fun record(context: Context, term: String) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        val next = ArrayList<String>(LIMIT)
        next += clean
        all(context).filterNot { it.equals(clean, ignoreCase = true) }.take(LIMIT - 1).forEach(next::add)
        write(context, next)
    }

    fun remove(context: Context, term: String) {
        write(context, all(context).filterNot { it == term })
    }

    fun clear(context: Context) {
        write(context, emptyList())
    }

    private fun write(context: Context, terms: List<String>) {
        val array = JSONArray()
        terms.forEach(array::put)
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
