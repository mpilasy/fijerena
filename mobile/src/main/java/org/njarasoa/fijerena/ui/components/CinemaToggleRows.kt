package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaRadioButton
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/*
 * Phone toggle rows, the same as the TV's `TvCheckRow`, `TvRadioRow` and `TvSwitchRow`: the whole
 * row takes the tap, and the check box, radio or switch in it only shows the state. The label is
 * Body (Heading when [labelIsName]: the label is a name, a source or a Look), the supporting text
 * under it is in the secondary tone. The rows set no side padding: [modifier] places the row
 * (a page gives its inset, a dialog none).
 */

/** One option of several that can be on together (a filter's genres, what to import). */
@Composable
fun CinemaCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ToggleRow(
        label = label,
        supportingText = supportingText,
        icon = icon,
        enabled = enabled,
        labelIsName = false,
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        leading = { CinemaCheckbox(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * One option of a single choice (a picker's value, a track, a source). [trailing] is a small static
 * sample at the row's end (a colour theme's colours).
 */
@Composable
fun CinemaRadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    labelIsName: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    ToggleRow(
        label = label,
        supportingText = supportingText,
        icon = icon,
        enabled = enabled,
        labelIsName = labelIsName,
        modifier =
            modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        leading = { CinemaRadioButton(selected = selected, onClick = null, enabled = enabled) },
        trailing = trailing,
    )
}

/** A setting that is on or off, the switch at the row's end. */
@Composable
fun CinemaSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    labelIsName: Boolean = false,
) {
    ToggleRow(
        label = label,
        supportingText = supportingText,
        icon = icon,
        enabled = enabled,
        labelIsName = labelIsName,
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        trailing = { CinemaSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** The shared body of the toggle rows: [modifier] already carries the row's tap. */
@Composable
private fun ToggleRow(
    label: String,
    supportingText: String?,
    icon: ImageVector?,
    enabled: Boolean,
    labelIsName: Boolean,
    modifier: Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val labelTone = if (enabled) TextTone.Primary else TextTone.Disabled
    val supportingTone = if (enabled) TextTone.Secondary else TextTone.Disabled
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = CinemaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(CinemaSpacing.sm))
        }
        if (icon != null) {
            CinemaIcon(icon, contentDescription = null, tone = supportingTone)
            Spacer(modifier = Modifier.width(CinemaSpacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            CinemaText(label, role = if (labelIsName) TextRole.Heading else TextRole.Body, tone = labelTone)
            if (supportingText != null) {
                CinemaText(supportingText, tone = supportingTone)
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(CinemaSpacing.sm))
            trailing()
        }
    }
}
