@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.ToggleableSurfaceDefaults
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * A one-of-N picker button (theme, UI scale, language, stream format, …).
 *
 * The point of this component is that it is **one composable with a `selected` flag**, not two
 * composables swapped in one slot. The `if (selected) CinemaPrimaryButton else
 * CinemaSecondaryButton` shape it replaces made Compose remove the focused node and insert a
 * different one every time the user picked an option, which dropped focus to the window root — the
 * next D-pad press then restarted from the top of the screen.
 *
 * Pressing OK on the already-selected option re-invokes [onSelect]; callers should treat that as
 * idempotent rather than wiring it to `{}`, so the press still gives visible feedback.
 *
 * Colour comes from the active palette and shape / scale / outline weight / focus shadow /
 * emphasis weight from the active look-and-feel style, via [TvFocusTokens].
 */
@Composable
fun TvSelectableButton(
    selected: Boolean,
    onSelect: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        checked = selected,
        onCheckedChange = { onSelect() },
        modifier = modifier,
        enabled = enabled,
        shape = ToggleableSurfaceDefaults.shape(shape = RoundedCornerShape(CornerRadius.small)),
        colors =
            ToggleableSurfaceDefaults.colors(
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
            ),
        scale =
            ToggleableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                selectedScale = TvFocusTokens.defaultScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        border =
            ToggleableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder =
                    Border(
                        border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaAccentLight),
                        shape = RoundedCornerShape(CornerRadius.small),
                    ),
                selectedBorder =
                    Border(
                        border = BorderStroke(TvFocusTokens.borderDefault, CinemaAccent),
                        shape = RoundedCornerShape(CornerRadius.small),
                    ),
            ),
        glow = ToggleableSurfaceDefaults.glow(focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)),
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) TvFocusTokens.emphasisWeight else TvFocusTokens.regularWeight,
            )
        }
    }
}
