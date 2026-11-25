package com.quackify.compose.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quackify.backend.core.store.StreamSource
import com.quackify.compose.R
import com.quackify.compose.components.CanvasVideoPlayer
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.components.EncoreExplicitTag
import com.quackify.compose.components.EncoreHeartButton
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncoreMenu
import com.quackify.compose.components.EncoreMenuItem
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.EncoreRepeatButton
import com.quackify.compose.components.EncoreSmartShuffleButton
import com.quackify.compose.components.MusicVideoSurface
import com.quackify.compose.components.QuackifyToastHost
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.rememberQuackifyToastState
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.sheets.DarkSystemBarsSheet
import com.quackify.compose.sheets.LyricsProviderSheet
import com.quackify.compose.sheets.LyricsShortcutSheet
import com.quackify.compose.sheets.TranslationLanguageSheet
import com.quackify.compose.shell.ChapterMarker
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreSpacers
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.util.rememberDominantColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

private const val LANDSCAPE_ARTWORK_PANE_WEIGHT = 0.40f
private const val LANDSCAPE_CONTROLS_PANE_WEIGHT = 0.60f
private const val DRAG_AXIS_SLOP_PX = 6f
private const val SNAP_BACK_FLING_VELOCITY = -100f
private const val DISMISS_FLING_VELOCITY = 350f
private const val DISMISS_THRESHOLD_DP = 96f
private const val AXIS_UNDECIDED = 0
private const val AXIS_VERTICAL = 2
private const val AXIS_YIELD = 3

/** Reveal choreography: the artwork lands first, then the rest follows. */
private const val REVEAL_MS = 420
private const val REVEAL_ARTWORK_DELAY_MS = 0
private const val REVEAL_INFO_DELAY_MS = 90
private const val REVEAL_CONTROLS_DELAY_MS = 160
private const val REVEAL_REST_DELAY_MS = 220
private const val REVEAL_RISE_FRACTION = 0.06f

/** Canvas banners are glued to the status bar and zoomed to hide the seam. */
private const val CANVAS_ZOOM = 1.06f
private val HEADER_ROW_HEIGHT = 60.dp
private val PEEK_HEIGHT = 44.dp

/** Episode comments render at most this many rows before the thread scrolls. */
private const val MAX_VISIBLE_COMMENTS = 5

/** Height cap for the scrollable thread: MAX_VISIBLE_COMMENTS rows. */
private val COMMENT_THREAD_MAX_HEIGHT = 380.dp

private val LANDSCAPE_LYRICS_PEEK_HEIGHT = 200.dp

private val InkDark = Color(0xFF191414)

/**
 * The lyrics panels and the header row sit straight on the album's dominant
 * color, so white content disappears on light covers (and dark content on
 * dark ones). Pick the ink from the background's luminance.
 */
private fun contrastInk(background: Color): Color =
    if (background.luminance() > 0.4f) InkDark else Color.White

/**
 * Staggered entrance for the player's blocks: each one rises a little and
 * fades in, in the order the eye should follow them - artwork, track info,
 * controls, then everything below. Driven off one shared progress value so
 * the whole reveal reads as a single motion rather than four animations.
 */
@Composable
private fun Modifier.revealAt(
    progress: Float,
    delayFraction: Float
): Modifier {
    val local = ((progress - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f)
    return this
        .graphicsLayer {
            alpha = local
            translationY = (1f - local) * size.height * REVEAL_RISE_FRACTION
        }
}

@Composable
private fun rememberRevealProgress(): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = REVEAL_MS, easing = EncoreAnimation.DecelerateInterpolator)
        )
    }
    return progress.value
}

