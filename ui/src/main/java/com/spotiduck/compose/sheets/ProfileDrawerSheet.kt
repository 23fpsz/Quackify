package com.spotiduck.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme

/**
 * Spotify Profile Side Sheet / Navigation Menu opened when tapping the user avatar.
 * Matches Spotify's user profile navigation drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDrawerSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onViewProfileClick: () -> Unit,
    onWhatsNewClick: () -> Unit,
    onListeningHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EncoreColors.TextSecondary.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // Profile Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onViewProfileClick()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE91429)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (userDisplayName.trim().firstOrNull() ?: 'D').uppercaseChar().toString(),
                        style = SpotifyTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userDisplayName.ifEmpty { "Duck User" },
                        style = SpotifyTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "View profile",
                        style = SpotifyTheme.typography.bodySmall.copy(
                            color = EncoreColors.TextSecondary
                        )
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
                color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Menu Items
            ProfileDrawerItem(
                title = "What's new",
                iconRes = R.drawable.encore_icon_notification_new_24,
                onClick = {
                    onDismiss()
                    onWhatsNewClick()
                }
            )

            ProfileDrawerItem(
                title = "Listening history",
                iconRes = R.drawable.encore_icon_recently_played_24,
                onClick = {
                    onDismiss()
                    onListeningHistoryClick()
                }
            )

            ProfileDrawerItem(
                title = "Settings and privacy",
                iconRes = R.drawable.encore_icon_tune_24,
                onClick = {
                    onDismiss()
                    onSettingsClick()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileDrawerItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = EncoreColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = SpotifyTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview
@Composable
fun PreviewProfileDrawerSheet() {
    SpotifyEncoreTheme {
        ProfileDrawerSheet(
            isOpen = true,
            onDismiss = {},
            onViewProfileClick = {},
            onWhatsNewClick = {},
            onListeningHistoryClick = {},
            onSettingsClick = {}
        )
    }
}
