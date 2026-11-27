package com.quackify.compose.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quackify.backend.core.store.AppPrefs
import com.quackify.backend.core.store.StreamSource
import com.quackify.backend.library.lyrics.LyricsProvider
import java.util.Locale

/** Sentinel for "translate into whatever language the device is using". */
const val TRANSLATION_DEVICE_LANGUAGE = "device"

/** Lyrics offset range: ±10 seconds, in tenths of a second. */
const val LYRICS_OFFSET_TENTHS_MIN = -100
const val LYRICS_OFFSET_TENTHS_MAX = 100

object LyricsOptions {

    private const val PREFS = "quackify.client"

    private const val KEY_ENABLED = "lyricsEnabled"

    private const val KEY_PREVIEW = "lyricsPreviewEnabled"

    private const val KEY_PRIORITIZE_WORD_SYNCED = "lyricsPrioritizeWordSynced"

    private const val KEY_PROVIDER_ORDER = "lyricsProviderOrder"

    private const val KEY_YTM_PROVIDER_ORDER = "lyricsYtmProviderOrder"

    private const val KEY_TRANSLATION_ENABLED = "lyricsTranslationEnabled"

    private const val KEY_TRANSLATION_LANGUAGE = "lyricsTranslationLanguage"

    /** Positive: lyrics run late, show them earlier. Stored in ms, shown in tenths of a second. */
    private const val KEY_OFFSET_TENTHS = "lyricsOffsetTenths"

    /** Screen stays awake while the fullscreen lyrics reader is open. */
    private const val KEY_KEEP_SCREEN_ON = "lyricsKeepScreenOn"

    private const val KEY_DEEP_FEEL = "lyricsDeepFeel"

    /** The fullscreen reader ends with a blurred sneak peek of the next track's opening lines. */
    private const val KEY_NEXT_TRACK_PEEK = "lyricsNextTrackPeek"

    private const val KEY_TRANSCRIPT_SMOOTH_SWEEP = "transcriptSmoothSweepEnabled"

    private const val KEY_TRANSCRIPT_OFFSET_TENTHS = "transcriptOffsetTenths"

    private const val KEY_TRANSCRIPT_TRANSLATION = "transcriptTranslationEnabled"

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

    /** The saved master chain; per-stream chains are filtered views of it. */
    var providerOrder by mutableStateOf(LyricsProvider.offered)
        private set

    /** The provider chain used while the audio streams from YouTube Music. */
    var ytmProviderOrder by mutableStateOf(LyricsProvider.offeredFor(StreamSource.YT_MUSIC))
        private set

    /**
     * How far off the vocal the lyrics feel, in tenths of a second. Positive
     * means lyrics arrive late, so the sweep is shifted earlier by that much.
     * The clamped range keeps one bad drag from throwing the sweep half a
     * minute away from the vocal.
     */
    var offsetTenths by mutableStateOf(0)
        private set

    var keepScreenOn by mutableStateOf(false)
        private set

    var deepFeelEnabled by mutableStateOf(false)
        private set

    var nextTrackPeek by mutableStateOf(true)
        private set

    var transcriptSmoothSweep by mutableStateOf(true)
        private set

    var transcriptOffsetTenths by mutableStateOf(0)
        private set

    var transcriptTranslationEnabled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        val prefs = AppPrefs.prefs(context, PREFS)
        enabled = prefs.getBoolean(KEY_ENABLED, true)
        preview = prefs.getBoolean(KEY_PREVIEW, true)
        prioritizeWordSynced = prefs.getBoolean(KEY_PRIORITIZE_WORD_SYNCED, true)
        providerOrder = readProviderOrder(prefs.getString(KEY_PROVIDER_ORDER, null))
        ytmProviderOrder = LyricsProvider.orderedFor(
            readProviderOrder(
                prefs.getString(KEY_YTM_PROVIDER_ORDER, null),
                LyricsProvider.offeredFor(StreamSource.YT_MUSIC),
            ),
            StreamSource.YT_MUSIC,
        )
        translationEnabled = prefs.getBoolean(KEY_TRANSLATION_ENABLED, false)
        translationLanguage = prefs.getString(KEY_TRANSLATION_LANGUAGE, null)
            ?.takeIf { it.isNotBlank() }
            ?: TRANSLATION_DEVICE_LANGUAGE
        offsetTenths = prefs.getInt(KEY_OFFSET_TENTHS, 0).coerceIn(LYRICS_OFFSET_TENTHS_MIN, LYRICS_OFFSET_TENTHS_MAX)
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        deepFeelEnabled = prefs.getBoolean(KEY_DEEP_FEEL, false)
        nextTrackPeek = prefs.getBoolean(KEY_NEXT_TRACK_PEEK, true)
        transcriptSmoothSweep = prefs.getBoolean(KEY_TRANSCRIPT_SMOOTH_SWEEP, true)
        transcriptOffsetTenths = prefs.getInt(KEY_TRANSCRIPT_OFFSET_TENTHS, 0)
            .coerceIn(LYRICS_OFFSET_TENTHS_MIN, LYRICS_OFFSET_TENTHS_MAX)
        transcriptTranslationEnabled = prefs.getBoolean(KEY_TRANSCRIPT_TRANSLATION, false)
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

