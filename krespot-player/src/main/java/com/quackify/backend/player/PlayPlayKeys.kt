package com.quackify.backend.player

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.player.playplay.PlayPlayLicenseRequest
import com.quackify.backend.player.playplay.PlayPlayRecordDecoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val log = Logs.of(Logs.Module.SOURCE)

internal object PlayPlayKeys {

    private const val KEY_URL = "https://spclient.wg.spotify.com/playplay/v1/key/"

    private val mutex = Mutex()

    private val cache = HashMap<String, ByteArray>()

    @Volatile
    private var rateLimitedUntil = 0L

    val isRateLimited: Boolean
        get() = System.currentTimeMillis() < rateLimitedUntil

    suspend fun key(context: Context, fileId: String): ByteArray? {
        if (!fileId.matches(Regex("[0-9a-fA-F]{40}"))) {
            log.w("playplay invalid file id '${fileId.take(12)}'")
            return null
        }
        mutex.withLock { cache[fileId] }?.let { return it }
        if (System.currentTimeMillis() < rateLimitedUntil) return null
        val blob = Net.postBlob(
            "$KEY_URL$fileId",
            PlayPlayLicenseRequest.encode(),
            "application/x-protobuf",
            mapOf(
                "Authorization" to "Bearer ${EncoreAuth.token(context)}",
                "Accept" to "application/x-protobuf",
                "User-Agent" to Net.DESKTOP_UA,
            ),
            tag = "playplay",
        )
        if (blob != null && blob.code == 429) {
            rateLimitedUntil = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
            log.w("playplay key rate limited, cooling down for ${RATE_LIMIT_COOLDOWN_MS / 1000}s")
            return null
        }
        if (blob?.ok != true) {
            log.w("playplay key failed '${fileId.take(12)}'")
            return null
        }
        val key = runCatching {
            PlayPlayRecordDecoder.decodeObfuscatedKey(
                PlayPlayRecordDecoder.parseObfuscatedKey(blob.bytes)
            )
        }.getOrElse {
            log.w("playplay decode failed '${fileId.take(12)}' ${it.javaClass.simpleName}: ${it.message}")
            return null
        }
        mutex.withLock { cache[fileId] = key }
        log.i("playplay key '${fileId.take(12)}' ${key.size}B (${blob.bytes.size}B response)")
        return key
    }

    private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
}
