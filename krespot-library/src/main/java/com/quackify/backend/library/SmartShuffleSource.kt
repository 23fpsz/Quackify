package com.quackify.backend.library

import android.content.Context
import android.util.Base64
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.net.Pathfinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.LIBRARY)

data class SmartShuffleEligibility(
    val canSmartShuffle: Boolean,
    val lensUri: String,
)

object SmartShuffleSource {

    private const val LIKED_SONGS = "spotify:collection:tracks"
    private const val LENS_ENHANCE = "enhance"
    private const val BASE = "https://spclient.wg.spotify.com"

    suspend fun eligibility(context: Context, token: String, contextUri: String): SmartShuffleEligibility {
        val uri = lensCandidate(contextUri)
        val fallback = SmartShuffleEligibility(canSmartShuffle = false, lensUri = "")
        if (uri.isBlank()) return fallback
        val variables = JSONObject().put("uris", JSONArray().put(uri)).toString()
        val response = withContext(Dispatchers.IO) {
            runCatching { Pathfinder.queryPost(context, token, "smartShuffle", variables) }.getOrNull()
        } ?: return fallback
        val node = response
            .optJSONObject("data")
            ?.optJSONArray("lookup")?.optJSONObject(0)
            ?.optJSONObject("data")
            ?.optJSONObject("smartShuffle")
        if (node == null) {
            log.w("smartShuffle: no node for '${uri.takeLast(24)}'")
            return fallback
        }
        val lens = node.optString("uri").ifBlank { uri }
        val available = node.optBoolean("available", false)
        log.i("smartShuffle: '$uri' available=$available lens='${lens.takeLast(24)}'")
        return SmartShuffleEligibility(canSmartShuffle = available, lensUri = lens)
    }

    suspend fun reset(context: Context, token: String, spclient: String, lensUri: String): Boolean =
        send(context, token, spclient, lensUri, "reset")

    suspend fun tap(context: Context, token: String, spclient: String, lensUri: String, uid: String): Boolean =
        send(context, token, spclient, lensUri, "tap", uid)

    suspend fun plus(context: Context, token: String, spclient: String, lensUri: String, uid: String): Boolean =
        send(context, token, spclient, lensUri, "plus", uid)

    suspend fun minus(context: Context, token: String, spclient: String, lensUri: String, uid: String): Boolean =
        send(context, token, spclient, lensUri, "minus", uid)

    private suspend fun send(
        context: Context,
        token: String,
        spclient: String,
        lensUri: String,
        identifier: String,
        uid: String = "",
    ): Boolean {
        if (spclient.isBlank() || lensUri.isBlank()) return false
        val type = lensUri.substringAfter("spotify:").substringBefore(":")
        val id = lensUri.substringAfterLast(":")
        if (type.isBlank() || id.isBlank()) return false
        val base = when (type) {
            "playlist" -> "$BASE/playlist/v2/playlist/$id"
            else -> "$BASE/$type/$id"
        }
        val url = "$base/signals?spotify-apply-lenses=$LENS_ENHANCE"
        val signal = JSONObject()
            .put("identifier", identifier)
            .put("data", Base64.encodeToString(signalData(uid), Base64.NO_WRAP))
        val body = JSONObject()
            .put("emittedSignals", JSONArray().put(signal))
            .toString()
        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
        )
        val response = withContext(Dispatchers.IO) {
            Net.postJson(url, body, headers, tag = "smart-shuffle/$identifier")
        }
        val ok = response?.ok == true
        log.i("signal '$identifier' on '$id' -> ${response?.code ?: "no response"} ok=$ok")
        return ok
    }

    private fun signalData(uid: String): ByteArray {
        val lens = LENS_ENHANCE.toByteArray(Charsets.UTF_8)
        val item = itemId(uid)
        var size = 1 + varintLen(lens.size) + lens.size
        if (item.isNotEmpty()) size += 1 + varintLen(item.size) + item.size
        val out = java.io.ByteArrayOutputStream(size)
        out.write(0x0A)
        out.writeVarint(lens.size)
        out.write(lens)
        if (item.isNotEmpty()) {
            out.write(0x1A)
            out.writeVarint(item.size)
            out.write(item)
        }
        return out.toByteArray()
    }

    private fun itemId(uid: String): ByteArray = runCatching {
        Base64.decode(uid, Base64.NO_WRAP or Base64.URL_SAFE)
    }.getOrElse { if (uid.isBlank()) ByteArray(0) else uid.toByteArray(Charsets.UTF_8) }

    private fun varintLen(value: Int): Int {
        var v = value
        var n = 1
        while (v >= 0x80) {
            v = v shr 7
            n++
        }
        return n
    }

    private fun java.io.ByteArrayOutputStream.writeVarint(value: Int) {
        var v = value
        while (v >= 0x80) {
            write((v and 0x7F) or 0x80)
            v = v shr 7
        }
        write(v)
    }

    private fun lensCandidate(contextUri: String): String = when {
        contextUri.startsWith("spotify:playlist:") -> contextUri
        contextUri.startsWith("spotify:user:") && contextUri.endsWith(":collection") -> LIKED_SONGS
        contextUri == LIKED_SONGS -> LIKED_SONGS
        else -> ""
    }
}
