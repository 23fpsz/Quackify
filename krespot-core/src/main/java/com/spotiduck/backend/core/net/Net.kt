package com.spotiduck.backend.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import com.spotiduck.backend.core.log.Logs

private val log = Logs.of(Logs.Module.NET)

private const val NETWORK_ATTEMPTS = 3
private const val NETWORK_BACKOFF_MS = 700L

object Net {

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var onUnauthorized: (suspend (sentToken: String, reason: String) -> String?)? = null

    private val refreshGate = kotlinx.coroutines.sync.Mutex()

    private suspend fun refreshed(sent: String, reason: String): String? {
        val hook = onUnauthorized ?: return null
        return refreshGate.withLock {
            log.w("$reason 401, asking for a fresh access token")
            hook(sent, reason)
        }
    }

    private fun bearerOf(headers: Map<String, String>): String {
        val value = headers.entries
            .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
            ?.value.orEmpty()
        return value.removePrefix("Bearer ").trim()
    }

    private fun withBearer(headers: Map<String, String>, token: String): Map<String, String> {
        if (token.isBlank()) return headers
        val key = headers.keys.firstOrNull { it.equals("Authorization", ignoreCase = true) }
            ?: return headers
        return headers + (key to "Bearer $token")
    }

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"

    data class Response(
        val code: Int,
        val body: String,
        val contentType: String,
        val millis: Long,
        val url: String,
    ) {
        val ok: Boolean get() = code in 200..299
        val bytes: Int get() = body.length
        fun json(): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
        fun jsonArray(): JSONArray? = runCatching { JSONArray(body) }.getOrNull()
    }

    data class Blob(
        val code: Int,
        val bytes: ByteArray,
        val contentType: String,
        val millis: Long,
        val headers: Map<String, String> = emptyMap(),
    ) {
        val ok: Boolean get() = code in 200..299
        val size: Int get() = bytes.size
        fun header(name: String): String = headers[name.lowercase()].orEmpty()
        val retryAfterSeconds: Long? get() = header("retry-after").trim().toLongOrNull()
    }

    private val counter = AtomicInteger(0)

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = request("GET", url, headers, null, tag)

    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = request(
        "POST",
        url,
        if (headers.containsKey("Content-Type")) {
            headers
        } else {
            headers + mapOf("Content-Type" to "application/json")
        },
        json,
        tag,
    )

    suspend fun putJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = request(
        "PUT",
        url,
        if (headers.containsKey("Content-Type")) {
            headers
        } else {
            headers + mapOf("Content-Type" to "application/json")
        },
        json,
        tag,
    )

    suspend fun postBytes(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = requestBytes(
        "POST",
        url,
        headers + mapOf("Content-Type" to contentType),
        bytes,
        tag,
    )

    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        tag: String = "",
    ): Response? = requestBytes(method, url, headers, body?.toByteArray(), tag, body)

    suspend fun requestBytes(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        tag: String = "",
        bodyPreview: String? = null,
    ): Response? {
        val first = attempt(method, url, headers, body, tag, bodyPreview)
        if (first == null || first.code != 401) return first
        val fresh = refreshed(bearerOf(headers), "$method ${short(url)}") ?: return first
        log.w("${tag.ifBlank { method }} retrying with a token ${fresh.length}B long after a 401")
        return attempt(method, url, withBearer(headers, fresh), body, tag, bodyPreview)
    }

