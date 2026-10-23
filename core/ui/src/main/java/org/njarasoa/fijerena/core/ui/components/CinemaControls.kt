package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ControlStyle

/**
 * A text field as [ControlStyle] and [CinemaTheme] say: the Look and Feel's field corner, the
 * theme's outline, accent when focused, danger on [isError]. [label], [placeholder] and
 * [supportingText] are plain strings, set in the theme's roles.
 */
@Composable
fun CinemaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = CinemaTheme.colors
    // look-ok: the component that wraps Material's OutlinedTextField
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyleOf(TextRole.Body),
        label = label?.let { { CinemaText(it, role = TextRole.BodySmall, tone = TextTone.Inherit) } },
        placeholder = placeholder?.let { { CinemaText(it, tone = TextTone.Tertiary) } },
        supportingText = supportingText?.let { { CinemaText(it, role = TextRole.Caption, tone = TextTone.Inherit) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = CinemaTheme.shapes.field,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                disabledTextColor = c.textDisabled,
                errorTextColor = c.textPrimary,
                cursorColor = c.accent,
                errorCursorColor = c.danger,
                focusedBorderColor = c.accent,
                unfocusedBorderColor = c.outline,
                disabledBorderColor = c.divider,
                errorBorderColor = c.danger,
                focusedLabelColor = c.accent,
                unfocusedLabelColor = c.textSecondary,
                disabledLabelColor = c.textDisabled,
                errorLabelColor = c.danger,
                focusedSupportingTextColor = c.textSecondary,
                unfocusedSupportingTextColor = c.textSecondary,
                errorSupportingTextColor = c.danger,
                focusedLeadingIconColor = c.textSecondary,
                unfocusedLeadingIconColor = c.textSecondary,
                focusedTrailingIconColor = c.textSecondary,
                unfocusedTrailingIconColor = c.textSecondary,
                focusedContainerColor = c.surface.copy(alpha = 0f),
                unfocusedContainerColor = c.surface.copy(alpha = 0f),
            ),
    )
}

/** A checkbox in the theme's accent. */
@Composable
fun CinemaCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = CinemaTheme.colors
    // look-ok: the component that wraps Material's Checkbox
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors =
            CheckboxDefaults.colors(
                checkedColor = c.accent,
                uncheckedColor = c.textSecondary,
                checkmarkColor = c.onAccent,
                disabledCheckedColor = c.textDisabled,
                disabledUncheckedColor = c.textDisabled,
            ),
    )
}

/** A switch in the theme's accent. */
@Composable
fun CinemaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = CinemaTheme.colors
    // look-ok: the component that wraps Material's Switch
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                checkedBorderColor = c.accent,
                uncheckedThumbColor = c.textSecondary,
                uncheckedTrackColor = c.surfaceRaised,
                uncheckedBorderColor = c.outline,
            ),
    )
}

/** A radio button in the theme's accent. */
@Composable
fun CinemaRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = CinemaTheme.colors
    // look-ok: the component that wraps Material's RadioButton
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            RadioButtonDefaults.colors(
                selectedColor = c.accent,
                unselectedColor = c.textSecondary,
                disabledSelectedColor = c.textDisabled,
                disabledUnselectedColor = c.textDisabled,
            ),
    )
}

/** A divider line in the theme's divider colour, the hairline width. */
@Composable
fun CinemaDivider(modifier: Modifier = Modifier) {
    // look-ok: the component that wraps Material's HorizontalDivider
    HorizontalDivider(modifier = modifier, thickness = CinemaTheme.borders.hairline, color = CinemaTheme.colors.divider)
}

/**
 * A panel (card) on the phone and in dialogs: the raised surface, the Look and Feel's card corner,
 * [ControlStyle.panelPadding] inside. [outlined] adds the theme's outline; [onClick] makes it a
 * tappable card. TV rows and cards with focus are the TV app's own (tv `ui/components/cards`).
 */
@Composable
fun CinemaPanel(
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CinemaTheme.colors
    val colors = CardDefaults.cardColors(containerColor = c.surfaceRaised, contentColor = c.textPrimary)
    val border = if (outlined) BorderStroke(CinemaTheme.borders.outline, c.outline) else null
    val inner: @Composable ColumnScope.() -> Unit = {
        androidx.compose.foundation.layout
            .Column(Modifier.padding(ControlStyle.panelPadding), content = content)
    }
    if (onClick != null) {
        // look-ok: the component that wraps Material's Card
        Card(onClick = onClick, modifier = modifier, shape = CinemaTheme.shapes.card, colors = colors, border = border, content = inner)
    } else {
        // look-ok: the component that wraps Material's Card
        Card(modifier = modifier, shape = CinemaTheme.shapes.card, colors = colors, border = border, content = inner)
    }
}

/**
 * A progress bar: the accent over a faint track (brighter when [highlighted], the focused seek bar), [ControlStyle.progressThickness] thick unless
 * [modifier] sets a height. [progress] null is indeterminate (something is happening, amount unknown).
 */
@Composable
fun CinemaProgressBar(
    progress: (() -> Float)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val c = CinemaTheme.colors
    // [highlighted]: the bar is the focused control (TV seek bar) — the bright accent and a stronger track.
    val bar = if (highlighted) c.accentSoft else c.accent
    val track =
        c.textSecondary.copy(
            alpha = if (highlighted) ControlStyle.PROGRESS_TRACK_ALPHA_HIGHLIGHTED else ControlStyle.PROGRESS_TRACK_ALPHA,
        )
    if (progress == null) {
        // look-ok: the component that wraps Material's LinearProgressIndicator
        LinearProgressIndicator(modifier = modifier.height(ControlStyle.progressThickness), color = bar, trackColor = track)
    } else {
        // look-ok: the component that wraps Material's LinearProgressIndicator
        LinearProgressIndicator(
            progress = progress,
            modifier = modifier.height(ControlStyle.progressThickness),
            color = bar,
            trackColor = track,
            drawStopIndicator = {},
        )
    }
}

/** A spinner in the accent. */
@Composable
fun CinemaSpinner(modifier: Modifier = Modifier) {
    // look-ok: the component that wraps Material's CircularProgressIndicator
    CircularProgressIndicator(modifier = modifier, color = CinemaTheme.colors.accent)
}
