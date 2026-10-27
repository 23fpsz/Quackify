package com.spotiduck.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalSpotifyColors = staticCompositionLocalOf { SpotifyColorScheme() }
private val LocalSpotifyTypography = staticCompositionLocalOf { SpotifyTypography() }

object SpotifyTheme {
    val colors: SpotifyColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalSpotifyColors.current

    val typography: SpotifyTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalSpotifyTypography.current
}

@Composable
fun SpotifyEncoreTheme(
    content: @Composable () -> Unit
) {
    val colors = SpotifyColorScheme()
    val typography = SpotifyTypography()

    val materialColorScheme = darkColorScheme(
        primary = EncoreColors.Green,
        onPrimary = EncoreColors.PureBlack,
        background = EncoreColors.Background,
        surface = EncoreColors.Surface,
        onBackground = EncoreColors.TextPrimary,
        onSurface = EncoreColors.TextPrimary
    )

    CompositionLocalProvider(
        LocalSpotifyColors provides colors,
        LocalSpotifyTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
