package com.spotiduck.backend.connect.cluster

import org.json.JSONObject
import com.spotiduck.backend.model.ConnectDevice
import com.spotiduck.backend.model.DeviceKind

private const val COMPUTER = "COMPUTER"
private const val SMARTPHONE = "SMARTPHONE"
private const val TABLET = "TABLET"
private const val SPEAKER = "SPEAKER"
private const val TV = "TV"
private const val GAME_CONSOLE = "GAME_CONSOLE"
private const val AUTOMOBILE = "AUTOMOBILE"
private const val AVR = "AVR"
private const val STB = "STB"
private const val AUDIO_DONGLE = "AUDIO_DONGLE"
private const val CAST_VIDEO = "CAST_VIDEO"
private const val CAST_AUDIO = "CAST_AUDIO"
private const val UNKNOWN = "UNKNOWN"

private fun JSONObject.devText(name: String): String =
    if (isNull(name)) "" else optString(name, "")

private fun JSONObject.devFlag(name: String): Boolean = devText(name).equals("true", ignoreCase = true)

internal fun kindOf(type: String): DeviceKind = when (type.uppercase()) {
    COMPUTER -> DeviceKind.COMPUTER
    SMARTPHONE -> DeviceKind.PHONE
    TABLET -> DeviceKind.TABLET
    SPEAKER -> DeviceKind.SPEAKER
    TV -> DeviceKind.TV
    AUTOMOBILE -> DeviceKind.CAR
    GAME_CONSOLE -> DeviceKind.CONSOLE
    AVR -> DeviceKind.RECEIVER
    STB -> DeviceKind.STICK
    AUDIO_DONGLE -> DeviceKind.DONGLE
    CAST_VIDEO, CAST_AUDIO -> DeviceKind.CAST
    UNKNOWN -> DeviceKind.UNKNOWN
    else -> DeviceKind.UNKNOWN
}

internal fun parseDevice(node: JSONObject, activeId: String, localId: String): ConnectDevice? {
    val id = node.devText("device_id")
    if (id.isBlank()) return null
    val caps = node.optJSONObject("capabilities") ?: JSONObject()
    val name = node.devText("name")
    return ConnectDevice(
        id = id,
        name = name.ifBlank { node.devText("model").ifBlank { "Unknown device" } },
        kind = kindOf(node.devText("device_type")),
        canPlay = node.devFlag("can_play"),
        volume = node.optInt("volume", 0),
        volumeSteps = caps.optInt("volume_steps", 0),
        isControllable = caps.devFlag("is_controllable"),
        disableVolume = caps.devFlag("disable_volume"),
        supportsTransfer = caps.devFlag("supports_transfer_command"),
        supportsLogout = caps.devFlag("supports_logout"),
        supportsCommandRequest = caps.devFlag("supports_command_request"),
        supportsSleepTimer = caps.devFlag("supports_remote_sleep_timer"),
        isObservable = caps.devFlag("is_observable"),
        audioQuality = caps.devText("supported_audio_quality"),
        brand = node.devText("brand"),
        model = node.devText("model"),
        softwareVersion = node.devText("device_software_version"),
        license = node.devText("license"),
        active = id == activeId,
        local = localId.isNotBlank() && id == localId,
    )
}

internal fun parseDevices(root: JSONObject, activeId: String, localId: String): List<ConnectDevice> {
    val map = root.optJSONObject("devices") ?: return emptyList()
    val keys = map.keys()
    val out = ArrayList<ConnectDevice>(map.length())
    while (keys.hasNext()) {
        val key = keys.next()
        val node = map.optJSONObject(key) ?: continue
        parseDevice(node, activeId, localId)?.let { out.add(it) }
    }
    return out
}