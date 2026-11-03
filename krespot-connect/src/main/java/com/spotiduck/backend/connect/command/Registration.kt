package com.spotiduck.backend.connect.command

import android.content.Context
import com.spotiduck.backend.connect.ConnectHeaders
import com.spotiduck.backend.core.log.Logs
import com.spotiduck.backend.core.net.Net
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.REGISTER)

private const val SDK_VERSION = "harmony:4.81.1-1f3d48b7f"

object Registration {

    fun observerId(deviceId: String): String = "hobs_$deviceId".take(40)

    suspend fun observer(
        context: Context,
        spclient: String,
        deviceId: String,
        connectionId: String,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/devices/${observerId(deviceId)}"
        val headers = ConnectHeaders.of(context) + mapOf(
            "Accept" to "application/json",
            "X-Spotify-Connection-Id" to connectionId,
        )
        val body = JSONObject()
            .put("member_type", "CONNECT_STATE")
            .put(
                "device",
                JSONObject().put(
                    "device_info",
                    JSONObject().put(
                        "capabilities",
                        JSONObject()
                            .put("can_be_player", false)
                            .put("hidden", true)
                            .put("needs_full_player_state", true),
                    ),
                ),
            )
            .toString()
        val response = Net.request("PUT", url, headers, body, tag = "observer")
        log.i("observer ${observerId(deviceId)} -> ${response?.code ?: "no response"} ${response?.bytes ?: 0}B"
        )
        return response
    }

    suspend fun player(
        context: Context,
        spclient: String,
        deviceId: String,
        connectionId: String,
    ): Net.Response? {
        val url = "https://$spclient/track-playback/v1/devices"
        val info = deviceInfo(deviceId)
        val body = JSONObject()
            .put("device", info)
            .put("outro_endcontent_snooping", false)
            .put("connection_id", connectionId)
            .put("client_version", SDK_VERSION)
            .put("volume", 65535)
            .toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = "register")
        val parsed = response?.json()
        log.i("player $deviceId -> ${response?.code ?: "no response"} " +
                "seq=${parsed?.optInt("initial_seq_num") ?: -1} " +
                "license=${parsed?.optString("effective_license") ?: "-"}"
        )
        if (response?.ok == true) {
            val updated = Net.request(
                "PUT",
                "$url/$deviceId",
                ConnectHeaders.of(context) + mapOf("Content-Type" to "application/json"),
                info.toString(),
                tag = "devinfo",
            )
            log.i("device info -> ${updated?.code ?: "no response"}")
        }
        return response
    }

    fun initialSeq(response: Net.Response?): Int =
        response?.json()?.optInt("initial_seq_num") ?: -1

    private fun deviceInfo(deviceId: String): JSONObject = JSONObject()
        .put("brand", "spotify")
        .put("model", "web_player")
        .put("name", "SpotiDuck")
        .put("device_id", deviceId)
        .put("device_type", "COMPUTER")
        .put("platform_name", "web_player")
        .put("platform_identifier", "web_player windows 10;chrome 150.0.0.0;desktop")
        .put("is_group", false)
        .put("is_public", false)
        .put("correlation_id", deviceId)
        .put("client_version", SDK_VERSION)
        .put("metadata", JSONObject())
        .put("capabilities", capabilities())

    private fun capabilities(): JSONObject = JSONObject()
        .put("can_be_player", true)
        .put("is_controllable", true)
        .put("is_observable", true)
        .put("needs_full_player_state", true)
        .put("supported_types", supportedTypes())

    private fun supportedTypes(): JSONArray = JSONArray()
        .put("audio/track")
        .put("audio/episode")
        .put("audio/ad")
        .put("audio/chapter")
}
