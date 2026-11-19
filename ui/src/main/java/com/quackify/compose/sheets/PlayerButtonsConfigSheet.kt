package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.settings.PlayerButtonAction
import com.quackify.compose.settings.PlayerButtonsConfig
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerButtonsConfigSheet(
    initialConfig: PlayerButtonsConfig,
    onSave: (PlayerButtonsConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slots = remember { mutableStateListOf<PlayerButtonAction>().apply { addAll(initialConfig.slots) } }
    var activePickerSlot by remember { mutableStateOf<Int?>(null) }

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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.configure_player_buttons),
                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.configure_player_buttons_subtitle),
                style = EncoreTheme.typography.bodySmall,
                color = EncoreColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Visual Media Card Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, EncoreColors.Divider, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF282828)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.encore_icon_album_24),
                                contentDescription = null,
                                tint = EncoreColors.Green,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dracula - JENNIE Remix",
                                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tame Impala",
                                style = EncoreTheme.typography.bodySmall,
                                color = EncoreColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress Bar Demo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.25f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(EncoreColors.Green)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "0:52",
                            style = EncoreTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = EncoreColors.TextSecondary,
                        )
                        Text(
                            text = "3:29",
                            style = EncoreTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = EncoreColors.TextSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive 6-Slot Button Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (i in 0 until 6) {
                            val action = slots.getOrElse(i) { PlayerButtonAction.NONE }
                            val isNone = action == PlayerButtonAction.NONE
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isNone) Color(0xFF141414) else Color(0xFF282828))
                                    .border(
                                        width = 1.dp,
                                        color = if (isNone) EncoreColors.Divider else EncoreColors.Green.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { activePickerSlot = i },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(action.iconRes),
                                    contentDescription = stringResource(action.labelRes),
                                    tint = if (isNone) EncoreColors.TextSecondary.copy(alpha = 0.4f) else Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.tap_button_to_customize),
                style = EncoreTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = EncoreColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        slots.clear()
                        slots.addAll(PlayerButtonsConfig.DEFAULT_SLOTS)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.reset_default),
                        color = EncoreColors.TextSecondary,
                        style = EncoreTheme.typography.bodyMediumBold,
                    )
                }

                Button(
                    onClick = {
                        val newConfig = initialConfig.copy(slots = slots.toList())
                        onSave(newConfig)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EncoreColors.Green,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.save_and_apply),
                        style = EncoreTheme.typography.bodyMediumBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Slot Action Picker Dialog
    activePickerSlot?.let { slotIndex ->
        AlertDialog(
            onDismissRequest = { activePickerSlot = null },
            containerColor = EncoreColors.Card,
            title = {
                Text(
                    text = stringResource(R.string.select_action_for_slot, slotIndex + 1),
                    style = EncoreTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    PlayerButtonAction.entries.forEach { action ->
                        val isSelected = slots.getOrNull(slotIndex) == action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) EncoreColors.Green.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    slots[slotIndex] = action
                                    activePickerSlot = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(action.iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) EncoreColors.Green else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(action.labelRes),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) EncoreColors.Green else Color.White,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(R.drawable.encore_icon_check_24),
                                    contentDescription = null,
                                    tint = EncoreColors.Green,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activePickerSlot = null }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = EncoreColors.TextSecondary,
                    )
                }
            }
        )
    }
}
