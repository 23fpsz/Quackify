/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * |    \| '__/ _ \/ __/ '_ \ / _ \| __|
 * |\  \ | |  __/\__ \ |_) | (_) | |_
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |
 *                     |_|
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal and educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.core.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference

private val log = Logs.of(Logs.Module.WEBVIEW)

private const val ACCOUNTS_ORIGIN = "https://accounts.spotify.com"

private const val LOGIN_REFERER =
    "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

private const val EDGE_SEC_CH_UA =
    "\"Not A;Brand\";v=\"99\", \"Chromium\";v=\"158\", \"Microsoft Edge\";v=\"158\""

private const val EDGE_LOGIN_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/158.0.0.0 Safari/537.36 Edg/158.0.0.0"

/**
 * The login page posts the email-confirmation code to /login/code/send from
 * inside the WebView, whose network stack silently stamps the request with
 * x-requested-with and Android sec-ch-* hints; Spotify's bot gate answers
 * that with a 400. The page's call is absorbed here and re-issued by OkHttp —
 * which adds none of those marks — wearing exactly the desktop Edge header
 * set of a real browser session, and the answer is handed back to the page
 * as if its own call had produced it.
 */
object LoginCodeRelay {

    fun install(view: WebView) {
        view.addJavascriptInterface(Bridge(view), "AndCodeRelay")
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            log.w("code relay: DOCUMENT_START_SCRIPT unsupported, code send stays in-page")
            return
        }
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(view, JS, setOf(ACCOUNTS_ORIGIN))
            log.i("code relay installed on $ACCOUNTS_ORIGIN (document-start)")
        }.onFailure { log.e("code relay install failed", it) }
    }

    class Bridge(view: WebView) {

        private val main = Handler(Looper.getMainLooper())
        private val target = WeakReference(view)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @JavascriptInterface
        fun send(request: String) {
            val o = runCatching { JSONObject(request) }.getOrElse {
                log.w("code relay payload not-json ${request.take(120)}")
                return
            }
            val url = o.optString("url")
            val body = o.optString("body")
            val callback = o.optString("callback")
            if (url.isBlank() || body.isBlank() || callback.isBlank()) return
            log.i("code relay: taking over $url")
            scope.launch {
                val response = Net.postJson(url, body, headers(url), tag = "code-relay")
                val outcome = if (response != null) {
                    log.i("code relay: ${response.code} in ${response.millis}ms")
                    JSONObject().put("status", response.code).put("body", response.body)
                } else {
                    log.w("code relay: request never completed")
                    JSONObject().put("status", 0).put("body", "")
                }
                val script = "try{window.$callback($outcome);}catch(e){}"
                main.post {
                    val view = target.get()
                    if (view == null) {
                        log.w("code relay: webview gone before the answer")
                        return@post
                    }
                    runCatching { view.evaluateJavascript(script, null) }
                }
            }
        }
    }

    private fun headers(url: String): Map<String, String> = mapOf(
        "accept" to "application/json",
        "accept-language" to "en-US,en;q=0.9",
        "cache-control" to "no-cache",
        "content-type" to "application/json",
        "cookie" to (cookieManagerOrNull()?.getCookie(url).orEmpty()),
        "origin" to ACCOUNTS_ORIGIN,
        "pragma" to "no-cache",
        "priority" to "u=1, i",
        "referer" to LOGIN_REFERER,
        "sec-ch-ua" to EDGE_SEC_CH_UA,
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\"",
        "sec-fetch-dest" to "empty",
        "sec-fetch-mode" to "cors",
        "sec-fetch-site" to "same-origin",
        "user-agent" to EDGE_LOGIN_UA,
        "x-csrf-token" to cookieValue(ACCOUNTS_ORIGIN, "sp_sso_csrf_token"),
    )

    private fun cookieValue(url: String, name: String): String =
        cookieManagerOrNull()?.getCookie(url)
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            .orEmpty()

    private val JS = """
        (function(){
            if (window.__quackifyCodeRelay) return;
            window.__quackifyCodeRelay = 1;

            var PATH = '/login/code/send';

            function abs(u){
                try { return new URL(String(u), location.href).href; }
                catch(e) { return String(u); }
            }

            function isSend(u){
                try {
                    var url = new URL(String(u), location.href);
                    return url.origin === 'https://accounts.spotify.com' && url.pathname === PATH;
                } catch(e) { return false; }
            }

            function makeResponse(ours){
                return new Response(ours.body, {
                    status: ours.status,
                    statusText: '',
                    headers: { 'content-type': 'application/json' }
                });
            }

            var of = window.fetch;
            if (of) {
                window.fetch = function(input, init){
                    var url = abs(typeof input === 'string' ? input : (input && input.url) || '');
                    var method = (init && init.method) || (input && input.method) || 'GET';
                    var body = (init && typeof init.body === 'string') ? init.body : '';
                    if (!isSend(url) || method.toUpperCase() !== 'POST' ||
                        !window.AndCodeRelay || !body) {
                        return of.apply(this, arguments);
                    }
                    var cb = '__qr' + Math.floor(Math.random() * 1e9);
                    return new Promise(function(resolve, reject){
                        window[cb] = function(ours){
                            delete window[cb];
                            try {
                                if (ours.status <= 0) reject(new TypeError('network error'));
                                else resolve(makeResponse(ours));
                            } catch(e) { reject(e); }
                        };
                        try {
                            window.AndCodeRelay.send(JSON.stringify({
                                url: url, body: body, callback: cb
                            }));
                        } catch(e) {
                            delete window[cb];
                            reject(e);
                        }
                    });
                };
            }

            var XP = XMLHttpRequest.prototype;
            var xo = XP.open, xs = XP.send;
            XP.open = function(m, u){
                this.__qrSend = isSend(u) && String(m).toUpperCase() === 'POST' && !!window.AndCodeRelay;
                this.__qrUrl = abs(u);
                return xo.apply(this, arguments);
            };
            XP.send = function(b){
                if (!this.__qrSend || typeof b !== 'string') return xs.apply(this, arguments);
                var self = this;
                var cb = '__qr' + Math.floor(Math.random() * 1e9);
                window[cb] = function(ours){
                    delete window[cb];
                    try {
                        Object.defineProperty(self, 'readyState', { value: 4, configurable: true });
                        Object.defineProperty(self, 'status', { value: ours.status, configurable: true });
                        Object.defineProperty(self, 'responseText', { value: ours.body, configurable: true });
                        Object.defineProperty(self, 'response', { value: ours.body, configurable: true });
                        self.dispatchEvent(new Event('readystatechange'));
                        if (ours.status <= 0) {
                            self.dispatchEvent(new ProgressEvent('error'));
                        } else {
                            self.dispatchEvent(new Event('load'));
                        }
                        self.dispatchEvent(new Event('loadend'));
                    } catch(e) {}
                };
                try {
                    window.AndCodeRelay.send(JSON.stringify({
                        url: self.__qrUrl, body: b, callback: cb
                    }));
                } catch(e) {
                    delete window[cb];
                    xs.apply(this, arguments);
                }
            };
        })();
    """.trimIndent()
}
