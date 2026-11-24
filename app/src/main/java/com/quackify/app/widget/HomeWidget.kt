package com.quackify.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.DisplayMetrics
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.quackify.app.MainActivity
import com.quackify.app.PlaybackHost
import com.quackify.app.PlaybackService
import com.quackify.app.R
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.resolveRemoteImageUrl
import kotlin.math.roundToInt
import com.quackify.compose.R as UiR

class HomeWidget : AppWidgetProvider() {

    companion object {

        private const val ACTION_PLAYPAUSE = "com.quackify.app.widget.WIDGET_PLAYPAUSE"
        private const val ACTION_PREV = "com.quackify.app.widget.WIDGET_PREV"
        private const val ACTION_NEXT = "com.quackify.app.widget.WIDGET_NEXT"
        private const val ACTION_REPEAT = "com.quackify.app.widget.REPEAT_ACTION"
        private const val ACTION_SHUFFLE = "com.quackify.app.widget.SHUFFLE_ACTION"
        private const val ACTION_FAV = "com.quackify.app.widget.ADDTOFAV_ACTION"

        private val COLOR_ICON = Color.WHITE
        private val COLOR_MUTED = Color.parseColor("#CCCCCC")
        private val COLOR_ACCENT = Color.parseColor("#1DB954")
        private val COLOR_ON_PLAY = Color.BLACK

        // Sizes are in dp, as the launcher reports them. A two-row cell is at least 110dp, but
        // the card needs ~130dp before its cover stops being a sliver; below that the strip
        // simply grows to fill the height.
        private const val CARD_MIN_HEIGHT_DP = 130f
        private const val CARD_TIMES_MIN_HEIGHT_DP = 140f
        private const val CARD_SIDE_CONTROLS_MIN_WIDTH_DP = 240f
        // Title + artist + album need ~50dp; below that the album line would be clipped.
        private const val CARD_ALBUM_MIN_TOP_ROW_DP = 50f
        // Narrower than this, the strip's text would only show a letter or two; hide it instead.
        private const val STRIP_TEXT_MIN_WIDTH_DP = 48f
        // Below this the strip only has room for two lines and smaller controls.
        private const val STRIP_COMPACT_HEIGHT_DP = 64f

        // Used before the launcher has reported any size for a freshly placed widget.
        private val DEFAULT_SIZE = SizeF(250f, 56f)

        // RemoteViews(Map) accepts at most 16 sizes.
        private const val MAX_SIZES = 16

        private const val COVER_PX = 256
        private const val COVER_CORNER_FRACTION = 0.16f

        // The backdrop is tiny on purpose: the ImageView stretches it, which reads as a blur.
        private const val BACKDROP_PX = 32
        private const val BACKDROP_BLUR_RADIUS = 3
        // Brightest spot of the backdrop, as relative luminance. 0.08 keeps the white title and
        // the #C2C2C2 album line above 4.5:1 contrast, so pale covers can't wash out the text.
        private const val BACKDROP_MAX_LUMINANCE = 0.08
        private const val BACKDROP_MIN_DIM = 0.25f
        private const val BACKDROP_MAX_DIM = 0.85f

        private val handler = Handler(Looper.getMainLooper())
        private var isUpdateRunning = false
        private var appContext: Context? = null

        // Updates run every 2s while playing; keep the processed art instead of redoing it each time.
        private val artLock = Any()
        private var artUrl: String? = null
        private var artCache: Art? = null

        private val updateRunnable = object : Runnable {
            override fun run() {
                val context = appContext
                val bridge = PlaybackHost.bridge
                if (context != null && bridge != null && bridge.state.isPlaying) {
                    updateAllWidgets(context)
                    handler.postDelayed(this, 2000L)
                } else {
                    isUpdateRunning = false
                }
            }
        }

        private class Art(val cover: Bitmap, val backdrop: Bitmap?)

        private data class Frame(
            val snapshot: WidgetSnapshot,
            val repeatMode: RepeatMode,
            val shuffleMode: ShuffleMode,
            val cover: Bitmap?,
            val backdrop: Bitmap?,
            val openApp: PendingIntent,
        )

        @JvmStatic
        fun updateAllWidgets(context: Context) {
            appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, HomeWidget::class.java))
            if (appWidgetIds.isEmpty()) return
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }

            val bridge = PlaybackHost.bridge
            if (bridge != null && bridge.state.isPlaying && !isUpdateRunning) {
                isUpdateRunning = true
                handler.post(updateRunnable)
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val bridge = PlaybackHost.bridge
            val live = bridge
                ?.let { WidgetSnapshot.from(it.state, it.progress, WidgetAlbumNames.nameFor(context, it)) }
                ?.takeIf { it.hasTrack }
            val snapshot = WidgetSync.resolve(live, context)
            val art = if (snapshot.hasTrack) artFor(context, snapshot.coverUrl) else null
            val frame = Frame(
                snapshot = snapshot,
                repeatMode = bridge?.state?.repeatMode ?: RepeatMode.OFF,
                shuffleMode = bridge?.state?.shuffleMode ?: ShuffleMode.OFF,
                cover = art?.cover,
                backdrop = art?.backdrop,
                openApp = openAppIntent(context, appWidgetId),
            )
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId) ?: Bundle()

            try {
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, options, frame))
            } catch (e: Throwable) {
                // The art can push the update past the RemoteViews size limit; retry without it.
                try {
                    val plain = frame.copy(cover = null, backdrop = null)
                    appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, options, plain))
                } catch (ignored: Throwable) {}
            }
        }

        /**
         * Gives the launcher one layout per size the widget can appear at (portrait and
         * landscape), so it never has to shrink or letterbox a layout made for another size.
         */
        private fun buildViews(context: Context, options: Bundle, frame: Frame): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = reportedSizes(options)
                if (sizes.isNotEmpty()) {
                    return RemoteViews(sizes.associateWith { buildForSize(context, it, frame) })
                }
            }
            // Older launchers only report bounds: portrait is min width x max height,
            // landscape is max width x min height.
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
            if (minWidth <= 0f || maxWidth <= 0f || minHeight <= 0f || maxHeight <= 0f) {
                return buildForSize(context, DEFAULT_SIZE, frame)
            }
            return RemoteViews(
                buildForSize(context, SizeF(maxWidth, minHeight), frame),
                buildForSize(context, SizeF(minWidth, maxHeight), frame),
            )
        }

        private fun reportedSizes(options: Bundle): List<SizeF> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
            val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            } else {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            }
            return sizes.orEmpty()
                .filter { it.width > 0f && it.height > 0f }
                .distinct()
                .take(MAX_SIZES)
        }

        private fun buildForSize(context: Context, size: SizeF, frame: Frame): RemoteViews =
            if (size.height >= CARD_MIN_HEIGHT_DP) {
                buildCard(context, size, frame)
            } else {
                buildStrip(context, size, frame)
            }

        private fun buildStrip(context: Context, size: SizeF, frame: Frame): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.home_widget)
            bindShared(views, context, frame, isCard = false)

            // Mirrors home_widget.xml: padding, square cover, text margins, then the transport
            // (prev + play + next + play's margins), which shrinks on compact strips.
            val compact = size.height < STRIP_COMPACT_HEIGHT_DP
            val pad = if (compact) 6f else 8f
            val transport = if (compact) 108f else 122f
            val textWidth = size.width - 2 * pad - (size.height - 2 * pad) - 12f - 8f - transport
            // INVISIBLE, not GONE: the weighted gap keeps the controls pinned to the right edge.
            views.setViewVisibility(
                R.id.text_container,
                if (textWidth >= STRIP_TEXT_MIN_WIDTH_DP) View.VISIBLE else View.INVISIBLE,
            )

            if (compact) {
                dp(context, pad).let { views.setViewPadding(R.id.strip_content, it, it, it, it) }
                views.setViewVisibility(R.id.txt_album, View.GONE)
                views.setTextViewTextSize(R.id.txt_title, TypedValue.COMPLEX_UNIT_SP, 13f)
                views.setTextViewTextSize(R.id.txt_artist, TypedValue.COMPLEX_UNIT_SP, 11f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setSquareSize(R.id.btn_play, 36f)
                    views.setSquareSize(R.id.btn_prev, 32f)
                    views.setSquareSize(R.id.btn_next, 32f)
                    dp(context, 9f).let { views.setViewPadding(R.id.btn_play, it, it, it, it) }
                    dp(context, 5f).let {
                        views.setViewPadding(R.id.btn_prev, it, it, it, it)
                        views.setViewPadding(R.id.btn_next, it, it, it, it)
                    }
                }
            }
            return views
        }

        private fun buildCard(context: Context, size: SizeF, frame: Frame): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_expanded)
            bindShared(views, context, frame, isCard = true)
            val snapshot = frame.snapshot

            val sideControls = if (size.width >= CARD_SIDE_CONTROLS_MIN_WIDTH_DP) View.VISIBLE else View.GONE
            views.setViewVisibility(R.id.btn_shuffle, sideControls)
            views.setViewVisibility(R.id.btn_repeat, sideControls)

            views.setImageViewResource(
                R.id.btn_shuffle,
                if (frame.shuffleMode == ShuffleMode.SMART_SHUFFLE) R.drawable.smart_shuffle else R.drawable.ic_notif_shuffle,
            )
            views.setInt(
                R.id.btn_shuffle,
                "setColorFilter",
                if (frame.shuffleMode != ShuffleMode.OFF) COLOR_ACCENT else COLOR_MUTED,
            )
            views.setImageViewResource(
                R.id.btn_repeat,
                if (frame.repeatMode == RepeatMode.REPEAT_ONE) R.drawable.ic_notif_repeat_one else R.drawable.ic_notif_repeat,
            )
            views.setInt(
                R.id.btn_repeat,
                "setColorFilter",
                if (frame.repeatMode != RepeatMode.OFF) COLOR_ACCENT else COLOR_MUTED,
            )
            views.setOnClickPendingIntent(R.id.btn_shuffle, createPendingIntent(context, ACTION_SHUFFLE))
            views.setOnClickPendingIntent(R.id.btn_repeat, createPendingIntent(context, ACTION_REPEAT))

            val duration = if (snapshot.hasTrack) snapshot.durationMs.coerceAtLeast(0L) else 0L
            val position = if (snapshot.hasTrack) snapshot.positionMs.coerceAtLeast(0L) else 0L
            val progress = if (duration > 0L) (position * 1000L / duration).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.progress_bar, 1000, progress, false)

            // The length is 0 until playback has loaded the track; "0:00 / 0:00" reads as broken.
            val showTimes = size.height >= CARD_TIMES_MIN_HEIGHT_DP && duration > 0L
            val ticking = showTimes && snapshot.hasTrack && snapshot.isPlaying
            views.setViewVisibility(R.id.txt_duration, if (showTimes) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.txt_position, if (showTimes && !ticking) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.txt_position_chrono, if (ticking) View.VISIBLE else View.GONE)
            views.setChronometer(
                R.id.txt_position_chrono,
                SystemClock.elapsedRealtime() - if (ticking) position else 0L,
                null,
                ticking,
            )
            views.setTextViewText(R.id.txt_position, formatTime(position))
            views.setTextViewText(R.id.txt_duration, formatTime(duration))

            // Height left for the top row: padding 24, controls 44 + 6, progress row (+ its 6 gap).
            val topRow = size.height - 24f - 50f - if (showTimes) 19f else 9f
            if (topRow < CARD_ALBUM_MIN_TOP_ROW_DP) views.setViewVisibility(R.id.txt_album, View.GONE)
            return views
        }

        /** Views both layouts share. Only the card has a like button. */
        private fun bindShared(views: RemoteViews, context: Context, frame: Frame, isCard: Boolean) {
            val snapshot = frame.snapshot
            if (snapshot.hasTrack) {
                views.setTextViewText(R.id.txt_title, snapshot.title.ifBlank { context.getString(R.string.txt_notrack) })
                views.setTextViewText(R.id.txt_artist, snapshot.artist)
                views.setViewVisibility(R.id.txt_artist, if (snapshot.artist.isBlank()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.txt_album, snapshot.album)
                views.setViewVisibility(R.id.txt_album, if (snapshot.album.isBlank()) View.GONE else View.VISIBLE)
            } else {
                views.setTextViewText(R.id.txt_title, context.getString(R.string.txt_not_playing))
                views.setViewVisibility(R.id.txt_artist, View.GONE)
                views.setViewVisibility(R.id.txt_album, View.GONE)
            }

            if (frame.backdrop != null) {
                views.setImageViewBitmap(R.id.img_backdrop, frame.backdrop)
                views.setViewVisibility(R.id.img_backdrop, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.img_backdrop, View.GONE)
            }

            val playing = snapshot.hasTrack && snapshot.isPlaying
            views.setImageViewResource(R.id.btn_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setInt(R.id.btn_play, "setColorFilter", COLOR_ON_PLAY)
            views.setInt(R.id.btn_prev, "setColorFilter", COLOR_ICON)
            views.setInt(R.id.btn_next, "setColorFilter", COLOR_ICON)

            views.setOnClickPendingIntent(R.id.widget_root, frame.openApp)
            views.setOnClickPendingIntent(R.id.btn_play, createPendingIntent(context, ACTION_PLAYPAUSE))
            views.setOnClickPendingIntent(R.id.btn_prev, createPendingIntent(context, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.btn_next, createPendingIntent(context, ACTION_NEXT))

            if (frame.cover != null) {
                views.setImageViewBitmap(R.id.img_cover, frame.cover)
            } else {
                views.setImageViewResource(R.id.img_cover, R.drawable.logo_duck)
            }

            if (!isCard) return

            val liked = snapshot.hasTrack && snapshot.isLiked
            views.setImageViewResource(R.id.btn_like, if (liked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart)
            views.setInt(R.id.btn_like, "setColorFilter", if (liked) COLOR_ACCENT else COLOR_MUTED)
            views.setContentDescription(
                R.id.btn_like,
                context.getString(if (liked) UiR.string.remove_from_your_library else UiR.string.save_to_your_library),
            )
            views.setOnClickPendingIntent(R.id.btn_like, createPendingIntent(context, ACTION_FAV))
        }

        private fun RemoteViews.setSquareSize(viewId: Int, sizeDp: Float) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            setViewLayoutWidth(viewId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(viewId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        private fun dp(context: Context, value: Float): Int =
            (value * context.resources.displayMetrics.density + 0.5f).toInt()

        /** Cover and backdrop for [rawUrl]; null while the art is still downloading (a refresh follows). */
        private fun artFor(context: Context, rawUrl: String): Art? {
            val url = resolveRemoteImageUrl(rawUrl) ?: return null
            synchronized(artLock) {
                if (url == artUrl) artCache?.let { return it }
            }
            val source = runCatching { WidgetArt.cached(url) }.getOrNull()
            if (source == null || source.isRecycled) {
                WidgetArt.prefetch(context.applicationContext, url) { updateAllWidgets(context) }
                return null
            }
            val cover = runCatching { squareCover(source) }.getOrNull() ?: return null
            val art = Art(cover, runCatching { backdrop(source) }.getOrNull())
            synchronized(artLock) {
                artUrl = url
                artCache = art
            }
            return art
        }

        private fun squareCover(source: Bitmap): Bitmap {
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            val output = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val bounds = RectF(0f, 0f, COVER_PX.toFloat(), COVER_PX.toFloat())
            val radius = COVER_PX * COVER_CORNER_FRACTION
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawRoundRect(bounds, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(source, Rect(left, top, left + side, top + side), bounds, paint)
            // Intrinsic size becomes 256dp on any screen, above the card's 120dp cap, so the
            // ImageView's row height or maxHeight sizes the cover instead of the pixel count.
            output.density = DisplayMetrics.DENSITY_MEDIUM
            return output
        }

        /**
         * Small, blurred and darkened copy of the cover. The darkening is measured, not fixed:
         * the brightest spot ends at [BACKDROP_MAX_LUMINANCE], so a white cover turns mid-grey
         * while a dark one is barely touched, and white text and buttons stay readable on both.
         */
        private fun backdrop(source: Bitmap): Bitmap {
            // Skip the outer 10%: cached covers have transparent rounded corners.
            val side = minOf(source.width, source.height)
            val inset = side / 10
            val left = (source.width - side) / 2 + inset
            val top = (source.height - side) / 2 + inset
            val crop = side - 2 * inset

            // Two steps so the small image averages the cover instead of sampling a few pixels.
            val mid = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            Canvas(mid).drawBitmap(
                source,
                Rect(left, top, left + crop, top + crop),
                RectF(0f, 0f, 128f, 128f),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val small = Bitmap.createScaledBitmap(mid, BACKDROP_PX, BACKDROP_PX, true)

            val n = BACKDROP_PX
            val pixels = IntArray(n * n)
            small.getPixels(pixels, 0, n, 0, 0, n, n)
            if (small !== mid) small.recycle()
            mid.recycle()

            boxBlur(pixels, n, n, BACKDROP_BLUR_RADIUS)
            boxBlur(pixels, n, n, BACKDROP_BLUR_RADIUS)

            val brightest = pixels.maxByOrNull { ColorUtils.calculateLuminance(it) } ?: Color.BLACK
            val keep = keepFactor(brightest)
            for (i in pixels.indices) pixels[i] = dim(pixels[i], keep)
            return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        }

        /** Largest brightness factor that keeps [color] at or under the luminance cap. */
        private fun keepFactor(color: Int): Float {
            val most = 1f - BACKDROP_MIN_DIM
            val least = 1f - BACKDROP_MAX_DIM
            if (ColorUtils.calculateLuminance(dim(color, most)) <= BACKDROP_MAX_LUMINANCE) return most
            var lo = least
            var hi = most
            repeat(12) {
                val mid = (lo + hi) / 2f
                if (ColorUtils.calculateLuminance(dim(color, mid)) <= BACKDROP_MAX_LUMINANCE) lo = mid else hi = mid
            }
            return lo
        }

        private fun dim(color: Int, keep: Float): Int = Color.rgb(
            (Color.red(color) * keep).roundToInt(),
            (Color.green(color) * keep).roundToInt(),
            (Color.blue(color) * keep).roundToInt(),
        )

        /** Separable box blur with clamped edges; alpha is dropped (the backdrop is opaque). */
        private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
            val temp = IntArray(pixels.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var r = 0
                    var g = 0
                    var b = 0
                    for (k in -radius..radius) {
                        val c = pixels[y * width + (x + k).coerceIn(0, width - 1)]
                        r += Color.red(c)
                        g += Color.green(c)
                        b += Color.blue(c)
                    }
                    val count = 2 * radius + 1
                    temp[y * width + x] = Color.rgb(r / count, g / count, b / count)
                }
            }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var r = 0
                    var g = 0
                    var b = 0
                    for (k in -radius..radius) {
                        val c = temp[(y + k).coerceIn(0, height - 1) * width + x]
                        r += Color.red(c)
                        g += Color.green(c)
                        b += Color.blue(c)
                    }
                    val count = 2 * radius + 1
                    pixels[y * width + x] = Color.rgb(r / count, g / count, b / count)
                }
            }
        }

        private fun openAppIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(PlaybackService.EXTRA_OPEN_PLAYER, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            return PendingIntent.getActivity(context, appWidgetId, intent, getFlags())
        }

        private fun createPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, HomeWidget::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, getFlags())
        }

        private fun getFlags(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        // Same formatter the ticking Chronometer uses, so position and length always match.
        private fun formatTime(millis: Long): String =
            DateUtils.formatElapsedTime(millis.coerceAtLeast(0L) / 1000L)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val app = context.applicationContext
        val bridge = PlaybackHost.ensure(app)

        when (action) {
            ACTION_PLAYPAUSE -> {
                val live = bridge
                    .let { WidgetSnapshot.from(it.state, it.progress, WidgetAlbumNames.nameFor(app, it)) }
                    .takeIf { it.hasTrack }
                val current = WidgetSync.resolve(live, app)
                WidgetSync.expectOptimistic(current.copy(isPlaying = !current.isPlaying))
                bridge.listener.onPlayPause()
                updateAllWidgets(app)
            }
            ACTION_PREV -> {
                bridge.listener.onSkipPrevious()
                updateAllWidgets(app)
            }
            ACTION_NEXT -> {
                bridge.listener.onSkipNext()
                updateAllWidgets(app)
            }
            ACTION_REPEAT -> {
                bridge.listener.onRepeatToggle()
                updateAllWidgets(app)
            }
            ACTION_SHUFFLE -> {
                bridge.listener.onShuffleToggle()
                updateAllWidgets(app)
            }
            ACTION_FAV -> {
                bridge.listener.onLikeToggle()
                updateAllWidgets(app)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
