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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.util.Random
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

private const val DEEP_FEEL_TAU = 6.2831855f

internal class DeepFeelBlob(
    val anchorX: Float,
    val anchorY: Float,
    val radius: Float,
    val stretchX: Float,
    val stretchY: Float,
    val tiltDeg: Float,
    val driftAmpX: Float,
    val driftAmpY: Float,
    val cyclesX: Int,
    val cyclesY: Int,
    val cyclesPulse: Int,
    val phaseX: Float,
    val phaseY: Float,
    val phasePulse: Float,
    val colorIndex: Int,
    val maxAlpha: Float
)

internal class DeepFeelFlowPhases(
    val clock: State<Float>,
    val breathe: State<Float>,
    val blobs: List<DeepFeelBlob>
)

@Composable
internal fun rememberDeepFeelFlowPhases(): DeepFeelFlowPhases {
    val transition = rememberInfiniteTransition(label = "deepFeelFlow")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing)
        ),
        label = "deepFeelFlowClock"
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
    val blobs = remember {
        val random = Random()
        List(5 + random.nextInt(4)) {
            DeepFeelBlob(
                anchorX = 0.08f + random.nextFloat() * 0.84f,
                anchorY = 0.60f + random.nextFloat() * 0.46f,
                radius = 0.26f + random.nextFloat() * 0.40f,
                stretchX = 1.1f + random.nextFloat() * 1.7f,
                stretchY = 0.55f + random.nextFloat() * 0.55f,
                tiltDeg = -38f + random.nextFloat() * 76f,
                driftAmpX = 0.05f + random.nextFloat() * 0.17f,
                driftAmpY = 0.02f + random.nextFloat() * 0.08f,
                cyclesX = 1 + random.nextInt(3),
                cyclesY = 1 + random.nextInt(3),
                cyclesPulse = 1 + random.nextInt(2),
                phaseX = random.nextFloat() * DEEP_FEEL_TAU,
                phaseY = random.nextFloat() * DEEP_FEEL_TAU,
                phasePulse = random.nextFloat() * DEEP_FEEL_TAU,
                colorIndex = random.nextInt(3),
                maxAlpha = 0.10f + random.nextFloat() * 0.12f
            )
        }
    }
    return DeepFeelFlowPhases(clock, breathe, blobs)
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
    val clock = phases.clock.value
    for (blob in phases.blobs) {
        val center = Offset(
            blob.anchorX * w + blob.driftAmpX * w * sin(DEEP_FEEL_TAU * clock * blob.cyclesX + blob.phaseX),
            blob.anchorY * h + blob.driftAmpY * h * sin(DEEP_FEEL_TAU * clock * blob.cyclesY + blob.phaseY)
        )
        val pulse = 0.72f + 0.28f * sin(DEEP_FEEL_TAU * clock * blob.cyclesPulse + blob.phasePulse)
        val radius = blob.radius * w * (0.92f + 0.08f * pulse)
        val color = when (blob.colorIndex) {
            0 -> palette.base
            1 -> palette.accent
            else -> palette.glow
        }
        val alpha = blob.maxAlpha * pulse
        val brush = Brush.radialGradient(
            0.0f to color.copy(alpha = alpha),
            0.42f to color.copy(alpha = alpha * 0.5f),
            1.0f to color.copy(alpha = 0f),
            center = center,
            radius = radius
        )
        withTransform({
            rotate(degrees = blob.tiltDeg, pivot = center)
            scale(scaleX = blob.stretchX, scaleY = blob.stretchY, pivot = center)
        }) {
            drawCircle(brush = brush, radius = radius, center = center, alpha = flowReveal)
        }
    }
    val breath = sin(phases.breathe.value * PI.toFloat())
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.accent.copy(alpha = 0.12f + 0.08f * breath),
                palette.accent.copy(alpha = 0f)
            ),
            center = Offset(w * 0.5f, h * 1.10f),
            radius = w * (0.60f + 0.16f * breath)
        ),
        alpha = flowReveal
    )
}
