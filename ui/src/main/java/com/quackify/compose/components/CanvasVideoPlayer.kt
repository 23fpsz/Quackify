package com.quackify.compose.components

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

@Composable
fun CanvasVideoPlayer(
    filePath: String,
    modifier: Modifier = Modifier,
    fadeFraction: Float = 0f
) {
    val context = LocalContext.current
    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(File(filePath).toUri()))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { viewContext ->
            CanvasFadeFrame(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val texture = texture
                player.setVideoTextureView(texture)
                val delegate = texture.surfaceTextureListener
                texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        delegate?.onSurfaceTextureAvailable(surface, width, height)
                        applyVideoSize()
                        applyFade()
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        delegate?.onSurfaceTextureSizeChanged(surface, width, height)
                        applyVideoSize()
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        applyFade()
                        return delegate?.onSurfaceTextureDestroyed(surface) ?: true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        delegate?.onSurfaceTextureUpdated(surface)
                        applyVideoSize()
                    }
                }
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        videoWidth = videoSize.width
                        videoHeight = videoSize.height
                        applyVideoSize()
                    }
                })
                this.fadeFraction = fadeFraction
                applyFade()
            }
        },
        update = { frame ->
            frame.fadeFraction = fadeFraction
            frame.applyVideoSize()
            frame.applyFade()
        },
        modifier = modifier
    )
}

private class CanvasFadeFrame(context: Context) : FrameLayout(context) {
    val texture = TextureView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        isOpaque = false
    }

    var videoWidth = 0
    var videoHeight = 0

    var fadeFraction: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            gradient = null
            invalidate()
        }

    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var gradient: LinearGradient? = null

    // onSurfaceTextureUpdated fires for every decoded frame and AndroidView's
    // update block runs on every recomposition. Both used to re-apply the
    // video transform / fade effect unconditionally, and setTransform +
    // setRenderEffect each invalidate the view — on low-end GPUs that doubled
    // the per-frame composition work and the canvas visibly stuttered. Both
    // are now cached by key and become no-ops when nothing actually changed.
    private var transformKey = Long.MIN_VALUE
    private var fadeKey = Long.MIN_VALUE

    init {
        addView(texture)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = null
        applyVideoSize()
        applyFade()
    }

    fun applyVideoSize() {
        if (videoWidth <= 0 || videoHeight <= 0) return
        val viewWidth = texture.width
        val viewHeight = texture.height
        if (viewWidth <= 0 || viewHeight <= 0) return
        val key = (videoWidth.toLong() and 0xFFFFL).shl(48) or
            (videoHeight.toLong() and 0xFFFFL).shl(32) or
            (viewWidth.toLong() and 0xFFFFL).shl(16) or
            (viewHeight.toLong() and 0xFFFFL)
        if (key == transformKey) return
        transformKey = key
        val videoAspect = videoWidth.toFloat() / videoHeight
        val viewAspect = viewWidth.toFloat() / viewHeight
        val matrix = Matrix()
        when {
            videoAspect > viewAspect ->
                matrix.setScale(videoAspect / viewAspect, 1f, viewWidth / 2f, viewHeight / 2f)
            videoAspect < viewAspect ->
                matrix.setScale(1f, viewAspect / videoAspect, viewWidth / 2f, viewHeight / 2f)
        }
        texture.setTransform(matrix)
    }

    fun applyFade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Rebuilding the RenderEffect on every call also reallocated the
            // shader on each frame; keep one alive until (height, fraction)
            // actually changes.
            val key = (height.toLong() and 0xFFFFFFFFL).shl(32) or
                (fadeFraction.toRawBits().toLong() and 0xFFFFFFFFL)
            if (key == fadeKey) return
            fadeKey = key
            texture.setRenderEffect(fadeEffect(height, fadeFraction))
        }
        // The pre-S fallback paints the mask in dispatchDraw, and every
        // caller that can change what it draws already invalidates (the
        // fadeFraction setter and onSizeChanged), so nothing to do here.
    }

    override fun dispatchDraw(canvas: Canvas) {
        val fade = fadeFraction
        if (fade <= 0.001f || height <= 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.dispatchDraw(canvas)
            return
        }
        val shader = gradient?.takeIf { gradientHeight == height } ?: fadeShader(height, fade).also {
            gradient = it
            gradientHeight = height
        }
        maskPaint.shader = shader
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        canvas.restoreToCount(layer)
    }

    private var gradientHeight = 0
}

private fun fadeShader(heightPx: Int, fraction: Float): LinearGradient {
    val endY = heightPx.toFloat()
    return LinearGradient(
        0f,
        endY * (1f - fraction.coerceIn(0f, 1f)),
        0f,
        endY,
        IntArray(FadeCurveStops + 1) { index ->
            val alpha = (255f * (1f - smootherStep(index / FadeCurveStops.toFloat()))).toInt()
            android.graphics.Color.argb(alpha, 0, 0, 0)
        },
        FloatArray(FadeCurveStops + 1) { index -> index / FadeCurveStops.toFloat() },
        Shader.TileMode.CLAMP
    )
}

@RequiresApi(Build.VERSION_CODES.S)
private fun fadeEffect(heightPx: Int, fraction: Float): RenderEffect? {
    if (fraction <= 0.001f || heightPx <= 0) return null
    return RenderEffect.createBlendModeEffect(
        RenderEffect.createOffsetEffect(0f, 0f),
        RenderEffect.createShaderEffect(fadeShader(heightPx, fraction)),
        BlendMode.DST_IN
    )
}
