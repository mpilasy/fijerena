package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PinDots
import org.njarasoa.fijerena.core.ui.components.PinPromptState
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton

/**
 * A PIN prompt on the phone (the profile pin plan): [state]'s title, the dots
 * the number keyboard fills, and its message; the fourth digit submits. Cancel, Back or a tap outside calls [onCancel] — unless [cancellable] is false. [note] shows above the title. Screenshots and screen recording are blocked while it is open.
 */
@Composable
fun PinDialog(
    state: PinPromptState,
    onCancel: () -> Unit,
    cancellable: Boolean = true,
    note: String? = null,
) {
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }
    CinemaAlertDialog(
        onDismissRequest = { if (cancellable) onCancel() },
        // No screenshot, screen recording or Recents thumbnail while a PIN is typed.
        properties =
            DialogProperties(
                dismissOnBackPress = cancellable,
                dismissOnClickOutside = cancellable,
                securePolicy = SecureFlagPolicy.SecureOn,
            ),
        // Centred over the dots.
        title = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Who asks, for a request from another device ("Living Room asks").
                note?.let { CinemaText(it, role = TextRole.Body, tone = TextTone.Secondary, textAlign = TextAlign.Center) }
                CinemaText(
                    state.title,
                    role = TextRole.Title,
                    tone = TextTone.Inherit,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

/**
 * The child lock's prompt: shown while [viewModel] holds a switch away from a child profile
 * ([ProfilesViewModel.leavingChild]). The right PIN makes the switch, then [onSwitched] runs;
 * Cancel keeps the child.
 */
@Composable
fun LeaveChildPinDialog(
    viewModel: ProfilesViewModel,
    onSwitched: () -> Unit,
) {
    val request by viewModel.leavingChild.collectAsStateWithLifecycle()
    request?.let { leaving ->
        PinDialog(
            state = rememberPinPromptState(viewModel.pins, leaving) { viewModel.switchTo(leaving.targetId, onSwitched, pinTyped = true) },
            onCancel = viewModel::cancelLeavingChild,
        )
    }
}
