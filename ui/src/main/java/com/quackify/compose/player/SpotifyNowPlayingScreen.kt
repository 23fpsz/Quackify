package com.quackify.compose.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.CanvasVideoPlayer
import com.quackify.compose.components.MusicVideoSurface
import com.quackify.compose.components.EncoreHeartButton
import com.quackify.compose.components.EncoreExplicitTag
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.EncoreRepeatButton
import com.quackify.compose.components.EncoreSmartShuffleButton
import com.quackify.compose.components.FullArtworkBackdrop
import com.quackify.compose.components.dissolveInto
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.SpotifyArtworkImage
import com.quackify.compose.components.rememberFullArtworkBlur
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreSpacers
import com.quackify.compose.theme.SpotifyEncoreTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.util.rememberDominantColor

private const val LANDSCAPE_ARTWORK_PANE_WEIGHT = 0.40f
private const val LANDSCAPE_CONTROLS_PANE_WEIGHT = 0.60f
private const val DRAG_AXIS_SLOP_PX = 8f
private const val SKIP_THRESHOLD_PX = 160f
private const val SKIP_TRAVEL_PX = 800f
private const val SNAP_BACK_FLING_VELOCITY = -100f
private const val DISMISS_FLING_VELOCITY = 1000f
private const val AXIS_UNDECIDED = 0
private const val AXIS_HORIZONTAL = 1
private const val AXIS_VERTICAL = 2
private const val AXIS_YIELD = 3

/** Reveal choreography: the artwork lands first, then the rest follows. */
private const val REVEAL_MS = 420
private const val REVEAL_ARTWORK_DELAY_MS = 0
private const val REVEAL_INFO_DELAY_MS = 90
private const val REVEAL_CONTROLS_DELAY_MS = 160
private const val REVEAL_REST_DELAY_MS = 220
private const val REVEAL_RISE_FRACTION = 0.06f
private const val ARTWORK_FADE_FRACTION = 0.42f

/** Canvas banners are glued to the status bar and zoomed to hide the seam. */
private const val CANVAS_FADE_FRACTION = 0.30f
private const val CANVAS_ZOOM = 1.06f
private val HEADER_ROW_HEIGHT = 60.dp
private val PEEK_HEIGHT = 44.dp

private val LANDSCAPE_LYRICS_PEEK_HEIGHT = 200.dp

