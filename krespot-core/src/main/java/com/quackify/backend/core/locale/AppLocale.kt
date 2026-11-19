package com.quackify.backend.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.quackify.backend.core.store.AppPrefs
import java.util.Locale

/**
 * In-app language override. The chosen tag lives in the client prefs store,
 * the activity wraps its base context with [wrap] (attachBaseContext), and a
 * change is applied by recreating the activity — the same mechanism the web
 * player uses, so the resource folders (values-de, values-ja, ...) that the
 * system locale already picks up are reused as-is.
 *
 * The login webview is deliberately isolated from the app language through
 * [loginContext] / [pushLoginLocale]: Spotify negotiates the webview's
 * Accept-Language into its URLs, and a language picked for the app UI has no
 * business deciding what accounts.spotify.com serves.
 */
object AppLocale {

    private const val PREFS = "quackify.client"
    private const val KEY = "appLanguage"

    const val SYSTEM = "system"

    /** "System default" plus every language the app ships translations for. */
    val AVAILABLE: List<String> = listOf(
        SYSTEM, "en", "de", "es", "fr", "it", "ja", "ko", "pt", "ru", "zh-CN",
    )

    private val NATIVE_NAMES = mapOf(
        "en" to "English",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "ja" to "日本語",
        "ko" to "한국어",
        "pt" to "Português",
        "ru" to "Русский",
        "zh-CN" to "简体中文",
    )

    /** A few languages resolve on Android through their legacy ISO codes. */
    private val LEGACY_LANGUAGES = mapOf(
        "id" to "in",
        "he" to "iw",
        "yi" to "ji",
    )

    fun current(context: Context): String {
        val stored = AppPrefs.getString(context, PREFS, KEY)
        return if (stored != null && stored in AVAILABLE) stored else SYSTEM
    }

    fun set(context: Context, tag: String) {
        AppPrefs.putString(context, PREFS, KEY, tag)
    }

    fun nativeName(tag: String): String? = NATIVE_NAMES[tag]

    fun wrap(base: Context): Context {
        val tag = current(base)
        val config = base.resources.configuration
        if (tag == SYSTEM) {
            val system = config.locales[0]
            if (system != null && Locale.getDefault() != system) Locale.setDefault(system)
            return base
        }
        val locale = localeFor(tag) ?: return base
        Locale.setDefault(locale)
        val wrapped = Configuration(config)
        wrapped.setLocale(locale)
        wrapped.setLocales(LocaleList(locale))
        return base.createConfigurationContext(wrapped)
    }

    // ------------------------------------------------------------------
    // Login locale isolation
    // ------------------------------------------------------------------

    /**
     * A context pinned to en-US for the login webviews, whatever the app
     * language is. Does not touch the process-wide default — pair with
     * [pushLoginLocale] when a login webview is actually alive.
     */
    fun loginContext(base: Context): Context {
        val locale = localeFor(LOGIN_LOCALE) ?: return base
        val wrapped = Configuration(base.resources.configuration)
        wrapped.setLocale(locale)
        wrapped.setLocales(LocaleList(locale))
        return base.createConfigurationContext(wrapped)
    }

    private var loginDepth = 0
    private var savedLocales: LocaleList? = null

    /**
     * Pins the process locale to en-US while a login webview is alive: some
     * WebView builds derive Accept-Language from the process default rather
     * than from the creating context's configuration. Pairs with
     * [popLoginLocale]. Main thread only.
     */
    fun pushLoginLocale() {
        if (loginDepth++ == 0) {
            savedLocales = LocaleList.getDefault()
            val locale = localeFor(LOGIN_LOCALE) ?: return
            Locale.setDefault(locale)
            LocaleList.setDefault(LocaleList(locale))
        }
    }

    /** Restores what [pushLoginLocale] pinned. Safe to call unbalanced. */
    fun popLoginLocale() {
        if (loginDepth == 0) return
        if (--loginDepth == 0) {
            savedLocales?.let { LocaleList.setDefault(it) }
            savedLocales = null
        }
    }

    private const val LOGIN_LOCALE = "en-US"

    private fun localeFor(tag: String): Locale? {
        val parsed = runCatching { Locale.forLanguageTag(tag) }.getOrNull()
            ?.takeIf { it.language.isNotBlank() } ?: return null
        val legacy = LEGACY_LANGUAGES[parsed.language] ?: return parsed
        return runCatching {
            Locale.Builder()
                .setLanguage(legacy)
                .apply {
                    if (parsed.script.isNotBlank()) setScript(parsed.script)
                    if (parsed.country.isNotBlank()) setRegion(parsed.country)
                    if (parsed.variant.isNotBlank()) setVariant(parsed.variant)
                }
                .build()
        }.getOrNull() ?: parsed
    }
}
