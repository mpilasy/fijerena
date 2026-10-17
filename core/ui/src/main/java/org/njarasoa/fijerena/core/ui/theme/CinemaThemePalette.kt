package org.njarasoa.fijerena.core.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.njarasoa.fijerena.core.ui.R

@Immutable
data class CinemaThemePalette(
    val id: String,
    /** The name shown in Settings, localised (U-M4). */
    @param:StringRes val nameRes: Int,
    /** A light theme: dark text on light surfaces, dark system-bar icons. */
    val isLight: Boolean,
    // Primary accent
    val accent: Color,
    val accentDark: Color,
    val accentLight: Color,
    // Secondary accent
    val orange: Color,
    val orangeDark: Color,
    val orangeLight: Color,
    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceLight: Color,
    // Glassmorphism
    val glassBackground: Color,
    val glassBorder: Color,
    // Status — every theme states its own, so a light theme can darken them
    val success: Color,
    val warning: Color,
    val error: Color,
    val live: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    /** Dims what is behind a dialog or over a picture. */
    val scrim: Color,
) {
    /**
     * Text and icons on the accent (filled buttons, the selected tab): white on a dark accent,
     * black on a light one — AMOLED Black's light grey accent left white button text unreadable.
     */
    val onAccent: Color get() = if (accent.luminance() > 0.5f) Color.Black else Color.White
}

// --- Predefined Palettes ---

val DeepNightPalette =
    CinemaThemePalette(
        id = "deep_night",
        nameRes = R.string.theme_deep_night,
        isLight = false,
        accent = Color(0xFF2979FF),
        accentDark = Color(0xFF1565C0),
        accentLight = Color(0xFF82B1FF),
        orange = Color(0xFFFF6D00),
        orangeDark = Color(0xFFE65100),
        orangeLight = Color(0xFFFFAB40),
        background = Color(0xFF0F1014),
        surface = Color(0xFF161A20),
        surfaceVariant = Color(0xFF1E2228),
        surfaceLight = Color(0xFF2A3038),
        glassBackground = Color(0xBF0F1014),
        glassBorder = Color(0x142979FF),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB0B0B0),
        textTertiary = Color(0xFF808080),
        textDisabled = Color(0xFF606060),
        scrim = Color(0x80000000),
    )

val AmoledBlackPalette =
    CinemaThemePalette(
        id = "amoled_black",
        nameRes = R.string.theme_pure_black,
        isLight = false,
        accent = Color(0xFFE0E0E0),
        accentDark = Color(0xFFB0B0B0),
        accentLight = Color(0xFFFFFFFF),
        orange = Color(0xFFFF6D00),
        orangeDark = Color(0xFFE65100),
        orangeLight = Color(0xFFFFAB40),
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0A),
        surfaceVariant = Color(0xFF121212),
        surfaceLight = Color(0xFF1A1A1A),
        glassBackground = Color(0xBF000000),
        glassBorder = Color(0x14E0E0E0),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB0B0B0),
        textTertiary = Color(0xFF808080),
        textDisabled = Color(0xFF606060),
        scrim = Color(0x80000000),
    )

val AmethystPalette =
    CinemaThemePalette(
        id = "amethyst",
        nameRes = R.string.theme_amethyst,
        isLight = false,
        accent = Color(0xFF9C6BFF),
        accentDark = Color(0xFF6A3FD6),
        accentLight = Color(0xFFC7A8FF),
        orange = Color(0xFFFF6D00),
        orangeDark = Color(0xFFE65100),
        orangeLight = Color(0xFFFFAB40),
        background = Color(0xFF0F1014),
        surface = Color(0xFF161A20),
        surfaceVariant = Color(0xFF1E2228),
        surfaceLight = Color(0xFF2A3038),
        glassBackground = Color(0xBF0F1014),
        glassBorder = Color(0x149C6BFF),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB0B0B0),
        textTertiary = Color(0xFF808080),
        textDisabled = Color(0xFF606060),
        scrim = Color(0x80000000),
    )

