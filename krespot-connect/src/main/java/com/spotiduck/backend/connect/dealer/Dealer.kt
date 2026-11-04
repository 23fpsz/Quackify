package com.spotiduck.backend.connect.dealer

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.os.SystemClock
import com.spotiduck.backend.core.log.Logs

private val log = Logs.of(Logs.Module.DEALER)

private const val PING_MS = 30_000L

/** No server frame for this long means the socket is half dead: force a reconnect. */
private const val SILENCE_MS = 3 * PING_MS

class Dealer {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .build()

    fun open(host: String, token: String): Flow<DealerFrame> = callbackFlow {
        val request = Request.Builder()
            .url("wss://$host/?access_token=$token")
            .build()

        val listener = object : WebSocketListener() {

            /** Elapsed-realtime clock of the last received frame, pings included. */
            @Volatile
            var lastReceiveAt = SystemClock.elapsedRealtime()

            override fun onOpen(webSocket: WebSocket, response: Response) {
                log.i("open ${response.code} $host")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastReceiveAt = SystemClock.elapsedRealtime()
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (json.optString("type")) {
                    "ping" -> {
                        webSocket.send("""{"type":"pong"}""")
                        return
                    }
                    "pong" -> return
                }
                val uri = json.optString("uri")
                if (uri.isBlank()) return
                val headers = json.optJSONObject("headers")?.let { node ->
                    node.keys().asSequence().associateWith { node.optString(it) }
                }.orEmpty()
                val payloads = json.optJSONArray("payloads")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        when (val item = array.opt(index)) {
                            null -> null
                            is String -> item
                            else -> item.toString()
                        }
                    }
                }.orEmpty()
                trySend(DealerFrame(uri = uri, headers = headers, payloads = payloads))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.w("failure ${t.javaClass.simpleName}: ${t.message} code=${response?.code ?: 0}")
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log.i("closed $code $reason")
                close()
            }
        }

        val socket = client.newWebSocket(request, listener)

        val pinger = launch {
            while (true) {
                delay(PING_MS)
                if (!socket.send("""{"type":"ping"}""")) return@launch
                // A buffered write can "succeed" into a half-open socket for
                // minutes after a network switch; a silent server means the
                // connection is gone even though nothing failed yet.
                if (SystemClock.elapsedRealtime() - listener.lastReceiveAt > SILENCE_MS) {
                    log.w("dealer silent for ${SILENCE_MS / 1000}s, forcing a reconnect")
                    socket.cancel()
                    return@launch
                }
            }
        }

        awaitClose {
            pinger.cancel()
            socket.close(1000, null)
        }
    }
}
