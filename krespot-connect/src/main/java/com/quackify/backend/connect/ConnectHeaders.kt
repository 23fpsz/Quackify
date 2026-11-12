package com.quackify.backend.connect

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth

internal object ConnectHeaders {

    const val APP_VERSION = "1.3.8.509.g26b6eac7300d"

    fun of(context: Context): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${EncoreAuth.token(context)}",
        "client-token" to EncoreAuth.clientToken(context),
        "app-platform" to "WebPlayer",
        "spotify-app-version" to APP_VERSION,
    )
}
