package com.quackify.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 */
object EncoreColors {
    val Green = Color(0xFF1DB954)
    val GreenLight = Color(0xFF1ED760)
    val GreenDark = Color(0xFF169C46)

    val PureBlack = Color(0xFF000000)
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF181818)         // gray_10: Cards, quick access rows
    val Card = Color(0xFF282828)            // gray_15: Floating mini-player, elevated cards
    val CardElevated = Color(0xFF333333)    // gray_20: Dialogs, sheets, bottom navigation
    val Divider = Color(0xFF535353)         // gray_30: Subtle separators, outlines
    val Outline = Color(0x33FFFFFF)         // 20% white outline

    val TextPrimary = Color(0xFFFFFFFF)     // High-emphasis white
    val TextSecondary = Color(0xFFB3B3B3)   // gray_70: Subdued artist/metadata text
    val TextDisabled = Color(0xFF7F7F7F)    // gray_50: Disabled/placeholder text

    val Error = Color(0xFFE91429)
    val Beta = Color(0xFFFFD54F)
    val HeartActive = Color(0xFF1DB954)
    val ProgressBarBackground = Color(0x33FFFFFF)
    val ScrubberThumb = Color(0xFFFFFFFF)

    val BackgroundLight = Color(0xFFF6F6F6)
    val SurfaceLight = Color(0xFFFFFFFF)
    val CardLight = Color(0xFFF0F0F0)
    val CardElevatedLight = Color(0xFFE4E4E4)
    val DividerLight = Color(0xFFCCCCCC)
    val OutlineLight = Color(0x33000000)

    val TextPrimaryLight = Color(0xFF191919)
    val TextSecondaryLight = Color(0xFF5E5E5E)
    val TextDisabledLight = Color(0xFF9A9A9A)
    val ProgressBarBackgroundLight = Color(0x33000000)
    val ScrubberThumbLight = Color(0xFF191919)
}

@Immutable
data class EncoreColorScheme(
    val canvas: Color = EncoreColors.PureBlack,
    val background: Color = EncoreColors.Background,
    val surface: Color = EncoreColors.Surface,
    val card: Color = EncoreColors.Card,
    val cardElevated: Color = EncoreColors.CardElevated,
    val primary: Color = EncoreColors.Green,
    val onPrimary: Color = EncoreColors.PureBlack,
    val textPrimary: Color = EncoreColors.TextPrimary,
    val textSecondary: Color = EncoreColors.TextSecondary,
    val textDisabled: Color = EncoreColors.TextDisabled,
    val divider: Color = EncoreColors.Divider,
    val outline: Color = EncoreColors.Outline,
    val progressBarBackground: Color = EncoreColors.ProgressBarBackground,
    val scrubberThumb: Color = EncoreColors.ScrubberThumb,
    val error: Color = EncoreColors.Error
) {
    companion object {
        fun light() = EncoreColorScheme(
            canvas = Color.White,
            background = EncoreColors.BackgroundLight,
            surface = EncoreColors.SurfaceLight,
            card = EncoreColors.CardLight,
            cardElevated = EncoreColors.CardElevatedLight,
            primary = EncoreColors.GreenDark,
            onPrimary = Color.White,
            textPrimary = EncoreColors.TextPrimaryLight,
            textSecondary = EncoreColors.TextSecondaryLight,
            textDisabled = EncoreColors.TextDisabledLight,
            divider = EncoreColors.DividerLight,
            outline = EncoreColors.OutlineLight,
            progressBarBackground = EncoreColors.ProgressBarBackgroundLight,
            scrubberThumb = EncoreColors.ScrubberThumbLight,
            error = EncoreColors.Error
        )
    }
}
