package com.spotiduck.backend.connect.dealer

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

    val connectionId: String
        get() = runCatching {
            val raw = uri.removePrefix(DealerUri.CONNECTIONS)
            String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP))
        }.getOrDefault("")
            .filterNot { it.isISOControl() }
            .trim()

    val connectionIdRaw: String
        get() = headers["Spotify-Connection-Id"]
            .orEmpty()
            .ifBlank { uri.removePrefix(DealerUri.CONNECTIONS) }

    val firstJson: JSONObject?
        get() = payloads.firstOrNull()?.let { runCatching { JSONObject(it) }.getOrNull() }
}
