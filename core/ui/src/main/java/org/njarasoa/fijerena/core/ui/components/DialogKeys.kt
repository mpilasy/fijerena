package org.njarasoa.fijerena.core.ui.components

import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The keys a dialog's window receives: a dialog is a window of its own, so the activity's
 * dispatchKeyEvent never sees them. The TV app's Show Key Input sets [onKey]; null, nothing is done.
 */
object DialogKeys {
    /** After the dialog's window dispatched the key: the event, and whether it was handled. */
    @Volatile var onKey: ((KeyEvent, Boolean) -> Unit)? = null
}

/** Inside a dialog's content: passes each key its window receives to [DialogKeys.onKey]. */
@Composable
fun RecordDialogKeys() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val original = window.callback
        window.callback =
            object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                    original.dispatchKeyEvent(event).also { handled -> DialogKeys.onKey?.invoke(event, handled) }
            }
        onDispose { window.callback = original }
    }
}
