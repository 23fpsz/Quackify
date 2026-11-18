package com.quackify.compose.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quackify.backend.core.store.AppPrefs
import com.quackify.backend.library.lyrics.LyricsProvider

object LyricsOptions {

    private const val PREFS = "quackify.client"

    private const val KEY_ENABLED = "lyricsEnabled"

    private const val KEY_PREVIEW = "lyricsPreviewEnabled"

    private const val KEY_PRIORITIZE_WORD_SYNCED = "lyricsPrioritizeWordSynced"

    private const val KEY_PROVIDER_ORDER = "lyricsProviderOrder"

    var enabled by mutableStateOf(true)
        private set

    var preview by mutableStateOf(true)
        private set

    var prioritizeWordSynced by mutableStateOf(true)
        private set

    var providerOrder by mutableStateOf(LyricsProvider.offered)
        private set

    fun load(context: Context) {
        val prefs = AppPrefs.prefs(context, PREFS)
        enabled = prefs.getBoolean(KEY_ENABLED, true)
        preview = prefs.getBoolean(KEY_PREVIEW, true)
        prioritizeWordSynced = prefs.getBoolean(KEY_PRIORITIZE_WORD_SYNCED, true)
        providerOrder = readProviderOrder(prefs.getString(KEY_PROVIDER_ORDER, null))
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_ENABLED, value).apply()
    }

    fun setPreview(context: Context, value: Boolean) {
        preview = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_PREVIEW, value).apply()
    }

    fun setPrioritizeWordSynced(context: Context, value: Boolean) {
        prioritizeWordSynced = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_PRIORITIZE_WORD_SYNCED, value).apply()
    }

    fun setProviderOrder(context: Context, providers: List<LyricsProvider>) {
        providerOrder = LyricsProvider.ordered(providers)
        AppPrefs.edit(context, PREFS)
            .putString(KEY_PROVIDER_ORDER, providerOrder.joinToString(",") { it.name })
            .apply()
    }

    private fun readProviderOrder(saved: String?): List<LyricsProvider> {
        if (saved.isNullOrBlank()) return LyricsProvider.offered
        val parsed = saved.split(',').mapNotNull { name ->
            LyricsProvider.entries.firstOrNull { it.name == name.trim() }
        }
        if (parsed.isEmpty()) return LyricsProvider.offered
        return LyricsProvider.ordered(parsed)
    }
}
