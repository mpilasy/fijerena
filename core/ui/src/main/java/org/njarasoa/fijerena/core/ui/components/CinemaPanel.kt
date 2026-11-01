package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/**
 * What a panel is for (docs/plans/20261101_style-layout-tokens-plan.md, Phase 5). The colour theme
 * and the Look and Feel give its colour through [CinemaTheme]; a screen names the kind, never a
 * colour.
 */
enum class PanelFill {
    /** The screen's ground. Also an opaque band that hides what scrolls under it. */
    Ground,

    /** A block on the ground: a pinned header, a card at rest. */
    Surface,

    /** One step above [Surface]: a guide cell, a control at rest. */
    Raised,

    /** [Raised], see-through: a folding section header, a list pane. */
    RaisedFaint,

    /** Two steps above [Surface]: a row being edited. */
    High,

    /** Frosted glass: a pane over the backdrop. */
    Glass,

    /** Behind video or artwork, and a label over them. */
    Media,

    /** Over artwork: clear at the top, [Media] at the bottom, so text at the bottom stays legible. */
    MediaFade,

    /** Over video: the ground at the top, clear at the bottom, so the player's top line stays legible. */
    TopFade,

    /** Over the whole video while the player's controls show. */
    Osd,

    /** Over video behind a side panel: a light tint. */
    Veil,

    /** Dims what is behind it: a mark over a poster, the video while a channel tunes. */
    Dim,

    /** Hides most of what is behind it: a full-screen overlay (an error, a selector). */
    Shade,

    /** Behind a dialog: the ground at the Look and Feel's dialog scrim. */
    DialogScrim,

    /** The theme's scrim: behind a panel that a tap outside closes. */
    Scrim,

    /** A short notice over video (slow connection, an error): the ground, nearly opaque. */
    Notice,

    /** A card over video: the surface, half see-through. */
    MediaCard,

    /** The selected item in a row of tabs: a faint accent. */
    Selected,

    /** The accent: the on-air mark. */
    Accent,

    /** A done or positive action. */
    Success,

    /** Something in progress or to check. */
    Warning,

    /** A warning band or a destructive action. */
    Danger,

    /** The live mark. */
    Live,

    /** A thin line: a time mark in the guide. */
    Rule,

    /** Nothing: the item shows what is behind it. */
    Clear,
}

/** A panel's corner, from the Look and Feel ([CinemaTheme.shapes]). */
enum class PanelCorner {
    /** Square: a band or a whole screen. */
    None,

    /** The card corner. */
    Card,

    /** The chip corner. */
    Chip,

    /** The control corner (icon buttons, badges). */
    Control,

    /** The dialog corner. */
    Dialog,

    /** Round in every Look and Feel: avatars, colour dots, status dots. */
    Identity,
}

/** A panel's outline, from the theme's colours and [CinemaTheme.borders]. */
enum class PanelOutline {
    /** No outline. */
    None,

    /** A faint edge: a choice at rest. */
    Hairline,

    /** The accent at the focus width: the chosen choice, a row being edited. */
    Selected,

    /** The text colour at the focus width: the chosen colour among swatches. */
    Marked,
}

/** The shape of [corner] in the active Look and Feel. */
@Composable
@ReadOnlyComposable
fun panelShape(corner: PanelCorner): Shape =
    when (corner) {
        PanelCorner.None -> RectangleShape
        PanelCorner.Card -> CinemaTheme.shapes.card
        PanelCorner.Chip -> CinemaTheme.shapes.chip
        PanelCorner.Control -> CinemaTheme.shapes.control
        PanelCorner.Dialog -> CinemaTheme.shapes.dialog
        PanelCorner.Identity -> CinemaTheme.shapes.identity
    }

/**
 * The colour of [fill] in the active colour theme. [PanelFill.MediaFade] and [PanelFill.TopFade]
 * are fades, which only [panel] draws: here they give their dark end.
 */
