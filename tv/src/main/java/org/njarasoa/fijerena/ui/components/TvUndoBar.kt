package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * What a [TvUndoBar] shows: the last removal and how to take it back
 * (docs/plans/20261009_tv-recents-favorites-plan.md → A, "No confirmation dialog; Undo instead").
 *
 * ```
 * val undoBar = rememberUndoBarState()
 * Box(Modifier.undoOnMenuKey(undoBar)) {
 *     // a row's removal:
 *     val undo = viewModel.removeFromRecent(item.id, contentType)
 *     undoBar.show(item.name, undo)
 *     TvUndoBar(undoBar, Modifier.align(Alignment.BottomCenter))
 * }
 * ```
 *
 * One removal at a time: a new [show] replaces the bar (the previous removal stays done). The bar
 * goes after [CinemaAnimation.undoBarDismissMs], on [dismiss] (the screen moved on: another list,
 * another tab) or with the screen.
 */
@Stable
class UndoBarState {
    internal var entry by mutableStateOf<Entry?>(null)
        private set

    val isShowing: Boolean
        get() = entry != null

    /** Shows "Removed [name]" with [onUndo] as its Undo, replacing any bar already up. */
    fun show(
        name: String,
        onUndo: () -> Unit,
    ) {
        entry = Entry(name, onUndo)
    }

    /** Runs the Undo of the bar showing and hides it. Returns whether there was one. */
    fun undo(): Boolean {
        val current = entry
        entry = null
        current?.onUndo?.invoke()
        return current != null
    }

    fun dismiss() {
        entry = null
    }

    internal fun expire(expired: Entry) {
        if (entry === expired) entry = null
    }

    // A class, not a data class: two removals of the same row are two bars, each with its timer.
    internal class Entry(
        val name: String,
        val onUndo: () -> Unit,
    )
}

@Composable
fun rememberUndoBarState(): UndoBarState = remember { UndoBarState() }

/**
 * The remote's way to Undo: the Menu key while the bar shows. The bar itself never takes focus —
 * a focusable bar that goes away after a few seconds would drop focus with it, the very bug the
 * removal fix is about — so focus stays on the list (on the row that took the removed row's
 * place) and Menu, which otherwise opens a row's actions, undoes instead. Hold OK still opens the
 * row's actions. Put this on the screen's (or the panel's) root: it previews the key before the
 * focused row sees it.
 */
fun Modifier.undoOnMenuKey(state: UndoBarState): Modifier =
    onPreviewKeyEvent { event ->
        if (event.key == Key.Menu && state.isShowing) {
            if (event.type == KeyEventType.KeyDown) state.undo()
            true
        } else {
            false
        }
    }

/**
 * A small bar, "Removed 24Hrs TV · Press Menu to undo", for [state]'s last removal; place it at
 * the bottom centre of the screen or panel. Not focusable — see [undoOnMenuKey].
 */
@Composable
fun TvUndoBar(
    state: UndoBarState,
    modifier: Modifier = Modifier,
) {
    val entry = state.entry ?: return
    LaunchedEffect(entry) {
        delay(CinemaAnimation.undoBarDismissMs)
        state.expire(entry)
    }
    val radius = CornerRadius.medium
    val shape = remember(radius) { RoundedCornerShape(radius) }
    Row(
        modifier =
            modifier
                .padding(bottom = Spacing.md)
                .background(CinemaSurface, shape)
                .border(TvDimensions.borderDefault, CinemaGlassBorder, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = CinemaIcons.Delete,
            contentDescription = null,
            tint = CinemaTextPrimary,
            modifier = Modifier.size(TvDimensions.iconSmall),
        )
        Text(
            text = stringResource(R.string.undo_removed_format, entry.name),
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
