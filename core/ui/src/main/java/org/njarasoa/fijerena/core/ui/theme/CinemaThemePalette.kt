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
    /** Offered only to an Admin profile: kept for testing, not for viewing. */
    val adminOnly: Boolean = false,
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
    /**
     * A ground with depth (the 2026-10-17 themes): a soft glow of this colour from the top right,
     * over [background] fading to [groundEnd] corner to corner. Null: the plain ground.
     */
    val groundGlow: Color? = null,
    val groundEnd: Color? = null,
    /** A painted or flat ground in place of the wash and the glow (null: those). */
    val scenery: Scenery? = null,
    /**
     * The fill of a focused TV button, tab or chip (null: [textPrimary], the white focus of the
     * 2026-10-17 refresh). Console's is a pale cream.
     */
    val focusFill: Color? = null,
    /** Text and icons on the accent where the luminance rule below picks badly (Console's mid-tone orange takes black). */
    val onAccentOverride: Color? = null,
    /** The fill of a tag badge (`BadgeKind.Neutral` and `OnCard`); null: the grey of the surfaces. */
    val badgeFill: Color? = null,
) {
    /**
     * Text and icons on the accent (filled buttons, the selected tab): white on a dark accent,
     * black on a light one — AMOLED Black's light grey accent left white button text unreadable.
     */
    val onAccent: Color get() = onAccentOverride ?: if (accent.luminance() > 0.5f) Color.Black else Color.White
}

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

/** Plum into black, violet accent: its own ground since 2026-10-19 (was Deep night's). */
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
        background = Color(0xFF140E1F),
        surface = Color(0xFF1C1430),
        surfaceVariant = Color(0xFF251B3D),
        surfaceLight = Color(0xFF33274F),
        glassBackground = Color(0xBF140E1F),
        glassBorder = Color(0x149C6BFF),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFF1EBFF),
        textSecondary = Color(0xFFB6A8D6),
        textTertiary = Color(0xFF85789F),
        textDisabled = Color(0xFF5C5275),
        scrim = Color(0x80000000),
        groundGlow = Color(0xFF2E1B52),
        groundEnd = Color(0xFF08060D),
    )

/** Black and dark greys with a bright green accent: a music-app look (until 2026-11-01, a green-tinted ground with a jade accent). */
val ForestPalette =
    CinemaThemePalette(
        id = "forest",
        nameRes = R.string.theme_forest,
        isLight = false,
        accent = Color(0xFF1DB954),
        accentDark = Color(0xFF1AA34A),
        accentLight = Color(0xFF1ED760),
        orange = Color(0xFFF59B23),
        orangeDark = Color(0xFFD97E0E),
        orangeLight = Color(0xFFFFC266),
        background = Color(0xFF121212),
        surface = Color(0xFF181818),
        surfaceVariant = Color(0xFF282828),
        surfaceLight = Color(0xFF3E3E3E),
        glassBackground = Color(0xBF121212),
        glassBorder = Color(0x141DB954),
        success = Color(0xFF1DB954),
        warning = Color(0xFFFFA42B),
        error = Color(0xFFE91429),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3B3B3),
        textTertiary = Color(0xFF7A7A7A),
        textDisabled = Color(0xFF535353),
        scrim = Color(0x99000000),
        // The luminance rule gives white, which does not read on this green.
        onAccentOverride = Color(0xFF000000),
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
        // A streaming app is watched in dim rooms and its art is made for dark: kept for admins.
        adminOnly = true,
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

/** Navy into black, ice-blue accent (the tv visual refresh plan). */
val MidnightPalette =
    CinemaThemePalette(
        id = "midnight",
        nameRes = R.string.theme_midnight,
        isLight = false,
        accent = Color(0xFF8FD3FF),
        accentDark = Color(0xFF4FA8E0),
        accentLight = Color(0xFFC4E9FF),
        orange = Color(0xFFFF8A65),
        orangeDark = Color(0xFFE64A19),
        orangeLight = Color(0xFFFFAB91),
        background = Color(0xFF0A1020),
        surface = Color(0xFF0F1830),
        surfaceVariant = Color(0xFF16213D),
        surfaceLight = Color(0xFF22304F),
        glassBackground = Color(0xBF0A1020),
        glassBorder = Color(0x148FD3FF),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFEAF0FF),
        textSecondary = Color(0xFF9FB0D0),
        textTertiary = Color(0xFF6E7FA0),
        textDisabled = Color(0xFF4F5C78),
        scrim = Color(0x80000000),
        groundGlow = Color(0xFF1D2F5C),
        groundEnd = Color(0xFF05070D),
    )

