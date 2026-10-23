package org.njarasoa.fijerena.ui.components

import android.content.res.Resources
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.navigation.LocalDockInset

/**
 * The phone's Undo after a removal from Recent, Favourites or the Wishlist (wishlist UX plan, U-D4):
 * "Removed <title> · Undo" for 10 s, as TV's Undo bar. A new removal takes the place of the one
 * shown; that one stays removed.
 */
class UndoSnackbar(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val resources: Resources,
) {
    fun show(
        name: String,
        undo: () -> Unit,
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val title = parseDisplayTitle(name).title.ifBlank { name }
            val result =
                hostState.showSnackbar(
                    message = resources.getString(R.string.removed_format, title),
                    actionLabel = resources.getString(R.string.undo_action),
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
}

@Composable
fun rememberUndoSnackbar(): UndoSnackbar {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    return remember(resources) { UndoSnackbar(SnackbarHostState(), scope, resources) }
}

/** Where the Undo shows: above the dock. */
@Composable
fun UndoSnackbarHost(
    undo: UndoSnackbar,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(undo.hostState, modifier.padding(bottom = LocalDockInset.current))
}
