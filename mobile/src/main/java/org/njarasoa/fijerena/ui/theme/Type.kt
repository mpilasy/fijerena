@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package org.njarasoa.fijerena.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.njarasoa.fijerena.R
import org.njarasoa.fijerena.core.ui.theme.UiTypeTokens
import org.njarasoa.fijerena.core.ui.theme.applyUiLabelTokens
import org.njarasoa.fijerena.core.ui.theme.applyUiTitleTokens
import org.njarasoa.fijerena.core.ui.theme.applyUiTypeTokens

/**
 * Display font for headers only (display and headline styles) — a variable font, sampled at
 * each weight via [FontVariation.Settings]. Body/title/label text keeps [FontFamily.Default]
 * (Roboto) for consistency with Material Design.
 */
private val CinemaDisplayFontFamily =
    FontFamily(
        Font(R.font.manrope_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.manrope_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

/**
 * Optimized for mobile viewing distance (arms-length, ~16-20 inches)
 * Manrope for display/headline styles, Roboto (system default) for title/body/label.
 */
private val BaseTypography =
    Typography(
        // Display styles - Hero text, large headings
        displayLarge =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        // Headline styles - Section headers
        headlineLarge =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = CinemaDisplayFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        // Title styles - Component headers, card titles
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        // Body styles - Main content text
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        // Label styles - Buttons, tabs, metadata
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

/** [BaseTypography] with the active [UiTypeTokens] weight/tracking character applied. */
fun cinemaTypography(tokens: UiTypeTokens): Typography {
    fun TextStyle.display() = applyUiTypeTokens(tokens, CinemaDisplayFontFamily)

    fun TextStyle.body() = applyUiTypeTokens(tokens, FontFamily.Default)

    fun TextStyle.label() = applyUiLabelTokens(tokens, FontFamily.Default)

    // Titles and headings: the Look's title face when it has one (Modern, Cinema…); the other Looks
    // keep their display/body treatment unchanged.
    val ownTitles = tokens.titleFontFamily != null || tokens.titleWeight != null

    fun TextStyle.displayTitle() = if (ownTitles) applyUiTitleTokens(tokens, CinemaDisplayFontFamily) else display()

    fun TextStyle.bodyTitle() = if (ownTitles) applyUiTitleTokens(tokens, CinemaDisplayFontFamily) else body()
    return BaseTypography.copy(
        displayLarge = BaseTypography.displayLarge.displayTitle(),
        displayMedium = BaseTypography.displayMedium.displayTitle(),
        displaySmall = BaseTypography.displaySmall.displayTitle(),
        headlineLarge = BaseTypography.headlineLarge.displayTitle(),
        headlineMedium = BaseTypography.headlineMedium.displayTitle(),
        headlineSmall = BaseTypography.headlineSmall.displayTitle(),
        titleLarge = BaseTypography.titleLarge.bodyTitle(),
        titleMedium = BaseTypography.titleMedium.bodyTitle(),
        titleSmall = BaseTypography.titleSmall.body(),
        bodyLarge = BaseTypography.bodyLarge.body(),
        bodyMedium = BaseTypography.bodyMedium.body(),
        bodySmall = BaseTypography.bodySmall.body(),
        labelLarge = BaseTypography.labelLarge.label(),
        labelMedium = BaseTypography.labelMedium.label(),
        labelSmall = BaseTypography.labelSmall.label(),
    )
}
