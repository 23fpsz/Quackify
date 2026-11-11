package com.quackify.backend.connect.command

import org.json.JSONObject

/**
 * A command another player addressed to this device over the dealer, in the
 * connect-state v1 JSON dialect: {"message_id":..,"sent_by_device_id":..,
 * "command":{"endpoint":"pause"|"resume"|"seek_to"|...}}. The payload travels
 * base64+gzip wrapped, the dealer unwraps it before this parser runs.
 */
data class RemoteCommand(
    val endpoint: String,
    val messageId: Int,
    val sentByDeviceId: String,
    val value: Boolean?,
    val seekToMs: Long,
    val trackUri: String,
    val shuffle: Boolean?,
    val repeatContext: Boolean?,
    val repeatTrack: Boolean?,
) {
    val isTransfer: Boolean get() = endpoint == "transfer"
    val isPlay: Boolean get() = endpoint == "play"
}

object RemoteCommandParser {

    private fun JSONObject.text(name: String): String =
        optString(name).takeIf { it.isNotBlank() && it != "null" }.orEmpty()

    fun parse(payload: String): RemoteCommand? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val command = root.optJSONObject("command") ?: return null
        val endpoint = command.text("endpoint")
        if (endpoint.isBlank()) return null
        return RemoteCommand(
            endpoint = endpoint,
            messageId = root.optInt("message_id", 0),
            sentByDeviceId = root.text("sent_by_device_id"),
            value = command.optBooleanOrNull("value"),
            seekToMs = command.optLong("value", -1L).takeIf { endpoint == "seek_to" && it >= 0L }
                ?: command.optLong("position", -1L).takeIf { endpoint == "seek_to" && it >= 0L }
                ?: -1L,
            trackUri = command.optJSONObject("track")?.text("uri").orEmpty(),
            shuffle = command.optBooleanOrNull("shuffling_context"),
            repeatContext = command.optBooleanOrNull("repeating_context"),
            repeatTrack = command.optBooleanOrNull("repeating_track"),
        )
    }

    private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
        when {
            isNull(name) || !has(name) -> null
            else -> optBoolean(name)
        }
}
