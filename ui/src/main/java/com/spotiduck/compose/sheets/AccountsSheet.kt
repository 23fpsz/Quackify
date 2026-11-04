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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotiduck.backend.core.auth.SpotifyAuth
import com.spotiduck.compose.R
import com.spotiduck.compose.components.EncoreProfileAvatar
import com.spotiduck.compose.theme.EncoreColors
import com.spotiduck.compose.theme.SpotifyEncoreTheme
import com.spotiduck.compose.theme.SpotifyTheme

/**
 * Account switcher sheet opened from the profile drawer: lists every account
 * saved on this device, marks the active one, offers adding another account
 * and removing the saved ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    accounts: List<SpotifyAuth.Account>,
    activeAccountId: String,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
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
            Text(
                text = "Accounts",
                style = SpotifyTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            accounts.forEach { account ->
                val isActive = account.id == activeAccountId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isActive) {
                            onDismiss()
                            onSwitchAccount(account.id)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreProfileAvatar(
                        userDisplayName = account.name,
                        avatarUrl = account.imageUrl.takeIf { it.isNotBlank() },
                        onClick = {
                            if (!isActive) {
                                onDismiss()
                                onSwitchAccount(account.id)
                            }
                        },
                        size = 44.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.name.ifBlank { "Spotify user" },
                            style = SpotifyTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (account.email.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = account.email,
                                style = SpotifyTheme.typography.bodySmall.copy(
                                    color = EncoreColors.TextSecondary
                                )
                            )
                        } else if (isActive) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Current account",
                                style = SpotifyTheme.typography.bodySmall.copy(
                                    color = EncoreColors.TextSecondary
                                )
                            )
                        }
                    }

                    if (isActive) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_check_24),
                            contentDescription = "Current account",
                            tint = EncoreColors.Green,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_x_24),
                            contentDescription = "Remove ${account.name}",
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    onRemoveAccount(account.id)
                                }
                                .padding(3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onAddAccount()
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_plus_24),
                    contentDescription = null,
                    tint = EncoreColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Add account",
                    style = SpotifyTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
fun PreviewAccountsSheet() {
    SpotifyEncoreTheme {
        AccountsSheet(
            isOpen = true,
            onDismiss = {},
            accounts = listOf(
                SpotifyAuth.Account(id = "a1", name = "Duck User", email = "duck@spotiduck.app"),
                SpotifyAuth.Account(id = "a2", name = "Second Account"),
            ),
            activeAccountId = "a1",
            onSwitchAccount = {},
            onAddAccount = {},
            onRemoveAccount = {}
        )
    }
}
