package com.quackify.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quackify.compose.R

/**
 */
val EncoreMixFontFamily = FontFamily(
    Font(R.font.mix_ui_regular, FontWeight.Normal),
    Font(R.font.mix_ui_bold, FontWeight.Bold)
)

val EncoreMixTitleFontFamily = FontFamily(
    Font(R.font.mix_ui_title_bold, FontWeight.Bold),
    Font(R.font.mix_ui_title_extrabold, FontWeight.ExtraBold)
)

/**
 * - HeadlineLarge: 48sp Extrabold (encore_text_size_larger_5)
 * - HeadlineMedium: 40sp Bold (encore_text_size_larger_4)
 * - TitleLarge: 32sp Bold (encore_text_size_larger_3)
 * - TitleMedium: 24sp Bold (encore_text_size_larger_2)
 * - TitleSmall: 20sp Bold (encore_text_size_larger)
 * - BodyMedium: 16sp Regular (encore_text_size_base)
 * - BodyMediumBold: 16sp Bold (encore_text_size_base)
 * - BodySmall: 13sp Regular (encore_text_size_smaller)
 * - BodySmallBold: 13sp Bold (encore_text_size_smaller)
 * - Marginal: 11sp Regular (encore_text_size_smaller_2)
 * - MarginalBold: 11sp Bold (encore_text_size_smaller_2)
 * - TagLabel: 10sp Bold Uppercase
 */
@Immutable
data class EncoreTypography(
    val ink: Color = EncoreColors.TextPrimary,
    val inkSecondary: Color = EncoreColors.TextSecondary,
    val headlineLarge: TextStyle = TextStyle(
        fontFamily = EncoreMixTitleFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        color = ink
    ),
    val headlineMedium: TextStyle = TextStyle(
        fontFamily = EncoreMixTitleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        color = ink
    ),
    val titleLarge: TextStyle = TextStyle(
        fontFamily = EncoreMixTitleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        color = ink
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = EncoreMixTitleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = ink
    ),
    val titleSmall: TextStyle = TextStyle(
        fontFamily = EncoreMixTitleFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = ink
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = EncoreMixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = ink
    ),
    val bodyMediumBold: TextStyle = TextStyle(
        fontFamily = EncoreMixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = ink
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = EncoreMixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = inkSecondary
    ),
    val bodySmallBold: TextStyle = TextStyle(
        fontFamily = EncoreMixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = ink
    ),
    val marginal: TextStyle = TextStyle(
        fontFamily = EncoreMixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = inkSecondary
    ),
    val marginalBold: TextStyle = TextStyle(
        fontFamily = EncoreMixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = ink
    ),
    val tagLabel: TextStyle = TextStyle(
        fontFamily = EncoreMixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
        color = ink
    )
)
