package org.njarasoa.fijerena.ui.components

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.navigation.LocalDockInset

/**
 * The phone's one snackbar: the Undo after a removal (wishlist UX plan, U-D4: "Removed <title> ·
 * Undo" for 10 s, as TV's Undo bar) and the app's plain messages ([AppNotices]). One shows at a
 * time, in the one [CinemaSnackbarHost] at the root of the screens; a screen gets it from
 * [LocalCinemaSnackbar].
 *
 * A new Undo takes the place of the one shown; that one stays removed. An Undo goes when the
 * screen changes ([dismissUndo]): it acts on the screen that made it.
 */
class CinemaSnackbar(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val resources: Resources,
) {
    private var undoJob: Job? = null

    /**
     * "Removed [name] · Undo". A title ([isTitle]) shows without its language tag and year; a
     * category's name shows as it is.
     */
    fun show(
        name: String,
        isTitle: Boolean = true,
        undo: () -> Unit,
    ) {
        val title = if (isTitle) parseDisplayTitle(name).title.ifBlank { name } else name
        showUndo(resources.getString(R.string.removed_format, title), undo)
    }

    /** [message] with an Undo, for 10 s. */
    fun showUndo(
        message: String,
        undo: () -> Unit,
    ) {
        undoJob?.cancel()
        hostState.currentSnackbarData?.dismiss()
        undoJob =
            scope.launch {
                val result =
                    hostState.showSnackbar(
                        message = message,
                        actionLabel = resources.getString(R.string.undo_action),
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) undo()
            }
    }

    /** A plain message, after the one shown. Send it through [AppNotices]. */
    internal fun message(text: String) {
        scope.launch { hostState.showSnackbar(message = text, duration = SnackbarDuration.Short) }
    }

    /** Takes away the Undo shown, if any; a plain message stays. */
    fun dismissUndo() {
        undoJob?.cancel()
        undoJob = null
    }
}

@Composable
fun rememberCinemaSnackbar(): CinemaSnackbar {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    return remember(resources) { CinemaSnackbar(SnackbarHostState(), scope, resources) }
}

/** The screens' [CinemaSnackbar], provided with its [CinemaSnackbarHost] at the root of the screens. */
val LocalCinemaSnackbar =
    staticCompositionLocalOf<CinemaSnackbar> { error("No CinemaSnackbar: provide one with its CinemaSnackbarHost") }

/**
 * Where [snackbar] shows: at the bottom centre, above the dock, the system bar and the keyboard.
 * Put it once, over the screens; it also shows the [AppNotices] messages.
 */
@Composable
fun CinemaSnackbarHost(
    snackbar: CinemaSnackbar,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(snackbar) { AppNotices.messages.collect(snackbar::message) }
    val systemBottom =
        WindowInsets.navigationBars
            .union(WindowInsets.ime)
            .asPaddingValues()
            .calculateBottomPadding()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // The dock's inset already holds the system bar.
        SnackbarHost(snackbar.hostState, Modifier.padding(bottom = maxOf(LocalDockInset.current, systemBottom)))
    }
}
