package org.njarasoa.fijerena.ui.components.modifiers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Lets D-pad Up/Down leave a focused text field. A focused Compose text field uses the direction
 * keys for its caret; on TV the remote is the only pointer, so focus would go in and never come
 * out. This sends Up/Down to the focus manager instead.
 *
 * Only for **single-line** fields: a multi-line field needs Up/Down for its caret, so keep it out
 * of the D-pad path (see [org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit]).
 *
 * Left/Right are not touched: they move the caret in the line, and call sites that need to leave
 * sideways (the search bar's buttons) handle them.
 */
@Composable
fun Modifier.tvDpadEscape(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        val direction =
            when {
                event.type != KeyEventType.KeyDown -> null
                event.key == Key.DirectionUp -> FocusDirection.Up
                event.key == Key.DirectionDown -> FocusDirection.Down
                else -> null
            }
        // Report the event as unhandled when there is nowhere to go, so the platform still gets it.
        direction != null && focusManager.moveFocus(direction)
    }
}
