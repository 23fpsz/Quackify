package com.quackify.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalEncoreColors = staticCompositionLocalOf { EncoreColorScheme() }
private val LocalEncoreTypography = staticCompositionLocalOf { EncoreTypography() }

object EncoreTheme {
    val colors: EncoreColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalEncoreColors.current

    val typography: EncoreTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalEncoreTypography.current
}

@Composable
fun EncoreAppTheme(
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) EncoreColorScheme() else EncoreColorScheme.light()
    val typography = if (dark) EncoreTypography()
    else EncoreTypography(ink = EncoreColors.TextPrimaryLight, inkSecondary = EncoreColors.TextSecondaryLight)

    val materialColorScheme = if (dark) {
        darkColorScheme(
            primary = EncoreColors.Green,
            onPrimary = EncoreColors.PureBlack,
            background = EncoreTheme.colors.background,
            surface = EncoreTheme.colors.surface,
            onBackground = EncoreTheme.colors.textPrimary,
            onSurface = EncoreTheme.colors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = EncoreColors.GreenDark,
            onPrimary = Color.White,
            background = EncoreColors.BackgroundLight,
            surface = EncoreColors.SurfaceLight,
            onBackground = EncoreColors.TextPrimaryLight,
            onSurface = EncoreColors.TextPrimaryLight
        )
    }

    CompositionLocalProvider(
        LocalEncoreColors provides colors,
        LocalEncoreTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