/**
 * - Context header ("PLAYING FROM PLAYLIST" with chevron collapse)
 * - 1:1 Album Artwork with swipe-to-skip gesture
 * - Scrubber slider with elapsed and remaining timestamps
 * - 64dp Play/Pause button with Shuffle & Repeat indicators
 * - Connect Device bar with Share & Queue buttons
 * - Lyrics Peek Card with fullscreen expansion
 * - "About the Artist" Card with Verified Artist badge and Follow button
 * - Landscape: two-pane layout with artwork/canvas on the left pane and
 *   scrollable controls, lyrics and artist info on the right pane
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncoreNowPlayingScreen(
    title: String,
    artist: String,
    albumOrPlaylistContext: String,
    isPlaying: Boolean,
    progress: Float, // 0.0f to 1.0f
    durationMs: Long,
    currentPositionMs: Long,
    isLiked: Boolean,
    isShuffle: Boolean = false,
    isRepeat: Boolean = false,
    canShuffle: Boolean = true,
    canSmartShuffle: Boolean = false,
    canRepeat: Boolean = true,
    canSkipPrevious: Boolean = true,
    canSeek: Boolean = true,
    connectedDeviceName: String,
    onCollapseClick: () -> Unit,
    onExpandPlayerClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSwipePreviousClick: () -> Unit = onSkipPreviousClick,
    docked: Boolean = false,
    onLikeClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onMoreClick: () -> Unit,
    onQueueClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onTitleLongClick: (() -> Unit)? = null,
    onArtistClick: () -> Unit = {},
    onArtistLongClick: (() -> Unit)? = null,
    canvasFilePath: String? = null,
    isCanvasAllowed: Boolean = true,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: (Boolean) -> Unit = {},
    musicVideoAvailable: Boolean = false,
    videoActive: Boolean = false,
    streamSource: String = "",
    videoQualities: List<Int> = emptyList(),
    videoSwitching: Boolean = false,
    onSwitchVideo: (Boolean) -> Unit = {},
    videoFullscreen: Boolean = false,
    onToggleVideoFullscreen: (Boolean) -> Unit = {},
    onVideoQuality: (Int) -> Unit = {},
    onVideoSurface: (android.view.Surface?) -> Unit = {},
    isEpisode: Boolean = false,
    playbackSpeed: Float = 1f,
    chapters: List<ChapterMarker> = emptyList(),
    onPlaybackSpeed: (Float) -> Unit = {},
    onSkipSeek: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    onBanClick: (() -> Unit)? = null,
    contextHeaderTitle: String? = null,
    isExplicit: Boolean = false,
    isEnhancedTrack: Boolean = false,
    shuffleMode: ShuffleMode = if (isShuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF,
    repeatMode: RepeatMode = if (isRepeat) RepeatMode.REPEAT_ALL else RepeatMode.OFF,
    dominantColor: Color = Color(0xFF432371),
    coverUrl: String? = null,
    isLoading: Boolean = false,
    monthlyListeners: String = "",
    artistBio: String = "",
    artistImageUrl: String? = null,
    isArtistFollowing: Boolean = false,
    onArtistFollowToggle: (Boolean) -> Unit = {},
    nowPlayingShow: com.quackify.backend.model.ShowDetail? = null,
    nowPlayingComments: List<com.quackify.backend.model.EpisodeComment> = emptyList(),
    onCommentsRefresh: () -> Unit = {},
    onEpisodeComment: suspend (String) -> Boolean = { false },
    onLoadCommentReplies: suspend (String, String?) -> com.quackify.backend.model.EpisodeCommentReplies? = { _, _ -> null },
    onCommentReaction: suspend (String, String) -> Boolean = { _, _ -> false },
    onRemoveCommentReaction: suspend (String) -> Boolean = { false },
    onLoadCommentReactions: suspend (String) -> com.quackify.backend.model.EpisodeCommentReactions? = { null },
    selfUserUri: String = "",
    onDeleteComment: suspend (String) -> Boolean = { false },
    lyrics: List<TimeSyncedLyric> = emptyList(),
    nextTrackLyrics: List<TimeSyncedLyric> = emptyList(),
    transcript: List<TranscriptLine> = emptyList(),
    lyricsProviderName: String = "",
    showLyricsPreview: Boolean = true,
    previousTrack: TrackItem? = null,
    nextTrack: TrackItem? = null,
    onSkipToPreviousClick: () -> Unit = {}
) {
    var isLyricsFullscreen by remember { mutableStateOf(false) }
    var isLyricsShareOpen by remember { mutableStateOf(false) }
    var isLyricsMenuOpen by remember { mutableStateOf(false) }
    var isTranslationLanguageSheetOpen by remember { mutableStateOf(false) }
    var isLyricsProviderSheetOpen by remember { mutableStateOf(false) }
    var isTranscriptFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isLyricsFullscreen) { isLyricsFullscreen = false }
    BackHandler(enabled = isTranscriptFullscreen) { isTranscriptFullscreen = false }

    val view = LocalView.current
    DisposableEffect(videoActive) {
        view.keepScreenOn = videoActive
        onDispose { view.keepScreenOn = false }
    }

    val appContext = LocalContext.current.applicationContext
    val realLandscape = view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val onToggleViewLayout: (() -> Unit)? = if (realLandscape) {
        {
            val dual = PlayerViewOptions.landscapeDualView
            PlayerViewOptions.setLandscapeDualView(appContext, !dual)
            if (dual && docked) onExpandPlayerClick()
        }
    } else {
        null
    }

    if (videoFullscreen && videoActive) {
        Dialog(
            onDismissRequest = { onToggleVideoFullscreen(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val dialogView = LocalView.current
            LaunchedEffect(dialogView) {
                val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
                val controller = WindowCompat.getInsetsController(window, dialogView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            VideoFullscreenLayout(
                title = title,
                videoQualities = videoQualities,
                onVideoQuality = onVideoQuality,
                onVideoSurface = onVideoSurface,
                progress = progress,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                onSeek = onSeek,
                onSkipSeek = onSkipSeek,
                isLoading = isLoading,
                canSeek = canSeek,
                chapters = chapters,
                isPlaying = isPlaying,
                canSkipPrevious = canSkipPrevious,
                onPlayPause = onPlayPauseClick,
                onSkipPrevious = onSkipToPreviousClick,
                onSkipNext = onSkipNextClick,
                onExit = { onToggleVideoFullscreen(false) }
            )
        }
        return
    }

    val canvasActive = isCanvasAllowed && isCanvasEnabled && canvasFilePath != null

    val coroutineScope = rememberCoroutineScope()
    val playerOffsetY = remember { Animatable(0f) }
    val scrollState = rememberScrollState()
    val reveal = rememberRevealProgress()

    val activeLyricIndex = remember(currentPositionMs, lyrics) {
        lyrics.indexOfLast { currentPositionMs >= it.startTimeMs }.coerceAtLeast(0)
    }
    val activeLyricLine = lyrics.getOrNull(activeLyricIndex)
    val activeLyricText = activeLyricLine?.text.orEmpty()
    // One frame-ticking clock for every place lyrics render on this screen:
    // the one-line preview strip under the artwork, the peek card and the
    // fullscreen view. Each renderer reads the same State from its draw
    // phase, so they all sweep in lock-step without recomposing per frame.
    val lyricClock = rememberLyricClock(
        reportedPositionMs = { currentPositionMs },
        isPlaying = isPlaying,
        trackKey = lyrics.firstOrNull()?.startTimeMs,
    )
    // The transcript preview strip and the peek card sweep against the same
    // frame clock as the fullscreen view, with the transcript's own offset
    // rather than the lyrics one, so the spoken words travel smoothly in all
    // three surfaces instead of jumping a word per position report.
    val transcriptClock = rememberLyricClock(
        reportedPositionMs = { currentPositionMs },
        isPlaying = isPlaying,
        trackKey = transcript.firstOrNull()?.startMs,
        offsetMs = { LyricsOptions.transcriptOffsetMs },
    )

    val activeTranscriptIndex = remember(currentPositionMs, transcript) {
        transcript.indexOfLast { currentPositionMs >= it.startMs }.coerceAtLeast(0)
    }
    val activeTranscriptRow = transcript.getOrNull(activeTranscriptIndex)
    val previewTranscriptRow = if (activeTranscriptRow?.speaker == true) {
        transcript.getOrNull(activeTranscriptIndex + 1) ?: activeTranscriptRow
    } else {
        activeTranscriptRow
    }

    val effectiveMonthlyListeners = if (isEpisode) {
        nowPlayingShow?.let { show ->
            val rating = if (show.rating > 0.0) "★ %.1f".format(show.rating) else ""
            listOfNotNull(
                rating.takeIf { it.isNotBlank() },
                show.topics.firstOrNull(),
            ).joinToString(" • ").takeIf { it.isNotBlank() }
        }.orEmpty()
    } else {
        monthlyListeners
    }
    val effectiveArtistBio = if (isEpisode) {
        nowPlayingShow?.description.orEmpty()
    } else {
        artistBio.ifBlank {
            if (artist.isNotBlank()) "Listen to $artist. Discover top songs and upcoming releases." else ""
        }
    }
    val effectiveArtistImageUrl = if (isEpisode) {
        nowPlayingShow?.imageUrl?.takeIf { it.isNotBlank() } ?: artistImageUrl
    } else {
        artistImageUrl
    }
    val effectiveArtistName = if (isEpisode) (nowPlayingShow?.publisher ?: artist) else artist
    val effectiveIsFollowing = if (isEpisode) (nowPlayingShow?.saved == true) else isArtistFollowing
    val effectiveOnFollowToggle: (Boolean) -> Unit = if (isEpisode) {
        { onArtistFollowToggle(it) }
    } else {
        { onArtistFollowToggle(it) }
    }
    val showAboutArtist = if (isEpisode) {
        effectiveArtistName.isNotBlank()
    } else {
        artist.isNotBlank()
    }
    var commentsRefreshTick by remember(nowPlayingComments) { mutableStateOf(0) }
    LaunchedEffect(commentsRefreshTick) {
        if (commentsRefreshTick > 0) onCommentsRefresh()
    }

    val effectiveIsLoading = isLoading
    val lyricsAvailable = lyrics.isNotEmpty() && activeLyricText.isNotBlank()
    val effectiveLyricsVisible = lyricsAvailable
    val previewLyricVisible = effectiveLyricsVisible && showLyricsPreview
    val transcriptAvailable = transcript.isNotEmpty()
    val previewTranscriptVisible = transcriptAvailable && showLyricsPreview
    val previewLineVisible = previewLyricVisible || previewTranscriptVisible

    val lyricLineHeight by animateDpAsState(
        targetValue = if (previewLyricVisible) 44.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "lyricLineHeight"
    )
    val transcriptLineHeight by animateDpAsState(
        targetValue = if (previewTranscriptVisible) 44.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "transcriptLineHeight"
    )
    val lyricBottomSpacerHeight by animateDpAsState(
        targetValue = if (previewLineVisible) 28.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "lyricBottomSpacerHeight"
    )

    val artworkPushUpOffset by animateDpAsState(
        targetValue = if (previewLineVisible) 0.dp else 40.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "artworkPushUpOffset"
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val canvasImmersive = canvasActive && !videoActive && !isLandscape
    var canvasControlsRevealed by remember { mutableStateOf(true) }
    val canvasControlsShown = !canvasImmersive || canvasControlsRevealed
    var prevCanvasEnabled by remember { mutableStateOf(isCanvasEnabled) }

    LaunchedEffect(canvasImmersive) {
        if (!canvasImmersive) canvasControlsRevealed = true
    }
    LaunchedEffect(isCanvasEnabled) {
        if (isCanvasEnabled && !prevCanvasEnabled) canvasControlsRevealed = false
        prevCanvasEnabled = isCanvasEnabled
    }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = with(density) { DISMISS_THRESHOLD_DP.dp.toPx() }
    val videoTopHeight = (configuration.screenWidthDp * 9f / 16f).dp
    val artworkSide = minOf(
        configuration.screenWidthDp - 48,
        (configuration.screenHeightDp * 0.55f).toInt()
    ).coerceAtLeast(120).dp
    val mediaTransition = tween<Dp>(350, easing = EncoreAnimation.DecelerateInterpolator)
    val animatedMediaHeight by animateDpAsState(
        targetValue = if (videoActive) videoTopHeight else artworkSide,
        animationSpec = mediaTransition,
        label = "mediaHeight"
    )
    val animatedMediaPadding by animateDpAsState(
        targetValue = if (videoActive) 0.dp else 24.dp,
        animationSpec = mediaTransition,
        label = "mediaPadding"
    )
    val topSpacerWeight by animateFloatAsState(
        targetValue = if (videoActive) 0f else 1f,
        animationSpec = tween(350, easing = EncoreAnimation.DecelerateInterpolator),
        label = "topSpacerWeight"
    )
    val bottomSpacerWeight by animateFloatAsState(
        targetValue = if (videoActive) 0f else 1f,
        animationSpec = tween(350, easing = EncoreAnimation.DecelerateInterpolator),
        label = "bottomSpacerWeight"
    )

    LaunchedEffect(Unit) {
        playerOffsetY.snapTo(0f)
    }

    val dismissPlayer: () -> Unit = {
        coroutineScope.launch {
            playerOffsetY.animateTo(
                targetValue = screenHeightPx + 500f,
                animationSpec = tween(
                    durationMillis = EncoreAnimation.NowPlayingExitDuration,
                    easing = EncoreAnimation.DecelerateInterpolator
                )
            )
            onCollapseClick()
        }
    }

    val snapBackPlayer: () -> Unit = {
        coroutineScope.launch {
            playerOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = EncoreAnimation.NowPlayingExitDuration,
                    easing = EncoreAnimation.DecelerateInterpolator
                )
            )
        }
    }

    val headerDragModifier = if (docked) {
        Modifier
    } else {
        Modifier.npvDismissDragGesture(
            coroutineScope = coroutineScope,
            playerOffsetY = playerOffsetY,
            dismissThresholdPx = dismissThresholdPx,
            onDismiss = dismissPlayer,
            onSnapBack = snapBackPlayer
        )
    }

    val artworkGestureModifier = Modifier.npvArtworkGesture(
        coroutineScope = coroutineScope,
        playerOffsetY = playerOffsetY,
        scrollState = scrollState,
        scrollEnabled = !effectiveIsLoading,
        dismissThresholdPx = dismissThresholdPx,
        dismissEnabled = !docked,
        onDismiss = dismissPlayer,
        onSnapBack = snapBackPlayer
    )

    val artworkSwipeModifier = npvSwipeToSkip(
        canSwipePrevious = canSkipPrevious,
        canSwipeNext = nextTrack != null,
        onSwipePrevious = onSkipToPreviousClick,
        onSwipeNext = onSkipNextClick
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = playerOffsetY.value.coerceAtLeast(0f)
            }
            .pointerInput(canvasImmersive) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var consumed = false
                    var dragDistance = 0f
                    var dragX = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) consumed = true
                        val delta = change.positionChangeIgnoreConsumed()
                        dragDistance += delta.getDistance()
                        dragX += delta.x
                        if (canvasImmersive && !canvasControlsRevealed && dragDistance > viewConfiguration.touchSlop) {
                            canvasControlsRevealed = true
                        }
                        if (!change.pressed) break
                    }
                    val swipeThreshold = size.width.toFloat() * SWIPE_COMMIT_FRACTION
                    if (canvasImmersive && !consumed && dragX < -swipeThreshold && nextTrack != null) {
                        onSkipNextClick()
                    } else if (canvasImmersive && !consumed && dragX > swipeThreshold && canSkipPrevious) {
                        onSkipToPreviousClick()
                    } else if (canvasImmersive && !consumed && dragDistance <= viewConfiguration.touchSlop) {
                        canvasControlsRevealed = !canvasControlsRevealed
                    }
                }
            }
    ) {
        val viewportHeight = maxHeight
        val dynamicArtworkColor by rememberDominantColor(
            imageUrl = coverUrl,
            fallbackSeed = title + artist,
            defaultColor = dominantColor
        )

        val animatedDominantColor by animateColorAsState(
            targetValue = if (videoActive) Color(0xFF121212) else dynamicArtworkColor,
            animationSpec = tween(durationMillis = 600, easing = EncoreAnimation.DecelerateInterpolator),
            label = "npvDominantColorTransition"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to animatedDominantColor,
                        0.45f to lerp(animatedDominantColor, Color(0xFF121212), 0.35f),
                        0.80f to lerp(animatedDominantColor, Color(0xFF121212), 0.75f),
                        1.0f to Color(0xFF121212)
                    )
                )
        )

        if (canvasActive && !videoActive && !isLandscape) {
            CanvasVideoPlayer(
                filePath = canvasFilePath,
                zoom = CANVAS_ZOOM,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            val resolvedContextTitle = contextHeaderTitle ?: when {
                albumOrPlaylistContext.isBlank() || albumOrPlaylistContext.equals("Recommended for you", ignoreCase = true) -> stringResource(R.string.recommended_for_you)
                else -> stringResource(R.string.playing_from_playlist)
            }
            val hasSubtitle = albumOrPlaylistContext.isNotBlank() && !albumOrPlaylistContext.equals("Recommended for you", ignoreCase = true)

            val isScrollEnabled = !effectiveIsLoading

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(LANDSCAPE_ARTWORK_PANE_WEIGHT)
                            .fillMaxHeight()
                            .then(artworkGestureModifier)
                    ) {
                        if (videoActive || canvasActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NowPlayingArtworkCard(
                                    title = title,
                                    coverUrl = coverUrl,
                                    dominantColor = dynamicArtworkColor,
                                    backgroundColor = animatedDominantColor,
                                    canvasFilePath = canvasFilePath,
                                    isCanvasEnabled = canvasActive,
                                    videoActive = videoActive,
                                    gestureModifier = if (canvasActive && !videoActive) artworkSwipeModifier else Modifier,
                                    videoQualities = videoQualities,
                                    onVideoQuality = onVideoQuality,
                                    onVideoSurface = onVideoSurface,
                                    onToggleVideoFullscreen = { onToggleVideoFullscreen(true) },
                                    modifier = Modifier
                                        .aspectRatio(if (videoActive) 16f / 9f else 1.0f)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SwipableTrackArtwork(
                                    currentTitle = title,
                                    currentCoverUrl = coverUrl,
                                    currentDominantColor = dynamicArtworkColor,
                                    cardBackgroundColor = animatedDominantColor,
                                    previousTrack = previousTrack,
                                    nextTrack = nextTrack,
                                    canSwipePrevious = canSkipPrevious,
                                    onSwipePrevious = onSkipToPreviousClick,
                                    onSwipeNext = onSkipNextClick,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(LANDSCAPE_CONTROLS_PANE_WEIGHT)
                            .fillMaxHeight()
                    ) {
                        val paneHeight = maxHeight
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(state = scrollState)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = paneHeight),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                NowPlayingHeaderRow(
                                    resolvedContextTitle = resolvedContextTitle,
                                    contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                                    showCollapse = !docked,
                                    onCollapseClick = onCollapseClick,
                                    onMoreClick = onMoreClick,
                                    dominantColor = animatedDominantColor,
                                    dragModifier = headerDragModifier,
                                    onToggleViewLayout = onToggleViewLayout
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    NowPlayingSyncedLyricLine(
                                        lyricLine = activeLyricLine,
                                        clockMs = lyricClock,
                                        visible = previewLyricVisible,
                                        height = if (previewLyricVisible) 44.dp else 0.dp,
                                        onOpenLyrics = { isLyricsFullscreen = true }
                                    )

                                    NowPlayingTranscriptLine(
                                        line = previewTranscriptRow,
                                        clockMs = transcriptClock,
                                        ink = contrastInk(dynamicArtworkColor),
                                        visible = previewTranscriptVisible,
                                        height = if (previewTranscriptVisible) 44.dp else 0.dp,
                                        onOpenTranscript = { isTranscriptFullscreen = true }
                                    )

                                    MusicVideoSwitchRow(
                                        available = musicVideoAvailable,
                                        active = videoActive,
                                        switching = videoSwitching,
                                        onSwitch = onSwitchVideo
                                    )

                                    if (previewLineVisible) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    NowPlayingTrackInfoRow(
                                        title = title,
                                        artist = artist,
                                        isExplicit = isExplicit,
                                        isLiked = isLiked,
                                        showSmartShuffleBadge = isEnhancedTrack,
                                        onTitleClick = onTitleClick,
                                        onTitleLongClick = onTitleLongClick,
                                        onArtistClick = onArtistClick,
                                        onArtistLongClick = onArtistLongClick,
                                        onLikeClick = onLikeClick,
                                        onBanClick = onBanClick
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    NowPlayingSeekBarSection(
                                        progress = progress,
                                        durationMs = durationMs,
                                        currentPositionMs = currentPositionMs,
                                        onSeek = onSeek,
                                        isLoading = effectiveIsLoading,
                                        canSeek = canSeek,
                                        markers = chapterFractions(chapters, durationMs)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (isEpisode) {
                                        EpisodeControlsRow(
                                            playbackSpeed = playbackSpeed,
                                            isPlaying = isPlaying,
                                            isLoading = effectiveIsLoading,
                                            onPlaybackSpeed = onPlaybackSpeed,
                                            onSkipSeek = onSkipSeek,
                                            onSkipPreviousClick = onSkipPreviousClick,
                                            onPlayPauseClick = onPlayPauseClick,
                                            onSkipNextClick = onSkipNextClick
                                        )
                                    } else {
                                        NowPlayingControlsRow(
                                            shuffleMode = shuffleMode,
                                            repeatMode = repeatMode,
                                            isPlaying = isPlaying,
                                            isLoading = effectiveIsLoading,
                                            canShuffle = canShuffle,
                                            canSmartShuffle = canSmartShuffle,
                                            canRepeat = canRepeat,
                                            canSkipPrevious = canSkipPrevious,
                                            onShuffleClick = onShuffleClick,
                                            onSkipPreviousClick = onSkipPreviousClick,
                                            onPlayPauseClick = onPlayPauseClick,
                                            onSkipNextClick = onSkipNextClick,
                                            onRepeatClick = onRepeatClick
                                        )
                                    }
                                }

                                NowPlayingFooterRow(
                                    connectedDeviceName = connectedDeviceName,
                                    onDeviceClick = onDeviceClick,
                                    onQueueClick = onQueueClick,
                                    onShareClick = onShareClick
                                )
                            }

                        Spacer(modifier = Modifier.height(12.dp))

                        NowPlayingLyricsCard(
                            visible = lyricsAvailable,
                            lyrics = lyrics,
                            activeIndex = activeLyricIndex,
                            clockMs = lyricClock,
                            dominantColor = dynamicArtworkColor,
                            peekHeight = LANDSCAPE_LYRICS_PEEK_HEIGHT,
                            onOpenLyrics = { isLyricsFullscreen = true },
                            onShareLyrics = { isLyricsShareOpen = true }
                        )

                        NowPlayingTranscriptCard(
                            visible = transcriptAvailable,
                            lines = transcript,
                            activeIndex = activeTranscriptIndex,
                            clockMs = transcriptClock,
                            dominantColor = dynamicArtworkColor,
                            peekHeight = LANDSCAPE_LYRICS_PEEK_HEIGHT,
                            onOpenTranscript = { isTranscriptFullscreen = true }
                        )

                        if (!effectiveIsLoading && showAboutArtist) {
                            if (lyricsAvailable || transcriptAvailable) {
                                Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                            }

                            NowPlayingAboutArtistCard(
                                artist = effectiveArtistName,
                                monthlyListeners = effectiveMonthlyListeners,
                                artistBio = effectiveArtistBio,
                                artistImageUrl = effectiveArtistImageUrl,
                                isFollowingArtist = effectiveIsFollowing,
                                onFollowToggle = { effectiveOnFollowToggle(!effectiveIsFollowing) },
                                onArtistClick = onArtistClick,
                                isPodcastPublisher = isEpisode
                            )

                            if (isEpisode) {
                                Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                                NowPlayingEpisodeCommentsCard(
                                    comments = nowPlayingComments,
                                    onRefresh = { commentsRefreshTick++ },
                                    onPostComment = onEpisodeComment,
                                    onLoadReplies = onLoadCommentReplies,
                                    onReact = onCommentReaction,
                                    onRemoveReaction = onRemoveCommentReaction,
                                    onLoadReactions = onLoadCommentReactions,
                                    selfUserUri = selfUserUri,
                                    onDeleteComment = onDeleteComment
                                )
                            }

                            Spacer(modifier = Modifier.height(EncoreSpacers.Looser3))
                        }
                    }
                }
            }
        } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(state = scrollState, enabled = isScrollEnabled),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(artworkGestureModifier)
                            .then(if (canvasImmersive) artworkSwipeModifier else Modifier)
                            .heightIn(min = viewportHeight - PEEK_HEIGHT),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                            ) {
                                NowPlayingHeaderRow(
                                    resolvedContextTitle = resolvedContextTitle,
                                    contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                                    showCollapse = !docked,
                                    onCollapseClick = onCollapseClick,
                                    onMoreClick = onMoreClick,
                                    dominantColor = animatedDominantColor,
                                    dragModifier = headerDragModifier,
                                    onToggleViewLayout = onToggleViewLayout
                                )
                            }

                        Spacer(modifier = Modifier.weight(topSpacerWeight.coerceAtLeast(0.01f)))

                        Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .revealAt(reveal, REVEAL_ARTWORK_DELAY_MS / REVEAL_MS.toFloat()),
                                contentAlignment = Alignment.Center
                            ) {
                                if (canvasActive && !videoActive) {
                                    Spacer(
                                        modifier = Modifier
                                            .padding(horizontal = animatedMediaPadding)
                                            .fillMaxWidth()
                                            .aspectRatio(1.0f)
                                    )
                                } else if (videoActive) {
                                    NowPlayingArtworkCard(
                                        title = title,
                                        coverUrl = coverUrl,
                                        dominantColor = dynamicArtworkColor,
                                        backgroundColor = animatedDominantColor,
                                        canvasFilePath = canvasFilePath,
                                        isCanvasEnabled = canvasActive,
                                        videoActive = true,
                                        videoFullBleed = true,
                                        videoQualities = videoQualities,
                                        onVideoQuality = onVideoQuality,
                                        onVideoSurface = onVideoSurface,
                                        onToggleVideoFullscreen = { onToggleVideoFullscreen(true) },
                                        modifier = Modifier
                                            .padding(horizontal = animatedMediaPadding)
                                            .fillMaxWidth()
                                            .height(animatedMediaHeight)
                                    )
                                } else {
                                    SwipableTrackArtwork(
                                        currentTitle = title,
                                        currentCoverUrl = coverUrl,
                                        currentDominantColor = dynamicArtworkColor,
                                        cardBackgroundColor = animatedDominantColor,
                                        previousTrack = previousTrack,
                                        nextTrack = nextTrack,
                                        canSwipePrevious = canSkipPrevious,
                                        onSwipePrevious = onSkipToPreviousClick,
                                        onSwipeNext = onSkipNextClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(animatedMediaHeight)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(bottomSpacerWeight.coerceAtLeast(0.01f)))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!videoActive) {
                                NowPlayingSyncedLyricLine(
                                    lyricLine = activeLyricLine,
                                    clockMs = lyricClock,
                                    visible = previewLyricVisible,
                                    height = lyricLineHeight,
                                    onOpenLyrics = { isLyricsFullscreen = true }
                                )
                                NowPlayingTranscriptLine(
                                    line = previewTranscriptRow,
                                    clockMs = transcriptClock,
                                    ink = contrastInk(dynamicArtworkColor),
                                    visible = previewTranscriptVisible,
                                    height = transcriptLineHeight,
                                    onOpenTranscript = { isTranscriptFullscreen = true }
                                )
                            }

                            AnimatedVisibility(
                                visible = canvasControlsShown,
                                enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = EncoreAnimation.DecelerateInterpolator)),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                            ) {
                                MusicVideoSwitchRow(
                                    available = musicVideoAvailable,
                                    active = videoActive,
                                    switching = videoSwitching,
                                    onSwitch = onSwitchVideo
                                )
                            }

                            if (!videoActive) {
                                Spacer(modifier = Modifier.height(lyricBottomSpacerHeight))
                            }

                            NowPlayingTrackInfoRow(
                                title = title,
                                artist = artist,
                                isExplicit = isExplicit,
                                isLiked = isLiked,
                                showSmartShuffleBadge = isEnhancedTrack,
                                onTitleClick = onTitleClick,
                                onTitleLongClick = onTitleLongClick,
                                onArtistClick = onArtistClick,
                                onArtistLongClick = onArtistLongClick,
                                onLikeClick = onLikeClick,
                                onBanClick = onBanClick,
                                coverUrl = if (canvasImmersive) coverUrl else null
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            NowPlayingSeekBarSection(
                                progress = progress,
                                durationMs = durationMs,
                                currentPositionMs = currentPositionMs,
                                onSeek = onSeek,
                                isLoading = effectiveIsLoading,
                                canSeek = canSeek,
                                markers = chapterFractions(chapters, durationMs)
                            )

                            AnimatedVisibility(
                                visible = canvasControlsShown,
                                enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = EncoreAnimation.DecelerateInterpolator)),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(20.dp))

                                    if (isEpisode) {
                                        EpisodeControlsRow(
                                            playbackSpeed = playbackSpeed,
                                            isPlaying = isPlaying,
                                            isLoading = effectiveIsLoading,
                                            onPlaybackSpeed = onPlaybackSpeed,
                                            onSkipSeek = onSkipSeek,
                                            onSkipPreviousClick = onSkipPreviousClick,
                                            onPlayPauseClick = onPlayPauseClick,
                                            onSkipNextClick = onSkipNextClick
                                        )
                                    } else {
                                        NowPlayingControlsRow(
                                            shuffleMode = shuffleMode,
                                            repeatMode = repeatMode,
                                            isPlaying = isPlaying,
                                            isLoading = effectiveIsLoading,
                                            canShuffle = canShuffle,
                                            canSmartShuffle = canSmartShuffle,
                                            canRepeat = canRepeat,
                                            canSkipPrevious = canSkipPrevious,
                                            onShuffleClick = onShuffleClick,
                                            onSkipPreviousClick = onSkipPreviousClick,
                                            onPlayPauseClick = onPlayPauseClick,
                                            onSkipNextClick = onSkipNextClick,
                                            onRepeatClick = onRepeatClick
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    NowPlayingFooterRow(
                                        connectedDeviceName = connectedDeviceName,
                                        onDeviceClick = onDeviceClick,
                                        onQueueClick = onQueueClick,
                                        onShareClick = onShareClick
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .revealAt(reveal, REVEAL_REST_DELAY_MS / REVEAL_MS.toFloat())
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NowPlayingLyricsCard(
                                visible = lyricsAvailable && canvasControlsShown,
                                lyrics = lyrics,
                                activeIndex = activeLyricIndex,
                                clockMs = lyricClock,
                                dominantColor = dynamicArtworkColor,
                                onOpenLyrics = { isLyricsFullscreen = true },
                                onShareLyrics = { isLyricsShareOpen = true }
                            )

                            NowPlayingTranscriptCard(
                                visible = transcriptAvailable && canvasControlsShown,
                                lines = transcript,
                                activeIndex = activeTranscriptIndex,
                                clockMs = transcriptClock,
                                dominantColor = dynamicArtworkColor,
                                onOpenTranscript = { isTranscriptFullscreen = true }
                            )

                            if (!effectiveIsLoading && showAboutArtist) {
                                AnimatedVisibility(
                                    visible = canvasControlsShown,
                                    enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = EncoreAnimation.DecelerateInterpolator)),
                                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                                ) {
                                    Column {
                                        if (lyricsAvailable || transcriptAvailable) {
                                            Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                                        }

                                        NowPlayingAboutArtistCard(
                                            artist = effectiveArtistName,
                                            monthlyListeners = effectiveMonthlyListeners,
                                            artistBio = effectiveArtistBio,
                                            artistImageUrl = effectiveArtistImageUrl,
                                            isFollowingArtist = effectiveIsFollowing,
                                            onFollowToggle = { effectiveOnFollowToggle(!effectiveIsFollowing) },
                                            onArtistClick = onArtistClick,
                                            isPodcastPublisher = isEpisode
                                        )

                                        if (isEpisode) {
                                            Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                                            NowPlayingEpisodeCommentsCard(
                                                comments = nowPlayingComments,
                                                onRefresh = { commentsRefreshTick++ },
                                                onPostComment = onEpisodeComment,
                                                onLoadReplies = onLoadCommentReplies,
                                                onReact = onCommentReaction,
                                                onRemoveReaction = onRemoveCommentReaction,
                                                onLoadReactions = onLoadCommentReactions,
                                                selfUserUri = selfUserUri,
                                                onDeleteComment = onDeleteComment
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isLyricsFullscreen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220)
            )
        ) {
            FullscreenLyricsView(
                title = title,
                artist = artist,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                dominantColor = dominantColor,
                artworkColor = dynamicArtworkColor,
                lyrics = lyrics,
                nextTrack = nextTrack,
                nextTrackLyrics = nextTrackLyrics,
                lyricsProviderName = lyricsProviderName,
                onSeekMs = { targetMs -> if (durationMs > 0L) onSeek(targetMs.toFloat() / durationMs.toFloat()) },
                onShareClick = { isLyricsShareOpen = true },
                onLyricsMenuClick = { isLyricsMenuOpen = true },
                onClose = { isLyricsFullscreen = false }
            )
        }

        AnimatedVisibility(
            visible = isTranscriptFullscreen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220)
            )
        ) {
            FullscreenTranscriptView(
                title = title,
                artist = artist,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                dominantColor = dominantColor,
                lines = transcript,
                onSeekMs = { targetMs -> if (durationMs > 0L) onSeek(targetMs.toFloat() / durationMs.toFloat()) },
                onClose = { isTranscriptFullscreen = false }
            )
        }

        AnimatedVisibility(
            visible = isLyricsShareOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220)
            )
        ) {
            LyricsShareDialog(
                title = title,
                artist = artist,
                lyrics = lyrics,
                initialDominantColor = dominantColor,
                onDismiss = { isLyricsShareOpen = false },
                onShareConfirm = { _, _, _ -> isLyricsShareOpen = false }
            )
        }

        if (isLyricsMenuOpen) {
            val context = LocalContext.current
            LyricsShortcutSheet(
                onDismiss = { isLyricsMenuOpen = false },
                onShareClick = { isLyricsShareOpen = true },
                onOpenTranslationLanguage = { isTranslationLanguageSheetOpen = true },
                onOpenLyricsSources = { isLyricsProviderSheetOpen = true }
            )
        }
        if (isTranslationLanguageSheetOpen) {
            val context = LocalContext.current
            TranslationLanguageSheet(
                selectedCode = LyricsOptions.translationLanguage,
                onLanguageSelected = { code ->
                    LyricsOptions.setTranslationLanguage(context, code)
                },
                onDismiss = { isTranslationLanguageSheetOpen = false }
            )
        }
        if (isLyricsProviderSheetOpen) {
            val context = LocalContext.current
            val stream = streamSource
                .let { key -> StreamSource.entries.firstOrNull { it.key == key } }
                ?: StreamSource.SPOTIFY
            LyricsProviderSheet(
                orderedProviders = LyricsOptions.providerOrderFor(stream),
                stream = stream,
                onOrderSelected = { order ->
                    if (stream == StreamSource.YT_MUSIC) {
                        LyricsOptions.setYtmProviderOrder(context, order)
                    } else {
                        LyricsOptions.setProviderOrder(context, order)
                    }
                },
                onDismiss = { isLyricsProviderSheetOpen = false }
            )
        }
    }
}

/**
 * Drag-to-dismiss gesture for the Now Playing header: dragging down
 * translates the whole player, releasing past the threshold (or with
 * enough downward velocity) collapses it back to the mini player.
 */
