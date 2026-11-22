package com.quackify.backend.player

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val log = Logs.of(Logs.Module.YTM)

/**
 * Proof-of-origin tokens for the web innertube clients, minted from the
 * BotGuard attestation the YouTube homepage hands out.
 *
 * The heavy lifting runs inside a WebView that only ever holds our harness
 * page; every byte on the wire (homepage, interpreter script, integrity
 * exchange) goes through [Net] from Kotlin so the app's network stack and
 * logging stay the single point of truth.
 */
internal object PoTokenGate {

    /** A primed attestation session: the visitor it belongs to and its request token. */
    data class Session(
        val visitorData: String,
        val requestToken: String,
        val expiresAt: Long,
    ) {
        fun alive(): Boolean = System.currentTimeMillis() < expiresAt
    }

    /** One webview minter per visitor; the integrity epoch rules the session. */
    private val gate = Mutex()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var page: PoTokenPage? = null

    /** Set when this device's WebView cannot host the harness at all. */
    @Volatile
    private var unusable: Boolean = false

    /** The visitor-bound token the web clients send in the player request body. */
    suspend fun session(context: Context): Session? {
        cached()?.let { return it }
        if (unusable) return null
        return gate.withLock {
            cached()?.let { return it }
            try {
                open(context)
            } catch (failure: Throwable) {
                log.w("att session failed: ${failure.message ?: failure.javaClass.simpleName}")
                null
            }
        }
    }

    /** The video-bound token the web clients append to the stream urls. */
    suspend fun videoToken(context: Context, videoId: String): String? {
        if (unusable) return null
        if (cached() == null && session(context) == null) return null
        val active = page ?: return null
        return try {
            val minted = active.mint(videoId)
            log.i("att mint for '$videoId' ${minted.length}ch")
            minted
        } catch (failure: Throwable) {
            log.w("att mint for '$videoId' failed: ${failure.message ?: failure.javaClass.simpleName}")
            invalidate()
            null
        }
    }

    fun invalidate() {
        val doomed = page
        page = null
        session = null
        doomed?.close()
    }

    private fun cached(): Session? = session?.takeIf { it.alive() && page != null }