val TealPalette =
    CinemaThemePalette(
        id = "teal",
        nameRes = R.string.theme_teal,
        isLight = false,
        accent = Color(0xFF26C6DA),
        accentDark = Color(0xFF00838F),
        accentLight = Color(0xFF80DEEA),
        orange = Color(0xFFFF6D00),
        orangeDark = Color(0xFFE65100),
        orangeLight = Color(0xFFFFAB40),
        background = Color(0xFF0F1014),
        surface = Color(0xFF161A20),
        surfaceVariant = Color(0xFF1E2228),
        surfaceLight = Color(0xFF2A3038),
        glassBackground = Color(0xBF0F1014),
        glassBorder = Color(0x1426C6DA),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB0B0B0),
        textTertiary = Color(0xFF808080),
        textDisabled = Color(0xFF606060),
        scrim = Color(0x80000000),
    )

val ForestPalette =
    CinemaThemePalette(
        id = "forest",
        nameRes = R.string.theme_forest,
        isLight = false,
        accent = Color(0xFF2EBD85),
        accentDark = Color(0xFF1E8A5F),
        accentLight = Color(0xFF7FE0B8),
        orange = Color(0xFFFF6D00),
        orangeDark = Color(0xFFE65100),
        orangeLight = Color(0xFFFFAB40),
        background = Color(0xFF0E1311),
        surface = Color(0xFF141B18),
        surfaceVariant = Color(0xFF1B2420),
        surfaceLight = Color(0xFF26322D),
        glassBackground = Color(0xBF0E1311),
        glassBorder = Color(0x142EBD85),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB0B8B4),
        textTertiary = Color(0xFF808A85),
        textDisabled = Color(0xFF5E6762),
        scrim = Color(0x80000000),
    )

/**
 * The light theme: dark text on near-white surfaces. Status colours are darker than the dark
 * themes' so they read on white; [surfaceLight] (focus and press) is a step darker than
 * [surfaceVariant], since on a light theme lifting toward the viewer reads as more contrast.
 */
val DaylightPalette =
    CinemaThemePalette(
        id = "daylight",
        nameRes = R.string.theme_daylight,
        isLight = true,
        accent = Color(0xFF1565C0),
        accentDark = Color(0xFF0D47A1),
        // On white the soft accent has to be the darker blue: it marks the current row, the selected tab and
        // a focused icon's label as text.
        accentLight = Color(0xFF0D47A1),
        orange = Color(0xFFE65100),
        orangeDark = Color(0xFFBF360C),
        orangeLight = Color(0xFFFF8F00),
        background = Color(0xFFF4F6F9),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFECEFF3),
        surfaceLight = Color(0xFFDDE2E9),
        glassBackground = Color(0xE6FFFFFF),
        glassBorder = Color(0x1F1565C0),
        success = Color(0xFF2E7D32),
        warning = Color(0xFFB26A00),
        error = Color(0xFFC62828),
        live = Color(0xFFD84315),
        textPrimary = Color(0xFF14171C),
        textSecondary = Color(0xFF4A5260),
        textTertiary = Color(0xFF6B7380),
        textDisabled = Color(0xFFA0A6B0),
        scrim = Color(0x66000000),
    )

val AllPalettes: List<CinemaThemePalette> =
    listOf(
        DeepNightPalette,
        AmoledBlackPalette,
        AmethystPalette,
        TealPalette,
        ForestPalette,
        DaylightPalette,
    )

fun paletteById(id: String): CinemaThemePalette = AllPalettes.firstOrNull { it.id == id } ?: DeepNightPalette

// --- Global holder for non-composable and composable access ---

object CinemaThemeHolder {
    var current: CinemaThemePalette by mutableStateOf(DeepNightPalette)
}

val LocalCinemaTheme = staticCompositionLocalOf { DeepNightPalette }
