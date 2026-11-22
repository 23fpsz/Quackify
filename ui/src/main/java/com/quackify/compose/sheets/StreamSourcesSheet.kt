package com.quackify.compose.sheets

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.core.store.StreamSource
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSourcesSheet(
    orderedSources: List<StreamSource>,
    onOrderSelected: (List<StreamSource>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var order by remember { mutableStateOf(orderedSources) }

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
                text = stringResource(R.string.stream_sources),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.stream_sources_desc),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            order.forEachIndexed { index, source ->
                SourceRow(
                    position = index + 1,
                    source = source,
                    isFirst = index == 0,
                    isLast = index == order.lastIndex,
                    onMoveUp = {
                        order = order.moved(index, index - 1)?.also(onOrderSelected) ?: order
                    },
                    onMoveDown = {
                        order = order.moved(index, index + 1)?.also(onOrderSelected) ?: order
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T>? {
    if (to !in indices || from !in indices) return null
    val next = toMutableList()
    next.add(to, next.removeAt(from))
    return next
}

@Composable
private fun SourceRow(
    position: Int,
    source: StreamSource,
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
                text = stringResource(
                    when (source) {
                        StreamSource.SPOTIFY -> R.string.stream_source_spotify
                        StreamSource.YT_MUSIC -> R.string.stream_source_ytmusic
                    }
                ),
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFirst) EncoreColors.Green else EncoreColors.TextPrimary
                )
            )
            Text(
                text = stringResource(
                    if (isFirst) R.string.stream_source_primary else R.string.stream_source_fallback
                ),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
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
