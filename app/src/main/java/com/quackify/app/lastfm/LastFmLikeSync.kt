package com.quackify.app.lastfm

class LastFmLikeSync(
    private val api: LastFmApiClient
) {
    suspend fun setLoved(
        title: String,
        artist: String,
        loved: Boolean,
        sessionKey: String?
    ): Boolean {
        if (sessionKey.isNullOrBlank() ||
            title.isBlank() || artist.isBlank()
        ) return false

        return try {
            api.call(
                method = if (loved) "track.love" else "track.unlove",
                parameters = mapOf(
                    "sk" to sessionKey,
                    "track" to title,
                    "artist" to artist
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
