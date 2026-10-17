@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Checkbox
import androidx.tv.material3.CheckboxDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.CinemaCheckbox
import org.njarasoa.fijerena.core.ui.components.CinemaRadioButton
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/**
 * D-pad toggle rows.
 *
 * Each row is a single focus target: the row itself owns the click and the focus, and the
 * checkbox / radio / switch inside it is an inert indicator (`onCheckedChange = null`). The
 * previous shape — a `Modifier.focusable()` wrapper around an independently focusable Material3
 * control — produced two D-pad stops per item, only one of which responded to OK.
 *
 * These use the TV flavours of the controls (`androidx.tv.material3`), not the touch ones, so the
 * indicator sizing and colours match the rest of the 10-foot UI.
 */
@Composable
fun TvCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    TvStateRow(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = label,
        modifier = modifier,
        description = description,
        enabled = enabled,
        leading = {
            CinemaCheckbox(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    )
}

@Composable
fun TvRadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    TvStateRow(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        description = description,
        enabled = enabled,
        leading = {
            CinemaRadioButton(selected = selected, onClick = null, enabled = enabled)
        },
    )
}

@Composable
fun TvSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    // The switch itself shows the state; tinting the row as well made a checked row look like
    // the focused one.
    TvStateRow(
        selected = false,
        onClick = { onCheckedChange(!checked) },
        label = label,
        modifier = modifier.fillMaxWidth(),
        description = description,
        enabled = enabled,
        trailing = {
            CinemaSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    )
}

/**
 * A label (+ optional description) row carrying an inert state indicator on one side.
 * Shared body of [TvCheckRow], [TvRadioRow] and [TvSwitchRow].
 */
@Composable
private fun TvStateRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier,
    description: String?,
    enabled: Boolean,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    TvInputListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leading,
        trailingContent = trailing,
        supportingContent =
            description?.let {
                {
                    CinemaText(text = it, role = TextRole.BodySmall, tone = TextTone.Secondary)
                }
            },
    ) {
        CinemaText(text = label, role = TextRole.Heading)
    }
}
