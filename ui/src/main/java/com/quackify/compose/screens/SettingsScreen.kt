package com.quackify.compose.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.pm.PackageInfoCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import com.quackify.compose.R
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import com.quackify.compose.widget.WidgetBackgroundStyle
import com.quackify.compose.widget.WidgetOptionsStore

data class AppUpdate(
    val version: String,
    val url: String,
    val notes: String = "",
)

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
    update: AppUpdate? = null,
    updateChecking: Boolean = false,
    updateChecked: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    onAppIconChange: (String) -> Unit = {},
) {
    val bottomPadding = rememberBottomPadding()
    val context = LocalContext.current
    val account = remember { EncoreAuth.account(context) }
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName.orEmpty()} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("")
    }

    val clientPrefs = remember {
        com.quackify.backend.core.store.AppPrefs.prefs(context, "quackify.client")
    }
    var autoplayOnStartup by remember {
        mutableStateOf(clientPrefs.getBoolean("autoplayOnStartup", false))
    }
    var autoplayOnEnd by remember {
        mutableStateOf(clientPrefs.getBoolean("autoplayOnEnd", true))
    }
    var gapless by remember {
        mutableStateOf(clientPrefs.getBoolean("gaplessPlayback", false))
    }
    var appIcon by remember {
        mutableStateOf(clientPrefs.getString("appIcon", "default").orEmpty().ifBlank { "default" })
    }
    var widgetOptions by remember { mutableStateOf(WidgetOptionsStore.load(context)) }
    var category by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = category != null) {
        category = null
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
                onClick = {
                    if (category == null) onBackClick() else category = null
                },
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (category) {
                    0 -> "General"
                    1 -> "Personalization"
                    2 -> "More"
                    else -> "Settings"
                },
                style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        }

        AnimatedContent(
            targetState = category,
            transitionSpec = {
                val forward = (initialState ?: -1) < (targetState ?: -1)
                (
                    fadeIn(tween(EncoreAnimation.ScreenEnterDuration, easing = EncoreAnimation.BoxInterpolator)) +
                        slideInHorizontally(
                            tween(EncoreAnimation.ScreenEnterDuration, easing = EncoreAnimation.BoxInterpolator)
                        ) { full -> if (forward) full / 6 else -full / 6 }
                    ) togetherWith (
                    fadeOut(tween(EncoreAnimation.ScreenExitDuration)) +
                        slideOutHorizontally(tween(EncoreAnimation.ScreenExitDuration)) { full ->
                            if (forward) -full / 8 else full / 8
                        }
                    )
            },
            label = "SettingsCategoryTransition"
        ) { target ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
            ) {
                if (target == null) {
                    item {
                        ProfileSummary(account)
                        HorizontalDivider(
                            color = EncoreColors.Card,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    item {
                        SettingsCategoryRow(
                            title = "General",
                            description = "Videos, profile visibility and account",
                            onClick = { category = 0 }
                        )
                        SettingsCategoryRow(
                            title = "Personalization",
                            description = "App icon and home screen widgets",
                            onClick = { category = 1 }
                        )
                        SettingsCategoryRow(
                            title = "More",
                            description = "Playback and app information",
                            onClick = { category = 2 }
                        )
                    }
                }

                if (target == 0) {
                    item {
                        SettingsSectionHeader(title = "Videos and Canvas")
                        Text(
                            text = "It may take some time for your experience to update.",
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
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

                }
                if (target == 1) {
                    item {
                        SettingsSectionHeader(title = "Widget")
                        Text(
                            text = "Home screen widgets come in 4x1, 3x2 and 3x3 sizes. Changes show up the next time the widgets refresh.",
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        SettingsToggleRow(
                            title = "Show artwork",
                            subtitle = "Display the album cover on the widgets.",
                            isChecked = widgetOptions.showArtwork,
                            enabled = true,
                            onCheckedChange = { on ->
                                widgetOptions = widgetOptions.copy(showArtwork = on)
                                WidgetOptionsStore.saveShowArtwork(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = "Show like button",
                            subtitle = "Keep the heart beside the playback controls.",
                            isChecked = widgetOptions.showLike,
                            enabled = true,
                            onCheckedChange = { on ->
                                widgetOptions = widgetOptions.copy(showLike = on)
                                WidgetOptionsStore.saveShowLike(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = "Show progress bar",
                            subtitle = "A thin playback line on the 3x2 and 3x3 widgets.",
                            isChecked = widgetOptions.showProgress,
                            enabled = true,
                            onCheckedChange = { on ->
                                widgetOptions = widgetOptions.copy(showProgress = on)
                                WidgetOptionsStore.saveShowProgress(context, on)
                            }
                        )
                        Text(
                            text = "Background",
                            style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsChipOption(
                                label = "Dark",
                                selected = widgetOptions.background == WidgetBackgroundStyle.DARK,
                                onClick = {
                                    widgetOptions = widgetOptions.copy(background = WidgetBackgroundStyle.DARK)
                                    WidgetOptionsStore.saveBackground(context, WidgetBackgroundStyle.DARK)
                                }
                            )
                            SettingsChipOption(
                                label = "Artwork tint",
                                selected = widgetOptions.background == WidgetBackgroundStyle.TINTED,
                                onClick = {
                                    widgetOptions = widgetOptions.copy(background = WidgetBackgroundStyle.TINTED)
                                    WidgetOptionsStore.saveBackground(context, WidgetBackgroundStyle.TINTED)
                                }
                            )
                            SettingsChipOption(
                                label = "Transparent",
                                selected = widgetOptions.background == WidgetBackgroundStyle.TRANSPARENT,
                                onClick = {
                                    widgetOptions = widgetOptions.copy(background = WidgetBackgroundStyle.TRANSPARENT)
                                    WidgetOptionsStore.saveBackground(context, WidgetBackgroundStyle.TRANSPARENT)
                                }
                            )
                        }
                    }

                    item {
                        SettingsSectionHeader(title = "App icon")
                        Text(
                            text = "Pick the launcher icon. It can take a moment for your home screen to update, and the app may restart once.",
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsIconOption(
                                label = "Default",
                                iconRes = R.mipmap.ic_launcher_default,
                                selected = appIcon == "default",
                                onClick = {
                                    appIcon = "default"
                                    clientPrefs.edit().putString("appIcon", "default").apply()
                                    onAppIconChange("default")
                                }
                            )
                            SettingsIconOption(
                                label = "Normal",
                                iconRes = R.mipmap.ic_launcher_normal,
                                selected = appIcon == "normal",
                                onClick = {
                                    appIcon = "normal"
                                    clientPrefs.edit().putString("appIcon", "normal").apply()
                                    onAppIconChange("normal")
                                }
                            )
                        }
                    }

                }
                if (target == 0) {
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
                            style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onLogout() }
                                .padding(vertical = 12.dp)
                        )
                    }

                }
                if (target == 2) {
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
                            title = "Gapless playback",
                            subtitle = "Prepares the next track while the current one plays, so albums and playlists flow without silence between songs.",
                            isChecked = gapless,
                            enabled = true,
                            badge = "BETA",
                            badgeInfo = "Gapless is not optimised yet and may use a lot of license key quota.",
                            onCheckedChange = { on ->
                                gapless = on
                                clientPrefs.edit().putBoolean("gaplessPlayback", on).apply()
                            }
                        )
                    }

                    item {
                        SettingsSectionHeader(title = "About")
                        Text(
                            text = "Quackify is a native Android music client built with Jetpack Compose, continuing the SpotiDuck project.",
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SettingsInfoRow(
                            title = "Version",
                            value = version,
                        )
                        val context = LocalContext.current
                        if (update != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EncoreColors.Card)
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(update.url))
                                            )
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Update available",
                                        style = EncoreTheme.typography.bodyMediumBold
                                    )
                                    Text(
                                        text = update.version,
                                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                    )
                                }
                                Text(
                                    text = "Download",
                                    style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Latest version",
                                    style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = when {
                                        updateChecking -> "Checking\u2026"
                                        updateChecked -> "Up to date"
                                        else -> "\u2014"
                                    },
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                            Text(
                                text = "Check",
                                style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EncoreColors.Card)
                                    .clickable { onCheckUpdates() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AboutLinkButton(label = "GitHub", iconRes = R.drawable.ic_github_24, url = GITHUB_URL)
                            AboutLinkButton(label = "Discord", iconRes = R.drawable.ic_discord_24, url = DISCORD_URL)
                        }
                        Text(
                            text = "\u00A9 2026 Quackify Team",
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            }
        }
    }
}

private const val GITHUB_URL = "https://github.com/23fpsz/Quackify"
private const val DISCORD_URL = "https://discord.gg/NNXDGZEDFs"

@Composable
private fun AboutLinkButton(label: String, iconRes: Int, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(EncoreColors.Card)
            .clickable {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = EncoreColors.TextPrimary
        )
        Text(
            text = label,
            style = EncoreTheme.typography.bodyMediumBold
        )
    }
}

@Composable
private fun ProfileSummary(account: EncoreAuth.Account?) {
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
                style = EncoreTheme.typography.titleLarge.copy(
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
                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            val detail = account?.email?.takeIf { it.isNotBlank() }
                ?: account?.product?.replaceFirstChar { it.uppercase() }.orEmpty()
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = EncoreTheme.typography.titleMedium.copy(
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
    badge: String = "",
    badgeInfo: String = "",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) EncoreColors.TextPrimary else EncoreColors.TextSecondary
                    )
                )
                if (badge.isNotBlank() && badgeInfo.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SettingsBadge(text = badge, info = badgeInfo)
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
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
private fun SettingsBadge(text: String, info: String) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        delay(4500)
        open = false
    }
    Box {
        Text(
            text = text,
            style = EncoreTheme.typography.marginal.copy(
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = EncoreColors.PureBlack
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(EncoreColors.Beta)
                .clickable { open = !open }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(-10, 24),
                onDismissRequest = { open = false }
            ) {
                val arrowHeight = with(density) { 7.dp.toPx() }
                val arrowWidth = with(density) { 14.dp.toPx() }
                val arrowColor = EncoreColors.CardElevated.copy(alpha = 0.94f)
                Column {
                    Canvas(
                        modifier = Modifier
                            .padding(start = with(density) { 14.dp })
                            .size(width = 14.dp, height = 7.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, arrowHeight)
                            lineTo(arrowWidth / 2f, 0f)
                            lineTo(arrowWidth, arrowHeight)
                            close()
                        }
                        drawPath(path, arrowColor)
                    }
                    Text(
                        text = info,
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextPrimary),
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(arrowColor)
                            .border(1.dp, EncoreColors.Divider, RoundedCornerShape(8.dp))
                            .clickable { open = false }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIconOption(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) EncoreColors.Green else EncoreColors.Divider,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onClick)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = EncoreTheme.typography.marginal.copy(
                color = if (selected) EncoreColors.Green else EncoreColors.TextSecondary
            )
        )
    }
}

@Composable
private fun SettingsChipOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = EncoreTheme.typography.bodySmall.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else EncoreColors.TextSecondary
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) EncoreColors.Green else EncoreColors.Card)
            .border(
                width = 1.dp,
                color = if (selected) EncoreColors.Green else EncoreColors.Divider,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = description,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
        Icon(
            painter = painterResource(R.drawable.encore_icon_chevron_right_24),
            contentDescription = null,
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(20.dp)
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
            style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Text(
            text = value,
            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
        )
    }
}

@Preview
@Composable
fun PreviewSettingsScreen() {
    EncoreAppTheme {
        SettingsScreen(onBackClick = {})
    }
}