/** Spotify's dark ink, for content that sits on light artwork. */
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
 * Full-screen Now Playing Player Screen matching Spotify APK (NPV):
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
fun SpotifyNowPlayingScreen(
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
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onMoreClick: () -> Unit,
    onQueueClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    canvasFilePath: String? = null,
    isCanvasAllowed: Boolean = true,
    isCanvasEnabled: Boolean = false,
    fullArtwork: Boolean = true,
    onCanvasToggle: (Boolean) -> Unit = {},
    musicVideoAvailable: Boolean = false,
    videoActive: Boolean = false,
    videoQualities: List<Int> = emptyList(),
    videoSwitching: Boolean = false,
    onSwitchVideo: (Boolean) -> Unit = {},
    onVideoQuality: (Int) -> Unit = {},
    onVideoSurface: (android.view.Surface?) -> Unit = {},
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
    lyrics: List<TimeSyncedLyric> = emptyList()
) {
    var isLyricsFullscreen by remember { mutableStateOf(false) }
    var isLyricsShareOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = isLyricsFullscreen) { isLyricsFullscreen = false }

    val canvasActive = isCanvasAllowed && isCanvasEnabled && canvasFilePath != null

    val coroutineScope = rememberCoroutineScope()
    val playerOffsetY = remember { Animatable(0f) }
    val scrollState = rememberScrollState()
    val reveal = rememberRevealProgress()

    val activeLyricIndex = remember(currentPositionMs, lyrics) {
        lyrics.indexOfLast { currentPositionMs >= it.startTimeMs }.coerceAtLeast(0)
    }
    val activeLyricLine = lyrics.getOrNull(activeLyricIndex)?.text.orEmpty()

    val effectiveMonthlyListeners = monthlyListeners
    val effectiveArtistBio = artistBio.ifBlank {
        if (artist.isNotBlank()) "Listen to $artist on Spotify. Discover top songs and upcoming releases." else ""
    }

    val effectiveIsLoading = isLoading
    val effectiveLyricsVisible = lyrics.isNotEmpty() && activeLyricLine.isNotBlank()

    val lyricLineHeight by animateDpAsState(
        targetValue = if (effectiveLyricsVisible) 36.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "lyricLineHeight"
    )
    val lyricBottomSpacerHeight by animateDpAsState(
        targetValue = if (effectiveLyricsVisible) 28.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "lyricBottomSpacerHeight"
    )

    val artworkPushUpOffset by animateDpAsState(
        targetValue = if (effectiveLyricsVisible) 0.dp else 40.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "artworkPushUpOffset"
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = screenHeightPx * 0.5f
    val fullArtworkHeight = (configuration.screenWidthDp + 76).dp
    val videoAreaHeight = (configuration.screenWidthDp * 0.8f * 9f / 16f).dp + 24.dp

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

    val headerDragModifier = Modifier.npvDismissDragGesture(
        coroutineScope = coroutineScope,
        playerOffsetY = playerOffsetY,
        dismissThresholdPx = dismissThresholdPx,
        onDismiss = dismissPlayer,
        onSnapBack = snapBackPlayer
    )

    val artworkGestureModifier = Modifier.npvArtworkGesture(
        coroutineScope = coroutineScope,
        playerOffsetY = playerOffsetY,
        scrollState = scrollState,
        scrollEnabled = !effectiveIsLoading,
        dismissThresholdPx = dismissThresholdPx,
        onSkipNext = onSkipNextClick,
        onSkipPrevious = onSkipPreviousClick,
        onDismiss = dismissPlayer,
        onSnapBack = snapBackPlayer
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = playerOffsetY.value.coerceAtLeast(0f)
            }
    ) {
        val viewportHeight = maxHeight
        val dynamicArtworkColor by rememberDominantColor(
            imageUrl = coverUrl,
            fallbackSeed = title + artist,
            defaultColor = dominantColor
        )

        val animatedDominantColor by animateColorAsState(
            targetValue = dynamicArtworkColor,
            animationSpec = tween(durationMillis = 600, easing = EncoreAnimation.DecelerateInterpolator),
            label = "npvDominantColorTransition"
        )

        val fullArtworkBlur by rememberFullArtworkBlur(coverUrl)

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

        if (fullArtwork) {
            FullArtworkBackdrop(
                image = fullArtworkBlur,
                modifier = Modifier.fillMaxSize()
            )
        }

        @OptIn(ExperimentalFoundationApi::class)
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null
        ) {
            val resolvedContextTitle = contextHeaderTitle ?: when {
                albumOrPlaylistContext.isBlank() || albumOrPlaylistContext.equals("Recommended for you", ignoreCase = true) -> "Recommended for you"
                else -> "Playing from playlist"
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
                            videoQualities = videoQualities,
                            onVideoQuality = onVideoQuality,
                            onVideoSurface = onVideoSurface,
                            modifier = Modifier
                                .aspectRatio(if (videoActive) 16f / 9f else 1.0f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(LANDSCAPE_CONTROLS_PANE_WEIGHT)
                            .fillMaxHeight()
                            .verticalScroll(state = scrollState)
                    ) {
                        NowPlayingHeaderRow(
                            resolvedContextTitle = resolvedContextTitle,
                            contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                            onCollapseClick = onCollapseClick,
                            onMoreClick = onMoreClick,
                            dominantColor = animatedDominantColor,
                            dragModifier = headerDragModifier
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        NowPlayingSyncedLyricLine(
                            lyricText = activeLyricLine,
                            visible = effectiveLyricsVisible,
                            height = if (effectiveLyricsVisible) 36.dp else 0.dp,
                            onOpenLyrics = { isLyricsFullscreen = true }
                        )

                        MusicVideoSwitchRow(
                            available = musicVideoAvailable,
                            active = videoActive,
                            switching = videoSwitching,
                            onSwitch = onSwitchVideo
                        )

                        if (effectiveLyricsVisible) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        NowPlayingTrackInfoRow(
                            title = title,
                            artist = artist,
                            isExplicit = isExplicit,
                            isLiked = isLiked,
                            showSmartShuffleBadge = isEnhancedTrack,
                            onTitleClick = onTitleClick,
                            onArtistClick = onArtistClick,
                            onLikeClick = onLikeClick,
                            onBanClick = onBanClick
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        NowPlayingSeekBarSection(
                            progress = progress,
                            durationMs = durationMs,
                            currentPositionMs = currentPositionMs,
                            onSeek = onSeek,
                            isLoading = effectiveIsLoading,
                            canSeek = canSeek
                        )

                        Spacer(modifier = Modifier.height(20.dp))

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

                        Spacer(modifier = Modifier.height(16.dp))

                        NowPlayingFooterRow(
                            connectedDeviceName = connectedDeviceName,
                            onDeviceClick = onDeviceClick,
                            onQueueClick = onQueueClick,
                            onShareClick = onShareClick
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        NowPlayingLyricsPeekCard(
                            visible = effectiveLyricsVisible,
                            lyrics = lyrics,
                            activeIndex = activeLyricIndex,
                            dominantColor = dynamicArtworkColor,
                            peekHeight = LANDSCAPE_LYRICS_PEEK_HEIGHT,
                            onOpenLyrics = { isLyricsFullscreen = true },
                            onShareLyrics = { isLyricsShareOpen = true }
                        )

                        if (!effectiveIsLoading && artist.isNotBlank()) {
                            if (effectiveLyricsVisible) {
                                Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                            }

                            NowPlayingAboutArtistCard(
                                artist = artist,
                                monthlyListeners = effectiveMonthlyListeners,
                                artistBio = effectiveArtistBio,
                                artistImageUrl = artistImageUrl,
                                isFollowingArtist = isArtistFollowing,
                                onFollowToggle = { onArtistFollowToggle(!isArtistFollowing) },
                                onArtistClick = onArtistClick
                            )

                            Spacer(modifier = Modifier.height(EncoreSpacers.Looser3))
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
                            .heightIn(min = viewportHeight - PEEK_HEIGHT),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (fullArtwork) {
                            val card: @Composable () -> Unit = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .revealAt(reveal, REVEAL_ARTWORK_DELAY_MS / REVEAL_MS.toFloat())
                                ) {
                                    NowPlayingArtworkCard(
                                        title = title,
                                        coverUrl = coverUrl,
                                        dominantColor = dynamicArtworkColor,
                                        backgroundColor = animatedDominantColor,
                                        canvasFilePath = canvasFilePath,
                                        isCanvasEnabled = canvasActive,
                                        fullBleed = true,
                                        fadeFraction = if (canvasActive) CANVAS_FADE_FRACTION else ARTWORK_FADE_FRACTION,
                                        canvasZoom = CANVAS_ZOOM,
                                        videoActive = videoActive,
                                        videoQualities = videoQualities,
                                        onVideoQuality = onVideoQuality,
                                        onVideoSurface = onVideoSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (videoActive) Modifier.height(videoAreaHeight)
                                                else Modifier.height(fullArtworkHeight)
                                            )
                                    )
                                }
                            }
                            val header: @Composable () -> Unit = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                ) {
                                    NowPlayingHeaderRow(
                                        resolvedContextTitle = resolvedContextTitle,
                                        contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                                        onCollapseClick = onCollapseClick,
                                        onMoreClick = onMoreClick,
                                        dominantColor = animatedDominantColor,
                                        dragModifier = headerDragModifier
                                    )
                                }
                            }
                            if (videoActive) {
                                header()
                                Spacer(modifier = Modifier.weight(1f))
                                card()
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    card()
                                    header()
                                }
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                            ) {
                                NowPlayingHeaderRow(
                                    resolvedContextTitle = resolvedContextTitle,
                                    contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                                    onCollapseClick = onCollapseClick,
                                    onMoreClick = onMoreClick,
                                    dominantColor = animatedDominantColor,
                                    dragModifier = headerDragModifier
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 24.dp)
                                    .revealAt(reveal, REVEAL_ARTWORK_DELAY_MS / REVEAL_MS.toFloat()),
                                contentAlignment = Alignment.Center
                            ) {
                                NowPlayingArtworkCard(
                                    title = title,
                                    coverUrl = coverUrl,
                                    dominantColor = dynamicArtworkColor,
                                    backgroundColor = animatedDominantColor,
                                    canvasFilePath = canvasFilePath,
                                    isCanvasEnabled = canvasActive,
                                    fullBleed = false,
                                    fadeFraction = 0f,
                                    videoActive = videoActive,
                                    videoQualities = videoQualities,
                                    onVideoQuality = onVideoQuality,
                                    onVideoSurface = onVideoSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (videoActive) Modifier.height(videoAreaHeight)
                                            else Modifier.aspectRatio(1.0f)
                                        )
                                        )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NowPlayingSyncedLyricLine(
                                lyricText = activeLyricLine,
                                visible = effectiveLyricsVisible,
                                height = lyricLineHeight,
                                onOpenLyrics = { isLyricsFullscreen = true }
                            )

                            MusicVideoSwitchRow(
                                available = musicVideoAvailable,
                                active = videoActive,
                                switching = videoSwitching,
                                onSwitch = onSwitchVideo
                            )

                            Spacer(modifier = Modifier.height(lyricBottomSpacerHeight))

                            NowPlayingTrackInfoRow(
                                title = title,
                                artist = artist,
                                isExplicit = isExplicit,
                                isLiked = isLiked,
                                showSmartShuffleBadge = isEnhancedTrack,
                                onTitleClick = onTitleClick,
                                onArtistClick = onArtistClick,
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
                                canSeek = canSeek
                            )

                            Spacer(modifier = Modifier.height(20.dp))

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

                            Spacer(modifier = Modifier.height(16.dp))

                            NowPlayingFooterRow(
                                connectedDeviceName = connectedDeviceName,
                                onDeviceClick = onDeviceClick,
                                onQueueClick = onQueueClick,
                                onShareClick = onShareClick
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .revealAt(reveal, REVEAL_REST_DELAY_MS / REVEAL_MS.toFloat())
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NowPlayingLyricsPeekCard(
                                visible = effectiveLyricsVisible,
                                lyrics = lyrics,
                                activeIndex = activeLyricIndex,
                                dominantColor = dynamicArtworkColor,
                                onOpenLyrics = { isLyricsFullscreen = true },
                                onShareLyrics = { isLyricsShareOpen = true }
                            )

                            if (!effectiveIsLoading && artist.isNotBlank()) {
                                if (effectiveLyricsVisible) {
                                    Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                                }

                                NowPlayingAboutArtistCard(
                                    artist = artist,
                                    monthlyListeners = effectiveMonthlyListeners,
                                    artistBio = effectiveArtistBio,
                                    artistImageUrl = artistImageUrl,
                                    isFollowingArtist = isArtistFollowing,
                                    onFollowToggle = { onArtistFollowToggle(!isArtistFollowing) },
                                    onArtistClick = onArtistClick
                                )

                                Spacer(modifier = Modifier.height(48.dp))
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
                dominantColor = dominantColor,
                lyrics = lyrics,
                onSeekMs = { targetMs -> if (durationMs > 0L) onSeek(targetMs.toFloat() / durationMs.toFloat()) },
                onShareClick = { isLyricsShareOpen = true },
                onClose = { isLyricsFullscreen = false }
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
                if (velocity.y > 1000f || playerOffsetY.value > dismissThresholdPx) {
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
 * Artwork gestures: horizontal drags perform the swipe-to-skip track
 * animation while vertical drags translate the whole player for
 * drag-to-dismiss.
 *
 * The axis is resolved manually, before any event is consumed. Vertical drags
 * are only claimed once the page can no longer scroll in that direction, so
 * dragging over the artwork scrolls the page like any other part of it and
 * only turns into a dismissal once the top is reached.
 */
private fun Modifier.npvArtworkGesture(
    coroutineScope: CoroutineScope,
    playerOffsetY: Animatable<Float, AnimationVector1D>,
    scrollState: ScrollState,
    scrollEnabled: Boolean,
    dismissThresholdPx: Float,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onSnapBack: () -> Unit
): Modifier = pointerInput(dismissThresholdPx, scrollEnabled) {
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

            val delta = change.positionChange()
            totalDragX += delta.x
            totalDragY += delta.y

            if (axis == AXIS_UNDECIDED) {
                val absX = totalDragX.absoluteValue
                val absY = totalDragY.absoluteValue
                if (absX > DRAG_AXIS_SLOP_PX && absX > absY) {
                    axis = AXIS_HORIZONTAL
                } else if (absY > DRAG_AXIS_SLOP_PX) {
                    val pageAtTop = !scrollEnabled || scrollState.value <= 0
                    axis = if (totalDragY > 0f && pageAtTop) AXIS_VERTICAL else AXIS_YIELD
                }
            }

            when (axis) {
                AXIS_HORIZONTAL -> {
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                }
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
                AXIS_HORIZONTAL -> when {
                    totalDragX < -SKIP_THRESHOLD_PX -> onSkipNext()
                    totalDragX > SKIP_THRESHOLD_PX -> onSkipPrevious()
                    else -> Unit
                }
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
    onCollapseClick: () -> Unit,
    onMoreClick: () -> Unit,
    dominantColor: Color,
    dragModifier: Modifier = Modifier
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
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_chevron_down_24,
            contentDescription = "Collapse player",
            onClick = onCollapseClick,
            tint = ink,
            size = 48.dp,
            iconSize = 24.dp
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = resolvedContextTitle,
                style = SpotifyTheme.typography.marginal.copy(
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
                    style = SpotifyTheme.typography.bodySmallBold.copy(
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
            contentDescription = "More options",
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
    fullBleed: Boolean = false,
    fadeFraction: Float = 0f,
    canvasZoom: Float = 1f,
    videoActive: Boolean = false,
    videoQualities: List<Int> = emptyList(),
    onVideoQuality: (Int) -> Unit = {},
    onVideoSurface: (android.view.Surface?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val corner = if (fullBleed) 0.dp else EncoreCornerRadii.Larger2
    val canvasActive = isCanvasEnabled && canvasFilePath != null
    val layerModifier = if (!canvasActive && !videoActive && fadeFraction > 0f) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .dissolveInto(fadeFraction)
    } else {
        Modifier
    }
    val surfaceModifier = if (canvasActive || videoActive) {
        Modifier
    } else {
        Modifier.background(backgroundColor)
    }
    Box(
        modifier = modifier
            .then(layerModifier)
            .shadow(elevation = if (fullBleed) 0.dp else 16.dp, shape = RoundedCornerShape(corner))
            .then(gestureModifier)
            .clip(RoundedCornerShape(corner))
            .then(surfaceModifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            videoActive -> {
                var overlayVisible by remember { mutableStateOf(false) }
                LaunchedEffect(overlayVisible) {
                    if (overlayVisible) {
                        delay(3000)
                        overlayVisible = false
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { overlayVisible = !overlayVisible }
                        }
                ) {
                    MusicVideoSurface(
                        onSurface = onVideoSurface,
                        modifier = Modifier.fillMaxSize()
                    )
                    AnimatedVisibility(
                        visible = overlayVisible,
                        enter = fadeIn(tween(durationMillis = 180)),
                        exit = fadeOut(tween(durationMillis = 180))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f))
                        ) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .clickable { menuOpen = true }
                                        .padding(7.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.encore_icon_settings_24),
                                        contentDescription = "Video quality",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = Color(0xE6191919)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Auto", color = Color.White) },
                                        onClick = {
                                            onVideoQuality(0)
                                            menuOpen = false
                                        }
                                    )
                                    videoQualities.forEach { height ->
                                        DropdownMenuItem(
                                            text = { Text("${height}p", color = Color.White) },
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
            canvasActive -> {
                CanvasVideoPlayer(
                    filePath = canvasFilePath,
                    fadeFraction = fadeFraction,
                    zoom = canvasZoom,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                SpotifyArtworkImage(
                    imageUrl = coverUrl,
                    contentDescription = title,
                    fallbackColor = dominantColor,
                    modifier = Modifier.fillMaxSize()
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
            label = if (active) "Audio" else "Music video",
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
            style = SpotifyTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

/**
 * 1-line real-time synchronized lyrics element.
 * Reference: revised_template_overlay.xml (@id/lyrics_element)
 * Constant 40dp height container ensures track title and controls below NEVER shift position!
 */
@Composable
private fun NowPlayingSyncedLyricLine(
    lyricText: String,
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
                targetState = lyricText,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(150)))
                },
                label = "1LineLyricsContent"
            ) { lyricLine ->
                Text(
                    text = lyricLine,
                    style = SpotifyTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLyrics() }
                )
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
@Composable
private fun NowPlayingTrackInfoRow(
    title: String,
    artist: String,
    isExplicit: Boolean,
    isLiked: Boolean,
    showSmartShuffleBadge: Boolean,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit,
    onLikeClick: () -> Unit,
    onBanClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SpotifyTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onTitleClick)
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
                        contentDescription = "Smart Shuffle on",
                        tint = EncoreColors.Green,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(14.dp)
                    )
                }
                Text(
                    text = artist,
                    style = SpotifyTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.70f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onArtistClick)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBanClick != null) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_ban_24,
                    contentDescription = "Don't play this",
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
 * 1:1 Spotify Native NPV Scrubber SeekBar with elapsed & remaining timestamps.
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SpotifySeekBar(
            progress = progress,
            durationMs = durationMs,
            onSeek = onSeek,
            isLoading = isLoading,
            enabled = canSeek,
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
                style = SpotifyTheme.typography.marginal.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = formatTime(durationMs),
                style = SpotifyTheme.typography.marginal.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    fontFeatureSettings = "tnum"
                )
            )
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
            contentDescription = "Previous",
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
            contentDescription = "Next",
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
                contentDescription = "Connected devices",
                onClick = onDeviceClick,
                tint = EncoreColors.Green,
                size = 32.dp,
                iconSize = 20.dp
            )
            Text(
                text = connectedDeviceName,
                style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.Green),
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_share_android_24,
                contentDescription = "Share",
                onClick = onShareClick,
                tint = Color.White.copy(alpha = 0.70f),
                size = 40.dp,
                iconSize = 20.dp
            )
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_queue_24,
                contentDescription = "Queue",
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
private fun NowPlayingLyricsPeekCard(
    visible: Boolean,
    lyrics: List<TimeSyncedLyric>,
    activeIndex: Int,
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
                        text = "Lyrics preview",
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ink)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_share_android_24,
                            contentDescription = "Share lyrics",
                            onClick = onShareLyrics,
                            tint = ink,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_fullscreen_24,
                            contentDescription = "Expand lyrics",
                            onClick = onOpenLyrics,
                            tint = ink,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val lyricsListState = rememberLazyListState()
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
                        Text(
                            text = lyric.text,
                            style = SpotifyTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 30.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                color = when {
                                    isActive -> ink.copy(alpha = 0.95f)
                                    i < activeIndex -> ink.copy(alpha = 0.45f)
                                    else -> ink.copy(alpha = 0.55f)
                                }
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

/**
 * "About the Artist" Card (16dp rounded corner, horizontal margin 16dp)
 * with Verified Artist badge and Follow button.
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
    modifier: Modifier = Modifier
) {
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
                    text = "About the artist",
                    style = SpotifyTheme.typography.titleMedium.copy(
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
                            SpotifyArtworkImage(
                                imageUrl = artistImageUrl,
                                contentDescription = artist,
                                modifier = Modifier.fillMaxSize(),
                                fallbackColor = Color(0xFFE8115B)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFE8115B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = artist.firstOrNull()?.uppercase() ?: "A",
                                    style = SpotifyTheme.typography.titleMedium.copy(color = Color.White)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = artist,
                                style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(EncoreSpacers.Tighter4))
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_verified_check_active_24),
                                contentDescription = "Verified Artist",
                                tint = EncoreColors.Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = monthlyListeners,
                            style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                        )
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
                        text = if (isFollowingArtist) "Following" else "Follow",
                        style = SpotifyTheme.typography.bodySmallBold.copy(
                            color = if (isFollowingArtist) EncoreColors.Green else EncoreColors.TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = artistBio,
                style = SpotifyTheme.typography.bodySmall.copy(
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

/**
 * Fullscreen Time-Synced Lyrics Screen matching Spotify mobile lyrics view
 * and decompiled `com.spotify.timesyncedtext.v1.TimeSyncedText` protobuf model:
 * - Dynamic active line calculation from current playback timestamp
 * - Tap any line to seek directly to that lyric position
 * - Smooth auto-scrolling to current line
 */
@Composable
private fun FullscreenLyricsView(
    title: String,
    artist: String,
    currentPositionMs: Long,
    dominantColor: Color,
    lyrics: List<TimeSyncedLyric>,
    onSeekMs: (Long) -> Unit,
    onShareClick: () -> Unit,
    onClose: () -> Unit
) {
    val activeIndex = lyrics.indexOfLast { currentPositionMs >= it.startTimeMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val ink = contrastInk(dominantColor)

    LaunchedEffect(activeIndex) {
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
                contentDescription = "Close lyrics",
                onClick = onClose,
                tint = ink
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = SpotifyTheme.typography.bodySmallBold.copy(color = ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = SpotifyTheme.typography.marginal.copy(color = ink.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            EncoreIconButton(
                iconRes = R.drawable.encore_icon_share_android_24,
                contentDescription = "Share",
                onClick = onShareClick,
                tint = ink
            )
        }

        Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrentLine = index == activeIndex
                Text(
                    text = line.text,
                    style = SpotifyTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentLine) ink else ink.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekMs(line.startTimeMs) }
                        .padding(vertical = EncoreSpacers.Tighter)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewSpotifyNowPlayingScreen() {
    SpotifyEncoreTheme {
        var isPlaying by remember { mutableStateOf(true) }
        var isLiked by remember { mutableStateOf(true) }
        var isShuffle by remember { mutableStateOf(false) }
        var isRepeat by remember { mutableStateOf(true) }

        SpotifyNowPlayingScreen(
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            albumOrPlaylistContext = "Today's Top Hits",
            isPlaying = isPlaying,
            progress = 0.35f,
            durationMs = 230000L,
            currentPositionMs = 80000L,
            isLiked = isLiked,
            isShuffle = isShuffle,
            isRepeat = isRepeat,
            connectedDeviceName = "Living Room Speaker",
            onCollapseClick = {},
            onPlayPauseClick = { isPlaying = !isPlaying },
            onSkipNextClick = {},
            onSkipPreviousClick = {},
            onLikeClick = { isLiked = !isLiked },
            onShuffleClick = { isShuffle = !isShuffle },
            onRepeatClick = { isRepeat = !isRepeat },
            onSeek = {},
            onMoreClick = {},
            onQueueClick = {},
            onDeviceClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 800, heightDp = 360)
@Composable
fun PreviewSpotifyNowPlayingScreenLandscape() {
    SpotifyEncoreTheme {
        var isPlaying by remember { mutableStateOf(true) }
        var isLiked by remember { mutableStateOf(true) }
        var isShuffle by remember { mutableStateOf(false) }
        var isRepeat by remember { mutableStateOf(true) }

        SpotifyNowPlayingScreen(
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            albumOrPlaylistContext = "Today's Top Hits",
            isPlaying = isPlaying,
            progress = 0.35f,
            durationMs = 230000L,
            currentPositionMs = 80000L,
            isLiked = isLiked,
            isShuffle = isShuffle,
            isRepeat = isRepeat,
            connectedDeviceName = "Living Room Speaker",
            onCollapseClick = {},
            onPlayPauseClick = { isPlaying = !isPlaying },
            onSkipNextClick = {},
            onSkipPreviousClick = {},
            onLikeClick = { isLiked = !isLiked },
            onShuffleClick = { isShuffle = !isShuffle },
            onRepeatClick = { isRepeat = !isRepeat },
            onSeek = {},
            onMoreClick = {},
            onQueueClick = {},
            onDeviceClick = {}
        )
    }
}
