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

data class SmartShuffleRow(
    val uri: String,
    val uid: String,
    val enhanced: Boolean,
)

object SmartShuffleSource {

    private const val LIKED_SONGS = "spotify:collection:tracks"
    private const val LENS_ENHANCE = "enhance"
    private const val BASE = "https://spclient.wg.spotify.com"
    private const val ENHANCED_PROVIDER = "enhanced_recommendation"

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

    suspend fun reset(context: Context, token: String, spclient: String, lensUri: String): List<SmartShuffleRow> {
        val response = post(context, token, spclient, lensUri, "reset") ?: return emptyList()
        if (!response.ok) return emptyList()
        val rows = runCatching { extractRows(response.bytes) }.getOrElse {
            log.w("signal 'reset': the enhanced context did not parse (${it.message})")
            emptyList()
        }
        log.i("signal 'reset': ${rows.size} row(s), ${rows.count { it.enhanced }} enhanced")
        return rows
    }

    suspend fun tap(context: Context, token: String, spclient: String, lensUri: String, uid: String): List<SmartShuffleRow> {
        val response = post(context, token, spclient, lensUri, "tap", uid) ?: return emptyList()
        if (!response.ok) return emptyList()
        return runCatching { extractRows(response.bytes) }.getOrElse { emptyList() }
    }

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
        val response = post(context, token, spclient, lensUri, identifier, uid)
        val ok = response?.ok == true
        log.i("signal '$identifier' -> ${response?.code ?: "no response"} ok=$ok")
        return ok
    }

    private suspend fun post(
        context: Context,
        token: String,
        spclient: String,
        lensUri: String,
        identifier: String,
        uid: String = "",
    ): Net.Blob? {
        if (spclient.isBlank() || lensUri.isBlank()) return null
        val type = lensUri.substringAfter("spotify:").substringBefore(":")
        val id = lensUri.substringAfterLast(":")
        if (type.isBlank() || id.isBlank()) return null
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
        val headers = mapOf("Authorization" to "Bearer $token")
        return withContext(Dispatchers.IO) {
            Net.postBlob(url, body.toByteArray(), "application/json", headers, tag = "smart-shuffle/$identifier")
        }
    }

    /**
     * The reset answer is a binary context proto: one message per row, each
     * carrying its track uri as a plain string field and the recommendations
     * marked with provider 'enhanced_recommendation' somewhere in their
     * metadata. The walk keeps document order so the list is the play order.
     */
    private fun extractRows(bytes: ByteArray): List<SmartShuffleRow> {
        val rows = mutableListOf<SmartShuffleRow>()
        walk(bytes, 0, bytes.size, rows, 0)
        return rows
    }

    private fun walk(bytes: ByteArray, from: Int, to: Int, rows: MutableList<SmartShuffleRow>, depth: Int) {
        if (depth > 8) return
        var i = from
        var uri = ""
        var uid = ""
        while (i < to) {
            val tag = varint(bytes, i) ?: return
            i = tag.second
            when (tag.first and 0x07) {
                0 -> {
                    val value = varint(bytes, i) ?: return
                    i = value.second
                }
                1 -> i += 8
                5 -> i += 4
                2 -> {
                    val length = varint(bytes, i) ?: return
                    i = length.second
                    val end = i + length.first
                    if (end > to) return
                    val text = text(bytes, i, end)
                    if (text != null) {
                        if (text.startsWith("spotify:track:")) uri = text
                    } else if (tag.first ushr 3 == 2 && uid.isBlank()) {
                        uid = Base64.encodeToString(bytes.copyOfRange(i, end), Base64.NO_WRAP or Base64.URL_SAFE)
                    } else {
                        walk(bytes, i, end, rows, depth + 1)
                    }
                    i = end
                }
                else -> return
            }
        }
        if (uri.isNotBlank()) {
            rows.add(SmartShuffleRow(uri, uid, contains(bytes, from, to, ENHANCED_PROVIDER)))
        }
    }

    private fun text(bytes: ByteArray, from: Int, to: Int): String? {
        if (to - from <= 0) return null
        val out = String(bytes, from, to - from, Charsets.UTF_8)
        if (out.indexOf('\uFFFD') >= 0) return null
        out.forEach { if (it < ' ') return null }
        return out
    }

    private fun varint(bytes: ByteArray, at: Int): Pair<Int, Int>? {
        var value = 0
        var shift = 0
        var i = at
        while (i < bytes.size) {
            val raw = bytes[i].toInt()
            value = value or ((raw and 0x7F) shl shift)
            i++
            if (raw >= 0) return value to i
            shift += 7
            if (shift > 28) return null
        }
        return null
    }

    private fun contains(bytes: ByteArray, from: Int, to: Int, needle: String): Boolean {
        val target = needle.toByteArray(Charsets.UTF_8)
        if (target.isEmpty() || to - from < target.size) return false
        outer@ for (i in from..to - target.size) {
            for (j in target.indices) {
                if (bytes[i + j] != target[j]) continue@outer
            }
            return true
        }
        return false
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
