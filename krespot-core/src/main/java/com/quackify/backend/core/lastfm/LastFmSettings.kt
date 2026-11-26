package com.quackify.backend.core.lastfm

import android.content.Context

data class LastFmSettings(
    val scrobblingEnabled: Boolean = false,
    val nowPlayingEnabled: Boolean = true,
    val syncLikesEnabled: Boolean = false,
    val thresholdPercent: Int = 50,
    val minimumTrackDurationMs: Long = 30_000L,
    val maximumScrobbleTimeMs: Long = 240_000L
) {
    companion object {
        private const val PREFS = "lastfm_settings"

        fun load(context: Context): LastFmSettings {
            val p = context.applicationContext.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE
            )
            return LastFmSettings(
                scrobblingEnabled = p.getBoolean("scrobbling_enabled", false),
                nowPlayingEnabled = p.getBoolean("now_playing_enabled", true),
                syncLikesEnabled = p.getBoolean("sync_likes_enabled", false),
                thresholdPercent = p.getInt("threshold_percent", 50)
                    .coerceIn(1, 100),
                minimumTrackDurationMs = p.getLong(
                    "minimum_track_duration_ms", 30_000L
                ).coerceAtLeast(0L),
                maximumScrobbleTimeMs = p.getLong(
                    "maximum_scrobble_time_ms", 240_000L
                ).coerceAtLeast(1L)
            )
        }

        fun save(context: Context, settings: LastFmSettings) {
            context.applicationContext.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE
            ).edit()
                .putBoolean("scrobbling_enabled", settings.scrobblingEnabled)
                .putBoolean("now_playing_enabled", settings.nowPlayingEnabled)
                .putBoolean("sync_likes_enabled", settings.syncLikesEnabled)
                .putInt(
                    "threshold_percent",
                    settings.thresholdPercent.coerceIn(1, 100)
                )
                .putLong(
                    "minimum_track_duration_ms",
                    settings.minimumTrackDurationMs.coerceAtLeast(0L)
                )
                .putLong(
                    "maximum_scrobble_time_ms",
                    settings.maximumScrobbleTimeMs.coerceAtLeast(1L)
                )
                .apply()
        }
    }
}
