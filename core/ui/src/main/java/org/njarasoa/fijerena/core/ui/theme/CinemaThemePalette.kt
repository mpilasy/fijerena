package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Defines all themeable color properties.
 * Each predefined palette provides a complete set of colors.
 */
@Immutable
data class CinemaThemePalette(
    val id: String,
    val displayName: String,
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
    // Status (constant across themes)
    val success: Color = Color(0xFF4CAF50),
    val warning: Color = Color(0xFFFFC107),
    val error: Color = Color(0xFFF44336),
    val live: Color = Color(0xFFFF6D00),
    // Text (constant across themes)
    val textPrimary: Color = Color(0xFFFFFFFF),
    val textSecondary: Color = Color(0xFFB0B0B0),
    val textTertiary: Color = Color(0xFF808080),
    val textDisabled: Color = Color(0xFF606060),
)

// --- Predefined Palettes ---

val DeepNightPalette =
    CinemaThemePalette(
        id = "deep_night",
        displayName = "Deep Night",
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
    )

val AmoledBlackPalette =
    CinemaThemePalette(
        id = "amoled_black",
        displayName = "AMOLED Black",
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
    )

val AmethystPalette =
    CinemaThemePalette(
        id = "amethyst",
        displayName = "Amethyst",
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
    )

val TealPalette =
    CinemaThemePalette(
        id = "teal",
        displayName = "Teal",
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
    )

// --- All palettes ---

val AllPalettes: List<CinemaThemePalette> =
    listOf(
        DeepNightPalette,
        AmoledBlackPalette,
        AmethystPalette,
        TealPalette,
    )

fun paletteById(id: String): CinemaThemePalette = AllPalettes.firstOrNull { it.id == id } ?: DeepNightPalette

// --- Global holder for non-composable and composable access ---

object CinemaThemeHolder {
    var current: CinemaThemePalette by mutableStateOf(DeepNightPalette)
}

// --- CompositionLocal for composable access ---

val LocalCinemaTheme = staticCompositionLocalOf { DeepNightPalette }
