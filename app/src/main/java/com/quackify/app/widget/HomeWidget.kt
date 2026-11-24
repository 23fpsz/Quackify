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
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.quackify.app.MainActivity
import com.quackify.app.PlaybackHost
import com.quackify.app.PlaybackService
import com.quackify.app.R
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.resolveRemoteImageUrl
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
        private val COLOR_MUTED = Color.parseColor("#B3B3B3")
        private val COLOR_ACCENT = Color.parseColor("#1DB954")
        private val COLOR_ON_PLAY = Color.BLACK

        // Sizes are in dp, as the launcher reports them. A two-row cell is at least 110dp, but
        // the card needs ~130dp before its cover stops being a sliver; below that the strip
        // simply grows to fill the height.
        private const val CARD_MIN_HEIGHT_DP = 130f
        private const val CARD_TIMES_MIN_HEIGHT_DP = 140f
        private const val CARD_SIDE_CONTROLS_MIN_WIDTH_DP = 240f
        private const val STRIP_LIKE_MIN_WIDTH_DP = 250f
        private const val STRIP_TEXT_MIN_WIDTH_DP = 210f
        private const val STRIP_ROOMY_HEIGHT_DP = 64f

        // Used before the launcher has reported any size for a freshly placed widget.
        private val DEFAULT_SIZE = SizeF(250f, 56f)

        // RemoteViews(Map) accepts at most 16 sizes.
        private const val MAX_SIZES = 16

        private const val COVER_PX = 256
        private const val COVER_CORNER_FRACTION = 0.16f

        private val handler = Handler(Looper.getMainLooper())
        private var isUpdateRunning = false
        private var appContext: Context? = null

        // Updates run every 2s while playing; keep the cropped cover instead of redoing it each time.
        private val coverLock = Any()
        private var coverUrl: String? = null
        private var coverBitmap: Bitmap? = null

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

        private data class Frame(
            val snapshot: WidgetSnapshot,
            val repeatMode: RepeatMode,
            val shuffleMode: ShuffleMode,
            val cover: Bitmap?,
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
            val live = bridge?.let { WidgetSnapshot.from(it.state, it.progress) }?.takeIf { it.hasTrack }
            val snapshot = WidgetSync.resolve(live, context)
            val frame = Frame(
                snapshot = snapshot,
                repeatMode = bridge?.state?.repeatMode ?: RepeatMode.OFF,
                shuffleMode = bridge?.state?.shuffleMode ?: ShuffleMode.OFF,
                cover = if (snapshot.hasTrack) coverFor(context, snapshot.coverUrl) else null,
                openApp = openAppIntent(context, appWidgetId),
            )
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId) ?: Bundle()

            try {
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, options, frame))
            } catch (e: Throwable) {
                // The cover can push the update past the RemoteViews size limit; retry without it.
                try {
                    appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, options, frame.copy(cover = null)))
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
            bindShared(views, context, frame)

            views.setViewVisibility(
                R.id.btn_like,
                if (size.width >= STRIP_LIKE_MIN_WIDTH_DP) View.VISIBLE else View.GONE,
            )
            // INVISIBLE, not GONE: the weighted gap keeps the controls pinned to the right edge.
            views.setViewVisibility(
                R.id.text_container,
                if (size.width >= STRIP_TEXT_MIN_WIDTH_DP) View.VISIBLE else View.INVISIBLE,
            )

            // Some launchers give a single row 80-100dp; use that height instead of a thin strip.
            if (size.height >= STRIP_ROOMY_HEIGHT_DP) {
                val pad = dp(context, 8f)
                views.setViewPadding(R.id.widget_root, pad, pad, pad, pad)
                views.setTextViewTextSize(R.id.txt_title, TypedValue.COMPLEX_UNIT_SP, 15f)
                views.setTextViewTextSize(R.id.txt_artist, TypedValue.COMPLEX_UNIT_SP, 13f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setSquareSize(R.id.btn_play, 44f)
                    views.setSquareSize(R.id.btn_prev, 40f)
                    views.setSquareSize(R.id.btn_next, 40f)
                    views.setSquareSize(R.id.btn_like, 40f)
                    dp(context, 11f).let { views.setViewPadding(R.id.btn_play, it, it, it, it) }
                    dp(context, 8f).let {
                        views.setViewPadding(R.id.btn_prev, it, it, it, it)
                        views.setViewPadding(R.id.btn_next, it, it, it, it)
                    }
                    dp(context, 10f).let { views.setViewPadding(R.id.btn_like, it, it, it, it) }
                }
            }
            return views
        }

        private fun buildCard(context: Context, size: SizeF, frame: Frame): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_expanded)
            bindShared(views, context, frame)
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

            val showTimes = size.height >= CARD_TIMES_MIN_HEIGHT_DP
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
            return views
        }

        /** Views both layouts share: text, cover, play, like, skip, and their click targets. */
        private fun bindShared(views: RemoteViews, context: Context, frame: Frame) {
            val snapshot = frame.snapshot
            if (snapshot.hasTrack) {
                views.setTextViewText(R.id.txt_title, snapshot.title.ifBlank { context.getString(R.string.txt_notrack) })
                views.setTextViewText(R.id.txt_artist, snapshot.artist)
                views.setViewVisibility(R.id.txt_artist, if (snapshot.artist.isBlank()) View.GONE else View.VISIBLE)
            } else {
                views.setTextViewText(R.id.txt_title, context.getString(R.string.txt_not_playing))
                views.setViewVisibility(R.id.txt_artist, View.GONE)
            }

            if (frame.cover != null) {
                views.setImageViewBitmap(R.id.img_cover, frame.cover)
            } else {
                views.setImageViewResource(R.id.img_cover, R.drawable.logo_duck)
            }

            val playing = snapshot.hasTrack && snapshot.isPlaying
            views.setImageViewResource(R.id.btn_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setInt(R.id.btn_play, "setColorFilter", COLOR_ON_PLAY)
            views.setInt(R.id.btn_prev, "setColorFilter", COLOR_ICON)
            views.setInt(R.id.btn_next, "setColorFilter", COLOR_ICON)

            val liked = snapshot.hasTrack && snapshot.isLiked
            views.setImageViewResource(R.id.btn_like, if (liked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart)
            views.setInt(R.id.btn_like, "setColorFilter", if (liked) COLOR_ACCENT else COLOR_MUTED)
            views.setContentDescription(
                R.id.btn_like,
                context.getString(if (liked) UiR.string.remove_from_your_library else UiR.string.save_to_your_library),
            )

            views.setOnClickPendingIntent(R.id.widget_root, frame.openApp)
            views.setOnClickPendingIntent(R.id.btn_play, createPendingIntent(context, ACTION_PLAYPAUSE))
            views.setOnClickPendingIntent(R.id.btn_prev, createPendingIntent(context, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.btn_next, createPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.btn_like, createPendingIntent(context, ACTION_FAV))
        }

        private fun RemoteViews.setSquareSize(viewId: Int, sizeDp: Float) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            setViewLayoutWidth(viewId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(viewId, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        private fun dp(context: Context, value: Float): Int =
            (value * context.resources.displayMetrics.density + 0.5f).toInt()

        /** Square, rounded cover; null while the art is still downloading (a refresh follows). */
        private fun coverFor(context: Context, rawUrl: String): Bitmap? {
            val url = resolveRemoteImageUrl(rawUrl) ?: return null
            synchronized(coverLock) {
                if (url == coverUrl) coverBitmap?.let { return it }
            }
            val source = runCatching { WidgetArt.cached(url) }.getOrNull()
            if (source == null || source.isRecycled) {
                WidgetArt.prefetch(context.applicationContext, url) { updateAllWidgets(context) }
                return null
            }
            val cover = runCatching { squareCover(source) }.getOrNull() ?: return null
            synchronized(coverLock) {
                coverUrl = url
                coverBitmap = cover
            }
            return cover
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
            return output
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

        private fun formatTime(millis: Long): String {
            if (millis <= 0L) return "0:00"
            val totalSeconds = millis / 1000L
            val seconds = totalSeconds % 60L
            val minutes = totalSeconds / 60L
            return "%d:%02d".format(minutes, seconds)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val app = context.applicationContext
        val bridge = PlaybackHost.ensure(app)

        when (action) {
            ACTION_PLAYPAUSE -> {
                val live = bridge.let { WidgetSnapshot.from(it.state, it.progress) }.takeIf { it.hasTrack }
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
