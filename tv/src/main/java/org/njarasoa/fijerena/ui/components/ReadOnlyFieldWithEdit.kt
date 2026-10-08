@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.rememberFocusReturn
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * A text field shown as a row — label, then the value and a pencil — like a Settings row.
 * OK on the row toggles to an editable OutlinedTextField.
 * Pressing Enter confirms, Escape/Back cancels, returning to read-only mode.
 *
 * Designed for TV/D-pad: prevents accidental keyboard trigger on focus.
 */
@Composable
fun ReadOnlyFieldWithEdit(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    displayText: String = value,
    // Lets a dialog land its initial focus on the edit button; see CinemaAlertDialog.initialFocus.
    editButtonFocusRequester: FocusRequester? = null,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember(value) { mutableStateOf(value) }
    val editFocusRequester = remember { FocusRequester() }

    // Committing or cancelling removes the text field from the composition entirely. Without
    // somewhere to hand focus back to, Compose drops it to the window root and the next D-pad
    // press restarts from the top of the form — which, in a provider form of six of these inside
    // one scrolling Column, also scrolls the whole thing back to the start.
    val returnFocusRequester = rememberFocusReturn(active = isEditing)

    if (isEditing) {
        OutlinedTextField(
            value = editValue,
            onValueChange = { editValue = it },
            label = { Text(label) },
            placeholder =
                if (placeholder.isNotEmpty()) {
                    { Text(placeholder) }
                } else {
                    null
                },
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        onValueChange(editValue)
                        isEditing = false
                    },
                ),
            modifier =
                modifier
                    .fillMaxWidth()
                    .focusRequester(editFocusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Escape, Key.Back -> {
                                    editValue = value
                                    isEditing = false
                                    true
                                }

                                Key.Enter -> {
                                    onValueChange(editValue)
                                    isEditing = false
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CinemaTextPrimary,
                    unfocusedTextColor = CinemaTextPrimary,
                    cursorColor = CinemaAccent,
                    focusedBorderColor = CinemaAccent,
                    unfocusedBorderColor = CinemaTextSecondary,
                    focusedLabelColor = CinemaAccent,
                    unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    focusedContainerColor = CinemaSurfaceVariant,
                    focusedPlaceholderColor = CinemaTextSecondary,
                    unfocusedPlaceholderColor = CinemaTextSecondary,
                ),
        )

        LaunchedEffect(Unit) {
            editFocusRequester.requestFocusWithRetry()
        }
    } else {
        // One focus stop, laid out like a Settings row: the label on the left, the value and a
        // pencil on the right; OK opens the editor (TV UI audit, X5).
        val editDescription = stringResource(R.string.common_edit_field_description_format, label)
        TvInputListItem(
            selected = false,
            onClick = {
                editValue = value
                isEditing = true
            },
            modifier =
                modifier
                    .fillMaxWidth()
                    .focusRequester(returnFocusRequester)
                    .then(if (editButtonFocusRequester != null) Modifier.focusRequester(editButtonFocusRequester) else Modifier)
                    .semantics { contentDescription = editDescription },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayText.ifEmpty { placeholder },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (displayText.isNotEmpty()) CinemaTextPrimary else CinemaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = TvDimensions.settingsInputWidth),
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Icon(
                        imageVector = CinemaIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(TvDimensions.iconSmall),
                    )
                }
            },
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
