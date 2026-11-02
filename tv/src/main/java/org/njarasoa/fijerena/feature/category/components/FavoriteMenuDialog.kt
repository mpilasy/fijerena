package org.njarasoa.fijerena.feature.category.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.njarasoa.fijerena.core.ui.model.nameAndFavoriteState
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.components.TvActionMenu

/**
 * The action menu ([TvActionMenu]) of a category/stream row (long-press OK or the Menu key — UX
 * overhaul plan Part II P3): favorite toggle always, watched toggle too when [onToggleWatched] is
 * given — `target.isWatched == null` (Live TV, categories) is what keeps callers from passing one.
 * A show row gets "Mark show watched / unwatched" ([onToggleShowWatched]) in place of that toggle.
 * See the watch state durable storage plan Phase 6. Independent actions rather
 * than an AlertDialog's usual confirm/cancel pair: each row commits immediately, `onDismiss` alone
 * closes the menu. Safe actions come first and focus opens on the first of them; the removals
 * (Remove from Favourites, Remove from Recent) come last, just above Cancel, in every list (user,
 * 2026-10-30, UX audit fixes plan → UX-D1; it replaces "Remove from Recent first" on Recent). With
 * no safe action, focus opens on Cancel. Removals are not confirmed: the caller shows an Undo bar
 * instead (`TvUndoBar`).
 */
@Composable
fun FavoriteContextMenuDialog(
    target: FavoriteMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onToggleWatched: (() -> Unit)? = null,
    onRemoveFromRecent: (() -> Unit)? = null,
    // A show row's "Mark show watched / unwatched", worded from `target.isShowWatched`
    // (the season show watched plan Phase 3). Null hides it.
    onToggleShowWatched: (() -> Unit)? = null,
) {
    val (itemName, isFavorite) = target.nameAndFavoriteState()
    val streamTarget = target as? FavoriteMenuTarget.Stream
    val isWatched = streamTarget?.isWatched
    val isInRecent = streamTarget?.isInRecent == true

    val favoriteAction =
        if (isFavorite) {
            MenuAction(CinemaIcons.StarBorder, stringResource(R.string.favorite_remove), removal = true, onClick = onConfirm)
        } else {
            MenuAction(CinemaIcons.Star, stringResource(R.string.favorite_add), onClick = onConfirm)
        }
    val watchedAction =
        if (onToggleWatched != null && isWatched != null) {
            MenuAction(
                icon = if (isWatched) CinemaIcons.RadioButtonUnchecked else CinemaIcons.CheckCircle,
                label = stringResource(if (isWatched) R.string.watched_unmark else R.string.watched_mark),
                onClick = onToggleWatched,
            )
        } else {
            null
        }
    val showWatchedAction =
        onToggleShowWatched?.let { toggle ->
            val showWatched = streamTarget?.isShowWatched == true
            MenuAction(
                icon = if (showWatched) CinemaIcons.RadioButtonUnchecked else CinemaIcons.CheckCircle,
                label = stringResource(if (showWatched) R.string.watched_unmark_show else R.string.watched_mark_show),
                onClick = toggle,
            )
        }
    val recentAction =
        onRemoveFromRecent?.takeIf { isInRecent }?.let { remove ->
            MenuAction(CinemaIcons.Delete, stringResource(R.string.recent_remove), removal = true, onClick = remove)
        }

    // Add to Favourites is safe: first. Remove from Favourites is a removal: last, before Remove
    // from Recent (the menu puts the removals last).
    TvActionMenu(
        title = itemName,
        actions = listOfNotNull(favoriteAction, watchedAction, showWatchedAction, recentAction),
        onDismiss = onDismiss,
    )
}