private fun Modifier.npvDismissDragGesture(
    coroutineScope: CoroutineScope,
    playerOffsetY: Animatable<Float, AnimationVector1D>,
    dismissThresholdPx: Float,
    onDismiss: () -> Unit,
    onSnapBack: () -> Unit
): Modifier = pointerInput(dismissThresholdPx) {
    val velocityTracker = VelocityTracker()
    detectDragGestures(
        onDragStart = { velocityTracker.resetTracking() },
        onDragEnd = {
            val velocity = velocityTracker.calculateVelocity()
            coroutineScope.launch {
                if (velocity.y > DISMISS_FLING_VELOCITY || playerOffsetY.value > dismissThresholdPx) {
                    onDismiss()
                } else {
                    onSnapBack()
                }
            }
        },
        onDragCancel = { onSnapBack() },
        onDrag = { change, dragAmount ->
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            if (dragAmount.y > 0 || playerOffsetY.value > 0) {
                change.consume()
                coroutineScope.launch {
                    playerOffsetY.snapTo((playerOffsetY.value + dragAmount.y).coerceAtLeast(0f))
                }
            }
        }
    )
}

/**
 * Artwork gestures: vertical drags translate the whole player for
 * drag-to-dismiss while horizontal drags are yielded to the swipe
 * carousel that owns the artwork row.
 *
 * The axis is resolved manually, before any event is consumed. Vertical
 * drags are only claimed once the page can no longer scroll in that
 * direction, so dragging over the artwork scrolls the page like any other
 * part of it and only turns into a dismissal once the top is reached.
 *
 * The claim is re-evaluated on every move: the scroll may not sit at the
 * top when the drag starts, but it reaches it as the finger keeps pulling
 * down, and from that moment the player must track the finger 1:1 instead
 * of staying frozen until the next gesture. Deltas are read with
 * positionChangeIgnoreConsumed so an intermediate scroll consumer cannot
 * zero them out mid-drag.
 */
private fun Modifier.npvArtworkGesture(
    coroutineScope: CoroutineScope,
    playerOffsetY: Animatable<Float, AnimationVector1D>,
    scrollState: ScrollState,
    scrollEnabled: Boolean,
    dismissThresholdPx: Float,
    dismissEnabled: Boolean,
    onDismiss: () -> Unit,
    onSnapBack: () -> Unit
): Modifier = pointerInput(dismissThresholdPx, scrollEnabled, dismissEnabled) {
    var axis = AXIS_UNDECIDED
    var totalDragX = 0f
    var totalDragY = 0f
    val velocityTracker = VelocityTracker()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        axis = AXIS_UNDECIDED
        totalDragX = 0f
        totalDragY = 0f
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            val delta = change.positionChangeIgnoreConsumed()
            totalDragX += delta.x
            totalDragY += delta.y

            val verticalBias = totalDragY.absoluteValue > totalDragX.absoluteValue
            if (axis == AXIS_UNDECIDED || axis == AXIS_YIELD) {
                val pageAtTop = !scrollEnabled || scrollState.value <= 0
                if (totalDragY > DRAG_AXIS_SLOP_PX && verticalBias && pageAtTop && dismissEnabled) {
                    axis = AXIS_VERTICAL
                } else if (totalDragY < -DRAG_AXIS_SLOP_PX || totalDragX.absoluteValue > DRAG_AXIS_SLOP_PX && !verticalBias) {
                    axis = AXIS_YIELD
                }
            }

            when (axis) {
                AXIS_VERTICAL -> {
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    coroutineScope.launch {
                        playerOffsetY.snapTo((playerOffsetY.value + delta.y).coerceAtLeast(0f))
                    }
                }
                AXIS_YIELD -> Unit
            }
        }

        val velocity = velocityTracker.calculateVelocity()
        coroutineScope.launch {
            when (axis) {
                AXIS_VERTICAL -> {
                    when {
                        velocity.y < SNAP_BACK_FLING_VELOCITY -> onSnapBack()
                        velocity.y > DISMISS_FLING_VELOCITY -> onDismiss()
                        playerOffsetY.value > dismissThresholdPx -> onDismiss()
                        else -> onSnapBack()
                    }
                }
            }
            axis = AXIS_UNDECIDED
        }
    }
}

/**
 * Top Navigation Bar (Collapse Chevron, Context Header, More Options).
 * Reference: context_header.xml & revised_template_overlay.xml (@id/player_overlay_header)
 */
@Composable
private fun NowPlayingHeaderRow(
    resolvedContextTitle: String,
    contextSubtitle: String?,
    showCollapse: Boolean = true,
    onCollapseClick: () -> Unit,
    onMoreClick: () -> Unit,
    dominantColor: Color,
    dragModifier: Modifier = Modifier,
    onToggleViewLayout: (() -> Unit)? = null
) {
    val ink = contrastInk(dominantColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .then(dragModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCollapse) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_chevron_down_24,
                    contentDescription = stringResource(R.string.collapse_player),
                    onClick = onCollapseClick,
                    tint = ink,
                    size = 48.dp,
                    iconSize = 24.dp
                )
            }

            if (onToggleViewLayout != null) {
                EncoreIconButton(
                    iconRes = if (PlayerViewOptions.landscapeDualView) R.drawable.encore_icon_list_view_24
                    else R.drawable.encore_icon_grid_view_24,
                    contentDescription = stringResource(
                        if (PlayerViewOptions.landscapeDualView) R.string.switch_to_full_view
                        else R.string.switch_to_dual_view
                    ),
                    onClick = onToggleViewLayout,
                    tint = ink,
                    size = 48.dp,
                    iconSize = 24.dp
                )
            } else if (!showCollapse) {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = resolvedContextTitle,
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = ink
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contextSubtitle != null) {
                Text(
                    text = contextSubtitle,
                    style = EncoreTheme.typography.bodySmallBold.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_more_android_24,
            contentDescription = stringResource(R.string.more_options),
            onClick = onMoreClick,
            tint = ink,
            size = 48.dp,
            iconSize = 24.dp
        )
    }
}

