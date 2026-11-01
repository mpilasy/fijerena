package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.colorOf

/**
 * An action as an icon: [label] is its content description and shows in a tooltip on long-press.
 * The mobile side of the icon buttons plan (TV: `TvIconAction`).
 *
 * [tone]: the glyph's colour, from the theme ([TextTone.Inherit]: the colour around it).
 * [buttonSize]: the size of the touch target, when it is not the default; [iconModifier] sizes the glyph.
 * [on]: the button is a switch (UX-D7) — the glyph takes `PhoneButtonStyle.iconOnContent` while on
 * and `iconContent` while off (not [tone]), and it reads as a switch with that state; [label] then
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
    tone: TextTone = TextTone.Inherit,
    on: Boolean? = null,
    buttonSize: Dp = Dp.Unspecified,
    iconModifier: Modifier = Modifier,
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
                Modifier
                    .then(if (buttonSize.isSpecified) Modifier.size(buttonSize) else Modifier)
                    .then(
                        if (on != null) {
                            Modifier.semantics {
                                role = Role.Switch
                                toggleableState = ToggleableState(on)
                            }
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = iconModifier,
                tint =
                    when (on) {
                        null -> colorOf(tone)
                        true -> PhoneButtonStyle.iconOnContent
                        false -> PhoneButtonStyle.iconContent
                    },
            )
        }
    }
}
