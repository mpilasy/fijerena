@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.inputs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun TvEditableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isEditing: Boolean,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    val scale = LocalUiScale.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = if (isEditing) onValueChange else { {} },
            readOnly = !isEditing,
            label = { Text(label) },
            placeholder = placeholder,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { /* handle focus if needed */ },
            enabled = isEditing, // Make it disabled (non-focusable) when not editing
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CinemaTextPrimary,
                unfocusedTextColor = if (isEditing) CinemaTextPrimary else CinemaTextSecondary,
                cursorColor = CinemaAccent,
                focusedBorderColor = if (isEditing) CinemaAccent else CinemaTextSecondary,
                unfocusedBorderColor = CinemaTextSecondary,
                focusedLabelColor = if (isEditing) CinemaAccent else CinemaTextSecondary,
                unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                focusedContainerColor = CinemaSurfaceVariant,
                unfocusedContainerColor = CinemaSurfaceVariant,
                focusedPlaceholderColor = CinemaTextSecondary,
                unfocusedPlaceholderColor = CinemaTextSecondary,
                disabledTextColor = CinemaTextPrimary,
                disabledBorderColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                disabledLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                disabledContainerColor = CinemaSurfaceVariant
            )
        )

        if (!isEditing) {
            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
            CinemaIconButton(
                onClick = onEditClick,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit $label",
                        tint = CinemaAccent
                    )
                },
                size = 48.dp // Standard size, scaled inside CinemaIconButton
            )
        }
    }
}