/**
 * Large 1:1 Album Artwork with 8dp rounded corners, the real looping canvas
 * video (when the track has one) and the CANVAS toggle pill.
 * Reference: revised_template_page_layout.xml (@id/track_carousel) -> bounds [63,308][1017,1262] (24dp margins)
 */
@Composable
private fun NowPlayingArtworkCard(
    title: String,
    coverUrl: String?,
    dominantColor: Color,
    backgroundColor: Color,
    canvasFilePath: String?,
    isCanvasEnabled: Boolean,
    gestureModifier: Modifier = Modifier,
    videoActive: Boolean = false,
    videoFullBleed: Boolean = false,
    videoOverlayVisible: Boolean? = null,
    onVideoOverlayTap: (() -> Unit)? = null,
    onDoubleTapSeek: ((Long) -> Unit)? = null,
    videoQualities: List<Int> = emptyList(),
    onVideoQuality: (Int) -> Unit = {},
    onVideoSurface: (android.view.Surface?) -> Unit = {},
    onToggleVideoFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val corner = when {
        videoActive || videoFullBleed -> 0.dp
        else -> EncoreCornerRadii.Larger2
    }
    val canvasActive = isCanvasEnabled && canvasFilePath != null
    val surfaceModifier = if (canvasActive || videoActive) {
        Modifier
    } else {
        Modifier.background(backgroundColor)
    }
    Box(
        modifier = modifier
            .shadow(elevation = if (videoActive || videoFullBleed) 0.dp else 16.dp, shape = RoundedCornerShape(corner))
            .then(gestureModifier)
            .clip(RoundedCornerShape(corner))
            .then(surfaceModifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            videoActive -> {
                var internalOverlay by remember { mutableStateOf(false) }
                var seekHint by remember { mutableStateOf<Int?>(null) }
                val currentDoubleTapSeek by rememberUpdatedState(onDoubleTapSeek)
                val overlayVisible = videoOverlayVisible ?: internalOverlay
                if (videoOverlayVisible == null) {
                    LaunchedEffect(internalOverlay) {
                        if (internalOverlay) {
                            delay(3000.milliseconds)
                            internalOverlay = false
                        }
                    }
                }
                LaunchedEffect(seekHint) {
                    if (seekHint != null) {
                        delay(650.milliseconds)
                        seekHint = null
                    }
                }
                Box(
                    modifier = Modifier
                        .then(
                            if (videoFullBleed) Modifier.fillMaxSize()
                            else Modifier.fillMaxWidth(0.8f).aspectRatio(16f / 9f)
                        )
                        .clip(if (videoFullBleed) RectangleShape else RoundedCornerShape(16.dp))
                        .pointerInput(onDoubleTapSeek != null) {
                            if (onDoubleTapSeek != null) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        val seek = currentDoubleTapSeek
                                        if (seek != null) {
                                            val forward = offset.x >= size.width / 2f
                                            seekHint = if (forward) 1 else -1
                                            seek(if (forward) 15_000L else -15_000L)
                                        }
                                    },
                                    onTap = {
                                        if (videoOverlayVisible != null) onVideoOverlayTap?.invoke()
                                        else internalOverlay = !internalOverlay
                                    }
                                )
                            } else {
                                detectTapGestures {
                                    if (videoOverlayVisible != null) onVideoOverlayTap?.invoke()
                                    else internalOverlay = !internalOverlay
                                }
                            }
                        }
                ) {
                    MusicVideoSurface(
                        onSurface = onVideoSurface,
                        modifier = Modifier.fillMaxSize(),
                        sessionKey = title
                    )
                    AnimatedVisibility(
                        visible = seekHint != null,
                        enter = fadeIn(tween(durationMillis = 120)),
                        exit = fadeOut(tween(durationMillis = 280))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (seekHint == 1) {
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                                        } else {
                                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                                        }
                                    )
                                ),
                            contentAlignment = if (seekHint == 1) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (seekHint == 1) R.drawable.encore_icon_seek_forward_15
                                    else R.drawable.encore_icon_seek_back_15
                                ),
                                contentDescription = stringResource(
                                    if (seekHint == 1) R.string.forward_15_seconds
                                    else R.string.back_15_seconds
                                ),
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(horizontal = 40.dp)
                                    .size(38.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = overlayVisible,
                        enter = fadeIn(tween(durationMillis = 250)),
                        exit = fadeOut(tween(durationMillis = 250))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f))
                        ) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .clickable { onToggleVideoFullscreen() }
                                        .padding(7.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.encore_icon_fullscreen_24),
                                        contentDescription = stringResource(R.string.toggle_fullscreen_video),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .clickable { menuOpen = true }
                                            .padding(7.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.encore_icon_settings_24),
                                            contentDescription = stringResource(R.string.video_quality),
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    EncoreMenu(
                                        expanded = menuOpen,
                                        onDismiss = { menuOpen = false },
                                    ) {
                                        EncoreMenuItem(
                                            label = stringResource(R.string.auto),
                                            onClick = {
                                                onVideoQuality(0)
                                                menuOpen = false
                                            }
                                        )
                                        videoQualities.forEach { height ->
                                            EncoreMenuItem(
                                                label = "${height}p",
                                                onClick = {
                                                    onVideoQuality(height)
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            canvasActive -> {
                CanvasVideoPlayer(
                    filePath = canvasFilePath,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                EncoreArtworkImage(
                    imageUrl = coverUrl,
                    contentDescription = title,
                    fallbackColor = dominantColor,
                    requestSize = 640,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private const val SWIPE_COMMIT_FRACTION = 0.3f
private const val SWIPE_SETTLE_MS = 240

@Composable
private fun npvSwipeToSkip(
    canSwipePrevious: Boolean,
    canSwipeNext: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
): Modifier {
    val haptics = LocalHapticFeedback.current
    return Modifier.pointerInput(canSwipePrevious, canSwipeNext) {
        val threshold = (size.width * SWIPE_COMMIT_FRACTION).coerceAtLeast(40f)
        val flick = size.width * 1.5f
        val tracker = VelocityTracker()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                total = 0f
                tracker.resetTracking()
            },
            onDragCancel = { total = 0f },
            onHorizontalDrag = { change, amount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                total += amount
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().x
                tracker.resetTracking()
                val next = canSwipeNext && total < 0f && (total < -threshold || velocity < -flick)
                val previous = canSwipePrevious && total > 0f && (total > threshold || velocity > flick)
                if (next || previous) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    if (next) onSwipeNext() else onSwipePrevious()
                }
                total = 0f
            }
        )
    }
}

@Composable
private fun SwipableTrackArtwork(
    currentTitle: String,
    currentCoverUrl: String?,
    currentDominantColor: Color,
    cardBackgroundColor: Color,
    previousTrack: TrackItem?,
    nextTrack: TrackItem?,
    canSwipePrevious: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasPrev = canSwipePrevious && previousTrack != null
    val hasNext = nextTrack != null
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(currentCoverUrl, currentTitle) {
        drag.snapTo(0f)
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .pointerInput(hasPrev, hasNext) {
                val cardWidthPx = size.width.toFloat()
                val flickVelocity = cardWidthPx * 1.5f
                val commitDistancePx = cardWidthPx * SWIPE_COMMIT_FRACTION
                val tracker = VelocityTracker()
                var tickedNext = false
                var tickedPrev = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        tickedNext = false
                        tickedPrev = false
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        tracker.resetTracking()
                        val offset = drag.value
                        val commitNext = hasNext && offset < 0f &&
                            (offset < -cardWidthPx * SWIPE_COMMIT_FRACTION || velocity < -flickVelocity)
                        val commitPrev = hasPrev && offset > 0f &&
                            (offset > cardWidthPx * SWIPE_COMMIT_FRACTION || velocity > flickVelocity)
                        if ((commitNext && !tickedNext) || (commitPrev && !tickedPrev)) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                        if (commitNext) onSwipeNext()
                        if (commitPrev) onSwipePrevious()
                        scope.launch {
                            drag.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = SWIPE_SETTLE_MS,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            drag.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = SWIPE_SETTLE_MS,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val raw = drag.value + amount
                        val clamped = when {
                            raw > 0f && !hasPrev -> 0f
                            raw < 0f && !hasNext -> 0f
                            raw > cardWidthPx -> cardWidthPx
                            raw < -cardWidthPx -> -cardWidthPx
                            else -> raw
                        }
                        if (hasNext && clamped <= -commitDistancePx) {
                            if (!tickedNext) {
                                tickedNext = true
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        } else {
                            tickedNext = false
                        }
                        if (hasPrev && clamped >= commitDistancePx) {
                            if (!tickedPrev) {
                                tickedPrev = true
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        } else {
                            tickedPrev = false
                        }
                        scope.launch { drag.snapTo(clamped) }
                    }
                )
            }
    ) {
        val pageWidth = maxWidth
        Row(
            modifier = Modifier
                .requiredWidth(pageWidth * 3)
                .fillMaxHeight()
                .graphicsLayer { translationX = drag.value }
        ) {
            SwipeTrackArtworkSlot(
                title = previousTrack?.title.orEmpty(),
                coverUrl = previousTrack?.imageUrl,
                dominantColor = currentDominantColor,
                backgroundColor = cardBackgroundColor,
                modifier = Modifier
                    .width(pageWidth)
                    .fillMaxHeight()
            )
            SwipeTrackArtworkSlot(
                title = currentTitle,
                coverUrl = currentCoverUrl,
                dominantColor = currentDominantColor,
                backgroundColor = cardBackgroundColor,
                modifier = Modifier
                    .width(pageWidth)
                    .fillMaxHeight()
            )
            SwipeTrackArtworkSlot(
                title = nextTrack?.title.orEmpty(),
                coverUrl = nextTrack?.imageUrl,
                dominantColor = currentDominantColor,
                backgroundColor = cardBackgroundColor,
                modifier = Modifier
                    .width(pageWidth)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun SwipeTrackArtworkSlot(
    title: String,
    coverUrl: String?,
    dominantColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(EncoreCornerRadii.Larger2))
                .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            EncoreArtworkImage(
                imageUrl = coverUrl,
                contentDescription = title.ifBlank { null },
                fallbackColor = dominantColor,
                requestSize = 640,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun VideoFullscreenLayout(
    title: String,
    videoQualities: List<Int>,
    onVideoQuality: (Int) -> Unit,
    onVideoSurface: (android.view.Surface?) -> Unit,
    progress: Float,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Float) -> Unit,
    onSkipSeek: (Long) -> Unit,
    isLoading: Boolean,
    canSeek: Boolean,
    chapters: List<ChapterMarker>,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExit: () -> Unit,
) {
    var overlayVisible by remember { mutableStateOf(true) }
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            delay(3000.milliseconds)
            overlayVisible = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        NowPlayingArtworkCard(
            title = title,
            coverUrl = null,
            dominantColor = Color.Black,
            backgroundColor = Color.Black,
            canvasFilePath = null,
            isCanvasEnabled = false,
            videoActive = true,
            videoFullBleed = true,
            videoOverlayVisible = overlayVisible,
            videoQualities = videoQualities,
            onVideoQuality = onVideoQuality,
            onVideoSurface = onVideoSurface,
            onToggleVideoFullscreen = onExit,
            onVideoOverlayTap = { overlayVisible = !overlayVisible },
            onDoubleTapSeek = onSkipSeek,
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(tween(durationMillis = 250)),
            exit = fadeOut(tween(durationMillis = 250)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onExit)
                    .padding(7.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.encore_icon_chevron_down_24),
                    contentDescription = stringResource(R.string.exit_fullscreen_video),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(tween(durationMillis = 250)),
            exit = fadeOut(tween(durationMillis = 250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier.padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_skip_back_24,
                        contentDescription = stringResource(R.string.previous),
                        onClick = onSkipPrevious,
                        tint = Color.White,
                        size = 48.dp,
                        iconSize = 30.dp,
                        enabled = canSkipPrevious
                    )
                    EncorePlayButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPause,
                        size = 64.dp,
                        iconSize = 32.dp,
                        backgroundColor = Color.White,
                        iconColor = EncoreColors.PureBlack,
                        isLoading = isLoading
                    )
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_skip_forward_24,
                        contentDescription = stringResource(R.string.next),
                        onClick = onSkipNext,
                        tint = Color.White,
                        size = 48.dp,
                        iconSize = 30.dp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                NowPlayingSeekBarSection(
                    progress = progress,
                    durationMs = durationMs,
                    currentPositionMs = currentPositionMs,
                    onSeek = onSeek,
                    isLoading = isLoading,
                    canSeek = canSeek,
                    markers = chapterFractions(chapters, durationMs)
                )
            }
        }
    }
}

@Composable
private fun MusicVideoSwitchRow(
    available: Boolean,
    active: Boolean,
    switching: Boolean,
    onSwitch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!available && !active) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        MusicVideoSwitchPill(
            label = if (active) stringResource(R.string.audio) else stringResource(R.string.music_video),
            iconRes = if (active) R.drawable.encore_icon_audio_24 else R.drawable.encore_icon_video_24,
            switching = switching,
            onClick = { onSwitch(!active) }
        )
    }
}

@Composable
private fun MusicVideoSwitchPill(
    label: String,
    iconRes: Int,
    switching: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (switching) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = EncoreTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

/**
 * Real-time synchronized lyrics element, up to 2 lines with ellipsis.
 * Reference: revised_template_overlay.xml (@id/lyrics_element)
 * Constant 44dp height container ensures track title and controls below NEVER shift position!
 */
@Composable
private fun NowPlayingSyncedLyricLine(
    lyricLine: TimeSyncedLyric?,
    clockMs: State<Long>,
    visible: Boolean,
    height: Dp,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (height <= 0.dp && !visible) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
        ) {
            AnimatedContent(
                targetState = lyricLine,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(150)))
                },
                label = "1LineLyricsContent"
            ) { line ->
                if (line != null && line.isWordSynced) {
                    // Karaoke sweep on the one-line strip too: same frame
                    // clock, same draw-phase clip as the fullscreen view.
                    WordSyncedLyricSweepText(
                        lyric = line,
                        positionMs = clockMs,
                        sungColor = Color.White,
                        unsungColor = Color.White.copy(alpha = 0.45f),
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLyrics() }
                    )
                } else {
                    Text(
                        text = line?.text.orEmpty(),
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLyrics() }
                    )
                }
            }
        }
    }
}

