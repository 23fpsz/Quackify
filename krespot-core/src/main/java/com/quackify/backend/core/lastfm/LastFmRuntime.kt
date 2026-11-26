package com.quackify.backend.core.lastfm

object LastFmRuntime {
    @Volatile private var apiKey: String = ""
    @Volatile private var sharedSecret: String = ""

    fun configure(key: String, secret: String) {
        apiKey = key.trim()
        sharedSecret = secret.trim()
    }

    fun isConfigured(): Boolean =
        apiKey.isNotBlank() && sharedSecret.isNotBlank()

    fun authorizationUrl(token: String): String {
        check(isConfigured()) { "Last.fm API credentials belum dikonfigurasi" }
        require(token.isNotBlank()) { "Last.fm token kosong" }
        return "https://www.last.fm/api/auth/?api_key=" +
            java.net.URLEncoder.encode(apiKey, "UTF-8") +
            "&token=" + java.net.URLEncoder.encode(token, "UTF-8")
    }

    fun client(): LastFmApiClient {
        check(isConfigured()) {
            "Last.fm API credentials belum dikonfigurasi"
        }
        return LastFmApiClient(apiKey, sharedSecret)
    }
}