    fun setYtmProviderOrder(context: Context, providers: List<LyricsProvider>) {
        ytmProviderOrder = LyricsProvider.orderedFor(providers, StreamSource.YT_MUSIC)
        AppPrefs.edit(context, PREFS)
            .putString(KEY_YTM_PROVIDER_ORDER, ytmProviderOrder.joinToString(",") { it.name })
            .apply()
    }

    /** Persists the chain the given stream should be asked through, in its own order. */
    fun setOrderFor(context: Context, stream: StreamSource, providers: List<LyricsProvider>) {
        when (stream) {
            StreamSource.SPOTIFY -> setProviderOrder(context, providers)
            StreamSource.YT_MUSIC -> setYtmProviderOrder(context, providers)
        }
    }

    /** The chain the given stream should be asked through, in its own order. */
    fun providerOrderFor(stream: StreamSource): List<LyricsProvider> = when (stream) {
        StreamSource.SPOTIFY -> LyricsProvider.orderedFor(providerOrder, StreamSource.SPOTIFY)
        StreamSource.YT_MUSIC -> ytmProviderOrder
    }

    fun setOffsetTenths(context: Context, tenths: Int) {
        val clamped = tenths.coerceIn(LYRICS_OFFSET_TENTHS_MIN, LYRICS_OFFSET_TENTHS_MAX)
        offsetTenths = clamped
        AppPrefs.edit(context, PREFS).putInt(KEY_OFFSET_TENTHS, clamped).apply()
    }

    fun setKeepScreenOn(context: Context, value: Boolean) {
        keepScreenOn = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
    }

    fun setDeepFeelEnabled(context: Context, value: Boolean) {
        deepFeelEnabled = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_DEEP_FEEL, value).apply()
    }

    fun setNextTrackPeek(context: Context, value: Boolean) {
        nextTrackPeek = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_NEXT_TRACK_PEEK, value).apply()
    }

    fun setTranscriptSmoothSweep(context: Context, value: Boolean) {
        transcriptSmoothSweep = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_TRANSCRIPT_SMOOTH_SWEEP, value).apply()
    }

    fun setTranscriptOffsetTenths(context: Context, tenths: Int) {
        val clamped = tenths.coerceIn(LYRICS_OFFSET_TENTHS_MIN, LYRICS_OFFSET_TENTHS_MAX)
        transcriptOffsetTenths = clamped
        AppPrefs.edit(context, PREFS).putInt(KEY_TRANSCRIPT_OFFSET_TENTHS, clamped).apply()
    }

    fun setTranscriptTranslationEnabled(context: Context, value: Boolean) {
        transcriptTranslationEnabled = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_TRANSCRIPT_TRANSLATION, value).apply()
    }

    /** The sweep position offset to apply, in milliseconds. */
    val offsetMs: Long get() = offsetTenths * 100L

    /** The transcript timing offset to apply, in milliseconds. */
    val transcriptOffsetMs: Long get() = transcriptOffsetTenths * 100L

    private fun readProviderOrder(
        saved: String?,
        fallback: List<LyricsProvider> = LyricsProvider.offered,
    ): List<LyricsProvider> {
        if (saved.isNullOrBlank()) return fallback
        val parsed = saved.split(',').mapNotNull { name ->
            LyricsProvider.entries.firstOrNull { it.name == name.trim() }
        }
        if (parsed.isEmpty()) return fallback
        return LyricsProvider.ordered(parsed)
    }
}
