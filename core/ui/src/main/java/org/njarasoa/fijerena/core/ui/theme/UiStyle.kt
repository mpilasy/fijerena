package org.njarasoa.fijerena.core.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.njarasoa.fijerena.core.ui.R

/**
 * Look-and-feel tokens: platform-inspired shape/type/grid/dialog/icon character, independent of
 * [CinemaThemePalette] (which only covers color). Any palette combines with any style.
 */
@Immutable
data class UiShapeTokens(
    val card: Dp,
    val button: Dp,
    val chip: Dp,
    val dialog: Dp,
)

@Immutable
data class UiTypeTokens(
    /** Every piece of text in this font (FontFamily.Default keeps each text role's own). */
    val fontFamily: FontFamily,
    val weightRegular: Int,
    val weightEmphasis: Int,
    val trackingTitleSp: Float,
    val trackingBodySp: Float,
    /** Titles and headings in this font instead (null: as [fontFamily]); the rest keeps [fontFamily]. */
    val titleFontFamily: FontFamily? = null,
    /** The weight titles use with [titleFontFamily] (null: the type scale's own). */
    val titleWeight: Int? = null,
    /** Labels (buttons, tabs, chips) in this font instead (null: as [fontFamily]). */
    val labelFontFamily: FontFamily? = null,
    /** Labels (buttons, tabs, chips, badges) in capitals (Console). Titles and headings keep their case. */
    val capsLabels: Boolean = false,
)

enum class DialogPosition { CENTERED, BOTTOM_SHEET, FULL_BLEED }

@Immutable
data class UiDialogTokens(
    val cornerRadius: Dp,
    val position: DialogPosition,
    val scrimAlpha: Float,
)

@Immutable
data class UiGridTokens(
    val spacing: Dp,
    val focusScale: Float,
    val focusUsesOutline: Boolean,
    val focusUsesShadow: Boolean,
    /** No glow anywhere, not even on a focused card (Console): focus is a fill or a ring. */
    val flat: Boolean = false,
)

enum class IconStyle { FILLED, OUTLINED, BOLD_OUTLINED }

@Immutable
data class UiIconTokens(
    val style: IconStyle,
)

@Immutable
data class UiStyle(
    val id: String,
    /** The name shown in Settings: neutral and localised (no product names, U-M4). */
    @param:StringRes val nameRes: Int,
    val shapes: UiShapeTokens,
    val type: UiTypeTokens,
    val dialog: UiDialogTokens,
    val grid: UiGridTokens,
    val icon: UiIconTokens,
    /** A screen frame on TV (Console); null: none. */
    val frame: UiFrameTokens? = null,
)

/** The system font throughout, 16–20 dp corners: the device's own look. */
val MaterialStyle =
    UiStyle(
        id = "material",
        nameRes = R.string.ui_style_standard,
        shapes = UiShapeTokens(card = 16.dp, button = 20.dp, chip = 20.dp, dialog = 28.dp),
        type =
            UiTypeTokens(
                fontFamily = FontFamily.Default,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = 0f,
                trackingBodySp = 0f,
                // The system font for titles too: the device's own look.
                titleFontFamily = FontFamily.Default,
            ),
        dialog = UiDialogTokens(cornerRadius = 28.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.32f),
        grid = UiGridTokens(spacing = 8.dp, focusScale = 1.04f, focusUsesOutline = true, focusUsesShadow = false),
        icon = UiIconTokens(style = IconStyle.FILLED),
    )

/** Nunito throughout, pill buttons, bottom-sheet dialogs: soft and friendly. */
val CupertinoStyle =
    UiStyle(
        id = "cupertino",
        nameRes = R.string.ui_style_rounded,
        shapes = UiShapeTokens(card = 22.dp, button = 100.dp, chip = 100.dp, dialog = 18.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.Nunito,
                weightRegular = 400,
                weightEmphasis = 500,
                trackingTitleSp = -0.2f,
                trackingBodySp = 0f,
            ),
        dialog = UiDialogTokens(cornerRadius = 18.dp, position = DialogPosition.BOTTOM_SHEET, scrimAlpha = 0.4f),
        grid = UiGridTokens(spacing = 10.dp, focusScale = 1.09f, focusUsesOutline = false, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.OUTLINED),
    )

/** Barlow Condensed titles over Barlow, square corners, wide tracking, full-screen dialogs: broadcast TV. */
val RokuStyle =
    UiStyle(
        id = "roku",
        nameRes = R.string.ui_style_sharp,
        shapes = UiShapeTokens(card = 2.dp, button = 2.dp, chip = 2.dp, dialog = 0.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.Barlow,
                weightRegular = 500,
                weightEmphasis = 800,
                trackingTitleSp = 1.2f,
                trackingBodySp = 0.4f,
                titleFontFamily = UiFonts.BarlowCondensed,
                titleWeight = 600,
            ),
        dialog = UiDialogTokens(cornerRadius = 0.dp, position = DialogPosition.FULL_BLEED, scrimAlpha = 0.55f),
        grid = UiGridTokens(spacing = 4.dp, focusScale = 1.0f, focusUsesOutline = true, focusUsesShadow = false),
        icon = UiIconTokens(style = IconStyle.FILLED),
    )