    private suspend fun attempt(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        tag: String,
        bodyPreview: String?,
    ): Response? = withContext(Dispatchers.IO) {
        val id = counter.incrementAndGet()
        val label = if (tag.isBlank()) "#$id" else "#$id/$tag"
        logRequest(label, method, url, headers, bodyPreview, if (bodyPreview == null) body?.size ?: 0 else 0)

        var attempt = 1
        while (true) {
            val started = System.currentTimeMillis()
            val outcome = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 15_000
                    conn.instanceFollowRedirects = true
                    headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                    if (body != null) {
                        conn.doOutput = true
                        conn.setFixedLengthStreamingMode(body.size)
                        conn.outputStream.use { it.write(body) }
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val type = conn.contentType.orEmpty()
                    // No disconnect() here: reading the whole body puts the
                    // socket back in the JVM keep-alive pool, so the next call
                    // to the same host skips the TCP + TLS handshake. Only a
                    // failure below frees the broken socket explicitly.
                    val millis = System.currentTimeMillis() - started
                    logResponse(label, code, millis, text, type)
                    Response(code = code, body = text, contentType = type, millis = millis, url = url)
                } catch (failure: Throwable) {
                    runCatching { conn.disconnect() }
                    throw failure
                }
            }
            outcome.onFailure {
                val millis = System.currentTimeMillis() - started
                log.e("$label ✗ ${it.javaClass.simpleName}: ${it.message} after ${millis}ms ${short(url)}")
            }

            val failure = outcome.exceptionOrNull()
            val neverLeftTheDevice = failure is java.net.UnknownHostException ||
                failure is java.net.ConnectException
            if (failure == null || !neverLeftTheDevice || attempt >= NETWORK_ATTEMPTS) {
                return@withContext outcome.getOrNull()
            }
            val wait = attempt * NETWORK_BACKOFF_MS
            log.w("$label ${failure.javaClass.simpleName} on attempt $attempt/$NETWORK_ATTEMPTS, retrying in ${wait}ms"
            )
            delay(wait)
            attempt++
        }
        null
    }

    suspend fun blob(
        url: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Blob? = withContext(Dispatchers.IO) {
        val id = counter.incrementAndGet()
        val label = if (tag.isBlank()) "#$id" else "#$id/$tag"
        log.i("$label → GET ${short(url)} ${headers.size} headers")
        val started = System.currentTimeMillis()
        val outcome = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                val type = conn.contentType.orEmpty()
                val fromServer = received(conn)
                // Keep-alive: leave a fully read connection pooled.
                val millis = System.currentTimeMillis() - started
                val mark = if (code in 200..299) "←" else "✗"
                log.i("$label $mark $code ${millis}ms ${bytes.size}B ${type.ifBlank { "?" }}")
                Blob(code, bytes, type, millis, fromServer)
            } catch (failure: Throwable) {
                runCatching { conn.disconnect() }
                throw failure
            }
        }
        outcome.onFailure {
            log.e("$label ✗ ${it.javaClass.simpleName}: ${it.message} ${short(url)}")
        }
        outcome.getOrNull()
    }

    suspend fun fetch(
        url: String,
        start: Int,
        end: Int,
        headers: Map<String, String> = emptyMap(),
        tag: String = "cdn",
    ): ByteArray? = blob(url, headers + mapOf("Range" to "bytes=$start-$end"), tag)
        ?.takeIf { it.ok }
        ?.bytes

    suspend fun postBlob(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Blob? {
        val first = attemptPostBlob(url, body, contentType, headers, tag)
        if (first == null || first.code != 401) return first
        val fresh = refreshed(bearerOf(headers), "POST ${short(url)}") ?: return first
        log.w("${tag.ifBlank { "postBlob" }} retrying with a fresh token after a 401")
        return attemptPostBlob(url, body, contentType, withBearer(headers, fresh), tag)
    }

    private suspend fun attemptPostBlob(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
        tag: String,
    ): Blob? = withContext(Dispatchers.IO) {
        val id = counter.incrementAndGet()
        val label = if (tag.isBlank()) "#$id" else "#$id/$tag"
        log.i("$label → POST ${short(url)} body(${body.size}B binary)")
        if (enabled) headers.forEach { (k, v) -> log.i("$label   $k: ${redact(k, v)}") }
        val started = System.currentTimeMillis()
        val outcome = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.setRequestProperty("Content-Type", contentType)
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                val type = conn.contentType.orEmpty()
                val fromServer = received(conn)
                // Keep-alive: leave a fully read connection pooled.
                val millis = System.currentTimeMillis() - started
                val mark = if (code in 200..299) "←" else "✗"
                log.i("$label $mark $code ${millis}ms ${bytes.size}B ${type.ifBlank { "?" }}")
                Blob(code, bytes, type, millis, fromServer)
            } catch (failure: Throwable) {
                runCatching { conn.disconnect() }
                throw failure
            }
        }
        outcome.onFailure {
            log.e("$label ✗ ${it.javaClass.simpleName}: ${it.message} ${short(url)}")
        }
        outcome.getOrNull()
    }

    private fun received(conn: HttpURLConnection): Map<String, String> = runCatching {
        conn.headerFields.entries
            .mapNotNull { entry -> entry.key?.let { it.lowercase() to entry.value.firstOrNull().orEmpty() } }
            .toMap()
    }.getOrDefault(emptyMap())

    private fun logRequest(
        label: String,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        binaryBytes: Int = 0,
    ) {
        if (!enabled) return
        log.i("$label → $method ${short(url)}")
        headers.forEach { (k, v) -> log.i("$label   $k: ${redact(k, v)}") }
        if (binaryBytes > 0) {
            log.i("$label   body($binaryBytes B binary)")
        } else if (!body.isNullOrEmpty()) {
            log.i("$label   body(${body.length}B) ${preview(body)}")
        }
    }

    private fun logResponse(label: String, code: Int, millis: Long, body: String, type: String) {
        if (!enabled) return
        val mark = if (code in 200..299) "←" else "✗"
        log.i("$label $mark $code ${millis}ms ${body.length}B ${type.ifBlank { "?" }}")
        if (body.isNotBlank()) log.i("$label   ${preview(body)}")
    }

    private fun short(url: String): String =
        if (url.length <= 180) url else url.take(180) + "…(${url.length})"

    private fun preview(body: String): String {
        val flat = body.replace(Regex("\\s+"), " ").trim()
        // The token endpoints answer with the live bearer in the body; keep
        // it out of the log the same way redact() keeps it out of the headers.
        val safe = tokenValue.replace(flat) { match ->
            "\"${match.groupValues[1]}\":\"…${match.groupValues[2].length}B\""
        }
        return if (safe.length <= 700) safe else safe.take(700) + "…+${safe.length - 700}"
    }

    private val tokenValue = Regex(
        "\"(accessToken|access_token|refreshToken|refresh_token|clientToken|client_token|id_token|token)\"\\s*:\\s*\"([^\"]*)\""
    )

    private fun redact(name: String, value: String): String {
        val lower = name.lowercase()
        val sensitive = lower == "authorization" ||
            lower == "cookie" ||
            lower == "client-token" ||
            lower == "set-cookie"
        if (!sensitive) return value
        val scheme = value.substringBefore(' ', "")
        val head = if (scheme == "Bearer" || scheme == "Basic") "$scheme " else ""
        return "$head${value.takeLast(6)} (${value.length}B)"
    }
}
