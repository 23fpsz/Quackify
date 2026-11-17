package com.quackify.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quackify.compose.R
import com.quackify.compose.screens.AppUpdate
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme

enum class UpdateInstallPhase { Idle, Downloading, Installing }

@Composable
fun UpdateDialog(
    update: AppUpdate,
    currentVersion: String,
    phase: UpdateInstallPhase,
    downloadedMb: Float,
    totalMb: Float,
    speedMbps: Float,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (phase == UpdateInstallPhase.Idle) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(EncoreColors.CardElevated)
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_available),
                    style = EncoreTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = EncoreColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.padding(top = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentVersion,
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                    )
                    Icon(
                        painter = painterResource(R.drawable.encore_icon_arrow_right_24),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(16.dp),
                        tint = EncoreColors.TextSecondary
                    )
                    Text(
                        text = update.version,
                        style = EncoreTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.Green
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(EncoreColors.Green.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.padding(top = 10.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (update.notes.isBlank()) {
                        Text(
                            text = stringResource(R.string.no_release_notes),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                    } else {
                        MarkdownText(markdown = update.notes, compact = true)
                    }
                }
                Spacer(modifier = Modifier.padding(top = 14.dp))
                when (phase) {
                    UpdateInstallPhase.Downloading -> {
                        LinearProgressIndicator(
                            progress = {
                                if (totalMb > 0f) (downloadedMb / totalMb).coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = EncoreColors.Green,
                            trackColor = Color.White.copy(alpha = 0.14f)
                        )
                        Spacer(modifier = Modifier.padding(top = 6.dp))
                        Text(
                            text = if (totalMb > 0f) {
                                "%.1f / %.1f MB \u00B7 %.1f MB/s".format(downloadedMb, totalMb, speedMbps)
                            } else {
                                "%.1f MB \u00B7 %.1f MB/s".format(downloadedMb, speedMbps)
                            },
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    UpdateInstallPhase.Installing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = EncoreColors.Green
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 5.dp))
                            Text(
                                text = stringResource(R.string.installing),
                                style = EncoreTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EncoreColors.TextPrimary
                                )
                            )
                        }
                    }
                    UpdateInstallPhase.Idle -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.later),
                                style = EncoreTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EncoreColors.TextSecondary
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onLater() }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(R.string.install),
                                style = EncoreTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EncoreColors.Green)
                                    .clickable { onInstall() }
                                    .padding(horizontal = 20.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