/** Warm charcoal, amber accent. */
val EmberPalette =
    CinemaThemePalette(
        id = "ember",
        nameRes = R.string.theme_ember,
        isLight = false,
        accent = Color(0xFFFFB547),
        accentDark = Color(0xFFD98A1C),
        accentLight = Color(0xFFFFD38A),
        orange = Color(0xFFFF6E40),
        orangeDark = Color(0xFFDD2C00),
        orangeLight = Color(0xFFFF9E80),
        background = Color(0xFF141010),
        surface = Color(0xFF1C1615),
        surfaceVariant = Color(0xFF261E1C),
        surfaceLight = Color(0xFF352A27),
        glassBackground = Color(0xBF141010),
        glassBorder = Color(0x14FFB547),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF5A36),
        textPrimary = Color(0xFFF6EFE8),
        textSecondary = Color(0xFFBFAE9F),
        textTertiary = Color(0xFF8C7D70),
        textDisabled = Color(0xFF62574E),
        scrim = Color(0x80000000),
        groundGlow = Color(0xFF3B2414),
        groundEnd = Color(0xFF0B0909),
    )

/** Near-black with a neon green accent: a streaming-service look (until 2026-11-01, deep teal fading to violet with a mint accent). */
val AuroraPalette =
    CinemaThemePalette(
        id = "aurora",
        nameRes = R.string.theme_aurora,
        isLight = false,
        accent = Color(0xFF1CE783),
        accentDark = Color(0xFF15B868),
        accentLight = Color(0xFF6CF2B0),
        orange = Color(0xFFFFB547),
        orangeDark = Color(0xFFE0922A),
        orangeLight = Color(0xFFFFD08A),
        background = Color(0xFF0B0C0F),
        surface = Color(0xFF16181D),
        surfaceVariant = Color(0xFF1F2228),
        surfaceLight = Color(0xFF2A2E36),
        glassBackground = Color(0xBF0B0C0F),
        glassBorder = Color(0x141CE783),
        success = Color(0xFF1CE783),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA7ADB8),
        textTertiary = Color(0xFF7C828C),
        textDisabled = Color(0xFF545862),
        scrim = Color(0x99000000),
    )

/** Neutral greys, lime accent (electric blue until 2026-10-19, too close to Deep night's). */
val GraphitePalette =
    CinemaThemePalette(
        id = "graphite",
        nameRes = R.string.theme_graphite,
        isLight = false,
        accent = Color(0xFFC6F135),
        accentDark = Color(0xFF94BC12),
        accentLight = Color(0xFFE2FA91),
        orange = Color(0xFFFF6D00),
        orangeDark = Color(0xFFE65100),
        orangeLight = Color(0xFFFFAB40),
        background = Color(0xFF202124),
        surface = Color(0xFF2A2B2F),
        surfaceVariant = Color(0xFF34363B),
        surfaceLight = Color(0xFF42444A),
        glassBackground = Color(0xBF202124),
        glassBorder = Color(0x14C6F135),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFF2F2F2),
        textSecondary = Color(0xFFB4B5B8),
        textTertiary = Color(0xFF8E9094),
        textDisabled = Color(0xFF606266),
        scrim = Color(0x80000000),
        groundGlow = Color(0xFF303236),
        groundEnd = Color(0xFF18191B),
    )

