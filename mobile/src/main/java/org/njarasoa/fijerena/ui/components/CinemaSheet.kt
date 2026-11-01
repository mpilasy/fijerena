package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ApplyDialogScrim
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.DialogPosition
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * The phone sheet: a list or a detail over the screen. The Look and Feel decides how it opens
 * (`UiDialogTokens.sheet`): from the bottom with a drag handle, as a centred panel, or full screen
 * with a Close button. Its corners and scrim come from the dialog tokens and its colour from the
 * theme, as for `CinemaAlertDialog`. Never call `ModalBottomSheet` or `Dialog` in a screen: use
 * this.
 *
 * A swipe down, a tap on the scrim or Back calls [onDismissRequest]. When [dismissible] is false,
 * none of them closes the sheet (the caller closes it). [content] gets `close`: it closes the sheet
 * as the Look and Feel does (a bottom sheet slides down first) and then calls [onDismissRequest].
 * The content sets its own side padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val tokens = LocalUiStyle.current.dialog
    val color = CinemaTheme.colors.surface
    val canDismiss by rememberUpdatedState(dismissible)
    val dismiss = { if (canDismiss) onDismissRequest() }

    when (tokens.sheet) {
        DialogPosition.BOTTOM_SHEET -> {
            val state =
                rememberModalBottomSheetState(
                    // Fully open: a half-open sheet hides the end of its list.
                    skipPartiallyExpanded = true,
                    confirmValueChange = { canDismiss || it != SheetValue.Hidden },
                )
            val scope = rememberCoroutineScope()
            val close: () -> Unit = {
                scope.launch { state.hide() }.invokeOnCompletion { if (!state.isVisible) onDismissRequest() }
            }
            ModalBottomSheet(
                onDismissRequest = dismiss,
                modifier = modifier,
                sheetState = state,
                shape = cornerShape(tokens.cornerRadius),
                containerColor = color,
                scrimColor = CinemaTheme.colors.mediaScrim.copy(alpha = tokens.scrimAlpha),
                properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
            ) {
                ProvideUiScaledDensity {
                    Column(modifier = Modifier.fillMaxWidth()) { content(close) }
                }
            }
        }

        DialogPosition.CENTERED -> {
            Dialog(
                onDismissRequest = dismiss,
                properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
            ) {
                ApplyDialogScrim(tokens.scrimAlpha)
                ProvideUiScaledDensity {
                    Surface(modifier = modifier, shape = cornerShape(tokens.cornerRadius), color = color) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = CinemaSpacing.md)) {
                            content(onDismissRequest)
                        }
                    }
                }
            }
        }

        DialogPosition.FULL_BLEED -> {
            Dialog(
                onDismissRequest = dismiss,
                properties =
                    DialogProperties(
                        dismissOnBackPress = dismissible,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false,
                    ),
            ) {
                ApplyDialogScrim(tokens.scrimAlpha)
                ProvideUiScaledDensity {
                    Surface(modifier = modifier.fillMaxSize(), shape = RectangleShape, color = color) {
                        Column(modifier = Modifier.fillMaxSize().padding(bottom = CinemaSpacing.md)) {
                            // No scrim to tap in full screen: Close is on the sheet.
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                if (dismissible) {
                                    IconAction(
                                        onClick = onDismissRequest,
                                        icon = CinemaIcons.Close,
                                        label = stringResource(R.string.common_close),
                                    )
                                }
                            }
                            content(onDismissRequest)
                        }
                    }
                }
            }
        }
    }
}
