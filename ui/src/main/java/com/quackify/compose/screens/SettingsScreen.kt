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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.pm.PackageInfoCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import kotlin.math.roundToInt
import com.quackify.compose.R
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.locale.AppLocale
import com.quackify.backend.core.log.DeviceInfo
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.store.StreamSource
import com.quackify.backend.core.store.StreamSources
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.player.LyricsOptions
import com.quackify.compose.player.LYRICS_OFFSET_TENTHS_MAX
import com.quackify.compose.player.LYRICS_OFFSET_TENTHS_MIN
import com.quackify.compose.player.TRANSLATION_DEVICE_LANGUAGE
import com.quackify.compose.sheets.AppLanguageSheet
import com.quackify.compose.sheets.LyricsProviderSheet
import com.quackify.compose.sheets.PlayerButtonsConfigSheet
import com.quackify.compose.sheets.StreamSourcesSheet
import com.quackify.compose.sheets.TranslationLanguageSheet
import com.quackify.compose.settings.PlayerButtonsConfig
import com.quackify.compose.settings.PlayerButtonsStore
import com.quackify.backend.library.lyrics.translationLanguageName
import java.util.Locale
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource
import org.json.JSONObject

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

@OptIn(ExperimentalFoundationApi::class)
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
    updateChecking: Boolean = false,
    updateChecked: Boolean = false,
    updateError: String? = null,
    updateVersion: String? = null,
    onCheckUpdates: () -> Unit = {},
    onAppIconChange: (String) -> Unit = {},
    onOpenLogcat: () -> Unit = {},
    onSpDcSignedIn: () -> Unit = {},
) {
    val bottomPadding = rememberBottomPadding()
    val context = LocalContext.current
    // Read the live LyricsOptions state, not a one-shot copy: the fullscreen
    // player's three-dot sheet writes the same flags, and the rows here must
    // flip with them while the settings screen sits composed underneath.
    val lyricsEnabled = LyricsOptions.enabled
    val lyricsPreview = LyricsOptions.preview
    val lyricsPrioritizeWordSynced = LyricsOptions.prioritizeWordSynced
    val lyricsProviderOrder = LyricsOptions.providerOrder
    val lyricsKeepScreenOn = LyricsOptions.keepScreenOn
    val lyricsNextTrackPeek = LyricsOptions.nextTrackPeek
    val lyricsOffsetTenths = LyricsOptions.offsetTenths + LYRICS_OFFSET_TENTHS_MAX
    val lyricsOffsetLabel = remember(LyricsOptions.offsetTenths) {
        val seconds = LyricsOptions.offsetTenths / 10f
        context.getString(R.string.lyrics_offset_value, seconds)
    }
    val lyricsTranslationEnabled = LyricsOptions.translationEnabled
    val lyricsTranslationLanguage = LyricsOptions.translationLanguage
    val translationLanguageLabel = remember(lyricsTranslationLanguage) {
        if (lyricsTranslationLanguage == TRANSLATION_DEVICE_LANGUAGE) {
            context.getString(
                R.string.lyrics_translation_device_language,
                context.resources.configuration.locales[0]?.displayLanguage
                    ?.takeIf { it.isNotBlank() } ?: Locale.getDefault().displayLanguage,
            )
        } else {
            translationLanguageName(
                lyricsTranslationLanguage,
                context.resources.configuration.locales[0] ?: Locale.getDefault(),
            )
        }
    }
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
    var crossfadeSeconds by remember {
        mutableStateOf((clientPrefs.getInt("crossfadeMs", 0) / 1000).coerceIn(0, 12))
    }
    var automix by remember {
        mutableStateOf(clientPrefs.getBoolean("automix", false))
    }
    var pauseOnDisconnect by remember {
        mutableStateOf(clientPrefs.getBoolean("pauseOnDisconnect", true))
    }
    var resumeOnConnect by remember {
        mutableStateOf(clientPrefs.getBoolean("resumeOnConnect", false))
    }
    var resumeOnHeadphone by remember {
        mutableStateOf(clientPrefs.getBoolean("resumeOnHeadphone", false))
    }
    var appIcon by remember {
        mutableStateOf(clientPrefs.getString("appIcon", "default").orEmpty().ifBlank { "default" })
    }
    if (clientPrefs.contains("debugLogging")) {
        Logs.floor = if (clientPrefs.getBoolean("debugLogging", false)) Logs.Level.DEBUG else Logs.Level.INFO
    }
    var debugUnlocked by remember {
        mutableStateOf(clientPrefs.getBoolean("debugUnlocked", false))
    }
    var debugLogging by remember {
        mutableStateOf(clientPrefs.getBoolean("debugLogging", false))
    }
    var versionTaps by remember { mutableIntStateOf(0) }
    var playerButtonsConfig by remember { mutableStateOf(PlayerButtonsStore.load(context)) }
    var playerButtonsSheetOpen by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<Int?>(null) }
    var spDcDialogOpen by remember { mutableStateOf(false) }
    // Holds the sp_dc that could not be checked while the user decides
    // whether to keep it anyway; blank when no decision is pending.
    var spDcUnchecked by remember { mutableStateOf<String?>(null) }
    // Bumped after a sp_dc is installed so the row's subtitle — which reads
    // prefs, not snapshot state — recomposes and picks the new length up.
    var spDcRevision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val disableDebug = {
        debugUnlocked = false
        debugLogging = false
        versionTaps = 0
        clientPrefs.edit().putBoolean("debugUnlocked", false).apply()
        clientPrefs.edit().putBoolean("debugLogging", false).apply()
        Logs.floor = Logs.Level.INFO
        clientPrefs.edit().putLong("logcatEpochMs", System.currentTimeMillis()).apply()
        if (category == 3) category = null
        Toast.makeText(context, R.string.debug_disabled, Toast.LENGTH_SHORT).show()
    }
    var lyricsProviderSheetOpen by remember { mutableStateOf(false) }
    var translationLanguageSheetOpen by remember { mutableStateOf(false) }
    var appLanguageSheetOpen by remember { mutableStateOf(false) }
    var streamSources by remember { mutableStateOf(StreamSources.order(context)) }
    var streamSourcesSheetOpen by remember { mutableStateOf(false) }
    val streamSourcesLabel = remember(streamSources) {
        streamSources.joinToString(" → ") { source ->
            context.getString(
                when (source) {
                    StreamSource.SPOTIFY -> R.string.stream_source_spotify
                    StreamSource.YT_MUSIC -> R.string.stream_source_ytmusic
                }
            )
        }
    }
    val appLanguage = remember { AppLocale.current(context) }

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
                contentDescription = stringResource(R.string.back),
                onClick = {
                    if (category == null) onBackClick() else category = null
                },
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (category) {
                    0 -> stringResource(R.string.general)
                    1 -> stringResource(R.string.personalization)
                    2 -> stringResource(R.string.more)
                    3 -> stringResource(R.string.debug)
                    else -> stringResource(R.string.settings)
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
                            title = stringResource(R.string.general),
                            description = stringResource(R.string.videos_profile_visibility_and_account),
                            onClick = { category = 0 }
                        )
                        SettingsCategoryRow(
                            title = stringResource(R.string.personalization),
                            description = stringResource(R.string.player_controls_and_app_icon),
                            onClick = { category = 1 }
                        )
                        SettingsCategoryRow(
                            title = stringResource(R.string.more),
                            description = stringResource(R.string.playback_and_app_information),
                            onClick = { category = 2 }
                        )
                        if (debugUnlocked) {
                            SettingsCategoryRow(
                                title = stringResource(R.string.debug),
                                description = stringResource(R.string.developer_tools_and_diagnostics),
                                onClick = { category = 3 },
                                onLongClick = disableDebug
                            )
                        }
                    }
                }

                if (target == 0) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.videos_and_canvas))
                        Text(
                            text = stringResource(R.string.it_may_take_some_time_for_your_experience_to_upd),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.music_videos),
                            subtitle = stringResource(R.string.when_off_music_videos_and_live_performances_play),
                            isChecked = switches.musicVideos,
                            enabled = !locks.musicVideos && !loading,
                            onCheckedChange = onToggleMusicVideos
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.canvas),
                            subtitle = stringResource(R.string.short_looping_visuals_when_a_song_is_playing),
                            isChecked = switches.canvas,
                            enabled = !locks.canvas && !loading,
                            onCheckedChange = onToggleCanvas
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.other_videos),
                            subtitle = stringResource(R.string.video_podcasts_and_videos_from_creators_video_ad),
                            isChecked = switches.otherVideos,
                            enabled = !locks.otherVideos && !loading,
                            onCheckedChange = onToggleOtherVideos
                        )
                    }

                    item {
                        SettingsSectionHeader(title = stringResource(R.string.lyrics))
                        SettingsToggleRow(
                            title = stringResource(R.string.lyrics_show),
                            subtitle = stringResource(R.string.lyrics_show_desc),
                            isChecked = lyricsEnabled,
                            enabled = true,
                            onCheckedChange = { on ->
                                LyricsOptions.setEnabled(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.lyrics_preview),
                            subtitle = stringResource(R.string.lyrics_preview_desc),
                            isChecked = lyricsPreview,
                            enabled = lyricsEnabled,
                            onCheckedChange = { on ->
                                LyricsOptions.setPreview(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.lyrics_prioritize_word_synced),
                            subtitle = stringResource(R.string.lyrics_prioritize_word_synced_desc),
                            isChecked = lyricsPrioritizeWordSynced,
                            enabled = lyricsEnabled,
                            onCheckedChange = { on ->
                                LyricsOptions.setPrioritizeWordSynced(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.lyrics_keep_screen_on),
                            subtitle = stringResource(R.string.lyrics_keep_screen_on_desc),
                            isChecked = lyricsKeepScreenOn,
                            enabled = lyricsEnabled,
                            onCheckedChange = { on ->
                                LyricsOptions.setKeepScreenOn(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.lyrics_next_up),
                            subtitle = stringResource(R.string.lyrics_next_up_desc),
                            isChecked = lyricsNextTrackPeek,
                            enabled = lyricsEnabled,
                            onCheckedChange = { on ->
                                LyricsOptions.setNextTrackPeek(context, on)
                            }
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.lyrics_offset),
                            subtitle = stringResource(R.string.lyrics_offset_desc),
                            value = lyricsOffsetTenths,
                            maxValue = LYRICS_OFFSET_TENTHS_MAX * 2,
                            valueLabel = lyricsOffsetLabel,
                            onValueChange = { tenths ->
                                LyricsOptions.setOffsetTenths(
                                    context,
                                    tenths - LYRICS_OFFSET_TENTHS_MAX,
                                )
                            }
                        )
                        if (lyricsProviderSheetOpen) {
                            LyricsProviderSheet(
                                orderedProviders = lyricsProviderOrder,
                                onOrderSelected = { order ->
                                    LyricsOptions.setProviderOrder(context, order)
                                },
                                onDismiss = { lyricsProviderSheetOpen = false }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = lyricsEnabled) { lyricsProviderSheetOpen = true }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.lyrics_source),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = if (lyricsEnabled) EncoreColors.TextPrimary
                                        else EncoreColors.TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.lyrics_source_desc),
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                            Text(
                                text = lyricsProviderOrder.firstOrNull()?.label.orEmpty(),
                                style = EncoreTheme.typography.bodySmallBold.copy(
                                    color = if (lyricsEnabled) EncoreColors.Green
                                    else EncoreColors.TextSecondary
                                )
                            )
                        }
                        SettingsToggleRow(
                            title = stringResource(R.string.lyrics_translation),
                            subtitle = stringResource(R.string.lyrics_translation_desc),
                            isChecked = lyricsTranslationEnabled,
                            enabled = lyricsEnabled,
                            onCheckedChange = { on ->
                                LyricsOptions.setTranslationEnabled(context, on)
                            }
                        )
                        if (translationLanguageSheetOpen) {
                            TranslationLanguageSheet(
                                selectedCode = lyricsTranslationLanguage,
                                onLanguageSelected = { code ->
                                    LyricsOptions.setTranslationLanguage(context, code)
                                },
                                onDismiss = { translationLanguageSheetOpen = false }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = lyricsEnabled && lyricsTranslationEnabled
                                ) { translationLanguageSheetOpen = true }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.lyrics_translation_language),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = if (lyricsEnabled && lyricsTranslationEnabled) EncoreColors.TextPrimary
                                        else EncoreColors.TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.lyrics_translation_language_desc),
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                            Text(
                                text = translationLanguageLabel,
                                style = EncoreTheme.typography.bodySmallBold.copy(
                                    color = if (lyricsEnabled && lyricsTranslationEnabled) EncoreColors.Green
                                    else EncoreColors.TextSecondary
                                )
                            )
                        }
                    }

                    item {
                        SettingsSectionHeader(title = stringResource(R.string.what_others_can_see_on_your_profile))
                        SettingsToggleRow(
                            title = stringResource(R.string.followers_and_following),
                            subtitle = stringResource(R.string.on_your_profile_people_can_see_who_s_following_y),
                            isChecked = switches.showFollows,
                            enabled = !loading,
                            onCheckedChange = onToggleShowFollows
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.public_playlists),
                            subtitle = stringResource(R.string.people_can_see_the_playlists_you_ve_added_to_you),
                            isChecked = switches.showPlaylists,
                            enabled = !loading,
                            onCheckedChange = onToggleShowPlaylists
                        )
                    }

                    item {
                        SettingsSectionHeader(title = stringResource(R.string.language))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { appLanguageSheetOpen = true }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.language),
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = EncoreColors.TextPrimary
                                )
                            )
                            Text(
                                text = AppLocale.nativeName(appLanguage)
                                    ?: stringResource(R.string.language_system_default),
                                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green)
                            )
                        }
                        if (appLanguageSheetOpen) {
                            AppLanguageSheet(
                                selected = appLanguage,
                                onLanguageSelected = { tag ->
                                    if (tag != appLanguage) {
                                        AppLocale.set(context, tag)
                                        (context as? Activity)?.recreate()
                                    }
                                },
                                onDismiss = { appLanguageSheetOpen = false }
                            )
                        }
                    }

                }
                if (target == 1) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.player_controls))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { playerButtonsSheetOpen = true }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.notification_and_car_controls),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = EncoreColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.notification_and_car_controls_desc),
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
                        SettingsToggleRow(
                            title = stringResource(R.string.enable_android_auto),
                            subtitle = stringResource(R.string.enable_android_auto_desc),
                            isChecked = playerButtonsConfig.andAutoEnabled,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                val updated = playerButtonsConfig.copy(andAutoEnabled = enabled)
                                playerButtonsConfig = updated
                                PlayerButtonsStore.save(context, updated)
                                val intent = Intent().setClassName(context.packageName, "com.quackify.app.PlaybackService")
                                    .setAction("com.quackify.app.REFRESH")
                                runCatching { context.startService(intent) }
                            }
                        )

                        if (playerButtonsSheetOpen) {
                            PlayerButtonsConfigSheet(
                                initialConfig = playerButtonsConfig,
                                onSave = { updated ->
                                    playerButtonsConfig = updated
                                    PlayerButtonsStore.save(context, updated)
                                    val intent = Intent().setClassName(context.packageName, "com.quackify.app.PlaybackService")
                                        .setAction("com.quackify.app.REFRESH")
                                    runCatching { context.startService(intent) }
                                },
                                onDismiss = { playerButtonsSheetOpen = false }
                            )
                        }
                    }

                    item {
                        SettingsSectionHeader(title = stringResource(R.string.app_icon))
                        Text(
                            text = stringResource(R.string.pick_the_launcher_icon_it_can_take_a_moment_for),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsIconOption(
                                label = stringResource(R.string.default_option),
                                iconRes = R.mipmap.ic_launcher_default,
                                selected = appIcon == "default",
                                onClick = {
                                    appIcon = "default"
                                    clientPrefs.edit().putString("appIcon", "default").apply()
                                    onAppIconChange("default")
                                }
                            )
                            SettingsIconOption(
                                label = stringResource(R.string.normal),
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
                        SettingsSectionHeader(title = stringResource(R.string.account))
                        val detail = listOfNotNull(
                            product.takeIf { it.isNotBlank() },
                            country.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (detail.isNotBlank()) {
                            SettingsInfoRow(title = stringResource(R.string.plan), value = detail)
                        }
                        Text(
                            text = stringResource(R.string.log_out),
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
                        SettingsSectionHeader(title = stringResource(R.string.playback))
                        if (streamSourcesSheetOpen) {
                            StreamSourcesSheet(
                                orderedSources = streamSources,
                                onOrderSelected = { order ->
                                    streamSources = order
                                    StreamSources.save(context, order)
                                },
                                onDismiss = { streamSourcesSheetOpen = false }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { streamSourcesSheetOpen = true }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.stream_sources),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = EncoreColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.stream_sources_row_desc),
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                            Text(
                                text = streamSourcesLabel,
                                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green)
                            )
                        }
                        SettingsToggleRow(
                            title = stringResource(R.string.autoplay_on_startup),
                            subtitle = stringResource(R.string.when_you_open_the_app_the_loaded_track_starts_pl),
                            isChecked = autoplayOnStartup,
                            enabled = true,
                            onCheckedChange = { on ->
                                autoplayOnStartup = on
                                clientPrefs.edit().putBoolean("autoplayOnStartup", on).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.autoplay),
                            subtitle = stringResource(R.string.keep_playing_a_station_when_the_album_or_playlis),
                            isChecked = autoplayOnEnd,
                            enabled = true,
                            onCheckedChange = { on ->
                                autoplayOnEnd = on
                                clientPrefs.edit().putBoolean("autoplayOnEnd", on).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.gapless_playback),
                            subtitle = stringResource(R.string.prepares_the_next_track_while_the_current_one_pl),
                            isChecked = gapless,
                            enabled = true,
                            badge = stringResource(R.string.beta),
                            badgeInfo = stringResource(R.string.gapless_warning),
                            onCheckedChange = { on ->
                                gapless = on
                                clientPrefs.edit().putBoolean("gaplessPlayback", on).apply()
                            }
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.crossfade),
                            subtitle = stringResource(R.string.crossfade_subtitle),
                            value = crossfadeSeconds,
                            maxValue = 12,
                            valueLabel = if (crossfadeSeconds == 0) {
                                stringResource(R.string.crossfade_off)
                            } else {
                                stringResource(R.string.crossfade_seconds, crossfadeSeconds)
                            },
                            onValueChange = { seconds ->
                                crossfadeSeconds = seconds
                                clientPrefs.edit().putInt("crossfadeMs", seconds * 1000).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.automix),
                            subtitle = stringResource(R.string.automix_subtitle),
                            isChecked = automix,
                            enabled = true,
                            badge = stringResource(R.string.beta),
                            badgeInfo = stringResource(R.string.automix_warning),
                            onCheckedChange = { on ->
                                automix = on
                                clientPrefs.edit().putBoolean("automix", on).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.pause_on_disconnect),
                            subtitle = stringResource(R.string.pause_when_the_bluetooth_usb_or_wired_audio),
                            isChecked = pauseOnDisconnect,
                            enabled = true,
                            onCheckedChange = { on ->
                                pauseOnDisconnect = on
                                clientPrefs.edit().putBoolean("pauseOnDisconnect", on).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.resume_on_connect),
                            subtitle = stringResource(R.string.resume_when_a_bluetooth_audio_device_connect),
                            isChecked = resumeOnConnect,
                            enabled = true,
                            onCheckedChange = { on ->
                                resumeOnConnect = on
                                clientPrefs.edit().putBoolean("resumeOnConnect", on).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.resume_on_headphone_plug),
                            subtitle = stringResource(R.string.resume_when_wired_headphones_are_plugged_in),
                            isChecked = resumeOnHeadphone,
                            enabled = true,
                            onCheckedChange = { on ->
                                resumeOnHeadphone = on
                                clientPrefs.edit().putBoolean("resumeOnHeadphone", on).apply()
                            }
                        )
                    }

                    item {
                        SettingsSectionHeader(title = stringResource(R.string.about))
                        Text(
                            text = stringResource(R.string.quackify_is_a_native_android_music_client_built),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SettingsInfoRow(
                            title = stringResource(R.string.version),
                            value = version,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (!debugUnlocked) {
                                        versionTaps++
                                        if (versionTaps >= 5) {
                                            debugUnlocked = true
                                            clientPrefs.edit().putBoolean("debugUnlocked", true).apply()
                                            Toast.makeText(context, R.string.debug_unlocked, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onLongClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Version", version))
                                        Toast.makeText(context, R.string.version_copied, Toast.LENGTH_SHORT).show()
                                    }
                                    if (debugUnlocked || debugLogging) disableDebug()
                                }
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.latest_version),
                                    style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = when {
                                        updateChecking -> "Checking\u2026"
                                        updateError != null -> stringResource(R.string.update_check_failed)
                                        updateVersion != null -> stringResource(R.string.update_available)
                                        updateChecked -> stringResource(R.string.up_to_date)
                                        else -> "\u2014"
                                    },
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                            Text(
                                text = stringResource(R.string.check),
                                style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EncoreColors.Card)
                                    .clickable { onCheckUpdates() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AboutLinkButton(label = stringResource(R.string.github), iconRes = R.drawable.ic_github_24, url = GITHUB_URL)
                            AboutLinkButton(label = stringResource(R.string.discord), iconRes = R.drawable.ic_discord_24, url = DISCORD_URL)
                        }
                        Text(
                            text = stringResource(R.string.copyright),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }

                if (target == 3) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.logging))
                        SettingsToggleRow(
                            title = stringResource(R.string.log_debug_infos),
                            subtitle = stringResource(R.string.log_debug_infos_subtitle),
                            isChecked = debugLogging,
                            enabled = true,
                            onCheckedChange = { on ->
                                debugLogging = on
                                clientPrefs.edit().putBoolean("debugLogging", on).apply()
                                Logs.floor = if (on) Logs.Level.DEBUG else Logs.Level.INFO
                                if (on) {
                                    val logger = Logs.of(Logs.Module.MAIN)
                                    DeviceInfo.collect(context).lines().forEach { logger.i(it) }
                                }
                            }
                        )
                    }
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.tools))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EncoreColors.Card)
                                .clickable { onOpenLogcat() }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.open_logcat),
                                    style = EncoreTheme.typography.bodyMediumBold
                                )
                                Text(
                                    text = stringResource(R.string.open_logcat_subtitle),
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EncoreColors.Card)
                                .clickable { spDcDialogOpen = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // When a custom sp_dc is installed the subtitle
                                // says so — with the cookie's length in bytes,
                                // never its value — so the debug door keeps
                                // reminding the tinkerer what session they are
                                // actually running on.
                                val customSpDcLength = remember(spDcRevision) {
                                    EncoreAuth.customSpDcLength(context)
                                }
                                Text(
                                    text = stringResource(R.string.sign_in_with_sp_dc),
                                    style = EncoreTheme.typography.bodyMediumBold
                                )
                                Text(
                                    text = if (customSpDcLength > 0) {
                                        stringResource(R.string.sp_dc_installed_subtitle, customSpDcLength)
                                    } else {
                                        stringResource(R.string.sign_in_with_sp_dc_subtitle)
                                    },
                                    style = EncoreTheme.typography.bodySmall.copy(
                                        color = if (customSpDcLength > 0) {
                                            EncoreColors.Green
                                        } else {
                                            EncoreColors.TextSecondary
                                        }
                                    )
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
                }
            }
        }
    }

    val activeSpDc = remember(spDcRevision) {
        if (EncoreAuth.customSpDc(context)) EncoreAuth.spDc(context) else ""
    }
    SpDcSignInDialog(
        isOpen = spDcDialogOpen,
        initialSpDc = activeSpDc,
        onRemove = {
            spDcDialogOpen = false
            spDcRevision++
            onLogout()
        },
        onConfirm = { value ->
            spDcDialogOpen = false
            val spDc = value.trim().removePrefix("sp_dc=").substringBefore(";").trim()
            if (spDc.isNotBlank()) {
                scope.launch {
                    when (val outcome = EncoreAuth.fetchSpDcOutcome(context, spDc)) {
                        is EncoreAuth.SpDcOutcome.Ok -> {
                            val cookies = JSONObject()
                                .put("https://open.spotify.com", "sp_dc=$spDc")
                                .toString()
                            EncoreAuth.saveLogin(context, cookies, outcome.account, customSpDc = true)
                            Toast.makeText(
                                context,
                                context.getString(R.string.sp_dc_signed_in, outcome.account.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            onSpDcSignedIn()
                            spDcRevision++
                        }
                        // The server could not be reached to vouch for the
                        // cookie, so the user decides: keep it and let the
                        // profile fill in later, or drop it and try again
                        // once the network is back.
                        EncoreAuth.SpDcOutcome.FetchFailed -> spDcUnchecked = spDc
                        EncoreAuth.SpDcOutcome.Rejected ->
                            Toast.makeText(context, R.string.sp_dc_rejected, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        onDismiss = { spDcDialogOpen = false }
    )

    SpDcUncheckedConfirmDialog(
        spDc = spDcUnchecked,
        onConfirm = {
            val spDc = spDcUnchecked
            spDcUnchecked = null
            if (spDc.isNullOrBlank()) return@SpDcUncheckedConfirmDialog
            scope.launch {
                val cookies = JSONObject()
                    .put("https://open.spotify.com", "sp_dc=$spDc")
                    .toString()
                // The profile endpoints never answered, so there is nothing
                // to show yet; the account resolves and persists itself the
                // first time the app talks to Spotify with this session.
                EncoreAuth.saveLogin(
                    context,
                    cookies,
                    EncoreAuth.Account(name = context.getString(R.string.sp_dc_pending_name)),
                    customSpDc = true
                )
                Toast.makeText(context, R.string.sp_dc_signed_in_unchecked, Toast.LENGTH_SHORT).show()
                onSpDcSignedIn()
                spDcRevision++
            }
        },
        onDismiss = { spDcUnchecked = null }
    )
}

/**
 * The sp_dc could not be verified because the check itself failed — offline,
 * timeout, server hiccup. Presented in the same cinematic language as the
 * sp_dc entry dialog it follows: the gradient canvas, the centered message,
 * and two pill buttons — "keep it anyway" in the signature green, cancel
 * quiet in white. The cookie is only as good as the user's confidence, so
 * the choice belongs to them, not to a silent failure toast.
 */
@Composable
private fun SpDcUncheckedConfirmDialog(
    spDc: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (spDc.isNullOrBlank()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF5A5A5A),
                            Color(0xFF242424),
                            Color(0xFF121212)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // block outside clicks
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.sp_dc_check_failed_title),
                    style = EncoreTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.sp_dc_check_failed_body),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(EncoreColors.Green)
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.sp_dc_keep_anyway),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpDcSignInDialog(
    isOpen: Boolean,
    initialSpDc: String,
    onRemove: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var textFieldValue by remember(initialSpDc) {
        mutableStateOf(
            TextFieldValue(
                text = initialSpDc,
                selection = TextRange(0, initialSpDc.length)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF5A5A5A),
                            Color(0xFF242424),
                            Color(0xFF121212)
                        )
                    )
                )
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.sign_in_with_sp_dc),
                    style = EncoreTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = EncoreTheme.typography.bodyMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val trimmed = textFieldValue.text.trim()
                                if (trimmed.isNotBlank()) {
                                    onConfirm(trimmed)
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.45f))
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                val canConfirm = textFieldValue.text.trim().isNotBlank()

                if (initialSpDc.isNotBlank()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpDcDialogPill(
                                label = stringResource(R.string.cancel),
                                background = Color.Transparent,
                                contentColor = Color.White,
                                onClick = onDismiss
                            )
                            SpDcDialogPill(
                                label = stringResource(R.string.remove_custom_sp_dc),
                                background = EncoreColors.Error,
                                contentColor = Color.White,
                                onClick = onRemove
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SpDcDialogPill(
                            label = stringResource(R.string.sign_in),
                            background = if (canConfirm) EncoreColors.Green else EncoreColors.Green.copy(alpha = 0.4f),
                            contentColor = Color.Black,
                            enabled = canConfirm,
                            onClick = { onConfirm(textFieldValue.text.trim()) }
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpDcDialogPill(
                            label = stringResource(R.string.cancel),
                            background = Color.Transparent,
                            contentColor = Color.White,
                            onClick = onDismiss
                        )
                        SpDcDialogPill(
                            label = stringResource(R.string.sign_in),
                            background = if (canConfirm) EncoreColors.Green else EncoreColors.Green.copy(alpha = 0.4f),
                            contentColor = Color.Black,
                            enabled = canConfirm,
                            onClick = { onConfirm(textFieldValue.text.trim()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpDcDialogPill(
    label: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        )
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
                text = account?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_signed_in),
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
private fun SettingsSliderRow(
    title: String,
    subtitle: String,
    value: Int,
    maxValue: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = EncoreColors.TextPrimary
                )
            )
            Text(
                text = valueLabel,
                style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
            )
        }
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, maxValue)) },
            valueRange = 0f..maxValue.toFloat(),
            steps = maxValue - 1,
            colors = SliderDefaults.colors(
                thumbColor = EncoreColors.Green,
                activeTrackColor = EncoreColors.Green,
                inactiveTrackColor = EncoreColors.Card
            )
        )
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsCategoryRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
