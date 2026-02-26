package org.njarasoa.fijerena.feature.category.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent

/**
 * Modifier that detects long-press of the D-pad center / Enter key on TV.
 * Calls [onLongPress] when the key has been held long enough.
 */
internal fun Modifier.tvLongPress(onLongPress: () -> Unit): Modifier = composed {
    var longPressDetected by remember { mutableStateOf(false) }
    this.onPreviewKeyEvent { event ->
        val keyCode = event.key.nativeKeyCode
        val isDpadCenter = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER
        if (isDpadCenter &&
            event.type == KeyEventType.KeyDown &&
            event.nativeKeyEvent.repeatCount > 0 &&
            event.nativeKeyEvent.isLongPress &&
            !longPressDetected
        ) {
            // Mark that a long-press happened, but don't fire callback yet
            longPressDetected = true
            true
        } else if (isDpadCenter && event.type == KeyEventType.KeyDown && longPressDetected) {
            // Consume repeated KeyDown events while held
            true
        } else if (isDpadCenter && event.type == KeyEventType.KeyUp && longPressDetected) {
            // Fire callback on release so the dialog opens after the key is up
            longPressDetected = false
            onLongPress()
            true
        } else {
            false
        }
    }
}