    private suspend fun open(context: Context): Session? {
        val fresh = PoTokenPage.open(context) ?: run {
            unusable = true
            return null
        }
        try {
            val home = Net.get(
                "https://www.youtube.com",
                mapOf("User-Agent" to Net.DESKTOP_UA, "Accept-Language" to "en-US,en;q=0.9"),
                tag = "ytm-att",
            )?.takeIf { it.ok } ?: throw IllegalStateException("homepage refused")
            val ytcfg = extract(home.body, """ytcfg\.set\(({.+?})\);""") ?: throw IllegalStateException("no ytcfg")
            val attCall = extract(home.body, """window\.ytAtN\(\s*({[\s\S]*?})\s*\)""") ?: throw IllegalStateException("no attestation")
            val visitorData = extract(home.body, """"VISITOR_DATA"\s*:\s*"([^"]+)"""")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: throw IllegalStateException("no visitor data in ytcfg")
            val challenge = JSONObject(fresh.parseAttestation(attCall))
            val program = challenge.optString("program")
            val globalName = challenge.optString("globalName")
            val scriptUrl = challenge.optString("script")
            if (program.isBlank() || globalName.isBlank() || scriptUrl.isBlank()) {
                throw IllegalStateException("challenge incomplete")
            }
            val interpreter = Net.get(
                "https:$scriptUrl",
                mapOf("User-Agent" to Net.DESKTOP_UA),
                tag = "ytm-att",
            )?.takeIf { it.ok }?.body ?: throw IllegalStateException("interpreter refused")
            log.i("att challenge program ${program.length}ch via $globalName")

            fresh.prime(ytcfg)
            fresh.run(program, globalName, interpreter)
            val snapshot = fresh.snapshot()
            val integrity = Net.postJson(
                "https://www.youtube.com/api/jnn/v1/GenerateIT",
                JSONArray().put(REQUEST_KEY).put(snapshot).toString(),
                mapOf(
                    "User-Agent" to Net.DESKTOP_UA,
                    "Accept" to "application/json",
                    "Content-Type" to "application/json+protobuf",
                    "x-goog-api-key" to API_KEY,
                    "x-user-agent" to "grpc-web-javascript/0.1",
                ),
                tag = "ytm-att",
            )?.takeIf { it.ok }?.jsonArray()
            val token = integrity?.optString(0).orEmpty()
            val ttl = integrity?.optLong(1, 0L) ?: 0L
            if (token.isBlank() || ttl <= 0L) throw IllegalStateException("integrity exchange refused")
            log.i("att integrity token ${token.length}ch for ${ttl}s")

            fresh.arm(decodeWebsafe(token))
            val requestToken = fresh.mint(visitorData)
            log.i("att session primed, visitor ${visitorData.take(16)}… token ${requestToken.length}ch")
            val primed = Session(visitorData, requestToken, System.currentTimeMillis() + (ttl - 600L) * 1000L)
            page = fresh
            session = primed
            return primed
        } catch (failure: Throwable) {
            fresh.close()
            throw failure
        }
    }

    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

    private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"

    private fun extract(html: String, pattern: String): String? =
        Regex(pattern, RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)

    /** YouTube's integrity tokens ride websafe base64; normalize and decode. */
    fun decodeWebsafe(value: String): ByteArray {
        val padded = value.replace('-', '+').replace('_', '/').replace('.', '=')
        return android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
    }

    fun encodeWebsafe(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            .replace('+', '-').replace('/', '_')
}

/**
 * The harness page: a WebView that carries the BotGuard VM. Kotlin feeds it
 * the challenge, the interpreter and the integrity token; it answers with
 * minted tokens through the [QuackToken] bridge.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class PoTokenPage private constructor(context: Context) {

    private val webView = WebView(context)

    private val answers = ConcurrentHashMap<String, CompletableDeferred<String>>()

    private val sequence = AtomicLong(0)

    private val closed = AtomicBoolean(false)

    @Volatile
    private var dead = false

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = Net.DESKTOP_UA
        webView.settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, BRIDGE)
    }

    /** Load the harness and wait until the page is up. */
    private suspend fun load() {
        withContext(Dispatchers.Main) {
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.complete(Unit)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    dead = true
                    log.w("att webview renderer gone")
                    loaded.completeExceptionally(IllegalStateException("renderer gone"))
                    answers.values.forEach { it.completeExceptionally(IllegalStateException("renderer gone")) }
                    answers.clear()
                    return true
                }
            }
            webView.loadDataWithBaseURL("https://www.youtube.com", HARNESS, "text/html", "utf-8", null)
        }
        withTimeout(LOAD_TIMEOUT_MS) { loaded.await() }
    }

    private val loaded = CompletableDeferred<Unit>()

    /** Give the page the session's ytcfg; BotGuard reads yt.config_ while snapshotting. */
    suspend fun prime(ytcfg: String) = evaluate("bgPrime(($ytcfg))")

    /** Parse the attestation literal in the page; it is a JS object, not JSON. */
    suspend fun parseAttestation(attCall: String): String = evaluate("bgParseAtt(($attCall))")

    /** Execute the interpreter and arm the challenge program. */
    suspend fun run(program: String, globalName: String, interpreter: String) = evaluate(
        "bgRun(${JSONObject.quote(program)}, ${JSONObject.quote(globalName)}, ${JSONObject.quote(interpreter)})"
    )

    /** Take the attestation snapshot; returns the response BotGuard produced. */
    suspend fun snapshot(): String = evaluate("bgSnapshot()")

    /** Turn the integrity token into a minter held by the page. */
    suspend fun arm(integrityToken: ByteArray) = evaluate(
        "bgArm(new Uint8Array([${integrityToken.joinToString(",") { it.toString() }}]))"
    )

    /** Mint a token bound to [identifier] and return it websafe-base64 encoded. */
    suspend fun mint(identifier: String): String {
        val bytes = identifier.toByteArray()
        val comma = evaluate("bgMint(new Uint8Array([${bytes.joinToString(",") { it.toString() }}]))")
        if (comma.isBlank()) throw IllegalStateException("mint produced nothing")
        val raw = comma.split(",").mapNotNull { it.trim().toIntOrNull() }.map { it.toByte() }.toByteArray()
        return PoTokenGate.encodeWebsafe(raw)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        dead = true
        val doom = Runnable {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
            }
        }
        val main = android.os.Looper.getMainLooper()
        if (android.os.Looper.myLooper() == main) doom.run() else android.os.Handler(main).post(doom)
    }

    @JavascriptInterface
    fun done(key: String, value: String) {
        answers.remove(key)?.complete(value)
    }

    @JavascriptInterface
    fun fail(key: String, error: String) {
        answers.remove(key)?.completeExceptionally(IllegalStateException(error.take(240)))
    }

    private suspend fun evaluate(script: String): String {
        if (closed.get() || dead) throw IllegalStateException("att page is gone")
        val key = "op${sequence.incrementAndGet()}"
        val answer = CompletableDeferred<String>()
        answers[key] = answer
        withContext(Dispatchers.Main) {
            if (closed.get() || dead) {
                answers.remove(key)
                throw IllegalStateException("att page is gone")
            }
            webView.evaluateJavascript(
                "(function(){var k=${JSONObject.quote(key)};try{$script.then(function(v){$BRIDGE.done(k,v||'')},function(e){$BRIDGE.fail(k,String(e))})}catch(e){$BRIDGE.fail(k,e+':'+(e&&e.stack||''))}})()",
                null,
            )
        }
        return try {
            withTimeout(OP_TIMEOUT_MS) { answer.await() }
        } catch (failure: Throwable) {
            answers.remove(key)
            dead = true
            throw failure
        }
    }

    companion object {
        private const val BRIDGE = "QuackToken"

        private const val LOAD_TIMEOUT_MS = 20_000L

        private const val OP_TIMEOUT_MS = 30_000L

        /**
         * The BotGuard harness. Loads the interpreter, runs the challenge
         * program with the full argument shape the current VM expects, takes
         * the snapshot, and keeps the minter the integrity token produces.
         */
        private const val HARNESS = """<!DOCTYPE html><html lang="en"><head><title></title><script>
var bg = { vm: null, snapshotFn: null, output: null, minter: null };

function bgPrime(ytcfg) { window.yt = { config_: ytcfg }; return Promise.resolve(''); }

function bgParseAtt(att) {
  return new Promise(function (resolve, reject) {
    try {
      var payload = (att && typeof att.R === 'string') ? JSON.parse(att.R) : att.R;
      var challenge = payload && payload.bgChallenge;
      if (!challenge || !challenge.program) throw new Error('no bgChallenge');
      var script = challenge.interpreterUrl &&
        challenge.interpreterUrl.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue;
      if (!script) throw new Error('no interpreterUrl');
      resolve(JSON.stringify({
        program: challenge.program,
        globalName: challenge.globalName,
        script: script
      }));
    } catch (e) { reject(e); }
  });
}

function bgRun(program, globalName, interpreterJs) {
  return new Promise(function (resolve, reject) {
    try {
      new Function(interpreterJs)();
      var vm = window[globalName];
      if (!vm || !vm.a) throw new Error('vm ' + globalName + ' not defined');
      bg.vm = vm;
      var armed = false;
      vm.a(program, function (asyncSnapshot, shutdown, passEvent, checkCamera) {
        if (armed) return;
        armed = true;
        bg.snapshotFn = asyncSnapshot;
        resolve('');
      }, true, undefined, function () {}, [[], []], undefined, false,
        [function () {}, function () {}, function () {}, function () {}, function () {}]);
      setTimeout(function () {
        if (!armed) reject(new Error('vm never armed'));
      }, 15000);
    } catch (e) { reject(e); }
  });
}

function bgSnapshot() {
  return new Promise(function (resolve, reject) {
    bg.output = [];
    try {
      if (!bg.snapshotFn) throw new Error('vm not armed');
      bg.snapshotFn(function (response) { resolve(response); },
        [undefined, undefined, bg.output, undefined]);
      setTimeout(function () { reject(new Error('snapshot stalled')); }, 20000);
    } catch (e) { reject(e); }
  });
}

function bgArm(integrityToken) {
  return new Promise(function (resolve, reject) {
    var waits = 0;
    var timer = setInterval(function () {
      if (bg.output && bg.output.length) {
        clearInterval(timer);
        try {
          var factory = bg.output[0];
          if (typeof factory !== 'function') { reject(new Error('factory is not callable')); return; }
          var made = factory(integrityToken);
          var settle = function (minter) {
            if (typeof minter !== 'function') { reject(new Error('minter is not callable')); return; }
            bg.minter = minter;
            resolve('');
          };
          if (made && typeof made.then === 'function') made.then(settle, reject); else settle(made);
        } catch (e) { reject(e); }
      } else if (++waits > 100) {
        clearInterval(timer);
        reject(new Error('factory never appeared'));
      }
    }, 50);
  });
}

function bgMint(identifierBytes) {
  return new Promise(function (resolve, reject) {
    try {
      if (!bg.minter) throw new Error('minter is not primed');
      var made = bg.minter(identifierBytes);
      var deliver = function (token) {
        if (!token) { reject(new Error('mint returned nothing')); return; }
        resolve(token.join(','));
      };
      if (made && typeof made.then === 'function') made.then(deliver, reject); else deliver(made);
    } catch (e) { reject(e); }
  });
}
</script></head><body></body></html>"""

        suspend fun open(context: Context): PoTokenPage? {
            var created: PoTokenPage? = null
            try {
                return withTimeout(LOAD_TIMEOUT_MS) {
                    created = withContext(Dispatchers.Main) { PoTokenPage(context) }
                    created!!.load()
                    created
                }
            } catch (failure: Throwable) {
                log.w("att page failed to open: ${failure.message ?: failure.javaClass.simpleName}")
                created?.close()
                return null
            }
        }
    }
}
