package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.PinDots
import org.njarasoa.fijerena.core.ui.components.PinPromptState
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton

/**
 * A PIN prompt on the phone (docs/plans/archive/20261017_profile-pin-plan.md): [state]'s title, the dots
 * and its message over a field the number keyboard types into (shown hidden); the fourth digit
 * submits. Cancel, Back or a tap outside calls [onCancel] — unless [cancellable] is false.
 */
@Composable
fun PinDialog(
    state: PinPromptState,
    onCancel: () -> Unit,
    cancellable: Boolean = true,
) {
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }
    CinemaAlertDialog(
        onDismissRequest = { if (cancellable) onCancel() },
        properties = DialogProperties(dismissOnBackPress = cancellable, dismissOnClickOutside = cancellable),
        title = { CinemaText(state.title, role = TextRole.Title, tone = TextTone.Inherit) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            ) {
                PinDots(typed = state.typed.length)
                CinemaTextField(
                    value = state.typed,
                    onValueChange = state::replace,
                    modifier = Modifier.focusRequester(field),
                    supportingText = state.message,
                    isError = state.messageIsError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            if (cancellable) {
                CinemaDialogActionButton(onClick = onCancel, kind = ButtonKind.Other) {
                    CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
                }
            }
        },
    )
}