/** Neutral dark greys, white text, a light blue accent: a plain ground with no glow. */
val SlatePalette =
    CinemaThemePalette(
        id = "slate",
        nameRes = R.string.theme_slate,
        isLight = false,
        accent = Color(0xFFA8C7FA),
        accentDark = Color(0xFF7CACF8),
        accentLight = Color(0xFFD3E3FD),
        orange = Color(0xFFFFB27A),
        orangeDark = Color(0xFFE8894A),
        orangeLight = Color(0xFFFFD2B0),
        background = Color(0xFF131314),
        surface = Color(0xFF1E1F20),
        surfaceVariant = Color(0xFF282A2C),
        surfaceLight = Color(0xFF333537),
        glassBackground = Color(0xBF131314),
        glassBorder = Color(0x14A8C7FA),
        success = Color(0xFF6DD58C),
        warning = Color(0xFFFDD663),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFE3E3E3),
        textSecondary = Color(0xFFC4C7C5),
        textTertiary = Color(0xFF8E918F),
        textDisabled = Color(0xFF5E5F60),
        scrim = Color(0x80000000),
        // The luminance rule gives black; a dark blue on the light blue accent.
        onAccentOverride = Color(0xFF062E6F),
    )

/** A starship-console look: a flat black ground, orange and lavender blocks, pale blue text (docs/plans/20261020_lcars-look-plan.md). */
val ConsolePalette =
    CinemaThemePalette(
        id = "console",
        nameRes = R.string.theme_console,
        isLight = false,
        accent = Color(0xFFFF9966),
        accentDark = Color(0xFFCC6633),
        accentLight = Color(0xFFFFCC99),
        orange = Color(0xFFCC99CC),
        orangeDark = Color(0xFF996699),
        orangeLight = Color(0xFFDDBBDD),
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0E),
        surfaceVariant = Color(0xFF16141C),
        surfaceLight = Color(0xFF3B3050),
        glassBackground = Color(0xF2000000),
        glassBorder = Color(0x00000000),
        success = Color(0xFF99CC66),
        warning = Color(0xFFFFCC66),
        error = Color(0xFFCC4444),
        live = Color(0xFFFF5533),
        textPrimary = Color(0xFFF5E9DC),
        textSecondary = Color(0xFF99CCFF),
        textTertiary = Color(0xFFCC9966),
        textDisabled = Color(0xFF6B5D52),
        scrim = Color(0xB3000000),
        scenery = Scenery.Flat,
        focusFill = Color(0xFFFFF2E0),
        onAccentOverride = Color.Black,
    )

/** A painted night: a blue-violet sky with stars and a moon over dark hills, amber lantern accent (docs/plans/20261021_meadow-look-plan.md). */
val MeadowDuskPalette =
    CinemaThemePalette(
        id = "meadow_dusk",
        nameRes = R.string.theme_meadow_dusk,
        isLight = false,
        accent = Color(0xFFF2B45A),
        accentDark = Color(0xFFD9963A),
        accentLight = Color(0xFFF7CD8A),
        orange = Color(0xFFE58A5C),
        orangeDark = Color(0xFFC46A40),
        orangeLight = Color(0xFFF0A983),
        background = Color(0xFF161A33),
        surface = Color(0xFF24202E),
        surfaceVariant = Color(0xFF2F2B36),
        surfaceLight = Color(0xFF433D52),
        glassBackground = Color(0xEB24202E),
        glassBorder = Color(0x14F2B45A),
        success = Color(0xFF8DB26E),
        warning = Color(0xFFF2B45A),
        error = Color(0xFFE5674F),
        live = Color(0xFFFF7A45),
        textPrimary = Color(0xFFF3E9D2),
        textSecondary = Color(0xFFC9BFA8),
        textTertiary = Color(0xFF9C937F),
        textDisabled = Color(0xFF6B6458),
        scrim = Color(0x99000000),
        scenery =
            Scenery.Meadow(
                skyTop = Color(0xFF141833),
                skyBottom = Color(0xFF4A3C66),
                hills = listOf(Color(0xFF253B35), Color(0xFF1A2C26), Color(0xFF11201B)),
                night = true,
                starColor = Color(0xFFFFF6DC),
                moonColor = Color(0xFFF6EBC8),
            ),
    )

