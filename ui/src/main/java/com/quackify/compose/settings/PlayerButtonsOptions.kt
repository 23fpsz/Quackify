package com.quackify.compose.settings

import android.content.Context
import com.quackify.backend.core.store.AppPrefs
import com.quackify.compose.R

enum class PlayerButtonAction(
    val token: String,
    val labelRes: Int,
    val iconRes: Int,
) {
    PREV("PREV", R.string.previous_track, R.drawable.encore_icon_skip_back_24),
    PLAY("PLAY", R.string.play_pause, R.drawable.encore_icon_play_24),
    NEXT("NEXT", R.string.next_track, R.drawable.encore_icon_skip_forward_24),
    FAV("FAV", R.string.favorite_like, R.drawable.encore_icon_heart_24),
    REPEAT("REPEAT", R.string.repeat_mode, R.drawable.encore_icon_repeat_24),
    SHUFFLE("SHUFFLE", R.string.shuffle_mode, R.drawable.encore_icon_shuffle_24),
    NONE("NONE", R.string.none_empty_slot, R.drawable.encore_icon_ban_24);

    companion object {
        fun fromToken(token: String): PlayerButtonAction =
            entries.firstOrNull { it.token.equals(token.trim(), ignoreCase = true) } ?: NONE
    }
}

data class PlayerButtonsConfig(
    val slots: List<PlayerButtonAction>,
    val andAutoEnabled: Boolean = true,
) {
    fun toSerialized(): String = slots.joinToString(",") { it.token }

    companion object {
        val DEFAULT_SLOTS = listOf(
            PlayerButtonAction.PLAY,
            PlayerButtonAction.PREV,
            PlayerButtonAction.NEXT,
            PlayerButtonAction.FAV,
            PlayerButtonAction.REPEAT,
            PlayerButtonAction.SHUFFLE,
        )

        fun fromSerialized(raw: String?, andAutoEnabled: Boolean = true): PlayerButtonsConfig {
            if (raw.isNullOrBlank()) {
                return PlayerButtonsConfig(DEFAULT_SLOTS, andAutoEnabled)
            }
            val tokens = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val parsed = tokens.map { PlayerButtonAction.fromToken(it) }.toMutableList()
            while (parsed.size < 6) {
                parsed.add(PlayerButtonAction.NONE)
            }
            return PlayerButtonsConfig(parsed.take(6), andAutoEnabled)
        }
    }
}

object PlayerButtonsStore {
    private const val PREFS = "quackify.client"
    private const val KEY_ORDER = "AaButtonOrder"
    private const val KEY_AND_AUTO = "AndAuto"

    fun load(context: Context): PlayerButtonsConfig {
        val prefs = AppPrefs.prefs(context, PREFS)
        val rawOrder = prefs.getString(KEY_ORDER, null)
        val andAuto = prefs.getBoolean(KEY_AND_AUTO, true)
        return PlayerButtonsConfig.fromSerialized(rawOrder, andAuto)
    }

    fun save(context: Context, config: PlayerButtonsConfig) {
        val prefs = AppPrefs.prefs(context, PREFS)
        prefs.edit()
            .putString(KEY_ORDER, config.toSerialized())
            .putBoolean(KEY_AND_AUTO, config.andAutoEnabled)
            .apply()
    }
}
