package com.quackify.compose.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

internal class DeepFeelPalette(
    val base: Color,
    val accent: Color,
    val glow: Color,
    val deepTop: Color,
    val deepBottom: Color
)

@Composable
internal fun rememberDeepFeelPalette(artworkColor: Color): DeepFeelPalette = remember(artworkColor) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(artworkColor.toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.4f).coerceIn(0.12f, 1f)
    hsv[2] = hsv[2].coerceIn(0.52f, 0.68f)
    val accent = Color(android.graphics.Color.HSVToColor(hsv))
    DeepFeelPalette(
        base = artworkColor,
        accent = accent,
        glow = lerp(accent, Color.White, 0.35f),
        deepTop = lerp(artworkColor, Color(0xFF0A0A10), 0.84f),
        deepBottom = lerp(artworkColor, Color(0xFF050507), 0.94f)
    )
}

internal class DeepFeelGlowPhases(
    val breathe: State<Float>,
    val sweep: State<Float>
)

@Composable
internal fun rememberDeepFeelGlowPhases(): DeepFeelGlowPhases {
    val transition = rememberInfiniteTransition(label = "deepFeelGlow")
    val breathe = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "deepFeelGlowBreathe"
    )
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "deepFeelGlowSweep"
    )
    return DeepFeelGlowPhases(breathe, sweep)
}

internal class DeepFeelFlowPhases(
    val forward: State<Float>,
    val backward: State<Float>,
    val breathe: State<Float>
)

@Composable
internal fun rememberDeepFeelFlowPhases(): DeepFeelFlowPhases {
    val transition = rememberInfiniteTransition(label = "deepFeelFlow")
    val forward = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing)
        ),
        label = "deepFeelFlowForward"
    )
    val backward = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing)
        ),
        label = "deepFeelFlowBackward"
    )
    val breathe = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "deepFeelFlowBreathe"
    )
    return DeepFeelFlowPhases(forward, backward, breathe)
}

internal fun Modifier.deepFeelLineGlow(
    palette: DeepFeelPalette,
    phases: DeepFeelGlowPhases?,
    progress: Float
): Modifier {
    if (phases == null) return this
    return drawBehind {
        if (progress <= 0.01f) return@drawBehind
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val breathe = phases.breathe.value
        val sweep = phases.sweep.value
        val pulse = sin(breathe * PI.toFloat())
        val inset = 12.dp.toPx()
        val topLeft = Offset(-inset, -inset * 0.75f)
        val glowSize = Size(w + inset * 2f, h + inset * 1.5f)
        val corner = CornerRadius(26.dp.toPx())
        val wash = Brush.horizontalGradient(
            0.0f to palette.accent.copy(alpha = 0.03f),
            0.24f to palette.accent.copy(alpha = 0.22f),
            0.5f to palette.base.copy(alpha = 0.38f),
            0.76f to palette.accent.copy(alpha = 0.22f),
            1.0f to palette.accent.copy(alpha = 0.03f),
            startX = w * 0.14f * breathe,
            endX = w * (1.02f - 0.14f * breathe)
        )
        drawRoundRect(
            brush = wash,
            topLeft = topLeft,
            size = glowSize,
            cornerRadius = corner,
            alpha = progress * (0.78f + 0.22f * pulse)
        )
        val bandWidth = w * 0.55f
        val bandStart = -bandWidth + (w + bandWidth * 2f) * sweep
        val sheen = Brush.horizontalGradient(
            colors = listOf(
                palette.glow.copy(alpha = 0f),
                palette.glow.copy(alpha = 0.13f),
                palette.glow.copy(alpha = 0f)
            ),
            startX = bandStart,
            endX = bandStart + bandWidth
        )
        drawRoundRect(
            brush = sheen,
            topLeft = topLeft,
            size = glowSize,
            cornerRadius = corner,
            alpha = progress
        )
    }
}

internal fun Modifier.deepFeelBackdrop(
    topColor: Color,
    bottomColor: Color,
    palette: DeepFeelPalette,
    phases: DeepFeelFlowPhases?,
    flowReveal: Float
): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to topColor,
            1.0f to bottomColor
        )
    )
    if (phases == null || flowReveal <= 0.01f) return@drawBehind
    val w = size.width
    val h = size.height
    val bandHeight = h * 0.34f
    val bandTop = h - bandHeight
    val period = w * 1.35f
    val forward = phases.forward.value * period
    drawRect(
        brush = Brush.horizontalGradient(
            0.0f to palette.base.copy(alpha = 0f),
            0.22f to palette.base.copy(alpha = 0.30f),
            0.5f to palette.accent.copy(alpha = 0.42f),
            0.78f to palette.base.copy(alpha = 0.30f),
            1.0f to palette.base.copy(alpha = 0f),
            startX = -period + forward,
            endX = forward,
            tileMode = TileMode.Repeated
        ),
        topLeft = Offset(0f, bandTop),
        size = Size(w, bandHeight),
        alpha = flowReveal
    )
    val period2 = w * 0.9f
    val backward = phases.backward.value * period2
    drawRect(
        brush = Brush.horizontalGradient(
            0.0f to palette.glow.copy(alpha = 0f),
            0.5f to palette.glow.copy(alpha = 0.20f),
            1.0f to palette.glow.copy(alpha = 0f),
            startX = -period2 + backward,
            endX = backward,
            tileMode = TileMode.Repeated
        ),
        topLeft = Offset(0f, bandTop),
        size = Size(w, bandHeight),
        alpha = flowReveal
    )
    val breath = sin(phases.breathe.value * PI.toFloat())
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.accent.copy(alpha = 0.14f + 0.10f * breath),
                palette.accent.copy(alpha = 0f)
            ),
            center = Offset(w * 0.5f, h * 1.08f),
            radius = w * (0.55f + 0.14f * breath)
        ),
        topLeft = Offset(0f, bandTop),
        size = Size(w, bandHeight),
        alpha = flowReveal
    )
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to bottomColor,
            0.6f to bottomColor.copy(alpha = 0f),
            1.0f to bottomColor.copy(alpha = 0f)
        ),
        topLeft = Offset(0f, bandTop),
        size = Size(w, bandHeight)
    )
}