/** A painted day: a soft blue sky over green hills, cream paper, moss text. Light, so kept for admins as Daylight is. */
val MeadowPalette =
    CinemaThemePalette(
        id = "meadow",
        nameRes = R.string.theme_meadow,
        isLight = true,
        adminOnly = true,
        accent = Color(0xFF4E6E3A),
        accentDark = Color(0xFF3B5A2B),
        // On cream the soft accent has to be the darker green: it marks the current row and the selected tab as text.
        accentLight = Color(0xFF3B5A2B),
        orange = Color(0xFFB5604A),
        orangeDark = Color(0xFF94472F),
        orangeLight = Color(0xFFD9826B),
        background = Color(0xFFE8F1E4),
        surface = Color(0xFFF7F1E1),
        surfaceVariant = Color(0xFFF1E8D2),
        surfaceLight = Color(0xFFE2D6B8),
        glassBackground = Color(0xEBF7F1E1),
        glassBorder = Color(0x1F4E6E3A),
        success = Color(0xFF2E7D32),
        warning = Color(0xFFB26A00),
        error = Color(0xFFB3402A),
        live = Color(0xFFC7502A),
        textPrimary = Color(0xFF2F3B2E),
        textSecondary = Color(0xFF515C47),
        textTertiary = Color(0xFF6A735C),
        textDisabled = Color(0xFFA3A896),
        scrim = Color(0x66000000),
        scenery =
            Scenery.Meadow(
                skyTop = Color(0xFFAFD8E8),
                skyBottom = Color(0xFFEEF4E2),
                hills = listOf(Color(0xFFD3E4BD), Color(0xFFC1D9A4), Color(0xFFADCB8C)),
                night = false,
                starColor = Color(0xFFFFFFFF),
                moonColor = Color(0xFFFFFFFF),
            ),
    )

/** Near-black with a strong red accent and white text: a streaming-service look. */
val CrimsonPalette =
    CinemaThemePalette(
        id = "crimson",
        nameRes = R.string.theme_crimson,
        isLight = false,
        accent = Color(0xFFE8141C),
        accentDark = Color(0xFFB20710),
        accentLight = Color(0xFFFF4D55),
        orange = Color(0xFFF5A623),
        orangeDark = Color(0xFFC77F0A),
        orangeLight = Color(0xFFFFC966),
        background = Color(0xFF141414),
        surface = Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFF262626),
        surfaceLight = Color(0xFF333333),
        glassBackground = Color(0xBF141414),
        glassBorder = Color(0x14FFFFFF),
        success = Color(0xFF46D369),
        warning = Color(0xFFFFC107),
        // Orange, not red: red is the accent.
        error = Color(0xFFE87C03),
        live = Color(0xFFE8141C),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3B3B3),
        textTertiary = Color(0xFF808080),
        textDisabled = Color(0xFF595959),
        scrim = Color(0x99000000),
    )

/** A deep navy ground fading to black, with a royal blue accent: a family streaming look. */
val RoyalPalette =
    CinemaThemePalette(
        id = "royal",
        nameRes = R.string.theme_royal,
        isLight = false,
        accent = Color(0xFF1F6FEB),
        accentDark = Color(0xFF0050C0),
        accentLight = Color(0xFF4D97FF),
        orange = Color(0xFFF9C74F),
        orangeDark = Color(0xFFD9A72E),
        orangeLight = Color(0xFFFFDD8A),
        background = Color(0xFF0F1322),
        surface = Color(0xFF1A1D29),
        surfaceVariant = Color(0xFF252836),
        surfaceLight = Color(0xFF31343F),
        glassBackground = Color(0xBF0F1322),
        glassBorder = Color(0x144D97FF),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFC107),
        error = Color(0xFFF44336),
        live = Color(0xFFFF6D00),
        textPrimary = Color(0xFFF9F9F9),
        textSecondary = Color(0xFFCACACA),
        textTertiary = Color(0xFF8D8F99),
        textDisabled = Color(0xFF5A5C66),
        scrim = Color(0x99000000),
        groundGlow = Color(0xFF1E2A4F),
        groundEnd = Color(0xFF040714),
    )

