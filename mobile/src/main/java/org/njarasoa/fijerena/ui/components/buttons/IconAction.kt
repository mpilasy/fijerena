package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone

/**
 * An action as an icon: [label] is its content description and shows in a tooltip on long-press.
 * The mobile side of the icon buttons plan (TV: `TvIconAction`).
 *
 * [on]: the button is a switch (UX-D7) — the glyph takes `PhoneButtonStyle.iconOnContent` while on
 * and `iconContent` while off (not [tint]), and it reads as a switch with that state; [label] then
 * names the setting and its state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconAction(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    on: Boolean? = null,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { CinemaText(label, role = TextRole.BodySmall, tone = TextTone.Inherit) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                if (on != null) {
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(on)
                    }
                } else {
                    Modifier
                },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint =
                    when (on) {
                        null -> tint
                        true -> PhoneButtonStyle.iconOnContent
                        false -> PhoneButtonStyle.iconContent
                    },
            )
        }
    }
}
