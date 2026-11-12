package com.quackify.backend.connect.dealer

import android.util.Base64
import org.json.JSONObject

object DealerUri {
    const val CONNECTIONS = "hm://pusher/v1/connections/"
    const val TRACK_COMMAND = "hm://track-playback/v1/command"
    const val CLUSTER = "hm://connect-state/v1/cluster"
    const val RESUME_POINT = "hm://herodotus/uri/spotify:list:play-history:v1/resume-point-revision/"
}

data class DealerFrame(
    val uri: String,
    val headers: Map<String, String>,
    val payloads: List<String>,
) {
    val isConnection: Boolean get() = uri.startsWith(DealerUri.CONNECTIONS)

    /**
     * The connection id exactly as the dealer handed it out: the
     *
     * track-playback register body want this form. Decoding it and sending
     * the plain uuid is what makes every player command answer
     * `410 COMMAND_FAILED`: the server binds the routed connection by the
     * base64 string, so a decoded one names a connection that is not ours.
     */
    val connectionId: String
        get() = headers.entries
            .firstOrNull { it.key.contains("connection-id", ignoreCase = true) }
            ?.value.orEmpty()
            .ifBlank { uri.removePrefix(DealerUri.CONNECTIONS) }
            .filter { it.code in 0x21..0x7E }
            .trim()

    /** The same id decoded, for log lines only. */
    val decodedConnectionId: String
        get() = runCatching {
            String(Base64.decode(connectionId, Base64.URL_SAFE or Base64.NO_WRAP))
        }.getOrDefault("")
            .filter { it.code in 0x21..0x7E }
            .trim()

    val firstJson: JSONObject?
        get() = payloads.firstOrNull()?.let { runCatching { JSONObject(it) }.getOrNull() }
}
