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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
        List(7 + random.nextInt(4)) {
            DeepFeelBlob(
                anchorX = 0.08f + random.nextFloat() * 0.84f,
                anchorY = 0.02f + random.nextFloat() * 0.96f,
                radius = 0.26f + random.nextFloat() * 0.40f,
                stretchX = 1.1f + random.nextFloat() * 1.7f,
                stretchY = 0.55f + random.nextFloat() * 0.55f,
                tiltDeg = -38f + random.nextFloat() * 76f,
                driftAmpX = 0.05f + random.nextFloat() * 0.17f,
                driftAmpY = 0.03f + random.nextFloat() * 0.10f,
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
        // The wash bleeds past the text block so the highlight reads as a
        // band of light rather than a box behind words: a cushion of air
        // above and below, and vertical ramps that dissolve the band into
        // the backdrop instead of cutting it off.
        val pad = 6.dp.toPx()
        val bandTop = -pad
        val bandBottom = h + pad
        val bandHeight = bandBottom - bandTop
        // The top and bottom fades occupy this share of the band each; the
        // middle stays at full strength so the active line itself never dims.
        val fadeSpan = (bandHeight * 0.34f).coerceAtLeast(1f)
        val core = (0.5f + 0.07f * sin(DEEP_FEEL_TAU * sweep)).coerceIn(0.08f, 0.92f)
        // Painted as ONE rect, then faded at the edges by masking the same
        // layer with a vertical alpha ramp. The band was once sliced into
        // stacked rects and the seams between slices — each painted twice
        // where they overlapped — showed up as faint white hairlines
        // cutting the highlight into little boxes; one surface, one mask,
        // and the band is seamless.
        val wash = Brush.horizontalGradient(
            0.0f to palette.accent.copy(alpha = 0f),
            0.06f to palette.accent.copy(alpha = 0.10f + 0.04f * pulse),
            (core * 0.5f) to palette.base.copy(alpha = 0.30f + 0.09f * pulse),
            core to palette.base.copy(alpha = 0.38f + 0.11f * pulse),
            (core + (1f - core) * 0.5f) to palette.base.copy(alpha = 0.30f + 0.09f * pulse),
            0.94f to palette.accent.copy(alpha = 0.10f + 0.04f * pulse),
            1.0f to palette.accent.copy(alpha = 0f)
        )
        val bandRect = Rect(0f, bandTop, w, bandBottom)
        // The vertical fade as a piecewise-linear smoothstep: full strength
        // through the middle, easing out over the fade span at both edges —
        // the same eased shape the sliced version had, minus the seams.
        val fadeFraction = (fadeSpan / bandHeight).coerceIn(0.02f, 0.49f)
        val maskBrush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            (fadeFraction * 0.25f) to Color.White.copy(alpha = 0.16f),
            (fadeFraction * 0.5f) to Color.White.copy(alpha = 0.5f),
            (fadeFraction * 0.75f) to Color.White.copy(alpha = 0.84f),
            fadeFraction to Color.White,
            (1f - fadeFraction) to Color.White,
            (1f - fadeFraction * 0.75f) to Color.White.copy(alpha = 0.84f),
            (1f - fadeFraction * 0.5f) to Color.White.copy(alpha = 0.5f),
            (1f - fadeFraction * 0.25f) to Color.White.copy(alpha = 0.16f),
            1.0f to Color.Transparent,
            startY = bandTop,
            endY = bandBottom
        )
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bandRect, Paint())
            drawRect(
                brush = wash,
                topLeft = Offset(0f, bandTop),
                size = Size(w, bandHeight),
                alpha = progress
            )
            // DstIn multiplies the layer's alpha by the mask, dissolving
            // the band's top and bottom edges into the backdrop in one
            // continuous surface.
            drawRect(
                brush = maskBrush,
                topLeft = Offset(0f, bandTop),
                size = Size(w, bandHeight),
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
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