/** IBM Plex Sans throughout, even 8 dp corners: neutral and precise. */
val BraviaStyle =
    UiStyle(
        id = "bravia",
        nameRes = R.string.ui_style_balanced,
        shapes = UiShapeTokens(card = 8.dp, button = 8.dp, chip = 8.dp, dialog = 8.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.IbmPlexSans,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = 0.3f,
                trackingBodySp = 0.2f,
            ),
        dialog = UiDialogTokens(cornerRadius = 8.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.4f),
        grid = UiGridTokens(spacing = 8.dp, focusScale = 1.06f, focusUsesOutline = true, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.BOLD_OUTLINED),
    )

/** Manrope titles over Inter, 12 dp corners: clean, like Google TV (the tv visual refresh plan). */
val ModernStyle =
    UiStyle(
        id = "modern",
        nameRes = R.string.ui_style_modern,
        shapes = UiShapeTokens(card = 12.dp, button = 12.dp, chip = 8.dp, dialog = 16.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.Inter,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = -0.3f,
                trackingBodySp = 0f,
                titleFontFamily = UiFonts.Manrope,
                titleWeight = 800,
            ),
        dialog = UiDialogTokens(cornerRadius = 16.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.45f),
        grid = UiGridTokens(spacing = 10.dp, focusScale = 1.06f, focusUsesOutline = false, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.OUTLINED),
    )

/** DM Serif Display titles over Inter, sharp 4 dp corners: a film-poster feel. */
val CinemaStyle =
    UiStyle(
        id = "cinema",
        nameRes = R.string.ui_style_cinema,
        shapes = UiShapeTokens(card = 4.dp, button = 4.dp, chip = 3.dp, dialog = 6.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.Inter,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = 0f,
                trackingBodySp = 0f,
                titleFontFamily = UiFonts.DmSerifDisplay,
                titleWeight = 400,
            ),
        dialog = UiDialogTokens(cornerRadius = 6.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.5f),
        grid = UiGridTokens(spacing = 8.dp, focusScale = 1.06f, focusUsesOutline = false, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.OUTLINED),
    )

/** Lexend throughout, even 18 dp corners: friendly, easy to read. */
val SoftStyle =
    UiStyle(
        id = "soft",
        nameRes = R.string.ui_style_soft,
        shapes = UiShapeTokens(card = 18.dp, button = 18.dp, chip = 18.dp, dialog = 24.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.Lexend,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = -0.2f,
                trackingBodySp = 0f,
                titleWeight = 700,
            ),
        dialog = UiDialogTokens(cornerRadius = 24.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.4f),
        grid = UiGridTokens(spacing = 10.dp, focusScale = 1.08f, focusUsesOutline = false, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.FILLED),
    )

/** Space Grotesk throughout, square corners: bold and technical. */
val GroteskStyle =
    UiStyle(
        id = "grotesk",
        nameRes = R.string.ui_style_grotesk,
        shapes = UiShapeTokens(card = 0.dp, button = 0.dp, chip = 0.dp, dialog = 0.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.SpaceGrotesk,
                weightRegular = 400,
                weightEmphasis = 700,
                trackingTitleSp = -0.6f,
                trackingBodySp = 0f,
                titleWeight = 700,
            ),
        dialog = UiDialogTokens(cornerRadius = 0.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.5f),
        grid = UiGridTokens(spacing = 8.dp, focusScale = 1.04f, focusUsesOutline = false, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.BOLD_OUTLINED),
    )

/**
 * A starship-console look (docs/plans/20261020_lcars-look-plan.md): pill buttons and chips, Antonio
 * titles, headings and labels (labels in capitals) over Inter text, no zoom and no glow on focus.
 */
val ConsoleStyle =
    UiStyle(
        id = "console",
        nameRes = R.string.ui_style_console,
        shapes = UiShapeTokens(card = 6.dp, button = 100.dp, chip = 100.dp, dialog = 24.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.Inter,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = 0.6f,
                trackingBodySp = 0f,
                titleFontFamily = UiFonts.Antonio,
                titleWeight = 600,
                labelFontFamily = UiFonts.Antonio,
                capsLabels = true,
            ),
        dialog = UiDialogTokens(cornerRadius = 24.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.7f),
        grid = UiGridTokens(spacing = 6.dp, focusScale = 1.0f, focusUsesOutline = false, focusUsesShadow = false, flat = true),
        icon = UiIconTokens(style = IconStyle.FILLED),
        frame = ConsoleFrame,
    )

