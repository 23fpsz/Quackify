package com.quackify.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.quackify.compose.theme.EncoreColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MIN_ROWS = 25
private const val HIDE_AFTER_MS = 1200L

@Composable
fun EncoreFastScroll(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }

    val info = listState.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    val scrollable = total - visible
    val fraction = if (dragging) {
        dragFraction
    } else if (scrollable <= 0) {
        0f
    } else {
        (listState.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
    }

    val scrolling = listState.isScrollInProgress
    val shown = total >= MIN_ROWS && (scrolling || dragging || touched)
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(180),
        label = "fastScrollAlpha",
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
        if (scrollable <= 0) return
        val target = (tapped.coerceIn(0f, 1f) * scrollable).roundToInt()
        scope.launch { listState.scrollToItem(target) }
    }

    Box(
        modifier = modifier
            .width(28.dp)
            .height(264.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(248.dp)
                .onSizeChanged { trackSize = it }
                .alpha(alpha)
                .pointerInput(scrollable) {
                    detectTapGestures { offset -> jumpTo(offset.y / size.height.toFloat()) }
                }
                .pointerInput(scrollable) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            touched = true
                            dragFraction = fraction
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragFraction = (dragFraction + amount.y / trackSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
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
                    .width(4.dp)
                    .height(248.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EncoreColors.TextPrimary.copy(alpha = 0.12f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = ((fraction - 0.5f) * 200).dp)
                    .width(6.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (dragging) EncoreColors.Green else EncoreColors.TextPrimary.copy(alpha = 0.55f)),
            )
        }
    }
}
