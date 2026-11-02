@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Border
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * A switch that is one focus stop of its own, beside a row's text (a guide source's on/off). OK
 * calls [onToggle]; the switch inside is an inert indicator.
 *
 * P5: checked keeps the resting container and shows the switch in the current colour; only focus
 * lifts it, with the white focus outline of every TV row in the control corner (one TV focus look).
 */
@Composable
fun TvSwitchButton(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CinemaTheme.shapes.control
    Surface(
        selected = checked,
        onClick = onToggle,
        modifier = modifier,
        colors =
            SelectableSurfaceDefaults.colors(
                containerColor = TvFocusTokens.restingContainer,
                contentColor = CinemaTheme.colors.textSecondary,
                focusedContainerColor = TvFocusTokens.focusedContainer,
                focusedContentColor = CinemaTheme.colors.textPrimary,
                selectedContainerColor = TvFocusTokens.restingContainer,
                selectedContentColor = TvFocusTokens.currentText,
                focusedSelectedContainerColor = TvFocusTokens.focusedContainer,
                focusedSelectedContentColor = TvFocusTokens.currentText,
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
                focusedBorder =
                    Border(
                        border = BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline),
                        shape = shape,
                    ),
            ),
        glow = SelectableSurfaceDefaults.glow(),
        shape = SelectableSurfaceDefaults.shape(shape = shape),
    ) {
        Box(
            modifier = Modifier.padding(Spacing.xs.scaled(LocalUiScale.current)),
            contentAlignment = Alignment.Center,
        ) {
            CinemaSwitch(checked = checked, onCheckedChange = null)
        }
    }
}
