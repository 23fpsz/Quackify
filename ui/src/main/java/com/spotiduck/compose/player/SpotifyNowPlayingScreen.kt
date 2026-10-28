package com.spotiduck.compose.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.spotiduck.compose.theme.SpotifyTheme

/**
 * Full-screen Now Playing Player Screen matching Spotify APK (NPV):
 * - Context header ("PLAYING FROM PLAYLIST" with chevron collapse)
 * - 1:1 Album Artwork with swipe-to-skip gesture
 * - Scrubber slider with elapsed and remaining timestamps
 * - 64dp Play/Pause button with Shuffle & Repeat indicators
 * - Connect Device bar with Share & Queue buttons
 * - Lyrics Peek Card with fullscreen expansion
 * - "About the Artist" Card with Verified Artist badge and Follow button
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
    isExplicit: Boolean = false,
    shuffleMode: ShuffleMode = if (isShuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF,
    repeatMode: RepeatMode = if (isRepeat) RepeatMode.REPEAT_ALL else RepeatMode.OFF,
    dominantColor: Color = Color(0xFF432371),
    coverUrl: String? = null,
    monthlyListeners: String = "98,421,114 monthly listeners",
    artistBio: String = "The Weeknd is a Canadian singer, songwriter, and record producer known for his sonic versatility and dark lyricism.",
    lyricsSnippet: String = "I'm tryna put you in the worst mood, ah\nP1 cleaner than your church shoes, ah\nMilli point two just to hurt you, ah\nAll red Lamb' leanin' on you, ah"
) {
    var isLyricsFullscreen by remember { mutableStateOf(false) }
    var isLyricsShareOpen by remember { mutableStateOf(false) }
    var isFollowingArtist by remember { mutableStateOf(false) }
    var isCanvasVisualizerEnabled by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val playerOffsetY = remember { Animatable(0f) }
    val artworkOffsetX = remember { Animatable(0f) }
    val scrollState = rememberScrollState()

    // Reset translation whenever the screen is composed
    LaunchedEffect(Unit) {
        playerOffsetY.snapTo(0f)
    }

    val dismissPlayer: () -> Unit = {
        coroutineScope.launch {
            playerOffsetY.animateTo(
                targetValue = 2500f,
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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // When player is dragged down (playerOffsetY > 0), any upward drag pulls the sheet back up
                if (available.y < 0f && playerOffsetY.value > 0f) {
                    val newOffset = (playerOffsetY.value + available.y).coerceAtLeast(0f)
                    val consumed = newOffset - playerOffsetY.value
                    coroutineScope.launch {
                        playerOffsetY.snapTo(newOffset)
                    }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // When scrollState is at top (0), dragging down pulls the player sheet down
                if (available.y > 0f && scrollState.value == 0) {
                    coroutineScope.launch {
                        playerOffsetY.snapTo(playerOffsetY.value + available.y)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (playerOffsetY.value > 0f) {
                    val threshold = 180f
                    if (playerOffsetY.value > threshold || available.y > 600f) {
                        dismissPlayer()
                    } else {
                        snapBackPlayer()
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 600f && scrollState.value == 0) {
                    dismissPlayer()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = playerOffsetY.value.coerceAtLeast(0f)
            }
            .nestedScroll(nestedScrollConnection)
    ) {
        // Living dynamic Jellyfish wave background extracted from Spotify Mobile JellyfishView.smali
        JellyfishCanvas(
            dominantColor = dominantColor,
            modifier = Modifier.fillMaxSize()
        )

        @OptIn(ExperimentalFoundationApi::class)
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = EncoreSpacers.Looser2)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Navigation Bar (Collapse Chevron, Context Header, More Options)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = EncoreSpacers.Tighter2, bottom = EncoreSpacers.Base),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_chevron_down_24,
                        contentDescription = "Collapse player",
                        onClick = onCollapseClick,
                        tint = EncoreColors.TextPrimary
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAYING FROM PLAYLIST",
                            style = SpotifyTheme.typography.marginal.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                color = EncoreColors.TextSecondary
                            )
                        )
                        Text(
                            text = albumOrPlaylistContext,
                            style = SpotifyTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_more_android_24,
                        contentDescription = "More options",
                        onClick = onMoreClick,
                        tint = EncoreColors.TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(EncoreSpacers.Base))

                // Large 1:1 Album Artwork with 8dp rounded corners and interactive swipe-to-skip gesture
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.0f)
                        .graphicsLayer {
                            translationX = artworkOffsetX.value
                            rotationZ = (artworkOffsetX.value / 60f).coerceIn(-6f, 6f)
                            alpha = 1f - (artworkOffsetX.value.absoluteValue / 1200f).coerceIn(0f, 0.4f)
                        }
                        .shadow(elevation = 20.dp, shape = RoundedCornerShape(EncoreCornerRadii.Larger2))
                        .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(dominantColor.copy(alpha = 0.9f), Color(0xFF1E1035))
                            )
                        )
                        .pointerInput(Unit) {
                            var isHorizontalGesture: Boolean? = null
                            detectDragGestures(
                                onDragStart = {
                                    isHorizontalGesture = null
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (isHorizontalGesture == true) {
                                            val offset = artworkOffsetX.value
                                            val threshold = 160f
                                            if (offset < -threshold) {
                                                artworkOffsetX.animateTo(
                                                    -800f,
                                                    tween(150, easing = EncoreAnimation.DecelerateInterpolator)
                                                )
                                                onSkipNextClick()
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
                                                onSkipPreviousClick()
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
                                            val threshold = 180f
                                            if (playerOffsetY.value > threshold) {
                                                dismissPlayer()
                                            } else {
                                                snapBackPlayer()
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
                                        snapBackPlayer()
                                        isHorizontalGesture = null
                                    }
                                },
                                onDrag = { change, dragAmount ->
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
                        },
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
                        .clickable { isCanvasVisualizerEnabled = !isCanvasVisualizerEnabled }
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

            Spacer(modifier = Modifier.height(EncoreSpacers.Looser3))

            // Track Title, Artist & Like Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = SpotifyTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(EncoreSpacers.Tighter4))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExplicit) {
                            // ContentRestrictionBadgeView from track_info_nowplaying_view.xml
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
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
                            style = SpotifyTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                EncoreHeartButton(
                    isLiked = isLiked,
                    onLikeToggle = onLikeClick,
                    size = 48.dp,
                    iconSize = 28.dp
                )
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Base))

            // 1:1 Spotify Native NPV Scrubber SeekBar
            SpotifySeekBar(
                progress = progress,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth()
            )

            // Elapsed time & Remaining time timestamps (TextAppearance.NowPlayingTimestamps, 12sp, tnum)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Tighter4),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPositionMs),
                    style = SpotifyTheme.typography.marginal.copy(
                        fontSize = 12.sp,
                        color = EncoreColors.TextSecondary,
                        fontFeatureSettings = "tnum"
                    )
                )
                Text(
                    text = formatTime(durationMs),
                    style = SpotifyTheme.typography.marginal.copy(
                        fontSize = 12.sp,
                        color = EncoreColors.TextSecondary,
                        fontFeatureSettings = "tnum"
                    )
                )
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Looser))

            // Playback Controls (Shuffle, Previous, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    iconColor = EncoreColors.PureBlack
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

            Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

            // Bottom Device & Share / Queue Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDeviceClick)
                        .padding(vertical = EncoreSpacers.Tighter4, horizontal = EncoreSpacers.Tighter3),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_devices_24,
                        contentDescription = "Connected devices",
                        onClick = onDeviceClick,
                        tint = EncoreColors.Green,
                        size = 28.dp,
                        iconSize = 20.dp
                    )
                    Text(
                        text = connectedDeviceName,
                        style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.Green),
                        modifier = Modifier.padding(start = EncoreSpacers.Tighter4)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_share_android_24,
                        contentDescription = "Share",
                        onClick = {},
                        tint = EncoreColors.TextSecondary,
                        size = 40.dp,
                        iconSize = 20.dp
                    )
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_queue_24,
                        contentDescription = "Queue",
                        onClick = onQueueClick,
                        tint = EncoreColors.TextSecondary,
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

            // Lyrics Peek Card (16dp rounded corner, dominant color background)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
                    .background(dominantColor.copy(alpha = 0.65f))
                    .clickable { isLyricsFullscreen = true }
                    .padding(EncoreSpacers.Looser)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lyrics",
                            style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EncoreIconButton(
                                iconRes = R.drawable.encore_icon_share_android_24,
                                contentDescription = "Share lyrics",
                                onClick = { isLyricsShareOpen = true },
                                tint = Color.White,
                                size = 32.dp,
                                iconSize = 18.dp
                            )
                            EncoreIconButton(
                                iconRes = R.drawable.encore_icon_fullscreen_24,
                                contentDescription = "Expand lyrics",
                                onClick = { isLyricsFullscreen = true },
                                tint = Color.White,
                                size = 32.dp,
                                iconSize = 18.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))

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

            Spacer(modifier = Modifier.height(EncoreSpacers.Base))

            // "About the Artist" Card (matches Spotify NPV layout)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                                .clickable { isFollowingArtist = !isFollowingArtist }
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

                    Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))

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

            Spacer(modifier = Modifier.height(48.dp))
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
                lyrics = defaultLyricsList,
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
                lyrics = defaultLyricsList,
                initialDominantColor = dominantColor,
                onDismiss = { isLyricsShareOpen = false },
                onShareConfirm = { _, _, _ -> isLyricsShareOpen = false }
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
