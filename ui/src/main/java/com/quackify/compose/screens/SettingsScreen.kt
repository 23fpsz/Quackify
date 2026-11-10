package com.quackify.compose.screens

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import android.os.SystemClock
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quackify.compose.R
import com.quackify.backend.core.auth.SpotifyAuth
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.SpotifyEncoreTheme
import com.quackify.compose.theme.SpotifyTheme
import com.quackify.compose.theme.rememberBottomPadding
import kotlinx.coroutines.delay

data class SettingsSwitches(
    val musicVideos: Boolean = true,
    val canvas: Boolean = true,
    val otherVideos: Boolean = true,
    val showFollows: Boolean = true,
    val showPlaylists: Boolean = true,
)

data class SettingsLocks(
    val musicVideos: Boolean = false,
    val canvas: Boolean = false,
    val otherVideos: Boolean = false,
)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    switches: SettingsSwitches = SettingsSwitches(),
    locks: SettingsLocks = SettingsLocks(),
    product: String = "",
    country: String = "",
    loading: Boolean = false,
    onToggleMusicVideos: (Boolean) -> Unit = {},
    onToggleCanvas: (Boolean) -> Unit = {},
    onToggleOtherVideos: (Boolean) -> Unit = {},
    onToggleShowFollows: (Boolean) -> Unit = {},
    onToggleShowPlaylists: (Boolean) -> Unit = {},
    onToggleFullArtwork: (Boolean) -> Unit = {},
    onOpenLogViewer: () -> Unit = {},
) {
    val bottomPadding = rememberBottomPadding()
    val context = LocalContext.current
    val account = remember { SpotifyAuth.account(context) }
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName.orEmpty()} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("")
    }

    val clientPrefs = remember {
        context.getSharedPreferences("quackify.client", Context.MODE_PRIVATE)
    }
    var autoplayOnStartup by remember {
        mutableStateOf(clientPrefs.getBoolean("autoplayOnStartup", false))
    }
    var autoplayOnEnd by remember {
        mutableStateOf(clientPrefs.getBoolean("autoplayOnEnd", true))
    }
    var fullArtwork by remember {
        mutableStateOf(clientPrefs.getBoolean("fullArtworkPlayer", true))
    }

    val haptics = LocalHapticFeedback.current
    var versionTaps by remember { mutableIntStateOf(0) }
    var lastVersionTapAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(versionTaps) {
        if (versionTaps > 0) {
            delay(5_000L)
            versionTaps = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose { versionTaps = 0 }
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
            item {
                ProfileSummary(account)
                HorizontalDivider(
                    color = EncoreColors.Card,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsSectionHeader(title = "Videos and Canvas")
                Text(
                    text = "It may take some time for your experience to update.",
                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                SettingsToggleRow(
                    title = "Music videos",
                    subtitle = "When off, music videos and live performances play as audio-only.",
                    isChecked = switches.musicVideos,
                    enabled = !locks.musicVideos && !loading,
                    onCheckedChange = onToggleMusicVideos
                )
                SettingsToggleRow(
                    title = "Canvas",
                    subtitle = "Short, looping visuals when a song is playing.",
                    isChecked = switches.canvas,
                    enabled = !locks.canvas && !loading,
                    onCheckedChange = onToggleCanvas
                )
                SettingsToggleRow(
                    title = "Other videos",
                    subtitle = "Video podcasts and videos from creators. Video ads can't be turned off.",
                    isChecked = switches.otherVideos,
                    enabled = !locks.otherVideos && !loading,
                    onCheckedChange = onToggleOtherVideos
                )
            }

            item {
                SettingsSectionHeader(title = "What others can see on your profile")
                SettingsToggleRow(
                    title = "Followers and following",
                    subtitle = "On your profile, people can see who's following you and who you're following.",
                    isChecked = switches.showFollows,
                    enabled = !loading,
                    onCheckedChange = onToggleShowFollows
                )
                SettingsToggleRow(
                    title = "Public playlists",
                    subtitle = "People can see the playlists you've added to your profile.",
                    isChecked = switches.showPlaylists,
                    enabled = !loading,
                    onCheckedChange = onToggleShowPlaylists
                )
            }

            item {
                SettingsSectionHeader(title = "Playback")
                SettingsToggleRow(
                    title = "Autoplay on startup",
                    subtitle = "When you open the app, the loaded track starts playing right away.",
                    isChecked = autoplayOnStartup,
                    enabled = true,
                    onCheckedChange = { on ->
                        autoplayOnStartup = on
                        clientPrefs.edit().putBoolean("autoplayOnStartup", on).apply()
                    }
                )
                SettingsToggleRow(
                    title = "Autoplay",
                    subtitle = "Keep playing a station when the album or playlist runs out.",
                    isChecked = autoplayOnEnd,
                    enabled = true,
                    onCheckedChange = { on ->
                        autoplayOnEnd = on
                        clientPrefs.edit().putBoolean("autoplayOnEnd", on).apply()
                    }
                )
                SettingsToggleRow(
                    title = "Full artwork",
                    subtitle = "Stretch the cover edge to edge behind the status bar, fading into a blurred copy of it. When off, the cover sits in a card.",
                    isChecked = fullArtwork,
                    enabled = true,
                    onCheckedChange = { on ->
                        fullArtwork = on
                        clientPrefs.edit().putBoolean("fullArtworkPlayer", on).apply()
                        onToggleFullArtwork(on)
                    }
                )
            }

            item {
                SettingsSectionHeader(title = "Account")
                val detail = listOfNotNull(
                    product.takeIf { it.isNotBlank() },
                    country.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    SettingsInfoRow(title = "Plan", value = detail)
                }
                Text(
                    text = "Log out",
                    style = SpotifyTheme.typography.bodyMediumBold.copy(color = EncoreColors.Error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLogout() }
                        .padding(vertical = 12.dp)
                )
            }

            item {
                SettingsSectionHeader(title = "About")
                SettingsInfoRow(
                    title = "Version",
                    value = version,
                    modifier = Modifier.clickable {
                        val now = SystemClock.elapsedRealtime()
                        versionTaps = if (now - lastVersionTapAt < 5_000L) versionTaps + 1 else 1
                        lastVersionTapAt = now
                        if (versionTaps >= 5) {
                            versionTaps = 0
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenLogViewer()
                        } else {
                            val remaining = 5 - versionTaps
                            Toast.makeText(
                                context,
                                "$remaining more tap${if (remaining == 1) "" else "s"} to open the log viewer",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun ProfileSummary(account: SpotifyAuth.Account?) {
    val context = LocalContext.current
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
                .background(EncoreColors.Card),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = account?.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                style = SpotifyTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                )
            )
            val avatar = account?.imageUrl.orEmpty()
            if (avatar.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account?.name?.takeIf { it.isNotBlank() } ?: "Not signed in",
                style = SpotifyTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            val detail = account?.email?.takeIf { it.isNotBlank() }
                ?: account?.product?.replaceFirstChar { it.uppercase() }.orEmpty()
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                )
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
    enabled: Boolean = true,
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
                style = SpotifyTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) EncoreColors.TextPrimary else EncoreColors.TextSecondary
                )
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = SpotifyTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                )
            }
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
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
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
