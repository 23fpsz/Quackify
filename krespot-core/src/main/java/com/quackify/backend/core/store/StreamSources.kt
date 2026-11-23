package com.quackify.backend.core.store

import android.content.Context

enum class StreamSource(val key: String) {
    SPOTIFY("spotify"),
    YT_MUSIC("ytmusic"),
}

object StreamSources {

    const val STORE = "quackify.client"

    const val ORDER_KEY = "streamSourceOrder"

    private val DEFAULT_ORDER = listOf(StreamSource.SPOTIFY, StreamSource.YT_MUSIC)

    fun order(context: Context): List<StreamSource> {
        val saved = AppPrefs.prefs(context, STORE).getString(ORDER_KEY, "").orEmpty()
        val parsed = saved.split(',').mapNotNull { token ->
            StreamSource.entries.firstOrNull { it.key == token.trim() }
        }.distinct()
        if (parsed.isEmpty()) return DEFAULT_ORDER
        return parsed + DEFAULT_ORDER.filterNot { it in parsed }
    }

    fun save(context: Context, order: List<StreamSource>) {
        val keys = order.distinct().joinToString(",") { it.key }
        AppPrefs.edit(context, STORE).putString(ORDER_KEY, keys).apply()
    }

    fun after(context: Context, source: StreamSource): List<StreamSource> {
        val order = order(context)
        val index = order.indexOf(source)
        if (index < 0) return emptyList()
        return order.subList(index + 1, order.size)
    }
}