/** A warm charcoal ground with a terracotta accent and warm off-white text: a calm, paper-like look. */
val ClayPalette =
    CinemaThemePalette(
        id = "clay",
        nameRes = R.string.theme_clay,
        isLight = false,
        accent = Color(0xFFD97757),
        accentDark = Color(0xFFC15F3C),
        accentLight = Color(0xFFE8A288),
        orange = Color(0xFFE3B085),
        orangeDark = Color(0xFFC8935F),
        orangeLight = Color(0xFFF0CBA8),
        background = Color(0xFF262624),
        surface = Color(0xFF30302E),
        surfaceVariant = Color(0xFF3A3936),
        surfaceLight = Color(0xFF45443F),
        glassBackground = Color(0xBF262624),
        glassBorder = Color(0x14FFFFFF),
        success = Color(0xFF7FB069),
        warning = Color(0xFFE3B341),
        error = Color(0xFFE5484D),
        live = Color(0xFFE5484D),
        textPrimary = Color(0xFFFAF9F5),
        textSecondary = Color(0xFFC2C0B6),
        textTertiary = Color(0xFF8F8D86),
        textDisabled = Color(0xFF5E5D59),
        scrim = Color(0x99000000),
        groundGlow = Color(0xFF332D29),
        groundEnd = Color(0xFF1C1B1A),
        // White on the terracotta reads at 3:1 only; the warm near-black reads at 5.5:1.
        onAccentOverride = Color(0xFF1F1E1D),
    )

/** An indigo ground with a soft glow, a white accent with navy text on it, yellow badges: a streaming-radio look. */
val IndigoPalette =
    CinemaThemePalette(
        id = "indigo",
        nameRes = R.string.theme_indigo,
        isLight = false,
        accent = Color(0xFFFFFFFF),
        // Dark: a phone's selected row fills with it and takes white text.
        accentDark = Color(0xFF4A4F8F),
        accentLight = Color(0xFFC9CCF2),
        orange = Color(0xFFFFD23F),
        orangeDark = Color(0xFFD9A92B),
        orangeLight = Color(0xFFFFE38A),
        background = Color(0xFF1C1E3A),
        surface = Color(0xFF24274A),
        surfaceVariant = Color(0xFF2D3158),
        surfaceLight = Color(0xFF383C66),
        glassBackground = Color(0xBF1C1E3A),
        glassBorder = Color(0x14FFFFFF),
        success = Color(0xFF4CAF50),
        warning = Color(0xFFFFB020),
        error = Color(0xFFEF5350),
        live = Color(0xFFEF5350),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFC3C5E0),
        textTertiary = Color(0xFF8E91B8),
        textDisabled = Color(0xFF5D6088),
        scrim = Color(0x99000000),
        groundGlow = Color(0xFF2A2E5C),
        groundEnd = Color(0xFF14152B),
        // Navy on white, not the black that the luminance rule picks.
        onAccentOverride = Color(0xFF1C1E3A),
        // Not the white default: a Main button at rest is white here, and focus must differ from it.
        focusFill = Color(0xFFFFD23F),
        badgeFill = Color(0xFFFFD23F),
    )

val AllPalettes: List<CinemaThemePalette> =
    listOf(
        DeepNightPalette,
        MidnightPalette,
        EmberPalette,
        AuroraPalette,
        GraphitePalette,
        SlatePalette,
        CrimsonPalette,
        RoyalPalette,
        ClayPalette,
        IndigoPalette,
        AmoledBlackPalette,
        AmethystPalette,
        ForestPalette,
        DaylightPalette,
        ConsolePalette,
        MeadowDuskPalette,
        MeadowPalette,
    )

/** The colour themes [admin] may pick; [current] stays listed so its picker can show it. */
fun palettesFor(
    admin: Boolean,
    current: String,
): List<CinemaThemePalette> = AllPalettes.filter { admin || !it.adminOnly || it.id == current }

/** Themes no longer offered, and the one each profile that had it now gets: Teal went 2026-10-19. */
private val RETIRED_THEMES = mapOf("teal" to "aurora")

/** The theme with [id] (a retired one's replacement); Deep night, the default, for an unknown one. */
fun paletteById(id: String): CinemaThemePalette =
    (RETIRED_THEMES[id] ?: id).let { theme -> AllPalettes.firstOrNull { it.id == theme } } ?: DeepNightPalette

object CinemaThemeHolder {
    var current: CinemaThemePalette by mutableStateOf(DeepNightPalette)
}

val LocalCinemaTheme = staticCompositionLocalOf { DeepNightPalette }
