package com.quackify.compose.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BlurSourcePx = 128
private const val BlurPasses = 3
private const val BlurCacheEntries = 8

const val FadeCurveStops = 16

fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

val FullArtworkScrimBrush = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.30f),
    0.55f to Color.Black.copy(alpha = 0.46f),
    1.0f to Color.Black.copy(alpha = 0.66f)
)

fun Modifier.dissolveInto(fraction: Float): Modifier = if (fraction <= 0f) {
    this
} else {
    drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = Array(FadeCurveStops + 1) { index ->
                    val t = index / FadeCurveStops.toFloat()
                    t to Color.Black.copy(alpha = 1f - smootherStep(t))
                },
                startY = size.height * (1f - fraction.coerceIn(0f, 1f)),
                endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }
}

@Composable
fun rememberFullArtworkBlur(imageUrl: String?): State<ImageBitmap?> {
    val context = LocalContext.current
    val resolved = resolveRemoteImageUrl(imageUrl)
    val holder = remember(resolved) { mutableStateOf(resolved?.let { blurCache[it] }) }

    LaunchedEffect(resolved) {
        if (resolved.isNullOrBlank()) {
            holder.value = null
            return@LaunchedEffect
        }
        blurCache[resolved]?.let {
            holder.value = it
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(resolved)
            .size(BlurSourcePx)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@LaunchedEffect
        val blurred = withContext(Dispatchers.Default) { bitmap.boxBlurred(BlurPasses).asImageBitmap() }
        if (blurCache.size >= BlurCacheEntries) blurCache.clear()
        blurCache[resolved] = blurred
        holder.value = blurred
    }

    return holder
}

@Composable
fun FullArtworkBackdrop(
    image: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = image,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "fullArtworkBackdrop"
        ) { bitmap ->
            if (bitmap != null) {
                val painter = remember(bitmap) { BitmapPainter(bitmap) }
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(FullArtworkScrimBrush)
                    )
                }
            }
        }
    }
}

private val blurCache = LinkedHashMap<String, ImageBitmap>()

private fun Bitmap.boxBlurred(passes: Int): Bitmap {
    val width = width
    val height = height
    if (width < 2 || height < 2) return this
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val scratch = IntArray(pixels.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)
    repeat(passes) {
        blurPass(pixels, scratch, width, height, radius, horizontal = true)
        blurPass(scratch, pixels, width, height, radius, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun blurPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean
) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1

    for (line in 0 until minor) {
        fun pixel(at: Int): Int {
            val clamped = at.coerceIn(0, major - 1)
            return if (horizontal) source[line * width + clamped] else source[clamped * width + line]
        }

        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        for (moving in 0 until major) {
            val position = if (horizontal) line * width + moving else moving * width + line
            target[position] = (0xFF shl 24) or
                    ((red / window) shl 16) or
                    ((green / window) shl 8) or
                    (blue / window)
            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
