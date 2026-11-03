package com.spotiduck.backend.connect

import android.content.Context
import com.spotiduck.backend.core.auth.SpotifyAuth

internal object ConnectHeaders {

    const val APP_VERSION = "1.3.8.509.g26b6eac7300d"

    fun of(context: Context): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${SpotifyAuth.token(context)}",
        "client-token" to SpotifyAuth.clientToken(context),
        "app-platform" to "WebPlayer",
        "spotify-app-version" to APP_VERSION,
    )
}
