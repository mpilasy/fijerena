@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * The one focus/selection language shared by every D-pad control in `ui/components/input`.
 *
 * Every value here resolves through the active [org.njarasoa.fijerena.core.ui.theme.CinemaThemePalette]
 * (colour) and [org.njarasoa.fijerena.core.ui.theme.UiStyle] (corner radius, focus scale, outline
 * weight, focus shadow), so these controls follow the user's theme and look-and-feel settings the
 * same way the rest of the TV UI does.
 *
 * `androidx.tv.material3.ListItem` carries a full focused × selected state matrix, which is what
 * these tokens fill in. That matrix is the whole point: a row can be focused-and-unselected or
 * selected-and-unfocused, and both have to read at ten feet.
 *
 * - **Focus** lifts: [TvFocusTokens.focusedContainer] (lighter than rest) plus a full-weight accent
 *   outline plus the active style's scale and, where the style asks for one, its shadow.
 * - **Selection** keeps the container: a [TvFocusTokens.currentBarWidth] bar on the leading edge
 *   ([currentIndicator]), [TvFocusTokens.currentText] for the text, and the control's own glyph
 *   (check / radio dot / switch thumb). No tint and no outline, so a selected row never looks
 *   like a second focused one (UX overhaul plan Part II P5).
 *
 * When a row is both, it gets the focus look and keeps the bar and the text colour, so the two
 * never collapse into one another.
 */
object TvInputDefaults {
    @ReadOnlyComposable
    @Composable
    fun colors(): ListItemColors =
        ListItemDefaults.colors(
            containerColor = TvFocusTokens.restingContainer,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = TvFocusTokens.focusedContainer,
            focusedContentColor = CinemaTextPrimary,
            selectedContainerColor = TvFocusTokens.restingContainer,
            selectedContentColor = TvFocusTokens.currentText,
            focusedSelectedContainerColor = TvFocusTokens.focusedContainer,
            focusedSelectedContentColor = TvFocusTokens.currentText,
            disabledContainerColor = TvFocusTokens.restingContainer.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint),
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

    @Composable
    fun scale(): ListItemScale =
        ListItemDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleSubtle,
            selectedScale = TvFocusTokens.defaultScale,
            pressedScale = TvFocusTokens.pressedScaleSubtle,
        )

    @Composable
    fun shape(): ListItemShape = ListItemDefaults.shape(shape = RoundedCornerShape(CornerRadius.small))

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
            border = BorderStroke(width = TvFocusTokens.focusBorderWidth, color = CinemaAccentLight),
            shape = RoundedCornerShape(CornerRadius.small),
        )
}

/**
 * The one "selected / current" mark (UX overhaul plan Part II P5): a [TvFocusTokens.currentBarWidth]
 * bar in [TvFocusTokens.currentAccent] along the leading edge, drawn over the content and clipped
 * to [shape]. Pair it with [TvFocusTokens.currentText] for the row's title. It sits on top of
 * whatever the focus look draws, so a focused current row keeps its bar.
 *
 * Put it on a node that fills the row's container — inside a tv `Surface` / `Card`'s content, which
 * the container already clips and scales. Outside one (as [TvInputListItem] must, `ListItem` pads
 * its content), pass the container's [shape] and its animated scale as [containerScale]: the
 * container scales only in its own draw, so a bar drawn outside it would otherwise sit inside its
 * edge while it is focused.
 */
@Composable
fun Modifier.currentIndicator(
    active: Boolean,
    shape: Shape = RectangleShape,
    containerScale: () -> Float = { TvFocusTokens.defaultScale },
): Modifier {
    // Always in the chain, drawing nothing while inactive: adding and removing a node as the
    // selection moves would restructure a focused row's modifier chain.
    val color = TvFocusTokens.currentAccent
    val width = TvFocusTokens.currentBarWidth
    return drawWithCache {
        val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val barWidth = width.toPx()
        val barLeft = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - barWidth
        onDrawWithContent {
            drawContent()
            if (active) {
                scale(containerScale()) {
                    clipPath(clip) {
                        drawRect(color = color, topLeft = Offset(barLeft, 0f), size = Size(barWidth, size.height))
                    }
                }
            }
        }
    }
}
