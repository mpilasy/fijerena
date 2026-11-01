package org.njarasoa.fijerena.core.ui.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The app's short plain messages ("Joined", "S9E17 is not in your source"), in place of a `Toast`.
 * Any code can send one, also code that is not in a composable (a click's callback, a view model's
 * reply, a link that no app opens). Each app shows them in its one host at the root of its screens:
 * the phone's `CinemaSnackbarHost` (above the dock), the TV's `TvNoticeHost` (bottom centre, not
 * focusable). An Undo after a removal does not come here: the phone's `LocalCinemaSnackbar` and the
 * TV's `TvUndoBar` show it.
 *
 * A message sent while no host is on screen is lost: send only what the user can do without.
 */
object AppNotices {
    private val sent = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The messages for the host to show, one after the other. */
    val messages: SharedFlow<String> = sent.asSharedFlow()

    fun show(text: String) {
        sent.tryEmit(text)
    }
}
