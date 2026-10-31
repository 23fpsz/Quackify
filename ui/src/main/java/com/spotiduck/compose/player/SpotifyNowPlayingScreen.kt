package com.spotiduck.compose.player

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreHeartButton
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.components.EncorePlayButton
import com.spotiduck.compose.components.EncoreRepeatButton
import com.spotiduck.compose.components.EncoreSmartShuffleButton
import com.spotiduck.compose.components.JellyfishCanvas
import com.spotiduck.compose.components.RepeatMode
import com.spotiduck.compose.components.ShuffleMode
import com.spotiduck.compose.theme.EncoreAnimation
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.EncoreCornerRadii
import com.spotiduck.compose.theme.EncoreSpacers
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.spotiduck.compose.theme.SpotifyTheme

// Landscape two-pane layout: weight split between the artwork/canvas pane
// and the scrollable controls & lyrics pane
private const val LANDSCAPE_ARTWORK_PANE_WEIGHT = 0.40f
private const val LANDSCAPE_CONTROLS_PANE_WEIGHT = 0.60f

// The landscape right pane is shorter than a portrait screen, so the lyrics
// peek card uses a reduced minimum height instead of the portrait 360dp
private val LANDSCAPE_LYRICS_PEEK_MIN_HEIGHT = 200.dp

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
    modifier: Modifier = Modifier,
    onBanClick: (() -> Unit)? = null,
    contextHeaderTitle: String? = null,
    isExplicit: Boolean = false,
    shuffleMode: ShuffleMode = if (isShuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF,
    repeatMode: RepeatMode = if (isRepeat) RepeatMode.REPEAT_ALL else RepeatMode.OFF,
    dominantColor: Color = Color(0xFF432371),
    coverUrl: String? = null,
    isLoading: Boolean = false,
    monthlyListeners: String = "98,421,114 monthly listeners",
    artistBio: String = "The Weeknd is a Canadian singer, songwriter, and record producer known for his sonic versatility and dark lyricism.",
    lyricsSnippet: String = "I'm tryna put you in the worst mood, ah\nP1 cleaner than your church shoes, ah\nMilli point two just to hurt you, ah\nAll red Lamb' leanin' on you, ah",
    lyrics: List<TimeSyncedLyric> = defaultLyricsList
) {
    var isLyricsFullscreen by remember { mutableStateOf(false) }
    var isLyricsShareOpen by remember { mutableStateOf(false) }
    var isFollowingArtist by remember { mutableStateOf(false) }
    var isCanvasVisualizerEnabled by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val playerOffsetY = remember { Animatable(0f) }
    val artworkOffsetX = remember { Animatable(0f) }
    val scrollState = rememberScrollState()

    val activeLyricIndex = remember(currentPositionMs, lyrics) {
        lyrics.indexOfLast { currentPositionMs >= it.startTimeMs }.coerceAtLeast(0)
    }
    val activeLyricLine = lyrics.getOrNull(activeLyricIndex)?.text.orEmpty()

    // Realistic state: track loading animation and async lyrics availability
    var isSimulatedLoading by remember(title, artist) { mutableStateOf(true) }
    var isLyricsAvailable by remember(title, artist, lyrics) { mutableStateOf(false) }

    LaunchedEffect(title, artist, lyrics) {
        isSimulatedLoading = true
        isLyricsAvailable = false
        kotlinx.coroutines.delay(800)
        isSimulatedLoading = false
        if (lyrics.isNotEmpty()) {
            kotlinx.coroutines.delay(350)
            isLyricsAvailable = true
        }
    }

    val effectiveIsLoading = isLoading || isSimulatedLoading
    val effectiveLyricsVisible = isLyricsAvailable && lyrics.isNotEmpty() && activeLyricLine.isNotBlank()

    // When lyrics become available, the album art smoothly glides UPWARD
    // to accommodate the 1-line lyrics while the player controls remain at their exact vertical positions!
    val artworkPushUpOffset by animateDpAsState(
        targetValue = if (effectiveLyricsVisible) 0.dp else 40.dp,
        animationSpec = tween(
            durationMillis = 400,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "artworkPushUpOffset"
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Landscape: side-by-side layout so the artwork never crowds out the
    // controls on short (phone) landscape viewports
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = screenHeightPx * 0.5f

    // Reset translation whenever the screen is composed
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

    // Shared drag-to-dismiss gesture applied to the context header
    val headerDragModifier = Modifier.npvDismissDragGesture(
        coroutineScope = coroutineScope,
        playerOffsetY = playerOffsetY,
        dismissThresholdPx = dismissThresholdPx,
        onDismiss = dismissPlayer,
        onSnapBack = snapBackPlayer
    )

    // Artwork gestures: horizontal swipe-to-skip + vertical drag-to-dismiss
    val artworkGestureModifier = Modifier.npvArtworkGesture(
        coroutineScope = coroutineScope,
        playerOffsetY = playerOffsetY,
        artworkOffsetX = artworkOffsetX,
        dismissThresholdPx = dismissThresholdPx,
        onSkipNext = onSkipNextClick,
        onSkipPrevious = onSkipPreviousClick,
        onDismiss = dismissPlayer,
        onSnapBack = snapBackPlayer
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = playerOffsetY.value.coerceAtLeast(0f)
            }
    ) {
        val animatedDominantColor by animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(durationMillis = 600, easing = EncoreAnimation.DecelerateInterpolator),
            label = "npvDominantColorTransition"
        )

        // Authentic album-art-extracted vertical gradient matching Spotify nowplaying_overlay_gradient.xml
        // 100% solid, fully opaque colors — zero transparency
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to animatedDominantColor,
                        0.35f to animatedDominantColor,
                        0.70f to Color(0xFF181818),
                        1.0f to Color(0xFF121212)
                    )
                )
        )

        @OptIn(ExperimentalFoundationApi::class)
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null
        ) {
            val resolvedContextTitle = contextHeaderTitle ?: when {
                albumOrPlaylistContext.isBlank() || albumOrPlaylistContext.equals("Recommended for you", ignoreCase = true) -> "Recommended for you"
                else -> "Playing from playlist"
            }
            val hasSubtitle = albumOrPlaylistContext.isNotBlank() && !albumOrPlaylistContext.equals("Recommended for you", ignoreCase = true)

            val isScrollEnabled = !effectiveIsLoading && effectiveLyricsVisible

            if (isLandscape) {
                // Landscape: two panes — artwork/canvas on the left,
                // scrollable controls, lyrics & artist info on the right
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Left pane: 1:1 artwork/canvas constrained by the pane height
                    // so it can never overflow the short landscape viewport
                    Box(
                        modifier = Modifier
                            .weight(LANDSCAPE_ARTWORK_PANE_WEIGHT)
                            .fillMaxHeight()
                            .padding(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NowPlayingArtworkCard(
                            title = title,
                            coverUrl = coverUrl,
                            dominantColor = dominantColor,
                            backgroundColor = animatedDominantColor,
                            isCanvasVisualizerEnabled = isCanvasVisualizerEnabled,
                            onCanvasToggle = { isCanvasVisualizerEnabled = !isCanvasVisualizerEnabled },
                            gestureModifier = artworkGestureModifier,
                            modifier = Modifier
                                .aspectRatio(1.0f)
                                .graphicsLayer { translationX = artworkOffsetX.value }
                        )
                    }

                    // Right pane: always scrollable so every control stays
                    // reachable even on the shortest landscape screens
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
                            dragModifier = headerDragModifier
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        NowPlayingSyncedLyricLine(
                            lyricText = activeLyricLine,
                            visible = effectiveLyricsVisible,
                            onOpenLyrics = { isLyricsFullscreen = true }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        NowPlayingTrackInfoRow(
                            title = title,
                            artist = artist,
                            isExplicit = isExplicit,
                            isLiked = isLiked,
                            onLikeClick = onLikeClick,
                            onBanClick = onBanClick
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        NowPlayingSeekBarSection(
                            progress = progress,
                            durationMs = durationMs,
                            currentPositionMs = currentPositionMs,
                            onSeek = onSeek,
                            isLoading = effectiveIsLoading
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        NowPlayingControlsRow(
                            shuffleMode = shuffleMode,
                            repeatMode = repeatMode,
                            isPlaying = isPlaying,
                            isLoading = effectiveIsLoading,
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
                            onQueueClick = onQueueClick
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        NowPlayingLyricsPeekCard(
                            visible = effectiveLyricsVisible,
                            lyricsSnippet = lyricsSnippet,
                            dominantColor = dominantColor,
                            minHeight = LANDSCAPE_LYRICS_PEEK_MIN_HEIGHT,
                            onOpenLyrics = { isLyricsFullscreen = true },
                            onShareLyrics = { isLyricsShareOpen = true }
                        )

                        // About-the-artist card stays reachable via the scrollable pane
                        if (!effectiveIsLoading) {
                            Spacer(modifier = Modifier.height(EncoreSpacers.Base))

                            NowPlayingAboutArtistCard(
                                artist = artist,
                                monthlyListeners = monthlyListeners,
                                artistBio = artistBio,
                                isFollowingArtist = isFollowingArtist,
                                onFollowToggle = { isFollowingArtist = !isFollowingArtist }
                            )

                            Spacer(modifier = Modifier.height(EncoreSpacers.Looser3))
                        }
                    }
                }
            } else {
                // Portrait: single scrollable column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .verticalScroll(state = scrollState, enabled = isScrollEnabled),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Navigation Bar (Collapse Chevron, Context Header, More Options)
                    NowPlayingHeaderRow(
                        resolvedContextTitle = resolvedContextTitle,
                        contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                        onCollapseClick = onCollapseClick,
                        onMoreClick = onMoreClick,
                        dragModifier = headerDragModifier
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Large 1:1 Album Artwork with 8dp rounded corners and interactive swipe-to-skip gesture
                    // Reference: revised_template_page_layout.xml (@id/track_carousel) -> bounds [63,308][1017,1262] (24dp margins)
                    NowPlayingArtworkCard(
                        title = title,
                        coverUrl = coverUrl,
                        dominantColor = dominantColor,
                        backgroundColor = animatedDominantColor,
                        isCanvasVisualizerEnabled = isCanvasVisualizerEnabled,
                        onCanvasToggle = { isCanvasVisualizerEnabled = !isCanvasVisualizerEnabled },
                        gestureModifier = artworkGestureModifier,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = artworkPushUpOffset)
                            .padding(horizontal = 24.dp)
                            .aspectRatio(1.0f)
                            .graphicsLayer { translationX = artworkOffsetX.value }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NowPlayingSyncedLyricLine(
                        lyricText = activeLyricLine,
                        visible = effectiveLyricsVisible,
                        onOpenLyrics = { isLyricsFullscreen = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NowPlayingTrackInfoRow(
                        title = title,
                        artist = artist,
                        isExplicit = isExplicit,
                        isLiked = isLiked,
                        onLikeClick = onLikeClick,
                        onBanClick = onBanClick
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    NowPlayingSeekBarSection(
                        progress = progress,
                        durationMs = durationMs,
                        currentPositionMs = currentPositionMs,
                        onSeek = onSeek,
                        isLoading = effectiveIsLoading
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    NowPlayingControlsRow(
                        shuffleMode = shuffleMode,
                        repeatMode = repeatMode,
                        isPlaying = isPlaying,
                        isLoading = effectiveIsLoading,
                        onShuffleClick = onShuffleClick,
                        onSkipPreviousClick = onSkipPreviousClick,
                        onPlayPauseClick = onPlayPauseClick,
                        onSkipNextClick = onSkipNextClick,
                        onRepeatClick = onRepeatClick
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    NowPlayingFooterRow(
                        connectedDeviceName = connectedDeviceName,
                        onDeviceClick = onDeviceClick,
                        onQueueClick = onQueueClick
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NowPlayingLyricsPeekCard(
                        visible = effectiveLyricsVisible,
                        lyricsSnippet = lyricsSnippet,
                        dominantColor = dominantColor,
                        onOpenLyrics = { isLyricsFullscreen = true },
                        onShareLyrics = { isLyricsShareOpen = true }
                    )

                    // Other cards (e.g. About the Artist) only appear once loaded and accessible upon scrolling down
                    if (isScrollEnabled) {
                        Spacer(modifier = Modifier.height(EncoreSpacers.Base))

                        NowPlayingAboutArtistCard(
                            artist = artist,
                            monthlyListeners = monthlyListeners,
                            artistBio = artistBio,
                            isFollowingArtist = isFollowingArtist,
                            onFollowToggle = { isFollowingArtist = !isFollowingArtist }
                        )

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }

        // Fullscreen Lyrics Overlay (Slides up over player)
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
                onSeekMs = { targetMs -> onSeek(targetMs.toFloat() / durationMs.toFloat()) },
                onShareClick = { isLyricsShareOpen = true },
                onClose = { isLyricsFullscreen = false }
            )
        }

        // Lyrics Share Card Modal
        if (isLyricsShareOpen) {
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
 */
private fun Modifier.npvArtworkGesture(
    coroutineScope: CoroutineScope,
    playerOffsetY: Animatable<Float, AnimationVector1D>,
    artworkOffsetX: Animatable<Float, AnimationVector1D>,
    dismissThresholdPx: Float,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onSnapBack: () -> Unit
): Modifier = pointerInput(dismissThresholdPx) {
    var isHorizontalGesture: Boolean? = null
    val velocityTracker = VelocityTracker()
    detectDragGestures(
        onDragStart = {
            isHorizontalGesture = null
            velocityTracker.resetTracking()
        },
        onDragEnd = {
            val velocity = velocityTracker.calculateVelocity()
            coroutineScope.launch {
                if (isHorizontalGesture == true) {
                    val offset = artworkOffsetX.value
                    val threshold = 160f
                    if (offset < -threshold) {
                        artworkOffsetX.animateTo(
                            -800f,
                            tween(150, easing = EncoreAnimation.DecelerateInterpolator)
                        )
                        onSkipNext()
                        artworkOffsetX.snapTo(800f)
                        artworkOffsetX.animateTo(
                            0f,
                            tween(200, easing = EncoreAnimation.DecelerateInterpolator)
                        )
                    } else if (offset > threshold) {
                        artworkOffsetX.animateTo(
                            800f,
                            tween(150, easing = EncoreAnimation.DecelerateInterpolator)
                        )
                        onSkipPrevious()
                        artworkOffsetX.snapTo(-800f)
                        artworkOffsetX.animateTo(
                            0f,
                            tween(200, easing = EncoreAnimation.DecelerateInterpolator)
                        )
                    } else {
                        artworkOffsetX.animateTo(
                            0f,
                            tween(150, easing = EncoreAnimation.DecelerateInterpolator)
                        )
                    }
                } else if (isHorizontalGesture == false || playerOffsetY.value > 0f) {
                    if (velocity.y < -100f) {
                        // Swiped UPWARD: no matter the offset, NPV always stays!
                        onSnapBack()
                    } else if (velocity.y > 1000f) {
                        // Flung DOWNWARD: dismiss!
                        onDismiss()
                    } else if (playerOffsetY.value > dismissThresholdPx) {
                        // Released past halfway point without upward swipe: dismiss!
                        onDismiss()
                    } else {
                        // Released in upper half: stay!
                        onSnapBack()
                    }
                }
                isHorizontalGesture = null
            }
        },
        onDragCancel = {
            coroutineScope.launch {
                artworkOffsetX.animateTo(
                    0f,
                    tween(150, easing = EncoreAnimation.DecelerateInterpolator)
                )
                onSnapBack()
                isHorizontalGesture = null
            }
        },
        onDrag = { change, dragAmount ->
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            if (isHorizontalGesture == null) {
                if (dragAmount.x.absoluteValue > dragAmount.y.absoluteValue && dragAmount.x.absoluteValue > 10f) {
                    isHorizontalGesture = true
                } else if (dragAmount.y.absoluteValue > 10f) {
                    isHorizontalGesture = false
                }
            }
            if (isHorizontalGesture == true) {
                change.consume()
                coroutineScope.launch {
                    artworkOffsetX.snapTo(artworkOffsetX.value + dragAmount.x)
                }
            } else if (isHorizontalGesture == false) {
                if (dragAmount.y > 0 || playerOffsetY.value > 0) {
                    change.consume()
                    coroutineScope.launch {
                        playerOffsetY.snapTo((playerOffsetY.value + dragAmount.y).coerceAtLeast(0f))
                    }
                }
            }
        }
    )
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
    dragModifier: Modifier = Modifier
) {
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
            tint = Color.White,
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
                    color = Color.White
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
                        color = Color.White
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
            tint = Color.White,
            size = 48.dp,
            iconSize = 24.dp
        )
    }
}

/**
 * Large 1:1 Album Artwork with 8dp rounded corners, the Jellyfish canvas
 * visualizer (when enabled) and the CANVAS toggle pill.
 * Reference: revised_template_page_layout.xml (@id/track_carousel) -> bounds [63,308][1017,1262] (24dp margins)
 */
@Composable
private fun NowPlayingArtworkCard(
    title: String,
    coverUrl: String?,
    dominantColor: Color,
    backgroundColor: Color,
    isCanvasVisualizerEnabled: Boolean,
    onCanvasToggle: () -> Unit,
    gestureModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(EncoreCornerRadii.Larger2))
            .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
            .background(backgroundColor)
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        if (isCanvasVisualizerEnabled) {
            JellyfishCanvas(
                dominantColor = dominantColor,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            com.spotiduck.compose.components.SpotifyArtworkImage(
                imageUrl = coverUrl,
                contentDescription = title,
                fallbackColor = dominantColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Canvas Mode Toggle Pill (matches Spotify Canvas on/off toggle badge)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onCanvasToggle)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCanvasVisualizerEnabled) "CANVAS ON" else "CANVAS",
                style = SpotifyTheme.typography.marginal.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (isCanvasVisualizerEnabled) EncoreColors.Green else Color.White.copy(alpha = 0.7f)
                )
            )
        }
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
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
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
 * Reference: track_info_nowplaying_view.xml & revised_template_overlay.xml (@id/track_info_feedback_container)
 * Live dump: track_info_view_title [63, 1598], feedback_buttons_container [765, 1596][1038, 1722]
 */
@Composable
private fun NowPlayingTrackInfoRow(
    title: String,
    artist: String,
    isExplicit: Boolean,
    isLiked: Boolean,
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
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExplicit) {
                    // ContentRestrictionBadgeView from track_info_nowplaying_view.xml: 16dp x 16dp, 3dp radius, marginEnd 8dp
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(EncoreColors.TextSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "E",
                            style = SpotifyTheme.typography.marginal.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EncoreColors.PureBlack
                            )
                        )
                    }
                }
                Text(
                    text = artist,
                    style = SpotifyTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.70f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Feedback buttons container: Don't Play (Ban) + Add/Like
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SpotifySeekBar(
            progress = progress,
            durationMs = durationMs,
            onSeek = onSeek,
            isLoading = isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        // Elapsed time & Remaining time timestamps (TextAppearance.Encore.Marginal, 12sp, tnum)
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
        // Smart Shuffle (Off -> Shuffle -> Smart Shuffle)
        EncoreSmartShuffleButton(
            mode = shuffleMode,
            onClick = onShuffleClick,
            size = 48.dp
        )

        // Previous
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_back_24,
            contentDescription = "Previous",
            onClick = onSkipPreviousClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp
        )

        // Play / Pause Button (White 64dp circle)
        EncorePlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPauseClick,
            size = 64.dp,
            iconSize = 32.dp,
            backgroundColor = Color.White,
            iconColor = EncoreColors.PureBlack,
            isLoading = isLoading
        )

        // Next
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_forward_24,
            contentDescription = "Next",
            onClick = onSkipNextClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp
        )

        // Repeat (Off -> Repeat All -> Repeat One)
        EncoreRepeatButton(
            mode = repeatMode,
            onClick = onRepeatClick,
            size = 48.dp
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
                onClick = {},
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
 * Reference: live dump [42, 2269][1038, 2400]
 */
@Composable
private fun NowPlayingLyricsPeekCard(
    visible: Boolean,
    lyricsSnippet: String,
    dominantColor: Color,
    onOpenLyrics: () -> Unit,
    onShareLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 360.dp
) {
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
                .defaultMinSize(minHeight = minHeight) // Ensures only the lyrics card peeks through; other tabs remain below the fold!
                .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
                .background(dominantColor.copy(alpha = 0.65f))
                .clickable { onOpenLyrics() }
                .padding(EncoreSpacers.Looser)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lyrics preview",
                        style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_share_android_24,
                            contentDescription = "Share lyrics",
                            onClick = onShareLyrics,
                            tint = Color.White,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_fullscreen_24,
                            contentDescription = "Expand lyrics",
                            onClick = onOpenLyrics,
                            tint = Color.White,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = lyricsSnippet,
                    style = SpotifyTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.95f)
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
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
    isFollowingArtist: Boolean,
    onFollowToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
            .background(EncoreColors.Card)
            .padding(EncoreSpacers.Looser)
    ) {
        Column {
            // Header: Artist photo banner placeholder + Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8115B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artist.firstOrNull()?.uppercase() ?: "A",
                            style = SpotifyTheme.typography.titleMedium.copy(color = Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = artist,
                                style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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

                // Follow Button (Encore 999dp pill)
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
                tint = Color.White
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = SpotifyTheme.typography.bodySmallBold.copy(color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = SpotifyTheme.typography.marginal.copy(color = Color.White.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            EncoreIconButton(
                iconRes = R.drawable.encore_icon_share_android_24,
                contentDescription = "Share",
                onClick = onShareClick,
                tint = Color.White
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
                        color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.35f)
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
