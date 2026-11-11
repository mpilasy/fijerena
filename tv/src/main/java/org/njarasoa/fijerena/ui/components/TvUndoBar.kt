package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * What a [TvUndoBar] shows: the last removal and how to take it back
 * (the tv recents favorites plan → A, "No confirmation dialog; Undo instead").
 *
 * ```
 * val undoBar = rememberUndoBarState()
 * Box(Modifier.undoOnRemoteKeys(undoBar)) {
 *     // a row's removal:
 *     val undo = viewModel.removeFromRecent(item.id, contentType)
 *     undoBar.show(item.name, undo)
 *     Column {
 *         List(Modifier.weight(1f))
 *         TvUndoBar(undoBar)
 *     }
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

    /**
     * Shows "Removed [name]", or with [episodesWatched] "Episodes marked as watched: [name]", with
     * [onUndo] as its Undo, replacing any bar already up.
     */
    fun show(
        name: String,
        episodesWatched: Boolean = false,
        onUndo: () -> Unit,
    ) {
        entry = Entry(name, onUndo, episodesWatched)
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

    /** OK is held from an Undo ([undoOnRemoteKeys]): the rest of that press is not the list's. */
    internal var swallowingOk = false

    internal fun expire(expired: Entry) {
        if (entry === expired) entry = null
    }

    // A class, not a data class: two removals of the same row are two bars, each with its timer.
    internal class Entry(
        val name: String,
        val onUndo: () -> Unit,
        val episodesWatched: Boolean,
    )
}

@Composable
fun rememberUndoBarState(): UndoBarState = remember { UndoBarState() }

/**
 * The remote's ways to Undo while the bar shows: the Menu key, or holding OK (for remotes without
 * a Menu key, such as the Chromecast with Google TV's). The bar never takes focus: a focusable bar
 * that goes away after a few seconds would drop focus with it. So focus stays on the list (on the
 * row that took the removed row's place), and Menu or a held OK, which otherwise open a row's
 * actions, undo instead. A short OK still does the focused row's job. Put this on the screen's (or
 * the panel's) root: it previews the keys before the focused row.
 *
 * A held OK is caught on its first auto-repeat, where a row's `onLongClick` would fire; the press's
 * later repeats and its key-up are swallowed too, so the row neither opens its actions nor takes
 * the release as a click.
 */
fun Modifier.undoOnRemoteKeys(state: UndoBarState): Modifier =
    onPreviewKeyEvent { event ->
        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        // A new press: a held press whose key-up went elsewhere must not swallow this one.
        if (isOk && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) state.swallowingOk = false
        when {
            event.key == Key.Menu && state.isShowing -> {
                if (event.type == KeyEventType.KeyDown) state.undo()
                true
            }

            isOk && state.swallowingOk -> {
                if (event.type == KeyEventType.KeyUp) state.swallowingOk = false
                true
            }

            isOk && state.isShowing && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                state.swallowingOk = true
                state.undo()
                true
            }

            else -> {
                false
            }
        }
    }

/**
 * A small bar, "Removed 24Hrs TV · Hold OK or press Menu to undo", for [state]'s last removal. Not
 * focusable (see [undoOnRemoteKeys]). Put it in the screen's (or the panel's) column under the
 * list, which takes the rest of the column (`Modifier.weight(1f)`), so it never covers the last
 * row. Callers give it no alignment or padding.
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
    Box(modifier.fillMaxWidth().padding(top = Spacing.sm), contentAlignment = Alignment.Center) {
        TvMessageBar(
            icon = if (entry.episodesWatched) CinemaIcons.MarkWatched else CinemaIcons.Delete,
            text =
                stringResource(
                    if (entry.episodesWatched) R.string.new_episodes_marked_watched_undo_format else R.string.undo_removed_format,
                    entry.name,
                ),
        )
    }
}

/** The look of [TvUndoBar] and [TvNoticeHost]: an icon and up to two lines on a panel. */
@Composable
internal fun TvMessageBar(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val radius = CornerRadius.medium
    val shape = cornerShape(radius)
    Row(
        modifier =
            modifier
                .padding(bottom = Spacing.md)
                .background(CinemaTheme.colors.surface, shape)
                .border(TvDimensions.borderDefault, CinemaTheme.colors.glassBorder, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TvIcon(
            imageVector = icon,
            contentDescription = null,
            tone = TextTone.Primary,
            modifier = Modifier.size(TvDimensions.iconSmall),
        )
        CinemaText( // the panels are narrow: "Hold OK or press Menu to undo" must not be cut off
            overflow = TextOverflow.Ellipsis,
            text = text,
            maxLines = 2,
        )
    }
}