/** A hand-painted look (docs/plans/20261021_meadow-look-plan.md): Klee One titles over Zen Maru Gothic, round 20 dp corners, a gentle lift. */
val MeadowStyle =
    UiStyle(
        id = "meadow",
        nameRes = R.string.ui_style_meadow,
        shapes = UiShapeTokens(card = 20.dp, button = 20.dp, chip = 20.dp, dialog = 24.dp),
        type =
            UiTypeTokens(
                fontFamily = UiFonts.ZenMaruGothic,
                weightRegular = 400,
                weightEmphasis = 700,
                trackingTitleSp = 0f,
                trackingBodySp = 0f,
                titleFontFamily = UiFonts.KleeOne,
                titleWeight = 600,
            ),
        dialog = UiDialogTokens(cornerRadius = 24.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.45f),
        grid = UiGridTokens(spacing = 10.dp, focusScale = 1.05f, focusUsesOutline = false, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.FILLED),
    )

val AllUiStyles: List<UiStyle> =
    listOf(
        MaterialStyle,
        CupertinoStyle,
        RokuStyle,
        BraviaStyle,
        ModernStyle,
        CinemaStyle,
        SoftStyle,
        GroteskStyle,
        ConsoleStyle,
        MeadowStyle,
    )

/** Looks no longer offered, and the one each profile that had it now gets: Serif went 2026-10-19. */
private val RETIRED_STYLES = mapOf("serif" to "cinema")

/** The Look with [id] (a retired one's replacement); Modern (the default since 2026-10-17) for an unknown one. */
fun styleById(id: String): UiStyle = (RETIRED_STYLES[id] ?: id).let { look -> AllUiStyles.firstOrNull { it.id == look } } ?: ModernStyle

/** Non-composable access mirror, see [CinemaThemeHolder]. */
object UiStyleHolder {
    @Volatile
    var current: UiStyle = ModernStyle
}

/** A corner of [radius]: every Look's corners are rounded. */
fun UiShapeTokens.shape(radius: Dp): CornerBasedShape = RoundedCornerShape(radius)

/**
 * A corner of [radius] in the Look and Feel's corner style. Every corner in the app is built with
 * this (or a [CinemaTheme] shape role), never with a shape class directly.
 */
@Composable
@ReadOnlyComposable
fun cornerShape(radius: Dp): CornerBasedShape = LocalUiStyle.current.shapes.shape(radius)

val LocalUiStyle = staticCompositionLocalOf { ModernStyle }

/**
 * Applies this style's weight/tracking character to a base [TextStyle] without disturbing the
 * platform's existing per-slot size/line-height scale: [FontWeight.Bold] is remapped to
 * [UiTypeTokens.weightEmphasis] and [FontWeight.Normal] to [UiTypeTokens.weightRegular] (other
 * weights, e.g. Medium/SemiBold used for intermediate slots, are left as-is to preserve the
 * scale's internal hierarchy). Tracking is nudged by [UiTypeTokens.trackingTitleSp] when
 * [fontFamily] matches [displayFontFamily] (display/headline slots), else by
 * [UiTypeTokens.trackingBodySp].
 */

/**
 * [applyUiTypeTokens] for a title or heading slot: in the Look's [UiTypeTokens.titleFontFamily] and
 * [UiTypeTokens.titleWeight] when it has them, else as a [displayFontFamily] slot.
 */
fun TextStyle.applyUiTitleTokens(
    tokens: UiTypeTokens,
    displayFontFamily: FontFamily,
): TextStyle {
    val base = applyUiTypeTokens(tokens, displayFontFamily)
    val family = tokens.titleFontFamily ?: if (tokens.fontFamily == FontFamily.Default) base.fontFamily else tokens.fontFamily
    val weight = tokens.titleWeight?.let { FontWeight(it) } ?: base.fontWeight
    return base.copy(fontFamily = family, fontWeight = weight, letterSpacing = (letterSpacing.value + tokens.trackingTitleSp).sp)
}

/** [applyUiTypeTokens] for a label slot (buttons, tabs, chips): in [UiTypeTokens.labelFontFamily] when the Look has one. */
fun TextStyle.applyUiLabelTokens(
    tokens: UiTypeTokens,
    displayFontFamily: FontFamily,
): TextStyle =
    applyUiTypeTokens(tokens, displayFontFamily).let { base ->
        tokens.labelFontFamily?.let { base.copy(fontFamily = it) } ?: base
    }

fun TextStyle.applyUiTypeTokens(
    tokens: UiTypeTokens,
    displayFontFamily: FontFamily,
): TextStyle {
    val weight =
        when (fontWeight) {
            FontWeight.Bold -> FontWeight(tokens.weightEmphasis)
            FontWeight.Normal -> FontWeight(tokens.weightRegular)
            else -> fontWeight
        }
    val trackingDeltaSp = if (fontFamily == displayFontFamily) tokens.trackingTitleSp else tokens.trackingBodySp
    val family = if (tokens.fontFamily == FontFamily.Default) fontFamily else tokens.fontFamily
    return copy(fontFamily = family, fontWeight = weight, letterSpacing = (letterSpacing.value + trackingDeltaSp).sp)
}
