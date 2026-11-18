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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.library.lyrics.TRANSLATION_LANGUAGES
import com.quackify.backend.library.lyrics.translationLanguageName
import com.quackify.compose.R
import com.quackify.compose.player.TRANSLATION_DEVICE_LANGUAGE
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationLanguageSheet(
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val deviceLanguageName = remember(appLocale) {
        appLocale.displayLanguage.takeIf { it.isNotBlank() && !it.equals(appLocale.language, ignoreCase = true) }
            ?: appLocale.getDisplayLanguage(appLocale)
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
                text = stringResource(R.string.lyrics_translation_language),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LanguageRow(
                label = stringResource(R.string.lyrics_translation_device_language, deviceLanguageName),
                isSelected = selectedCode == TRANSLATION_DEVICE_LANGUAGE,
                onClick = {
                    onLanguageSelected(TRANSLATION_DEVICE_LANGUAGE)
                    onDismiss()
                }
            )

            TRANSLATION_LANGUAGES.forEach { language ->
                LanguageRow(
                    label = translationLanguageName(language.code, appLocale),
                    isSelected = selectedCode == language.code,
                    onClick = {
                        onLanguageSelected(language.code)
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) EncoreColors.Green else EncoreColors.TextPrimary
            )
        )
        if (isSelected) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_check_24),
                contentDescription = stringResource(R.string.selected),
                tint = EncoreColors.Green,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
