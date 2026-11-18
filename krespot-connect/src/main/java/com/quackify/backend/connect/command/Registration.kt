/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.connect.command

import android.content.Context
import com.quackify.backend.connect.ConnectHeaders
import com.quackify.backend.connect.cluster.Device
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
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

    const val REASON_NEW_DEVICE = "NEW_DEVICE"

    const val REASON_STATE_CHANGED = "PLAYER_STATE_CHANGED"

    const val REASON_BECAME_INACTIVE = "BECAME_INACTIVE"

    /**
     * Reads the account's connect cluster outright, the way the web player
     * does with GET connect-state/v1/cluster: it needs no dealer connection
     * and no connection id, so it is the fastest way to learn who is playing
     * right after launch - the first dealer frame can take seconds to arrive.
     */
    suspend fun cluster(
        context: Context,
        spclient: String,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/cluster"
        val headers = ConnectHeaders.of(context) + mapOf("Accept" to "application/json")
        val response = Net.request("GET", url, headers, null, tag = "cluster-get")
        log.i("cluster read -> ${response?.code ?: "no response"} ${response?.bytes ?: 0}B")
        return response
    }

    /**
     * Registers this device as a real connect-state player by PUTting its
     * device state, the same way native clients (librespot & friends) do.
     * The hidden hobs_ observer never shows up in the account's device list
     * and the track-playback register is what the web player uses, so this
     * PUT is what actually makes the phone selectable in "Listening on" and
     * dealer connection the server routes commands over. The answer is the
     * cluster, like the observer PUT.
     *
     * While this device plays, the same PUT carries `is_active: true` and a
     * full `player_state` inside the device block - the shape librespot
     * publishes - so the server makes this phone the account's active player
     * and every other client follows it.
     */
    suspend fun connectState(
        context: Context,
        spclient: String,
        deviceId: String,
        connectionId: String,
        reason: String = REASON_NEW_DEVICE,
        isActive: Boolean = false,
        playerState: JSONObject? = null,
        startedPlayingAt: Long = 0L,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/devices/$deviceId"
        val headers = ConnectHeaders.of(context) + mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "X-Spotify-Connection-Id" to connectionId,
        )
        val device = JSONObject().put("device_info", deviceInfo(deviceId))
        if (playerState != null) device.put("player_state", playerState)
        val body = JSONObject()
            .put("member_type", "CONNECT_STATE")
            .put("put_state_reason", reason)
            .put("is_active", isActive)
            .put("client_side_timestamp", System.currentTimeMillis())
        if (startedPlayingAt > 0L) body.put("started_playing_at", startedPlayingAt)
        body.put("device", device)
        val response = Net.request("PUT", url, headers, body.toString(), tag = "member")
        log.i("member $deviceId -> ${response?.code ?: "no response"} ${response?.bytes ?: 0}B " +
                "active=$isActive reason=$reason state=${playerState != null}"
        )
        return response
    }

    /**
     * Removes this device from the connect cluster, the way librespot does
     * on shutdown, so "Listening on" on the other clients drops the phone
     * instead of showing a ghost that never plays again.
     */
    suspend fun unregister(
        context: Context,
        spclient: String,
        deviceId: String,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/devices/$deviceId"
        val response = Net.request(
            "DELETE",
            url,
            ConnectHeaders.of(context) + mapOf("Accept" to "application/json"),
            null,
            tag = "member",
        )
        log.i("member delete $deviceId -> ${response?.code ?: "no response"}")
        return response
    }

    /**
     * The logout-time counterpart of the register handshake: drops every
     * entry this device holds in an account's cluster, the sequence the web
     * player walks on logout. Credentials are passed in explicitly — the
     * account being signed out is not necessarily the active one, so
     * ConnectHeaders (bound to the active session) must not be used here.
     * Returns how many of the three entries the server accepted.
     */
    suspend fun signOutDevices(
        spclient: String,
        deviceId: String,
        token: String,
        clientToken: String,
    ): Int {
        if (spclient.isBlank() || token.isBlank()) {
            log.w("device sign-out skipped, spclient=${spclient.isNotBlank()} token=${token.isNotBlank()}")
            return 0
        }
        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "client-token" to clientToken,
            "Accept" to "application/json",
            "app-platform" to "WebPlayer",
            "spotify-app-version" to ConnectHeaders.APP_VERSION,
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
            "User-Agent" to Net.DESKTOP_UA,
        )
        var removed = 0

        // The hidden observer entry, the one the web player drops first.
        val observerId = observerId(deviceId)
        val observer = Net.requestOnce(
            "DELETE",
            "https://$spclient/connect-state/v1/devices/$observerId",
            headers,
            null,
            tag = "signout-observer",
        )
        log.i("sign-out observer $observerId -> ${observer?.code ?: "no response"}")
        if (observer?.ok == true) removed++

        // The member entry this app publishes librespot-style.
        val member = Net.requestOnce(
            "DELETE",
            "https://$spclient/connect-state/v1/devices/$deviceId",
            headers,
            null,
            tag = "signout-member",
        )
        log.i("sign-out member $deviceId -> ${member?.code ?: "no response"}")
        if (member?.ok == true) removed++

        // The track-playback device, deregistered with the same body the
        // web player sends (a JSON body labeled text/plain, verbatim).
        val body = JSONObject()
            .put("seq_num", 1)
            .put("state_ref", JSONObject.NULL)
            .put(
                "sub_state",
                JSONObject()
                    .put("playback_speed", 1)
                    .put("format", "file_urls_external")
                    .put("is_video_on", false),
            )
            .put("debug_source", "deregister")
            .toString()
        val playback = Net.requestOnce(
            "DELETE",
            "https://$spclient/track-playback/v1/devices/$deviceId",
            headers + mapOf("Content-Type" to "text/plain;charset=UTF-8"),
            body,
            tag = "signout-playback",
        )
        log.i("sign-out track-playback $deviceId -> ${playback?.code ?: "no response"}")
        if (playback?.ok == true) removed++
        return removed
    }

    suspend fun player(
        context: Context,
        spclient: String,
        deviceId: String,
        connectionId: String,
    ): Net.Response? {
        val url = "https://$spclient/track-playback/v1/devices"
        val info = trackDevice(deviceId)
        val body = JSONObject()
            .put("device", info)
            .put("outro_endcontent_snooping", false)
            .put("connection_id", connectionId)
            .put("client_version", SDK_VERSION)
            .put("volume", 65535)
            .toString()
        val headers = ConnectHeaders.of(context) + mapOf(
            "Accept" to "application/json",
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
            "User-Agent" to Net.DESKTOP_UA,
        )
        val response = Net.postJson(url, body, headers, tag = "register")
        val parsed = response?.json()
        log.i("player $deviceId -> ${response?.code ?: "no response"} " +
                "seq=${parsed?.optInt("initial_seq_num") ?: -1} " +
                "license=${parsed?.optString("effective_license") ?: "-"}"
        )
        if (response != null && !response.ok) {
            log.w("player register failed, connectionId(${connectionId.length}B)=" +
                    "'${connectionId.takeLast(10)}' body=${response.body.take(180)}"
            )
        }
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

    /** The device block of the track-playback register, verbatim web-player shape. */
    private fun trackDevice(deviceId: String): JSONObject = JSONObject()
        .put("brand", "spotify")
        .put("model", "web_player")
        .put("name", "Quackify")
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

    /** The DeviceInfo block of a connect-state member, the way native clients publish it. */
    private fun deviceInfo(deviceId: String): JSONObject = JSONObject()
        .put("can_play", true)
        .put("volume", 65535)
        .put("name", "Quackify")
        .put("device_id", deviceId)
        .put("device_type", "COMPUTER")
        .put("device_software_version", ConnectHeaders.APP_VERSION)
        .put("client_id", Device.CLIENT_ID)
        .put("brand", "spotify")
        .put("model", "web_player")
        .put("capabilities", capabilities())

    private fun capabilities(): JSONObject = JSONObject()
        .put("can_be_player", true)
        .put("is_controllable", true)
        .put("is_observable", true)
        .put("needs_full_player_state", true)
        .put("supported_types", supportedTypes())

    private fun supportedTypes(): JSONArray = JSONArray()
        .put("audio/track")
        .put("video/track")
        .put("audio/episode")
        .put("audio/ad")
        .put("audio/chapter")
}
