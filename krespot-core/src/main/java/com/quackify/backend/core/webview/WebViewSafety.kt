package com.quackify.backend.core.webview

import android.webkit.CookieManager
import com.quackify.backend.core.log.Logs

private val log = Logs.of(Logs.Module.WEBVIEW)

/**
 * The system WebView provider can disappear from under the app — the
 * com.google.android.webview package gets disabled, hidden or swapped
 * mid-update (seen on MIUI as "Package not found: com.google.android.webview"),
 * and `CookieManager.getInstance()` then throws an AndroidRuntimeException
 * that takes the whole process down when it happens on a composition path.
 *
 * Every cookie-jar access goes through here: a broken provider degrades to
 * "no cookies" (and the webview flows back off) instead of a crash loop.
 */
fun cookieManagerOrNull(): CookieManager? = try {
    CookieManager.getInstance()
} catch (t: Throwable) {
    log.w("cookie jar unavailable: ${t.cause?.message ?: t.message}")
    null
}
