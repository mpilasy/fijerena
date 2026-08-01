@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import org.njarasoa.fijerena.R
import org.njarasoa.fijerena.core.ui.theme.UiTypeTokens
import org.njarasoa.fijerena.core.ui.theme.applyUiTypeTokens

/**
 * Display font for headers only (display and headline styles) — a variable font, sampled at
 * each weight via [FontVariation.Settings]. Body/title/label text keeps [FontFamily.Default]
 * (Roboto) for guaranteed 10-ft legibility.
 */
private val CinemaDisplayFontFamily =
    FontFamily(
        Font(R.font.manrope_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.manrope_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

/**
 * Full 13-style typography scale optimized for 10-foot TV viewing.
 * All body text 18sp minimum, headers 32sp+.
 * Font: Manrope for display/headline styles, Roboto (system default) for title/body/label.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
private val BaseTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 48.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                lineHeight = 42.sp,
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.5.sp,
            ),
    )

/** [BaseTypography] with the active [UiTypeTokens] weight/tracking character applied. */
@OptIn(ExperimentalTvMaterial3Api::class)
fun cinemaTypography(tokens: UiTypeTokens): Typography {
    fun TextStyle.display() = applyUiTypeTokens(tokens, CinemaDisplayFontFamily)
    fun TextStyle.body() = applyUiTypeTokens(tokens, FontFamily.Default)
    return BaseTypography.copy(
        displayLarge = BaseTypography.displayLarge.display(),
        displayMedium = BaseTypography.displayMedium.display(),
        displaySmall = BaseTypography.displaySmall.display(),
        headlineLarge = BaseTypography.headlineLarge.display(),
        headlineMedium = BaseTypography.headlineMedium.display(),
        headlineSmall = BaseTypography.headlineSmall.display(),
        titleLarge = BaseTypography.titleLarge.body(),
        titleMedium = BaseTypography.titleMedium.body(),
        titleSmall = BaseTypography.titleSmall.body(),
        bodyLarge = BaseTypography.bodyLarge.body(),
        bodyMedium = BaseTypography.bodyMedium.body(),
        bodySmall = BaseTypography.bodySmall.body(),
        labelLarge = BaseTypography.labelLarge.body(),
        labelMedium = BaseTypography.labelMedium.body(),
        labelSmall = BaseTypography.labelSmall.body(),
    )
}
