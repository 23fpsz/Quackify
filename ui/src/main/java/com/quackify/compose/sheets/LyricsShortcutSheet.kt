package com.quackify.compose.sheets

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.library.lyrics.LyricsProvider
import com.quackify.backend.library.lyrics.translationLanguageName
import com.quackify.compose.R
import com.quackify.compose.player.LyricsOptions
import com.quackify.compose.player.LYRICS_OFFSET_TENTHS_MAX
import com.quackify.compose.player.LYRICS_OFFSET_TENTHS_MIN
import com.quackify.compose.player.TRANSLATION_DEVICE_LANGUAGE
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import java.util.Locale

/**
 * Lyrics shortcuts while reading them fullscreen: the share action that used
 * to live on the top bar, plus every lyrics setting the reader might want to
 * flip without leaving the screen. Each entry applies immediately through
 * [LyricsOptions]; changing a translation or source setting re-fetches the
 * current track's lyrics on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsShortcutSheet(
    onDismiss: () -> Unit,
    onShareClick: () -> Unit,
    onOpenTranslationLanguage: () -> Unit,
    onOpenLyricsSources: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val translationEnabled = LyricsOptions.translationEnabled
    val translationLanguage = LyricsOptions.translationLanguage
    val prioritizeWordSynced = LyricsOptions.prioritizeWordSynced
    val providerOrder = LyricsOptions.providerOrder
    val keepScreenOn = LyricsOptions.keepScreenOn
    val offsetTenths = LyricsOptions.offsetTenths
    val offsetLabel = remember(offsetTenths) {
        String.format(Locale.US, "%+.1f s", offsetTenths / 10f)
    }
    val deviceLanguageName = remember(appLocale) {
        appLocale.displayLanguage.takeIf { it.isNotBlank() && !it.equals(appLocale.language, ignoreCase = true) }
            ?: appLocale.getDisplayLanguage(appLocale)
    }
    val deviceLanguageTemplate = stringResource(R.string.lyrics_translation_device_language, deviceLanguageName)
    val translationLanguageLabel = if (translationLanguage == TRANSLATION_DEVICE_LANGUAGE) {
        deviceLanguageTemplate
    } else {
        remember(translationLanguage, appLocale) {
            translationLanguageName(translationLanguage, appLocale)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.lyrics_menu),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ShortcutActionRow(
                title = stringResource(R.string.share_lyrics),
                subtitle = stringResource(R.string.share_lyrics_desc),
                iconRes = R.drawable.encore_icon_share_android_24,
                onClick = {
                    onDismiss()
                    onShareClick()
                }
            )

            ShortcutToggleRow(
                title = stringResource(R.string.lyrics_translation),
                subtitle = stringResource(R.string.lyrics_translation_desc),
                isChecked = translationEnabled,
                onCheckedChange = { on ->
                    onDismiss()
                    LyricsOptions.setTranslationEnabled(context, on)
                }
            )

            ShortcutValueRow(
                title = stringResource(R.string.lyrics_translation_language),
                value = translationLanguageLabel,
                enabled = translationEnabled,
                iconRes = R.drawable.encore_icon_globe_24,
                onClick = {
                    onDismiss()
                    onOpenTranslationLanguage()
                }
            )

            ShortcutToggleRow(
                title = stringResource(R.string.lyrics_prioritize_word_synced),
                subtitle = stringResource(R.string.lyrics_prioritize_word_synced_desc),
                isChecked = prioritizeWordSynced,
                onCheckedChange = { on ->
                    onDismiss()
                    LyricsOptions.setPrioritizeWordSynced(context, on)
                }
            )

            ShortcutToggleRow(
                title = stringResource(R.string.lyrics_keep_screen_on),
                subtitle = stringResource(R.string.lyrics_keep_screen_on_desc),
                isChecked = keepScreenOn,
                onCheckedChange = { on ->
                    onDismiss()
                    LyricsOptions.setKeepScreenOn(context, on)
                }
            )

            ShortcutOffsetRow(
                offsetTenths = offsetTenths,
                offsetLabel = offsetLabel,
                onOffsetChange = { tenths ->
                    LyricsOptions.setOffsetTenths(context, tenths)
                }
            )

            ShortcutValueRow(
                title = stringResource(R.string.lyrics_source),
                value = providerOrder.firstOrNull()?.label
                    ?: LyricsProvider.offered.firstOrNull()?.label.orEmpty(),
                iconRes = R.drawable.encore_icon_check_24,
                onClick = {
                    onDismiss()
                    onOpenLyricsSources()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShortcutActionRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = EncoreColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = EncoreColors.TextPrimary
                )
            )
            Text(
                text = subtitle,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
    }
}

@Composable
private fun ShortcutToggleRow(
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
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = EncoreColors.TextPrimary
                )
            )
            Text(
                text = subtitle,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
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

/**
 * The lyrics offset, adjustable without leaving the reader: positive means
 * the lyrics feel late, so the sweep is shifted earlier by that much.
 * Applies live — the open reader retimes on the next frame.
 */
@Composable
private fun ShortcutOffsetRow(
    offsetTenths: Int,
    offsetLabel: String,
    onOffsetChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lyrics_offset),
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = EncoreColors.TextPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = offsetLabel,
                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green)
            )
        }
        Text(
            text = stringResource(R.string.lyrics_offset_desc),
            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
        )
        Slider(
            value = (offsetTenths + LYRICS_OFFSET_TENTHS_MAX).toFloat(),
            onValueChange = { raw ->
                onOffsetChange((raw.toInt() - LYRICS_OFFSET_TENTHS_MAX).coerceIn(
                    LYRICS_OFFSET_TENTHS_MIN,
                    LYRICS_OFFSET_TENTHS_MAX,
                ))
            },
            valueRange = 0f..(LYRICS_OFFSET_TENTHS_MAX * 2).toFloat(),
            steps = LYRICS_OFFSET_TENTHS_MAX * 2 - 1,
            colors = SliderDefaults.colors(
                thumbColor = EncoreColors.Green,
                activeTrackColor = EncoreColors.Green,
                inactiveTrackColor = EncoreColors.Card
            )
        )
    }
}

@Composable
private fun ShortcutValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    iconRes: Int,
    onClick: () -> Unit
) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = if (enabled) EncoreColors.TextPrimary else EncoreColors.TextSecondary
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = EncoreTheme.typography.bodySmallBold.copy(
                color = if (enabled) EncoreColors.Green else EncoreColors.TextSecondary
            )
        )
    }
}
