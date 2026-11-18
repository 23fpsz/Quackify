package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.quackify.backend.library.lyrics.LyricsProvider
import com.quackify.compose.R
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProviderSheet(
    orderedProviders: List<LyricsProvider>,
    onOrderSelected: (List<LyricsProvider>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var order by remember { mutableStateOf(orderedProviders) }

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
            Text(
                text = stringResource(R.string.lyrics_source_desc),
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            order.forEachIndexed { index, provider ->
                val isSelected = index == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = listOf(provider) + order.filterNot { it == provider }
                            order = next
                            onOrderSelected(next)
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.TextSecondary
                        ),
                        modifier = Modifier.width(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.label,
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) EncoreColors.Green else EncoreColors.TextPrimary
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
                    if (isSelected) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_check_24),
                            contentDescription = stringResource(R.string.selected),
                            tint = EncoreColors.Green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
