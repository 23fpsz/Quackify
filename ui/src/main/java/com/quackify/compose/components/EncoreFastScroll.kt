package com.quackify.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MIN_ROWS = 25
private const val HIDE_AFTER_MS = 1000L
private const val TRACK_HEIGHT = 248

@Composable
fun EncoreFastScroll(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val scope = rememberCoroutineScope()
        var trackHeightPx by remember { mutableFloatStateOf(1f) }
        var dragFraction by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        var touched by remember { mutableStateOf(false) }

        val info = listState.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        val scrollable = total - visible
        val scrollableState = rememberUpdatedState(scrollable)
        val fraction = if (dragging) {
            dragFraction
        } else if (scrollable <= 0) {
            0f
        } else {
            (listState.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
        }
        val thumbFraction = if (total <= 0) 1f else (visible.toFloat() / total).coerceIn(0.14f, 1f)
        val thumbHeight = (TRACK_HEIGHT * thumbFraction).dp.coerceAtLeast(32.dp)
        val thumbTop = fraction * (TRACK_HEIGHT - thumbHeight.value)
        val thumbOffset = (thumbTop + thumbHeight.value / 2f - TRACK_HEIGHT / 2f).dp

        val scrolling = listState.isScrollInProgress
        val shown = total >= MIN_ROWS && (scrolling || dragging || touched)
        val alpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(250),
            label = "fastScrollAlpha",
        )
        val thumbWidth by animateDpAsState(
            targetValue = if (dragging) 10.dp else 6.dp,
            animationSpec = tween(150),
            label = "fastScrollThumbWidth",
        )
        val thumbColor by animateColorAsState(
            targetValue = if (dragging) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.30f),
            animationSpec = tween(150),
            label = "fastScrollThumbColor",
        )

        LaunchedEffect(scrolling, dragging) {
            if (scrolling || dragging) {
                touched = true
            } else if (touched) {
                delay(HIDE_AFTER_MS)
                touched = false
            }
        }

        fun jumpTo(tapped: Float) {
            val count = scrollableState.value
            if (count <= 0) return
            val target = (tapped.coerceIn(0f, 1f) * count).roundToInt()
            scope.launch { listState.scrollToItem(target) }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = maxWidth * 0.02f)
                .width(24.dp)
                .height(TRACK_HEIGHT.dp)
                .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1).toFloat() }
                .alpha(alpha)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> jumpTo(offset.y / size.height.toFloat()) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            touched = true
                            dragFraction = fraction
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragFraction = (dragFraction + amount.y / trackHeightPx).coerceIn(0f, 1f)
                            jumpTo(dragFraction)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = thumbOffset)
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(50))
                    .background(thumbColor),
            )
        }
    }
}
