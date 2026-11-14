package com.quackify.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.content.res.Configuration

/** How long the toast stays on screen before dismissing itself. */
private const val TOAST_DURATION_MS = 2400L

/** Toasts with an action stay a little longer so the button can be reached. */
private const val TOAST_ACTION_DURATION_MS = 4000L

/** Gap between the message and the action button. */
private val TOAST_ACTION_SPACING = 8.dp

/** Spotify's in-app toast surface, white like the real snackbar. */
private val SpotifyToastBackground = Color(0xFFFFFFFF)

private val SpotifyToastTextColor = Color(0xFF191414)

/** Spotify renders snackbar actions in their signature green. */
private val SpotifyToastActionColor = Color(0xFF1DB954)

/** A tappable action rendered next to the toast message. */
class SpotifyToastAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * State holder for the Spotify-style in-app toast. `show()` can be called from
 * any callback (options sheet, swipe gesture, ...); the host renders the pill
 * and clears it again after [TOAST_DURATION_MS]. An optional trailing action
 * button can be attached by passing its label and click handler.
 */
class SpotifyToastState {
    var message by mutableStateOf<String?>(null)
        private set

    var action by mutableStateOf<SpotifyToastAction?>(null)
        private set

    /** Bumped on every show() so a toast showing right now restarts its timer. */
    var sequence by mutableIntStateOf(0)
        private set

    fun show(
        text: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        message = text
        action = if (actionLabel != null && onAction != null) {
            SpotifyToastAction(actionLabel, onAction)
        } else {
            null
        }
        sequence++
    }

    fun clear() {
        message = null
        action = null
    }
}

@Composable
fun rememberSpotifyToastState(): SpotifyToastState = remember { SpotifyToastState() }

/**
 * Spotify-style toast: a white bar with dark text that floats above the mini
 * player at the bottom of the app and dismisses itself after a few seconds,
 * matching the "Added to queue" snackbar of the real client. Portrait spans
 * the full screen width with a wrap-content height; landscape hugs its
 * content instead so it never stretches across the wide layout. When an
 * action is attached it sits at the far end in portrait and follows the
 * centered content with a small gap in landscape.
 */
@Composable
fun SpotifyToastHost(
    state: SpotifyToastState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.sequence) {
        if (state.message != null) {
            delay(if (state.action != null) TOAST_ACTION_DURATION_MS else TOAST_DURATION_MS)
            state.clear()
        }
    }

    // Captured at show() time (keyed on the sequence) so the exit animation
    // still has the last message and action to draw after the state has been
    // cleared.
    val text = remember(state.sequence) { state.message ?: "" }
    val action = remember(state.sequence) { state.action }
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    AnimatedVisibility(
        visible = state.message != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(240)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it / 2 },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (isLandscape) Modifier.wrapContentWidth()
                    else Modifier.fillMaxWidth()
                )
                .background(
                    SpotifyToastBackground,
                    if (isLandscape) RoundedCornerShape(12.dp) else RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Start,
        ) {
            Text(
                text = text,
                color = SpotifyToastTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = if (isLandscape) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                modifier = if (isLandscape) Modifier else Modifier.weight(1f),
            )
            val currentAction = action
            if (currentAction != null) {
                Spacer(modifier = Modifier.width(TOAST_ACTION_SPACING))
                Text(
                    text = currentAction.label,
                    color = SpotifyToastActionColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable {
                            currentAction.onClick()
                            state.clear()
                        }
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}
