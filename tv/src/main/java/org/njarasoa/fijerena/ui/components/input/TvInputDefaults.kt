@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.tv.material3.Border
import androidx.tv.material3.ListItemBorder
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.ListItemShape
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * The one focus/selection language shared by every D-pad control in `ui/components/input`.
 *
 * `androidx.tv.material3.ListItem` carries a full focused × selected state matrix, which is what
 * these tokens fill in. That matrix is the whole point: a row can be focused-and-unselected or
 * selected-and-unfocused, and both have to read at ten feet.
 *
 * - **Focus** lifts: [TvFocusTokens.focusedContainer] (lighter than rest) plus a full-weight accent
 *   outline plus the active style's scale.
 * - **Selection** tints: [TvFocusTokens.selectedContainer] plus a hairline accent outline, and the
 *   control's own glyph (check / radio dot / switch thumb).
 *
 * When a row is both, focus wins the container and the glyph still carries selection, so the two
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
            selectedContainerColor = TvFocusTokens.selectedContainer,
            selectedContentColor = CinemaTextPrimary,
            focusedSelectedContainerColor = TvFocusTokens.focusedContainer,
            focusedSelectedContentColor = CinemaTextPrimary,
            disabledContainerColor = TvFocusTokens.restingContainer.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint),
        )

    @ReadOnlyComposable
    @Composable
    fun border(): ListItemBorder =
        ListItemDefaults.border(
            border = Border.None,
            focusedBorder = focusBorder(),
            selectedBorder = selectionBorder(),
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

    @ReadOnlyComposable
    @Composable
    private fun focusBorder(): Border =
        Border(
            border = BorderStroke(width = TvFocusTokens.focusBorderWidth, color = CinemaAccentLight),
            shape = RoundedCornerShape(CornerRadius.small),
        )

    @ReadOnlyComposable
    @Composable
    private fun selectionBorder(): Border =
        Border(
            border = BorderStroke(width = TvFocusTokens.borderDefault, color = CinemaAccent),
            shape = RoundedCornerShape(CornerRadius.small),
        )
}
