package com.quackify.backend.core.lastfm

object LastFmRuntime {
    fun isConfigured(apiKey: String, sharedSecret: String): Boolean =
        apiKey.isNotBlank() && sharedSecret.isNotBlank()

    fun authorizationUrl(token: String, apiKey: String): String {
        check(apiKey.isNotBlank()) { "Last.fm API key is not configured" }
        require(token.isNotBlank()) { "Last.fm token is empty" }
        return "https://www.last.fm/api/auth/?api_key=" +
            java.net.URLEncoder.encode(apiKey.trim(), "UTF-8") +
            "&token=" + java.net.URLEncoder.encode(token, "UTF-8")
    }

    fun client(apiKey: String, sharedSecret: String): LastFmApiClient {
        check(isConfigured(apiKey, sharedSecret)) {
            "Last.fm API credentials are not configured"
        }
        return LastFmApiClient(apiKey.trim(), sharedSecret.trim())
    }
}
