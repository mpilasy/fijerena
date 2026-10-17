package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/**
 * The phone's search bar: a filled, single-line field on the theme's raised surface, the Look and
 * Feel's field corner, the accent outline while focused. [placeholder] is shown in secondary text.
 * A plain form field is core/ui `CinemaTextField`; this is the bar a search screen opens with.
 */
@Composable
fun CinemaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = CinemaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyleOf(TextRole.Body),
        placeholder = { CinemaText(placeholder, tone = TextTone.Secondary) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = CinemaTheme.shapes.field,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                disabledTextColor = c.textDisabled,
                cursorColor = c.accent,
                focusedContainerColor = c.surfaceRaised,
                unfocusedContainerColor = c.surfaceHigh,
                focusedBorderColor = c.accent,
                unfocusedBorderColor = c.textPrimary.copy(alpha = CinemaAlpha.focusedTint),
                focusedLeadingIconColor = c.textSecondary,
                unfocusedLeadingIconColor = c.textSecondary,
                focusedTrailingIconColor = c.textPrimary,
                unfocusedTrailingIconColor = c.textPrimary,
            ),
    )
}
