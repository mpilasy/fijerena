package org.njarasoa.fijerena.ui.components.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Gives focus back after a transient editor closes.
 *
 * A composable that swaps an editor in for its trigger control (`if (isEditing) TextField else
 * Row { … pencil }`) destroys the focused node when [active] goes false. Focus then falls to the
 * window root, and the next D-pad press lands on the first focusable on the screen (the top of a
 * settings form).
 *
 * Attach the returned requester to the control the user should land on: this focuses it on the
 * [active] `true -> false` edge only, never on first composition or on the way into the editor.
 *
 * ```
 * val editFocus = rememberFocusReturn(active = isEditing)
 * if (isEditing) { … } else { CinemaIconButton(modifier = Modifier.focusRequester(editFocus), …) }
 * ```
 */
@Composable
fun rememberFocusReturn(active: Boolean): FocusRequester {
    val requester = remember { FocusRequester() }
    var wasActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (wasActive && !active) {
            // The target may not be attached yet (the read-only row composes in the same pass that removes the
            // editor), or ever (a row scrolled out of a lazy list); losing the hand-off then is acceptable.
            requester.requestFocusWithRetry()
        }
        wasActive = active
    }
    return requester
}
