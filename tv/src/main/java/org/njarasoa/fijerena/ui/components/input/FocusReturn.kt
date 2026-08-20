package org.njarasoa.fijerena.ui.components.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Gives back focus after a transient editor closes.
 *
 * A composable that swaps an editor in for its trigger control (`if (isEditing) TextField else
 * Row { … pencil }`) destroys the focused node when [active] flips false. Compose has nothing left
 * to restore to, so focus falls to the window root and the next D-pad press lands on the first
 * focusable on the screen — for a settings form, that means jumping back to the top.
 *
 * Attach the returned requester to the control the user should land on, and this re-aims focus at
 * it on the [active] `true -> false` edge only. It never steals focus on first composition, and
 * never fires on the way *into* the editor.
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
            // The target may not be attached — a read-only row scrolled out of a lazy list, or a
            // parent that unmounted along with the editor. Losing the hand-off is survivable;
            // crashing is not.
            try {
                requester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
        wasActive = active
    }
    return requester
}
