package com.spotiduck.backend.core.net

import android.content.Context
import com.spotiduck.backend.core.auth.SecurePrefs
import com.spotiduck.backend.core.auth.SpotifyAuth
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.util.UUID
import com.spotiduck.backend.core.log.Logs

private val log = Logs.of(Logs.Module.CLIENT_TOKEN)

object ClientToken {

    private const val ENDPOINT = "https://clienttoken.spotify.com/v1/clienttoken"
    private const val CLIENT_ID = "d8a5ed958d274c2e8ee717e6a4b0971d"
    private const val CLIENT_VERSION = "1.3.8.509.g26b6eac7300d"

    private const val KEY_REFRESH = "client_token_refresh"
    private const val KEY_MINTED_AT = "client_token_minted_at"
    private const val KEY_DEVICE = "client_device_id"

    private const val SKEW_MS = 60_000L

    private val mutex = Mutex()

    suspend fun get(context: Context, force: Boolean = false): String {
        mutex.lock()
        try {
            val stored = SpotifyAuth.clientToken(context)
            if (!force && stored.isNotBlank() && !stale(context)) return stored
            val minted = mint(context)
            if (minted.isNotBlank()) return minted
            if (stored.isNotBlank()) {
                log.w("mint failed, keeping the harvested token (${stored.length}B)")
            }
            return stored
        } finally {
            mutex.unlock()
        }
    }

    fun deviceId(context: Context): String {
        val prefs = SecurePrefs.get(context)
        prefs.getString(KEY_DEVICE, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE, generated).apply()
        log.i("device id minted $generated")
        return generated
    }

    fun invalidate(context: Context) {
        log.w("client-token dropped, the next call mints a new one")
        SecurePrefs.get(context).edit()
            .remove(KEY_REFRESH)
            .remove(KEY_MINTED_AT)
            .apply()
        SpotifyAuth.saveClientToken(context, "", 0L)
    }

    fun remainingMs(context: Context): Long {
        val refreshAt = SecurePrefs.get(context).getLong(KEY_REFRESH, 0L)
        if (refreshAt <= 0L) return -1L
        return refreshAt - System.currentTimeMillis()
    }

    fun ageMs(context: Context): Long {
        val at = SecurePrefs.get(context).getLong(KEY_MINTED_AT, 0L)
        if (at <= 0L) return -1L
        return System.currentTimeMillis() - at
    }

    private fun stale(context: Context): Boolean {
        val refreshAt = SecurePrefs.get(context).getLong(KEY_REFRESH, 0L)
        if (refreshAt <= 0L) return SpotifyAuth.clientTokenRemainingMs(context) <= SKEW_MS
        return System.currentTimeMillis() >= refreshAt - SKEW_MS
    }

    private suspend fun mint(context: Context): String {
        val device = deviceId(context)
        val body = JSONObject()
            .put(
                "client_data",
                JSONObject()
                    .put("client_version", CLIENT_VERSION)
                    .put("client_id", CLIENT_ID)
                    .put(
                        "js_sdk_data",
                        JSONObject()
                            .put("device_brand", "unknown")
                            .put("device_model", "unknown")
                            .put("os", "windows")
                            .put("os_version", "NT 10.0")
                            .put("device_id", device)
                            .put("device_type", "computer"),
                    ),
            )
            .toString()
        val response = Net.postJson(
            url = ENDPOINT,
            json = body,
            headers = mapOf(
                "Accept" to "application/json",
                "User-Agent" to Net.DESKTOP_UA,
            ),
            tag = "clienttoken",
        ) ?: return ""
        val json = response.json()
        if (json == null) {
            log.w("mint http=${response.code} not-json ${response.body.take(200)}")
            return ""
        }
        val granted = json.optJSONObject("granted_token")
        val token = granted?.optString("token").orEmpty()
        if (token.isBlank()) {
            log.w("mint http=${response.code} type=${json.optString("response_type")} no token " +
                    response.body.take(200)
            )
            return ""
        }
        val expires = granted?.optLong("expires_after_seconds", 0L) ?: 0L
        val refresh = granted?.optLong("refresh_after_seconds", 0L) ?: 0L
        val now = System.currentTimeMillis()
        val expiresAt = if (expires > 0L) now + expires * 1000L else 0L
        val refreshAt = if (refresh > 0L) now + refresh * 1000L else expiresAt
        SpotifyAuth.saveClientToken(context, token, expiresAt)
        SecurePrefs.get(context).edit()
            .putLong(KEY_REFRESH, refreshAt)
            .putLong(KEY_MINTED_AT, now)
            .apply()
        log.i("minted ${token.length}B expires=${if (expires > 0L) "${expires}s" else "?"} " +
                "refresh=${if (refresh > 0L) "${refresh}s" else "?"} device=${device.take(8)}…"
        )
        return token
    }
}
