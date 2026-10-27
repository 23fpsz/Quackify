package com.spotiduck.compose.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SpotifyTokenProvider {

    private const val ACTIVE_SECRET_RAW = ",7/*F(\"rLJ2oxaKL^f+E1xvP@N"
    private const val VERSION = 61
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder().build()

    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiryMs: Long = 0L

    fun getSecretBytes(): ByteArray {
        val chars = ACTIVE_SECRET_RAW.toCharArray()
        val out = StringBuilder()
        for (i in chars.indices) {
            val c = chars[i].code
            val k = (i % 33) + 9
            val xored = c xor k
            out.append(xored.toString())
        }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    fun generateTotp(timestampMs: Long, secret: ByteArray): String {
        val counter = timestampMs / 1000L / 30L
        val counterBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(counterBytes)

        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val otp = binary % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    suspend fun getAccessToken(spDc: String, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedToken != null && now < (tokenExpiryMs - 60_000)) {
            return@withContext cachedToken!!
        }

        val secret = getSecretBytes()

        // 1. Fetch server time
        val timeReq = Request.Builder()
            .url(SERVER_TIME_URL)
            .header("User-Agent", USER_AGENT)
            .build()

        var serverTimeSec = System.currentTimeMillis() / 1000L
        httpClient.newCall(timeReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                serverTimeSec = json.optLong("serverTime", serverTimeSec)
            }
        }

        val clientTotp = generateTotp(now, secret)
        val serverTotp = generateTotp(serverTimeSec * 1000L, secret)

        // 2. Fetch Token
        val tokenUrl = "$TOKEN_URL?reason=transport&productType=web_player&totp=$clientTotp&totpServer=$serverTotp&totpVer=$VERSION"
        val tokenReq = Request.Builder()
            .url(tokenUrl)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "sp_dc=$spDc")
            .header("Referer", "https://open.spotify.com/")
            .header("Origin", "https://open.spotify.com")
            .build()

        httpClient.newCall(tokenReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Failed to acquire Spotify access token: HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("Empty token response")
            val json = JSONObject(body)
            val token = json.getString("accessToken")
            val expiresMs = json.optLong("accessTokenExpirationTimestampMs", now + 3600_000L)

            cachedToken = token
            tokenExpiryMs = expiresMs
            return@withContext token
        }
    }
}
