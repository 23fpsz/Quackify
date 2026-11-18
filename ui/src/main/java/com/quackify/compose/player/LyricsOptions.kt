package com.quackify.compose.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quackify.backend.core.store.AppPrefs
import com.quackify.backend.library.lyrics.LyricsProvider
import java.util.Locale

/** Sentinel for "translate into whatever language the device is using". */
const val TRANSLATION_DEVICE_LANGUAGE = "device"

object LyricsOptions {

    private const val PREFS = "quackify.client"

    private const val KEY_ENABLED = "lyricsEnabled"

    private const val KEY_PREVIEW = "lyricsPreviewEnabled"

    private const val KEY_PRIORITIZE_WORD_SYNCED = "lyricsPrioritizeWordSynced"

    private const val KEY_PROVIDER_ORDER = "lyricsProviderOrder"

    private const val KEY_TRANSLATION_ENABLED = "lyricsTranslationEnabled"

    private const val KEY_TRANSLATION_LANGUAGE = "lyricsTranslationLanguage"

    var enabled by mutableStateOf(true)
        private set

    var preview by mutableStateOf(true)
        private set

    var prioritizeWordSynced by mutableStateOf(true)
        private set

    /** "device" follows the device locale; anything else is a language code. */
    var translationLanguage by mutableStateOf(TRANSLATION_DEVICE_LANGUAGE)
        private set

    var translationEnabled by mutableStateOf(false)
        private set

    var providerOrder by mutableStateOf(LyricsProvider.offered)
        private set

    fun load(context: Context) {
        val prefs = AppPrefs.prefs(context, PREFS)
        enabled = prefs.getBoolean(KEY_ENABLED, true)
        preview = prefs.getBoolean(KEY_PREVIEW, true)
        prioritizeWordSynced = prefs.getBoolean(KEY_PRIORITIZE_WORD_SYNCED, true)
        providerOrder = readProviderOrder(prefs.getString(KEY_PROVIDER_ORDER, null))
        translationEnabled = prefs.getBoolean(KEY_TRANSLATION_ENABLED, false)
        translationLanguage = prefs.getString(KEY_TRANSLATION_LANGUAGE, null)
            ?.takeIf { it.isNotBlank() }
            ?: TRANSLATION_DEVICE_LANGUAGE
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

    fun setTranslationEnabled(context: Context, value: Boolean) {
        translationEnabled = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_TRANSLATION_ENABLED, value).apply()
    }

    fun setTranslationLanguage(context: Context, code: String) {
        translationLanguage = code
        AppPrefs.edit(context, PREFS).putString(KEY_TRANSLATION_LANGUAGE, code).apply()
    }

    /**
     * The language tag lyrics should be translated into right now: the saved
     * code, or the device locale's tag when the user chose to follow it.
     */
    fun translationTargetTag(context: Context): String {
        if (translationLanguage != TRANSLATION_DEVICE_LANGUAGE) return translationLanguage
        val locales = context.resources.configuration.locales
        val tag = if (!locales.isEmpty) locales[0].toLanguageTag() else Locale.getDefault().toLanguageTag()
        return tag.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) } ?: "en"
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
