package org.njarasoa.fijerena.core.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
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
    /** Corners cut straight (an octagon-like outline) instead of rounded. */
    val cut: Boolean,
)

@Immutable
data class UiTypeTokens(
    /** Every piece of text in this font (FontFamily.Default keeps each text role's own). */
    val fontFamily: FontFamily,
    val weightRegular: Int,
    val weightEmphasis: Int,
    val trackingTitleSp: Float,
    val trackingBodySp: Float,
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
)

val MaterialStyle =
    UiStyle(
        id = "material",
        nameRes = R.string.ui_style_standard,
        shapes = UiShapeTokens(card = 16.dp, button = 20.dp, chip = 20.dp, dialog = 28.dp, cut = false),
        type =
            UiTypeTokens(
                fontFamily = FontFamily.Default,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = 0f,
                trackingBodySp = 0f,
            ),
        dialog = UiDialogTokens(cornerRadius = 28.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.32f),
        grid = UiGridTokens(spacing = 8.dp, focusScale = 1.04f, focusUsesOutline = true, focusUsesShadow = false),
        icon = UiIconTokens(style = IconStyle.FILLED),
    )

val CupertinoStyle =
    UiStyle(
        id = "cupertino",
        nameRes = R.string.ui_style_rounded,
        shapes = UiShapeTokens(card = 22.dp, button = 100.dp, chip = 100.dp, dialog = 18.dp, cut = false),
        type =
            UiTypeTokens(
                fontFamily = FontFamily.Default,
                weightRegular = 400,
                weightEmphasis = 500,
                trackingTitleSp = -0.2f,
                trackingBodySp = 0f,
            ),
        dialog = UiDialogTokens(cornerRadius = 18.dp, position = DialogPosition.BOTTOM_SHEET, scrimAlpha = 0.4f),
        grid = UiGridTokens(spacing = 10.dp, focusScale = 1.09f, focusUsesOutline = false, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.OUTLINED),
    )

val RokuStyle =
    UiStyle(
        id = "roku",
        nameRes = R.string.ui_style_sharp,
        shapes = UiShapeTokens(card = 2.dp, button = 2.dp, chip = 2.dp, dialog = 0.dp, cut = false),
        type =
            UiTypeTokens(
                fontFamily = FontFamily.Default,
                weightRegular = 500,
                weightEmphasis = 800,
                trackingTitleSp = 1.2f,
                trackingBodySp = 0.4f,
            ),
        dialog = UiDialogTokens(cornerRadius = 0.dp, position = DialogPosition.FULL_BLEED, scrimAlpha = 0.55f),
        grid = UiGridTokens(spacing = 4.dp, focusScale = 1.0f, focusUsesOutline = true, focusUsesShadow = false),
        icon = UiIconTokens(style = IconStyle.FILLED),
    )

val BraviaStyle =
    UiStyle(
        id = "bravia",
        nameRes = R.string.ui_style_balanced,
        shapes = UiShapeTokens(card = 8.dp, button = 8.dp, chip = 8.dp, dialog = 8.dp, cut = false),
        type =
            UiTypeTokens(
                fontFamily = FontFamily.Default,
                weightRegular = 400,
                weightEmphasis = 600,
                trackingTitleSp = 0.3f,
                trackingBodySp = 0.2f,
            ),
        dialog = UiDialogTokens(cornerRadius = 8.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.4f),
        grid = UiGridTokens(spacing = 8.dp, focusScale = 1.06f, focusUsesOutline = true, focusUsesShadow = true),
        icon = UiIconTokens(style = IconStyle.BOLD_OUTLINED),
    )

val SerifStyle =
    UiStyle(
        id = "serif",
        nameRes = R.string.ui_style_serif,
        shapes = UiShapeTokens(card = 12.dp, button = 10.dp, chip = 8.dp, dialog = 14.dp, cut = true),
        type =
            UiTypeTokens(
                fontFamily = FontFamily.Serif,
                weightRegular = 400,
                weightEmphasis = 700,
                trackingTitleSp = 0.2f,
                trackingBodySp = 0.1f,
            ),
        dialog = UiDialogTokens(cornerRadius = 14.dp, position = DialogPosition.CENTERED, scrimAlpha = 0.45f),
        grid = UiGridTokens(spacing = 8.dp, focusScale = 1.05f, focusUsesOutline = true, focusUsesShadow = false),
        icon = UiIconTokens(style = IconStyle.OUTLINED),
    )

val AllUiStyles: List<UiStyle> = listOf(MaterialStyle, CupertinoStyle, RokuStyle, BraviaStyle, SerifStyle)

fun styleById(id: String): UiStyle = AllUiStyles.firstOrNull { it.id == id } ?: MaterialStyle

/** Non-composable access mirror, see [CinemaThemeHolder]. */
object UiStyleHolder {
    @Volatile
    var current: UiStyle = MaterialStyle
}

/** A corner of [radius] in these tokens' corner style: rounded, or cut straight ([UiShapeTokens.cut]). */
fun UiShapeTokens.shape(radius: Dp): CornerBasedShape = if (cut) CutCornerShape(radius) else RoundedCornerShape(radius)

/**
 * A corner of [radius] in the Look and Feel's corner style. Every corner in the app is built with
 * this (or a [CinemaTheme] shape role), never with a shape class directly.
 */
@Composable
@ReadOnlyComposable
fun cornerShape(radius: Dp): CornerBasedShape = LocalUiStyle.current.shapes.shape(radius)

val LocalUiStyle = staticCompositionLocalOf { MaterialStyle }

/**
 * Applies this style's weight/tracking character to a base [TextStyle] without disturbing the
 * platform's existing per-slot size/line-height scale: [FontWeight.Bold] is remapped to
 * [UiTypeTokens.weightEmphasis] and [FontWeight.Normal] to [UiTypeTokens.weightRegular] (other
 * weights, e.g. Medium/SemiBold used for intermediate slots, are left as-is to preserve the
 * scale's internal hierarchy). Tracking is nudged by [UiTypeTokens.trackingTitleSp] when
 * [fontFamily] matches [displayFontFamily] (display/headline slots), else by
 * [UiTypeTokens.trackingBodySp].
 */
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
