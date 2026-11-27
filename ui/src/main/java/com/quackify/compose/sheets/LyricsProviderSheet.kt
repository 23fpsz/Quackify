package com.quackify.compose.sheets

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.core.store.StreamSource
import com.quackify.backend.library.lyrics.LyricsProvider
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.player.LyricsOptions
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProviderSheet(
    activeStream: StreamSource? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val spotifyOrder = LyricsOptions.providerOrderFor(StreamSource.SPOTIFY)
    val ytmOrder = LyricsOptions.providerOrderFor(StreamSource.YT_MUSIC)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.lyrics_source),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (activeStream != null) {
                Text(
                    text = stringResource(
                        R.string.lyrics_source_stream_desc,
                        streamLabel(activeStream)
                    ),
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                val order = if (activeStream == StreamSource.YT_MUSIC) ytmOrder else spotifyOrder
                order.forEachIndexed { index, provider ->
                    ProviderRow(
                        position = index + 1,
                        provider = provider,
                        isFirst = index == 0,
                        isLast = index == order.lastIndex,
                        onMoveUp = { move(context, activeStream, order, index, index - 1) },
                        onMoveDown = { move(context, activeStream, order, index, index + 1) },
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.lyrics_source_streams_desc),
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                StreamSection(
                    title = stringResource(R.string.stream_source_spotify),
                    order = spotifyOrder,
                    stream = StreamSource.SPOTIFY
                )
                StreamSection(
                    title = stringResource(R.string.stream_source_ytmusic),
                    order = ytmOrder,
                    stream = StreamSource.YT_MUSIC
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StreamSection(
    title: String,
    order: List<LyricsProvider>,
    stream: StreamSource
) {
    val context = LocalContext.current
    Text(
        text = title,
        style = EncoreTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            color = EncoreColors.TextPrimary
        ),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    order.forEachIndexed { index, provider ->
        ProviderRow(
            position = index + 1,
            provider = provider,
            isFirst = index == 0,
            isLast = index == order.lastIndex,
            onMoveUp = { move(context, stream, order, index, index - 1) },
            onMoveDown = { move(context, stream, order, index, index + 1) },
        )
    }
}

@Composable
private fun ProviderRow(
    position: Int,
    provider: LyricsProvider,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.toString(),
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = EncoreColors.TextSecondary
            ),
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.label,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFirst) EncoreColors.Green else EncoreColors.TextPrimary
                )
            )
            Text(
                text = if (provider.wordSynced) {
                    stringResource(R.string.lyrics_provider_word_synced)
                } else {
                    stringResource(R.string.lyrics_provider_line_synced)
                },
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary
                )
            )
        }
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_arrow_up_24,
            contentDescription = stringResource(R.string.stream_source_move_up),
            onClick = onMoveUp,
            enabled = !isFirst,
            modifier = Modifier.size(40.dp)
        )
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_arrow_down_24,
            contentDescription = stringResource(R.string.stream_source_move_down),
            onClick = onMoveDown,
            enabled = !isLast,
            modifier = Modifier.size(40.dp)
        )
    }
}

private fun move(
    context: Context,
    stream: StreamSource,
    order: List<LyricsProvider>,
    from: Int,
    to: Int,
) {
    if (to !in order.indices || from !in order.indices) return
    val next = order.toMutableList()
    next.add(to, next.removeAt(from))
    LyricsOptions.setOrderFor(context, stream, next)
}

@Composable
private fun streamLabel(stream: StreamSource): String = stringResource(
    when (stream) {
        StreamSource.SPOTIFY -> R.string.stream_source_spotify
        StreamSource.YT_MUSIC -> R.string.stream_source_ytmusic
    }
)
