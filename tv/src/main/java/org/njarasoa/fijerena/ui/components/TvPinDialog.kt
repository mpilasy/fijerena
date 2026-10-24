package org.njarasoa.fijerena.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Icon
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PinDots
import org.njarasoa.fijerena.core.ui.components.PinPromptState
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.PinStyle
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A PIN prompt on TV (the profile pin plan): [state]'s title, the dots and its
 * message over a 3×4 number pad (1–9, Erase, 0) the D-pad moves on; a remote's digit keys and
 * Delete type directly. The fourth digit submits. Focus opens on 1; Back or Cancel calls [onCancel].
 */
@Composable
fun TvPinDialog(
    state: PinPromptState,
    onCancel: () -> Unit,
) {
    val firstKey = remember { FocusRequester() }
    CinemaAlertDialog(
        initialFocus = firstKey,
        onDismissRequest = onCancel,
        containerColor = CinemaTheme.colors.background,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
        // Centred over the dots and pad.
        title = { CinemaText(state.title, role = TextRole.Title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(
                modifier =
                    Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val code = event.nativeKeyEvent.keyCode
                        when (code) {
                            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                                state.type('0' + (code - KeyEvent.KEYCODE_0))
                                true
                            }

                            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                                state.type('0' + (code - KeyEvent.KEYCODE_NUMPAD_0))
                                true
                            }

                            KeyEvent.KEYCODE_DEL -> {
                                state.erase()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PinDots(typed = state.typed.length)
                state.message?.let {
                    CinemaText(
                        it,
                        role = TextRole.Body,
                        tone = if (state.messageIsError) TextTone.Danger else TextTone.Secondary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(PinStyle.keyGap)) {
                    listOf("123", "456", "789").forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(PinStyle.keyGap)) {
                            row.forEach { digit ->
                                PadKey(
                                    onClick = { state.type(digit) },
                                    modifier = if (digit == '1') Modifier.focusRequester(firstKey) else Modifier,
                                ) { CinemaText(digit.toString(), tone = TextTone.Inherit) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(PinStyle.keyGap)) {
                        PadKey(onClick = state::erase) {
                            Icon(CinemaIcons.Backspace, contentDescription = stringResource(R.string.pin_erase))
                        }
                        PadKey(onClick = { state.type('0') }) { CinemaText("0", tone = TextTone.Inherit) }
                        Spacer(Modifier.width(PinStyle.keyWidth))
                    }
                }
            }
        },
        confirmButton = {
            CinemaButton(onClick = onCancel, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel), tone = TextTone.Inherit)
            }
        },
    )
}

@Composable
private fun PadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CinemaButton(onClick = onClick, modifier = modifier.width(PinStyle.keyWidth), kind = ButtonKind.Other) { content() }
}
