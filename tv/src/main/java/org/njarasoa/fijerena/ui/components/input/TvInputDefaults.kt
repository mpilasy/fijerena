@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.Border
import androidx.tv.material3.ListItemBorder
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemGlow
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.ListItemShape
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * The one focus/selection language of every D-pad control in `ui/components/input`. Every value
 * comes from the active [org.njarasoa.fijerena.core.ui.theme.CinemaThemePalette] (colour) and
 * [org.njarasoa.fijerena.core.ui.theme.UiStyle] (corner radius, focus scale, outline weight, focus
 * shadow), so these controls follow the user's theme and look-and-feel as the rest of the TV UI does.
 *
 * `androidx.tv.material3.ListItem` has a full focused × selected state matrix, which these tokens
 * fill in: a row can be focused and unselected or selected and unfocused, and both must read at ten
 * feet.
 *
 * - **Focus** lifts: [TvFocusTokens.focusedContainer] (lighter than rest) plus a full-weight white
 *   outline ([TvFocusTokens.focusedRowOutline]) plus the active style's scale and, where the style
 *   asks for one, its shadow; the text stays white. The same look as a content list row
 *   ([org.njarasoa.fijerena.ui.components.cards.TvListRowDefaults], TV UI audit X2).
 * - **Selection** keeps the container: a [TvFocusTokens.currentBarWidth] bar on the leading edge
 *   ([currentIndicator]), [TvFocusTokens.currentText] for the text at rest, and the control's own
 *   glyph (check / radio dot / switch thumb). No tint and no outline, so a selected row never looks
 *   like a second focused one (UX overhaul plan Part II P5).
 *
 * A row that is both gets the focus look (white text) and keeps the bar, so focus never borrows
 * the accent.
 */
object TvInputDefaults {
    /**
     * One text role for each kind of tab and chip (consistency round 2, finding 31): a page's tabs
     * (TvSectionTabs, the season tabs, Search's Catalogue / People / Teams / Guide) and a filter's
     * chips (TvSelectableButton). A tab marks the current one with the bar on its bottom edge, a
     * chip with the bar on its leading edge ([currentIndicator]).
     */
    val tabLabelRole: TextRole = TextRole.Heading

    /** See [tabLabelRole]. */
    val chipLabelRole: TextRole = TextRole.Label

    @ReadOnlyComposable
    @Composable
    fun colors(): ListItemColors =
        ListItemDefaults.colors(
            containerColor = TvFocusTokens.restingContainer,
            contentColor = CinemaTheme.colors.textPrimary,
            focusedContainerColor = TvFocusTokens.focusedContainer,
            focusedContentColor = CinemaTheme.colors.textPrimary,
            selectedContainerColor = TvFocusTokens.restingContainer,
            selectedContentColor = TvFocusTokens.currentText,
            focusedSelectedContainerColor = TvFocusTokens.focusedContainer,
            focusedSelectedContentColor = CinemaTheme.colors.textPrimary,
            disabledContainerColor = TvFocusTokens.restingContainer.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.textFaint),
        )

    @ReadOnlyComposable
    @Composable
    fun border(): ListItemBorder =
        ListItemDefaults.border(
            border = Border.None,
            focusedBorder = focusBorder(),
            selectedBorder = Border.None,
            focusedSelectedBorder = focusBorder(),
            pressedBorder = focusBorder(),
            pressedSelectedBorder = focusBorder(),
        )

    /** No outline in any state: a row around the item draws it (a source row and its buttons). */
    @Composable
    fun noBorder(): ListItemBorder =
        ListItemDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            selectedBorder = Border.None,
            focusedSelectedBorder = Border.None,
            pressedBorder = Border.None,
            pressedSelectedBorder = Border.None,
        )

    @Composable
    fun scale(): ListItemScale =
        ListItemDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleRow,
            selectedScale = TvFocusTokens.defaultScale,
            pressedScale = TvFocusTokens.pressedScaleSubtle,
        )

    @Composable
    fun shape(): ListItemShape = ListItemDefaults.shape(shape = cornerShape(CornerRadius.row))

    @Composable
    fun glow(): ListItemGlow =
        ListItemDefaults.glow(
            focusedGlow = TvFocusTokens.focusedGlow,
            focusedSelectedGlow = TvFocusTokens.focusedGlow,
        )

    @ReadOnlyComposable
    @Composable
    private fun focusBorder(): Border =
        Border(
            border = BorderStroke(width = TvFocusTokens.focusBorderWidth, color = TvFocusTokens.focusedRowOutline),
            shape = cornerShape(CornerRadius.row),
        )
}

/** Which edge [currentIndicator] draws its bar along: the leading edge for rows, the bottom for tabs. */
enum class CurrentIndicatorEdge { Start, Bottom }

/**
 * The one "selected / current" mark (UX overhaul plan Part II P5): a [TvFocusTokens.currentBarWidth]
 * bar in [TvFocusTokens.currentAccent] along the leading edge (or the bottom edge for tabs, see
 * [edge]), drawn over the content and clipped to [shape]. Pair it with [TvFocusTokens.currentText]
 * for the row's title. It draws over the focus look, so a focused current row keeps its bar.
 *
 * Put it on a node that fills the row's container, inside a tv `Surface` / `Card`'s content, which
 * the container clips and scales. Outside one (as [TvInputListItem] must, as `ListItem` pads its
 * content), pass the container's [shape] and its animated scale as [containerScale]: the container
 * scales only in its own draw, so a bar drawn outside it would sit inside its edge while focused.
 */
@Composable
fun Modifier.currentIndicator(
    active: Boolean,
    shape: Shape = RectangleShape,
    containerScale: () -> Float = { TvFocusTokens.defaultScale },
    edge: CurrentIndicatorEdge = CurrentIndicatorEdge.Start,
): Modifier {
    // Always in the chain, drawing nothing while inactive: adding and removing a node as the
    // selection moves would restructure a focused row's modifier chain.
    val color = TvFocusTokens.currentAccent
    val width = TvFocusTokens.currentBarWidth
    return drawWithCache {
        val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val thickness = width.toPx()
        val (barTopLeft, barSize) =
            when (edge) {
                CurrentIndicatorEdge.Start -> {
                    val barLeft = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - thickness
                    Offset(barLeft, 0f) to Size(thickness, size.height)
                }

                CurrentIndicatorEdge.Bottom -> {
                    Offset(0f, size.height - thickness) to Size(size.width, thickness)
                }
            }
        onDrawWithContent {
            drawContent()
            if (active) {
                scale(containerScale()) {
                    clipPath(clip) {
                        drawRect(color = color, topLeft = barTopLeft, size = barSize)
                    }
                }
            }
        }
    }
}
