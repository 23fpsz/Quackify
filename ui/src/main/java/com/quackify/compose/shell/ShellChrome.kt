package com.quackify.compose.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.player.SpotifyMiniPlayer
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.util.rememberIsOffline

private const val NAV_BAR_ALPHA = 0.72f
private val NAV_GAP = 8.dp

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
    hasTrack: Boolean = true,
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit = onBarClick,
) {
    val isOffline = rememberIsOffline()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
    ) {
        // Offline banner (like the Spotify app's "You're offline" bar): appears
        // above the mini player whenever the device loses its validated network.
        AnimatedVisibility(
            visible = isOffline,
            enter = fadeIn(animationSpec = tween(EncoreAnimation.ButtonDuration)) +
                    expandVertically(animationSpec = tween(EncoreAnimation.ButtonDuration)),
            exit = fadeOut(animationSpec = tween(EncoreAnimation.ButtonDuration)) +
                    shrinkVertically(animationSpec = tween(EncoreAnimation.ButtonDuration))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EncoreColors.CardElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_cloud_off_24),
                    contentDescription = null,
                    tint = EncoreColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You're offline. Check your internet connection.",
                    style = SpotifyTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }

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
            onExpandClick = onExpandClick,
            onSkipNextClick = onSkipNext,
            onSkipPreviousClick = onSkipPrevious,
            hasTrack = hasTrack
        )

        Spacer(modifier = Modifier.height(NAV_GAP))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EncoreColors.PureBlack.copy(alpha = NAV_BAR_ALPHA))
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