/**
 * Track Title, Artist & Feedback Buttons Row.
 * Tapping the title opens the album, tapping the artist opens the artist page.
 * Reference: track_info_nowplaying_view.xml & revised_template_overlay.xml (@id/track_info_feedback_container)
 * Live dump: track_info_view_title [63, 1598], feedback_buttons_container [765, 1596][1038, 1722]
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingTrackInfoRow(
    title: String,
    artist: String,
    isExplicit: Boolean,
    isLiked: Boolean,
    showSmartShuffleBadge: Boolean,
    onTitleClick: () -> Unit,
    onTitleLongClick: (() -> Unit)? = null,
    onArtistClick: () -> Unit,
    onArtistLongClick: (() -> Unit)? = null,
    onLikeClick: () -> Unit,
    onBanClick: (() -> Unit)? = null,
    coverUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverUrl != null) {
            EncoreArtworkImage(
                imageUrl = coverUrl,
                contentDescription = null,
                cornerRadius = 8.dp,
                requestSize = 128,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = EncoreTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(
                    onClick = onTitleClick,
                    onLongClick = onTitleLongClick
                )
            )
            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExplicit) {
                    EncoreExplicitTag(
                        modifier = Modifier.padding(end = 8.dp),
                        size = 16.dp,
                        cornerRadius = 3.dp,
                        fontSize = 10.sp
                    )
                }
                if (showSmartShuffleBadge) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_enhance_badge_16),
                        contentDescription = stringResource(R.string.smart_shuffle_on),
                        tint = EncoreColors.Green,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(14.dp)
                    )
                }
                Text(
                    text = artist,
                    style = EncoreTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.70f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.combinedClickable(
                        onClick = onArtistClick,
                        onLongClick = onArtistLongClick
                    )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBanClick != null) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_ban_24,
                    contentDescription = stringResource(R.string.don_t_play_this),
                    onClick = onBanClick,
                    tint = Color.White.copy(alpha = 0.70f),
                    size = 48.dp,
                    iconSize = 24.dp
                )
            }
            EncoreHeartButton(
                isLiked = isLiked,
                onLikeToggle = onLikeClick,
                size = 48.dp,
                iconSize = 28.dp
            )
        }
    }
}

/**
 * Horizontal padding 24dp aligns perfectly with timestamps, track title, and album artwork.
 * Live dump bounds: position [63, 1809], duration [948, 1809] (24dp screen margins)
 */
@Composable
private fun NowPlayingSeekBarSection(
    progress: Float,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Float) -> Unit,
    isLoading: Boolean,
    canSeek: Boolean = true,
    markers: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EncoreSeekBar(
            progress = progress,
            durationMs = durationMs,
            onSeek = onSeek,
            isLoading = isLoading,
            enabled = canSeek,
            markers = markers,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPositionMs),
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = formatTime(durationMs),
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

/**
 * Podcast controls: speed pill, skip 15s, previous, play/pause, next.
 * Reference: web player control-button-seek-back-15 / control-button-playback-speed.
 */
private val EPISODE_SPEED_OPTIONS = listOf(0.5f, 0.8f, 1f, 1.2f, 1.5f, 2f, 2.5f, 3f, 3.5f)

private fun chapterFractions(chapters: List<ChapterMarker>, durationMs: Long): List<Float> {
    if (durationMs <= 0L) return emptyList()
    return chapters.map { it.startMs / durationMs.toFloat() }
}

@Composable
private fun EpisodeControlsRow(
    playbackSpeed: Float,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlaybackSpeed: (Float) -> Unit,
    onSkipSeek: (Long) -> Unit,
    onSkipPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeedPill(
            speed = playbackSpeed,
            onSelect = onPlaybackSpeed
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_seek_back_15,
            contentDescription = stringResource(R.string.back_15_seconds),
            onClick = { onSkipSeek(-15_000L) },
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 26.dp
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_back_24,
            contentDescription = stringResource(R.string.previous),
            onClick = onSkipPreviousClick,
            tint = EncoreColors.TextPrimary,
            size = 44.dp,
            iconSize = 26.dp
        )

        EncorePlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPauseClick,
            size = 64.dp,
            iconSize = 32.dp,
            backgroundColor = Color.White,
            iconColor = EncoreColors.PureBlack,
            isLoading = isLoading
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_forward_24,
            contentDescription = stringResource(R.string.next),
            onClick = onSkipNextClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_seek_forward_15,
            contentDescription = stringResource(R.string.forward_15_seconds),
            onClick = { onSkipSeek(15_000L) },
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 26.dp
        )
    }
}

@Composable
private fun SpeedPill(
    speed: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (speed == 1f) "1" else String.format(java.util.Locale.US, "%.1f", speed),
                style = EncoreTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = Color.White
            )
            Text(
                text = "×",
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                modifier = Modifier.padding(start = 1.dp)
            )
        }
        EncoreMenu(
            expanded = expanded,
            onDismiss = { expanded = false }
        ) {
            EPISODE_SPEED_OPTIONS.forEach { option ->
                EncoreMenuItem(
                    label = if (option == 1f) "1x" else String.format(java.util.Locale.US, "%.1fx", option),
                    selected = kotlin.math.abs(option - speed) < 0.01f,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

/**
 * Playback Controls (Shuffle, Previous, Play/Pause, Next, Repeat).
 * Reference: revised_template_overlay.xml (@id/playback_controls_container) -> paddingHorizontal 16dp
 */
@Composable
private fun NowPlayingControlsRow(
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    isPlaying: Boolean,
    isLoading: Boolean,
    canShuffle: Boolean,
    canSmartShuffle: Boolean,
    canRepeat: Boolean,
    canSkipPrevious: Boolean = true,
    onShuffleClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EncoreSmartShuffleButton(
            mode = shuffleMode,
            onClick = onShuffleClick,
            size = 48.dp,
            enabled = canShuffle
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_back_24,
            contentDescription = stringResource(R.string.previous),
            onClick = onSkipPreviousClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp,
            enabled = canSkipPrevious
        )

        EncorePlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPauseClick,
            size = 64.dp,
            iconSize = 32.dp,
            backgroundColor = Color.White,
            iconColor = EncoreColors.PureBlack,
            isLoading = isLoading
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_forward_24,
            contentDescription = stringResource(R.string.next),
            onClick = onSkipNextClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp
        )

        EncoreRepeatButton(
            mode = repeatMode,
            onClick = onRepeatClick,
            size = 48.dp,
            enabled = canRepeat
        )
    }
}

/**
 * Bottom Device & Share / Queue Row.
 * Reference: revised_template_overlay_footer.xml -> marginStart/End 8dp (@dimen/footer_button_margin)
 */
@Composable
private fun NowPlayingFooterRow(
    connectedDeviceName: String,
    onDeviceClick: () -> Unit,
    onQueueClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDeviceClick)
                .padding(vertical = EncoreSpacers.Tighter4, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_devices_24,
                contentDescription = stringResource(R.string.connected_devices),
                onClick = onDeviceClick,
                tint = if (connectedDeviceName.isNotBlank()) EncoreColors.Green else EncoreColors.TextPrimary,
                size = 32.dp,
                iconSize = 20.dp
            )
            if (connectedDeviceName.isNotBlank()) {
                Text(
                    text = connectedDeviceName,
                    style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_share_android_24,
                contentDescription = stringResource(R.string.share),
                onClick = onShareClick,
                tint = Color.White.copy(alpha = 0.70f),
                size = 40.dp,
                iconSize = 20.dp
            )
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_queue_24,
                contentDescription = stringResource(R.string.queue),
                onClick = onQueueClick,
                tint = Color.White.copy(alpha = 0.70f),
                size = 40.dp,
                iconSize = 22.dp
            )
        }
    }
}

/**
 * Lyrics Peek Card (16dp rounded corner, dominant color background, horizontal margin 16dp).
 * Fixed peek height: the card keeps its layout size while the synced lyrics fill it
 * and scroll inside, auto-following the active line.
 * Reference: live dump [42, 2269][1038, 2400]
 */
