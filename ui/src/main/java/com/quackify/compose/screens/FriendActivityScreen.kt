package com.quackify.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.model.FriendActivityEntry
import com.quackify.compose.R
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.ProfileAvatar
import com.quackify.compose.components.ShimmerBlock
import com.quackify.compose.components.ShimmerHost
import com.quackify.compose.components.ShimmerText
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import kotlinx.coroutines.delay

private const val SILENT_REFRESH_MS = 60_000L

@Composable
fun FriendActivityScreen(
    onBackClick: () -> Unit,
    friends: List<FriendActivityEntry> = emptyList(),
    loading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onSilentRefresh: () -> Unit = {},
    onOpenFriend: (FriendActivityEntry) -> Unit = {},
    onOpenFriendProfile: (FriendActivityEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bottomPadding = rememberBottomPadding()

    LaunchedEffect(Unit) {
        while (true) {
            delay(SILENT_REFRESH_MS)
            onSilentRefresh()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = stringResource(R.string.back),
                onClick = onBackClick,
                tint = EncoreColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.friend_activity),
                style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }

        when {
            loading && friends.isEmpty() -> FriendActivitySkeleton()
            loadFailed && friends.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_cloud_off_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.couldn_t_load_friend_activity),
                        style = EncoreTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.check_your_internet_connection_and_try_again),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.try_again), color = EncoreColors.Green)
                    }
                }
            }
            friends.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_people_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_friend_activity_yet),
                        style = EncoreTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.when_friends_you_follow_play_something_it_ll_show_up_here),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
            ) {
                items(friends, key = { it.userUri.ifBlank { it.userName } }) { friend ->
                    FriendActivityRow(
                        friend = friend,
                        onClick = { onOpenFriend(friend) },
                        onAvatarClick = { onOpenFriendProfile(friend) },
                        modifier = Modifier.animateItem()
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

@Composable
private fun FriendActivityRow(
    friend: FriendActivityEntry,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(
            displayName = friend.userName,
            avatarUrl = friend.userAvatarUrl,
            size = 48.dp,
            onClick = onAvatarClick
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.userName,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = friend.trackName,
                style = EncoreTheme.typography.bodyMedium,
                color = EncoreColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = friend.artistLine,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            EncoreArtworkImage(
                imageUrl = friend.trackImageUrl.takeIf { it.isNotBlank() },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                cornerRadius = 4.dp
            )
            if (friend.timestampMs > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = relativeTime(friend.timestampMs),
                    style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                )
            }
        }
    }
}

@Composable
private fun FriendActivitySkeleton() {
    ShimmerHost {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(8) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerText(widthFraction = 0.44f)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerText(widthFraction = 0.82f)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerText(widthFraction = 0.28f)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ShimmerBlock(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }
    }
}
