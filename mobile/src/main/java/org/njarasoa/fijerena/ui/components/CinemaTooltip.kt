package org.njarasoa.fijerena.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone

/**
 * The phone's one tooltip: [label] shows over [content] on a long-press, above it ([below] false)
 * or below it. Icon actions ([org.njarasoa.fijerena.ui.components.buttons.IconAction]), the dock
 * and the header's buttons use it; feature code never calls Material's tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaTooltip(
    label: String,
    modifier: Modifier = Modifier,
    below: Boolean = false,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                if (below) TooltipAnchorPosition.Below else TooltipAnchorPosition.Above,
            ),
        tooltip = { PlainTooltip { CinemaText(label, role = TextRole.BodySmall, tone = TextTone.Inherit) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
