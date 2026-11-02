package com.spotiduck.compose.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.compose.player.SpotifyMiniPlayer
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyTheme

@Composable
internal fun ShellBottomBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    progress: Float,
    isLiked: Boolean,
    coverUrl: String,
    connectedDevice: String,
    navItems: List<NavItem>,
    currentNavIndex: Int,
    onNavSelect: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onDeviceClick: () -> Unit,
    onBarClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xD9000000),
                        EncoreColors.PureBlack
                    )
                )
            )
    ) {
        SpotifyMiniPlayer(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            progress = progress,
            isLiked = isLiked,
            coverUrl = coverUrl,
            connectedDeviceName = connectedDevice,
            onPlayPauseClick = onPlayPause,
            onLikeClick = onLike,
            onDeviceClick = onDeviceClick,
            onBarClick = onBarClick,
            onSkipNextClick = onSkipNext,
            onSkipPreviousClick = onSkipPrevious
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EncoreColors.PureBlack)
                .navigationBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                SpotifyBottomNavItem(
                    item = item,
                    isSelected = currentNavIndex == index,
                    onClick = { onNavSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SpotifyBottomNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(
                if (isSelected) item.activeIconRes else item.iconRes
            ),
            contentDescription = item.title,
            tint = if (isSelected) EncoreColors.TextPrimary else EncoreColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = item.title,
            style = SpotifyTheme.typography.tagLabel.copy(fontSize = 10.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) EncoreColors.TextPrimary else EncoreColors.TextSecondary
        )
    }
}
