package com.spotiduck.compose.sheets

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.spotiduck.compose.components.EncorePlayIndicator
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.EncoreCornerRadii
import com.spotiduck.compose.theme.EncoreSpacers
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme

enum class DeviceType {
    PHONE,
    SPEAKER,
    COMPUTER,
    CAST,
    CAR
}

data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val isCurrent: Boolean = false,
    val protocol: String = "Spotify Connect"
)

/**
 * Spotify "Connect to a device" bottom sheet matching Spotify Mobile decompiled resources:
 * - Current listening device card with animated green equalizer soundwaves and volume slider
 * - Available remote devices (Google Cast, Spotify Connect, Bluetooth)
 * - Volume control with volume icon indicators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyConnectDeviceSheet(
    onDismissRequest: () -> Unit,
    currentDeviceName: String,
    onSelectDevice: (SpotifyDevice) -> Unit,
    devices: List<SpotifyDevice> = defaultDeviceList(),
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var volume by remember { mutableFloatStateOf(0.70f) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        contentColor = EncoreColors.TextPrimary,
        shape = RoundedCornerShape(topStart = EncoreCornerRadii.Larger3, topEnd = EncoreCornerRadii.Larger3),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = EncoreSpacers.Base)
        ) {
            // Header: Title with close 'X' button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Base),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Listening on",
                    style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_x_24,
                    contentDescription = "Close",
                    onClick = onDismissRequest,
                    tint = EncoreColors.TextPrimary,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Base))

            // Current Device Hero Card (Active green state)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Base)
                    .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
                    .background(Color(0xFF1E3A25))
                    .padding(EncoreSpacers.Base)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EncoreColors.Green),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_device_mobile_24),
                                contentDescription = "Active Device",
                                tint = EncoreColors.PureBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Device",
                                style = SpotifyTheme.typography.marginalBold.copy(color = EncoreColors.Green)
                            )
                            Text(
                                text = currentDeviceName,
                                style = SpotifyTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary)
                            )
                        }

                        EncorePlayIndicator(
                            isPlaying = true,
                            color = EncoreColors.Green,
                            size = 16.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(EncoreSpacers.Base))

                    // Volume Slider Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (volume == 0f) R.drawable.encore_icon_volume_off_24 else R.drawable.encore_icon_volume_24
                            ),
                            contentDescription = "Volume",
                            tint = EncoreColors.Green,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = EncoreColors.TextPrimary,
                                activeTrackColor = EncoreColors.Green,
                                inactiveTrackColor = EncoreColors.Divider
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

            // "Select another device" Section
            Text(
                text = "Select another device",
                style = SpotifyTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                modifier = Modifier.padding(horizontal = EncoreSpacers.Base)
            )

            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter2))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(devices.filter { !it.isCurrent }) { device ->
                    DeviceRow(
                        device = device,
                        onClick = { onSelectDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: SpotifyDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconRes = when (device.type) {
        DeviceType.PHONE -> R.drawable.encore_icon_device_mobile_24
        DeviceType.SPEAKER -> R.drawable.encore_icon_device_speaker_24
        DeviceType.COMPUTER -> R.drawable.encore_icon_devices_24
        DeviceType.CAST -> R.drawable.encore_icon_chromecast_disconnected_24
        DeviceType.CAR -> R.drawable.encore_icon_device_car_24
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = device.name,
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(EncoreSpacers.Base))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = SpotifyTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = device.protocol,
                style = SpotifyTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
            )
        }
    }
}

private fun defaultDeviceList(): List<SpotifyDevice> = listOf(
    SpotifyDevice("1", "This Phone", DeviceType.PHONE, isCurrent = true),
    SpotifyDevice("2", "Living Room Speaker", DeviceType.SPEAKER, protocol = "Google Cast"),
    SpotifyDevice("3", "MacBook Pro", DeviceType.COMPUTER, protocol = "Spotify Connect"),
    SpotifyDevice("4", "Kitchen Echo", DeviceType.SPEAKER, protocol = "Spotify Connect"),
    SpotifyDevice("5", "Tesla Model 3", DeviceType.CAR, protocol = "Bluetooth")
)

@Preview
@Composable
fun PreviewSpotifyConnectDeviceSheet() {
    SpotifyEncoreTheme {
        SpotifyConnectDeviceSheet(
            onDismissRequest = {},
            currentDeviceName = "Galaxy S24 Ultra",
            onSelectDevice = {}
        )
    }
}
