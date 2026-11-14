package com.quackify.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.theme.EncoreColors
import kotlinx.coroutines.delay

/** How long the toast stays on screen before dismissing itself. */
private const val TOAST_DURATION_MS = 2400L

/** Spotify's in-app toast gray, slightly translucent like the real snackbar. */
private val SpotifyToastBackground = Color(0xEB464646)

/**
 * State holder for the Spotify-style in-app toast. `show()` can be called from
 * any callback (options sheet, swipe gesture, ...); the host renders the pill
 * and clears it again after [TOAST_DURATION_MS].
 */
class SpotifyToastState {
    var message by mutableStateOf<String?>(null)
        private set

    /** Bumped on every show() so a toast showing right now restarts its timer. */
    var sequence by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        sequence++
    }

    fun clear() {
        message = null
    }
}

@Composable
fun rememberSpotifyToastState(): SpotifyToastState = remember { SpotifyToastState() }

/**
 * Spotify-style toast: a dark pill with white text that floats above the mini
 * player at the bottom of the app and dismisses itself after a few seconds,
 * matching the "Added to queue" snackbar of the real client.
 */
@Composable
fun SpotifyToastHost(
    state: SpotifyToastState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.sequence) {
        if (state.message != null) {
            delay(TOAST_DURATION_MS)
            state.clear()
        }
    }

    // Captured at show() time (keyed on the sequence) so the exit animation
    // still has the last message to draw after the state has been cleared.
    val text = remember(state.sequence) { state.message ?: "" }

    AnimatedVisibility(
        visible = state.message != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(240)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it / 2 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .background(SpotifyToastBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = EncoreColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
