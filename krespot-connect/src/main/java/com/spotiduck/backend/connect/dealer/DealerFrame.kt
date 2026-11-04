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

    /**
     * The connection id the server wants in X-Spotify-Connection-Id and in the
     * track-playback register body. The `Spotify-Connection-Id` header of the
     * first dealer frame is the authoritative form, but some dealer builds
     * omit it, and then the only copy lives base64-encoded in the frame uri.
     *
     * Whatever the source, the id must stay printable ascii: the dealer
     * appends raw binary bytes to it, and a mangled id (control characters,
     * U+FFFD replacement chars or the undecoded base64 itself) makes
     * track-playback/v1/devices answer 500 and the device never shows up in
     * the account's "Listening on" list.
     */
    val connectionId: String
        get() {
            val header = headers.entries
                .firstOrNull { it.key.contains("connection-id", ignoreCase = true) }
                ?.value.orEmpty()
            val candidate = header.ifBlank {
                runCatching {
                    val raw = uri.removePrefix(DealerUri.CONNECTIONS)
                    String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP))
                }.getOrDefault("")
            }
            return candidate.filter { it.code in 0x21..0x7E }.trim()
        }

    val firstJson: JSONObject?
        get() = payloads.firstOrNull()?.let { runCatching { JSONObject(it) }.getOrNull() }
}
