package com.spotiduck.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreIconButton
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme
import com.spotiduck.compose.theme.rememberBottomPadding

/**
 * Spotify Settings and Privacy Screen matching Spotify's native preferences.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isComposeUiEnabled: Boolean = true,
    onToggleComposeUi: (Boolean) -> Unit = {},
    onOpenSpotiDuckSettings: () -> Unit = {}
) {
    var isDataSaver by remember { mutableStateOf(false) }
    var isGapless by remember { mutableStateOf(true) }
    var isAutomix by remember { mutableStateOf(true) }
    var isCanvas by remember { mutableStateOf(true) }
    var isNormalizeVolume by remember { mutableStateOf(true) }
    var crossfadeSeconds by remember { mutableFloatStateOf(0f) }
    val bottomPadding = rememberBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        // Sticky Header with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = "Back",
                onClick = onBackClick,
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                style = SpotifyTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
        ) {
            // Profile Summary Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91429)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "D",
                            style = SpotifyTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Duck User",
                            style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "View profile",
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(EncoreColors.Green.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SpotiDuck Engine Active",
                            style = SpotifyTheme.typography.bodySmallBold.copy(
                                color = EncoreColors.Green,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                HorizontalDivider(
                    color = EncoreColors.Card,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Section: Interface Mode
            item {
                SettingsSectionHeader(title = "Interface Mode")
                SettingsToggleRow(
                    title = "Native Spotify Compose UI",
                    subtitle = "Toggle off to switch to the Classic Web Player",
                    isChecked = isComposeUiEnabled,
                    onCheckedChange = { onToggleComposeUi(it) }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSpotiDuckSettings() }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SpotiDuck Engine Settings",
                            style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Adblocker, Multi-account, Proxies & Auto-sleep",
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_chevron_right_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                HorizontalDivider(
                    color = EncoreColors.Card,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Section: Playback
            item {
                SettingsSectionHeader(title = "Playback")

                // Crossfade
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Crossfade",
                            style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = if (crossfadeSeconds == 0f) "Off" else "${crossfadeSeconds.toInt()}s",
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.Green)
                        )
                    }
                    Slider(
                        value = crossfadeSeconds,
                        onValueChange = { crossfadeSeconds = it },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = EncoreColors.Green,
                            activeTrackColor = EncoreColors.Green,
                            inactiveTrackColor = EncoreColors.Card
                        )
                    )
                }

                // Gapless Playback
                SettingsToggleRow(
                    title = "Gapless playback",
                    subtitle = "Allows seamless playback from track to track",
                    isChecked = isGapless,
                    onCheckedChange = { isGapless = it }
                )

                // Automix
                SettingsToggleRow(
                    title = "Automix",
                    subtitle = "Allows smooth transitions between songs in a playlist",
                    isChecked = isAutomix,
                    onCheckedChange = { isAutomix = it }
                )

                // Canvas
                SettingsToggleRow(
                    title = "Canvas",
                    subtitle = "Displays short, looping visuals on tracks",
                    isChecked = isCanvas,
                    onCheckedChange = { isCanvas = it }
                )
            }

            // Section: Audio Quality
            item {
                SettingsSectionHeader(title = "Audio quality")

                SettingsInfoRow(
                    title = "WiFi streaming",
                    value = "Very high"
                )

                SettingsInfoRow(
                    title = "Cellular streaming",
                    value = "High"
                )

                SettingsToggleRow(
                    title = "Normalize volume",
                    subtitle = "Set the same volume level for all tracks",
                    isChecked = isNormalizeVolume,
                    onCheckedChange = { isNormalizeVolume = it }
                )
            }

            // Section: Storage
            item {
                SettingsSectionHeader(title = "Storage")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cache",
                            style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "142 MB",
                            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(EncoreColors.Card)
                            .clickable { /* Clear cache */ }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Clear cache",
                            style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
                        )
                    }
                }
            }

            // Section: About
            item {
                SettingsSectionHeader(title = "About")

                SettingsInfoRow(title = "Version", value = "1.2.2 (Build 23)")
                SettingsInfoRow(title = "Third-party software", value = "Open-source notices")

                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = SpotifyTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White
        ),
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EncoreColors.Green,
                uncheckedThumbColor = EncoreColors.TextSecondary,
                uncheckedTrackColor = EncoreColors.Card
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = SpotifyTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Text(
            text = value,
            style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
        )
    }
}

@Preview
@Composable
fun PreviewSettingsScreen() {
    SpotifyEncoreTheme {
        SettingsScreen(onBackClick = {})
    }
}