@Composable
@ReadOnlyComposable
fun panelColor(fill: PanelFill): Color {
    val colors = CinemaTheme.colors
    return when (fill) {
        PanelFill.Ground -> colors.background
        PanelFill.Surface -> colors.surface
        PanelFill.Raised -> colors.surfaceRaised
        PanelFill.RaisedFaint -> colors.surfaceRaised.copy(alpha = CinemaAlpha.tint)
        PanelFill.High -> colors.surfaceHigh
        PanelFill.Glass -> colors.glass
        PanelFill.Media, PanelFill.MediaFade -> colors.mediaScrim
        PanelFill.TopFade -> colors.background.copy(alpha = CinemaAlpha.textMedium)
        PanelFill.Osd -> colors.mediaScrim.copy(alpha = CinemaAlpha.osdScrim)
        PanelFill.Veil -> colors.background.copy(alpha = CinemaAlpha.tint)
        PanelFill.Dim -> colors.background.copy(alpha = CinemaAlpha.scrim)
        PanelFill.Shade -> colors.background.copy(alpha = CinemaAlpha.overlayHeavy)
        PanelFill.DialogScrim -> colors.background.copy(alpha = LocalUiStyle.current.dialog.scrimAlpha)
        PanelFill.Scrim -> colors.scrim
        PanelFill.Notice -> colors.background.copy(alpha = CinemaAlpha.glass)
        PanelFill.MediaCard -> colors.surface.copy(alpha = CinemaAlpha.scrim)
        PanelFill.Selected -> colors.accent.copy(alpha = CinemaAlpha.textLow)
        PanelFill.Accent -> colors.accent
        PanelFill.Success -> colors.success
        PanelFill.Warning -> colors.warning
        PanelFill.Danger -> colors.danger
        PanelFill.Live -> colors.live
        PanelFill.Rule -> colors.textTertiary
        PanelFill.Clear -> colors.clear
    }
}

/** Paints [fill] behind this element, in [corner]. It does not clip the content. */
@Composable
fun Modifier.panel(
    fill: PanelFill,
    corner: PanelCorner = PanelCorner.None,
): Modifier {
    val shape = panelShape(corner)
    val colors = CinemaTheme.colors
    return when (fill) {
        PanelFill.MediaFade -> {
            val dark = colors.mediaScrim
            background(remember(dark) { Brush.verticalGradient(listOf(colors.clear, dark)) }, shape)
        }

        PanelFill.TopFade -> {
            val dark = panelColor(fill)
            background(remember(dark) { Brush.verticalGradient(listOf(dark, colors.clear)) }, shape)
        }

        else -> {
            background(panelColor(fill), shape)
        }
    }
}

/** Draws [outline] around this element, in [corner]. */
@Composable
fun Modifier.panelOutline(
    outline: PanelOutline,
    corner: PanelCorner = PanelCorner.None,
): Modifier {
    val colors = CinemaTheme.colors
    val borders = CinemaTheme.borders
    return when (outline) {
        PanelOutline.None -> this
        PanelOutline.Hairline -> border(borders.outline, colors.surfaceHigh, panelShape(corner))
        PanelOutline.Selected -> border(borders.focus, colors.accent, panelShape(corner))
        PanelOutline.Marked -> border(borders.focus, colors.textPrimary, panelShape(corner))
    }
}

/**
 * A plain panel: [fill] behind [content], in [corner], with [outline]. Use it in place of a raw
 * `Surface` or a `Box` with a background; a focusable row or card uses the app's card and row
 * components instead.
 *
 * As a Material `Surface`, it stops touches: a tap on the panel does not reach what is under it
 * (the player under an error panel). A click set in [modifier] still works. [passTouches] lets
 * touches go through, for a panel that is only a picture.
 */
@Composable
fun CinemaPanel(
    modifier: Modifier = Modifier,
    fill: PanelFill = PanelFill.Surface,
    corner: PanelCorner = PanelCorner.None,
    outline: PanelOutline = PanelOutline.None,
    contentAlignment: Alignment = Alignment.TopStart,
    passTouches: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .panel(fill, corner)
                .panelOutline(outline, corner)
                // An empty pointer handler, as Material's Surface: the panel is hit, so the layer under it is not.
                .then(if (passTouches) Modifier else Modifier.pointerInput(Unit) {}),
        contentAlignment = contentAlignment,
        content = content,
    )
}