@Composable
private fun NowPlayingLyricsCard(
    visible: Boolean,
    lyrics: List<TimeSyncedLyric>,
    activeIndex: Int,
    clockMs: State<Long>,
    dominantColor: Color,
    onOpenLyrics: () -> Unit,
    onShareLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 360.dp
) {
    val ink = contrastInk(dominantColor)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 400, easing = EncoreAnimation.DecelerateInterpolator)
        ) + fadeIn(tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(peekHeight) // Fixed peek size: lyrics fill the card instead of growing it
                .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
                .background(dominantColor.copy(alpha = 0.65f))
                .clickable { onOpenLyrics() }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PEEK_HEIGHT)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.lyrics),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ink)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_share_android_24,
                            contentDescription = stringResource(R.string.share_lyrics),
                            onClick = onShareLyrics,
                            tint = ink,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_fullscreen_24,
                            contentDescription = stringResource(R.string.expand_lyrics),
                            onClick = onOpenLyrics,
                            tint = ink,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val lyricsListState = rememberLazyListState()
                val lyricFontSize = 19.sp
                LaunchedEffect(activeIndex, lyrics.size) {
                    if (lyrics.isEmpty()) return@LaunchedEffect
                    lyricsListState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                }
                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .weight(1f)
                ) {
                    itemsIndexed(lyrics) { i, lyric ->
                        val isActive = i == activeIndex
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isActive && lyric.isWordSynced) {
                                WordSyncedLyricSweepText(
                                    lyric = lyric,
                                    positionMs = clockMs,
                                    sungColor = ink,
                                    unsungColor = ink.copy(alpha = 0.55f),
                                    style = EncoreTheme.typography.titleMedium.copy(
                                        fontSize = lyricFontSize * 1.02f,
                                        lineHeight = 31.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    maxLines = 2,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            } else {
                                Text(
                                    text = lyric.text,
                                    style = EncoreTheme.typography.titleMedium.copy(
                                        fontSize = if (isActive) lyricFontSize * 1.02f else lyricFontSize,
                                        lineHeight = 31.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = when {
                                            isActive -> ink.copy(alpha = 0.95f)
                                            i < activeIndex -> ink.copy(alpha = 0.45f)
                                            else -> ink.copy(alpha = 0.55f)
                                        }
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = if (isActive && lyric.translation.isNotBlank()) 2.dp else 6.dp)
                                )
                            }
                            if (isActive && lyric.translation.isNotBlank()) {
                                Text(
                                    text = lyric.translation,
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 19.sp,
                                        color = ink.copy(alpha = 0.65f)
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "About the Artist" Card (16dp rounded corner, horizontal margin 16dp)
 * with Verified Artist badge and Follow button. Podcast episodes reuse the
 * same card as "About the show" with the publisher in place of the artist
 * and no verified badge.
 */
@Composable
private fun NowPlayingAboutArtistCard(
    artist: String,
    monthlyListeners: String,
    artistBio: String,
    artistImageUrl: String?,
    isFollowingArtist: Boolean,
    onFollowToggle: () -> Unit,
    onArtistClick: () -> Unit = {},
    isPodcastPublisher: Boolean = false,
    modifier: Modifier = Modifier
) {
    val fallbackColor = if (isPodcastPublisher) Color(0xFF535353) else Color(0xFFE8115B)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
            .background(EncoreColors.Card)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PEEK_HEIGHT)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (isPodcastPublisher) stringResource(R.string.about_the_show) else stringResource(R.string.about_the_artist),
                    style = EncoreTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onArtistClick)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!artistImageUrl.isNullOrBlank()) {
                            EncoreArtworkImage(
                                imageUrl = artistImageUrl,
                                contentDescription = artist,
                                modifier = Modifier.fillMaxSize(),
                                fallbackColor = fallbackColor
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(fallbackColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = artist.firstOrNull()?.uppercase() ?: "A",
                                    style = EncoreTheme.typography.titleMedium.copy(color = Color.White)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = artist,
                                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!isPodcastPublisher) {
                                Spacer(modifier = Modifier.width(EncoreSpacers.Tighter4))
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_verified_check_active_24),
                                    contentDescription = stringResource(R.string.verified_artist),
                                    tint = EncoreColors.Green,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (monthlyListeners.isNotBlank()) {
                            Text(
                                text = monthlyListeners,
                                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(EncoreCornerRadii.FollowPill))
                        .border(
                            width = 1.dp,
                            color = if (isFollowingArtist) EncoreColors.Green else EncoreColors.TextSecondary,
                            shape = RoundedCornerShape(EncoreCornerRadii.FollowPill)
                        )
                        .clickable { onFollowToggle() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFollowingArtist) stringResource(R.string.following) else "Follow",
                        style = EncoreTheme.typography.bodySmallBold.copy(
                            color = if (isFollowingArtist) EncoreColors.Green else EncoreColors.TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (artistBio.isNotBlank()) {
                Text(
                    text = artistBio,
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary,
                        lineHeight = 18.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
}

/**
 * - Dynamic active line calculation from current playback timestamp
 * - Tap any line to seek directly to that lyric position
 * - Smooth auto-scrolling to current line
 */
@Composable
private fun FullscreenLyricsView(
    title: String,
    artist: String,
    currentPositionMs: Long,
    isPlaying: Boolean,
    dominantColor: Color,
    artworkColor: Color,
    lyrics: List<TimeSyncedLyric>,
    nextTrack: TrackItem?,
    nextTrackLyrics: List<TimeSyncedLyric>,
    lyricsProviderName: String,
    onSeekMs: (Long) -> Unit,
    onShareClick: () -> Unit,
    onLyricsMenuClick: () -> Unit,
    onClose: () -> Unit
) {
    // The player only reports its position twice a second; a karaoke sweep
    // needs every frame. The clock steers itself with those readings and
    // ticks smoothly in between. Kept as State: the active index is derived
    // from it, so only the lines whose active bit flips recompose, and the
    // sweep reads it straight from the draw phase.
    val clockMs = rememberLyricClock(
        reportedPositionMs = { currentPositionMs },
        isPlaying = isPlaying,
        trackKey = lyrics.firstOrNull()?.startTimeMs,
    )
    val activeIndex by rememberActiveLyricIndex(clockMs, lyrics)
    val listState = rememberLazyListState()
    val trackKey = remember(lyrics) { lyrics.firstOrNull()?.startTimeMs }

    // Swipe a line right to start picking lines: the swiped line lands in
    // the selection, every other checkbox starts off, and the header trades
    // its controls for the count + copy pair until the reader is done.
    var selectionMode by remember { mutableStateOf(false) }
    val selectedLines = remember { mutableStateListOf<Int>() }
    val context = LocalContext.current
    val lyricsToast = rememberQuackifyToastState()
    val lyricsCopiedLabel = stringResource(R.string.lyrics_copied)
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val density = LocalDensity.current
    val selectionSwipeThresholdPx = with(density) { 48.dp.toPx() }
    BackHandler(enabled = selectionMode) {
        selectionMode = false
    }
    LaunchedEffect(trackKey) {
        selectionMode = false
        selectedLines.clear()
    }

    // ---- The sneak peek hand-off ----------------------------------------
    // When the song ends and the next one starts while the reader is open,
    // the peek earns its keep: it is already showing the opening of what is
    // now playing, so instead of the lyrics list snapping to a new song, the
    // whole sheet — title bar, old lyrics and the peek at the bottom — is
    // carried up and out by the peek itself, and the new song's lines slide
    // in from below. The old sheet's data is held for the exit so it keeps
    // rendering what the user was just reading while it leaves.
    val deepFeel = LyricsOptions.deepFeelEnabled
    val nextPeekEnabled = LyricsOptions.nextTrackPeek
    var handoffExit by remember { mutableStateOf(false) }
    var heldTitle by remember { mutableStateOf(title) }
    var heldArtist by remember { mutableStateOf(artist) }
    var heldLyrics by remember { mutableStateOf(lyrics) }
    var heldProvider by remember { mutableStateOf(lyricsProviderName) }
    var heldPeekTrack by remember { mutableStateOf(nextTrack) }
    var heldPeekLyrics by remember { mutableStateOf(nextTrackLyrics) }
    var heldTrackKey by remember { mutableStateOf(trackKey) }
    var hasShownPeekHandoff by remember { mutableStateOf(false) }
    // Rides at the head of the exiting sheet: the peek pulls upward a touch
    // faster than the sheet itself, so the hand-off reads as the next song
    // dragging the old one away.
    val peekLead by animateFloatAsState(
        targetValue = if (handoffExit) 1f else 0f,
        animationSpec = tween(durationMillis = 460, easing = EncoreAnimation.DecelerateInterpolator),
        label = "peekLead"
    )
    LaunchedEffect(trackKey) {
        if (heldTrackKey != trackKey && hasShownPeekHandoff) {
            handoffExit = true
            // The exit runs 460ms; the entering sheet waits for it so the
            // two never overlap on screen.
            delay(460)
            handoffExit = false
        }
        hasShownPeekHandoff = true
        heldTrackKey = trackKey
        heldTitle = title
        heldArtist = artist
        heldLyrics = lyrics
        heldProvider = lyricsProviderName
        heldPeekTrack = nextTrack
        heldPeekLyrics = nextTrackLyrics
    }
    // When the old song's sheet is on its way out, the active index is
    // meaningless for it — the clock has already jumped to the new song —
    // so the held lines render as a static page while they leave.
    val sheetActiveIndex = if (handoffExit) -1 else activeIndex
    // Which data set the sheet renders: held (old song) while it exits,
    // live (new song) once it has gone.
    val sheetTitle = if (handoffExit) heldTitle else title
    val sheetArtist = if (handoffExit) heldArtist else artist
    val sheetLyrics = if (handoffExit) heldLyrics else lyrics
    val sheetProvider = if (handoffExit) heldProvider else lyricsProviderName
    val sheetPeekTrack = if (handoffExit) heldPeekTrack else nextTrack
    val sheetPeekLyrics = if (handoffExit) heldPeekLyrics else nextTrackLyrics
    val showNextPeek = nextPeekEnabled && (if (handoffExit) heldPeekTrack else nextTrack) != null &&
        (if (handoffExit) heldPeekLyrics else nextTrackLyrics).isNotEmpty()

    val deepFeelPalette = rememberDeepFeelPalette(artworkColor)
    val glowPhases = if (deepFeel) rememberDeepFeelGlowPhases() else null
    val flowPhases = if (deepFeel) rememberDeepFeelFlowPhases() else null
    var heldLyricIndex by remember { mutableStateOf<Int?>(null) }
    // While the reader holds a lyric down (and for a short grace window
    // after they let go), the auto-scroll that chases the active line is
    // suspended: they scrolled away on purpose to read ahead.
    var suppressAutoScroll by remember { mutableStateOf(false) }
    val flowReveal by animateFloatAsState(
        targetValue = if (deepFeel) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = EncoreAnimation.DecelerateInterpolator),
        label = "deepFeelFlowReveal"
    )
    val ink by animateColorAsState(
        targetValue = if (deepFeel) Color.White else contrastInk(dominantColor),
        animationSpec = tween(durationMillis = 600, easing = EncoreAnimation.DecelerateInterpolator),
        label = "deepFeelInk"
    )
    val backgroundTop by animateColorAsState(
        targetValue = if (deepFeel) deepFeelPalette.deepTop else dominantColor,
        animationSpec = tween(durationMillis = 600, easing = EncoreAnimation.DecelerateInterpolator),
        label = "deepFeelBackgroundTop"
    )
    val backgroundBottom by animateColorAsState(
        targetValue = if (deepFeel) deepFeelPalette.deepBottom else dominantColor,
        animationSpec = tween(durationMillis = 600, easing = EncoreAnimation.DecelerateInterpolator),
        label = "deepFeelBackgroundBottom"
    )
    LaunchedEffect(heldLyricIndex, selectionMode) {
        if (heldLyricIndex != null || selectionMode) {
            suppressAutoScroll = true
        } else {
            delay(2500.milliseconds)
            suppressAutoScroll = false
        }
    }
    LaunchedEffect(handoffExit, activeIndex) {
        // While the hand-off runs, the list shows the old song's held
        // sheet; jumping to the new active line mid-exit would fight the
        // sweep, so the chase waits for the entering sheet to land.
        if (handoffExit) return@LaunchedEffect
        if (!suppressAutoScroll) {
            // A fresh sheet enters scrolled to its top: snap, no animation,
            // because the sheet itself is already gliding into place.
            val entering = peekLead > 0f
            val target = (activeIndex - 1).coerceAtLeast(0)
            if (entering) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }

    // Keep the screen awake while the reader is open, if the user asked for
    // it. Window flag, not a wake lock: nothing to acquire or release by
    // hand, and Android clears it never — the flag simply rides on this
    // window and is removed the moment it is cleared.
    val keepScreenOn = LyricsOptions.keepScreenOn
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val copySelectedLyrics = {
        val picked = selectedLines.sorted()
        if (picked.isNotEmpty()) {
            val text = picked.mapNotNull { sheetLyrics.getOrNull(it)?.text }.joinToString("\n")
            clipboard?.setPrimaryClip(ClipData.newPlainText("Lyrics", text))
            lyricsToast.show(lyricsCopiedLabel)
        }
        selectionMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .deepFeelBackdrop(
                topColor = backgroundTop,
                bottomColor = backgroundBottom,
                palette = deepFeelPalette,
                phases = flowPhases,
                flowReveal = flowReveal
            )
            .statusBarsPadding()
            .navigationBarsPadding()
        // No horizontal padding here: the DeepFeel highlight wash runs from
        // screen edge to screen edge, so the list itself is full-bleed and
        // each line pads its own text inward.
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EncoreSpacers.Looser2)
                .padding(vertical = EncoreSpacers.Tighter2)
        ) {
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_x_24,
                        contentDescription = stringResource(R.string.close),
                        onClick = { selectionMode = false },
                        tint = ink
                    )
                    Text(
                        text = stringResource(R.string.lyrics_selection_count, selectedLines.size),
                        style = EncoreTheme.typography.bodySmallBold.copy(color = ink),
                        maxLines = 1
                    )
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_copy_24,
                        contentDescription = stringResource(R.string.copy_selected_lyrics),
                        onClick = copySelectedLyrics,
                        tint = ink
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_chevron_down_24,
                        contentDescription = stringResource(R.string.close_lyrics),
                        onClick = onClose,
                        tint = ink
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_deepfeel_24,
                            contentDescription = stringResource(R.string.lyrics_deepfeel),
                            onClick = { LyricsOptions.setDeepFeelEnabled(context, !deepFeel) },
                            tint = ink,
                            isActive = deepFeel,
                            activeTint = deepFeelPalette.accent
                        )
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_more_android_24,
                            contentDescription = stringResource(R.string.lyrics_menu),
                            onClick = onLyricsMenuClick,
                            tint = ink
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = sheetTitle,
                        style = EncoreTheme.typography.bodySmallBold.copy(color = ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sheetArtist,
                        style = EncoreTheme.typography.marginal.copy(color = ink.copy(alpha = 0.7f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            // The hand-off rides the list: while the old song's sheet exits
            // upward it is translated by the exit fraction, and the new
            // song's lines start one screen below and rise in as the exit
            // finishes — one continuous upward sweep from old lyrics to new.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (handoffExit) {
                        // Exit: the old sheet rides a full screen up, so by
                        // the time the content swaps it is already gone.
                        translationY = -peekLead * size.height
                        alpha = 1f - peekLead * 0.55f
                    } else if (peekLead > 0f) {
                        // Enter: the fraction decays back to 0 after the
                        // exit, so the new sheet starts one screen below and
                        // rises the last stretch into place.
                        translationY = peekLead * size.height
                        alpha = 1f - peekLead
                    }
                },
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(sheetLyrics) { index, line ->
                val isCurrentLine = index == sheetActiveIndex
                val lineAlpha by animateFloatAsState(
                    targetValue = when {
                        !deepFeel -> if (isCurrentLine) 1f else 0.35f
                        isCurrentLine -> 1f
                        index < sheetActiveIndex -> 0.16f
                        else -> 0.38f
                    },
                    animationSpec = tween(durationMillis = 420, easing = EncoreAnimation.DecelerateInterpolator),
                    label = "deepFeelLineAlpha"
                )
                val lineScale by animateFloatAsState(
                    targetValue = when {
                        !deepFeel -> 1f
                        isCurrentLine -> 1f
                        index < sheetActiveIndex -> 0.82f
                        else -> 0.87f
                    },
                    animationSpec = tween(durationMillis = 420, easing = EncoreAnimation.DecelerateInterpolator),
                    label = "deepFeelLineScale"
                )
                val activation by animateFloatAsState(
                    targetValue = if (deepFeel && isCurrentLine) 1f else 0f,
                    animationSpec = tween(durationMillis = 520, easing = EncoreAnimation.DecelerateInterpolator),
                    label = "deepFeelLineActivation"
                )
                val distance = (index - sheetActiveIndex).absoluteValue
                val lineBlur by animateDpAsState(
                    targetValue = when {
                        !deepFeel -> 0.dp
                        heldLyricIndex == index -> 0.dp
                        distance >= 5 -> 9.dp
                        distance == 4 -> 6.75.dp
                        distance == 3 -> 4.5.dp
                        distance == 2 -> 2.25.dp
                        else -> 0.dp
                    },
                    animationSpec = tween(durationMillis = 420, easing = EncoreAnimation.DecelerateInterpolator),
                    label = "deepFeelLineBlur"
                )
                val lineBlurModifier = if (lineBlur > 0.dp) Modifier.blur(lineBlur) else Modifier
                val lineFont by animateFloatAsState(
                    targetValue = when {
                        !deepFeel -> if (isCurrentLine) 28f else 23f
                        isCurrentLine -> 30f
                        else -> 23f
                    },
                    animationSpec = tween(durationMillis = 420, easing = EncoreAnimation.DecelerateInterpolator),
                    label = "deepFeelLineFont"
                )
                val lineVerticalPadding by animateDpAsState(
                    targetValue = when {
                        isCurrentLine -> 12.dp
                        deepFeel -> 2.dp
                        else -> 3.dp
                    },
                    animationSpec = tween(durationMillis = 420, easing = EncoreAnimation.DecelerateInterpolator),
                    label = "deepFeelLineVerticalPadding"
                )
                val lineInteraction = remember { MutableInteractionSource() }
                val linePressed by lineInteraction.collectIsPressedAsState()
                val holdConsumed = remember { mutableStateOf(false) }
                // A ~360ms press means "let me read this", in DeepFeel and
                // in the plain reader alike: it peels the blur off the line
                // (if any), suspends the active-line auto-scroll and, on
                // release, does not fire the tap-to-seek.
                LaunchedEffect(linePressed) {
                    if (linePressed) {
                        delay(360.milliseconds)
                        holdConsumed.value = true
                        heldLyricIndex = index
                    } else {
                        heldLyricIndex = null
                        holdConsumed.value = false
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = lineVerticalPadding)
                        .then(lineBlurModifier)
                        .deepFeelLineGlow(
                            palette = deepFeelPalette,
                            phases = glowPhases,
                            progress = activation
                        )
                        .graphicsLayer {
                            scaleX = lineScale
                            scaleY = lineScale
                            alpha = lineAlpha
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .pointerInput(selectionSwipeThresholdPx) {
                            var swipedRight = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    swipedRight = (swipedRight + dragAmount).coerceAtLeast(0f)
                                },
                                onDragEnd = {
                                    if (swipedRight >= selectionSwipeThresholdPx) {
                                        selectionMode = true
                                        if (!selectedLines.remove(index)) selectedLines.add(index)
                                    }
                                    swipedRight = 0f
                                },
                                onDragCancel = { swipedRight = 0f }
                            )
                        }
                        .clickable(
                            interactionSource = lineInteraction,
                            indication = null
                        ) {
                            if (selectionMode) {
                                if (!selectedLines.remove(index)) selectedLines.add(index)
                            } else if (!holdConsumed.value) {
                                onSeekMs(line.startTimeMs)
                            }
                        }
                        // The wash behind the active line is full-bleed, so
                        // the horizontal inset lives on the text itself.
                        .padding(horizontal = EncoreSpacers.Looser2)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        if (selectionMode) {
                            LyricSelectionCheckbox(
                                checked = index in selectedLines,
                                ink = ink,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (isCurrentLine && line.isWordSynced) {
                                WordSyncedLyricSweepText(
                                    lyric = line,
                                    positionMs = clockMs,
                                    sungColor = ink,
                                    unsungColor = ink.copy(alpha = if (deepFeel) 0.42f else 0.35f),
                                    style = EncoreTheme.typography.titleLarge.copy(
                                        fontSize = lineFont.sp,
                                        lineHeight = (lineFont * 1.42f).sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    style = EncoreTheme.typography.titleLarge.copy(
                                        fontSize = lineFont.sp,
                                        lineHeight = (lineFont * 1.42f).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ink
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            AnimatedVisibility(
                                visible = isCurrentLine && line.translation.isNotBlank(),
                                enter = fadeIn(
                                    animationSpec = tween(durationMillis = 320, easing = EncoreAnimation.DecelerateInterpolator)
                                ) + expandVertically(
                                    expandFrom = Alignment.Top,
                                    animationSpec = tween(durationMillis = 320, easing = EncoreAnimation.DecelerateInterpolator)
                                ),
                                exit = fadeOut(
                                    animationSpec = tween(durationMillis = 240, easing = EncoreAnimation.DecelerateInterpolator)
                                ) + shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    animationSpec = tween(durationMillis = 240, easing = EncoreAnimation.DecelerateInterpolator)
                                )
                            ) {
                                Text(
                                    text = line.translation,
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        color = ink.copy(alpha = if (deepFeel) 0.5f else 0.55f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
            // The sneak peek: once the last line of this song scrolls by,
            // the opening lines of the next one surface beneath it — each
            // line blurrier than the one above, like the song is coming
            // into view through fog. Subtle on purpose: it is a whisper of
            // what is next, not a second lyric sheet.
            item(key = "nextTrackPeek") {
                NextTrackPeekReveal(
                    visible = showNextPeek,
                    nextTrack = sheetPeekTrack,
                    nextLyrics = sheetPeekLyrics,
                    ink = ink,
                    accent = if (deepFeel) deepFeelPalette.accent else ink,
                    deepFeel = deepFeel,
                    handoffExit = handoffExit,
                    peekLead = peekLead
                )
            }
            if (sheetProvider.isNotBlank()) {
                item {
                    // Attribution for the source these lines came from, at
                    // the very end of the scroll — where Spotify itself
                    // puts it.
                    Text(
                        text = stringResource(R.string.lyrics_provided_by, sheetProvider),
                        style = EncoreTheme.typography.marginal.copy(
                            color = ink.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EncoreSpacers.Looser2)
                            .padding(top = 28.dp)
                    )
                }
            }
        }

        if (lyricsToast.entries.isNotEmpty()) {
            QuackifyToastHost(
                state = lyricsToast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
        }
    }
}

/**
 * The next-track peek with its own reveal animation, in a composable of
 * its own so the peek's AnimatedVisibility resolves in a plain scope:
 * inside the reader's list the sheet's layout scopes sit behind the
 * toast's overlay Box and are not implicitly reachable there.
 */
@Composable
private fun NextTrackPeekReveal(
    visible: Boolean,
    nextTrack: TrackItem?,
    nextLyrics: List<TimeSyncedLyric>,
    ink: Color,
    accent: Color,
    deepFeel: Boolean,
    handoffExit: Boolean,
    peekLead: Float
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 320, easing = EncoreAnimation.DecelerateInterpolator)
        ) + expandVertically(
            animationSpec = tween(durationMillis = 320, easing = EncoreAnimation.DecelerateInterpolator)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 240, easing = EncoreAnimation.DecelerateInterpolator)
        ) + shrinkVertically(
            animationSpec = tween(durationMillis = 240, easing = EncoreAnimation.DecelerateInterpolator)
        )
    ) {
        NextTrackPeek(
            nextTrack = nextTrack,
            nextLyrics = nextLyrics,
            ink = ink,
            // In the plain reader the backdrop IS the dominant colour, so
            // tinting the eyebrow with it made the "next up" label vanish
            // into the background; the contrast ink keeps it legible in
            // both modes.
            accent = accent,
            deepFeel = deepFeel,
            // During the hand-off the peek is the engine: it is already
            // the next song's opening, so it travels further upward than
            // the sheet carrying it and the old lines appear to be dragged
            // after it.
            leadFraction = if (handoffExit) peekLead else 0f
        )
    }
}

/**
 * The pick marker beside a lyric line while the reader is in selection mode:
 * a quiet ring that snaps to the app green with a check the moment the line
 * is chosen, sized to sit beside the 28sp lyric text.
 */
@Composable
private fun LyricSelectionCheckbox(
    checked: Boolean,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (checked) EncoreColors.Green else Color.Transparent)
            .border(
                width = if (checked) 0.dp else 2.dp,
                color = if (checked) EncoreColors.Green else ink.copy(alpha = 0.55f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_check_24),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * The tail of the fullscreen reader: a whisper of the next track. Its name
 * sits under a small "next up" eyebrow, and its opening lines fade out line
 * by line — 0%, 25%, 50%, 75%, 100% blur — so the farther down you read,
 * the more the song feels like it is still wrapped in fog. No seek, no
 * glow, no translation: it is something to glance at, not to read along
 * with, and it stays quieter than the real lyric sheet in both modes.
 */
@Composable
private fun NextTrackPeek(
    nextTrack: TrackItem?,
    nextLyrics: List<TimeSyncedLyric>,
    ink: Color,
    accent: Color,
    deepFeel: Boolean,
    leadFraction: Float = 0f
) {
    if (nextTrack == null || nextLyrics.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // The hand-off lead: the peek glides up by a sixth of the
                // screen beyond the sheet's own travel, as if it pulled the
                // finished song away behind it.
                if (leadFraction > 0f) {
                    translationY = -leadFraction * size.height * 0.16f
                }
            }
            .padding(horizontal = EncoreSpacers.Looser2)
            .padding(top = 44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 2.dp)
                    .background(accent.copy(alpha = if (deepFeel) 0.9f else 0.6f))
            )
            Text(
                text = stringResource(R.string.lyrics_next_up),
                style = EncoreTheme.typography.marginalBold.copy(
                    color = accent.copy(alpha = if (deepFeel) 0.95f else 0.8f),
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                )
            )
        }
        Text(
            text = stringResource(R.string.lyrics_next_track_name, nextTrack.title, nextTrack.artist),
            style = EncoreTheme.typography.bodyMedium.copy(
                color = ink.copy(alpha = if (deepFeel) 0.8f else 0.75f),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )
        // Each line down the peek is blurrier than the one above it: a
        // quarter step per row, all the way to fully unreadable.
        nextLyrics.take(5).forEachIndexed { index, line ->
            val blur by animateDpAsState(
                targetValue = 1.6.dp * index,
                animationSpec = tween(durationMillis = 420, easing = EncoreAnimation.DecelerateInterpolator),
                label = "nextPeekBlur"
            )
            val lineAlpha = if (deepFeel) 0.44f else 0.5f
            Text(
                text = line.text,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink.copy(alpha = lineAlpha - index * 0.04f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)
            )
        }
    }
}

/**
 * The transcript highlight spans are consecutive char ranges, so the spoken
 * prefix of a sentence is the endChar of the last word whose startMs the
 * playback position has already passed.
 */
private fun spokenPrefixEnd(line: TranscriptLine, positionMs: Long): Int {
    val words = line.words
    if (words.isEmpty()) return if (positionMs >= line.startMs) line.text.length else 0
    var end = 0
    for ((startMs, _, endChar) in words) {
        if (positionMs >= startMs) end = endChar else break
    }
    return end
}

@Composable
private fun NowPlayingTranscriptLine(
    line: TranscriptLine?,
    clockMs: State<Long>,
    ink: Color,
    visible: Boolean,
    height: Dp,
    onOpenTranscript: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (height <= 0.dp && !visible) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
        ) {
            AnimatedContent(
                targetState = line,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(150)))
                },
                label = "transcriptPreviewContent"
            ) { row ->
                // Same sweep as the fullscreen view when the smooth animation
                // is on: the boundary is clipped in the draw phase against the
                // frame clock, so the one-line strip never recomposes per frame.
                if (row != null && LyricsOptions.transcriptSmoothSweep && row.words.isNotEmpty()) {
                    TranscriptSweepText(
                        line = row,
                        positionMs = clockMs,
                        sungColor = ink,
                        unsungColor = ink.copy(alpha = 0.55f),
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTranscript() }
                    )
                } else {
                    Text(
                        text = buildAnnotatedString {
                            append(row?.text.orEmpty())
                            val prefixEnd = row?.let { spokenPrefixEnd(it, clockMs.value) } ?: 0
                            if (prefixEnd > 0) addStyle(SpanStyle(color = ink), 0, prefixEnd)
                        },
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink.copy(alpha = 0.55f)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTranscript() }
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTranscriptCard(
    visible: Boolean,
    lines: List<TranscriptLine>,
    activeIndex: Int,
    clockMs: State<Long>,
    dominantColor: Color,
    onOpenTranscript: () -> Unit,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 360.dp
) {
    val ink = contrastInk(dominantColor)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 400, easing = EncoreAnimation.DecelerateInterpolator)
        ) + fadeIn(tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(peekHeight)
                .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
                .background(dominantColor.copy(alpha = 0.65f))
                .clickable { onOpenTranscript() }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PEEK_HEIGHT)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.transcript),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ink)
                    )
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_fullscreen_24,
                        contentDescription = stringResource(R.string.expand_transcript),
                        onClick = onOpenTranscript,
                        tint = ink,
                        size = 32.dp,
                        iconSize = 18.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val transcriptListState = rememberLazyListState()
                LaunchedEffect(activeIndex, lines.size) {
                    if (lines.isEmpty()) return@LaunchedEffect
                    transcriptListState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                }
                LazyColumn(
                    state = transcriptListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .weight(1f)
                ) {
                    itemsIndexed(lines) { i, line ->
                        if (line.speaker) {
                            Text(
                                text = line.text.uppercase(),
                                style = EncoreTheme.typography.bodySmallBold.copy(color = ink.copy(alpha = 0.5f)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        } else {
                            val isActive = i == activeIndex
                            if (isActive && LyricsOptions.transcriptSmoothSweep && line.words.isNotEmpty()) {
                                TranscriptSweepText(
                                    line = line,
                                    positionMs = clockMs,
                                    sungColor = ink,
                                    unsungColor = ink.copy(alpha = 0.5f),
                                    style = EncoreTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            } else {
                                Text(
                                    text = buildAnnotatedString {
                                        append(line.text)
                                        if (isActive) {
                                            val prefixEnd = spokenPrefixEnd(line, clockMs.value)
                                            if (prefixEnd > 0) addStyle(SpanStyle(color = ink), 0, prefixEnd)
                                        }
                                    },
                                    style = EncoreTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 26.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = when {
                                            isActive -> ink.copy(alpha = 0.5f)
                                            i < activeIndex -> ink.copy(alpha = 0.45f)
                                            else -> ink.copy(alpha = 0.55f)
                                        }
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * - Dynamic active line calculation from current playback timestamp
 * - Karaoke-style spoken-word highlighting on the active sentence
 * - Tap any sentence to seek directly to that transcript position
 * - Smooth auto-scrolling to the current line
 */
@Composable
private fun FullscreenTranscriptView(
    title: String,
    artist: String,
    currentPositionMs: Long,
    isPlaying: Boolean = true,
    dominantColor: Color,
    lines: List<TranscriptLine>,
    onSeekMs: (Long) -> Unit,
    onClose: () -> Unit
) {
    val clockMs = rememberLyricClock(
        reportedPositionMs = { currentPositionMs },
        isPlaying = isPlaying,
        trackKey = lines.firstOrNull()?.startMs,
        offsetMs = { LyricsOptions.transcriptOffsetMs },
    )
    val activeIndex by remember(lines, clockMs) {
        derivedStateOf { lines.indexOfLast { clockMs.value >= it.startMs }.coerceAtLeast(0) }
    }
    val listState = rememberLazyListState()
    val ink = contrastInk(dominantColor)
    var isMenuOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val keepScreenOn = LyricsOptions.keepScreenOn
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(activeIndex) {
        if (lines.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dominantColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EncoreSpacers.Looser2)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = EncoreSpacers.Tighter2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_chevron_down_24,
                contentDescription = stringResource(R.string.close_transcript),
                onClick = onClose,
                tint = ink
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = EncoreTheme.typography.bodySmallBold.copy(color = ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = EncoreTheme.typography.marginal.copy(color = ink.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            EncoreIconButton(
                iconRes = R.drawable.encore_icon_more_android_24,
                contentDescription = stringResource(R.string.transcript_menu),
                onClick = { isMenuOpen = true },
                tint = ink
            )
        }

        Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                if (line.speaker) {
                    Text(
                        text = line.text.uppercase(),
                        style = EncoreTheme.typography.bodySmallBold.copy(color = ink.copy(alpha = 0.5f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                    )
                } else {
                    val isCurrentLine = index == activeIndex
                    val smooth = isCurrentLine && LyricsOptions.transcriptSmoothSweep && line.words.isNotEmpty()
                    if (smooth) {
                        TranscriptSweepText(
                            line = line,
                            positionMs = clockMs,
                            sungColor = ink,
                            unsungColor = ink.copy(alpha = 0.35f),
                            style = EncoreTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekMs(line.startMs) }
                                .padding(vertical = EncoreSpacers.Tighter)
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                append(line.text)
                                if (isCurrentLine) {
                                    val prefixEnd = spokenPrefixEnd(line, clockMs.value)
                                    if (prefixEnd > 0) addStyle(SpanStyle(color = ink), 0, prefixEnd)
                                }
                            },
                            style = EncoreTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentLine) ink.copy(alpha = 0.5f) else ink.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekMs(line.startMs) }
                                .padding(vertical = EncoreSpacers.Tighter)
                        )
                    }
                }
            }
        }
    }

    if (isMenuOpen) {
        TranscriptShortcutSheet(onDismiss = { isMenuOpen = false })
    }
}

/**
 * The transcript line as a karaoke sweep: same two stacked copies clipped
 * in the draw phase as the lyrics sweep, with the boundary interpolated
 * between word timings so it travels across each word rather than jumping
 * a word at a time.
 */
@Composable
private fun TranscriptSweepText(
    line: TranscriptLine,
    positionMs: State<Long>,
    sungColor: Color,
    unsungColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = line.text,
            style = style.copy(color = unsungColor),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
        )
        Text(
            text = line.text,
            style = style.copy(color = sungColor),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val measured = layout ?: return@drawWithContent
                    val boundary = transcriptBoundary(line, positionMs.value)
                    if (boundary <= 0f) return@drawWithContent
                    val textLength = line.text.length
                    if (boundary >= textLength) {
                        drawContent()
                        return@drawWithContent
                    }
                    val boundaryIndex = boundary.toInt().coerceIn(0, textLength - 1)
                    var row = 0
                    while (row < measured.lineCount - 1 &&
                        boundaryIndex >= measured.getLineEnd(row, visibleEnd = false)
                    ) {
                        row++
                    }
                    val fraction = boundary - boundaryIndex
                    val left = measured.getHorizontalPosition(boundaryIndex, usePrimaryDirection = true)
                    val right = measured.getHorizontalPosition(
                        (boundaryIndex + 1).coerceAtMost(textLength),
                        usePrimaryDirection = true,
                    )
                    val boundaryX = left + (right - left) * fraction
                    val boundaryTop = measured.getLineTop(row)
                    val boundaryBottom = measured.getLineBottom(row)
                    clipRect(left = 0f, top = boundaryTop, right = boundaryX, bottom = boundaryBottom) {
                        this@drawWithContent.drawContent()
                    }
                    if (row > 0) {
                        clipRect(left = 0f, top = 0f, right = size.width, bottom = boundaryTop) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
        )
    }
}

/**
 * Where the sweep boundary sits, in (fractional) characters. Each word is
 * lit continuously from its own startMs to the next word's startMs: within
 * that span the boundary travels the word's own character range, so a word
 * lights up gradually as it is spoken instead of jumping whole at its start.
 */
private fun transcriptBoundary(line: TranscriptLine, positionMs: Long): Float {
    val words = line.words
    if (words.isEmpty()) return if (positionMs >= line.startMs) line.text.length.toFloat() else 0f
    val start = words.first().startMs
    if (positionMs < start) return 0f
    for (index in words.indices.reversed()) {
        val word = words[index]
        if (positionMs >= word.startMs) {
            val span = ((words.getOrNull(index + 1)?.startMs ?: (line.startMs + 1)))
                .coerceAtLeast(word.startMs + 1L) - word.startMs
            val frac = ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
            return word.startChar + (word.endChar - word.startChar) * frac
        }
    }
    return 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptShortcutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val smooth = LyricsOptions.transcriptSmoothSweep
    val offsetTenths = LyricsOptions.transcriptOffsetTenths
    val offsetLabel = remember(offsetTenths) {
        String.format(java.util.Locale.US, "%+.1f s", offsetTenths / 10f)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        containerColor = EncoreColors.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.transcript_menu),
                style = EncoreTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.smooth_transcript_animation),
                        style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(R.string.smooth_transcript_animation_desc),
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )
                }
                Switch(checked = smooth, onCheckedChange = { on ->
                    LyricsOptions.setTranscriptSmoothSweep(context, on)
                })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.transcript_offset),
                    style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = offsetLabel,
                    style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green)
                )
            }
            Text(
                text = stringResource(R.string.transcript_offset_desc),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
            val offsetSliderState = rememberSliderState(
                value = (offsetTenths + LYRICS_OFFSET_TENTHS_MAX).toFloat(),
                steps = LYRICS_OFFSET_TENTHS_MAX * 2 - 1,
                trackRange = 0f..(LYRICS_OFFSET_TENTHS_MAX * 2).toFloat(),
            )
            LaunchedEffect(offsetTenths) {
                offsetSliderState.value = (offsetTenths + LYRICS_OFFSET_TENTHS_MAX).toFloat()
            }
            Slider(
                state = offsetSliderState,
                onValueChange = { raw ->
                    LyricsOptions.setTranscriptOffsetTenths(
                        context,
                        (raw.toInt() - LYRICS_OFFSET_TENTHS_MAX).coerceIn(
                            LYRICS_OFFSET_TENTHS_MIN,
                            LYRICS_OFFSET_TENTHS_MAX,
                        )
                    )
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private data class CommentReplyState(
    val loading: Boolean = false,
    val expanded: Boolean = false,
    val replies: List<com.quackify.backend.model.EpisodeCommentReply> = emptyList(),
    val total: Int = 0,
    val nextToken: String = "",
)

private data class ReactionViewState(
    val loading: Boolean = false,
    val expanded: Boolean = false,
    val entries: List<com.quackify.backend.model.CommentReactionEntry> = emptyList(),
)

private val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

/**
 * Episode comments under the About card: the same 16dp rounded card as the
 * rest of the now-playing extras, a composer pinned at its bottom and the
 * thread below. Posting goes through [onPostComment]; the caller refetches
 * the thread, so the list below updates on the next composition. Tapping a
 * comment's reply count expands its replies inline, fetched page by page
 * through [onLoadReplies].
 */
@Composable
private fun NowPlayingEpisodeCommentsCard(
    comments: List<com.quackify.backend.model.EpisodeComment>,
    onRefresh: () -> Unit,
    onPostComment: suspend (String) -> Boolean,
    onLoadReplies: suspend (String, String?) -> com.quackify.backend.model.EpisodeCommentReplies?,
    onReact: suspend (String, String) -> Boolean,
    onRemoveReaction: suspend (String) -> Boolean,
    onLoadReactions: suspend (String) -> com.quackify.backend.model.EpisodeCommentReactions?,
    selfUserUri: String,
    onDeleteComment: suspend (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val draftState = rememberTextFieldState("")
    var posting by remember { mutableStateOf(false) }
    val replyStates = remember { mutableStateMapOf<String, CommentReplyState>() }
    val reactionViews = remember { mutableStateMapOf<String, ReactionViewState>() }
    var reactionPickerFor by remember { mutableStateOf<String?>(null) }
    var deleteAskFor by remember { mutableStateOf<String?>(null) }

    fun toggleReplies(commentUri: String) {
        val current = replyStates[commentUri]
        if (current != null && (current.replies.isNotEmpty() || current.expanded)) {
            replyStates[commentUri] = current.copy(expanded = !current.expanded)
            return
        }
        replyStates[commentUri] = CommentReplyState(loading = true, expanded = true)
        scope.launch {
            val page = onLoadReplies(commentUri, null)
            val state = replyStates[commentUri] ?: return@launch
            replyStates[commentUri] = state.copy(
                loading = false,
                replies = page?.replies.orEmpty(),
                total = page?.total ?: 0,
                nextToken = page?.nextToken.orEmpty(),
            )
        }
    }

    fun loadMoreReplies(commentUri: String) {
        val current = replyStates[commentUri] ?: return
        if (current.loading || current.nextToken.isBlank()) return
        replyStates[commentUri] = current.copy(loading = true)
        scope.launch {
            val page = onLoadReplies(commentUri, current.nextToken)
            val state = replyStates[commentUri] ?: return@launch
            replyStates[commentUri] = state.copy(
                loading = false,
                replies = state.replies + page?.replies.orEmpty(),
                total = page?.total ?: state.total,
                nextToken = page?.nextToken.orEmpty(),
            )
        }
    }

    fun toggleReactionPicker(commentUri: String) {
        reactionPickerFor = if (reactionPickerFor == commentUri) null else commentUri
    }

    // Picking the emoji the account already reacted with removes the reaction,
    // picking any other swaps it; the refetch brings back the new counts and
    // the caller's own reaction marker.
    fun react(commentUri: String, currentReaction: String, emoji: String) {
        reactionPickerFor = null
        scope.launch {
            if (currentReaction == emoji) {
                onRemoveReaction(commentUri)
            } else {
                onReact(commentUri, emoji)
            }
            onRefresh()
        }
    }

    fun toggleReactionsView(commentUri: String) {
        val current = reactionViews[commentUri]
        if (current != null && (current.entries.isNotEmpty() || current.expanded)) {
            reactionViews[commentUri] = current.copy(expanded = !current.expanded)
            return
        }
        reactionViews[commentUri] = ReactionViewState(loading = true, expanded = true)
        scope.launch {
            val page = onLoadReactions(commentUri)
            val state = reactionViews[commentUri] ?: return@launch
            reactionViews[commentUri] = state.copy(
                loading = false,
                entries = page?.entries.orEmpty(),
            )
        }
    }

    fun deleteComment(commentUri: String) {
        deleteAskFor = null
        scope.launch {
            onDeleteComment(commentUri)
            onRefresh()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
            .background(EncoreColors.Card)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PEEK_HEIGHT)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.comments),
                    style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_arrow_down_24,
                    contentDescription = stringResource(R.string.refresh_comments),
                    onClick = onRefresh,
                    tint = EncoreColors.TextSecondary,
                    size = 32.dp,
                    iconSize = 18.dp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // The thread is capped at roughly five comments so a long
                // episode no longer stretches the extras column (and the
                // screen behind it) to the full comment count; anything
                // beyond that scrolls inside the card instead. Replies stay
                // inside the same scroll so the composer below stays put.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (comments.size > MAX_VISIBLE_COMMENTS)
                                Modifier
                                    .heightIn(max = COMMENT_THREAD_MAX_HEIGHT)
                                    .verticalScroll(rememberScrollState())
                            else Modifier
                        )
                ) {
                comments.forEach { comment ->
                    EpisodeCommentRow(
                        comment = comment,
                        repliesExpanded = replyStates[comment.uri]?.expanded == true,
                        onToggleReplies = { toggleReplies(comment.uri) },
                        onReactClick = { toggleReactionPicker(comment.uri) },
                        onToggleReactions = { toggleReactionsView(comment.uri) },
                        isOwn = selfUserUri.isNotBlank() && comment.authorUri == selfUserUri,
                        onDeleteClick = { deleteAskFor = comment.uri }
                    )
                    if (reactionPickerFor == comment.uri) {
                        ReactionPickerRow(
                            current = comment.myReaction,
                            onPick = { emoji -> react(comment.uri, comment.myReaction, emoji) }
                        )
                    }
                    val reactionView = reactionViews[comment.uri]
                    if (reactionView?.expanded == true) {
                        ReactionEntriesList(state = reactionView)
                    }
                    val replyState = replyStates[comment.uri]
                    if (replyState?.expanded == true) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 40.dp)
                        ) {
                            if (replyState.replies.isEmpty() && replyState.loading) {
                                Text(
                                    text = stringResource(R.string.loading_replies),
                                    style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            replyState.replies.forEach { reply ->
                                EpisodeReplyRow(
                                    reply = reply,
                                    onReactClick = { toggleReactionPicker(reply.uri) },
                                    onToggleReactions = { toggleReactionsView(reply.uri) },
                                    isOwn = selfUserUri.isNotBlank() && reply.authorUri == selfUserUri,
                                    onDeleteClick = { deleteAskFor = reply.uri }
                                )
                                if (reactionPickerFor == reply.uri) {
                                    ReactionPickerRow(
                                        current = reply.myReaction,
                                        onPick = { emoji -> react(reply.uri, reply.myReaction, emoji) }
                                    )
                                }
                                val replyReactionView = reactionViews[reply.uri]
                                if (replyReactionView?.expanded == true) {
                                    ReactionEntriesList(state = replyReactionView)
                                }
                                Spacer(modifier = Modifier.height(EncoreSpacers.Tighter4))
                            }
                            if (replyState.nextToken.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.show_more_replies),
                                    style = EncoreTheme.typography.marginal.copy(color = EncoreColors.Green),
                                    modifier = Modifier
                                        .padding(top = 2.dp, bottom = 4.dp)
                                        .clickable(enabled = !replyState.loading) { loadMoreReplies(comment.uri) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))
                }
                if (comments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_comments_yet),
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))
                }
                }

                Spacer(modifier = Modifier.height(EncoreSpacers.Tighter4))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter4)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        state = draftState,
                        inputTransformation = InputTransformation.maxLengthTrim(280),
                        textStyle = EncoreTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(EncoreColors.Green),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
                            .background(EncoreColors.PureBlack.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorator = TextFieldDecorator { inner ->
                            Box(modifier = Modifier.heightIn(min = 20.dp)) {
                                if (draftState.text.isEmpty()) {
                                    androidx.compose.material3.Text(
                                        text = stringResource(R.string.write_a_comment),
                                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                                    )
                                } else {
                                    inner()
                                }
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (draftState.text.isBlank() || posting) EncoreColors.PureBlack.copy(alpha = 0.3f) else EncoreColors.Green)
                            .clickable(enabled = draftState.text.isNotBlank() && !posting) {
                                val text = draftState.text.toString().trim()
                                if (text.isEmpty()) return@clickable
                                posting = true
                                scope.launch {
                                    val ok = onPostComment(text)
                                    posting = false
                                    if (ok) {
                                        draftState.clearText()
                                        onRefresh()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (posting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_arrow_up_24),
                                contentDescription = stringResource(R.string.post_comment),
                                tint = if (draftState.text.isBlank()) EncoreColors.TextSecondary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    deleteAskFor?.let { uri ->
        com.quackify.compose.sheets.DeleteConfirmDialog(
            title = stringResource(R.string.delete_comment_title),
            message = stringResource(R.string.delete_comment_message),
            onConfirm = { deleteComment(uri) },
            onDismiss = { deleteAskFor = null }
        )
    }
}

@Composable
private fun EpisodeCommentRow(
    comment: com.quackify.backend.model.EpisodeComment,
    repliesExpanded: Boolean = false,
    onToggleReplies: () -> Unit = {},
    onReactClick: () -> Unit = {},
    onToggleReactions: () -> Unit = {},
    isOwn: Boolean = false,
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF535353)),
            contentAlignment = Alignment.Center
        ) {
            if (comment.avatarUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = comment.avatarUrl,
                    contentDescription = comment.authorName,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 16.dp
                )
            } else {
                Text(
                    text = comment.authorName.firstOrNull()?.uppercase() ?: "?",
                    style = EncoreTheme.typography.bodySmallBold.copy(color = Color.White)
                )
            }
        }
        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter4))
        Column {
            Text(
                text = comment.authorName,
                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
            )
            Text(
                text = comment.text,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter4)
            ) {
                if (comment.reactionCount > 0) {
                    Text(
                        text = if (comment.topReaction.isNotBlank()) {
                            "${comment.topReaction} ${comment.reactionCount}"
                        } else {
                            comment.reactionCount.toString()
                        },
                        style = EncoreTheme.typography.marginal.copy(
                            color = if (comment.myReaction.isNotBlank()) EncoreColors.Green
                            else EncoreColors.TextSecondary
                        ),
                        modifier = Modifier
                            .clickable { onToggleReactions() }
                            .padding(vertical = 2.dp)
                    )
                }
                if (comment.replyCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onToggleReplies() }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.comment_replies, comment.replyCount),
                            style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                        )
                        Icon(
                            painter = painterResource(
                                id = if (repliesExpanded) R.drawable.encore_icon_chevron_up_24
                                else R.drawable.encore_icon_chevron_down_24
                            ),
                            contentDescription = null,
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_add_reaction_24),
                    contentDescription = stringResource(R.string.add_reaction),
                    tint = if (comment.myReaction.isNotBlank()) EncoreColors.Green else EncoreColors.TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onReactClick() }
                )
                if (isOwn) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_trash_24),
                        contentDescription = stringResource(R.string.delete_comment_title),
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDeleteClick() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeReplyRow(
    reply: com.quackify.backend.model.EpisodeCommentReply,
    onReactClick: () -> Unit = {},
    onToggleReactions: () -> Unit = {},
    isOwn: Boolean = false,
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF535353)),
            contentAlignment = Alignment.Center
        ) {
            if (reply.avatarUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = reply.avatarUrl,
                    contentDescription = reply.authorName,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 12.dp
                )
            } else {
                Text(
                    text = reply.authorName.firstOrNull()?.uppercase() ?: "?",
                    style = EncoreTheme.typography.marginal.copy(color = Color.White)
                )
            }
        }
        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter4))
        Column {
            Text(
                text = reply.authorName,
                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
            )
            Text(
                text = reply.text,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter4)
            ) {
                if (reply.reactionCount > 0) {
                    Text(
                        text = if (reply.topReaction.isNotBlank()) {
                            "${reply.topReaction} ${reply.reactionCount}"
                        } else {
                            reply.reactionCount.toString()
                        },
                        style = EncoreTheme.typography.marginal.copy(
                            color = if (reply.myReaction.isNotBlank()) EncoreColors.Green
                            else EncoreColors.TextSecondary
                        ),
                        modifier = Modifier
                            .clickable { onToggleReactions() }
                            .padding(vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_add_reaction_24),
                    contentDescription = stringResource(R.string.add_reaction),
                    tint = if (reply.myReaction.isNotBlank()) EncoreColors.Green else EncoreColors.TextSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onReactClick() }
                )
                if (isOwn) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_trash_24),
                        contentDescription = stringResource(R.string.delete_comment_title),
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onDeleteClick() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionPickerRow(
    current: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QUICK_REACTIONS.forEach { emoji ->
            val selected = emoji == current
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) EncoreColors.Green.copy(alpha = 0.25f)
                        else EncoreColors.PureBlack.copy(alpha = 0.3f)
                    )
                    .clickable { onPick(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    style = EncoreTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ReactionEntriesList(
    state: ReactionViewState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (state.entries.isEmpty() && state.loading) {
            Text(
                text = stringResource(R.string.loading_reactions),
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
            )
        }
        state.entries.forEach { entry ->
            Text(
                text = "${entry.reactionUnicode} ${entry.authorName}",
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}
