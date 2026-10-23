package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PinDots
import org.njarasoa.fijerena.core.ui.components.PinPromptState
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton

/**
 * A PIN prompt on the phone (the profile pin plan): [state]'s title, the dots
 * the number keyboard fills, and its message; the fourth digit submits. Cancel, Back or a tap outside calls [onCancel] — unless [cancellable] is false.
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
        // Centred over the dots.
        title = {
            CinemaText(
                state.title,
                role = TextRole.Title,
                tone = TextTone.Inherit,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
            ) {
                Box {
                    PinDots(typed = state.typed.length)
                    // The input the number keyboard types into, invisible over the dots: a tap on them opens it.
                    BasicTextField(
                        value = state.typed,
                        onValueChange = state::replace,
                        modifier = Modifier.matchParentSize().alpha(0f).focusRequester(field),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                }
                state.message?.let {
                    CinemaText(
                        it,
                        role = TextRole.Body,
                        tone = if (state.messageIsError) TextTone.Danger else TextTone.Secondary,
                    )
                }
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
