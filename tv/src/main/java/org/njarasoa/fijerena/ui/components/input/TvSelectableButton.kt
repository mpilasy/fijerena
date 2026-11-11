@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Border
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.ProvideChipLabel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * A one-of-N picker button (theme, UI scale, language, stream format, …).
 *
 * It is one composable with a `selected` flag, not two composables swapped in one slot: swapping
 * removed the focused node on every pick, which dropped focus to the window root.
 *
 * OK on the selected option calls [onSelect] again; callers treat that as idempotent instead of
 * wiring it to `{}`, so the press still gives visible feedback.
 *
 * Colour comes from the active palette, and shape / scale / outline weight / focus shadow /
 * emphasis weight from the active look-and-feel style, via [TvFocusTokens].
 *
 * Selected is never a fill or an outline: the P5 "current" bar on the leading edge
 * ([currentIndicator]), [TvFocusTokens.currentText] and the emphasis weight mark it on the resting
 * container, so the one lifted, outlined option is always the focused one.
 * [tab]: the button is one of a page's tabs (Search's Catalogue / People / Teams / Guide), not a
 * filter chip: the tab's text role and the bar on the bottom edge, as on every other tab row
 * ([TvInputDefaults.tabLabelRole]).
 */
@Composable
fun TvSelectableButton(
    selected: Boolean,
    onSelect: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tab: Boolean = false,
) {
    Surface(
        selected = selected,
        onClick = onSelect,
        modifier = modifier,
        enabled = enabled,
        shape = SelectableSurfaceDefaults.shape(shape = if (tab) CinemaTheme.shapes.chip else cornerShape(CornerRadius.small)),
        colors =
            SelectableSurfaceDefaults.colors(
                // A tab has the one tab look: no fill at rest, its label alone (tabChipFill).
                containerColor = if (tab) CinemaTheme.colors.clear else TvFocusTokens.restingContainer,
                contentColor = if (tab) CinemaTheme.colors.textSecondary else CinemaTheme.colors.textPrimary,
                focusedContainerColor = TvFocusTokens.focusFill,
                focusedContentColor = TvFocusTokens.onFocusFill,
                selectedContainerColor = if (tab) CinemaTheme.colors.clear else TvFocusTokens.restingContainer,
                selectedContentColor = TvFocusTokens.currentText,
                focusedSelectedContainerColor = TvFocusTokens.focusFill,
                focusedSelectedContentColor = TvFocusTokens.onFocusFill,
                disabledContainerColor = TvFocusTokens.restingContainer.copy(alpha = CinemaAlpha.scrim),
                disabledContentColor = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.textFaint),
            ),
        scale =
            SelectableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                selectedScale = TvFocusTokens.defaultScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        border =
            SelectableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border.None,
            ),
        glow = SelectableSurfaceDefaults.glow(focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        // Fills the surface's content box, which the surface clips and scales with focus.
        Box(
            modifier =
                Modifier.matchParentSize().currentIndicator(
                    selected,
                    edge = if (tab) CurrentIndicatorEdge.Bottom else CurrentIndicatorEdge.Start,
                ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)),
        ) {
            // The surface's content colour: dark on the white focus.
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides androidx.tv.material3.LocalContentColor.current,
            ) {
                // A tab's role is not a label, so only a chip takes the Look's chip label style.
                ProvideChipLabel {
                    CinemaText(
                        text = text,
                        role = if (tab) TvInputDefaults.tabLabelRole else TvInputDefaults.chipLabelRole,
                        tone = TextTone.Inherit,
                        emphasis = selected,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
