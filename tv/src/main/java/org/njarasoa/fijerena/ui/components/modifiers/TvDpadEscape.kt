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
 * Lets D-pad Up/Down leave a focused text field.
 *
 * A focused Compose text field consumes the direction keys to move its caret. On a phone that is
 * invisible — you tap somewhere else. On TV the remote is the only pointer, so the field becomes a
 * dead end: focus goes in and never comes out, and everything below it on the screen is
 * unreachable. This forwards Up/Down to the focus manager instead.
 *
 * Only for **single-line** fields. On a multi-line field the caret genuinely needs Up/Down, and
 * the right fix there is to keep the field out of the D-pad path entirely — see
 * [org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit].
 *
 * Left/Right are deliberately untouched: they are caret movement within the line, and call sites
 * that need horizontal escape (the search bar's clear/submit buttons) already handle them.
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
        // Report the event as unhandled when there is nowhere to go, so the platform still gets
        // its chance — swallowing it would make the field feel frozen instead of merely bounded.
        direction != null && focusManager.moveFocus(direction)
    }
}
